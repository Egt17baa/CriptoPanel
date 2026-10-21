package app.criptopanel.coins

import app.criptopanel.market.CoinMarketDto

data class CoinMarket(
    val id: String,
    val rank: Int?,
    val symbol: String,
    val name: String,
    val imageUrl: String?,
    val price: Double?,
    val marketCap: Double?,
    val volume24h: Double?,
    val change1h: Double?,
    val change24h: Double?,
    val change7d: Double?,
    val sparkline7d: List<Double>,
)

fun CoinMarketDto.toDomain(): CoinMarket = CoinMarket(
    id = id,
    rank = marketCapRank,
    symbol = symbol.uppercase(),
    name = name,
    imageUrl = image,
    price = currentPrice,
    marketCap = marketCap,
    volume24h = totalVolume,
    change1h = change1h,
    change24h = change24h,
    change7d = change7d,
    sparkline7d = sparkline?.price.orEmpty().filterNotNull(),
)
