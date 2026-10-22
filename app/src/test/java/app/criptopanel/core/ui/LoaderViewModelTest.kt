package app.criptopanel.core.ui

import app.criptopanel.MainDispatcherRule
import app.criptopanel.core.network.AppError
import app.criptopanel.core.network.Outcome
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoaderViewModelTest {
    @get:Rule val main = MainDispatcherRule()

    private var next: Outcome<Int> = Outcome.Ok(1)
    private val forced = mutableListOf<Boolean>()
    private val vm = LoaderViewModel { force -> forced += force; next }

    @Test fun `refresh loads data`() = runTest(main.dispatcher) {
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.data)
        assertFalse(vm.state.value.loading)
        assertEquals(listOf(false), forced)
    }

    @Test fun `failure keeps previous data`() = runTest(main.dispatcher) {
        vm.refresh()
        advanceUntilIdle()
        next = Outcome.Err(AppError.Offline)
        vm.refresh(force = true)
        advanceUntilIdle()
        assertEquals(1, vm.state.value.data)
        assertEquals(AppError.Offline, vm.state.value.error)
        assertEquals(listOf(false, true), forced)
    }

    @Test fun `a new refresh cancels the running one`() = runTest(main.dispatcher) {
        vm.refresh()
        vm.refresh()
        advanceUntilIdle()
        assertEquals(1, vm.state.value.data)
        assertEquals(listOf(false), forced)
    }
}
