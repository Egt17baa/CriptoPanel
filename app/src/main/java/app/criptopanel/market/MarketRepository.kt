package app.criptopanel.market

import app.criptopanel.coins.ChartRange
import app.criptopanel.coins.CoinDetail
import app.criptopanel.coins.CoinMarket
import app.criptopanel.coins.toPricePoints
import app.criptopanel.coins.toPrices
import app.criptopanel.portfolio.PricePoint
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
    private val pointCaches = mutableMapOf<Pair<String, ChartRange>, TtlCache<List<PricePoint>>>()
    private val idsCaches = mutableMapOf<String, TtlCache<List<CoinMarket>>>()

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

    /** Prices for exactly these coins (portfolio, favourites) in one request; none for an empty list. */
    suspend fun marketsByIds(ids: Collection<String>, force: Boolean = false): Outcome<List<CoinMarket>> {
        if (ids.isEmpty()) return Outcome.Ok(emptyList())
        val key = ids.distinct().sorted().joinToString(",")
        return synchronized(idsCaches) { idsCaches.getOrPut(key) { TtlCache(now) } }
            .get(force) { api.markets(ids = key, perPage = 250, sparkline = true).map { it.toDomain() } }
    }

    /** Like [chart] but keeping each price's time, for the portfolio value history. */
    suspend fun pricePoints(id: String, range: ChartRange, force: Boolean = false): Outcome<List<PricePoint>> =
        withResolvedId(id) { geckoId ->
            synchronized(pointCaches) { pointCaches.getOrPut(geckoId to range) { TtlCache(now) } }
                .get(force) { api.marketChart(geckoId, range.days).toPricePoints() }
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
