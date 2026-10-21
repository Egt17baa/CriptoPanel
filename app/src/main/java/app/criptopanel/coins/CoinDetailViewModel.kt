package app.criptopanel.coins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.core.ui.UiState
import app.criptopanel.core.ui.loading
import app.criptopanel.core.ui.with
import app.criptopanel.market.MarketRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CoinDetailViewModel(private val id: String, private val repo: MarketRepository) : ViewModel() {
    private val _state = MutableStateFlow(UiState<CoinDetail>())
    private val _chart = MutableStateFlow(UiState<List<Double>>())
    private val _range = MutableStateFlow(ChartRange.W1)
    private var chartJob: Job? = null

    val state: StateFlow<UiState<CoinDetail>> = _state.asStateFlow()
    val chart: StateFlow<UiState<List<Double>>> = _chart.asStateFlow()
    val range: StateFlow<ChartRange> = _range.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        _state.update { it.loading() }
        viewModelScope.launch {
            val outcome = repo.coin(id)
            _state.update { it.with(outcome) }
        }
        loadChart()
    }

    fun setRange(r: ChartRange) {
        if (r == _range.value) return
        _range.value = r
        loadChart()
    }

    private fun loadChart() {
        chartJob?.cancel()
        _chart.update { it.loading() }
        val r = _range.value
        chartJob = viewModelScope.launch {
            val outcome = repo.chart(id, r)
            _chart.update { it.with(outcome) }
        }
    }
}
