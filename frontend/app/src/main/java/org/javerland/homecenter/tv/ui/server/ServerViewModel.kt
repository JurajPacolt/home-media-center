package org.javerland.homecenter.tv.ui.server

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.javerland.homecenter.tv.R
import org.javerland.homecenter.tv.data.session.SessionStore
import java.io.IOException
import javax.inject.Inject

data class ServerUiState(
    val address: String = "",
    val checking: Boolean = false,
    @get:StringRes val error: Int? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class ServerViewModel @Inject constructor(
    private val sessionStore: SessionStore,
    private val client: OkHttpClient,
) : ViewModel() {

    private val _state = MutableStateFlow(
        ServerUiState(address = sessionStore.snapshot().serverUrl ?: DEFAULT_ADDRESS)
    )
    val state: StateFlow<ServerUiState> = _state.asStateFlow()

    fun onAddressChanged(value: String) {
        _state.update { it.copy(address = value, error = null) }
    }

    /**
     * Checks the address before storing it. A typo would otherwise surface as a failed
     * login, which points the blame at the password instead of the address.
     */
    fun save() {
        val address = _state.value.address.trim().trimEnd('/')
        val url = address.toHttpUrlOrNull()
        if (url == null || (url.scheme != "http" && url.scheme != "https")) {
            _state.update { it.copy(error = R.string.server_invalid) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(checking = true, error = null) }
            val reachable = ping(address)
            if (reachable) {
                sessionStore.setServerUrl(address)
                _state.update { it.copy(checking = false, saved = true) }
            } else {
                _state.update { it.copy(checking = false, error = R.string.server_unreachable) }
            }
        }
    }

    /**
     * /actuator/health is the one endpoint the server leaves open, so this confirms the
     * address without needing an account first.
     */
    private suspend fun ping(address: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url("$address/actuator/health").build()
            client.newCall(request).execute().use { it.isSuccessful }
        }.getOrElse { failure ->
            if (failure is IOException) false else throw failure
        }
    }

    private companion object {
        /** A plausible starting point on a home network; the port is the server's default. */
        const val DEFAULT_ADDRESS = "http://192.168.1.10:8085"
    }
}
