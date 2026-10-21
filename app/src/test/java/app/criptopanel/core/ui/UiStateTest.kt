package app.criptopanel.core.ui

import app.criptopanel.core.network.AppError
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UiStateTest {
    @Test fun `busy server is not shown while data is on screen`() =
        assertNull(UiState(data = 1, error = AppError.RateLimited).visibleError())

    @Test fun `busy server is shown when there is nothing to show`() =
        assertEquals(AppError.RateLimited, UiState<Int>(error = AppError.RateLimited).visibleError())

    @Test fun `offline is shown even with data`() =
        assertEquals(AppError.Offline, UiState(data = 1, error = AppError.Offline).visibleError())
}
