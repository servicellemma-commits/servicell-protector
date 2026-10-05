package ar.servicell.protector

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.net.Uri
import android.os.Build
import android.os.IBinder

/**
 * 🛡️ Servicio que deja el "botón de emergencia" fijo en la barra de
 * notificaciones y avisa cuando se instala una app que no vino de la tienda.
 */
class ProteccionService : Service() {

    private val receptor = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            Alertas.revisarAppsNuevas(c)
        }
    }

    override fun onCreate() {
        super.onCreate()
        Alertas.crearCanales(this)
        val n = Alertas.notificacionBoton(this)
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(Alertas.ID_BOTON, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(Alertas.ID_BOTON, n)
        }
        val filtro = IntentFilter(Intent.ACTION_PACKAGE_ADDED).apply { addDataScheme("package") }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(receptor, filtro, Context.RECEIVER_EXPORTED)
        else registerReceiver(receptor, filtro)

        // Por si se instaló algo mientras el servicio estaba apagado
        Alertas.revisarAppsNuevas(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Vuelve a mostrar el botón (por si recién se dio el permiso de notificaciones)
        try {
            getSystemService(NotificationManager::class.java).notify(Alertas.ID_BOTON, Alertas.notificacionBoton(this))
        } catch (e: SecurityException) { }
        return START_STICKY
    }

    override fun onDestroy() {
        try { unregisterReceiver(receptor) } catch (e: Exception) { }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun iniciar(c: Context) {
            if (!Licencia.estaActivada(c)) return
            try {
                c.startForegroundService(Intent(c, ProteccionService::class.java))
            } catch (e: Exception) { }
        }
    }
}

object Alertas {
    const val ID_BOTON = 1
    private const val CANAL_BOTON = "boton"
    private const val CANAL_ALERTA = "alerta"
    const val EXTRA_PAQUETE = "paquete"

    fun crearCanales(c: Context) {
        val nm = c.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL_BOTON, "Botón de emergencia", NotificationManager.IMPORTANCE_LOW).apply {
                setShowBadge(false)
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(CANAL_ALERTA, "Avisos de apps nuevas", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    fun notificacionBoton(c: Context): Notification {
        val abrir = PendingIntent.getActivity(
            c, 0,
            Intent(c, EmergenciaActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(c, CANAL_BOTON)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("🚨 ¿Te molesta una publicidad?")
            .setContentText("Tocá acá y te ayudo a encontrarla")
            .setContentIntent(abrir)
            .setOngoing(true)
            .setColor(Colores.ROJO)
            .build()
    }

    /** Compara con la "foto" anterior de apps y avisa por cada app nueva sospechosa. */
    fun revisarAppsNuevas(c: Context) {
        val actuales = Analizador.paquetesDeUsuario(c.packageManager).map { it.packageName }.toSet()
        val anteriores = Prefs.fotoApps(c)
        Prefs.setFotoApps(c, actuales)
        if (anteriores == null) return  // primera vez: solo se saca la foto
        for (p in actuales - anteriores) {
            if (p == c.packageName || p in Prefs.confiables(c)) continue
            if (Analizador.vinoDeTienda(c, p) && p !in Config.VIRUS_CONOCIDOS) continue
            avisarAppNueva(c, p)
        }
    }

    private fun avisarAppNueva(c: Context, paquete: String) {
        val app = Analizador.evaluarUna(c, paquete) ?: return
        val id = paquete.hashCode()

        val borrar = PendingIntent.getActivity(
            c, id,
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$paquete")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val confiar = PendingIntent.getBroadcast(
            c, id,
            Intent(c, ConfiarReceiver::class.java).putExtra(EXTRA_PAQUETE, paquete),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val icono = Icon.createWithResource(c, R.drawable.ic_notif)

        val n = Notification.Builder(c, CANAL_ALERTA)
            .setSmallIcon(R.drawable.ic_notif)
            .setContentTitle("⚠️ Se instaló «${app.nombre}»")
            .setContentText("¿La instalaste vos? No viene de la Play Store.")
            .setStyle(Notification.BigTextStyle().bigText(
                "¿La instalaste vos?\nNo viene de la Play Store. Si no la conocés, borrala."
            ))
            .setColor(Colores.ROJO)
            .setAutoCancel(true)
            .setContentIntent(borrar)
            .addAction(Notification.Action.Builder(icono, "🗑️ NO, BORRAR", borrar).build())
            .addAction(Notification.Action.Builder(icono, "✅ Sí, es mía", confiar).build())
            .build()

        try {
            c.getSystemService(NotificationManager::class.java).notify(id, n)
        } catch (e: SecurityException) { }
    }
}

/** Cuando el cliente toca "Sí, es mía". */
class ConfiarReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val p = i.getStringExtra(Alertas.EXTRA_PAQUETE) ?: return
        Prefs.agregarConfiable(c, p)
        c.getSystemService(NotificationManager::class.java).cancel(p.hashCode())
    }
}

/** Vuelve a poner el botón cuando se prende el celular. */
class ArranqueReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        ProteccionService.iniciar(c)
    }
}
