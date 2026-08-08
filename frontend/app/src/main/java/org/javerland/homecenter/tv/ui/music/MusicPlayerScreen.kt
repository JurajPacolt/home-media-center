package org.javerland.homecenter.tv.ui.music

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.javerland.homecenter.tv.R
import org.javerland.homecenter.tv.ui.common.ErrorState
import org.javerland.homecenter.tv.ui.common.LoadingState
import org.javerland.homecenter.tv.ui.common.errorMessage
import org.javerland.homecenter.tv.ui.theme.HomeCenterPalette

@Composable
fun MusicPlayerScreen(
    onBack: () -> Unit,
    viewModel: MusicPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val playFocus = remember { FocusRequester() }
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler(onBack = onBack)
    LaunchedEffect(state.tracks.isNotEmpty()) {
        if (state.tracks.isNotEmpty()) playFocus.requestFocus()
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) viewModel.player.pause()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        when {
            state.error != null -> ErrorState(message = errorMessage(state.error))

            state.tracks.isEmpty() -> LoadingState()

            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(64.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(220.dp)
                        .background(
                            Brush.linearGradient(
                                listOf(HomeCenterPalette.Amber300.copy(alpha = 0.4f), HomeCenterPalette.Slate900)
                            )
                        )
                )

                Text(
                    text = stringResource(R.string.music_now_playing),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 32.dp),
                )
                Text(
                    text = state.current?.title.orEmpty(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 760.dp)
                        .padding(top = 8.dp),
                )
                Text(
                    text = stringResource(
                        R.string.photo_position,
                        state.index + 1,
                        state.tracks.size,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.padding(top = 40.dp),
                ) {
                    OutlinedButton(onClick = viewModel::previous) {
                        Text(stringResource(R.string.music_previous))
                    }
                    Button(
                        onClick = viewModel::togglePlayPause,
                        modifier = Modifier.focusRequester(playFocus),
                    ) {
                        Text(stringResource(R.string.music_play_pause))
                    }
                    OutlinedButton(onClick = viewModel::next) {
                        Text(stringResource(R.string.music_next))
                    }
                }
            }
        }
    }
}
