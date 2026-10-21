package app.criptopanel.market

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/** Market-wide indices charted with TradingView's CRYPTOCAP data. */
enum class MarketIndex(val tvSymbol: String, val title: String) {
    TOTAL("CRYPTOCAP:TOTAL", "Capitalización total"),
    TOTAL2("CRYPTOCAP:TOTAL2", "TOTAL2 (sin BTC)"),
    TOTAL3("CRYPTOCAP:TOTAL3", "TOTAL3 (sin BTC ni ETH)"),
    BTC_D("CRYPTOCAP:BTC.D", "Dominancia BTC"),
    ETH_D("CRYPTOCAP:ETH.D", "Dominancia ETH"),
}

enum class ChartInterval(val label: String, val tvCode: String) {
    H1("1h", "60"),
    H2("2h", "120"),
    H4("4h", "240"),
    H12("12h", "720"),
    D1("1D", "D"),
    W1("1S", "W");

    companion object {
        val DEFAULT = H4
    }
}

const val TRADINGVIEW_BASE_URL = "https://www.tradingview.com"
const val TRADINGVIEW_EMBED_SCRIPT =
    "https://s3.tradingview.com/external-embedding/embed-widget-advanced-chart.js"

/** Full-page HTML for TradingView's free Advanced Chart widget; the app's chips pick the interval. */
fun tradingViewHtml(index: MarketIndex, interval: ChartInterval, dark: Boolean, timezone: String): String {
    val config = buildJsonObject {
        put("autosize", true)
        put("symbol", index.tvSymbol)
        put("interval", interval.tvCode)
        put("timezone", timezone)
        put("theme", if (dark) "dark" else "light")
        put("style", "1")
        put("locale", "es")
        put("hide_top_toolbar", true)
        put("hide_side_toolbar", true)
        put("allow_symbol_change", false)
        put("save_image", false)
        put("support_host", TRADINGVIEW_BASE_URL)
    }
    val background = if (dark) "#131722" else "#ffffff"
    return """
        <!DOCTYPE html>
        <html>
        <head>
        <meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html, body { margin: 0; height: 100%; background: $background; }</style>
        </head>
        <body>
        <div class="tradingview-widget-container" style="height:100%;width:100%">
          <div class="tradingview-widget-container__widget" style="height:100%;width:100%"></div>
          <script type="text/javascript" src="$TRADINGVIEW_EMBED_SCRIPT" async>
          $config
          </script>
        </div>
        </body>
        </html>
    """.trimIndent()
}
