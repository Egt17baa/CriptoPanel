package app.criptopanel.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.InputChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.coins.ChartRange
import app.criptopanel.coins.CoinMarket
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.DisclaimerLine
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.MultiLineChart
import app.criptopanel.core.ui.SeriesColors
import app.criptopanel.core.ui.changeColor
import app.criptopanel.core.ui.message
import app.criptopanel.search.CoinSearchPicker
import app.criptopanel.search.SearchViewModel
import coil3.compose.AsyncImage

private class Metric(val label: String, val value: (CoinMarket) -> Double?, val format: (Double?) -> String, val lowerIsBetter: Boolean = false)

private val metrics = listOf(
    Metric("Posición", { it.rank?.toDouble() }, { v -> v?.let { "#${it.toInt()}" } ?: "—" }, lowerIsBetter = true),
    Metric("Precio", { it.price }, { formatUsd(it) }),
    Metric("Capitalización", { it.marketCap }, { formatCompactUsd(it) }),
    Metric("Volumen 24 h", { it.volume24h }, { formatCompactUsd(it) }),
    Metric("Cambio 24 h", { it.change24h }, { formatPercent(it) }),
    Metric("Cambio 7 d", { it.change7d }, { formatPercent(it) }),
)

@Composable
fun CompareScreen(vm: CompareViewModel, search: SearchViewModel, onBack: () -> Unit) {
    val coins by vm.coins.collectAsStateWithLifecycle()
    val markets by vm.markets.collectAsStateWithLifecycle()
    val series by vm.series.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()

    BackScaffold("Comparador", onBack) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Hasta $MAX_COMPARED monedas. Toca una para quitarla.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                coins.forEachIndexed { i, c ->
                    InputChip(
                        selected = true,
                        onClick = { vm.remove(c.id) },
                        label = { Text("${c.symbol} ✕", color = SeriesColors[i % SeriesColors.size]) },
                        avatar = { AsyncImage(model = c.imageUrl, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    )
                }
            }
            if (coins.size < MAX_COMPARED) CoinSearchPicker(search, "Añadir moneda (${coins.size}/$MAX_COMPARED)") { vm.add(it) }
            markets.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.onSurfaceVariant) }

            if (coins.size >= 2) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Rendimiento (base 100)", style = MaterialTheme.typography.titleSmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf(ChartRange.W1, ChartRange.M1, ChartRange.Y1).forEach { r ->
                                FilterChip(selected = r == range, onClick = { vm.setRange(r) }, label = { Text(r.label) })
                            }
                        }
                        MultiLineChart(
                            series = coins.map { rebased(series[it.id].orEmpty()) },
                            modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 8.dp),
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                            coins.forEachIndexed { i, c ->
                                val change = rebased(series[c.id].orEmpty()).lastOrNull()?.let { it - 100 }
                                Text("━ ${c.symbol} ${formatPercent(change)}", color = SeriesColors[i % SeriesColors.size], style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                GlassCard(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp).horizontalScroll(rememberScrollState())) {
                        Row {
                            Box(Modifier.width(110.dp))
                            coins.forEachIndexed { i, c ->
                                Text(c.symbol, color = SeriesColors[i % SeriesColors.size], fontWeight = FontWeight.Bold, modifier = Modifier.width(96.dp))
                            }
                        }
                        metrics.forEach { m ->
                            val values = coins.map { c -> markets.data?.get(c.id)?.let(m.value) }
                            val best = bestIndex(values, m.lowerIsBetter)
                            Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(m.label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(110.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                                values.forEachIndexed { i, v ->
                                    val isChange = m.label.startsWith("Cambio")
                                    Text(
                                        m.format(v),
                                        fontWeight = if (i == best) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isChange) changeColor(v) else MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.width(96.dp),
                                        maxLines = 1,
                                    )
                                }
                            }
                        }
                        Text("En negrita, el mejor de cada fila.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                Text("Añade al menos dos monedas para compararlas.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            DisclaimerLine()
        }
    }
}
