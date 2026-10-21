package app.criptopanel.core.network

import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

const val COINGECKO_BASE_URL = "https://api.coingecko.com/api/v3/"
const val DEFILLAMA_BASE_URL = "https://api.llama.fi/"

val appJson = Json {
    ignoreUnknownKeys = true
    coerceInputValues = true
    explicitNulls = false
}

/** Shared client. The CoinGecko Demo key header is sent only when a key is set, and only to CoinGecko. */
fun okHttpClient(demoKey: String, coinGeckoHost: String = "api.coingecko.com"): OkHttpClient =
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
        .build()

fun retrofit(baseUrl: String, client: OkHttpClient): Retrofit =
    Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(appJson.asConverterFactory("application/json".toMediaType()))
        .build()
