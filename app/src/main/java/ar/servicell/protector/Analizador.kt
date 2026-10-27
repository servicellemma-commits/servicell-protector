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
    /** Es una app de una marca conocida (Facebook, WhatsApp, bancos…). */
    val conocida: Boolean = false,
) {
    val nivel: Nivel
        get() = when {
            puntos >= 9 -> Nivel.PELIGROSA
            puntos >= 5 -> Nivel.REVISAR
            else -> Nivel.TRANQUILA
        }
}

enum class Nivel { PELIGROSA, REVISAR, TRANQUILA }

/** 🔍 Revisa las apps del celular y les pone un puntaje de riesgo. */
object Analizador {

    private val TIENDAS = setOf(
        "com.android.vending",                 // Play Store
        "com.google.android.feedback",         // Play Store (celulares viejos)
        "com.sec.android.app.samsungapps",     // Galaxy Store
        "com.huawei.appmarket",                // AppGallery
        "com.xiaomi.market", "com.xiaomi.mipicks", "com.xiaomi.discover",
        "com.heytap.market", "com.oppo.market", // Oppo / Realme
        "com.vivo.appstore",
        "com.amazon.venezia",
        // Restauración al cambiar de celular (copian las apps del celular viejo)
        "com.sec.android.easyMover",           // Samsung Smart Switch
        "com.google.android.apps.restore",     // Restaurar de Google
        "com.motorola.migrate", "com.miui.backup", "com.coloros.backuprestore",
        // Instaladores oficiales de Facebook (preinstalados de fábrica)
        "com.facebook.system", "com.facebook.appmanager",
    )

    /**
     * Instaladores "a mano": cuando alguien abrió un archivo APK suelto.
     * Si una app conocida (Facebook, WhatsApp…) vino así, sí puede ser una copia trucha.
     */
    private val INSTALADORES_MANUALES = setOf(
        "com.google.android.packageinstaller", "com.android.packageinstaller",
        "com.samsung.android.packageinstaller", "com.miui.packageinstaller",
    )

    private enum class Origen { TIENDA, PREINSTALADA, MANUAL, OTRA_APP }

    // Apps conocidas: se ignoran SOLO si vinieron de una tienda oficial
    // (un virus puede disfrazarse con el nombre "com.google...", pero no viene de la tienda).
    private val CONOCIDAS = listOf(
        "com.whatsapp", "com.facebook.", "com.instagram.", "com.mercadopago.",
        "com.mercadolibre", "com.google.", "com.android.", "com.samsung.", "com.sec.",
        "com.motorola.", "com.miui.", "com.xiaomi.", "com.huawei.", "com.spotify.",
        "com.netflix.", "com.zhiliaoapp.", "com.snapchat.", "org.telegram.",
        "com.microsoft.", "com.ualabee.", "ar.com.", "com.brubank", "com.naranjax",
    )

    // Nombres típicos de "limpiadores" y apps engañosas
    private val NOMBRES_FUERTES = listOf(
        "clean", "limpi", "boost", "acelera", "junk", "basura", "cooler", "enfria", "enfría",
        "optimi", "phone master", "phonemaster", "antivirus", "virus", "cache", "battery saver",
        "batterysaver", "ahorro de bater", "speed up", "speedup", "ram ",
    )
    private val NOMBRES_DEBILES = listOf(
        "bater", "battery", "update", "actualiz", "sistema", "system", "security", "seguridad",
        "wallpaper", "fondo", "linterna", "flashlight", "torch", "qr", "pdf", "weather", "clima",
        "vpn", "recover", "recuper", "file manager", "launcher",
    )

