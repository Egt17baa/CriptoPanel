package app.criptopanel.tools

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.coins.ChartRange
import app.criptopanel.coins.CoinMarket
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.ui.UiState
import app.criptopanel.core.ui.loading
import app.criptopanel.core.ui.with
import app.criptopanel.market.MarketRepository
import app.criptopanel.portfolio.FormCoin
import app.criptopanel.portfolio.PricePoint
import app.criptopanel.portfolio.parseAmount
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

private fun CoinMarket.toFormCoin() = FormCoin(id, symbol, name, imageUrl)

class CalculatorViewModel(private val market: MarketRepository) : ViewModel() {
    private val _from = MutableStateFlow<Asset>(Asset.Coin(BITCOIN))
    val from: StateFlow<Asset> = _from.asStateFlow()
    private val _to = MutableStateFlow<Asset>(Asset.Usd)
    val to: StateFlow<Asset> = _to.asStateFlow()
    private val _amount = MutableStateFlow("1")
    val amount: StateFlow<String> = _amount.asStateFlow()

    /** USD price per coin id. */
    private val _prices = MutableStateFlow(UiState<Map<String, CoinMarket>>())
    val prices: StateFlow<UiState<Map<String, CoinMarket>>> = _prices.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    fun setAmount(text: String) {
        _amount.value = text
    }

    fun setFrom(asset: Asset) {
        _from.value = asset
        refresh()
    }

    fun setTo(asset: Asset) {
        _to.value = asset
        refresh()
    }

    fun swap() {
        val f = _from.value
        _from.value = _to.value
        _to.value = f
    }

    fun refresh(force: Boolean = false) {
        val ids = listOf(_from.value, _to.value).filterIsInstance<Asset.Coin>().map { it.coin.id }.distinct()
        _prices.update { it.loading() }
        job?.cancel()
        job = viewModelScope.launch {
            val r = when (val o = market.marketsByIds(ids, force)) {
                is Outcome.Ok -> Outcome.Ok(o.value.associateBy { it.id })
                is Outcome.Err -> o
            }
            _prices.update { it.with(r) }
            // Fill in names and logos for coins picked by id only.
            r.let { if (it is Outcome.Ok) enrich(it.value) }
        }
    }

    private fun enrich(found: Map<String, CoinMarket>) {
        fun Asset.better(): Asset = if (this is Asset.Coin) found[coin.id]?.let { Asset.Coin(it.toFormCoin()) } ?: this else this
        _from.value = _from.value.better()
        _to.value = _to.value.better()
    }

    fun usdPrice(asset: Asset): Double? = when (asset) {
        Asset.Usd -> 1.0
        is Asset.Coin -> _prices.value.data?.get(asset.coin.id)?.price
    }

    /** The converted amount, or null while a price or the amount is missing. */
    fun result(): Double? {
        val a = parseAmount(_amount.value) ?: return null
        return convert(a, usdPrice(_from.value), usdPrice(_to.value))
    }
}

class WhatIfViewModel(private val market: MarketRepository, private val today: () -> LocalDate = LocalDate::now) : ViewModel() {
    private val _coin = MutableStateFlow(BITCOIN)
    val coin: StateFlow<FormCoin> = _coin.asStateFlow()
    private val _amount = MutableStateFlow("100")
    val amount: StateFlow<String> = _amount.asStateFlow()
    private val _period = MutableStateFlow(WhatIfPeriod.Y1)
    val period: StateFlow<WhatIfPeriod> = _period.asStateFlow()
    private val _monthly = MutableStateFlow(false)
    val monthly: StateFlow<Boolean> = _monthly.asStateFlow()

    private val _data = MutableStateFlow(UiState<Pair<List<PricePoint>, Double>>())

    /** Price history and current price of the coin; the result is recomputed from them without new requests. */
    val data: StateFlow<UiState<Pair<List<PricePoint>, Double>>> = _data.asStateFlow()

    private var job: Job? = null

    init {
        load()
    }

    fun setCoin(c: FormCoin) {
        _coin.value = c
        load()
    }

    fun setAmount(text: String) { _amount.value = text }
    fun setPeriod(p: WhatIfPeriod) { _period.value = p }
    fun setMonthly(m: Boolean) { _monthly.value = m }

    fun load(force: Boolean = false) {
        val id = _coin.value.id
        _data.update { UiState(loading = true) }
        job?.cancel()
        job = viewModelScope.launch {
            val now = market.marketsByIds(listOf(id), force)
            val pts = market.pricePoints(id, ChartRange.Y1, force)
            val out: Outcome<Pair<List<PricePoint>, Double>> = when {
                now is Outcome.Err -> now
                pts is Outcome.Err -> pts
                else -> {
                    val m = (now as Outcome.Ok).value.firstOrNull()
                    m?.let { _coin.value = it.toFormCoin() }
                    val price = m?.price ?: (pts as Outcome.Ok).value.lastOrNull()?.price ?: 0.0
                    Outcome.Ok((pts as Outcome.Ok).value to price)
                }
            }
            _data.update { it.with(out) }
        }
    }

    fun result(): WhatIfResult? {
        val (points, price) = _data.value.data ?: return null
        val a = parseAmount(_amount.value) ?: return null
        return simulate(points, price, a, _period.value, _monthly.value, today())
    }
}

class CompareViewModel(private val market: MarketRepository) : ViewModel() {
    private val _coins = MutableStateFlow(listOf(BITCOIN, ETHEREUM))
    val coins: StateFlow<List<FormCoin>> = _coins.asStateFlow()
    private val _range = MutableStateFlow(ChartRange.M1)
    val range: StateFlow<ChartRange> = _range.asStateFlow()
    private val _markets = MutableStateFlow(UiState<Map<String, CoinMarket>>())
    val markets: StateFlow<UiState<Map<String, CoinMarket>>> = _markets.asStateFlow()
    private val _series = MutableStateFlow<Map<String, List<PricePoint>>>(emptyMap())
    val series: StateFlow<Map<String, List<PricePoint>>> = _series.asStateFlow()

    private var job: Job? = null

    init {
        refresh()
    }

    /** False when the list is full or the coin is already in it. */
    fun add(c: FormCoin): Boolean {
        if (_coins.value.size >= MAX_COMPARED || _coins.value.any { it.id == c.id }) return false
        _coins.value = _coins.value + c
        refresh()
        return true
    }

    fun remove(id: String) {
        _coins.value = _coins.value.filterNot { it.id == id }
        refresh()
    }

    fun setRange(r: ChartRange) {
        _range.value = r
        refresh()
    }

    fun refresh(force: Boolean = false) {
        val ids = _coins.value.map { it.id }
        val range = _range.value
        _markets.update { it.loading() }
        job?.cancel()
        job = viewModelScope.launch {
            val m = when (val o = market.marketsByIds(ids, force)) {
                is Outcome.Ok -> Outcome.Ok(o.value.associateBy { it.id })
                is Outcome.Err -> o
            }
            _markets.update { it.with(m) }
            if (m is Outcome.Ok) _coins.value = _coins.value.map { c -> m.value[c.id]?.toFormCoin() ?: c }
            val s = mutableMapOf<String, List<PricePoint>>()
            for (id in ids) (market.pricePoints(id, range, force) as? Outcome.Ok)?.let { s[id] = it.value }
            _series.value = s
        }
    }
}
