package app.criptopanel.defi

import retrofit2.http.GET
import retrofit2.http.Path

interface DefiLlamaApi {
    @GET("v2/chains")
    suspend fun chains(): List<ChainDto>

    @GET("v2/historicalChainTvl")
    suspend fun historical(): List<TvlPointDto>

    @GET("v2/historicalChainTvl/{chain}")
    suspend fun chainHistorical(@Path("chain") chain: String): List<TvlPointDto>

    @GET("protocols")
    suspend fun protocols(): List<ProtocolDto>
}
