# CriptoPanel: importar el historial de Binance en Cartera (diseño)

Fecha: 22 de octubre de 2026. Versión 1.10.0. El usuario preguntó si se puede pasar a Cartera lo comprado y vendido en Binance para ver ganancias y pérdidas, y lo delegó («hazlo tú, para la última versión de la app o la web»). Las decisiones son de Claude. No hay un archivo real de ejemplo: los formatos salen de la documentación de Binance y de importadores de código abierto (rotki).

## Qué archivos se aceptan

Se detectan por la cabecera, en CSV (coma o punto y coma) o Excel (`.xlsx`), con los nombres de columna en inglés o en español:

| Archivo de Binance | Dónde se descarga | Columnas |
|---|---|---|
| **Historial de operaciones** (spot) | App: Órdenes → Spot → Historial de operaciones → Exportar. Web: Órdenes → Órdenes de spot → Historial de operaciones → Exportar | `Date(UTC), Pair, [Type], Side, Price, Executed, Amount, Fee` (cantidades con la moneda pegada: `0.0015BTC`) |
| Historial de operaciones (formato antiguo) | Igual, cuentas antiguas | `Date(UTC), Market, Type, Price, Amount, Total, Fee, Fee Coin` |
| **Historial de transacciones** (estado de cuenta) | App: Perfil → Historial de transacciones → Exportar. Web: Cartera → Historial de transacciones → Exportar | `User_ID, UTC_Time, Account, Operation, Coin, Change, Remark` |

## Cómo se convierte en operaciones de Cartera

- **Efectivo:** las stablecoins en dólares (USDT, USDC, FDUSD…) y las monedas fiat (USD, EUR) son el dinero con el que se compra; no se apuntan como posiciones.
- **Compra o venta contra efectivo:** una operación de compra o venta de la cripto, con el precio en dólares (las stablecoins valen 1 $; el euro, al cambio del BCE de ese día).
- **Cripto contra cripto** (ETH/BTC, Convert): dos operaciones, compra de una y venta de la otra, valoradas con el precio en dólares de la moneda de pago ese día (apertura diaria de Binance frente a USDT; si no hay par, CoinGecko).
- **Comisiones:** en dólares en la operación. Si se cobran en la misma moneda comprada, la cantidad recibida se reduce (lo que de verdad llegó). Las cobradas en BNB solo se suman como coste (no se resta BNB).
- **Estado de cuenta:** las filas del mismo segundo se agrupan (Buy/Sell/Fee, Transaction Buy/Spend/Fee, Transaction Sold/Revenue, Binance Convert, Small Assets Exchange BNB, Large OTC Trading). Un grupo con más de dos monedas que no se puede repartir con seguridad se cuenta como «no reconocido».
- **Compra con tarjeta** (`Buy Crypto`): compra al precio de mercado del día.
- **Recompensas** (Simple Earn, staking, Launchpool, airdrops, distribuciones, comisiones de referidos): compra a precio 0, con la nota «Recompensa».
- **Se ignoran** (se cuentan en el resumen): depósitos y retiradas (son tus propias monedas que entran o salen), transferencias entre cuentas, suscripciones y reembolsos de Earn, futuros, margen y P2P.
- **Saldo previo:** si una venta deja una moneda en negativo (se depositó desde fuera y el depósito no estaba en el archivo), se añade antes una compra «Saldo previo» por lo que falta, al precio de ese día. Así la cantidad cuadra y la ganancia se cuenta desde esa fecha.
- **Moneda de CoinGecko:** el símbolo se busca entre las 250 de mayor capitalización y, si no está, con la búsqueda de CoinGecko (la de mejor posición). Las que no se encuentran no se importan y se nombran en el resumen.
- **Sin duplicados:** cada operación lleva un id derivado de su fila (fecha, par, cantidades); importar el mismo archivo dos veces no duplica nada.
- **Fechas:** en UTC en el archivo; en Cartera, el día local.

## Pantalla

Cartera → menú → **Importar desde Binance** → elegir archivo → «Leyendo y poniendo precio…» → resumen antes de guardar: tipo de archivo, compras, ventas, recompensas, monedas, saldos previos añadidos, ignorados por motivo y monedas no reconocidas → **Importar** o **Cancelar**. La ayuda explica dónde se descarga cada archivo.

## Seguridad

- Máximo 20 MB por archivo; en `.xlsx`, máximo 50 MB descomprimidos y 200.000 filas (contra «bombas zip»), y XML sin DTD (sin entidades externas).
- El archivo no sale del móvil: solo se piden precios públicos (Binance, CoinGecko, BCE).
- Celdas con fórmulas no se evalúan: se lee su texto.

## Pruebas

Lectura de CSV (coma, punto y coma, comillas) y de `.xlsx` (cadenas compartidas, en línea y números); detección de los tres formatos y de cabeceras en español; números con la moneda pegada y separadores; fechas; reparto de pares sin separador; agrupación del estado de cuenta (compra, venta, Convert, comisión aparte, grupos ambiguos); recompensas e ignorados; conversión a operaciones (efectivo, cripto contra cripto, comisión en la moneda comprada, en BNB, euros); saldo previo; ids estables.
