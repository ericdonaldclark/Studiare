package net.ericclark.studiare.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import com.materialkolor.PaletteStyle
import com.materialkolor.dynamicColorScheme

/**
 * Generates a full Material 3 ColorScheme (every surface-container tier, fixed-color role, and
 * inverse color included) from the 4 user-picked custom-theme colors, via MaterialKolor's real
 * HCT/tonal-palette algorithm (a Kotlin wrapper around Google's material-color-utilities)
 * instead of hand-rolled HSL math. `neutral = background` keeps the dialog's "Background" picker
 * driving the surface/neutral ladder, same as it already did before this rewrite.
 */
fun generateCustomScheme(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
    isDark: Boolean
): ColorScheme = dynamicColorScheme(
    seedColor = primary,
    isDark = isDark,
    style = PaletteStyle.Expressive,
    primary = primary,
    secondary = secondary,
    tertiary = tertiary,
    neutral = background,
)
