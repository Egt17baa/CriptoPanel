package app.criptopanel.defi

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.ui.LineChart
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.message

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChainDetailScreen(chain: String, vm: ChainDetailViewModel, onBack: () -> Unit) {
    val history by vm.history.collectAsStateWithLifecycle()
    val protocols by vm.protocols.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(chain) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            Column(Modifier.padding(12.dp)) {
                Text("TVL", style = MaterialTheme.typography.labelMedium)
                Text(formatCompactUsd(history.data?.lastOrNull()?.tvl), style = MaterialTheme.typography.headlineSmall)
                Box(Modifier.fillMaxWidth().height(160.dp), contentAlignment = Alignment.Center) {
                    val points = history.data
                    when {
                        points != null -> LineChart(
                            points = points.takeLast(365).map { it.tvl },
                            modifier = Modifier.fillMaxSize(),
                            color = MaterialTheme.colorScheme.primary,
                            fill = true,
                        )
                        history.loading -> CircularProgressIndicator()
                        history.error != null -> Text(history.error.message())
                    }
                }
            }
            Text("Protocolos en $chain", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(horizontal = 12.dp))
            StateContent(protocols, onRefresh = vm::refresh) { list ->
                LazyColumn {
                    itemsIndexed(list, key = { _, item -> item.first.slug }) { i, (protocol, tvl) ->
                        ProtocolRow(i + 1, protocol, tvl)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
