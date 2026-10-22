package app.criptopanel.alerts

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.Money
import app.criptopanel.core.format.formatMoney
import app.criptopanel.core.format.formatUsd
import app.criptopanel.portfolio.parseAmount
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.message
import app.criptopanel.portfolio.FormCoin
import app.criptopanel.search.SearchViewModel
import coil3.compose.AsyncImage

private val coinKinds = listOf(AlertKind.PRICE_ABOVE, AlertKind.PRICE_BELOW, AlertKind.CHANGE_WINDOW, AlertKind.CHANGE_24H)
private val portfolioKinds = listOf(AlertKind.PORTFOLIO_ABOVE, AlertKind.PORTFOLIO_BELOW, AlertKind.PORTFOLIO_CHANGE_TODAY)
private val directionLabels = listOf(Direction.UP to "Al alza", Direction.DOWN to "A la baja", Direction.ANY to "Cualquiera")

@Composable
fun AlertFormScreen(vm: AlertFormViewModel, search: SearchViewModel, onDone: () -> Unit) {
    val form by vm.form.collectAsStateWithLifecycle()
    val current by vm.current.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) onDone() }

    BackScaffold(if (vm.isEdit) "Editar alerta" else "Nueva alerta", onDone) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Qué vigilar", style = MaterialTheme.typography.labelMedium)
            val coin = form.coin
            when {
                form.portfolio && !picking -> GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("💼 Mi cartera", modifier = Modifier.weight(1f))
                        TextButton(onClick = { picking = true }) { Text("Cambiar") }
                    }
                }
                coin != null && !picking -> GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(model = coin.imageUrl, contentDescription = null, modifier = Modifier.size(28.dp))
                        Text("${coin.name} (${coin.symbol})", modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        TextButton(onClick = { picking = true }) { Text("Cambiar") }
                    }
                }
                else -> TargetPicker(
                    search,
                    onCoin = {
                        vm.selectCoin(it)
                        picking = false
                    },
                    onPortfolio = {
                        vm.selectPortfolio()
                        picking = false
                    },
                )
            }

            if (form.coin != null || form.portfolio) {
                current?.let {
                    Text(
                        (if (form.portfolio) "Valor actual de tu cartera: " else "Precio actual: ") + formatUsd(it),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text("Tipo", style = MaterialTheme.typography.labelMedium)
                Chips(if (form.portfolio) portfolioKinds else coinKinds, form.kind, { it.label }) { k -> vm.update { it.copy(kind = k) } }
                if (form.kind == AlertKind.CHANGE_WINDOW) {
                    Text("En cuánto tiempo", style = MaterialTheme.typography.labelMedium)
                    Chips(CHANGE_WINDOWS_MINUTES, form.windowMinutes, { if (it < 60) "$it min" else "${it / 60} h" }) { w ->
                        vm.update { it.copy(windowMinutes = w) }
                    }
                }
                if (form.kind.isPercent) {
                    Text("Sentido", style = MaterialTheme.typography.labelMedium)
                    Chips(directionLabels.map { it.first }, form.direction, { d -> directionLabels.first { it.first == d }.second }) { d ->
                        vm.update { it.copy(direction = d) }
                    }
                }
                OutlinedTextField(
                    value = form.threshold,
                    onValueChange = { v -> vm.update { it.copy(threshold = v) } },
                    label = { Text(if (form.kind.isPercent) "Porcentaje (%)" else "Valor (${Money.symbol})") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (!form.kind.isPercent && Money.current.code != "USD") {
                    parseAmount(form.threshold)?.let { v ->
                        Text(
                            "Se vigila en dólares: ${formatMoney(v)} ≈ ${formatMoney(Money.current.toUsd(v), symbol = "$")} al cambio de hoy",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                OutlinedTextField(
                    value = form.note,
                    onValueChange = { v -> vm.update { it.copy(note = v.replace("\n", " ").take(ALERT_NOTE_MAX)) } },
                    label = { Text("Nota (opcional, sale en el aviso)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            error?.let { Text(it, color = NegativeColor) }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text(if (vm.isEdit) "Guardar y activar" else "Crear alerta") }
        }
    }
}

@Composable
private fun <T> Chips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}

@Composable
private fun TargetPicker(search: SearchViewModel, onCoin: (FormCoin) -> Unit, onPortfolio: () -> Unit) {
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.results.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        GlassCard(Modifier.fillMaxWidth(), onClick = onPortfolio) {
            Text("💼 Mi cartera (valor total)", modifier = Modifier.padding(12.dp))
        }
        OutlinedTextField(
            value = query,
            onValueChange = search::onQuery,
            label = { Text("O busca una moneda") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        results.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        results.data.orEmpty().take(8).forEach { c ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().clickable { onCoin(FormCoin(c.id, c.symbol, c.name, c.thumbUrl)) }.padding(vertical = 8.dp),
            ) {
                AsyncImage(model = c.thumbUrl, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(c.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
