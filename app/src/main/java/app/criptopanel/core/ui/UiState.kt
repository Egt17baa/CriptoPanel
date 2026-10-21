package app.criptopanel.core.ui

import app.criptopanel.core.network.AppError
import app.criptopanel.core.network.Outcome

/** Screen state: last good data is kept when a refresh fails, with the error shown alongside. */
data class UiState<T>(
    val data: T? = null,
    val loading: Boolean = false,
    val error: AppError? = null,
)

fun <T> UiState<T>.loading(): UiState<T> = copy(loading = true)

fun <T> UiState<T>.with(outcome: Outcome<T>): UiState<T> = when (outcome) {
    // The same instance means a cache hit, not fresh data: keep any error the user is seeing.
    is Outcome.Ok -> if (outcome.value === data) copy(loading = false) else UiState(data = outcome.value)
    is Outcome.Err -> copy(loading = false, error = outcome.error)
}

/** The error worth showing: a busy data server is silent while there is data on screen (auto-refresh retries). */
fun <T> UiState<T>.visibleError(): AppError? =
    if (error == AppError.RateLimited && data != null) null else error

fun AppError.message(): String = when (this) {
    AppError.RateLimited -> "Servidor de datos ocupado, reintentando…"
    AppError.Offline -> "Sin conexión"
    is AppError.Http -> "Error del servidor ($code)"
    is AppError.Unknown -> "Error inesperado"
}
