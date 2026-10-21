# CriptoPanel

App Android (Kotlin + Jetpack Compose) con el mercado cripto y un panel DeFi en un solo sitio.

## Compilar

Requisitos: Android Studio (o el SDK de Android con `compileSdk 36`) y JDK 17+.

```bash
./gradlew assembleDebug      # APK en app/build/outputs/apk/debug/
./gradlew installDebug       # instala en un emulador o móvil conectado
./gradlew :app:testDebugUnitTest
```

### Clave de CoinGecko (opcional)

La app funciona sin clave. Si quieres más margen de peticiones, crea una clave Demo gratuita en CoinGecko
y añádela a `local.properties`:

```
COINGECKO_DEMO_KEY=tu_clave
```

## Tests sin SDK de Android

`jvm-check/` es un proyecto Gradle que compila la lógica de `app/src/main` (todo salvo las pantallas)
y ejecuta `app/src/test` solo con Maven Central:

```bash
gradle -p jvm-check test
```
