package org.javerlabd.homecenter.tv.ui.player

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.javerlabd.homecenter.tv.data.repository.LibraryRepository
import org.javerlabd.homecenter.tv.data.repository.PlaybackRepository
import org.javerlabd.homecenter.tv.di.ApplicationScope
import org.javerlabd.homecenter.tv.domain.MediaItem
import org.javerlabd.homecenter.tv.ui.navigation.Routes
import javax.inject.Inject

data class VideoPlayerUiState(
    val item: MediaItem? = null,
    val error: Throwable? = null,
    val playbackFailed: Boolean = false,
    val finished: Boolean = false,
)

/**
 * Owns the player for as long as the screen exists. Keeping it here rather than in the
 * composable means a configuration change—or the box briefly changing display mode—does
 * not restart the film from the beginning.
 */
@HiltViewModel
class VideoPlayerViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackRepository: PlaybackRepository,
    @param:ApplicationScope private val applicationScope: CoroutineScope,
    playerFactory: PlayerFactory,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<String>(Routes.MEDIA_ID_ARG)?.toLongOrNull()
        ?: error("Prehrávač otvorený bez identifikátora položky")

    private val fromStart: Boolean =
        savedStateHandle.get<String>(Routes.FROM_START_ARG)?.toBooleanStrictOrNull() ?: true

    val player: ExoPlayer = playerFactory.create()

    private val _state = MutableStateFlow(VideoPlayerUiState())
    val state: StateFlow<VideoPlayerUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            _state.update { it.copy(playbackFailed = true) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                rememberPosition()
                _state.update { it.copy(finished = true) }
            }
        }
    }

    init {
        player.addListener(listener)
        load()
        trackPosition()
    }

    private fun load() {
        viewModelScope.launch {
            runCatching { libraryRepository.item(mediaId) }.fold(
                onSuccess = { item ->
                    _state.update { it.copy(item = item) }
                    val resumeAt =
                        if (fromStart) 0L else playbackRepository.resumePoint(item.id)?.positionMs ?: 0L
                    player.setMediaItem(ExoMediaItem.fromUri(item.streamUrl))
                    if (resumeAt > 0) player.seekTo(resumeAt)
                    player.prepare()
                    player.playWhenReady = true
                },
                onFailure = { failure -> _state.update { it.copy(error = failure) } },
            )
        }
    }

    /**
     * Saves where playback has got to at a steady interval. Writing only when the screen
     * closes would lose the position every time the box is switched off mid-film, which on
     * a television is an ordinary way to stop watching.
     */
    private fun trackPosition() {
        viewModelScope.launch {
            while (true) {
                delay(POSITION_SAVE_INTERVAL_MS)
                if (player.isPlaying) rememberPosition()
            }
        }
    }

    fun rememberPosition() {
        val item = _state.value.item ?: return
        val position = player.currentPosition
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        viewModelScope.launch {
            playbackRepository.save(item, position, duration)
        }
    }

    override fun onCleared() {
        // Reading the position before releasing: afterwards the player reports zero.
        val item = _state.value.item
        val position = player.currentPosition
        val duration = player.duration.takeIf { it > 0 } ?: 0L
        player.removeListener(listener)
        player.release()

        if (item != null) {
            // viewModelScope is already cancelled here, so the last write—the one that
            // matters most, because it is the position the viewer left on—runs on a scope
            // that outlives the screen.
            applicationScope.launch { playbackRepository.save(item, position, duration) }
        }
    }

    private companion object {
        const val POSITION_SAVE_INTERVAL_MS = 10_000L
    }
}
