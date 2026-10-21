package app.criptopanel.market

import app.criptopanel.MainDispatcherRule
import app.criptopanel.core.network.AppError
import app.criptopanel.core.ui.message
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class OverviewViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private val coinLore = FakeCoinLoreApi()
    private val vm = OverviewViewModel(MarketRepository(FakeCoinGeckoApi(), coinLore))

    @Test fun `refresh loads metrics`() = runTest(main.dispatcher) {
        vm.refresh()
        advanceUntilIdle()
        assertEquals(2758813436905.867 * (1 - 0.6049), vm.state.value.data!!.total2, 1.0)
        assertFalse(vm.state.value.loading)
        assertNull(vm.state.value.error)
    }

    @Test fun `error keeps previous data`() = runTest(main.dispatcher) {
        vm.refresh()
        advanceUntilIdle()
        coinLore.failWith(IOException())
        vm.refresh(force = true)
        advanceUntilIdle()
        assertNotNull(vm.state.value.data)
        assertEquals(AppError.Offline, vm.state.value.error)
        assertFalse(vm.state.value.loading)
    }

    @Test fun `busy message never mentions an api limit`() =
        assertEquals("Servidor de datos ocupado, reintentando…", AppError.RateLimited.message())

    @Test fun `offline message`() = assertEquals("Sin conexión", AppError.Offline.message())
}
