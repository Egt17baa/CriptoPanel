package app.criptopanel.portfolio

import app.criptopanel.coins.CoinMarket
import kotlinx.serialization.Serializable
import java.time.LocalDate
import java.time.ZoneId

@Serializable
enum class OperationType { BUY, SELL }

@Serializable
data class Operation(
    val id: String,
    /** CoinGecko id. */
    val coinId: String,
    val symbol: String,
    val name: String,
    val imageUrl: String? = null,
    val type: OperationType,
    val quantity: Double,
    val priceUsd: Double,
    val feeUsd: Double = 0.0,
    /** Local day, ISO yyyy-MM-dd. */
    val date: String,
    val note: String = "",
) {
    val localDate: LocalDate get() = LocalDate.parse(date)
}

@Serializable
data class PortfolioData(
    val version: Int = 1,
    val operations: List<Operation> = emptyList(),
    /** CoinGecko ids, in the order they were starred. */
    val favorites: List<String> = emptyList(),
)

/** Quantities below this are rounding noise and count as zero. */
const val QUANTITY_EPSILON = 1e-9

/** Date order; on the same day buys come before sells, so the order they were typed in never matters. */
fun List<Operation>.chronological(): List<Operation> = sortedWith(compareBy({ it.date }, { it.type.ordinal }))

/** One coin's result of all its operations, average-cost method. */
data class Position(
    val coinId: String,
    val symbol: String,
    val name: String,
    val imageUrl: String?,
    val quantity: Double,
    /** Cost basis of what is still held, fees included. */
    val cost: Double,
    /** Profit already made by selling. */
    val realized: Double,
    /** Everything ever spent on buys, fees included. */
    val invested: Double,
) {
    val averagePrice: Double? get() = if (quantity > 0) cost / quantity else null
    val isOpen: Boolean get() = quantity > 0
}

/** Positions per coin, largest cost first; coins sold out are kept for their realized profit. */
fun positions(operations: List<Operation>): List<Position> =
    operations.chronological()
        .groupBy { it.coinId }
        .map { (coinId, ops) ->
            var quantity = 0.0
            var cost = 0.0
            var realized = 0.0
            var invested = 0.0
            ops.forEach { op ->
                when (op.type) {
                    OperationType.BUY -> {
                        quantity += op.quantity
                        cost += op.quantity * op.priceUsd + op.feeUsd
                        invested += op.quantity * op.priceUsd + op.feeUsd
                    }
                    OperationType.SELL -> {
                        val average = if (quantity > 0) cost / quantity else 0.0
                        realized += op.quantity * op.priceUsd - op.feeUsd - op.quantity * average
                        cost -= op.quantity * average
                        quantity -= op.quantity
                        if (quantity < QUANTITY_EPSILON) {
                            quantity = 0.0
                            cost = 0.0
                        }
                    }
                }
            }
            val latest = ops.last()
            Position(coinId, latest.symbol, latest.name, latest.imageUrl, quantity, cost, realized, invested)
        }
        .sortedByDescending { it.cost }

/** The first sell that would take a coin below zero on its date, or null if every sell is covered. */
fun firstShortfall(operations: List<Operation>): Operation? {
    val held = mutableMapOf<String, Double>()
    operations.chronological().forEach { op ->
        val now = (held[op.coinId] ?: 0.0) + if (op.type == OperationType.BUY) op.quantity else -op.quantity
        if (now < -QUANTITY_EPSILON) return op
        held[op.coinId] = now
    }
    return null
}

data class PricedPosition(
    val position: Position,
    val price: Double?,
    val change24h: Double?,
    /** Null while the price is unknown. */
    val value: Double?,
) {
    /** Realized plus unrealized; unrealized counts as 0 while the price is unknown. */
    val profit: Double get() = position.realized + (value?.let { it - position.cost } ?: 0.0)
    val profitPct: Double? get() = position.invested.takeIf { it > 0 }?.let { profit / it * 100 }
}

data class PortfolioSummary(
    val value: Double,
    val profit: Double,
    val profitPct: Double?,
    val change24h: Double,
    val change24hPct: Double?,
    /** Some coin had no 24 h change: [change24h] leaves it out. */
    val change24hApprox: Boolean,
    /** Open positions by value, largest first. */
    val open: List<PricedPosition>,
    /** Every position, open or closed. */
    val all: List<PricedPosition>,
)

