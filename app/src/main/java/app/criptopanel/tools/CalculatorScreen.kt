package app.criptopanel.tools

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilledTonalButton
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
import app.criptopanel.core.format.formatQuantity
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.DisclaimerLine
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.message
import app.criptopanel.search.CoinSearchPicker
import app.criptopanel.search.SearchViewModel
import coil3.compose.AsyncImage

private fun format(asset: Asset, value: Double?): String =
    if (asset == Asset.Usd) formatUsd(value) else "${formatQuantity(value)} ${asset.label}"

@Composable
fun CalculatorScreen(vm: CalculatorViewModel, search: SearchViewModel, onBack: () -> Unit) {
    val from by vm.from.collectAsStateWithLifecycle()
    val to by vm.to.collectAsStateWithLifecycle()
    val amount by vm.amount.collectAsStateWithLifecycle()
    val prices by vm.prices.collectAsStateWithLifecycle()
    // Which side the coin search is choosing for: "from", "to" or none.
    var picking by rememberSaveable { mutableStateOf<String?>(null) }

    BackScaffold("Calculadora", onBack) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            AssetCard("De", from, onChange = { picking = "from" }, onUsd = { vm.setFrom(Asset.Usd) })
            OutlinedTextField(
                value = amount,
                onValueChange = vm::setAmount,
                label = { Text("Cantidad") },
                suffix = { Text(from.label) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                FilledTonalButton(onClick = vm::swap) { Text("⇅ Intercambiar") }
            }
            AssetCard("A", to, onChange = { picking = "to" }, onUsd = { vm.setTo(Asset.Usd) })
            if (picking != null) {
                CoinSearchPicker(search, label = if (picking == "from") "Moneda de origen" else "Moneda de destino") { c ->
                    if (picking == "from") vm.setFrom(Asset.Coin(c)) else vm.setTo(Asset.Coin(c))
                    picking = null
                }
            }
            GlassCard(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Resultado", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val r = vm.result()
                    Text(if (r != null) format(to, r) else "—", style = MaterialTheme.typography.headlineSmall)
                    listOf(from, to).filterIsInstance<Asset.Coin>().distinct().forEach { a ->
                        Text("1 ${a.label} = ${formatUsd(vm.usdPrice(a))}", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    prices.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
                }
            }
            TextButton(onClick = { vm.refresh(force = true) }) { Text("Actualizar precios") }
            DisclaimerLine()
        }
    }
}

@Composable
private fun AssetCard(label: String, asset: Asset, onChange: () -> Unit, onUsd: () -> Unit) {
    GlassCard(Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (asset is Asset.Coin) AsyncImage(model = asset.coin.imageUrl, contentDescription = null, modifier = Modifier.size(24.dp))
            Text(if (asset is Asset.Coin) "${asset.coin.name} (${asset.label})" else "Dólar (USD)", modifier = Modifier.weight(1f))
            if (asset is Asset.Coin) TextButton(onClick = onUsd) { Text("USD") }
            TextButton(onClick = onChange) { Text("Moneda") }
        }
    }
}
