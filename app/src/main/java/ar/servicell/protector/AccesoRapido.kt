package ar.servicell.protector

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.app.Activity
import android.app.PendingIntent
import android.app.StatusBarManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

/**
 * ⚡ Atajo de emergencia (accesibilidad).
 * Se activa manteniendo apretados los DOS botones de volumen 3 segundos
 * (atajo de accesibilidad de Android). Lo ÚNICO que hace es abrir el menú
 * de apagado, que aparece encima de cualquier publicidad, y explicar en voz
 * alta cómo entrar al modo seguro. No lee ni toca nada de la pantalla.
 */
class AccesoRapidoService : AccessibilityService() {

    private var voz: Voz? = null
    private var callback: AccessibilityButtonController.AccessibilityButtonCallback? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instancia = this
        voz = Voz(this)
        if (Build.VERSION.SDK_INT >= 30) {
            val cb = object : AccessibilityButtonController.AccessibilityButtonCallback() {
                override fun onClicked(controller: AccessibilityButtonController) {
                    abrirMenuApagado()
                }
            }
            accessibilityButtonController.registerAccessibilityButtonCallback(cb)
            callback = cb
        }
    }

    fun abrirMenuApagado() {
        performGlobalAction(GLOBAL_ACTION_POWER_DIALOG)
        val marca = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        val texto = when {
            "xiaomi" in marca || "redmi" in marca || "poco" in marca ->
                "Tocá Apagar. Después prendelo y, cuando aparezca el logo, mantené apretado el botón de bajar volumen."
            "google" in marca ->
                "Mantené el dedo apretado sobre Reiniciar, hasta que aparezca modo seguro. Después tocá Aceptar."
            else ->
                "Mantené el dedo apretado sobre Apagar, hasta que aparezca modo seguro. Después tocalo."
        }
        voz?.decir(texto)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* no se usa: no mira la pantalla */ }
    override fun onInterrupt() {}

    override fun onDestroy() {
        if (Build.VERSION.SDK_INT >= 30) {
            callback?.let { accessibilityButtonController.unregisterAccessibilityButtonCallback(it) }
        }
        voz?.apagar()
        if (instancia === this) instancia = null
        super.onDestroy()
    }

    companion object {
        @Volatile var instancia: AccesoRapidoService? = null

        fun estaActivo(c: Context): Boolean {
            val activos = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES) ?: return false
            val yo = ComponentName(c, AccesoRapidoService::class.java)
            return activos.split(':').any { ComponentName.unflattenFromString(it) == yo }
        }
    }
}

/** Botón "⏻ Menú de apagado" de la notificación. */
class ApagadoReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val s = AccesoRapidoService.instancia
        if (s != null) s.abrirMenuApagado()
        else Toast.makeText(c, "Activá el atajo de emergencia en la app", Toast.LENGTH_LONG).show()
    }
}

/** Si el cliente desliza la notificación para borrarla, se vuelve a poner. */
class ReponerBotonReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        if (!Licencia.estaActivada(c)) return
        try {
            c.getSystemService(android.app.NotificationManager::class.java)
                .notify(Alertas.ID_BOTON, Alertas.notificacionBoton(c))
        } catch (e: SecurityException) { }
    }
}

/** 🚨 Botón en los accesos rápidos (al lado del Wi-Fi y la linterna). */
class EmergenciaTileService : TileService() {
    override fun onStartListening() {
        super.onStartListening()
        qsTile?.apply {
            label = "Emergencia"
            if (Build.VERSION.SDK_INT >= 29) subtitle = "Publicidad"
            state = Tile.STATE_ACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val i = Intent(this, EmergenciaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= 34) {
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, i, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(i)
        }
    }
}

/** ⚡ Pantalla para configurar los atajos de emergencia. */
class AtajoActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onResume() {
        super.onResume()
        val col = pantalla()
        col.logo()
        col.titulo("⚡ Atajos de emergencia", 28f)
        col.texto("Para cuando la publicidad tapa todo y no se puede usar el celular.")

        // ───── 1) Botón de volumen ─────
        val activo = AccesoRapidoService.estaActivo(this)
        val t = col.tarjeta(if (activo) Colores.VERDE else Colores.AMARILLO)
        t.texto("🔊 Los dos botones de volumen", tam = 22f, negrita = true)
        t.texto(if (activo) "✅ Activado" else "❌ Todavía no está activado", negrita = true,
            color = if (activo) Colores.VERDE else Colores.ROJO)
        t.texto(
            "Mantené apretados los DOS botones de volumen 3 segundos: se abre el menú de apagado " +
                "y la app te dice en voz alta cómo entrar al modo seguro.", tam = 18f
        )
        if (!activo) {
            t.texto(
                """
                Cómo activarlo (se hace una sola vez):
                1. Tocá el botón de abajo.
                2. Buscá «Servicell Protector» y entrá.
                3. Activá «Acceso directo» o «Atajo».
                4. Si te pide elegir, marcá «Mantener presionadas las teclas de volumen».

                Si dice que es un «ajuste restringido», hacé lo mismo que con el otro permiso: Ajustes → Aplicaciones → Servicell Protector → ⋮ → «Permitir ajustes restringidos».
                """.trimIndent(), tam = 17f
            )
            t.boton("Abrir accesibilidad", Colores.AZUL) {
                try { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) } catch (e: Exception) { }
            }
        } else {
            t.boton("Probar ahora", Colores.AZUL) {
                AccesoRapidoService.instancia?.abrirMenuApagado()
                    ?: Toast.makeText(this, "Probá apretando los dos botones de volumen", Toast.LENGTH_LONG).show()
            }
        }

        // ───── 2) Accesos rápidos ─────
        val q = col.tarjeta(Colores.AZUL)
        q.texto("🔲 Botón en los accesos rápidos", tam = 22f, negrita = true)
        q.texto("Un botón «Emergencia» en el panel de arriba, al lado del Wi-Fi y la linterna.", tam = 18f)
        if (Build.VERSION.SDK_INT >= 33) {
            q.boton("Agregar el botón", Colores.AZUL) { agregarTile() }
        } else {
            q.texto(
                "Bajá la barra de arriba dos veces, tocá el lápiz ✏️ y arrastrá «Emergencia» a los accesos rápidos.",
                tam = 17f
            )
        }

        col.texto("🔒 Este permiso se usa SOLO para abrir el menú de apagado. La app no lee ni toca nada de tu pantalla.",
            tam = 16f, color = Colores.GRIS)
        col.boton("⬅ Volver", Colores.GRIS) { finish() }
    }

    private fun agregarTile() {
        if (Build.VERSION.SDK_INT < 33) return
        try {
            getSystemService(StatusBarManager::class.java).requestAddTileService(
                ComponentName(this, EmergenciaTileService::class.java),
                "Emergencia",
                Icon.createWithResource(this, R.drawable.ic_notif),
                mainExecutor
            ) { resultado ->
                val ok = resultado == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                    resultado == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
                Toast.makeText(this, if (ok) "✅ Listo, ya está el botón" else "No se agregó", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Agregalo a mano desde el lápiz ✏️ de los accesos rápidos", Toast.LENGTH_LONG).show()
        }
    }
}
