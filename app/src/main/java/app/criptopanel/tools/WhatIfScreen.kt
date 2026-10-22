package app.criptopanel.tools

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.format.formatQuantity
import app.criptopanel.core.format.formatSignedUsd
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.DisclaimerLine
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.MultiLineChart
import app.criptopanel.core.ui.SeriesColors
import app.criptopanel.core.ui.changeColor
import app.criptopanel.core.ui.message
import app.criptopanel.search.CoinSearchPicker
import app.criptopanel.search.SearchViewModel
import coil3.compose.AsyncImage

@Composable
fun WhatIfScreen(vm: WhatIfViewModel, search: SearchViewModel, onBack: () -> Unit) {
    val coin by vm.coin.collectAsStateWithLifecycle()
    val amount by vm.amount.collectAsStateWithLifecycle()
    val period by vm.period.collectAsStateWithLifecycle()
    val monthly by vm.monthly.collectAsStateWithLifecycle()
    val data by vm.data.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }

    BackScaffold("¿Y si hubiera invertido?", onBack) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            GlassCard(Modifier.fillMaxWidth()) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AsyncImage(model = coin.imageUrl, contentDescription = null, modifier = Modifier.size(28.dp))
                    Text("${coin.name} (${coin.symbol})", modifier = Modifier.weight(1f))
                    TextButton(onClick = { picking = !picking }) { Text("Cambiar") }
                }
            }
            if (picking) {
                CoinSearchPicker(search, "Moneda") {
                    vm.setCoin(it)
                    picking = false
                }
            }
            OutlinedTextField(
                value = amount,
                onValueChange = vm::setAmount,
                label = { Text(if (monthly) "Dólares cada mes" else "Dólares invertidos") },
                suffix = { Text("$") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Text("Hace cuánto", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                WhatIfPeriod.entries.forEach { p -> FilterChip(selected = p == period, onClick = { vm.setPeriod(p) }, label = { Text(p.label) }) }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !monthly, onClick = { vm.setMonthly(false) }, label = { Text("De una vez") })
                FilterChip(selected = monthly, onClick = { vm.setMonthly(true) }, label = { Text("Cada mes (DCA)") })
            }

            val r = vm.result()
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    when {
                        r != null -> {
                            Text("Hoy valdría", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(formatUsd(r.value), style = MaterialTheme.typography.headlineMedium)
                            Text(
                                "${formatSignedUsd(r.profit)} (${formatPercent(r.profitPct)})",
                                color = changeColor(r.profit),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Invertido: ${formatUsd(r.invested)} en ${r.purchases} ${if (r.purchases == 1) "compra" else "compras"} · " +
                                    "${formatQuantity(r.units)} ${coin.symbol} a ${formatUsd(r.averagePrice)} de media",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            MultiLineChart(
                                series = listOf(r.valueSeries.map { it.price }, r.investedSeries.map { it.price }),
                                modifier = Modifier.fillMaxWidth().height(140.dp).padding(top = 8.dp),
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                Text("━ Valor", color = SeriesColors[0], style = MaterialTheme.typography.labelSmall)
                                Text("━ Invertido", color = SeriesColors[1], style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        data.loading -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        data.error != null -> {
                            Text(data.error!!.message(), color = MaterialTheme.colorScheme.onSurfaceVariant)
                            TextButton(onClick = { vm.load(force = true) }) { Text("Reintentar") }
                        }
                        else -> Text("Escribe un importe para ver el resultado", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text(
                "Precios diarios de CoinGecko del último año. Sin comisiones ni impuestos. Rentabilidades pasadas no garantizan resultados futuros.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DisclaimerLine()
        }
    }
}
