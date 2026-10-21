package app.criptopanel.search

import app.criptopanel.MainDispatcherRule
import app.criptopanel.market.FakeCoinGeckoApi
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
    private val vm by lazy { SearchViewModel(MarketRepository(fake)) }

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
}
