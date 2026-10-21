package app.criptopanel.defi

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.ui.UiState
import app.criptopanel.core.ui.loading
import app.criptopanel.core.ui.with
import kotlinx.coroutines.async
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DefiOverview(
    val totalTvl: Double,
    val history: List<TvlPoint>,
    val chains: List<ChainTvl>,
    val protocols: List<Protocol>,
)

/** Protocols active on [chain], paired with their TVL on that chain, largest first. */
fun protocolsOnChain(protocols: List<Protocol>, chain: String): List<Pair<Protocol, Double>> {
    val names = listOfNotNull(chain, legacyChainNames[chain]).map { it.lowercase() }
    return protocols
        .mapNotNull { p ->
            p.chainTvls.entries.firstOrNull { it.key.lowercase() in names }?.let { p to it.value }
        }
        .sortedByDescending { it.second }
}

/** /v2/chains display names that /protocols may still report under an older name. */
private val legacyChainNames = mapOf(
    "BSC" to "Binance",
    "OP Mainnet" to "Optimism",
    "Gnosis" to "xDai",
    "Rootstock" to "RSK",
    "Hydration" to "HydraDX",
    "PulseChain" to "Pulse",
    "XPR Network" to "Proton",
    "Vaulta" to "EOS",
    "XRPL" to "Ripple",
    "Strato Chain" to "Strato",
    "MultiversX" to "Elrond",
    "ReyaChain" to "Reya Network",
    "Kaia" to "Klaytn",
    "KUB" to "Bitkub",
    "Fuel Ignition" to "Fuel",
    "WEMIX3.0" to "WEMIX",
    "OKTChain" to "OKExChain",
    "opBNB" to "Op_Bnb",
    "MAP Protocol" to "Map",
    "Terra Classic" to "Terra",
    "KCC" to "Kucoin",
    "Oasis Sapphire" to "Sapphire",
    "Oasis Emerald" to "Oasis",
    "Oraichain" to "Orai",
    "CosmosHub" to "Cosmos",
    "Data Network" to "Story",
    "Viction" to "TomoChain",
    "zkLink Nova" to "zkLink",
    "SKALE Europa" to "Europa",
    "Persistence One" to "Persistence",
    "Unit Zero" to "UNIT0",
    "HSK Chain" to "HashKey Chain",
)

class DefiViewModel(private val repo: DefiRepository) : ViewModel() {
    private val _state = MutableStateFlow(UiState<DefiOverview>())
    val state: StateFlow<UiState<DefiOverview>> = _state.asStateFlow()

    private var refreshJob: Job? = null

    fun refresh(force: Boolean = false) {
        _state.update { it.loading() }
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val history = async { repo.totalHistory(force) }
            val chains = async { repo.chains(force) }
            val protocols = async { repo.protocols(force) }
            val h = history.await()
            val c = chains.await()
            val p = protocols.await()
            val outcome: Outcome<DefiOverview> =
                if (h is Outcome.Ok && c is Outcome.Ok && p is Outcome.Ok) {
                    Outcome.Ok(DefiOverview(h.value.lastOrNull()?.tvl ?: 0.0, h.value, c.value, p.value))
                } else {
                    listOf(h, c, p).filterIsInstance<Outcome.Err>().first()
                }
            _state.update { it.with(outcome) }
        }
    }
}
