# -*- coding: utf-8 -*-
"""
🛡️ Servicell Protector PC — herramienta para el técnico de Servicell-Emma.

Trabaja por cable USB con el celular del cliente usando ADB (la herramienta
oficial de Google). NO instala nada en el celular salvo que el técnico lo
decida con el botón correspondiente.

Qué hace:
  • Detecta el celular conectado (necesita «Depuración USB» activada).
  • Analiza todas las apps instaladas por el usuario y les pone un puntaje de
    riesgo (🔴 peligrosa / 🟡 revisar / 🟢 tranquila) con el mismo criterio
    que la app de Android.
  • Permite desinstalar las apps sospechosas desde la computadora.
  • Permite instalar Servicell Protector en el celular del cliente y dejarle
    los permisos puestos.
  • Arma un informe para el cliente.

Es una herramienta de uso profesional, operada por el técnico con el celular
del cliente presente y con la depuración activada por su dueño.
"""

import os
import sys
import re
import subprocess
import threading
import datetime
import tkinter as tk
from tkinter import ttk, messagebox, filedialog

NEGOCIO = "Servicell-Emma"
PAQUETE_APP = "ar.servicell.protector"

# ───────── Colores de la marca ─────────
FONDO = "#141414"
TARJETA = "#1E1E1E"
TEXTO = "#F2F2F2"
GRIS = "#9E9E9E"
ROJO = "#E53935"
AMARILLO = "#F9A825"
VERDE = "#2E7D32"
VERDE_CLARO = "#7CE29A"
AZUL = "#1565C0"

# ───────── Empresas de publicidad (un "limpiador" falso suele traer muchas) ─────────
REDES_PUBLICIDAD = {
    "com.google.android.gms.ads": "Google", "com.applovin": "AppLovin",
    "com.unity3d.ads": "Unity", "com.unity3d.services": "Unity", "com.ironsource": "ironSource",
    "com.facebook.ads": "Meta", "com.bytedance.sdk.openadsdk": "Pangle", "com.pangle": "Pangle",
    "com.mbridge": "Mintegral", "com.mintegral": "Mintegral", "com.vungle": "Vungle",
    "com.inmobi": "InMobi", "com.chartboost": "Chartboost", "com.fyber": "Fyber",
    "com.my.target": "myTarget", "com.yandex.mobile.ads": "Yandex", "com.adcolony": "AdColony",
    "sg.bigo.ads": "Bigo", "com.tapjoy": "Tapjoy", "com.startapp": "Start.io", "com.smaato": "Smaato",
}

NOMBRES_FUERTES = ["clean", "limpi", "boost", "acelera", "junk", "basura", "cooler", "enfria",
                   "optimi", "master", "antivirus", "virus", "cache", "saver", "speed", "ram"]
NOMBRES_DEBILES = ["bater", "battery", "update", "actualiz", "sistema", "system", "security",
                   "seguridad", "wallpaper", "fondo", "linterna", "flashlight", "torch", "vpn"]

# Apps conocidas de confianza (se ignoran si vinieron de una tienda oficial)
CONOCIDAS = ["com.whatsapp", "com.facebook", "com.instagram", "com.mercadopago", "com.mercadolibre",
             "com.google.", "com.android.", "com.samsung.", "com.sec.", "com.motorola.", "com.miui.",
             "com.xiaomi.", "com.huawei.", "com.spotify.", "com.netflix.", "org.telegram.",
             "com.microsoft.", "com.brubank", "com.naranjax", "ar.com.", PAQUETE_APP]
TIENDAS = ["com.android.vending", "com.sec.android.app.samsungapps", "com.huawei.appmarket",
           "com.xiaomi.market", "com.amazon.venezia"]

