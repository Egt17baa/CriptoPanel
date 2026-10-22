# CriptoPanel: apartado «Seguridad» (diseño)

Fecha: 22 de octubre de 2026. Subproyecto 5. Estado: fases A (1.7.0) y B (1.8.0) implementadas. Por su tamaño se entrega en dos fases:

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
