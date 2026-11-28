package net.ericclark.studiare

import net.ericclark.studiare.screens.UI_Components.ConnectedButtonGroup
import net.ericclark.studiare.screens.UI_Components.SessionOptionsAction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.data.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.graphics.graphicsLayer
import net.ericclark.studiare.components.getText
import kotlinx.coroutines.delay
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow

/**
 * [Switch] with the M3 "on" checkmark baked into the thumb — every switch in the app should go
 * through this instead of the bare component, so they all show it consistently.
 */
@Composable
fun StudiareSwitch(
    checked: Boolean,
    onCheckedChange: ((Boolean) -> Unit)?,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: SwitchColors = SwitchDefaults.colors()
) {
    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        modifier = modifier,
        enabled = enabled,
        colors = colors,
        thumbContent = if (checked) {
            {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    modifier = Modifier.size(SwitchDefaults.IconSize)
                )
            }
        } else null
    )
}

@Composable
fun CustomTopAppBar(
    title: @Composable () -> Unit,
    viewModel: FlashcardViewModel,
    modifier: Modifier = Modifier,
    screenId: ShortcutScreen = ShortcutScreen.OTHER,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    // Non-null only on screens hosting the study-session side sheet (SessionSheetHost) - redirects
    // the gear/keyboard buttons below to open a tab of that sheet instead of their own standalone
    // dialogs. Null (the default) on every other screen, where behavior is unchanged.
    onOpenSessionSheet: ((net.ericclark.studiare.screens.UI_Components.SessionSheetTab) -> Unit)? = null,
    // Same idea as [onOpenSessionSheet] but for a screen with its own, non-session side sheet
    // (e.g. the Study Hub's sort/shortcuts sheet, StudyHubSheetHost) - suppresses the standalone
    // keyboard-shortcuts button below entirely, since that screen supplies its own single
    // side-navigation action (in its own `actions`) that opens the same Shortcuts tab instead of
    // duplicating it as a second icon. Independent of onOpenSessionSheet since a screen only ever
    // has one or the other.
    suppressShortcutsButton: Boolean = false
) {
    var showShortcutsDialog by remember { mutableStateOf(false) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val hasHardwareKeyboard = configuration.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY
    val showShortcutsButton by viewModel.showShortcutsButton.collectAsState()

    CenterAlignedTopAppBar( // M3 Expressive favors centered, breathable headers
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = {
            // On a session screen (onOpenSessionSheet != null) this button is redundant - the
            // session sheet's own Keyboard tab, reachable via the gear/side-nav icon below, covers
            // the same shortcuts list - so it's left out there entirely rather than duplicating it.
            if (onOpenSessionSheet == null && !suppressShortcutsButton && hasHardwareKeyboard && showShortcutsButton) {
                TooltipIconButton(
                    description = getText(R.string.keyboard_shortcuts),
                    onClick = { showShortcutsDialog = true },
                    modifier = Modifier.withShortcut(Key.K, "K", id = "global.show_dialog") { showShortcutsDialog = true }
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = getText(R.string.keyboard_shortcuts))
                }
            }
            // After the caller's own actions() (e.g. a study mode's edit-card pencil) so the side
            // sheet's icon is always the rightmost action, not sandwiched before them.
            actions()
            SessionOptionsAction(viewModel, screenId, onOpenSessionSheet)
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )

    if (showShortcutsDialog) {
        KeyboardShortcutsDialog(onDismiss = { showShortcutsDialog = false }, viewModel = viewModel, currentScreen = screenId)
    }
}

/**
 * A study session's top bar title: the deck/set name, then " - " and the mode name — but only
 * when the full "Deck - Mode" string actually fits the title slot's available width. If it
 * doesn't, the mode suffix is dropped so the deck name itself is never the thing that gets
 * truncated/ellipsized away. If Timed Session is on, a countdown pill (see [TimedSessionPill])
 * always claims its own space first — the pill never gets squeezed out; the title text is what
 * drops first (to deck-name-only, then disappears entirely) when space runs short.
 */
@Composable
fun AdaptiveSessionTitle(state: StudyState, suppressTimerPill: Boolean = false) {
    val deckName = state.deckWithCards.deck.name
    val modeLabel = state.studyMode.asString()
    val full = "$deckName - $modeLabel"
    val textMeasurer = rememberTextMeasurer()
    val style = LocalTextStyle.current
    // Suppressed while the standard (docked) side sheet is open and showing its own
    // DetailedTimedSessionCard, so the countdown never renders twice at once.
    val remainingSeconds = if (suppressTimerPill) null else state.timedSessionRemainingSeconds()

    Row(verticalAlignment = Alignment.CenterVertically) {
        BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
            val maxWidthPx = constraints.maxWidth
            val displayText = remember(full, deckName, style, maxWidthPx) {
                fun fits(text: String) = maxWidthPx > 0 && !textMeasurer.measure(
                    text = text,
                    style = style,
                    maxLines = 1,
                    softWrap = false,
                    constraints = androidx.compose.ui.unit.Constraints(maxWidth = maxWidthPx)
                ).hasVisualOverflow
                when {
                    fits(full) -> full
                    fits(deckName) -> deckName
                    else -> null
                }
            }
            displayText?.let {
                Text(text = it, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        if (remainingSeconds != null) {
            Spacer(Modifier.width(LocalStudiareDimensions.current.spacingSmall))
            TimedSessionPill(state, remainingSeconds)
        }
    }
}

/** The app bar's live countdown, shown whenever [StudyState.timedSessionEnabled] is on. Redraws at
 *  [timedSessionTickDelaySeconds]'s cadence rather than every second, following whichever display
 *  frequency the mode options configured. [state] is read via [rememberUpdatedState] inside the
 *  polling loop so a pause/resume (which changes [StudyState.timedSessionResumedAt] without
 *  changing [StudyState.sessionId]) is picked up on the very next tick instead of a stale one. */
@Composable
private fun TimedSessionPill(state: StudyState, initialRemainingSeconds: Long) {
    val dimensions = LocalStudiareDimensions.current
    val currentState = rememberUpdatedState(state)
    var remaining by remember(state.sessionId) { mutableStateOf(initialRemainingSeconds) }

    LaunchedEffect(state.sessionId, state.timedSessionResumedAt) {
        while (true) {
            val s = currentState.value
            val r = s.timedSessionRemainingSeconds() ?: break
            remaining = r
            if (r <= 0) break
            delay(timedSessionTickDelaySeconds(r, s.timedSessionDisplayUpdateSecondsUnder60, s.timedSessionDisplayUpdateSecondsOver60) * 1000)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .background(MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(50))
            .padding(horizontal = dimensions.paddingSmall, vertical = 4.dp)
    ) {
        Icon(
            Icons.Default.Timer,
            contentDescription = getText(R.string.timed_session),
            tint = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = formatCountdown(remaining),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/**
 * The Progress tab's bigger, more detailed timer — a countdown with a real determinate
 * [LinearProgressIndicator] when [StudyState.timedSessionEnabled], or a plain ticking elapsed-time
 * row otherwise. Unlike [TimedSessionPill], this never caches the displayed number in a
 * remembered var seeded once from the initial [state] - [tick] (bumped once a second by the
 * [LaunchedEffect] below) only forces a recomposition; the number shown is always read fresh from
 * [state] at that moment via `remember(tick) { ... }`. That matters here specifically because the
 * side sheet this card lives in is torn down and recreated each time it's closed and reopened
 * (unlike the always-on app bar pill), so a value seeded once at first composition would reopen
 * showing whatever [state] happened to be at that exact instant rather than staying live.
 */
@Composable
fun DetailedTimedSessionCard(state: StudyState) {
    val dimensions = LocalStudiareDimensions.current
    val currentState = rememberUpdatedState(state)
    var tick by remember { mutableIntStateOf(0) }

    if (state.timedSessionEnabled) {
        LaunchedEffect(state.sessionId) {
            while (true) {
                val s = currentState.value
                val r = s.timedSessionRemainingSeconds() ?: break
                if (r <= 0) break
                delay(timedSessionTickDelaySeconds(r, s.timedSessionDisplayUpdateSecondsUnder60, s.timedSessionDisplayUpdateSecondsOver60) * 1000)
                tick++
            }
        }
        val remaining = remember(tick) { state.timedSessionRemainingSeconds() } ?: 0L
        val progress = if (state.timedSessionDurationSeconds > 0) {
            1f - (remaining.toFloat() / state.timedSessionDurationSeconds)
        } else 0f

        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.inverseSurface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                modifier = Modifier.padding(dimensions.paddingMedium)
            ) {
                Icon(
                    Icons.Default.Timer,
                    contentDescription = getText(R.string.timed_session),
                    tint = MaterialTheme.colorScheme.inverseOnSurface
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formatCountdown(remaining),
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Text(
                        text = getText(R.string.timed_session),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.inverseOnSurface
                    )
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.inversePrimary,
                        trackColor = MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.3f)
                    )
                }
            }
        }
    } else {
        // Not a timed session — a plain, live-ticking elapsed-time row instead of the countdown
        // card above; formatCountdown's "M:SS"/"H:MM:SS" clock style fits a live-ticking display
        // better than StudyCompletion's single-dominant-unit summary format (which is meant for a
        // frozen final total, not something updating every second).
        LaunchedEffect(state.sessionId, state.isComplete) {
            while (!currentState.value.isComplete) {
                delay(1000)
                tick++
            }
        }
        val elapsed = remember(tick) { state.elapsedSeconds() }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(dimensions.cornerRadiusMedium))
                .padding(dimensions.paddingMedium)
        ) {
            Icon(
                Icons.Default.Timer,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = formatCountdown(elapsed),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = getText(R.string.elapsed_time_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

/**
 * What a pane wants shown in the single, shared outer Scaffold when it's the
 * active (deepest) pane. Panes report this instead of building their own
 * Scaffold/TopAppBar/FAB.
 */
data class PaneChrome(
    val title: @Composable () -> Unit = {},
    val actions: @Composable RowScope.() -> Unit = {},
    val fab: @Composable () -> Unit = {},
    val screenId: ShortcutScreen = ShortcutScreen.OTHER,
    // Mirrors CustomTopAppBar's own suppressShortcutsButton param - true suppresses the shared
    // app bar's standalone keyboard-shortcuts button, for a pane supplying its own combined action
    // (e.g. the Study Hub's single side-navigation icon) in [actions] instead.
    val suppressShortcutsButton: Boolean = false
)

/**
 * A slider for rating the difficulty of a card.
 * @param label The label to display above the slider.
 * @param difficulty The current difficulty value.
 * @param onDifficultyChange Callback for when the difficulty value changes.
 */
@Composable
fun DifficultySlider(
    label: String,
    difficulty: DifficultySetting,
    onDifficultyChange: (DifficultySetting) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Column(modifier = modifier.padding(vertical = dimensions.paddingSmall)) {
        Text(
            text = "$label: ${difficulty.value}",
            style = MaterialTheme.typography.titleSmall, // Expressive bold label
            color = MaterialTheme.colorScheme.tertiary
        )
        Slider(
            value = difficulty.value.toFloat(),
            onValueChange = { onDifficultyChange(DifficultySetting.fromInt(it.roundToInt())) },
            valueRange = 1f..5f,
            steps = 3,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.tertiary,
                activeTrackColor = MaterialTheme.colorScheme.tertiary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * A [FilterChip] for one option in a mutually-exclusive, same-row selection group (e.g. a chip
 * inside a [StableWrapChipRows] grid of sibling mode/sort options). A plain conditional
 * `leadingIcon` - M3 animates that slot's own appearance/disappearance (expand + fade) whenever it
 * goes from null to non-null or back, which is what every chip here actually relies on.
 */
/**
 * Wraps [items] into rows, deciding each row's membership from every item's own
 * *unselected*-state width (measured by calling [chip] with `isSelected = false` in a throwaway
 * subcomposition) rather than its real, current selection state - so which item happens to be
 * selected can never change which row it lands on. Each row is then rendered with [chip]'s real
 * selection state inside its own horizontally-scrolling [Row], so a selected chip that grows
 * wider (e.g. [SequencedSelectionChip] gaining its checkmark) just scrolls, or spills partway past
 * that row's edge, instead of pushing a sibling chip onto a different line. Same technique as
 * `CreateStudySessionDialog`'s Category/Mode chips (`TypedChipGrid` in `SettingsComponents.kt`) -
 * a reserved-width buffer (per-chip, then row-level guessed, then row-level measured) was tried
 * there three different ways first and kept failing in practice before this one held up.
 */
@Composable
internal fun <T> StableWrapChipRows(
    items: List<T>,
    isSelected: (T) -> Boolean,
    chip: @Composable (item: T, isSelected: Boolean) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    SubcomposeLayout(modifier = Modifier.fillMaxWidth()) { constraints ->
        val naturalWidths = items.mapIndexed { index, item ->
            subcompose("measure-$index") { chip(item, false) }
                .first().measure(Constraints()).width
        }
        val spacingPx = dimensions.spacingSmall.roundToPx()

        val rows = mutableListOf<MutableList<Int>>()
        var currentRowWidth = 0
        naturalWidths.forEachIndexed { index, width ->
            val currentRow = rows.lastOrNull()
            if (currentRow == null || currentRowWidth + spacingPx + width > constraints.maxWidth) {
                rows.add(mutableListOf(index))
                currentRowWidth = width
            } else {
                currentRow.add(index)
                currentRowWidth += spacingPx + width
            }
        }

        val contentPlaceable = subcompose("content") {
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                rows.forEach { rowIndices ->
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                    ) {
                        rowIndices.forEach { index ->
                            val item = items[index]
                            chip(item, isSelected(item))
                        }
                    }
                }
            }
        }.first().measure(constraints)

        layout(contentPlaceable.width, contentPlaceable.height) {
            contentPlaceable.placeRelative(0, 0)
        }
    }
}

@Composable
fun SequencedSelectionChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    // Used to stagger the newly-selected chip's checkmark 250ms behind the deselected chip's own
    // shrink (plus a manual animateContentSize spring) specifically to stop two uncoordinated
    // width animations from briefly overflowing the row and bumping its last chip onto the next
    // line. That's now handled structurally instead (every call site lays these out via
    // StableWrapChipRows, which decides row membership from unselected widths alone, so nothing
    // can ever reflow regardless of animation timing) - letting leadingIcon toggle directly, with
    // no artificial delay, uses M3's own built-in expand+fade for the icon appearing/disappearing
    // (confirmed smooth after the Material3 1.5.0 stable upgrade), matching TypedChipGrid's Mode
    // chips instead of fighting it with a second, uncoordinated animation of our own.
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier,
        label = label,
        enabled = enabled,
        leadingIcon = if (selected) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )
}

@Composable
fun SortModeDialogSection(
    sortMode: SortMode, onSortModeChange: (SortMode) -> Unit,
    sortDirection: Direction, onSortDirectionChange: (Direction) -> Unit,
    sortSide: CardSide, onSortSideChange: (CardSide) -> Unit,
    sortExpanded: Boolean, onToggleExpand: () -> Unit,
    showDivider: Boolean = true,
    // false for CreateStudySessionScreen's tabbed layout, where every section is always shown -
    // SetAutoDialogs.kt still wants this collapsible, so it stays the default.
    collapsible: Boolean = true,
    // true only for CreateStudySessionDialog.kt, which wants its chips alphabetized by their
    // displayed label; SetAutoDialogs.kt keeps the enum's own (priority) order by default.
    alphabetical: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(
        title = getText(R.string.sort_and_priority),
        subtitle = if (collapsible) (if (sortMode == SortMode.RANDOM) SortMode.RANDOM.asString() else "${sortMode.asString()} (${sortDirection.asString()})") else null,
        isExpanded = if (collapsible) sortExpanded else null,
        onToggle = if (collapsible) onToggleExpand else null,
        showDivider = showDivider
    ) {
        Column {
            val sortOptionLabels = SortMode.entries.associateWith { it.asString() }
            val sortOptions = if (alphabetical) SortMode.entries.sortedBy { sortOptionLabels[it] } else SortMode.entries
            StableWrapChipRows(
                items = sortOptions,
                isSelected = { it == sortMode }
            ) { option, isSelected ->
                SequencedSelectionChip(
                    selected = isSelected,
                    onClick = { onSortModeChange(option) },
                    label = { Text(option.asString(), maxLines = 1, softWrap = false) }
                )
            }

            if (sortMode != SortMode.RANDOM) {
                Spacer(Modifier.height(dimensions.spacingSmall))
                ConnectedButtonGroup(
                    items = listOf(Direction.ASC, Direction.DESC),
                    selected = sortDirection,
                    onSelected = onSortDirectionChange,
                    labelFor = { if (it == Direction.ASC) getText(R.string.ascending) else getText(R.string.descending) },
                    modifier = Modifier.fillMaxWidth()
                )
                if (sortMode == SortMode.ALPHABETICAL) {
                    ConnectedButtonGroup(
                        items = listOf(CardSide.FRONT, CardSide.BACK),
                        selected = sortSide,
                        onSelected = onSortSideChange,
                        labelFor = { if (it == CardSide.FRONT) getText(R.string.front_side) else getText(R.string.back_side) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** Quick-pick chips (10/25/50/100) for number pickers; only values within [min, max] are shown. */
@Composable
fun PresetChips(
    current: Int,
    max: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    min: Int = 1,
    presets: List<Int> = listOf(10, 25, 50, 100)
) {
    val dimensions = LocalStudiareDimensions.current
    val options = presets.filter { it in min..max }
    if (options.isEmpty()) return
    // Centered when they fit; scrolls sideways when they don't.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val fullWidth = maxWidth
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()).widthIn(min = fullWidth),
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall, Alignment.CenterHorizontally)
        ) {
            options.forEach { value ->
                FilterChip(
                    selected = current == value,
                    onClick = { onSelect(value) },
                    label = { Text(value.toString()) },
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                )
            }
        }
    }
}

/**
 * The tonal "value pill" every -/+ stepper in the app shows its current value in ([CountPicker],
 * `ValueStepper` in `ModeOptions.kt`, `BackupIntervalStepper` in `SettingsScreen.kt`). Tapping it
 * swaps to an inline text field, pre-selected so typing immediately replaces the whole value, with
 * the number-pad keyboard (or the decimal-pad one when [allowDecimal]). The typed text is filtered
 * to only digits (and a single `.` when [allowDecimal]) as it's entered, so it can never contain
 * anything un-parseable — the only thing [isValid] still needs to check is whether the number is
 * in range. On commit (losing focus or IME Done) [onCommit] receives the raw text; callers own
 * parsing and clamping, since steppers here range over both `Int` and `Double` values with very
 * different step sizes and display formats ("4 Tiles", "500 ms", "2.5s", plain "4", …) — clamping
 * to the nearest bound (not reverting to the last value) is the expected behavior throughout.
 */
@Composable
fun EditableStepperPill(
    valueText: String,
    onCommit: (String) -> Unit,
    isValid: (String) -> Boolean,
    modifier: Modifier = Modifier,
    allowDecimal: Boolean = false,
    fontSize: androidx.compose.ui.unit.TextUnit = androidx.compose.ui.unit.TextUnit.Unspecified
) {
    val dimensions = LocalStudiareDimensions.current
    var isEditing by remember { mutableStateOf(false) }
    var editValue by remember { mutableStateOf(androidx.compose.ui.text.input.TextFieldValue(valueText)) }
    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    // onFocusChanged fires once immediately on mount reporting the current (unfocused) state —
    // before the LaunchedEffect below even gets to call requestFocus() — so a naive "not focused
    // -> commit and close" check closes the field the instant it opens. Only react to a real loss
    // once focus has actually been gained at least once.
    var hasBeenFocused by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = modifier
    ) {
        // Centers the value when this pill is stretched wider than its content (e.g. given
        // Modifier.weight(1f) standalone, as Timed Session's duration field does) - a no-op for
        // every other caller, which sizes this to wrap its content tightly via defaultMinSize.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        if (isEditing) {
            androidx.compose.foundation.text.BasicTextField(
                value = editValue,
                onValueChange = { new ->
                    editValue = new.copy(text = new.text.filter { it.isDigit() || (allowDecimal && it == '.') })
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = if (allowDecimal) KeyboardType.Decimal else KeyboardType.Number,
                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = {
                    isEditing = false
                    onCommit(editValue.text)
                }),
                textStyle = LocalTextStyle.current.copy(
                    color = if (isValid(editValue.text)) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.error,
                    fontSize = fontSize,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSecondaryContainer),
                modifier = Modifier
                    .focusRequester(focusRequester)
                    .onFocusChanged { state ->
                        if (state.isFocused) {
                            hasBeenFocused = true
                        } else if (hasBeenFocused) {
                            isEditing = false
                            onCommit(editValue.text)
                        }
                    }
                    .defaultMinSize(minWidth = 32.dp)
                    .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
            )
            LaunchedEffect(Unit) {
                editValue = editValue.copy(selection = androidx.compose.ui.text.TextRange(0, editValue.text.length))
                focusRequester.requestFocus()
            }
        } else {
            Text(
                text = valueText,
                fontSize = fontSize,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .clickable {
                        editValue = androidx.compose.ui.text.input.TextFieldValue(valueText, androidx.compose.ui.text.TextRange(0, valueText.length))
                        hasBeenFocused = false
                        isEditing = true
                    }
                    .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
            )
        }
        }
    }
}

/** Label, -/+ stepper with value, optional slider and preset chips for picking a count from min..max. */
@Composable
fun CountPicker(
    label: String,
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    showSlider: Boolean = true,
    showPresets: Boolean = true,
    min: Int = 1,
    // Color.Unspecified preserves the default "inherit LocalContentColor" behavior — only
    // difficulty-weighting's count label passes an explicit tertiary override.
    labelColor: Color = Color.Unspecified
) {
    val dimensions = LocalStudiareDimensions.current
    if (label.isNotBlank())
        Text(label, style = MaterialTheme.typography.bodyMedium, color = labelColor, modifier = Modifier.padding(top = dimensions.paddingSmall))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall)
    ) {
        TooltipFilledTonalIconButton(description = getText(R.string.decrease), onClick = { if (value > min) onValueChange(value - 1) }, enabled = value > min) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
        Spacer(Modifier.width(dimensions.spacingMedium))

        EditableStepperPill(
            valueText = if (max == 0) "0" else value.toString(),
            onCommit = { text -> text.toIntOrNull()?.let { onValueChange(it.coerceIn(min, max.coerceAtLeast(min))) } },
            isValid = { text -> text.toIntOrNull()?.let { it in min..max } == true },
            fontSize = 24.sp,
            // Capped so it doesn't greedily fill the whole Row (its own inner Box is a plain
            // fillMaxWidth()) and push the + button off the edge of the dialog.
            modifier = Modifier.widthIn(max = 120.dp)
        )

        Spacer(Modifier.width(dimensions.spacingMedium))
        TooltipFilledTonalIconButton(description = getText(R.string.increase), onClick = { if (value < max) onValueChange(value + 1) }, enabled = value < max) { Icon(Icons.Default.Add, getText(R.string.increase)) }
    }
    if (showSlider) {
        Slider(
            value = value.toFloat().coerceIn(1f, max.toFloat().coerceAtLeast(1f)),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 1f..max.toFloat().coerceAtLeast(1f),
            steps = 0
        )
    }
    if (showPresets) PresetChips(current = value, max = max, onSelect = onValueChange, min = min)
}

@Composable
fun CardCountSection(
    numberOfCards: Int,
    availableCardsCount: Int,
    onValueChange: (Int) -> Unit,
    label: String? = null,
    showDivider: Boolean = true
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(
        title = label ?: getText(R.string.number_of_cards),
        showDivider = showDivider
    ) {
        CountPicker(
            label = "",
            value = numberOfCards,
            max = availableCardsCount,
            onValueChange = onValueChange
        )
    }
}

@Composable
fun DialogSection(
    title: String,
    subtitle: String? = null,
    isExpanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
    /** Gap between the header and the content; defaults to spacingSmall. */
    contentTopSpacing: Dp? = null,
    showDivider: Boolean = true,
    /** Title color override, e.g. to match Settings' Customization section headers. Defaults to
     * the normal titleMedium text color when unspecified. */
    titleColor: Color = Color.Unspecified,
    /** Title weight override, e.g. to match the Study Hub's mode-header look. Defaults to
     * titleMedium's own weight when unspecified. */
    titleFontWeight: FontWeight? = null,
    /** Non-null shows a Switch in the header, before the chevron, with its own tap target - lets a
     *  section with an on/off option (e.g. Difficulty Weighting, Timed Session) be turned on/off
     *  without expanding it first. Independent of [isExpanded]/[onToggle]: flipping it doesn't
     *  change the collapsed state either way. */
    switchChecked: Boolean? = null,
    onSwitchChange: ((Boolean) -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val isCollapsible = isExpanded != null && subtitle != null && onToggle != null

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                .clickable(enabled = isCollapsible, onClick = { onToggle?.invoke() })
                .padding(vertical = dimensions.paddingSmall, horizontal = dimensions.paddingSmall),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = titleColor, fontWeight = titleFontWeight)
                // Collapsed (collapsible, not expanded): the usual summary-while-collapsed line,
                // with its own top gap. Non-collapsible (isExpanded == null): subtitle doubles as a
                // static explainer that's always shown, tight under the title with no gap - same
                // sizing/positioning as SwitchModeOption's own title+description pair.
                if (subtitle != null && (!isCollapsible || !isExpanded!!)) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = if (isCollapsible) Modifier.padding(top = dimensions.paddingSmall) else Modifier
                    )
                }
            }
            if (switchChecked != null && onSwitchChange != null) {
                Spacer(Modifier.width(dimensions.spacingSmall))
                StudiareSwitch(checked = switchChecked, onCheckedChange = onSwitchChange)
                if (isCollapsible) Spacer(Modifier.width(dimensions.spacingSmall))
            }
            if (isCollapsible) {
                val iconRotation by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isExpanded!!) 180f else 0f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "chevronRotation"
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) getText(R.string.collapse) else getText(R.string.expand),
                    modifier = Modifier.graphicsLayer { rotationZ = iconRotation }
                )
            }
        }

        AnimatedVisibility(visible = !isCollapsible || isExpanded!!) {
            Column(modifier = Modifier.padding(horizontal = dimensions.paddingSmall)) {
                Spacer(Modifier.height(contentTopSpacing ?: dimensions.spacingSmall))
                content()
            }
        }
        if (showDivider) HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingSmall))
    }
}

