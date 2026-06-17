package net.ericclark.studiare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.data.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.TagDefinition
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.launch
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import kotlinx.coroutines.coroutineScope
import net.ericclark.studiare.LocalNavAnimatedVisibilityScope
import net.ericclark.studiare.LocalSharedTransitionScope
import coil.compose.AsyncImage
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import coil.compose.AsyncImage
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * A stable, custom implementation of a TopAppBar to avoid using experimental Material3 APIs.
 * @param title The title composable to be displayed in the app bar.
 * @param modifier The modifier to be applied to the app bar.
 * @param navigationIcon The composable for the navigation icon.
 * @param actions The composable for the actions on the trailing side.
 */
@Composable
fun CustomTopAppBar(
    title: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    var showShortcutsDialog by remember { mutableStateOf(false) }
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val hasHardwareKeyboard = configuration.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY

    CenterAlignedTopAppBar( // M3 Expressive favors centered, breathable headers
        title = title,
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = {
            if (hasHardwareKeyboard) {
                IconButton(onClick = { showShortcutsDialog = true }) {
                    Icon(Icons.Default.Keyboard, contentDescription = "Keyboard Shortcuts")
                }
            }
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
        KeyboardShortcutsDialog(onDismiss = { showShortcutsDialog = false })
    }
}

@Composable
fun KeyboardShortcutsDialog(onDismiss: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Keyboard Shortcuts") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
            ) {
                ShortcutSection("Global")
                ShortcutItem("Show Hints", "Alt (Hold)")
                ShortcutItem("Navigate Back", "Esc / Backspace")

                ShortcutSection("Study Session")
                ShortcutItem("Flip / Next Card", "Space / Enter")
                ShortcutItem("Previous / Next", "Left / Right Arrows")
                ShortcutItem("Rate Difficulty", "1 - 5")
                ShortcutItem("Mark Known / Unknown", "K / U")

                ShortcutSection("Crossword Mode")
                ShortcutItem("Jump to Clue", "/ or Ctrl + J")
                ShortcutItem("Focus Clue List", "Alt + C")
                ShortcutItem("Switch Across/Down", "Enter (at intersections)")
                ShortcutItem("Get Hint", "H (Shift+H for full)")
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
fun ShortcutSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun ShortcutItem(action: String, shortcut: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(text = action, style = MaterialTheme.typography.bodyMedium)
        Text(
            text = shortcut,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

// Loading Overlay Composable


/**
 * A reusable button for navigating between cards in a study session.
 * @param onClick The action to perform when the button is clicked.
 * @param icon The icon to display on the button.
 * @param modifier The modifier to apply to the button.
 */
@Composable
fun StudyCardNavButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    containerColor: Color? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "buttonSquish"
    )

    // Determine colors: Use provided ones or fallback to M3 defaults
    val colors = if (containerColor != null) {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor
        )
    } else {
        IconButtonDefaults.filledTonalIconButtonColors()
    }

    // M3 Expressive prefers FilledTonal for secondary actions

    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .size(42.dp) // Increased to Expressive 56dp standard touch target
            .scale(scale),
        shape = CircleShape, // Enforce expressive circular shape
        colors = colors
    ) {
        icon()
    }
}

/**
 * A circular button with a checkmark to mark a card as "known".
 * @param isKnown The current known status of the card.
 * @param onClick The action to perform when the button is clicked.
 */
@Composable
fun MarkKnownButton(
    isKnown: Boolean,
    onClick: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "knownSquish"
    )

    OutlinedIconToggleButton(
        checked = isKnown,
        onCheckedChange = { onClick() },
        interactionSource = interactionSource,
        modifier = Modifier
            .size(56.dp) // Increased to Expressive 56dp touch target
            .scale(scale),
        shape = CircleShape, // Explicitly enforce expressive circular shape
        colors = IconButtonDefaults.outlinedIconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = IconButtonDefaults.outlinedIconToggleButtonBorder(
            enabled = true,
            checked = isKnown
        )
    ) {
        Icon(
            imageVector = if (isKnown) Icons.Filled.Check else Icons.Default.Check,
            contentDescription = if (isKnown) getText(R.string.mark_as_not_known) else getText(R.string.mark_as_known)
        )
    }
}


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

