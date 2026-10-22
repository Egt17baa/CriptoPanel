package app.criptopanel.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.changeColor
import coil3.compose.AsyncImage

@Composable
fun TrendingScreen(vm: LoaderViewModel<List<TrendingCoin>>, onCoin: (String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.refresh() }
    BackScaffold("Tendencias", onBack) { padding ->
        StateContent(state, onRefresh = { vm.refresh(force = true) }, modifier = Modifier.padding(padding)) { coins ->
            if (coins.isEmpty()) EmptyMessage("Sin tendencias ahora mismo")
            LazyColumn {
                item {
                    Text(
                        "Las más buscadas en CoinGecko en las últimas horas.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                itemsIndexed(coins, key = { _, c -> c.id }) { i, coin ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onCoin(coin.id) }.padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(24.dp))
                        AsyncImage(model = coin.imageUrl, contentDescription = null, modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(coin.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                "${coin.symbol} · ${coin.rank?.let { "#$it" } ?: "sin posición"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(formatUsd(coin.price))
                            Text(formatPercent(coin.change24h), style = MaterialTheme.typography.bodySmall, color = changeColor(coin.change24h))
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
