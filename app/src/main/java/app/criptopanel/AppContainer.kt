package app.criptopanel

import app.criptopanel.core.network.COINGECKO_BASE_URL
import app.criptopanel.core.network.DEFILLAMA_BASE_URL
import app.criptopanel.core.network.okHttpClient
import app.criptopanel.core.network.retrofit
import app.criptopanel.defi.DefiLlamaApi
import app.criptopanel.defi.DefiRepository
import app.criptopanel.market.CoinGeckoApi
import app.criptopanel.market.MarketRepository

/** Manual dependency graph, one per process. */
class AppContainer(demoKey: String) {
    private val client = okHttpClient(demoKey)

    val marketRepository = MarketRepository(
        retrofit(COINGECKO_BASE_URL, client).create(CoinGeckoApi::class.java),
    )

    val defiRepository = DefiRepository(
        retrofit(DEFILLAMA_BASE_URL, client).create(DefiLlamaApi::class.java),
    )
}
