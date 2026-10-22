package app.criptopanel.core.format

import java.math.BigDecimal
import java.math.MathContext
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale
import kotlin.math.abs

private const val DASH = "—"

private val compactSteps = listOf(1.0 to "", 1e3 to " K", 1e6 to " M", 1e9 to " B", 1e12 to " T")

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

/** Large amounts with K/M/B/T suffixes and no currency, e.g. "19,80 M". */
fun formatCompact(value: Double?, locale: Locale = Locale.getDefault()): String {
    if (value == null || value.isNaN()) return DASH
    // Suffix by magnitude, then one step up if rounding reaches 1000 (999 999 reads "1,00 M", not "1.000,00 K").
    var i = compactSteps.indexOfLast { abs(value) >= it.first }.coerceAtLeast(0)
    while (i < compactSteps.lastIndex && Math.round(abs(value) / compactSteps[i].first * 100) / 100.0 >= 1000) i++
    val (divisor, suffix) = compactSteps[i]
    return "${twoDecimals(locale).format(value / divisor)}$suffix"
}

/** Large USD amounts with K/M/B/T suffixes, e.g. "3,21 T $". */
fun formatCompactUsd(value: Double?, locale: Locale = Locale.getDefault()): String =
    if (value == null || value.isNaN()) DASH else "${formatCompact(value, locale)} $"

/** Signed percentage, e.g. "+2,35 %". */
fun formatPercent(value: Double?, locale: Locale = Locale.getDefault()): String {
    if (value == null || value.isNaN()) return DASH
    return "${twoDecimals(locale, "+#,##0.00;-#,##0.00").format(value)} %"
}

/** Coin amounts: up to 8 decimals, no trailing zeros, e.g. "0,12345678" or "1.500". */
fun formatQuantity(value: Double?, locale: Locale = Locale.getDefault()): String {
    if (value == null || value.isNaN()) return DASH
    return DecimalFormat("#,##0.########", DecimalFormatSymbols.getInstance(locale)).format(value)
}

/** Signed USD amount, e.g. "+1.204,10 $"; for profits and losses. */
fun formatSignedUsd(value: Double?, locale: Locale = Locale.getDefault()): String {
    if (value == null || value.isNaN()) return DASH
    return "${twoDecimals(locale, "+#,##0.00;-#,##0.00").format(value)} $"
}
