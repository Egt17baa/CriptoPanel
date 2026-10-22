package app.criptopanel.defi

import kotlinx.serialization.Serializable
import retrofit2.http.GET

/** DefiLlama yields API (yields.llama.fi). The response is ~10 MB. */
interface YieldsApi {
    @GET("pools")
    suspend fun pools(): PoolsDto
}

@Serializable
data class PoolsDto(val data: List<PoolDto> = emptyList())

@Serializable
data class PoolDto(
    val pool: String,
    val chain: String = "",
    val project: String = "",
    val symbol: String = "",
    val tvlUsd: Double? = null,
    val apy: Double? = null,
    val apyBase: Double? = null,
    val apyReward: Double? = null,
    val stablecoin: Boolean = false,
    val ilRisk: String? = null,
    val outlier: Boolean = false,
)

data class Pool(
    val id: String,
    val project: String,
    val symbol: String,
    val chain: String,
    val apy: Double,
    val apyBase: Double?,
    val apyReward: Double?,
    val tvl: Double,
    val stablecoin: Boolean,
    /** Impermanent loss risk. */
    val ilRisk: Boolean,
)

enum class PoolSort(val label: String) { APY("APY"), TVL("TVL") }

enum class MinTvl(val label: String, val usd: Double) {
    M1("≥ 1 M$", 1e6), M10("≥ 10 M$", 1e7), M100("≥ 100 M$", 1e8),
}

const val MAX_APY = 1_000.0
const val MAX_POOLS = 100

/** Pools worth listing at all: not flagged as outliers, with a believable APY and at least 1 M$ TVL. */
fun PoolsDto.toPools(): List<Pool> =
    data
        .filter { p -> !p.outlier && p.apy != null && p.apy in 0.0..MAX_APY && (p.tvlUsd ?: 0.0) >= MinTvl.M1.usd }
        .map { p ->
            Pool(p.pool, p.project, p.symbol, p.chain, p.apy!!, p.apyBase, p.apyReward, p.tvlUsd!!, p.stablecoin, p.ilRisk == "yes")
        }

fun List<Pool>.filtered(stableOnly: Boolean, minTvl: MinTvl, sort: PoolSort): List<Pool> =
    filter { (!stableOnly || it.stablecoin) && it.tvl >= minTvl.usd }
        .let { pools -> if (sort == PoolSort.APY) pools.sortedByDescending { it.apy } else pools.sortedByDescending { it.tvl } }
        .take(MAX_POOLS)

fun poolUrl(id: String): String = "https://defillama.com/yields/pool/$id"
