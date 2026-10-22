package app.criptopanel.security

import org.bouncycastle.crypto.InvalidCipherTextException
import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.modes.ChaCha20Poly1305
import org.bouncycastle.crypto.params.AEADParameters
import org.bouncycastle.crypto.params.Argon2Parameters
import org.bouncycastle.crypto.params.KeyParameter
import java.nio.ByteBuffer
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Authenticated ciphers offered; [id] is what the header stores. */
enum class Aead(val id: Byte, val label: String) {
    AES_256_GCM(1, "AES-256-GCM"),
    CHACHA20_POLY1305(2, "ChaCha20-Poly1305"),
    ;

    companion object {
        fun of(id: Byte): Aead = entries.firstOrNull { it.id == id } ?: throw CryptoFailure("Algoritmo desconocido")
    }
}

/** Argon2id cost; stored with every ciphertext so it can be raised later without breaking old ones. */
data class KdfParams(val memoryKiB: Int = 64 * 1024, val iterations: Int = 3, val parallelism: Int = 1) {
    /** Refuses absurd values from a crafted header (memory bombs). */
    fun checkSane() {
        if (memoryKiB !in 8 * parallelism..1024 * 1024 || iterations !in 1..10 || parallelism !in 1..4) {
            throw CryptoFailure("Parámetros de cifrado no válidos")
        }
    }
}

/** Wrong password, damaged or tampered data: deliberately one message, so it leaks nothing about which. */
class CryptoFailure(message: String = "Contraseña incorrecta o datos dañados") : Exception(message)

const val KEY_BYTES = 32
const val SALT_BYTES = 16
const val NONCE_BYTES = 12
const val TAG_BYTES = 16

val secureRandom: SecureRandom by lazy { SecureRandom() }

fun randomBytes(n: Int, random: SecureRandom = secureRandom): ByteArray = ByteArray(n).also(random::nextBytes)

/** Argon2id (RFC 9106, version 0x13) of [password]; the caller wipes the result when done. */
fun argon2id(password: CharArray, salt: ByteArray, p: KdfParams, length: Int = KEY_BYTES): ByteArray {
    val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt)
        .withMemoryAsKB(p.memoryKiB)
        .withIterations(p.iterations)
        .withParallelism(p.parallelism)
        .build()
    val out = ByteArray(length)
    Argon2BytesGenerator().apply { init(params) }.generateBytes(password, out)
    return out
}

/** Raw Argon2id with secret and associated data, only for the RFC 9106 test vector. */
internal fun argon2idRaw(password: ByteArray, salt: ByteArray, secret: ByteArray, ad: ByteArray, p: KdfParams, length: Int): ByteArray {
    val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
        .withVersion(Argon2Parameters.ARGON2_VERSION_13)
        .withSalt(salt).withSecret(secret).withAdditional(ad)
        .withMemoryAsKB(p.memoryKiB).withIterations(p.iterations).withParallelism(p.parallelism)
        .build()
    val out = ByteArray(length)
    Argon2BytesGenerator().apply { init(params) }.generateBytes(password, out)
    return out
}

