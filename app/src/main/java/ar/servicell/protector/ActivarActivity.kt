package ar.servicell.protector

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast

/** Mensaje de "¡Listo!" después de activar. */
fun mensajeActivada(c: Context): String {
    val e = Licencia.estado(c)
    return if (e is Licencia.Estado.Hasta)
        "¡Activada hasta el ${e.vence.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))}!"
    else "¡Activada!"
}

/**
 * 🔗 Se abre sola cuando el cliente toca el link de activación que le mandó
 * Servicell-Emma por WhatsApp (servicell://activar/...).
 * Comprueba la firma; si está bien activa, y si no, explica por qué.
 */
class ActivarActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val texto = intent?.data?.toString().orEmpty()
        val problema = Licencia.activar(this, texto)
        if (problema == null) {
            Toast.makeText(this, mensajeActivada(this), Toast.LENGTH_LONG).show()
            ProteccionService.iniciar(this)
        } else {
            Toast.makeText(this, problema, Toast.LENGTH_LONG).show()
        }
        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
        finish()
    }
}
