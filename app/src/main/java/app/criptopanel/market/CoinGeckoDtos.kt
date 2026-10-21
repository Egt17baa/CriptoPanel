package app.criptopanel.market

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GlobalEnvelopeDto(val data: GlobalDataDto)

@Serializable
data class GlobalDataDto(
    @SerialName("total_market_cap") val totalMarketCap: Map<String, Double> = emptyMap(),
    @SerialName("total_volume") val totalVolume: Map<String, Double> = emptyMap(),
    @SerialName("market_cap_percentage") val marketCapPercentage: Map<String, Double> = emptyMap(),
    @SerialName("market_cap_change_percentage_24h_usd") val marketCapChange24hUsd: Double? = null,
)

@Serializable
data class CoinMarketDto(
    val id: String,
    val symbol: String = "",
    val name: String = "",
    val image: String? = null,
    @SerialName("current_price") val currentPrice: Double? = null,
    @SerialName("market_cap") val marketCap: Double? = null,
    @SerialName("market_cap_rank") val marketCapRank: Int? = null,
    @SerialName("total_volume") val totalVolume: Double? = null,
    @SerialName("price_change_percentage_1h_in_currency") val change1h: Double? = null,
    @SerialName("price_change_percentage_24h_in_currency") val change24h: Double? = null,
    @SerialName("price_change_percentage_7d_in_currency") val change7d: Double? = null,
    @SerialName("sparkline_in_7d") val sparkline: SparklineDto? = null,
)

@Serializable
data class SparklineDto(val price: List<Double> = emptyList())
