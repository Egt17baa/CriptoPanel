# Publicar CriptoPanel en Google Play

## 0. Antes de nada: el identificador de la app

El `applicationId` es `app.criptopanel` (en `app/build.gradle.kts`). **No se puede cambiar después de
publicar** y tiene que ser único en Google Play. Si prefieres uno tuyo (por ejemplo `com.tunombre.criptopanel`),
cámbialo antes de la primera subida.

## 1. Cuenta de desarrollador

1. Crea la cuenta en <https://play.google.com/console> (pago único de 25 $ y verificación de identidad).
2. Las cuentas **personales** nuevas deben hacer una **prueba cerrada con al menos 12 testers durante 14 días**
   antes de poder publicar en producción. Las cuentas de organización no tienen ese requisito.

## 2. Crear tu clave de subida (una sola vez)

```bash
keytool -genkeypair -v -keystore criptopanel-upload.jks -alias criptopanel \
  -keyalg RSA -keysize 2048 -validity 10000
```

Guarda el archivo `.jks` y las contraseñas en un sitio seguro (gestor de contraseñas + copia de seguridad).
**Nunca** lo subas a Git: `.gitignore` ya ignora `*.jks` y `keystore.properties`.
Google Play firma la app final con su propia clave (*Play App Signing*); la tuya solo identifica tus subidas.

## 3. Generar el AAB firmado

### Opción A — en tu ordenador

Crea `keystore.properties` en la raíz del proyecto:

```properties
storeFile=criptopanel-upload.jks
storePassword=TU_CONTRASEÑA
keyAlias=criptopanel
keyPassword=TU_CONTRASEÑA
```

y ejecuta (sube `versionCode` en cada nueva versión):

```bash
./gradlew :app:bundleRelease -PversionCode=1
```

El archivo para Google Play queda en `app/build/outputs/bundle/release/app-release.aab`.

### Opción B — con GitHub Actions

En GitHub: *Settings → Secrets and variables → Actions → New repository secret*:

| Secreto | Valor |
|---|---|
| `CRIPTOPANEL_KEYSTORE_BASE64` | el `.jks` en base64: `base64 -w0 criptopanel-upload.jks` |
| `CRIPTOPANEL_KEYSTORE_PASSWORD` | contraseña del almacén |
| `CRIPTOPANEL_KEY_ALIAS` | `criptopanel` |
| `CRIPTOPANEL_KEY_PASSWORD` | contraseña de la clave |

Después crea una etiqueta (`git tag v1.0.0 && git push origin v1.0.0`) o lanza el workflow *Build APK*
a mano. El job **play-bundle** deja el AAB como artefacto `CriptoPanel-google-play-aab`.
El `versionCode` es el número de ejecución del workflow, así que siempre sube solo.

## 4. Ficha de la tienda

Textos listos para copiar en [`ficha-tienda.md`](ficha-tienda.md). Imágenes en [`store/`](../../store):

- Icono 512×512: `store/icon-512.png`
- Gráfico destacado 1024×500: `store/feature-graphic-1024x500.png`
- Capturas: al menos 2 del móvil (puedes usar capturas de pantalla reales de la app).

## 5. Formularios obligatorios de Play Console

- **Política de privacidad**: Play pide una URL pública. Publica
  [`politica-privacidad.md`](politica-privacidad.md) (por ejemplo con GitHub Pages o en una web tuya) y pega la URL.
- **Seguridad de los datos**: la app no tiene cuentas, no guarda ni envía datos personales y no usa
  analíticas propias. Los gráficos se cargan desde el widget de TradingView, que puede usar cookies o
  identificadores propios; revisa su política y decláralo si corresponde.
- **Funciones financieras**: la app solo **muestra información**; no permite comprar, vender, custodiar
  criptomonedas ni conecta carteras. Elige la opción de que la app no ofrece servicios financieros.
- **Público objetivo**: recomendado **18+** (contenido sobre criptomonedas).
- **Clasificación de contenido**: rellena el cuestionario (categoría *Referencia / noticias / educación*).
- **Anuncios**: la app no contiene anuncios.
- **Servicio en primer plano** (*Contenido de la app → Servicios en primer plano*): declara el tipo
  `specialUse` con el texto de [`ficha-tienda.md`](ficha-tienda.md) y un vídeo corto (se puede grabar con el móvil)
  donde se cree una alerta, salga la notificación fija «Vigilando N alertas» y llegue el aviso.
- **Cifrado / exportación** (*Contenido de la app* o al subir la versión): responde que **sí usa cifrado**, solo
  con algoritmos estándar (AES, ChaCha20, Twofish, Serpent, Camellia) para proteger datos del propio usuario.
  Encaja en la exención de mercado masivo; no hace falta ningún documento.
- **Permisos**: notificaciones (alertas), arranque del móvil (reactivar las alertas) e Internet. La huella usa
  la API biométrica del sistema; la app nunca ve la huella.
- **Gratis para siempre**: una app publicada como gratuita **no puede pasar a ser de pago** después
  (sí se podrían añadir compras dentro de la app, aunque no es el plan).

## 6. Subir

1. *Crear app* → nombre **CriptoPanel**, idioma español, app gratuita.
2. *Pruebas → Prueba cerrada* (o *Producción* si tu cuenta lo permite) → *Crear versión* → sube el `.aab`.
3. Completa las notas de la versión y envía a revisión.

## Comprobación antes de cada subida

```bash
./gradlew :app:testDebugUnitTest :app:lintRelease :app:bundleRelease -PversionCode=N
```
