package app.criptopanel.alerts

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BinanceTest {
    private val binance = listOf(
        BinancePriceDto("BTCUSDT", "81822.00"),
        BinancePriceDto("ETHUSDT", "2402.66"),
        BinancePriceDto("ABCUSDT", "5.0"),
        BinancePriceDto("USDCUSDT", "1.0001"),
    )

    @Test fun `pairs matched by symbol and close price`() {
        val coins = listOf(
            CoinRef("bitcoin", "btc", 81_500.0),
            CoinRef("ethereum", "ETH", 2_400.0),
            CoinRef("other-abc", "ABC", 1.0), // same ticker, different coin
            CoinRef("usd-coin", "USDC", 1.0), // stablecoin: polled instead
            CoinRef("unknown", "ZZZ", 1.0),
            CoinRef("no-price", "BTC", null),
        )
        assertEquals(mapOf("bitcoin" to "BTCUSDT", "ethereum" to "ETHUSDT"), binancePairs(coins, binance))
    }

    @Test fun `mini ticker parsed with 24h change`() {
        val t = parseMiniTicker(
            """{"stream":"btcusdt@miniTicker","data":{"e":"24hrMiniTicker","E":1,"s":"BTCUSDT","c":"81834.43","o":"83484.01","h":"1","l":"1","v":"1","q":"1"}}""",
        )!!
        assertEquals("BTCUSDT", t.pair)
        assertEquals(81834.43, t.price, 1e-9)
        assertEquals((81834.43 / 83484.01 - 1) * 100, t.change24h!!, 1e-9)
    }

    @Test fun `garbage is ignored`() {
        assertNull(parseMiniTicker("not json"))
        assertNull(parseMiniTicker("""{"result":null,"id":1}"""))
        assertNull(parseMiniTicker("""{"data":{"s":"BTCUSDT","c":"0"}}"""))
    }

    @Test fun `stream url lists every pair`() {
        assertEquals(
            "wss://data-stream.binance.vision/stream?streams=btcusdt@miniTicker/ethusdt@miniTicker",
            streamUrl(listOf("ETHUSDT", "BTCUSDT")),
        )
    }
}
