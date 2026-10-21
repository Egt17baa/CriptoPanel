package app.criptopanel.coins

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatCompact
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.LineChart
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.PositiveColor
import app.criptopanel.core.ui.StateContent
import app.criptopanel.core.ui.UiState
import app.criptopanel.core.ui.message
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoinDetailScreen(vm: CoinDetailViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val chart by vm.chart.collectAsStateWithLifecycle()
    val range by vm.range.collectAsStateWithLifecycle()
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(state.data?.name.orEmpty()) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
            )
        },
    ) { padding ->
        StateContent(state, onRefresh = vm::refresh, modifier = Modifier.padding(padding)) { coin ->
            LazyColumn(contentPadding = PaddingValues(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item { Header(coin) }
                item {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                    ) {
                        ChartRange.entries.forEach { r ->
                            FilterChip(selected = r == range, onClick = { vm.setRange(r) }, label = { Text(r.label) })
                        }
                    }
                }
                item { PriceChart(chart) }
                item { Stats(coin) }
                if (coin.description.isNotBlank()) item { Description(coin.description) }
            }
        }
    }
}

@Composable
private fun Header(coin: CoinDetail) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AsyncImage(model = coin.imageUrl, contentDescription = null, modifier = Modifier.size(40.dp))
        Column {
            Text("${coin.symbol} · ${coin.rank?.let { "#$it" } ?: "—"}", style = MaterialTheme.typography.labelMedium)
            Text(formatUsd(coin.price), style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun PriceChart(chart: UiState<List<Double>>) {
    Box(Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
        val points = chart.data
        when {
            points != null -> LineChart(
                points = points,
                modifier = Modifier.fillMaxSize(),
                color = if (points.isNotEmpty() && points.last() >= points.first()) PositiveColor else NegativeColor,
                fill = true,
            )
            chart.loading -> CircularProgressIndicator()
            chart.error != null -> Text(chart.error.message())
        }
    }
}

@Composable
private fun Stats(coin: CoinDetail) {
    val rows = listOf(
        "Capitalización" to formatCompactUsd(coin.marketCap),
        "Volumen 24 h" to formatCompactUsd(coin.volume24h),
        "Máximo 24 h" to formatUsd(coin.high24h),
        "Mínimo 24 h" to formatUsd(coin.low24h),
        "Máximo histórico" to formatUsd(coin.ath),
        "En circulación" to formatCompact(coin.circulatingSupply),
        "Oferta total" to formatCompact(coin.totalSupply),
        "Oferta máxima" to formatCompact(coin.maxSupply),
    )
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        rows.forEach { (label, value) ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(label, style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun Description(text: String) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.animateContentSize().clickable { expanded = !expanded }) {
        Text("Descripción", style = MaterialTheme.typography.titleSmall)
        Text(text, maxLines = if (expanded) Int.MAX_VALUE else 4, style = MaterialTheme.typography.bodySmall)
        Text(if (expanded) "Ver menos" else "Ver más", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
    }
}
