package app.criptopanel.defi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query
import kotlin.math.abs

/** DefiLlama stablecoins API (stablecoins.llama.fi). */
interface StablecoinsApi {
    @GET("stablecoins")
    suspend fun stablecoins(@Query("includePrices") includePrices: Boolean = true): StablecoinsDto

    @GET("stablecoincharts/all")
    suspend fun totalChart(): List<StablecoinChartPointDto>
}

@Serializable
data class StablecoinsDto(val peggedAssets: List<PeggedAssetDto> = emptyList())

@Serializable
data class PeggedAssetDto(
    val id: String,
    val name: String = "",
    val symbol: String = "",
    @SerialName("gecko_id") val geckoId: String? = null,
    val pegType: String = "",
    val pegMechanism: String? = null,
    /** Units in circulation, keyed by peg type ("peggedUSD", "peggedEUR"…). */
    val circulating: Map<String, Double?> = emptyMap(),
    val circulatingPrevDay: Map<String, Double?> = emptyMap(),
    val chains: List<String> = emptyList(),
    val price: Double? = null,
)

@Serializable
data class StablecoinChartPointDto(
    /** Epoch seconds, sent as a string. */
    val date: String,
    /** USD value in circulation per peg type. */
    val totalCirculatingUSD: Map<String, Double?> = emptyMap(),
)

data class Stablecoin(
    val id: String,
    val name: String,
    val symbol: String,
    val geckoId: String?,
    val mechanism: String,
    val marketCap: Double,
    val change24h: Double?,
    val chainCount: Int,
    val price: Double?,
    /** A USD stablecoin trading more than 1 % away from 1 $. */
    val offPeg: Boolean,
)

data class StablecoinOverview(
    val totalCap: Double,
    val change7d: Double?,
    /** Last year of total USD in circulation, oldest first. */
    val history: List<TvlPoint>,
    val coins: List<Stablecoin>,
)

private const val USD_PEG = "peggedUSD"
private const val PEG_TOLERANCE = 0.01

private fun mechanismLabel(raw: String?): String = when (raw) {
    "fiat-backed" -> "Fiat"
    "crypto-backed" -> "Cripto"
    "algorithmic" -> "Algorítmica"
    null -> "—"
    else -> raw
}

/** Stablecoins whose USD market cap can be worked out, largest first. */
fun StablecoinsDto.toStablecoins(): List<Stablecoin> =
    peggedAssets
        .mapNotNull { a ->
            val units = a.circulating[a.pegType] ?: return@mapNotNull null
            // A USD stablecoin without a price is valued at par; other pegs need a price to convert.
            val usdPrice = a.price ?: if (a.pegType == USD_PEG) 1.0 else return@mapNotNull null
            val prevUnits = a.circulatingPrevDay[a.pegType]
            Stablecoin(
                id = a.id,
                name = a.name,
                symbol = a.symbol,
                geckoId = a.geckoId,
                mechanism = mechanismLabel(a.pegMechanism),
                marketCap = units * usdPrice,
                change24h = prevUnits?.takeIf { it > 0 }?.let { (units / it - 1) * 100 },
                chainCount = a.chains.size,
                price = a.price,
                offPeg = a.pegType == USD_PEG && a.price != null && abs(a.price - 1) > PEG_TOLERANCE,
            )
        }
        .filter { it.marketCap > 0 }
        .sortedByDescending { it.marketCap }

/** Total over all peg types for each day, oldest first. */
fun List<StablecoinChartPointDto>.toTotals(): List<TvlPoint> =
    mapNotNull { p -> p.date.toLongOrNull()?.let { TvlPoint(it, p.totalCirculatingUSD.values.sumOf { v -> v ?: 0.0 }) } }
        .sortedBy { it.epochSeconds }

fun stablecoinOverview(totals: List<TvlPoint>, coins: List<Stablecoin>): StablecoinOverview {
    val last = totals.lastOrNull()?.tvl ?: coins.sumOf { it.marketCap }
    val weekAgo = totals.getOrNull(totals.size - 8)?.tvl?.takeIf { it > 0 }
    return StablecoinOverview(
        totalCap = last,
        change7d = weekAgo?.let { (last / it - 1) * 100 },
        history = totals.takeLast(365),
        coins = coins,
    )
}
