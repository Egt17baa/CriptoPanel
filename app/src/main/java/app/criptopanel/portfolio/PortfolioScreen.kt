package app.criptopanel.portfolio

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.alerts.AlertsSection
import app.criptopanel.alerts.AlertsViewModel
import app.criptopanel.coins.ChartRange
import app.criptopanel.coins.CoinRow
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.format.formatQuantity
import app.criptopanel.core.format.formatSignedUsd
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.AutoRefresh
import app.criptopanel.core.ui.DisclaimerLine
import app.criptopanel.core.ui.ErrorBanner
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.LineChart
import app.criptopanel.core.ui.MenuColor
import app.criptopanel.core.ui.changeColor
import app.criptopanel.core.ui.message
import app.criptopanel.core.ui.visibleError
import coil3.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import app.criptopanel.portfolio.binance.planBinanceImport
import app.criptopanel.portfolio.binance.BinanceImportState
import app.criptopanel.portfolio.binance.BinanceImportService
import app.criptopanel.portfolio.binance.BinanceImportDialog
import app.criptopanel.portfolio.binance.BINANCE_FILE_TYPES

private val portfolioRanges = listOf(ChartRange.W1, ChartRange.M1, ChartRange.Y1)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    vm: PortfolioViewModel,
    store: PortfolioStore,
    binanceImport: () -> BinanceImportService,
    onCoin: (String) -> Unit,
    onAddOperation: () -> Unit,
    onHistory: (coinId: String?) -> Unit,
    alerts: AlertsViewModel,
    onAddAlert: () -> Unit,
    onEditAlert: (String) -> Unit,
    initialTab: Int = 0,
) {
    val data by vm.data.collectAsStateWithLifecycle()
    var tab by rememberSaveable(initialTab) { mutableIntStateOf(initialTab) }
    var menu by remember { mutableStateOf(false) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    AutoRefresh { vm.refreshPrices() }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) scope.launch { snackbar.showSnackbar(exportTo(context, uri, store)) }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch { snackbar.showSnackbar(importFrom(context, uri, store)) }
    }
    var binance by remember { mutableStateOf<BinanceImportState?>(null) }
    val binancePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) {
            binance = null
            return@rememberLauncherForActivityResult
        }
        binance = BinanceImportState.Working
        scope.launch { binance = planBinanceImport(context, uri, store, binanceImport()) }
    }
    binance?.let { state ->
        BinanceImportDialog(
            state = state,
            onPickFile = { binancePicker.launch(BINANCE_FILE_TYPES) },
            onConfirm = { plan ->
                binance = null
                scope.launch {
                    val added = store.addImported(plan.operations)
                    snackbar.showSnackbar(
                        when (added) {
                            null -> "No se ha importado: alguna venta dejaría una moneda en negativo"
                            0 -> "Nada nuevo: esas operaciones ya estaban en tu cartera"
                            else -> "$added operaciones importadas de Binance"
                        },
                    )
                }
            },
            onDismiss = { binance = null },
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Cartera") },
                actions = {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "Más opciones") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }, containerColor = MenuColor) {
                        DropdownMenuItem(text = { Text("Exportar CSV") }, onClick = {
                            menu = false
                            exporter.launch("criptopanel-cartera-${LocalDate.now()}.csv")
                        })
                        DropdownMenuItem(text = { Text("Importar CSV") }, onClick = {
                            menu = false
                            importer.launch(arrayOf("text/*", "application/csv", "application/vnd.ms-excel", "application/octet-stream"))
                        })
                        DropdownMenuItem(text = { Text("Importar desde Binance") }, onClick = {
                            menu = false
                            binance = BinanceImportState.Help
                        })
                    }
                },
            )
        },
        floatingActionButton = {
            when (tab) {
                0 -> ExtendedFloatingActionButton(
                    onClick = onAddOperation,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Operación") },
                )
                2 -> ExtendedFloatingActionButton(
                    onClick = onAddAlert,
                    icon = { Icon(Icons.Default.Add, contentDescription = null) },
                    text = { Text("Alerta") },
                )
            }
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Cartera") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Favoritos") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("Alertas") })
            }
            when (tab) {
                0 -> if (data.operations.isEmpty()) EmptyPortfolio(onAddOperation) else PortfolioContent(vm, onHistory)
                1 -> Favorites(vm, onCoin)
                else -> AlertsSection(alerts, snackbar, onEditAlert)
            }
        }
    }
}

