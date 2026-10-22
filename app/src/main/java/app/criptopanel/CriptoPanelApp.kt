package app.criptopanel

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import app.criptopanel.core.applySavedCurrency
import app.criptopanel.alerts.android.AndroidAlertNotifier
import app.criptopanel.security.DECRYPT_TMP_PREFIX

class CriptoPanelApp : Application(), SingletonImageLoader.Factory {
    override fun onCreate() {
        super.onCreate()
        applySavedCurrency(this)
        // A decrypted file left behind if the app died before it was saved.
        cacheDir.listFiles { f -> f.name.startsWith(DECRYPT_TMP_PREFIX) && f.name.endsWith(".tmp") }?.forEach { it.delete() }
    }

    val container: AppContainer by lazy { AppContainer(BuildConfig.COINGECKO_DEMO_KEY, filesDir, AndroidAlertNotifier(this)) }

    /** Coin logos use the app's own HTTPS client too, with the same certificate checks. */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(OkHttpNetworkFetcherFactory(callFactory = { container.client })) }
            .build()
}
