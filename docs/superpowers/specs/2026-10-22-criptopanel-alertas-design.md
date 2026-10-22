# CriptoPanel: alertas de precio en directo (diseño)

Fecha: 22 de octubre de 2026. Estado: pendiente de revisión. Subproyecto 3 de 4 (versión 1.3.0).

## Decisiones tomadas en el brainstorming

- **En directo:** el usuario quiere enterarse en el momento, no minutos después. Sin un servidor propio, la única forma en Android es un **servicio en primer plano** con una notificación fija («CriptoPanel vigila 3 alertas») mientras haya alertas activas.
- **Fuente de precios:** el flujo WebSocket público de Binance (gratis, sin clave), que da 1 actualización por segundo. Las monedas que no cotizan en Binance se comprueban **cada minuto** con CoinGecko.
- **Tipos de alerta:**
  1. el precio cruza un valor (sube de / baja de);
  2. cambio de X % en una ventana corta (15 min, 1 h o 4 h), al alza, a la baja o en cualquier sentido;
  3. cambio de X % en 24 h;
  4. la cartera: su valor sube de / baja de un importe, o cambia X % hoy.
- **Al saltar**, la alerta se desactiva sola y se reactiva con un toque. Así no llegan veinte avisos si el precio rebota alrededor del valor.
- **Aviso:** notificación de prioridad alta con sonido y vibración, que respeta el modo No molestar.
- **Ubicación:** tercera sección en la pestaña Cartera (**Cartera · Favoritos · Alertas**), más un botón 🔔 en el detalle de cada moneda.

## Experiencia de uso

### Sección Alertas

- **Lista de alertas** con su descripción («BTC sube de 100.000 $», «ETH ±5 % en 1 h», «Cartera baja de 10.000 $»), el precio actual en directo, un interruptor y la fecha del último aviso si ya saltó. Al tocar una alerta se edita; con la papelera se borra y se puede deshacer.
- Arriba, un **estado de la vigilancia**: «En directo (Binance) · 3 alertas», «Comprobando cada minuto» o «Detenida: sin conexión» (se reintenta solo).
- **Avisos de configuración**, solo cuando hace falta:
  - sin permiso de notificaciones (Android 13+): «Permite las notificaciones para recibir alertas» con el botón **Permitir**;
  - con optimización de batería activa: «Para que las alertas lleguen con la pantalla apagada, desactiva la optimización de batería» con el botón **Ajustes**, que abre la pantalla de Android correspondiente;
  - en Xiaomi, Redmi, POCO, Huawei, Honor, OPPO, realme, vivo y OnePlus (según `Build.MANUFACTURER`): una ayuda plegable que explica cómo activar el «Inicio automático» y quitar las restricciones de batería en su capa. No hay un ajuste estándar que la app pueda abrir.
- Un botón flotante **«+ Alerta»**.

### Crear o editar una alerta

1. **Qué vigilar:** una moneda (buscador, o ya elegida si se entra desde 🔔) o **Mi cartera**.
2. **Tipo:**
   - Moneda: *Precio sube de*, *Precio baja de*, *Cambio rápido* (X % en 15 min / 1 h / 4 h; al alza, a la baja o en cualquier sentido) o *Cambio en 24 h* (X %; al alza, a la baja o en cualquier sentido).
   - Cartera: *Valor sube de*, *Valor baja de* o *Cambio hoy* (X %; al alza, a la baja o en cualquier sentido).
3. **Valor:** importe en $ o porcentaje, con el precio o el valor actual a la vista como referencia.
4. **Nota** opcional, que se muestra en la notificación.

Validación:
- *Sube de* exige un valor por encima del precio actual y *baja de* uno por debajo; si no, saltaría al instante. Se avisa con «El precio ya está por encima».
- Los porcentajes deben estar entre 0,1 y 1.000.

### La notificación

- Título: «BTC ha subido de 100.000 $». Texto: «Precio: 100.214 $ · +2,1 % en 24 h» más la nota.
- Al tocarla se abre el detalle de la moneda (o la Cartera, si la alerta era de la cartera).
- Canal de notificaciones «Alertas de precio» con importancia alta. El usuario puede cambiar el sonido desde Android.

### La notificación fija del servicio

- «CriptoPanel vigila N alertas · En directo», con la acción **Pausar** (detiene la vigilancia hasta que se vuelve a la app) y un toque que abre la sección Alertas.
- Canal «Vigilancia de alertas» con importancia mínima: sin sonido y plegada.
- El servicio solo funciona mientras haya alertas activas. Al apagar la última, se detiene y la notificación desaparece.

## Funcionamiento

### Fuentes de precio