/** One-shot AEAD: returns ciphertext ‖ tag, or the plaintext; throws [CryptoFailure] on a bad tag. */
fun aead(alg: Aead, encrypt: Boolean, key: ByteArray, nonce: ByteArray, aad: ByteArray, input: ByteArray): ByteArray = when (alg) {
    Aead.AES_256_GCM -> try {
        Cipher.getInstance("AES/GCM/NoPadding").run {
            init(if (encrypt) Cipher.ENCRYPT_MODE else Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
            updateAAD(aad)
            doFinal(input)
        }
    } catch (e: GeneralSecurityException) {
        throw CryptoFailure()
    }
    Aead.CHACHA20_POLY1305 -> try {
        ChaCha20Poly1305().run {
            init(encrypt, AEADParameters(KeyParameter(key), TAG_BYTES * 8, nonce, aad))
            val out = ByteArray(getOutputSize(input.size))
            val n = processBytes(input, 0, input.size, out, 0)
            val total = n + doFinal(out, n)
            if (total == out.size) out else out.copyOf(total)
        }
    } catch (e: InvalidCipherTextException) {
        throw CryptoFailure()
    }
}

/** Header shared by texts and files; the whole of it is authenticated as associated data. */
internal data class Header(
    val kind: Byte,
    val alg: Aead,
    val kdf: KdfParams,
    val salt: ByteArray,
    /** Full 12-byte nonce for texts, 7-byte STREAM prefix for files. */
    val nonce: ByteArray,
) {
    fun bytes(): ByteArray = ByteBuffer.allocate(SIZE_BEFORE_NONCE + nonce.size).apply {
        put(MAGIC); put(VERSION); put(kind); put(alg.id); put(KDF_ARGON2ID)
        putInt(kdf.memoryKiB); put(kdf.iterations.toByte()); put(kdf.parallelism.toByte())
        put(salt); put(nonce)
    }.array()

    companion object {
        val MAGIC = byteArrayOf('C'.code.toByte(), 'P'.code.toByte())
        const val VERSION: Byte = 1
        const val KDF_ARGON2ID: Byte = 1
        const val KIND_TEXT: Byte = 1
        const val KIND_FILE: Byte = 2
        const val SIZE_BEFORE_NONCE = 2 + 1 + 1 + 1 + 1 + 4 + 1 + 1 + SALT_BYTES

        fun nonceSize(kind: Byte) = if (kind == KIND_FILE) STREAM_PREFIX_BYTES else NONCE_BYTES

        /** Parses and validates; [expectedKind] guards against feeding a file header to the text decoder and vice versa. */
        fun parse(buf: ByteBuffer, expectedKind: Byte): Header {
            if (buf.remaining() < SIZE_BEFORE_NONCE) throw CryptoFailure("No es un dato cifrado por CriptoPanel")
            val magic = ByteArray(2).also { buf.get(it) }
            if (!magic.contentEquals(MAGIC)) throw CryptoFailure("No es un dato cifrado por CriptoPanel")
            if (buf.get() != VERSION) throw CryptoFailure("Versión de formato no compatible")
            val kind = buf.get()
            if (kind != expectedKind) throw CryptoFailure(if (kind == KIND_FILE) "Esto es un archivo cifrado" else "Esto es un texto cifrado")
            val alg = Aead.of(buf.get())
            if (buf.get() != KDF_ARGON2ID) throw CryptoFailure("Derivación de clave desconocida")
            val kdf = KdfParams(buf.int, buf.get().toInt() and 0xFF, buf.get().toInt() and 0xFF).also { it.checkSane() }
            val salt = ByteArray(SALT_BYTES).also { buf.get(it) }
            if (buf.remaining() < nonceSize(kind)) throw CryptoFailure()
            val nonce = ByteArray(nonceSize(kind)).also { buf.get(it) }
            return Header(kind, alg, kdf, salt, nonce)
        }
    }
}

const val TEXT_PREFIX = "CP1:"

/** Encrypts [plain] into "CP1:" + URL-safe Base64 of header ‖ ciphertext ‖ tag. */
fun encryptText(plain: String, password: CharArray, alg: Aead = Aead.AES_256_GCM, kdf: KdfParams = KdfParams(), random: SecureRandom = secureRandom): String {
    val header = Header(Header.KIND_TEXT, alg, kdf, randomBytes(SALT_BYTES, random), randomBytes(NONCE_BYTES, random))
    val aad = header.bytes()
    val key = argon2id(password, header.salt, kdf)
    try {
        val sealed = aead(alg, true, key, header.nonce, aad, plain.toByteArray(Charsets.UTF_8))
        return TEXT_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(aad + sealed)
    } finally {
        key.fill(0)
    }
}

/** Decrypts a "CP1:" text; spaces and line breaks added by chat apps are ignored. */
fun decryptText(token: String, password: CharArray): String {
    val clean = token.trim().filterNot { it.isWhitespace() }
    if (!clean.startsWith(TEXT_PREFIX)) throw CryptoFailure("El texto cifrado debe empezar por $TEXT_PREFIX")
    val raw = try {
        Base64.getUrlDecoder().decode(clean.removePrefix(TEXT_PREFIX))
    } catch (e: IllegalArgumentException) {
        throw CryptoFailure("El texto cifrado está incompleto o dañado")
    }
    val buf = ByteBuffer.wrap(raw)
    val header = Header.parse(buf, Header.KIND_TEXT)
    val aad = raw.copyOfRange(0, raw.size - buf.remaining())
    val sealed = raw.copyOfRange(aad.size, raw.size)
    if (sealed.size < TAG_BYTES) throw CryptoFailure()
    val key = argon2id(password, header.salt, header.kdf)
    try {
        return aead(header.alg, false, key, header.nonce, aad, sealed).toString(Charsets.UTF_8)
    } finally {
        key.fill(0)
    }
}
