package app.criptopanel.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.URLDecoder
import java.util.TimeZone

class IndexChartTest {
    @Test fun `indices map to TradingView CRYPTOCAP symbols`() = assertEquals(
        listOf("CRYPTOCAP:TOTAL", "CRYPTOCAP:TOTAL2", "CRYPTOCAP:TOTAL3", "CRYPTOCAP:BTC.D", "CRYPTOCAP:ETH.D"),
        MarketIndex.entries.map { it.tvSymbol },
    )

    @Test fun `intervals in order with TradingView codes`() {
        assertEquals(listOf("1h", "2h", "4h", "12h", "1D", "1S"), ChartInterval.entries.map { it.label })
        assertEquals(listOf("60", "120", "240", "720", "D", "W"), ChartInterval.entries.map { it.tvCode })
        assertEquals(ChartInterval.H4, ChartInterval.DEFAULT)
    }

    private fun widgetConfig(url: String): JsonObject {
        val prefix = "https://www.tradingview-widget.com/embed-widget/advanced-chart/?locale=es#"
        assertTrue(url, url.startsWith(prefix))
        val hash = url.removePrefix(prefix)
        assertFalse("spaces must not become +", hash.contains('+'))
        return Json.parseToJsonElement(URLDecoder.decode(hash, "UTF-8")).jsonObject
    }

    @Test fun `url opens the advanced chart widget with the chosen symbol and interval`() {
        val c = widgetConfig(tradingViewUrl(MarketIndex.TOTAL3, ChartInterval.H12, dark = true, timezone = "Europe/Madrid"))
        assertEquals("CRYPTOCAP:TOTAL3", c["symbol"]!!.jsonPrimitive.content)
        assertEquals("720", c["interval"]!!.jsonPrimitive.content)
        assertEquals("dark", c["theme"]!!.jsonPrimitive.content)
        assertEquals("es", c["locale"]!!.jsonPrimitive.content)
        assertEquals("Europe/Madrid", c["timezone"]!!.jsonPrimitive.content)
        assertEquals("1", c["style"]!!.jsonPrimitive.content)
        assertEquals("100%", c["width"]!!.jsonPrimitive.content)
        assertEquals("100%", c["height"]!!.jsonPrimitive.content)
        assertTrue(c["hide_top_toolbar"]!!.jsonPrimitive.boolean)
        assertTrue(c["autosize"]!!.jsonPrimitive.boolean)
        assertEquals(false, c["allow_symbol_change"]!!.jsonPrimitive.boolean)
    }

    @Test fun `light theme`() =
        assertEquals("light", widgetConfig(tradingViewUrl(MarketIndex.BTC_D, ChartInterval.D1, dark = false, timezone = "UTC"))["theme"]!!.jsonPrimitive.content)

    @Test fun `only the widget page may load inside the app`() {
        assertTrue(isWidgetUrl(tradingViewUrl(MarketIndex.TOTAL, ChartInterval.H1, dark = false, timezone = "Etc/UTC")))
        assertFalse(isWidgetUrl("https://www.tradingview.com/symbols/CRYPTOCAP-BTC.D/"))
    }

    private val now = 1_792_540_800_000L

    @Test fun `supported timezone passes through`() =
        assertEquals("Europe/Madrid", tradingViewTimezone(TimeZone.getTimeZone("Europe/Madrid"), now))

    @Test fun `unsupported zone maps to one with the same clock all year`() {
        val canary = TimeZone.getTimeZone("Atlantic/Canary")
        val mapped = TimeZone.getTimeZone(tradingViewTimezone(canary, now))
        assertTrue(mapped.id, mapped.id in TRADINGVIEW_TIMEZONES)
        val january = 1_799_971_200_000L
        val july = 1_815_609_600_000L
        listOf(now, january, july).forEach { assertEquals(mapped.id, canary.getOffset(it), mapped.getOffset(it)) }
    }

    @Test fun `unsupported zone without twin maps to the same current offset`() {
        val montevideo = TimeZone.getTimeZone("America/Montevideo")
        val mapped = tradingViewTimezone(montevideo, now)
        assertTrue(mapped, mapped in TRADINGVIEW_TIMEZONES)
        assertEquals(montevideo.getOffset(now), TimeZone.getTimeZone(mapped).getOffset(now))
    }

    @Test fun `gmt maps to utc`() {
        assertEquals("Etc/UTC", tradingViewTimezone(TimeZone.getTimeZone("GMT"), now))
        assertEquals("Etc/UTC", tradingViewTimezone(TimeZone.getTimeZone("UTC"), now))
    }

}
