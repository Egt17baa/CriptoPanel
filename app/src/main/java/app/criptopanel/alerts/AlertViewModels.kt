package app.criptopanel.alerts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.core.format.Money
import app.criptopanel.core.network.Outcome
import app.criptopanel.market.MarketRepository
import app.criptopanel.portfolio.FormCoin
import app.criptopanel.portfolio.PortfolioStore
import app.criptopanel.portfolio.parseAmount
import app.criptopanel.portfolio.positions
import app.criptopanel.portfolio.summarize
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

/** Current price of [coinId], or the portfolio's value when [coinId] is null; null if unknown. */
suspend fun currentValue(coinId: String?, market: MarketRepository, portfolio: PortfolioStore): Double? {
    if (coinId != null) return (market.marketsByIds(listOf(coinId)) as? Outcome.Ok)?.value?.firstOrNull()?.price
    portfolio.load()
    val open = positions(portfolio.data.value.operations).filter { it.isOpen }
    if (open.isEmpty()) return null
    val prices = (market.marketsByIds(open.map { it.coinId }) as? Outcome.Ok)?.value?.associateBy { it.id } ?: return null
    return summarize(open, prices).value
}

class AlertsViewModel(
    private val store: AlertStore,
    val monitor: AlertMonitor,
    private val market: MarketRepository,
    private val portfolio: PortfolioStore,
) : ViewModel() {
    val data: StateFlow<AlertsData> = store.data

    // A channel, not a SharedFlow: a message sent before the screen collects is kept, not dropped.
    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages: Flow<String> = _messages.receiveAsFlow()

    init {
        viewModelScope.launch { store.load() }
    }

    /** Refuses to switch on an alert that would fire at once (its price is already past the threshold). */
    fun setEnabled(alert: Alert, enabled: Boolean) {
        viewModelScope.launch {
            if (enabled && !alert.kind.isPercent) {
                val problem = thresholdProblem(alert.kind, alert.threshold, currentValue(alert.coinId, market, portfolio))
                if (problem != null) {
                    _messages.send("$problem: edita la alerta para cambiarlo")
                    return@launch
                }
            }
            store.setEnabled(alert.id, enabled)
        }
    }

    fun delete(alert: Alert) {
        viewModelScope.launch { store.delete(alert.id) }
    }

    fun restore(alert: Alert) {
        viewModelScope.launch { store.upsert(alert) }
    }
}

data class AlertForm(
    val coin: FormCoin? = null,
    val portfolio: Boolean = false,
    val kind: AlertKind = AlertKind.PRICE_ABOVE,
    val threshold: String = "",
    val windowMinutes: Int = 60,
    val direction: Direction = Direction.ANY,
    val note: String = "",
)

const val ALERT_NOTE_MAX = 120

/** Creates an alert, or edits [editId]; [coinId] preselects a coin. Saving enables the alert again. */
class AlertFormViewModel(
    private val store: AlertStore,
    private val market: MarketRepository,
    private val portfolio: PortfolioStore,
    private val editId: String?,
    coinId: String?,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {
    private val _form = MutableStateFlow(AlertForm())
    val form: StateFlow<AlertForm> = _form.asStateFlow()

    /** Price of the chosen coin, or the portfolio value. */
    private val _current = MutableStateFlow<Double?>(null)
    val current: StateFlow<Double?> = _current.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    val isEdit: Boolean get() = editId != null
    private var createdAt: Long? = null

    init {
        viewModelScope.launch {
            store.load()
            val existing = editId?.let { id -> store.data.value.alerts.firstOrNull { it.id == id } }
            if (existing != null) {
                createdAt = existing.createdAt
                _form.value = AlertForm(
                    coin = existing.coinId?.let { FormCoin(it, existing.symbol, existing.name, existing.imageUrl) },
                    portfolio = existing.coinId == null,
                    kind = existing.kind,
                    threshold = (if (existing.kind.isPercent) existing.threshold else Money.current.fromUsd(existing.threshold))
                        .toBigDecimal().setScale(2, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString(),
                    windowMinutes = existing.windowMinutes ?: 60,
                    direction = existing.direction,
                    note = existing.note,
                )
                refreshCurrent()
            } else if (coinId != null) {
                val m = (market.marketsByIds(listOf(coinId)) as? Outcome.Ok)?.value?.firstOrNull()
                _form.update { it.copy(coin = m?.let { c -> FormCoin(c.id, c.symbol, c.name, c.imageUrl) } ?: FormCoin(coinId, coinId.uppercase(), coinId, null)) }
                _current.value = m?.price
            }
        }
    }

    private fun refreshCurrent() {
        val f = _form.value
        if (f.coin == null && !f.portfolio) return
        viewModelScope.launch { _current.value = currentValue(if (f.portfolio) null else f.coin?.id, market, portfolio) }
    }

    fun update(change: (AlertForm) -> AlertForm) {
        _form.update(change)
        _error.value = null
    }

    fun selectCoin(coin: FormCoin) {
        _current.value = null
        update { it.copy(coin = coin, portfolio = false, kind = if (it.kind.forPortfolio) AlertKind.PRICE_ABOVE else it.kind) }
        refreshCurrent()
    }

    fun selectPortfolio() {
        _current.value = null
        update { it.copy(coin = null, portfolio = true, kind = if (it.kind.forPortfolio) it.kind else AlertKind.PORTFOLIO_BELOW) }
        refreshCurrent()
    }

    fun save() {
        val f = _form.value
        if (f.coin == null && !f.portfolio) {
            _error.value = "Elige una moneda o tu cartera"
            return
        }
        // Money thresholds are typed in the display currency and watched in USD at today's rate.
        val value = parseAmount(f.threshold)?.let { if (f.kind.isPercent) it else Money.current.toUsd(it) }
        if (value == null) {
            _error.value = if (f.kind.isPercent) "Porcentaje no válido" else "Valor no válido"
            return
        }
        thresholdProblem(f.kind, value, _current.value)?.let {
            _error.value = it
            return
        }
        val coin = f.coin
        val alert = Alert(
            id = editId ?: UUID.randomUUID().toString(),
            coinId = if (f.portfolio) null else coin?.id,
            symbol = if (f.portfolio) "" else coin?.symbol.orEmpty(),
            name = if (f.portfolio) "Cartera" else coin?.name.orEmpty(),
            imageUrl = if (f.portfolio) null else coin?.imageUrl,
            kind = f.kind,
            threshold = value,
            windowMinutes = if (f.kind == AlertKind.CHANGE_WINDOW) f.windowMinutes else null,
            direction = f.direction,
            note = f.note.replace(Regex("[\\r\\n]+"), " ").trim().take(ALERT_NOTE_MAX),
            enabled = true,
            createdAt = createdAt ?: now(),
        )
        viewModelScope.launch {
            store.upsert(alert)
            _saved.value = true
        }
    }
}
