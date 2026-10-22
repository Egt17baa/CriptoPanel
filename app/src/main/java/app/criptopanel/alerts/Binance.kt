package app.criptopanel.alerts

import app.criptopanel.core.network.appJson
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import kotlin.math.abs

const val BINANCE_REST_URL = "https://data-api.binance.vision/"
const val BINANCE_STREAM_URL = "wss://data-stream.binance.vision/stream?streams="

/** Binance's market-data-only REST API: public, no key. */
interface BinanceApi {
    /** Every pair's last price (~2 000 pairs, one request). */
    @GET("api/v3/ticker/price")
    suspend fun prices(): List<BinancePriceDto>
}

@Serializable
data class BinancePriceDto(val symbol: String, val price: String)

/** USD stablecoins have no XUSDT pair worth following; CoinGecko covers them. */
val USD_STABLECOINS = setOf("USDT", "USDC", "DAI", "FDUSD", "TUSD", "USDE", "PYUSD", "USDS", "BUSD", "USD1", "USDP", "GUSD", "FRAX", "LUSD")

/** Max difference between Binance's and CoinGecko's price for a pair to count as the same coin. */
const val SAME_COIN_TOLERANCE = 0.03

data class CoinRef(val coinId: String, val symbol: String, val price: Double?)

/**
 * Binance pair (e.g. "BTCUSDT") for each coin that trades there. A pair counts only if its price is within
 * [SAME_COIN_TOLERANCE] of CoinGecko's, so two coins sharing a ticker symbol are never mixed up.
 */
fun binancePairs(coins: List<CoinRef>, binance: List<BinancePriceDto>): Map<String, String> {
    val prices = binance.associate { it.symbol to it.price.toDoubleOrNull() }
    return coins.mapNotNull { c ->
        val symbol = c.symbol.uppercase()
        if (symbol in USD_STABLECOINS) return@mapNotNull null
        val pair = symbol + "USDT"
        val bp = prices[pair] ?: return@mapNotNull null
        val gp = c.price ?: return@mapNotNull null
        if (gp <= 0 || abs(bp / gp - 1) > SAME_COIN_TOLERANCE) return@mapNotNull null
        c.coinId to pair
    }.toMap()
}

@Serializable
data class StreamEnvelopeDto(val stream: String? = null, val data: MiniTickerDto? = null)

/** `<pair>@miniTicker`: `c` last price, `o` price 24 h ago. */
@Serializable
data class MiniTickerDto(val s: String? = null, val c: String? = null, val o: String? = null)

data class MiniTicker(val pair: String, val price: Double, val change24h: Double?)

/** Null for anything that is not a well-formed mini ticker (pings, errors, garbage). */
fun parseMiniTicker(text: String): MiniTicker? {
    val dto = try {
        appJson.decodeFromString<StreamEnvelopeDto>(text).data
    } catch (e: Exception) {
        null
    } ?: return null
    val pair = dto.s ?: return null
    val close = dto.c?.toDoubleOrNull()?.takeIf { it > 0 } ?: return null
    val open = dto.o?.toDoubleOrNull()?.takeIf { it > 0 }
    return MiniTicker(pair, close, open?.let { (close / it - 1) * 100 })
}

fun streamUrl(pairs: Collection<String>): String = BINANCE_STREAM_URL + pairs.sorted().joinToString("/") { "${it.lowercase()}@miniTicker" }
