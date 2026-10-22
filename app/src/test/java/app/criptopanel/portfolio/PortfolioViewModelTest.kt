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

    private val binance = object : app.criptopanel.alerts.BinanceHistoryApi {
        var calls = 0
        override suspend fun price(symbol: String) = app.criptopanel.alerts.BinancePriceDto(symbol, "98000")
        override suspend fun klines(symbol: String, startTime: Long, interval: String, limit: Int): List<List<kotlinx.serialization.json.JsonElement>> {
            calls++
            return listOf(listOf(kotlinx.serialization.json.JsonPrimitive(startTime), kotlinx.serialization.json.JsonPrimitive("12345.6")))
        }
    }
    private val history by lazy { HistoricalPrices(gecko, binance) }
    private val today = LocalDate.parse("2026-10-22")
    private fun opVm(editId: String? = null, coinId: String? = "bitcoin") =
        OperationViewModel(store, market, history, editId, coinId, today = { today })

    @Test fun `form validation`() {
        val coin = FormCoin("bitcoin", "BTC", "Bitcoin", null)
        val ok = OperationForm(coin = coin, mode = AmountMode.QUANTITY, amount = "0,5", price = "100", date = today, note = " x ")
        val valid = ok.validate("id", today) as FormResult.Valid
        assertEquals(0.5, valid.operation.quantity, 0.0)
        assertEquals("x", valid.operation.note)
        assertEquals(FormResult.Invalid("Elige una moneda"), ok.copy(coin = null).validate("id", today))
        assertEquals(FormResult.Invalid("Cantidad no válida"), ok.copy(amount = "0").validate("id", today))
        assertEquals(FormResult.Invalid("Importe no válido"), ok.copy(mode = AmountMode.USD, amount = "").validate("id", today))
        assertEquals(FormResult.Invalid("Precio no válido"), ok.copy(price = "1.2.3").validate("id", today))
        assertEquals(FormResult.Invalid("Comisión no válida"), ok.copy(fee = "-1").validate("id", today))
        assertEquals(FormResult.Invalid("La fecha no puede ser futura"), ok.copy(date = today.plusDays(1)).validate("id", today))
    }

    @Test fun `amount in dollars becomes a quantity`() {
        val f = OperationForm(coin = FormCoin("bitcoin", "BTC", "Bitcoin", null), mode = AmountMode.USD, amount = "100", price = "80000", date = today)
        assertEquals(0.00125, f.quantity!!, 1e-12)
        assertEquals(100.0, f.total!!, 1e-9)
        assertEquals(0.00125, (f.validate("id", today) as FormResult.Valid).operation.quantity, 1e-12)
    }

    @Test fun `today uses the current price and saves`() = runTest(main.dispatcher) {
        val vm = opVm()
        advanceUntilIdle()
        assertEquals("BTC", vm.form.value.coin!!.symbol)
        assertEquals("98123.45", vm.form.value.price)
        assertEquals(PriceSource.Current, vm.priceSource.value)
        vm.update { it.copy(amount = "1000") }
        vm.save()
        advanceUntilIdle()
        assertTrue(vm.saved.value)
        assertEquals(1000 / 98123.45, store.data.value.operations.single().quantity, 1e-12)
    }

    @Test fun `past date fills the price of that day`() = runTest(main.dispatcher) {
        val vm = opVm()
        advanceUntilIdle()
        vm.setDate(LocalDate.parse("2026-03-01"))
        advanceUntilIdle()
        assertEquals("50000", vm.form.value.price)
        assertEquals(PriceSource.OnDate(LocalDate.parse("2026-03-01"), "CoinGecko"), vm.priceSource.value)
        assertEquals("01-03-2026", gecko.historyDates.last())
    }

    @Test fun `older dates fall back to binance`() = runTest(main.dispatcher) {
        gecko.historyPrice = null
        val vm = opVm()
        advanceUntilIdle()
        vm.setDate(LocalDate.parse("2024-01-01"))
        advanceUntilIdle()
        assertEquals("12345.6", vm.form.value.price)
        assertEquals("Binance", (vm.priceSource.value as PriceSource.OnDate).provider)
    }

    @Test fun `a typed price survives date changes until reset`() = runTest(main.dispatcher) {
        val vm = opVm()
        advanceUntilIdle()
        vm.setPrice("123")
        vm.setDate(LocalDate.parse("2026-03-01"))
        advanceUntilIdle()
        assertEquals("123", vm.form.value.price)
        vm.usePriceOfDate()
        advanceUntilIdle()
        assertEquals("50000", vm.form.value.price)
    }

    @Test fun `switching mode converts the amount`() = runTest(main.dispatcher) {
        val vm = opVm()
        advanceUntilIdle()
        vm.setPrice("80000")
        vm.update { it.copy(mode = AmountMode.USD, amount = "100") }
        vm.setMode(AmountMode.QUANTITY)
        assertEquals("0.00125", vm.form.value.amount)
        vm.setMode(AmountMode.USD)
        assertEquals("100", vm.form.value.amount)
    }

    @Test fun `sell shows holdings and fractions`() = runTest(main.dispatcher) {
        store.upsert(buy(2.0, 10.0, date = "2026-10-01"))
        val vm = opVm()
        advanceUntilIdle()
        vm.setType(OperationType.SELL)
        assertEquals(2.0, vm.held.value!!, 0.0)
        vm.setFraction(0.25)
        assertEquals(AmountMode.QUANTITY, vm.form.value.mode)
        assertEquals("0.5", vm.form.value.amount)
        vm.setDate(LocalDate.parse("2026-09-01"))
        advanceUntilIdle()
        assertEquals(0.0, vm.held.value!!, 0.0)
    }

    @Test fun `uncovered sell shows the store message`() = runTest(main.dispatcher) {
        val vm = opVm()
        advanceUntilIdle()
        vm.update { it.copy(type = OperationType.SELL, mode = AmountMode.QUANTITY, amount = "1") }
        vm.save()
        advanceUntilIdle()
        assertFalse(vm.saved.value)
        assertEquals(SHORTFALL_MESSAGE, vm.error.value)
    }

    @Test fun `editing loads the operation as a quantity`() = runTest(main.dispatcher) {
        val b = buy(1.5, 10.0, fee = 0.0).copy(note = "n")
        store.upsert(b)
        val vm = opVm(editId = b.id, coinId = null)
        advanceUntilIdle()
        assertEquals(AmountMode.QUANTITY, vm.form.value.mode)
        assertEquals("1.5", vm.form.value.amount)
        assertEquals("", vm.form.value.fee)
        assertEquals("n", vm.form.value.note)
        assertEquals(PriceSource.Typed, vm.priceSource.value)
        assertNull(vm.error.value)
    }

    @Test fun `euro form is saved in dollars at the day's rate`() = runTest(main.dispatcher) {
        val vm = OperationViewModel(store, market, history, null, "bitcoin", today = { today }, perUsdOn = { 0.5 })
        advanceUntilIdle()
        // The price is shown in the display currency: 98 123,45 $ × 0,5.
        assertEquals("49061.725", vm.form.value.price)
        vm.update { it.copy(mode = AmountMode.USD, amount = "100", fee = "1") }
        vm.save()
        advanceUntilIdle()
        val op = store.data.value.operations.single()
        assertEquals(98123.45, op.priceUsd, 1e-6)
        assertEquals(2.0, op.feeUsd, 1e-9)
        assertEquals(100 / 49061.725, op.quantity, 1e-12)
    }
}
