package app.criptopanel.market

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
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
import androidx.compose.ui.platform.LocalContext
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
    val context = LocalContext.current
    val failed = remember { mutableStateOf(!context.isOnline()) }
    val dark = isSystemInDarkTheme()
    val html = remember(index, interval, dark) {
        tradingViewHtml(index, interval, dark, tradingViewTimezone(TimeZone.getDefault()))
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
                            failed.value = !context.isOnline()
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
                            failed.value = !context.isOnline()
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
                        if (isWidgetLoadFailure(request.url.toString(), request.isForMainFrame)) onLoadError()
                    }

                    // Links out of the widget (logo, attribution) open in the browser, never inside the app.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame) return false
                        try {
                            context.startActivity(Intent(Intent.ACTION_VIEW, request.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: ActivityNotFoundException) {
                        }
                        return true
                    }
                }
                loadDataWithBaseURL(TRADINGVIEW_BASE_URL, html, "text/html", "utf-8", null)
            }
        },
        onRelease = { it.destroy() },
    )
}

private fun Context.isOnline(): Boolean {
    val cm = getSystemService(ConnectivityManager::class.java) ?: return true
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
