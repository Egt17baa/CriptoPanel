package app.criptopanel.portfolio

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PortfolioStoreTest {
    @get:Rule val tmp = TemporaryFolder()

    private fun file() = File(tmp.root, "portfolio.json")
    private fun store() = PortfolioStore(file(), Dispatchers.Unconfined)

    @Test fun `saves and reads back`() = runTest {
        val a = buy(1.0, 10.0)
        store().apply { assertNull(upsert(a)); toggleFavorite("ethereum") }
        val again = store().apply { load() }.data.value
        assertEquals(listOf(a), again.operations)
        assertEquals(listOf("ethereum"), again.favorites)
    }

    @Test fun `upsert replaces by id and flattens note newlines`() = runTest {
        val s = store()
        val a = buy(1.0, 10.0)
        s.upsert(a)
        s.upsert(a.copy(quantity = 2.0, note = "uno\ndos"))
        assertEquals(2.0, s.data.value.operations.single().quantity, 0.0)
        assertEquals("uno dos", s.data.value.operations.single().note)
    }

    @Test fun `uncovered sell is refused and nothing changes`() = runTest {
        val s = store()
        assertEquals(SHORTFALL_MESSAGE, s.upsert(sell(1.0, 10.0)))
        assertTrue(s.data.value.operations.isEmpty())
        assertTrue(!file().exists())
    }

    @Test fun `deleting the buy behind a sell is refused`() = runTest {
        val s = store()
        val b = buy(1.0, 10.0)
        s.upsert(b)
        s.upsert(sell(1.0, 10.0))
        assertEquals(SHORTFALL_MESSAGE, s.delete(b.id))
        assertEquals(2, s.data.value.operations.size)
    }

    @Test fun `favorite toggles`() = runTest {
        val s = store()
        s.toggleFavorite("bitcoin")
        s.toggleFavorite("bitcoin")
        assertTrue(s.data.value.favorites.isEmpty())
    }

    @Test fun `damaged file is kept aside and the store starts empty`() = runTest {
        file().writeText("{not json")
        val s = store().apply { load() }
        assertTrue(s.data.value.operations.isEmpty())
        assertTrue(File(file().path + ".bad").exists())
    }

    @Test fun `import adds new operations once`() = runTest {
        val s = store()
        val csv = listOf(buy(1.0, 10.0), sell(0.5, 20.0)).toCsv()
        assertEquals(2, (s.importCsv(csv) as CsvImport.Ok).operations.size)
        assertEquals(2, (s.importCsv(csv) as CsvImport.Ok).skipped)
        assertEquals(2, s.data.value.operations.size)
    }

    @Test fun `import that leaves a sell uncovered imports nothing`() = runTest {
        val s = store()
        assertTrue(s.importCsv(listOf(sell(1.0, 10.0)).toCsv()) is CsvImport.Error)
        assertTrue(s.data.value.operations.isEmpty())
    }
}
