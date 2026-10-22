package app.criptopanel

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import app.criptopanel.alerts.android.AlertService
import app.criptopanel.alerts.android.EXTRA_ROUTE
import app.criptopanel.core.ui.CriptoPanelTheme
import app.criptopanel.navigation.AppNavHost
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    /** Screen a tapped notification asked for; consumed by the navigation once shown. */
    private val pendingRoute = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Always-dark app: light system bar icons even when the phone uses the light theme.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        if (savedInstanceState == null) pendingRoute.value = intent?.getStringExtra(EXTRA_ROUTE)
        setContent { CriptoPanelTheme { AppNavHost(pendingRoute) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(EXTRA_ROUTE)?.let { pendingRoute.value = it }
    }

    override fun onStart() {
        super.onStart()
        // Opening the app ends a pause from the notification.
        AlertService.paused = false
    }
}