fun summarize(positions: List<Position>, prices: Map<String, CoinMarket>): PortfolioSummary {
    val priced = positions.map { p ->
        val market = prices[p.coinId]
        val price = market?.price
        PricedPosition(p, price, market?.change24h, price?.let { it * p.quantity })
    }
    val open = priced.filter { it.position.isOpen }.sortedByDescending { it.value ?: 0.0 }
    val value = open.sumOf { it.value ?: 0.0 }
    val profit = priced.sumOf { it.profit }
    val invested = positions.sumOf { it.invested }
    var approx = false
    val change = open.sumOf { pp ->
        val v = pp.value
        val c = pp.change24h
        if (v == null || c == null || c <= -100) {
            approx = true
            0.0
        } else {
            v * c / (100 + c)
        }
    }
    val yesterday = value - change
    return PortfolioSummary(
        value = value,
        profit = profit,
        profitPct = invested.takeIf { it > 0 }?.let { profit / it * 100 },
        change24h = change,
        change24hPct = yesterday.takeIf { it > 0 }?.let { change / it * 100 },
        change24hApprox = approx,
        open = open,
        all = priced,
    )
}

data class AllocationSlice(val label: String, val fraction: Double)

/** Share of the portfolio value per coin: the [top] largest and the rest as "Otras". */
fun allocation(summary: PortfolioSummary, top: Int = 8): List<AllocationSlice> {
    if (summary.value <= 0) return emptyList()
    val valued = summary.open.filter { (it.value ?: 0.0) > 0 }
    val slices = valued.take(top).map { AllocationSlice(it.position.symbol, it.value!! / summary.value) }
    val rest = valued.drop(top).sumOf { it.value!! }
    return if (rest > 0) slices + AllocationSlice("Otras", rest / summary.value) else slices
}

data class PricePoint(val epochMillis: Long, val price: Double)

/**
 * Portfolio value at each time of [grid]: per coin, the quantity held at that time (operations dated on or before it)
 * times the last price at or before it (or the first price, if the series starts later).
 */
fun valueSeries(
    operations: List<Operation>,
    prices: Map<String, List<PricePoint>>,
    grid: List<Long>,
    zone: ZoneId = ZoneId.systemDefault(),
): List<PricePoint> {
    val byCoin = operations.filter { it.coinId in prices }.chronological().groupBy { it.coinId }
    return grid.map { t ->
        val total = byCoin.entries.sumOf { (coinId, ops) ->
            val held = ops
                .filter { it.localDate.atStartOfDay(zone).toInstant().toEpochMilli() <= t }
                .sumOf { if (it.type == OperationType.BUY) it.quantity else -it.quantity }
                .coerceAtLeast(0.0)
            if (held == 0.0) {
                0.0
            } else {
                val series = prices.getValue(coinId)
                val price = series.lastOrNull { it.epochMillis <= t }?.price ?: series.firstOrNull()?.price ?: 0.0
                held * price
            }
        }
        PricePoint(t, total)
    }
}

/**
 * Operations re-priced so the portfolio maths, done in "today's dollars", come out right in another display currency:
 * each price and fee is converted with the rate of its own day ([perUsdOn]) and back with today's ([perUsdNow]).
 * The cost of a euro purchase then stays what was really paid in euros.
 */
fun List<Operation>.inDisplayCurrency(perUsdOn: (LocalDate) -> Double, perUsdNow: Double): List<Operation> = map { op ->
    val k = perUsdOn(op.localDate) / perUsdNow
    op.copy(priceUsd = op.priceUsd * k, feeUsd = op.feeUsd * k)
}

/** Quantity of [coinId] held at the end of [date], leaving out the operation being edited. */
fun heldOn(operations: List<Operation>, coinId: String, date: LocalDate, excludeId: String? = null): Double =
    operations
        .filter { it.coinId == coinId && it.id != excludeId && !it.localDate.isAfter(date) }
        .sumOf { if (it.type == OperationType.BUY) it.quantity else -it.quantity }
        .let { if (it < QUANTITY_EPSILON) 0.0 else it }
