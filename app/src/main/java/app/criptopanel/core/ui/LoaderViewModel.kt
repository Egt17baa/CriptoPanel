package app.criptopanel.core.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.core.network.Outcome
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** ViewModel for a screen or card that shows the result of one [load] call. */
class LoaderViewModel<T>(private val load: suspend (force: Boolean) -> Outcome<T>) : ViewModel() {
    private val _state = MutableStateFlow(UiState<T>())
    val state: StateFlow<UiState<T>> = _state.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh(force: Boolean = false) {
        _state.update { it.loading() }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val outcome = load(force)
            _state.update { it.with(outcome) }
        }
    }
}
