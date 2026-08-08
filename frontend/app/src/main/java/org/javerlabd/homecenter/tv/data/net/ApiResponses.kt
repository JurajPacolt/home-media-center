package org.javerlabd.homecenter.tv.data.net

import retrofit2.Response
import java.io.IOException

/**
 * The reason a call failed, in the terms the UI needs. Screens show a message, and
 * [Unauthorized] additionally sends the user back to the login screen.
 */
sealed class ApiException(message: String, cause: Throwable? = null) : Exception(message, cause) {

    /** The server could not be reached at all—wrong address, TV offline, server down. */
    class Unreachable(cause: Throwable?) : ApiException("Server neodpovedá", cause)

    /** Login was refused, or the stored token is no longer valid. */
    class Unauthorized : ApiException("Prihlásenie zlyhalo")

    class NotFound : ApiException("Položka sa nenašla")

    class Server(val code: Int) : ApiException("Server odpovedal chybou $code")
}

/**
 * Unwraps a generated API call. The generated interfaces return [Response] so that the
 * status code stays visible; every caller wants the body and an exception otherwise.
 */
fun <T : Any> Response<T>.bodyOrThrow(): T {
    if (!isSuccessful) throw toApiException()
    return body() ?: throw ApiException.Server(code())
}

fun Response<*>.orThrow() {
    if (!isSuccessful) throw toApiException()
}

private fun Response<*>.toApiException(): ApiException = when (code()) {
    401, 403 -> ApiException.Unauthorized()
    404 -> ApiException.NotFound()
    else -> ApiException.Server(code())
}

/**
 * Runs a call and turns transport failures into the same vocabulary. Without this, every
 * screen would have to know what an [IOException] from OkHttp means.
 */
suspend fun <T> apiCall(block: suspend () -> T): T =
    try {
        block()
    } catch (failure: IOException) {
        throw ApiException.Unreachable(failure)
    }
