package app.criptopanel.portfolio.binance

import java.io.ByteArrayInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler

/** Largest export accepted (Binance caps each one at 10 000 rows, a few MB). */
const val SHEET_MAX_BYTES = 20 * 1024 * 1024
private const val XLSX_MAX_UNZIPPED = 50L * 1024 * 1024
private const val MAX_ROWS = 200_000

class SheetException(message: String) : Exception(message)

/** The rows of a CSV or Excel (.xlsx) file as text cells; the first sheet only. */
fun readSheet(bytes: ByteArray): List<List<String>> {
    if (bytes.size > SHEET_MAX_BYTES) throw SheetException("El archivo es demasiado grande")
    val isZip = bytes.size > 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
    return if (isZip) readXlsx(bytes) else readCsv(bytes.decodeToString().removePrefix("\uFEFF"))
}

/** CSV with commas, semicolons or tabs (whichever the first line uses most), quotes and line breaks in quotes. */
fun readCsv(text: String): List<List<String>> {
    val firstLine = text.lineSequence().firstOrNull().orEmpty()
    val delimiter = listOf(',', ';', '\t').maxBy { d -> firstLine.count { it == d } }
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val cell = StringBuilder()
    var quoted = false
    var i = 0
    fun endCell() {
        row += cell.toString()
        cell.clear()
    }
    fun endRow() {
        endCell()
        if (row.any { it.isNotBlank() }) rows += row
        if (rows.size > MAX_ROWS) throw SheetException("Demasiadas filas")
        row = mutableListOf()
    }
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && text.getOrNull(i + 1) == '"' -> { cell.append('"'); i++ }
            c == '"' -> quoted = !quoted
            !quoted && c == delimiter -> endCell()
            !quoted && (c == '\n' || c == '\r') -> {
                if (c == '\r' && text.getOrNull(i + 1) == '\n') i++
                endRow()
            }
            else -> cell.append(c)
        }
        i++
    }
    if (cell.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}

/** Minimal .xlsx reader: shared strings + first worksheet; formulas are not evaluated (their cached value is read). */
private fun readXlsx(bytes: ByteArray): List<List<String>> {
    val parts = mutableMapOf<String, ByteArray>()
    var total = 0L
    ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
        while (true) {
            val entry = zip.nextEntry ?: break
            val name = entry.name
            val wanted = name == "xl/sharedStrings.xml" || name == "xl/workbook.xml" ||
                (name.startsWith("xl/worksheets/sheet") && name.endsWith(".xml"))
            if (!wanted) continue
            val out = java.io.ByteArrayOutputStream()
            val buf = ByteArray(64 * 1024)
            while (true) {
                val n = zip.read(buf)
                if (n < 0) break
                total += n
                if (total > XLSX_MAX_UNZIPPED) throw SheetException("El archivo Excel es demasiado grande")
                out.write(buf, 0, n)
            }
            parts[name] = out.toByteArray()
        }
    }
    val strings = parts["xl/sharedStrings.xml"]?.let(::sharedStrings).orEmpty()
    val sheetName = parts.keys.filter { it.startsWith("xl/worksheets/sheet") }
        .minByOrNull { it.removePrefix("xl/worksheets/sheet").removeSuffix(".xml").toIntOrNull() ?: Int.MAX_VALUE }
        ?: throw SheetException("El archivo Excel no tiene hojas")
    return sheetRows(parts.getValue(sheetName), strings)
}

private fun saxParse(xml: ByteArray, handler: DefaultHandler) {
    val factory = SAXParserFactory.newInstance().apply {
        isNamespaceAware = false
        // No DTDs at all: no external entities, no entity expansion bombs.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
        runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
        runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
    }
    if (String(xml, 0, minOf(xml.size, 4096), Charsets.UTF_8).contains("<!DOCTYPE", ignoreCase = true)) {
        throw SheetException("Archivo Excel no válido")
    }
    try {
        factory.newSAXParser().parse(ByteArrayInputStream(xml), handler)
    } catch (e: SheetException) {
        throw e
    } catch (e: Exception) {
        throw SheetException("Archivo Excel no válido")
    }
}

private fun sharedStrings(xml: ByteArray): List<String> {
    val out = mutableListOf<String>()
    val current = StringBuilder()
    var inText = false
    saxParse(xml, object : DefaultHandler() {
        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
            when (qName.substringAfter(':')) {
                "si" -> current.clear()
                "t" -> inText = true
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (qName.substringAfter(':')) {
                "t" -> inText = false
                "si" -> out += current.toString()
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inText) current.append(ch, start, length)
        }
    })
    return out
}

/** Column index of a cell reference such as "C12" (0-based). */
private fun columnOf(ref: String?): Int? {
    if (ref.isNullOrEmpty()) return null
    var col = 0
    for (c in ref) {
        if (!c.isLetter()) break
        col = col * 26 + (c.uppercaseChar() - 'A' + 1)
    }
    return if (col == 0) null else col - 1
}

private fun sheetRows(xml: ByteArray, strings: List<String>): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = sortedMapOf<Int, String>()
    var cellCol = 0
    var nextCol = 0
    var cellType: String? = null
    val value = StringBuilder()
    var inValue = false
    saxParse(xml, object : DefaultHandler() {
        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            when (qName.substringAfter(':')) {
                "row" -> {
                    row = sortedMapOf()
                    nextCol = 0
                }
                "c" -> {
                    cellCol = columnOf(attributes.getValue("r")) ?: nextCol
                    cellType = attributes.getValue("t")
                    value.clear()
                }
                "v", "t" -> inValue = true
            }
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (qName.substringAfter(':')) {
                "v", "t" -> inValue = false
                "c" -> {
                    val raw = value.toString()
                    row[cellCol] = if (cellType == "s") raw.trim().toIntOrNull()?.let { strings.getOrNull(it) }.orEmpty() else raw
                    nextCol = cellCol + 1
                }
                "row" -> {
                    if (row.values.any { it.isNotBlank() }) {
                        val width = (row.keys.maxOrNull() ?: -1) + 1
                        rows += List(width) { row[it].orEmpty() }
                        if (rows.size > MAX_ROWS) throw SheetException("Demasiadas filas")
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inValue) value.append(ch, start, length)
        }
    })
    return rows
}
