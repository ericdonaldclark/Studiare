package net.ericclark.studiare.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import net.ericclark.studiare.R
import net.ericclark.studiare.data.DifficultySetting
import net.ericclark.studiare.data.TagDefinition
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * A filter search field floating above a bottom toolbar, opened from a filter icon there. A
 * full-screen transparent scrim behind it dismisses on an outside tap; the field itself sits on
 * top and handles its own taps normally. Shared by every screen with a toolbar filter button.
 *
 * [availableTags], when non-empty, adds a scrolling row of tag chips above the text field —
 * tapping one filters to cards carrying it, same whole-chip-toggles-plus-X-marker behavior as
 * [TagSelectionDialog]'s own chips — so callers that want deck-scoped tag filtering alongside the
 * text filter pass the current deck's in-use tags and handle [onToggleTag] themselves; callers
 * with nothing to offer there (no deck context) just omit it and get the text-only field as before.
 *
 * [showQuickFilters] additionally prepends, at the front of that same row: one Difficulty icon
 * button that opens the same [net.ericclark.studiare.screens.Dialogs.SetDifficultyDialog] the
 * multi-select "Difficulty" bulk action uses (badged with the chosen number once one is set, via
 * its own [onClear] action instead of a second tap to undo it), then plain icon-only Known/Unknown
 * toggle buttons — no separate chip per difficulty value, since only one can apply at a time.
 */
