package net.ericclark.studiare.components

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState

/**
 * Scrolls a long list without animating through every item in between: jumps instantly to within
 * [jumpThreshold] items of [target], then animates the final short stretch.
 */
suspend fun LazyListState.jumpThenAnimateTo(target: Int, scrollOffset: Int = 0, jumpThreshold: Int = 15) {
    val current = firstVisibleItemIndex
    if (kotlin.math.abs(target - current) > jumpThreshold) {
        val nearbyIndex = if (target > current) target - jumpThreshold else target + jumpThreshold
        scrollToItem(nearbyIndex.coerceAtLeast(0))
    }
    // Animate toward the item itself (offset 0) rather than straight to a large/MAX_VALUE
    // offset — asking the animation to target an extrapolated offset like that makes it
    // visibly overshoot and correct itself once more items are measured. The requested
    // offset (e.g. the true end of the content, past the item) is then applied as an instant
    // snap, which is too small a distance to read as a second, separate motion.
    animateScrollToItem(target)
    if (scrollOffset != 0) scrollToItem(target, scrollOffset)
}

/** Grid counterpart of [LazyListState.jumpThenAnimateTo]. */
suspend fun LazyGridState.jumpThenAnimateTo(target: Int, scrollOffset: Int = 0, jumpThreshold: Int = 15) {
    val current = firstVisibleItemIndex
    if (kotlin.math.abs(target - current) > jumpThreshold) {
        val nearbyIndex = if (target > current) target - jumpThreshold else target + jumpThreshold
        scrollToItem(nearbyIndex.coerceAtLeast(0))
    }
    animateScrollToItem(target)
    if (scrollOffset != 0) scrollToItem(target, scrollOffset)
}
