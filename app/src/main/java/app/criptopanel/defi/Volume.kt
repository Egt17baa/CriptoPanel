package app.criptopanel.defi

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** DefiLlama /overview endpoints: what is measured and how it is requested. */
enum class VolumeKind(val label: String, val path: String, val dataType: String?) {
    DEX("Volumen DEX", "dexs", null),
    FEES("Comisiones", "fees", "dailyFees"),
    REVENUE("Ingresos", "fees", "dailyRevenue"),
}

@Serializable
data class VolumeOverviewDto(
    val allChains: List<String> = emptyList(),
    val total24h: Double? = null,
    @SerialName("change_1d") val change1d: Double? = null,
    @SerialName("change_7d") val change7d: Double? = null,
    @SerialName("change_1m") val change1m: Double? = null,
    val protocols: List<VolumeProtocolDto> = emptyList(),
)

@Serializable
data class VolumeProtocolDto(
    val slug: String? = null,
    val name: String = "",
    val displayName: String? = null,
    val logo: String? = null,
    val category: String? = null,
    val chains: List<String> = emptyList(),
    val total24h: Double? = null,
    @SerialName("change_1d") val change1d: Double? = null,
)

data class VolumeProtocol(
    val slug: String?,
    val name: String,
    val logoUrl: String?,
    val chains: List<String>,
    val total24h: Double,
    val change1d: Double?,
)

data class VolumeOverview(
    val total24h: Double?,
    val change1d: Double?,
    val change7d: Double?,
    val change1m: Double?,
    /** Chains to filter by, largest first. */
    val chains: List<String>,
    val protocols: List<VolumeProtocol>,
)

const val VOLUME_CHAINS = 15
const val VOLUME_PROTOCOLS = 100

fun VolumeOverviewDto.toDomain(): VolumeOverview = VolumeOverview(
    total24h = total24h,
    change1d = change1d,
    change7d = change7d,
    change1m = change1m,
    chains = allChains.take(VOLUME_CHAINS),
    protocols = protocols
        .filter { (it.total24h ?: 0.0) > 0 }
        .sortedByDescending { it.total24h }
        .take(VOLUME_PROTOCOLS)
        .map { VolumeProtocol(it.slug, it.displayName ?: it.name, it.logo, it.chains, it.total24h!!, it.change1d) },
)

fun protocolUrl(slug: String): String = "https://defillama.com/protocol/$slug"
