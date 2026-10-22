package app.criptopanel.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.core.network.Outcome
import app.criptopanel.market.MarketRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.UUID

data class FormCoin(val id: String, val symbol: String, val name: String, val imageUrl: String?)

/** What the amount field holds: coin units, or dollars spent / received. */
enum class AmountMode { QUANTITY, USD }

data class OperationForm(
    val type: OperationType = OperationType.BUY,
    val coin: FormCoin? = null,
    val mode: AmountMode = AmountMode.USD,
    /** Coin units or dollars, depending on [mode]. */
    val amount: String = "",
    val price: String = "",
    /** The user typed the price: changing the date or coin no longer overwrites it. */
    val priceEdited: Boolean = false,
    val date: LocalDate = LocalDate.now(),
    val fee: String = "",
    val note: String = "",
) {
    /** Coin units implied by the amount and price, or null while either is missing. */
    val quantity: Double?
        get() {
            val a = parseAmount(amount)?.takeIf { it > 0 } ?: return null
            if (mode == AmountMode.QUANTITY) return a
            val p = parseAmount(price)?.takeIf { it > 0 } ?: return null
            return a / p
        }

    /** Dollars before fees, or null while unknown. */
    val total: Double?
        get() {
            val p = parseAmount(price)?.takeIf { it > 0 } ?: return null
            return quantity?.let { it * p }
        }
}

/** Accepts a decimal comma or point; no thousands separators. */
fun parseAmount(text: String): Double? =
    text.trim().replace(" ", "").replace(',', '.').takeIf { it.count { c -> c == '.' } <= 1 }?.toDoubleOrNull()?.takeIf { it.isFinite() }

sealed interface FormResult {
    data class Valid(val operation: Operation) : FormResult
    data class Invalid(val message: String) : FormResult
}

fun OperationForm.validate(id: String, today: LocalDate): FormResult {
    val c = coin ?: return FormResult.Invalid("Elige una moneda")
    if (parseAmount(amount)?.takeIf { it > 0 } == null) {
        return FormResult.Invalid(if (mode == AmountMode.USD) "Importe no válido" else "Cantidad no válida")
    }
    val p = parseAmount(price)?.takeIf { it > 0 } ?: return FormResult.Invalid("Precio no válido")
    val q = quantity ?: return FormResult.Invalid("Cantidad no válida")
    val f = if (fee.isBlank()) 0.0 else parseAmount(fee)?.takeIf { it >= 0 } ?: return FormResult.Invalid("Comisión no válida")
    if (date.isAfter(today)) return FormResult.Invalid("La fecha no puede ser futura")
    return FormResult.Valid(Operation(id, c.id, c.symbol, c.name, c.imageUrl, type, q, p, f, date.toString(), note.trim().take(NOTE_MAX)))
}

/** Where the price in the form came from, shown under it. */
sealed interface PriceSource {
    data object Loading : PriceSource
    data object Current : PriceSource
    data class OnDate(val date: LocalDate, val provider: String) : PriceSource
    data object Missing : PriceSource
    data object Typed : PriceSource
}