@Composable
fun TextFieldWithNotes(
    mainText: String,
    onMainTextChange: (String) -> Unit,
    mainLabel: String,
    notesText: String?,
    onNotesTextChange: (String?) -> Unit,
    notesLabel: String
) {
    val dimensions = LocalStudiareDimensions.current
    val showNotes = notesText != null

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            OutlinedTextField(
                value = mainText,
                onValueChange = onMainTextChange,
                label = { Text(mainLabel) },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
            )
            if (!showNotes) {
                TooltipFilledTonalIconButton(description = getText(R.string.add_note), 
                    onClick = { onNotesTextChange("") },
                    modifier = Modifier.padding(start = dimensions.spacingSmall)
                ) {
                    Icon(Icons.Default.Add, contentDescription = getText(R.string.add_note))
                }
            }
        }
        AnimatedVisibility(visible = showNotes) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = notesText ?: "",
                    onValueChange = { onNotesTextChange(it) },
                    label = { Text(notesLabel) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                )
                TooltipFilledTonalIconButton(description = getText(R.string.remove_note), 
                    onClick = { onNotesTextChange(null) },
                    modifier = Modifier.padding(start = dimensions.spacingSmall),
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Icon(Icons.Default.Clear, contentDescription = getText(R.string.remove_note))
                }
            }
        }
    }
}

