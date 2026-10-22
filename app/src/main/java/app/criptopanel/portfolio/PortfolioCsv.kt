package app.criptopanel.portfolio

import java.time.LocalDate
import java.time.format.DateTimeParseException
import java.util.UUID

private val HEADER = listOf("fecha", "tipo", "moneda_id", "simbolo", "nombre", "cantidad", "precio_usd", "comision_usd", "nota", "id")

/** Spreadsheets run a cell starting with one of these as a formula (CSV injection); such text gets a leading '. */
private const val FORMULA_START = "=+-@\t\r"

private fun field(value: String): String =
    if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"${value.replace("\"", "\"\"")}\"" else value

private fun text(value: String): String = if (value.firstOrNull()?.let { it in FORMULA_START } == true) "'$value" else value

private fun untext(value: String): String = if (value.length > 1 && value[0] == '\'' && value[1] in FORMULA_START) value.substring(1) else value

/** Larger files are refused before reading (a real portfolio CSV is a few KB). */
const val CSV_MAX_BYTES = 5 * 1024 * 1024

/** Up to [limit] bytes of the stream (InputStream.readNBytes needs Android 13). */
fun java.io.InputStream.readAtMost(limit: Int): ByteArray {
    val out = java.io.ByteArrayOutputStream()
    val buf = ByteArray(8192)
    while (out.size() < limit) {
        val n = read(buf, 0, minOf(buf.size, limit - out.size()))
        if (n < 0) break
        out.write(buf, 0, n)
    }
    return out.toByteArray()
}

/** CSV with a header row, comma separated, decimals with a point; opens in any spreadsheet. */
fun List<Operation>.toCsv(): String = buildString {
    appendLine(HEADER.joinToString(","))
    chronological().forEach { op ->
        val type = if (op.type == OperationType.BUY) "compra" else "venta"
        val row = listOf(
            op.date, type, text(op.coinId), text(op.symbol), text(op.name),
            op.quantity.toBigDecimal().toPlainString(), op.priceUsd.toBigDecimal().toPlainString(),
            op.feeUsd.toBigDecimal().toPlainString(), text(op.note), op.id,
        )
        appendLine(row.joinToString(",") { field(it) })
    }
}

sealed interface CsvImport {
    /** [operations] are the new ones; [skipped] were already in the portfolio (same id). */
    data class Ok(val operations: List<Operation>, val skipped: Int) : CsvImport

    /** Nothing is imported; [line] is 1-based as a spreadsheet shows it. */
    data class Error(val line: Int, val reason: String) : CsvImport
}

/** Splits one CSV record; quotes may wrap commas and doubled quotes. Newlines inside quotes are not supported. */
private fun splitRecord(line: String): List<String>? {
    val out = mutableListOf<String>()
    val cur = StringBuilder()
    var quoted = false
    var i = 0
    while (i < line.length) {
        val c = line[i]
        when {
            quoted && c == '"' && line.getOrNull(i + 1) == '"' -> { cur.append('"'); i++ }
            c == '"' -> quoted = !quoted
            c == ',' && !quoted -> { out += cur.toString(); cur.clear() }
            else -> cur.append(c)
        }
        i++
    }
    if (quoted) return null
    out += cur.toString()
    return out
}

private fun number(text: String): Double? = text.trim().replace(',', '.').toDoubleOrNull()?.takeIf { it.isFinite() }

/** Validates the whole file first: any bad row means nothing is imported. */
fun parseCsv(text: String, existingIds: Set<String>): CsvImport {
    val lines = text.removePrefix("\uFEFF").lines()
    val header = lines.firstOrNull()?.let(::splitRecord)?.map { it.trim().lowercase() }
    if (header == null || header.take(HEADER.size - 1) != HEADER.dropLast(1)) {
        return CsvImport.Error(1, "La cabecera no es la de un CSV exportado por CriptoPanel")
    }
    val ops = mutableListOf<Operation>()
    var skipped = 0
    val seen = mutableSetOf<String>()
    lines.drop(1).forEachIndexed { index, raw ->
        val line = index + 2
        if (raw.isBlank()) return@forEachIndexed
        val f = splitRecord(raw) ?: return CsvImport.Error(line, "Comillas sin cerrar")
        if (f.size < HEADER.size - 1) return CsvImport.Error(line, "Faltan columnas")
        val date = try {
            LocalDate.parse(f[0].trim())
        } catch (e: DateTimeParseException) {
            return CsvImport.Error(line, "Fecha no válida: ${f[0]}")
        }
        if (date.isAfter(LocalDate.now())) return CsvImport.Error(line, "La fecha es futura")
        val type = when (f[1].trim().lowercase()) {
            "compra", "buy" -> OperationType.BUY
            "venta", "sell" -> OperationType.SELL
            else -> return CsvImport.Error(line, "Tipo no válido: ${f[1]} (compra o venta)")
        }
        val coinId = untext(f[2].trim()).ifEmpty { return CsvImport.Error(line, "Falta moneda_id") }
        val quantity = number(f[5])?.takeIf { it > 0 } ?: return CsvImport.Error(line, "Cantidad no válida")
        val price = number(f[6])?.takeIf { it > 0 } ?: return CsvImport.Error(line, "Precio no válido")
        val fee = if (f[7].isBlank()) 0.0 else number(f[7])?.takeIf { it >= 0 } ?: return CsvImport.Error(line, "Comisión no válida")
        val id = f.getOrNull(9)?.trim().orEmpty().ifEmpty { UUID.randomUUID().toString() }
        if (id in existingIds || !seen.add(id)) {
            skipped++
            return@forEachIndexed
        }
        ops += Operation(
            id = id,
            coinId = coinId,
            symbol = untext(f[3].trim()).uppercase().ifEmpty { coinId.uppercase() },
            name = untext(f[4].trim()).ifEmpty { coinId },
            type = type,
            quantity = quantity,
            priceUsd = price,
            feeUsd = fee,
            date = date.toString(),
            note = untext(f[8].trim()).take(NOTE_MAX),
        )
    }
    return CsvImport.Ok(ops, skipped)
}

const val NOTE_MAX = 200
