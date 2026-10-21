package app.criptopanel.market

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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

@Serializable
data class CoinDetailDto(
    val id: String,
    val symbol: String = "",
    val name: String = "",
    @SerialName("market_cap_rank") val marketCapRank: Int? = null,
    val image: ImageDto? = null,
    val description: Map<String, String?> = emptyMap(),
    @SerialName("market_data") val marketData: MarketDataDto? = null,
)

@Serializable
data class ImageDto(val thumb: String? = null, val small: String? = null, val large: String? = null)

@Serializable
data class MarketDataDto(
    @SerialName("current_price") val currentPrice: Map<String, Double?> = emptyMap(),
    @SerialName("market_cap") val marketCap: Map<String, Double?> = emptyMap(),
    @SerialName("total_volume") val totalVolume: Map<String, Double?> = emptyMap(),
    @SerialName("high_24h") val high24h: Map<String, Double?> = emptyMap(),
    @SerialName("low_24h") val low24h: Map<String, Double?> = emptyMap(),
    val ath: Map<String, Double?> = emptyMap(),
    @SerialName("circulating_supply") val circulatingSupply: Double? = null,
    @SerialName("total_supply") val totalSupply: Double? = null,
    @SerialName("max_supply") val maxSupply: Double? = null,
)

/** Each entry is [timestamp ms, value]. */
@Serializable
data class MarketChartDto(val prices: List<List<Double>> = emptyList())

@Serializable
data class SearchDto(val coins: List<SearchCoinDto> = emptyList())

@Serializable
data class SearchCoinDto(
    val id: String,
    val name: String = "",
    val symbol: String = "",
    val thumb: String? = null,
    @SerialName("market_cap_rank") val marketCapRank: Int? = null,
)
