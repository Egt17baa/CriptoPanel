package app.criptopanel.defi

import app.criptopanel.core.network.Outcome
import app.criptopanel.core.network.TtlCache

class DefiRepository(
    private val api: DefiLlamaApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val chainsCache = TtlCache<List<ChainTvl>>(now)
    private val totalCache = TtlCache<List<TvlPoint>>(now)
    // /protocols is ~9 MB; DefiLlama itself caches it for 30 minutes.
    private val protocolsCache = TtlCache<List<Protocol>>(now, ttlMs = 30 * 60_000L)
    private val chainHistoryCaches = mutableMapOf<String, TtlCache<List<TvlPoint>>>()

    suspend fun chains(force: Boolean = false): Outcome<List<ChainTvl>> =
        chainsCache.get(force) { api.chains().toChainTvls() }

    suspend fun totalHistory(force: Boolean = false): Outcome<List<TvlPoint>> =
        totalCache.get(force) { api.historical().toPoints() }

    suspend fun chainHistory(name: String, force: Boolean = false): Outcome<List<TvlPoint>> =
        synchronized(chainHistoryCaches) { chainHistoryCaches.getOrPut(name) { TtlCache(now) } }
            .get(force) { api.chainHistorical(name).toPoints() }

    suspend fun protocols(force: Boolean = false): Outcome<List<Protocol>> =
        protocolsCache.get(force) { api.protocols().toProtocols() }
}
