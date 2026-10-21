package app.criptopanel.market

import app.criptopanel.decodeFixture

class FakeCoinLoreApi : CoinLoreApi {
    private var failure: Throwable? = null

    fun failWith(t: Throwable?) { failure = t }

    override suspend fun global(): List<CoinLoreGlobalDto> {
        failure?.let { throw it }
        return decodeFixture("cl_global.json")
    }

    override suspend fun tickers(start: Int, limit: Int): CoinLoreTickersDto {
        failure?.let { throw it }
        return decodeFixture("cl_tickers.json")
    }
}
