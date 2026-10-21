package app.criptopanel.market

import app.criptopanel.api
import app.criptopanel.core.network.AppError
import app.criptopanel.core.network.Outcome
import app.criptopanel.enqueueFixture
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MarketRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private var clock = 1_000_000L
    private val repo = MarketRepository(server.api(), server.api(), now = { clock })

    @After fun tearDown() = server.shutdown()

    @Test fun `parses coins including nulls`() = runTest {
        server.enqueueFixture("cg_markets.json")
        val coins = (repo.topCoins() as Outcome.Ok).value
        assertEquals(3, coins.size)
        assertEquals("BTC", coins[0].symbol)
        assertEquals(listOf(95000.0, 96000.0, 98123.45), coins[0].sparkline7d)
        assertNull(coins[2].marketCap)
        assertNull(coins[2].change24h)
        assertNull(coins[2].rank)
        assertEquals(emptyList<Double>(), coins[2].sparkline7d)
    }

    @Test fun `uses cache within 60s`() = runTest {
        repeat(2) { server.enqueueFixture("cg_markets.json") }
        repo.topCoins()
        clock += 59_000
        repo.topCoins()
        assertEquals(1, server.requestCount)
    }

    @Test fun `refetches after 60s or when forced`() = runTest {
        repeat(3) { server.enqueueFixture("cg_markets.json") }
        repo.topCoins()
        repo.topCoins(force = true)
        clock += 61_000
        repo.topCoins()
        assertEquals(3, server.requestCount)
    }

    @Test fun `request has top 100 query`() = runTest {
        server.enqueueFixture("cg_markets.json")
        repo.topCoins()
        val path = server.takeRequest().path!!
        assertTrue(path, path.startsWith("/coins/markets?vs_currency=usd&order=market_cap_desc&per_page=100&page=1&sparkline=true"))
    }

    @Test fun `error does not replace cache`() = runTest {
        server.enqueueFixture("cl_global.json")
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429)) }
        assertTrue(repo.global() is Outcome.Ok)
        assertEquals(Outcome.Err(AppError.RateLimited), repo.global(force = true))
        assertEquals(10.95, (repo.global() as Outcome.Ok).value.ethDominance, 1e-9)
    }

    @Test fun `global metrics come from CoinLore`() = runTest {
        server.enqueueFixture("cl_global.json")
        val m = (repo.global() as Outcome.Ok).value
        val total = 2758813436905.867
        assertEquals(total, m.totalCap, 1.0)
        assertEquals(total * (1 - 0.6049), m.total2, 1.0)
        assertEquals(total * (1 - 0.6049 - 0.1095), m.total3, 1.0)
        assertEquals(60.49, m.btcDominance, 1e-9)
        assertEquals(10.95, m.ethDominance, 1e-9)
        assertEquals(112348604740.86633, m.volume24h, 1.0)
        assertEquals(2.44, m.capChange24hPct!!, 1e-9)
        assertEquals("/global/", server.takeRequest().path)
    }

    @Test fun `top coins fall back to CoinLore when CoinGecko keeps rate limiting`() = runTest {
        repeat(3) { server.enqueue(MockResponse().setResponseCode(429)) }
        server.enqueueFixture("cl_tickers.json")
        val coins = (repo.topCoins() as Outcome.Ok).value
        assertEquals(listOf("bitcoin", "ethereum", "tether"), coins.map { it.id })
        assertEquals(listOf("BTC", "ETH", "USDT"), coins.map { it.symbol })
        assertEquals(1, coins[0].rank)
        assertTrue(coins[0].price!! > 0)
        assertNull(coins[2].marketCap)
        assertNull(coins[2].change7d)
        assertEquals(emptyList<Double>(), coins[0].sparkline7d)
        repeat(3) { server.takeRequest() }
        assertEquals("/tickers/?start=0&limit=100", server.takeRequest().path)
    }
}
