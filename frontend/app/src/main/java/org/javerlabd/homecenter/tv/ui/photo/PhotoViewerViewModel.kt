package org.javerlabd.homecenter.tv.ui.photo

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.javerlabd.homecenter.tv.data.repository.LibraryRepository
import org.javerlabd.homecenter.tv.data.repository.MediaQueue
import org.javerlabd.homecenter.tv.domain.MediaCategory
import org.javerlabd.homecenter.tv.domain.MediaItem
import org.javerlabd.homecenter.tv.ui.navigation.Routes
import javax.inject.Inject

data class PhotoUiState(
    val photos: List<MediaItem> = emptyList(),
    val index: Int = 0,
    val error: Throwable? = null,
) {
    val current: MediaItem? get() = photos.getOrNull(index)
}

@HiltViewModel
class PhotoViewerViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    mediaQueue: MediaQueue,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<String>(Routes.MEDIA_ID_ARG)?.toLongOrNull()
        ?: error("Prehliadač fotiek otvorený bez identifikátora položky")

    private val _state = MutableStateFlow(PhotoUiState())
    val state: StateFlow<PhotoUiState> = _state.asStateFlow()

    init {
        // Whatever the user was just looking at is already in memory, so left and right
        // move instantly. Opening a photo any other way—from a resumed session, say—falls
        // back to fetching a page.
        val queued = mediaQueue.snapshot().filter { it.category == MediaCategory.PHOTO }
        val index = queued.indexOfFirst { it.id == mediaId }
        if (index >= 0) {
            _state.update { it.copy(photos = queued, index = index) }
        } else {
            loadFromServer()
        }
    }

    fun next() = move(1)

    fun previous() = move(-1)

    private fun move(step: Int) {
        _state.update { current ->
            if (current.photos.isEmpty()) {
                current
            } else {
                // Wrapping around means the end of an album is never a dead end.
                val size = current.photos.size
                current.copy(index = ((current.index + step) % size + size) % size)
            }
        }
    }

    private fun loadFromServer() {
        viewModelScope.launch {
            runCatching { libraryRepository.page(category = MediaCategory.PHOTO, limit = PAGE_LIMIT) }
                .fold(
                    onSuccess = { page ->
                        val index = page.items.indexOfFirst { it.id == mediaId }.coerceAtLeast(0)
                        _state.update { it.copy(photos = page.items, index = index) }
                    },
                    onFailure = { failure -> _state.update { it.copy(error = failure) } },
                )
        }
    }

    private companion object {
        const val PAGE_LIMIT = 200
    }
}
