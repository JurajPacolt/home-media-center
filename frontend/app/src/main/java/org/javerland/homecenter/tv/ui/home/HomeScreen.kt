package org.javerland.homecenter.tv.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.javerland.homecenter.tv.R
import org.javerland.homecenter.tv.data.repository.ResumePoint
import org.javerland.homecenter.tv.domain.MediaCategory
import org.javerland.homecenter.tv.ui.common.ErrorState
import org.javerland.homecenter.tv.ui.common.LoadingState
import org.javerland.homecenter.tv.ui.common.errorMessage
import org.javerland.homecenter.tv.ui.common.formatDuration
import org.javerland.homecenter.tv.ui.common.formatInstant
import org.javerland.homecenter.tv.ui.theme.HomeCenterPalette
import org.javerland.homecenter.tv.ui.theme.accent

/**
 * The three tiles the whole design is built around. Everything a remote can reach from
 * here is watching, looking or listening—configuration lives in the browser.
 */
@Composable
fun HomeScreen(
    onOpenCategory: (MediaCategory) -> Unit,
    onOpenItem: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val firstTile = remember { FocusRequester() }

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.loading && state.summary == null -> LoadingState()

            state.summary == null -> ErrorState(
                message = errorMessage(state.error),
                onRetry = viewModel::load,
            )

            else -> {
                val summary = state.summary!!
                LaunchedEffect(Unit) { firstTile.requestFocus() }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 56.dp, vertical = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(40.dp),
                ) {
                    HomeHeader(
                        displayName = state.account?.displayName,
                        lastScan = formatInstant(summary.lastScanFinishedAt),
                        onOpenSettings = onOpenSettings,
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        MediaCategory.entries.forEachIndexed { index, category ->
                            CategoryTile(
                                category = category,
                                count = summary.count(category),
                                onClick = { onOpenCategory(category) },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(220.dp)
                                    .then(
                                        if (index == 0) Modifier.focusRequester(firstTile) else Modifier
                                    ),
                            )
                        }
                    }

                    if (state.resume.isNotEmpty()) {
                        ContinueWatching(points = state.resume, onOpenItem = onOpenItem)
                    } else if (summary.totalItems == 0L) {
                        Text(
                            text = stringResource(R.string.home_empty_library),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeHeader(
    displayName: String?,
    lastScan: String?,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = displayName?.let { stringResource(R.string.home_greeting, it) }
                    ?: stringResource(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            lastScan?.let {
                Text(
                    text = stringResource(R.string.home_last_scan, it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        OutlinedButton(onClick = onOpenSettings) {
            Text(stringResource(R.string.home_settings))
        }
    }
}

@Composable
private fun CategoryTile(
    category: MediaCategory,
    count: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (category) {
        MediaCategory.VIDEO -> R.string.home_videos
        MediaCategory.PHOTO -> R.string.home_photos
        MediaCategory.AUDIO -> R.string.home_music
    }

    Card(onClick = onClick, modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        listOf(category.accent.copy(alpha = 0.45f), HomeCenterPalette.Slate900)
                    )
                )
                .padding(28.dp),
            contentAlignment = Alignment.BottomStart,
        ) {
            Column {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = pluralStringResource(R.plurals.home_items, count.toInt(), count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ContinueWatching(
    points: List<ResumePoint>,
    onOpenItem: (Long) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = stringResource(R.string.home_continue),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            items(points, key = { it.mediaId }) { point ->
                Card(
                    onClick = { onOpenItem(point.mediaId) },
                    modifier = Modifier.width(280.dp),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = point.title,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = formatDuration(point.positionMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
