package app.criptopanel

import app.criptopanel.alerts.AlertMonitor
import app.criptopanel.alerts.AlertNotifier
import app.criptopanel.alerts.AlertStore
import app.criptopanel.alerts.BINANCE_REST_URL
import app.criptopanel.alerts.BinanceApi
import app.criptopanel.alerts.BinanceHistoryApi
import app.criptopanel.alerts.OkHttpPriceStream
import app.criptopanel.core.format.Money
import app.criptopanel.core.network.ALTERNATIVE_BASE_URL
import app.criptopanel.core.network.FRANKFURTER_BASE_URL
import app.criptopanel.core.network.FrankfurterApi
import app.criptopanel.core.network.FxRepository
import app.criptopanel.core.network.Outcome
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
import app.criptopanel.portfolio.HistoricalPrices
import app.criptopanel.portfolio.PortfolioStore
import java.io.File
import java.time.LocalDate

/** Also named in res/xml/backup_rules.xml and data_extraction_rules.xml. */
const val PORTFOLIO_FILE = "portfolio.json"
const val ALERTS_FILE = "alerts.json"

/** Manual dependency graph, one per process. */
class AppContainer(demoKey: String, filesDir: File, notifier: AlertNotifier) {
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

    val fx = FxRepository(retrofit(FRANKFURTER_BASE_URL, client).create(FrankfurterApi::class.java))

    /** Display-currency units per dollar on [date]: 1 in USD; the ECB rate of that day in EUR (today's if unknown). */
    suspend fun displayPerUsdOn(date: LocalDate): Double {
        val c = Money.current
        if (c.code != "EUR") return 1.0
        return (fx.eurPerUsdOn(date, date) as? Outcome.Ok)?.value?.invoke(date) ?: c.perUsd
    }

    /** Per-day display rates for a span, or null when showing USD (or the rates cannot be fetched). */
    suspend fun displayRates(from: LocalDate, to: LocalDate): ((LocalDate) -> Double)? {
        if (Money.current.code != "EUR") return null
        return (fx.eurPerUsdOn(from, to) as? Outcome.Ok)?.value
    }

    /** Prices on past days for the operation form. */
    val historicalPrices = HistoricalPrices(coinGecko, retrofit(BINANCE_REST_URL, client).create(BinanceHistoryApi::class.java))

    val alertStore = AlertStore(File(filesDir, ALERTS_FILE))

    /** One per process: the service runs it, the UI reads its status and live prices. */
    val alertMonitor = AlertMonitor(
        alertStore,
        portfolioStore,
        marketRepository,
        retrofit(BINANCE_REST_URL, client).create(BinanceApi::class.java),
        OkHttpPriceStream(client),
        notifier,
    )

    val defiRepository = DefiRepository(defiLlama)

    val defiExtras = DefiExtrasRepository(
        defiLlama,
        retrofit(STABLECOINS_BASE_URL, client).create(StablecoinsApi::class.java),
        retrofit(YIELDS_BASE_URL, client).create(YieldsApi::class.java),
    )
}
