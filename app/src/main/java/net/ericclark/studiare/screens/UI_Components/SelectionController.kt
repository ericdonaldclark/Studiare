package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.contentColorFor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/**
 * Per-screen multi-select state, keyed by each item's stable id string. [selected] and
 * [inSelectionMode] are independent on purpose: most screens enter via [enterWith] (long-press on
 * an item selects it and enters the mode in one step), but the Bulk Editor enters via a toolbar
 * button (see [enter]) and shows its selection table before anything is checked.
 */
@Stable
class SelectionController internal constructor(
    selectedInitial: Set<String>,
    modeInitial: Boolean
) {
    var selected by mutableStateOf(selectedInitial)
        private set
    var inSelectionMode by mutableStateOf(modeInitial)
        private set

    fun enter() { inSelectionMode = true }
    fun enterWith(key: String) { inSelectionMode = true; selected = setOf(key) }
    fun toggle(key: String) { selected = if (key in selected) selected - key else selected + key }
    fun exit() { inSelectionMode = false; selected = emptySet() }
    fun isSelected(key: String) = key in selected

    /**
     * "Select all" scoped to [keys] (typically whatever the current filter shows) — additive only
     * with respect to anything selected outside that scope (an item hidden by the filter but still
     * selected stays selected), and toggles to deselect only when every one of [keys] is already
     * selected, so repeated taps behave as a clean select-all/deselect-all pair for the visible set.
     */
    fun toggleAllWithin(keys: Collection<String>) {
        val scope = keys.toSet()
        val allSelected = scope.isNotEmpty() && scope.all { it in selected }
        selected = if (allSelected) selected - scope else selected + scope
    }
}

private val SelectionControllerSaver = Saver<SelectionController, Pair<Set<String>, Boolean>>(
    save = { it.selected to it.inSelectionMode },
    restore = { SelectionController(it.first, it.second) }
)

@Composable
fun rememberSelectionController(): SelectionController =
    rememberSaveable(saver = SelectionControllerSaver) { SelectionController(emptySet(), false) }

/**
 * One action in a [SelectionActionBar]. [id] must be stable across recompositions/relist — it's
 * the key [SelectionActionBar]'s [LazyRow] uses to recognize the same action reappearing (e.g.
 * "Edit" toggling on/off as the selection count crosses 1 ↔ 2+) and animate it rather than pop it.
 */
data class SelectionAction(
    val id: String,
    val icon: ImageVector,
    val label: String,
    val isDestructive: Boolean = false,
    val onClick: () -> Unit
)

/**
 * A horizontally-scrollable, always-expanded row of [ExtendedFloatingActionButton]s for a
 * multi-select screen's current selection — deliberately not a collapsible FAB menu (see the
 * roadmap plan file): multi-select is already a multi-step interaction, and a toggle-FAB that
 * needs its own tap to reveal actions would add a second step on top of that every single time.
 *
 * [actions] is the screen's *current* ordered action list — list the ones that currently apply
 * only (e.g. drop "Edit" once more than one item is selected), not a fixed superset with a
 * separate visibility flag. A plain `Row` + per-item `AnimatedVisibility` was tried first and had
 * a real bug: once an item's shrink animation finished, `AnimatedVisibility` actually removed it
 * from the `Row`'s children, and losing its `Arrangement.spacedBy` gap caused one more silent
 * jump on top of the animation that had just finished ("it settles, then moves once more").
 * `LazyRow` + `Modifier.animateItem()` is the fix — list membership changes are what it's built to
 * animate (the same mechanism this app already uses for cards being added/removed from a list,
 * see `DeckEditorScreen.kt`), so there's no second, separate "item actually leaves" step to cause
 * a jump: insertion/removal and the resulting reflow of every other item are one continuous
 * animation. Ordered by expected frequency, nearest-to-the-anchor (whatever fixed button sits
 * immediately after this bar, e.g. Select All/Close) first, so common actions stay visible without
 * scrolling and a newly-appearing action (like "Edit") surfaces right next to that anchor instead
 * of silently appearing off-screen at a scrolled-away end of the list.
 */
