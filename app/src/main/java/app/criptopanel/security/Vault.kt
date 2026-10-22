package app.criptopanel.security

import app.criptopanel.core.network.appJson
import kotlinx.serialization.Serializable
import java.security.SecureRandom
import java.util.Base64

@Serializable
enum class EntryType(val label: String) { LOGIN("Acceso"), SEED("Frase semilla"), NOTE("Nota segura"), TOTP("Código 2FA") }

/** A TOTP account (RFC 6238). */
@Serializable
data class TotpSecret(
    val secret: String,
    val issuer: String = "",
    val account: String = "",
    val algorithm: String = "SHA1",
    val digits: Int = 6,
    val period: Int = 30,
)

@Serializable
data class VaultEntry(
    val id: String,
    val type: EntryType,
    val title: String,
    val username: String = "",
    val password: String = "",
    val url: String = "",
    val notes: String = "",
    /** Wallet recovery words, space separated. */
    val seedWords: String = "",
    val totp: TotpSecret? = null,
    val createdAt: Long,
    val updatedAt: Long,
)

@Serializable
data class VaultKdf(val memoryKiB: Int, val iterations: Int, val parallelism: Int) {
    fun params() = KdfParams(memoryKiB, iterations, parallelism)
}

/**
 * What lives in files/vault.bin (and in an exported backup). A random data key (DEK) encrypts the entries;
 * the DEK itself is stored wrapped by a key derived from the master password with Argon2id. All with AES-256-GCM.
 * Nothing in here is readable without the master password (or the device's biometric key, kept elsewhere).
 */
@Serializable
data class VaultFile(
    val format: String = FORMAT,
    /** 1: only the two sealed blobs were authenticated. 2: every field is (see [VaultCrypto]). */
    val version: Int = VERSION,
    val kdf: VaultKdf,
    /** Base64 salt for the master password. */
    val salt: String,
    /** Base64 nonce ‖ DEK encrypted with the master key. */
    val wrappedKey: String,
    /** Base64 nonce ‖ entries JSON encrypted with the DEK. */
    val data: String,
    /** Last unlock with the master password (biometrics stop working 7 days after it). */
    val masterCheckedAt: Long = 0,
    /** Bumped on every save, and authenticated with the data: an older data blob cannot be spliced in. */
    val revision: Long = 0,
) {
    companion object {
        const val FORMAT = "criptopanel-vault"
        const val VERSION = 2
    }
}

private val AAD_KEY_V1 = "criptopanel-vault-key-v1".toByteArray()
private val AAD_DATA_V1 = "criptopanel-vault-data-v1".toByteArray()
private val b64 = Base64.getEncoder()
private val unb64 = Base64.getDecoder()

/** The plain fields of [f], in a fixed order, as associated data: changing any of them makes opening fail. */
private fun fieldsAad(label: String, f: VaultFile, vararg extra: Any): ByteArray =
    (listOf(label, f.format, f.version, f.kdf.memoryKiB, f.kdf.iterations, f.kdf.parallelism, f.salt) + extra)
        .joinToString("\u0000").toByteArray(Charsets.UTF_8)

/** Version 2 binds the format, version, Argon2id cost and salt to the wrapped key. */
private fun keyAad(f: VaultFile) = if (f.version == 1) AAD_KEY_V1 else fieldsAad("criptopanel-vault-key-v2", f)

/**
 * Version 2 binds the same fields plus the revision and the last master check to the entries: a phone with root
 * can neither stretch the 7-day biometric window nor mix an older data blob into the current file.
 */
private fun dataAad(f: VaultFile) =
    if (f.version == 1) AAD_DATA_V1 else fieldsAad("criptopanel-vault-data-v2", f, f.revision, f.masterCheckedAt)

/** Encrypts and then wipes [plain]. */
private fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray, random: SecureRandom, wipe: Boolean = true): String {
    val nonce = randomBytes(NONCE_BYTES, random)
    try {
        return b64.encodeToString(nonce + aead(Aead.AES_256_GCM, true, key, nonce, aad, plain))
    } finally {
        if (wipe) plain.fill(0)
    }
}

private fun open(key: ByteArray, sealed: String, aad: ByteArray): ByteArray {
    val raw = try {
        unb64.decode(sealed)
    } catch (e: IllegalArgumentException) {
        throw CryptoFailure()
    }
    if (raw.size < NONCE_BYTES + TAG_BYTES) throw CryptoFailure()
    return aead(Aead.AES_256_GCM, false, key, raw.copyOfRange(0, NONCE_BYTES), aad, raw.copyOfRange(NONCE_BYTES, raw.size))
}

object VaultCrypto {
    /** A new vault: fresh DEK wrapped with [master]. Returns the file and the open DEK (caller keeps it while unlocked). */
    fun create(master: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): Pair<VaultFile, ByteArray> {
        val dek = randomBytes(KEY_BYTES, random)
        try {
            return wrap(dek, emptyList(), master, now, kdf, random) to dek
        } catch (e: Exception) {
            dek.fill(0)
            throw e
        }
    }

