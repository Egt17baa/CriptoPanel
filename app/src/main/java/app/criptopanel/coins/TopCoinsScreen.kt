package app.criptopanel.coins

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.AutoRefresh
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.LineChart
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.PositiveColor
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.changeColor
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TopCoinsScreen(vm: TopCoinsViewModel, onCoin: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    AutoRefresh { vm.refresh() }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Top 100") }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Filtrar por nombre o símbolo") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            ) {
                SortKey.entries.forEach { key ->
                    FilterChip(selected = sort == key, onClick = { vm.setSort(key) }, label = { Text(key.label) })
                }
            }
            StateContent(state, onRefresh = { vm.refresh(force = true) }) { coins ->
                if (coins.isEmpty()) EmptyMessage("Sin resultados")
                LazyColumn {
                    items(coins, key = { it.id }) { coin ->
                        CoinRow(coin, onClick = { onCoin(coin.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CoinRow(coin: CoinMarket, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(coin.rank?.let { "#$it" } ?: "—", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(36.dp))
        AsyncImage(model = coin.imageUrl, contentDescription = null, modifier = Modifier.size(28.dp))
        Column(Modifier.weight(1f)) {
            Text(coin.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text("${coin.symbol} · ${formatCompactUsd(coin.marketCap)}", style = MaterialTheme.typography.bodySmall)
        }
        val spark = coin.sparkline7d
        LineChart(
            points = spark,
            modifier = Modifier.size(width = 64.dp, height = 28.dp),
            color = if (spark.isNotEmpty() && spark.last() >= spark.first()) PositiveColor else NegativeColor,
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(formatUsd(coin.price), style = MaterialTheme.typography.bodyMedium)
            Text(formatPercent(coin.change24h), style = MaterialTheme.typography.bodySmall, color = changeColor(coin.change24h))
        }
    }
}
