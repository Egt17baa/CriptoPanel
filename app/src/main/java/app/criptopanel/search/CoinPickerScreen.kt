package app.criptopanel.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.core.ui.message
import app.criptopanel.portfolio.FormCoin
import coil3.compose.AsyncImage

/** Search field with up to 8 matching coins under it; picking one clears the query. */
@Composable
fun CoinSearchPicker(search: SearchViewModel, label: String, onPick: (FormCoin) -> Unit) {
    val query by search.query.collectAsStateWithLifecycle()
    val results by search.results.collectAsStateWithLifecycle()
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = query,
            onValueChange = search::onQuery,
            label = { Text(label) },
            placeholder = { Text("Bitcoin, ETH, Solana…") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        results.error?.let { Text(it.message(), color = MaterialTheme.colorScheme.onSurfaceVariant) }
        results.data.orEmpty().take(8).forEach { c ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onPick(FormCoin(c.id, c.symbol, c.name, c.thumbUrl))
                        search.onQuery("")
                    }
                    .padding(vertical = 8.dp),
            ) {
                AsyncImage(model = c.thumbUrl, contentDescription = null, modifier = Modifier.size(24.dp))
                Text(c.name, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(c.symbol, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