@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonText: String? = null,
    dismissButtonText: String? = null, // ADD THIS PARAMETER
    icon: @Composable (() -> Unit)? = null
) {
    val dimensions = LocalStudiareDimensions.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = icon,
        title = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmButtonText ?: getText(R.string.confirm)) }
        },
        dismissButton = {
            // USE THE NEW PARAMETER HERE
            TextButton(onClick = onDismiss) { Text(dismissButtonText ?: getText(R.string.cancel)) }
        }
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
                    FilterChip(
                        selected = sortMode == option,
                        onClick = { onSortModeChange(option) },
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                        label = { Text(option.asString(), maxLines = 1, softWrap = false) },
                        leadingIcon = if (sortMode == option) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
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
        Text(stringResource(R.string.count_format, numberOfCards), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = dimensions.paddingSmall))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall)
        ) {
            FilledTonalIconButton(onClick = { if (numberOfCards > 1) onValueChange(numberOfCards - 1) }, enabled = numberOfCards > 1) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
            Spacer(Modifier.width(dimensions.spacingMedium))

            // M3 Expressive Tonal Value Indicator
            Surface(
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ) {
                Text(
                    text = if (availableCardsCount == 0) "0" else numberOfCards.toString(),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
                )
            }

            Spacer(Modifier.width(dimensions.spacingMedium))
            FilledTonalIconButton(onClick = { if (numberOfCards < availableCardsCount) onValueChange(numberOfCards + 1) }, enabled = numberOfCards < availableCardsCount) { Icon(Icons.Default.Add, getText(R.string.increase)) }
        }
        Slider(
            value = numberOfCards.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 1f..availableCardsCount.toFloat().coerceAtLeast(1f),
            steps = 0
        )
    }
}

@Composable
fun DialogSection(
    title: String,
    subtitle: String? = null,
    isExpanded: Boolean? = null,
    onToggle: (() -> Unit)? = null,
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
                Spacer(Modifier.height(dimensions.spacingSmall))
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
                FilledTonalIconButton(
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
                FilledTonalIconButton(
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
                    FilterChip(
                        selected = state.selectionMode == option,
                        onClick = {
                            actions.onModeChange(option)
                            // Defaults logic
                            if (option == SelectionMode.REVIEW_DATE) actions.onFilterTypeChange(FilterType.EXCLUDE)
                            if (option == SelectionMode.INCORRECT_DATE) actions.onFilterTypeChange(FilterType.INCLUDE)
                        },
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                        label = { Text(option.asString(), maxLines = 1, softWrap = false) },
                        enabled = isEnabled,
                        leadingIcon = if (state.selectionMode == option) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
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
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
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

@Composable
fun ToggleButton(text: String, isSelected: Boolean, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val dimensions = LocalStudiareDimensions.current

    val targetContainerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer // M3 Expressive shift: PrimaryContainer for selected toggles
        else -> Color.Transparent
    }

    val targetContentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer // M3 Expressive shift
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "toggleBg"
    )

    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "toggleText"
    )

    val border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        border = border,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        contentPadding = PaddingValues(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
    ) {
        Text(text, maxLines = 1)
    }
}

@Composable
fun ToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current

    val targetContainerColor = when {
        !isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    val targetContentColor = when {
        !isSelected -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "toggleBg"
    )

    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "toggleText"
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder,
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = dimensions.paddingSmall)
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
}

// Helper to parse hex color safely
fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray // Fallback color
    }
}

