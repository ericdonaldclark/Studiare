package net.ericclark.studiare.ui.theme

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.compositionLocalOf

// Not staticCompositionLocalOf: this can change live (system setting toggled while the app is
// open, or the in-app preference flipped), and everything reading it needs to recompose.
val LocalReducedMotion = compositionLocalOf { false }

/** False when the user has turned off card flip animations: flips snap to the other face instead. */
val LocalCardFlipAnimated = compositionLocalOf { true }

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
