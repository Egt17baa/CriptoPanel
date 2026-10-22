package app.criptopanel.portfolio

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.coins.ChartRange
import app.criptopanel.coins.CoinMarket
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.ui.UiState
import app.criptopanel.core.ui.loading
import app.criptopanel.core.ui.with
import app.criptopanel.market.MarketRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** The value history asks CoinGecko once per coin, so only the largest positions are drawn. */
const val SERIES_MAX_COINS = 10

class PortfolioViewModel(private val store: PortfolioStore, private val market: MarketRepository) : ViewModel() {
    val data: StateFlow<PortfolioData> = store.data

    private val _prices = MutableStateFlow(UiState<Map<String, CoinMarket>>())
    val prices: StateFlow<UiState<Map<String, CoinMarket>>> = _prices.asStateFlow()

    val summary: StateFlow<PortfolioSummary> = combine(data, _prices) { d, p -> summarize(positions(d.operations), p.data.orEmpty()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, summarize(emptyList(), emptyMap()))

    /** Favourites with their market data, in the order they were starred; unknown prices are left out until they load. */
    val favorites: StateFlow<List<CoinMarket>> = combine(data, _prices) { d, p -> d.favorites.mapNotNull { p.data?.get(it) } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val _range = MutableStateFlow(ChartRange.M1)
    val range: StateFlow<ChartRange> = _range.asStateFlow()

    private val _series = MutableStateFlow(UiState<List<PricePoint>>())
    val series: StateFlow<UiState<List<PricePoint>>> = _series.asStateFlow()

    private var pricesJob: Job? = null
    private var seriesJob: Job? = null

    init {
        viewModelScope.launch { store.load() }
        // A new coin or favourite needs its price; a new operation changes the history.
        viewModelScope.launch { data.map { trackedIds(it) }.distinctUntilChanged().collect { refreshPrices(force = false) } }
        viewModelScope.launch { data.map { it.operations }.distinctUntilChanged().collect { loadSeries(force = false) } }
    }

    private fun trackedIds(d: PortfolioData): List<String> =
        (positions(d.operations).filter { it.isOpen }.map { it.coinId } + d.favorites).distinct()

    /** Pull to refresh: prices and the value history. */
    fun refreshAll(force: Boolean = true) {
        refreshPrices(force)
        loadSeries(force)
    }

    /** Prices only: cheap enough for the 60 s auto-refresh (one request), unlike the history (one per coin). */
    fun refreshPrices(force: Boolean = false) {
        val ids = trackedIds(data.value)
        _prices.update { it.loading() }
        pricesJob?.cancel()
        pricesJob = viewModelScope.launch {
            val outcome = when (val r = market.marketsByIds(ids, force)) {
                is Outcome.Ok -> Outcome.Ok(r.value.associateBy { it.id })
                is Outcome.Err -> r
            }
            _prices.update { it.with(outcome) }
        }
    }

    fun setRange(range: ChartRange) {
        _range.value = range
        loadSeries(force = false)
    }

    /** More open positions than [SERIES_MAX_COINS]: the history leaves the smallest out. */
    val seriesTruncated: StateFlow<Boolean> = data.map { d -> positions(d.operations).count { it.isOpen } > SERIES_MAX_COINS }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private fun loadSeries(force: Boolean) {
        val ops = data.value.operations
        val range = _range.value
        // From the operations themselves (summary may lag a change): largest by value once prices are in, by cost before that.
        val prices = _prices.value.data.orEmpty()
        val coins = positions(ops)
            .filter { it.isOpen }
            .sortedByDescending { p -> prices[p.coinId]?.price?.let { it * p.quantity } ?: p.cost }
            .take(SERIES_MAX_COINS)
            .map { it.coinId }
        _series.update { it.loading() }
        seriesJob?.cancel()
        seriesJob = viewModelScope.launch {
            val points = mutableMapOf<String, List<PricePoint>>()
            for (id in coins) {
                when (val r = market.pricePoints(id, range, force)) {
                    is Outcome.Ok -> points[id] = r.value
                    is Outcome.Err -> {
                        _series.update { it.with(r) }
                        return@launch
                    }
                }
            }
            val grid = coins.firstOrNull()?.let { points[it] }.orEmpty().map { it.epochMillis }
            _series.update { it.with(Outcome.Ok(valueSeries(ops, points, grid))) }
        }
    }

    fun toggleFavorite(coinId: String) {
        viewModelScope.launch { store.toggleFavorite(coinId) }
    }
}
