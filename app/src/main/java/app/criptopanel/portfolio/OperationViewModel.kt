package app.criptopanel.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.core.network.Outcome
import app.criptopanel.market.MarketRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class FormCoin(val id: String, val symbol: String, val name: String, val imageUrl: String?)

data class OperationForm(
    val type: OperationType = OperationType.BUY,
    val coin: FormCoin? = null,
    val quantity: String = "",
    val price: String = "",
    val date: LocalDate = LocalDate.now(),
    val fee: String = "",
    val note: String = "",
)

/** Accepts a decimal comma or point; no thousands separators. */
fun parseAmount(text: String): Double? =
    text.trim().replace(" ", "").replace(',', '.').takeIf { it.count { c -> c == '.' } <= 1 }?.toDoubleOrNull()?.takeIf { it.isFinite() }

sealed interface FormResult {
    data class Valid(val operation: Operation) : FormResult
    data class Invalid(val message: String) : FormResult
}

fun OperationForm.validate(id: String, today: LocalDate): FormResult {
    val c = coin ?: return FormResult.Invalid("Elige una moneda")
    val q = parseAmount(quantity)?.takeIf { it > 0 } ?: return FormResult.Invalid("Cantidad no válida")
    val p = parseAmount(price)?.takeIf { it > 0 } ?: return FormResult.Invalid("Precio no válido")
    val f = if (fee.isBlank()) 0.0 else parseAmount(fee)?.takeIf { it >= 0 } ?: return FormResult.Invalid("Comisión no válida")
    if (date.isAfter(today)) return FormResult.Invalid("La fecha no puede ser futura")
    return FormResult.Valid(Operation(id, c.id, c.symbol, c.name, c.imageUrl, type, q, p, f, date.toString(), note.trim().take(NOTE_MAX)))
}

/** Creates an operation ([editId] null) or edits one; [coinId] preselects a coin and fills in its current price. */
class OperationViewModel(
    private val store: PortfolioStore,
    private val market: MarketRepository,
    private val editId: String?,
    coinId: String?,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val _form = MutableStateFlow(OperationForm(date = today()))
    val form: StateFlow<OperationForm> = _form.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val isEdit: Boolean get() = editId != null

    init {
        viewModelScope.launch {
            store.load()
            val existing = editId?.let { id -> store.data.value.operations.firstOrNull { it.id == id } }
            if (existing != null) {
                _form.value = OperationForm(
                    type = existing.type,
                    coin = FormCoin(existing.coinId, existing.symbol, existing.name, existing.imageUrl),
                    quantity = existing.quantity.toBigDecimal().toPlainString(),
                    price = existing.priceUsd.toBigDecimal().toPlainString(),
                    date = existing.localDate,
                    fee = if (existing.feeUsd == 0.0) "" else existing.feeUsd.toBigDecimal().toPlainString(),
                    note = existing.note,
                )
            } else if (coinId != null) {
                fillFromMarket(coinId, fallback = null)
            }
        }
    }

    fun update(change: (OperationForm) -> OperationForm) {
        _form.update(change)
        _error.value = null
    }

    /** Picks [coin] and, if the price is still empty, fills in its current price. */
    fun selectCoin(coin: FormCoin) {
        update { it.copy(coin = coin) }
        viewModelScope.launch { fillFromMarket(coin.id, fallback = coin) }
    }

    private suspend fun fillFromMarket(coinId: String, fallback: FormCoin?) {
        val m = (market.marketsByIds(listOf(coinId)) as? Outcome.Ok)?.value?.firstOrNull()
        _form.update { f ->
            val coin = m?.let { FormCoin(it.id, it.symbol, it.name, it.imageUrl) } ?: fallback ?: f.coin
            val price = if (f.price.isBlank() && m?.price != null) m.price.toBigDecimal().toPlainString() else f.price
            f.copy(coin = coin, price = price)
        }
    }

    fun save() {
        when (val r = _form.value.validate(editId ?: UUID.randomUUID().toString(), today())) {
            is FormResult.Invalid -> _error.value = r.message
            is FormResult.Valid -> viewModelScope.launch {
                val problem = store.upsert(r.operation)
                if (problem == null) _saved.value = true else _error.value = problem
            }
        }
    }
}
