package app.criptopanel.portfolio

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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.format.formatQuantity
import app.criptopanel.core.format.Money
import app.criptopanel.core.format.formatMoney
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.GlassCard
import app.criptopanel.core.ui.MenuColor
import app.criptopanel.core.ui.NegativeColor
import app.criptopanel.core.ui.PositiveColor
import app.criptopanel.core.ui.message
import app.criptopanel.search.SearchViewModel
import coil3.compose.AsyncImage
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

private val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM)

@Composable
private fun Step(number: Int, title: String) {
    Text("$number · $title", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OperationScreen(vm: OperationViewModel, search: SearchViewModel, onDone: () -> Unit) {
    val form by vm.form.collectAsStateWithLifecycle()
    val source by vm.priceSource.collectAsStateWithLifecycle()
    val held by vm.held.collectAsStateWithLifecycle()
    val error by vm.error.collectAsStateWithLifecycle()
    val saved by vm.saved.collectAsStateWithLifecycle()
    var picking by rememberSaveable { mutableStateOf(false) }
    var datePicker by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(saved) { if (saved) onDone() }
    val buy = form.type == OperationType.BUY
    val accent = if (buy) PositiveColor else NegativeColor
    val coin = form.coin
    val today = LocalDate.now()

    BackScaffold(if (vm.isEdit) "Editar operación" else "Nueva operación", onDone) { padding ->
        Column(
            Modifier.padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Step(1, "Moneda")
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
            if (coin == null) return@Column

            Step(2, "¿Compra o venta?")
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(OperationType.BUY to "Compra", OperationType.SELL to "Venta").forEachIndexed { i, (type, label) ->
                    val color = if (type == OperationType.BUY) PositiveColor else NegativeColor
                    SegmentedButton(
                        selected = form.type == type,
                        onClick = { vm.setType(type) },
                        shape = SegmentedButtonDefaults.itemShape(i, 2),
                        colors = SegmentedButtonDefaults.colors(
                            activeContainerColor = color.copy(alpha = 0.22f),
                            activeContentColor = color,
                            activeBorderColor = color,
                        ),
                    ) { Text(label) }
                }
            }

            Step(3, "Cuándo")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
                FilterChip(selected = form.date == today, onClick = { vm.setDate(today) }, label = { Text("Hoy") })
                FilterChip(selected = form.date == today.minusDays(1), onClick = { vm.setDate(today.minusDays(1)) }, label = { Text("Ayer") })
                val other = form.date.isBefore(today.minusDays(1))
                FilterChip(
                    selected = other,
                    onClick = { datePicker = true },
                    label = { Text(if (other) form.date.format(dateFormat) else "Otra fecha…") },
                )
            }

            Step(4, if (buy) "Cuánto compraste" else "Cuánto vendiste")
            if (!buy && held != null) {
                val h = held ?: 0.0
                Text(
                    if (h > 0) "Tienes ${formatQuantity(h)} ${coin.symbol} ese día" else "No tenías ${coin.symbol} ese día",
                    color = if (h > 0) MaterialTheme.colorScheme.onSurfaceVariant else NegativeColor,
                )
                if (h > 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0.25 to "25 %", 0.5 to "50 %", 0.75 to "75 %", 1.0 to "Todo").forEach { (f, label) ->
                            AssistChip(onClick = { vm.setFraction(f) }, label = { Text(label) })
                        }
                    }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                listOf(AmountMode.USD to "Importe en ${Money.symbol}", AmountMode.QUANTITY to "Cantidad de ${coin.symbol}").forEachIndexed { i, (mode, label) ->
                    SegmentedButton(selected = form.mode == mode, onClick = { vm.setMode(mode) }, shape = SegmentedButtonDefaults.itemShape(i, 2)) {
                        Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            OutlinedTextField(
                value = form.amount,
                onValueChange = { v -> vm.update { it.copy(amount = v) } },
                label = { Text(if (form.mode == AmountMode.USD) (if (buy) "Importe que pagaste" else "Importe que recibiste") else "Cantidad de ${coin.symbol}") },
                suffix = { Text(if (form.mode == AmountMode.USD) Money.symbol else coin.symbol) },
                supportingText = {
                    val other = if (form.mode == AmountMode.USD) form.quantity?.let { "≈ ${formatQuantity(it)} ${coin.symbol}" } else form.total?.let { "≈ ${formatMoney(it)}" }
                    other?.let { Text(it) }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )

            Step(5, "Precio por unidad")
            OutlinedTextField(
                value = form.price,
                onValueChange = vm::setPrice,
                suffix = { Text(Money.symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = {
                    Text(
                        when (val s = source) {
                            PriceSource.Loading -> "Buscando el precio…"
                            PriceSource.Current -> "Precio actual"
                            is PriceSource.OnDate -> "Precio del ${s.date.format(dateFormat)} (${s.provider})"
                            PriceSource.Missing -> "No encontramos el precio de ese día: escríbelo tú"
                            PriceSource.Typed -> "Precio escrito por ti"
                            null -> ""
                        },
                    )
                },
                modifier = Modifier.fillMaxWidth(),
            )
            if (source == PriceSource.Typed) {
                TextButton(onClick = vm::usePriceOfDate) { Text("Usar el precio de ese día") }
            }

            Step(6, "Opcional")
            OutlinedTextField(
                value = form.fee,
                onValueChange = { v -> vm.update { it.copy(fee = v) } },
                label = { Text("Comisión del exchange") },
                suffix = { Text(Money.symbol) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = form.note,
                onValueChange = { v -> vm.update { it.copy(note = v.replace("\n", " ").take(NOTE_MAX)) } },
                label = { Text("Nota (exchange, motivo…)") },
                supportingText = { Text("${form.note.length}/$NOTE_MAX") },
                modifier = Modifier.fillMaxWidth(),
            )

            Summary(form, coin, accent)
            error?.let { Text(it, color = NegativeColor) }
            Button(onClick = vm::save, modifier = Modifier.fillMaxWidth()) { Text(if (buy) "Guardar compra" else "Guardar venta") }
        }
    }

    if (datePicker) {
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
                    // The picker works in UTC days.
                    state.selectedDateMillis?.let { vm.setDate(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
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

/** Plain-language recap of what will be saved. */
@Composable
private fun Summary(form: OperationForm, coin: FormCoin, accent: Color) {
    val q = form.quantity ?: return
    val total = form.total ?: return
    val price = parseAmount(form.price) ?: return
    val fee = parseAmount(form.fee)?.takeIf { it > 0 } ?: 0.0
    val buy = form.type == OperationType.BUY
    GlassCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Resumen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                "${if (buy) "Compras" else "Vendes"} ${formatQuantity(q)} ${coin.symbol} a ${formatMoney(price)} el ${form.date.format(dateFormat)}",
                style = MaterialTheme.typography.titleSmall,
                color = accent,
            )
            if (buy) {
                Text("Pagas ${formatMoney(total)}" + if (fee > 0) " + ${formatMoney(fee)} de comisión = ${formatMoney(total + fee)}" else "")
            } else {
                Text("Recibes ${formatMoney(total)}" + if (fee > 0) " − ${formatMoney(fee)} de comisión = ${formatMoney(total - fee)}" else "")
            }
        }
    }
}

@Composable
private fun CoinPicker(search: SearchViewModel, onPick: (FormCoin) -> Unit) {
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.results.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = search::onQuery,
            label = { Text("Busca la moneda") },
            placeholder = { Text("Bitcoin, ETH, Solana…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        results.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        results.data.orEmpty().take(8).forEach { c ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().clickable { onPick(FormCoin(c.id, c.symbol, c.name, c.thumbUrl)) }.padding(vertical = 8.dp),
            ) {
                AsyncImage(model = c.thumbUrl, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(c.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
