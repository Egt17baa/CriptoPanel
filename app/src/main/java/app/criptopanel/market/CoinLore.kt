package app.criptopanel.market

import app.criptopanel.coins.CoinMarket
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive
import retrofit2.http.GET
import retrofit2.http.Query

/** CoinLore: free, keyless, no strict rate limit. Numbers often arrive as strings, sometimes empty. */
interface CoinLoreApi {
    @GET("global/")
    suspend fun global(): List<CoinLoreGlobalDto>

    @GET("tickers/")
    suspend fun tickers(@Query("start") start: Int = 0, @Query("limit") limit: Int = 100): CoinLoreTickersDto
}

@Serializable
data class CoinLoreGlobalDto(
    @SerialName("total_mcap") val totalMcap: Double? = null,
    @SerialName("total_volume") val totalVolume: Double? = null,
    @SerialName("btc_d") val btcD: JsonElement? = null,
    @SerialName("eth_d") val ethD: JsonElement? = null,
    @SerialName("mcap_change") val mcapChange: JsonElement? = null,
)

@Serializable
data class CoinLoreTickersDto(val data: List<CoinLoreTickerDto> = emptyList())

@Serializable
data class CoinLoreTickerDto(
    val nameid: String,
    val symbol: String = "",
    val name: String = "",
    val rank: Int? = null,
    @SerialName("price_usd") val priceUsd: JsonElement? = null,
    @SerialName("percent_change_1h") val change1h: JsonElement? = null,
    @SerialName("percent_change_24h") val change24h: JsonElement? = null,
    @SerialName("percent_change_7d") val change7d: JsonElement? = null,
    @SerialName("market_cap_usd") val marketCapUsd: JsonElement? = null,
    val volume24: JsonElement? = null,
)

/** A JSON number or numeric string as Double; null, "" or anything else gives null. */
fun JsonElement?.asDouble(): Double? = (this as? JsonPrimitive)?.content?.toDoubleOrNull()

/** CoinLore's nameid usually equals the CoinGecko id, so detail screens keep working. No logos or sparklines. */
fun CoinLoreTickerDto.toDomain() = CoinMarket(
    id = nameid,
    rank = rank,
    symbol = symbol.uppercase(),
    name = name,
    imageUrl = null,
    price = priceUsd.asDouble(),
    marketCap = marketCapUsd.asDouble(),
    volume24h = volume24.asDouble(),
    change1h = change1h.asDouble(),
    change24h = change24h.asDouble(),
    change7d = change7d.asDouble(),
    sparkline7d = emptyList(),
)
