# CriptoPanel: apartado «Seguridad» (diseño)

Fecha: 22 de octubre de 2026. Subproyecto 5. Estado: fases A (1.7.0), B (1.8.0) y C (1.9.0) implementadas. Por su tamaño se entrega en dos fases:

- **Fase A (1.7.0):** núcleo criptográfico, cifrado de texto, cifrado de archivos, generador de contraseñas y comprobadores.
- **Fase B (1.8.0):** bóveda de contraseñas con huella, autenticador 2FA y exportación de la bóveda.

## Decisiones tomadas en el brainstorming

- **Herramientas:** bóveda con generador, cifrado de texto y de archivos, autenticador 2FA y comprobadores (filtraciones y huella SHA-256).
- **Desbloqueo de la bóveda:** contraseña maestra, y huella o cara para las siguientes veces.
- **Copia de seguridad:** la bóveda **no** va en la copia de Google. Se exporta a mano a un archivo cifrado.
- **Ubicación:** Más → **Seguridad**, una sección propia con su pantalla de inicio. No es una pestaña nueva porque la barra inferior ya tiene 5.

## Criptografía (igual para todas las herramientas)

| Pieza | Elección | Motivo |
|---|---|---|
| Cifrado | **AES-256-GCM** (por defecto) o **ChaCha20-Poly1305** | Cifrado autenticado: además de ocultar, detecta cualquier manipulación. AES usa la aceleración por hardware del móvil; ChaCha20 es la alternativa moderna, rápida sin hardware dedicado. |
| Contraseña → clave | **Argon2id**: 64 MiB, 3 pasadas, 1 hilo, sal de 16 bytes | Ganador de la Password Hashing Competition y recomendado por OWASP. Que necesite mucha memoria encarece mucho los ataques con GPU. |
| Aleatoriedad | `SecureRandom` | Generador criptográfico del sistema. |
| Nonce | 12 bytes aleatorios por mensaje | Nunca se repite con la misma clave. |
| Librería | Bouncy Castle (`bcprov-jdk18on`), API ligera, para Argon2id y ChaCha20-Poly1305; AES-GCM con `javax.crypto` del sistema | Ninguna implementación propia de primitivas. |

### Formato de texto cifrado

`CP1:` seguido, en Base64 URL-safe, de `cabecera ‖ cifrado ‖ etiqueta`. La cabecera lleva:

- magia `CP`, versión 1 y algoritmo (1 = AES-256-GCM, 2 = ChaCha20-Poly1305);
- KDF (1 = Argon2id), memoria en KiB, pasadas y paralelismo;
- sal (16 bytes) y nonce (12 bytes).

La cabecera entera va como datos asociados (AAD): no se puede alterar sin que el descifrado falle. Al guardar los parámetros, se pueden subir en el futuro sin romper lo cifrado antes.

### Formato de archivo cifrado (`.cpenc`)

- **Cabecera** como la del texto, pero con un prefijo de nonce de 7 bytes.
- **Fragmentos de 64 KiB** con la construcción STREAM (la de *age* o Tink): `nonce = prefijo (7) ‖ contador (4, big-endian) ‖ último (1)`. Cada fragmento tiene su propia etiqueta.
  - El indicador de último fragmento impide truncar el archivo sin que se note.
  - El contador impide reordenar los fragmentos.
- **Primer registro:** el nombre original del archivo, también cifrado.
- **Memoria:** se cifra y descifra en streaming, sin cargar el archivo entero.
- **Selección de archivos:** con el selector de Android (Storage Access Framework), sin permisos de almacenamiento.

## Herramientas de la fase A

1. **Cifrar texto:** se escribe o pega el texto y una contraseña (con la opción de generar una), se elige AES o ChaCha20 y sale el resultado `CP1:…` con los botones Copiar y Compartir. **Descifrar:** se pega el `CP1:…` y la contraseña. El algoritmo se lee de la cabecera.
2. **Cifrar archivo:** se elige el archivo, la contraseña y el algoritmo, y se guarda `nombre.cpenc` donde quieras. **Descifrar:** se elige el `.cpenc`, la contraseña y dónde guardarlo. Si la contraseña es incorrecta o el archivo está dañado: «Contraseña incorrecta o archivo dañado», y no se escribe nada.
3. **Generador:**
   - **Contraseña:** de 8 a 64 caracteres; mayúsculas, minúsculas, números y símbolos; la opción de quitar caracteres ambiguos (0O1lI); al menos uno de cada tipo elegido.
   - **Frase:** de 4 a 10 palabras de la lista grande de la EFF (7.776 palabras en inglés, licencia CC BY 3.0, con atribución), con separador y la opción de un número.
   - Muestra la **entropía en bits** y un nivel: débil por debajo de 50, aceptable de 50 a 79, fuerte de 80 a 99 y muy fuerte desde 100.
