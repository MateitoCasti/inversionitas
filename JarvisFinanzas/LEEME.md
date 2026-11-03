# Jarvis Finanzas — app de Android de uso personal

## Qué hace
- **Hoy:** valor de tu cartera de IOL, cómo viene el día, dólar, riesgo país, Merval, S&P 500, Bitcoin y noticias del día con su impacto (marca las que tocan tu cartera).
- **Cartera:** tus posiciones reales de IOL (acciones, CEDEARs, bonos, letras, ON, FCI, cauciones), plata disponible, ganancia y distribución. Alertas de precio.
- **Mercado:** todos los dólares, índices de EE.UU., commodities y cripto, plazo fijo e inflación, panel líder, CEDEARs, ETFs, bonos y tu lista de seguimiento.
- **Análisis:** análisis automático (distribución, concentración, pesos contra inflación, alertas). Opcional: análisis con Gemini usando tu clave gratuita.
- **En segundo plano:** cada ~15 minutos, en días hábiles de 10 a 18, revisa tus alertas y los movimientos fuertes de tu cartera y te avisa con una notificación. Cada día hábil desde las 9 te manda un resumen.

## Cómo instalarla
1. Descomprimí la carpeta `JarvisFinanzas` en tu compu.
2. En Android Studio: **File → Open** y elegí la carpeta `JarvisFinanzas`. Esperá que termine el "Gradle sync" (la primera vez descarga cosas y tarda unos minutos).
   - Si Android Studio ofrece actualizar el plugin de Android o Gradle, podés aceptar.
3. En el teléfono activá las **Opciones de desarrollador** (Ajustes → Acerca del teléfono → tocá 7 veces "Número de compilación") y adentro activá **Depuración USB**.
4. Conectá el teléfono por USB, aceptá el aviso de "Permitir depuración" y en Android Studio tocá **Run ▶** con tu teléfono elegido arriba.
5. La app queda instalada como "Jarvis". Ya no hace falta la compu.

Para generar un APK y pasarlo por WhatsApp/Drive: **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. El archivo queda en `app/build/outputs/apk/debug/app-debug.apk`.

## Primer uso
1. Abrí **Ajustes**, poné tu usuario y contraseña de IOL y tocá **Guardar**, después **Probar conexión**.
   - Si IOL rechaza el acceso, entrá a tu cuenta de IOL desde la web y fijate en la configuración de tu cuenta que tengas habilitado el uso de la API.
2. (Opcional) Sacá una clave gratis en https://aistudio.google.com/apikey y pegala en "Análisis con IA".
3. Aceptá el permiso de notificaciones para recibir alertas.

## Privacidad
Usuario, contraseña y clave se guardan **cifrados solo en tu teléfono**. La app habla directo con IOL, DolarApi, ArgentinaDatos, Yahoo Finance, CoinGecko, Google Noticias y (si la activás) Gemini. No hay ningún servidor intermedio.

## Fuentes
IOL API (api.invertironline.com) · dolarapi.com · api.argentinadatos.com · Yahoo Finance · CoinGecko · Google Noticias · Gemini (opcional).

Jarvis informa y analiza; no es asesoramiento financiero.
