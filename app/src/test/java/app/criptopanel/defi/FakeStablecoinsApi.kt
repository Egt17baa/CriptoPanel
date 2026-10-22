package app.criptopanel.defi

import app.criptopanel.decodeFixture

class FakeStablecoinsApi : StablecoinsApi {
    var calls = 0

    override suspend fun stablecoins(includePrices: Boolean): StablecoinsDto {
        calls++
        return decodeFixture("llama_stablecoins.json")
    }

    override suspend fun totalChart(): List<StablecoinChartPointDto> = decodeFixture("llama_stablecoin_chart.json")
}

class FakeYieldsApi : YieldsApi {
    override suspend fun pools(): PoolsDto = decodeFixture("llama_pools.json")
}