4. **¿Filtrada?** Comprueba una contraseña contra Have I Been Pwned con k-anonimato: se calcula su SHA-1 en el móvil y **solo se envían los 5 primeros caracteres del hash**, con relleno (`Add-Padding: true`). La contraseña nunca sale del móvil. Dice «Aparece N veces en filtraciones» o «No aparece».
5. **Huella de archivo:** SHA-256 de cualquier archivo (en streaming), con un campo para pegar la huella esperada y ver si coincide (✓ o ✗). Sirve para verificar descargas como el APK o un firmware de wallet.

## Protecciones comunes

- `FLAG_SECURE` en todas las pantallas de Seguridad: sin capturas de pantalla ni vista previa en apps recientes.
- **Portapapeles:** se marca como sensible (`EXTRA_IS_SENSITIVE`, Android 13+) y se borra solo a los 45 s si sigue conteniendo lo copiado.
- Las contraseñas se manejan como `CharArray` y se ponen a cero tras derivar la clave. Las claves derivadas también se ponen a cero al terminar.
- Nada de lo escrito se guarda en disco en la fase A.

## Fase B (implementada)

- **Bóveda** (`files/vault.bin`, fuera de la copia de seguridad):
  - una clave de datos aleatoria (DEK) cifra las entradas con AES-256-GCM;
  - la DEK se guarda envuelta dos veces: con la clave Argon2id de la contraseña maestra y con una clave AES del **Android Keystore** que exige huella o cara (`BIOMETRIC_STRONG`, y se invalida si se añade una huella nueva).
- **Tipos de entrada:** acceso (usuario, contraseña, web y notas), **frase semilla de wallet**, nota segura y código 2FA.
- **Bloqueo** automático al salir de la app y tras 5 minutos. Se pide la contraseña maestra cada 7 días aunque haya huella.
- **Autenticador 2FA** (TOTP, RFC 6238): con SHA-1, SHA-256 y SHA-512, de 6 u 8 dígitos y 30 s. Se añade escaneando el QR (escáner de Google Play services, sin permiso de cámara para la app), pegando un `otpauth://` o escribiendo la clave.
- **Exportar e importar** la bóveda cifrada a un archivo. Al importar se pide su contraseña maestra.
- **Sin recuperación:** si se olvida la contraseña maestra, los datos no se pueden recuperar. Se avisa al crear la bóveda.

## Fase C (1.9.0): frases semilla, más algoritmos y clave propia

Petición del usuario: cifrar las 12 o 24 palabras de una wallet con una clave que guarda él y **sin huella**.

- **Modo frase semilla** en Cifrar texto: autocompletado con la lista BIP-39 inglesa (2.048 palabras), aviso de palabras que no existen con sugerencia (prefijo único de 4 letras o una letra de diferencia), recuento y **checksum BIP-39** (SHA-256). Al descifrar, si el resultado es una frase válida se muestra numerada en columnas.
- **Algoritmos** (todos con 256 bits y cifrado autenticado):

| Id | Algoritmo | Implementación |
|---|---|---|
| 1 | AES-256-GCM (por defecto) | `javax.crypto` |
| 2 | ChaCha20-Poly1305 | Bouncy Castle |
| 3 | Twofish-256-GCM | Bouncy Castle |
| 4 | Serpent-256-GCM | Bouncy Castle |
| 5 | Camellia-256-GCM | Bouncy Castle |
| 6 | Cascada AES → Twofish → Serpent | Tres capas GCM; cada capa con su clave derivada por HKDF-SHA256 (info `criptopanel-cascade-<capa>`). Sobrecarga: 48 bytes. |

- **Secreto:** contraseña (KDF 1 = Argon2id) o **clave AES-256 aleatoria**: desde 1.9.1, KDF 3 = la clave se expande con HKDF-SHA256 y una sal aleatoria de 16 bytes, así cada texto o archivo usa una clave distinta (los de 1.9.0, KDF 2 = clave directa, se siguen leyendo). La clave se muestra como 64 caracteres hexadecimales en grupos de 4; al pegarla se ignoran espacios y mayúsculas. Si se intenta descifrar con el tipo de secreto equivocado, el mensaje lo dice.
- **Descifrar** lee la cabecera y avisa del algoritmo y de si hace falta clave o contraseña.
- Los archivos `.cpenc` admiten los mismos algoritmos y secretos; cada registro lleva la sobrecarga de su algoritmo.
- **Independencia:** solo se usan algoritmos públicos y libres de patentes (AES, ChaCha20, Twofish, Serpent y Camellia), implementados por `javax.crypto` y Bouncy Castle (licencia MIT). No hay código, formato, nombre ni diseño de ninguna otra app; el formato `CP1` es propio.
- **Pruebas:** vectores de Camellia (RFC 3713) y Twofish; ida y vuelta de texto y archivos con cada algoritmo y cada tipo de secreto; secreto equivocado y manipulación; checksums BIP-39 conocidos y sugerencias.

