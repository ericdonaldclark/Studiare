package net.ericclark.studiare.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme // CHANGED
import androidx.compose.material3.MotionScheme // NEW
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

internal val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

internal val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40

    /* Other default colors to override
    background = Color(0xFFFFFBFE),
    surface = Color(0xFFFFFBFE),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C1B1F),
    onSurface = Color(0xFF1C1B1F),
    */
)


/*
@Composable
fun StudiareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}


@Composable
fun StudiareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }
/*
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.primary.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = darkTheme
        }
    }
*/
    // CHANGED: Use MaterialExpressiveTheme instead of MaterialTheme
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        motionScheme = MotionScheme.expressive(), // NEW: Adds the bouncy/spring physics
        content = content
    )
}
 */

@Composable
fun StudiareTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    // NEW: Optional override
    customColorScheme: androidx.compose.material3.ColorScheme? = null,
    reducedMotion: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        customColorScheme != null -> customColorScheme // Use custom if provided
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    // StudiareTheme is invoked above/outside where LocalStudiareDimensions' per-tier value
    // resolves (in MainActivity), so this uses the Normal tier as a fixed baseline rather than
    // reacting to spacingMode. Most of the app's own components already pass
    // dimensions.cornerRadiusX directly as their shape, bypassing M3's internal defaults — this
    // mainly backstops any stock M3 component that doesn't set its own shape.
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(NormalShapeScale.small),
        small = RoundedCornerShape(NormalShapeScale.small),
        medium = RoundedCornerShape(NormalShapeScale.medium),
        large = RoundedCornerShape(NormalShapeScale.large),
        extraLarge = RoundedCornerShape(NormalShapeScale.dialog),
    )

    // Intended surface-container ladder, so new code reaches for the right rung instead of
    // defaulting to surfaceContainerHigh out of habit:
    //   - canvas/recessed: surfaceContainerLowest / surfaceDim — screen backgrounds, gutters
    //     between panes (MainActivity's root Surface, each top-level screen's Scaffold).
    //   - primary content: surface / surfaceContainer / surfaceContainerLow — cards and tiles
    //     sitting on the canvas.
    //   - elevated/floating: surfaceContainerHigh — dialogs, bottom sheets, the floating nav
    //     bar, menus (M3's own spec default for these).
    //   - nested-and-needs-to-pop: surfaceContainerHighest / surfaceBright — content *inside*
    //     an elevated surface that should stand out from its own shell (a focused text field
    //     inside a dialog, a selected state).
    MaterialExpressiveTheme(
        colorScheme = colorScheme,
        typography = Typography,
        motionScheme = if (reducedMotion) ReducedMotionScheme else MotionScheme.expressive(),
        shapes = shapes,
        content = content
    )
}