package app.criptopanel.market

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrendingDto(val coins: List<TrendingItemWrapperDto> = emptyList())

@Serializable
data class TrendingItemWrapperDto(val item: TrendingItemDto)

@Serializable
data class TrendingItemDto(
    val id: String,
    val name: String = "",
    val symbol: String = "",
    @SerialName("market_cap_rank") val marketCapRank: Int? = null,
    val small: String? = null,
    val thumb: String? = null,
    val data: TrendingDataDto? = null,
)

@Serializable
data class TrendingDataDto(
    val price: Double? = null,
    @SerialName("price_change_percentage_24h") val change24h: Map<String, Double?> = emptyMap(),
)

data class TrendingCoin(
    val id: String,
    val name: String,
    val symbol: String,
    val rank: Int?,
    val imageUrl: String?,
    val price: Double?,
    val change24h: Double?,
)

fun TrendingDto.toDomain(): List<TrendingCoin> = coins.map { (item) ->
    TrendingCoin(
        id = item.id,
        name = item.name,
        symbol = item.symbol.uppercase(),
        rank = item.marketCapRank,
        imageUrl = item.small ?: item.thumb,
        price = item.data?.price,
        change24h = item.data?.change24h?.get("usd"),
    )
}
