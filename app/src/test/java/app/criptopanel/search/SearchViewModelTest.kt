package app.criptopanel.search

import app.criptopanel.MainDispatcherRule
import app.criptopanel.market.FakeCoinGeckoApi
import app.criptopanel.market.FakeCoinLoreApi
import app.criptopanel.market.MarketRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val fake = FakeCoinGeckoApi()
    private val vm by lazy { SearchViewModel(MarketRepository(fake, FakeCoinLoreApi())) }

    @Test fun `debounces and ignores short queries`() = runTest(main.dispatcher) {
        vm.onQuery("s")
        advanceTimeBy(500)
        assertEquals(emptyList<String>(), fake.queries)
        assertEquals(emptyList<SearchCoin>(), vm.results.value.data)
        vm.onQuery("so")
        vm.onQuery("sol")
        advanceTimeBy(500)
        assertEquals(listOf("sol"), fake.queries)
    }

    @Test fun `maps results`() = runTest(main.dispatcher) {
        vm.onQuery("sol")
        advanceUntilIdle()
        val results = vm.results.value.data!!
        assertEquals("SOL", results.first().symbol)
        assertEquals(6, results.first().rank)
        assertNull(results[1].thumbUrl)
    }

    @Test fun `shows loading while searching`() = runTest(main.dispatcher) {
        fake.searchDelayMs = 1_000
        vm.onQuery("sol")
        advanceTimeBy(450)
        assertEquals(true, vm.results.value.loading)
        advanceUntilIdle()
        assertEquals(false, vm.results.value.loading)
        assertEquals("SOL", vm.results.value.data!!.first().symbol)
    }

    @Test fun `retry repeats the same query after an error`() = runTest(main.dispatcher) {
        fake.failWith(java.io.IOException())
        vm.onQuery("sol")
        advanceUntilIdle()
        assertEquals(app.criptopanel.core.network.AppError.Offline, vm.results.value.error)
        fake.failWith(null)
        vm.retry()
        advanceUntilIdle()
        assertEquals(listOf("sol", "sol"), fake.queries)
        assertEquals("SOL", vm.results.value.data!!.first().symbol)
    }
}
