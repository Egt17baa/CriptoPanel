package app.criptopanel

import android.app.Application

class CriptoPanelApp : Application() {
    val container: AppContainer by lazy { AppContainer(BuildConfig.COINGECKO_DEMO_KEY, filesDir) }
}
