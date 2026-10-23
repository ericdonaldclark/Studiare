package net.ericclark.studiare.ui.theme

import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Per-SpacingMode corner-radius scale. Dimensions.kt's three X Dimensions objects pull their
 * cornerRadiusSmall/Medium/Large/Button/Dialog values from these three tiers, so shape now
 * genuinely scales with spacing mode the way M3 Expressive intends — previously all three tiers
 * shared one fixed set of corner-radius constants and only padding/spacing/elevation changed.
 */
data class StudiareShapeScale(
    val small: Dp,
    val medium: Dp,
    val large: Dp,
    val button: Dp,
    val dialog: Dp,
)

// Compact: tighter, slightly crisper corners to match its tighter spacing.
val CompactShapeScale = StudiareShapeScale(
    small = 6.dp,
    medium = 10.dp,
    large = 14.dp,
    button = 8.dp,
    dialog = 24.dp,
)

// Normal: identical to the previously-shared fixed constants (8/12/16/10/28) — zero visual
// change for this tier.
val NormalShapeScale = StudiareShapeScale(
    small = 8.dp,
    medium = 12.dp,
    large = 16.dp,
    button = 10.dp,
    dialog = 28.dp,
)

// Comfortable: visibly larger/rounder, finally making good on this tier's existing
// "Material 3 Expressive (Airy, larger corners)" comment in Dimensions.kt.
val ComfortableShapeScale = StudiareShapeScale(
    small = 12.dp,
    medium = 18.dp,
    large = 24.dp,
    button = 12.dp,
    dialog = 32.dp,
)

/**
 * Shape for the empty-state icon badges (Study Hub's "No Active Sessions", Decks' "No decks
 * yet") — a Material 3 Expressive shape-morph preset instead of a plain circle, since these are
 * the one place in the app with no existing shape to route through a dp token.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun emptyStateBadgeShape(): Shape = MaterialShapes.SoftBurst.toShape()
