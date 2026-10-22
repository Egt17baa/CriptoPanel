package app.criptopanel.core.network

import app.criptopanel.core.format.DisplayCurrency
import app.criptopanel.core.format.formatCompactUsd
import app.criptopanel.core.format.formatMoney
import app.criptopanel.core.format.formatSignedUsd
import app.criptopanel.core.format.formatUsd
import app.criptopanel.portfolio.buy
import app.criptopanel.portfolio.inDisplayCurrency
import app.criptopanel.portfolio.positions
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.Locale

class FxTest {
    private val es = Locale("es", "ES")
    private val eur = DisplayCurrency.eur(0.9)

    @Test fun `amounts shown in euros`() {
        assertEquals("90,00 €", formatUsd(100.0, es, eur))
        assertEquals("100,00 $", formatUsd(100.0, es, DisplayCurrency.USD))
        assertEquals("9,00 B €", formatCompactUsd(1e10, es, eur))
        assertEquals("-9,00 €", formatSignedUsd(-10.0, es, eur))
        assertEquals("12,50 €", formatMoney(12.5, es, "€"))
        assertEquals(100.0, eur.toUsd(90.0), 1e-9)
    }

    private val api = object : FrankfurterApi {
        val ranges = mutableListOf<String>()
        override suspend fun latest(base: String, symbols: String) = FxLatestDto("2026-10-21", mapOf("EUR" to 0.89))
        override suspend fun series(range: String, base: String, symbols: String): FxSeriesDto {
            ranges += range
            // Friday then Monday: no weekend rates, like the ECB.
            return FxSeriesDto(mapOf("2024-01-05" to mapOf("EUR" to 0.91), "2024-01-08" to mapOf("EUR" to 0.92)))
        }
    }

    @Test fun `weekend days take the previous working day`() = runTest {
        val fx = FxRepository(api)
        val rate = (fx.eurPerUsdOn(LocalDate.parse("2024-01-06"), LocalDate.parse("2024-01-08")) as Outcome.Ok).value
        assertEquals(0.91, rate(LocalDate.parse("2024-01-06")), 0.0)
        assertEquals(0.91, rate(LocalDate.parse("2024-01-07")), 0.0)
        assertEquals(0.92, rate(LocalDate.parse("2024-01-08")), 0.0)
        assertEquals(listOf("2023-12-30..2024-01-08"), api.ranges)
        assertEquals(0.89, (fx.eurPerUsd() as Outcome.Ok).value, 0.0)
    }

    @Test fun `portfolio cost uses each day's rate and value today's`() {
        // Bought 1 coin for 100 $ when 1 $ = 0.80 €: paid 80 €. Today 1 $ = 0.90 €.
        val ops = listOf(buy(1.0, 100.0, date = "2025-01-02")).inDisplayCurrency({ 0.80 }, perUsdNow = 0.90)
        val p = positions(ops).single()
        // In "today's dollars": 80 € / 0.90 = 88.89 $, which shows as 80,00 € again.
        assertEquals(80.0, eur.fromUsd(p.cost), 1e-9)
        assertTrue(p.cost < 100.0)
    }
}
