package app.criptopanel.coins

import app.criptopanel.MainDispatcherRule
import app.criptopanel.api
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.network.appJson
import app.criptopanel.enqueueFixture
import app.criptopanel.market.CoinDetailDto
import app.criptopanel.market.FakeCoinGeckoApi
import app.criptopanel.market.FakeCoinLoreApi
import app.criptopanel.market.MarketRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class CoinDetailTest {
    @get:Rule val main = MainDispatcherRule()

    private val server = MockWebServer().apply { start() }
    private val repo = MarketRepository(server.api(), server.api())

    @After fun tearDown() = server.shutdown()

    @Test fun `maps coin detail`() = runTest {
        server.enqueueFixture("cg_coin_bitcoin.json")
        val d = (repo.coin("bitcoin") as Outcome.Ok).value
        assertEquals("BTC", d.symbol)
        assertEquals(98123.45, d.price!!, 1e-9)
        assertEquals(126000.0, d.ath!!, 1e-9)
        assertEquals(21_000_000.0, d.maxSupply!!, 1e-9)
        assertEquals("https://coin-images.coingecko.com/coins/images/1/large/bitcoin.png", d.imageUrl)
        assertFalse(d.description.contains("<a"))
        assertTrue(d.description.startsWith("Bitcoin is the first cryptocurrency."))
        assertTrue(server.takeRequest().path!!.startsWith("/coins/bitcoin?localization=false&tickers=false"))
    }

    @Test fun `chart requests days of range`() = runTest {
        server.enqueueFixture("cg_market_chart.json")
        val prices = (repo.chart("bitcoin", ChartRange.M1) as Outcome.Ok).value
        assertEquals(listOf(95000.0, 96500.5, 98123.45), prices)
        assertTrue(server.takeRequest().path!!.startsWith("/coins/bitcoin/market_chart?days=30"))
    }

    @Test fun `changing range reloads chart`() = runTest(main.dispatcher) {
        val fake = FakeCoinGeckoApi()
        val vm = CoinDetailViewModel("bitcoin", MarketRepository(fake, FakeCoinLoreApi()))
        advanceUntilIdle()
        assertEquals("Bitcoin", vm.state.value.data!!.name)
        vm.setRange(ChartRange.Y1)
        advanceUntilIdle()
        assertEquals(ChartRange.Y1, vm.range.value)
        assertEquals(3, vm.chart.value.data!!.size)
        assertEquals(listOf("7", "365"), fake.chartDays)
    }

    @Test fun `null currency values parse`() {
        val json = """{"id":"x","symbol":"x","name":"X","market_data":{"current_price":{"usd":1.5},"high_24h":{"usd":null}}}"""
        val d = appJson.decodeFromString<CoinDetailDto>(json).toDomain()
        assertEquals(1.5, d.price!!, 1e-9)
        assertNull(d.high24h)
    }
}
