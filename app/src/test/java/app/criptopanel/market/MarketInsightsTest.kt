package app.criptopanel.market

import app.criptopanel.api
import app.criptopanel.coins.CoinMarket
import app.criptopanel.core.network.AppError
import app.criptopanel.core.network.Outcome
import app.criptopanel.decodeFixture
import app.criptopanel.enqueueFixture
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class MarketInsightsTest {
    private val gecko = FakeCoinGeckoApi()
    private val coinLore = FakeCoinLoreApi()
    private val sentiment = FakeSentimentApi()
    private var clock = 1_000_000L
    private val repo = MarketInsightsRepository(gecko, sentiment, MarketRepository(gecko, coinLore, now = { clock }), now = { clock })

    private fun coin(id: String, change: Double?) =
        CoinMarket(id, null, id.uppercase(), id, null, null, null, null, null, change, null, emptyList())

    @Test fun `movers sort gainers and losers and drop unknown change`() {
        val m = movers(listOf(coin("a", 1.0), coin("b", -3.0), coin("c", null), coin("d", 5.0), coin("e", -1.0), coin("f", 0.0)))
        assertEquals(listOf("d", "a"), m.gainers.map { it.id })
        assertEquals(listOf("b", "e"), m.losers.map { it.id })
    }

    @Test fun `movers ties keep market cap order`() {
        val m = movers(listOf(coin("first", 2.0), coin("second", 2.0)))
        assertEquals(listOf("first", "second"), m.gainers.map { it.id })
    }

    @Test fun `movers limit`() = assertEquals(1, movers(listOf(coin("a", 1.0), coin("b", 2.0)), n = 1).gainers.size)

    @Test fun `trending maps price change and missing rank`() {
        val t = decodeFixture<TrendingDto>("cg_trending.json").toDomain()
        assertEquals(3, t.size)
        assertTrue(t[0].change24h != null)
        assertEquals(t[0].symbol.uppercase(), t[0].symbol)
        assertNull(t[2].rank)
        assertNull(t[2].change24h)
    }

    @Test fun `categories hide empty sectors and sort`() {
        val c = decodeFixture<List<CategoryDto>>("cg_categories.json").toCategories()
        assertEquals(3, c.size)
        assertTrue(c.zipWithNext().all { (a, b) -> a.marketCap >= b.marketCap })
        val byChange = c.sortedBy(CategorySort.CHANGE)
        assertTrue(byChange.zipWithNext().all { (a, b) -> (a.change24h ?: -1e9) >= (b.change24h ?: -1e9) })
    }

    @Test fun `exchange volume converted with btc price or left in btc`() {
        val dtos = decodeFixture<List<ExchangeDto>>("cg_exchanges.json")
        val usd = dtos.toExchanges(btcPrice = 100_000.0)
        assertEquals(dtos[0].volume24hBtc!! * 100_000.0, usd[0].volume24hUsd!!, 1.0)
        assertNull(dtos.toExchanges(btcPrice = null)[0].volume24hUsd)
        assertNull(usd[1].trustScore)
    }

    @Test fun `exchange url must be http`() {
        val e = listOf(ExchangeDto("x", url = "javascript:alert(1)")).toExchanges(null)
        assertNull(e[0].url)
    }

    @Test fun `exchanges use bitcoin price from top coins`() = runTest {
        val e = (repo.exchanges() as Outcome.Ok).value
        val dto = decodeFixture<List<ExchangeDto>>("cg_exchanges.json")[0]
        assertEquals(dto.volume24hBtc!! * 98123.45, e[0].volume24hUsd!!, 1.0)
    }

    @Test fun `fear and greed cached for 10 minutes`() = runTest {
        repo.fearGreed()
        clock += INSIGHTS_TTL_MS - 1
        repo.fearGreed()
        assertEquals(1, sentiment.calls)
        clock += 1
        repo.fearGreed()
        assertEquals(2, sentiment.calls)
    }

    @Test fun `sector detail joins the sector and its coins`() = runTest {
        val d = (repo.categoryDetail("smart-contract-platform") as Outcome.Ok).value
        assertEquals("smart-contract-platform", d.category.id)
        assertTrue(d.category.description!!.startsWith("Smart contract platforms"))
        assertEquals(listOf("bitcoin", "ethereum", "nullcoin"), d.coins.map { it.id })
        assertEquals(listOf<String?>("smart-contract-platform"), gecko.categories)
    }

    @Test fun `blank sector description becomes null`() = runTest {
        assertNull((repo.categoryDetail("layer-1") as Outcome.Ok).value.category.description)
    }

    @Test fun `sector coins cached per sector`() = runTest {
        repo.categoryDetail("layer-1")
        repo.categoryDetail("layer-1")
        repo.categoryDetail("smart-contract-platform")
        assertEquals(listOf<String?>("layer-1", "smart-contract-platform"), gecko.categories)
    }

    @Test fun `unknown sector is a 404`() = runTest {
        assertEquals(Outcome.Err(AppError.Http(404)), repo.categoryDetail("nope"))
    }

    @Test fun `category id goes in the markets query`() = runTest {
        val server = MockWebServer().apply { start() }
        server.enqueueFixture("cg_markets.json")
        server.api<CoinGeckoApi>().markets(category = "layer-1")
        assertTrue(server.takeRequest().path!!.endsWith("&category=layer-1"))
        server.shutdown()
    }

    @Test fun `movers fail when top coins fail`() = runTest {
        gecko.failWith(IOException())
        coinLore.failWith(IOException())
        assertTrue(repo.movers() is Outcome.Err)
    }

    @Test fun `trending endpoint path`() = runTest {
        val server = MockWebServer().apply { start() }
        server.enqueueFixture("cg_trending.json")
        server.enqueueFixture("cg_exchanges.json")
        val api = server.api<CoinGeckoApi>()
        api.trending()
        api.exchanges()
        assertEquals("/search/trending", server.takeRequest().path)
        assertEquals("/exchanges?per_page=50", server.takeRequest().path)
        server.shutdown()
    }
}
