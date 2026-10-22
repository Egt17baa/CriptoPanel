# CriptoPanel: cartera y favoritos (diseño)

Fecha: 22 de octubre de 2026. Estado: pendiente de revisión. Subproyecto 2 de 4 (versión 1.2.0).

## Decisiones tomadas en el brainstorming

- **Cartera por operaciones:** se apunta cada compra y venta (moneda, cantidad, precio, fecha, comisión y nota). La app calcula lo que tienes, tu precio medio y tu ganancia o pérdida.
- **Nueva pestaña inferior «Cartera»**, con dos secciones: Cartera y Favoritos. La estrella para marcar favoritos está en el detalle de cada moneda.
- **Datos solo en el móvil**, sin cuentas ni servidores. Se pueden exportar e importar en un archivo CSV y además se incluyen en la copia de seguridad automática de Android.
- **Extras:** reparto de la cartera (gráfico), evolución del valor (7 d, 30 d y 1 año), cambio de hoy e historial de operaciones con edición y borrado.

## Experiencia de uso

### Pestaña Cartera

Arriba, dos pestañas: **Cartera · Favoritos**.

**Cartera** (de arriba abajo, con tarjetas de vidrio como el resto de la app):

1. **Resumen:** valor total, ganancia o pérdida total (en $ y %), y cambio de hoy (últimas 24 h, en $ y %).
2. **Evolución:** gráfico del valor de la cartera con selector 7 d · 30 d · 1 año.
3. **Reparto:** barras horizontales con el % de cada moneda sobre el valor total (las 8 mayores y «Otras»).
4. **Posiciones:** una fila por moneda con logo, símbolo, cantidad, valor actual, precio medio y ganancia o pérdida (en $ y %). Al tocarla se abre su historial de operaciones.
5. Enlace **«Historial de operaciones ›»** (todas las monedas) y menú **⋮** con «Exportar CSV» e «Importar CSV».
6. Botón flotante **«+ Operación»**.

Si no hay operaciones, la pestaña muestra un mensaje que explica cómo empezar y el botón «+ Operación».

**Favoritos:** la lista de monedas marcadas con la misma fila que Top 100 (precio, 24 h y minigráfico). Al tocar una se abre su detalle. Si no hay ninguna: «Marca monedas con la ★ en su detalle».

### Estrella de favorito

En la barra superior del Detalle de moneda aparece una ★ que se rellena al marcarla. Al lado está «+ Operación», que abre el formulario con esa moneda ya elegida.

### Formulario de operación

- **Tipo:** Compra / Venta.
- **Moneda:** buscador (el mismo que Buscar). Se rellena solo si se entra desde el detalle de una moneda o desde su historial.
- **Cantidad.**
- **Precio por unidad en $:** se rellena con el precio actual y se puede cambiar.
- **Fecha:** selector de fecha de Material 3, hoy por defecto. No admite fechas futuras.
- **Comisión en $** (opcional, 0 por defecto).
- **Nota** (opcional, hasta 200 caracteres).

Validación: la cantidad y el precio deben ser mayores que 0 y la comisión mayor o igual que 0. Una venta no puede dejar la cantidad de la moneda en negativo en ninguna fecha. Si pasara, se muestra «No tenías tanta cantidad en esa fecha». Al editar se aplica la misma regla.

### Historial de operaciones

Lista por fecha, de la más reciente a la más antigua, filtrable por moneda. Cada fila muestra el tipo, la fecha, la cantidad, el precio, el total y la nota. Al tocar una operación se edita; al deslizarla o con el botón de borrar se elimina, con opción de deshacer durante unos segundos.

## Cálculos (método del precio medio)

Para cada moneda, recorriendo sus operaciones por fecha:

- **Compra** de `q` unidades a `p` con comisión `f`: `cantidad += q`; `coste += q·p + f`.
- **Venta** de `q` unidades a `p` con comisión `f`: `precioMedio = coste / cantidad`; `realizado += q·p − f − q·precioMedio`; `coste −= q·precioMedio`; `cantidad −= q`.
- **Valor actual** = `cantidad × precio actual`.
- **Ganancia no realizada** = `valor − coste`. **Ganancia total** = `realizado + no realizada`, y en % sobre lo invertido en compras (`Σ q·p + f` de las compras).
- **Cambio de hoy** = `Σ valor × c / (100 + c)`, con `c` = cambio en 24 h en % de cada moneda. Si falta el cambio de una moneda, se excluye y la cifra se marca con «~».
- **Evolución:** para cada punto `t` del periodo, `Σ cantidad(t) × precio(t)`, donde `cantidad(t)` usa solo las operaciones con fecha ≤ `t`. Los precios vienen de `coins/{id}/market_chart` (ya en caché de la app), con sus marcas de tiempo. Para cada moneda se toma el último precio anterior o igual a `t`.
- Una posición con cantidad 0 (todo vendido) no sale en Posiciones ni en Reparto, pero sí cuenta en la ganancia realizada y en el historial.

