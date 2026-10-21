package app.criptopanel.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.ui.AutoRefresh
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.changeColor

private data class Metric(
    val label: String,
    val value: String,
    val color: Color = Color.Unspecified,
    val index: MarketIndex? = null,
)

private fun GlobalMetrics.cards() = listOf(
    Metric(MarketIndex.TOTAL.title, formatCompactUsd(totalCap), index = MarketIndex.TOTAL),
    Metric(MarketIndex.TOTAL2.title, formatCompactUsd(total2), index = MarketIndex.TOTAL2),
    Metric(MarketIndex.TOTAL3.title, formatCompactUsd(total3), index = MarketIndex.TOTAL3),
    Metric(MarketIndex.BTC_D.title, "%.2f %%".format(btcDominance), index = MarketIndex.BTC_D),
    Metric(MarketIndex.ETH_D.title, "%.2f %%".format(ethDominance), index = MarketIndex.ETH_D),
    Metric("Volumen 24 h", formatCompactUsd(volume24h)),
    Metric("Cambio 24 h", formatPercent(capChange24hPct), changeColor(capChange24hPct)),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(vm: OverviewViewModel, onSearch: () -> Unit, onIndex: (MarketIndex) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    AutoRefresh { vm.refresh() }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Mercado") },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Default.Search, contentDescription = "Buscar") }
                },
            )
        },
    ) { padding ->
        StateContent(state, onRefresh = { vm.refresh(force = true) }, modifier = Modifier.padding(padding)) { metrics ->
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(metrics.cards()) { MetricCard(it, onIndex) }
            }
        }
    }
}

@Composable
private fun MetricCard(metric: Metric, onIndex: (MarketIndex) -> Unit) {
    val index = metric.index
    val content: @Composable () -> Unit = {
        Column(Modifier.padding(12.dp)) {
            Text(if (index != null) "${metric.label} ›" else metric.label, style = MaterialTheme.typography.labelMedium)
            Text(metric.value, style = MaterialTheme.typography.titleMedium, color = metric.color)
        }
    }
    if (index != null) Card(onClick = { onIndex(index) }) { content() } else Card { content() }
}