/** Creates an operation ([editId] null) or edits one; [coinId] preselects a coin. */
class OperationViewModel(
    private val store: PortfolioStore,
    private val market: MarketRepository,
    private val history: HistoricalPrices,
    private val editId: String?,
    coinId: String?,
    private val today: () -> LocalDate = LocalDate::now,
) : ViewModel() {
    private val _form = MutableStateFlow(OperationForm(date = today()))
    val form: StateFlow<OperationForm> = _form.asStateFlow()

    private val _priceSource = MutableStateFlow<PriceSource?>(null)
    val priceSource: StateFlow<PriceSource?> = _priceSource.asStateFlow()

    /** How much of the chosen coin you hold on the chosen date (for sells). */
    private val _held = MutableStateFlow<Double?>(null)
    val held: StateFlow<Double?> = _held.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val isEdit: Boolean get() = editId != null

    private var currentPrice: Double? = null
    private var priceJob: Job? = null

    init {
        viewModelScope.launch {
            store.load()
            val existing = editId?.let { id -> store.data.value.operations.firstOrNull { it.id == id } }
            if (existing != null) {
                _form.value = OperationForm(
                    type = existing.type,
                    coin = FormCoin(existing.coinId, existing.symbol, existing.name, existing.imageUrl),
                    mode = AmountMode.QUANTITY,
                    amount = existing.quantity.toBigDecimal().toPlainString(),
                    price = existing.priceUsd.toBigDecimal().toPlainString(),
                    priceEdited = true,
                    date = existing.localDate,
                    fee = if (existing.feeUsd == 0.0) "" else existing.feeUsd.toBigDecimal().toPlainString(),
                    note = existing.note,
                )
                _priceSource.value = PriceSource.Typed
                refreshHeld()
            } else if (coinId != null) {
                val m = (market.marketsByIds(listOf(coinId)) as? Outcome.Ok)?.value?.firstOrNull()
                val coin = m?.let { FormCoin(it.id, it.symbol, it.name, it.imageUrl) } ?: FormCoin(coinId, coinId.uppercase(), coinId, null)
                selectCoin(coin)
            }
        }
    }

    fun update(change: (OperationForm) -> OperationForm) {
        _form.update(change)
        _error.value = null
    }

    fun setType(type: OperationType) {
        update { it.copy(type = type) }
    }

    fun setMode(mode: AmountMode) {
        // Keep what was typed meaningful: convert it to the other unit when the price is known.
        update { f ->
            if (f.mode == mode) return@update f
            val converted = when (mode) {
                AmountMode.QUANTITY -> f.quantity?.let(::plain)
                AmountMode.USD -> f.total?.let { plain(it, 2) }
            }
            f.copy(mode = mode, amount = converted ?: f.amount)
        }
    }

    /** Sells: a share of what you hold (1.0 = everything). */
    fun setFraction(fraction: Double) {
        val h = _held.value ?: return
        update { it.copy(mode = AmountMode.QUANTITY, amount = plain(h * fraction)) }
    }

    fun setPrice(text: String) {
        update { it.copy(price = text, priceEdited = true) }
        _priceSource.value = PriceSource.Typed
    }

    /** Back to the price the app looks up for the chosen date. */
    fun usePriceOfDate() {
        update { it.copy(priceEdited = false) }
        fillPrice()
    }

    fun setDate(date: LocalDate) {
        update { it.copy(date = date) }
        refreshHeld()
        if (!_form.value.priceEdited) fillPrice()
    }

    fun selectCoin(coin: FormCoin) {
        update { it.copy(coin = coin, priceEdited = false) }
        currentPrice = null
        refreshHeld()
        fillPrice()
    }

    private fun refreshHeld() {
        val f = _form.value
        val coin = f.coin ?: return
        _held.value = heldOn(store.data.value.operations, coin.id, f.date, editId)
    }

    private fun fillPrice() {
        val coin = _form.value.coin ?: return
        val date = _form.value.date
        _priceSource.value = PriceSource.Loading
        priceJob?.cancel()
        priceJob = viewModelScope.launch {
            val now = currentPrice ?: (market.marketsByIds(listOf(coin.id)) as? Outcome.Ok)?.value?.firstOrNull()?.price
            currentPrice = now
            val (price, source) = if (date == today()) {
                now to (if (now != null) PriceSource.Current else PriceSource.Missing)
            } else {
                val dated = history.priceOn(coin.id, coin.symbol, date, now)
                dated?.price to (dated?.let { PriceSource.OnDate(date, it.source) } ?: PriceSource.Missing)
            }
            if (_form.value.priceEdited) return@launch // The user typed a price meanwhile.
            _form.update { it.copy(price = price?.let(::plain) ?: "") }
            _priceSource.value = source
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

/** A number as the user would type it: no exponent, no trailing zeros, at most [decimals] decimals. */
fun plain(value: Double, decimals: Int = 8): String =
    value.toBigDecimal().setScale(decimals, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
