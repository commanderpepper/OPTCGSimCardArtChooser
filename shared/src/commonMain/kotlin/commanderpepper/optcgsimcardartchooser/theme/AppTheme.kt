package commanderpepper.optcgsimcardartchooser.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Material theme in light or dark colours, per [mode], with all text scaled to [textScale] percent.
 * Scaling the font scale scales every `sp` size (the whole type scale, menus and dialogs) while
 * `dp` layout sizes stay put.
 */
@Composable
fun AppTheme(mode: ThemeMode, textScale: Int = TextScale.DEFAULT, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val density = LocalDensity.current
    CompositionLocalProvider(LocalDensity provides Density(density.density, density.fontScale * textScale / 100f)) {
        MaterialTheme(colorScheme = if (dark) darkColorScheme() else lightColorScheme(), content = content)
    }
}

/** Buttons for changing the text size. */
class TextSizeActions(
    val onIncrease: () -> Unit,
    val onDecrease: () -> Unit,
    val onReset: () -> Unit,
)
