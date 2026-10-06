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

    override fun onResume() {
        super.onResume()
        mostrar()
    }

    private fun mostrar() {
        when {
            !Licencia.estaActivada(this) -> pasoActivacion()
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
        col.titulo("🔐 Activar la app")
        col.texto("Esta app es de ${Config.NEGOCIO} y funciona solo en este celular.")
        col.texto("Código de este celular:", negrita = true)
        col.texto(codigo, tam = 40f, negrita = true, color = Colores.AZUL, centrado = true)
        col.boton("📋 Copiar código", Colores.GRIS) {
            val cm = getSystemService(ClipboardManager::class.java)
            cm.setPrimaryClip(ClipData.newPlainText("codigo", codigo))
            Toast.makeText(this, "Código copiado", Toast.LENGTH_SHORT).show()
        }
        col.botonWhatsApp("Mandar mi código a ${Config.NEGOCIO}") { Ayuda.mandarCodigo(this) }
        col.texto("Te respondemos por WhatsApp con tu código de activación.", tam = 17f, color = Colores.GRIS)
        col.espacio()
        col.texto("Código de activación (te lo da ${Config.NEGOCIO}):", negrita = true)
        val campo = campoTexto(col, "XXXX-XXXX", mayusculas = true)
        col.boton("✅ ACTIVAR", Colores.VERDE) {
            val ingresado = campo.text.toString()
            if (Licencia.esValido(this, ingresado)) {
                Prefs.setActivacion(this, ingresado)
                Toast.makeText(this, "¡Activada!", Toast.LENGTH_SHORT).show()
                mostrar()
            } else {
                Toast.makeText(this, "El código no es correcto para este celular", Toast.LENGTH_LONG).show()
            }
        }
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
        col.encabezado("Hola, ${Prefs.nombre(this)} 👋", protegido)

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
            Triple("📸", "Seguinos en Instagram") { Ayuda.abrirInstagram(this) },
            Triple("🔒", "Privacidad") { Ayuda.mostrarPrivacidad(this) },
        ))

        col.botonWhatsApp("Pedir ayuda a ${Config.NEGOCIO}") { Ayuda.pedirAyuda(this) }

        col.espacio(10)
        col.texto("Código del celular: ${Licencia.codigoCelular(this)}", tam = 14f, color = Colores.GRIS, centrado = true)
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
