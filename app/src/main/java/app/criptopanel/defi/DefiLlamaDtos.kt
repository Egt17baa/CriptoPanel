package app.criptopanel.defi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChainDto(
    val name: String,
    val tvl: Double? = null,
    val tokenSymbol: String? = null,
    @SerialName("gecko_id") val geckoId: String? = null,
)

@Serializable
data class TvlPointDto(val date: Long, val tvl: Double = 0.0)

@Serializable
data class ProtocolDto(
    val slug: String,
    val name: String = "",
    val logo: String? = null,
    val category: String? = null,
    val tvl: Double? = null,
    @SerialName("change_1d") val change1d: Double? = null,
    val chains: List<String> = emptyList(),
    val chainTvls: Map<String, Double> = emptyMap(),
)