Todos los importes están en USD, como el resto de la app. Las cantidades se guardan como `Double`: para una cartera personal, el error de redondeo (menos de 1e-9 de unidad) es despreciable. Al restar, una cantidad menor que 1e-9 se trata como 0.

## Datos y almacenamiento

- **Archivo JSON** en el almacenamiento interno de la app (`files/portfolio.json`), escrito de forma atómica con `AtomicFile`. Contiene la versión del formato, las operaciones y los ids de los favoritos. No hace falta Room: son pocos cientos de registros y así no se añaden dependencias ni procesadores de anotaciones.
- **Operación:** `id` (UUID), `coinId` (id de CoinGecko), `symbol`, `name`, `imageUrl`, `type` (BUY/SELL), `quantity`, `priceUsd`, `feeUsd`, `date` (día local, ISO `yyyy-MM-dd`) y `note`.
- **`PortfolioStore`** expone `StateFlow<PortfolioData>` y las funciones `add`, `update`, `delete`, `toggleFavorite` e `importCsv`. Toda la lógica de cálculo vive en funciones puras (`holdings()`, `summary()`, `valueSeries()`), así que se prueba en JVM.
- **Precios actuales:** una sola petición `coins/markets?ids=<id1,id2,…>` para todas las monedas de la cartera y los favoritos. Trae precio, cambio en 24 h, logo y minigráfico. Usa caché de 60 s y la cartera se refresca cada 60 s mientras está visible, como Top 100.
- **Evolución:** como mucho, las 10 posiciones de mayor valor, con una petición por moneda y la caché de gráficos que ya existe. Si hay más, aparece la nota «Incluye tus 10 mayores posiciones».
- **Copia automática de Android:** `allowBackup` pasa a `true`, y `data_extraction_rules.xml` y `full_backup_content` solo incluyen `files/portfolio.json`.

### CSV

- **Exportar:** «Exportar CSV» abre el selector de archivos de Android (`CreateDocument`, sin permisos) con el nombre `criptopanel-cartera-AAAA-MM-DD.csv`.
- **Columnas:** `fecha,tipo,moneda_id,simbolo,nombre,cantidad,precio_usd,comision_usd,nota,id`. Separador coma, decimales con punto y comillas en los textos con coma.
- **Importar:** se abre con `OpenDocument`. Se valida todo el archivo antes de tocar nada. Si una fila es incorrecta, se indica su número y su motivo, y no se importa nada. Las operaciones cuyo `id` ya existe se saltan, así que importar dos veces el mismo archivo no duplica nada. Al final se muestra «N operaciones importadas, M ya estaban».

## Navegación

- La barra inferior pasa a **Mercado · Top 100 · DeFi · Cartera**.
- Rutas nuevas: `portfolio`, `operation?coinId=…&id=…` (crear o editar) y `history?coinId=…`.

## Pruebas

Tests de JVM, siguiendo el estilo actual:

- **Precio medio:** compras y ventas parciales, venta total (cantidad 0), comisiones en compra y en venta, y ganancia realizada más no realizada.
- **Validación:** una venta mayor que lo que se tenía en su fecha (también al editar o borrar una compra anterior) se rechaza.
- **Cambio de hoy:** con un cambio nulo de alguna moneda se marca como aproximado.
- **Evolución:** operaciones posteriores a `t` no cuentan; sin precio en `t` se usa el último anterior.
- **CSV:** ida y vuelta exacta, comillas y comas en la nota, fila incorrecta que no importa nada, e ids repetidos que se saltan.
- **`PortfolioStore`:** guarda y vuelve a leer; un archivo dañado no rompe la app (empieza vacía y conserva una copia `portfolio.json.bad`).
- **Repositorio de precios:** `markets?ids=` lleva los ids separados por comas, y con 0 monedas no se hace ninguna petición.

## Privacidad

La política de privacidad debe decir que la cartera y los favoritos se guardan solo en el dispositivo. También debe aclarar que pueden ir en la copia de seguridad de Google del usuario si la tiene activada, y que no se envían a ningún servidor de la app. En la ficha de Google Play hay que actualizar la sección «Seguridad de los datos»: la app no recoge datos, porque todo se queda en el dispositivo.

## Fuera de alcance

- Sincronizar entre dispositivos, conectar con exchanges o wallets, e impuestos.
- Monedas de referencia distintas de USD.
- Alertas de precio: subproyecto 3, que usará los favoritos.
