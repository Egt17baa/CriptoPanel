package app.criptopanel.defi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.ui.AutoRefresh
import app.criptopanel.core.ui.LineChart
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.changeColor
import coil3.compose.AsyncImage

private val tabTitles = listOf("Redes", "Protocolos")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DefiScreen(vm: DefiViewModel, onChain: (String) -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    AutoRefresh { vm.refresh() }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("DeFi") }) },
    ) { padding ->
        StateContent(state, onRefresh = { vm.refresh(force = true) }, modifier = Modifier.padding(padding)) { overview ->
            LazyColumn {
                item {
                    Column(Modifier.padding(12.dp)) {
                        Text("TVL DeFi total", style = MaterialTheme.typography.labelMedium)
                        Text(formatCompactUsd(overview.totalTvl), style = MaterialTheme.typography.headlineSmall)
                        LineChart(
                            points = overview.history.takeLast(365).map { it.tvl },
                            modifier = Modifier.fillMaxWidth().height(160.dp).padding(top = 8.dp),
                            color = MaterialTheme.colorScheme.primary,
                            fill = true,
                        )
                    }
                }
                item {
                    TabRow(selectedTabIndex = tab) {
                        tabTitles.forEachIndexed { i, title ->
                            Tab(selected = tab == i, onClick = { tab = i }, text = { Text(title) })
                        }
                    }
                }
                if (tab == 0) {
                    itemsIndexed(overview.chains, key = { _, c -> "chain-${c.name}" }) { i, chain ->
                        ChainRow(i + 1, chain, onClick = { onChain(chain.name) })
                        HorizontalDivider()
                    }
                } else {
                    itemsIndexed(overview.protocols, key = { _, p -> "protocol-${p.slug}" }) { i, protocol ->
                        ProtocolRow(i + 1, protocol, protocol.tvl)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun ChainRow(position: Int, chain: ChainTvl, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("$position", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
        Text(chain.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCompactUsd(chain.tvl))
            Text("%.2f %%".format(chain.share * 100), style = MaterialTheme.typography.bodySmall)
        }
    }
}

/** One protocol with the TVL to show ([tvl] is the total or the TVL on one chain). */
@Composable
fun ProtocolRow(position: Int, protocol: Protocol, tvl: Double) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("$position", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
        AsyncImage(model = protocol.logoUrl, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(protocol.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(protocol.category ?: "—", style = MaterialTheme.typography.bodySmall)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCompactUsd(tvl))
            Text(formatPercent(protocol.change1d), style = MaterialTheme.typography.bodySmall, color = changeColor(protocol.change1d))
        }
    }
}
