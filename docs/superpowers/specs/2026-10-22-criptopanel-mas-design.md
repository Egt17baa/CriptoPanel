# CriptoPanel: pestaña «Más», herramientas y transparencia (diseño)

Fecha: 22 de octubre de 2026. Versión 1.4.0.

## Decisiones tomadas en el brainstorming

- El usuario mostró otra app como referencia («no copies, mejóralo»). La idea es una pestaña que reúna herramientas, información legal y transparencia, con el estilo de vidrio de CriptoPanel.
- **Herramientas:** Comparador, Calculadora y «¿Y si hubiera invertido?» (de una vez o DCA).
- **Legal y transparencia:**
  - términos y condiciones, que se aceptan al abrir la app por primera vez;
  - el aviso «Datos informativos, no asesoramiento financiero», visible en varios sitios;
  - privacidad y fuentes de datos;
  - «Cómo calculamos».
- **Euros:** la preferencia USD/EUR para toda la app se hará como subproyecto aparte (la 1.5.0). Afecta a todos los precios, a la cartera y a las alertas, y es demasiado para meterla aquí.
- **«Aprende»** también irá dentro de «Más», pero en su propio subproyecto.

## Navegación

La barra inferior pasa a tener cinco pestañas: **Mercado · Top 100 · DeFi · Cartera · Más**.

## Pantalla «Más»

De arriba abajo, con tarjetas de vidrio:

1. **Herramientas**
   - Comparador: «Hasta 4 monedas lado a lado».
   - Calculadora: «Cripto ⇄ dólares y entre criptos».
   - ¿Y si hubiera invertido?: «De una vez o comprando cada mes».
   - Alertas: «N activas · en directo». Lleva a Cartera → Alertas.
2. **Información**
   - Términos y condiciones.
   - Privacidad.
   - Fuentes de datos: qué da cada fuente, con su enlace.
   - Cómo calculamos.
3. **«Lo importante, en claro»:** una tarjeta con 4 frases cortas:
   - es solo información, no asesoramiento financiero;
   - no hay custodia, compra-venta ni publicidad;
   - tus datos se quedan en el móvil;
   - las fuentes gratuitas pueden fallar o llegar con retraso, y entonces se ve el último dato con un aviso.
4. **Pie:** CriptoPanel, la versión, «Datos informativos, no asesoramiento financiero» y «Sin custodia · Sin trading · Sin publicidad».

## Términos y condiciones

- **Primera vez que se abre la app** (y cada vez que cambie la versión de los términos): una pantalla con el resumen en 4 puntos, el enlace a los términos completos, la casilla «He leído y acepto los términos y entiendo que CriptoPanel no da asesoramiento financiero» y el botón «Empezar».
  - No se puede usar la app sin aceptar.
  - La aceptación (versión y fecha) se guarda en el móvil.
- **Contenido:**
  1. objeto: app informativa;
  2. sin asesoramiento financiero;
  3. datos de terceros sin garantía de exactitud ni de disponibilidad;
  4. la cartera y las alertas son herramientas personales, y una alerta puede no llegar (batería, red, sistema);
  5. no hay custodia ni compra-venta;
  6. limitación de responsabilidad;
  7. privacidad (remite a la política);
  8. propiedad intelectual y marcas de terceros;
  9. cambios en los términos;
  10. ley española y contacto.
- El texto vive en `legal/LegalTexts.kt` (dentro de la app) y en `docs/google-play/terminos.md` (para publicarlo).

## Aviso «no es asesoramiento»

Una línea discreta, «Datos informativos, no asesoramiento financiero.», al final de:

- la Cartera;
- Rendimientos (sustituye al aviso actual de APY);
- Alertas;
- la Calculadora;
- «¿Y si hubiera invertido?»;
- el Comparador;
- y en el pie de «Más».

## Comparador

- Hasta 4 monedas (buscador). Los chips de arriba quitan una moneda.
- **Tabla:** posición, precio, capitalización, volumen 24 h, cambio 24 h y cambio 7 d. Se resalta el mejor valor de cada fila.
- **Gráfico:** rendimiento en % (base 100) en 7 d, 30 d o 1 año, con una línea por moneda en colores distintos de verde y rojo (plata, celeste, rosa y ámbar) y una leyenda.
- **Datos:** `markets?ids=` (1 petición) y `market_chart` por moneda (caché ya existente).
- Solo monedas. Las redes y los protocolos DeFi quedan fuera de esta versión.

## Calculadora

- **De:** moneda o USD, con un importe. **A:** moneda o USD. Botón ⇅ para intercambiarlos.
- Resultado al instante con los precios actuales: `importe × precio(De) / precio(A)`. Muestra también el precio unitario de cada lado.
- Por defecto: 1 BTC → USD.

## ¿Y si hubiera invertido?

- **Moneda**, **importe en $**, **hace cuánto** (1 mes, 3 meses, 6 meses o 1 año) y **modo**: «De una vez» o «Cada mes (DCA)».
- **Resultado:** lo invertido, lo que valdría hoy, la ganancia en $ y en %, las unidades compradas y el precio medio, más un gráfico de la evolución del valor frente a lo invertido.
- **Datos:** una sola petición `market_chart` de 365 días, que ya está en caché. Por eso el máximo es 1 año: la API gratuita de CoinGecko no da más.
- **Cálculo** (función pura y probada): la compra única se hace al precio del primer día del periodo. En DCA se compra el mismo importe el día del mes que toca, al último precio disponible de ese día.

## Pruebas

- Simulación: compra única, DCA con número de compras e importes, y precio medio.
- Calculadora: conversión moneda→USD, USD→moneda y entre monedas; precio desconocido.
- Comparador: máximo 4 monedas, sin repetidas, y mejor valor por fila.
- Términos: una versión nueva pide aceptar otra vez.
