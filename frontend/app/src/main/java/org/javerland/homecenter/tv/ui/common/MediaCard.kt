package org.javerland.homecenter.tv.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Card
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.javerland.homecenter.tv.domain.MediaCategory
import org.javerland.homecenter.tv.domain.MediaItem
import org.javerland.homecenter.tv.ui.theme.HomeCenterPalette
import org.javerland.homecenter.tv.ui.theme.accent

/**
 * One item in a grid. Videos get a poster in the usual 2:3 shape; photos and music are
 * wider, because a square-ish tile reads better for a picture and an album has no poster
 * at all.
 */
@Composable
fun MediaCard(
    item: MediaItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ratio = if (item.category == MediaCategory.VIDEO) POSTER_RATIO else WIDE_RATIO

    Card(onClick = onClick, modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(ratio)
        ) {
            MediaArtwork(item)
        }
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val caption = item.sequenceLabel ?: item.releaseYear?.toString() ?: item.extension.uppercase()
            Text(
                text = caption,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

/**
 * A poster when the server has one. Photos show themselves—the stream address is the image
 * —while anything else falls back to a tinted plate with the title on it, which is still
 * more use than an empty grey rectangle.
 *
 * The placeholder is drawn underneath rather than swapped in on failure: a picture that
 * does not load leaves the image transparent, so what shows through is exactly the state
 * this should end in, with no error callback to wire up.
 */
@Composable
private fun MediaArtwork(item: MediaItem) {
    val artwork = item.posterUrl ?: item.streamUrl.takeIf { item.category == MediaCategory.PHOTO }

    PlaceholderArtwork(item)
    if (artwork != null) {
        AsyncImage(
            model = artwork,
            contentDescription = item.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun PlaceholderArtwork(item: MediaItem) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(
                        item.category.accent.copy(alpha = 0.35f),
                        HomeCenterPalette.Slate900,
                    )
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .padding(12.dp)
                .alpha(0.85f),
        )
    }
}

private const val POSTER_RATIO = 2f / 3f
private const val WIDE_RATIO = 16f / 10f