    // Empresas de publicidad: un limpiador falso suele tener MUCHAS a la vez
    private val REDES_DE_PUBLICIDAD = mapOf(
        "com.google.android.gms.ads" to "Google", "com.applovin" to "AppLovin",
        "com.unity3d.ads" to "Unity", "com.unity3d.services" to "Unity", "com.ironsource" to "ironSource",
        "com.facebook.ads" to "Meta", "com.bytedance.sdk.openadsdk" to "Pangle", "com.pangle" to "Pangle",
        "com.mbridge" to "Mintegral", "com.mintegral" to "Mintegral", "com.vungle" to "Vungle",
        "com.inmobi" to "InMobi", "com.chartboost" to "Chartboost", "com.fyber" to "Fyber",
        "com.inneractive" to "Fyber", "com.my.target" to "myTarget", "com.yandex.mobile.ads" to "Yandex",
        "com.adcolony" to "AdColony", "sg.bigo.ads" to "Bigo", "com.bigossp" to "Bigo",
        "com.tapjoy" to "Tapjoy", "com.startapp" to "Start.io", "com.smaato" to "Smaato",
        "com.verve" to "Verve", "com.amazon.device.ads" to "Amazon",
    )

    // Apps famosas que los virus suelen imitar → paquetes oficiales
    private val OFICIALES = mapOf(
        "whatsapp" to setOf("com.whatsapp", "com.whatsapp.w4b"),
        "whatsapp business" to setOf("com.whatsapp.w4b"),
        "instagram" to setOf("com.instagram.android", "com.instagram.lite"),
        "facebook" to setOf("com.facebook.katana", "com.facebook.lite"),
        "messenger" to setOf("com.facebook.orca", "com.facebook.mlite"),
        "tiktok" to setOf("com.zhiliaoapp.musically", "com.ss.android.ugc.trill", "com.zhiliaoapp.musically.go"),
        "telegram" to setOf("org.telegram.messenger", "org.telegram.messenger.web"),
        "chrome" to setOf("com.android.chrome"),
        "youtube" to setOf("com.google.android.youtube"),
        "gmail" to setOf("com.google.android.gm"),
        "google" to setOf("com.google.android.googlequicksearchbox"),
        "play store" to setOf("com.android.vending"),
        "google play" to setOf("com.android.vending"),
        "google play services" to setOf("com.google.android.gms"),
        "servicios de google play" to setOf("com.google.android.gms"),
        "mercado pago" to setOf("com.mercadopago.wallet"),
        "mercado libre" to setOf("com.mercadolibre"),
        "netflix" to setOf("com.netflix.mediaclient"),
        "spotify" to setOf("com.spotify.music"),
    )

    /** Nombre "limpio" para comparar: minúsculas, sin acentos ni símbolos. */
    private fun normalizarNombre(s: String): String =
        java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ").trim()

