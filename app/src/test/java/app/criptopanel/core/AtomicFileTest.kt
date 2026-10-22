package app.criptopanel.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AtomicFileTest {
    @get:Rule val tmp = TemporaryFolder()

    @Test fun `replaces the content and leaves no temporary file`() {
        val f = File(tmp.root, "sub/data.json")
        f.writeTextAtomically("uno")
        f.writeTextAtomically("dos ñ")
        assertEquals("dos ñ", f.readText())
        assertFalse(File(f.path + ".tmp").exists())
    }
}
