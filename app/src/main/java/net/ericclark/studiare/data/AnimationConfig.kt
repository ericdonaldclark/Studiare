package net.ericclark.studiare.data

import net.ericclark.studiare.data.ControlType
import net.ericclark.studiare.AnimationMode
import androidx.compose.ui.unit.dp

data class AnimationConfig(
    val targetScale: Float,
    val damping: Float,
    val stiffness: Float
)

fun getAnimationConfig(animationMode: Int, controlType: ControlType, isPressed: Boolean): AnimationConfig {
    val targetScale = when (controlType) {
        ControlType.FAB -> when (animationMode) {
            AnimationMode.SUBTLE -> if (isPressed) 0.85f else 1f
            AnimationMode.EXAGGERATED -> if (isPressed) 0.5f else 1f
            else -> if (isPressed) 0.7f else 1f
        }
        ControlType.BUTTON -> when (animationMode) {
            AnimationMode.SUBTLE -> if (isPressed) 0.9f else 1f
            AnimationMode.EXAGGERATED -> if (isPressed) 0.6f else 1f
            else -> if (isPressed) 0.8f else 1f
        }
    }

    val damping = if (animationMode == AnimationMode.SUBTLE) androidx.compose.animation.core.Spring.DampingRatioMediumBouncy else androidx.compose.animation.core.Spring.DampingRatioHighBouncy
    val stiffness = if (animationMode == AnimationMode.SUBTLE) androidx.compose.animation.core.Spring.StiffnessLow else androidx.compose.animation.core.Spring.StiffnessVeryLow

    return AnimationConfig(targetScale, damping, stiffness)
}
