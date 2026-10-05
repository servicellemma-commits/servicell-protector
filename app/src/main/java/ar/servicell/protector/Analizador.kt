package ar.servicell.protector

import android.app.AppOpsManager
import android.app.admin.DevicePolicyManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Process
import android.provider.Settings

class AppRevisada(
    val paquete: String,
    val nombre: String,
    val icono: Drawable?,
    val puntos: Int,
    val motivos: List<String>,
    val esAdmin: Boolean,
    val abiertaRecien: Boolean,
) {
    val nivel: Nivel
        get() = when {
            puntos >= 7 -> Nivel.PELIGROSA
            puntos >= 4 -> Nivel.REVISAR
            else -> Nivel.TRANQUILA
        }
}

enum class Nivel { PELIGROSA, REVISAR, TRANQUILA }

/** 🔍 Revisa las apps del celular y les pone un puntaje de riesgo. */
object Analizador {

    private val TIENDAS = setOf(
        "com.android.vending",                 // Play Store
        "com.sec.android.app.samsungapps",     // Galaxy Store
        "com.huawei.appmarket",
        "com.xiaomi.market",
        "com.amazon.venezia",
    )

    // Apps conocidas: se ignoran SOLO si vinieron de una tienda oficial
    // (un virus puede disfrazarse con el nombre "com.google...", pero no viene de la tienda).
    private val CONOCIDAS = listOf(
        "com.whatsapp", "com.facebook.", "com.instagram.", "com.mercadopago.",
        "com.mercadolibre", "com.google.", "com.android.", "com.samsung.", "com.sec.",
        "com.motorola.", "com.miui.", "com.xiaomi.", "com.huawei.", "com.spotify.",
        "com.netflix.", "com.zhiliaoapp.", "com.snapchat.", "org.telegram.",
        "com.microsoft.", "com.ualabee.", "ar.com.", "com.brubank", "com.naranjax",
    )

    private val PALABRAS_SOSPECHOSAS = listOf(
        "limpia", "clean", "boost", "bater", "battery", "update", "actualiz",
        "sistema", "system", "wallpaper", "fondo", "linterna", "flashlight",
        "optimi", "acelera", "speed", "antivirus", "security", "seguridad",
    )

    /** Revisa todas las apps instaladas por el usuario. La más sospechosa queda primera. */
    fun analizar(c: Context, minutosRecientes: Long = 10): List<AppRevisada> {
        val pm = c.packageManager
        val recientes = appsAbiertasRecien(c, minutosRecientes)
        val admins = administradores(c)
        val accesibilidad = conAccesibilidad(c)
        val confiables = Prefs.confiables(c)

        return paquetesDeUsuario(pm)
            .filter { it.packageName != c.packageName && it.packageName !in confiables }
            .mapNotNull { evaluar(c, it, recientes, admins, accesibilidad) }
            .filter { it.puntos > 0 }
            .sortedWith(compareByDescending<AppRevisada> { it.puntos }.thenByDescending { it.abiertaRecien })
    }

    /**
     * La que más probablemente está mostrando la publicidad:
     * estaba abierta hace un momento y además tiene varias señales feas.
     */
    fun culpable(lista: List<AppRevisada>): AppRevisada? =
        lista.firstOrNull { it.abiertaRecien && it.puntos >= 6 }
            ?: lista.firstOrNull { it.puntos >= 8 }

    /** Revisa una sola app (se usa cuando se instala algo nuevo). */
    fun evaluarUna(c: Context, paquete: String): AppRevisada? {
        val pi = infoPaquete(c.packageManager, paquete) ?: return null
        return evaluar(c, pi, emptySet(), administradores(c), conAccesibilidad(c))
    }

    fun estaInstalada(c: Context, paquete: String) = infoPaquete(c.packageManager, paquete) != null

    fun vinoDeTienda(c: Context, paquete: String) = instalador(c.packageManager, paquete) in TIENDAS

    @Suppress("DEPRECATION")
    fun paquetesDeUsuario(pm: PackageManager): List<PackageInfo> =
        pm.getInstalledPackages(PackageManager.GET_PERMISSIONS).filter { pi ->
            val ai = pi.applicationInfo ?: return@filter false
            (ai.flags and ApplicationInfo.FLAG_SYSTEM) == 0
        }

