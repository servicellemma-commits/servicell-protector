package ar.servicell.protector

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

/**
 * 🎬 Presentación al abrir la app: logo de Servicell-Emma animado
 * sobre fondo negro, con un sonido tecnológico corto.
 * - Si el celular está en silencio o vibración, no suena.
 * - Tocando la pantalla se saltea.
 */
class PresentacionActivity : Activity() {

    private val handler = Handler(Looper.getMainLooper())
    private var musica: MediaPlayer? = null
    private var termino = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.BLACK
        window.navigationBarColor = Color.BLACK

        val raiz = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#0D0D0D"))
            setOnClickListener { seguir() }
        }
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(28), 0, dp(28), 0)
        }
        val logo = ImageView(this).apply {
            setImageResource(R.drawable.logo_blanco)
            adjustViewBounds = true
            alpha = 0f; scaleX = 0.85f; scaleY = 0.85f
        }
        val linea = View(this).apply {
            setBackgroundColor(Color.WHITE)
            scaleX = 0f
        }
        val nombre = TextView(this).apply {
            text = "🛡️  PROTECTOR"
            textSize = 22f
            letterSpacing = 0.3f
            setTextColor(Color.WHITE)
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
            alpha = 0f
        }
        col.addView(logo, LinearLayout.LayoutParams(-1, -2))
        col.addView(linea, LinearLayout.LayoutParams(-1, dp(2)).apply {
            topMargin = dp(22); bottomMargin = dp(22)
            leftMargin = dp(40); rightMargin = dp(40)
        })
        col.addView(nombre, LinearLayout.LayoutParams(-1, -2))
        raiz.addView(col, FrameLayout.LayoutParams(-1, -1))
        setContentView(raiz)

        sonar()

        val suave = DecelerateInterpolator()
        logo.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(900).setInterpolator(suave).start()
        linea.animate().scaleX(1f).setStartDelay(650).setDuration(600).setInterpolator(suave).start()
        nombre.animate().alpha(1f).setStartDelay(1000).setDuration(600).start()

        handler.postDelayed({ seguir() }, 2600)
    }

    private fun sonar() {
        val audio = getSystemService(AudioManager::class.java)
        if (audio?.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        try {
            musica = MediaPlayer.create(
                this, R.raw.intro,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
                audio.generateAudioSessionId()
            )?.apply {
                setVolume(0.6f, 0.6f)
                setOnCompletionListener { it.release(); if (musica === it) musica = null }
                start()
            }
        } catch (e: Exception) { }
    }

    private fun seguir() {
        if (termino) return
        termino = true
        handler.removeCallbacksAndMessages(null)
        startActivity(Intent(this, MainActivity::class.java))
        @Suppress("DEPRECATION")
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
        finish()
    }

    override fun onDestroy() {
        handler.removeCallbacksAndMessages(null)
        // Si se saltea la presentación, el sonido termina solo (dura 2 segundos)
        super.onDestroy()
    }
}
