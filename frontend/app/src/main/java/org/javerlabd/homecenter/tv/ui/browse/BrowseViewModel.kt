package org.javerlabd.homecenter.tv.ui.browse

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.javerlabd.homecenter.tv.data.repository.LibraryRepository
import org.javerlabd.homecenter.tv.data.repository.MediaQueue
import org.javerlabd.homecenter.tv.domain.Genre
import org.javerlabd.homecenter.tv.domain.MediaCategory
import org.javerlabd.homecenter.tv.domain.MediaItem
import org.javerlabd.homecenter.tv.ui.navigation.Routes
import javax.inject.Inject

data class BrowseUiState(
    val category: MediaCategory,
    val items: List<MediaItem> = emptyList(),
    val total: Long = 0,
    val genres: List<Genre> = emptyList(),
    val selectedGenreId: Long? = null,
    val search: String = "",
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: Throwable? = null,
) {
    val hasMore: Boolean get() = items.size < total
}

@OptIn(FlowPreview::class)
@HiltViewModel
class BrowseViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val mediaQueue: MediaQueue,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val category: MediaCategory =
        MediaCategory.valueOf(savedStateHandle.get<String>(Routes.CATEGORY_ARG) ?: MediaCategory.VIDEO.name)

    private val _state = MutableStateFlow(BrowseUiState(category = category))
    val state: StateFlow<BrowseUiState> = _state.asStateFlow()

    private val searchInput = MutableStateFlow("")
    private var pageJob: Job? = null

    init {
        reload()
        if (category == MediaCategory.VIDEO) loadGenres()

        viewModelScope.launch {
            // Typing on a remote is slow enough that a request per keystroke would mostly
            // send queries nobody finished writing.
            searchInput
                .drop(1)
                .debounce(SEARCH_DEBOUNCE_MS)
                .distinctUntilChanged()
                .collect { reload() }
        }
    }

    fun onSearchChanged(value: String) {
        _state.update { it.copy(search = value) }
        searchInput.value = value
    }

    fun onGenreSelected(genreId: Long?) {
        if (_state.value.selectedGenreId == genreId) return
        _state.update { it.copy(selectedGenreId = genreId) }
        reload()
    }

    fun reload() {
        pageJob?.cancel()
        pageJob = viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            val current = _state.value
            runCatching {
                libraryRepository.page(
                    category = current.category,
                    genreId = current.selectedGenreId,
                    search = current.search,
                    offset = 0,
                )
            }.fold(
                onSuccess = { page ->
                    _state.update {
                        it.copy(loading = false, items = page.items, total = page.total, error = null)
                    }
                    mediaQueue.replaceWith(page.items)
                },
                onFailure = { failure ->
                    _state.update { it.copy(loading = false, error = failure) }
                },
            )
        }
    }

    /**
     * Called as the grid nears its end. The server pages by offset, and the whole library
     * would otherwise arrive in one response—a photo archive can be tens of thousands of
     * rows.
     */
    fun loadMore() {
        val current = _state.value
        if (current.loading || current.loadingMore || !current.hasMore) return

        viewModelScope.launch {
            _state.update { it.copy(loadingMore = true) }
            runCatching {
                libraryRepository.page(
                    category = current.category,
                    genreId = current.selectedGenreId,
                    search = current.search,
                    offset = current.items.size,
                )
            }.fold(
                onSuccess = { page ->
                    val combined = _state.value.items + page.items
                    _state.update {
                        it.copy(loadingMore = false, items = combined, total = page.total)
                    }
                    mediaQueue.replaceWith(combined)
                },
                // A failed follow-up page leaves what is already on screen alone; the user
                // can keep scrolling and it will be retried.
                onFailure = { _state.update { it.copy(loadingMore = false) } },
            )
        }
    }

    private fun loadGenres() {
        viewModelScope.launch {
            runCatching { libraryRepository.genres() }
                .onSuccess { genres -> _state.update { it.copy(genres = genres) } }
        }
    }

    private companion object {
        const val SEARCH_DEBOUNCE_MS = 400L
    }
}
