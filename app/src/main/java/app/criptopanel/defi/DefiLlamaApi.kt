package app.criptopanel.defi

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface DefiLlamaApi {
    @GET("v2/chains")
    suspend fun chains(): List<ChainDto>

    @GET("v2/historicalChainTvl")
    suspend fun historical(): List<TvlPointDto>

    @GET("v2/historicalChainTvl/{chain}")
    suspend fun chainHistorical(@Path("chain") chain: String): List<TvlPointDto>

    @GET("protocols")
    suspend fun protocols(): List<ProtocolDto>

    /** DEX volume or fees across all chains; [dataType] is null for DEX volume. */
    @GET("overview/{kind}")
    suspend fun volumeOverview(
        @Path("kind") kind: String,
        @Query("dataType") dataType: String?,
        @Query("excludeTotalDataChart") noChart: Boolean = true,
        @Query("excludeTotalDataChartBreakdown") noBreakdown: Boolean = true,
    ): VolumeOverviewDto

    @GET("overview/{kind}/{chain}")
    suspend fun chainVolumeOverview(
        @Path("kind") kind: String,
        @Path("chain") chain: String,
        @Query("dataType") dataType: String?,
        @Query("excludeTotalDataChart") noChart: Boolean = true,
        @Query("excludeTotalDataChartBreakdown") noBreakdown: Boolean = true,
    ): VolumeOverviewDto
}
