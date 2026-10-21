package app.criptopanel.market

import retrofit2.http.GET
import retrofit2.http.Query

interface CoinGeckoApi {
    @GET("global")
    suspend fun global(): GlobalEnvelopeDto

    @GET("coins/markets")
    suspend fun markets(
        @Query("vs_currency") vs: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = true,
        @Query("price_change_percentage") changes: String = "1h,24h,7d",
    ): List<CoinMarketDto>
}
