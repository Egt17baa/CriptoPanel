# CriptoPanel: sección «Aprende» (diseño)

Fecha: 22 de octubre de 2026. Versión 1.5.0. Subproyecto 4 de 4.

## Origen

En el primer brainstorming, el usuario pidió una sección «solo para hablar de las criptomonedas: los orígenes, criptografía, wallets calientes vs wallets frías, etc.». Al pedir «Hazlo», delegó el diseño. Las decisiones de este documento son de Claude y se pueden cambiar.

## Decisiones

- **Ubicación:** «Más» → **Aprende**, la primera entrada de la pantalla, en una tarjeta propia con el progreso («3 de 10 lecciones leídas»).
- **Contenido escrito dentro de la app**, en español, sin Internet: textos propios, sin copiar de ninguna web y revisados para que sean exactos. Las fechas y cifras son hechos públicos y conocidos.
- **10 lecciones,** de más básica a más avanzada:
  1. Qué es una criptomoneda
  2. Los orígenes: de los cypherpunks a Bitcoin
  3. Blockchain y consenso: prueba de trabajo y prueba de participación
  4. Criptografía sin fórmulas: hashes, claves y firmas
  5. Wallets: calientes frente a frías
  6. Ethereum y los contratos inteligentes
  7. Stablecoins
  8. DeFi: TVL, DEX y rendimientos
  9. Seguridad: estafas habituales y cómo evitarlas
  10. Cómo leer CriptoPanel (TOTAL2, dominancia, miedo y codicia…)
- **Formato de cada lección:**
  - nivel (Básico o Intermedio) y minutos de lectura;
  - bloques de contenido (títulos, párrafos, listas, avisos y tablas comparativas);
  - «En resumen»;
  - un mini-test de 3 preguntas con explicación. Al responder se ve si es correcto y por qué.
- **Glosario:** unos 40 términos de la A a la Z, con buscador.
- **Progreso:** una lección cuenta como leída al llegar al final o al terminar el test. Se guarda en el móvil (SharedPreferences), se puede reiniciar y no se envía a ninguna parte.
- **Cada lección termina con el aviso** de que es contenido educativo y no asesoramiento financiero.

## Arquitectura

- El paquete `learn` tiene:
  - `LearnContent.kt`, con el modelo (`Lesson`, `Block`, `Question`, `Term`) y el contenido;
  - `LearnProgress.kt`, que guarda el progreso;
  - las pantallas `LearnScreen` (índice), `LessonScreen` y `GlossaryScreen`.
- Rutas nuevas: `learn`, `lesson/{id}` y `glossary`.

## Pruebas

Integridad del contenido:

- ids únicos;
- cada lección tiene bloques y exactamente 3 preguntas;
- la respuesta correcta de cada pregunta es una opción válida;
- el glosario está ordenado y sin términos repetidos;
- ningún texto vacío.
