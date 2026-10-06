package ar.servicell.protector

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Lo poquito que la app guarda, y solo dentro de este celular. */
object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("servicell", Context.MODE_PRIVATE)

    fun nombre(c: Context): String = p(c).getString("nombre", "") ?: ""
    fun setNombre(c: Context, v: String) = p(c).edit().putString("nombre", v.trim()).apply()

    fun activacion(c: Context): String = p(c).getString("activacion", "") ?: ""
    fun setActivacion(c: Context, v: String) = p(c).edit().putString("activacion", v).apply()

    fun privacidadAceptada(c: Context) = p(c).getBoolean("privacidad", false)
    fun setPrivacidadAceptada(c: Context) = p(c).edit().putBoolean("privacidad", true).apply()

    fun permisosVistos(c: Context) = p(c).getBoolean("permisos_vistos", false)
    fun setPermisosVistos(c: Context) = p(c).edit().putBoolean("permisos_vistos", true).apply()

    fun ultimaRevision(c: Context): Long = p(c).getLong("ultima_revision", 0L)
    fun setUltimaRevision(c: Context, v: Long) = p(c).edit().putLong("ultima_revision", v).apply()

    /** App que hay que borrar en modo seguro (para preguntar al volver). */
    fun setModoSeguroPendiente(c: Context, paquete: String, nombre: String) =
        p(c).edit().putString("ms_pendiente", "$paquete|${nombre.replace("|", " ")}")
            .putLong("ms_desde", System.currentTimeMillis()).apply()

    /** (paquete, nombre) si hay una pendiente y pasaron al menos 2 minutos. */
    fun modoSeguroPendiente(c: Context): Pair<String, String>? {
        val v = p(c).getString("ms_pendiente", null) ?: return null
        if (System.currentTimeMillis() - p(c).getLong("ms_desde", 0L) < 2 * 60_000) return null
        return v.substringBefore("|") to v.substringAfter("|")
    }

    fun limpiarModoSeguro(c: Context) = p(c).edit().remove("ms_pendiente").remove("ms_desde").apply()

    /** Apps que el cliente quiso borrar (paquete|nombre), para anotarlas cuando se borren. */
    fun marcarParaBorrar(c: Context, paquete: String, nombre: String) {
        val limpias = (p(c).getStringSet("para_borrar", emptySet()) ?: emptySet())
            .filter { !it.startsWith("$paquete|") }.toSet()
        p(c).edit().putStringSet("para_borrar", limpias + "$paquete|${nombre.replace("|", " ")}").apply()
    }

    /** Si la app estaba marcada para borrar, la pasa al historial. */
    fun registrarBorrada(c: Context, paquete: String) {
        val pendientes = p(c).getStringSet("para_borrar", emptySet()) ?: emptySet()
        val item = pendientes.firstOrNull { it.startsWith("$paquete|") } ?: return
        val nombre = item.substringAfter("|")
        val linea = "${System.currentTimeMillis()}|$nombre|$paquete"
        val nuevo = (listOf(linea) + historialCrudo(c)).take(100)
        p(c).edit()
            .putStringSet("para_borrar", pendientes - item)
            .putString("historial", nuevo.joinToString("\n"))
            .apply()
    }

    private fun historialCrudo(c: Context): List<String> =
        (p(c).getString("historial", "") ?: "").split("\n").filter { it.isNotBlank() }

    /** Historial de apps borradas, la más nueva primero: (fecha, nombre, paquete). */
    fun historial(c: Context): List<Triple<Long, String, String>> = historialCrudo(c).mapNotNull {
        val partes = it.split("|")
        if (partes.size < 3) null else Triple(partes[0].toLongOrNull() ?: 0L, partes[1], partes[2])
    }

    /** Apps que el cliente dijo "es mía, confío". */
    fun confiables(c: Context): Set<String> =
        p(c).getStringSet("confiables", emptySet())?.toSet() ?: emptySet()

    fun agregarConfiable(c: Context, paquete: String) =
        p(c).edit().putStringSet("confiables", confiables(c) + paquete).apply()

    /** Foto de las apps instaladas, para darse cuenta cuando aparece una nueva. */
    fun fotoApps(c: Context): Set<String>? =
        if (p(c).contains("foto_apps")) p(c).getStringSet("foto_apps", emptySet())?.toSet() else null

    fun setFotoApps(c: Context, v: Set<String>) =
        p(c).edit().putStringSet("foto_apps", v).apply()
}

/**
 * 🔐 Activación por celular.
 * Cada celular tiene un código propio. Solo con la llave secreta se puede
 * generar el código de activación que le corresponde. Si copian la app a
 * otro celular, el código no coincide y la app no arranca.
 */
object Licencia {
    // Letras fáciles de leer (sin I, L, O, U para no confundir)
    private const val ALFABETO = "0123456789ABCDEFGHJKMNPQRSTVWXYZ"

    @SuppressLint("HardwareIds")
    fun codigoCelular(c: Context): String {
        val id = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "sin-id"
        val h = MessageDigest.getInstance("SHA-256").digest("servicell:$id".toByteArray())
        return conGuion(base32(h.copyOf(5)))
    }

    fun codigoActivacionPara(codigoCelular: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Config.CLAVE_SECRETA.toByteArray(), "HmacSHA256"))
        val h = mac.doFinal(normalizar(codigoCelular).toByteArray())
        return conGuion(base32(h.copyOf(5)))
    }

    fun esValido(c: Context, ingresado: String): Boolean {
        val esperado = normalizar(codigoActivacionPara(codigoCelular(c)))
        return normalizar(ingresado) == esperado
    }

    fun estaActivada(c: Context) = esValido(c, Prefs.activacion(c))

    fun normalizar(s: String): String = s.uppercase()
        .replace('O', '0').replace('I', '1').replace('L', '1')
        .filter { it in ALFABETO }

    private fun conGuion(s: String) = s.substring(0, 4) + "-" + s.substring(4, 8)

    private fun base32(b: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bits = 0
        for (x in b) {
            buffer = ((buffer shl 8) or (x.toInt() and 0xFF)) and 0xFFFF
            bits += 8
            while (bits >= 5) {
                sb.append(ALFABETO[(buffer shr (bits - 5)) and 31])
                bits -= 5
            }
        }
        if (bits > 0) sb.append(ALFABETO[(buffer shl (5 - bits)) and 31])
        return sb.toString()
    }
}
