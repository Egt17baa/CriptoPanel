package app.criptopanel.defi

import app.criptopanel.MainDispatcherRule
import app.criptopanel.core.network.AppError
import app.criptopanel.decodeFixture
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DefiViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val fake = FakeDefiLlamaApi()
    private val repo = DefiRepository(fake)
    private val protocols = decodeFixture<List<ProtocolDto>>("llama_protocols.json").toProtocols()

    @Test fun `overview total is last history point`() = runTest(main.dispatcher) {
        val vm = DefiViewModel(repo)
        vm.refresh()
        advanceUntilIdle()
        val data = vm.state.value.data!!
        assertEquals(9.5e10, data.totalTvl, 1.0)
        assertEquals(3, data.chains.size)
        assertEquals(2, data.protocols.size)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `one failing endpoint keeps old data`() = runTest(main.dispatcher) {
        val vm = DefiViewModel(repo)
        vm.refresh()
        advanceUntilIdle()
        fake.failProtocols = true
        vm.refresh(force = true)
        advanceUntilIdle()
        assertNotNull(vm.state.value.data)
        assertEquals(AppError.Offline, vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `protocols on chain use chain tvl`() =
        assertEquals(
            listOf("aave" to 2.0e10, "lido" to 1.0e10),
            protocolsOnChain(protocols, "Ethereum").map { it.first.slug to it.second },
        )

    @Test fun `chain detail loads history and protocols for the chain`() = runTest(main.dispatcher) {
        val vm = ChainDetailViewModel("Solana", repo)
        advanceUntilIdle()
        assertEquals(listOf("Solana"), fake.chainHistoryRequests)
        assertEquals(2, vm.history.value.data!!.size)
        assertEquals(listOf("lido" to 5.0e8), vm.protocols.value.data!!.map { it.first.slug to it.second })
    }

    @Test fun `protocols on chain match legacy chain names`() {
        val bnb = Protocol("pancakeswap", "PancakeSwap", null, "Dexs", 3.0e9, null, listOf("Binance"), mapOf("Binance" to 3.0e9))
        assertEquals(listOf("pancakeswap" to 3.0e9), protocolsOnChain(listOf(bnb), "BSC").map { it.first.slug to it.second })
    }
}
