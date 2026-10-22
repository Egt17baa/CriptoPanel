# CriptoPanel 1.1.0 (más mercado, más DeFi, vidrio líquido): plan de implementación

> **Para agentes:** se ejecuta tarea a tarea con superpowers:executing-plans. Cada tarea sigue TDD: primero el test con un fixture, luego el código y por último `gradle -p jvm-check test`.

**Spec:** [`docs/superpowers/specs/2026-10-22-criptopanel-mas-mercado-y-defi-design.md`](../specs/2026-10-22-criptopanel-mas-mercado-y-defi-design.md)

**Arquitectura:** mismo patrón que la 1.0.0 (API Retrofit → repositorio con `TtlCache` → ViewModel → pantalla Compose). Para no repetir un ViewModel por pantalla, las pantallas nuevas usan un `LoaderViewModel<T>` genérico que recibe la función de carga. Los datos nuevos de mercado van en `MarketInsightsRepository` y los de DeFi en `DefiExtrasRepository`, así `MarketRepository` y `DefiRepository` y sus tests no cambian.

## Restricciones globales

- Se mantienen todas las de la 1.0.0: USD, textos en español, `—` para valores nulos y ningún aviso de límite que tape datos.
- TTL de 10 min para miedo y codicia, tendencias, sectores, exchanges y stablecoins; 30 min para DEX, comisiones y rendimientos (como `/protocols`).
- Las tarjetas nuevas de Mercado se refrescan cada 5 min (`AutoRefresh(300_000)`), no cada 60 s.
- Tema oscuro fijo en gris y negro; el vidrio se consigue con colores translúcidos del `colorScheme`, así se aplica también a las pantallas existentes.

## Tareas

1. **`LoaderViewModel<T>`** (`core/ui`): `state: StateFlow<UiState<T>>` y `refresh(force)`, que cancela la carga anterior. Test: conserva los datos si falla y no duplica cargas.
2. **Miedo y codicia:** `SentimentApi` (`api.alternative.me/fng/?limit=30`), `FearGreed.kt` (DTO, dominio y clasificación en español). Tests: los límites 24/25, 44/45, 55/56 y 75/76, el `value` que llega como texto y el orden cronológico.
3. **Tendencias, sectores y exchanges:** endpoints nuevos en `CoinGeckoApi` y mapeos en `Trending.kt`, `Categories.kt` y `Exchanges.kt`. Tests con fixtures: nulos, sectores sin capitalización ocultos y exchanges en USD o en ₿.
4. **Suben y bajan:** función pura `movers(coins, n)`. Tests: orden, exclusión de nulos y empates estables.
5. **`MarketInsightsRepository`** con cachés de 10 min, y `MarketRepository.topCoins` para «suben y bajan» y el precio de BTC. Test: caché de 10 min.
6. **DeFi, DTOs y APIs:** `StablecoinsApi` (`stablecoins.llama.fi`), `YieldsApi` (`yields.llama.fi`) y `overview/dexs` y `overview/fees` en `DefiLlamaApi`. Mapeos en `Stablecoins.kt`, `Volume.kt` y `Yields.kt`. Tests: suma de `peggedUSD`, desviación de paridad > 1 %, red con espacios codificada en la URL, `dataType`, filtros y orden de pools.
7. **`DefiExtrasRepository`** con cachés por red y tipo. Test: caché por red.
8. **Tema y vidrio:** `Theme.kt` (paleta fija), `Glass.kt` (`LiquidBackground`, `Modifier.glass`, `GlassCard`), `themes.xml` oscuros y TradingView siempre oscuro. `AppNavHost` pone el fondo líquido detrás del `Scaffold`.
9. **Mercado:** tarjetas nuevas en `OverviewScreen`, más las pantallas `MoversScreen`, `TrendingScreen`, `CategoriesScreen` y `ExchangesScreen`, con sus rutas.
10. **DeFi:** `DefiScreen` con `ScrollableTabRow` y las pestañas `StablecoinsTab`, `VolumeTab` (DEX y comisiones/ingresos) y `YieldsTab`.
11. **Cierre:** `versionName` 1.1.0, README, ficha de Google Play y política de privacidad (nueva fuente: alternative.me). Verificación: `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease` y `gradle -p jvm-check test`.
