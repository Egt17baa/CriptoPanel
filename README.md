# CriptoPanel

App Android (Kotlin + Jetpack Compose) con el mercado cripto y un panel DeFi en un solo sitio.

## Pantallas

- **Mercado**: capitalización total, TOTAL2 (sin BTC), TOTAL3 (sin BTC ni ETH), dominancia de BTC y ETH,
  volumen y cambio en 24 h. Se refresca sola cada 60 s; tira hacia abajo para forzarlo.
  Toca TOTAL, TOTAL2, TOTAL3 o una dominancia para ver su gráfico de velas de TradingView
  (CRYPTOCAP:TOTAL, TOTAL2, TOTAL3, BTC.D, ETH.D) en 1h, 2h, 4h, 12h, 1D o 1S.
- **Top 100**: las 100 primeras monedas por capitalización con precio, cambio 24 h, minigráfico de 7 días;
  filtro por nombre o símbolo y orden por posición, 24 h, 7 d o volumen.
- **Detalle de moneda**: gráfico de 24 h / 7 d / 30 d / 1 año, capitalización, volumen, máximos, oferta y descripción.
- **Buscar** (lupa en Mercado): cualquier moneda o token de CoinGecko.
- **DeFi**: TVL total con gráfico de un año, TVL por red (con % del total) y por protocolo.
  Al tocar una red: su histórico de TVL y sus protocolos.

## Fuentes de datos (públicas y gratuitas)

- [CoinLore API](https://www.coinlore.com/cryptocurrency-data-api) (`api.coinlore.net`): datos de Mercado
  (capitalización total, volumen, dominancia de BTC y ETH). Gratis, sin clave y sin límite estricto.
  TOTAL2 = TOTAL × (1 − dominancia BTC); TOTAL3 = TOTAL × (1 − dominancia BTC − dominancia ETH).
- [CoinGecko API](https://docs.coingecko.com/reference/introduction) (`api.coingecko.com/api/v3`): Top 100 con logos
  y minigráficos, detalle de monedas, gráficos y búsqueda. Si su plan gratuito está saturado, el Top 100 usa CoinLore.
- [TradingView](https://www.tradingview.com/widget-docs/widgets/charts/advanced-chart/): widget gratuito para los gráficos de índices.
- [DefiLlama API](https://api-docs.defillama.com/) (`api.llama.fi`): TVL por red, histórico y protocolos (sin exchanges centralizados).

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

- `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`: compila, 61 tests pasan, lint sin errores.
- Las respuestas reales de CoinGecko y DefiLlama se han comprobado contra los modelos de la app.
- Falta probarla en un móvil o emulador: instala el APK con `./gradlew installDebug` y revisa cada pantalla.
