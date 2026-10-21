# CriptoPanel (Android) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Una app Android que reúne en un solo sitio el mercado cripto (capitalización total, TOTAL2, TOTAL3, dominancia de BTC, top 100 por capitalización, precio de cualquier moneda) y un panel DeFi tipo DefiLlama (TVL total, TVL por red y por protocolo).

**Architecture:** Un solo módulo `app` en Kotlin + Jetpack Compose, organizado por funcionalidad (`market`, `coins`, `search`, `defi`) más un paquete `core` compartido. Cada funcionalidad tiene su API Retrofit, un repositorio con caché en memoria y un ViewModel que expone `StateFlow<UiState<T>>`. Las dependencias se crean a mano en un `AppContainer` (sin Hilt).

**Tech Stack:** Kotlin 2.x, Jetpack Compose + Material 3, Navigation Compose, Retrofit + kotlinx.serialization, OkHttp, Coil 3 (logos), coroutines. Tests: JUnit 4, kotlinx-coroutines-test, OkHttp MockWebServer, Turbine.

**Spec:** No hay documento de diseño: el usuario se saltó el brainstorming. Los requisitos son los de su mensaje: *"una aplicación en Android para criptomonedas: ver el TOTAL2, el TOTAL3, la dominancia de bitcoin, una especie de DefiLlama donde ves cada moneda, cada red, el TVL de todas las redes, cuánto cuesta ahora mismo cada token, las 100 monedas del top por capitalización… muy completa"*, más su aclaración: *"con APIs públicas y gratuitas"*. Todo lo demás de este plan son decisiones de Claude y se pueden cambiar.

## Global Constraints

- Android: `minSdk 26`, `compileSdk 36`, `targetSdk 36`. Paquete `app.criptopanel`, nombre visible "CriptoPanel".
- Solo APIs públicas y gratuitas, sin registro: CoinGecko `https://api.coingecko.com/api/v3/` y DefiLlama `https://api.llama.fi/`. La clave Demo gratuita de CoinGecko es opcional: si `local.properties` tiene `COINGECKO_DEMO_KEY`, se envía en la cabecera `x-cg-demo-api-key`; si está vacía, no se envía ninguna cabecera.
- Moneda de referencia: USD.
- Definiciones (iguales a TradingView): `TOTAL` = `total_market_cap.usd` de `/global`; `capBTC = TOTAL × market_cap_percentage.btc / 100`; `capETH = TOTAL × market_cap_percentage.eth / 100`; `TOTAL2 = TOTAL − capBTC`; `TOTAL3 = TOTAL − capBTC − capETH`; dominancia BTC = `market_cap_percentage.btc`.
- TVL DeFi total = el `tvl` del último punto de `/v2/historicalChainTvl`. En las listas de protocolos se excluye `category == "CEX"`.
- Textos de la interfaz en español. Los números usan el `Locale` del dispositivo, y los tests fijan `Locale("es", "ES")`.
- Caché: cada endpoint se pide como mucho una vez cada 60 s, salvo que el usuario fuerce el refresco tirando hacia abajo (`force = true`). Mientras la pantalla está visible, se refresca sola cada 60 s.
- Un valor nulo o ausente nunca rompe la app: se muestra como `—`.

## Review Focus

1. **CoinGecko devuelve HTTP 429 (límite del plan gratuito):** se siguen viendo los últimos datos y aparece el aviso "Límite de la API alcanzado, reintenta en un minuto". Lo prueban la Tarea 2 (`apiCall` convierte el 429 en `AppError.RateLimited`) y la Tarea 4 (el ViewModel conserva los datos cuando falla).
2. **Sin conexión:** se conservan los últimos datos y aparece "Sin conexión". Lo prueban la Tarea 2 (`IOException` se convierte en `AppError.Offline`) y la Tarea 4.
3. **Precios diminutos o enormes** (SHIB a 0,00001234 $, BTC a 98 000 $): nunca se ve "0,00 $". Lo prueba la Tarea 1.
4. **Campos nulos en monedas** (`max_supply`, `market_cap`, `price_change_percentage_*`, sin `sparkline_in_7d`): se procesan sin fallar. Lo prueba la Tarea 3 con un fixture que lleva esos campos a `null`.
5. **Nombres de red con espacios** ("OP Mainnet"): la URL se codifica bien. Lo prueba la Tarea 8 comprobando la ruta `/v2/historicalChainTvl/OP%20Mainnet`.

---

## File Structure

