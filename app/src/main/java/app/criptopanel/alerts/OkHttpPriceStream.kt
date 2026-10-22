package app.criptopanel.alerts

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import java.util.concurrent.TimeUnit

/** Binance combined mini-ticker stream over OkHttp. Pings keep dead connections from going unnoticed. */
class OkHttpPriceStream(base: OkHttpClient) : PriceStream {
    private val client = base.newBuilder().pingInterval(20, TimeUnit.SECONDS).readTimeout(0, TimeUnit.MILLISECONDS).build()

    override fun open(pairs: Set<String>, listener: PriceStreamListener): AutoCloseable {
        val socket = client.newWebSocket(
            Request.Builder().url(streamUrl(pairs)).build(),
            object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) = listener.onOpen()

                override fun onMessage(webSocket: WebSocket, text: String) {
                    parseMiniTicker(text)?.let(listener::onTicker)
                }

                // Binance closes every connection after 24 h; any close means "reconnect".
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                    webSocket.close(1000, null)
                    listener.onFailure()
                }

                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) = listener.onFailure()
            },
        )
        return AutoCloseable { socket.cancel() }
    }
}
