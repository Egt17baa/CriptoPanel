package app.criptopanel.alerts

import app.criptopanel.core.network.Outcome
import app.criptopanel.market.MarketRepository
import app.criptopanel.portfolio.PortfolioStore
import app.criptopanel.portfolio.positions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

enum class MonitorStatus { IDLE, CONNECTING, LIVE, POLLING, OFFLINE }

/** What a live price source reports; called from any thread. */
interface PriceStreamListener {
    fun onOpen()
    fun onTicker(ticker: MiniTicker)
    /** The connection dropped or could not open. */
    fun onFailure()
}

/** A live connection to a price stream for some Binance pairs. */
fun interface PriceStream {
    fun open(pairs: Set<String>, listener: PriceStreamListener): AutoCloseable
}

fun interface AlertNotifier {
    fun notify(fired: FiredAlert)
}

const val POLL_MS = 60_000L
const val MAX_BACKOFF_MS = 60_000L
private const val PAIR_CACHE_MS = 24 * 60 * 60_000L

/**
 * Watches the coins that enabled alerts need: Binance pairs live over the stream, everything else polled from
 * CoinGecko every [pollMs]. A fired alert is saved disabled before it is notified, so it never fires twice.
 */
class AlertMonitor(
    private val alerts: AlertStore,
    private val portfolio: PortfolioStore,
    private val market: MarketRepository,
    private val binance: BinanceApi,
    private val stream: PriceStream,
    private val notifier: AlertNotifier,
    private val now: () -> Long = System::currentTimeMillis,
    private val pollMs: Long = POLL_MS,
) {
    private val engine = AlertEngine()
    private val fireLock = Mutex()

    private val _status = MutableStateFlow(MonitorStatus.IDLE)
    val status: StateFlow<MonitorStatus> = _status.asStateFlow()

    private val _quotes = MutableStateFlow<Map<String, Quote>>(emptyMap())
    val quotes: StateFlow<Map<String, Quote>> = _quotes.asStateFlow()

    private val reconnect = Channel<Unit>(Channel.CONFLATED)
    private var job: Job? = null

    /** Coin → Binance pair for coins already checked (absent = not on Binance), with when each was checked. */
    private val pairCache = mutableMapOf<String, String>()
    private val checkedAt = mutableMapOf<String, Long>()

    val isRunning: Boolean get() = job?.isActive == true

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        job = scope.launch { run() }
    }

    fun stop() {
        job?.cancel()
        job = null
        _status.value = MonitorStatus.IDLE
    }

    /** The network is back: retry the stream now instead of waiting out the backoff. */
    fun reconnectNow() {
        reconnect.trySend(Unit)
    }

    private fun holdings(): Map<String, Double> =
        positions(portfolio.data.value.operations).filter { it.isOpen }.associate { it.coinId to it.quantity }

    /** True when [alert] would fire right now; the UI refuses to enable such an alert. */
    fun holdsNow(alert: Alert): Boolean = synchronized(engine) { engine.holds(alert, holdings(), now()) }

    /** Minutes of price history a quick-move alert still needs. */
    fun warmupMinutes(alert: Alert): Int = synchronized(engine) { engine.warmupMinutes(alert, now()) }

    private fun watched(list: List<Alert>, open: Set<String>): Set<String> {
        val enabled = list.filter { it.enabled }
        val coins = enabled.mapNotNull { it.coinId }.toMutableSet()
        if (enabled.any { it.kind.forPortfolio }) coins += open
        return coins
    }

    private suspend fun run() {
        alerts.load()
        portfolio.load()
        combine(alerts.data, portfolio.data) { a, p ->
            watched(a.alerts, positions(p.operations).filter { it.isOpen }.map { it.coinId }.toSet())
        }
            .distinctUntilChanged()
            .collectLatest { coins -> watch(coins) }
    }

    private suspend fun onQuote(coinId: String, price: Double, change24h: Double?) {
        val fired = synchronized(engine) {
            engine.update(coinId, price, change24h, now())
            _quotes.value = engine.allQuotes
            engine.evaluate(alerts.data.value.alerts, holdings(), now())
        }
        if (fired.isEmpty()) return
        fireLock.withLock {
            // Re-check against the saved state: another source may have fired the same alert meanwhile.
            val stillEnabled = alerts.data.value.alerts.filter { it.enabled }.map { it.id }.toSet()
            fired.filter { it.alert.id in stillEnabled }.forEach { f ->
                alerts.markTriggered(f.alert.id, now())
                notifier.notify(f)
            }
        }
    }

    /** Pairs for [coins], checking Binance only for coins not checked in the last 24 h; null if the check failed. */
    private suspend fun pairsFor(coins: Set<String>): Map<String, String>? {
        val stale = coins.filter { now() - (checkedAt[it] ?: Long.MIN_VALUE / 2) >= PAIR_CACHE_MS }
        if (stale.isNotEmpty()) {
            val refs = (market.marketsByIds(stale) as? Outcome.Ok)?.value ?: return null
            refs.forEach { m -> m.price?.let { onQuote(m.id, it, m.change24h) } }
            val binancePrices = try {
                binance.prices()
            } catch (e: Exception) {
                return null
            }
            val found = binancePairs(refs.map { CoinRef(it.id, it.symbol, it.price) }, binancePrices)
            stale.forEach { id ->
                found[id]?.let { pairCache[id] = it } ?: pairCache.remove(id)
                checkedAt[id] = now()
            }
        }
        return coins.mapNotNull { id -> pairCache[id]?.let { id to it } }.toMap()
    }

    private suspend fun watch(coins: Set<String>) = coroutineScope {
        if (coins.isEmpty()) {
            _status.value = MonitorStatus.IDLE
            awaitCancellation()
        }
        _status.value = MonitorStatus.CONNECTING
        var backoff = 1_000L
        var found = pairsFor(coins)
        while (found == null) {
            _status.value = MonitorStatus.OFFLINE
            withTimeoutOrNull(backoff) { reconnect.receive() }
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
            found = pairsFor(coins)
        }
        val pairs: Map<String, String> = found
        val polled = coins - pairs.keys
        if (polled.isNotEmpty()) launch { poll(polled, onlySource = pairs.isEmpty()) }
        if (pairs.isNotEmpty()) streamLoop(pairs) else awaitCancellation()
    }

    private suspend fun poll(coins: Set<String>, onlySource: Boolean) {
        while (true) {
            when (val r = market.marketsByIds(coins, force = true)) {
                is Outcome.Ok -> {
                    if (onlySource) _status.value = MonitorStatus.POLLING
                    r.value.forEach { m -> m.price?.let { onQuote(m.id, it, m.change24h) } }
                }
                is Outcome.Err -> if (onlySource) _status.value = MonitorStatus.OFFLINE
            }
            delay(pollMs)
        }
    }

    private sealed interface Event {
        data object Open : Event
        data class Tick(val t: MiniTicker) : Event
        data object Failed : Event
    }

    private suspend fun streamLoop(pairs: Map<String, String>) {
        val coinByPair = pairs.entries.associate { (coin, pair) -> pair to coin }
        var backoff = 1_000L
        while (true) {
            _status.value = MonitorStatus.CONNECTING
            val events = Channel<Event>(Channel.UNLIMITED)
            val handle = stream.open(
                pairs.values.toSet(),
                object : PriceStreamListener {
                    override fun onOpen() { events.trySend(Event.Open) }
                    override fun onTicker(ticker: MiniTicker) { events.trySend(Event.Tick(ticker)) }
                    override fun onFailure() { events.trySend(Event.Failed) }
                },
            )
            try {
                for (e in events) {
                    when (e) {
                        Event.Open -> {
                            _status.value = MonitorStatus.LIVE
                            backoff = 1_000L
                        }
                        is Event.Tick -> coinByPair[e.t.pair]?.let { onQuote(it, e.t.price, e.t.change24h) }
                        Event.Failed -> break
                    }
                }
            } finally {
                handle.close()
            }
            _status.value = MonitorStatus.OFFLINE
            withTimeoutOrNull(backoff) { reconnect.receive() }
            backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
        }
    }
}
