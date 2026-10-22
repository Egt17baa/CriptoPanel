package app.criptopanel.market

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.coins.CoinRow
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.changeColor

/** CoinGecko returns at most one page of 100 coins per sector. */
private const val PAGE = 100

@Composable
fun CategoryDetailScreen(title: String, vm: LoaderViewModel<CategoryDetail>, onCoin: (String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(vm) { vm.refresh() }
    BackScaffold(state.data?.category?.name ?: title, onBack) { padding ->
        StateContent(state, onRefresh = { vm.refresh(force = true) }, modifier = Modifier.padding(padding)) { detail ->
            LazyColumn {
                item { SectorSummary(detail) }
                item {
                    Text(
                        "Monedas del sector, por capitalización",
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                if (detail.coins.isEmpty()) item { EmptyMessage("CoinGecko no lista monedas en este sector") }
                items(detail.coins, key = { it.id }) { coin ->
                    CoinRow(coin, onClick = { onCoin(coin.id) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun SectorSummary(detail: CategoryDetail) {
    val c = detail.category
    val leader = detail.coins.firstOrNull()
    val leaderShare = leader?.marketCap?.let { it / c.marketCap * 100 }
    GlassCard(Modifier.fillMaxWidth().padding(12.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Capitalización", formatCompactUsd(c.marketCap), Modifier.weight(1f))
                Stat("Cambio 24 h", formatPercent(c.change24h), Modifier.weight(1f), changeColor(c.change24h))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Stat("Volumen 24 h", formatCompactUsd(c.volume24h), Modifier.weight(1f))
                Stat(
                    "Monedas",
                    if (detail.coins.size >= PAGE) "$PAGE+" else "${detail.coins.size}",
                    Modifier.weight(1f),
                )
            }
            if (leader != null && leaderShare != null) {
                Stat("Peso de la mayor (${leader.symbol})", "%.1f %%".format(leaderShare), Modifier.fillMaxWidth())
            }
            c.description?.let { Description(it) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier, color: Color = Color.Unspecified) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.animateContentSize().clickable { expanded = !expanded }) {
        Text("Qué es (descripción de CoinGecko, en inglés)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            text,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = if (expanded) Int.MAX_VALUE else 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(if (expanded) "Ver menos" else "Ver más", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}
