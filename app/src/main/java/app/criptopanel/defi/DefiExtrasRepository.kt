package app.criptopanel.defi

import app.criptopanel.core.network.Outcome
import app.criptopanel.core.network.TtlCache
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

private const val STABLECOINS_TTL_MS = 10 * 60_000L
// Multi-megabyte responses that DefiLlama itself refreshes slowly, like /protocols.
private const val HEAVY_TTL_MS = 30 * 60_000L

/** Stablecoins, DEX volume, fees and yields: each tab loads only when first opened. */
class DefiExtrasRepository(
    private val llama: DefiLlamaApi,
    private val stables: StablecoinsApi,
    private val yields: YieldsApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val stablecoinsCache = TtlCache<StablecoinOverview>(now, STABLECOINS_TTL_MS)
    private val poolsCache = TtlCache<List<Pool>>(now, HEAVY_TTL_MS)
    private val volumeCaches = mutableMapOf<Pair<VolumeKind, String?>, TtlCache<VolumeOverview>>()

    suspend fun stablecoins(force: Boolean = false): Outcome<StablecoinOverview> =
        stablecoinsCache.get(force) {
            coroutineScope {
                val chart = async { stables.totalChart().toTotals() }
                val coins = async { stables.stablecoins().toStablecoins() }
                stablecoinOverview(chart.await(), coins.await())
            }
        }

    /** [chain] null means all chains. */
    suspend fun volume(kind: VolumeKind, chain: String?, force: Boolean = false): Outcome<VolumeOverview> =
        synchronized(volumeCaches) { volumeCaches.getOrPut(kind to chain) { TtlCache(now, HEAVY_TTL_MS) } }
            .get(force) {
                val dto = if (chain == null) {
                    llama.volumeOverview(kind.path, kind.dataType)
                } else {
                    llama.chainVolumeOverview(kind.path, chain, kind.dataType)
                }
                dto.toDomain()
            }

    suspend fun pools(force: Boolean = false): Outcome<List<Pool>> =
        poolsCache.get(force) { yields.pools().toPools() }
}
