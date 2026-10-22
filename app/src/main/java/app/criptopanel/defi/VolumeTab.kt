package app.criptopanel.defi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.changeColor
import app.criptopanel.core.ui.openSafely
import coil3.compose.AsyncImage

/** DEX volume, or fees and revenue ([kinds] with more than one entry shows a switch). */
@Composable
fun VolumeTab(extras: DefiExtrasRepository, kinds: List<VolumeKind>) {
    var kind by rememberSaveable { mutableStateOf(kinds.first()) }
    var chain by rememberSaveable { mutableStateOf<String?>(null) }
    // The all-chains overview also supplies the chain chips, so it is always loaded.
    val all = viewModel(key = "volume-${kind.name}-all") { LoaderViewModel { extras.volume(kind, null, it) } }
    val selectedChain = chain
    val vm = if (selectedChain == null) {
        all
    } else {
        viewModel(key = "volume-${kind.name}-$selectedChain") { LoaderViewModel { extras.volume(kind, selectedChain, it) } }
    }
    val allState by all.state.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(all) { all.refresh() }
    LaunchedEffect(vm) { vm.refresh() }
    val uriHandler = LocalUriHandler.current

    Column {
        if (kinds.size > 1) {
            ChipRow {
                kinds.forEach { k -> FilterChip(selected = kind == k, onClick = { kind = k }, label = { Text(k.label) }) }
            }
        }
        val chains = allState.data?.chains.orEmpty()
        if (chains.isNotEmpty()) {
            ChipRow {
                FilterChip(selected = chain == null, onClick = { chain = null }, label = { Text("Todas") })
                chains.forEach { c -> FilterChip(selected = chain == c, onClick = { chain = c }, label = { Text(c) }) }
            }
        }
        StateContent(state, onRefresh = { vm.refresh(force = true) }) { overview ->
            LazyColumn {
                item { VolumeSummary(kind, selectedChain, overview) }
                if (overview.protocols.isEmpty()) item { EmptyMessage("Sin datos para esta red") }
                itemsIndexed(overview.protocols, key = { i, p -> p.slug ?: "row-$i" }) { i, protocol ->
                    VolumeRow(i + 1, protocol, onClick = protocol.slug?.let { slug -> { uriHandler.openSafely(protocolUrl(slug)) } })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun ChipRow(content: @Composable () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
    ) { content() }
}

@Composable
private fun VolumeSummary(kind: VolumeKind, chain: String?, overview: VolumeOverview) {
    Column(Modifier.padding(12.dp)) {
        Text("${kind.label} en 24 h · ${chain ?: "todas las redes"}", style = MaterialTheme.typography.labelMedium)
        Text(formatCompactUsd(overview.total24h), style = MaterialTheme.typography.headlineSmall)
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            listOf("1 d" to overview.change1d, "7 d" to overview.change7d, "30 d" to overview.change1m).forEach { (label, change) ->
                Text(
                    "$label ${formatPercent(change)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = changeColor(change),
                )
            }
        }
    }
}

@Composable
private fun VolumeRow(position: Int, protocol: VolumeProtocol, onClick: (() -> Unit)?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("$position", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
        AsyncImage(model = protocol.logoUrl, contentDescription = null, modifier = Modifier.size(24.dp))
        Column(Modifier.weight(1f)) {
            Text(protocol.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                protocol.chains.take(3).joinToString(", ").ifEmpty { "—" } + if (protocol.chains.size > 3) "…" else "",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCompactUsd(protocol.total24h))
            Text(formatPercent(protocol.change1d), style = MaterialTheme.typography.bodySmall, color = changeColor(protocol.change1d))
        }
    }
}
