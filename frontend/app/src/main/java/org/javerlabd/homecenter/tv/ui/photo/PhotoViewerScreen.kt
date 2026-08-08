package org.javerlabd.homecenter.tv.ui.photo

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import org.javerlabd.homecenter.tv.R
import org.javerlabd.homecenter.tv.ui.common.ErrorState
import org.javerlabd.homecenter.tv.ui.common.LoadingState
import org.javerlabd.homecenter.tv.ui.common.errorMessage
import org.javerlabd.homecenter.tv.ui.theme.HomeCenterPalette

/**
 * One photo at a time, left and right to move. There is nothing to focus on screen, so the
 * key handling hangs off an invisible focusable that takes focus as soon as it appears.
 */
@Composable
fun PhotoViewerScreen(
    onBack: () -> Unit,
    viewModel: PhotoViewerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    BackHandler(onBack = onBack)
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeCenterPalette.Slate950)
            .focusRequester(focusRequester)
            .focusable()
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionRight, Key.MediaNext -> {
                        viewModel.next(); true
                    }
                    Key.DirectionLeft, Key.MediaPrevious -> {
                        viewModel.previous(); true
                    }
                    else -> false
                }
            }
    ) {
        when {
            state.error != null -> ErrorState(message = errorMessage(state.error))

            state.photos.isEmpty() -> LoadingState()

            else -> {
                val photo = state.current
                if (photo != null) {
                    AsyncImage(
                        model = photo.streamUrl,
                        contentDescription = photo.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize(),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(40.dp),
                    ) {
                        Text(
                            text = photo.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = stringResource(
                                R.string.photo_position,
                                state.index + 1,
                                state.photos.size,
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
