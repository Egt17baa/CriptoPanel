package app.criptopanel.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.ErrorBanner
import app.criptopanel.core.ui.LoadingBox
import coil3.compose.AsyncImage

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(vm: SearchViewModel, onCoin: (String) -> Unit, onBack: () -> Unit) {
    val query by vm.query.collectAsStateWithLifecycle()
    val results by vm.results.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Buscar") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = vm::onQuery,
                placeholder = { Text("Cualquier moneda o token") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().padding(12.dp).focusRequester(focus),
            )
            LaunchedEffect(Unit) { focus.requestFocus() }
            results.error?.let {
                ErrorBanner(it)
                TextButton(onClick = vm::retry, modifier = Modifier.padding(horizontal = 4.dp)) { Text("Reintentar") }
            }
            when {
                results.loading -> LoadingBox()
                results.error == null && query.trim().length >= SEARCH_MIN_CHARS && results.data.isNullOrEmpty() ->
                    EmptyMessage("Sin resultados")
            }
            LazyColumn {
                items(results.data.orEmpty(), key = { it.id }) { coin ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth().clickable { onCoin(coin.id) }.padding(12.dp),
                    ) {
                        AsyncImage(model = coin.thumbUrl, contentDescription = null, modifier = Modifier.size(24.dp))
                        Column(Modifier.weight(1f)) {
                            Text(coin.name)
                            Text(coin.symbol, style = MaterialTheme.typography.bodySmall)
                        }
                        Text(coin.rank?.let { "#$it" } ?: "—", style = MaterialTheme.typography.labelMedium)
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}
