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
import json
import tempfile
import webbrowser
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

# Apps conocidas de confianza (se ignoran si vinieron de una tienda oficial o de fábrica)
CONOCIDAS = ["com.whatsapp", "com.facebook", "com.instagram", "com.mercadopago", "com.mercadolibre",
             "com.google.", "com.android.", "com.samsung.", "com.sec.", "com.motorola.", "com.miui.",
             "com.xiaomi.", "com.huawei.", "com.spotify.", "com.netflix.", "org.telegram.",
             "com.microsoft.", "com.brubank", "com.naranjax", "ar.com.", "com.zhiliaoapp.",
             "com.snapchat.", "com.ualabee.", PAQUETE_APP,
             # 🇦🇷 Bancos y billeteras
             "com.mosync.app_Banco_Galicia", "com.galicia", "com.santander", "com.bbva", "com.macro",
             "ar.macro", "com.icbc", "com.supervielle", "com.hsbc", "com.bancopatagonia", "com.patagonia",
             "com.bancociudad", "com.bancoprovincia", "com.bna", "com.cuentadni", "com.uala", "ar.uala",
             "com.playdigital.modo", "com.personalpay", "com.prex", "com.lemoncash", "com.belo",
             "com.cuentasimple", "com.credicoop", "com.comafi", "com.itau",
             # 🇦🇷 Estado, celulares, delivery y viajes
             "ar.gob.", "ar.gov.", "gob.ar.", "com.afip", "com.anses", "com.sube",
             "com.personal", "com.telecom", "com.claro", "com.movistar", "com.telefonica",
             "com.grability.rappi", "com.pedidosya", "com.ubercab", "com.didiglobal", "com.cabify",
             "com.tiendanube", "com.despegar"]
TIENDAS = ["com.android.vending", "com.google.android.feedback", "com.sec.android.app.samsungapps",
           "com.huawei.appmarket", "com.xiaomi.market", "com.xiaomi.mipicks", "com.xiaomi.discover",
           "com.heytap.market", "com.oppo.market", "com.vivo.appstore", "com.amazon.venezia",
           # Restauración al cambiar de celular
           "com.sec.android.easyMover", "com.google.android.apps.restore", "com.motorola.migrate",
           "com.miui.backup", "com.coloros.backuprestore",
           # Instaladores oficiales de Facebook (de fábrica)
           "com.facebook.system", "com.facebook.appmanager"]
INSTALADORES_MANUALES = ["com.google.android.packageinstaller", "com.android.packageinstaller",
                         "com.samsung.android.packageinstaller", "com.miui.packageinstaller"]

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


def _carpetas_base():
    """Dónde buscar lo que viene adentro del programa.
    Armado como un solo .exe, todo se descomprime en una carpeta temporal (sys._MEIPASS)."""
    carpetas = []
    if getattr(sys, "_MEIPASS", None):
        carpetas.append(sys._MEIPASS)
    carpetas.append(os.path.dirname(sys.executable if getattr(sys, "frozen", False) else os.path.abspath(__file__)))
    return carpetas


def ruta_adb():
    """adb.exe que viene con el programa; si no, el que esté en el sistema."""
    for base in _carpetas_base():
        for p in (os.path.join(base, "platform-tools", "adb.exe"), os.path.join(base, "adb.exe")):
            if os.path.exists(p):
                return p
    return "adb"


def ruta_apk():
    for base in _carpetas_base():
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


# ───────── Datos guardados en la compu del técnico ─────────
def carpeta_datos():
    base = os.environ.get("APPDATA") or os.path.expanduser("~")
    d = os.path.join(base, "ServicellProtectorPC")
    try:
        os.makedirs(d, exist_ok=True)
    except Exception:
        pass
    return d


