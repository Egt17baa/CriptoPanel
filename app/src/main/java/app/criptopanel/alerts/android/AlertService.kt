package app.criptopanel.alerts.android

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.criptopanel.CriptoPanelApp
import app.criptopanel.R
import app.criptopanel.alerts.MonitorStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Keeps the process alive while alerts are enabled, so [app.criptopanel.alerts.AlertMonitor] can watch prices live.
 * Foreground type specialUse: dataSync is capped at 6 h a day on Android 15.
 */
class AlertService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private val container get() = (application as CriptoPanelApp).container

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_PAUSE) {
            paused = true
            stopSelf()
            return START_NOT_STICKY
        }
        createAlertChannels(this)
        try {
            ServiceCompat.startForeground(
                this,
                WATCH_NOTIFICATION_ID,
                watching(0, MonitorStatus.CONNECTING),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0,
            )
        } catch (e: Exception) {
            // Not allowed to go foreground now (e.g. restarted from the background on Android 12+).
            stopSelf()
            return START_NOT_STICKY
        }
        if (!started) {
            started = true
            val monitor = container.alertMonitor
            monitor.start(scope)
            scope.launch {
                container.alertStore.load()
                combine(container.alertStore.data, monitor.status) { d, s -> d.alerts.count { it.enabled } to s }
                    .collect { (count, status) ->
                        if (count == 0) {
                            stopSelf()
                        } else if (canNotify(this@AlertService)) {
                            try {
                                NotificationManagerCompat.from(this@AlertService).notify(WATCH_NOTIFICATION_ID, watching(count, status))
                            } catch (e: SecurityException) {
                                // Permission revoked meanwhile.
                            }
                        }
                    }
            }
            watchNetwork()
        }
        return START_STICKY
    }

    private fun watchNetwork() {
        val cm = getSystemService(ConnectivityManager::class.java) ?: return
        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) = container.alertMonitor.reconnectNow()
        }
        try {
            cm.registerDefaultNetworkCallback(callback)
            networkCallback = callback
        } catch (e: Exception) {
            // Too many callbacks registered by the system; backoff still reconnects.
        }
    }

    private fun watching(count: Int, status: MonitorStatus): Notification {
        val pause = PendingIntent.getService(
            this,
            1,
            Intent(this, AlertService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = when (count) {
            0 -> "CriptoPanel vigila tus alertas"
            1 -> "CriptoPanel vigila 1 alerta"
            else -> "CriptoPanel vigila $count alertas"
        }
        return NotificationCompat.Builder(this, CHANNEL_WATCH)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(statusText(status))
            .setOngoing(true)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openAppIntent(this, ROUTE_ALERTS, 2))
            .addAction(0, "Pausar", pause)
            .build()
    }

    override fun onDestroy() {
        container.alertMonitor.stop()
        networkCallback?.let { cb -> getSystemService(ConnectivityManager::class.java)?.unregisterNetworkCallback(cb) }
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val ACTION_PAUSE = "app.criptopanel.alerts.PAUSE"

        /** Set by the Pausar action; cleared when the app is opened again. */
        @Volatile var paused = false

        /** Runs the service while [enabledAlerts] > 0 (and not paused), stops it otherwise. */
        fun sync(context: Context, enabledAlerts: Int) {
            val intent = Intent(context, AlertService::class.java)
            if (enabledAlerts > 0 && !paused) {
                try {
                    ContextCompat.startForegroundService(context, intent)
                } catch (e: Exception) {
                    // Background start refused (Android 12+); the next app start retries.
                }
            } else {
                context.stopService(intent)
            }
        }
    }
}
