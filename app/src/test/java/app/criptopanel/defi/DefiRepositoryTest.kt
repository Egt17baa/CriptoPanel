package app.criptopanel.defi

import app.criptopanel.api
import app.criptopanel.core.network.Outcome
import app.criptopanel.enqueueFixture
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DefiRepositoryTest {
    private val server = MockWebServer().apply { start() }
    private var clock = 1_000_000L
    private val repo = DefiRepository(server.api(), now = { clock })

    @After fun tearDown() = server.shutdown()

    @Test fun `chains sorted with share`() = runTest {
        server.enqueueFixture("llama_chains.json")
        val c = (repo.chains() as Outcome.Ok).value
        assertEquals(listOf("Ethereum", "OP Mainnet", "Solana"), c.map { it.name })
        assertEquals(1.0, c.sumOf { it.share }, 1e-9)
        assertEquals(60.0 / 95.0, c[0].share, 1e-9)
        assertEquals("ETH", c[0].tokenSymbol)
        assertEquals("/v2/chains", server.takeRequest().path)
    }

    @Test fun `protocols exclude CEX and null tvl`() = runTest {
        server.enqueueFixture("llama_protocols.json")
        val p = (repo.protocols() as Outcome.Ok).value
        assertEquals(listOf("aave", "lido"), p.map { it.slug })
        assertTrue(p.zipWithNext().all { (a, b) -> a.tvl >= b.tvl })
    }

    @Test fun `protocol chain tvls drop derived keys`() = runTest {
        server.enqueueFixture("llama_protocols.json")
        val aave = (repo.protocols() as Outcome.Ok).value.first()
        assertEquals(mapOf("Ethereum" to 2.0e10, "Arbitrum" to 5.0e9), aave.chainTvls)
    }

    @Test fun `chain name is url encoded`() = runTest {
        server.enqueueFixture("llama_historical.json")
        repo.chainHistory("OP Mainnet")
        assertEquals("/v2/historicalChainTvl/OP%20Mainnet", server.takeRequest().path)
    }

    @Test fun `total tvl is last historical point`() = runTest {
        server.enqueueFixture("llama_historical.json")
        val history = (repo.totalHistory() as Outcome.Ok).value
        assertEquals(9.5e10, history.last().tvl, 1.0)
        assertEquals(1792454400L, history.last().epochSeconds)
        assertEquals("/v2/historicalChainTvl", server.takeRequest().path)
    }

    @Test fun `chains are cached for 60s`() = runTest {
        repeat(2) { server.enqueueFixture("llama_chains.json") }
        repo.chains()
        clock += 30_000
        repo.chains()
        assertEquals(1, server.requestCount)
    }

    @Test fun `protocols list is cached for 30 minutes`() = runTest {
        repeat(2) { server.enqueueFixture("llama_protocols.json") }
        repo.protocols()
        clock += 5 * 60_000
        repo.protocols()
        assertEquals(1, server.requestCount)
        clock += 26 * 60_000
        repo.protocols()
        assertEquals(2, server.requestCount)
    }
}
