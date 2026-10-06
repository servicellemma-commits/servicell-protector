package ar.servicell.protector

import android.app.Activity
import android.os.Build
import android.os.Bundle
import android.view.Gravity

/**
 * 😵 Guía de "Modo seguro", un paso por pantalla y leída en voz alta.
 * - Muestra los pasos de la marca del celular (Samsung, Motorola, Xiaomi…).
 * - Antes de empezar, recuerda en grande qué app hay que borrar.
 * - Al volver al modo normal, la pantalla principal pregunta si se pudo borrar.
 *
 * Una app no puede reiniciar el celular ni funciona dentro del modo seguro,
 * por eso esto guía a la persona para que lo haga ella.
 */
class ModoSeguroActivity : Activity() {

    companion object {
        const val EXTRA_PAQUETE = "paquete"
        const val EXTRA_NOMBRE = "nombre"
    }

    private lateinit var voz: Voz
    private var pasos: List<Paso> = emptyList()
    private var actual = 0

    class Paso(val titulo: String, val texto: String, val voz: String = texto)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        actual = savedInstanceState?.getInt("actual") ?: 0

        val paquete = intent.getStringExtra(EXTRA_PAQUETE)
        val nombre = intent.getStringExtra(EXTRA_NOMBRE)
        if (paquete != null && nombre != null) {
            Prefs.setModoSeguroPendiente(this, paquete, nombre)
            Prefs.marcarParaBorrar(this, paquete, nombre)
        }

        // Si no sabemos cuál es, buscamos las más sospechosas para anotarlas
        if (nombre == null) {
            val col = pantalla()
            col.titulo("Un momento…")
            Thread {
                val sospechosas = try {
                    Analizador.analizar(this).filter { it.nivel != Nivel.TRANQUILA }.take(3)
                } catch (e: Exception) { emptyList() }
                runOnUiThread {
                    if (isFinishing) return@runOnUiThread
                    sospechosas.firstOrNull()?.let {
                        Prefs.setModoSeguroPendiente(this, it.paquete, it.nombre)
                        Prefs.marcarParaBorrar(this, it.paquete, it.nombre)
                    }
                    pasos = armarPasos(sospechosas.map { it.nombre })
                    mostrar()
                }
            }.start()
        } else {
            pasos = armarPasos(listOf(nombre))
            mostrar()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("actual", actual)
    }

    override fun onDestroy() {
        voz.apagar()
        super.onDestroy()
    }

    // ───────────── Pantalla de un paso ─────────────
    private fun mostrar() {
        val paso = pasos[actual]
        val col = pantalla()
        col.texto("Paso ${actual + 1} de ${pasos.size}", tam = 18f, color = Colores.GRIS, centrado = true)
        col.titulo(paso.titulo, 28f).gravity = Gravity.CENTER_HORIZONTAL
        val t = col.tarjeta(Colores.AZUL)
        t.texto(paso.texto, tam = 24f)

        col.espacio(8)
        if (actual < pasos.size - 1) {
            col.boton("Siguiente ▶", Colores.VERDE) { actual++; mostrar() }
        } else {
            col.boton("✅ Entendido", Colores.VERDE) { finish() }
        }
        if (actual > 0) col.boton("◀ Anterior", Colores.GRIS) { actual--; mostrar() }
        col.boton("🔊 Escuchar de nuevo", Colores.GRIS) { voz.decir(paso.voz) }
        col.botonWhatsApp("Pedir ayuda a ${Config.NEGOCIO}") {
            Ayuda.pedirAyuda(this, "No me sale entrar al modo seguro.")
        }
        voz.decir(paso.voz)
    }

    // ───────────── Armado de los pasos ─────────────
    private fun armarPasos(apps: List<String>): List<Paso> {
        val lista = mutableListOf<Paso>()

        // 1) Recordatorio: qué hay que borrar
        lista += if (apps.isNotEmpty()) {
            val nombres = apps.joinToString("\n") { "👉 $it" }
            Paso(
                "📝 Primero, anotá esto",
                "Cuando estés en modo seguro, tenés que borrar:\n\n$nombres\n\nSi podés, sacale una foto a esta pantalla.",
                "Primero, anotá esto. Cuando estés en modo seguro, tenés que borrar: ${apps.joinToString(", ")}. " +
                    "Si podés, sacale una foto a esta pantalla."
            )
        } else {
            Paso(
                "📝 Primero, tené en cuenta",
                "En modo seguro vas a buscar una app que no reconozcas, por ejemplo con nombres como «Limpiador», «Batería» o «Actualización».",
                "Primero, tené en cuenta: en modo seguro vas a buscar una app que no reconozcas, " +
                    "por ejemplo con nombres como limpiador, batería o actualización."
            )
        }

        // 2) Entrar al modo seguro, según la marca
        lista += pasosDeLaMarca()

        // 3) Borrar en modo seguro
        lista += Paso(
            "🗑️ Ya en modo seguro",
            "Vas a ver «Modo seguro» abajo en la pantalla. La publicidad ya no aparece.\n\n" +
                "1. Abrí «Ajustes».\n2. Entrá a «Aplicaciones».\n3. Buscá la app que anotaste.\n4. Tocá «Desinstalar» y «Aceptar».",
            "Ya en modo seguro, vas a ver las palabras modo seguro abajo en la pantalla, y la publicidad ya no aparece. " +
                "Abrí Ajustes. Entrá a Aplicaciones. Buscá la app que anotaste. Tocá Desinstalar, y después Aceptar."
        )

        // 4) Salir del modo seguro
        lista += Paso(
            "🔄 Volver a la normalidad",
            "Reiniciá el celular de forma normal:\nmantené apretado el botón de apagado y tocá «Reiniciar».\n\n" +
                "Cuando vuelva a prender, abrí esta app y te confirmo si se borró. ✅",
            "Para volver a la normalidad, reiniciá el celular de forma normal: mantené apretado el botón de apagado y tocá Reiniciar. " +
                "Cuando vuelva a prender, abrí esta app y te confirmo si se borró."
        )
        return lista
    }

