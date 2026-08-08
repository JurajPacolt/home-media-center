package org.javerland.homecenter.tv.ui.music

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem as ExoMediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.javerland.homecenter.tv.data.repository.LibraryRepository
import org.javerland.homecenter.tv.data.repository.MediaQueue
import org.javerland.homecenter.tv.domain.MediaCategory
import org.javerland.homecenter.tv.domain.MediaItem
import org.javerland.homecenter.tv.ui.navigation.Routes
import org.javerland.homecenter.tv.ui.player.PlayerFactory
import javax.inject.Inject

data class MusicUiState(
    val tracks: List<MediaItem> = emptyList(),
    val index: Int = 0,
    val playing: Boolean = false,
    val error: Throwable? = null,
) {
    val current: MediaItem? get() = tracks.getOrNull(index)
}

/**
 * Plays a whole list rather than a single file: picking one song and being returned to the
 * grid when it ends is not how anybody listens to music. ExoPlayer holds the queue, so
 * "next" costs nothing.
 */
@HiltViewModel
class MusicPlayerViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    mediaQueue: MediaQueue,
    playerFactory: PlayerFactory,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<String>(Routes.MEDIA_ID_ARG)?.toLongOrNull()
        ?: error("Prehrávač hudby otvorený bez identifikátora položky")

    val player: ExoPlayer = playerFactory.create()

    private val _state = MutableStateFlow(MusicUiState())
    val state: StateFlow<MusicUiState> = _state.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(playing = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: ExoMediaItem?, reason: Int) {
            _state.update { it.copy(index = player.currentMediaItemIndex) }
        }
    }

    init {
        player.addListener(listener)

        val queued = mediaQueue.snapshot().filter { it.category == MediaCategory.AUDIO }
        if (queued.any { it.id == mediaId }) {
            start(queued)
        } else {
            loadFromServer()
        }
    }

    fun togglePlayPause() {
        if (player.isPlaying) player.pause() else player.play()
    }

    fun next() = player.seekToNextMediaItem()

    fun previous() = player.seekToPreviousMediaItem()

    private fun start(tracks: List<MediaItem>) {
        val startIndex = tracks.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
        _state.update { it.copy(tracks = tracks, index = startIndex) }

        player.setMediaItems(tracks.map { ExoMediaItem.fromUri(it.streamUrl) }, startIndex, 0L)
        player.prepare()
        player.playWhenReady = true
    }

    private fun loadFromServer() {
        viewModelScope.launch {
            runCatching { libraryRepository.page(category = MediaCategory.AUDIO, limit = PAGE_LIMIT) }
                .fold(
                    onSuccess = { page -> start(page.items) },
                    onFailure = { failure -> _state.update { it.copy(error = failure) } },
                )
        }
    }

    override fun onCleared() {
        player.removeListener(listener)
        player.release()
    }

    private companion object {
        const val PAGE_LIMIT = 200
    }
}