/**
 * A highly reusable, expressive Flashcard component that supports 3D flipping,
 * navigation, and tag display.
 *
 * @param frontText The text to display on the front.
 * @param backText The text to display on the back.
 * @param isFlipped Whether the card is currently showing the back.
 * @param onFlip Callback triggered when the card is tapped.
 * @param modifier Modifier for the card container.
 * @param frontNotes Optional notes for the front side.
 * @param backNotes Optional notes for the back side.
 * @param showNavigation Whether to show the Next/Previous arrow buttons.
 * @param onNext Callback for the Next button.
 * @param onPrevious Callback for the Previous button.
 * @param tags Optional list of tags to display at the bottom of the card.
 * @param containerColorFront Background color for the front.
 * @param contentColorFront Text color for the front.
 * @param containerColorBack Background color for the back.
 * @param contentColorBack Text color for the back.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CommonFlashcard(
    frontText: String,
    isFrontRichText: Boolean = false,
    backText: String,
    isBackRichText: Boolean = false,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    frontNotes: List<NoteField> = emptyList(),
    backNotes: List<NoteField> = emptyList(),
    showBackNavigation: Boolean = false,
    showFrontNavigation: Boolean = false,
    showIndex: Boolean = true,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    tags: List<TagDefinition> = emptyList(),
    containerColorFront: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColorFront: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColorBack: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColorBack: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    cardIndex: Int,
    totalCards: Int,
    sessionId: String = "", // NEW: Pass the sessionId down to link the animation!
    completelyHideNavigation: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current
    val context = LocalContext.current

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    // Generate the shared bounds modifier based on the session ID
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && sessionId.isNotEmpty()) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "session_card_$sessionId"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
            )
        }
    } else Modifier

    // Independent axes of rotation
    val rotationX = remember { androidx.compose.animation.core.Animatable(if (isFlipped) 180f else 0f) }
    val rotationY = remember { androidx.compose.animation.core.Animatable(0f) }

    // State holding what is CURRENTLY being rendered so we can swap it mid-flip
    var renderFrontText by remember { mutableStateOf(frontText) }
    var renderIsFrontRichText by remember { mutableStateOf(isFrontRichText) }
    var renderBackText by remember { mutableStateOf(backText) }
    var renderIsBackRichText by remember { mutableStateOf(isBackRichText) }
    var renderFrontNotes by remember { mutableStateOf(frontNotes) }
    var renderBackNotes by remember { mutableStateOf(backNotes) }
    var renderTags by remember { mutableStateOf(tags) }

    var renderIsFlipped by remember { mutableStateOf(isFlipped) }
    var fullScreenNote by remember { mutableStateOf<NoteField?>(null) }

    var prevIndex by remember { mutableIntStateOf(cardIndex) }
    var prevIsFlipped by remember { mutableStateOf(isFlipped) }

    LaunchedEffect(cardIndex, isFlipped, frontText, backText, frontNotes, backNotes, tags) {
        if (cardIndex == prevIndex && isFlipped == prevIsFlipped) {
            renderFrontText = frontText
            renderBackText = backText
            renderFrontNotes = frontNotes
            renderBackNotes = backNotes
            renderTags = tags
            return@LaunchedEffect
        }

        if (cardIndex != prevIndex) {
            // Horizontal flip for Next/Prev card
            val dir = if (cardIndex > prevIndex) 180f else -180f

            launch {
                rotationY.animateTo(
                    targetValue = rotationY.targetValue + dir,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    )
                )
            }

            // Wait for halfway point of the flip to swap the text
            kotlinx.coroutines.delay(150)

            renderFrontText = frontText
            renderIsFrontRichText = isFrontRichText
            renderBackText = backText
            renderIsBackRichText = isBackRichText
            renderFrontNotes = frontNotes
            renderBackNotes = backNotes
            renderTags = tags
            renderIsFlipped = isFlipped

        } else if (isFlipped != prevIsFlipped) {
            // Vertical flip for turning card
            val dir = if (isFlipped) 180f else -180f

            // Update text immediately (the natural flip hides it)
            renderFrontText = frontText
            renderIsFrontRichText = isFrontRichText
            renderBackText = backText
            renderIsBackRichText = isBackRichText
            renderFrontNotes = frontNotes
            renderBackNotes = backNotes
            renderIsFlipped = isFlipped

            launch {
                rotationX.animateTo(
                    targetValue = rotationX.targetValue + dir,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    )
                )
            }
        }

        prevIndex = cardIndex
        prevIsFlipped = isFlipped
    }

    // Determine current visual state based on absolute accumulated rotations
    val currentRotY = Math.abs(rotationY.value)
    val currentRotX = Math.abs(rotationX.value)

    val yFlips = ((currentRotY + 90f) / 180f).toInt()
    val xFlips = ((currentRotX + 90f) / 180f).toInt()

    val isYFlipped = yFlips % 2 != 0
    val isXFlipped = xFlips % 2 != 0

    // The back is logical if it has been flipped an ODD number of times total
    val isBackVisible = renderIsFlipped

    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isBackVisible) containerColorBack else containerColorFront,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "cardBgColor"
    )

    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isBackVisible) contentColorBack else contentColorFront,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "cardTextColor"
    )

    val navButtonContainerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isBackVisible) containerColorFront else containerColorBack,
        label = "navBgColor"
    )

    Box(
        modifier = modifier
            .then(sharedModifier)
            .clip(RoundedCornerShape(dimensions.cornerRadiusLarge))
            .graphicsLayer {
                this.rotationY = rotationY.value
                this.rotationX = rotationX.value
                cameraDistance = 12f * density
            }
            .background(containerColor)
            .clickable { onFlip() },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.paddingLarge)
                .graphicsLayer {
                    // Counteract rotations to keep text right-side up and un-mirrored
                    if (isYFlipped) this.rotationY = 180f
                    if (isXFlipped) this.rotationX = 180f
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(bottom = if (renderTags.isNotEmpty()) 32.dp else 0.dp)
            ) {
                val currentText = if (isBackVisible) renderBackText else renderFrontText
                val isCurrentRichText = if (isBackVisible) renderIsBackRichText else renderIsFrontRichText

                if (isCurrentRichText) {
                    val state = rememberRichTextState()
                    LaunchedEffect(currentText) { state.setHtml(currentText) }

                    RichText(
                        state = state,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = contentColor
                    )
                } else {
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = contentColor
                    )
                }

                val currentNotes = if (isBackVisible) renderBackNotes else renderFrontNotes
                if (currentNotes.isNotEmpty()) {
                    Spacer(Modifier.height(dimensions.spacingSmall))

                    val textNotes = currentNotes.filter { it.type == MediaType.PLAIN_TEXT || it.type == MediaType.RICH_TEXT || it.type == MediaType.HTML || it.type == MediaType.WEB_LINK }
                    val mediaNotes = currentNotes.filter {
                        (it.type == MediaType.IMAGE || it.type == MediaType.VIDEO || it.type == MediaType.AUDIO) &&
                                it.content.isNotBlank() &&
                                it.content.startsWith(context.filesDir.absolutePath)
                    }

                    textNotes.forEach { note ->
                        when (note.type) {
                            MediaType.PLAIN_TEXT -> {
                                Text(
                                    text = "${note.name}\n${note.content}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontStyle = FontStyle.Italic,
                                    textAlign = TextAlign.Center,
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            }
                            MediaType.RICH_TEXT, MediaType.HTML -> {
                                val state = rememberRichTextState()
                                LaunchedEffect(note.content) { state.setHtml(note.content) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${note.name}\n", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
                                    RichText(state = state, style = MaterialTheme.typography.bodyLarge, color = contentColor.copy(alpha = 0.8f))
                                }
                            }
                            MediaType.WEB_LINK -> {
                                Text(
                                    text = "${note.name}\n${note.content}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                )
                            }
                            else -> {}
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (mediaNotes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            mediaNotes.forEach { note ->
                                MediaThumbnail(note = note, onClick = { fullScreenNote = note }, contentColor = contentColor)
                            }
                        }
                    }
                }
            }

            if (renderTags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = if (showBackNavigation) 64.dp else 0.dp,
                            end = if (showFrontNavigation) 64.dp else 0.dp
                        )
                        .horizontalScroll(rememberScrollState())
                ) {
                    renderTags.forEach { tag ->
                        val chipColor = parseHexColor(tag.color)
                        Surface(
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            color = chipColor,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isYFlipped) this.rotationY = 180f
                    if (isXFlipped) this.rotationX = 180f
                }
        ) {
            if (totalCards > 0 && showIndex) {
                SuggestionChip(
                    onClick = { },
                    label = { Text(stringResource(R.string.card_index_of_total, cardIndex + 1, totalCards)) },
                    // Separate the padding directions to override the invisible touch target boundary
                    modifier = Modifier.align(Alignment.TopStart).padding(start = dimensions.paddingSmall, top = 0.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = null
                )
            }

            if (!completelyHideNavigation)
            {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(dimensions.paddingSmall)) {
                    StudyCardNavButton(
                        onClick = onPrevious,
                        icon = { Icon(Icons.Default.KeyboardArrowLeft, getText(R.string.previous)) },
                        containerColor = navButtonContainerColor,
                        enabled = showBackNavigation
                    )
                }

                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(dimensions.paddingSmall)) {
                    StudyCardNavButton(
                        onClick = onNext,
                        icon = { Icon(Icons.Default.KeyboardArrowRight, getText(R.string.next)) },
                        containerColor = navButtonContainerColor,
                        enabled = showFrontNavigation
                    )
                }
            }
        }
        if (fullScreenNote != null) {
            FullScreenMediaViewerDialog(
                note = fullScreenNote!!,
                onDismiss = { fullScreenNote = null }
            )
        }
    }
}

/**
 * The content of the card prompt area in Quiz mode.
 * @param state The current study state.
 * @param viewModel The ViewModel providing business logic.
 */
