package app.criptopanel.portfolio

import app.criptopanel.alerts.BinanceHistoryApi
import app.criptopanel.alerts.SAME_COIN_TOLERANCE
import app.criptopanel.alerts.USD_STABLECOINS
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.network.apiCall
import app.criptopanel.market.CoinGeckoApi
import kotlinx.serialization.json.jsonPrimitive
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlin.math.abs

data class DatedPrice(val price: Double, val source: String)

private val geckoDate = DateTimeFormatter.ofPattern("dd-MM-yyyy")

/**
 * What a coin cost on a past day, to fill in the operation form: CoinGecko first (last 365 days on the free API),
 * then the day's opening price on Binance (any date since the pair listed). Both are the price at 00:00 UTC.
 */
class HistoricalPrices(private val gecko: CoinGeckoApi, private val binance: BinanceHistoryApi) {
    private val cache = mutableMapOf<Pair<String, LocalDate>, DatedPrice>()

    suspend fun priceOn(coinId: String, symbol: String, date: LocalDate, currentPrice: Double?): DatedPrice? {
        synchronized(cache) { cache[coinId to date] }?.let { return it }
        val found = fromGecko(coinId, date) ?: fromBinance(symbol, date, currentPrice) ?: return null
        synchronized(cache) { cache[coinId to date] = found }
        return found
    }

    private suspend fun fromGecko(coinId: String, date: LocalDate): DatedPrice? =
        (apiCall { gecko.history(coinId, date.format(geckoDate)) } as? Outcome.Ok)
            ?.value?.marketData?.currentPrice?.get("usd")
            ?.takeIf { it > 0 }
            ?.let { DatedPrice(it, "CoinGecko") }

    private suspend fun fromBinance(symbol: String, date: LocalDate, currentPrice: Double?): DatedPrice? {
        val s = symbol.uppercase()
        if (s in USD_STABLECOINS || currentPrice == null) return null
        val pair = s + "USDT"
        // Same check as the alerts: a pair priced differently today is another coin with the same ticker.
        val now = (apiCall { binance.price(pair) } as? Outcome.Ok)?.value?.price?.toDoubleOrNull() ?: return null
        if (abs(now / currentPrice - 1) > SAME_COIN_TOLERANCE) return null
        val start = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val row = (apiCall { binance.klines(pair, start) } as? Outcome.Ok)?.value?.firstOrNull() ?: return null
        val openTime = row.getOrNull(0)?.jsonPrimitive?.content?.toLongOrNull()
        if (openTime != start) return null // The pair did not trade yet that day.
        return row.getOrNull(1)?.jsonPrimitive?.content?.toDoubleOrNull()?.takeIf { it > 0 }?.let { DatedPrice(it, "Binance") }
    }
}