```
settings.gradle.kts, build.gradle.kts, gradle/libs.versions.toml, README.md
app/build.gradle.kts
app/src/main/AndroidManifest.xml                  INTERNET permission, MainActivity
app/src/main/java/app/criptopanel/
  CriptoPanelApp.kt                               Application; owns AppContainer
  AppContainer.kt                                 builds OkHttp, Retrofit APIs, repositories
  MainActivity.kt                                 setContent { AppNavHost() }
  navigation/AppNavHost.kt                        bottom bar (Mercado, Top 100, DeFi) + routes
  core/format/Formatters.kt                       formatUsd, formatCompactUsd, formatPercent
  core/network/Http.kt                            okHttpClient(), retrofit(baseUrl), Json config
  core/network/Outcome.kt                         Outcome, AppError, apiCall()
  core/ui/UiState.kt                              UiState<T>, AppError.message()
  core/ui/LineChart.kt                            Canvas line chart (sparkline + history)
  core/ui/AutoRefresh.kt                          lifecycle-aware 60 s refresh effect
  core/ui/StatusViews.kt                          ErrorBanner, LoadingBox
  market/CoinGeckoApi.kt                          all CoinGecko endpoints
  market/CoinGeckoDtos.kt                         @Serializable DTOs
  market/GlobalMetrics.kt                         domain + GlobalDto.toMetrics()
  market/MarketRepository.kt                      cached global + top coins + detail + search
  market/OverviewViewModel.kt, OverviewScreen.kt
  coins/CoinMarket.kt                             domain + mapper
  coins/TopCoins.kt                               SortKey, filterAndSort()
  coins/TopCoinsViewModel.kt, TopCoinsScreen.kt
  coins/CoinDetail.kt, ChartRange.kt              domain + mappers
  coins/CoinDetailViewModel.kt, CoinDetailScreen.kt
  search/SearchViewModel.kt, SearchScreen.kt
  defi/DefiLlamaApi.kt, DefiLlamaDtos.kt
  defi/DefiModels.kt                              ChainTvl, TvlPoint, Protocol + mappers
  defi/DefiRepository.kt
  defi/DefiViewModel.kt, DefiScreen.kt
  defi/ChainDetailViewModel.kt, ChainDetailScreen.kt
app/src/test/java/app/criptopanel/...             mirrors main packages
app/src/test/resources/fixtures/*.json            trimmed real API responses
```

---

### Task 1: Proyecto base y formateo de números

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/app/criptopanel/MainActivity.kt` (placeholder `Text("CriptoPanel")`), `README.md`
- Create: `app/src/main/java/app/criptopanel/core/format/Formatters.kt`
- Test: `app/src/test/java/app/criptopanel/core/format/FormattersTest.kt`

**Interfaces:**
- Produces: `fun formatUsd(value: Double?, locale: Locale = Locale.getDefault()): String`, `fun formatCompactUsd(value: Double?, locale: Locale = Locale.getDefault()): String`, `fun formatPercent(value: Double?, locale: Locale = Locale.getDefault()): String`

- [ ] **Step 1: Create the Gradle project** with the Global Constraints SDK values, the Compose compiler plugin, kotlinx.serialization plugin and every library from Tech Stack in `libs.versions.toml` (latest stable versions, pinned). Add the Gradle wrapper (`gradle wrapper`). `README.md` says how to build (`./gradlew assembleDebug`) and how to set `COINGECKO_DEMO_KEY`.
- [ ] **Step 2: Verify it builds**

Run: `./gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Write the failing test**

```kotlin
private val es = Locale("es", "ES")

@Test fun `usd above one uses two decimals`() = assertEquals("98.123,46 $", formatUsd(98123.456, es))
@Test fun `usd below one keeps four significant digits`() = assertEquals("0,00001234 $", formatUsd(0.000012341, es))
@Test fun `usd between cents`() = assertEquals("0,5432 $", formatUsd(0.54321, es))
@Test fun `null is a dash`() { assertEquals("—", formatUsd(null, es)); assertEquals("—", formatPercent(null, es)); assertEquals("—", formatCompactUsd(null, es)) }
@Test fun `compact suffixes`() {
    assertEquals("3,21 T $", formatCompactUsd(3.214e12, es))
    assertEquals("945,00 B $", formatCompactUsd(9.45e11, es))
    assertEquals("12,30 M $", formatCompactUsd(1.23e7, es))
    assertEquals("999,00 $", formatCompactUsd(999.0, es))
}
@Test fun `percent has sign`() { assertEquals("+2,35 %", formatPercent(2.345, es)); assertEquals("-0,50 %", formatPercent(-0.5, es)) }
```

