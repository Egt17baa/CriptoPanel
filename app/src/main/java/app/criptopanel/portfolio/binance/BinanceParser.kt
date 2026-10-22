package app.criptopanel.portfolio.binance

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

enum class BinanceFormat(val label: String) {
    TRADES("Historial de operaciones (spot)"),
    TRADES_OLD("Historial de operaciones (formato antiguo)"),
    STATEMENT("Historial de transacciones"),
}

data class Amount(val asset: String, val qty: Double)

/** [received] obtained for [spent] at [time] (UTC); [fee] charged apart. [ref] is the source rows' content. */
data class Swap(val time: LocalDateTime, val received: Amount, val spent: Amount, val fee: Amount?, val ref: String)

/** Crypto credited at [time] with no price in the file: a card purchase ("Buy Crypto"). */
data class MarketBuy(val time: LocalDateTime, val amount: Amount, val ref: String)

/** Earn interest, staking, airdrops, distributions, referral commissions: free coins. */
data class Reward(val time: LocalDateTime, val amount: Amount, val label: String, val ref: String)

data class BinanceParse(
    val format: BinanceFormat,
    val swaps: List<Swap>,
    val marketBuys: List<MarketBuy>,
    val rewards: List<Reward>,
    /** Rows left out on purpose, by reason (deposits, transfers…). */
    val ignored: Map<String, Int>,
    /** Rows that look like trades but could not be split safely, or unknown operations. */
    val unrecognized: Int,
)

class BinanceFormatException(message: String) : Exception(message)

/** Quote assets a pair can end with, longest first so "FDUSD" wins over "USD". */
private val QUOTES = listOf(
    "FDUSD", "USDT", "USDC", "BUSD", "TUSD", "USDP", "USDS", "IDRT", "BIDR", "BVND", "DAI", "PAX",
    "USD", "EUR", "GBP", "TRY", "BRL", "AUD", "RUB", "UAH", "NGN", "ZAR", "ARS", "MXN", "PLN", "RON", "CZK", "COP", "JPY",
    "BTC", "ETH", "BNB", "SOL", "XRP", "TRX", "DOGE", "DOT", "VAI",
).sortedByDescending { it.length }

private fun key(header: String) = header.lowercase().filter { it.isLetterOrDigit() }
    .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")

private fun List<String>.col(vararg names: String): Int? {
    val keys = map(::key)
    return names.firstNotNullOfOrNull { n -> keys.indexOf(key(n)).takeIf { it >= 0 } }
}

/** "1,234.5", "1.234,5", "0,5", "1 234" → Double; null if it is not a number. */
internal fun parseNumber(text: String): Double? {
    var s = text.trim().replace(" ", "").replace(" ", "")
    if (s.isEmpty()) return null
    s = when {
        s.contains(',') && s.contains('.') ->
            if (s.lastIndexOf(',') > s.lastIndexOf('.')) s.replace(".", "").replace(',', '.') else s.replace(",", "")
        Regex("-?\\d{1,3}(,\\d{3})+").matches(s) -> s.replace(",", "")
        else -> s.replace(',', '.')
    }
    return s.toDoubleOrNull()?.takeIf { it.isFinite() }
}

/** "0.00150000BTC" → 0.0015 if it ends with [asset]; null otherwise. */
internal fun amountOf(text: String, asset: String): Double? {
    val s = text.trim().replace(" ", "")
    if (!s.uppercase().endsWith(asset.uppercase())) return null
    return parseNumber(s.dropLast(asset.length))
}

/** A number followed by an asset ("0.75BNB") when the asset is not known beforehand. */
internal fun splitAmount(text: String, candidates: List<String> = emptyList()): Amount? {
    val s = text.trim().replace(" ", "")
    candidates.filter { it.isNotEmpty() }.sortedByDescending { it.length }.forEach { c ->
        amountOf(s, c)?.let { return Amount(c.uppercase(), it) }
    }
    val m = Regex("^([-0-9.,Ee+]*[0-9])([A-Za-z][A-Za-z0-9]*)$").find(s) ?: return null
    return parseNumber(m.groupValues[1])?.let { Amount(m.groupValues[2].uppercase(), it) }
}

