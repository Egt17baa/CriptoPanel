package app.criptopanel.portfolio

import app.criptopanel.coins.CoinMarket
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

private var counter = 0

fun op(
    type: OperationType, qty: Double, price: Double, date: String = "2026-01-01", fee: Double = 0.0, coin: String = "bitcoin",
) = Operation("op${counter++}", coin, coin.take(3).uppercase(), coin, null, type, qty, price, fee, date)

fun buy(qty: Double, price: Double, date: String = "2026-01-01", fee: Double = 0.0, coin: String = "bitcoin") =
    op(OperationType.BUY, qty, price, date, fee, coin)

fun sell(qty: Double, price: Double, date: String = "2026-01-02", fee: Double = 0.0, coin: String = "bitcoin") =
    op(OperationType.SELL, qty, price, date, fee, coin)

private fun market(id: String, price: Double?, change: Double?) =
    CoinMarket(id, null, id.uppercase(), id, null, price, null, null, null, change, null, emptyList())

class PortfolioTest {
    @Test fun `average price includes fees`() {
        val p = positions(listOf(buy(1.0, 100.0, fee = 2.0), buy(1.0, 200.0, "2026-01-02"))).single()
        assertEquals(2.0, p.quantity, 1e-12)
        assertEquals(302.0, p.cost, 1e-9)
        assertEquals(151.0, p.averagePrice!!, 1e-9)
        assertEquals(302.0, p.invested, 1e-9)
    }

    @Test fun `partial sell realizes profit at average cost`() {
        val p = positions(listOf(buy(2.0, 100.0), sell(0.5, 300.0, fee = 1.0))).single()
        assertEquals(1.5, p.quantity, 1e-12)
        assertEquals(150.0, p.cost, 1e-9) // average stays 100
        assertEquals(0.5 * 300 - 1 - 0.5 * 100, p.realized, 1e-9)
    }

    @Test fun `selling everything closes the position but keeps realized profit`() {
        val p = positions(listOf(buy(1.0, 100.0), sell(1.0, 150.0))).single()
        assertFalse(p.isOpen)
        assertEquals(0.0, p.cost, 0.0)
        assertNull(p.averagePrice)
        assertEquals(50.0, p.realized, 1e-9)
    }

    @Test fun `rounding dust counts as zero`() {
        val p = positions(listOf(buy(0.3, 10.0), buy(0.6, 10.0), sell(0.9, 10.0))).single()
        assertFalse(p.isOpen)
    }

    @Test fun `same day buy counts before sell whatever the order typed`() {
        val ops = listOf(sell(1.0, 10.0, "2026-01-01"), buy(1.0, 5.0, "2026-01-01"))
        assertNull(firstShortfall(ops))
        assertEquals(5.0, positions(ops).single().realized, 1e-9)
    }

    @Test fun `selling more than held on that date is a shortfall`() {
        val s = sell(2.0, 10.0, "2026-01-02")
        assertEquals(s, firstShortfall(listOf(buy(1.0, 5.0), s, buy(5.0, 5.0, "2026-01-03"))))
    }

    @Test fun `summary values open positions and totals profit`() {
        val ops = listOf(buy(1.0, 100.0), buy(10.0, 1.0, coin = "doge"), sell(5.0, 3.0, coin = "doge"))
        val s = summarize(positions(ops), mapOf("bitcoin" to market("bitcoin", 150.0, 50.0), "doge" to market("doge", 2.0, 0.0)))
        assertEquals(160.0, s.value, 1e-9)
        // bitcoin +50 unrealized; doge realized 5*3-5*1=10, unrealized 5*2-5=5
        assertEquals(65.0, s.profit, 1e-9)
        assertEquals(65.0 / 110.0 * 100, s.profitPct!!, 1e-9)
        // bitcoin rose 50 % in 24 h: from 100 to 150.
        assertEquals(50.0, s.change24h, 1e-9)
        assertEquals(50.0 / 110.0 * 100, s.change24hPct!!, 1e-9)
        assertFalse(s.change24hApprox)
        assertEquals(listOf("bitcoin", "doge"), s.open.map { it.position.coinId })
    }

    @Test fun `missing 24h change makes today's change approximate`() {
        val s = summarize(positions(listOf(buy(1.0, 1.0))), mapOf("bitcoin" to market("bitcoin", 2.0, null)))
        assertTrue(s.change24hApprox)
        assertEquals(0.0, s.change24h, 0.0)
    }

    @Test fun `unknown price leaves value out but keeps the position`() {
        val s = summarize(positions(listOf(buy(1.0, 1.0))), emptyMap())
        assertEquals(0.0, s.value, 0.0)
        assertNull(s.open.single().value)
    }

    @Test fun `allocation groups the rest as others`() {
        val ops = (1..10).map { buy(1.0, it.toDouble(), coin = "c$it") }
        val prices = (1..10).associate { "c$it" to market("c$it", it.toDouble(), 0.0) }
        val slices = allocation(summarize(positions(ops), prices), top = 3)
        assertEquals(listOf("C10", "C9", "C8", "Otras"), slices.map { it.label })
        assertEquals(1.0, slices.sumOf { it.fraction }, 1e-9)
    }

    @Test fun `value series counts only operations dated on or before each point`() {
        val day = { d: String -> LocalDate.parse(d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli() }
        val ops = listOf(buy(1.0, 10.0, "2026-01-02"), buy(1.0, 10.0, "2026-01-04"), sell(2.0, 10.0, "2026-01-06"))
        val prices = mapOf("bitcoin" to listOf(PricePoint(day("2026-01-02"), 10.0), PricePoint(day("2026-01-05"), 20.0)))
        val grid = listOf("2026-01-01", "2026-01-02", "2026-01-04", "2026-01-05", "2026-01-06").map(day)
        val v = valueSeries(ops, prices, grid, ZoneOffset.UTC).map { it.price }
        assertEquals(listOf(0.0, 10.0, 20.0, 40.0, 0.0), v)
    }
}