- [ ] **Step 4: Run test to verify it fails**

Run: `./gradlew :app:testDebugUnitTest --tests '*FormattersTest'`
Expected: FAIL (unresolved reference `formatUsd`)

- [ ] **Step 5: Implement the three functions in `Formatters.kt`** using `NumberFormat`/`DecimalFormat` for `locale`, with the symbol written as `" $"` after the number. Suffixes: `K` from 1e3, `M` from 1e6, `B` from 1e9, `T` from 1e12, always 2 decimals. Values below 1 use `BigDecimal.round(MathContext(4))` and print no trailing exponent.
- [ ] **Step 6: Run test to verify it passes** (same command, expected PASS)
- [ ] **Step 7: Commit**: `git commit -m "feat: project scaffold and number formatters"`

---

### Task 2: Red base y métricas globales (TOTAL, TOTAL2, TOTAL3, dominancia)

**Files:**
- Create: `core/network/Http.kt`, `core/network/Outcome.kt`, `market/CoinGeckoApi.kt`, `market/CoinGeckoDtos.kt`, `market/GlobalMetrics.kt`
- Test: `core/network/OutcomeTest.kt`, `market/GlobalMetricsTest.kt`, fixture `fixtures/cg_global.json`

**Interfaces:**
- Produces:
  - `fun okHttpClient(demoKey: String): OkHttpClient` (adds the `x-cg-demo-api-key` header only when `demoKey` is not blank, and only for host `api.coingecko.com`)
  - `fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit` (kotlinx.serialization, `Json { ignoreUnknownKeys = true; coerceInputValues = true; explicitNulls = false }`)
  - `sealed interface AppError { data object RateLimited; data object Offline; data class Http(val code: Int); data class Unknown(val cause: Throwable) }`
  - `sealed interface Outcome<out T> { data class Ok<T>(val value: T); data class Err(val error: AppError) }`
  - `suspend fun <T> apiCall(block: suspend () -> T): Outcome<T>` (`HttpException` 429 → `RateLimited`, other `HttpException` → `Http(code)`, `IOException` → `Offline`, anything else except `CancellationException` → `Unknown`)
  - `interface CoinGeckoApi { @GET("global") suspend fun global(): GlobalEnvelopeDto }`
  - `data class GlobalMetrics(val totalCap: Double, val total2: Double, val total3: Double, val btcDominance: Double, val ethDominance: Double, val volume24h: Double, val capChange24hPct: Double?)`
  - `fun GlobalDataDto.toMetrics(): GlobalMetrics`

- [ ] **Step 1: Write the failing tests**

```kotlin
// GlobalMetricsTest: MockWebServer serves fixtures/cg_global.json, where
// total_market_cap.usd = 3.0e12, market_cap_percentage.btc = 56.5, eth = 12.0,
// total_volume.usd = 1.2e11, market_cap_change_percentage_24h_usd = -1.25
@Test fun `computes total2 and total3`() = runTest {
    val m = api.global().data.toMetrics()
    assertEquals(3.0e12, m.totalCap, 1.0)
    assertEquals(1.305e12, m.total2, 1.0)
    assertEquals(0.945e12, m.total3, 1.0)
    assertEquals(56.5, m.btcDominance, 1e-9)
    assertEquals(-1.25, m.capChange24hPct!!, 1e-9)
    assertEquals("/global", server.takeRequest().path)
}

// OutcomeTest
@Test fun `429 is RateLimited`() = runTest { assertEquals(Outcome.Err(AppError.RateLimited), apiCall { throw HttpException(Response.error<Any>(429, "".toResponseBody())) }) }
@Test fun `io is Offline`() = runTest { assertEquals(Outcome.Err(AppError.Offline), apiCall { throw IOException() }) }
@Test fun `ok wraps value`() = runTest { assertEquals(Outcome.Ok(1), apiCall { 1 }) }
@Test fun `demo key header only when set`() { /* request through okHttpClient("") to MockWebServer has no x-cg-demo-api-key header */ }
```

