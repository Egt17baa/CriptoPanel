package app.criptopanel

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.criptopanel.alerts.android.AlertService
import app.criptopanel.alerts.android.EXTRA_ROUTE
import app.criptopanel.core.ui.CriptoPanelTheme
import app.criptopanel.navigation.AppNavHost
import app.criptopanel.navigation.allowedExternalRoute
import app.criptopanel.security.VaultLock
import kotlinx.coroutines.flow.MutableStateFlow

/** A FragmentActivity because the biometric prompt needs one. */
class MainActivity : FragmentActivity() {
    /** Screen a tapped notification asked for; consumed by the navigation once shown. */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-dark app: light system bar icons even when the phone uses the light theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) pendingRoute.value = allowedExternalRoute(intent?.getStringExtra(EXTRA_ROUTE))
        setContent { CriptoPanelTheme { AppNavHost(pendingRoute) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        allowedExternalRoute(intent.getStringExtra(EXTRA_ROUTE))?.let { pendingRoute.value = it }
    }

    override fun onStart() {
        super.onStart()
        // Opening the app ends a pause from the notification.
        AlertService.paused = false
        VaultLock.keepOpenOnce = false
    }

    override fun onStop() {
        super.onStop()
        // Leaving the app locks the vault, except while a picker or the QR scanner we opened is on top.
        if (!VaultLock.keepOpenOnce) (application as CriptoPanelApp).container.vault.lock()
    }
}
