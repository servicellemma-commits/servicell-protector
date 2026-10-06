package ar.servicell.protector

import android.Manifest
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast

/**
 * Pantalla principal. Si falta configurar algo, muestra los pasos en orden:
 * 1) Activación  2) Nombre y privacidad  3) Permisos  → Inicio
 */
class MainActivity : Activity() {

    /** true cuando el cliente tocó "Ingresar código nuevo" para renovar. */
    private var renovando = false
    private val formatoFecha = java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy")

    override fun onResume() {
        super.onResume()
        mostrar()
    }

    private fun mostrar() {
        when {
            renovando || !Licencia.estaActivada(this) -> pasoActivacion()
            Prefs.nombre(this).isBlank() || !Prefs.privacidadAceptada(this) -> pasoNombre()
            !Prefs.permisosVistos(this) -> pasoPermisos()
            else -> inicio()
        }
    }

    // ───────────── 1) ACTIVACIÓN ─────────────
    private fun pasoActivacion() {
        val col = pantalla()
        val codigo = Licencia.codigoCelular(this)
        col.logo()
        val estado = Licencia.estado(this)
        val vencida = estado is Licencia.Estado.Hasta && !Licencia.estaActivada(this)
        when {
            vencida -> {
                col.titulo("⏰ Tu protección venció")
                col.texto("Venció el ${(estado as Licencia.Estado.Hasta).vence.format(formatoFecha)}. " +
                    "Renovala con ${Config.NEGOCIO} para seguir protegido.")
            }
            renovando -> {
                col.titulo("🔄 Renovar la protección")
                col.texto("Pedí tu código nuevo a ${Config.NEGOCIO} y escribilo abajo.")
            }
            else -> {
                col.titulo("🔐 Activar la app")
                col.texto("Esta app es de ${Config.NEGOCIO} y funciona solo en este celular.")
            }
        }
        col.texto("Código de este celular:", negrita = true)
        col.texto(codigo, tam = 40f, negrita = true, color = Colores.AZUL, centrado = true)
        col.boton("📋 Copiar código", Colores.GRIS) {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("codigo", codigo))
            Toast.makeText(this, "Código copiado", Toast.LENGTH_SHORT).show()
        }
        val renovar = vencida || renovando
        col.botonWhatsApp(if (renovar) "Renovar con ${Config.NEGOCIO}" else "Mandar mi código a ${Config.NEGOCIO}") {
            Ayuda.mandarCodigo(this, renovar)
        }
        col.texto("Te respondemos por WhatsApp con tu código de activación.", tam = 17f, color = Colores.GRIS)
        col.espacio()
        col.texto("Código de activación (te lo da ${Config.NEGOCIO}):", negrita = true)
        val campo = campoTexto(col, "XXXX-XXXX-XXXX", mayusculas = true)
        col.boton("✅ ACTIVAR", Colores.VERDE) {
            val ingresado = campo.text.toString()
            val problema = Licencia.problemaCon(this, ingresado)
            if (problema == null) {
                Prefs.setActivacion(this, ingresado)
                renovando = false
                val e = Licencia.estado(this)
                val msg = if (e is Licencia.Estado.Hasta) "¡Activada hasta el ${e.vence.format(formatoFecha)}!" else "¡Activada!"
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                ProteccionService.iniciar(this)
                mostrar()
            } else {
                Toast.makeText(this, problema, Toast.LENGTH_LONG).show()
            }
        }
        if (renovando) col.boton("⬅ Volver", Colores.GRIS) { renovando = false; mostrar() }
    }

    // ───────────── 2) NOMBRE Y PRIVACIDAD ─────────────
    private fun pasoNombre() {
        val col = pantalla()
        col.logo()
        col.titulo("👋 ¡Hola!")
        col.texto("¿Cómo te llamás?")
        val campo = campoTexto(col, "Tu nombre", mayusculas = false)
        campo.setText(Prefs.nombre(this))
        col.espacio(4)
        col.texto("🔒 Tu privacidad", tam = 24f, negrita = true)
        col.texto(Ayuda.TEXTO_PRIVACIDAD, tam = 19f)
        col.boton("Entendido, seguir ➜", Colores.VERDE) {
            val nombre = campo.text.toString().trim()
            if (nombre.isBlank()) {
                Toast.makeText(this, "Escribí tu nombre", Toast.LENGTH_SHORT).show()
            } else {
                Prefs.setNombre(this, nombre)
                Prefs.setPrivacidadAceptada(this)
                mostrar()
            }
        }
    }

    // ───────────── 3) PERMISOS ─────────────
    private fun pasoPermisos() {
        val col = pantalla()
        col.logo()
        col.titulo("⚙️ Último paso")
        col.texto("Hay que dar 2 permisos para que la app pueda ayudarte. Se hace una sola vez.")
        mostrarPermisos(col)
        col.boton("Listo ➜", Colores.VERDE) {
            Prefs.setPermisosVistos(this)
            mostrar()
        }
    }

    private fun mostrarPermisos(col: LinearLayout) {
        val notif = tieneNotificaciones()
        val uso = Analizador.tienePermisoDeUso(this)

        col.texto(
            (if (notif) "✅" else "❌") + " Notificaciones: para el botón de emergencia y los avisos.",
            tam = 19f
        )
        if (!notif) col.boton("Dar permiso de notificaciones", Colores.AZUL) { pedirNotificaciones() }

        col.texto(
            (if (uso) "✅" else "❌") + " Acceso de uso: para saber qué app estaba abierta cuando apareció la publicidad.",
            tam = 19f
        )
        if (!uso) {
            col.texto("Buscá «Servicell Protector» en la lista y activalo.", tam = 17f, color = Colores.GRIS)
            col.boton("Dar acceso de uso", Colores.AZUL) { Ayuda.abrirPermisoDeUso(this) }
        }
    }

    // ───────────── INICIO ─────────────
    private fun inicio() {
        ProteccionService.iniciar(this)
        val col = pantalla()
        val protegido = tieneNotificaciones() && Analizador.tienePermisoDeUso(this)
        val lic = Licencia.estado(this)
        val hasta = if (lic is Licencia.Estado.Hasta) " · hasta ${lic.vence.format(formatoFecha)}" else ""
        col.encabezado("Hola, ${Prefs.nombre(this)} 👋", protegido, hasta)

        if (lic is Licencia.Estado.Hasta && lic.dias <= 7) {
            val t = col.tarjeta(if (lic.dias < 0) Colores.ROJO else Colores.AMARILLO)
            t.texto(
                when {
                    lic.dias < 0 -> "⚠️ Tu protección venció. Tenés ${Licencia.DIAS_GRACIA + lic.dias + 1} día(s) para renovarla."
                    lic.dias == 0 -> "⏰ Tu protección vence HOY."
                    lic.dias == 1 -> "⏰ Tu protección vence MAÑANA."
                    else -> "⏰ Tu protección vence en ${lic.dias} días (${lic.vence.format(formatoFecha)})."
                }, negrita = true
            )
            t.botonWhatsApp("Renovar con ${Config.NEGOCIO}") { Ayuda.mandarCodigo(this, true) }
            t.boton("Ingresar código nuevo", Colores.AZUL) { renovando = true; mostrar() }
        }

        if (!protegido) {
            val t = col.tarjeta(Colores.AMARILLO)
            t.texto("⚠️ Falta un permiso para que la app funcione bien:", negrita = true)
            mostrarPermisos(t)
        }

        col.tarjetaEmergencia { startActivity(Intent(this, EmergenciaActivity::class.java)) }

        col.cuadricula(listOf(
            Triple("🔍", "Revisar el celular") {
                startActivity(Intent(this, EmergenciaActivity::class.java).putExtra(EmergenciaActivity.EXTRA_COMPLETO, true))
            },
            Triple("🔐", "Seguridad de cuentas") { startActivity(Intent(this, SeguridadActivity::class.java)) },
            Triple("📋", "Apps que borré") { startActivity(Intent(this, HistorialActivity::class.java)) },
            Triple("😵", "No puedo usar el celular") { Ayuda.mostrarModoSeguro(this) },
            Triple("⚡", "Atajos de emergencia") { startActivity(Intent(this, AtajoActivity::class.java)) },
            Triple("📸", "Seguinos en Instagram") { Ayuda.abrirInstagram(this) },
            Triple("🔒", "Privacidad") { Ayuda.mostrarPrivacidad(this) },
        ))

        col.botonWhatsApp("Pedir ayuda a ${Config.NEGOCIO}") { Ayuda.pedirAyuda(this) }

        chequearModoSeguro()

        col.espacio(10)
        col.texto("Código del celular: ${Licencia.codigoCelular(this)}", tam = 14f, color = Colores.GRIS, centrado = true)
    }

    /** Al volver del modo seguro: ¿se pudo borrar la app anotada? */
    private fun chequearModoSeguro() {
        val (paquete, nombre) = Prefs.modoSeguroPendiente(this) ?: return
        if (!Analizador.estaInstalada(this, paquete)) {
            Prefs.registrarBorrada(this, paquete)
            Prefs.limpiarModoSeguro(this)
            android.app.AlertDialog.Builder(this)
                .setTitle("✅ ¡Muy bien!")
                .setMessage("Se borró «$nombre». Ya no debería aparecer más esa publicidad.")
                .setPositiveButton("¡Genial!", null)
                .show()
            Voz(this).decir("Muy bien. Se borró $nombre.")
        } else {
            android.app.AlertDialog.Builder(this)
                .setTitle("¿Pudiste borrar «$nombre»?")
                .setMessage("Todavía está instalada en tu celular.")
                .setPositiveButton("🗑️ Borrarla ahora") { _, _ -> Ayuda.desinstalar(this, paquete) }
                .setNeutralButton("Ver los pasos") { _, _ -> Ayuda.mostrarModoSeguro(this, paquete, nombre) }
                .setNegativeButton("Ya no hace falta") { _, _ -> Prefs.limpiarModoSeguro(this) }
                .show()
        }
    }

    // ───────────── AUXILIARES ─────────────
    private fun campoTexto(col: LinearLayout, pista: String, mayusculas: Boolean): EditText {
        val e = EditText(this).apply {
            hint = pista
            textSize = 26f
            isSingleLine = true
            inputType = if (mayusculas)
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            else
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
        }
        col.addView(e, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(14) })
        return e
    }

    private fun tieneNotificaciones(): Boolean =
        Build.VERSION.SDK_INT < 33 ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun pedirNotificaciones() {
        if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        mostrar()
    }
}
