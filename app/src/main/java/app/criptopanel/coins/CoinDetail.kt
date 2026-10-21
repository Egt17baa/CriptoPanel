package app.criptopanel.coins

import app.criptopanel.market.CoinDetailDto
import app.criptopanel.market.MarketChartDto

data class CoinDetail(
    val id: String,
    val name: String,
    val symbol: String,
    val imageUrl: String?,
    val rank: Int?,
    val price: Double?,
    val marketCap: Double?,
    val volume24h: Double?,
    val high24h: Double?,
    val low24h: Double?,
    val ath: Double?,
    val circulatingSupply: Double?,
    val totalSupply: Double?,
    val maxSupply: Double?,
    val description: String,
)

private val htmlTag = Regex("<[^>]+>")

fun CoinDetailDto.toDomain(): CoinDetail {
    val md = marketData
    return CoinDetail(
        id = id,
        name = name,
        symbol = symbol.uppercase(),
        imageUrl = image?.large ?: image?.small ?: image?.thumb,
        rank = marketCapRank,
        price = md?.currentPrice?.get("usd"),
        marketCap = md?.marketCap?.get("usd"),
        volume24h = md?.totalVolume?.get("usd"),
        high24h = md?.high24h?.get("usd"),
        low24h = md?.low24h?.get("usd"),
        ath = md?.ath?.get("usd"),
        circulatingSupply = md?.circulatingSupply,
        totalSupply = md?.totalSupply,
        maxSupply = md?.maxSupply,
        description = description["en"].orEmpty().replace(htmlTag, "").replace("\r\n", "\n").trim(),
    )
}

fun MarketChartDto.toPrices(): List<Double> = prices.mapNotNull { it.getOrNull(1) }
