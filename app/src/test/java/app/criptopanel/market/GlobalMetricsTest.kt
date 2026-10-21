package app.criptopanel.market

import app.criptopanel.api
import app.criptopanel.enqueueFixture
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class GlobalMetricsTest {
    private val server = MockWebServer().apply { start() }
    private val api: CoinGeckoApi = server.api()

    @After fun tearDown() = server.shutdown()

    @Test fun `computes total2 and total3`() = runTest {
        server.enqueueFixture("cg_global.json")
        val m = api.global().data.toMetrics()
        assertEquals(3.0e12, m.totalCap, 1.0)
        assertEquals(1.305e12, m.total2, 1.0)
        assertEquals(0.945e12, m.total3, 1.0)
        assertEquals(56.5, m.btcDominance, 1e-9)
        assertEquals(12.0, m.ethDominance, 1e-9)
        assertEquals(1.2e11, m.volume24h, 1.0)
        assertEquals(-1.25, m.capChange24hPct!!, 1e-9)
        assertEquals("/global", server.takeRequest().path)
    }
}
