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
    is Outcome.Ok -> UiState(data = outcome.value)
    is Outcome.Err -> copy(loading = false, error = outcome.error)
}

fun AppError.message(): String = when (this) {
    AppError.RateLimited -> "Límite de la API alcanzado, reintenta en un minuto"
    AppError.Offline -> "Sin conexión"
    is AppError.Http -> "Error del servidor ($code)"
    is AppError.Unknown -> "Error inesperado"
}
