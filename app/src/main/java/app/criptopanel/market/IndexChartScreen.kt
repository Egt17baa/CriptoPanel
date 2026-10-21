package app.criptopanel.market

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.criptopanel.core.network.AppError
import app.criptopanel.core.ui.message
import java.util.TimeZone

/** TradingView candles for a market index, with the app's own interval chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexChartScreen(index: MarketIndex, onBack: () -> Unit) {
    var interval by rememberSaveable { mutableStateOf(ChartInterval.DEFAULT) }
    var reloads by remember { mutableIntStateOf(0) }
    val failed = remember { mutableStateOf(false) }
    val dark = isSystemInDarkTheme()
    val html = remember(index, interval, dark) {
        tradingViewHtml(index, interval, dark, TimeZone.getDefault().id)
    }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(index.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp),
            ) {
                ChartInterval.entries.forEach { i ->
                    FilterChip(
                        selected = i == interval,
                        onClick = {
                            interval = i
                            failed.value = false
                        },
                        label = { Text(i.label) },
                    )
                }
            }
            if (failed.value) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(AppError.Offline.message())
                    Button(
                        onClick = {
                            failed.value = false
                            reloads++
                        },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Reintentar") }
                }
            } else {
                key(html, reloads) {
                    TradingViewWebView(html, onLoadError = { failed.value = true }, modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TradingViewWebView(html: String, onLoadError: () -> Unit, modifier: Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        // The page itself is inline; failing to fetch the widget script means no connection.
                        if (request.isForMainFrame || request.url.toString() == TRADINGVIEW_EMBED_SCRIPT) onLoadError()
                    }
                }
                loadDataWithBaseURL(TRADINGVIEW_BASE_URL, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}
