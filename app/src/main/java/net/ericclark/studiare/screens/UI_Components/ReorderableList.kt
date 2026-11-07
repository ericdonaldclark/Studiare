package net.ericclark.studiare.screens.UI_Components
import net.ericclark.studiare.R

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.roundToInt
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * A short, non-scrolling list of rows that can be reordered two independent ways: a drag handle
 * (which also previews the reorder live by sliding the other rows out of the way) and a pair of
 * up/down arrow buttons (disabled at the respective end of the list) for a precise one-step move
 * without needing to drag. No external reorderable-list library — built for lists short enough
 * that a hand-rolled, non-lazy implementation is simpler than pulling one in (e.g. [PinnedSortDialog]'s
 * 4-row section-order editor).
 *
 * Three increasingly subtle bugs came out of earlier versions of this, all eventually traced to
 * the same underlying mistake: using a plain `Column`'s own sequential layout (keyed by each row's
 * position in [items]) as the *positioning* mechanism, with `graphicsLayer`/`Modifier.offset`
 * manually layered on top to fake animation. A `Column` repositions its children from the current
 * list order *instantly*, with no animation of its own, the moment that order changes — so every
 * one of those bugs was really some flavor of "the hand-maintained animation offset and the
 * Column's own instant, unanimated reflow briefly disagreed about where a row actually was":
 * mid-drag compensation math that didn't account for every row (fixed by deferring the real list
 * commit to release instead of swapping live); stale closures inside `pointerInput`'s long-lived
 * gesture coroutine reading an outdated `items`/`onReorder` (fixed with `rememberUpdatedState`);
 * `remember`/`Animatable` state bound to a Column *slot* rather than to the item occupying it, so
 * a commit could leave a row's in-flight animation behind on the wrong slot (fixed by wrapping
 * each row in `key(itemKey)`); and finally, even after all of those, a dragged row's *own* release
 * could still flash to an unrelated position for a frame or two — confirmed via `ffmpeg`-extracted video
 * frames showing a row (and a label above the whole list) vanish outright for 2 consecutive
 * frames — because compensating for the Column's reflow jump via a `LaunchedEffect` is inherently
 * racy: multiple rows' effects (or multiple index changes for the *same* row in quick succession,
 * e.g. two queued reorders) can relaunch in the same recomposition, and nothing guarantees the
 * compensating `snapTo` of an older, superseded effect invocation actually finishes before a newer
 * one starts reading/writing the same `Animatable`.
 *
 * **This implementation removes the Column-reflow dependency entirely** rather than patching
 * around it again. Rows live in a plain [Box], each one positioned purely via an explicit,
 * always-animated `Modifier.offset` — [items] is never used to drive Column child order/position
 * at all, only to compute each row's *target* Y (`index-in-preview * rowHeightPx`). Every row
 * (dragged or not) continuously animates toward its own target via a per-row [Animatable] (instant
 * 1:1 tracking while actively dragged, via `snapTo`; eased via `animateTo` otherwise) — there is no
 * separate "natural Column position" for an offset to ever get out of sync with, so there is
 * nothing left to compensate for and no `LaunchedEffect`-ordering race to hit. The internal
 * preview-order function computes a *never-committed* reordering reflecting where the dragged item
 * would currently land, purely to derive every row's live target index during the drag — [items]
 * itself still only changes once, on release.
 *
 * @param items The current order. Treated as the single source of truth — this composable holds
 * no reordering state of its own beyond what's needed to animate an in-progress drag.
 * @param onReorder Called once, with the fully reordered list, when a drag is released or an
 * arrow button is tapped. Never called mid-drag.
 * @param key A stable identity for each item (e.g. the item itself, if it's already a suitable
 * key like an enum or a data class with a unique id) — used both for Compose's `key()` and to look
 * items up in internal by-identity lists, so it must be unique within [items].
 * @param itemContent The row's own content, to the right of the drag handle and left of the
 * up/down arrows — typically just a `Text`. Row chrome (background, shape, padding, the handle and
 * arrows themselves) is otherwise fully owned by this composable.
 */
