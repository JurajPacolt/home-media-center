package org.javerland.homecenter.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Surface
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import org.javerland.homecenter.tv.data.session.Session
import org.javerland.homecenter.tv.data.session.SessionStore
import org.javerland.homecenter.tv.ui.common.LoadingState
import org.javerland.homecenter.tv.ui.navigation.HomeCenterNavHost
import org.javerland.homecenter.tv.ui.navigation.Routes
import org.javerland.homecenter.tv.ui.theme.HomeCenterTheme
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            HomeCenterTheme {
                HomeCenterApp()
            }
        }
    }
}

@HiltViewModel
class SessionViewModel @Inject constructor(
    sessionStore: SessionStore,
) : ViewModel() {
    val session: StateFlow<Session?> = sessionStore.session
    val expired: SharedFlow<Unit> = sessionStore.expired
}

/**
 * Decides where the app opens. The stored session is read from disk, so it is null for the
 * first moment—waiting for it avoids showing the login screen to somebody who is already
 * signed in, which would be a visible flash on every launch.
 */
@Composable
private fun HomeCenterApp(viewModel: SessionViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()

    val current = session
    if (current == null) {
        Surface(modifier = Modifier.fillMaxSize()) { LoadingState() }
        return
    }

    val startDestination = when {
        !current.hasServer -> Routes.SERVER
        !current.isLoggedIn -> Routes.LOGIN
        else -> Routes.HOME
    }

    HomeCenterNavHost(
        startDestination = startDestination,
        sessionExpired = viewModel.expired,
    )
}
