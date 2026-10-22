package app.criptopanel.alerts

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.alerts.android.AlertService
import app.criptopanel.alerts.android.canNotify
import app.criptopanel.alerts.android.statusText
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.DisclaimerLine
import app.criptopanel.core.ui.GlassCard
import coil3.compose.AsyncImage
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

/** Phone makers whose battery savers kill background apps unless the user allows them by hand. */
private val aggressiveMakers = setOf("xiaomi", "redmi", "poco", "huawei", "honor", "oppo", "realme", "vivo", "oneplus")

private val firedFormat = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.SHORT)

fun pctLabel(value: Double): String = (if (value % 1.0 == 0.0) "%.0f" else "%.1f").format(value).replace('.', ',') + " %"

/** Third section of the Cartera tab: watch status, setup hints and the alert list. */
@Composable
fun AlertsSection(vm: AlertsViewModel, snackbar: SnackbarHostState, onEdit: (String) -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val status by vm.monitor.status.collectAsStateWithLifecycle()
    val quotes by vm.monitor.quotes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Re-read permission and battery settings when coming back from the system screens.
    var resumes by remember { mutableIntStateOf(0) }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { resumes++ }
    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val notificationsOk = remember(resumes) { canNotify(context) }
    val batteryOk = remember(resumes) {
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) ?: true
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { resumes++ }
    val enabled = data.alerts.count { it.enabled }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text("Vigilancia", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        when {
                            enabled == 0 -> "Sin alertas activas"
                            AlertService.paused -> "En pausa · $enabled ${if (enabled == 1) "alerta" else "alertas"}"
                            else -> "${statusText(status)} · $enabled ${if (enabled == 1) "alerta" else "alertas"}"
                        },
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (AlertService.paused && enabled > 0) {
                        TextButton(onClick = {
                            AlertService.paused = false
                            AlertService.sync(context, enabled)
                            resumes++
                        }) { Text("Reanudar") }
                    }
                }
            }
        }
        if (!notificationsOk && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            item {
                Hint("Permite las notificaciones para recibir las alertas.", "Permitir") {
                    permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
        if (!batteryOk && enabled > 0) {
            item {
                Hint("Para que las alertas lleguen con la pantalla apagada, quita la optimización de batería a CriptoPanel.", "Ajustes") {
                    runCatching { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
                }
            }
        }
        if (enabled > 0 && Build.MANUFACTURER.lowercase() in aggressiveMakers) item { MakerHelp() }
        if (data.alerts.isEmpty()) {
            item {
                Text(
                    "Crea una alerta con «+ Alerta» o con la 🔔 en el detalle de una moneda. " +
                        "Te avisaremos al momento aunque tengas la app cerrada.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }
        items(data.alerts.sortedByDescending { it.enabled }, key = { it.id }) { alert ->
            AlertRow(
                alert = alert,
                price = alert.coinId?.let { quotes[it]?.price },
                warmup = if (alert.enabled) vm.monitor.warmupMinutes(alert) else 0,
                onClick = { onEdit(alert.id) },
                onToggle = { vm.setEnabled(alert, it) },
                onDelete = {
                    vm.delete(alert)
                    scope.launch {
                        val r = snackbar.showSnackbar("Alerta borrada", actionLabel = "Deshacer", duration = SnackbarDuration.Short)
                        if (r == SnackbarResult.ActionPerformed) vm.restore(alert)
                    }
                },
            )
            HorizontalDivider()
        }
        item { DisclaimerLine() }
    }
}

@Composable
private fun Hint(text: String, action: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onClick) { Text(action) }
        }
    }
}

@Composable
private fun MakerHelp() {
    var open by rememberSaveable { mutableStateOf(false) }
    GlassCard(Modifier.fillMaxWidth(), onClick = { open = !open }) {
        Column(Modifier.padding(16.dp)) {
            Text("¿No te llegan las alertas? ${if (open) "▲" else "▼"}", style = MaterialTheme.typography.titleSmall)
            if (open) {
                Text(
                    "Tu móvil (${Build.MANUFACTURER}) cierra las apps en segundo plano para ahorrar batería. Para evitarlo:\n" +
                        "1. Ajustes → Aplicaciones → CriptoPanel → Ahorro de batería → Sin restricciones.\n" +
                        "2. En la misma pantalla, activa «Inicio automático» (en Xiaomi/HyperOS: Ajustes → Aplicaciones → Permisos → Inicio automático).\n" +
                        "3. En la vista de apps recientes, mantén pulsada CriptoPanel y toca el candado para que no se cierre.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun AlertRow(alert: Alert, price: Double?, warmup: Int, onClick: () -> Unit, onToggle: (Boolean) -> Unit, onDelete: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp),
    ) {
        if (alert.imageUrl != null) {
            AsyncImage(model = alert.imageUrl, contentDescription = null, modifier = Modifier.size(28.dp))
        } else {
            Text("💼", modifier = Modifier.size(28.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(
                alert.describe({ formatUsd(it) }, ::pctLabel),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (alert.enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val extra = listOfNotNull(
                price?.let { "Ahora ${formatUsd(it)}" },
                "Calentando: faltan $warmup min".takeIf { warmup > 0 },
                alert.lastTriggered?.let { "Saltó ${Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).format(firedFormat)}" },
                alert.note.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (extra.isNotEmpty()) {
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Switch(checked = alert.enabled, onCheckedChange = onToggle)
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Borrar alerta") }
    }
}