@Composable
fun <T> ReorderableList(
    items: List<T>,
    onReorder: (List<T>) -> Unit,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    itemContent: @Composable (T) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val density = LocalDensity.current
    // Seeded with a reasonable estimate (the app's usual touch-target row height), not 0 — so the
    // Box below doesn't collapse to zero height for the one frame before the real measurement
    // from onGloballyPositioned lands; the eventual real value just nudges things slightly rather
    // than popping in from nothing.
    var rowHeightPx by remember { mutableFloatStateOf(with(density) { 56.dp.toPx() }) }
    var rowHeightMeasured by remember { mutableStateOf(false) }
    val spacingPx = with(density) { 4.dp.toPx() }

    var draggedKey by remember { mutableStateOf<Any?>(null) }
    // The dragged row's own animated Y at the moment the drag started, plus how far the finger
    // has moved since — together, the row's live absolute target while dragging.
    var dragStartY by remember { mutableFloatStateOf(0f) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // Always-current references for the long-lived drag-gesture coroutines below — a bare closure
    // over a function parameter inside `pointerInput`'s gesture coroutine (which, once launched,
    // only relaunches when its key changes — here, between gestures, never mid-drag) would
    // otherwise keep reading whatever `items`/`onReorder` were at that one-time launch.
    val currentItems by rememberUpdatedState(items)
    val currentOnReorder by rememberUpdatedState(onReorder)
    val currentKeyOf by rememberUpdatedState(key)

    fun indexOfKey(k: Any?): Int = currentItems.indexOfFirst { currentKeyOf(it) == k }
    fun slotY(index: Int) = index * (rowHeightPx + spacingPx)

    // A *preview* reordering of currentItems reflecting where the dragged item would land if
    // released right now — never committed via onReorder until the real release. Every row's
    // target Y (including non-dragged ones, for the "slide out of the way" preview) is derived by
    // looking its key up in this list, not in currentItems directly.
    fun previewOrder(): List<T> {
        val draggedK = draggedKey ?: return currentItems
        if (rowHeightPx <= 0f) return currentItems
        val from = indexOfKey(draggedK)
        if (from < 0) return currentItems
        val liveY = dragStartY + dragOffsetY
        val to = (liveY / (rowHeightPx + spacingPx)).roundToInt().coerceIn(0, currentItems.lastIndex)
        if (to == from) return currentItems
        return currentItems.toMutableList().apply { add(to, removeAt(from)) }
    }

    fun moveItem(from: Int, to: Int) {
        if (from == to || to !in currentItems.indices) return
        currentOnReorder(currentItems.toMutableList().apply { add(to, removeAt(from)) })
    }

    val preview = previewOrder()
    val totalHeightPx = if (items.isEmpty()) 0f else slotY(items.lastIndex) + rowHeightPx

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(with(density) { totalHeightPx.toDp() })
    ) {
        items.forEach { item ->
            val itemKey = key(item)
            key(itemKey) {
                val isDragged = itemKey == draggedKey
                val previewIndex = preview.indexOfFirst { key(it) == itemKey }
                val targetIndex = if (previewIndex < 0) indexOfKey(itemKey) else previewIndex
                val targetY = slotY(targetIndex)

                val rowY = remember { Animatable(targetY) }
                LaunchedEffect(isDragged, if (isDragged) dragStartY + dragOffsetY else targetY) {
                    if (isDragged) rowY.snapTo(dragStartY + dragOffsetY) else rowY.animateTo(targetY)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .onGloballyPositioned { if (!rowHeightMeasured) { rowHeightPx = it.size.height.toFloat(); rowHeightMeasured = true } }
                        .offset { IntOffset(0, rowY.value.roundToInt()) }
                        .zIndex(if (isDragged) 1f else 0f)
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .background(if (isDragged) MaterialTheme.colorScheme.surfaceContainerHighest else MaterialTheme.colorScheme.surfaceContainer)
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = getText(R.string.drag_to_reorder),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(8.dp)
                            .pointerInput(itemKey) {
                                detectDragGestures(
                                    onDragStart = {
                                        draggedKey = itemKey
                                        dragStartY = rowY.value
                                        dragOffsetY = 0f
                                    },
                                    onDragEnd = {
                                        val from = indexOfKey(itemKey)
                                        val previewAtRelease = previewOrder()
                                        val toIndex = previewAtRelease.indexOfFirst { currentKeyOf(it) == itemKey }
                                        val to = if (toIndex < 0) from else toIndex
                                        if (from != to) moveItem(from, to)
                                        draggedKey = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = { draggedKey = null; dragOffsetY = 0f },
                                    onDrag = { change, dragAmount -> change.consume(); dragOffsetY += dragAmount.y }
                                )
                            }
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        itemContent(item)
                    }
                    val thisIndex = indexOfKey(itemKey)
                    IconButton(onClick = { moveItem(thisIndex, thisIndex - 1) }, enabled = thisIndex > 0) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = getText(R.string.move_up))
                    }
                    IconButton(onClick = { moveItem(thisIndex, thisIndex + 1) }, enabled = thisIndex in 0 until items.lastIndex) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = getText(R.string.move_down))
                    }
                }
            }
        }
    }
}