// [Update QuizCardContent for Compact Mode support]
@Composable
fun QuizCardContent(
    state: StudyState,
    viewModel: FlashcardViewModel,
    modifier: Modifier = Modifier.fillMaxWidth().aspectRatio(1.6f),
    showNavigation: Boolean = true, // Parameter kept for compatibility, but ignored for nav logic
    showIndex: Boolean = true,
    tags: List<TagDefinition> = emptyList(),
    overrideSide: CardSide? = null,
    overrideCardIndex: Int? = null,
    completelyHideNav: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current
    val currentIndex = overrideCardIndex ?: state.currentCardIndex
    val card = state.shuffledCards[currentIndex]
    val effectiveSide = overrideSide ?: state.quizPromptSide
    val promptText = if (effectiveSide == CardSide.FRONT) card.front else card.back
    val promptRichText = if (effectiveSide == CardSide.FRONT) card.frontRichText else card.backRichText
    val promptNotes = if (effectiveSide == CardSide.FRONT) card.frontNotes else card.backNotes

    CommonFlashcard(
        frontText = promptText,
        isFrontRichText = promptRichText?.isNotBlank() == true,
        backText = "", // Not used in Quiz mode usually
        frontNotes = promptNotes,
        isFlipped = false, // Always show front
        onFlip = { /* Disable flip in Quiz mode if desired */ },

        // Respect showNavigation flag while dynamically enabling/disabling them using Quiz-specific rules
        showBackNavigation = showNavigation && currentIndex > 0,
        showFrontNavigation = showNavigation && (currentIndex < state.furthestCardIndex || (state.correctAnswerFound && currentIndex < state.shuffledCards.size - 1)),

        showIndex = showIndex,
        onPrevious = { viewModel.previousCard() },
        onNext = { viewModel.nextCard() },
        modifier = modifier,
        tags = tags,
        // Override colors to match Quiz styling (e.g., secondary container for Back prompts)
        containerColorFront = if (effectiveSide == CardSide.BACK) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColorFront = if (effectiveSide == CardSide.BACK) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        cardIndex = currentIndex,
        totalCards = state.shuffledCards.size,
        sessionId = state.sessionId,
        completelyHideNavigation = completelyHideNav
    )
}

