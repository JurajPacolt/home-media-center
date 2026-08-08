package org.javerland.homecenter.tv.ui.detail

import androidx.lifecycle.SavedStateHandle
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
import org.javerland.homecenter.tv.domain.MediaCategory
import org.javerland.homecenter.tv.domain.MediaItem
import org.javerland.homecenter.tv.ui.navigation.Routes
import javax.inject.Inject

data class DetailUiState(
    val loading: Boolean = true,
    val item: MediaItem? = null,
    /** Episodes of the same series, or parts of the same film, in playing order. */
    val siblings: List<MediaItem> = emptyList(),
    val resume: ResumePoint? = null,
    val error: Throwable? = null,
)

@HiltViewModel
class DetailViewModel @Inject constructor(
    private val libraryRepository: LibraryRepository,
    private val playbackRepository: PlaybackRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val mediaId: Long = savedStateHandle.get<String>(Routes.MEDIA_ID_ARG)?.toLongOrNull()
        ?: error("Detail otvorený bez identifikátora položky")

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            runCatching { libraryRepository.item(mediaId) }.fold(
                onSuccess = { item ->
                    _state.update {
                        it.copy(
                            loading = false,
                            item = item,
                            resume = playbackRepository.resumePoint(item.id),
                            error = null,
                        )
                    }
                    loadSiblings(item)
                },
                onFailure = { failure ->
                    _state.update { it.copy(loading = false, error = failure) }
                },
            )
        }
    }

    /**
     * The API has no "everything in this group" endpoint, but its search covers the group
     * title, so one query brings back the whole series and the group key sorts out anything
     * that merely shares a name.
     */
    private fun loadSiblings(item: MediaItem) {
        val groupKey = item.groupKey ?: return
        val groupTitle = item.groupTitle ?: return

        viewModelScope.launch {
            runCatching {
                libraryRepository.page(
                    category = MediaCategory.VIDEO,
                    search = groupTitle,
                    limit = GROUP_LIMIT,
                )
            }.onSuccess { page ->
                val ordered = page.items
                    .filter { it.groupKey == groupKey }
                    .sortedWith(
                        compareBy(
                            { it.seasonNumber ?: Int.MAX_VALUE },
                            { it.episodeNumber ?: Int.MAX_VALUE },
                            { it.partNumber ?: Int.MAX_VALUE },
                            { it.title },
                        )
                    )
                // A single result is the item itself; a list of one is noise on the screen.
                _state.update { it.copy(siblings = if (ordered.size > 1) ordered else emptyList()) }
            }
        }
    }

    private companion object {
        /** Long-running series exist, but a page this size covers them without paging. */
        const val GROUP_LIMIT = 300
    }
}