## Auditoría de seguridad (1.9.1)

Revisión de todo el código (cifrado, bóveda, red, WebView, intents, importación, compilación). Corregido:

| Gravedad | Problema | Arreglo |
|---|---|---|
| Media | El APK público se firmaba con la clave de prueba, que está en el repositorio (público): cualquiera podía firmar una «actualización» falsa que, instalada encima, leería la bóveda. | El workflow firma con la clave privada de los secretos del repositorio cuando existen (ver PUBLICAR.md). |
| Media | MainActivity está exportada y navegaba a cualquier ruta recibida: otra app podía abrir cualquier pantalla o cerrar la app con una ruta falsa. | Solo se aceptan las rutas de nuestras notificaciones (`allowedExternalRoute`) y la navegación no puede romper la app. |
| Media | Al pasar de una pantalla de Seguridad a otra, la anterior quitaba `FLAG_SECURE` al salir: la nueva quedaba sin protección contra capturas. | Contador de pantallas seguras. |
| Media | El borrado del portapapeles a los 45 s se cancelaba al salir de la pantalla: la frase copiada se quedaba en el portapapeles. | Temporizador en un ámbito del proceso, no de la pantalla. |
| Baja | La frase semilla de la bóveda y el texto libre usaban el teclado normal, que aprende y sugiere palabras. | Teclado de contraseña, sin autocorrector. |
| Baja | Un archivo descifrado podía quedarse en la caché si la app moría antes de guardarlo. | La ruta sobrevive a la recreación y los restos se borran al arrancar. |
| Baja | Una cabecera manipulada podía pedir 1 GiB a Argon2id (cierre por falta de memoria). | Máximo 256 MiB. |
| Baja | Clave AES-256 propia usada tal cual en todos los mensajes. | HKDF con sal aleatoria por mensaje (KDF 3). |
| Baja | CSV: celdas que empiezan por `= + - @` se ejecutaban como fórmulas en Excel; un archivo enorme cerraba la app. | Se anteponen con `'` (y se quitan al importar); límite de 5 MB. |
| Baja | El WebView de TradingView podía abrir enlaces de cualquier esquema (deep links de otras apps). | Solo `http`/`https`. |
| Baja | Sin protección contra superposiciones (tapjacking) en Android 8–11. | `filterTouchesWhenObscured` en las pantallas de Seguridad. |

Revisado y correcto: HTTPS/WSS en todas las conexiones, sin tráfico en claro, sin registros (logs) de datos, `PendingIntent` inmutables y explícitos, servicio y receptor no exportados, copias de seguridad sin bóveda, R8 activado, sin vulnerabilidades conocidas en OkHttp 5.3.2, Retrofit 3.0.0 y Bouncy Castle 1.80, huella con `CryptoObject` y clave del Keystore invalidada al añadir huellas, nombres de archivo sin rutas.

Riesgos que quedan (inherentes): las contraseñas escritas pasan por `String` de Compose y no se pueden borrar de memoria; un móvil con root o malware con permisos de accesibilidad puede leer la pantalla; no hay auditoría externa.

## Segunda revisión (1.9.2)

**Borrado de memoria (zeroing):** contraseñas como `CharArray`, claves derivadas (Argon2id, HKDF, capas de la cascada), la DEK, la KEK y la clave propia se ponen a cero al terminar; desde 1.9.2 también los bytes en claro (texto cifrado y descifrado, cada bloque de 64 KiB de los archivos, el JSON de la bóveda, las capas intermedias de la cascada y la clave TOTP). Límite inevitable: lo que se escribe o se muestra en pantalla pasa por `String` de Compose/Java, que no se puede borrar.

**Derivación pesada:** Argon2id con 64 MiB, 3 pasadas (≈1 s en un móvil), por encima del mínimo de OWASP (19 MiB, 2 pasadas). La clave propia de 256 bits no la necesita (no se puede adivinar) y usa HKDF.

