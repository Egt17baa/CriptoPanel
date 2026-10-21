package app.criptopanel.core.format

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

private const val DASH = "—"

private fun twoDecimals(locale: Locale, pattern: String = "#,##0.00") =
    DecimalFormat(pattern, DecimalFormatSymbols.getInstance(locale))

/** Price in USD. Values below 1 keep 4 significant digits so tiny prices never read as 0,00. */
fun formatUsd(value: Double?, locale: Locale = Locale.getDefault()): String {
    if (value == null || value.isNaN()) return DASH
    if (abs(value) >= 1 || value == 0.0) return "${twoDecimals(locale).format(value)} $"
    var rounded = BigDecimal(value).round(MathContext(4)).stripTrailingZeros()
    if (rounded.scale() < 2) rounded = rounded.setScale(2)
    val separator = DecimalFormatSymbols.getInstance(locale).decimalSeparator
    return "${rounded.toPlainString().replace('.', separator)} $"
}

/** Large USD amounts with K/M/B/T suffixes, e.g. "3,21 T $". */
fun formatCompactUsd(value: Double?, locale: Locale = Locale.getDefault()): String {
    if (value == null || value.isNaN()) return DASH
    val (divisor, suffix) = when {
        abs(value) >= 1e12 -> 1e12 to " T"
        abs(value) >= 1e9 -> 1e9 to " B"
        abs(value) >= 1e6 -> 1e6 to " M"
        abs(value) >= 1e3 -> 1e3 to " K"
        else -> 1.0 to ""
    }
    return "${twoDecimals(locale).format(value / divisor)}$suffix $"
}

/** Signed percentage, e.g. "+2,35 %". */
fun formatPercent(value: Double?, locale: Locale = Locale.getDefault()): String {
    if (value == null || value.isNaN()) return DASH
    return "${twoDecimals(locale, "+#,##0.00;-#,##0.00").format(value)} %"
}