/** Splits a pair written without separator ("ETHBTC") by the known quote assets. */
internal fun splitPair(pair: String): Pair<String, String>? {
    val p = pair.trim().uppercase()
    Regex("^([A-Z0-9]+)[/_\\-]([A-Z0-9]+)$").find(p)?.let { return it.groupValues[1] to it.groupValues[2] }
    val quote = QUOTES.firstOrNull { p.endsWith(it) && p.length > it.length } ?: return null
    return p.dropLast(quote.length) to quote
}

private val DATE_FORMATS = listOf(
    "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss.SSS",
    "yy-MM-dd HH:mm:ss", "yyyy/MM/dd HH:mm:ss", "dd/MM/yyyy HH:mm:ss", "dd-MM-yyyy HH:mm:ss", "yyyy-MM-dd HH:mm",
).map { DateTimeFormatter.ofPattern(it) }

/** Date-time in UTC as Binance writes it, or an Excel serial number (days since 1899-12-30). */
internal fun parseTime(text: String): LocalDateTime? {
    val s = text.trim().removeSuffix("Z").removeSuffix(" UTC").trim()
    DATE_FORMATS.forEach { f ->
        try {
            return LocalDateTime.parse(s, f)
        } catch (e: DateTimeParseException) {
            // Next format.
        }
    }
    val serial = s.toDoubleOrNull() ?: return null
    if (serial !in 20_000.0..80_000.0) return null
    val days = serial.toLong()
    val seconds = Math.round((serial - days) * 86_400)
    return LocalDate.of(1899, 12, 30).plusDays(days).atStartOfDay().plusSeconds(seconds)
}

/** Detects the export by its header row and turns it into exchanges, purchases and rewards. */
fun parseBinance(rows: List<List<String>>): BinanceParse {
    val headerIndex = rows.take(10).indexOfFirst { r -> r.col("Date(UTC)", "Fecha(UTC)", "UTC_Time", "Hora UTC", "Tiempo UTC", "Fecha", "Date", "Time") != null }
    if (headerIndex < 0) throw BinanceFormatException("No parece un archivo exportado de Binance")
    val header = rows[headerIndex]
    val data = rows.drop(headerIndex + 1)
    return when {
        header.col("Operation", "Operación", "Operacion") != null && header.col("Change", "Cambio") != null -> parseStatement(header, data)
        header.col("Executed", "Ejecutado") != null -> parseTrades(header, data)
        header.col("Total") != null && header.col("Market", "Mercado", "Pair", "Par") != null -> parseOldTrades(header, data)
        else -> throw BinanceFormatException("Formato de Binance no reconocido: exporta el «Historial de operaciones» o el «Historial de transacciones»")
    }
}

private fun isBuy(side: String): Boolean? = when (side.trim().uppercase()) {
    "BUY", "COMPRA", "COMPRAR" -> true
    "SELL", "VENTA", "VENDER" -> false
    else -> null
}

private fun parseTrades(h: List<String>, rows: List<List<String>>): BinanceParse {
    val cDate = h.col("Date(UTC)", "Fecha(UTC)", "Date", "Fecha", "Time")!!
    val cPair = h.col("Pair", "Par", "Market", "Mercado", "Symbol") ?: throw BinanceFormatException("Falta la columna del par")
    val cSide = h.col("Side", "Lado", "Dirección", "Direccion") ?: h.col("Type", "Tipo") ?: throw BinanceFormatException("Falta la columna de compra o venta")
    val cExec = h.col("Executed", "Ejecutado")!!
    val cAmount = h.col("Amount", "Importe", "Monto", "Total") ?: throw BinanceFormatException("Falta la columna del importe")
    val cFee = h.col("Fee", "Comisión", "Comision", "Tarifa")
    val swaps = mutableListOf<Swap>()
    var bad = 0
    rows.forEach { r ->
        val time = parseTime(r.getOrElse(cDate) { "" })
        val buy = isBuy(r.getOrElse(cSide) { "" })
        val pairText = r.getOrElse(cPair) { "" }
        val exec = r.getOrElse(cExec) { "" }
        val amount = r.getOrElse(cAmount) { "" }
        // Base and quote from the pair; when it has no separator, from where the suffixes of both amounts fit.
        val split = Regex("^([A-Za-z0-9]+)[/_\\-]([A-Za-z0-9]+)$").find(pairText.trim())?.let { it.groupValues[1].uppercase() to it.groupValues[2].uppercase() }
            ?: pairText.trim().uppercase().let { p ->
                (1 until p.length).map { k -> p.take(k) to p.drop(k) }
                    .firstOrNull { (b, q) -> amountOf(exec, b) != null && amountOf(amount, q) != null }
            }
            ?: splitPair(pairText)
        if (time == null || buy == null || split == null) {
            bad++
            return@forEach
        }
        val (base, quote) = split
        val baseQty = amountOf(exec, base) ?: parseNumber(exec)
        val quoteQty = amountOf(amount, quote) ?: parseNumber(amount)
        if (baseQty == null || quoteQty == null || baseQty <= 0 || quoteQty <= 0) {
            bad++
            return@forEach
        }
        val fee = cFee?.let { r.getOrNull(it) }?.takeIf { it.isNotBlank() }?.let { splitAmount(it, listOf(base, quote, "BNB")) }?.takeIf { it.qty > 0 }
        val b = Amount(base, baseQty)
        val q = Amount(quote, quoteQty)
        swaps += if (buy) Swap(time, b, q, fee, "T|${r.joinToString("|")}") else Swap(time, q, b, fee, "T|${r.joinToString("|")}")
    }
    return BinanceParse(BinanceFormat.TRADES, swaps, emptyList(), emptyList(), emptyMap(), bad)
}

