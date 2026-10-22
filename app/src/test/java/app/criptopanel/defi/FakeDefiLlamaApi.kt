package app.criptopanel.defi

import app.criptopanel.decodeFixture
import java.io.IOException

class FakeDefiLlamaApi : DefiLlamaApi {
    var failProtocols = false
    val chainHistoryRequests = mutableListOf<String>()
    val volumeRequests = mutableListOf<String>()

    override suspend fun chains(): List<ChainDto> = decodeFixture("llama_chains.json")

    override suspend fun historical(): List<TvlPointDto> = decodeFixture("llama_historical.json")

    override suspend fun chainHistorical(chain: String): List<TvlPointDto> {
        chainHistoryRequests += chain
        return decodeFixture("llama_historical.json")
    }

    override suspend fun protocols(): List<ProtocolDto> {
        if (failProtocols) throw IOException("offline")
        return decodeFixture("llama_protocols.json")
    }

    override suspend fun volumeOverview(kind: String, dataType: String?, noChart: Boolean, noBreakdown: Boolean): VolumeOverviewDto {
        volumeRequests += "$kind/all/$dataType"
        return decodeFixture(if (kind == "dexs") "llama_dexs.json" else "llama_fees.json")
    }

    override suspend fun chainVolumeOverview(
        kind: String, chain: String, dataType: String?, noChart: Boolean, noBreakdown: Boolean,
    ): VolumeOverviewDto {
        volumeRequests += "$kind/$chain/$dataType"
        return decodeFixture(if (kind == "dexs") "llama_dexs.json" else "llama_fees.json")
    }
}
