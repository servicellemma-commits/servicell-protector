package ar.servicell.protector

/**
 * ⚙️ DATOS QUE PODÉS CAMBIAR
 * Después de cambiar algo acá, GitHub arma un APK nuevo solo.
 */
object Config {

    /** Nombre del negocio que ve el cliente. */
    const val NEGOCIO = "Servicell-Emma"

    /** WhatsApp del local, con código de país y sin "+" ni espacios. Ej: 5491123456789 */
    const val WHATSAPP = ""

    /**
     * 🔑 LLAVE SECRETA para los códigos de activación.
     * No está escrita acá: GitHub la pone al armar el APK (secreto CLAVE_SECRETA).
     * Tiene que ser IGUAL a la del "generador de códigos".
     */
    val CLAVE_SECRETA: String = BuildConfig.CLAVE_SECRETA

    /**
     * 🦠 LISTA DE VIRUS CONOCIDOS.
     * Cuando encuentres una app maldita reparando un celular, agregá acá su
     * "nombre de paquete" (aparece en Ajustes → Apps → la app, o en el informe de WhatsApp).
     * Ejemplo: "com.ejemplo.limpiadorfalso",
     */
    val VIRUS_CONOCIDOS = setOf<String>(
    )
}