@Composable
fun SelectionModeDialogSection(
    state: SelectionSectionState,
    actions: SelectionSectionActions,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    // false for CreateStudySessionScreen's tabbed layout, where every section is always shown -
    // SetAutoDialogs.kt still wants this collapsible, so it stays the default.
    collapsible: Boolean = true,
    // true only for CreateStudySessionDialog.kt, which wants its chips alphabetized by their
    // displayed label; SetAutoDialogs.kt keeps the enum's own (priority) order by default.
    alphabetical: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current

    // Subtitle Logic
    val subtitle = when (state.selectionMode) {
        SelectionMode.ANY -> stringResource(R.string.all_available_cards)
        SelectionMode.TAGS -> stringResource(R.string.tags_selected_format, state.selectedTags.size)
        SelectionMode.DIFFICULTY -> stringResource(R.string.diff_format, state.selectedDifficulties.sorted().joinToString())
        SelectionMode.ALPHABET -> stringResource(R.string.alphabet_filter_format, state.filterSide.asString(), state.alphabetStart, state.alphabetEnd)
        SelectionMode.CARD_ORDER -> stringResource(R.string.cards_range_format, state.cardOrderStart, state.cardOrderEnd)
        SelectionMode.REVIEW_DATE, SelectionMode.INCORRECT_DATE -> stringResource(R.string.time_filter_format, state.filterType.asString(), state.timeValue, state.timeUnit.asString())
        SelectionMode.REVIEW_COUNT -> stringResource(R.string.reviews_filter_format, state.reviewDirection.asString(), state.reviewThreshold)
        SelectionMode.SCORE -> stringResource(R.string.score_filter_format, state.scoreDirection.asString(), state.scoreThreshold)
    }

    DialogSection(
        title = getText(R.string.selection_mode),
        subtitle = if (collapsible) "$subtitle ${if (state.excludeKnown) stringResource(R.string.no_known) else ""}" else null,
        isExpanded = if (collapsible) isExpanded else null,
        onToggle = if (collapsible) onToggleExpand else null
    ) {
        Column {
            val selectionOptionsInPriorityOrder = listOf(
                SelectionMode.ANY,
                SelectionMode.DIFFICULTY,
                SelectionMode.TAGS,
                SelectionMode.ALPHABET,
                SelectionMode.CARD_ORDER,
                SelectionMode.REVIEW_DATE,
                SelectionMode.INCORRECT_DATE,
                SelectionMode.REVIEW_COUNT,
                SelectionMode.SCORE
            )
            val selectionOptionLabels = selectionOptionsInPriorityOrder.associateWith { it.asString() }
            val selectionOptions = if (alphabetical) selectionOptionsInPriorityOrder.sortedBy { selectionOptionLabels[it] } else selectionOptionsInPriorityOrder
            StableWrapChipRows(
                items = selectionOptions,
                isSelected = { it == state.selectionMode }
            ) { option, isSelected ->
                val isEnabled = if (option == SelectionMode.REVIEW_COUNT) state.maxDeckReviews > 0 else true
                SequencedSelectionChip(
                    selected = isSelected,
                    onClick = {
                        actions.onModeChange(option)
                        // Defaults logic
                        if (option == SelectionMode.REVIEW_DATE) actions.onFilterTypeChange(FilterType.EXCLUDE)
                        if (option == SelectionMode.INCORRECT_DATE) actions.onFilterTypeChange(FilterType.INCLUDE)
                    },
                    label = { Text(option.asString(), maxLines = 1, softWrap = false) },
                    enabled = isEnabled
                )
            }

            Spacer(Modifier.height(dimensions.spacingSmall))

            when (state.selectionMode) {
                SelectionMode.ANY -> Text(getText(R.string.selects_all_cards), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                SelectionMode.ALPHABET -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.alphabetStart,
                            onValueChange = { if (it.length <= 1) actions.onAlphabetStartChange(it.uppercase()) },
                            label = { Text(getText(R.string.from)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                        Text("-", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = state.alphabetEnd,
                            onValueChange = { if (it.length <= 1) actions.onAlphabetEndChange(it.uppercase()) },
                            label = { Text(getText(R.string.to)) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                    }
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(getText(R.string.front_side), style = MaterialTheme.typography.bodyMedium)
                        StudiareSwitch(
                            checked = state.filterSide == CardSide.BACK,
                            onCheckedChange = { actions.onFilterSideChange(if (it) CardSide.BACK else CardSide.FRONT) },
                            modifier = Modifier.padding(horizontal = dimensions.paddingSmall)
                        )
                        Text(getText(R.string.back_side), style = MaterialTheme.typography.bodyMedium)
                    }
                    ConnectedButtonGroup(
                        items = listOf(CardSide.FRONT, CardSide.BACK),
                        selected = state.filterSide,
                        onSelected = actions.onFilterSideChange,
                        labelFor = { if (it == CardSide.FRONT) getText(R.string.front_side) else getText(R.string.back_side) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                SelectionMode.CARD_ORDER -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.start_card_format, state.cardOrderStart), style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = state.cardOrderStart.toFloat(),
                                onValueChange = { actions.onCardOrderStartChange(it.roundToInt()) },
                                valueRange = 1f..state.totalCards.toFloat(),
                                steps = 0
                            )
                        }
                        OutlinedTextField(
                            value = state.cardOrderStart.toString(),
                            onValueChange = { actions.onCardOrderStartChange(it.toIntOrNull() ?: 1) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.end_card_format, state.cardOrderEnd), style = MaterialTheme.typography.labelSmall)
                            Slider(
                                value = state.cardOrderEnd.toFloat(),
                                onValueChange = { actions.onCardOrderEndChange(it.roundToInt()) },
                                valueRange = 1f..state.totalCards.toFloat(),
                                steps = 0
                            )
                        }
                        OutlinedTextField(
                            value = state.cardOrderEnd.toString(),
                            onValueChange = { actions.onCardOrderEndChange(it.toIntOrNull() ?: state.totalCards) },
                            modifier = Modifier.width(80.dp),
                            singleLine = true,
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                    }
                }

                SelectionMode.REVIEW_DATE, SelectionMode.INCORRECT_DATE -> {
                    Column {
                        var isUnitDropdownExpanded by remember { mutableStateOf(false) }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            FilledTonalIconButton(
                                onClick = { if (state.timeValue > 1) actions.onTimeValueChange(state.timeValue - 1) },
                                modifier = Modifier.size(40.dp)
                            ) { Text("-", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

                            OutlinedTextField(
                                value = state.timeValue.toString(),
                                onValueChange = { actions.onTimeValueChange(it.toIntOrNull() ?: 1) },
                                label = { Text(getText(R.string.amount)) },
                                modifier = Modifier.width(60.dp),
                                singleLine = true,
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                                ),
                                textStyle = androidx.compose.ui.text.TextStyle(textAlign = TextAlign.Center)
                            )

                            FilledTonalIconButton(
                                onClick = { actions.onTimeValueChange(state.timeValue + 1) },
                                modifier = Modifier.size(40.dp)
                            ) { Text("+", fontWeight = FontWeight.Bold, fontSize = 18.sp) }

                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedButton(
                                    onClick = { isUnitDropdownExpanded = true },
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                    contentPadding = PaddingValues(horizontal = dimensions.paddingSmall)
                                ) {
                                    Text(state.timeUnit.asString())
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(
                                    expanded = isUnitDropdownExpanded,
                                    onDismissRequest = { isUnitDropdownExpanded = false }
                                ) {
                                    TimeUnit.entries.forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit.asString()) },
                                            onClick = { actions.onTimeUnitChange(unit); isUnitDropdownExpanded = false }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(dimensions.spacingMedium))

                        ConnectedButtonGroup(
                            items = listOf(FilterType.INCLUDE, FilterType.EXCLUDE),
                            selected = state.filterType,
                            onSelected = actions.onFilterTypeChange,
                            labelFor = { it.asString() },
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(Modifier.height(dimensions.spacingSmall))
                        Text(
                            text = if (state.selectionMode == SelectionMode.REVIEW_DATE) {
                                if (state.filterType == FilterType.EXCLUDE) stringResource(R.string.selects_not_reviewed_format, state.timeValue, state.timeUnit.asString())
                                else stringResource(R.string.selects_reviewed_format, state.timeValue, state.timeUnit.asString())
                            } else {
                                if (state.filterType == FilterType.EXCLUDE) stringResource(R.string.selects_not_incorrect_format, state.timeValue, state.timeUnit.asString())
                                else stringResource(R.string.selects_incorrect_format, state.timeValue, state.timeUnit.asString())
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SelectionMode.REVIEW_COUNT -> {
                    Column {
                        val sliderColors = if (state.reviewDirection == Direction.ASC) {
                            SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary
                            )
                        } else SliderDefaults.colors()

                        Text(stringResource(R.string.reviews_count_format, state.reviewThreshold), style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.reviewThreshold.toFloat(),
                            onValueChange = { actions.onReviewThresholdChange(it.roundToInt()) },
                            valueRange = 0f..state.maxDeckReviews.toFloat(),
                            steps = 0,
                            colors = sliderColors
                        )

                        ConnectedButtonGroup(
                            items = listOf(Direction.ASC, Direction.DESC),
                            selected = state.reviewDirection,
                            onSelected = actions.onReviewDirectionChange,
                            labelFor = { it.asString() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                SelectionMode.SCORE -> {
                    Column {
                        val sliderColors = if (state.scoreDirection == Direction.ASC) {
                            SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary
                            )
                        } else SliderDefaults.colors()

                        Text(stringResource(R.string.score_percent_format, state.scoreThreshold), style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.scoreThreshold.toFloat(),
                            onValueChange = { actions.onScoreThresholdChange(it.roundToInt()) },
                            valueRange = 0f..100f,
                            steps = 0,
                            colors = sliderColors
                        )

                        ConnectedButtonGroup(
                            items = listOf(Direction.ASC, Direction.DESC),
                            selected = state.scoreDirection,
                            onSelected = actions.onScoreDirectionChange,
                            labelFor = { it.asString() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }

                SelectionMode.TAGS -> {
                    if (state.availableTags.isEmpty()) {
                        Text(getText(R.string.no_tags_found), color = MaterialTheme.colorScheme.error)
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(vertical = dimensions.paddingSmall),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            state.availableTags.sortedBy { it.lowercase() }.forEach { tagName ->
                                val tagDef = state.allTagDefinitions.find { it.name == tagName }
                                val colorHex = tagDef?.color ?: "#0D47A1"
                                val isSelected = tagName in state.selectedTags

                                net.ericclark.studiare.components.TagChip(
                                    text = tagName,
                                    colorHex = colorHex,
                                    onClick = {
                                        if (isSelected) {
                                            actions.onTagsChange(state.selectedTags - tagName)
                                        } else {
                                            actions.onTagsChange(state.selectedTags + tagName)
                                        }
                                    },
                                    // Just a visual marker — the whole chip, not only this icon,
                                    // unapplies the tag — matching TagSelectionDialog's behavior.
                                    trailingIcon = if (isSelected) Icons.Default.Clear else null
                                )
                            }
                        }
                    }
                }

                SelectionMode.DIFFICULTY -> {
                    // Multi-select, so it can't use the single-select ConnectedButtonGroup — same
                    // connected-shapes pattern (M3 Expressive's MultiSelectConnectedButtonGroupSample),
                    // hand-rolled here since this is the only multi-select site in the app.
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                    ) {
                        (1..5).forEachIndexed { index, diff ->
                            val isSelected = diff in state.selectedDifficulties
                            ToggleButton(
                                checked = isSelected,
                                onCheckedChange = {
                                    val newDiffs = state.selectedDifficulties.toMutableList()
                                    if (isSelected) {
                                        if (newDiffs.size > 1) newDiffs.remove(diff)
                                    } else newDiffs.add(diff)
                                    actions.onDifficultiesChange(newDiffs)
                                },
                                modifier = Modifier.weight(1f),
                                shapes = when (index) {
                                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    4 -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                }
                            ) {
                                Text(diff.toString())
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(dimensions.spacingLarge))
            // Global Exclude
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(getText(R.string.exclude_known_cards), modifier = Modifier.weight(1f))
                StudiareSwitch(checked = state.excludeKnown, onCheckedChange = actions.onExcludeKnownChange)
            }
            Text(stringResource(R.string.available_pool_format, state.availableCardsCount), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}
