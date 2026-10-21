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
    // Missing figures are an error (last good data stays on screen), never a fake 0,00 $.
    val total = requireNotNull(totalMcap) { "CoinLore global without total_mcap" }
    val btcPct = requireNotNull(btcD.asDouble()) { "CoinLore global without btc_d" }
    val ethPct = requireNotNull(ethD.asDouble()) { "CoinLore global without eth_d" }
    return GlobalMetrics(
        totalCap = total,
        total2 = total * (1 - btcPct / 100),
        total3 = total * (1 - btcPct / 100 - ethPct / 100),
        btcDominance = btcPct,
        ethDominance = ethPct,
        volume24h = requireNotNull(totalVolume) { "CoinLore global without total_volume" },
        capChange24hPct = mcapChange.asDouble(),
    )
}
