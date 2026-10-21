package app.criptopanel.market

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.util.TimeZone

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

/** Time zone IDs accepted by TradingView widgets; any other ID silently falls back to a wrong zone. */
val TRADINGVIEW_TIMEZONES = listOf(
    "Etc/UTC", "Africa/Cairo", "Africa/Casablanca", "Africa/Johannesburg", "Africa/Lagos", "Africa/Nairobi",
    "Africa/Tunis", "America/Anchorage", "America/Argentina/Buenos_Aires", "America/Bogota", "America/Caracas",
    "America/Chicago", "America/El_Salvador", "America/Juneau", "America/Lima", "America/Los_Angeles",
    "America/Mexico_City", "America/New_York", "America/Phoenix", "America/Santiago", "America/Sao_Paulo",
    "America/Toronto", "America/Vancouver", "Asia/Almaty", "Asia/Ashkhabad", "Asia/Bahrain", "Asia/Bangkok",
    "Asia/Chongqing", "Asia/Colombo", "Asia/Dhaka", "Asia/Dubai", "Asia/Ho_Chi_Minh", "Asia/Hong_Kong",
    "Asia/Jakarta", "Asia/Jerusalem", "Asia/Karachi", "Asia/Kathmandu", "Asia/Kolkata", "Asia/Kuwait",
    "Asia/Manila", "Asia/Muscat", "Asia/Nicosia", "Asia/Qatar", "Asia/Riyadh", "Asia/Seoul", "Asia/Shanghai",
    "Asia/Singapore", "Asia/Taipei", "Asia/Tehran", "Asia/Tokyo", "Asia/Yangon", "Atlantic/Reykjavik",
    "Australia/Adelaide", "Australia/Brisbane", "Australia/Perth", "Australia/Sydney", "Europe/Amsterdam",
    "Europe/Athens", "Europe/Belgrade", "Europe/Berlin", "Europe/Bratislava", "Europe/Brussels", "Europe/Bucharest",
    "Europe/Budapest", "Europe/Copenhagen", "Europe/Dublin", "Europe/Helsinki", "Europe/Istanbul", "Europe/Lisbon",
    "Europe/London", "Europe/Luxembourg", "Europe/Madrid", "Europe/Malta", "Europe/Moscow", "Europe/Oslo",
    "Europe/Paris", "Europe/Prague", "Europe/Riga", "Europe/Rome", "Europe/Stockholm", "Europe/Tallinn",
    "Europe/Vienna", "Europe/Vilnius", "Europe/Warsaw", "Europe/Zurich", "Pacific/Auckland", "Pacific/Chatham",
    "Pacific/Fakaofo", "Pacific/Honolulu", "Pacific/Norfolk", "US/Mountain",
)

/**
 * The device zone if TradingView supports it; else a supported zone showing the same clock all year
 * (sampled twice a month), then one with the same offset now, then UTC.
 */
fun tradingViewTimezone(zone: TimeZone, nowMs: Long = System.currentTimeMillis()): String {
    if (zone.id in TRADINGVIEW_TIMEZONES) return zone.id
    val zones = TRADINGVIEW_TIMEZONES.map { TimeZone.getTimeZone(it) }
    val samples = (0 until 24).map { nowMs + it * 15L * 24 * 3_600_000 }
    return (zones.firstOrNull { tz -> samples.all { tz.getOffset(it) == zone.getOffset(it) } }
        ?: zones.firstOrNull { it.getOffset(nowMs) == zone.getOffset(nowMs) })
        ?.id ?: "Etc/UTC"
}

private val widgetDocumentPrefixes = listOf(
    TRADINGVIEW_EMBED_SCRIPT,
    "https://www.tradingview-widget.com/embed-widget/",
    "https://s.tradingview.com/embed-widget/",
)

/** True when a failed request means the chart cannot show: the page, the embed script or the widget's iframe document. */
fun isWidgetLoadFailure(url: String, isMainFrame: Boolean): Boolean =
    isMainFrame || widgetDocumentPrefixes.any { url.startsWith(it) }
