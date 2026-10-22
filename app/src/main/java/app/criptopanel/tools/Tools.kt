package app.criptopanel.tools

import app.criptopanel.core.format.Money
import app.criptopanel.portfolio.FormCoin
import app.criptopanel.portfolio.PricePoint
import java.time.LocalDate
import java.time.ZoneId

/** One side of the calculator: dollars or a coin. */
sealed interface Asset {
    val label: String

    /** Money in the display currency (USD or EUR). */
    data object Usd : Asset {
        override val label: String get() = Money.current.code
    }

    data class Coin(val coin: FormCoin) : Asset {
        override val label: String get() = coin.symbol
    }
}

val BITCOIN = FormCoin("bitcoin", "BTC", "Bitcoin", null)
val ETHEREUM = FormCoin("ethereum", "ETH", "Ethereum", null)

/** [amount] of an asset priced [fromUsd] dollars, expressed in an asset priced [toUsd]; null if a price is unknown. */
fun convert(amount: Double, fromUsd: Double?, toUsd: Double?): Double? {
    if (fromUsd == null || toUsd == null || toUsd <= 0) return null
    return amount * fromUsd / toUsd
}

enum class WhatIfPeriod(val label: String, val months: Long) {
    M1("1 mes", 1), M3("3 meses", 3), M6("6 meses", 6), Y1("1 año", 12),
}

data class WhatIfResult(
    val invested: Double,
    val value: Double,
    val units: Double,
    val purchases: Int,
    /** Value of what was bought, and money put in so far, at each price point from the first purchase. */
    val valueSeries: List<PricePoint>,
    val investedSeries: List<PricePoint>,
) {
    val profit: Double get() = value - invested
    val profitPct: Double? get() = if (invested > 0) profit / invested * 100 else null
    val averagePrice: Double? get() = if (units > 0) invested / units else null
}

/**
 * "What if I had invested [amount] dollars [period] ago": once on the first day, or the same amount every month (DCA).
 * Each purchase uses the last price known at the end of its day (or the first one after it, if the series starts later).
 */
fun simulate(
    points: List<PricePoint>,
    currentPrice: Double,
    amount: Double,
    period: WhatIfPeriod,
    monthly: Boolean,
    today: LocalDate,
    zone: ZoneId = ZoneId.systemDefault(),
): WhatIfResult? {
    if (points.isEmpty() || amount <= 0 || currentPrice <= 0) return null
    val sorted = points.sortedBy { it.epochMillis }
    val start = today.minusMonths(period.months)
    val dates = if (monthly) (0 until period.months).map { start.plusMonths(it) } else listOf(start)
    fun endOf(d: LocalDate) = d.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
    fun priceOn(d: LocalDate): Double? {
        val end = endOf(d)
        return (sorted.lastOrNull { it.epochMillis <= end } ?: sorted.firstOrNull { it.epochMillis > end })?.price
    }
    val buys = dates.mapNotNull { d -> priceOn(d)?.takeIf { it > 0 }?.let { endOf(d) to amount / it } }
    if (buys.isEmpty()) return null
    val units = buys.sumOf { it.second }
    val firstBuy = buys.first().first - 24 * 60 * 60_000L + 1
    val shown = sorted.filter { it.epochMillis >= firstBuy }
    return WhatIfResult(
        invested = amount * buys.size,
        value = units * currentPrice,
        units = units,
        purchases = buys.size,
        valueSeries = shown.map { p -> PricePoint(p.epochMillis, buys.filter { it.first - 86_399_999 <= p.epochMillis }.sumOf { it.second } * p.price) },
        investedSeries = shown.map { p -> PricePoint(p.epochMillis, amount * buys.count { it.first - 86_399_999 <= p.epochMillis }) },
    )
}

const val MAX_COMPARED = 4

/** Index of the best value (largest, or smallest when [lowerIsBetter]); null when fewer than two are known. */
fun bestIndex(values: List<Double?>, lowerIsBetter: Boolean = false): Int? {
    val known = values.withIndex().filter { it.value != null }
    if (known.size < 2) return null
    return (if (lowerIsBetter) known.minByOrNull { it.value!! } else known.maxByOrNull { it.value!! })?.index
}

/** Each series rebased to 100 at its first point, for a performance overlay. */
fun rebased(series: List<PricePoint>): List<Double> {
    val first = series.firstOrNull()?.price?.takeIf { it > 0 } ?: return emptyList()
    return series.map { it.price / first * 100 }
}
