package app.criptopanel.market

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface CoinGeckoApi {
    @GET("coins/markets")
    suspend fun markets(
        @Query("vs_currency") vs: String = "usd",
        @Query("order") order: String = "market_cap_desc",
        @Query("per_page") perPage: Int = 100,
        @Query("page") page: Int = 1,
        @Query("sparkline") sparkline: Boolean = true,
        @Query("price_change_percentage") changes: String = "1h,24h,7d",
        /** A CoinGecko category id (sector); null for the whole market. */
        @Query("category") category: String? = null,
    ): List<CoinMarketDto>

    @GET("coins/{id}")
    suspend fun coin(
        @Path("id") id: String,
        @Query("localization") l: Boolean = false,
        @Query("tickers") t: Boolean = false,
        @Query("community_data") c: Boolean = false,
        @Query("developer_data") d: Boolean = false,
    ): CoinDetailDto

    @GET("coins/{id}/market_chart")
    suspend fun marketChart(
        @Path("id") id: String,
        @Query("days") days: String,
        @Query("vs_currency") vs: String = "usd",
    ): MarketChartDto

    @GET("search")
    suspend fun search(@Query("query") q: String): SearchDto

    @GET("search/trending")
    suspend fun trending(): TrendingDto

    @GET("coins/categories")
    suspend fun categories(): List<CategoryDto>

    @GET("exchanges")
    suspend fun exchanges(@Query("per_page") perPage: Int = 50): List<ExchangeDto>
}
