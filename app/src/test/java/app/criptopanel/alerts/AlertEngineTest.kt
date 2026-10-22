package app.criptopanel.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

fun alert(
    kind: AlertKind, threshold: Double, coinId: String? = "bitcoin", window: Int? = null, direction: Direction = Direction.ANY,
    enabled: Boolean = true, id: String = "a",
) = Alert(id, coinId, "BTC", "Bitcoin", null, kind, threshold, window, direction, "", enabled, 0L)

class AlertEngineTest {
    private val engine = AlertEngine(usd = { "%.0f $".format(it) })
    private val min = 60_000L

    @Test fun `price above fires only strictly above`() {
        val a = alert(AlertKind.PRICE_ABOVE, 100.0)
        engine.update("bitcoin", 100.0, null, 0)
        assertTrue(engine.evaluate(listOf(a), emptyMap(), 0).isEmpty())
        engine.update("bitcoin", 100.5, 2.0, 1)
        val f = engine.evaluate(listOf(a), emptyMap(), 1).single()
        assertEquals("BTC ha subido de 100 $", f.title)
        assertTrue(f.text.startsWith("Precio: 101 $"))
    }

    @Test fun `price below and disabled alerts`() {
        engine.update("bitcoin", 90.0, null, 0)
        assertEquals(1, engine.evaluate(listOf(alert(AlertKind.PRICE_BELOW, 95.0)), emptyMap(), 0).size)
        assertTrue(engine.evaluate(listOf(alert(AlertKind.PRICE_BELOW, 95.0, enabled = false)), emptyMap(), 0).isEmpty())
    }

    @Test fun `24h change respects direction`() {
        engine.update("bitcoin", 90.0, -12.0, 0)
        val any = alert(AlertKind.CHANGE_24H, 10.0)
        val up = alert(AlertKind.CHANGE_24H, 10.0, direction = Direction.UP)
        val down = alert(AlertKind.CHANGE_24H, 10.0, direction = Direction.DOWN)
        assertEquals(listOf(any, down), engine.evaluate(listOf(any, up, down), emptyMap(), 0).map { it.alert })
        assertEquals("BTC baja un 12,0 % en 24 h", engine.evaluate(listOf(any), emptyMap(), 0).single().title)
    }

    @Test fun `quick move waits for enough history`() {
        val a = alert(AlertKind.CHANGE_WINDOW, 5.0, window = 15)
        engine.update("bitcoin", 100.0, null, 0)
        engine.update("bitcoin", 110.0, null, 5 * min)
        assertTrue(engine.evaluate(listOf(a), emptyMap(), 5 * min).isEmpty())
        assertEquals(9, engine.warmupMinutes(a, 5 * min))
        engine.update("bitcoin", 110.0, null, 15 * min)
        assertEquals(0, engine.warmupMinutes(a, 15 * min))
        assertEquals("BTC sube un 10,0 % en 15 min", engine.evaluate(listOf(a), emptyMap(), 15 * min).single().title)
    }

    @Test fun `quick move compares with the oldest price inside the window`() {
        engine.update("bitcoin", 50.0, null, 0)
        engine.update("bitcoin", 100.0, null, 50 * min)
        engine.update("bitcoin", 103.0, null, 61 * min)
        // 1 h window starts at minute 1: oldest sample inside it is 100.
        assertEquals(3.0, engine.windowChange("bitcoin", 60 * min, 61 * min)!!, 1e-9)
    }

    @Test fun `history keeps one sample per 10 s and at most 4 h`() {
        (0..30).forEach { engine.update("bitcoin", 1.0, null, it * 1_000L) }
        assertEquals(4, engine.historySize("bitcoin"))
        engine.update("bitcoin", 1.0, null, HISTORY_MS + 25_000L)
        assertEquals(2, engine.historySize("bitcoin"))
    }

    @Test fun `bad prices are ignored`() {
        engine.update("bitcoin", Double.NaN, null, 0)
        engine.update("bitcoin", 0.0, null, 0)
        assertNull(engine.quote("bitcoin"))
    }

    @Test fun `portfolio needs every coin quoted`() {
        val holdings = mapOf("bitcoin" to 1.0, "ethereum" to 10.0)
        val below = alert(AlertKind.PORTFOLIO_BELOW, 50_000.0, coinId = null)
        engine.update("bitcoin", 40_000.0, 0.0, 0)
        assertTrue(engine.evaluate(listOf(below), holdings, 0).isEmpty())
        engine.update("ethereum", 500.0, 0.0, 0)
        assertEquals("Tu cartera ha bajado de 50000 $", engine.evaluate(listOf(below), holdings, 0).single().title)
    }

    @Test fun `portfolio change today`() {
        engine.update("bitcoin", 110.0, 10.0, 0)
        val a = alert(AlertKind.PORTFOLIO_CHANGE_TODAY, 5.0, coinId = null, direction = Direction.UP)
        val f = engine.evaluate(listOf(a), mapOf("bitcoin" to 1.0), 0).single()
        assertEquals("Tu cartera sube un 10,0 % hoy", f.title)
    }

    @Test fun `description text`() {
        val d = alert(AlertKind.CHANGE_WINDOW, 5.0, window = 60, direction = Direction.DOWN).describe({ "$it" }, { "$it %" })
        assertEquals("BTC baja un 5.0 % en 1 h", d)
    }
}
