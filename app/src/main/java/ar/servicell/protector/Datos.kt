package ar.servicell.protector

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.time.LocalDate
import java.time.temporal.ChronoUnit
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

    // ───────────── Códigos con vencimiento ─────────────
    // Formato: DDDD-XXXX-XXXX  →  DDDD = día de vencimiento, el resto = firma.
    // Los códigos viejos de 8 letras (XXXX-XXXX) siguen siendo permanentes.

    /** Días de gracia después de vencer, antes de bloquear. */
    const val DIAS_GRACIA = 3
    private val EPOCA: LocalDate = LocalDate.of(2026, 1, 1)

    sealed class Estado {
        object SinActivar : Estado()
        object Permanente : Estado()
        /** [dias] = días que faltan (negativo si ya venció). */
        data class Hasta(val vence: LocalDate, val dias: Int) : Estado()
    }

    fun codigoConVencimiento(codigoCelular: String, vence: LocalDate): String {
        val dd = diasACodigo(ChronoUnit.DAYS.between(EPOCA, vence).toInt())
        val m = firma(normalizar(codigoCelular) + ":" + dd)
        return "$dd-${m.substring(0, 4)}-${m.substring(4, 8)}"
    }

    /** Qué tipo de código es [ingresado] para este celular (sin mirar la fecha de hoy). */
    private fun leer(c: Context, ingresado: String): Estado {
        if (ingresado.startsWith(PREFIJO_FIRMADO)) return leerFirmado(c, ingresado.removePrefix(PREFIJO_FIRMADO))
        val n = normalizar(ingresado)
        val cel = codigoCelular(c)
        return when (n.length) {
            8 -> if (n == normalizar(codigoActivacionPara(cel))) Estado.Permanente else Estado.SinActivar
            12 -> {
                val dd = n.substring(0, 4)
                val dias = codigoADias(dd) ?: return Estado.SinActivar
                if (n.substring(4) != firma(normalizar(cel) + ":" + dd)) return Estado.SinActivar
                val vence = EPOCA.plusDays(dias.toLong())
                Estado.Hasta(vence, ChronoUnit.DAYS.between(hoy(c), vence).toInt())
            }
            else -> Estado.SinActivar
        }
    }

    fun estado(c: Context): Estado = leer(c, Prefs.activacion(c))

    // ───────────── 🔗 Activación por link (firma de verdad) ─────────────
    // El generador firma con una llave PRIVADA que solo tiene Servicell-Emma.
    // La app tiene solo la llave PÚBLICA: sirve para comprobar la firma, no para fabricar códigos.
    // Así, aunque alguien desarme la app, no puede sacar activaciones gratis.
    private const val LLAVE_PUBLICA = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEibvSXl2pxwge4WbTDU3V2Om5TXfN0Gn1Cr6odiTr7U8qzOSLcboXmJ2keWrPprn83AGDDksVv7dBDeXOxfCAGQ=="
    private const val PREFIJO_FIRMADO = "SP2:"
    private const val DIAS_PERMANENTE = 0xFFFF
    private val TOKEN = Regex("(?<![A-Za-z0-9_-])[A-Za-z0-9_-]{90}(?![A-Za-z0-9_-])")

    /** Busca la activación dentro de lo que pegó el cliente (el link entero o solo el código). */
    fun tokenDeTexto(texto: String): String? = TOKEN.findAll(texto.trim()).map { it.value }.lastOrNull()

    private fun leerFirmado(c: Context, token: String): Estado {
        val b = try {
            android.util.Base64.decode(token, android.util.Base64.URL_SAFE or android.util.Base64.NO_WRAP or android.util.Base64.NO_PADDING)
        } catch (e: Exception) { return Estado.SinActivar }
        if (b.size != 67 || b[0].toInt() != 2) return Estado.SinActivar
        val dias = ((b[1].toInt() and 0xFF) shl 8) or (b[2].toInt() and 0xFF)
        val mensaje = "SP2|${normalizar(codigoCelular(c))}|$dias".toByteArray()
        if (!firmaValida(mensaje, b.copyOfRange(3, 67))) return Estado.SinActivar
        if (dias == DIAS_PERMANENTE) return Estado.Permanente
        val vence = EPOCA.plusDays(dias.toLong())
        return Estado.Hasta(vence, ChronoUnit.DAYS.between(hoy(c), vence).toInt())
    }

    private fun firmaValida(mensaje: ByteArray, rs: ByteArray): Boolean = try {
        val llave = KeyFactory.getInstance("EC").generatePublic(
            X509EncodedKeySpec(android.util.Base64.decode(LLAVE_PUBLICA, android.util.Base64.DEFAULT))
        )
        Signature.getInstance("SHA256withECDSA").run {
            initVerify(llave); update(mensaje); verify(aDer(rs))
        }
    } catch (e: Exception) { false }

    /** La firma viene como r||s (64 bytes); Android la pide en formato DER. */
    private fun aDer(rs: ByteArray): ByteArray {
        fun entero(x: ByteArray): ByteArray {
            var i = 0
            while (i < x.size - 1 && x[i] == 0.toByte()) i++
            var v = x.copyOfRange(i, x.size)
            if (v[0].toInt() and 0x80 != 0) v = byteArrayOf(0) + v
            return byteArrayOf(0x02, v.size.toByte()) + v
        }
        val cuerpo = entero(rs.copyOfRange(0, 32)) + entero(rs.copyOfRange(32, 64))
        return byteArrayOf(0x30, cuerpo.size.toByte()) + cuerpo
    }

    /**
     * Activa con lo que pegó o abrió el cliente. Devuelve null si salió bien,
     * o el motivo para mostrarle si no.
     * Los códigos cortos viejos ya no se aceptan para activaciones NUEVAS
     * (los celulares que ya estaban activados con ellos siguen funcionando).
     */
    fun activar(c: Context, texto: String): String? {
        val token = tokenDeTexto(texto)
        if (token == null) {
            val n = normalizar(texto)
            return if (n.length == 8 || n.length == 12)
                "Los códigos cortos ya no se usan. Pedile a ${Config.NEGOCIO} tu link de activación."
            else "No encontré un link de activación. Pegá el link completo que te mandó ${Config.NEGOCIO}."
        }
        return when (val e = leerFirmado(c, token)) {
            is Estado.SinActivar -> "Este link de activación no es para este celular. Pedí uno nuevo a ${Config.NEGOCIO}."
            is Estado.Hasta -> if (e.dias < 0) "Ese link ya venció. Pedí uno nuevo a ${Config.NEGOCIO}." else {
                Prefs.setActivacion(c, PREFIJO_FIRMADO + token); null
            }
            is Estado.Permanente -> { Prefs.setActivacion(c, PREFIJO_FIRMADO + token); null }
        }
    }

    /** Activada = permanente, o con fecha y todavía dentro del plazo (+ días de gracia). */
    fun estaActivada(c: Context): Boolean = when (val e = estado(c)) {
        is Estado.Permanente -> true
        is Estado.Hasta -> e.dias >= -DIAS_GRACIA
        else -> false
    }

    /** null si el código sirve; si no, el motivo para mostrarle al cliente. */
    fun problemaCon(c: Context, ingresado: String): String? = when (val e = leer(c, ingresado)) {
        is Estado.Permanente -> null
        is Estado.Hasta -> if (e.dias < 0) "Ese código ya venció. Pedí uno nuevo a ${Config.NEGOCIO}." else null
        else -> "El código no es correcto para este celular"
    }

    fun esValido(c: Context, ingresado: String) = problemaCon(c, ingresado) == null

    /**
     * Fecha de hoy, protegida contra el truco de atrasar el reloj:
     * nunca vuelve a un día anterior al último que ya vio.
     */
    fun hoy(c: Context): LocalDate {
        val real = LocalDate.now().toEpochDay()
        val p = c.getSharedPreferences("servicell", Context.MODE_PRIVATE)
        val visto = p.getLong("max_dia", 0L)
        if (real > visto) p.edit().putLong("max_dia", real).apply()
        return LocalDate.ofEpochDay(maxOf(real, visto))
    }

    private fun firma(texto: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(Config.CLAVE_SECRETA.toByteArray(), "HmacSHA256"))
        return base32(mac.doFinal(texto.toByteArray()).copyOf(5))
    }

    private fun diasACodigo(d: Int): String =
        (3 downTo 0).map { ALFABETO[(d shr (5 * it)) and 31] }.joinToString("")

    private fun codigoADias(s: String): Int? {
        var d = 0
        for (ch in s) { val v = ALFABETO.indexOf(ch); if (v < 0) return null; d = (d shl 5) or v }
        return d
    }

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
