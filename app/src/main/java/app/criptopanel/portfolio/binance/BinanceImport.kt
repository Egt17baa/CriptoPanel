package app.criptopanel.portfolio.binance

import app.criptopanel.alerts.USD_STABLECOINS
import app.criptopanel.portfolio.Operation
import app.criptopanel.portfolio.OperationType
import app.criptopanel.portfolio.QUANTITY_EPSILON
import app.criptopanel.portfolio.chronological
import java.security.MessageDigest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** Money, not a position: what crypto is bought and sold with. */
private val FIAT = setOf("USD", "EUR", "GBP", "TRY", "BRL", "AUD", "RUB", "UAH", "NGN", "ZAR", "ARS", "MXN", "PLN", "RON", "CZK", "COP", "JPY", "IDRT", "BIDR", "BVND")

fun isCash(asset: String): Boolean = asset.uppercase().let { it in USD_STABLECOINS || it in FIAT || it == "PAX" }

/** A Binance ticker matched to a CoinGecko coin. */
data class CoinInfo(val id: String, val symbol: String, val name: String, val imageUrl: String?)

/** What will be added, and the summary shown before importing. */
data class ImportPlan(
    val format: BinanceFormat,
    val operations: List<Operation>,
    val buys: Int,
    val sells: Int,
    val rewards: Int,
    /** "Saldo previo" buys added so no sell goes below zero. */
    val openingBalances: Int,
    val coins: Int,
    /** Tickers with no CoinGecko match: their operations are not imported. */
    val unknownSymbols: Set<String>,
    /** Trades left out because no USD price was found for them. */
    val unpriced: Int,
    val ignored: Map<String, Int>,
    val unrecognized: Int,
)

/**
 * Turns a parsed export into portfolio operations. [resolve] finds the CoinGecko coin of a ticker; [usdOn] gives
 * the USD value of one unit of an asset (crypto, stablecoin or fiat) on a day. Both are injected for tests.
 */
