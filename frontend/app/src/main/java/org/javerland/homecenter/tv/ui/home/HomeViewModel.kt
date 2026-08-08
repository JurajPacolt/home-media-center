package org.javerland.homecenter.tv.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.javerland.homecenter.tv.data.repository.LibraryRepository
import org.javerland.homecenter.tv.data.repository.PlaybackRepository
import org.javerland.homecenter.tv.data.repository.ResumePoint
import org.javerland.homecenter.tv.data.session.SessionStore
import org.javerland.homecenter.tv.domain.Account
import org.javerland.homecenter.tv.domain.LibrarySummary
import javax.inject.Inject

data class HomeUiState(
    val loading: Boolean = true,
    val account: Account? = null,
    val summary: LibrarySummary? = null,
    val resume: List<ResumePoint> = emptyList(),
    val error: Throwable? = null,
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    playbackRepository: PlaybackRepository,
    sessionStore: SessionStore,
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState(account = sessionStore.snapshot().account))
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
        viewModelScope.launch {
            playbackRepository.recent().collect { points ->
                _state.update { it.copy(resume = points) }
            }
        }
    }

    /**
     * Reloaded whenever the home screen is shown again, not just on the first visit—a scan
     * on the server changes the counts, and the tiles would otherwise stay stale until the
     * app was killed.
     */
    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { libraryRepository.summary() }.fold(
                onSuccess = { summary ->
                    _state.update { it.copy(loading = false, summary = summary, error = null) }
                },
                onFailure = { failure ->
                    _state.update { it.copy(loading = false, error = failure) }
                },
            )
        }
    }
}