def _leer_json(nombre, defecto):
    try:
        with open(os.path.join(carpeta_datos(), nombre), encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return defecto


def _guardar_json(nombre, datos):
    try:
        with open(os.path.join(carpeta_datos(), nombre), "w", encoding="utf-8") as f:
            json.dump(datos, f, ensure_ascii=False, indent=1)
    except Exception:
        pass


class ListaVirus:
    """🦠 Tu propia lista de virus: aprende con cada celular que limpiás en el local."""

    def __init__(self):
        self.datos = _leer_json("virus.json", {})  # paquete → {"nombre", "fecha"}

    def tiene(self, paquete):
        return paquete in self.datos

    def agregar(self, paquete, nombre):
        self.datos[paquete] = {"nombre": nombre,
                               "fecha": datetime.datetime.now().strftime("%d/%m/%Y")}
        _guardar_json("virus.json", self.datos)

    def quitar(self, paquete):
        self.datos.pop(paquete, None)
        _guardar_json("virus.json", self.datos)

    def todos(self):
        return sorted(self.datos.items(), key=lambda kv: kv[1].get("nombre", kv[0]).lower())


LISTA_VIRUS = ListaVirus()


# ───────── Lectura de una app (nombre real, ícono en el menú, publicidad que trae) ─────────
def leer_apk(ruta):
    """Lee del archivo de la app su nombre real, si tiene ícono en el menú y sus componentes."""
    from pyaxmlparser import APK
    apk = APK(ruta)
    nombre = ""
    try:
        nombre = (apk.application or "").strip()
    except Exception:
        pass
    componentes = []
    for metodo in ("get_activities", "get_services", "get_receivers"):
        f = getattr(apk, metodo, None)
        if callable(f):
            try:
                componentes += [str(x) for x in (f() or [])]
            except Exception:
                pass
    principal = None
    f = getattr(apk, "get_main_activity", None)
    if callable(f):
        try:
            principal = f()
        except Exception:
            principal = None
    return {"nombre": nombre, "componentes": componentes,
            "principal": principal, "leyo_manifiesto": bool(componentes)}


# ───────── Lectura de "dumpsys package packages" (todas las apps de una sola vez) ─────────
_INICIO_PAQUETE = re.compile(r"^\s{2}Package \[([^\]]+)\] \(")
_FUENTE = re.compile(r"\b(?:installerPackageName|(?:install)?[Ii]nitiatingPackageName|"
                     r"(?:install)?[Oo]riginatingPackageName)=(\S+)")


def separar_paquetes(texto):
    """Divide la salida de dumpsys en un bloque de texto por app."""
    bloques, actual, lineas = {}, None, []

    def cerrar():
        if actual and actual not in bloques:
            bloques[actual] = "\n".join(lineas)

    for l in texto.splitlines():
        m = _INICIO_PAQUETE.match(l)
        if m:
            cerrar()
            actual, lineas = m.group(1), []
            continue
        if actual is not None:
            if l and not l[0].isspace():      # empieza otra sección (ej. "Hidden system packages:")
                cerrar()
                actual, lineas = None, []
                continue
            lineas.append(l)
    cerrar()
    return bloques


def origen_de(bloque):
    fuentes = [f for f in _FUENTE.findall(bloque) if f not in ("null", "android", "")]
    if any(f in TIENDAS for f in fuentes):
        return "tienda"
    if not fuentes:
        return "preinstalada"
    if all(f in INSTALADORES_MANUALES for f in fuentes):
        return "manual"
    return "otra_app"


def version_de(bloque):
    m = re.search(r"versionCode=(\d+)", bloque)
    return m.group(1) if m else "0"


class App:
    def __init__(self, paquete):
        self.paquete = paquete
        self.etiqueta = paquete
        self.nombre_real = False
        self.puntos = 0
        self.motivos = []
        self.admin = False
        self.conocida = any(paquete.startswith(c) for c in CONOCIDAS)

    @property
    def nivel(self):
        if self.puntos >= 9:
            return "peligrosa"
        if self.puntos >= 5:
            return "revisar"
        return "tranquila"


def puntuar(paquete, bloque, admins, info=None):
    """Pone el puntaje de riesgo a una app. Devuelve None si es de confianza."""
    origen = origen_de(bloque)
    en_lista = LISTA_VIRUS.tiene(paquete)

    # Apps conocidas (Facebook, WhatsApp, bancos…): solo se revisan si alguien las instaló con un APK suelto
    if not en_lista and any(paquete.startswith(c) for c in CONOCIDAS) and origen in ("tienda", "preinstalada"):
        return None

    app = App(paquete)
    if info and info.get("nombre"):
        app.etiqueta = info["nombre"]
        app.nombre_real = True
    app.admin = paquete in admins

    def suma(p, motivo):
        app.puntos += p
        app.motivos.append(motivo)

    if en_lista:
        suma(10, f"Está en tu lista de virus de {NEGOCIO}")
    if origen in ("manual", "otra_app"):
        suma(3, "No se instaló desde la tienda oficial (Play Store)")
    elif origen == "preinstalada":
        suma(1, "No figura de dónde se instaló (puede venir de fábrica)")
    if app.admin:
        suma(3, "Está como administradora del dispositivo (para que no la borren)")

    if info and info.get("leyo_manifiesto") and not info.get("principal"):
        suma(3, "Está escondida: no tiene ícono en el menú")

    texto = (app.etiqueta + " " + paquete).lower()
    if any(x in texto for x in NOMBRES_FUERTES):
        suma(3, "Nombre de «limpiador» o «acelerador», típico de apps con publicidad")
    elif any(x in texto for x in NOMBRES_DEBILES):
        suma(1, "Nombre que suelen usar las apps engañosas")

    fuente_redes = "\n".join(info.get("componentes", [])) if info else bloque
    redes = {v for k, v in REDES_PUBLICIDAD.items() if k in fuente_redes}
    if len(redes) >= 5:
        suma(3, f"Trae publicidad de {len(redes)} empresas distintas")
    elif len(redes) >= 3:
        suma(2, f"Trae publicidad de {len(redes)} empresas distintas")

    permisos = set(re.findall(r"(android\.permission\.[A-Z_]+)", bloque))
    for perm, (pts, motivo) in PERMISOS.items():
        if perm in permisos:
            suma(pts, motivo)

    m = re.search(r"targetSdk=(\d+)", bloque)
    if m and int(m.group(1)) <= 27:
        suma(1, "Hecha para un Android viejo (evita controles nuevos)")
    return app


class Analizador:
    # A cuántas apps sospechosas como máximo se les lee el nombre real (para no tardar)
    MAX_LEER = 30
    # Apps más pesadas que esto no se copian a la compu (juegos grandes, por ejemplo)
    MAX_MB = 150

    def __init__(self, log):
        self.log = log
        self.cache = _leer_json("nombres.json", {})   # "paquete:versión" → info

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
        self.log("Leyendo la lista de apps…")
        ok, out = adb("shell", "cmd", "package", "list", "packages", "-3")
        if not ok:
            ok, out = adb("shell", "pm", "list", "packages", "-3")
            if not ok:
                return []
        terceros = [l.split(":", 1)[1].strip() for l in out.splitlines() if l.startswith("package:")]
        terceros = [p for p in terceros if p != PAQUETE_APP]

        # ⚡ Todas las apps en UNA sola lectura (antes era una por una)
        self.log("Leyendo los datos de todas las apps…")
        ok, dump = adb("shell", "dumpsys", "package", "packages", timeout=180)
        bloques = separar_paquetes(dump if ok else "")
        admins = self._admins()

        # 1ª pasada: puntaje rápido con lo que dice Android
        previas = []
        for p in terceros:
            bloque = bloques.get(p)
            if bloque is None:                      # por si este Android no lo trajo: se pide solo esa
                ok1, b1 = adb("shell", "dumpsys", "package", p)
                bloque = b1 if ok1 else ""
                bloques[p] = bloque
            app = puntuar(p, bloque, admins)
            if app:
                previas.append(app)

        # 2ª pasada: a las sospechosas se les lee el nombre real y qué publicidad traen
        candidatas = sorted([a for a in previas if a.puntos >= 2 or LISTA_VIRUS.tiene(a.paquete)],
                            key=lambda a: a.puntos, reverse=True)[:self.MAX_LEER]
        nombres_cand = {a.paquete for a in candidatas}
        resultado = [a for a in previas if a.paquete not in nombres_cand and a.puntos > 0]
        for i, a in enumerate(candidatas):
            self.log(f"Revisando a fondo {i + 1} de {len(candidatas)}: {a.paquete}")
            info = self._info(a.paquete, version_de(bloques[a.paquete]))
            app = puntuar(a.paquete, bloques[a.paquete], admins, info) or a
            if app.puntos > 0:
                resultado.append(app)
        _guardar_json("nombres.json", self.cache)

        resultado.sort(key=lambda a: a.puntos, reverse=True)
        return resultado, len(terceros)

    def _info(self, paquete, version):
        """Nombre real y componentes de una app (se guardan para no repetir)."""
        clave = f"{paquete}:{version}"
        if clave in self.cache:
            return self.cache[clave]
        ok, out = adb("shell", "pm", "path", paquete)
        rutas = [l.split(":", 1)[1].strip() for l in out.splitlines() if l.startswith("package:")] if ok else []
        if not rutas:
            return None
        ruta = next((r for r in rutas if r.endswith("/base.apk")), rutas[0])
        ok, tam = adb("shell", "stat", "-c", "%s", ruta)
        try:
            if ok and int(tam.strip().splitlines()[0]) > self.MAX_MB * 1024 * 1024:
                return None
        except Exception:
            pass
        carpeta = tempfile.mkdtemp(prefix="servicell_")
        local = os.path.join(carpeta, "app.apk")
        try:
            ok, _ = adb("pull", ruta, local, timeout=240)
            if not ok or not os.path.exists(local):
                return None
            info = leer_apk(local)
            self.cache[clave] = info
            return info
        except Exception:
            return None
        finally:
            try:
                if os.path.exists(local):
                    os.remove(local)
                os.rmdir(carpeta)
            except Exception:
                pass


# ───────── "No aparece mi celular": detectar la marca por el cable USB ─────────
MARCAS_USB = {
    "04E8": "Samsung", "22B8": "Motorola", "2717": "Xiaomi", "12D1": "Huawei / Honor",
    "18D1": "Google (o genérico)", "1004": "LG", "22D9": "Oppo / Realme", "2D95": "Vivo",
    "0FCE": "Sony", "2A70": "OnePlus", "19D2": "ZTE", "1BBB": "Alcatel / TCL", "17EF": "Lenovo",
    "0BB4": "HTC",
}
URL_DRIVER_GOOGLE = "https://developer.android.com/studio/run/win-usb"
URL_DRIVERS_MARCAS = "https://developer.android.com/studio/run/oem-usb"


def marcas_conectadas():
    """Marcas de celulares enchufados por USB (aunque Windows no tenga el driver)."""
    if os.name != "nt":
        return []
    try:
        r = subprocess.run(
            ["powershell", "-NoProfile", "-Command",
             "Get-PnpDevice -PresentOnly | ForEach-Object { $_.InstanceId }"],
            capture_output=True, text=True, timeout=25, encoding="utf-8", errors="replace",
            creationflags=subprocess.CREATE_NO_WINDOW)
        vids = set(re.findall(r"VID_([0-9A-Fa-f]{4})", r.stdout or ""))
        return sorted({MARCAS_USB[v.upper()] for v in vids if v.upper() in MARCAS_USB})
    except Exception:
        return []


class Ventana(tk.Tk):
    def __init__(self):
        super().__init__()
        self.title(f"Servicell Protector PC — {NEGOCIO}")
        self.geometry("1080x700")
        self.configure(bg=FONDO)
        self.minsize(940, 600)
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
        tk.Button(cab, text="❓ No aparece mi celular", command=self.no_aparece, bg="#333333", fg="white",
                  font=("Segoe UI", 10, "bold"), relief="flat", padx=10, pady=6, cursor="hand2",
                  activebackground="#333333", borderwidth=0).pack(side="right", padx=(0, 16))
        self.lbl_disp.pack(side="right", padx=12)

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
        self.btn_lista = self._boton(barra, "🦠  Mi lista de virus", "#6A1B9A", self.ver_lista)
        self.btn_lista.pack(side="left", padx=8)

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
            if messagebox.askyesno("Sin celular", "No veo ningún celular.\n\n¿Querés ver la ayuda para conectarlo?"):
                self.no_aparece()
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

        # Las apps de marcas conocidas nunca vienen tildadas: puede ser la original
        var = tk.BooleanVar(value=(app.nivel == "peligrosa" and not app.conocida))
        self.vars[app.paquete] = var
        tk.Checkbutton(top, variable=var, bg=TARJETA, activebackground=TARJETA,
                       selectcolor=TARJETA).pack(side="left")
        etiqueta = "🔴" if app.nivel == "peligrosa" else "🟡"
        tk.Label(top, text=f"{etiqueta}  {app.etiqueta}", bg=TARJETA, fg=TEXTO,
                 font=("Segoe UI", 13, "bold")).pack(side="left")
        tk.Label(top, text=f"riesgo {min(app.puntos, 15)}/15", bg=TARJETA, fg=color,
                 font=("Segoe UI", 10, "bold")).pack(side="right")
        en_lista = LISTA_VIRUS.tiene(app.paquete)
        btn = tk.Button(top, text=("✔ Sacar de mi lista" if en_lista else "🦠 Es virus"),
                        bg=("#455A64" if en_lista else "#6A1B9A"), fg="white", relief="flat",
                        font=("Segoe UI", 9, "bold"), padx=8, pady=3, cursor="hand2", borderwidth=0)
        btn.config(command=lambda a=app, b=btn: self.alternar_virus(a, b))
        btn.pack(side="right", padx=10)

        tk.Label(card, text=app.paquete, bg=TARJETA, fg=GRIS,
                 font=("Consolas", 9)).pack(anchor="w", padx=40)
        motivos = "\n".join("•  " + m for m in app.motivos)
        tk.Label(card, text=motivos, bg=TARJETA, fg="#D0D0D0", font=("Segoe UI", 10),
                 justify="left", anchor="w").pack(anchor="w", padx=40, pady=(2, 12))
        if app.conocida:
            tk.Label(card, text="⚠️ Es una app conocida: puede ser la original. Confirmá con el cliente antes de borrarla.",
                     bg=TARJETA, fg=AMARILLO, font=("Segoe UI", 9)).pack(anchor="w", padx=40, pady=(0, 10))
        if app.admin:
            tk.Label(card, text="⚠️ Es administradora: puede pedir un toque en el celular para soltarse.",
                     bg=TARJETA, fg=AMARILLO, font=("Segoe UI", 9)).pack(anchor="w", padx=40, pady=(0, 10))

    # ───────── Mi lista de virus ─────────
    def alternar_virus(self, app, boton):
        if LISTA_VIRUS.tiene(app.paquete):
            LISTA_VIRUS.quitar(app.paquete)
            boton.config(text="🦠 Es virus", bg="#6A1B9A")
            self.estado(f"«{app.etiqueta}» salió de tu lista de virus.")
        else:
            if app.conocida and not messagebox.askyesno(
                    "Ojo", f"«{app.etiqueta}» parece una app conocida.\n¿Seguro que es un virus?"):
                return
            LISTA_VIRUS.agregar(app.paquete, app.etiqueta)
            boton.config(text="✔ Sacar de mi lista", bg="#455A64")
            self.estado(f"«{app.etiqueta}» quedó en tu lista: desde ahora sale en rojo en todos los celulares.")

    def ver_lista(self):
        v = tk.Toplevel(self)
        v.title("Mi lista de virus")
        v.geometry("620x520")
        v.configure(bg=FONDO)
        tk.Label(v, text="🦠 Mi lista de virus", bg=FONDO, fg="white",
                 font=("Segoe UI", 16, "bold")).pack(anchor="w", padx=18, pady=(16, 2))
        tk.Label(v, text="Las apps de esta lista salen en rojo en todos los celulares que conectes.",
                 bg=FONDO, fg=GRIS, font=("Segoe UI", 10)).pack(anchor="w", padx=18, pady=(0, 10))
        cont = tk.Frame(v, bg=FONDO)
        cont.pack(fill="both", expand=True, padx=14)

        def dibujar():
            for w in cont.winfo_children():
                w.destroy()
            items = LISTA_VIRUS.todos()
            if not items:
                tk.Label(cont, text="Todavía está vacía. Tocá «🦠 Es virus» en una app para sumarla.",
                         bg=FONDO, fg=GRIS, font=("Segoe UI", 11)).pack(anchor="w", pady=20)
            for paq, d in items:
                f = tk.Frame(cont, bg=TARJETA)
                f.pack(fill="x", pady=4)
                tk.Label(f, text=d.get("nombre", paq), bg=TARJETA, fg=TEXTO,
                         font=("Segoe UI", 11, "bold")).pack(anchor="w", padx=10, pady=(6, 0))
                tk.Label(f, text=f"{paq}   ·   agregada el {d.get('fecha', '')}", bg=TARJETA, fg=GRIS,
                         font=("Consolas", 9)).pack(anchor="w", padx=10, pady=(0, 6))
                tk.Button(f, text="Quitar", bg="#455A64", fg="white", relief="flat", borderwidth=0,
                          cursor="hand2", command=lambda p=paq: (LISTA_VIRUS.quitar(p), dibujar())
                          ).place(relx=1.0, rely=0.5, anchor="e", x=-10)

        def copiar():
            texto = "\n".join(p for p, _ in LISTA_VIRUS.todos())
            self.clipboard_clear()
            self.clipboard_append(texto)
            messagebox.showinfo("Copiada", "Lista copiada.\n\nMandámela para sumarla a la app del celular "
                                           "en la próxima versión.", parent=v)

        abajo = tk.Frame(v, bg=FONDO)
        abajo.pack(fill="x", padx=14, pady=12)
        self._boton(abajo, "📋  Copiar lista", AZUL, copiar).pack(side="left")
        self._boton(abajo, "Cerrar", "#455A64", v.destroy).pack(side="right")
        dibujar()

    # ───────── No aparece mi celular ─────────
    def no_aparece(self):
        v = tk.Toplevel(self)
        v.title("No aparece mi celular")
        v.geometry("640x560")
        v.configure(bg=FONDO)
        tk.Label(v, text="❓ No aparece mi celular", bg=FONDO, fg="white",
                 font=("Segoe UI", 16, "bold")).pack(anchor="w", padx=18, pady=(16, 8))
        pasos = ("Probá en este orden:\n\n"
                 "1.  Cambiá el cable: muchos son solo de carga y no pasan datos.\n"
                 "2.  En el celular, bajá la barra de arriba, tocá «Cargando por USB» y elegí «Transferir archivos».\n"
                 "3.  Revisá que esté activada la «Depuración USB» (en Opciones de desarrollador).\n"
                 "4.  Desenchufá y volvé a enchufar: en el celular tocá «Permitir» y marcá «Siempre».\n"
                 "5.  Si sigue sin aparecer, falta el driver de la marca (se instala una sola vez).")
        tk.Label(v, text=pasos, bg=FONDO, fg=TEXTO, font=("Segoe UI", 11), justify="left",
                 wraplength=600).pack(anchor="w", padx=18)
        self.lbl_marca = tk.Label(v, text="🔎 Buscando qué celular está enchufado…", bg=FONDO, fg=AMARILLO,
                                  font=("Segoe UI", 11, "bold"), justify="left", wraplength=600)
        self.lbl_marca.pack(anchor="w", padx=18, pady=14)
        botones = tk.Frame(v, bg=FONDO)
        botones.pack(fill="x", padx=18)
        self._boton(botones, "⬇️  Drivers por marca (página de Google)", AZUL,
                    lambda: webbrowser.open(URL_DRIVERS_MARCAS)).pack(fill="x", pady=4)
        self._boton(botones, "⬇️  Driver de Google (Pixel y muchos genéricos)", "#455A64",
                    lambda: webbrowser.open(URL_DRIVER_GOOGLE)).pack(fill="x", pady=4)
        self._boton(botones, "Cerrar", "#333333", v.destroy).pack(fill="x", pady=(14, 4))

        def buscar():
            marcas = marcas_conectadas()
            if marcas:
                txt = ("📱 Detecté un celular " + " / ".join(marcas) + " enchufado.\n"
                       "Si no aparece arriba con ✅, buscá «" + marcas[0].split(" ")[0] +
                       "» en la página de drivers por marca.")
            else:
                txt = ("No detecto ningún celular por el cable. Probá con otro cable o con "
                       "«Transferir archivos» (pasos 1 y 2).")
            self.after(0, lambda: self.lbl_marca.config(text=txt) if self.lbl_marca.winfo_exists() else None)
        threading.Thread(target=buscar, daemon=True).start()

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


def _autoprueba(salida):
    """Usado al armar el programa en GitHub: confirma que encuentra lo que trae adentro."""
    r = {"adb": ADB, "adb_existe": os.path.exists(ADB), "apk": ruta_apk(), "lector_apps": False}
    try:
        import pyaxmlparser  # noqa: F401
        r["lector_apps"] = True
    except Exception as e:
        r["error"] = str(e)
    with open(salida, "w", encoding="utf-8") as f:
        json.dump(r, f)


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--autoprueba":
        _autoprueba(sys.argv[2])
    else:
        Ventana().mainloop()
