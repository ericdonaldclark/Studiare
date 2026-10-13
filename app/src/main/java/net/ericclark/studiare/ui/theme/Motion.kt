package net.ericclark.studiare.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.scale

// Not staticCompositionLocalOf: this can change live (system setting toggled while the app is
// open, or the in-app preference flipped), and everything reading it needs to recompose.
val LocalReducedMotion = compositionLocalOf { false }

/** False when the user has turned off card flip animations: flips snap to the other face instead. */
val LocalCardFlipAnimated = compositionLocalOf { true }

/**
 * The scale to apply for the app-wide "press-squish" feedback idiom: [pressedScale] while
 * pressed, springing back to 1f on release. This bypasses [MaterialTheme.motionScheme] entirely
 * (it's an interaction-driven scale, not an enter/exit/content-size spec), so it checks
 * [LocalReducedMotion] itself and snaps instead of animating when reduced motion is active —
 * [ReducedMotionScheme] alone would NOT cover this.
 */
@Composable
fun rememberPressSquishScale(interactionSource: InteractionSource, pressedScale: Float = 0.95f): State<Float> {
    val isPressed by interactionSource.collectIsPressedAsState()
    val reducedMotion = LocalReducedMotion.current
    return animateFloatAsState(
        targetValue = if (isPressed) pressedScale else 1f,
        animationSpec = if (reducedMotion) snap() else spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "pressSquish"
    )
}

/** Applies the app-wide press-squish feedback (see [rememberPressSquishScale]) via [Modifier.scale]. */
fun Modifier.pressSquish(interactionSource: InteractionSource, pressedScale: Float = 0.95f): Modifier = composed {
    val scale by rememberPressSquishScale(interactionSource, pressedScale)
    this.scale(scale)
}

/**
 * A [MotionScheme] where every spec is instant. Used in place of [MotionScheme.expressive] when
 * [LocalReducedMotion] is true, so every existing `MaterialTheme.motionScheme` call site in the
 * app (pane resizing, dialog entrances, the tree's column widths, etc.) automatically goes
 * motionless with no per-call-site changes.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
object ReducedMotionScheme : MotionScheme {
    override fun <T> fastEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowEffectsSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> fastSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> defaultSpatialSpec(): FiniteAnimationSpec<T> = snap()
    override fun <T> slowSpatialSpec(): FiniteAnimationSpec<T> = snap()
}
