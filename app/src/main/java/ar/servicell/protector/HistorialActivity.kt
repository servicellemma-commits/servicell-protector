package ar.servicell.protector

import android.app.Activity
import android.os.Bundle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 📋 Lista de las apps que se borraron con la ayuda de la app. */
class HistorialActivity : Activity() {

    override fun onResume() {
        super.onResume()
        val col = pantalla()
        col.logo()
        col.titulo("📋 Apps que borré", 28f)

        val lista = Prefs.historial(this)
        if (lista.isEmpty()) {
            col.texto("Todavía no se borró ninguna app con Servicell Protector. ✅")
        } else {
            col.texto(
                "Se ${if (lista.size == 1) "borró 1 app" else "borraron ${lista.size} apps"}:",
                color = Colores.GRIS
            )
            val formato = SimpleDateFormat("dd/MM/yyyy 'a las' HH:mm", Locale("es", "AR"))
            for ((fecha, nombre, paquete) in lista) {
                val t = col.tarjeta(Colores.GRIS)
                t.texto("🗑️ $nombre", tam = 22f, negrita = true)
                t.texto(formato.format(Date(fecha)), tam = 18f, color = Colores.GRIS)
                t.texto(paquete, tam = 14f, color = Colores.GRIS)
            }
        }
        col.boton("⬅ Volver", Colores.GRIS) { finish() }
    }
}
