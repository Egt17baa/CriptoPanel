package app.criptopanel.defi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.LineChart
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.changeColor

@Composable
fun StablecoinsTab(vm: LoaderViewModel<StablecoinOverview>, onCoin: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.refresh() }
    StateContent(state, onRefresh = { vm.refresh(force = true) }) { overview ->
        LazyColumn {
            item {
                Column(Modifier.padding(12.dp)) {
                    Text("Capitalización de las stablecoins", style = MaterialTheme.typography.labelMedium)
                    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(formatCompactUsd(overview.totalCap), style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "${formatPercent(overview.change7d)} en 7 d",
                            style = MaterialTheme.typography.bodySmall,
                            color = changeColor(overview.change7d),
                            modifier = Modifier.padding(bottom = 4.dp),
                        )
                    }
                    LineChart(
                        points = overview.history.map { it.tvl },
                        modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fill = true,
                    )
                }
            }
            itemsIndexed(overview.coins, key = { _, c -> c.id }) { i, coin ->
                StablecoinRow(i + 1, coin, onClick = coin.geckoId?.let { id -> { onCoin(id) } })
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun StablecoinRow(position: Int, coin: Stablecoin, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("$position", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text("${coin.name} (${coin.symbol})", maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${coin.mechanism} · ${coin.chainCount} ${if (coin.chainCount == 1) "red" else "redes"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCompactUsd(coin.marketCap))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    formatUsd(coin.price),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (coin.offPeg) NegativeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(formatPercent(coin.change24h), style = MaterialTheme.typography.bodySmall, color = changeColor(coin.change24h))
            }
        }
    }
}
