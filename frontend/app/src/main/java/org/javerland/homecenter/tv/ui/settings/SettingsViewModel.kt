package org.javerland.homecenter.tv.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.javerland.homecenter.tv.data.repository.AuthRepository
import org.javerland.homecenter.tv.data.repository.PlaybackRepository
import org.javerland.homecenter.tv.data.session.SessionStore
import org.javerland.homecenter.tv.domain.Account
import javax.inject.Inject

data class SettingsUiState(
    val account: Account? = null,
    val serverUrl: String? = null,
    val loggingOut: Boolean = false,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val playbackRepository: PlaybackRepository,
    sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(
        sessionStore.snapshot().let { SettingsUiState(account = it.account, serverUrl = it.serverUrl) }
    )
    val state: StateFlow<SettingsUiState> = _state.asStateFlow()

    /**
     * Logging out clears the resume positions too. They belong to whoever was watching, and
     * the next person to sign in on this television should not inherit them.
     */
    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(loggingOut = true) }
            authRepository.logout()
            playbackRepository.forgetAll()
            _state.update { it.copy(loggingOut = false, loggedOut = true) }
        }
    }
}
