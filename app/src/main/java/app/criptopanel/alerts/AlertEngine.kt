package app.criptopanel.alerts

import app.criptopanel.core.format.formatPercent
import app.criptopanel.core.format.formatUsd
import kotlin.math.abs

/** Latest price of a coin and, if known, its change over the last 24 h in %. */
data class Quote(val price: Double, val change24h: Double?, val at: Long)

/** An alert whose condition was met, with the notification text. */
data class FiredAlert(val alert: Alert, val title: String, val text: String)

const val HISTORY_MS = 4 * 60 * 60_000L
const val SAMPLE_EVERY_MS = 10_000L

/** A window counts as covered when the oldest sample is at most this much younger than its start. */
private const val COVER_SLACK_MS = 60_000L

private fun pct(value: Double): String = "%.1f %%".format(value).replace('.', ',')

/**
 * Keeps the latest quotes and a short price history per coin, and decides which alerts fire.
 * Pure Kotlin: the clock is passed in, so it is tested without Android.
 */
class AlertEngine(
    private val usd: (Double) -> String = { formatUsd(it) },
) {
    private val quotes = mutableMapOf<String, Quote>()
    private val history = mutableMapOf<String, ArrayDeque<Pair<Long, Double>>>()

    fun quote(coinId: String): Quote? = quotes[coinId]

    val allQuotes: Map<String, Quote> get() = quotes.toMap()

    fun update(coinId: String, price: Double, change24h: Double?, now: Long) {
        if (!price.isFinite() || price <= 0) return
        quotes[coinId] = Quote(price, change24h, now)
        val h = history.getOrPut(coinId) { ArrayDeque() }
        if (h.isEmpty() || now - h.last().first >= SAMPLE_EVERY_MS) h.addLast(now to price)
        while (h.isNotEmpty() && now - h.first().first > HISTORY_MS) h.removeFirst()
    }

    /** Samples kept for [coinId]; for tests and diagnostics. */
    fun historySize(coinId: String): Int = history[coinId]?.size ?: 0

    /** % change of [coinId] over the last [windowMs], or null while the history does not reach that far back. */
    fun windowChange(coinId: String, windowMs: Long, now: Long): Double? {
        val h = history[coinId] ?: return null
        val current = quotes[coinId]?.price ?: return null
        val oldest = h.firstOrNull() ?: return null
        if (oldest.first > now - windowMs + COVER_SLACK_MS) return null
        val base = h.firstOrNull { it.first >= now - windowMs }?.second ?: return null
        return (current / base - 1) * 100
    }

    /** Minutes still needed before a quick-move alert can be judged; 0 when ready. */
    fun warmupMinutes(alert: Alert, now: Long): Int {
        if (alert.kind != AlertKind.CHANGE_WINDOW || alert.coinId == null) return 0
        val windowMs = (alert.windowMinutes ?: 60) * 60_000L
        val oldest = history[alert.coinId]?.firstOrNull()?.first ?: return (windowMs / 60_000L).toInt()
        val missing = oldest - (now - windowMs) - COVER_SLACK_MS
        return if (missing <= 0) 0 else ((missing + 59_999) / 60_000L).toInt()
    }

    /** Portfolio value and today's change from [holdings] (coin id → quantity); null unless every coin has a quote. */
    fun portfolio(holdings: Map<String, Double>): Pair<Double, Double?>? {
        if (holdings.isEmpty()) return null
        var value = 0.0
        var change = 0.0
        var changeKnown = true
        for ((coinId, qty) in holdings) {
            val q = quotes[coinId] ?: return null
            val v = q.price * qty
            value += v
            val c = q.change24h
            if (c == null || c <= -100) changeKnown = false else change += v * c / (100 + c)
        }
        val yesterday = value - change
        val pctToday = if (changeKnown && yesterday > 0) change / yesterday * 100 else null
        return value to pctToday
    }

    private fun meets(change: Double, threshold: Double, direction: Direction): Boolean = when (direction) {
        Direction.UP -> change >= threshold
        Direction.DOWN -> change <= -threshold
        Direction.ANY -> abs(change) >= threshold
    }

    /** True when [alert]'s condition holds right now (used to refuse enabling an alert that would fire at once). */
    fun holds(alert: Alert, holdings: Map<String, Double>, now: Long): Boolean = fire(alert, holdings, now) != null

    /** The enabled alerts whose condition holds now. */
    fun evaluate(alerts: List<Alert>, holdings: Map<String, Double>, now: Long): List<FiredAlert> =
        alerts.filter { it.enabled }.mapNotNull { fire(it, holdings, now) }

    private fun fire(a: Alert, holdings: Map<String, Double>, now: Long): FiredAlert? {
        val note = a.note.takeIf { it.isNotBlank() }?.let { " · $it" }.orEmpty()
        if (a.kind.forPortfolio) {
            val (value, today) = portfolio(holdings) ?: return null
            val title = when (a.kind) {
                AlertKind.PORTFOLIO_ABOVE -> if (value > a.threshold) "Tu cartera ha subido de ${usd(a.threshold)}" else return null
                AlertKind.PORTFOLIO_BELOW -> if (value < a.threshold) "Tu cartera ha bajado de ${usd(a.threshold)}" else return null
                else -> {
                    val t = today ?: return null
                    if (!meets(t, a.threshold, a.direction)) return null
                    "Tu cartera ${if (t >= 0) "sube" else "baja"} un ${pct(abs(t))} hoy"
                }
            }
            return FiredAlert(a, title, "Valor: ${usd(value)}$note")
        }
        val coinId = a.coinId ?: return null
        val q = quotes[coinId] ?: return null
        val detail = "Precio: ${usd(q.price)}" + (q.change24h?.let { " · ${formatPercent(it)} en 24 h" } ?: "") + note
        val title = when (a.kind) {
            AlertKind.PRICE_ABOVE -> if (q.price > a.threshold) "${a.symbol} ha subido de ${usd(a.threshold)}" else return null
            AlertKind.PRICE_BELOW -> if (q.price < a.threshold) "${a.symbol} ha bajado de ${usd(a.threshold)}" else return null
            AlertKind.CHANGE_24H -> {
                val c = q.change24h ?: return null
                if (!meets(c, a.threshold, a.direction)) return null
                "${a.symbol} ${if (c >= 0) "sube" else "baja"} un ${pct(abs(c))} en 24 h"
            }
            AlertKind.CHANGE_WINDOW -> {
                val minutes = a.windowMinutes ?: 60
                val c = windowChange(coinId, minutes * 60_000L, now) ?: return null
                if (!meets(c, a.threshold, a.direction)) return null
                val span = if (minutes < 60) "$minutes min" else "${minutes / 60} h"
                "${a.symbol} ${if (c >= 0) "sube" else "baja"} un ${pct(abs(c))} en $span"
            }
            else -> return null
        }
        return FiredAlert(a, title, detail)
    }
}
