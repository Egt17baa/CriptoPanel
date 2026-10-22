package app.criptopanel.market

import app.criptopanel.decodeFixture

/** In-memory CoinGeckoApi serving fixtures; `failWith` makes every call throw until cleared. */
class FakeCoinGeckoApi : CoinGeckoApi {
    var failure: Throwable? = null
    var calls = 0
    val chartDays = mutableListOf<String>()
    val queries = mutableListOf<String>()
    val categories = mutableListOf<String?>()
    val idLists = mutableListOf<String?>()
    var searchDelayMs = 0L

    fun failWith(t: Throwable?) { failure = t }

    private fun <T> serve(value: () -> T): T {
        calls++
        failure?.let { throw it }
        return value()
    }

    override suspend fun markets(
        vs: String, order: String, perPage: Int, page: Int, sparkline: Boolean, changes: String, category: String?, ids: String?,
    ): List<CoinMarketDto> = serve { categories += category; idLists += ids; decodeFixture("cg_markets.json") }

    override suspend fun coin(id: String, l: Boolean, t: Boolean, c: Boolean, d: Boolean): CoinDetailDto =
        serve { decodeFixture("cg_coin_bitcoin.json") }

    override suspend fun marketChart(id: String, days: String, vs: String): MarketChartDto =
        serve { chartDays += days; decodeFixture("cg_market_chart.json") }

    override suspend fun search(q: String): SearchDto {
        queries += q
        kotlinx.coroutines.delay(searchDelayMs)
        return serve { decodeFixture("cg_search.json") }
    }

    override suspend fun trending(): TrendingDto = serve { decodeFixture("cg_trending.json") }

    override suspend fun categories(): List<CategoryDto> = serve { decodeFixture("cg_categories.json") }

    override suspend fun exchanges(perPage: Int): List<ExchangeDto> = serve { decodeFixture("cg_exchanges.json") }

    val historyDates = mutableListOf<String>()
    /** Price the fake reports for any past day; null makes history() fail like the free API outside 365 days. */
    var historyPrice: Double? = 50_000.0

    override suspend fun history(id: String, date: String, l: Boolean): CoinHistoryDto = serve {
        historyDates += date
        val p = historyPrice ?: throw retrofit2.HttpException(retrofit2.Response.error<Any>(401, okhttp3.ResponseBody.create(null, "")))
        CoinHistoryDto(MarketDataDto(currentPrice = mapOf("usd" to p)))
    }
}