    /**
     * Nombres de las apps ORIGINALES del celular (de fábrica o bajadas de la tienda):
     * nombre limpio → paquete. Sirve para descubrir copias truchas con el mismo nombre.
     */
    @Suppress("DEPRECATION")
    private fun nombresOriginales(c: Context): Map<String, String> {
        val pm = c.packageManager
        val mapa = HashMap<String, String>()
        val apps = try { pm.getInstalledApplications(0) } catch (e: Exception) { emptyList() }
        for (ai in apps) {
            val sistema = (ai.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (!sistema && origen(pm, ai.packageName) != Origen.TIENDA) continue
            val n = try { normalizarNombre(ai.loadLabel(pm).toString()) } catch (e: Exception) { "" }
            if (n.length >= 3) mapa.putIfAbsent(n, ai.packageName)
        }
        return mapa
    }

    /** Si el nombre imita a una app famosa, devuelve el nombre de esa app. */
    private fun imitaA(nombreLimpio: String, paquete: String): String? {
        for ((famosa, oficiales) in OFICIALES) {
            if (paquete in oficiales) return null
            if (nombreLimpio == famosa || nombreLimpio.startsWith("$famosa ")) return famosa
        }
        return null
    }

    /** Cantidad de apps revisadas en el último análisis. */
    @Volatile var ultimoTotal = 0

    /** Revisa todas las apps instaladas por el usuario. La más sospechosa queda primera. */
    fun analizar(c: Context, minutosRecientes: Long = 10): List<AppRevisada> {
        val pm = c.packageManager
        val recientes = appsAbiertasRecien(c, minutosRecientes)
        val admins = administradores(c)
        val accesibilidad = conAccesibilidad(c)
        val confiables = Prefs.confiables(c)

        val originales = nombresOriginales(c)
        val todas = paquetesDeUsuario(pm)
        ultimoTotal = todas.size
        return todas
            .filter { it.packageName != c.packageName && it.packageName !in confiables }
            .mapNotNull { evaluar(c, it, recientes, admins, accesibilidad, originales) }
            .filter { it.puntos > 0 }
            .sortedWith(compareByDescending<AppRevisada> { it.puntos }.thenByDescending { it.abiertaRecien })
    }

    /**
     * La que más probablemente está mostrando la publicidad:
     * estaba abierta hace un momento y además tiene varias señales feas.
     */
    fun culpable(lista: List<AppRevisada>): AppRevisada? =
        lista.firstOrNull { it.abiertaRecien && it.puntos >= 7 && !it.conocida }
            ?: lista.firstOrNull { it.puntos >= 11 && !it.conocida }

    /** Revisa una sola app (se usa cuando se instala algo nuevo). */
    fun evaluarUna(c: Context, paquete: String): AppRevisada? {
        val pi = infoPaquete(c.packageManager, paquete) ?: return null
        return evaluar(c, pi, emptySet(), administradores(c), conAccesibilidad(c), nombresOriginales(c))
    }

    fun estaInstalada(c: Context, paquete: String) = infoPaquete(c.packageManager, paquete) != null

    fun vinoDeTienda(c: Context, paquete: String) = origen(c.packageManager, paquete) == Origen.TIENDA

    /** De dónde vino la app: tienda, de fábrica, APK a mano o la instaló otra app. */
    @Suppress("DEPRECATION")
    private fun origen(pm: PackageManager, paquete: String): Origen {
        val fuentes = try {
            if (Build.VERSION.SDK_INT >= 30) {
                val i = pm.getInstallSourceInfo(paquete)
                listOf(i.installingPackageName, i.initiatingPackageName, i.originatingPackageName)
            } else listOf(pm.getInstallerPackageName(paquete))
        } catch (e: Exception) { emptyList() }
        val conocidas = fuentes.filterNotNull().filter { it.isNotBlank() && it != "android" }
        return when {
            conocidas.any { it in TIENDAS } -> Origen.TIENDA
            conocidas.isEmpty() -> Origen.PREINSTALADA          // de fábrica o cargada por la empresa
            conocidas.all { it in INSTALADORES_MANUALES } -> Origen.MANUAL
            else -> Origen.OTRA_APP
        }
    }

    fun esConocida(paquete: String) = CONOCIDAS.any { paquete.startsWith(it) }

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
        originales: Map<String, String> = emptyMap(),
    ): AppRevisada? {
        val pm = c.packageManager
        val paquete = pi.packageName
        val ai = pi.applicationInfo ?: return null
        val org = origen(pm, paquete)
        val desdeTienda = org == Origen.TIENDA

        // Apps conocidas (Facebook, WhatsApp, Mercado Pago…): se ignoran si vinieron de la tienda
        // o vienen de fábrica. Solo se revisan si alguien las instaló con un APK suelto.
        if (esConocida(paquete) && paquete !in Config.VIRUS_CONOCIDOS && org != Origen.MANUAL &&
            org != Origen.OTRA_APP) return null

        val nombre = try { ai.loadLabel(pm).toString() } catch (e: Exception) { paquete }
        // Se piden también las pantallas y servicios, para ver qué trae "adentro"
        val detalle = detallePaquete(pm, paquete) ?: pi
        val permisos = detalle.requestedPermissions?.toSet() ?: pi.requestedPermissions?.toSet() ?: emptySet()
        val componentes = (detalle.activities?.map { it.name } ?: emptyList()) +
            (detalle.services?.map { it.name } ?: emptyList())
        val permisosServicios = detalle.services?.mapNotNull { it.permission }?.toSet() ?: emptySet()
        fun tiene(p: String) = "android.permission.$p" in permisos

        val motivos = mutableListOf<String>()
        var puntos = 0
        fun suma(p: Int, motivo: String) { puntos += p; motivos += motivo }

        // ── Señales fuertes ──
        if (paquete in Config.VIRUS_CONOCIDOS) suma(10, "Está en la lista de virus conocidos de ${Config.NEGOCIO}")
        when (org) {
            Origen.MANUAL, Origen.OTRA_APP -> suma(3, "No se descargó de la tienda oficial (Play Store)")
            Origen.PREINSTALADA -> suma(1, "No figura de dónde se instaló (puede venir de fábrica)")
            Origen.TIENDA -> {}
        }
        if (pm.getLaunchIntentForPackage(paquete) == null) suma(3, "Está escondida: no tiene ícono en el menú")
        if (paquete in admins) suma(3, "Se puso como administradora del celular para que no la puedan borrar")
        if (paquete in accesibilidad) suma(3, "Tiene permiso para controlar la pantalla")
        else if ("android.permission.BIND_ACCESSIBILITY_SERVICE" in permisosServicios) suma(1, "Quiere permiso para controlar la pantalla")

        val n = (nombre + " " + paquete).lowercase()
        when {
            NOMBRES_FUERTES.any { it in n } -> suma(3, "Tiene nombre de «limpiador» o «acelerador», típico de apps con publicidad")
            NOMBRES_DEBILES.any { it in n } -> suma(1, "Tiene un nombre que suelen usar las apps engañosas")
        }

        val redes = REDES_DE_PUBLICIDAD.filterKeys { pref -> componentes.any { it.startsWith(pref) } }.values.toSet()
        when {
            redes.size >= 5 -> suma(3, "Trae publicidad de ${redes.size} empresas distintas")
            redes.size >= 3 -> suma(2, "Trae publicidad de ${redes.size} empresas distintas")
        }

        // ── Permisos típicos de apps con publicidad abusiva ──
        if (tiene("SYSTEM_ALERT_WINDOW")) suma(2, "Puede mostrarse encima de otras apps")
        if (tiene("USE_FULL_SCREEN_INTENT")) suma(2, "Puede abrir pantallas completas sola (así aparecen las publicidades)")
        if (tiene("KILL_BACKGROUND_PROCESSES")) suma(2, "Cierra otras apps para hacer creer que «limpia»")
        if (tiene("REQUEST_INSTALL_PACKAGES")) suma(2, "Puede instalar otras apps")
        if ("android.permission.BIND_NOTIFICATION_LISTENER_SERVICE" in permisosServicios)
            suma(2, "Quiere leer tus notificaciones")
        if (tiene("PACKAGE_USAGE_STATS")) suma(1, "Quiere saber qué apps usás")
        if (tiene("REQUEST_IGNORE_BATTERY_OPTIMIZATIONS")) suma(1, "Pide que el celular no la apague nunca")
        if (tiene("RECEIVE_BOOT_COMPLETED")) suma(1, "Arranca sola al prender el celular")
        if (ai.targetSdkVersion in 1..27) suma(1, "Está hecha para un Android viejo (evita controles nuevos)")

        if (System.currentTimeMillis() - pi.firstInstallTime < 7L * 24 * 60 * 60 * 1000) suma(1, "Se instaló hace pocos días")
        // ── Gemelos truchos: copia de una app conocida, bajada de afuera de la tienda ──
        if (org == Origen.MANUAL || org == Origen.OTRA_APP) {
            val limpio = normalizarNombre(nombre)
            val original = originales[limpio]
            val famosa = imitaA(limpio, paquete)
            when {
                original != null && original != paquete ->
                    suma(5, "Copia trucha: hay otra app «$nombre» original, y esta no viene de la tienda")
                famosa != null ->
                    suma(5, "Se hace pasar por «${nombre}», pero no es la app oficial")
            }
        }

        val reciente = paquete in recientes
        if (reciente) suma(4, "Estaba abierta hace un momento")

        val icono = try { ai.loadIcon(pm) } catch (e: Exception) { null }
        return AppRevisada(paquete, nombre, icono, puntos, motivos, paquete in admins, reciente, esConocida(paquete))
    }

    @Suppress("DEPRECATION")
    private fun detallePaquete(pm: PackageManager, paquete: String): PackageInfo? = try {
        pm.getPackageInfo(
            paquete,
            PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES
        )
    } catch (e: Throwable) { null }

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