    private fun pasosDeLaMarca(): List<Paso> {
        val marca = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        return when {
            "samsung" in marca -> listOf(
                Paso("📱 Samsung: paso 1",
                    "Mantené apretados al mismo tiempo el botón de apagado y el de bajar volumen, hasta que aparezca el menú de apagado.\n\n(También podés bajar la barra de arriba y tocar el botón ⏻)",
                    "Mantené apretados al mismo tiempo el botón de apagado y el botón de bajar volumen, hasta que aparezca el menú de apagado."),
                Paso("📱 Samsung: paso 2",
                    "Mantené el dedo apretado sobre «Apagar» hasta que aparezca «Modo seguro».",
                    "Mantené el dedo apretado sobre la palabra Apagar, hasta que aparezca Modo seguro."),
                Paso("📱 Samsung: paso 3",
                    "Tocá «Modo seguro». El celular se reinicia solo. Esperá a que prenda.",
                    "Tocá Modo seguro. El celular se reinicia solo. Esperá a que prenda."),
            )
            "xiaomi" in marca || "redmi" in marca || "poco" in marca -> listOf(
                Paso("📱 Xiaomi: paso 1",
                    "Apagá el celular por completo:\nmantené apretado el botón de apagado y tocá «Apagar».",
                    "Apagá el celular por completo. Mantené apretado el botón de apagado y tocá Apagar."),
                Paso("📱 Xiaomi: paso 2",
                    "Prendelo con el botón de apagado. Apenas aparezca el logo, mantené apretado el botón de BAJAR volumen y no lo sueltes.",
                    "Prendelo con el botón de apagado. Apenas aparezca el logo, mantené apretado el botón de bajar volumen, y no lo sueltes."),
                Paso("📱 Xiaomi: paso 3",
                    "Soltalo cuando termine de prender. Abajo vas a ver «Modo seguro».\n\n(Si no aparece, probá de nuevo o pedinos ayuda)",
                    "Soltalo cuando termine de prender. Abajo vas a ver Modo seguro. Si no aparece, probá de nuevo o pedinos ayuda."),
            )
            "huawei" in marca || "honor" in marca || "oppo" in marca || "realme" in marca -> listOf(
                Paso("📱 Paso 1",
                    "Apagá el celular por completo:\nmantené apretado el botón de apagado y tocá «Apagar».",
                    "Apagá el celular por completo. Mantené apretado el botón de apagado y tocá Apagar."),
                Paso("📱 Paso 2",
                    "Prendelo y, cuando aparezca el logo de la marca, mantené apretado el botón de BAJAR volumen hasta que termine de prender.",
                    "Prendelo, y cuando aparezca el logo de la marca, mantené apretado el botón de bajar volumen hasta que termine de prender."),
                Paso("📱 Paso 3",
                    "Abajo vas a ver «Modo seguro».\n\n(Si no aparece, probá de nuevo o pedinos ayuda)",
                    "Abajo vas a ver Modo seguro. Si no aparece, probá de nuevo o pedinos ayuda."),
            )
            "google" in marca -> listOf(
                Paso("📱 Pixel: paso 1",
                    "Mantené apretados el botón de apagado y el de SUBIR volumen hasta que aparezca el menú.",
                    "Mantené apretados el botón de apagado y el botón de subir volumen, hasta que aparezca el menú."),
                Paso("📱 Pixel: paso 2",
                    "Mantené el dedo apretado sobre «Reiniciar» hasta que aparezca «Reiniciar en modo seguro».",
                    "Mantené el dedo apretado sobre Reiniciar, hasta que aparezca Reiniciar en modo seguro."),
                Paso("📱 Pixel: paso 3",
                    "Tocá «Aceptar». El celular se reinicia solo.",
                    "Tocá Aceptar. El celular se reinicia solo."),
            )
            else -> listOf( // Motorola y la mayoría de las marcas
                Paso("📱 Paso 1",
                    "Mantené apretado el botón de apagado hasta que aparezca el menú.",
                    "Mantené apretado el botón de apagado, hasta que aparezca el menú."),
                Paso("📱 Paso 2",
                    "Mantené el dedo apretado sobre «Apagar» hasta que aparezca «Reiniciar en modo seguro».",
                    "Mantené el dedo apretado sobre la palabra Apagar, hasta que aparezca Reiniciar en modo seguro."),
                Paso("📱 Paso 3",
                    "Tocá «Aceptar». El celular se reinicia solo.\n\n(Si no aparece esa opción, pedinos ayuda)",
                    "Tocá Aceptar. El celular se reinicia solo. Si no aparece esa opción, pedinos ayuda."),
            )
        }
    }
}
