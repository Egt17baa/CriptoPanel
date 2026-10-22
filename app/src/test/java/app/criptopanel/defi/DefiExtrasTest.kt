package app.criptopanel.defi

import app.criptopanel.api
import app.criptopanel.core.network.Outcome
import app.criptopanel.decodeFixture
import app.criptopanel.enqueueFixture
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DefiExtrasTest {
    private val llama = FakeDefiLlamaApi()
    private val stables = FakeStablecoinsApi()
    private var clock = 1_000_000L
    private val repo = DefiExtrasRepository(llama, stables, FakeYieldsApi(), now = { clock })

    @Test fun `stablecoins valued in usd and sorted`() {
        val coins = decodeFixture<StablecoinsDto>("llama_stablecoins.json").toStablecoins()
        // EURT has no price, so its euros can't be converted: left out.
        assertEquals(listOf("USDT", "USDC", "BRKN"), coins.map { it.symbol })
        assertTrue(coins.zipWithNext().all { (a, b) -> a.marketCap >= b.marketCap })
    }

    @Test fun `off peg only beyond one percent`() {
        val coins = decodeFixture<StablecoinsDto>("llama_stablecoins.json").toStablecoins().associateBy { it.symbol }
        assertFalse(coins.getValue("USDT").offPeg)
        assertTrue(coins.getValue("BRKN").offPeg)
        assertEquals(1_000_000 * 0.95, coins.getValue("BRKN").marketCap, 1e-6)
    }

    @Test fun `missing previous day and gecko id do not break`() {
        val brkn = decodeFixture<StablecoinsDto>("llama_stablecoins.json").toStablecoins().first { it.symbol == "BRKN" }
        assertNull(brkn.change24h)
        assertNull(brkn.geckoId)
        assertEquals("Algorítmica", brkn.mechanism)
    }

    @Test fun `usd stablecoin without price is valued at par`() {
        val dto = StablecoinsDto(listOf(PeggedAssetDto("1", symbol = "X", pegType = "peggedUSD", circulating = mapOf("peggedUSD" to 5.0))))
        assertEquals(5.0, dto.toStablecoins().single().marketCap, 0.0)
    }

    @Test fun `total sums every peg and history is oldest first`() {
        val totals = decodeFixture<List<StablecoinChartPointDto>>("llama_stablecoin_chart.json").toTotals()
        assertEquals(303755085327.71 + 821843468.29, totals.last().tvl, 1.0)
        assertEquals(totals.sortedBy { it.epochSeconds }, totals)
    }

    @Test fun `seven day change needs eight points`() {
        val pts = (1..8).map { TvlPoint(it.toLong(), if (it == 1) 100.0 else 110.0) }
        assertEquals(10.0, stablecoinOverview(pts, emptyList()).change7d!!, 1e-9)
        assertNull(stablecoinOverview(pts.drop(1), emptyList()).change7d)
    }

    @Test fun `volume keeps active protocols largest first`() {
        val v = decodeFixture<VolumeOverviewDto>("llama_dexs.json").toDomain()
        assertEquals(3, v.protocols.size)
        assertTrue(v.protocols.none { it.name == "Sleepy" })
        assertTrue(v.protocols.zipWithNext().all { (a, b) -> a.total24h >= b.total24h })
        assertEquals(VOLUME_CHAINS, v.chains.size)
    }

    @Test fun `volume cached per kind and chain`() = runTest {
        repo.volume(VolumeKind.DEX, null)
        repo.volume(VolumeKind.DEX, null)
        repo.volume(VolumeKind.DEX, "Base")
        repo.volume(VolumeKind.REVENUE, null)
        assertEquals(listOf("dexs/all/null", "dexs/Base/null", "fees/all/dailyRevenue"), llama.volumeRequests)
    }

    @Test fun `volume urls encode chain and data type`() = runTest {
        val server = MockWebServer().apply { start() }
        repeat(2) { server.enqueueFixture("llama_fees.json") }
        val api = server.api<DefiLlamaApi>()
        api.chainVolumeOverview("fees", "OP Mainnet", "dailyFees")
        api.volumeOverview("dexs", null)
        assertEquals(
            "/overview/fees/OP%20Mainnet?dataType=dailyFees&excludeTotalDataChart=true&excludeTotalDataChartBreakdown=true",
            server.takeRequest().path,
        )
        assertEquals("/overview/dexs?excludeTotalDataChart=true&excludeTotalDataChartBreakdown=true", server.takeRequest().path)
        server.shutdown()
    }

    @Test fun `pools drop outliers, null and absurd apy and tiny tvl`() {
        val pools = decodeFixture<PoolsDto>("llama_pools.json").toPools()
        assertEquals(setOf("lido-steth", "aave-usdc", "aero-lp"), pools.map { it.id }.toSet())
        assertTrue(pools.first { it.id == "aero-lp" }.ilRisk)
    }

    @Test fun `pool filters and sort`() {
        val pools = decodeFixture<PoolsDto>("llama_pools.json").toPools()
        assertEquals(listOf("aero-lp", "aave-usdc", "lido-steth"), pools.filtered(false, MinTvl.M1, PoolSort.APY).map { it.id })
        assertEquals(listOf("lido-steth", "aave-usdc", "aero-lp"), pools.filtered(false, MinTvl.M1, PoolSort.TVL).map { it.id })
        assertEquals(listOf("aave-usdc"), pools.filtered(true, MinTvl.M1, PoolSort.APY).map { it.id })
        assertEquals(listOf("lido-steth", "aave-usdc"), pools.filtered(false, MinTvl.M100, PoolSort.TVL).map { it.id })
    }

    @Test fun `pool list capped at 100`() {
        val many = (1..150).map { Pool("$it", "p", "S", "c", it.toDouble(), null, null, 2e6, false, false) }
        assertEquals(MAX_POOLS, many.filtered(false, MinTvl.M1, PoolSort.APY).size)
    }

    @Test fun `stablecoins cached for 10 minutes`() = runTest {
        repo.stablecoins()
        clock += 9 * 60_000
        repo.stablecoins()
        assertEquals(1, stables.calls)
        assertTrue(repo.stablecoins(force = true) is Outcome.Ok)
        assertEquals(2, stables.calls)
    }
}
