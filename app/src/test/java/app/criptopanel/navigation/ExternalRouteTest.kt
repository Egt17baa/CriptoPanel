package app.criptopanel.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ExternalRouteTest {
    @Test fun `only notification routes are accepted`() {
        assertEquals("portfolio?tab=2", allowedExternalRoute("portfolio?tab=2")) // ROUTE_ALERTS
        assertEquals("coin/bitcoin", allowedExternalRoute("coin/bitcoin"))
        assertEquals("coin/usd%2Bcoin", allowedExternalRoute("coin/usd%2Bcoin"))
        listOf(null, "", "security/vault", "vault/edit?id=x", "operation?coinId=btc", "coin/", "coin/a/b", "coin/x?y=1", "nope").forEach {
            assertNull(it, allowedExternalRoute(it))
        }
    }
}
