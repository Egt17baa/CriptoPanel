package androidx.lifecycle

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

// Stand-in for androidx.lifecycle.ViewModel, which is only published on Google
// Maven. Mirrors the API surface the app uses: the class and viewModelScope.
abstract class ViewModel {
    internal val scope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
}

val ViewModel.viewModelScope: CoroutineScope get() = scope