# Permisos peligrosos → (puntos, motivo)
PERMISOS = {
    "android.permission.SYSTEM_ALERT_WINDOW": (2, "Puede mostrarse encima de otras apps"),
    "android.permission.USE_FULL_SCREEN_INTENT": (2, "Puede abrir pantallas completas solas (así aparece la publicidad)"),
    "android.permission.KILL_BACKGROUND_PROCESSES": (2, "Cierra otras apps para hacer creer que «limpia»"),
    "android.permission.REQUEST_INSTALL_PACKAGES": (2, "Puede instalar otras apps"),
    "android.permission.PACKAGE_USAGE_STATS": (1, "Quiere saber qué apps usás"),
    "android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS": (1, "Pide que el celular no la apague nunca"),
    "android.permission.RECEIVE_BOOT_COMPLETED": (1, "Arranca sola al prender el celular"),
}


def ruta_adb():
    """adb.exe junto al programa; si no, el que esté en el sistema."""
    base = os.path.dirname(sys.executable if getattr(sys, "frozen", False) else __file__)
    local = os.path.join(base, "platform-tools", "adb.exe")
    if os.path.exists(local):
        return local
    local2 = os.path.join(base, "adb.exe")
    return local2 if os.path.exists(local2) else "adb"


def ruta_apk():
    base = os.path.dirname(sys.executable if getattr(sys, "frozen", False) else __file__)
    for nombre in ("ServicellProtector.apk", os.path.join("apk", "ServicellProtector.apk")):
        p = os.path.join(base, nombre)
        if os.path.exists(p):
            return p
    return None


ADB = ruta_adb()


def adb(*args, timeout=60):
    """Corre un comando de adb y devuelve (ok, salida)."""
    try:
        r = subprocess.run([ADB, *args], capture_output=True, text=True,
                           timeout=timeout, encoding="utf-8", errors="replace",
                           creationflags=subprocess.CREATE_NO_WINDOW if os.name == "nt" else 0)
        return r.returncode == 0, (r.stdout or "") + (r.stderr or "")
    except Exception as e:
        return False, str(e)


class App:
    def __init__(self, paquete):
        self.paquete = paquete
        self.etiqueta = paquete
        self.puntos = 0
        self.motivos = []
        self.admin = False

    @property
    def nivel(self):
        if self.puntos >= 9:
            return "peligrosa"
        if self.puntos >= 5:
            return "revisar"
        return "tranquila"


