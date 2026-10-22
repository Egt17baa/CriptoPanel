package app.criptopanel.security

import okhttp3.ResponseBody
import retrofit2.http.GET
import retrofit2.http.Headers
import retrofit2.http.Path
import java.io.InputStream
import java.security.MessageDigest

const val HIBP_BASE_URL = "https://api.pwnedpasswords.com/"

/** Have I Been Pwned "range" API: k-anonymity, only the first 5 hex characters of a SHA-1 ever leave the phone. */
interface PwnedPasswordsApi {
    @Headers("Add-Padding: true")
    @GET("range/{prefix}")
    suspend fun range(@Path("prefix") prefix: String): ResponseBody
}

fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02X".format(it) }

fun sha1Hex(password: CharArray): String {
    val bytes = String(password).toByteArray(Charsets.UTF_8)
    try {
        return hex(MessageDigest.getInstance("SHA-1").digest(bytes))
    } finally {
        bytes.fill(0)
    }
}

/** How many times the hash suffix appears in a range response; padding lines (count 0) count as absent. */
fun pwnedCount(rangeBody: String, suffix: String): Int =
    rangeBody.lineSequence()
        .map { it.trim().split(':') }
        .firstOrNull { it.size == 2 && it[0].equals(suffix, ignoreCase = true) }
        ?.get(1)?.trim()?.toIntOrNull() ?: 0

/** Times [password] appears in known breaches; only the 5-character hash prefix is sent. */
suspend fun pwnedTimes(api: PwnedPasswordsApi, password: CharArray): Int {
    val hash = sha1Hex(password)
    return api.range(hash.take(5)).use { pwnedCount(it.string(), hash.drop(5)) }
}

/** SHA-256 of a stream, as lowercase hex (how download pages publish it). */
fun sha256Hex(input: InputStream): String {
    val md = MessageDigest.getInstance("SHA-256")
    val buf = ByteArray(64 * 1024)
    while (true) {
        val n = input.read(buf)
        if (n < 0) break
        md.update(buf, 0, n)
    }
    return hex(md.digest()).lowercase()
}

/** Compares a published hash with ours, ignoring case, spaces and colons. */
fun sameHash(a: String, b: String): Boolean {
    val norm = { s: String -> s.filter { it.isLetterOrDigit() }.lowercase() }
    return norm(a).isNotEmpty() && norm(a) == norm(b)
}
