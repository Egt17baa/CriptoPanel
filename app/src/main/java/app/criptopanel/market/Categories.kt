package app.criptopanel.market

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class CategoryDto(
    val id: String,
    val name: String = "",
    @SerialName("market_cap") val marketCap: Double? = null,
    @SerialName("market_cap_change_24h") val change24h: Double? = null,
    @SerialName("volume_24h") val volume24h: Double? = null,
    @SerialName("top_3_coins") val top3Coins: List<String> = emptyList(),
)

/** A CoinGecko category (sector): AI, memes, layer 2… */
data class Category(
    val id: String,
    val name: String,
    val marketCap: Double,
    val change24h: Double?,
    val volume24h: Double?,
    val topCoinImages: List<String>,
)

enum class CategorySort(val label: String) { CAP("Capitalización"), CHANGE("Cambio 24 h") }

/** Sectors with a market cap, largest first. */
fun List<CategoryDto>.toCategories(): List<Category> =
    filter { (it.marketCap ?: 0.0) > 0 }
        .map { Category(it.id, it.name, it.marketCap!!, it.change24h, it.volume24h, it.top3Coins) }
        .sortedByDescending { it.marketCap }

fun List<Category>.sortedBy(sort: CategorySort): List<Category> = when (sort) {
    CategorySort.CAP -> sortedByDescending { it.marketCap }
    // Unknown change goes last.
    CategorySort.CHANGE -> sortedByDescending { it.change24h ?: Double.NEGATIVE_INFINITY }
}
