package app.criptopanel.portfolio.binance

import app.criptopanel.alerts.BinanceHistoryApi
import app.criptopanel.alerts.USD_STABLECOINS
import app.criptopanel.core.network.FxRepository
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.network.apiCall
import app.criptopanel.market.CoinGeckoApi
import app.criptopanel.portfolio.HistoricalPrices
import app.criptopanel.portfolio.Operation
import kotlinx.serialization.json.jsonPrimitive
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Reads a Binance export and prices it with public data only (the file never leaves the phone): Binance daily
 * opens against USDT, CoinGecko for coins Binance does not list against USDT, ECB rates for euros.
 */
class BinanceImportService(
    private val gecko: CoinGeckoApi,
    private val binance: BinanceHistoryApi,
    private val fx: FxRepository,
    private val history: HistoricalPrices,
) {
    private var top: Map<String, CoinInfo>? = null
    private val resolved = mutableMapOf<String, CoinInfo?>()
    private val daily = mutableMapOf<String, MutableMap<LocalDate, Double>>()
    /** Days already asked for, per asset: a day missing inside them has no price on Binance (not listed yet). */
    private val covered = mutableMapOf<String, ClosedRange<LocalDate>>()
    private val noPair = mutableSetOf<String>()
    private var usdPerEur: ((LocalDate) -> Double)? = null

    suspend fun plan(bytes: ByteArray, existing: List<Operation>): ImportPlan {
        val parse = parseBinance(readSheet(bytes))
        val days = (parse.swaps.map { it.time } + parse.marketBuys.map { it.time } + parse.rewards.map { it.time }).map { it.toLocalDate() }
        if (days.isEmpty()) throw BinanceFormatException("El archivo no tiene operaciones que importar")
        val usesEur = parse.swaps.any { it.received.asset == "EUR" || it.spent.asset == "EUR" || it.fee?.asset == "EUR" }
        if (usesEur) {
            (fx.eurPerUsdOn(days.min(), days.max()) as? Outcome.Ok)?.value?.let { eurPerUsd -> usdPerEur = { d -> 1 / eurPerUsd(d) } }
        }
        return BinanceConverter(::resolve, ::usdOn).convert(parse, existing)
    }

    /** The CoinGecko coin for a Binance ticker: among the 250 largest first, then CoinGecko's search. */
    private suspend fun resolve(symbol: String): CoinInfo? {
        val s = symbol.uppercase()
        resolved[s]?.let { return it }
        if (s in resolved) return null
        if (top == null) {
            top = (apiCall { gecko.markets(perPage = 250, sparkline = false, changes = "24h") } as? Outcome.Ok)?.value
                ?.sortedBy { it.marketCapRank ?: Int.MAX_VALUE }
                ?.groupBy { it.symbol.uppercase() }
                ?.mapValues { (_, coins) -> coins.first().let { CoinInfo(it.id, it.symbol, it.name, it.image) } }
                .orEmpty()
        }
        val found = top?.get(s)
            ?: (apiCall { gecko.search(s) } as? Outcome.Ok)?.value?.coins
                ?.filter { it.symbol.equals(s, ignoreCase = true) }
                ?.minByOrNull { it.marketCapRank ?: Int.MAX_VALUE }
                ?.let { CoinInfo(it.id, it.symbol, it.name, it.thumb) }
        resolved[s] = found
        return found
    }

    /** USD per unit of [asset] on [day]: 1 for dollar stablecoins, ECB rate for euros, Binance or CoinGecko for coins. */
    private suspend fun usdOn(asset: String, day: LocalDate): Double? {
        val a = asset.uppercase()
        return when {
            a in USD_STABLECOINS || a == "USD" -> 1.0
            a == "EUR" -> usdPerEur?.invoke(day)
            isCash(a) -> null
            else -> binanceOpen(a, day) ?: resolve(a)?.let { history.priceOn(it.id, a, day, null)?.price }
        }
    }

    /** Daily opening price of ASSET/USDT on Binance, fetched up to 1000 days at a time. */
    private suspend fun binanceOpen(asset: String, day: LocalDate): Double? {
        if (asset in noPair) return null
        daily[asset]?.get(day)?.let { return it }
        if (covered[asset]?.contains(day) == true) return null
        val start = day.minusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val rows = when (val r = apiCall { binance.klines("${asset}USDT", start, "1d", 1000) }) {
            is Outcome.Ok -> r.value
            is Outcome.Err -> {
                noPair += asset
                return null
            }
        }
        val map = daily.getOrPut(asset) { mutableMapOf() }
        val last = rows.lastOrNull()?.getOrNull(0)?.jsonPrimitive?.content?.toLongOrNull()
            ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() } ?: day
        covered[asset] = day..maxOf(day, last)
        rows.forEach { row ->
            val open = row.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull()
            val time = row.getOrNull(0)?.jsonPrimitive?.content?.toLongOrNull()
            if (open != null && open > 0 && time != null) map[Instant.ofEpochMilli(time).atZone(ZoneOffset.UTC).toLocalDate()] = open
        }
        return map[day]
    }
}
