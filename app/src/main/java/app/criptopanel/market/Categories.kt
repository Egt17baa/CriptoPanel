package app.criptopanel.market

import app.criptopanel.coins.CoinMarket
import app.criptopanel.coins.toDomain
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
    /** Short English description written by CoinGecko. */
    val content: String? = null,
)

/** A CoinGecko category (sector): AI, memes, layer 2… */
data class Category(
    val id: String,
    val name: String,
    val marketCap: Double,
    val change24h: Double?,
    val volume24h: Double?,
    val topCoinImages: List<String>,
    val description: String?,
)

/** A sector with the coins in it, largest first (up to 100, CoinGecko's page size). */
data class CategoryDetail(val category: Category, val coins: List<CoinMarket>)

enum class CategorySort(val label: String) { CAP("Capitalización"), CHANGE("Cambio 24 h") }

/** Sectors with a market cap, largest first. */
fun List<CategoryDto>.toCategories(): List<Category> =
    filter { (it.marketCap ?: 0.0) > 0 }
        .map { Category(it.id, it.name, it.marketCap!!, it.change24h, it.volume24h, it.top3Coins, it.content?.takeIf(String::isNotBlank)) }
        .sortedByDescending { it.marketCap }

fun List<Category>.sortedBy(sort: CategorySort): List<Category> = when (sort) {
    CategorySort.CAP -> sortedByDescending { it.marketCap }
    // Unknown change goes last.
    CategorySort.CHANGE -> sortedByDescending { it.change24h ?: Double.NEGATIVE_INFINITY }
}