class BinanceConverter(
    private val resolve: suspend (String) -> CoinInfo?,
    private val usdOn: suspend (String, LocalDate) -> Double?,
    private val zone: ZoneId = ZoneId.systemDefault(),
) {
    private val ops = mutableListOf<Operation>()
    private val seen = mutableMapOf<String, Int>()
    private val unknown = sortedSetOf<String>()
    private var unpriced = 0

    private fun localDay(utc: LocalDateTime): LocalDate = utc.atOffset(ZoneOffset.UTC).atZoneSameInstant(zone).toLocalDate()

    private fun idFor(ref: String, leg: String): String {
        val base = "$ref#$leg"
        val n = seen.merge(base, 1, Int::plus)!!
        val digest = MessageDigest.getInstance("SHA-256").digest("$base#$n".toByteArray())
        return "binance-" + digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private suspend fun coin(symbol: String): CoinInfo? = resolve(symbol) ?: null.also { unknown += symbol }

    private suspend fun add(
        type: OperationType,
        symbol: String,
        qty: Double,
        priceUsd: Double,
        feeUsd: Double,
        day: LocalDate,
        note: String,
        ref: String,
        leg: String,
    ) {
        if (qty <= QUANTITY_EPSILON) return
        val c = coin(symbol) ?: return
        ops += Operation(
            id = idFor(ref, leg),
            coinId = c.id,
            symbol = c.symbol.uppercase(),
            name = c.name,
            imageUrl = c.imageUrl,
            type = type,
            quantity = qty,
            priceUsd = priceUsd,
            feeUsd = feeUsd,
            date = day.toString(),
            note = note.take(200),
        )
    }

    private suspend fun swap(s: Swap) {
        val day = localDay(s.time)
        val (got, paid) = s.received to s.spent
        // The trade's value in USD: from the cash side when there is one, else from the coin paid with.
        val value = when {
            isCash(paid.asset) -> usdOn(paid.asset, day)?.times(paid.qty)
            isCash(got.asset) -> usdOn(got.asset, day)?.times(got.qty)
            else -> usdOn(paid.asset, day)?.times(paid.qty) ?: usdOn(got.asset, day)?.times(got.qty)
        }
        if (value == null || value <= 0) {
            unpriced++
            return
        }
        val fee = s.fee
        val feeUsd = when {
            fee == null -> 0.0
            fee.asset == got.asset -> value / got.qty * fee.qty
            fee.asset == paid.asset -> value / paid.qty * fee.qty
            else -> usdOn(fee.asset, day)?.times(fee.qty) ?: 0.0
        }
        val pair = "${got.asset}/${paid.asset}"
        val buys = !isCash(got.asset)
        val sells = !isCash(paid.asset)
        // The fee is charged to one leg: the coin it was taken from if that coin is sold, else the buy if any.
        val feeOnSell = sells && (fee?.asset == paid.asset || !buys)
        if (buys) {
            // A fee taken from the coin bought reduces what really arrived; its value stays in the cost.
            val net = if (fee?.asset == got.asset) got.qty - fee.qty else got.qty
            add(OperationType.BUY, got.asset, net, value / got.qty, if (feeOnSell) 0.0 else feeUsd, day, "Binance $pair", s.ref, "buy")
        }
        if (sells) {
            // Paid in a coin: that coin is sold. A fee taken from it leaves the position too.
            val extra = if (fee?.asset == paid.asset) fee.qty else 0.0
            add(OperationType.SELL, paid.asset, paid.qty + extra, value / paid.qty, if (feeOnSell) feeUsd else 0.0, day, "Binance $pair", s.ref, "sell")
        }
    }

    suspend fun convert(parse: BinanceParse, existing: List<Operation>): ImportPlan {
        parse.swaps.sortedBy { it.time }.forEach { swap(it) }
        parse.marketBuys.forEach { b ->
            val day = localDay(b.time)
            val price = usdOn(b.amount.asset, day)
            if (price == null) unpriced++ else add(OperationType.BUY, b.amount.asset, b.amount.qty, price, 0.0, day, "Binance: compra con tarjeta (precio de mercado del día)", b.ref, "card")
        }
        parse.rewards.forEach { r ->
            if (!isCash(r.amount.asset)) add(OperationType.BUY, r.amount.asset, r.amount.qty, 0.0, 0.0, localDay(r.time), "Binance: recompensa (${r.label})", r.ref, "reward")
        }
        val known = existing.map { it.id }.toSet()
        val fresh = ops.filter { it.id !in known }
        val openings = openingBalances(existing, fresh)
        val all = fresh + openings
        return ImportPlan(
            format = parse.format,
            operations = all,
            buys = fresh.count { it.type == OperationType.BUY && it.priceUsd > 0 },
            sells = fresh.count { it.type == OperationType.SELL },
            rewards = fresh.count { it.type == OperationType.BUY && it.priceUsd == 0.0 },
            openingBalances = openings.size,
            coins = all.map { it.coinId }.toSet().size,
            unknownSymbols = unknown.filterNot(::isCash).toSet(),
            unpriced = unpriced,
            ignored = parse.ignored,
            unrecognized = parse.unrecognized,
        )
    }
}

/**
 * Coins deposited from outside (not in the file) are sold later: a "Saldo previo" buy on the sale's day, at the
 * sale's price, covers what is missing, so no position goes below zero and the gain counts from that day.
 */
internal fun openingBalances(existing: List<Operation>, added: List<Operation>): List<Operation> {
    val held = mutableMapOf<String, Double>()
    val out = mutableListOf<Operation>()
    (existing + added).chronological().forEach { op ->
        val now = (held[op.coinId] ?: 0.0) + if (op.type == OperationType.BUY) op.quantity else -op.quantity
        if (now < -QUANTITY_EPSILON) {
            out += op.copy(
                id = op.id + "-saldo",
                type = OperationType.BUY,
                quantity = -now,
                feeUsd = 0.0,
                note = "Saldo previo (depositado fuera del archivo)",
            )
            held[op.coinId] = 0.0
        } else {
            held[op.coinId] = now
        }
    }
    return out
}
