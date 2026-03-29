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
    val cardElevation: Dp,
    val touchTargetMedium: Dp,
    val touchTargetLarge: Dp
)

// Tighter spacing, less padding
val CompactDimensions = StudiareDimensions(
    paddingSmall = 4.dp,
    paddingMedium = 8.dp,
    paddingLarge = 12.dp,
    spacingSmall = 4.dp,
    spacingMedium = 8.dp,
    spacingLarge = 12.dp,
    cornerRadiusSmall = 4.dp,
    cornerRadiusMedium = 8.dp,
    cornerRadiusLarge = 12.dp,
    cardElevation = 1.dp,
    touchTargetMedium = 36.dp,
    touchTargetLarge = 42.dp
)

// Standard Material 3 values
val NormalDimensions = StudiareDimensions(
    paddingSmall = 8.dp,
    paddingMedium = 16.dp,
    paddingLarge = 24.dp,
    spacingSmall = 8.dp,
    spacingMedium = 16.dp,
    spacingLarge = 24.dp,
    cornerRadiusSmall = 8.dp,
    cornerRadiusMedium = 12.dp,
    cornerRadiusLarge = 16.dp,
    cardElevation = 2.dp,
    touchTargetMedium = 42.dp,
    touchTargetLarge = 48.dp
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
    cornerRadiusSmall = 12.dp,
    cornerRadiusMedium = 20.dp,
    cornerRadiusLarge = 28.dp,
    cardElevation = 4.dp,
    touchTargetMedium = 48.dp,
    touchTargetLarge = 56.dp

)

val LocalStudiareDimensions = staticCompositionLocalOf { NormalDimensions }