package app.criptopanel.market

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.RenderProcessGoneDetail
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.criptopanel.core.network.AppError
import app.criptopanel.core.ui.InkColor
import app.criptopanel.core.ui.message
import androidx.compose.ui.graphics.toArgb
import java.util.TimeZone

/** TradingView candles for a market index, with the app's own interval chips. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IndexChartScreen(index: MarketIndex, onBack: () -> Unit) {
    var interval by rememberSaveable { mutableStateOf(ChartInterval.DEFAULT) }
    var reloads by remember { mutableIntStateOf(0) }
    val context = LocalContext.current
    val failed = remember { mutableStateOf(!context.isOnline()) }
    val dark = true // The app is always dark.
    val timezone = remember { tradingViewTimezone(TimeZone.getDefault()) }
    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text(index.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás") }
                },
                actions = {
                    IconButton(onClick = {
                        context.openInBrowser(Uri.parse(tradingViewUrl(index, interval, dark, timezone, heightDp = 600)))
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ExitToApp, contentDescription = "Abrir en el navegador")
                    }
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
                BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val heightDp = maxHeight.value.toInt()
                    val url = remember(index, interval, dark, heightDp) {
                        tradingViewUrl(index, interval, dark, timezone, heightDp)
                    }
                    key(url, reloads) {
                        var loading by remember { mutableStateOf(true) }
                        TradingViewWebView(
                            url = url,
                            onLoaded = { loading = false },
                            onLoadError = { failed.value = true },
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (loading) CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun TradingViewWebView(url: String, onLoaded: () -> Unit, onLoadError: () -> Unit, modifier: Modifier) {
    var webView by remember { mutableStateOf<WebView?>(null) }
    // Pause the widget's timers and live data socket while the app is in the background.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, webView) {
        val view = webView
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> view?.onPause()
                Lifecycle.Event.ON_RESUME -> view?.onResume()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    AndroidView(
        modifier = modifier,
        factory = { context ->
            WebView(context).apply {
                // WRAP_CONTENT (the AndroidView default) can give the page a 0 px viewport height.
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                setBackgroundColor(InkColor.toArgb()) // No white flash before the dark widget draws.
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                webChromeClient = WebChromeClient()
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String) = onLoaded()

                    override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                        if (request.isForMainFrame) onLoadError()
                    }

                    override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                        if (request.isForMainFrame && response.statusCode >= 400) onLoadError()
                    }

                    // The renderer can be killed under memory pressure; unhandled, that kills the whole app.
                    // Showing the retry screen removes this WebView (destroyed in onRelease).
                    override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                        onLoadError()
                        return true
                    }

                    // Links out of the widget (logo, attribution) open in the browser, never inside the app.
                    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                        if (!request.isForMainFrame || isWidgetUrl(request.url.toString())) return false
                        context.openInBrowser(request.url)
                        return true
                    }
                }
                loadUrl(url)
                webView = this
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

private fun Context.openInBrowser(uri: Uri) {
    try {
        startActivity(Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (_: ActivityNotFoundException) {
    }
}
