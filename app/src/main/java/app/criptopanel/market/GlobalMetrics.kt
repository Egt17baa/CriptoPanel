package app.criptopanel.market

data class GlobalMetrics(
    val totalCap: Double,
    val total2: Double,
    val total3: Double,
    val btcDominance: Double,
    val ethDominance: Double,
    val volume24h: Double,
    val capChange24hPct: Double?,
)

/** TOTAL2 = TOTAL − BTC cap; TOTAL3 = TOTAL − BTC cap − ETH cap (TradingView definitions). */
fun GlobalDataDto.toMetrics(): GlobalMetrics {
    val total = totalMarketCap["usd"] ?: 0.0
    val btcPct = marketCapPercentage["btc"] ?: 0.0
    val ethPct = marketCapPercentage["eth"] ?: 0.0
    val btcCap = total * btcPct / 100
    val ethCap = total * ethPct / 100
    return GlobalMetrics(
        totalCap = total,
        total2 = total - btcCap,
        total3 = total - btcCap - ethCap,
        btcDominance = btcPct,
        ethDominance = ethPct,
        volume24h = totalVolume["usd"] ?: 0.0,
        capChange24hPct = marketCapChange24hUsd,
    )
}
