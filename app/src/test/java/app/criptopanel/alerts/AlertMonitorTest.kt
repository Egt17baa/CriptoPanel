package app.criptopanel.alerts

import app.criptopanel.market.FakeCoinGeckoApi
import app.criptopanel.market.FakeCoinLoreApi
import app.criptopanel.market.MarketRepository
import app.criptopanel.portfolio.PortfolioStore
import app.criptopanel.portfolio.buy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException

private class FakeBinance(var fail: Boolean = false) : BinanceApi {
    override suspend fun prices(): List<BinancePriceDto> {
        if (fail) throw IOException()
        return listOf(BinancePriceDto("BTCUSDT", "98000"))
    }
}

private class FakeStream : PriceStream {
    var listener: PriceStreamListener? = null
    val opened = mutableListOf<Set<String>>()
    var closed = 0

    override fun open(pairs: Set<String>, listener: PriceStreamListener): AutoCloseable {
        opened += pairs
        this.listener = listener
        return AutoCloseable { closed++ }
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AlertMonitorTest {
    @get:Rule val tmp = TemporaryFolder()

    private val gecko = FakeCoinGeckoApi()
    private val market = MarketRepository(gecko, FakeCoinLoreApi())
    private val stream = FakeStream()
    private val fired = mutableListOf<FiredAlert>()
    private val alerts by lazy { AlertStore(File(tmp.root, "alerts.json"), Dispatchers.Unconfined) }
    private val portfolio by lazy { PortfolioStore(File(tmp.root, "p.json"), Dispatchers.Unconfined) }

    private fun TestScope.monitor(binance: BinanceApi = FakeBinance()) =
        AlertMonitor(alerts, portfolio, market, binance, stream, { fired += it }, now = { testScheduler.currentTime }).also {
            it.start(backgroundScope)
            runCurrent()
        }

    @Test fun `bitcoin goes live over binance and fires once`() = runTest {
        alerts.upsert(alert(AlertKind.PRICE_ABOVE, 100_000.0))
        val m = monitor()
        assertEquals(listOf(setOf("BTCUSDT")), stream.opened)
        stream.listener!!.onOpen()
        runCurrent()
        assertEquals(MonitorStatus.LIVE, m.status.value)
        stream.listener!!.onTicker(MiniTicker("BTCUSDT", 100_100.0, 1.0))
        stream.listener!!.onTicker(MiniTicker("BTCUSDT", 100_200.0, 1.0))
        runCurrent()
        assertEquals(1, fired.size)
        val saved = alerts.data.value.alerts.single()
        assertFalse(saved.enabled)
        assertTrue(saved.lastTriggered != null)
        // No enabled alert left: nothing watched, stream closed.
        assertEquals(MonitorStatus.IDLE, m.status.value)
        assertEquals(1, stream.closed)
    }

    @Test fun `coins not on binance are polled every minute`() = runTest {
        alerts.upsert(alert(AlertKind.PRICE_ABOVE, 5_000.0, coinId = "ethereum"))
        val m = monitor()
        assertTrue(stream.opened.isEmpty())
        assertEquals(MonitorStatus.POLLING, m.status.value)
        val calls = gecko.idLists.size
        advanceTimeBy(POLL_MS + 1)
        runCurrent()
        assertTrue(gecko.idLists.size > calls)
        assertEquals("ethereum", gecko.idLists.last())
    }

    @Test fun `stream failure reconnects with backoff`() = runTest {
        alerts.upsert(alert(AlertKind.PRICE_ABOVE, 200_000.0))
        val m = monitor()
        stream.listener!!.onFailure()
        runCurrent()
        assertEquals(MonitorStatus.OFFLINE, m.status.value)
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(2, stream.opened.size)
        stream.listener!!.onFailure()
        runCurrent()
        m.reconnectNow()
        runCurrent()
        assertEquals(3, stream.opened.size)
    }

    @Test fun `binance down retries before streaming`() = runTest {
        alerts.upsert(alert(AlertKind.PRICE_ABOVE, 200_000.0))
        val binance = FakeBinance(fail = true)
        val m = monitor(binance)
        assertEquals(MonitorStatus.OFFLINE, m.status.value)
        binance.fail = false
        advanceTimeBy(1_001)
        runCurrent()
        assertEquals(listOf(setOf("BTCUSDT")), stream.opened)
    }

    @Test fun `portfolio alert watches the portfolio coins`() = runTest {
        portfolio.upsert(buy(1.0, 10.0, coin = "bitcoin"))
        alerts.upsert(alert(AlertKind.PORTFOLIO_BELOW, 50_000.0, coinId = null))
        monitor()
        stream.listener!!.onTicker(MiniTicker("BTCUSDT", 40_000.0, 0.0))
        runCurrent()
        assertTrue(fired.single().title.startsWith("Tu cartera ha bajado de"))
    }

    @Test fun `alert already met is reported`() = runTest {
        val m = monitor()
        alerts.upsert(alert(AlertKind.PRICE_ABOVE, 200_000.0))
        runCurrent()
        // The CoinGecko price (98 123) is already above 50 000.
        assertTrue(m.holdsNow(alert(AlertKind.PRICE_ABOVE, 50_000.0)))
        assertFalse(m.holdsNow(alert(AlertKind.PRICE_ABOVE, 200_000.0)))
    }
}
