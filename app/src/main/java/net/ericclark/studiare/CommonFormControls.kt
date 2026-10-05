package net.ericclark.studiare

import net.ericclark.studiare.screens.SessionOptionsAction
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextStyle

@Composable
fun CustomTopAppBar(
    title: @Composable () -> Unit,
    viewModel: FlashcardViewModel,
    modifier: Modifier = Modifier,
    screenId: ShortcutScreen = ShortcutScreen.OTHER,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
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
            if (hasHardwareKeyboard && showShortcutsButton) {
                TooltipIconButton(
                    description = getText(R.string.keyboard_shortcuts),
                    onClick = { showShortcutsDialog = true },
                    modifier = Modifier.withShortcut(Key.K, "K", id = "global.show_dialog") { showShortcutsDialog = true }
                ) {
                    Icon(Icons.Default.Keyboard, contentDescription = getText(R.string.keyboard_shortcuts))
                }
            }
            SessionOptionsAction(viewModel, screenId)
            actions()
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
 * What a pane wants shown in the single, shared outer Scaffold when it's the
 * active (deepest) pane. Panes report this instead of building their own
 * Scaffold/TopAppBar/FAB.
 */
data class PaneChrome(
    val title: @Composable () -> Unit = {},
    val actions: @Composable RowScope.() -> Unit = {},
    val fab: @Composable () -> Unit = {},
    val screenId: ShortcutScreen = ShortcutScreen.OTHER
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
            color = MaterialTheme.colorScheme.primary
        )
        Slider(
            value = difficulty.value.toFloat(),
            onValueChange = { onDifficultyChange(DifficultySetting.fromInt(it.roundToInt())) },
            valueRange = 1f..5f,
            steps = 3,
            interactionSource = interactionSource,
            colors = SliderDefaults.colors(
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}

/**
 * A [FilterChip] for one option in a mutually-exclusive, same-row selection group (e.g. a chip
 * inside a [androidx.compose.foundation.layout.FlowRow] of sibling mode/sort options). Its leading
 * checkmark drives [Modifier.animateContentSize] width changes, same as a plain `FilterChip` with a
 * conditional `leadingIcon` — but when [selected] flips from one sibling chip to another, this
 * delays the newly-selected chip's checkmark (and the growth it triggers) until the deselected
 * chip's own shrink has settled, instead of letting both run at once. Two uncoordinated width
 * animations otherwise cause the row's total width to briefly exceed its available space, bumping
 * the row's last chip onto the next line before it snaps back once both finish. The resting
 * appearance (icon shown exactly when [selected]) and the growth animation itself are unchanged;
 * only the growing chip's icon is staggered behind the shrinking chip's.
 */
@Composable
fun SequencedSelectionChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    var showIcon by remember { mutableStateOf(selected) }
    LaunchedEffect(selected) {
        if (selected) {
            delay(250)
            showIcon = true
        } else {
            showIcon = false
        }
    }
    FilterChip(
        selected = selected,
        onClick = onClick,
        modifier = modifier.animateContentSize(
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioNoBouncy,
                stiffness = Spring.StiffnessMedium
            )
        ),
        label = label,
        enabled = enabled,
        leadingIcon = if (showIcon) {
            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
        } else null
    )
}

