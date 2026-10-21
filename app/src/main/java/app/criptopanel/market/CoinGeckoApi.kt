package app.criptopanel.market

import retrofit2.http.GET

interface CoinGeckoApi {
    @GET("global")
    suspend fun global(): GlobalEnvelopeDto
}
