package app.criptopanel.market

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.criptopanel.coins.CoinRow
import app.criptopanel.core.ui.AutoRefresh
import app.criptopanel.core.ui.BackScaffold
import app.criptopanel.core.ui.EmptyMessage
import app.criptopanel.core.ui.LoaderViewModel
import app.criptopanel.core.ui.StateContent

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoversScreen(vm: LoaderViewModel<Movers>, onCoin: (String) -> Unit, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableIntStateOf(0) }
    AutoRefresh { vm.refresh() }
    BackScaffold("Suben y bajan (24 h)", onBack) { padding ->
        Column(Modifier.padding(padding)) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Suben") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Bajan") })
            }
            StateContent(state, onRefresh = { vm.refresh(force = true) }) { movers ->
                val coins = if (tab == 0) movers.gainers else movers.losers
                if (coins.isEmpty()) EmptyMessage("Ninguna moneda del Top 100")
                LazyColumn {
                    items(coins, key = { it.id }) { coin ->
                        CoinRow(coin, onClick = { onCoin(coin.id) })
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}
