package app.criptopanel.alerts.android

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.criptopanel.MainActivity
import app.criptopanel.R
import app.criptopanel.alerts.AlertNotifier
import app.criptopanel.alerts.FiredAlert
import app.criptopanel.alerts.MonitorStatus

const val CHANNEL_ALERTS = "price_alerts"
const val CHANNEL_WATCH = "alert_watch"
const val WATCH_NOTIFICATION_ID = 1

/** Route MainActivity opens when a notification is tapped. */
const val EXTRA_ROUTE = "app.criptopanel.ROUTE"
const val ROUTE_ALERTS = "portfolio?tab=2"

fun createAlertChannels(context: Context) {
    val nm = context.getSystemService(NotificationManager::class.java)
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_ALERTS, "Alertas de precio", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Avisos cuando se cumple una de tus alertas"
        },
    )
    nm.createNotificationChannel(
        NotificationChannel(CHANNEL_WATCH, "Vigilancia de alertas", NotificationManager.IMPORTANCE_MIN).apply {
            description = "Aviso fijo mientras CriptoPanel vigila tus alertas en directo"
            setShowBadge(false)
        },
    )
}

fun openAppIntent(context: Context, route: String, requestCode: Int): PendingIntent =
    PendingIntent.getActivity(
        context,
        requestCode,
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

fun canNotify(context: Context): Boolean =
    Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

/** Posts a high-priority, sounding notification per fired alert; tapping it opens the coin (or the portfolio). */
class AndroidAlertNotifier(private val context: Context) : AlertNotifier {
    override fun notify(fired: FiredAlert) {
        if (!canNotify(context)) return
        val route = fired.alert.coinId?.let { "coin/${android.net.Uri.encode(it)}" } ?: ROUTE_ALERTS
        val id = fired.alert.id.hashCode()
        val n = NotificationCompat.Builder(context, CHANNEL_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(fired.title)
            .setContentText(fired.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(fired.text))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent(context, route, id))
            .build()
        try {
            NotificationManagerCompat.from(context).notify(id, n)
        } catch (e: SecurityException) {
            // Permission revoked between the check and the call: nothing to show.
        }
    }
}

fun statusText(status: MonitorStatus): String = when (status) {
    MonitorStatus.LIVE -> "En directo (Binance)"
    MonitorStatus.POLLING -> "Comprobando cada minuto"
    MonitorStatus.CONNECTING -> "Conectando…"
    MonitorStatus.OFFLINE -> "Sin conexión, reintentando"
    MonitorStatus.IDLE -> "En pausa"
}
