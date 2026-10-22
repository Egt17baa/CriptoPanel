package app.criptopanel.market

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.criptopanel.coins.CoinMarket
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.LineChart
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.PositiveColor
import app.criptopanel.core.ui.UiState
import app.criptopanel.core.ui.changeColor
import app.criptopanel.core.ui.message
import coil3.compose.AsyncImage

private const val CARD_ROWS = 3

/** A card that loads on its own: small spinner, then its data, or a one-line error that leaves the rest of the screen alone. */
@Composable
private fun <T> InsightCard(
    title: String,
    state: UiState<T>,
    onMore: (() -> Unit)? = null,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(40.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (onMore != null) TextButton(onClick = onMore) { Text("Ver todo ›") }
            }
            Column(Modifier.padding(end = 8.dp)) {
                val data = state.data
                val error = state.error
                when {
                    data != null -> content(data)
                    error != null && !state.loading -> Text(
                        error.message(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> Box(Modifier.fillMaxWidth().height(56.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                    }
                }
            }
        }
    }
}

private fun fearGreedColor(value: Int): Color = when {
    value <= 44 -> NegativeColor
    value >= 56 -> PositiveColor
    else -> Color.Unspecified
}

@Composable
fun FearGreedCard(state: UiState<FearGreed>) {
    InsightCard("Miedo y codicia", state) { fg ->
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column {
                Text("${fg.value}", style = MaterialTheme.typography.displaySmall, color = fearGreedColor(fg.value))
                Text(fg.label, style = MaterialTheme.typography.titleSmall)
                fg.yesterday?.let {
                    Text("Ayer: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Column(Modifier.weight(1f)) {
                LineChart(
                    points = fg.history.map { it.value.toDouble() },
                    modifier = Modifier.fillMaxWidth().height(64.dp),
                    color = MaterialTheme.colorScheme.primary,
                    fill = true,
                )
                Text(
                    "Últimos ${fg.history.size} días · 0 miedo extremo, 100 codicia extrema",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun MoversCard(state: UiState<Movers>, onCoin: (String) -> Unit, onMore: () -> Unit) {
    InsightCard("Suben y bajan (24 h)", state, onMore) { m ->
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            MoverColumn("Suben", m.gainers.take(CARD_ROWS), onCoin, Modifier.weight(1f))
            MoverColumn("Bajan", m.losers.take(CARD_ROWS), onCoin, Modifier.weight(1f))
        }
    }
}

@Composable
private fun MoverColumn(title: String, coins: List<CoinMarket>, onCoin: (String) -> Unit, modifier: Modifier) {
    Column(modifier) {
        Text(title, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (coins.isEmpty()) Text("—")
        coins.forEach { coin ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().clickable { onCoin(coin.id) }.padding(vertical = 6.dp),
            ) {
                AsyncImage(model = coin.imageUrl, contentDescription = null, modifier = Modifier.size(20.dp))
                Text(coin.symbol, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(formatPercent(coin.change24h), style = MaterialTheme.typography.bodySmall, color = changeColor(coin.change24h))
            }
        }
    }
}

@Composable
fun TrendingCard(state: UiState<List<TrendingCoin>>, onCoin: (String) -> Unit, onMore: () -> Unit) {
    InsightCard("Tendencias", state, onMore) { coins ->
        if (coins.isEmpty()) Text("—")
        coins.take(CARD_ROWS).forEach { coin ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().clickable { onCoin(coin.id) }.padding(vertical = 6.dp),
            ) {
                AsyncImage(model = coin.imageUrl, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(coin.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(coin.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatPercent(coin.change24h), style = MaterialTheme.typography.bodySmall, color = changeColor(coin.change24h))
            }
        }
    }
}

@Composable
fun LinkCard(title: String, subtitle: String, onClick: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("›", style = MaterialTheme.typography.titleLarge)
        }
    }
}
