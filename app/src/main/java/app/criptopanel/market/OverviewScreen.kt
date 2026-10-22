package app.criptopanel.market

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.ui.AutoRefresh
import app.criptopanel.core.ui.ErrorBanner
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.core.ui.changeColor
import app.criptopanel.core.ui.visibleError

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

/** Market extras refresh less often than the metrics, to spare CoinGecko's free rate limit. */
private const val INSIGHTS_REFRESH_MS = 5 * 60_000L

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OverviewScreen(
    vm: OverviewViewModel,
    fearGreed: LoaderViewModel<FearGreed>,
    movers: LoaderViewModel<Movers>,
    trending: LoaderViewModel<List<TrendingCoin>>,
    onSearch: () -> Unit,
    onIndex: (MarketIndex) -> Unit,
    onCoin: (String) -> Unit,
    onOpen: (route: String) -> Unit,
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val fearGreedState by fearGreed.state.collectAsStateWithLifecycle()
    val moversState by movers.state.collectAsStateWithLifecycle()
    val trendingState by trending.state.collectAsStateWithLifecycle()
    AutoRefresh { vm.refresh() }
    AutoRefresh(INSIGHTS_REFRESH_MS) {
        fearGreed.refresh()
        movers.refresh()
        trending.refresh()
    }
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
        // Not StateContent: each card below loads and fails on its own, so the grid is always there.
        PullToRefreshBox(
            isRefreshing = state.loading && state.data != null,
            onRefresh = {
                vm.refresh(force = true)
                fearGreed.refresh(force = true)
                movers.refresh(force = true)
                trending.refresh(force = true)
            },
            modifier = Modifier.padding(padding).fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                state.visibleError()?.let { error -> fullWidth { ErrorBanner(error) } }
                val metrics = state.data
                if (metrics != null) {
                    items(metrics.cards()) { MetricCard(it, onIndex) }
                } else if (state.loading) {
                    fullWidth {
                        Box(Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
                    }
                }
                fullWidth { FearGreedCard(fearGreedState) }
                fullWidth { MoversCard(moversState, onCoin, onMore = { onOpen("movers") }) }
                fullWidth { TrendingCard(trendingState, onCoin, onMore = { onOpen("trending") }) }
                fullWidth { LinkCard("Sectores", "IA, memes, capa 2, RWA…", onClick = { onOpen("categories") }) }
                fullWidth { LinkCard("Exchanges", "Los 50 con más volumen", onClick = { onOpen("exchanges") }) }
            }
        }
    }
}

private fun LazyGridScope.fullWidth(content: @Composable () -> Unit) =
    item(span = { GridItemSpan(maxLineSpan) }) { content() }

@Composable
private fun MetricCard(metric: Metric, onIndex: (MarketIndex) -> Unit) {
    val index = metric.index
    GlassCard(onClick = index?.let { { onIndex(it) } }) {
        Column(Modifier.padding(14.dp)) {
            Text(if (index != null) "${metric.label} ›" else metric.label, style = MaterialTheme.typography.labelMedium)
            Text(metric.value, style = MaterialTheme.typography.titleMedium, color = metric.color)
        }
    }
}
