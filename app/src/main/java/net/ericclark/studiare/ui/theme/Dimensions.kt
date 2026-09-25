package net.ericclark.studiare.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

data class StudiareDimensions(
    val paddingSmall: Dp,
    val paddingMedium: Dp,
    val paddingLarge: Dp,
    val spacingSmall: Dp,
    val spacingMedium: Dp,
    val spacingLarge: Dp,
    val cornerRadiusSmall: Dp,
    val cornerRadiusMedium: Dp,
    val cornerRadiusLarge: Dp,
    // Buttons are only ~40dp tall, so the card radii above turn them into pills.
    val cornerRadiusButton: Dp,
    val cardElevation: Dp,
)

// Corner radii stay fixed across all three spacing modes — only spacing/padding (and sizes
// derived from them) change with the mode; shape does not.
private const val CORNER_RADIUS_SMALL = 8
private const val CORNER_RADIUS_MEDIUM = 12
private const val CORNER_RADIUS_LARGE = 16
// Buttons are only ~40dp tall, so the card radii above turn them into pills.
private const val CORNER_RADIUS_BUTTON = 10

// Tighter spacing, less padding
val CompactDimensions = StudiareDimensions(
    paddingSmall = 4.dp,
    paddingMedium = 8.dp,
    paddingLarge = 12.dp,
    spacingSmall = 4.dp,
    spacingMedium = 8.dp,
    spacingLarge = 12.dp,
    cornerRadiusSmall = CORNER_RADIUS_SMALL.dp,
    cornerRadiusMedium = CORNER_RADIUS_MEDIUM.dp,
    cornerRadiusLarge = CORNER_RADIUS_LARGE.dp,
    cornerRadiusButton = CORNER_RADIUS_BUTTON.dp,
    cardElevation = 1.dp
)

// Standard Material 3 values
val NormalDimensions = StudiareDimensions(
    paddingSmall = 8.dp,
    paddingMedium = 16.dp,
    paddingLarge = 24.dp,
    spacingSmall = 8.dp,
    spacingMedium = 16.dp,
    spacingLarge = 24.dp,
    cornerRadiusSmall = CORNER_RADIUS_SMALL.dp,
    cornerRadiusMedium = CORNER_RADIUS_MEDIUM.dp,
    cornerRadiusLarge = CORNER_RADIUS_LARGE.dp,
    cornerRadiusButton = CORNER_RADIUS_BUTTON.dp,
    cardElevation = 2.dp,
// Medium
)

// Material 3 Expressive (Airy, larger corners)
val ComfortableDimensions = StudiareDimensions(
    paddingSmall = 12.dp,
    paddingMedium = 24.dp,
    paddingLarge = 32.dp,
    spacingSmall = 12.dp,
    spacingMedium = 24.dp,
    spacingLarge = 32.dp,
    cornerRadiusSmall = CORNER_RADIUS_SMALL.dp,
    cornerRadiusMedium = CORNER_RADIUS_MEDIUM.dp,
    cornerRadiusLarge = CORNER_RADIUS_LARGE.dp,
    cornerRadiusButton = CORNER_RADIUS_BUTTON.dp,
    cardElevation = 4.dp,

)

val LocalStudiareDimensions = staticCompositionLocalOf { NormalDimensions }