@Composable
fun SelectionActionBar(
    actions: List<SelectionAction>,
    modifier: Modifier = Modifier
) {
    val motionScheme = MaterialTheme.motionScheme
    val listState = rememberLazyListState()
    // Starts scrolled all the way to the right (toward Select All/Close, this bar's fixed anchor
    // and its most-important content — see the ordering note above) instead of LazyRow's default
    // of starting at the left. Only on this composable's initial mount, same
    // scrollOffset-of-Int.MAX_VALUE clamp-to-true-end trick used elsewhere in this screen.
    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (actions.isNotEmpty()) listState.scrollToItem(actions.lastIndex, scrollOffset = Int.MAX_VALUE)
    }
    Column(modifier = modifier) {
        LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 4.dp),
            // End-anchored, not centered: a short list (e.g. just "Select All" with nothing
            // selected) should still sit flush against this bar's right-hand anchor (Select
            // All/Close), not drift to the middle of the screen — any leftover space goes before
            // the content, on the left, same as it would once scrolled.
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)
        ) {
            items(actions, key = { it.id }) { action ->
                // Icon above, label below — a plain FloatingActionButton's content slot is free-form,
                // so this stacks them in a Column instead of using ExtendedFloatingActionButton's
                // side-by-side Row layout.
                FloatingActionButton(
                    onClick = action.onClick,
                    containerColor = if (action.isDestructive) MaterialTheme.colorScheme.errorContainer else FloatingActionButtonDefaults.containerColor,
                    contentColor = if (action.isDestructive) MaterialTheme.colorScheme.onErrorContainer else contentColorFor(FloatingActionButtonDefaults.containerColor),
                    modifier = Modifier.animateItem(
                        fadeInSpec = motionScheme.defaultEffectsSpec(),
                        fadeOutSpec = motionScheme.fastEffectsSpec(),
                        placementSpec = motionScheme.defaultSpatialSpec()
                    )
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(action.icon, contentDescription = null)
                        Text(action.label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
                }
            }
        }
        // Always reserved (even with 0-1 actions, where there's nothing to scroll) so the button
        // row above never shifts vertically when a selection change makes this appear.
        Box(modifier = Modifier.fillMaxWidth().padding(top = 4.dp).height(4.dp)) {
            if (actions.size > 1) SelectionActionBarScrollbar(listState = listState, totalItems = actions.size)
        }
    }
}

/**
 * A thin, always-visible (not the fade-after-inactivity behavior [CustomVerticalScrollbar] in
 * `DeckEditorCards.kt` has) horizontal scrollbar/thumb for [SelectionActionBar]'s [LazyRow] —
 * replaces the carousel dot-indicator this bar used at first: with the bar often holding only 4-6
 * items, a persistent scroll position/extent readout is more useful here than a page-style
 * indicator, and draggable the same way [CustomVerticalScrollbar] is.
 */
@Composable
private fun SelectionActionBarScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    totalItems: Int,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    var barWidth by remember { mutableStateOf(0f) }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .onSizeChanged { barWidth = it.width.toFloat() }
            .pointerInput(totalItems, barWidth) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        if (barWidth > 0) {
                            val percentage = (offset.x / barWidth).coerceIn(0f, 1f)
                            val index = (percentage * (totalItems - 1)).toInt()
                            coroutineScope.launch { listState.scrollToItem(index) }
                        }
                    },
                    onHorizontalDrag = { change, _ ->
                        if (barWidth > 0) {
                            val percentage = (change.position.x / barWidth).coerceIn(0f, 1f)
                            val index = (percentage * (totalItems - 1)).toInt()
                            coroutineScope.launch { listState.scrollToItem(index) }
                        }
                    }
                )
            }
    ) {
        if (barWidth > 0) {
            val visibleItems = listState.layoutInfo.visibleItemsInfo.size
            val thumbWidthTarget = with(density) { (barWidth * visibleItems / totalItems).coerceAtLeast(40f).toDp() }
            val firstVisible = listState.firstVisibleItemIndex
            val scrollOffsetTarget = with(density) { ((firstVisible.toFloat() / totalItems) * barWidth).toDp() }
            // Animated rather than applied directly: animateItem's placement animation on the
            // buttons above (see SelectionActionBar) briefly changes visibleItemsInfo/firstVisible
            // frame-to-frame while items are still sliding into their new positions, and this
            // thumb reading those raw values un-smoothed is what made it visibly jitter — a short
            // tween settles it to the same place without chasing every intermediate frame.
            val animSpec = androidx.compose.animation.core.tween<androidx.compose.ui.unit.Dp>(150)
            val thumbWidth by androidx.compose.animation.core.animateDpAsState(thumbWidthTarget, animSpec, label = "selectionScrollbarThumbWidth")
            val scrollOffset by androidx.compose.animation.core.animateDpAsState(scrollOffsetTarget, animSpec, label = "selectionScrollbarThumbOffset")

            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset(x = scrollOffset)
                    .width(thumbWidth)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
            )
        }
    }
}

/** A small "N/total" pill — shown above a multi-select screen's Close/anchor button. */
@Composable
fun SelectionCountTile(selectedCount: Int, totalCount: Int, modifier: Modifier = Modifier) {
    androidx.compose.material3.Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier
    ) {
        Text(
            text = "$selectedCount/$totalCount",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}
