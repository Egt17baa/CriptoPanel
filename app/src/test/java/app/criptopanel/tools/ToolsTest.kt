package app.criptopanel.tools

import app.criptopanel.portfolio.PricePoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ToolsTest {
    private val today = LocalDate.parse("2026-10-22")
    private fun day(d: String) = LocalDate.parse(d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() + 12 * 3_600_000

    // Daily prices: 100 until 2026-08-21, then 200.
    private val points = generateSequence(LocalDate.parse("2025-10-20")) { it.plusDays(1) }
        .takeWhile { !it.isAfter(today) }
        .map { PricePoint(day(it.toString()), if (it.isBefore(LocalDate.parse("2026-08-22"))) 100.0 else 200.0) }
        .toList()

    @Test fun `lump sum bought on the first day`() {
        val r = simulate(points, currentPrice = 300.0, amount = 100.0, period = WhatIfPeriod.Y1, monthly = false, today = today, zone = ZoneOffset.UTC)!!
        assertEquals(1, r.purchases)
        assertEquals(1.0, r.units, 1e-12)
        assertEquals(300.0, r.value, 1e-9)
        assertEquals(200.0, r.profitPct!!, 1e-9)
        assertEquals(100.0, r.averagePrice!!, 1e-9)
    }

    @Test fun `monthly DCA buys once a month`() {
        val r = simulate(points, 200.0, 100.0, WhatIfPeriod.M3, monthly = true, today = today, zone = ZoneOffset.UTC)!!
        // 2026-07-22 at 100, 2026-08-22 and 2026-09-22 at 200.
        assertEquals(3, r.purchases)
        assertEquals(300.0, r.invested, 0.0)
        assertEquals(1.0 + 0.5 + 0.5, r.units, 1e-12)
        assertEquals(150.0, r.averagePrice!!, 1e-9)
        assertEquals(r.valueSeries.size, r.investedSeries.size)
        assertEquals(300.0, r.investedSeries.last().price, 0.0)
    }

    @Test fun `no prices or no amount gives nothing`() {
        assertNull(simulate(emptyList(), 1.0, 100.0, WhatIfPeriod.M1, false, today))
        assertNull(simulate(points, 1.0, 0.0, WhatIfPeriod.M1, false, today))
    }

    @Test fun `conversions`() {
        assertEquals(98_000.0, convert(1.0, 98_000.0, 1.0)!!, 0.0)
        assertEquals(0.001, convert(98.0, 1.0, 98_000.0)!!, 1e-12)
        assertEquals(40.0, convert(1.0, 100_000.0, 2_500.0)!!, 1e-9)
        assertNull(convert(1.0, null, 1.0))
    }

    @Test fun `best value per row`() {
        assertEquals(1, bestIndex(listOf(1.0, 3.0, null, 2.0)))
        assertEquals(0, bestIndex(listOf(1.0, 3.0), lowerIsBetter = true))
        assertNull(bestIndex(listOf(1.0, null)))
    }

    @Test fun `rebased series start at 100`() {
        assertEquals(listOf(100.0, 150.0), rebased(listOf(PricePoint(0, 2.0), PricePoint(1, 3.0))))
    }
}
