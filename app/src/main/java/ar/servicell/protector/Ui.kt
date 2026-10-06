package ar.servicell.protector

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale

/** 🎨 Colores y piezas grandes y simples, pensadas para adultos mayores. */
object Colores {
    val ROJO = Color.parseColor("#C62828")
    val VERDE = Color.parseColor("#2E7D32")
    val AZUL = Color.parseColor("#1A1A1A")   // negro de la marca Servicell-Emma
    val GRIS = Color.parseColor("#546E7A")
    val AMARILLO = Color.parseColor("#F9A825")
    val FONDO = Color.parseColor("#F5F7FA")
    val TEXTO = Color.parseColor("#1A1A1A")
}

fun Context.dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

/** Pantalla con scroll. Devuelve el contenedor donde se agregan las cosas. */
fun Activity.pantalla(): LinearLayout {
    val scroll = ScrollView(this).apply { setBackgroundColor(Colores.FONDO); isFillViewport = true }
    val col = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(20), dp(28), dp(20), dp(28))
    }
    scroll.addView(col)
    setContentView(scroll)
    return col
}

/** Logo de Servicell-Emma arriba de la pantalla. */
fun LinearLayout.logo() {
    val iv = android.widget.ImageView(context).apply {
        setImageResource(R.drawable.logo)
        adjustViewBounds = true
        contentDescription = Config.NEGOCIO
    }
    addView(iv, LinearLayout.LayoutParams(-1, -2).apply {
        bottomMargin = context.dp(22)
        leftMargin = context.dp(8); rightMargin = context.dp(8)
    })
}

fun LinearLayout.titulo(texto: String, tam: Float = 30f): TextView = texto(texto, tam, negrita = true)

fun LinearLayout.texto(
    texto: String,
    tam: Float = 21f,
    negrita: Boolean = false,
    color: Int = Colores.TEXTO,
    centrado: Boolean = false,
): TextView {
    val tv = TextView(context).apply {
        text = texto
        textSize = tam
        setTextColor(color)
        setLineSpacing(0f, 1.15f)
        if (negrita) setTypeface(typeface, Typeface.BOLD)
        if (centrado) gravity = Gravity.CENTER_HORIZONTAL
    }
    addView(tv, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(14) })
    return tv
}

fun LinearLayout.boton(texto: String, fondo: Int, colorTexto: Int = Color.WHITE, alClick: () -> Unit): Button {
    val b = Button(context).apply {
        text = texto
        isAllCaps = false
        textSize = 22f
        setTextColor(colorTexto)
        setTypeface(typeface, Typeface.BOLD)
        minHeight = context.dp(76)
        setPadding(context.dp(16), context.dp(12), context.dp(16), context.dp(12))
        background = GradientDrawable().apply { setColor(fondo); cornerRadius = context.dp(18).toFloat() }
        stateListAnimator = null
        setOnClickListener { alClick() }
    }
    addView(b, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(14) })
    return b
}

/**
 * Botón verde de WhatsApp. Muestra el ícono de WhatsApp que ya tiene instalado
 * el celular (el mismo que usa Android al "Compartir"); si no está, usa un emoji.
 */
fun LinearLayout.botonWhatsApp(texto: String, alClick: () -> Unit): Button {
    val icono = iconoWhatsApp(context)
    val b = boton(if (icono != null) texto else "💬 $texto", Colores.VERDE) { alClick() }
    if (icono != null) {
        val tam = context.dp(32)
        icono.setBounds(0, 0, tam, tam)
        b.setCompoundDrawablesRelative(icono, null, null, null)
        b.compoundDrawablePadding = context.dp(12)
    }
    return b
}

fun iconoWhatsApp(c: Context): android.graphics.drawable.Drawable? =
    listOf("com.whatsapp", "com.whatsapp.w4b").firstNotNullOfOrNull {
        try { c.packageManager.getApplicationIcon(it) } catch (e: Exception) { null }
    }

fun LinearLayout.tarjeta(colorBorde: Int): LinearLayout {
    val t = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(18), context.dp(18), context.dp(18), context.dp(8))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = context.dp(18).toFloat()
            setStroke(context.dp(4), colorBorde)
        }
    }
    addView(t, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = context.dp(18) })
    return t
}

fun LinearLayout.espacio(alto: Int = 12) {
    addView(View(context), LinearLayout.LayoutParams(-1, context.dp(alto)))
}

/** 🆘 Acciones de ayuda: borrar, frenar, WhatsApp, modo seguro. */
object Ayuda {

    fun desinstalar(a: Activity, paquete: String) {
        val i = Intent(Intent.ACTION_DELETE, Uri.parse("package:$paquete"))
        abrir(a, i, "No se pudo abrir la pantalla de borrar")
    }

    /** Pantalla de la app con "Forzar detención" y "Desactivar". */
    fun abrirDetalles(a: Activity, paquete: String) {
        val i = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$paquete"))
        abrir(a, i, "No se pudo abrir la información de la app")
    }

    /** Lista de "administradores del dispositivo" (cambia de lugar según la marca). */
    fun abrirAdministradores(a: Activity) {
        val directa = Intent().setComponent(
            ComponentName("com.android.settings", "com.android.settings.DeviceAdminSettings")
        )
        try { a.startActivity(directa) } catch (e: Exception) {
            abrir(a, Intent(Settings.ACTION_SECURITY_SETTINGS), "Buscá en Ajustes: «Administradores del dispositivo»")
        }
    }

