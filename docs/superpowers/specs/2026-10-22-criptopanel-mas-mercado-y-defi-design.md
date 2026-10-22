# CriptoPanel: más mercado y más DeFi (diseño)

Fecha: 22 de octubre de 2026. Estado: pendiente de revisión.

## Contexto

CriptoPanel 1.0.0 tiene tres pestañas (Mercado, Top 100 y DeFi), además de Buscar, Detalle de moneda, Detalle de red y los gráficos de índices de TradingView. Los datos vienen de CoinLore, CoinGecko y DefiLlama, todas gratuitas y sin clave. La app no guarda nada en el móvil.

En el brainstorming se eligieron cinco áreas nuevas. Como son demasiado para un solo diseño, se reparten en subproyectos, cada uno con su especificación, su plan y su versión del APK:

1. **Este documento:** más datos de mercado y más DeFi (versión 1.1.0).
2. Favoritos y cartera: la primera vez que la app guarda datos en el móvil.
3. Alertas de precio: depende de los favoritos.
4. Sección educativa (pestaña «Aprende»): orígenes de Bitcoin, criptografía, wallets calientes frente a frías, etc. Son textos dentro de la app y no necesitan APIs.

## Decisiones tomadas en el brainstorming

- **Mercado:** miedo y codicia, tendencias, las que más suben y bajan, sectores y exchanges.
- **DeFi:** stablecoins, volumen de DEX, comisiones e ingresos y rendimientos (APY).
- **Navegación:** el contenido nuevo va dentro de Mercado y DeFi. La barra inferior se queda con tres pestañas, con hueco para «Aprende» y «Cartera».
- **Detalle:** no hay pantallas de detalle nuevas. Las monedas abren el Detalle de moneda que ya existe. El resto de elementos enseña sus datos en la propia fila y un enlace a su web.

## Fuentes de datos

Todas son públicas, gratuitas y sin clave. Se comprobaron el 22-10-2026 desde la red y respondieron 200.

| Contenido | Endpoint | Tamaño aprox. |
|---|---|---|
| Miedo y codicia | `https://api.alternative.me/fng/?limit=30` | 3 KB |
| Tendencias | CoinGecko `search/trending` | 70 KB |
| Suben y bajan | ninguno: se calcula con el Top 100 que ya se descarga | — |
| Sectores | CoinGecko `coins/categories` | 350 KB |
| Exchanges | CoinGecko `exchanges?per_page=50` | 25 KB |
| Stablecoins (lista) | `https://stablecoins.llama.fi/stablecoins?includePrices=true` | 550 KB |
| Stablecoins (histórico) | `https://stablecoins.llama.fi/stablecoincharts/all` | 1,3 MB |
| Volumen de DEX | `api.llama.fi/overview/dexs[/{red}]?excludeTotalDataChart=true&excludeTotalDataChartBreakdown=true` | 2,3 MB |
| Comisiones / ingresos | `api.llama.fi/overview/fees[/{red}]?…&dataType=dailyFees` o `dailyRevenue` | 5 MB |
| Rendimientos | `https://yields.llama.fi/pools` | 10,7 MB |

Hay dos bases nuevas para Retrofit: `api.alternative.me` y `stablecoins.llama.fi`. Además, `yields.llama.fi` va en otra interfaz porque cambia de host.

## Experiencia de uso

### Pestaña Mercado

La cuadrícula de métricas actual no cambia. Debajo se añaden, en este orden y cada una a todo el ancho:

1. **Tarjeta de miedo y codicia.** Muestra el valor de hoy (0–100), su clasificación en español (Miedo extremo 0–24, Miedo 25–44, Neutral 45–55, Codicia 56–75, Codicia extrema 76–100, los mismos tramos que alternative.me), la variación frente a ayer y un minigráfico de 30 días con el `LineChart` actual.
2. **Tarjeta «Suben y bajan (24 h)».** Tiene dos columnas con las 3 que más suben y las 3 que más bajan del Top 100, y un botón «Ver todo ›» que abre la pantalla *Suben y bajan*.
3. **Tarjeta «Tendencias».** Muestra las 3 primeras monedas en tendencia de CoinGecko y un botón «Ver todo ›» que abre la pantalla *Tendencias*.
4. **Dos filas de enlace:** «Sectores ›» y «Exchanges ›».

