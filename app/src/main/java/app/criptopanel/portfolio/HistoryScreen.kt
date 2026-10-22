package app.criptopanel.portfolio

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatMoney
import app.criptopanel.core.format.formatQuantity
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.PositiveColor
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    store: PortfolioStore,
    initialCoinId: String?,
    onEdit: (operationId: String) -> Unit,
    onAdd: (coinId: String?) -> Unit,
    onBack: () -> Unit,
    displayRates: suspend (LocalDate, LocalDate) -> ((LocalDate) -> Double)? = { _, _ -> null },
) {
    val data by store.data.collectAsStateWithLifecycle()
    // Prices shown in the display currency of each operation's own day.
    var rates by remember { mutableStateOf<((LocalDate) -> Double)?>(null) }
    LaunchedEffect(data.operations) {
        data.operations.minOfOrNull { it.localDate }?.let { rates = displayRates(it, LocalDate.now()) }
    }
    var coinId by rememberSaveable { mutableStateOf(initialCoinId) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    LaunchedEffect(store) { store.load() }

    val coins = remember(data) { data.operations.associate { it.coinId to it.symbol } }
    val shown = remember(data, coinId) {
        data.operations.filter { coinId == null || it.coinId == coinId }.chronological().asReversed()
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Historial" + (coinId?.let { " · ${coins[it] ?: it}" } ?: "")) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
                actions = {
                    IconButton(onClick = { onAdd(coinId) }) { Icon(Icons.Default.Add, contentDescription = "Añadir operación") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            if (coins.size > 1) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
                ) {
                    FilterChip(selected = coinId == null, onClick = { coinId = null }, label = { Text("Todas") })
                    coins.forEach { (id, symbol) ->
                        FilterChip(selected = coinId == id, onClick = { coinId = id }, label = { Text(symbol) })
                    }
                }
            }
            if (shown.isEmpty()) EmptyMessage("Sin operaciones")
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
                items(shown, key = { it.id }) { op ->
                    OperationRow(
                        op,
                        perUsd = rates?.invoke(op.localDate),
                        onClick = { onEdit(op.id) },
                        onDelete = {
                            scope.launch {
                                val problem = store.delete(op.id)
                                if (problem != null) {
                                    snackbar.showSnackbar(problem)
                                } else {
                                    val r = snackbar.showSnackbar("Operación borrada", actionLabel = "Deshacer", duration = SnackbarDuration.Short)
                                    if (r == SnackbarResult.ActionPerformed) store.upsert(op)
                                }
                            }
                        },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun OperationRow(op: Operation, perUsd: Double?, onClick: () -> Unit, onDelete: () -> Unit) {
    val money: (Double) -> String = { usd -> if (perUsd != null) formatMoney(usd * perUsd) else formatUsd(usd) }
    val buy = op.type == OperationType.BUY
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(if (buy) "Compra" else "Venta", color = if (buy) PositiveColor else NegativeColor, style = MaterialTheme.typography.labelLarge)
                Text(op.localDate.format(dateFormat), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("${formatQuantity(op.quantity)} ${op.symbol} a ${money(op.priceUsd)}", maxLines = 1, overflow = TextOverflow.Ellipsis)
            val extra = listOfNotNull(
                "Comisión ${money(op.feeUsd)}".takeIf { op.feeUsd > 0 },
                op.note.takeIf { it.isNotBlank() },
            ).joinToString(" · ")
            if (extra.isNotEmpty()) {
                Text(extra, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(money(op.quantity * op.priceUsd), style = MaterialTheme.typography.bodyMedium)
        IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = "Borrar operación") }
    }
}
