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
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompact
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.openSafely
import coil3.compose.AsyncImage

@Composable
fun ExchangesScreen(vm: LoaderViewModel<List<Exchange>>, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val uriHandler = LocalUriHandler.current
    LaunchedEffect(vm) { vm.refresh() }
    BackScaffold("Exchanges", onBack) { padding ->
        StateContent(state, onRefresh = { vm.refresh(force = true) }, modifier = Modifier.padding(padding)) { exchanges ->
            if (exchanges.isEmpty()) EmptyMessage("Sin exchanges")
            LazyColumn {
                itemsIndexed(exchanges, key = { _, e -> e.id }) { i, exchange ->
                    val url = exchange.url
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(enabled = url != null) { url?.let(uriHandler::openSafely) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.width(28.dp))
                        AsyncImage(model = exchange.imageUrl, contentDescription = null, modifier = Modifier.size(28.dp))
                        Column(Modifier.weight(1f)) {
                            Text(exchange.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                listOfNotNull(exchange.country, exchange.year?.toString()).joinToString(" · ").ifEmpty { "—" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(exchange.volume24hUsd?.let { formatCompactUsd(it) } ?: "${formatCompact(exchange.volume24hBtc)} ₿")
                            Text(
                                "Confianza ${exchange.trustScore?.let { "$it/10" } ?: "—"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
