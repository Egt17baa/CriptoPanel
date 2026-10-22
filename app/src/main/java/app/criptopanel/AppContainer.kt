package app.criptopanel

import app.criptopanel.core.network.ALTERNATIVE_BASE_URL
import app.criptopanel.core.network.COINGECKO_BASE_URL
import app.criptopanel.core.network.COINLORE_BASE_URL
import app.criptopanel.core.network.DEFILLAMA_BASE_URL
import app.criptopanel.core.network.STABLECOINS_BASE_URL
import app.criptopanel.core.network.YIELDS_BASE_URL
import app.criptopanel.core.network.okHttpClient
import app.criptopanel.core.network.retrofit
import app.criptopanel.defi.DefiExtrasRepository
import app.criptopanel.defi.DefiLlamaApi
import app.criptopanel.defi.DefiRepository
import app.criptopanel.defi.StablecoinsApi
import app.criptopanel.defi.YieldsApi
import app.criptopanel.market.CoinGeckoApi
import app.criptopanel.market.CoinLoreApi
import app.criptopanel.market.MarketInsightsRepository
import app.criptopanel.market.MarketRepository
import app.criptopanel.market.SentimentApi

/** Manual dependency graph, one per process. */
class AppContainer(demoKey: String) {
    private val client = okHttpClient(demoKey)
    private val coinGecko = retrofit(COINGECKO_BASE_URL, client).create(CoinGeckoApi::class.java)
    private val defiLlama = retrofit(DEFILLAMA_BASE_URL, client).create(DefiLlamaApi::class.java)

    val marketRepository = MarketRepository(
        coinGecko,
        retrofit(COINLORE_BASE_URL, client).create(CoinLoreApi::class.java),
    )

    val marketInsights = MarketInsightsRepository(
        coinGecko,
        retrofit(ALTERNATIVE_BASE_URL, client).create(SentimentApi::class.java),
        marketRepository,
    )

    val defiRepository = DefiRepository(defiLlama)

    val defiExtras = DefiExtrasRepository(
        defiLlama,
        retrofit(STABLECOINS_BASE_URL, client).create(StablecoinsApi::class.java),
        retrofit(YIELDS_BASE_URL, client).create(YieldsApi::class.java),
    )
}