class Analizador:
    def __init__(self, log):
        self.log = log

    def dispositivo(self):
        ok, out = adb("devices")
        if not ok:
            return None
        lineas = [l for l in out.splitlines()[1:] if l.strip()]
        for l in lineas:
            if "\tdevice" in l:
                return l.split("\t")[0]
            if "\tunauthorized" in l:
                return "AUTORIZAR"
        return None

    def modelo(self):
        ok, m = adb("shell", "getprop", "ro.product.manufacturer")
        ok2, mod = adb("shell", "getprop", "ro.product.model")
        ok3, ver = adb("shell", "getprop", "ro.build.version.release")
        return f"{m.strip().title()} {mod.strip()} · Android {ver.strip()}"

    def _admins(self):
        ok, out = adb("shell", "dpm", "list-owners")
        ok2, out2 = adb("shell", "dumpsys", "device_policy")
        texto = (out or "") + (out2 or "")
        return set(re.findall(r"([a-zA-Z0-9_.]+)/[a-zA-Z0-9_.$]+", texto))

    def analizar(self):
        admins = self._admins()
        ok, out = adb("shell", "cmd", "package", "list", "packages", "-3")
        if not ok:
            return []
        paquetes = [l.split(":", 1)[1].strip() for l in out.splitlines() if ":" in l]
        resultado = []
        total = len(paquetes)
        for i, p in enumerate(paquetes):
            if p == PAQUETE_APP:
                continue
            self.log(f"Revisando {i + 1}/{total}…")
            app = self._evaluar(p, admins)
            if app and app.puntos > 0:
                resultado.append(app)
        resultado.sort(key=lambda a: a.puntos, reverse=True)
        return resultado, total

    def _evaluar(self, paquete, admins):
        ok, dump = adb("shell", "dumpsys", "package", paquete)
        if not ok or not dump:
            return None

        instalador = ""
        m = re.search(r"installerPackageName=(\S+)", dump)
        if m:
            instalador = m.group(1)
        desde_tienda = instalador in TIENDAS

        if desde_tienda and any(paquete.startswith(c) for c in CONOCIDAS):
            return None

        app = App(paquete)
        m = re.search(r"application-label(?:-[a-z]+)?:'([^']*)'", dump)
        if m:
            app.etiqueta = m.group(1)
        app.admin = paquete in admins

        permisos = set(re.findall(r"(android\.permission\.[A-Z_]+)", dump))

        def suma(p, motivo):
            app.puntos += p
            app.motivos.append(motivo)

        if not desde_tienda:
            suma(3, "No se instaló desde la tienda oficial (Play Store)")
        if "HAS CODE" in dump and re.search(r"hiddenUntilInstalled", dump):
            pass
        if app.admin:
            suma(3, "Está como administradora del dispositivo (para que no la borren)")

        texto = (app.etiqueta + " " + paquete).lower()
        if any(x in texto for x in NOMBRES_FUERTES):
            suma(3, "Nombre de «limpiador» o «acelerador», típico de apps con publicidad")
        elif any(x in texto for x in NOMBRES_DEBILES):
            suma(1, "Nombre que suelen usar las apps engañosas")

        redes = {v for k, v in REDES_PUBLICIDAD.items() if k in dump}
        if len(redes) >= 5:
            suma(3, f"Trae publicidad de {len(redes)} empresas distintas")
        elif len(redes) >= 3:
            suma(2, f"Trae publicidad de {len(redes)} empresas distintas")

        for perm, (pts, motivo) in PERMISOS.items():
            if perm in permisos:
                suma(pts, motivo)

        m = re.search(r"targetSdk=(\d+)", dump)
        if m and int(m.group(1)) <= 27:
            suma(1, "Hecha para un Android viejo (evita controles nuevos)")

        return app


