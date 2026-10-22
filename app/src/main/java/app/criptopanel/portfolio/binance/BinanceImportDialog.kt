package app.criptopanel.portfolio.binance

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.criptopanel.core.ui.MenuColor
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.portfolio.PortfolioStore
import app.criptopanel.portfolio.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** CSV and Excel files as Android's file picker labels them. */
val BINANCE_FILE_TYPES = arrayOf(
    "text/*",
    "application/csv",
    "application/vnd.ms-excel",
    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    "application/octet-stream",
    "application/zip",
)

sealed interface BinanceImportState {
    data object Help : BinanceImportState
    data object Working : BinanceImportState
    data class Preview(val plan: ImportPlan) : BinanceImportState
    data class Failed(val message: String) : BinanceImportState
}

/** Reads the chosen file and prices it; the file stays on the phone. */
suspend fun planBinanceImport(context: Context, uri: Uri, store: PortfolioStore, service: BinanceImportService): BinanceImportState =
    try {
        val bytes = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)!!.use { it.readAtMost(SHEET_MAX_BYTES + 1) }
        }
        if (bytes.size > SHEET_MAX_BYTES) {
            BinanceImportState.Failed("El archivo es demasiado grande (máximo 20 MB)")
        } else {
            store.load()
            val plan = withContext(Dispatchers.Default) { service.plan(bytes, store.data.value.operations) }
            if (plan.operations.isEmpty()) {
                BinanceImportState.Failed(nothingToImport(plan))
            } else {
                BinanceImportState.Preview(plan)
            }
        }
    } catch (e: BinanceFormatException) {
        BinanceImportState.Failed(e.message ?: "Formato no reconocido")
    } catch (e: SheetException) {
        BinanceImportState.Failed(e.message ?: "No se pudo leer el archivo")
    } catch (e: Exception) {
        BinanceImportState.Failed("No se pudo leer el archivo. Comprueba tu conexión: hace falta para poner precio a cada operación.")
    }

private fun nothingToImport(plan: ImportPlan): String = buildString {
    append("No hay operaciones nuevas que importar")
    if (plan.unknownSymbols.isNotEmpty()) append(". Monedas no reconocidas: ${plan.unknownSymbols.joinToString()}")
    if (plan.ignored.isNotEmpty()) append(". Se ignoran: ${plan.ignored.entries.joinToString { "${it.key.lowercase()} (${it.value})" }}")
}

@Composable
fun BinanceImportDialog(
    state: BinanceImportState,
    onPickFile: () -> Unit,
    onConfirm: (ImportPlan) -> Unit,
    onDismiss: () -> Unit,
) {
    when (state) {
        BinanceImportState.Help -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MenuColor,
            title = { Text("Importar desde Binance") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Descarga tu historial en Binance (CSV o Excel) y elígelo aquí:")
                    Text("• Compras y ventas en spot: Órdenes → Spot → Historial de operaciones → Exportar.", style = MaterialTheme.typography.bodySmall)
                    Text(
                        "• Todo (Convert, compras con tarjeta, recompensas de Earn): Perfil → Historial de transacciones → Exportar. " +
                            "En la web: Cartera → Historial de transacciones.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "Binance limita cada exportación a unos meses: puedes importar varios archivos, no se duplica nada.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Text(
                        "El archivo no sale de tu móvil: solo se consultan precios públicos de cada día.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = { TextButton(onClick = onPickFile) { Text("Elegir archivo") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
        )
        BinanceImportState.Working -> AlertDialog(
            onDismissRequest = {},
            containerColor = MenuColor,
            title = { Text("Leyendo el archivo") },
            text = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Text("Reconociendo monedas y buscando el precio de cada día…")
                }
            },
            confirmButton = {},
        )
        is BinanceImportState.Failed -> AlertDialog(
            onDismissRequest = onDismiss,
            containerColor = MenuColor,
            title = { Text("No se ha podido importar") },
            text = { Text(state.message) },
            confirmButton = { TextButton(onClick = onPickFile) { Text("Elegir otro archivo") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } },
        )
        is BinanceImportState.Preview -> {
            val p = state.plan
            AlertDialog(
                onDismissRequest = onDismiss,
                containerColor = MenuColor,
                title = { Text("¿Importar ${p.operations.size} operaciones?") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(p.format.label, style = MaterialTheme.typography.labelMedium)
                        Text("${p.buys} compras · ${p.sells} ventas · ${p.coins} monedas")
                        if (p.rewards > 0) Text("${p.rewards} recompensas (sin coste)")
                        if (p.openingBalances > 0) {
                            Text(
                                "${p.openingBalances} «saldo previo»: monedas que vendiste y que llegaron a Binance desde fuera; " +
                                    "se añaden al precio de esa venta.",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        if (p.unknownSymbols.isNotEmpty()) {
                            Text("No reconocidas (no se importan): ${p.unknownSymbols.joinToString()}", color = NegativeColor, style = MaterialTheme.typography.bodySmall)
                        }
                        if (p.unpriced > 0) Text("${p.unpriced} sin precio de ese día (no se importan)", color = NegativeColor, style = MaterialTheme.typography.bodySmall)
                        p.ignored.forEach { (reason, n) -> Text("Ignorado: $reason ($n)", style = MaterialTheme.typography.bodySmall) }
                        if (p.unrecognized > 0) Text("${p.unrecognized} filas no reconocidas", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Precios en dólares de cada día; las comisiones en BNB se suman al coste. Revisa el resultado en Historial.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                confirmButton = { TextButton(onClick = { onConfirm(p) }) { Text("Importar") } },
                dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
            )
        }
    }
}
