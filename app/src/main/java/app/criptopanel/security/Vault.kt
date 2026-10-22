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
    val version: Int = 1,
    val kdf: VaultKdf,
    /** Base64 salt for the master password. */
    val salt: String,
    /** Base64 nonce ‖ DEK encrypted with the master key. */
    val wrappedKey: String,
    /** Base64 nonce ‖ entries JSON encrypted with the DEK. */
    val data: String,
    /** Last unlock with the master password (biometrics stop working 7 days after it). */
    val masterCheckedAt: Long = 0,
) {
    companion object {
        const val FORMAT = "criptopanel-vault"
    }
}

private val AAD_KEY = "criptopanel-vault-key-v1".toByteArray()
private val AAD_DATA = "criptopanel-vault-data-v1".toByteArray()
private val b64 = Base64.getEncoder()
private val unb64 = Base64.getDecoder()

private fun seal(key: ByteArray, plain: ByteArray, aad: ByteArray, random: SecureRandom): String {
    val nonce = randomBytes(NONCE_BYTES, random)
    return b64.encodeToString(nonce + aead(Aead.AES_256_GCM, true, key, nonce, aad, plain))
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
        val salt = randomBytes(SALT_BYTES, random)
        val kek = argon2id(master, salt, kdf)
        try {
            val file = VaultFile(
                kdf = VaultKdf(kdf.memoryKiB, kdf.iterations, kdf.parallelism),
                salt = b64.encodeToString(salt),
                wrappedKey = seal(kek, dek, AAD_KEY, random),
                data = seal(dek, encodeEntries(emptyList()), AAD_DATA, random),
                masterCheckedAt = now,
            )
            return file to dek
        } finally {
            kek.fill(0)
        }
    }

    /** The DEK, or [CryptoFailure] for a wrong master password or a damaged file. */
    fun unlock(file: VaultFile, master: CharArray): ByteArray {
        if (file.format != VaultFile.FORMAT || file.version != 1) throw CryptoFailure("No es una bóveda de CriptoPanel")
        val kdf = file.kdf.params().also { it.checkSane() }
        val salt = try {
            unb64.decode(file.salt)
        } catch (e: IllegalArgumentException) {
            throw CryptoFailure()
        }
        val kek = argon2id(master, salt, kdf)
        try {
            return open(kek, file.wrappedKey, AAD_KEY).also { if (it.size != KEY_BYTES) throw CryptoFailure() }
        } finally {
            kek.fill(0)
        }
    }

    fun read(file: VaultFile, dek: ByteArray): List<VaultEntry> =
        try {
            appJson.decodeFromString<List<VaultEntry>>(open(dek, file.data, AAD_DATA).toString(Charsets.UTF_8))
        } catch (e: kotlinx.serialization.SerializationException) {
            throw CryptoFailure()
        }

    /** Re-encrypts all entries with a fresh nonce. */
    fun write(file: VaultFile, dek: ByteArray, entries: List<VaultEntry>, random: SecureRandom = secureRandom): VaultFile =
        file.copy(data = seal(dek, encodeEntries(entries), AAD_DATA, random))

    /** Same DEK and entries, wrapped with a new master password and salt. */
    fun changeMaster(file: VaultFile, dek: ByteArray, newMaster: CharArray, now: Long, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): VaultFile {
        val salt = randomBytes(SALT_BYTES, random)
        val kek = argon2id(newMaster, salt, kdf)
        try {
            return file.copy(
                kdf = VaultKdf(kdf.memoryKiB, kdf.iterations, kdf.parallelism),
                salt = b64.encodeToString(salt),
                wrappedKey = seal(kek, dek, AAD_KEY, random),
                masterCheckedAt = now,
            )
        } finally {
            kek.fill(0)
        }
    }

    private fun encodeEntries(entries: List<VaultEntry>): ByteArray = appJson.encodeToString(entries).toByteArray(Charsets.UTF_8)

    fun encodeFile(file: VaultFile): String = appJson.encodeToString(VaultFile.serializer(), file)

    fun decodeFile(text: String): VaultFile = try {
        appJson.decodeFromString(VaultFile.serializer(), text)
    } catch (e: Exception) {
        throw CryptoFailure("No es una bóveda de CriptoPanel")
    }
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