private fun parseOldTrades(h: List<String>, rows: List<List<String>>): BinanceParse {
    val cDate = h.col("Date(UTC)", "Fecha(UTC)", "Date", "Fecha")!!
    val cPair = h.col("Market", "Mercado", "Pair", "Par")!!
    val cSide = h.col("Type", "Tipo", "Side", "Lado") ?: throw BinanceFormatException("Falta la columna de compra o venta")
    val cAmount = h.col("Amount", "Cantidad", "Importe") ?: throw BinanceFormatException("Falta la columna de la cantidad")
    val cTotal = h.col("Total")!!
    val cFee = h.col("Fee", "Comisión", "Comision")
    val cFeeCoin = h.col("Fee Coin", "FeeCoin", "Moneda de comisión", "Moneda comision")
    val swaps = mutableListOf<Swap>()
    var bad = 0
    rows.forEach { r ->
        val time = parseTime(r.getOrElse(cDate) { "" })
        val buy = isBuy(r.getOrElse(cSide) { "" })
        val split = splitPair(r.getOrElse(cPair) { "" })
        val baseQty = parseNumber(r.getOrElse(cAmount) { "" })
        val quoteQty = parseNumber(r.getOrElse(cTotal) { "" })
        if (time == null || buy == null || split == null || baseQty == null || quoteQty == null || baseQty <= 0 || quoteQty <= 0) {
            bad++
            return@forEach
        }
        val feeQty = cFee?.let { r.getOrNull(it) }?.let(::parseNumber)
        val feeCoin = cFeeCoin?.let { r.getOrNull(it) }?.trim()?.uppercase()
        val fee = if (feeQty != null && feeQty > 0 && !feeCoin.isNullOrEmpty()) Amount(feeCoin, feeQty) else null
        val b = Amount(split.first, baseQty)
        val q = Amount(split.second, quoteQty)
        swaps += if (buy) Swap(time, b, q, fee, "O|${r.joinToString("|")}") else Swap(time, q, b, fee, "O|${r.joinToString("|")}")
    }
    return BinanceParse(BinanceFormat.TRADES_OLD, swaps, emptyList(), emptyList(), emptyMap(), bad)
}

private val TRADE_OPERATIONS = setOf(
    "buy", "sell", "fee", "transaction buy", "transaction spend", "transaction fee", "transaction sold", "transaction revenue",
    "transaction related", "binance convert", "small assets exchange bnb", "large otc trading", "buy crypto",
    "auto-invest transaction", "convert", "trading fee",
)
private val FEE_OPERATIONS = setOf("fee", "transaction fee", "trading fee")

private fun ignoredReason(operation: String, account: String): String? {
    val op = operation.lowercase()
    val acc = account.lowercase()
    return when {
        acc.contains("futures") || acc.contains("margin") || acc.contains("futuros") || acc.contains("margen") ||
            op.contains("futures") || op.contains("margin") || op.contains("funding fee") || op.contains("realized profit") -> "Futuros y margen"
        op.contains("p2p") -> "P2P"
        op == "deposit" || op == "withdraw" || op.contains("fiat deposit") || op.contains("fiat withdraw") ||
            op.contains("depósito") || op.contains("deposito") || op.contains("retirada") -> "Depósitos y retiradas"
        op.contains("transfer") || op.contains("transferencia") -> "Transferencias entre cuentas"
        op.contains("subscription") || op.contains("redemption") || op.contains("purchase") || op.contains("suscripci") ||
            op.contains("reembolso") || op.contains("staking redemption") -> "Suscripciones y reembolsos de Earn"
        else -> null
    }
}

