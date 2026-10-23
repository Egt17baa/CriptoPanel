package app.criptopanel.portfolio

import app.criptopanel.alerts.AlertsData
import app.criptopanel.core.network.appJson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The app's JSON leaves defaults out; the format version must still be written so each file says what it is. */
class SelfDescribingFilesTest {
    @Test fun `portfolio and alerts files always carry their version`() {
        assertTrue(appJson.encodeToString(PortfolioData.serializer(), PortfolioData()).contains("\"version\":1"))
        assertTrue(appJson.encodeToString(AlertsData.serializer(), AlertsData()).contains("\"version\":1"))
        // Older files without it still load.
        assertEquals(PortfolioData(), appJson.decodeFromString(PortfolioData.serializer(), "{}"))
    }
}
