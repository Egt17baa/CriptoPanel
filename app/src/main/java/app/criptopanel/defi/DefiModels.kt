package app.criptopanel.defi

data class ChainTvl(
    val name: String,
    val tvl: Double,
    /** Fraction of the summed TVL of all listed chains, 0..1. */
    val share: Double,
    val tokenSymbol: String?,
    val geckoId: String?,
)

data class TvlPoint(val epochSeconds: Long, val tvl: Double)

data class Protocol(
    val slug: String,
    val name: String,
    val logoUrl: String?,
    val category: String?,
    val tvl: Double,
    val change1d: Double?,
    val chains: List<String>,
    /** TVL per chain name, without DefiLlama's derived buckets (staking, borrowed, …). */
    val chainTvls: Map<String, Double>,
)

private val derivedTvlKeys = setOf("borrowed", "staking", "pool2", "doublecounted", "liquidstaking", "vesting", "offers", "treasury")

fun List<ChainDto>.toChainTvls(): List<ChainTvl> {
    val positive = filter { (it.tvl ?: 0.0) > 0 }
    val total = positive.sumOf { it.tvl!! }
    return positive
        .map { ChainTvl(it.name, it.tvl!!, it.tvl / total, it.tokenSymbol, it.geckoId) }
        .sortedByDescending { it.tvl }
}

fun List<ProtocolDto>.toProtocols(): List<Protocol> =
    filter { it.category != "CEX" && it.tvl != null }
        .map { p ->
            Protocol(
                slug = p.slug,
                name = p.name,
                logoUrl = p.logo,
                category = p.category,
                tvl = p.tvl!!,
                change1d = p.change1d,
                chains = p.chains,
                chainTvls = p.chainTvls.filterKeys { '-' !in it && it.lowercase() !in derivedTvlKeys },
            )
        }
        .sortedByDescending { it.tvl }

fun List<TvlPointDto>.toPoints(): List<TvlPoint> = map { TvlPoint(it.date, it.tvl) }