- **Binance:** `wss://data-stream.binance.vision/stream?streams=<sym>usdt@miniTicker/...`. Es el endpoint solo de datos de mercado, sin restricciones geográficas en la compra-venta, y se ha probado desde aquí. Cada mensaje trae el precio actual (`c`) y el de hace 24 h (`o`).
- **Correspondencia de monedas** (id de CoinGecko → par de Binance):
  1. se propone `SYMBOL + "USDT"`;
  2. se comprueba que existe con `GET data-api.binance.vision/api/v3/ticker/price?symbols=[...]`;
  3. se acepta solo si el precio de Binance difiere menos de un 3 % del de CoinGecko. Esto evita confundir monedas con el mismo símbolo.

  La correspondencia se guarda en caché 24 h. Las stablecoins con paridad en USD (USDT, USDC…) y las monedas que no pasan la comprobación usan el respaldo de CoinGecko.
- **Respaldo:** cada 60 s, una petición `coins/markets?ids=…` con todas las monedas que no van por Binance, que también da su cambio en 24 h.
- **Alertas de la cartera:** su valor se recalcula con cada precio nuevo de cualquiera de sus monedas, usando las cantidades actuales de `PortfolioStore`.

### Evaluación

- **Cruce de valor:** salta cuando el precio pasa al otro lado del umbral. Se compara con el último precio visto, no solo con «está por encima», para que una alerta creada con el precio ya pasado no salte de forma absurda (además, el formulario lo impide).
- **Cambio rápido:** cada moneda guarda en memoria un histórico de los precios de las últimas 4 h, con 1 muestra cada 10 s como máximo. El cambio se calcula frente al precio más antiguo dentro de la ventana. Hasta que no hay un histórico que cubra la ventana, la alerta muestra «Calentando: faltan 20 min».
- **Cambio en 24 h:** con el `o` de Binance, o con el cambio en 24 h de CoinGecko.
- **Cartera, cambio hoy:** la misma fórmula que el resumen de la cartera.
- Al saltar, la alerta se guarda con `enabled = false` y `lastTriggered = ahora` **antes** de notificar. Así, aunque el servicio muera justo después, no se repite.

### Ciclo de vida del servicio

- Tipo `specialUse` (con la propiedad `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` = «Alertas de precio de criptomonedas en tiempo real elegidas por el usuario»).
  - `dataSync` no sirve: en Android 15 solo puede funcionar 6 h al día.
  - Para publicar en Google Play habrá que justificar este uso en la Play Console; el APK de pruebas no lo necesita.
- **Cuándo arranca:** al activar una alerta, al abrir la app si hay alertas activas, y al encender el móvil (`BOOT_COMPLETED`). En Android 15 `specialUse` sí puede arrancar desde el arranque del sistema.
- **Reconexión:** WebSocket con espera exponencial (1, 2, 4… hasta 60 s) y reconexión inmediata cuando Android avisa de que vuelve la red (`ConnectivityManager.NetworkCallback`). Binance cierra las conexiones a las 24 h, y la app se reconecta sola.
- Los cambios de las alertas se aplican al momento: el servicio observa el almacén de alertas y se vuelve a suscribir a los pares que hagan falta.

## Datos

- `files/alerts.json`, con el mismo patrón que `PortfolioStore` (escritura atómica, archivo dañado guardado como `.bad`). Se incluye en la copia de seguridad.
- **Alerta:** `id`, `target` (moneda: `coinId`, `symbol`, `name`, `imageUrl`; o cartera), `kind`, `threshold`, `window` (solo en el cambio rápido), `direction` (al alza, a la baja o en cualquier sentido), `note`, `enabled`, `createdAt` y `lastTriggered`.

## Permisos nuevos

`POST_NOTIFICATIONS`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` y `RECEIVE_BOOT_COMPLETED`.

No se pide `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, porque Google Play lo restringe: se abre la pantalla de ajustes para que el usuario lo cambie.

## Pruebas

Tests de JVM:

- **Evaluación:** cruce en las dos direcciones; un precio igual al umbral no salta; cambio rápido con un histórico incompleto («calentando») y completo; cambio en 24 h; cartera.
- **Histórico de precios:** se descartan las muestras de más de 4 h y no se guarda más de 1 muestra cada 10 s.
- **Correspondencia con Binance:** coincidencia aceptada; símbolo con otro precio (más de un 3 % de diferencia) rechazado; stablecoins al respaldo.
- **Mensajes de `miniTicker`:** se interpretan bien y un mensaje roto se ignora.
- **Alerta que salta:** se guarda desactivada antes de avisar y no se repite.
- **`AlertStore`:** se guarda y se vuelve a leer, y un archivo dañado no rompe la app.
- **Formulario:** validación de un umbral ya superado y de los porcentajes.

La parte que solo existe en Android (servicio, notificaciones, arranque) se verifica a mano en el móvil: crear una alerta cerca del precio actual y comprobar que llega en segundos, con la pantalla apagada y tras reiniciar el móvil.

## Privacidad

La política de privacidad debe decir que la app se conecta a Binance (solo para leer precios públicos) y que las alertas se guardan solo en el dispositivo.

## Fuera de alcance

- Notificaciones push desde un servidor y alertas con la app forzada a detenerse.
- Indicadores técnicos (RSI, medias) y alertas de DeFi (TVL, APY).
- Avisos tipo alarma a pantalla completa.
