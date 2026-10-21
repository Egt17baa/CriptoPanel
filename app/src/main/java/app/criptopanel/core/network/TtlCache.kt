package app.criptopanel.core.network

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
        val startedAt = now()
        val result = apiCall { fetch() }
        if (result is Outcome.Ok) cached = Cached(result.value, startedAt)
        result
    }
}
