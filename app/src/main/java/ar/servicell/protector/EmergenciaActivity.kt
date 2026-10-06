package ar.servicell.protector

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Toast

/**
 * 🚨 Pantalla de emergencia: encuentra la app que está poniendo publicidad
 * y ayuda a borrarla con botones grandes.
 */
class EmergenciaActivity : Activity() {

    companion object {
        const val EXTRA_COMPLETO = "completo"
    }

    private lateinit var voz: Voz
    private var completo = false
    private var paqueteEnBorrado: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        voz = Voz(this)
        completo = intent.getBooleanExtra(EXTRA_COMPLETO, false)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        completo = intent.getBooleanExtra(EXTRA_COMPLETO, false)
    }

    override fun onResume() {
        super.onResume()
        if (!Licencia.estaActivada(this)) {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
            return
        }
        revisar()
    }

    override fun onDestroy() {
        voz.apagar()
        super.onDestroy()
    }

    private fun revisar() {
        val col = pantalla()
        col.titulo("🔍 Buscando…")
        col.texto("Esperá un momento, estoy revisando tu celular.")

        // Si volvemos de borrar una app, avisamos si se borró
        val borrada = paqueteEnBorrado
        paqueteEnBorrado = null

        Thread {
            val lista = Analizador.analizar(this)
            val sigueInstalada = borrada != null && Analizador.estaInstalada(this, borrada)
            if (borrada != null && !sigueInstalada) Prefs.registrarBorrada(this, borrada)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                if (borrada != null && !sigueInstalada) {
                    Toast.makeText(this, "✅ ¡Listo! La app se borró.", Toast.LENGTH_LONG).show()
                    voz.decir("Listo. La app se borró.")
                }
                if (completo) mostrarTodas(lista) else mostrarCulpable(lista)
            }
        }.start()
    }

    // ───────────── MODO EMERGENCIA: una sola app, la culpable ─────────────
    private fun mostrarCulpable(lista: List<AppRevisada>) {
        val col = pantalla()
        val app = Analizador.culpable(lista)

        if (app == null) {
            col.titulo("✅ No encontré al culpable")
            col.texto("No veo ninguna app peligrosa abierta recién.")
            if (!Analizador.tienePermisoDeUso(this)) {
                col.texto("⚠️ Falta el permiso «Acceso de uso». Sin él no puedo saber qué app estaba abierta.", color = Colores.ROJO)
                col.boton("Dar acceso de uso", Colores.AZUL) { Ayuda.abrirPermisoDeUso(this) }
            }
            col.texto("Si la publicidad sigue apareciendo:")
            col.boton("🔍 Revisar todo el celular", Colores.AZUL) { completo = true; mostrarTodas(lista) }
            col.botonWhatsApp("Pedir ayuda a ${Config.NEGOCIO}") {
                Ayuda.pedirAyuda(this, "Me aparece publicidad y la app no encontró al culpable.")
            }
            col.boton("😵 No puedo usar el celular", Colores.GRIS) { Ayuda.mostrarModoSeguro(this) }
            col.boton("⬅ Volver", Colores.GRIS) { finish() }
            voz.decir("No encontré al culpable. Si la publicidad sigue, pedí ayuda.")
            return
        }

        col.texto("La publicidad probablemente viene de:", tam = 22f)
        tarjetaApp(col, app, grande = true)

        if (app.esAdmin) {
            col.texto("Esta app se protege para que no la borren. Son 2 pasos:", negrita = true)
            col.boton("1️⃣ Quitarle el permiso", Colores.AMARILLO, Colores.TEXTO) {
                Toast.makeText(this, "Destildá «${app.nombre}» y tocá «Desactivar»", Toast.LENGTH_LONG).show()
                Ayuda.abrirAdministradores(this)
            }
            col.boton("2️⃣ 🗑️ SÍ, BORRARLA", Colores.ROJO) { borrar(app) }
        } else {
            col.boton("🗑️ SÍ, BORRARLA", Colores.ROJO) { borrar(app) }
        }
        col.boton("⏸️ Frenarla un rato", Colores.AMARILLO, Colores.TEXTO) {
            Toast.makeText(this, "Tocá «Forzar detención» o «Desactivar»", Toast.LENGTH_LONG).show()
            Ayuda.abrirDetalles(this, app.paquete)
        }
        col.botonWhatsApp("Avisar a ${Config.NEGOCIO}") {
            Ayuda.pedirAyuda(this, "La app detectó: ${app.nombre} (${app.paquete}).")
        }
        col.boton("No es esa, ver otras", Colores.GRIS) { completo = true; mostrarTodas(lista) }

        voz.decir("La publicidad probablemente viene de ${app.nombre}. ¿La borramos? Tocá el botón rojo.")
    }

    // ───────────── REVISIÓN COMPLETA: todas las sospechosas ─────────────
    private fun mostrarTodas(lista: List<AppRevisada>) {
        val col = pantalla()
        val sospechosas = lista.filter { it.nivel != Nivel.TRANQUILA }

        if (sospechosas.isEmpty()) {
            col.titulo("✅ Todo tranquilo")
            col.texto("No encontré apps sospechosas en tu celular.")
            col.boton("⬅ Volver", Colores.GRIS) { finish() }
            voz.decir("Todo tranquilo. No encontré apps sospechosas.")
            return
        }

        col.titulo("Encontré ${sospechosas.size} para revisar", 26f)
        col.texto("🔴 Peligrosa   🟡 Revisar", tam = 18f, color = Colores.GRIS)
        for (app in sospechosas) {
            tarjetaApp(col, app, grande = false)
        }
        col.boton("⬅ Volver", Colores.GRIS) { finish() }
        voz.decir("Encontré ${sospechosas.size} aplicaciones para revisar.")
    }

    // ───────────── Tarjeta de una app ─────────────
    private fun tarjetaApp(col: LinearLayout, app: AppRevisada, grande: Boolean) {
        val color = if (app.nivel == Nivel.PELIGROSA) Colores.ROJO else Colores.AMARILLO
        val t = col.tarjeta(color)

        val fila = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        app.icono?.let {
            val iv = ImageView(this).apply { setImageDrawable(it) }
            val tam = dp(if (grande) 64 else 48)
            fila.addView(iv, LinearLayout.LayoutParams(tam, tam).apply { rightMargin = dp(14) })
        }
        val nombre = android.widget.TextView(this).apply {
            text = (if (app.nivel == Nivel.PELIGROSA) "🔴 " else "🟡 ") + app.nombre
            textSize = if (grande) 28f else 22f
            setTextColor(Colores.TEXTO)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        fila.addView(nombre, LinearLayout.LayoutParams(0, -2, 1f))
        t.addView(fila, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(12) })

        t.texto(app.motivos.joinToString("\n") { "• $it" }, tam = 18f)

        if (!grande) {
            t.boton("🗑️ Borrar", Colores.ROJO) {
                if (app.esAdmin) {
                    Toast.makeText(this, "Primero destildá «${app.nombre}» en esta lista", Toast.LENGTH_LONG).show()
                    Ayuda.abrirAdministradores(this)
                } else borrar(app)
            }
            t.boton("Es mía, confío en ella", Colores.GRIS) {
                Prefs.agregarConfiable(this, app.paquete)
                Toast.makeText(this, "Listo, no la marco más", Toast.LENGTH_SHORT).show()
                revisar()
            }
        }
    }

    private fun borrar(app: AppRevisada) {
        voz.callar()
        paqueteEnBorrado = app.paquete
        Prefs.marcarParaBorrar(this, app.paquete, app.nombre)
        Ayuda.desinstalar(this, app.paquete)
    }
}
