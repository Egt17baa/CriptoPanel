package app.criptopanel.defi

import app.criptopanel.core.network.Outcome
import app.criptopanel.core.network.TtlCache

class DefiRepository(
    private val api: DefiLlamaApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val chainsCache = TtlCache<List<ChainTvl>>(now)
    private val totalCache = TtlCache<List<TvlPoint>>(now)
    private val protocolsCache = TtlCache<List<Protocol>>(now)
    private val chainHistoryCaches = mutableMapOf<String, TtlCache<List<TvlPoint>>>()

    suspend fun chains(force: Boolean = false): Outcome<List<ChainTvl>> =
        chainsCache.get(force) { api.chains().toChainTvls() }

    suspend fun totalHistory(force: Boolean = false): Outcome<List<TvlPoint>> =
        totalCache.get(force) { api.historical().toPoints() }

    suspend fun chainHistory(name: String): Outcome<List<TvlPoint>> =
        synchronized(chainHistoryCaches) { chainHistoryCaches.getOrPut(name) { TtlCache(now) } }
            .get(force = false) { api.chainHistorical(name).toPoints() }

    suspend fun protocols(force: Boolean = false): Outcome<List<Protocol>> =
        protocolsCache.get(force) { api.protocols().toProtocols() }
}
