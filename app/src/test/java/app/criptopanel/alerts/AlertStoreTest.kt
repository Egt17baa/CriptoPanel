package app.criptopanel.alerts

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AlertStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private fun file() = File(tmp.root, "alerts.json")

    @Test fun `saves, toggles, triggers and reads back`() = runTest {
        val s = AlertStore(file(), Dispatchers.Unconfined)
        s.upsert(alert(AlertKind.PRICE_ABOVE, 1.0, id = "x"))
        s.markTriggered("x", 42)
        val back = AlertStore(file(), Dispatchers.Unconfined).apply { load() }.data.value.alerts.single()
        assertFalse(back.enabled)
        assertEquals(42L, back.lastTriggered)
        s.setEnabled("x", true)
        assertTrue(s.data.value.alerts.single().enabled)
        s.delete("x")
        assertTrue(s.data.value.alerts.isEmpty())
    }

    @Test fun `damaged file starts empty`() = runTest {
        file().writeText("[[[")
        val s = AlertStore(file(), Dispatchers.Unconfined).apply { load() }
        assertTrue(s.data.value.alerts.isEmpty())
        assertTrue(File(file().path + ".bad").exists())
    }
}
