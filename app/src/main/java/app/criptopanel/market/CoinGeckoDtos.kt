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
