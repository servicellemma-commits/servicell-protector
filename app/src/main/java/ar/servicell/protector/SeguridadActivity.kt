package ar.servicell.protector

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast

/**
 * 🔐 Seguridad de mis cuentas: guía paso a paso para
 * 1) WhatsApp: activar la verificación en dos pasos.
 * 2) Google: hacer la revisión de seguridad de la cuenta.
 * La app no ve ni toca las cuentas: solo explica y abre la app correspondiente.
 */
class SeguridadActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mostrar()
    }

    private fun mostrar() {
        val col = pantalla()
        col.logo()
        col.titulo("🔐 Seguridad de mis cuentas", 28f)
        col.texto("Dos cosas simples para que no te roben el WhatsApp ni tu cuenta de Google.")

        // ───── 1) WhatsApp ─────
        val w = col.tarjeta(Colores.VERDE)
        w.texto("💬 WhatsApp: verificación en dos pasos", tam = 22f, negrita = true)
        w.texto("Así, aunque alguien consiga el código que te llega por mensaje, no puede quedarse con tu WhatsApp.", tam = 18f)
        w.texto(
            """
            1. Abrí WhatsApp.
            2. Tocá los 3 puntitos ⋮ → «Ajustes».
            3. Entrá a «Cuenta» → «Verificación en dos pasos».
            4. Tocá «Activar» y elegí un PIN de 6 números que te acuerdes.
            5. Cuando te pida un correo, ponelo (sirve si te olvidás el PIN).
            """.trimIndent(), tam = 19f
        )
        w.boton("Abrir WhatsApp", Colores.VERDE) { abrirWhatsApp() }
        casilla(w, "whatsapp_2pasos", "✅ Ya lo activé")

        // ───── 2) Google ─────
        val g = col.tarjeta(Colores.AZUL)
        g.texto("📧 Cuenta de Google (Gmail)", tam = 22f, negrita = true)
        g.texto("Google revisa tu cuenta y te dice qué conviene arreglar.", tam = 18f)
        g.texto(
            """
            1. Tocá el botón de abajo.
            2. Entrá con tu cuenta si te lo pide.
            3. Seguí lo que marque en amarillo o rojo, por ejemplo:
               • Agregar un teléfono de recuperación.
               • Cerrar sesión en celulares que ya no usás.
               • Cambiar la contraseña si es débil.
            """.trimIndent(), tam = 19f
        )
        g.boton("Abrir revisión de Google", Colores.AZUL) {
            abrir(Uri.parse("https://myaccount.google.com/security-checkup"))
        }
        casilla(g, "google_revision", "✅ Ya lo revisé")

        col.boton("💬 Pedir ayuda a ${Config.NEGOCIO}", Colores.VERDE) {
            Ayuda.pedirAyuda(this, "Quiero ayuda para revisar la seguridad de mi WhatsApp y mi cuenta de Google.")
        }
        col.boton("⬅ Volver", Colores.GRIS) { finish() }
    }

    private fun casilla(t: LinearLayout, clave: String, texto: String) {
        val prefs = getSharedPreferences("servicell", MODE_PRIVATE)
        val cb = CheckBox(this).apply {
            this.text = texto
            textSize = 20f
            isChecked = prefs.getBoolean(clave, false)
            setOnCheckedChangeListener { _, marcado -> prefs.edit().putBoolean(clave, marcado).apply() }
        }
        t.addView(cb, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })
    }

    private fun abrirWhatsApp() {
        val paquetes = listOf("com.whatsapp", "com.whatsapp.w4b")
        for (p in paquetes) {
            val i = packageManager.getLaunchIntentForPackage(p)
            if (i != null) { startActivity(i); return }
        }
        Toast.makeText(this, "No encontré WhatsApp en este celular", Toast.LENGTH_LONG).show()
    }

    private fun abrir(uri: Uri) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, uri))
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo abrir el navegador", Toast.LENGTH_LONG).show()
        }
    }
}
