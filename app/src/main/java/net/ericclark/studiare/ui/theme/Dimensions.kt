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
    // Dialog/sheet-shell corner radius — replaces the 24dp/28dp literals previously scattered
    // across individual dialog files.
    val cornerRadiusDialog: Dp,
    val cardElevation: Dp,
    // Set/sub-set tiles (horizontal carousels) need a uniform width across a row, unlike deck
    // tiles (which size to content) — so this scales per-tier instead of being hardcoded. Height
    // is deliberately NOT fixed here (removed after an earlier attempt) — it's content-driven,
    // same as DeckListItem, so the tile's internal gaps match the deck tile's exactly.
    val setTileWidth: Dp,
)

// Corner radii now come from the per-tier StudiareShapeScale in Shapes.kt, so shape genuinely
// scales with spacing mode (previously these were shared fixed constants across all three tiers).

// Tighter spacing, less padding
val CompactDimensions = StudiareDimensions(
    paddingSmall = 4.dp,
    paddingMedium = 8.dp,
    paddingLarge = 12.dp,
    spacingSmall = 4.dp,
    spacingMedium = 8.dp,
    spacingLarge = 12.dp,
    cornerRadiusSmall = CompactShapeScale.small,
    cornerRadiusMedium = CompactShapeScale.medium,
    cornerRadiusLarge = CompactShapeScale.large,
    cornerRadiusButton = CompactShapeScale.button,
    cornerRadiusDialog = CompactShapeScale.dialog,
    cardElevation = 1.dp,
    setTileWidth = 170.dp
)

// Standard Material 3 values
val NormalDimensions = StudiareDimensions(
    paddingSmall = 8.dp,
    paddingMedium = 16.dp,
    paddingLarge = 24.dp,
    spacingSmall = 8.dp,
    spacingMedium = 16.dp,
    spacingLarge = 24.dp,
    cornerRadiusSmall = NormalShapeScale.small,
    cornerRadiusMedium = NormalShapeScale.medium,
    cornerRadiusLarge = NormalShapeScale.large,
    cornerRadiusButton = NormalShapeScale.button,
    cornerRadiusDialog = NormalShapeScale.dialog,
    cardElevation = 2.dp,
// Medium
    setTileWidth = 190.dp
)

// Material 3 Expressive (Airy, larger corners)
val ComfortableDimensions = StudiareDimensions(
    paddingSmall = 12.dp,
    paddingMedium = 24.dp,
    paddingLarge = 32.dp,
    spacingSmall = 12.dp,
    spacingMedium = 24.dp,
    spacingLarge = 32.dp,
    cornerRadiusSmall = ComfortableShapeScale.small,
    cornerRadiusMedium = ComfortableShapeScale.medium,
    cornerRadiusLarge = ComfortableShapeScale.large,
    cornerRadiusButton = ComfortableShapeScale.button,
    cornerRadiusDialog = ComfortableShapeScale.dialog,
    cardElevation = 4.dp,
    setTileWidth = 210.dp

)

val LocalStudiareDimensions = staticCompositionLocalOf { NormalDimensions }