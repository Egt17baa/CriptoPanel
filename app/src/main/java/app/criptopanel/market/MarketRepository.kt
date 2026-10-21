package app.criptopanel.market

import app.criptopanel.coins.ChartRange
import app.criptopanel.coins.CoinDetail
import app.criptopanel.coins.CoinMarket
import app.criptopanel.coins.toPrices
import app.criptopanel.coins.toDomain
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.network.TtlCache
import app.criptopanel.search.SearchCoin
import app.criptopanel.search.toDomain
import app.criptopanel.core.network.apiCall

class MarketRepository(
    private val api: CoinGeckoApi,
    private val coinLore: CoinLoreApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val globalCache = TtlCache<GlobalMetrics>(now)
    private val topCache = TtlCache<List<CoinMarket>>(now)
    private val chartCaches = mutableMapOf<Pair<String, ChartRange>, TtlCache<List<Double>>>()

    suspend fun global(force: Boolean = false): Outcome<GlobalMetrics> =
        globalCache.get(force) { coinLore.global().first().toMetrics() }

    suspend fun topCoins(force: Boolean = false): Outcome<List<CoinMarket>> =
        topCache.get(force) {
            when (val gecko = apiCall { api.markets().map { it.toDomain() } }) {
                is Outcome.Ok -> gecko.value
                // CoinGecko's free tier is often saturated on shared mobile IPs; CoinLore keeps the list visible.
                is Outcome.Err -> coinLore.tickers().data.map { it.toDomain() }
            }
        }

    suspend fun coin(id: String): Outcome<CoinDetail> = apiCall { api.coin(id).toDomain() }

    suspend fun chart(id: String, range: ChartRange): Outcome<List<Double>> =
        synchronized(chartCaches) { chartCaches.getOrPut(id to range) { TtlCache(now) } }
            .get(force = false) { api.marketChart(id, range.days).toPrices() }

    suspend fun search(q: String): Outcome<List<SearchCoin>> = apiCall { api.search(q).coins.map { it.toDomain() } }
}
