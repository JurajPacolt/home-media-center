package org.javerland.homecenter.tv.data.repository

import android.os.Build
import org.javerland.homecenter.tv.api.AuthenticationApi
import org.javerland.homecenter.tv.api.model.AuthUserDto
import org.javerland.homecenter.tv.api.model.LoginRequestDto
import org.javerland.homecenter.tv.data.net.ApiException
import org.javerland.homecenter.tv.data.net.apiCall
import org.javerland.homecenter.tv.data.net.bodyOrThrow
import org.javerland.homecenter.tv.data.session.SessionStore
import org.javerland.homecenter.tv.domain.Account
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Login exchanges a password or PIN for a token exactly once. The server tries both, so
 * the TV does not have to know which one the user typed—and neither is kept afterwards.
 */
@Singleton
class AuthRepository @Inject constructor(
    private val api: AuthenticationApi,
    private val sessionStore: SessionStore,
) {

    suspend fun login(username: String, secret: String): Account = apiCall {
        val response = api.login(
            LoginRequestDto(
                username = username.trim(),
                secret = secret,
                deviceName = deviceName(),
            )
        ).bodyOrThrow()

        val token = response.token ?: throw ApiException.Unauthorized()
        val account = response.user?.toDomain() ?: throw ApiException.Unauthorized()
        sessionStore.saveLogin(token, account)
        account
    }

    /**
     * Revokes the token on the server before forgetting it locally. A failure is ignored on
     * purpose: if the server cannot be reached, the user still wants to be logged out here,
     * and the token expires on its own.
     */
    suspend fun logout() {
        val token = sessionStore.snapshot().token
        if (token != null) {
            runCatching { api.logout("Bearer $token") }
        }
        sessionStore.clearLogin()
    }

    /** Confirms the stored token still works, so a stale one does not reach the home screen. */
    suspend fun verifyToken(): Boolean =
        runCatching { api.me().bodyOrThrow() }.isSuccess

    /**
     * How the device appears in the server's list of sessions. The model name is what the
     * owner of the household will recognise—"SHIELD Android TV" beats a random identifier.
     */
    private fun deviceName(): String {
        val model = Build.MODEL?.takeIf { it.isNotBlank() } ?: "Android TV"
        val manufacturer = Build.MANUFACTURER?.takeIf { it.isNotBlank() }
        return if (manufacturer != null && !model.startsWith(manufacturer, ignoreCase = true)) {
            "$manufacturer $model"
        } else {
            model
        }
    }

    private fun AuthUserDto.toDomain(): Account? {
        val id = id ?: return null
        val username = username ?: return null
        return Account(
            id = id,
            username = username,
            displayName = displayName ?: username,
            isAdmin = role == AuthUserDto.Role.ADMIN,
        )
    }
}
