package app.criptopanel.coins

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class TopCoinsViewModel(private val repo: MarketRepository) : ViewModel() {
    private val loaded = MutableStateFlow(UiState<List<CoinMarket>>())
    private val _query = MutableStateFlow("")
    private val _sort = MutableStateFlow(SortKey.RANK)

    val query: StateFlow<String> = _query.asStateFlow()
    val sort: StateFlow<SortKey> = _sort.asStateFlow()

    /** Loaded coins with the current query and sort applied. */
    val state: StateFlow<UiState<List<CoinMarket>>> =
        combine(loaded, _query, _sort) { s, q, k -> s.copy(data = s.data?.let { filterAndSort(it, q, k) }) }
            .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    fun setQuery(q: String) { _query.value = q }

    fun setSort(s: SortKey) { _sort.value = s }

    private var refreshJob: Job? = null

    fun refresh(force: Boolean = false) {
        loaded.update { it.loading() }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val outcome = repo.topCoins(force)
            loaded.update { it.with(outcome) }
        }
    }
}