@Composable
fun AnimatedHamburgerMenu(
    viewModel: FlashcardViewModel,
    windowWidthSizeClass: WindowWidthSizeClass,
    modifier: Modifier = Modifier
) {
    val isWideScreen = windowWidthSizeClass != WindowWidthSizeClass.Compact
    val isPersistentDrawerOpen by viewModel.isLargeScreenDrawerOpen.collectAsState()

    // Grab the drawer state provided by our NavGraph wrapper
    val drawerState = LocalDrawerState.current
    val scope = rememberCoroutineScope()

    // 1. Determine if it should be shown based on the screen size's source of truth
    val isDrawerVisuallyOpen = if (isWideScreen) {
        isPersistentDrawerOpen
    } else {
        drawerState?.isOpen == true
    }

    // 2. Display it with AnimatedVisibility
    AnimatedVisibility(
        visible = !isDrawerVisuallyOpen,
        enter = scaleIn() + fadeIn(),
        exit = scaleOut() + fadeOut(),
        modifier = modifier
    ) {
        IconButton(
            onClick = {
                // 3. Open the correct drawer depending on the device
                if (isWideScreen) {
                    viewModel.setLargeScreenDrawerOpen(true)
                } else {
                    scope.launch { drawerState?.open() }
                }
            }
        ) {
            Icon(
                imageVector = Icons.Default.Menu,
                contentDescription = "Open Navigation Menu",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MediaThumbnail(note: NoteField, onClick: () -> Unit, contentColor: Color) {
    val dimensions = LocalStudiareDimensions.current
    Surface(
        modifier = Modifier.size(64.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        color = contentColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
    ) {
        when (note.type) {
            MediaType.IMAGE -> {
                AsyncImage(
                    model = note.content,
                    contentDescription = note.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            MediaType.VIDEO, MediaType.AUDIO -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayArrow, contentDescription = note.name, tint = contentColor)
                        Text(if (note.type == MediaType.VIDEO) "Video" else "Audio", style = MaterialTheme.typography.labelSmall, color = contentColor)
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun FullScreenMediaViewerDialog(note: NoteField, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Close button
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }

            // Content
            Box(modifier = Modifier.fillMaxSize().padding(top = 64.dp, bottom = 64.dp), contentAlignment = Alignment.Center) {
                when (note.type) {
                    MediaType.IMAGE -> {
                        AsyncImage(
                            model = note.content,
                            contentDescription = note.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    MediaType.VIDEO -> {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { context ->
                                android.widget.VideoView(context).apply {
                                    setVideoPath(note.content)
                                    val mediaController = android.widget.MediaController(context)
                                    mediaController.setAnchorView(this)
                                    setMediaController(mediaController)
                                    start()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    MediaType.AUDIO -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(100.dp))
                            Spacer(Modifier.height(32.dp))
                            var isPlaying by remember { mutableStateOf(false) }
                            val mediaPlayer = remember { android.media.MediaPlayer() }

                            DisposableEffect(note.content) {
                                try {
                                    mediaPlayer.setDataSource(note.content)
                                    mediaPlayer.prepare()
                                } catch (e: Exception) { e.printStackTrace() }
                                onDispose { mediaPlayer.release() }
                            }

                            IconButton(
                                onClick = {
                                    if (mediaPlayer.isPlaying) {
                                        mediaPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        mediaPlayer.start()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }

            // Label
            Text(
                text = note.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
            )
        }
    }
}

@Composable
fun BreadcrumbsBar(
    currentDeck: Deck,
    allDecks: List<Deck>,
    onNavigateHome: () -> Unit,
    onNavigateToDeck: (String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val path = remember(currentDeck, allDecks) {
        val list = mutableListOf<Deck>()
        var current: Deck? = currentDeck
        while (current != null) {
            list.add(0, current)
            current = allDecks.find { it.id == current!!.parentDeckId }
        }
        list
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = dimensions.paddingMedium, vertical = 4.dp)
        ) {
            // Home Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                    .clickable { onNavigateHome() }
                    .padding(horizontal = dimensions.paddingSmall, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = "Home",
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            path.forEachIndexed { index, deck ->
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val isLast = index == path.lastIndex
                val textColor = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .clickable(enabled = !isLast) { onNavigateToDeck(deck.id) }
                        .padding(horizontal = dimensions.paddingSmall, vertical = 4.dp)
                ) {
                    Text(
                        text = deck.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        fontWeight = fontWeight,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

class ShortcutRegistry {
    private val actions = mutableMapOf<Key, () -> Unit>()

    fun register(key: Key, action: () -> Unit) {
        actions[key] = action
    }

    fun unregister(key: Key) {
        actions.remove(key)
    }

    fun trigger(key: Key): Boolean {
        val action = actions[key]
        if (action != null) {
            action()
            return true
        }
        return false
    }
}

val LocalHintMode = compositionLocalOf { false }
val LocalShortcutRegistry = compositionLocalOf<ShortcutRegistry?> { null }

fun Modifier.withShortcut(
    key: Key,
    keyLabel: String,
    action: () -> Unit
): Modifier = composed {
    val registry = LocalShortcutRegistry.current
    val isHintMode = LocalHintMode.current
    val textMeasurer = rememberTextMeasurer()

    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }

    var positionInWindow by remember { mutableStateOf(Offset.Zero) }

    DisposableEffect(key, registry) {
        registry?.register(key, action)
        onDispose { registry?.unregister(key) }
    }

    this.onGloballyPositioned { coordinates ->
            positionInWindow = coordinates.localToWindow(Offset.Zero)
        }
        .drawWithContent {
            drawContent()
            if (isHintMode) {
                val style = TextStyle(
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                val textLayoutResult = textMeasurer.measure(keyLabel, style)

                val badgeWidth = textLayoutResult.size.width + 32.dp.toPx()
                val badgeHeight = textLayoutResult.size.height + 16.dp.toPx()

                // Try default top-right hover position
                var offsetX = size.width - (badgeWidth / 2f)
                var offsetY = -(badgeHeight / 2f)

                // Calculate where that would put the badge absolutely on the screen
                val absX = positionInWindow.x + offsetX
                val absY = positionInWindow.y + offsetY

                // Check if the default position would be cut off by the screen edges
                val isClippedByScreen = absX < 0f || absY < 0f ||
                        (absX + badgeWidth) > screenWidthPx ||
                        (absY + badgeHeight) > screenHeightPx

                if (isClippedByScreen) {
                    // Fallback: Perfectly center the badge inside the component so it avoids clipping
                    offsetX = (size.width - badgeWidth) / 2f
                    offsetY = (size.height - badgeHeight) / 2f
                }

                drawRoundRect(
                    color = Color.Black.copy(alpha = 0.85f),
                    topLeft = Offset(offsetX, offsetY),
                    size = Size(badgeWidth, badgeHeight),
                    cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = keyLabel,
                    style = style,
                    topLeft = Offset(offsetX + 16.dp.toPx(), offsetY + 8.dp.toPx())
                )
            }
        }

}
