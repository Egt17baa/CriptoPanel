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
fun CoinLoreGlobalDto.toMetrics(): GlobalMetrics {
    val total = totalMcap ?: 0.0
    val btcPct = btcD.asDouble() ?: 0.0
    val ethPct = ethD.asDouble() ?: 0.0
    return GlobalMetrics(
        totalCap = total,
        total2 = total * (1 - btcPct / 100),
        total3 = total * (1 - btcPct / 100 - ethPct / 100),
        btcDominance = btcPct,
        ethDominance = ethPct,
        volume24h = totalVolume ?: 0.0,
        capChange24hPct = mcapChange.asDouble(),
    )
}