- [ ] **Step 2: Run tests to verify they fail**: `./gradlew :app:testDebugUnitTest --tests '*GlobalMetricsTest' --tests '*OutcomeTest'` → FAIL (unresolved references)
- [ ] **Step 3: Implement** the Produces list. DTO field names follow the JSON (`@SerialName("total_market_cap") val totalMarketCap: Map<String, Double>` etc.).
- [ ] **Step 4: Run tests to verify they pass** (same command, PASS)
- [ ] **Step 5: Commit**: `git commit -m "feat: network core and global market metrics"`

---

### Task 3: Top 100 y repositorio de mercado con caché

**Files:**
- Modify: `market/CoinGeckoApi.kt`, `market/CoinGeckoDtos.kt`
- Create: `coins/CoinMarket.kt`, `market/MarketRepository.kt`
- Test: `market/MarketRepositoryTest.kt`, fixture `fixtures/cg_markets.json` (3 coins: bitcoin, ethereum, and one where `market_cap`, `max_supply`, `price_change_percentage_24h_in_currency` and `sparkline_in_7d` are `null`/absent)

**Interfaces:**
- Consumes: `apiCall`, `Outcome`, `CoinGeckoApi`, `GlobalMetrics` (Task 2)
- Produces:
  - `@GET("coins/markets") suspend fun markets(@Query("vs_currency") vs: String = "usd", @Query("order") order: String = "market_cap_desc", @Query("per_page") perPage: Int = 100, @Query("page") page: Int = 1, @Query("sparkline") sparkline: Boolean = true, @Query("price_change_percentage") changes: String = "1h,24h,7d"): List<CoinMarketDto>`
  - `data class CoinMarket(val id: String, val rank: Int?, val symbol: String, val name: String, val imageUrl: String?, val price: Double?, val marketCap: Double?, val volume24h: Double?, val change1h: Double?, val change24h: Double?, val change7d: Double?, val sparkline7d: List<Double>)` (`symbol` upper-cased, `sparkline7d` empty when missing)
  - `fun CoinMarketDto.toDomain(): CoinMarket`
  - `class MarketRepository(private val api: CoinGeckoApi, private val now: () -> Long = System::currentTimeMillis)` with `suspend fun global(force: Boolean = false): Outcome<GlobalMetrics>` and `suspend fun topCoins(force: Boolean = false): Outcome<List<CoinMarket>>` (each cached 60 000 ms; an `Err` never replaces the cache)

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `parses coins including nulls`() = runTest {
    val coins = (repo.topCoins() as Outcome.Ok).value
    assertEquals(3, coins.size); assertEquals("BTC", coins[0].symbol)
    assertNull(coins[2].marketCap); assertNull(coins[2].change24h); assertEquals(emptyList<Double>(), coins[2].sparkline7d)
}
@Test fun `uses cache within 60s`() = runTest { repo.topCoins(); clock += 59_000; repo.topCoins(); assertEquals(1, server.requestCount) }
@Test fun `refetches after 60s or when forced`() = runTest { repo.topCoins(); repo.topCoins(force = true); clock += 61_000; repo.topCoins(); assertEquals(3, server.requestCount) }
@Test fun `request has top 100 query`() = runTest { repo.topCoins(); assertTrue(server.takeRequest().path!!.contains("per_page=100")) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*MarketRepositoryTest'` → FAIL
- [ ] **Step 3: Implement** the Produces list. The cache is a private `data class Cached<T>(val value: T, val at: Long)` per endpoint, behind a `Mutex`.
- [ ] **Step 4: Run** (same command) → PASS
- [ ] **Step 5: Commit**: `git commit -m "feat: top 100 coins and cached market repository"`

---

### Task 4: Estructura de la app y pantalla "Mercado"

**Files:**
- Create: `CriptoPanelApp.kt`, `AppContainer.kt`, `navigation/AppNavHost.kt`, `core/ui/UiState.kt`, `core/ui/AutoRefresh.kt`, `core/ui/StatusViews.kt`, `market/OverviewViewModel.kt`, `market/OverviewScreen.kt`
- Modify: `MainActivity.kt`, `AndroidManifest.xml` (`android:name=".CriptoPanelApp"`)
- Test: `market/OverviewViewModelTest.kt`

**Interfaces:**
- Consumes: `MarketRepository.global` (Task 3), formatters (Task 1)
- Produces:
  - `data class UiState<T>(val data: T? = null, val loading: Boolean = false, val error: AppError? = null)`
  - `fun AppError.message(): String`: `RateLimited` → "Límite de la API alcanzado, reintenta en un minuto", `Offline` → "Sin conexión", `Http` → "Error del servidor (código)", `Unknown` → "Error inesperado"
  - `@Composable fun AutoRefresh(intervalMs: Long = 60_000, onRefresh: () -> Unit)`: calls `onRefresh` right away, then every `intervalMs` while the lifecycle is `STARTED`
  - `class OverviewViewModel(private val repo: MarketRepository) : ViewModel` with `val state: StateFlow<UiState<GlobalMetrics>>` and `fun refresh(force: Boolean = false)`
  - `class AppContainer(demoKey: String)` exposing `marketRepository`, plus `defiRepository` (added in Task 8)
  - Routes: `"market"`, `"top"`, `"defi"` (bottom bar), and later `"coin/{id}"`, `"search"`, `"chain/{name}"`

- [ ] **Step 1: Write the failing tests** (fake `CoinGeckoApi` that returns or throws on demand; `Dispatchers.setMain(StandardTestDispatcher())`; Turbine)

```kotlin
@Test fun `refresh loads metrics`() = runTest { vm.refresh(); advanceUntilIdle(); assertEquals(1.305e12, vm.state.value.data!!.total2, 1.0); assertFalse(vm.state.value.loading) }
@Test fun `error keeps previous data`() = runTest {
    vm.refresh(); advanceUntilIdle()
    fake.failWith(IOException()); vm.refresh(force = true); advanceUntilIdle()
    assertNotNull(vm.state.value.data); assertEquals(AppError.Offline, vm.state.value.error)
}
@Test fun `rate limit message`() = assertEquals("Límite de la API alcanzado, reintenta en un minuto", AppError.RateLimited.message())
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*OverviewViewModelTest'` → FAIL
- [ ] **Step 3: Implement** the Produces list. `OverviewScreen` shows pull-to-refresh (`PullToRefreshBox` → `refresh(force = true)`), `AutoRefresh { vm.refresh() }`, `ErrorBanner` when `error != null`, and a 2-column grid of cards: "Capitalización total", "TOTAL2 (sin BTC)", "TOTAL3 (sin BTC ni ETH)", "Dominancia BTC", "Dominancia ETH", "Volumen 24 h", "Cambio 24 h" (green when positive, red when negative). A search icon in the top bar navigates to `"search"`.
- [ ] **Step 4: Run** the tests → PASS, then `./gradlew assembleDebug` → `BUILD SUCCESSFUL`
- [ ] **Step 5: Commit**: `git commit -m "feat: app shell and market overview screen"`

---

### Task 5: Pantalla "Top 100"

**Files:**
- Create: `coins/TopCoins.kt`, `coins/TopCoinsViewModel.kt`, `coins/TopCoinsScreen.kt`, `core/ui/LineChart.kt`
- Test: `coins/TopCoinsTest.kt`

**Interfaces:**
- Consumes: `MarketRepository.topCoins`, `CoinMarket` (Task 3), `UiState`, `AutoRefresh` (Task 4)
- Produces:
  - `enum class SortKey { RANK, CHANGE_24H, CHANGE_7D, VOLUME }`
  - `fun filterAndSort(coins: List<CoinMarket>, query: String, sort: SortKey): List<CoinMarket>`: the query matches `name` or `symbol`, case-insensitive, after trimming; `RANK` ascending; the others descending; nulls always last
  - `@Composable fun LineChart(points: List<Double>, modifier: Modifier, color: Color, fill: Boolean = false)`: draws nothing when there are fewer than 2 points
  - `class TopCoinsViewModel(repo: MarketRepository)` with `state: StateFlow<UiState<List<CoinMarket>>>` (already filtered and sorted), `fun setQuery(q: String)`, `fun setSort(s: SortKey)`, `fun refresh(force: Boolean = false)`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `filters by symbol ignoring case`() = assertEquals(listOf("ethereum"), filterAndSort(coins, " eth ", SortKey.RANK).map { it.id })
@Test fun `sorts by 24h change desc with nulls last`() = assertEquals(listOf("ethereum", "bitcoin", "nullcoin"), filterAndSort(coins, "", SortKey.CHANGE_24H).map { it.id })
@Test fun `empty query returns all by rank`() = assertEquals(listOf(1, 2, null), filterAndSort(coins, "", SortKey.RANK).map { it.rank })
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*TopCoinsTest'` → FAIL
- [ ] **Step 3: Implement** the Produces list. The screen has a search field, sort chips, and a `LazyColumn` with one row per coin: rank, logo (Coil `AsyncImage`), name + symbol, price, 24 h %, 7-day sparkline (green when the last point ≥ the first), and market cap in compact form. Tapping a row navigates to `"coin/{id}"`.
- [ ] **Step 4: Run** the tests → PASS, then `./gradlew assembleDebug` → `BUILD SUCCESSFUL`
- [ ] **Step 5: Commit**: `git commit -m "feat: top 100 coins screen with filter, sort and sparklines"`

---

### Task 6: Detalle de moneda con gráfico

**Files:**
- Modify: `market/CoinGeckoApi.kt`, `market/CoinGeckoDtos.kt`, `market/MarketRepository.kt`
- Create: `coins/CoinDetail.kt`, `coins/ChartRange.kt`, `coins/CoinDetailViewModel.kt`, `coins/CoinDetailScreen.kt`
- Test: `coins/CoinDetailTest.kt`, fixtures `fixtures/cg_coin_bitcoin.json`, `fixtures/cg_market_chart.json`

**Interfaces:**
- Consumes: `apiCall`, `Outcome` (Task 2), `LineChart` (Task 5), `UiState` (Task 4)
- Produces:
  - `@GET("coins/{id}") suspend fun coin(@Path("id") id: String, @Query("localization") l: Boolean = false, @Query("tickers") t: Boolean = false, @Query("community_data") c: Boolean = false, @Query("developer_data") d: Boolean = false): CoinDetailDto`
  - `@GET("coins/{id}/market_chart") suspend fun marketChart(@Path("id") id: String, @Query("days") days: String, @Query("vs_currency") vs: String = "usd"): MarketChartDto`
  - `data class CoinDetail(val id: String, val name: String, val symbol: String, val imageUrl: String?, val rank: Int?, val price: Double?, val marketCap: Double?, val volume24h: Double?, val high24h: Double?, val low24h: Double?, val ath: Double?, val circulatingSupply: Double?, val totalSupply: Double?, val maxSupply: Double?, val description: String)` (the description is `description.en` with HTML tags removed)
  - `enum class ChartRange(val days: String, val label: String) { D1("1", "24 h"), W1("7", "7 d"), M1("30", "30 d"), Y1("365", "1 año") }`
  - `MarketRepository.coin(id: String): Outcome<CoinDetail>` and `MarketRepository.chart(id: String, range: ChartRange): Outcome<List<Double>>` (prices only, cached 60 s per id+range)
  - `class CoinDetailViewModel(id: String, repo: MarketRepository)` with `state: StateFlow<UiState<CoinDetail>>`, `chart: StateFlow<UiState<List<Double>>>`, `range: StateFlow<ChartRange>`, `fun setRange(r: ChartRange)`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `maps coin detail`() = runTest { val d = (repo.coin("bitcoin") as Outcome.Ok).value; assertEquals("BTC", d.symbol); assertFalse(d.description.contains("<a")) }
@Test fun `chart requests days of range`() = runTest { repo.chart("bitcoin", ChartRange.M1); assertTrue(server.takeRequest().path!!.startsWith("/coins/bitcoin/market_chart?days=30")) }
@Test fun `changing range reloads chart`() = runTest { vm.setRange(ChartRange.Y1); advanceUntilIdle(); assertEquals(ChartRange.Y1, vm.range.value); assertNotNull(vm.chart.value.data) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*CoinDetailTest'` → FAIL
- [ ] **Step 3: Implement** the Produces list. The screen shows a header (logo, name, price, rank), range chips, a filled `LineChart`, a stats grid (cap, volume, 24 h high/low, ATH, circulating/total/max supply), and a collapsible description. Register route `"coin/{id}"` in `AppNavHost`.
- [ ] **Step 4: Run** the tests → PASS, then `./gradlew assembleDebug` → `BUILD SUCCESSFUL`
- [ ] **Step 5: Commit**: `git commit -m "feat: coin detail screen with price chart"`

---

### Task 7: Búsqueda de cualquier moneda o token

**Files:**
- Modify: `market/CoinGeckoApi.kt`, `market/CoinGeckoDtos.kt`, `market/MarketRepository.kt`
- Create: `search/SearchViewModel.kt`, `search/SearchScreen.kt`
- Test: `search/SearchViewModelTest.kt`, fixture `fixtures/cg_search.json`

**Interfaces:**
- Consumes: `MarketRepository`, `UiState`, route `"coin/{id}"` (Task 6)
- Produces:
  - `@GET("search") suspend fun search(@Query("query") q: String): SearchDto`
  - `data class SearchCoin(val id: String, val name: String, val symbol: String, val thumbUrl: String?, val rank: Int?)`
  - `MarketRepository.search(q: String): Outcome<List<SearchCoin>>` (no cache)
  - `class SearchViewModel(repo: MarketRepository)` with `results: StateFlow<UiState<List<SearchCoin>>>` and `fun onQuery(q: String)`: 400 ms debounce, no request below 2 characters (results empty), the latest query wins (`mapLatest`)

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `debounces and ignores short queries`() = runTest {
    vm.onQuery("s"); advanceTimeBy(500); assertEquals(0, fake.searchCalls)
    vm.onQuery("so"); vm.onQuery("sol"); advanceTimeBy(500); assertEquals(listOf("sol"), fake.queries)
}
@Test fun `maps results`() = runTest { vm.onQuery("sol"); advanceUntilIdle(); assertEquals("SOL", vm.results.value.data!!.first().symbol) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*SearchViewModelTest'` → FAIL
- [ ] **Step 3: Implement** the Produces list. The screen has a text field with autofocus and a result list (thumbnail, name, symbol, `#rank`); tapping a result goes to `"coin/{id}"`. Register route `"search"`.
- [ ] **Step 4: Run** the tests → PASS
- [ ] **Step 5: Commit**: `git commit -m "feat: search any coin or token"`

---

### Task 8: Datos DeFi de DefiLlama (TVL, redes, protocolos)

**Files:**
- Create: `defi/DefiLlamaApi.kt`, `defi/DefiLlamaDtos.kt`, `defi/DefiModels.kt`, `defi/DefiRepository.kt`
- Modify: `AppContainer.kt` (adds `defiRepository`, Retrofit with base `https://api.llama.fi/`)
- Test: `defi/DefiRepositoryTest.kt`, fixtures `fixtures/llama_chains.json`, `fixtures/llama_historical.json`, `fixtures/llama_protocols.json` (trimmed; include one `"category": "CEX"` entry and one with `"tvl": null`)

**Interfaces:**
- Consumes: `apiCall`, `Outcome`, `retrofit()` (Task 2)
- Produces:
  - `interface DefiLlamaApi { @GET("v2/chains") suspend fun chains(): List<ChainDto>; @GET("v2/historicalChainTvl") suspend fun historical(): List<TvlPointDto>; @GET("v2/historicalChainTvl/{chain}") suspend fun chainHistorical(@Path("chain") chain: String): List<TvlPointDto>; @GET("protocols") suspend fun protocols(): List<ProtocolDto> }`
  - `data class ChainTvl(val name: String, val tvl: Double, val share: Double, val tokenSymbol: String?, val geckoId: String?)` (`share` = tvl / sum of all tvl, 0..1)
  - `data class TvlPoint(val epochSeconds: Long, val tvl: Double)`
  - `data class Protocol(val slug: String, val name: String, val logoUrl: String?, val category: String?, val tvl: Double, val change1d: Double?, val chains: List<String>, val chainTvls: Map<String, Double>)`
  - `fun List<ChainDto>.toChainTvls(): List<ChainTvl>` (sorted by tvl descending, tvl ≤ 0 dropped)
  - `fun List<ProtocolDto>.toProtocols(): List<Protocol>` (drops `CEX` and null tvl, sorted by tvl descending)
  - `class DefiRepository(api: DefiLlamaApi, now: () -> Long = System::currentTimeMillis)` with `chains(force)`, `totalHistory(force): Outcome<List<TvlPoint>>`, `chainHistory(name: String): Outcome<List<TvlPoint>>`, `protocols(force): Outcome<List<Protocol>>`, all cached 60 s

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `chains sorted with share`() = runTest { val c = (repo.chains() as Outcome.Ok).value; assertEquals("Ethereum", c[0].name); assertEquals(1.0, c.sumOf { it.share }, 1e-9) }
@Test fun `protocols exclude CEX and null tvl`() = runTest { val p = (repo.protocols() as Outcome.Ok).value; assertTrue(p.none { it.category == "CEX" }); assertTrue(p.zipWithNext().all { (a, b) -> a.tvl >= b.tvl }) }
@Test fun `chain name is url encoded`() = runTest { repo.chainHistory("OP Mainnet"); assertEquals("/v2/historicalChainTvl/OP%20Mainnet", server.takeRequest().path) }
@Test fun `total tvl is last historical point`() = runTest { assertEquals(9.5e10, (repo.totalHistory() as Outcome.Ok).value.last().tvl, 1.0) }
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*DefiRepositoryTest'` → FAIL
- [ ] **Step 3: Implement** the Produces list. `ProtocolDto.chainTvls` comes from `chainTvls` in the JSON; keep only keys without `-` (DefiLlama mixes in keys like `Ethereum-staking`).
- [ ] **Step 4: Run** (same command) → PASS
- [ ] **Step 5: Commit**: `git commit -m "feat: DefiLlama TVL, chains and protocols data"`

---

### Task 9: Pantallas DeFi (TVL total, redes, protocolos, detalle de red)

**Files:**
- Create: `defi/DefiViewModel.kt`, `defi/DefiScreen.kt`, `defi/ChainDetailViewModel.kt`, `defi/ChainDetailScreen.kt`
- Modify: `navigation/AppNavHost.kt` (route `"chain/{name}"`, name URL-encoded with `Uri.encode`)
- Test: `defi/DefiViewModelTest.kt`

**Interfaces:**
- Consumes: `DefiRepository`, models (Task 8), `UiState`, `AutoRefresh` (Task 4), `LineChart` (Task 5)
- Produces:
  - `data class DefiOverview(val totalTvl: Double, val history: List<TvlPoint>, val chains: List<ChainTvl>, val protocols: List<Protocol>)`
  - `class DefiViewModel(repo: DefiRepository)` with `state: StateFlow<UiState<DefiOverview>>`, `fun refresh(force: Boolean = false)` (loads the three endpoints in parallel with `async`; if one fails, the `error` is set and previous data is kept)
  - `fun protocolsOnChain(protocols: List<Protocol>, chain: String): List<Pair<Protocol, Double>>`: protocols whose `chainTvls` contains `chain`, paired with that value and sorted descending
  - `class ChainDetailViewModel(chain: String, repo: DefiRepository)` with `history: StateFlow<UiState<List<TvlPoint>>>` and `protocols: StateFlow<UiState<List<Pair<Protocol, Double>>>>`

- [ ] **Step 1: Write the failing tests**

```kotlin
@Test fun `overview total is last history point`() = runTest { vm.refresh(); advanceUntilIdle(); assertEquals(9.5e10, vm.state.value.data!!.totalTvl, 1.0) }
@Test fun `one failing endpoint keeps old data`() = runTest { vm.refresh(); advanceUntilIdle(); fake.failProtocols = true; vm.refresh(force = true); advanceUntilIdle(); assertNotNull(vm.state.value.data); assertNotNull(vm.state.value.error) }
@Test fun `protocols on chain use chain tvl`() = assertEquals(listOf("aave" to 2.0e10, "lido" to 1.0e10), protocolsOnChain(protocols, "Ethereum").map { it.first.slug to it.second })
```

- [ ] **Step 2: Run** `./gradlew :app:testDebugUnitTest --tests '*DefiViewModelTest'` → FAIL
- [ ] **Step 3: Implement** the Produces list. `DefiScreen` shows a header with total TVL (compact) and a 1-year filled `LineChart` (last 365 points), then tabs "Redes" | "Protocolos". Each "Redes" row has position, name, TVL and % of total; tapping it goes to `"chain/{name}"`. Each "Protocolos" row has logo, name, category, TVL and 1 d %. `ChainDetailScreen` shows the chain's TVL history chart and its protocol list with TVL on that chain.
- [ ] **Step 4: Run** the tests → PASS, then `./gradlew assembleDebug` → `BUILD SUCCESSFUL`
- [ ] **Step 5: Commit**: `git commit -m "feat: DeFi screens for TVL, chains and protocols"`

---

### Task 10: Verificación final

**Files:**
- Modify: `README.md` (screens list, data sources with links to the CoinGecko and DefiLlama docs, note about the free-tier rate limit)

- [ ] **Step 1: Run everything**

Run: `./gradlew :app:testDebugUnitTest :app:lintDebug :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`, 0 test failures, no lint errors (warnings allowed)

- [ ] **Step 2: Manual smoke test on an emulator or phone** (`./gradlew installDebug`): Mercado shows the 7 cards with real numbers; Top 100 shows 100 rows with sparklines; tapping a coin opens its chart; search for "pepe" finds it; DeFi shows the total TVL, chains and protocols; tapping "Ethereum" opens its detail. With airplane mode on, pull-to-refresh shows "Sin conexión" and the data stays.
- [ ] **Step 3: Commit**: `git commit -m "docs: README with screens and data sources"`
