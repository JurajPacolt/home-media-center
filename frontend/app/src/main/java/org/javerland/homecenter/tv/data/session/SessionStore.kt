package org.javerland.homecenter.tv.data.session

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.javerland.homecenter.tv.di.ApplicationScope
import org.javerland.homecenter.tv.domain.Account
import javax.inject.Inject
import javax.inject.Singleton

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "homecenter")

/**
 * What the TV remembers between launches: which server to talk to and the token issued
 * for it. Neither the password nor the PIN is ever stored—the server hands out a token
 * precisely so they do not have to be.
 */
data class Session(
    val serverUrl: String?,
    val token: String?,
    val account: Account?,
) {
    val hasServer: Boolean get() = !serverUrl.isNullOrBlank()
    val isLoggedIn: Boolean get() = hasServer && !token.isNullOrBlank()
}

@Singleton
class SessionStore @Inject constructor(
    @ApplicationContext context: Context,
    // Explicit target: from Kotlin 2.4 an untargeted annotation would land on the property
    // as well, and Hilt reads qualifiers from the constructor parameter.
    @param:ApplicationScope private val scope: CoroutineScope,
) {

    private val store = context.dataStore

    /**
     * The interceptors need the current server and token on a background thread, in the
     * middle of a call, where suspending is not an option. Reading the flow keeps this
     * field in step with what was last written.
     */
    @Volatile
    private var latest: Session = Session(null, null, null)

    /**
     * Null until the first value has been read from disk. The UI waits for it rather than
     * guessing—showing the login screen to somebody who is already logged in would be a
     * visible flash on every launch.
     */
    val session: StateFlow<Session?> = store.data
        .map { it.toSession() }
        .onEach { latest = it }
        .stateIn(scope, SharingStarted.Eagerly, null)

    private val _expired = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Emits when the server rejected the stored token; the UI returns to login. */
    val expired: SharedFlow<Unit> = _expired

    fun snapshot(): Session = latest

    suspend fun setServerUrl(url: String) {
        store.edit { preferences ->
            preferences[Keys.SERVER_URL] = url.trimEnd('/')
        }
    }

    suspend fun saveLogin(token: String, account: Account) {
        store.edit { preferences ->
            preferences[Keys.TOKEN] = token
            preferences[Keys.USER_ID] = account.id
            preferences[Keys.USERNAME] = account.username
            preferences[Keys.DISPLAY_NAME] = account.displayName
            preferences[Keys.IS_ADMIN] = account.isAdmin
        }
    }

    /** Forgets the token but keeps the server address; the next login starts one step in. */
    suspend fun clearLogin() {
        store.edit { preferences ->
            preferences.remove(Keys.TOKEN)
            preferences.remove(Keys.USER_ID)
            preferences.remove(Keys.USERNAME)
            preferences.remove(Keys.DISPLAY_NAME)
            preferences.remove(Keys.IS_ADMIN)
        }
    }

    /**
     * Called from the network layer when the server answers 401. Dropping the token here
     * means the UI cannot keep retrying with credentials the server has already refused.
     */
    fun onUnauthorized() {
        if (latest.token == null) return
        scope.launch {
            clearLogin()
            _expired.emit(Unit)
        }
    }

    private fun Preferences.toSession(): Session {
        val userId = this[Keys.USER_ID]
        val username = this[Keys.USERNAME]
        val account = if (userId != null && username != null) {
            Account(
                id = userId,
                username = username,
                displayName = this[Keys.DISPLAY_NAME] ?: username,
                isAdmin = this[Keys.IS_ADMIN] ?: false,
            )
        } else {
            null
        }
        return Session(
            serverUrl = this[Keys.SERVER_URL],
            token = this[Keys.TOKEN],
            account = account,
        )
    }

    private object Keys {
        val SERVER_URL = stringPreferencesKey("server_url")
        val TOKEN = stringPreferencesKey("token")
        val USER_ID = longPreferencesKey("user_id")
        val USERNAME = stringPreferencesKey("username")
        val DISPLAY_NAME = stringPreferencesKey("display_name")
        val IS_ADMIN = booleanPreferencesKey("is_admin")
    }
}
