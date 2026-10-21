package app.criptopanel.market

import app.criptopanel.coins.CoinMarket
import app.criptopanel.coins.toDomain
import app.criptopanel.core.network.Outcome
import app.criptopanel.core.network.apiCall
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

const val CACHE_TTL_MS = 60_000L

/** In-memory TTL cache for one value. A failed fetch never replaces what is cached. */
internal class TtlCache<T>(private val now: () -> Long) {
    private data class Cached<T>(val value: T, val at: Long)

    private val mutex = Mutex()
    private var cached: Cached<T>? = null

    suspend fun get(force: Boolean, fetch: suspend () -> T): Outcome<T> = mutex.withLock {
        val hit = cached
        if (!force && hit != null && now() - hit.at < CACHE_TTL_MS) return Outcome.Ok(hit.value)
        val result = apiCall { fetch() }
        if (result is Outcome.Ok) cached = Cached(result.value, now())
        result
    }
}

class MarketRepository(
    private val api: CoinGeckoApi,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val globalCache = TtlCache<GlobalMetrics>(now)
    private val topCache = TtlCache<List<CoinMarket>>(now)

    suspend fun global(force: Boolean = false): Outcome<GlobalMetrics> =
        globalCache.get(force) { api.global().data.toMetrics() }

    suspend fun topCoins(force: Boolean = false): Outcome<List<CoinMarket>> =
        topCache.get(force) { api.markets().map { it.toDomain() } }
}
