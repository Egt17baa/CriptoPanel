package app.criptopanel.market

import app.criptopanel.coins.CoinMarket
import app.criptopanel.coins.toDomain
import app.criptopanel.core.network.AppError
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.network.TtlCache

/** Slow-moving market extras; cached for 10 minutes to spare CoinGecko's free rate limit. */
const val INSIGHTS_TTL_MS = 10 * 60_000L

class MarketInsightsRepository(
    private val api: CoinGeckoApi,
    private val sentiment: SentimentApi,
    private val market: MarketRepository,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val fearGreedCache = TtlCache<FearGreed>(now, INSIGHTS_TTL_MS)
    private val trendingCache = TtlCache<List<TrendingCoin>>(now, INSIGHTS_TTL_MS)
    private val categoriesCache = TtlCache<List<Category>>(now, INSIGHTS_TTL_MS)
    private val exchangesCache = TtlCache<List<Exchange>>(now, INSIGHTS_TTL_MS)
    private val categoryCoinCaches = mutableMapOf<String, TtlCache<List<CoinMarket>>>()

    suspend fun fearGreed(force: Boolean = false): Outcome<FearGreed> =
        fearGreedCache.get(force) { checkNotNull(sentiment.fearGreed().toDomain()) { "empty fear & greed index" } }

    suspend fun trending(force: Boolean = false): Outcome<List<TrendingCoin>> =
        trendingCache.get(force) { api.trending().toDomain() }

    suspend fun categories(force: Boolean = false): Outcome<List<Category>> =
        categoriesCache.get(force) { api.categories().toCategories() }

    suspend fun exchanges(force: Boolean = false): Outcome<List<Exchange>> =
        exchangesCache.get(force) { api.exchanges().toExchanges(btcPrice()) }

    /** The sector (from the cached sector list) and its coins; fails if CoinGecko no longer lists the sector. */
    suspend fun categoryDetail(id: String, force: Boolean = false): Outcome<CategoryDetail> {
        val category = when (val all = categories()) {
            is Outcome.Ok -> all.value.firstOrNull { it.id == id } ?: return Outcome.Err(AppError.Http(404))
            is Outcome.Err -> return all
        }
        val coins = synchronized(categoryCoinCaches) { categoryCoinCaches.getOrPut(id) { TtlCache(now, INSIGHTS_TTL_MS) } }
            .get(force) { api.markets(category = id).map { it.toDomain() } }
        return when (coins) {
            is Outcome.Ok -> Outcome.Ok(CategoryDetail(category, coins.value))
            is Outcome.Err -> coins
        }
    }

    /** From the Top 100 (its own 60 s cache), so no extra request is made. */
    suspend fun movers(force: Boolean = false): Outcome<Movers> =
        when (val top = market.topCoins(force)) {
            is Outcome.Ok -> Outcome.Ok(movers(top.value))
            is Outcome.Err -> top
        }

    private suspend fun btcPrice(): Double? =
        (market.topCoins() as? Outcome.Ok)?.value?.firstOrNull { it.symbol == "BTC" }?.price
}
