package net.ericclark.studiare.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils

/**
 * Generates a Material 3 ColorScheme based on 4 core colors provided by the user.
 * It mathematically derives the container and content colors to ensure readability.
 */
fun generateCustomScheme(
    primary: Color,
    secondary: Color,
    tertiary: Color,
    background: Color,
    isDark: Boolean
): ColorScheme {
    val error = Color(0xFFB3261E) // Standard M3 Error

    // Helper to generate tonal variants (mixing with white/black)
    fun makeTone(color: Color, lightness: Float): Color {
        val hsl = FloatArray(3)
        ColorUtils.colorToHSL(color.toArgb(), hsl)
        // Adjust lightness (0.0 = Black, 1.0 = White)
        // We clamp it slightly to keep some color
        hsl[2] = lightness.coerceIn(0f, 1f)
        return Color(ColorUtils.HSLToColor(hsl))
    }

    // Helper to pick Black or White text based on background contrast
    fun onColor(bg: Color): Color {
        val contrastWhite = ColorUtils.calculateContrast(android.graphics.Color.WHITE, bg.toArgb())
        val contrastBlack = ColorUtils.calculateContrast(android.graphics.Color.BLACK, bg.toArgb())
        return if (contrastBlack > contrastWhite) Color.Black else Color.White
    }

    // --- Generate Derived Colors ---
    // Containers are usually lighter in Light Mode (90%) and darker in Dark Mode (30%)
    val primaryContainer = if (isDark) makeTone(primary, 0.3f) else makeTone(primary, 0.9f)
    val secondaryContainer = if (isDark) makeTone(secondary, 0.3f) else makeTone(secondary, 0.9f)
    val tertiaryContainer = if (isDark) makeTone(tertiary, 0.3f) else makeTone(tertiary, 0.9f)
    val errorContainer = if (isDark) makeTone(error, 0.3f) else makeTone(error, 0.9f)

    // "On" Colors (Text/Icons on top of main colors)
    val onPrimary = onColor(primary)
    val onSecondary = onColor(secondary)
    val onTertiary = onColor(tertiary)
    val onError = onColor(error)
    val onBackground = onColor(background)

    val onPrimaryContainer = onColor(primaryContainer)
    val onSecondaryContainer = onColor(secondaryContainer)
    val onTertiaryContainer = onColor(tertiaryContainer)
    val onErrorContainer = onColor(errorContainer)

    // Surface often matches background in simple custom themes, or slightly shifted
    val surface = background
    val onSurface = onBackground

    // Surface Variant (used for borders/dividers) - Slightly distinctive from background
    val surfaceVariant = if (isDark) makeTone(background, 0.3f) else makeTone(background, 0.9f)
    val onSurfaceVariant = onColor(surfaceVariant)

    val outline = if (isDark) makeTone(background, 0.6f) else makeTone(background, 0.5f)

    return if (isDark) {
        darkColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline
        )
    } else {
        lightColorScheme(
            primary = primary,
            onPrimary = onPrimary,
            primaryContainer = primaryContainer,
            onPrimaryContainer = onPrimaryContainer,
            secondary = secondary,
            onSecondary = onSecondary,
            secondaryContainer = secondaryContainer,
            onSecondaryContainer = onSecondaryContainer,
            tertiary = tertiary,
            onTertiary = onTertiary,
            tertiaryContainer = tertiaryContainer,
            onTertiaryContainer = onTertiaryContainer,
            background = background,
            onBackground = onBackground,
            surface = surface,
            onSurface = onSurface,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = onSurfaceVariant,
            error = error,
            onError = onError,
            errorContainer = errorContainer,
            onErrorContainer = onErrorContainer,
            outline = outline
        )
    }
}