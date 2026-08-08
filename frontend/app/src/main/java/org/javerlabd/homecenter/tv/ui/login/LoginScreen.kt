package org.javerlabd.homecenter.tv.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.javerlabd.homecenter.tv.R
import org.javerlabd.homecenter.tv.ui.common.HomeCenterTextField

/**
 * The password and the PIN are the same field on purpose. The server tries both, so asking
 * the user which one they are about to type would be a question with no useful answer.
 */
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit,
    onChangeServer: () -> Unit,
    viewModel: LoginViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val usernameFocus = remember { FocusRequester() }

    LaunchedEffect(state.loggedIn) {
        if (state.loggedIn) onLoggedIn()
    }
    LaunchedEffect(Unit) { usernameFocus.requestFocus() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 96.dp, vertical = 64.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text(
                    text = stringResource(R.string.login_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = stringResource(R.string.login_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                HomeCenterTextField(
                    value = state.username,
                    onValueChange = viewModel::onUsernameChanged,
                    label = stringResource(R.string.login_username),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(usernameFocus),
                )

                HomeCenterTextField(
                    value = state.secret,
                    onValueChange = viewModel::onSecretChanged,
                    label = stringResource(R.string.login_secret),
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions.Default,
                    modifier = Modifier.fillMaxWidth(),
                )

                state.error?.let { error ->
                    Text(
                        text = stringResource(error),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Button(onClick = viewModel::login, enabled = !state.working) {
                        Text(
                            stringResource(
                                if (state.working) R.string.login_working else R.string.login_submit
                            )
                        )
                    }
                    OutlinedButton(onClick = onChangeServer) {
                        Text(stringResource(R.string.login_change_server))
                    }
                }
            }
        }
    }
}
