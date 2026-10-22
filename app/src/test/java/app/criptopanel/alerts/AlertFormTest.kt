package app.criptopanel.alerts

import app.criptopanel.MainDispatcherRule
import app.criptopanel.market.FakeCoinGeckoApi
import app.criptopanel.market.FakeCoinLoreApi
import app.criptopanel.market.MarketRepository
import app.criptopanel.portfolio.PortfolioStore
import app.criptopanel.portfolio.buy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

@OptIn(ExperimentalCoroutinesApi::class)
class AlertFormTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val market = MarketRepository(FakeCoinGeckoApi(), FakeCoinLoreApi())
    private val store by lazy { AlertStore(File(tmp.root, "a.json"), Dispatchers.Unconfined) }
    private val portfolio by lazy { PortfolioStore(File(tmp.root, "p.json"), Dispatchers.Unconfined) }

    @Test fun `threshold problems`() {
        assertEquals("Ya está por encima de ese valor", thresholdProblem(AlertKind.PRICE_ABOVE, 90.0, 100.0))
        assertNull(thresholdProblem(AlertKind.PRICE_ABOVE, 110.0, 100.0))
        assertEquals("Ya está por debajo de ese valor", thresholdProblem(AlertKind.PORTFOLIO_BELOW, 100.0, 100.0))
        assertNull(thresholdProblem(AlertKind.PRICE_BELOW, 90.0, null))
        assertEquals("El porcentaje debe estar entre 0,1 y 1.000", thresholdProblem(AlertKind.CHANGE_24H, 0.05, null))
        assertNull(thresholdProblem(AlertKind.CHANGE_WINDOW, 5.0, 1.0))
    }

    @Test fun `alert from a coin saves enabled`() = runTest(main.dispatcher) {
        val vm = AlertFormViewModel(store, market, portfolio, editId = null, coinId = "bitcoin", now = { 7L })
        advanceUntilIdle()
        assertEquals("BTC", vm.form.value.coin!!.symbol)
        assertEquals(98123.45, vm.current.value!!, 1e-6)
        vm.update { it.copy(threshold = "90000") }
        vm.save()
        assertEquals("Ya está por encima de ese valor", vm.error.value)
        vm.update { it.copy(threshold = "100000", note = "a\nb") }
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.saved.value)
        val a = store.data.value.alerts.single()
        assertTrue(a.enabled)
        assertEquals("a b", a.note)
        assertEquals(7L, a.createdAt)
        assertNull(a.windowMinutes)
    }

    @Test fun `portfolio alert uses the portfolio value`() = runTest(main.dispatcher) {
        portfolio.upsert(buy(2.0, 10.0, coin = "bitcoin"))
        val vm = AlertFormViewModel(store, market, portfolio, editId = null, coinId = null)
        advanceUntilIdle()
        vm.selectPortfolio()
        advanceUntilIdle()
        assertEquals(2 * 98123.45, vm.current.value!!, 1e-6)
        assertEquals(AlertKind.PORTFOLIO_BELOW, vm.form.value.kind)
        vm.update { it.copy(threshold = "150000") }
        vm.save()
        advanceUntilIdle()
        assertNull(store.data.value.alerts.single().coinId)
    }

    @Test fun `enabling an alert already met is refused`() = runTest(main.dispatcher) {
        val a = alert(AlertKind.PRICE_ABOVE, 50_000.0, enabled = false)
        store.upsert(a)
        val monitor = AlertMonitor(store, portfolio, market, object : BinanceApi {
            override suspend fun prices() = emptyList<BinancePriceDto>()
        }, { _, _ -> AutoCloseable {} }, {})
        val vm = AlertsViewModel(store, monitor, market, portfolio)
        vm.setEnabled(a, true)
        advanceUntilIdle()
        assertFalse(store.data.value.alerts.single().enabled)
        // Sent before anyone collects: the channel keeps it for the screen.
        assertTrue(vm.messages.first().startsWith("Ya está por encima"))
    }
}
