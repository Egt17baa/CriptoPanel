package app.criptopanel.market

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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.changeColor
import coil3.compose.AsyncImage

@Composable
fun CategoriesScreen(vm: LoaderViewModel<List<Category>>, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var sort by rememberSaveable { mutableStateOf(CategorySort.CAP) }
    LaunchedEffect(vm) { vm.refresh() }
    BackScaffold("Sectores", onBack) { padding ->
        Column(Modifier.padding(padding)) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            ) {
                CategorySort.entries.forEach { key ->
                    FilterChip(selected = sort == key, onClick = { sort = key }, label = { Text(key.label) })
                }
            }
            StateContent(state, onRefresh = { vm.refresh(force = true) }) { categories ->
                val sorted = remember(categories, sort) { categories.sortedBy(sort) }
                if (sorted.isEmpty()) EmptyMessage("Sin sectores")
                LazyColumn {
                    itemsIndexed(sorted, key = { _, c -> c.id }) { i, category ->
                        CategoryRow(i + 1, category)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(position: Int, category: Category) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text("$position", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
        Column(Modifier.weight(1f)) {
            Text(category.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                category.topCoinImages.forEach { url ->
                    AsyncImage(model = url, contentDescription = null, modifier = Modifier.size(16.dp))
                }
                Text(
                    "  Vol. ${formatCompactUsd(category.volume24h)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatCompactUsd(category.marketCap))
            Text(formatPercent(category.change24h), style = MaterialTheme.typography.bodySmall, color = changeColor(category.change24h))
        }
    }
}