    private fun evaluar(
        c: Context,
        pi: PackageInfo,
        recientes: Set<String>,
        admins: Set<String>,
        accesibilidad: Set<String>,
    ): AppRevisada? {
        val pm = c.packageManager
        val paquete = pi.packageName
        val ai = pi.applicationInfo ?: return null
        val desdeTienda = instalador(pm, paquete) in TIENDAS

        if (desdeTienda && CONOCIDAS.any { paquete.startsWith(it) }) return null

        val nombre = try { ai.loadLabel(pm).toString() } catch (e: Exception) { paquete }
        val permisos = pi.requestedPermissions?.toSet() ?: emptySet()
        val motivos = mutableListOf<String>()
        var puntos = 0
        fun suma(p: Int, motivo: String) { puntos += p; motivos += motivo }

        if (paquete in Config.VIRUS_CONOCIDOS) suma(10, "Está en la lista de virus conocidos de ${Config.NEGOCIO}")
        if (!desdeTienda) suma(3, "No se descargó de la tienda oficial (Play Store)")
        if (pm.getLaunchIntentForPackage(paquete) == null) suma(3, "Está escondida: no tiene ícono en el menú")
        if (paquete in admins) suma(3, "Se puso como administradora del celular para que no la puedan borrar")
        if (paquete in accesibilidad) suma(3, "Tiene permiso para controlar la pantalla")
        if ("android.permission.SYSTEM_ALERT_WINDOW" in permisos) suma(2, "Puede mostrarse encima de otras apps")
        val n = (nombre + " " + paquete).lowercase()
        if (PALABRAS_SOSPECHOSAS.any { it in n }) suma(2, "Tiene un nombre típico de apps engañosas")
        if ("android.permission.RECEIVE_BOOT_COMPLETED" in permisos) suma(1, "Arranca sola al prender el celular")
        if (System.currentTimeMillis() - pi.firstInstallTime < 7L * 24 * 60 * 60 * 1000) suma(1, "Se instaló hace pocos días")
        val reciente = paquete in recientes
        if (reciente) suma(4, "Estaba abierta hace un momento")

        val icono = try { ai.loadIcon(pm) } catch (e: Exception) { null }
        return AppRevisada(paquete, nombre, icono, puntos, motivos, paquete in admins, reciente)
    }

    @Suppress("DEPRECATION")
    private fun infoPaquete(pm: PackageManager, paquete: String): PackageInfo? = try {
        pm.getPackageInfo(paquete, PackageManager.GET_PERMISSIONS)
    } catch (e: Exception) { null }

    @Suppress("DEPRECATION")
    private fun instalador(pm: PackageManager, paquete: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(paquete).installingPackageName
        else pm.getInstallerPackageName(paquete)
    } catch (e: Exception) { null }

    private fun administradores(c: Context): Set<String> {
        val dpm = c.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        return dpm.activeAdmins?.map { it.packageName }?.toSet() ?: emptySet()
    }

    private fun conAccesibilidad(c: Context): Set<String> {
        val s = Settings.Secure.getString(c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return emptySet()
        return s.split(':').mapNotNull { ComponentName.unflattenFromString(it)?.packageName }.toSet()
    }

    /** Apps que pasaron a primer plano en los últimos minutos. */
    @Suppress("DEPRECATION")
    private fun appsAbiertasRecien(c: Context, minutos: Long): Set<String> {
        if (!tienePermisoDeUso(c)) return emptySet()
        val usm = c.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val fin = System.currentTimeMillis()
        val eventos = usm.queryEvents(fin - minutos * 60_000, fin) ?: return emptySet()
        val e = UsageEvents.Event()
        val set = mutableSetOf<String>()
        while (eventos.hasNextEvent()) {
            eventos.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) set += e.packageName
        }
        return set
    }

    @Suppress("DEPRECATION")
    fun tienePermisoDeUso(c: Context): Boolean {
        val ops = c.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val modo = if (Build.VERSION.SDK_INT >= 29)
            ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        else
            ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName)
        return modo == AppOpsManager.MODE_ALLOWED
    }
}
