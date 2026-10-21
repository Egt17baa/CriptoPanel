package app.criptopanel.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.ui.UiState
import app.criptopanel.market.MarketRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.stateIn

const val SEARCH_DEBOUNCE_MS = 400L
const val SEARCH_MIN_CHARS = 2

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repo: MarketRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val retries = MutableStateFlow(0)

    /** Debounced results; queries shorter than [SEARCH_MIN_CHARS] give an empty list without a request. */
    val results: StateFlow<UiState<List<SearchCoin>>> = combine(_query.debounce(SEARCH_DEBOUNCE_MS), retries) { q, _ -> q.trim() }
        .transformLatest { q ->
            if (q.length < SEARCH_MIN_CHARS) {
                emit(UiState(data = emptyList()))
            } else {
                emit(UiState(loading = true))
                when (val outcome = repo.search(q)) {
                    is Outcome.Ok -> emit(UiState(data = outcome.value))
                    is Outcome.Err -> emit(UiState(error = outcome.error))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState(data = emptyList()))

    fun onQuery(q: String) { _query.value = q }

    /** Runs the current query again, e.g. after a network error. */
    fun retry() {
        retries.value++
    }
}
