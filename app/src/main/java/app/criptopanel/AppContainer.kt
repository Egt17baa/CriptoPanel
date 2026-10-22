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
import app.criptopanel.portfolio.PortfolioStore
import java.io.File

/** Also named in res/xml/backup_rules.xml and data_extraction_rules.xml. */
const val PORTFOLIO_FILE = "portfolio.json"

/** Manual dependency graph, one per process. */
class AppContainer(demoKey: String, filesDir: File) {
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

    /** Portfolio and favourites; the only data the app keeps (and backs up). */
    val portfolioStore = PortfolioStore(File(filesDir, PORTFOLIO_FILE))

    val defiRepository = DefiRepository(defiLlama)

    val defiExtras = DefiExtrasRepository(
        defiLlama,
        retrofit(STABLECOINS_BASE_URL, client).create(StablecoinsApi::class.java),
        retrofit(YIELDS_BASE_URL, client).create(YieldsApi::class.java),
    )
}
