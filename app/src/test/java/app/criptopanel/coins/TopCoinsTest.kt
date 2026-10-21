package app.criptopanel.coins

import app.criptopanel.MainDispatcherRule
import app.criptopanel.market.FakeCoinGeckoApi
import app.criptopanel.market.FakeCoinLoreApi
import app.criptopanel.market.MarketRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TopCoinsTest {
    @get:Rule val main = MainDispatcherRule()

    private fun coin(id: String, rank: Int?, symbol: String, change24h: Double?, change7d: Double? = null, volume: Double? = null) =
        CoinMarket(id, rank, symbol, id.replaceFirstChar { it.uppercase() }, null, 1.0, null, volume, null, change24h, change7d, emptyList())

    private val coins = listOf(
        coin("nullcoin", null, "NUL", null),
        coin("ethereum", 2, "ETH", 2.5, change7d = -3.1, volume = 2e10),
        coin("bitcoin", 1, "BTC", 1.2, change7d = 3.3, volume = 4.5e10),
    )

    @Test fun `filters by symbol ignoring case`() =
        assertEquals(listOf("ethereum"), filterAndSort(coins, " eth ", SortKey.RANK).map { it.id })

    @Test fun `filters by name`() =
        assertEquals(listOf("bitcoin"), filterAndSort(coins, "bitco", SortKey.RANK).map { it.id })

    @Test fun `sorts by 24h change desc with nulls last`() =
        assertEquals(listOf("ethereum", "bitcoin", "nullcoin"), filterAndSort(coins, "", SortKey.CHANGE_24H).map { it.id })

    @Test fun `sorts by 7d change and volume desc`() {
        assertEquals(listOf("bitcoin", "ethereum", "nullcoin"), filterAndSort(coins, "", SortKey.CHANGE_7D).map { it.id })
        assertEquals(listOf("bitcoin", "ethereum", "nullcoin"), filterAndSort(coins, "", SortKey.VOLUME).map { it.id })
    }

    @Test fun `empty query returns all by rank`() =
        assertEquals(listOf(1, 2, null), filterAndSort(coins, "", SortKey.RANK).map { it.rank })

    @Test fun `view model applies query to loaded coins`() = runTest(main.dispatcher) {
        val vm = TopCoinsViewModel(MarketRepository(FakeCoinGeckoApi(), FakeCoinLoreApi()))
        vm.refresh()
        advanceUntilIdle()
        assertEquals(3, vm.state.value.data!!.size)
        vm.setQuery("eth")
        advanceUntilIdle()
        assertEquals(listOf("ethereum"), vm.state.value.data!!.map { it.id })
        vm.setSort(SortKey.CHANGE_7D)
        vm.setQuery("")
        advanceUntilIdle()
        assertEquals(listOf("bitcoin", "ethereum", "nullcoin"), vm.state.value.data!!.map { it.id })
    }
}
