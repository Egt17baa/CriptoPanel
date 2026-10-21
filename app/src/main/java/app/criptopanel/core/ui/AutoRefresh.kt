package app.criptopanel.core.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.delay

/** Calls [onRefresh] right away and then every [intervalMs] while the screen is at least STARTED. */
@Composable
fun AutoRefresh(intervalMs: Long = 60_000, onRefresh: () -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val latest by rememberUpdatedState(onRefresh)
    LaunchedEffect(lifecycleOwner, intervalMs) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while (true) {
                latest()
                delay(intervalMs)
            }
        }
    }
}
