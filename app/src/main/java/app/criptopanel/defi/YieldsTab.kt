package app.criptopanel.defi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.core.ui.PositiveColor
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.openSafely

private fun apy(value: Double?): String = if (value == null) "—" else "%.2f %%".format(value)

@Composable
fun YieldsTab(vm: LoaderViewModel<List<Pool>>) {
    val state by vm.state.collectAsStateWithLifecycle()
    var stableOnly by rememberSaveable { mutableStateOf(false) }
    var minTvl by rememberSaveable { mutableStateOf(MinTvl.M1) }
    var sort by rememberSaveable { mutableStateOf(PoolSort.APY) }
    LaunchedEffect(vm) { vm.refresh() }
    val uriHandler = LocalUriHandler.current

    Column {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
        ) {
            FilterChip(selected = stableOnly, onClick = { stableOnly = !stableOnly }, label = { Text("Solo stablecoins") })
            VerticalDivider(Modifier.height(24.dp)) // A fixed height: the default fillMaxHeight would stretch the chip row over the whole tab.
            MinTvl.entries.forEach { m -> FilterChip(selected = minTvl == m, onClick = { minTvl = m }, label = { Text(m.label) }) }
            VerticalDivider(Modifier.height(24.dp))
            PoolSort.entries.forEach { s -> FilterChip(selected = sort == s, onClick = { sort = s }, label = { Text("Por ${s.label}") }) }
        }
        Text(
            "APY variable; no es una recomendación de inversión.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
        )
        StateContent(state, onRefresh = { vm.refresh(force = true) }) { pools ->
            val shown = remember(pools, stableOnly, minTvl, sort) { pools.filtered(stableOnly, minTvl, sort) }
            if (shown.isEmpty()) EmptyMessage("Ningún pool con estos filtros")
            LazyColumn {
                itemsIndexed(shown, key = { _, p -> p.id }) { i, pool ->
                    PoolRow(i + 1, pool, onClick = { uriHandler.openSafely(poolUrl(pool.id)) })
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun PoolRow(position: Int, pool: Pool, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("$position", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(pool.symbol, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(pool.project, pool.chain, "Riesgo de pérdida impermanente".takeIf { pool.ilRisk }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(apy(pool.apy), color = PositiveColor, style = MaterialTheme.typography.titleSmall)
            if (pool.apyReward != null && pool.apyReward > 0) {
                Text(
                    "${apy(pool.apyBase ?: 0.0)} + ${apy(pool.apyReward)} recompensas",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("TVL ${formatCompactUsd(pool.tvl)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
