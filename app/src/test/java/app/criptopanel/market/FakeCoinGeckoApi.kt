package app.criptopanel.market

import app.criptopanel.decodeFixture

/** In-memory CoinGeckoApi serving fixtures; `failWith` makes every call throw until cleared. */
class FakeCoinGeckoApi : CoinGeckoApi {
    var failure: Throwable? = null
    var calls = 0

    fun failWith(t: Throwable?) { failure = t }

    private fun <T> serve(value: () -> T): T {
        calls++
        failure?.let { throw it }
        return value()
    }

    override suspend fun global(): GlobalEnvelopeDto = serve { decodeFixture("cg_global.json") }

    override suspend fun markets(
        vs: String, order: String, perPage: Int, page: Int, sparkline: Boolean, changes: String,
    ): List<CoinMarketDto> = serve { decodeFixture("cg_markets.json") }
}