**Streaming:** los archivos se cifran y descifran por registros de 64 KiB (como *age*); la memoria no depende del tamaño. Prueba: un archivo mayor que toda la memoria del proceso de pruebas pasa por cifrado y descifrado encadenados.

Corregido:

| Gravedad | Problema | Arreglo |
|---|---|---|
| Alta | Bloquear la bóveda (salir de la app o inactividad) mientras se guardaba ponía a cero la clave en uso: los datos podían guardarse con una clave de ceros y la bóveda quedaba inservible con la contraseña maestra. | Las operaciones usan una copia de la clave y un contador de bloqueos: se guarda con la clave buena y no se reabre si hubo bloqueo. Lo mismo para un desbloqueo que termina con la app ya cerrada. |
| Media | Cambiar la contraseña maestra no cambiaba la clave de datos: una copia antigua + la contraseña antigua filtrada abrían también lo guardado después. | Nueva clave de datos en cada cambio de maestra. |
| Media | Al salir de la app, Android 10+ no deja leer el portapapeles y no se borraba. | Se borra igualmente a los 45 s. |
| Media | Un QR 2FA con una clave de 1 carácter bloqueaba para siempre la bóveda y el autenticador. | Mínimo 80 bits (16 caracteres Base32). |
| Baja | Cambiar la maestra con la bóveda ya bloqueada decía «cambiada» sin cambiar nada. | Se avisa de que no se ha cambiado. |
| Baja | Importar una bóveda enorme podía agotar la memoria. | Límite de 20 MB y lectura fuera del hilo principal. |
| Baja | Contador de registros de archivo sin tope. | Se rechaza antes de repetir un nonce (256 TiB). |

## Bóveda versión 2 (1.9.3): todo el archivo autenticado

Contra quien tenga **root** y pueda escribir en los archivos de la app:

- La clave envuelta lleva como datos asociados el formato, la versión, el coste de Argon2id y la sal.
- Los datos llevan además el número de **revisión** (sube en cada guardado) y la fecha de la última contraseña maestra.
- Resultado: cambiar la fecha para alargar los 7 días de huella, mezclar datos antiguos con el archivo actual, cambiar el coste o la sal, o bajar a la versión 1, hace fallar el descifrado.
- La huella no se ofrece con una fecha futura ni con archivos de versión 1.
- Las bóvedas y copias de versión 1 se abren y pasan a versión 2 en el siguiente desbloqueo con la contraseña maestra (la misma clave de datos, así la huella sigue valiendo).

Lo que ninguna app puede impedir con root: volver a poner el archivo completo de una fecha anterior (no hay almacenamiento de confianza para un contador), leer la memoria mientras la bóveda está abierta o capturar lo que se escribe.

## Avisos

- En Seguridad, en Términos y en Privacidad: las herramientas no han pasado una auditoría de seguridad independiente. Para fondos importantes se recomienda una wallet hardware y un gestor de contraseñas auditado.
- Privacidad: el comprobador de filtraciones envía 5 caracteres de un hash a Have I Been Pwned, y nada más.

## Pruebas (fase A)

- Vectores oficiales de AES-GCM (NIST), ChaCha20-Poly1305 (RFC 8439) y Argon2id (RFC 9106).
- **Texto:** ida y vuelta con los dos algoritmos; la contraseña incorrecta falla; cualquier byte alterado en la cabecera o en el cifrado hace fallar; el formato es estable.
- **Archivos:** ida y vuelta con 0 bytes, menos de un fragmento, exactamente un fragmento y varios; el truncado, el reordenado y el bit cambiado se detectan; el nombre se recupera.
- **Generador:** longitud, conjuntos de caracteres, al menos uno de cada tipo, sin ambiguos y cálculo de la entropía; 7.776 palabras sin duplicados.
- **HIBP:** se envían solo 5 caracteres, la respuesta se interpreta bien y las líneas de relleno con cuenta 0 se ignoran.
- **SHA-256:** vector conocido («abc»).

## Pruebas (fase B)

- Bóveda: ida y vuelta, contraseña maestra incorrecta, datos manipulados, intercambio entre el bloque de la clave y el de los datos (rechazado gracias a datos asociados distintos), cambio de maestra, exportar e importar (con contraseña incorrecta no se toca nada), bloqueo por inactividad, huella rechazada a los 7 días.
- TOTP: los vectores del RFC 6238 con SHA-1, SHA-256 y SHA-512; Base32 con espacios y minúsculas; enlaces `otpauth://` válidos y no válidos.
- Se verifica a mano en el móvil: activar huella, bloquear al salir, escanear un QR de prueba y comparar el código con otra app de 2FA.
