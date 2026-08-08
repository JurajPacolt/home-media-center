package org.javerland.homecenter.tv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import org.javerland.homecenter.tv.domain.MediaCategory

/**
 * The same palette as the management UI (doc/design-system.md): Iris for the brand, Aqua
 * for activity, blue-tinted Slate for surfaces, Amber and Rose for the rest.
 *
 * Only the dark theme exists here. A television in a living room is looked at from three
 * metres away in the evening, and the design system already treats dark as a first-class
 * theme rather than an inversion—the values below are its dark-side roles.
 */
object HomeCenterPalette {
    val Iris300 = Color(0xFFADAEF6)
    val Iris400 = Color(0xFF8B87EF)
    val Iris600 = Color(0xFF6149D9)
    val Iris950 = Color(0xFF241B4C)

    val Aqua300 = Color(0xFF5CD4D3)
    val Aqua400 = Color(0xFF2BB8B9)

    val Amber300 = Color(0xFFF6CD6B)
    val Rose400 = Color(0xFFE8747C)
    val Green400 = Color(0xFF45BD7C)

    val Slate950 = Color(0xFF0E1017)
    val Slate900 = Color(0xFF161A24)
    val Slate700 = Color(0xFF3C4359)
    val Slate400 = Color(0xFF9AA1B8)
    val Slate200 = Color(0xFFE2E5EF)
    val Slate50 = Color(0xFFF7F8FC)
}

/** Each of the three categories keeps one colour everywhere it appears, as on the web. */
val MediaCategory.accent: Color
    get() = when (this) {
        MediaCategory.VIDEO -> HomeCenterPalette.Iris400
        MediaCategory.PHOTO -> HomeCenterPalette.Aqua300
        MediaCategory.AUDIO -> HomeCenterPalette.Amber300
    }

private val HomeCenterColorScheme = darkColorScheme(
    primary = HomeCenterPalette.Iris400,
    onPrimary = HomeCenterPalette.Slate950,
    primaryContainer = HomeCenterPalette.Iris600,
    onPrimaryContainer = HomeCenterPalette.Slate50,
    secondary = HomeCenterPalette.Aqua300,
    onSecondary = HomeCenterPalette.Slate950,
    secondaryContainer = HomeCenterPalette.Aqua400,
    onSecondaryContainer = HomeCenterPalette.Slate950,
    background = HomeCenterPalette.Slate950,
    onBackground = HomeCenterPalette.Slate50,
    surface = HomeCenterPalette.Slate900,
    onSurface = HomeCenterPalette.Slate50,
    // Surfaces rise by getting lighter; a shadow cannot darken an already dark surface.
    surfaceVariant = HomeCenterPalette.Slate700,
    onSurfaceVariant = HomeCenterPalette.Slate200,
    border = HomeCenterPalette.Slate700,
    error = HomeCenterPalette.Rose400,
    onError = HomeCenterPalette.Slate950,
)

@Composable
fun HomeCenterTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = HomeCenterColorScheme, content = content)
}
