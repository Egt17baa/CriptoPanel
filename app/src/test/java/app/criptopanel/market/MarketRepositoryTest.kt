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
    private val repo = MarketRepository(server.api(), now = { clock })

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
        server.enqueueFixture("cg_global.json")
        server.enqueue(MockResponse().setResponseCode(429))
        assertTrue(repo.global() is Outcome.Ok)
        assertEquals(Outcome.Err(AppError.RateLimited), repo.global(force = true))
        assertEquals(1.305e12, (repo.global() as Outcome.Ok).value.total2, 1.0)
    }
}
