package app.criptopanel.market

import kotlinx.serialization.Serializable
import retrofit2.http.GET
import retrofit2.http.Query

/** alternative.me Crypto Fear & Greed Index. */
interface SentimentApi {
    @GET("fng/")
    suspend fun fearGreed(@Query("limit") limit: Int = 30): FearGreedResponseDto
}

@Serializable
data class FearGreedResponseDto(val data: List<FearGreedDto> = emptyList())

/** The API sends numbers as strings, newest first. */
@Serializable
data class FearGreedDto(val value: String? = null, val timestamp: String? = null)

data class FearGreedPoint(val epochSeconds: Long, val value: Int)

data class FearGreed(
    val value: Int,
    /** Previous day's value, if the API sent it. */
    val yesterday: Int?,
    /** Oldest first. */
    val history: List<FearGreedPoint>,
) {
    val label: String get() = fearGreedLabel(value)
}

/** Same bands as alternative.me, in Spanish. */
fun fearGreedLabel(value: Int): String = when {
    value <= 24 -> "Miedo extremo"
    value <= 44 -> "Miedo"
    value <= 55 -> "Neutral"
    value <= 75 -> "Codicia"
    else -> "Codicia extrema"
}

/** Null when the API sent no usable point. */
fun FearGreedResponseDto.toDomain(): FearGreed? {
    val points = data
        .mapNotNull { d ->
            val value = d.value?.toIntOrNull() ?: return@mapNotNull null
            val at = d.timestamp?.toLongOrNull() ?: return@mapNotNull null
            FearGreedPoint(at, value)
        }
        .sortedBy { it.epochSeconds }
    val today = points.lastOrNull() ?: return null
    return FearGreed(today.value, points.getOrNull(points.size - 2)?.value, points)
}
