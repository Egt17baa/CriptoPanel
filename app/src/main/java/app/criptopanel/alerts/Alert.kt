package app.criptopanel.alerts

import kotlinx.serialization.Serializable

@Serializable
enum class AlertKind(val forPortfolio: Boolean) {
    PRICE_ABOVE(false),
    PRICE_BELOW(false),
    /** X % within [Alert.windowMinutes]. */
    CHANGE_WINDOW(false),
    CHANGE_24H(false),
    PORTFOLIO_ABOVE(true),
    PORTFOLIO_BELOW(true),
    PORTFOLIO_CHANGE_TODAY(true),
}

@Serializable
enum class Direction { UP, DOWN, ANY }

/** Windows offered for a quick move. */
val CHANGE_WINDOWS_MINUTES = listOf(15, 60, 240)

@Serializable
data class Alert(
    val id: String,
    /** CoinGecko id; null for alerts on the whole portfolio. */
    val coinId: String? = null,
    val symbol: String = "",
    val name: String = "",
    val imageUrl: String? = null,
    val kind: AlertKind,
    /** USD for price and value alerts, percent for change alerts. */
    val threshold: Double,
    val windowMinutes: Int? = null,
    val direction: Direction = Direction.ANY,
    val note: String = "",
    val enabled: Boolean = true,
    val createdAt: Long,
    val lastTriggered: Long? = null,
)

@Serializable
data class AlertsData(val version: Int = 1, val alerts: List<Alert> = emptyList())

private fun windowLabel(minutes: Int?): String = when (minutes) {
    null -> "—"
    in 0..59 -> "$minutes min"
    else -> "${minutes / 60} h"
}

private fun directionWord(d: Direction): String = when (d) {
    Direction.UP -> "sube"
    Direction.DOWN -> "baja"
    Direction.ANY -> "se mueve"
}

/** Short Spanish description, e.g. "BTC sube de 100.000,00 $" or "ETH se mueve un 5 % en 1 h". */
fun Alert.describe(formatUsd: (Double) -> String, formatPct: (Double) -> String): String {
    val who = if (kind.forPortfolio) "Cartera" else symbol
    return when (kind) {
        AlertKind.PRICE_ABOVE, AlertKind.PORTFOLIO_ABOVE -> "$who sube de ${formatUsd(threshold)}"
        AlertKind.PRICE_BELOW, AlertKind.PORTFOLIO_BELOW -> "$who baja de ${formatUsd(threshold)}"
        AlertKind.CHANGE_WINDOW -> "$who ${directionWord(direction)} un ${formatPct(threshold)} en ${windowLabel(windowMinutes)}"
        AlertKind.CHANGE_24H -> "$who ${directionWord(direction)} un ${formatPct(threshold)} en 24 h"
        AlertKind.PORTFOLIO_CHANGE_TODAY -> "$who ${directionWord(direction)} un ${formatPct(threshold)} hoy"
    }
}
