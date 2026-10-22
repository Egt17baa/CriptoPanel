package app.criptopanel.portfolio

import app.criptopanel.MainDispatcherRule
import app.criptopanel.market.FakeCoinGeckoApi
import app.criptopanel.market.FakeCoinLoreApi
import app.criptopanel.market.MarketRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class PortfolioViewModelTest {
    @get:Rule val main = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val gecko = FakeCoinGeckoApi()
    private val market = MarketRepository(gecko, FakeCoinLoreApi())
    private val store by lazy { PortfolioStore(File(tmp.root, "p.json"), Dispatchers.Unconfined) }

    @Test fun `no coins means no price request`() = runTest(main.dispatcher) {
        PortfolioViewModel(store, market)
        advanceUntilIdle()
        assertTrue(gecko.idLists.isEmpty())
    }

    @Test fun `prices requested for open positions and favourites in one call`() = runTest(main.dispatcher) {
        store.upsert(buy(1.0, 50_000.0, coin = "bitcoin"))
        store.toggleFavorite("ethereum")
        val vm = PortfolioViewModel(store, market)
        advanceUntilIdle()
        assertEquals("bitcoin,ethereum", gecko.idLists.last())
        assertEquals(98123.45, vm.summary.value.value, 1e-6)
        assertEquals(listOf("ethereum"), vm.favorites.value.map { it.id })
    }

    @Test fun `value series uses the coin chart`() = runTest(main.dispatcher) {
        store.upsert(buy(2.0, 1.0, date = "2020-01-01", coin = "bitcoin"))
        val vm = PortfolioViewModel(store, market)
        advanceUntilIdle()
        val s = vm.series.value.data!!
        assertTrue(s.isNotEmpty())
        assertTrue(s.all { it.price > 0 })
    }

    @Test fun `form validation`() {
        val today = LocalDate.parse("2026-10-22")
        val coin = FormCoin("bitcoin", "BTC", "Bitcoin", null)
        val ok = OperationForm(coin = coin, quantity = "0,5", price = "100", date = today, fee = "", note = " x ")
        val valid = ok.validate("id", today) as FormResult.Valid
        assertEquals(0.5, valid.operation.quantity, 0.0)
        assertEquals("x", valid.operation.note)
        assertEquals(FormResult.Invalid("Elige una moneda"), ok.copy(coin = null).validate("id", today))
        assertEquals(FormResult.Invalid("Cantidad no válida"), ok.copy(quantity = "0").validate("id", today))
        assertEquals(FormResult.Invalid("Precio no válido"), ok.copy(price = "1.2.3").validate("id", today))
        assertEquals(FormResult.Invalid("Comisión no válida"), ok.copy(fee = "-1").validate("id", today))
        assertEquals(FormResult.Invalid("La fecha no puede ser futura"), ok.copy(date = today.plusDays(1)).validate("id", today))
    }

    @Test fun `new operation from a coin fills the current price and saves`() = runTest(main.dispatcher) {
        val vm = OperationViewModel(store, market, editId = null, coinId = "bitcoin")
        advanceUntilIdle()
        assertEquals("BTC", vm.form.value.coin!!.symbol)
        assertEquals("98123.45", vm.form.value.price)
        vm.update { it.copy(quantity = "1") }
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.saved.value)
        assertEquals(1, store.data.value.operations.size)
    }

    @Test fun `uncovered sell shows the store message`() = runTest(main.dispatcher) {
        val vm = OperationViewModel(store, market, editId = null, coinId = "bitcoin")
        advanceUntilIdle()
        vm.update { it.copy(type = OperationType.SELL, quantity = "1") }
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.saved.value)
        assertEquals(SHORTFALL_MESSAGE, vm.error.value)
    }

    @Test fun `editing loads the operation`() = runTest(main.dispatcher) {
        val b = buy(1.5, 10.0, fee = 0.0).copy(note = "n")
        store.upsert(b)
        val vm = OperationViewModel(store, market, editId = b.id, coinId = null)
        advanceUntilIdle()
        assertEquals("1.5", vm.form.value.quantity)
        assertEquals("", vm.form.value.fee)
        assertEquals("n", vm.form.value.note)
        assertNull(vm.error.value)
    }
}
