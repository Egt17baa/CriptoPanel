package app.criptopanel.security

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class VaultStoreTest {
    @get:Rule val tmp = TemporaryFolder()
    private var clock = 1_000L
    private fun store(name: String = "vault.bin") =
        VaultStore(File(tmp.root, name), Dispatchers.Unconfined, Dispatchers.Unconfined, now = { clock }, kdf = KdfParams(1024, 1, 1))

    private fun entry(id: String, title: String) = VaultEntry(id, EntryType.NOTE, title, notes = "secreto", createdAt = 0, updatedAt = 0)

    @Test fun `create, add, lock and unlock again`() = runTest {
        val s = store().apply { load() }
        assertEquals(VaultState.NoVault, s.state.value)
        s.create("maestra".toCharArray())
        s.upsert(entry("b", "Banco"))
        s.upsert(entry("a", "agua"))
        assertEquals(listOf("agua", "Banco"), (s.state.value as VaultState.Unlocked).entries.map { it.title })
        s.lock()
        assertEquals(VaultState.Locked, s.state.value)
        assertNull(s.dataKeyCopy())
        val reopened = store().apply { load() }
        assertEquals(VaultState.Locked, reopened.state.value)
        reopened.unlock("maestra".toCharArray())
        assertEquals(2, (reopened.state.value as VaultState.Unlocked).entries.size)
    }

    @Test fun `wrong master keeps it locked`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()); lock() }
        try {
            s.unlock("x".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun `biometric key needs the master every 7 days`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        val key = s.dataKeyCopy()!!
        s.lock()
        clock += MASTER_RECHECK_MS - 1
        s.unlockWithKey(key.copyOf())
        assertTrue(s.state.value is VaultState.Unlocked)
        s.lock()
        clock += 1
        assertFalse(s.biometricAllowed)
        try {
            s.unlockWithKey(key.copyOf())
            fail()
        } catch (e: MasterRequired) {
            // expected
        }
    }

    @Test fun `idle lock after 5 minutes`() = runTest {
        val s = store().apply { load(); create("m".toCharArray()) }
        clock += VAULT_IDLE_LOCK_MS - 1
        assertFalse(s.lockIfIdle())
        s.touch()
        clock += VAULT_IDLE_LOCK_MS
        assertTrue(s.lockIfIdle())
        assertEquals(VaultState.Locked, s.state.value)
    }

    @Test fun `export and import into another phone`() = runTest {
        val a = store("a.bin").apply { load(); create("m".toCharArray()); upsert(entry("1", "Wallet")) }
        val exported = a.exportText()!!
        val b = store("b.bin").apply { load(); create("otra".toCharArray()) }
        try {
            b.importReplace(exported, "mala".toCharArray())
            fail()
        } catch (e: CryptoFailure) {
            // expected
        }
        assertTrue((b.state.value as VaultState.Unlocked).entries.isEmpty())
        b.importReplace(exported, "m".toCharArray())
        assertEquals(listOf("Wallet"), (b.state.value as VaultState.Unlocked).entries.map { it.title })
    }

    @Test fun `change master and destroy`() = runTest {
        val s = store().apply { load(); create("vieja".toCharArray()); upsert(entry("1", "x")) }
        s.changeMaster("nueva".toCharArray())
        s.lock()
        s.unlock("nueva".toCharArray())
        s.destroy()
        assertEquals(VaultState.NoVault, s.state.value)
        assertFalse(File(tmp.root, "vault.bin").exists())
    }
}
