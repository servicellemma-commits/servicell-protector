# 🛡️ Servicell Protector

App de Android de **Servicell-Emma** para ayudar a los clientes a encontrar y borrar apps que llenan el celular de publicidad.

## Qué hace
- 🚨 **Botón de emergencia** fijo en la barra de notificaciones: el cliente lo toca cuando aparece la publicidad y la app le muestra la culpable con un botón grande para borrarla.
- ⚠️ **Aviso al instalar** una app que no viene de la Play Store: «¿La instalaste vos?» → *No, borrar* / *Sí, es mía*.
- 🔍 **Revisión completa** con puntaje de riesgo (🔴 peligrosa / 🟡 revisar).
- 💬 **Pedir ayuda por WhatsApp**: el cliente ve el mensaje antes de mandarlo.
- 🔊 Lee en voz alta, letra grande, pensada para adultos mayores.
- 🔒 **No tiene permiso de Internet**: no puede enviar datos a ningún lado. Solo guarda el nombre del cliente, en su celular.
- 🔐 **Activación por celular**: si copian el APK a otro celular, no funciona sin un código nuevo.

## Cómo conseguir el APK
Cada vez que se cambia algo en este repositorio, GitHub arma el APK solo.
Lo descargás desde **Releases → «Ultima version» → ServicellProtector.apk**.

## Cómo activar un celular
1. Instalá el APK en el celular del cliente.
2. La app muestra el **código del celular** (ej: `1FAQ-D7A9`).
3. Abrí el archivo `generador-codigos.html` en tu celular o compu, escribí ese código y te da el **código de activación**.
4. Escribilo en la app y listo.

## Cosas que podés cambiar (en `Config.kt`)
- `WHATSAPP`: el número del local.
- `VIRUS_CONOCIDOS`: nombres de paquete de virus que vayas encontrando.

🔑 La llave secreta y la firma de la app **no están en el código**: se guardan como *secretos* de GitHub
(`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `CLAVE_SECRETA`). Igual conviene que el repositorio sea **privado**.
El `generador-codigos.html` tiene la llave secreta: guardalo solo vos, nunca lo subas acá.