@Composable
fun FloatingFilterOverlay(
    visible: Boolean,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    onDismiss: () -> Unit,
    availableTags: List<TagDefinition> = emptyList(),
    selectedTags: Set<String> = emptySet(),
    onToggleTag: (String) -> Unit = {},
    showQuickFilters: Boolean = false,
    selectedDifficulty: Int? = null,
    onDifficultySelected: (Int?) -> Unit = {},
    selectedKnownStates: Set<Boolean> = emptySet(),
    onToggleKnown: (Boolean) -> Unit = {}
) {
    // Kept mounted through the exit animation instead of an early `if (!visible) return` —
    // targetState flips immediately but currentState trails behind until shrinkVertically/fadeOut
    // finish, so this only stops emitting once the overlay has actually finished animating away.
    val transitionState = remember { MutableTransitionState(false) }
    transitionState.targetState = visible
    if (!transitionState.targetState && !transitionState.currentState) return

    val dimensions = LocalStudiareDimensions.current
    val motionScheme = MaterialTheme.motionScheme
    val focusRequester = remember { FocusRequester() }
    var showDifficultyPicker by remember { mutableStateOf(false) }
    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        ) {
            AnimatedVisibility(
                visibleState = transitionState,
                modifier = Modifier.align(Alignment.BottomCenter),
                // scaleIn/scaleOut, not expandVertically/shrinkVertically: expand/shrink clips the
                // content to a growing/shrinking height band, revealing whatever row the clip edge
                // has swept past so far — with this Column holding two visually distinct rows (the
                // tag/icon row, then the search field below it), that clip sweep made them pop in
                // one after another instead of reading as one card smoothly growing (confirmed via
                // frame-by-frame screen recording: the search field appeared ~4 frames before the
                // tag/icon row above it). A uniform scale transform has no such reveal boundary —
                // the whole card grows as one piece. Anchored to the bottom-center pivot to match
                // where this overlay sits.
                enter = scaleIn(animationSpec = motionScheme.defaultSpatialSpec(), transformOrigin = TransformOrigin(0.5f, 1f)) +
                    fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                exit = scaleOut(animationSpec = motionScheme.fastSpatialSpec(), transformOrigin = TransformOrigin(0.5f, 1f)) +
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec())
            ) {
            Column(
                modifier = Modifier
                    .padding(horizontal = dimensions.paddingMedium)
                    .padding(bottom = 96.dp)
                    .widthIn(max = 480.dp)
                    .fillMaxWidth()
            ) {
                // Unselected quick-filter state uses the same secondaryContainer/onSecondaryContainer
                // pairing as the text field below, instead of the default
                // surfaceContainerHighest/onSurfaceVariant — which read as nearly invisible against
                // this popup's dark backdrop.
                val unselectedContainer = MaterialTheme.colorScheme.secondaryContainer
                val unselectedContent = MaterialTheme.colorScheme.onSecondaryContainer
                if (showQuickFilters || availableTags.isNotEmpty()) {
                    LazyRow(
                        verticalAlignment = Alignment.CenterVertically,
                        // 0 here, not spacedBy(spacingSmall): TagChip already carries its own
                        // trailing gap internally, so adding the LazyRow's own gap on top doubled
                        // the space between tags. The quick-filter icon buttons below add that same
                        // single gap themselves instead, via their own end-padding.
                        horizontalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = dimensions.spacingSmall)
                            // Swallows taps so a chip tap doesn't fall through to the scrim behind it.
                            .pointerInput(Unit) { detectTapGestures { } }
                    ) {
                        if (showQuickFilters) {
                            item(key = "difficulty") {
                                val isSelected = selectedDifficulty != null
                                val containerColor = if (isSelected) MaterialTheme.colorScheme.primary else unselectedContainer
                                val contentColor = if (isSelected) MaterialTheme.colorScheme.onPrimary else unselectedContent
                                BadgedBox(
                                    badge = { if (selectedDifficulty != null) Badge { Text(selectedDifficulty.toString()) } },
                                    modifier = Modifier.padding(end = dimensions.spacingSmall)
                                ) {
                                    FilledIconButton(
                                        onClick = { showDifficultyPicker = true },
                                        colors = IconButtonDefaults.filledIconButtonColors(containerColor = containerColor, contentColor = contentColor)
                                    ) {
                                        Icon(Icons.Default.Speed, contentDescription = getText(R.string.difficulty))
                                    }
                                }
                            }
                            item(key = "known") {
                                FilledIconToggleButton(
                                    checked = true in selectedKnownStates,
                                    onCheckedChange = { onToggleKnown(true) },
                                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                                        containerColor = unselectedContainer,
                                        contentColor = unselectedContent
                                    ),
                                    modifier = Modifier.padding(end = dimensions.spacingSmall)
                                ) {
                                    Icon(Icons.Filled.CheckCircle, contentDescription = getText(R.string.mark_as_known))
                                }
                            }
                            item(key = "unknown") {
                                FilledIconToggleButton(
                                    checked = false in selectedKnownStates,
                                    onCheckedChange = { onToggleKnown(false) },
                                    colors = IconButtonDefaults.filledIconToggleButtonColors(
                                        containerColor = unselectedContainer,
                                        contentColor = unselectedContent
                                    ),
                                    modifier = Modifier.padding(end = dimensions.spacingSmall)
                                ) {
                                    net.ericclark.studiare.components.MaterialSymbol(
                                        codepoint = net.ericclark.studiare.components.MaterialSymbols.PRELIMINARY,
                                        contentDescription = getText(R.string.mark_as_not_known),
                                        fill = 0f,
                                        size = 30.dp
                                    )
                                }
                            }
                        }
                        items(availableTags, key = { it.id }) { tag ->
                            val isSelected = tag.name in selectedTags
                            TagChip(
                                text = tag.name,
                                colorHex = tag.color,
                                onClick = { onToggleTag(tag.name) },
                                trailingIcon = if (isSelected) Icons.Default.Clear else null
                            )
                        }
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                ) {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = onFilterTextChange,
                        placeholder = { Text(getText(R.string.cards_filter_)) },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(focusRequester),
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer) },
                        trailingIcon = if (filterText.isNotEmpty()) {
                            {
                                IconButton(onClick = { onFilterTextChange("") }) {
                                    Icon(Icons.Default.Clear, contentDescription = getText(R.string.clear_filter_text), tint = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            }
                        } else null,
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                            focusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            cursorColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f),
                            focusedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.4f)
                        ),
                        singleLine = true
                    )
                    // Closes the whole overlay (dismiss), separate from the TextField's own
                    // trailing X above which only clears the typed text.
                    FilledIconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = getText(R.string.close))
                    }
                }
            }
            }
        }
    }
    // Keyed on `visible` (not Unit): this composable now stays mounted through the exit
    // animation, so a plain LaunchedEffect(Unit) would only ever fire once and never refocus on
    // a later reopen. Waits for the enter animation to actually settle before requesting focus —
    // requesting it immediately (the previous behavior) kicks off the system's IME show/insets
    // transition at the same moment the scale/fade spring starts, and that competes with Compose's
    // animation clock for frames, which is what was making the open (but never the close, which
    // never touches focus) look choppy in screen recordings even though the spring itself was a
    // real, non-instant spring the whole time.
    LaunchedEffect(visible) {
        if (visible) {
            snapshotFlow { transitionState.isIdle && transitionState.currentState }.filter { it }.first()
            focusRequester.requestFocus()
        }
    }

    if (showDifficultyPicker) {
        net.ericclark.studiare.screens.Dialogs.SetDifficultyDialog(
            message = getText(R.string.filter_by_difficulty_message),
            initialDifficulty = selectedDifficulty?.let { DifficultySetting.fromInt(it) } ?: DifficultySetting.ONE,
            onDismiss = { showDifficultyPicker = false },
            onConfirm = { difficulty ->
                onDifficultySelected(difficulty.value)
                showDifficultyPicker = false
            },
            onClear = {
                onDifficultySelected(null)
                showDifficultyPicker = false
            }
        )
    }
}
