package org.javerland.homecenter.tv.ui.player

import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.PlayerView
import org.javerland.homecenter.tv.R
import org.javerland.homecenter.tv.ui.common.ErrorState
import org.javerland.homecenter.tv.ui.common.errorMessage
import org.javerland.homecenter.tv.ui.theme.HomeCenterPalette

/**
 * Full-screen playback. The controls come from Media3's own [PlayerView] rather than being
 * rebuilt in Compose: it already handles a D-pad, a play/pause key and the seek bar the way
 * a TV remote expects, and reimplementing that would mean reimplementing its focus rules
 * too.
 */
@OptIn(UnstableApi::class)
@Composable
fun VideoPlayerScreen(
    onBack: () -> Unit,
    viewModel: VideoPlayerViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    BackHandler {
        viewModel.rememberPosition()
        onBack()
    }

    // Playback stops when the box goes to sleep or the user switches to another app;
    // otherwise the sound would keep coming out of a television showing something else.
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_STOP) {
                viewModel.rememberPosition()
                viewModel.player.pause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(state.finished) {
        if (state.finished) onBack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HomeCenterPalette.Slate950)
    ) {
        when {
            state.error != null -> ErrorState(message = errorMessage(state.error))

            state.playbackFailed -> ErrorState(message = stringResource(R.string.player_error))

            else -> AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = viewModel.player
                        useController = true
                        controllerShowTimeoutMs = CONTROLLER_TIMEOUT_MS
                        setShowNextButton(false)
                        setShowPreviousButton(false)
                        // The player is the only thing on screen, so it should be holding
                        // focus the moment it appears—otherwise the first D-pad press goes
                        // nowhere.
                        requestFocus()
                    }
                },
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

private const val CONTROLLER_TIMEOUT_MS = 4000
