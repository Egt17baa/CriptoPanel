# CriptoPanel

App Android (Kotlin + Jetpack Compose) con el mercado cripto y un panel DeFi en un solo sitio.
Diseño Material 3 siempre oscuro, en gris y negro, con paneles de vidrio sobre un fondo «líquido» animado
(se queda quieto si el sistema tiene las animaciones desactivadas).

## Pantallas

- **Mercado**: capitalización total, TOTAL2 (sin BTC), TOTAL3 (sin BTC ni ETH), dominancia de BTC y ETH,
  volumen y cambio en 24 h. Se refresca sola cada 60 s; tira hacia abajo para forzarlo.
  Toca TOTAL, TOTAL2, TOTAL3 o una dominancia para ver su gráfico de velas de TradingView
  (CRYPTOCAP:TOTAL, TOTAL2, TOTAL3, BTC.D, ETH.D) en 1h, 2h, 4h, 12h, 1D o 1S.
  Debajo: índice de miedo y codicia (30 días), las que más suben y bajan en 24 h, tendencias de CoinGecko,
  y enlaces a **Sectores** (capitalización y cambio por sector) y **Exchanges** (los 50 con más volumen).
- **Top 100**: las 100 primeras monedas por capitalización con precio, cambio 24 h, minigráfico de 7 días;
  filtro por nombre o símbolo y orden por posición, 24 h, 7 d o volumen.
- **Detalle de moneda**: gráfico de 24 h / 7 d / 30 d / 1 año, capitalización, volumen, máximos, oferta y descripción.
- **Buscar** (lupa en Mercado): cualquier moneda o token de CoinGecko.
- **Cartera**: apunta compras y ventas (cantidad, precio, fecha, comisión y nota) y ve el valor total, la ganancia
  o pérdida por precio medio, el cambio de hoy, la evolución (7 d, 30 d, 1 año), el reparto y el historial.
  Exporta e importa en CSV. En **Favoritos** están las monedas marcadas con la ☆ en su detalle.
  Todo se guarda solo en el móvil (`files/portfolio.json`, incluido en la copia de seguridad de Android).
- **Alertas** (Cartera → Alertas, o 🔔 en el detalle de una moneda): precio sube o baja de un valor, cambio rápido
  (X % en 15 min, 1 h o 4 h), cambio en 24 h, y valor o cambio de hoy de tu cartera. Se vigilan **en directo** con el
  flujo público de Binance (`data-stream.binance.vision`, 1 actualización por segundo); las monedas que no cotizan
  en Binance se comprueban cada minuto con CoinGecko. Mientras haya alertas activas, un servicio en primer plano
  (`specialUse`) muestra una notificación fija silenciosa. Una alerta que salta se desactiva sola.
- **DeFi**, con pestañas:
  - **TVL**: total con gráfico de un año, TVL por red (con % del total) y por protocolo.
    Al tocar una red: su histórico de TVL y sus protocolos.
  - **Stablecoins**: capitalización total, cambio en 7 días y cada stablecoin (en rojo si se aleja más de un 1 % de su paridad).
  - **DEX**: volumen en 24 h, total y por protocolo, filtrable por red.
  - **Comisiones**: comisiones o ingresos en 24 h por protocolo, filtrables por red.
  - **Rendimientos**: los 100 mejores pools por APY o TVL, con filtros (solo stablecoins, TVL mínimo).

- **Más**: Comparador (hasta 4 monedas: tabla y rendimiento en base 100), Calculadora (cripto ⇄ USD y entre criptos),
  ¿Y si hubiera invertido? (de una vez o DCA mensual, último año), y Términos, Privacidad, Fuentes y Cómo calculamos.
- **Aprende** (en Más): 10 lecciones sin conexión (orígenes, blockchain, criptografía, wallets calientes y frías, Ethereum,
  stablecoins, DeFi, seguridad, cómo leer CriptoPanel) con resumen y mini-test, y un glosario de 42 términos.
- **Importar desde Binance** (Cartera → menú): el «Historial de operaciones» de spot o el «Historial de transacciones»
  (CSV o Excel, cabeceras en inglés o español). Pares contra stablecoins, euros o cripto, comisiones, Convert, compras
  con tarjeta y recompensas; precio en dólares de cada día (Binance, CoinGecko, BCE), sin duplicados al reimportar.
  Diseño en `docs/superpowers/specs/2026-10-22-criptopanel-importar-binance-design.md`.
- **Euros** (Más → Preferencias → Divisa): todo se ve en euros con el cambio del BCE (Frankfurter). La cartera usa
  el cambio del día de cada operación; los formularios se escriben en euros y se guardan en dólares.
