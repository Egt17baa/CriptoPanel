package app.criptopanel.market

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

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

    private fun widgetConfig(html: String): JsonObject {
        val start = html.indexOf("embed-widget-advanced-chart.js")
        assertTrue("missing embed script", start >= 0)
        val json = html.substring(html.indexOf('{', start), html.indexOf("</script>", start)).trim()
        return Json.parseToJsonElement(json).jsonObject
    }

    @Test fun `html embeds the advanced chart with the chosen symbol and interval`() {
        val c = widgetConfig(tradingViewHtml(MarketIndex.TOTAL3, ChartInterval.H12, dark = true, timezone = "Europe/Madrid"))
        assertEquals("CRYPTOCAP:TOTAL3", c["symbol"]!!.jsonPrimitive.content)
        assertEquals("720", c["interval"]!!.jsonPrimitive.content)
        assertEquals("dark", c["theme"]!!.jsonPrimitive.content)
        assertEquals("es", c["locale"]!!.jsonPrimitive.content)
        assertEquals("Europe/Madrid", c["timezone"]!!.jsonPrimitive.content)
        assertEquals("1", c["style"]!!.jsonPrimitive.content)
        assertTrue(c["hide_top_toolbar"]!!.jsonPrimitive.boolean)
        assertTrue(c["autosize"]!!.jsonPrimitive.boolean)
        assertEquals(false, c["allow_symbol_change"]!!.jsonPrimitive.boolean)
    }

    @Test fun `light theme`() =
        assertEquals("light", widgetConfig(tradingViewHtml(MarketIndex.BTC_D, ChartInterval.D1, dark = false, timezone = "UTC"))["theme"]!!.jsonPrimitive.content)
}
