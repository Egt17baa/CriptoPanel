# CriptoPanel 1.3.0 (alertas en directo): plan de implementación

> **Para agentes:** se ejecuta tarea a tarea con superpowers:executing-plans. Cada tarea sigue TDD y termina con `./gradlew :app:testDebugUnitTest`.

**Spec:** [`docs/superpowers/specs/2026-10-22-criptopanel-alertas-design.md`](../specs/2026-10-22-criptopanel-alertas-design.md)

**Arquitectura:** el paquete `alerts` tiene:

- `Alert` y `AlertStore` (JSON atómico, como `PortfolioStore`);
- `AlertEngine`, puro: guarda las cotizaciones y el histórico corto, evalúa las alertas y redacta los avisos;
- `Binance`: correspondencia de monedas por REST, lectura de `miniTicker` y `OkHttpPriceStream` (WebSocket de OkHttp);
- `AlertMonitor`: corrutinas que unen almacén, cartera, flujo, respaldo de CoinGecko y motor.

El monitor vive en `AppContainer`, una sola vez por proceso, para que la interfaz vea su estado y sus precios. `AlertService`, el servicio en primer plano `specialUse`, solo lo arranca y lo para, y mantiene vivo el proceso.

## Decisiones de implementación

- **Cruce de valor:** *sube de* salta cuando el precio está por encima del umbral, y *baja de* cuando está por debajo. El formulario y el interruptor no dejan activar una alerta que ya se cumple. Así una subida que ocurre con el servicio parado (móvil apagado) también avisa al volver, cosa que no pasaría comparando con el último precio visto. Un precio igual al umbral no salta.
- **Alertas de la cartera:** solo se evalúan cuando todas las monedas abiertas tienen cotización, para no dar un valor falso por la falta de un precio.
- **Botón Pausar:** detiene el servicio hasta que se vuelve a abrir la app.

## Tareas

1. **Modelo y almacén:** `Alert`, `AlertKind`, `Direction`, `AlertStore` (`upsert`, `delete`, `setEnabled`, `markTriggered`). Tests.
2. **`AlertEngine`:** cotizaciones, histórico (1 muestra cada 10 s como máximo, 4 h), evaluación de los 7 tipos, «calentando» y textos. Tests.
3. **Binance:** `BinanceApi` (`ticker/price`), `binancePairs()` con la comprobación de ±3 % y las stablecoins, `parseMiniTicker()`. Tests.
4. **`AlertMonitor`:** suscripción según las alertas activas, WebSocket con reconexión, respaldo cada 60 s, disparo (guardar y luego avisar) y estado. Tests con un flujo y un notificador falsos.
5. **Android:** `AlertService`, `AlertNotifier` (2 canales), `BootReceiver`, permisos y manifiesto, enlace profundo desde la notificación y vigilancia de la red.
6. **UI:** sección Alertas en Cartera (estado, avisos de permisos, batería y fabricantes, y lista), formulario de alerta con su ViewModel y 🔔 en el detalle de moneda.
7. **Cierre:** versión 1.3.0, README, privacidad (Binance) y ficha. Verificación: tests, lint, APK release y `jvm-check`.
