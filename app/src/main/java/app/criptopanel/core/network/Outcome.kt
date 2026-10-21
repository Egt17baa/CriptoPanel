package app.criptopanel.core.network

import retrofit2.HttpException
import java.io.IOException
import kotlin.coroutines.cancellation.CancellationException

sealed interface AppError {
    data object RateLimited : AppError
    data object Offline : AppError
    data class Http(val code: Int) : AppError
    data class Unknown(val cause: Throwable) : AppError
}

sealed interface Outcome<out T> {
    data class Ok<T>(val value: T) : Outcome<T>
    data class Err(val error: AppError) : Outcome<Nothing>
}

suspend fun <T> apiCall(block: suspend () -> T): Outcome<T> =
    try {
        Outcome.Ok(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: HttpException) {
        Outcome.Err(if (e.code() == 429) AppError.RateLimited else AppError.Http(e.code()))
    } catch (e: IOException) {
        Outcome.Err(AppError.Offline)
    } catch (e: Exception) {
        Outcome.Err(AppError.Unknown(e))
    }
