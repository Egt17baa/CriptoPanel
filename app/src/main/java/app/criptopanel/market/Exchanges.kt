package app.criptopanel.market

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ExchangeDto(
    val id: String,
    val name: String = "",
    @SerialName("year_established") val yearEstablished: Int? = null,
    val country: String? = null,
    val url: String? = null,
    val image: String? = null,
    @SerialName("trust_score") val trustScore: Int? = null,
    @SerialName("trade_volume_24h_btc") val volume24hBtc: Double? = null,
)

data class Exchange(
    val id: String,
    val name: String,
    val country: String?,
    val year: Int?,
    val url: String?,
    val imageUrl: String?,
    /** CoinGecko trust score, 1..10. */
    val trustScore: Int?,
    val volume24hBtc: Double?,
    /** Null when the BTC price is unknown: show [volume24hBtc] in ₿ instead. */
    val volume24hUsd: Double?,
)

/** CoinGecko gives exchange volume in BTC; [btcPrice] converts it to USD. */
fun List<ExchangeDto>.toExchanges(btcPrice: Double?): List<Exchange> = map {
    Exchange(
        id = it.id,
        name = it.name,
        country = it.country,
        year = it.yearEstablished,
        url = it.url?.takeIf { url -> url.startsWith("https://") || url.startsWith("http://") },
        imageUrl = it.image,
        trustScore = it.trustScore,
        volume24hBtc = it.volume24hBtc,
        volume24hUsd = if (btcPrice != null && it.volume24hBtc != null) it.volume24hBtc * btcPrice else null,
    )
}