Si una tarjeta falla, muestra su propio aviso pequeño («Sin conexión», «No disponible ahora») y el resto de la pantalla sigue funcionando. Si su petición aún no ha terminado, la tarjeta muestra un esqueleto.

### Pantallas nuevas de Mercado

Todas tienen barra superior con «atrás», tirar para refrescar y las mismas vistas de carga y error que ya usa la app (`StatusViews`).

- **Suben y bajan:** dos pestañas, Suben y Bajan, con el Top 100 ordenado por cambio en 24 h. Las monedas sin dato de cambio se excluyen. Cada fila reutiliza la de Top 100 y al tocarla abre Detalle de moneda.
- **Tendencias:** hasta 15 monedas con logo, nombre, posición por capitalización, precio y cambio en 24 h. Al tocar una se abre Detalle de moneda.
- **Sectores:** lista ordenada por capitalización. Cada fila tiene nombre, capitalización, cambio en 24 h, volumen 24 h y los logos de sus 3 primeras monedas. Arriba hay un selector para ordenar por capitalización o por cambio en 24 h. Se ocultan los sectores sin capitalización.
- **Exchanges:** los 50 primeros por volumen de CoinGecko. Cada fila tiene logo, nombre, país, año, puntuación de confianza (1–10) y volumen en 24 h. CoinGecko da el volumen en BTC; se convierte a USD con el precio de BTC del Top 100 en caché y, si no está disponible, se muestra en ₿. Al tocar una fila se abre su web en el navegador.

### Pestaña DeFi

Arriba aparece una `ScrollableTabRow`: **TVL · Stablecoins · DEX · Comisiones · Rendimientos**. La pestaña TVL es el contenido actual de DeFi sin cambios, y Detalle de red sigue funcionando igual. Cada pestaña tiene su propio ViewModel y no pide nada hasta que se abre por primera vez. La pestaña elegida se conserva al cambiar de pestaña inferior y volver.

- **Stablecoins:** muestra la capitalización total de las stablecoins en USD, su cambio en 7 días y un gráfico de un año. Debajo va la lista ordenada por capitalización, con nombre, símbolo, capitalización, cambio en 24 h, mecanismo (fiat, cripto o algorítmico), número de redes y precio. El precio se marca en rojo si se aleja más de un 1 % de su paridad. Si la stablecoin tiene `gecko_id`, al tocarla se abre Detalle de moneda.
- **DEX:** muestra el volumen total en 24 h con su cambio en 1 día, 7 días y 30 días, y debajo la lista de protocolos ordenada por volumen en 24 h (logo, nombre, redes, volumen 24 h, cambio 1 d). Arriba hay un selector de red con «Todas» y las 15 primeras redes de `allChains`, que llama a `overview/dexs/{red}`. Al tocar un protocolo se abre `https://defillama.com/protocol/{slug}`.
- **Comisiones:** igual que DEX, más un interruptor *Comisiones / Ingresos* (`dataType=dailyFees` o `dailyRevenue`).
- **Rendimientos:** los 100 mejores pools tras aplicar filtros. Cada fila tiene proyecto, símbolo, red, APY (base + recompensas), TVL y si tiene riesgo de pérdida impermanente. Los filtros son «Solo stablecoins» (sí/no), TVL mínimo (1 M$ por defecto, 10 M$ o 100 M$) y orden por APY o por TVL. Siempre se excluyen los pools con `outlier == true`, los que no tienen APY y los que tienen un APY superior al 1.000 %. Al tocar un pool se abre `https://defillama.com/yields/pool/{pool}`. Debajo de los filtros aparece el texto «APY variable; no es una recomendación de inversión».

## Arquitectura

Se sigue el patrón actual: API Retrofit → repositorio con `TtlCache` → ViewModel con `StateFlow<UiState<T>>` → pantalla Compose. Las dependencias se crean en `AppContainer`.

