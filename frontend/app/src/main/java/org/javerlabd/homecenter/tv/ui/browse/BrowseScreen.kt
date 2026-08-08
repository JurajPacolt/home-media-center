package org.javerlabd.homecenter.tv.ui.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.javerlabd.homecenter.tv.R
import org.javerlabd.homecenter.tv.domain.MediaCategory
import org.javerlabd.homecenter.tv.ui.common.EmptyState
import org.javerlabd.homecenter.tv.ui.common.ErrorState
import org.javerlabd.homecenter.tv.ui.common.HomeCenterTextField
import org.javerlabd.homecenter.tv.ui.common.LoadingState
import org.javerlabd.homecenter.tv.ui.common.MediaCard
import org.javerlabd.homecenter.tv.ui.common.errorMessage

/**
 * A grid of one category. Videos additionally get genre filtering, which is the only place
 * the finer classification from TMDb shows up on the TV.
 */
@Composable
fun BrowseScreen(
    onOpenItem: (MediaCategory, Long) -> Unit,
    viewModel: BrowseViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val gridState = rememberLazyGridState()

    // Fetch the next page while there is still a screenful left to look at, so the grid
    // does not stop under the user's thumb.
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= state.items.size - LOAD_AHEAD
        }
    }
    LaunchedEffect(gridState) {
        snapshotFlow { shouldLoadMore }.collect { if (it) viewModel.loadMore() }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 48.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            BrowseHeader(state = state, onSearchChanged = viewModel::onSearchChanged)

            if (state.category == MediaCategory.VIDEO && state.genres.isNotEmpty()) {
                GenreFilter(
                    genres = state.genres,
                    selectedGenreId = state.selectedGenreId,
                    onGenreSelected = viewModel::onGenreSelected,
                )
            }

            when {
                state.loading && state.items.isEmpty() -> LoadingState()

                state.error != null && state.items.isEmpty() -> ErrorState(
                    message = errorMessage(state.error),
                    onRetry = viewModel::reload,
                )

                state.items.isEmpty() -> EmptyState(stringResource(R.string.browse_empty))

                else -> LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = COLUMN_WIDTH.dp),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(20.dp),
                    verticalArrangement = Arrangement.spacedBy(20.dp),
                    contentPadding = PaddingValues(bottom = 32.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(state.items, key = { _, item -> item.id }) { _, item ->
                        MediaCard(
                            item = item,
                            onClick = { onOpenItem(item.category, item.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseHeader(
    state: BrowseUiState,
    onSearchChanged: (String) -> Unit,
) {
    val title = when (state.category) {
        MediaCategory.VIDEO -> R.string.home_videos
        MediaCategory.PHOTO -> R.string.home_photos
        MediaCategory.AUDIO -> R.string.home_music
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.browse_count, state.items.size, state.total),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        HomeCenterTextField(
            value = state.search,
            onValueChange = onSearchChanged,
            label = stringResource(R.string.browse_search),
            placeholder = stringResource(R.string.browse_search_hint),
            modifier = Modifier.width(360.dp),
        )
    }
}

@Composable
private fun GenreFilter(
    genres: List<org.javerlabd.homecenter.tv.domain.Genre>,
    selectedGenreId: Long?,
    onGenreSelected: (Long?) -> Unit,
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            GenreButton(
                label = stringResource(R.string.browse_all_genres),
                selected = selectedGenreId == null,
                onClick = { onGenreSelected(null) },
            )
        }
        items(genres, key = { it.id }) { genre ->
            GenreButton(
                label = genre.name,
                selected = selectedGenreId == genre.id,
                onClick = { onGenreSelected(genre.id) },
            )
        }
    }
}

/**
 * A filled button marks the active filter. tv-material has chips, but their selected state
 * is far less visible from across a room than a solid fill.
 */
@Composable
private fun GenreButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(onClick = onClick) { Text(label) }
    } else {
        OutlinedButton(onClick = onClick) { Text(label) }
    }
}

private const val COLUMN_WIDTH = 200
private const val LOAD_AHEAD = 12
