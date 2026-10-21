package app.criptopanel.core.format

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

class FormattersTest {
    private val es = Locale("es", "ES")

    @Test fun `usd above one uses two decimals`() = assertEquals("98.123,46 $", formatUsd(98123.456, es))

    @Test fun `usd below one keeps four significant digits`() = assertEquals("0,00001234 $", formatUsd(0.000012341, es))

    @Test fun `usd between cents`() = assertEquals("0,5432 $", formatUsd(0.54321, es))

    @Test fun `null is a dash`() {
        assertEquals("—", formatUsd(null, es))
        assertEquals("—", formatPercent(null, es))
        assertEquals("—", formatCompactUsd(null, es))
    }

    @Test fun `compact suffixes`() {
        assertEquals("3,21 T $", formatCompactUsd(3.214e12, es))
        assertEquals("945,00 B $", formatCompactUsd(9.45e11, es))
        assertEquals("12,30 M $", formatCompactUsd(1.23e7, es))
        assertEquals("999,00 $", formatCompactUsd(999.0, es))
    }

    @Test fun `percent has sign`() {
        assertEquals("+2,35 %", formatPercent(2.345, es))
        assertEquals("-0,50 %", formatPercent(-0.5, es))
    }

    @Test fun `compact without currency`() {
        assertEquals("19,80 M", formatCompact(1.98e7, es))
        assertEquals("—", formatCompact(null, es))
    }
}