Paquetes nuevos o ampliados:

```
market/   SentimentApi (alternative.me), FearGreed.kt, Movers.kt, Trending.kt,
          Categories.kt, Exchanges.kt + pantallas y ViewModels; MarketRepository crece
defi/     StablecoinsApi, YieldsApi, DefiLlamaApi (+ overview dexs/fees),
          Stablecoins.kt, DexVolume.kt, Fees.kt, Yields.kt + pestañas y ViewModels;
          DefiRepository crece (o se divide si pasa de ~200 líneas)
navigation/AppNavHost: rutas movers, trending, categories, exchanges
```

### Caché y peticiones

- `TtlCache` admite ya un TTL por instancia. Los datos nuevos usan **10 minutos**, salvo «suben y bajan», que reutiliza la caché de 60 s del Top 100.
- La tarjeta de tendencias no se refresca con el `AutoRefresh` de 60 s de Mercado. Usa su caché de 10 min, para no gastar el límite gratuito de CoinGecko, al que ya se piden Top 100 y gráficos.
- Al abrir Mercado se añaden como mucho 2 peticiones (miedo y codicia y tendencias). Top 100 suele venir ya de la caché.

### Respuestas grandes

Los endpoints de rendimientos (10,7 MB), comisiones (5 MB) y DEX (2,3 MB) no pasan por el convertidor de Retrofit, que convierte todo el cuerpo en un `String`. Se declaran como `@Streaming suspend fun …(): ResponseBody` y se leen con `appJson.decodeFromStream` sobre DTOs que solo tienen los campos usados (`ignoreUnknownKeys` ya está activo). El resultado se reduce enseguida (top 100 filtrado, top 100 protocolos) antes de guardarlo en caché. Así la memoria queda acotada y la caché solo guarda lo que se enseña. OkHttp descomprime gzip de forma transparente.

Para que el `readTimeout` de 30 s no corte la descarga de rendimientos en redes lentas, ese cliente usa un `callTimeout` de 60 s.

### Errores

`apiCall` y `AppError` no cambian. Cada tarjeta y cada pestaña gestiona su error por separado, y si hay datos anteriores se conservan, igual que en las pantallas actuales.

## Pruebas

Se sigue el estilo actual: JUnit 4 + coroutines-test + fixtures JSON reales recortados en `app/src/test/resources/fixtures/`. También deben pasar en `jvm-check/`.

- Mapeo de miedo y codicia: clasificación en español en los límites 24/25, 44/45, 55/56 y 75/76; `value` llega como texto.
- Suben y bajan: orden correcto, exclusión de cambios nulos, empates estables.
- Exchanges: conversión de BTC a USD, y respaldo en ₿ si no hay precio de BTC.
- Stablecoins: suma solo `peggedUSD`; desviación de paridad > 1 %; `gecko_id` nulo no rompe.
- DEX y comisiones: nombre de red con espacios bien codificado (`overview/dexs/OP%20Mainnet`); cambios nulos se muestran como `—`.
- Rendimientos: filtros (stablecoin, TVL mínimo, outlier, APY nulo y APY > 1.000 %), orden y límite de 100. El parseo por *streaming* se prueba con un fixture de varios pools servido por MockWebServer.
- ViewModels: la pestaña DeFi no pide nada hasta abrirse, y un fallo conserva los datos anteriores.
- Antes de dar por terminado: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug` en verde y el APK de la release `apk-latest` regenerado por el workflow.

## Fuera de alcance

- Pantallas de detalle de sector, exchange, stablecoin o pool.
- Guardar datos en el móvil (subproyecto 2), notificaciones (subproyecto 3) y la sección educativa (subproyecto 4).
- Otras monedas de referencia distintas de USD.

## Versión

`versionName` pasa a 1.1.0. En el README se añaden las secciones y fuentes nuevas, y se actualizan la ficha de Google Play (`docs/google-play/ficha-tienda.md`) y la política de privacidad, que deben nombrar alternative.me como nueva fuente.