    fun abrirPermisoDeUso(a: Activity) =
        abrir(a, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS), "Buscá en Ajustes: «Acceso a datos de uso»")

    private fun abrir(a: Activity, i: Intent, siFalla: String) {
        try { a.startActivity(i) } catch (e: ActivityNotFoundException) {
            Toast.makeText(a, siFalla, Toast.LENGTH_LONG).show()
        }
    }

    fun datosCelular() = "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} · Android ${Build.VERSION.RELEASE}"

    /** Muestra ANTES el mensaje exacto, y solo si el cliente acepta se abre WhatsApp. */
    fun pedirAyuda(a: Activity, detalle: String? = null) {
        if (Config.WHATSAPP.isBlank()) {
            Toast.makeText(a, "Falta cargar el WhatsApp del local", Toast.LENGTH_LONG).show()
            return
        }
        val nombre = Prefs.nombre(a).ifBlank { "un cliente" }
        val msg = buildString {
            append("Hola, soy $nombre. Necesito ayuda con mi celular.\n")
            if (detalle != null) append("$detalle\n")
            append("Celular: ${datosCelular()}")
        }
        AlertDialog.Builder(a)
            .setTitle("Se va a mandar este mensaje:")
            .setMessage(msg)
            .setPositiveButton("Mandar por WhatsApp") { _, _ ->
                val url = "https://wa.me/${Config.WHATSAPP}?text=" + Uri.encode(msg)
                abrir(a, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No se encontró WhatsApp")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    /** Le manda a Servicell el código del celular para activarlo a distancia. */
    fun mandarCodigo(a: Activity) {
        if (Config.WHATSAPP.isBlank()) {
            Toast.makeText(a, "Falta cargar el WhatsApp del local", Toast.LENGTH_LONG).show()
            return
        }
        val msg = "Hola, quiero activar Servicell Protector.\n" +
            "Código del celular: ${Licencia.codigoCelular(a)}\n" +
            "Celular: ${datosCelular()}"
        AlertDialog.Builder(a)
            .setTitle("Se va a mandar este mensaje:")
            .setMessage(msg)
            .setPositiveButton("Mandar por WhatsApp") { _, _ ->
                val url = "https://wa.me/${Config.WHATSAPP}?text=" + Uri.encode(msg)
                abrir(a, Intent(Intent.ACTION_VIEW, Uri.parse(url)), "No se encontró WhatsApp")
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    fun mostrarPrivacidad(a: Activity, alAceptar: (() -> Unit)? = null) {
        val d = AlertDialog.Builder(a)
            .setTitle("🔒 Tu privacidad")
            .setMessage(TEXTO_PRIVACIDAD)
            .setCancelable(alAceptar == null)
        if (alAceptar != null) d.setPositiveButton("Entendido") { _, _ -> alAceptar() }
        else d.setPositiveButton("Cerrar", null)
        d.show()
    }

    fun mostrarModoSeguro(a: Activity) {
        AlertDialog.Builder(a)
            .setTitle("😵 No puedo usar el celular")
            .setMessage(TEXTO_MODO_SEGURO)
            .setPositiveButton("Entendido", null)
            .setNeutralButton("Pedir ayuda") { _, _ -> pedirAyuda(a, "No puedo usar el celular por la publicidad.") }
            .show()
    }

    val TEXTO_PRIVACIDAD = """
        • Esta app NO ve tus fotos, mensajes, contactos, contraseñas ni cuentas.

        • Solo revisa qué apps están instaladas y cuál estaba abierta, para encontrar la que pone publicidad. Eso no sale de tu celular.

        • Lo único que guarda es tu nombre, y solo en este celular.

        • La app no tiene permiso de Internet: no puede mandar nada a ningún lado.

        • Solo si vos tocás «Pedir ayuda», se abre WhatsApp con un mensaje que podés leer antes de mandarlo.
    """.trimIndent()

    val TEXTO_MODO_SEGURO = """
        Si la publicidad no te deja hacer nada, usá el «Modo seguro». Ahí las apps descargadas no funcionan y la publicidad desaparece.

        1. Mantené apretado el botón de apagar.
        2. Mantené el dedo apretado sobre «Apagar» hasta que aparezca «Modo seguro».
        3. Tocá «Aceptar». El celular se reinicia.
        4. Abrí esta app, tocá «Revisar todo el celular» y borrá la sospechosa.
        5. Reiniciá el celular normalmente.

        (En algunas marcas el paso 2 cambia. Si no te sale, pedinos ayuda.)
    """.trimIndent()
}

/** 🔊 Lee en voz alta lo que dice la pantalla. */
class Voz(c: Context) {
    private var lista = false
    private var pendiente: String? = null
    private val tts: TextToSpeech = TextToSpeech(c.applicationContext) { estado ->
        if (estado == TextToSpeech.SUCCESS) {
            lista = true
            pendiente?.let { decir(it) }
            pendiente = null
        }
    }

    fun decir(texto: String) {
        if (!lista) { pendiente = texto; return }
        // Si no hay voz en español, se usa la que tenga el celular.
        try { tts.setLanguage(Locale("es", "AR")) } catch (e: Exception) { }
        tts.speak(texto, TextToSpeech.QUEUE_FLUSH, null, "servicell")
    }

    fun callar() = tts.stop()
    fun apagar() = tts.shutdown()
}
