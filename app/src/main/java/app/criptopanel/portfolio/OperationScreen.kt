package app.criptopanel.portfolio

import androidx.compose.foundation.clickable
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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import app.criptopanel.core.format.formatUsd
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.MenuColor
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.message
import app.criptopanel.search.SearchViewModel
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationScreen(vm: OperationViewModel, search: SearchViewModel, onDone: () -> Unit) {
    val form by vm.form.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) onDone() }

    BackScaffold(if (vm.isEdit) "Editar operación" else "Nueva operación", onDone) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(OperationType.BUY to "Compra", OperationType.SELL to "Venta").forEachIndexed { i, (type, label) ->
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = { vm.update { it.copy(type = type) } },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                    ) { Text(label) }
                }
            }

            val coin = form.coin
            if (coin != null && !picking) {
                GlassCard(Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        AsyncImage(model = coin.imageUrl, contentDescription = null, modifier = Modifier.size(32.dp))
                        Column(Modifier.weight(1f)) {
                            Text(coin.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(coin.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        if (!vm.isEdit) TextButton(onClick = { picking = true }) { Text("Cambiar") }
                    }
                }
            } else {
                CoinPicker(search, onPick = {
                    vm.selectCoin(it)
                    picking = false
                })
            }

            AmountField("Cantidad", form.quantity) { v -> vm.update { it.copy(quantity = v) } }
            AmountField("Precio por unidad ($)", form.price) { v -> vm.update { it.copy(price = v) } }
            val q = parseAmount(form.quantity)
            val p = parseAmount(form.price)
            if (q != null && p != null) {
                Text(
                    "Total: ${formatUsd(q * p)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = { datePicker = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Fecha: ${form.date.format(dateFormat)}")
            }
            AmountField("Comisión ($, opcional)", form.fee) { v -> vm.update { it.copy(fee = v) } }
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> vm.update { it.copy(note = v.replace("\n", " ").take(NOTE_MAX)) } },
                label = { Text("Nota (opcional)") },
                supportingText = { Text("${form.note.length}/$NOTE_MAX") },
                modifier = Modifier.fillMaxWidth(),
            )
            error?.let { Text(it, color = NegativeColor) }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text("Guardar") }
        }
    }

    if (datePicker) {
        val today = LocalDate.now()
        val state = rememberDatePickerState(
            initialSelectedDateMillis = form.date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)

                override fun isSelectableYear(year: Int): Boolean = year <= today.year
            },
        )
        DatePickerDialog(
            onDismissRequest = { datePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { millis ->
                        // The picker works in UTC days.
                        val day = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        vm.update { it.copy(date = day) }
                    }
                    datePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = { TextButton(onClick = { datePicker = false }) { Text("Cancelar") } },
            colors = DatePickerDefaults.colors(containerColor = MenuColor),
        ) {
            DatePicker(state = state, colors = DatePickerDefaults.colors(containerColor = MenuColor))
        }
    }
}

@Composable
private fun AmountField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun CoinPicker(search: SearchViewModel, onPick: (FormCoin) -> Unit) {
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.results.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = search::onQuery,
            label = { Text("Moneda") },
            placeholder = { Text("Busca por nombre o símbolo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        results.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        results.data.orEmpty().take(8).forEach { c ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onPick(FormCoin(c.id, c.symbol, c.name, c.thumbUrl)) }
                    .padding(vertical = 8.dp),
            ) {
                AsyncImage(model = c.thumbUrl, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(c.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