private fun isReward(operation: String): Boolean {
    val op = operation.lowercase()
    return listOf(
        "interest", "reward", "distribution", "airdrop", "commission", "referral", "cashback", "launchpool", "megadrop",
        "rebate", "bonus", "voucher", "dividend", "recompensa", "interés", "interes",
    ).any { op.contains(it) }
}

private data class StatementRow(val index: Int, val time: LocalDateTime, val account: String, val operation: String, val coin: String, val change: Double)

private fun parseStatement(h: List<String>, rows: List<List<String>>): BinanceParse {
    val cTime = h.col("UTC_Time", "Hora UTC", "Tiempo UTC", "Date(UTC)", "Fecha(UTC)", "Time")!!
    val cAccount = h.col("Account", "Cuenta")
    val cOp = h.col("Operation", "Operación", "Operacion")!!
    val cCoin = h.col("Coin", "Moneda", "Asset", "Activo") ?: throw BinanceFormatException("Falta la columna de la moneda")
    val cChange = h.col("Change", "Cambio")!!
    val ignored = mutableMapOf<String, Int>()
    var unrecognized = 0
    val rewards = mutableListOf<Reward>()
    val tradeRows = mutableListOf<StatementRow>()
    rows.forEachIndexed { i, r ->
        val time = parseTime(r.getOrElse(cTime) { "" })
        val op = r.getOrElse(cOp) { "" }.trim()
        val coin = r.getOrElse(cCoin) { "" }.trim().uppercase()
        val change = parseNumber(r.getOrElse(cChange) { "" })
        val account = cAccount?.let { r.getOrNull(it) }.orEmpty()
        if (time == null || coin.isEmpty() || change == null || op.isEmpty()) {
            unrecognized++
            return@forEachIndexed
        }
        if (change == 0.0) return@forEachIndexed
        val reason = ignoredReason(op, account)
        when {
            reason != null -> ignored.merge(reason, 1, Int::plus)
            op.lowercase() in TRADE_OPERATIONS -> tradeRows += StatementRow(i, time, account, op, coin, change)
            isReward(op) -> if (change > 0) rewards += Reward(time, Amount(coin, change), op, "R|${r.joinToString("|")}") else ignored.merge("Otros movimientos", 1, Int::plus)
            else -> unrecognized++
        }
    }
    val swaps = mutableListOf<Swap>()
    val marketBuys = mutableListOf<MarketBuy>()
    // The legs of one trade or conversion share the same second.
    tradeRows.groupBy { it.time }.toSortedMap().forEach { (time, group) ->
        val fees = group.filter { it.operation.lowercase() in FEE_OPERATIONS }
        val legs = group - fees.toSet()
        val net = legs.groupBy { it.coin }.mapValues { (_, rs) -> rs.sumOf { it.change } }.filterValues { it != 0.0 }
        val received = net.filterValues { it > 0 }
        val spent = net.filterValues { it < 0 }
        val feeByCoin = fees.groupBy { it.coin }.mapValues { (_, rs) -> -rs.sumOf { it.change } }.filterValues { it > 0 }
        // From the content only (never the row number), so the same trade in two overlapping exports matches.
        val ref = "S|" + group.map { "${it.time}|${it.operation}|${it.coin}|${it.change}" }.sorted().joinToString("|")
        when {
            received.size == 1 && spent.size == 1 && feeByCoin.size <= 1 -> {
                val (rc, rq) = received.entries.single()
                val (sc, sq) = spent.entries.single()
                val fee = feeByCoin.entries.singleOrNull()?.let { Amount(it.key, it.value) }
                swaps += Swap(time, Amount(rc, rq), Amount(sc, -sq), fee, ref)
            }
            received.size == 1 && spent.isEmpty() && legs.all { it.operation.equals("Buy Crypto", ignoreCase = true) } -> {
                val (rc, rq) = received.entries.single()
                marketBuys += MarketBuy(time, Amount(rc, rq), ref)
            }
            else -> unrecognized += group.size
        }
    }
    return BinanceParse(BinanceFormat.STATEMENT, swaps, marketBuys, rewards, ignored, unrecognized)
}
