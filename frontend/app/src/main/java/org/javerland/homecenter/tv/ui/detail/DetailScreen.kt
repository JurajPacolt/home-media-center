package org.javerland.homecenter.tv.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.javerland.homecenter.tv.R
import org.javerland.homecenter.tv.domain.MediaItem
import org.javerland.homecenter.tv.ui.common.ErrorState
import org.javerland.homecenter.tv.ui.common.LoadingState
import org.javerland.homecenter.tv.ui.common.errorMessage
import org.javerland.homecenter.tv.ui.common.formatDuration
import org.javerland.homecenter.tv.ui.common.formatSize
import org.javerland.homecenter.tv.ui.theme.HomeCenterPalette

/**
 * Everything known about one video before playing it. When the item belongs to a series or
 * a multipart film, its siblings are listed here in playing order—which is where the
 * server's season and episode numbers earn their keep, since filenames sort S01E10 before
 * S01E02.
 */
@Composable
fun DetailScreen(
    onPlay: (mediaId: Long, fromStart: Boolean) -> Unit,
    onOpenItem: (Long) -> Unit,
    viewModel: DetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading && state.item == null -> LoadingState()

            state.item == null -> ErrorState(
                message = errorMessage(state.error),
                onRetry = viewModel::load,
            )

            else -> {
                val item = state.item!!
                LaunchedEffect(item.id) { playFocus.requestFocus() }

                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 56.dp, vertical = 40.dp),
                    horizontalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    Poster(item)

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = item.groupTitle ?: item.title,
                            style = MaterialTheme.typography.headlineLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Facts(item)

                        item.description?.let { description ->
                            Text(
                                text = description,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            val resume = state.resume
                            if (resume != null) {
                                Button(
                                    onClick = { onPlay(item.id, false) },
                                    modifier = Modifier.focusRequester(playFocus),
                                ) {
                                    Text(
                                        stringResource(
                                            R.string.detail_resume,
                                            formatDuration(resume.positionMs)
                                        )
                                    )
                                }
                                OutlinedButton(onClick = { onPlay(item.id, true) }) {
                                    Text(stringResource(R.string.detail_from_start))
                                }
                            } else {
                                Button(
                                    onClick = { onPlay(item.id, true) },
                                    modifier = Modifier.focusRequester(playFocus),
                                ) {
                                    Text(stringResource(R.string.detail_play))
                                }
                            }
                        }

                        if (state.siblings.isNotEmpty()) {
                            SiblingList(
                                items = state.siblings,
                                currentId = item.id,
                                onOpenItem = onOpenItem,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Poster(item: MediaItem) {
    Box(
        modifier = Modifier
            .width(300.dp)
            .aspectRatio(2f / 3f)
            .background(HomeCenterPalette.Slate900)
    ) {
        item.posterUrl?.let { poster ->
            AsyncImage(
                model = poster,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun Facts(item: MediaItem) {
    val facts = buildList {
        item.releaseYear?.let { add(it.toString()) }
        item.rating?.let { add(stringResource(R.string.detail_rating, "%.1f".format(it))) }
        item.sequenceLabel?.let { add(it) }
        add(item.extension.uppercase())
        add(formatSize(item.sizeBytes))
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = facts.joinToString(" · "),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (item.genres.isNotEmpty()) {
            Text(
                text = item.genres.joinToString(", ") { it.name },
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}

@Composable
private fun SiblingList(
    items: List<MediaItem>,
    currentId: Long,
    onOpenItem: (Long) -> Unit,
) {
    val heading = if (items.any { it.seasonNumber != null }) {
        R.string.detail_episodes
    } else {
        R.string.detail_parts
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(heading),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        // Bounded so the row of buttons above stays reachable; the list scrolls on its own.
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
        ) {
            items(items, key = { it.id }) { sibling ->
                Card(onClick = { onOpenItem(sibling.id) }, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Text(
                            text = sibling.sequenceLabel.orEmpty(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.width(80.dp),
                        )
                        Text(
                            text = sibling.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (sibling.id == currentId) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurface
                            },
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