    /** A version-2 file holding [entries] under [dek], with [dek] wrapped by [master] (fresh salt). */
    private fun wrap(dek: ByteArray, entries: List<VaultEntry>, master: CharArray, now: Long, kdf: KdfParams, random: SecureRandom, revision: Long = 0): VaultFile {
        val salt = randomBytes(SALT_BYTES, random)
        val kek = argon2id(master, salt, kdf)
        try {
            val plain = VaultFile(
                kdf = VaultKdf(kdf.memoryKiB, kdf.iterations, kdf.parallelism),
                salt = b64.encodeToString(salt),
                wrappedKey = "",
                data = "",
                masterCheckedAt = now,
                revision = revision,
            )
            return plain.copy(
                wrappedKey = seal(kek, dek, keyAad(plain), random, wipe = false), // The open DEK stays in use.
                data = seal(dek, encodeEntries(entries), dataAad(plain), random),
            )
        } finally {
            kek.fill(0)
        }
    }

    /** The DEK, or [CryptoFailure] for a wrong master password or a damaged or altered file. */
    fun unlock(file: VaultFile, master: CharArray): ByteArray {
        if (file.format != VaultFile.FORMAT || file.version !in 1..VaultFile.VERSION) throw CryptoFailure("No es una bóveda de CriptoPanel")
        val kdf = file.kdf.params().also { it.checkSane() }
        val salt = try {
            unb64.decode(file.salt)
        } catch (e: IllegalArgumentException) {
            throw CryptoFailure()
        }
        val kek = argon2id(master, salt, kdf)
        try {
            return open(kek, file.wrappedKey, keyAad(file)).also { if (it.size != KEY_BYTES) throw CryptoFailure() }
        } finally {
            kek.fill(0)
        }
    }

    fun read(file: VaultFile, dek: ByteArray): List<VaultEntry> {
        val json = open(dek, file.data, dataAad(file))
        try {
            return appJson.decodeFromString<List<VaultEntry>>(json.toString(Charsets.UTF_8))
        } catch (e: kotlinx.serialization.SerializationException) {
            throw CryptoFailure()
        } finally {
            json.fill(0)
        }
    }

    /** Re-encrypts all entries with a fresh nonce and the next revision. Version 2 only (see [afterMasterUnlock]). */
    fun write(file: VaultFile, dek: ByteArray, entries: List<VaultEntry>, random: SecureRandom = secureRandom): VaultFile {
        check(file.version == VaultFile.VERSION) { "Old vault format: unlock with the master password first" }
        val next = file.copy(revision = file.revision + 1)
        return next.copy(data = seal(dek, encodeEntries(entries), dataAad(next), random))
    }

    /**
     * After the master password opened [file]: records the check (re-sealing, since it is authenticated) and turns a
     * version-1 file into version 2, which needs the master password once more to wrap the key with the new binding.
     */
    fun afterMasterUnlock(file: VaultFile, dek: ByteArray, master: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): VaultFile {
        val entries = read(file, dek)
        return if (file.version == 1) {
            wrap(dek, entries, master, now, kdf, random)
        } else {
            write(file.copy(masterCheckedAt = now), dek, entries, random)
        }
    }

    /**
     * Same entries under a new master password, new salt and a **new data key**: an old export plus the old (maybe
     * leaked) password must not open anything saved after the change. Returns the file and the new open DEK.
     */
    fun changeMaster(file: VaultFile, dek: ByteArray, newMaster: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): Pair<VaultFile, ByteArray> {
        val entries = read(file, dek)
        val newDek = randomBytes(KEY_BYTES, random)
        try {
            return wrap(newDek, entries, newMaster, now, kdf, random, file.revision + 1) to newDek
        } catch (e: Exception) {
            newDek.fill(0)
            throw e
        }
    }

    private fun encodeEntries(entries: List<VaultEntry>): ByteArray = appJson.encodeToString(entries).toByteArray(Charsets.UTF_8)

    fun encodeFile(file: VaultFile): String = appJson.encodeToString(VaultFile.serializer(), file)

    fun decodeFile(text: String): VaultFile = try {
        appJson.decodeFromString(VaultFile.serializer(), text)
    } catch (e: Exception) {
        throw CryptoFailure("No es una bóveda de CriptoPanel")
    }

    /**
     * Whether a biometric unlock may proceed: only for version 2 (where the date is authenticated, so it is checked
     * again when the entries are read), within 7 days of the last master check and never with a date in the future.
     */
    fun biometricAllowed(file: VaultFile, now: Long): Boolean =
        file.version == VaultFile.VERSION && now - file.masterCheckedAt in 0 until MASTER_RECHECK_MS
}

/** Biometrics need the master password again after this long. */
const val MASTER_RECHECK_MS = 7L * 24 * 60 * 60_000
/** Locks after this long without use. */
const val VAULT_IDLE_LOCK_MS = 5 * 60_000L

/** Words in a seed phrase, and whether the count is one BIP-39 allows. */
fun seedWordCount(words: String): Pair<Int, Boolean> {
    val n = words.trim().split(Regex("\\s+")).count { it.isNotEmpty() }
    return n to (n in setOf(12, 15, 18, 21, 24))
}
