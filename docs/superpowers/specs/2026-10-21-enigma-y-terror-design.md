# Enigma y Terror — diseño (fase 1)

Fecha: 2026-10-21

## Intención

Una web de entretenimiento sobre terror y misterio con base real. Un visitante cualquiera, que llega sin buscar nada, tiene que pensar «pero ¿qué demonios es esto?» con lo primero que ve, engancharse y saltar de una historia a otra sin mirar el reloj. Tiene que sentir a la vez miedo y ganas de comprobar por sí mismo si lo que lee es verdad.

Por eso cada historia separa con claridad lo documentado de lo que es leyenda y da las fuentes para comprobarlo. Esa honestidad es lo que hace que el miedo funcione: lo inquietante es que *pasó de verdad*.

## Alcance

**Fase 1 (este documento):** la web con diez historias investigadas y redactadas, en español.

**Fase 2 (otro proyecto, después):** un rincón de la comunidad donde los visitantes cuentan sus propias historias, revisadas por una IA antes de publicarse. No se construye ahora, pero la fase 1 no debe impedirla (ver «Lo que deja preparado»).

## Objetivos

- La primera pantalla impacta en menos de tres segundos.
- Cada historia lleva a otras dos o tres, de modo que el visitante encadena varias en una visita.
- Cada historia indica cuánto es verdad y enlaza fuentes reales y verificables.
- Funciona igual de bien en el móvil, que es donde llegará la mayoría.

## No objetivos

- Cuentas de usuario, comentarios o envío de historias (fase 2).
- Más de diez historias, otros idiomas o un buscador.
- Monetización o publicidad.

## Antiobjetivos (fracaso aunque funcione)

- Que parezca una web genérica «hecha por IA», un blog de plantilla o un listicle de «10 historias de miedo».
- Datos inventados presentados como hechos, o fuentes que no existen.
- Morbo con víctimas reales: recrearse en la violencia, sobre todo con niños.
- Sustos baratos que molestan: sonidos que se reproducen solos, ventanas emergentes o *jumpscares*.

## Experiencia (decidida con el usuario)

