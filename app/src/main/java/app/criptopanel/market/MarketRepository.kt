package app.criptopanel.market

import app.criptopanel.coins.ChartRange
import app.criptopanel.coins.CoinDetail
import app.criptopanel.coins.CoinMarket
import app.criptopanel.coins.toPrices
import app.criptopanel.coins.toDomain
import app.criptopanel.core.network.AppError
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

    suspend fun coin(id: String): Outcome<CoinDetail> = withResolvedId(id) { geckoId -> apiCall { api.coin(geckoId).toDomain() } }

    suspend fun chart(id: String, range: ChartRange, force: Boolean = false): Outcome<List<Double>> =
        withResolvedId(id) { geckoId ->
            synchronized(chartCaches) { chartCaches.getOrPut(geckoId to range) { TtlCache(now) } }
                .get(force) { api.marketChart(geckoId, range.days).toPrices() }
        }

    suspend fun search(q: String): Outcome<List<SearchCoin>> = apiCall { api.search(q).coins.map { it.toDomain() } }

    /** CoinLore ids (fallback Top 100) that CoinGecko doesn't know, mapped to the CoinGecko id found by search. */
    private val resolvedIds = mutableMapOf<String, String>()

    /** Runs [call] with the CoinGecko id for [id]; on a 404 looks the coin up by name once and retries. */
    private suspend fun <T> withResolvedId(id: String, call: suspend (String) -> Outcome<T>): Outcome<T> {
        val known = synchronized(resolvedIds) { resolvedIds[id] }
        val first = call(known ?: id)
        if (known != null || first != Outcome.Err(AppError.Http(404))) return first
        val found = (search(id.replace('-', ' ')) as? Outcome.Ok)?.value?.firstOrNull()?.id ?: return first
        synchronized(resolvedIds) { resolvedIds[id] = found }
        return call(found)
    }
}