@Composable
private fun EmptyPortfolio(onAdd: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Tu cartera está vacía", style = MaterialTheme.typography.titleMedium)
        Text(
            "Apunta tus compras y ventas y verás cuánto vale tu cartera y cuánto ganas o pierdes. " +
                "Todo se guarda solo en este móvil.",
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp),
        )
        Button(onClick = onAdd) { Text("Añadir la primera operación") }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PortfolioContent(vm: PortfolioViewModel, onHistory: (String?) -> Unit) {
    val summary by vm.summary.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    val truncated by vm.seriesTruncated.collectAsStateWithLifecycle()
    PullToRefreshBox(isRefreshing = prices.loading && prices.data != null, onRefresh = { vm.refreshAll() }) {
        LazyColumn(
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            prices.visibleError()?.let { e -> item { ErrorBanner(e) } }
            item { SummaryCard(summary, pricesLoaded = prices.data != null) }
            item {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Evolución del valor", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            portfolioRanges.forEach { r ->
                                FilterChip(selected = r == range, onClick = { vm.setRange(r) }, label = { Text(r.label) })
                            }
                        }
                        val points = series.data
                        val error = series.error
                        Box(Modifier.fillMaxWidth().height(140.dp), contentAlignment = Alignment.Center) {
                            when {
                                points != null && points.size >= 2 -> LineChart(
                                    points = points.map { it.price },
                                    modifier = Modifier.fillMaxSize(),
                                    color = MaterialTheme.colorScheme.primary,
                                    fill = true,
                                )
                                error != null && !series.loading -> Text(error.message(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                                series.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                                else -> Text("Sin datos para este periodo", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        if (truncated) {
                            Text(
                                "Incluye tus $SERIES_MAX_COINS mayores posiciones",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            val slices = allocation(summary)
            if (slices.isNotEmpty()) item { AllocationCard(slices) }
            item { Text("Posiciones", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 4.dp)) }
            items(summary.open, key = { it.position.coinId }) { p -> PositionRow(p, onClick = { onHistory(p.position.coinId) }) }
            item {
                GlassCard(Modifier.fillMaxWidth(), onClick = { onHistory(null) }) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Historial de operaciones", modifier = Modifier.weight(1f))
                        Text("›", style = MaterialTheme.typography.titleLarge)
                    }
                }
            }
            item { DisclaimerLine() }
        }
    }
}

@Composable
private fun SummaryCard(s: PortfolioSummary, pricesLoaded: Boolean) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Valor total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if (pricesLoaded) formatUsd(s.value) else "—", style = MaterialTheme.typography.headlineMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1f)) {
                    Text("Ganancia total", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatSignedUsd(s.profit), color = changeColor(s.profit), style = MaterialTheme.typography.titleSmall)
                    Text(formatPercent(s.profitPct), color = changeColor(s.profitPct), style = MaterialTheme.typography.bodySmall)
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        if (s.change24hApprox) "Hoy (24 h, aprox.)" else "Hoy (24 h)",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        (if (s.change24hApprox) "~" else "") + formatSignedUsd(s.change24h),
                        color = changeColor(s.change24h),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(formatPercent(s.change24hPct), color = changeColor(s.change24hPct), style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
private fun AllocationCard(slices: List<AllocationSlice>) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Reparto", style = MaterialTheme.typography.titleSmall)
            slices.forEach { s ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(s.label, modifier = Modifier.width(56.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Box(Modifier.weight(1f).height(10.dp).background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(5.dp))) {
                        Box(
                            Modifier.fillMaxWidth(s.fraction.toFloat().coerceIn(0.01f, 1f)).height(10.dp)
                                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(5.dp)),
                        )
                    }
                    Text("%.1f %%".format(s.fraction * 100), modifier = Modifier.width(56.dp), textAlign = TextAlign.End)
                }
            }
        }
    }
}

@Composable
private fun PositionRow(p: PricedPosition, onClick: () -> Unit) {
    val pos = p.position
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        ) {
            AsyncImage(model = pos.imageUrl, contentDescription = null, modifier = Modifier.size(28.dp))
            Column(Modifier.weight(1f)) {
                Text("${formatQuantity(pos.quantity)} ${pos.symbol}", maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "Precio medio ${formatUsd(pos.averagePrice)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(formatUsd(p.value))
                Text(
                    "${formatSignedUsd(p.profit)} (${formatPercent(p.profitPct)})",
                    style = MaterialTheme.typography.bodySmall,
                    color = changeColor(p.profit),
                )
            }
        }
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Favorites(vm: PortfolioViewModel, onCoin: (String) -> Unit) {
    val data by vm.data.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    if (data.favorites.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
            Text(
                "Marca monedas con la ☆ en su detalle y aparecerán aquí.",
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    PullToRefreshBox(isRefreshing = prices.loading && prices.data != null, onRefresh = { vm.refreshPrices(force = true) }) {
        LazyColumn(Modifier.fillMaxSize()) {
            prices.visibleError()?.let { e -> item { ErrorBanner(e) } }
            if (favorites.isEmpty() && prices.loading) {
                item { Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() } }
            }
            items(favorites, key = { it.id }) { coin ->
                CoinRow(coin, onClick = { onCoin(coin.id) })
                HorizontalDivider()
            }
        }
    }
}

private suspend fun exportTo(context: Context, uri: Uri, store: PortfolioStore): String {
    val csv = store.exportCsv()
    return try {
        withContext(Dispatchers.IO) {
            // The BOM makes Excel read accents as UTF-8.
            context.contentResolver.openOutputStream(uri)?.use { it.write(("\uFEFF" + csv).toByteArray()) }
                ?: error("no stream")
        }
        "Cartera exportada"
    } catch (e: Exception) {
        "No se pudo guardar el archivo"
    }
}

private suspend fun importFrom(context: Context, uri: Uri, store: PortfolioStore): String {
    val text = try {
        withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val bytes = input.readAtMost(CSV_MAX_BYTES + 1)
                if (bytes.size > CSV_MAX_BYTES) return@withContext null
                bytes.decodeToString()
            } ?: error("no stream")
        }
    } catch (e: Exception) {
        return "No se pudo leer el archivo"
    } ?: return "El archivo es demasiado grande para ser una cartera de CriptoPanel"
    return when (val r = store.importCsv(text)) {
        is CsvImport.Ok -> "${r.operations.size} operaciones importadas" + if (r.skipped > 0) ", ${r.skipped} ya estaban" else ""
        is CsvImport.Error -> if (r.line > 0) "Fila ${r.line}: ${r.reason}. No se ha importado nada." else "${r.reason}. No se ha importado nada."
    }
}
