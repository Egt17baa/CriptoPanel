# CriptoPanel: ver la app en euros (diseño)

Fecha: 22 de octubre de 2026. Versión 1.6.0. Lo pidió el usuario en el brainstorming de «Más» («Moneda en euros») y lo delegó con «Hazlo». Las decisiones son de Claude.

## Decisiones

- **Dónde se elige:** en Más → Preferencias → **Divisa**: Dólar (USD) o Euro (EUR). Por defecto, USD. La elección se guarda en el móvil y, al cambiarla, la app se redibuja.
- **Tipo de cambio:** el del BCE, a través de Frankfurter (`api.frankfurter.dev`, gratis y sin clave).
  - El cambio de hoy se refresca como mucho cada hora y se guarda el último conocido para poder mostrar euros sin conexión.
  - Para fechas pasadas se usa el cambio de ese día, o el último día hábil anterior porque el BCE no publica los fines de semana.
- **Por dentro todo sigue en dólares,** que es lo que dan las fuentes. Los euros se calculan al mostrar:
  - **Mercado, DeFi, gráficos de índices:** importes en dólares × cambio de hoy. Los gráficos de TradingView siguen en USD, porque son sus índices oficiales; los filtros de TVL de Rendimientos siguen en M$.
  - **Cartera:** cada compra y cada venta se pasa a euros con el cambio **de su fecha**. Así, el coste y la ganancia en euros reflejan lo que de verdad pagaste en euros (incluye el efecto del cambio euro/dólar), y el valor actual usa el cambio de hoy.
  - **Formulario de operación:** se escribe en euros. Al guardar se pasa a dólares con el cambio del día de la operación. El precio que se rellena solo también sale en euros de ese día.
  - **Alertas:** el valor se escribe en euros y se vigila en dólares, pasado al cambio del momento de crearla. El formulario lo indica: «Se vigila en dólares: 90.000 € ≈ 101.104 $».
  - **Calculadora:** el lado de dinero pasa a ser EUR.
  - **¿Y si hubiera invertido?:** el importe se escribe en euros (pasado a dólares al cambio de hoy) y el resultado se ve en euros.
- **Si nunca se ha podido descargar el cambio:** la app sigue en dólares y Preferencias lo explica.

## Pruebas

- Conversión dólar↔euro y formato con el símbolo €.
- Cambio de una fecha sin dato (fin de semana): se usa el día hábil anterior.
- La cartera en euros usa el cambio de cada operación para el coste y el de hoy para el valor.
- El formulario guarda en dólares lo escrito en euros con el cambio de la fecha.
