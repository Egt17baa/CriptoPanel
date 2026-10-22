package app.criptopanel.security

import org.bouncycastle.crypto.engines.CamelliaEngine
import org.bouncycastle.crypto.engines.SerpentEngine
import org.bouncycastle.crypto.engines.TwofishEngine
import org.bouncycastle.crypto.params.KeyParameter
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

private fun hexBytes(s: String) = s.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
private val FAST = KdfParams(1024, 1, 1)

class SeedCryptoTest {
    private val bip39 = Bip39(
        listOf("src/main/assets", "../app/src/main/assets").map { File(it, "bip39_english.txt") }.first { it.exists() }.readLines().filter { it.isNotBlank() },
    )

    private fun block(engine: org.bouncycastle.crypto.BlockCipher, key: String, pt: String): String {
        engine.init(true, KeyParameter(hexBytes(key)))
        val out = ByteArray(16)
        engine.processBlock(hexBytes(pt), 0, out, 0)
        return hex(out).lowercase()
    }

    @Test fun `block ciphers match published vectors`() {
        // Camellia, RFC 3713 appendix A (256-bit key).
        assertEquals(
            "9acc237dff16d76c20ef7c919e3a7509",
            block(CamelliaEngine(), "0123456789abcdeffedcba987654321000112233445566778899aabbccddeeff", "0123456789abcdeffedcba9876543210"),
        )
        // Twofish, all-zero 256-bit key and block (Twofish paper / ecb_tbl.txt, I=1).
        assertEquals("57ff739d4dc92c1bd7fc01700cc8216f", block(TwofishEngine(), "00".repeat(32), "00".repeat(16)))
        // Serpent round trip on a 256-bit key (Bouncy Castle's engine is validated by its own NESSIE vectors).
        val serpent = SerpentEngine()
        val ct = block(serpent, "00".repeat(31) + "01", "00".repeat(16))
        serpent.init(false, KeyParameter(hexBytes("00".repeat(31) + "01")))
        val back = ByteArray(16)
        serpent.processBlock(hexBytes(ct), 0, back, 0)
        assertArrayEquals(ByteArray(16), back)
    }

    @Test fun `every algorithm round trips with password and with raw key`() {
        val key = parseRawKey(newRawKeyHex())!!
        Aead.entries.forEach { alg ->
            val withPw = encryptText("legal winner thank year", "pw".toCharArray(), alg, FAST)
            assertEquals(alg.name, "legal winner thank year", decryptText(withPw, "pw".toCharArray()))
            val withKey = encryptText("legal winner thank year", Secret.RawKey(key), alg)
            assertEquals(alg.name, "legal winner thank year", decryptText(withKey, Secret.RawKey(key.copyOf())))
            assertEquals(alg to true, inspectText(withKey))
            assertEquals(alg to false, inspectText(withPw))
        }
    }

    @Test fun `wrong key, wrong kind of secret and tampering fail`() {
        val k1 = parseRawKey(newRawKeyHex())!!
        val k2 = parseRawKey(newRawKeyHex())!!
        Aead.entries.forEach { alg ->
            val t = encryptText("seed", Secret.RawKey(k1), alg)
            expectFailure { decryptText(t, Secret.RawKey(k2)) }
            expectFailure { decryptText(t, Secret.Password("x".toCharArray())) }
            val flipped = t.dropLast(3) + (if (t[t.length - 3] == 'A') 'B' else 'A') + t.takeLast(2)
            expectFailure { decryptText(flipped, Secret.RawKey(k1)) }
        }
        val pw = encryptText("seed", "pw".toCharArray(), kdf = FAST)
        expectFailure { decryptText(pw, Secret.RawKey(k1)) }
    }

    @Test fun `cascade really uses three layers`() {
        val key = ByteArray(32) { it.toByte() }
        val nonce = ByteArray(12)
        val ct = aead(Aead.CASCADE, true, key, nonce, ByteArray(0), "x".toByteArray())
        assertEquals(1 + 3 * TAG_BYTES, ct.size)
        assertFalse(cascadeKey(key, "AES_256_GCM").contentEquals(cascadeKey(key, "TWOFISH_256_GCM")))
    }

    @Test fun `files work with every algorithm and a raw key`() {
        val key = parseRawKey(newRawKeyHex())!!
        Aead.entries.forEach { alg ->
            val data = ByteArray(2 * CHUNK_BYTES + 3) { it.toByte() }
            val enc = ByteArrayOutputStream().also { encryptStream(ByteArrayInputStream(data), it, "n", Secret.RawKey(key), alg) }
            val dec = ByteArrayOutputStream()
            decryptStream(ByteArrayInputStream(enc.toByteArray()), dec, Secret.RawKey(key.copyOf()))
            assertArrayEquals(alg.name, data, dec.toByteArray())
        }
    }

    @Test fun `raw keys`() {
        val text = newRawKeyHex()
        assertEquals(64 + 15, text.length)
        assertEquals(32, parseRawKey(text.uppercase().replace(" ", "-"))!!.size)
        assertNull(parseRawKey("abc"))
        assertNull(parseRawKey("zz".repeat(32)))
    }

    @Test fun `bip39 checksums`() {
        assertEquals(2048, bip39.words.size)
        assertTrue(bip39.check("abandon ".repeat(11) + "about").valid)
        assertTrue(bip39.check("legal winner thank year wave sausage worth useful legal winner thank yellow").valid)
        assertTrue(bip39.check("zoo ".repeat(23) + "vote").valid)
        assertEquals(false, bip39.check("abandon ".repeat(12)).checksumOk)
        assertNull(bip39.check("abandon ".repeat(11)).checksumOk)
    }

    @Test fun `unknown words get a suggestion`() {
        val c = bip39.check("abandon ".repeat(6) + "abandn " + "abandon ".repeat(4) + "about")
        assertEquals(listOf(UnknownWord(7, "abandn", "abandon")), c.unknown)
        assertEquals("zoo", bip39.suggest("zo"))
        assertEquals(listOf("abandon", "ability", "able", "about", "above", "absent"), bip39.complete("ab"))
    }

    private inline fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("expected CryptoFailure")
        } catch (e: CryptoFailure) {
            // expected
        }
    }
}