class Ventana(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(f"Servicell Protector PC — {NEGOCIO}")
        self.geometry("920x680")
        self.configure(bg=FONDO)
        self.minsize(820, 600)
        self.apps = []
        self.vars = {}
        self.total = 0
        self.analizador = Analizador(self.estado)
        self._ui()
        self.refrescar_dispositivo()

    # ───────── Construcción de la interfaz ─────────
    def _ui(self):
        cab = tk.Frame(self, bg=TARJETA)
        cab.pack(fill="x")
        tk.Label(cab, text="🛡️  SERVICELL PROTECTOR  ·  PC",
                 bg=TARJETA, fg="white", font=("Segoe UI", 18, "bold")).pack(side="left", padx=20, pady=16)
        self.lbl_disp = tk.Label(cab, text="Buscando celular…", bg=TARJETA, fg=GRIS,
                                 font=("Segoe UI", 11))
        self.lbl_disp.pack(side="right", padx=20)

        barra = tk.Frame(self, bg=FONDO)
        barra.pack(fill="x", padx=16, pady=12)
        self.btn_analizar = self._boton(barra, "🔍  Analizar celular", AZUL, self.analizar)
        self.btn_analizar.pack(side="left")
        self.btn_borrar = self._boton(barra, "🗑️  Borrar seleccionadas", ROJO, self.borrar)
        self.btn_borrar.pack(side="left", padx=8)
        self.btn_instalar = self._boton(barra, "📲  Instalar la app al cliente", VERDE, self.instalar)
        self.btn_instalar.pack(side="left", padx=8)
        self.btn_informe = self._boton(barra, "📄  Informe", "#455A64", self.informe)
        self.btn_informe.pack(side="left", padx=8)

        self.resumen = tk.Label(self, text="", bg=FONDO, fg=TEXTO, font=("Segoe UI", 12, "bold"))
        self.resumen.pack(anchor="w", padx=18)

        cont = tk.Frame(self, bg=FONDO)
        cont.pack(fill="both", expand=True, padx=16, pady=(6, 4))
        canvas = tk.Canvas(cont, bg=FONDO, highlightthickness=0)
        scroll = ttk.Scrollbar(cont, orient="vertical", command=canvas.yview)
        self.lista = tk.Frame(canvas, bg=FONDO)
        self.lista.bind("<Configure>", lambda e: canvas.configure(scrollregion=canvas.bbox("all")))
        self.win = canvas.create_window((0, 0), window=self.lista, anchor="nw")
        canvas.bind("<Configure>", lambda e: canvas.itemconfig(self.win, width=e.width))
        canvas.configure(yscrollcommand=scroll.set)
        canvas.pack(side="left", fill="both", expand=True)
        scroll.pack(side="right", fill="y")
        canvas.bind_all("<MouseWheel>", lambda e: canvas.yview_scroll(int(-e.delta / 120), "units"))

        self.lbl_estado = tk.Label(self, text="", bg="#0D0D0D", fg=VERDE_CLARO,
                                   font=("Consolas", 10), anchor="w")
        self.lbl_estado.pack(fill="x", side="bottom")

    def _boton(self, padre, texto, color, accion):
        return tk.Button(padre, text=texto, command=accion, bg=color, fg="white",
                         font=("Segoe UI", 11, "bold"), relief="flat", padx=14, pady=10,
                         activebackground=color, cursor="hand2", borderwidth=0)

    def estado(self, txt):
        self.lbl_estado.after(0, lambda: self.lbl_estado.config(text="  " + txt))

    # ───────── Detección del celular ─────────
    def refrescar_dispositivo(self):
        def tarea():
            d = self.analizador.dispositivo()
            if d == "AUTORIZAR":
                self.lbl_disp.after(0, lambda: self.lbl_disp.config(
                    text="⚠️ Tocá «Permitir» en el celular", fg=AMARILLO))
            elif d:
                modelo = self.analizador.modelo()
                self.lbl_disp.after(0, lambda: self.lbl_disp.config(
                    text="✅ " + modelo, fg=VERDE_CLARO))
            else:
                self.lbl_disp.after(0, lambda: self.lbl_disp.config(
                    text="❌ Sin celular. Conectá el cable y activá «Depuración USB».", fg=GRIS))
            self.after(2500, self.refrescar_dispositivo)
        threading.Thread(target=tarea, daemon=True).start()

    # ───────── Análisis ─────────
    def analizar(self):
        d = self.analizador.dispositivo()
        if d == "AUTORIZAR":
            messagebox.showwarning("Autorizar", "En el celular tocá «Permitir» (marcá «Siempre») y reintentá.")
            return
        if not d:
            messagebox.showwarning("Sin celular",
                                   "No veo ningún celular.\n\n1. Conectá el cable.\n"
                                   "2. En el celular activá «Depuración USB».\n3. Tocá «Permitir».")
            return
        self.btn_analizar.config(state="disabled")
        for w in self.lista.winfo_children():
            w.destroy()
        self.resumen.config(text="")

        def tarea():
            res = self.analizador.analizar()
            if not res:
                self.estado("No se pudo leer el celular.")
                self.btn_analizar.after(0, lambda: self.btn_analizar.config(state="normal"))
                return
            self.apps, self.total = res
            self.after(0, self._mostrar)
            self.btn_analizar.after(0, lambda: self.btn_analizar.config(state="normal"))
            self.estado(f"Listo. Revisé {self.total} apps.")
        threading.Thread(target=tarea, daemon=True).start()

    def _mostrar(self):
        self.vars = {}
        sospechosas = [a for a in self.apps if a.nivel != "tranquila"]
        rojas = sum(1 for a in sospechosas if a.nivel == "peligrosa")
        amar = sum(1 for a in sospechosas if a.nivel == "revisar")
        self.resumen.config(
            text=f"🛡️ Revisé {self.total} apps   ·   🔴 {rojas} peligrosas   ·   "
                 f"🟡 {amar} a revisar   ·   🟢 {self.total - rojas - amar} tranquilas")
        if not sospechosas:
            tk.Label(self.lista, text="✅ No encontré apps sospechosas.",
                     bg=FONDO, fg=VERDE_CLARO, font=("Segoe UI", 14)).pack(anchor="w", pady=20)
            return
        for a in sospechosas:
            self._tarjeta(a)

    def _tarjeta(self, app):
        color = ROJO if app.nivel == "peligrosa" else AMARILLO
        card = tk.Frame(self.lista, bg=TARJETA, highlightbackground=color, highlightthickness=2)
        card.pack(fill="x", pady=6, padx=2)
        top = tk.Frame(card, bg=TARJETA)
        top.pack(fill="x", padx=14, pady=(12, 4))

        var = tk.BooleanVar(value=(app.nivel == "peligrosa"))
        self.vars[app.paquete] = var
        tk.Checkbutton(top, variable=var, bg=TARJETA, activebackground=TARJETA,
                       selectcolor=TARJETA).pack(side="left")
        etiqueta = "🔴" if app.nivel == "peligrosa" else "🟡"
        tk.Label(top, text=f"{etiqueta}  {app.etiqueta}", bg=TARJETA, fg=TEXTO,
                 font=("Segoe UI", 13, "bold")).pack(side="left")
        tk.Label(top, text=f"riesgo {min(app.puntos, 15)}/15", bg=TARJETA, fg=color,
                 font=("Segoe UI", 10, "bold")).pack(side="right")

        tk.Label(card, text=app.paquete, bg=TARJETA, fg=GRIS,
                 font=("Consolas", 9)).pack(anchor="w", padx=40)
        motivos = "\n".join("•  " + m for m in app.motivos)
        tk.Label(card, text=motivos, bg=TARJETA, fg="#D0D0D0", font=("Segoe UI", 10),
                 justify="left", anchor="w").pack(anchor="w", padx=40, pady=(2, 12))
        if app.admin:
            tk.Label(card, text="⚠️ Es administradora: puede pedir un toque en el celular para soltarse.",
                     bg=TARJETA, fg=AMARILLO, font=("Segoe UI", 9)).pack(anchor="w", padx=40, pady=(0, 10))

    # ───────── Borrar ─────────
    def borrar(self):
        elegidas = [a for a in self.apps if self.vars.get(a.paquete) and self.vars[a.paquete].get()]
        if not elegidas:
            messagebox.showinfo("Nada seleccionado", "Marcá las apps que querés borrar.")
            return
        nombres = "\n".join("•  " + a.etiqueta for a in elegidas)
        if not messagebox.askyesno("Confirmar", f"¿Borrar estas {len(elegidas)} apps del celular?\n\n{nombres}"):
            return

        def tarea():
            borradas, fallaron = [], []
            for a in elegidas:
                self.estado(f"Borrando {a.etiqueta}…")
                if a.admin:
                    adb("shell", "dpm", "remove-active-admin", a.paquete)
                ok, out = adb("uninstall", a.paquete)
                if ok and "Success" in out:
                    borradas.append(a)
                else:
                    ok2, out2 = adb("shell", "pm", "uninstall", "--user", "0", a.paquete)
                    (borradas if (ok2 and "Success" in out2) else fallaron).append(a)
            self.ultimas_borradas = borradas
            self.after(0, lambda: self._fin_borrado(borradas, fallaron))
        threading.Thread(target=tarea, daemon=True).start()

    def _fin_borrado(self, borradas, fallaron):
        msg = f"✅ Se borraron {len(borradas)} apps."
        if fallaron:
            msg += ("\n\n⚠️ No se pudieron borrar (quizás son administradoras o del sistema):\n"
                    + "\n".join("•  " + a.etiqueta for a in fallaron)
                    + "\n\nProbá tocando «Desactivar» en el celular, o entrá en modo seguro.")
        messagebox.showinfo("Limpieza", msg)
        self.estado(msg.split("\n")[0])
        self.analizar()

    # ───────── Instalar la app ─────────
    def instalar(self):
        apk = ruta_apk()
        if not apk:
            apk = filedialog.askopenfilename(title="Elegí ServicellProtector.apk",
                                             filetypes=[("App de Android", "*.apk")])
            if not apk:
                return
        if self.analizador.dispositivo() in (None, "AUTORIZAR"):
            messagebox.showwarning("Sin celular", "Conectá el celular y autorizá la depuración.")
            return

        def tarea():
            self.estado("Instalando Servicell Protector…")
            ok, out = adb("install", "-r", "-g", apk, timeout=180)
            if not (ok and "Success" in out):
                ok, out = adb("install", "-r", apk, timeout=180)
            if not (ok and "Success" in out):
                self.after(0, lambda: messagebox.showerror("Error", "No se pudo instalar:\n\n" + out[-400:]))
                return
            self.estado("Dándole los permisos…")
            self._configurar_permisos()
            self.after(0, lambda: messagebox.showinfo(
                "¡Instalada!",
                "✅ Servicell Protector quedó instalada y con los permisos puestos.\n\n"
                "Ahora, en el celular, abrí la app y escribí el código de activación "
                "con el plan que el cliente haya elegido."))
            self.estado("App instalada y configurada.")
        threading.Thread(target=tarea, daemon=True).start()

    def _configurar_permisos(self):
        # Permisos y ajustes que, por cable, se pueden dejar listos sin tocar el celular
        adb("shell", "pm", "grant", PAQUETE_APP, "android.permission.POST_NOTIFICATIONS")
        adb("shell", "appops", "set", PAQUETE_APP, "GET_USAGE_STATS", "allow")
        adb("shell", "appops", "set", PAQUETE_APP, "SYSTEM_ALERT_WINDOW", "allow")
        adb("shell", "dumpsys", "deviceidle", "whitelist", "+" + PAQUETE_APP)
        adb("shell", "monkey", "-p", PAQUETE_APP, "-c", "android.intent.category.LAUNCHER", "1")

    # ───────── Informe ─────────
    def informe(self):
        borradas = getattr(self, "ultimas_borradas", [])
        fecha = datetime.datetime.now().strftime("%d/%m/%Y %H:%M")
        try:
            modelo = self.analizador.modelo()
        except Exception:
            modelo = ""
        lineas = [
            f"INFORME DE LIMPIEZA — {NEGOCIO}",
            f"Fecha: {fecha}",
            f"Celular: {modelo}",
            "",
            f"Apps revisadas: {self.total}",
            f"Apps borradas: {len(borradas)}",
            "",
        ]
        if borradas:
            lineas.append("Se quitaron estas apps con publicidad o sospechosas:")
            lineas += [f"  - {a.etiqueta}" for a in borradas]
        else:
            lineas.append("No se borraron apps en esta sesión.")
        lineas += ["", "Gracias por confiar en " + NEGOCIO + ".",
                   "Para que no vuelva a pasar, dejá instalada la app Servicell Protector."]
        texto = "\n".join(lineas)

        ruta = filedialog.asksaveasfilename(defaultextension=".txt",
                                            initialfile=f"Informe Servicell {fecha.replace('/', '-').replace(':', '.')}.txt",
                                            filetypes=[("Texto", "*.txt")])
        if ruta:
            with open(ruta, "w", encoding="utf-8") as f:
                f.write(texto)
            messagebox.showinfo("Informe", "Informe guardado:\n" + ruta)


if __name__ == "__main__":
    Ventana().mainloop()
