package app.criptopanel

import android.app.Application
import app.criptopanel.core.applySavedCurrency
import app.criptopanel.alerts.android.AndroidAlertNotifier

class CriptoPanelApp : Application() {
    override fun onCreate() {
        super.onCreate()
        applySavedCurrency(this)
    }

    val container: AppContainer by lazy { AppContainer(BuildConfig.COINGECKO_DEMO_KEY, filesDir, AndroidAlertNotifier(this)) }
}
