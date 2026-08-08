package org.javerlabd.homecenter.tv.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Button
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import org.javerlabd.homecenter.tv.BuildConfig
import org.javerlabd.homecenter.tv.R

/**
 * Account, server and the way out. Sources, users and scanning are deliberately absent—
 * they are set up in the browser, where there is a keyboard.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    onChangeServer: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val logoutFocus = remember { FocusRequester() }

    BackHandler(onBack = onBack)
    LaunchedEffect(state.loggedOut) {
        if (state.loggedOut) onLoggedOut()
    }
    LaunchedEffect(Unit) { logoutFocus.requestFocus() }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 72.dp, vertical = 56.dp)
                .widthIn(max = 900.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp),
        ) {
            Text(
                text = stringResource(R.string.settings_title),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )

            state.account?.let { account ->
                Fact(
                    label = stringResource(R.string.settings_account),
                    value = "${account.displayName} (${account.username})",
                )
                Fact(
                    label = stringResource(R.string.settings_role),
                    value = stringResource(
                        if (account.isAdmin) R.string.settings_role_admin else R.string.settings_role_user
                    ),
                )
            }

            state.serverUrl?.let { server ->
                Fact(label = stringResource(R.string.settings_server), value = server)
                Text(
                    text = stringResource(R.string.settings_admin_note, "$server/admin"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = stringResource(R.string.settings_logout_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Button(
                    onClick = viewModel::logout,
                    enabled = !state.loggingOut,
                    modifier = Modifier.focusRequester(logoutFocus),
                ) {
                    Text(stringResource(R.string.settings_logout))
                }
                OutlinedButton(onClick = onChangeServer) {
                    Text(stringResource(R.string.settings_change_server))
                }
                OutlinedButton(onClick = onBack) {
                    Text(stringResource(R.string.common_back))
                }
            }

            Text(
                text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun Fact(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
