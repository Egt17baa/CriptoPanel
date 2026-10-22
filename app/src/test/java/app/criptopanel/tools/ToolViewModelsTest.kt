package app.criptopanel.tools

import app.criptopanel.MainDispatcherRule
import app.criptopanel.market.FakeCoinGeckoApi
import app.criptopanel.market.FakeCoinLoreApi
import app.criptopanel.market.MarketRepository
import app.criptopanel.portfolio.FormCoin
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ToolViewModelsTest {
    @get:Rule val main = MainDispatcherRule()
    private val market = MarketRepository(FakeCoinGeckoApi(), FakeCoinLoreApi())

    @Test fun `calculator converts and swaps`() = runTest(main.dispatcher) {
        val vm = CalculatorViewModel(market)
        advanceUntilIdle()
        assertEquals(98123.45, vm.result()!!, 1e-6)
        vm.setTo(Asset.Coin(ETHEREUM))
        advanceUntilIdle()
        assertEquals(98123.45 / 3000.5, vm.result()!!, 1e-9)
        vm.swap()
        vm.setAmount("2")
        assertEquals(2 * 3000.5 / 98123.45, vm.result()!!, 1e-12)
    }

    @Test fun `comparator caps at four and refuses duplicates`() = runTest(main.dispatcher) {
        val vm = CompareViewModel(market)
        advanceUntilIdle()
        assertFalse(vm.add(BITCOIN))
        assertTrue(vm.add(FormCoin("a", "A", "A", null)))
        assertTrue(vm.add(FormCoin("b", "B", "B", null)))
        assertFalse(vm.add(FormCoin("c", "C", "C", null)))
        advanceUntilIdle()
        assertEquals(4, vm.coins.value.size)
        assertTrue(vm.series.value.isNotEmpty())
        vm.remove("a")
        assertEquals(3, vm.coins.value.size)
    }

    @Test fun `what if loads history once and recomputes locally`() = runTest(main.dispatcher) {
        val vm = WhatIfViewModel(market)
        advanceUntilIdle()
        assertNotNull(vm.data.value.data)
        vm.setAmount("abc")
        assertEquals(null, vm.result())
    }
}
