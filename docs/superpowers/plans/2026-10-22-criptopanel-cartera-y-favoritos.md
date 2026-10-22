# CriptoPanel 1.2.0 (cartera y favoritos): plan de implementación

> **Para agentes:** se ejecuta tarea a tarea con superpowers:executing-plans. Cada tarea sigue TDD y termina con `./gradlew :app:testDebugUnitTest`.

**Spec:** [`docs/superpowers/specs/2026-10-22-criptopanel-cartera-y-favoritos-design.md`](../specs/2026-10-22-criptopanel-cartera-y-favoritos-design.md)

**Arquitectura:** el paquete nuevo `portfolio` tiene funciones puras (modelo, cálculos y CSV) y un `PortfolioStore` que guarda un JSON con escritura atómica (`Files.move` con `ATOMIC_MOVE`; minSdk 26 tiene `java.nio.file`). Los precios vienen de `MarketRepository` (`markets?ids=` y gráficos con marcas de tiempo). La interfaz son pantallas Compose con sus ViewModels, igual que en el resto de la app.

## Decisiones de implementación

- Dentro de un mismo día, las compras van antes que las ventas. Así, una compra y una venta apuntadas el mismo día nunca dan una cantidad negativa, sea cual sea el orden en que se anotaron.
- La fecha de una operación cuenta desde el inicio de ese día en la zona horaria del móvil para el gráfico de evolución.
- La estrella de favorito es el carácter ★/☆: `material-icons-core` no tiene la estrella vacía.
- Los menús y el selector de fecha usan un fondo opaco (`#1F2024`): el vidrio translúcido no se lee bien encima de una lista.

## Tareas

1. **Modelo y cálculos** (`portfolio/Portfolio.kt`): `Operation`, `PortfolioData`, `positions()`, `firstShortfall()`, `summarize()`, `allocation()` y `valueSeries()`. Tests: precio medio, comisiones, ventas parciales y totales, venta sin saldo, cambio de hoy aproximado y evolución.
2. **CSV** (`portfolio/PortfolioCsv.kt`): `toCsv()` y `parseCsv()`. Tests: ida y vuelta, comillas, error por fila e ids repetidos.
3. **`PortfolioStore`:** carga perezosa, `Mutex`, escritura atómica, archivo dañado guardado como `.bad`, y las funciones `upsert`, `delete`, `toggleFavorite` e `importCsv` con validación. Tests con un directorio temporal.
4. **Precios:** `CoinGeckoApi.markets(ids=…)`, `MarketRepository.marketsByIds()` (sin petición si la lista está vacía) y `pricePoints()` con marcas de tiempo.
5. **ViewModels:** `PortfolioViewModel` (precios, resumen, evolución por periodo, favoritos) y `OperationViewModel` (formulario, precarga y validación). Tests.
6. **UI:** `PortfolioScreen` (Cartera y Favoritos, exportar e importar), `OperationScreen`, `HistoryScreen`, y la ★ y «+ Operación» en `CoinDetailScreen`. La barra inferior tiene una cuarta pestaña.
7. **Copia de seguridad:** `allowBackup`, `backup_rules.xml` y `data_extraction_rules.xml` incluyen solo `files/portfolio.json`.
8. **Cierre:** versión 1.2.0, README, política de privacidad y ficha. Verificación: `./gradlew :app:testDebugUnitTest :app:lintRelease :app:assembleRelease` y `gradle -p jvm-check test`.
