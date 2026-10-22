package app.criptopanel.alerts.android

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import app.criptopanel.CriptoPanelApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/** Resumes watching after the phone restarts or the app is updated. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED && intent.action != Intent.ACTION_MY_PACKAGE_REPLACED) return
        val pending = goAsync()
        val container = (context.applicationContext as CriptoPanelApp).container
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.alertStore.load()
                AlertService.sync(context, container.alertStore.data.value.alerts.count { it.enabled })
            } finally {
                pending.finish()
            }
        }
    }
}
