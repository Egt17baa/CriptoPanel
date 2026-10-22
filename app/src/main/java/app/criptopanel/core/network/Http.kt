package app.criptopanel.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3/"
const val DEFILLAMA_BASE_URL = "https://api.llama.fi/"
const val COINLORE_BASE_URL = "https://api.coinlore.net/api/"
const val ALTERNATIVE_BASE_URL = "https://api.alternative.me/"
const val STABLECOINS_BASE_URL = "https://stablecoins.llama.fi/"
const val YIELDS_BASE_URL = "https://yields.llama.fi/"

private const val MAX_RETRIES = 2
private const val MAX_RETRY_DELAY_MS = 3_000L

val appJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/**
 * Shared client. The CoinGecko Demo key header is sent only when a key is set, and only to CoinGecko.
 * HTTP 429 is retried twice, waiting Retry-After (at most 3 s) or 1 s, then 2 s.
 */
fun okHttpClient(
    demoKey: String,
    coinGeckoHost: String = "api.coingecko.com",
    retrySleep: (Long) -> Unit = Thread::sleep,
): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val request = chain.request()
            if (demoKey.isNotBlank() && request.url.host == coinGeckoHost) {
                chain.proceed(request.newBuilder().header("x-cg-demo-api-key", demoKey).build())
            } else {
                chain.proceed(request)
            }
        }
        .addInterceptor { chain ->
            var response = chain.proceed(chain.request())
            var attempt = 0
            while (response.code == 429 && attempt < MAX_RETRIES) {
                attempt++
                val delay = response.header("Retry-After")?.toLongOrNull()?.times(1_000) ?: (attempt * 1_000L)
                response.close()
                retrySleep(delay.coerceAtMost(MAX_RETRY_DELAY_MS))
                response = chain.proceed(chain.request())
            }
            response
        }
        .build()

fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(appJson.asConverterFactory("application/json".toMediaType()))
        .build()
