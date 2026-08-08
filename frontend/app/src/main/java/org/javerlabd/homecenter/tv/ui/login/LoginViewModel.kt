package org.javerlabd.homecenter.tv.ui.login

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.javerlabd.homecenter.tv.R
import org.javerlabd.homecenter.tv.data.net.ApiException
import org.javerlabd.homecenter.tv.data.repository.AuthRepository
import javax.inject.Inject

data class LoginUiState(
    val username: String = "",
    val secret: String = "",
    val working: Boolean = false,
    @get:StringRes val error: Int? = null,
    val loggedIn: Boolean = false,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun onUsernameChanged(value: String) {
        _state.update { it.copy(username = value, error = null) }
    }

    fun onSecretChanged(value: String) {
        _state.update { it.copy(secret = value, error = null) }
    }

    fun login() {
        val current = _state.value
        if (current.username.isBlank() || current.secret.isBlank()) {
            _state.update { it.copy(error = R.string.login_empty) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(working = true, error = null) }
            val result = runCatching { authRepository.login(current.username, current.secret) }
            result.fold(
                onSuccess = { _state.update { it.copy(working = false, loggedIn = true) } },
                onFailure = { failure ->
                    _state.update {
                        it.copy(working = false, secret = "", error = failure.toMessage())
                    }
                },
            )
        }
    }

    @StringRes
    private fun Throwable.toMessage(): Int = when (this) {
        // A wrong password and a wrong PIN are the same answer from the server, and saying
        // which one was wrong would help whoever is guessing more than the household.
        is ApiException.Unauthorized -> R.string.login_failed
        is ApiException.Unreachable -> R.string.common_error_network
        else -> R.string.common_error_generic
    }
}