La dirección visual combina dos de los bocetos (https://claude.ai/artifact/JtDaXSphaJFLnf64ikKudA, artboard «B + C»). El código de ese boceto está en `docs/superpowers/specs/bocetos/transmision-y-hilo-rojo.html` y es la referencia aprobada: colores, tipografías, la barra de verdad y el tablero se toman de ahí. Está escrito en el formato del editor de bocetos (`<x-dc>` y `{{signal}}` = `#e0221b`), así que se usa como referencia visual, no como código para copiar.

1. **La transmisión (B), al entrar:** pantalla negra que ocupa todo el alto, un indicador «REC» con código de tiempo, el lugar y la fecha en letra de monitor, y una frase enorme en mayúsculas con una palabra en rojo («El hombre del saco **existió**»). Abajo: «Sigue bajando. Si te atreves.»
2. **El relato, al bajar:** una columna estrecha de lectura, con frases cortas que se van revelando al hacer scroll y crean suspense.
3. **El nivel de verdad:** una barra de cinco segmentos, de «pura leyenda» a «documentado», con un resumen de qué está probado y qué es leyenda, y un enlace a las fuentes.
4. **El hilo rojo (C), al terminar:** un tablero de corcho con la historia actual marcada «Estás aquí» y unida con hilo rojo a otras dos o tres historias relacionadas. Cada nota lleva a otra historia. En el móvil el tablero se puede desplazar en horizontal, o se convierte en una lista vertical unida por el hilo (a elegir por quien lo construya).

La portada usa el mismo esquema: abre con la transmisión de una historia destacada y termina con el tablero completo de las diez historias.

Tipografías del boceto: Oswald (titulares), Source Serif 4 (lectura), VT323 (letra de monitor) y Caveat (notas a mano del tablero). Paleta: negro casi puro, blanco roto y un único rojo de acento; el corcho es marrón oscuro y las notas, color papel.

## Las diez historias

Seleccionadas por ser poco conocidas para un público hispanohablante, tener base real comprobable y cubrir distintos tipos de miedo. Las cifras y los detalles se contrastan al redactar, porque las fuentes divulgativas discrepan en varios puntos (señalados abajo).

| # | Historia | Lugar | Tipo | Nivel de verdad |
|---|----------|-------|------|-----------------|
| 1 | El hombre del saco existió: el crimen de Gádor | Almería, España, 1910 | Crimen real que creó una leyenda | 4/5 |
| 2 | El hombre lobo de Allariz: Manuel Blanco Romasanta | Galicia, España, 1852–1853 | Asesino real que alegó ser lobo | 4/5 |
| 3 | La Casa de las Siete Chimeneas | Madrid, España | Aparición y hallazgo real | 2/5 |
| 4 | El Familiar de los ingenios | Tucumán, Argentina, finales del s. XIX | Mito nacido de la explotación | 2/5 |
| 5 | La Recta Provincia: el juicio a los brujos de Chiloé | Ancud, Chile, 1880–1881 | Sociedad secreta llevada a juicio | 4/5 |
| 6 | La Pascualita | Chihuahua, México, desde 1930 | Maniquí que dicen que es una novia embalsamada | 2/5 |
| 7 | Los asesinatos del hacha de Villisca | Iowa, EE. UU., 1912 | Crimen sin resolver y casa «encantada» | 4/5 |
| 8 | La Bestia de Gévaudan | Francia, 1764–1767 | Bestia real, nunca identificada con certeza | 4/5 |
| 9 | Hinterkaifeck | Baviera, Alemania, 1922 | Crimen sin resolver | 4/5 |
| 10 | Okiku, la muñeca a la que le crece el pelo | Iwamizawa, Japón, desde 1938 | Objeto encantado en un templo real | 1/5 |

Reparto: tres de España, dos de Sudamérica, una de México, una de Norteamérica, dos de Europa y una de Asia.

Puntos que hay que contrastar al redactar:
- **Gádor:** el papel exacto de Francisco Leona (barbero o curandero) y quién denunció el crimen.
- **Romasanta:** el número de víctimas (9 confirmadas y 13 confesadas, según la fuente) y la fecha de la condena (1852 o 1853).
- **Gévaudan:** la cifra de muertos (el estudio de 1987 da 113 y otras fuentes dan más).
- **Hinterkaifeck:** las huellas en la nieve vienen de relatos populares; se presentan como tales.
- **Villisca:** los nombres de los niños varían entre fuentes.
- **La Pascualita:** dónde está expuesta hoy.

### Las conexiones del hilo rojo

Cada historia se une a otras por un motivo que se escribe en el propio hilo (la nota a mano del tablero):

- Gádor ↔ Romasanta: *vendían grasa humana*.
- Gádor ↔ El Familiar: *vidas a cambio de salud o riqueza*.
- Romasanta ↔ Gévaudan: *¿hombre o bestia?*
- Villisca ↔ Hinterkaifeck: *familias enteras, ningún culpable*.
- La Pascualita ↔ Okiku: *figuras que parecen seguir vivas*.
- La Pascualita ↔ Siete Chimeneas: *una joven muerta antes de tiempo*.
- El Familiar ↔ Recta Provincia: *pactos y brujos*.
- Siete Chimeneas ↔ Hinterkaifeck: *alguien más vivía en la casa*.
- Recta Provincia ↔ Romasanta: *un tribunal frente a lo sobrenatural*.
- Gévaudan ↔ El Familiar: *una bestia que nadie podía detener*.
- Villisca ↔ Siete Chimeneas: *casas que siguen en pie*.
- Okiku ↔ Siete Chimeneas: *un espíritu que no se fue*.

Con esto cada historia tiene al menos dos conexiones (las cuatro últimas las añadí yo para completar el tablero). Quien redacte puede añadir más si el contenido lo justifica, señalándolas en la revisión.

### Cómo se escribe cada historia

- **Tono (decisión mía, pendiente de confirmar con el usuario):** el miedo viene de lo que se insinúa y del detalle real, nunca de la descripción explícita de la violencia. Con víctimas reales, sobre todo niños, el relato es sobrio y respetuoso.
- **Extensión:** de 600 a 1.000 palabras, unos 4–6 minutos de lectura.
- **Estructura:**
  1. Frase de impacto para la pantalla de entrada.
  2. Lugar y fecha.
  3. Relato con suspense.
  4. «Lo documentado» y «Lo que es leyenda».
  5. Nivel de verdad.
  6. Fuentes: de 3 a 6 enlaces reales, priorizando prensa de la época, archivos y trabajos académicos sobre los blogs.
  7. Conexiones.
- **Fuentes:** solo enlaces que se hayan abierto y comprobado. Si un dato no se puede verificar, se presenta como «se cuenta que…» o se quita.

## Cómo se construye (decisiones mías)

- **Web estática**, sin servidor: rápida, barata y fácil de publicar. Quien la construya elige si es HTML, CSS y JavaScript sin framework o un generador estático ligero como Astro, que facilitaría la fase 2.
- **Contenido separado del diseño:** cada historia en su propio archivo de datos (Markdown con cabecera o JSON), para que añadir historias no exija tocar el código.
- **Publicación:** GitHub Pages desde este repositorio, con un flujo de GitHub Actions. Un dominio propio se puede conectar después.
- **Sin rastreadores ni cookies.**

## Herramientas que se usan (petición del usuario: todas las instaladas)

- **Superpowers:** este proceso (diseño, plan con `writing-plans`, ejecución, pruebas, revisión de código y verificación antes de dar nada por terminado).
- **design-taste-frontend:** dirección de arte y su inspector en navegador real, para pulir tipografía, espaciado y composición sin perder la dirección ya elegida (B + C). Sus reglas de «sin adornos» se aplican salvo cuando contradigan la estética decidida.
- **playwright-cli:** capturas en móvil, tableta y escritorio de cada página, y pruebas de que la navegación por el hilo rojo funciona.
- **/web-interface-guidelines:** revisión final de accesibilidad, rendimiento y textos.
- **DESIGN.md (estilo de Claude):** no se usa para el aspecto, porque su fondo crema y su estilo cálido contradicen la estética oscura elegida. Decisión mía; se puede retirar del repositorio o adaptar.

## Accesibilidad y rendimiento

- Las revelaciones al hacer scroll y cualquier parpadeo respetan `prefers-reduced-motion`: sin animación, el texto aparece directamente.
- Nada parpadea más de tres veces por segundo.
- Contraste de al menos 4,5:1 en todo el texto.
- El tablero es navegable con teclado y tiene una alternativa en lista para lectores de pantalla.
- Las imágenes, si las hay, son de dominio público o con licencia libre, con su crédito.
- La primera pantalla carga rápido en el móvil: fuentes con `font-display: swap` y sin librerías pesadas.

## Lo que deja preparado para la fase 2

- El formato de datos de una historia admite un campo de origen («investigada» o «de la comunidad»).
- El tablero puede mostrar notas de otro estilo para las historias de la comunidad.

## Lo que queda a criterio de quien lo construya

- El framework o la ausencia de él, y la estructura de carpetas.
- Las animaciones concretas, siempre dentro de la estética de la transmisión.
- La forma del tablero en el móvil.
- Las imágenes, si alguna historia las necesita.
