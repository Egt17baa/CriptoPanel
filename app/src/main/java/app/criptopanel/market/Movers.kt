package app.criptopanel.market

import app.criptopanel.coins.CoinMarket

/** Biggest 24 h gainers and losers. */
data class Movers(val gainers: List<CoinMarket>, val losers: List<CoinMarket>)

/** Coins without a 24 h change are left out; ties keep the market-cap order. */
fun movers(coins: List<CoinMarket>, n: Int = coins.size): Movers {
    val known = coins.filter { it.change24h != null }
    return Movers(
        gainers = known.filter { it.change24h!! > 0 }.sortedByDescending { it.change24h }.take(n),
        losers = known.filter { it.change24h!! < 0 }.sortedBy { it.change24h }.take(n),
    )
}