- **Seguridad** (en Más): cifrar texto (`CP1:…`) y archivos (`.cpenc`, por bloques de 64 KiB) con AES-256-GCM,
  ChaCha20-Poly1305, Twofish, Serpent, Camellia o la cascada AES→Twofish→Serpent, con contraseña (Argon2id, 64 MiB,
  3 pasadas) o con una clave AES-256 aleatoria que guardas aparte; modo **frase semilla** con validación BIP-39; generador de contraseñas y frases (lista EFF) con entropía;
  comprobador de filtraciones (Have I Been Pwned, k-anonimato) y huella SHA-256 de archivos. Sin capturas de pantalla,
  portapapeles que se borra a los 45 s. **Bóveda** (contraseñas, frases semilla, notas y códigos 2FA): clave de datos
  aleatoria envuelta con Argon2id de la contraseña maestra y, opcionalmente, con una clave del Android Keystore que exige
  huella; se bloquea al salir y tras 5 min; fuera de la copia de Google, exportable cifrada. **Autenticador 2FA** (TOTP,
  RFC 6238) con escáner de QR. Formato y pruebas en `docs/superpowers/specs/2026-10-22-criptopanel-seguridad-design.md`.
- Al abrir la app por primera vez hay que aceptar los **términos** (`docs/google-play/terminos.md`). En Cartera, Alertas,
  Rendimientos y las herramientas se recuerda: *datos informativos, no asesoramiento financiero*.

## Fuentes de datos (públicas y gratuitas)

- [CoinLore API](https://www.coinlore.com/cryptocurrency-data-api) (`api.coinlore.net`): datos de Mercado
  (capitalización total, volumen, dominancia de BTC y ETH). Gratis, sin clave y sin límite estricto.
  TOTAL2 = TOTAL × (1 − dominancia BTC); TOTAL3 = TOTAL × (1 − dominancia BTC − dominancia ETH).
- [CoinGecko API](https://docs.coingecko.com/reference/introduction) (`api.coingecko.com/api/v3`): Top 100 con logos
  y minigráficos, detalle de monedas, gráficos y búsqueda. Si su plan gratuito está saturado, el Top 100 usa CoinLore.
- [TradingView](https://www.tradingview.com/widget-docs/widgets/charts/advanced-chart/): widget gratuito para los gráficos de índices.
- [DefiLlama API](https://api-docs.defillama.com/) (`api.llama.fi`, `stablecoins.llama.fi`, `yields.llama.fi`): TVL por red,
  histórico y protocolos (sin exchanges centralizados), stablecoins, volumen de DEX, comisiones e ingresos, y rendimientos.
- [Binance market data](https://developers.binance.com/docs/binance-spot-api-docs/web-socket-streams) (`data-api.binance.vision`, `data-stream.binance.vision`): precios en directo para las alertas.
- [Frankfurter](https://frankfurter.dev) (`api.frankfurter.dev`): tipo de cambio euro/dólar del BCE.
- [Alternative.me](https://alternative.me/crypto/fear-and-greed-index/) (`api.alternative.me`): índice de miedo y codicia.

El plan gratuito de CoinGecko limita las peticiones por minuto (y en redes móviles muchas personas comparten IP).
La app reintenta automáticamente, recurre a CoinLore cuando puede y nunca tapa los datos con un aviso de límite.

## Compilar

Requisitos: Android Studio (o el SDK de Android con `compileSdk 36`) y JDK 17+.

```bash
./gradlew assembleDebug      # APK en app/build/outputs/apk/debug/
./gradlew installDebug       # instala en un emulador o móvil conectado
./gradlew :app:testDebugUnitTest
```

### Clave de CoinGecko (opcional)

La app funciona sin clave. Si quieres más margen de peticiones, crea una clave Demo gratuita en CoinGecko
y añádela a `local.properties`:

```
COINGECKO_DEMO_KEY=tu_clave
```

## Publicar en Google Play

Guía paso a paso, textos de la ficha y política de privacidad en [`docs/google-play/`](docs/google-play/PUBLICAR.md).
Icono y gráfico destacado en [`store/`](store/).

```bash
./gradlew :app:bundleRelease -PversionCode=1   # AAB firmado con tu clave (keystore.properties)
```

## Tests sin SDK de Android

`jvm-check/` es un proyecto Gradle que compila la lógica de `app/src/main` (todo salvo las pantallas)
y ejecuta `app/src/test` solo con Maven Central:

```bash
gradle -p jvm-check test
```

## Estado de verificación

- Versión 1.8.0: `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease` compila, 222 tests pasan
  (también en `jvm-check`) y lint no da errores.
- Los fixtures de las fuentes nuevas son respuestas reales de CoinGecko, DefiLlama y Alternative.me (22-10-2026), recortadas.
- Falta probarla en un móvil o emulador, sobre todo el aspecto de vidrio líquido: instala el APK y revisa cada pantalla.

## Licencia

Código abierto bajo la **Apache License 2.0** (ver [`LICENSE`](LICENSE)); los avisos y las licencias de lo que se
incluye de terceros están en [`NOTICE`](NOTICE). Puedes usarlo, modificarlo y distribuirlo, también con fines
comerciales, conservando los avisos de copyright y de la licencia e indicando los cambios. Se ofrece **«tal cual»**,
sin garantías: no es asesoramiento financiero y el autor no responde de pérdidas derivadas de su uso (cláusulas 7 y 8
de la licencia).
