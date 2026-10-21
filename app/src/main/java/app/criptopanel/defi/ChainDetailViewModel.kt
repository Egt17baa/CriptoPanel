package app.criptopanel.defi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.ui.UiState
import app.criptopanel.core.ui.loading
import app.criptopanel.core.ui.with
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ChainDetailViewModel(private val chain: String, private val repo: DefiRepository) : ViewModel() {
    private val _history = MutableStateFlow(UiState<List<TvlPoint>>())
    private val _protocols = MutableStateFlow(UiState<List<Pair<Protocol, Double>>>())

    val history: StateFlow<UiState<List<TvlPoint>>> = _history.asStateFlow()
    val protocols: StateFlow<UiState<List<Pair<Protocol, Double>>>> = _protocols.asStateFlow()

    private var jobs: List<Job> = emptyList()

    init {
        refresh()
    }

    fun refresh(force: Boolean = false) {
        _history.update { it.loading() }
        _protocols.update { it.loading() }
        jobs.forEach { it.cancel() }
        jobs = listOf(
            viewModelScope.launch {
                val outcome = repo.chainHistory(chain, force)
                _history.update { it.with(outcome) }
            },
            viewModelScope.launch {
                val outcome = when (val all = repo.protocols(force)) {
                    is Outcome.Ok -> Outcome.Ok(protocolsOnChain(all.value, chain))
                    is Outcome.Err -> all
                }
                _protocols.update { it.with(outcome) }
            },
        )
    }
}