@Composable
fun SortModeDialogSection(
    sortMode: SortMode, onSortModeChange: (SortMode) -> Unit,
    sortDirection: Direction, onSortDirectionChange: (Direction) -> Unit,
    sortSide: CardSide, onSortSideChange: (CardSide) -> Unit,
    sortExpanded: Boolean, onToggleExpand: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(
        title = getText(R.string.sort_and_priority),
        subtitle = if (sortMode == SortMode.RANDOM) SortMode.RANDOM.asString() else "${sortMode.asString()} (${sortDirection.asString()})",
        isExpanded = sortExpanded,
        onToggle = onToggleExpand
    ) {
        Column {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
            ) {
                SortMode.entries.forEach { option ->
                    SequencedSelectionChip(
                        selected = sortMode == option,
                        onClick = { onSortModeChange(option) },
                        label = { Text(option.asString(), maxLines = 1, softWrap = false) }
                    )
                }
            }

            if (sortMode != SortMode.RANDOM) {
                Spacer(Modifier.height(dimensions.spacingSmall))
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = sortDirection == Direction.ASC,
                        onClick = { onSortDirectionChange(Direction.ASC) },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(getText(R.string.ascending)) }
                    SegmentedButton(
                        selected = sortDirection == Direction.DESC,
                        onClick = { onSortDirectionChange(Direction.DESC) },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(getText(R.string.descending)) }
                }
                if (sortMode == SortMode.ALPHABETICAL) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = sortSide == CardSide.FRONT,
                            onClick = { onSortSideChange(CardSide.FRONT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(getText(R.string.front_side)) }
                        SegmentedButton(
                            selected = sortSide == CardSide.BACK,
                            onClick = { onSortSideChange(CardSide.BACK) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(getText(R.string.back_side)) }
                    }
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

/** Label, -/+ stepper with value, optional slider and preset chips for picking a count from min..max. */
@Composable
fun CountPicker(
    label: String,
    value: Int,
    max: Int,
    onValueChange: (Int) -> Unit,
    showSlider: Boolean = true,
    showPresets: Boolean = true,
    min: Int = 1
) {
    val dimensions = LocalStudiareDimensions.current
    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = dimensions.paddingSmall))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall)
    ) {
        TooltipFilledTonalIconButton(description = getText(R.string.decrease), onClick = { if (value > min) onValueChange(value - 1) }, enabled = value > min) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
        Spacer(Modifier.width(dimensions.spacingMedium))

        // M3 Expressive Tonal Value Indicator
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                text = if (max == 0) "0" else value.toString(),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
            )
        }

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
    isExpanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onValueChange: (Int) -> Unit,
    label: String? = null
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(
        title = label ?: getText(R.string.number_of_cards),
        subtitle = stringResource(R.string.count_of_total_format, numberOfCards, availableCardsCount),
        isExpanded = isExpanded,
        onToggle = { onToggle(!isExpanded) }
    ) {
        CountPicker(
            label = stringResource(R.string.count_format, numberOfCards),
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
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (isCollapsible && !isExpanded!!) {
                    Text(
                        text = subtitle ?: "",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = dimensions.paddingSmall)
                    )
                }
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
        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingSmall))
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
    onToggleExpand: () -> Unit
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
        subtitle = "$subtitle ${if (state.excludeKnown) stringResource(R.string.no_known) else ""}",
        isExpanded = isExpanded,
        onToggle = onToggleExpand
    ) {
        Column {
            val selectionOptions = listOf(
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
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
            ) {
                selectionOptions.forEach { option ->
                    val isEnabled = if (option == SelectionMode.REVIEW_COUNT) state.maxDeckReviews > 0 else true
                    SequencedSelectionChip(
                        selected = state.selectionMode == option,
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
                        Text(getText(R.string.front_side), style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = state.filterSide == CardSide.BACK,
                            onCheckedChange = { actions.onFilterSideChange(if (it) CardSide.BACK else CardSide.FRONT) },
                            modifier = Modifier.padding(horizontal = dimensions.paddingSmall)
                        )
                        Text(getText(R.string.back_side), style = MaterialTheme.typography.bodySmall)
                    }
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.filterSide == CardSide.FRONT,
                            onClick = { actions.onFilterSideChange(CardSide.FRONT) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(getText(R.string.front_side)) }
                        SegmentedButton(
                            selected = state.filterSide == CardSide.BACK,
                            onClick = { actions.onFilterSideChange(CardSide.BACK) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(getText(R.string.back_side)) }
                    }
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

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = state.filterType == FilterType.INCLUDE,
                                onClick = { actions.onFilterTypeChange(FilterType.INCLUDE) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) { Text(FilterType.INCLUDE.asString()) }
                            SegmentedButton(
                                selected = state.filterType == FilterType.EXCLUDE,
                                onClick = { actions.onFilterTypeChange(FilterType.EXCLUDE) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) { Text(FilterType.EXCLUDE.asString()) }
                        }

                        Spacer(Modifier.height(dimensions.spacingSmall))
                        Text(
                            text = if (state.selectionMode == SelectionMode.REVIEW_DATE) {
                                if (state.filterType == FilterType.EXCLUDE) stringResource(R.string.selects_not_reviewed_format, state.timeValue, state.timeUnit.asString())
                                else stringResource(R.string.selects_reviewed_format, state.timeValue, state.timeUnit.asString())
                            } else {
                                if (state.filterType == FilterType.EXCLUDE) stringResource(R.string.selects_not_incorrect_format, state.timeValue, state.timeUnit.asString())
                                else stringResource(R.string.selects_incorrect_format, state.timeValue, state.timeUnit.asString())
                            },
                            style = MaterialTheme.typography.bodySmall,
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

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = state.reviewDirection == Direction.ASC,
                                onClick = { actions.onReviewDirectionChange(Direction.ASC) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) { Text(Direction.ASC.asString()) }
                            SegmentedButton(
                                selected = state.reviewDirection == Direction.DESC,
                                onClick = { actions.onReviewDirectionChange(Direction.DESC) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) { Text(Direction.DESC.asString()) }
                        }
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

                        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                            SegmentedButton(
                                selected = state.scoreDirection == Direction.ASC,
                                onClick = { actions.onScoreDirectionChange(Direction.ASC) },
                                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                            ) { Text(Direction.ASC.asString()) }
                            SegmentedButton(
                                selected = state.scoreDirection == Direction.DESC,
                                onClick = { actions.onScoreDirectionChange(Direction.DESC) },
                                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                            ) { Text(Direction.DESC.asString()) }
                        }
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
                                    onDelete = if (isSelected) {
                                        { actions.onTagsChange(state.selectedTags - tagName) }
                                    } else null,
                                    onClick = if (!isSelected) {
                                        { actions.onTagsChange(state.selectedTags + tagName) }
                                    } else null
                                )
                            }
                        }
                    }
                }

                SelectionMode.DIFFICULTY -> {
                    MultiChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        (1..5).forEachIndexed { index, diff ->
                            val isSelected = diff in state.selectedDifficulties
                            SegmentedButton(
                                checked = isSelected,
                                onCheckedChange = {
                                    val newDiffs = state.selectedDifficulties.toMutableList()
                                    if (isSelected) {
                                        if (newDiffs.size > 1) newDiffs.remove(diff)
                                    } else newDiffs.add(diff)
                                    actions.onDifficultiesChange(newDiffs)
                                },
                                shape = SegmentedButtonDefaults.itemShape(index = index, count = 5)
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
                Switch(checked = state.excludeKnown, onCheckedChange = actions.onExcludeKnownChange)
            }
            Text(stringResource(R.string.available_pool_format, state.availableCardsCount), style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}
