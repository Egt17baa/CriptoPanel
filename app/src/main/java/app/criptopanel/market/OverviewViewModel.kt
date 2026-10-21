package app.criptopanel.market

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.core.ui.UiState
import app.criptopanel.core.ui.loading
import app.criptopanel.core.ui.with
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class OverviewViewModel(private val repo: MarketRepository) : ViewModel() {
    private val _state = MutableStateFlow(UiState<GlobalMetrics>())
    val state: StateFlow<UiState<GlobalMetrics>> = _state.asStateFlow()

    fun refresh(force: Boolean = false) {
        _state.update { it.loading() }
        viewModelScope.launch {
            val outcome = repo.global(force)
            _state.update { it.with(outcome) }
        }
    }
}
