package org.javerland.homecenter.tv.ui.common

import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import org.javerland.homecenter.tv.ui.theme.HomeCenterPalette

/**
 * Compose for TV has no text field—its components assume a remote, and typing is the one
 * thing a remote is bad at. The two screens that need input (server address and login)
 * therefore borrow the ordinary Material 3 field, wrapped in the project's colours so it
 * does not arrive with a light default scheme on a dark screen.
 */
private val TextFieldColorScheme = darkColorScheme(
    primary = HomeCenterPalette.Iris400,
    onPrimary = HomeCenterPalette.Slate950,
    background = HomeCenterPalette.Slate950,
    onBackground = HomeCenterPalette.Slate50,
    surface = HomeCenterPalette.Slate900,
    onSurface = HomeCenterPalette.Slate50,
    surfaceVariant = HomeCenterPalette.Slate700,
    onSurfaceVariant = HomeCenterPalette.Slate400,
    outline = HomeCenterPalette.Slate700,
    error = HomeCenterPalette.Rose400,
)

@Composable
fun HomeCenterTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    singleLine: Boolean = true,
    isError: Boolean = false,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
) {
    MaterialTheme(colorScheme = TextFieldColorScheme) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = modifier,
            label = { Text(label) },
            placeholder = placeholder?.let { { Text(it) } },
            singleLine = singleLine,
            isError = isError,
            visualTransformation = visualTransformation,
            keyboardOptions = keyboardOptions,
        )
    }
}
