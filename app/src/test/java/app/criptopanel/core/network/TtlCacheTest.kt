package app.criptopanel.core.network

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class TtlCacheTest {
    @Test fun `ttl counts from fetch start so a 60s refresh tick refetches`() = runTest {
        var clock = 0L
        var fetches = 0
        val cache = TtlCache<Int>(now = { clock })
        cache.get(force = false) { fetches++; clock += 500; 1 }
        clock = CACHE_TTL_MS
        cache.get(force = false) { fetches++; 2 }
        assertEquals(2, fetches)
    }
}
