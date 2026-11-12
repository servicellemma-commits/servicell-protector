package ar.servicell.protector

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast

/**
 * 🔔 "Me aparecen avisos raros": muchas veces no es un virus, sino una página web
 * a la que, sin querer, se le dio permiso de mandar notificaciones
 * ("¡Ganaste un premio!", "Tu celular tiene virus").
 *
 * Chrome y los demás navegadores guardan una lista con cada página que tiene
 * permiso. Esta pantalla explica cómo reconocerlas y abre DIRECTO esa lista en
 * cada navegador instalado, para que la persona apague las que no conoce.
 * La app no ve ni toca esa lista: solo la abre.
 */
class AvisosWebActivity : Activity() {

    private lateinit var voz: Voz

    // Navegadores más usados: paquete → nombre
    private val NAVEGADORES = linkedMapOf(
        "com.android.chrome" to "Chrome",
        "com.sec.android.app.sbrowser" to "Samsung Internet",
        "com.opera.browser" to "Opera",
        "com.opera.mini.native" to "Opera Mini",
        "org.mozilla.firefox" to "Firefox",
        "com.microsoft.emmx" to "Edge",
        "com.brave.browser" to "Brave",
        "com.mi.globalbrowser" to "Mi Browser",
        "com.huawei.browser" to "Huawei Browser",
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
    }

    override fun onResume() {
        super.onResume()
        mostrar()
    }

    override fun onDestroy() {
        voz.apagar()
        super.onDestroy()
    }

    private fun mostrar() {
        val col = pantalla()
        col.logo()
        col.titulo("🔔 Avisos raros de páginas web", 26f)
        col.texto(
            "Muchas veces la «publicidad» no es un virus: es una página web a la que, " +
                "sin querer, se le tocó «Permitir» y ahora manda avisos."
        )

        // ───── Cómo reconocerlos ─────
        val r = col.tarjeta(Colores.AMARILLO)
        r.texto("👀 Cómo darte cuenta", tam = 21f, negrita = true)
        r.texto(
            """
            • El aviso dice cosas como «¡Ganaste!», «Tu celular tiene virus» o «Reclamá tu premio».
            • Debajo aparece el nombre de una página web (por ejemplo «premios-ya.com») y el de tu navegador (Chrome).
            • Aparece aunque no tengas ninguna app nueva.
            """.trimIndent(), tam = 18f
        )

        // ───── Arreglarlo en cada navegador instalado ─────
        val instalados = NAVEGADORES.filterKeys { iconoDeApp(this, it) != null }
        if (instalados.isEmpty()) {
            col.texto("No encontré navegadores instalados.", color = Colores.GRIS)
        } else {
            val t = col.tarjeta(Colores.VERDE)
            t.texto("✅ Cómo sacarlos", tam = 21f, negrita = true)
            t.texto(
                """
                1. Tocá el botón de tu navegador.
                2. Vas a ver una lista con nombres de páginas web.
                3. Apagá todas las que no conozcas.
                """.trimIndent(), tam = 19f
            )
            for ((paquete, nombre) in instalados) {
                t.boton("🔔 Abrir avisos de $nombre", Colores.AZUL) { abrirAvisos(paquete, nombre) }
            }
            t.texto(
                "¿No ves la lista? Abrí ${instalados.values.first()} → ⋮ → «Configuración» → " +
                    "«Configuración de sitios» → «Notificaciones», y bloqueá las páginas que no conozcas.",
                tam = 16f, color = Colores.GRIS
            )
        }

        col.botonWhatsApp("Pedir ayuda a ${Config.NEGOCIO}") {
            Ayuda.pedirAyuda(this, "Me aparecen avisos raros, creo que de una página web.")
        }
        col.boton("⬅ Volver", Colores.GRIS) { finish() }

        voz.decir(
            "Muchas veces la publicidad no es un virus, sino una página web que manda avisos. " +
                "Tocá el botón de tu navegador y apagá las páginas que no conozcas."
        )
    }

    /** Abre los avisos de ese navegador: ahí Android muestra una lista con cada página web. */
    private fun abrirAvisos(paquete: String, nombre: String) {
        voz.callar()
        val avisos = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
            .putExtra(Settings.EXTRA_APP_PACKAGE, paquete)
        try {
            startActivity(avisos)
            Toast.makeText(this, "Apagá las páginas que no conozcas", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$paquete")))
                Toast.makeText(this, "Entrá a «Notificaciones» de $nombre", Toast.LENGTH_LONG).show()
            } catch (e2: Exception) {
                Toast.makeText(this, "No se pudo abrir $nombre", Toast.LENGTH_LONG).show()
            }
        }
    }
}
