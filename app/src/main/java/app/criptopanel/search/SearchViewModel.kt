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
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

const val SEARCH_DEBOUNCE_MS = 400L
const val SEARCH_MIN_CHARS = 2

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class SearchViewModel(private val repo: MarketRepository) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Debounced results; queries shorter than [SEARCH_MIN_CHARS] give an empty list without a request. */
    val results: StateFlow<UiState<List<SearchCoin>>> = _query
        .debounce(SEARCH_DEBOUNCE_MS)
        .mapLatest { raw ->
            val q = raw.trim()
            if (q.length < SEARCH_MIN_CHARS) {
                UiState(data = emptyList())
            } else {
                when (val outcome = repo.search(q)) {
                    is Outcome.Ok -> UiState(data = outcome.value)
                    is Outcome.Err -> UiState(error = outcome.error)
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState(data = emptyList()))

    fun onQuery(q: String) { _query.value = q }
}
