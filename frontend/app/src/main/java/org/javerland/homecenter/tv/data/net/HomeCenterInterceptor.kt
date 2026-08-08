package org.javerland.homecenter.tv.data.net

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import org.javerland.homecenter.tv.data.session.SessionStore
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Retrofit wants a base address when it is built, but the server address is something the
 * user types on the TV and can change later. Every call is therefore issued against
 * [PLACEHOLDER_BASE_URL] and pointed at the real host here.
 *
 * The same interceptor signs the request. The token goes on everything except login,
 * which is the one endpoint that does not have one yet.
 */
@Singleton
class HomeCenterInterceptor @Inject constructor(
    private val sessionStore: SessionStore,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val session = sessionStore.snapshot()
        val request = chain.request()
        val builder = request.newBuilder()

        val server = session.serverUrl?.toHttpUrlOrNull()
        if (server != null && request.url.host == PLACEHOLDER_HOST) {
            builder.url(
                request.url.newBuilder()
                    .scheme(server.scheme)
                    .host(server.host)
                    .port(server.port)
                    .build()
            )
        }

        val token = session.token
        if (token != null && !request.url.encodedPath.endsWith(LOGIN_PATH)) {
            builder.header("Authorization", "Bearer $token")
        }

        val response = chain.proceed(builder.build())

        // The server invalidates every token when a password or PIN changes, and tokens
        // expire after 90 days. Both arrive here as a plain 401.
        if (response.code == 401 && !request.url.encodedPath.endsWith(LOGIN_PATH)) {
            sessionStore.onUnauthorized()
        }
        return response
    }

    companion object {
        private const val LOGIN_PATH = "/api/v1/auth/login"

        const val PLACEHOLDER_HOST = "homecenter.invalid"

        /** Never reached: the interceptor above replaces the host on every call. */
        const val PLACEHOLDER_BASE_URL = "http://$PLACEHOLDER_HOST/"
    }
}
