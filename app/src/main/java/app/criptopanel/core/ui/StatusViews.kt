package app.criptopanel.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.criptopanel.core.network.AppError

@Composable
fun ErrorBanner(error: AppError, modifier: Modifier = Modifier) {
    Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = modifier.fillMaxWidth()) {
        Text(
            text = error.message(),
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(12.dp),
        )
    }
}

@Composable
fun LoadingBox(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Pull-to-refresh wrapper: spinner on first load, error banner above the last good data. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun <T> StateContent(
    state: UiState<T>,
    onRefresh: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit,
) {
    PullToRefreshBox(
        isRefreshing = state.loading && state.data != null,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize(),
    ) {
        val data = state.data
        // Without data nothing below scrolls, so make the column scrollable for the pull gesture to fire.
        val scroll = if (data == null) Modifier.verticalScroll(rememberScrollState()) else Modifier
        Column(Modifier.fillMaxSize().then(scroll)) {
            state.error?.let { ErrorBanner(it) }
            when {
                data != null -> content(data)
                state.loading -> LoadingBox()
            }
        }
    }
}
