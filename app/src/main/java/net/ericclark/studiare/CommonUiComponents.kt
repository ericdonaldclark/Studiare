package net.ericclark.studiare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import net.ericclark.studiare.data.TagDefinition

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
    val dimensions = LocalStudiareDimensions.current

    // M3 Expressive often uses a SurfaceContainer or slightly distinct background
    Surface(
        modifier = modifier.fillMaxWidth(),
        shadowElevation = dimensions.cardElevation, // Dynamic elevation
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(WindowInsets.statusBars.asPaddingValues())
                .height(64.dp) // Standard M3 height, could be increased for Expressive Large/Medium
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = dimensions.paddingSmall, end = dimensions.paddingLarge),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    navigationIcon()
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = dimensions.paddingMedium)
                ) {
                    ProvideTextStyle(value = MaterialTheme.typography.titleLarge) {
                        title()
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(
                        dimensions.spacingSmall,
                        Alignment.End
                    )
                ) {
                    actions()
                }
            }
        }
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
    modifier: Modifier = Modifier
) {
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
        modifier = modifier,
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
    val icon = if (isKnown) Icons.Filled.Check else Icons.Default.Check
    // Use Primary Container for active state in M3 Expressive
    val colors = if (isKnown) {
        ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    } else {
        ButtonDefaults.outlinedButtonColors()
    }
    val border = if (isKnown) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline)

    OutlinedButton(
        onClick = onClick,
        shape = CircleShape, // Expressive often uses full rounded shapes (stadium/circle)
        modifier = Modifier.size(44.dp), // Slightly larger touch target
        contentPadding = PaddingValues(0.dp),
        colors = colors,
        border = border
    ) {
        Icon(icon, contentDescription = if (isKnown) "Mark as not known" else "Mark as known")
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
    difficulty: Int,
    onDifficultyChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    Column(modifier = modifier.padding(vertical = dimensions.paddingSmall)) {
        Text(text = "$label: $difficulty", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = difficulty.toFloat(),
            onValueChange = { onDifficultyChange(it.roundToInt()) },
            valueRange = 1f..5f,
            steps = 3
        )
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonText: String = "Confirm"
) {
    val dimensions = LocalStudiareDimensions.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(text) },
        shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {
            Button(onClick = onConfirm) { Text(confirmButtonText) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun SortModeDialogSection(
    sortMode: String, onSortModeChange: (String) -> Unit,
    sortDirection: String, onSortDirectionChange: (String) -> Unit,
    sortSide: String, onSortSideChange: (String) -> Unit,
    sortExpanded: Boolean, onToggleExpand: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(
        title = "Sort & Priority",
        subtitle = if (sortMode == RANDOM) RANDOM else "$sortMode ($sortDirection)",
        isExpanded = sortExpanded,
        onToggle = onToggleExpand
    ) {
        Column {
            ToggleButton(
                text = RANDOM,
                isSelected = sortMode == RANDOM,
                onClick = { onSortModeChange(RANDOM) },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(dimensions.spacingSmall))

            val sortOptions = listOf(
                ALPHABETICAL,
                REVIEW_DATE,
                INCORRECT_DATE,
                REVIEW_COUNT,
                CARD_ORDER,
                SCORE
            )
            val chunkedOptions = sortOptions.chunked(2)
            chunkedOptions.forEach { rowOptions ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                ) {
                    rowOptions.forEach { option ->
                        ToggleButton(
                            text = option,
                            isSelected = sortMode == option,
                            onClick = { onSortModeChange(option) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowOptions.size < 2) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                Spacer(modifier = Modifier.height(dimensions.spacingSmall))
            }

            if (sortMode != RANDOM) {
                Spacer(Modifier.height(dimensions.spacingSmall))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Ascending", style = MaterialTheme.typography.bodySmall)
                    Switch(
                        checked = sortDirection == "DESC",
                        onCheckedChange = { onSortDirectionChange(if (it) "DESC" else "ASC") },
                        modifier = Modifier.padding(horizontal = dimensions.paddingSmall)
                    )
                    Text("Descending", style = MaterialTheme.typography.bodySmall)
                }
                if (sortMode == ALPHABETICAL) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Front Side", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = sortSide == "Back",
                            onCheckedChange = { onSortSideChange(if (it) "Back" else "Front") },
                            modifier = Modifier.padding(horizontal = dimensions.paddingSmall)
                        )
                        Text("Back Side", style = MaterialTheme.typography.bodySmall)
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
    label: String = "Number of Cards"
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(
        title = label,
        subtitle = "$numberOfCards of $availableCardsCount",
        isExpanded = isExpanded,
        onToggle = { onToggle(!isExpanded) }
    ) {
        Text("Count: $numberOfCards", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = dimensions.paddingSmall))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall)
        ) {
            FilledTonalIconButton(onClick = { if (numberOfCards > 1) onValueChange(numberOfCards - 1) }, enabled = numberOfCards > 1) { Icon(Icons.Default.Remove, "Decrease") }
            Spacer(Modifier.width(dimensions.spacingSmall))

            Box(
                modifier = Modifier
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(dimensions.cornerRadiusMedium))
                    .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
            ) {
                Text(if (availableCardsCount == 0) "0" else numberOfCards.toString(), fontSize = 20.sp)
            }

            Spacer(Modifier.width(dimensions.spacingSmall))
            FilledTonalIconButton(onClick = { if (numberOfCards < availableCardsCount) onValueChange(numberOfCards + 1) }, enabled = numberOfCards < availableCardsCount) { Icon(Icons.Default.Add, "Increase") }
        }
        Slider(
            value = numberOfCards.toFloat(),
            onValueChange = { onValueChange(it.roundToInt()) },
            valueRange = 1f..availableCardsCount.toFloat().coerceAtLeast(1f),
            steps = (availableCardsCount - 2).coerceAtLeast(0)
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
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            if (isCollapsible) {
                Icon(
                    imageVector = if (isExpanded!!) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "Collapse" else "Expand"
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
                IconButton(onClick = { onNotesTextChange("") }) {
                    Icon(Icons.Default.Add, contentDescription = "Add Note")
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
                IconButton(onClick = { onNotesTextChange(null) }) {
                    Icon(Icons.Default.Clear, contentDescription = "Remove Note")
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
        ANY -> "All available cards"
        TAGS -> "${state.selectedTags.size} tags selected"
        DIFFICULTY -> "Diff: ${state.selectedDifficulties.sorted().joinToString()}"
        ALPHABET -> "${state.filterSide}: ${state.alphabetStart} - ${state.alphabetEnd}"
        CARD_ORDER -> "Cards ${state.cardOrderStart} - ${state.cardOrderEnd}"
        REVIEW_DATE, INCORRECT_DATE -> "${state.filterType} within ${state.timeValue} ${state.timeUnit}"
        REVIEW_COUNT -> "${state.reviewDirection}: ${state.reviewThreshold} reviews"
        SCORE -> "${state.scoreDirection}: ${state.scoreThreshold}%"
        else -> ""
    }

    DialogSection(
        title = "Selection Mode",
        subtitle = "$subtitle ${if (state.excludeKnown) "(No Known)" else ""}",
        isExpanded = isExpanded,
        onToggle = onToggleExpand
    ) {
        Column {
            val selectionOptions = listOf(
                ANY,
                DIFFICULTY,
                TAGS,
                ALPHABET,
                CARD_ORDER,
                REVIEW_DATE,
                INCORRECT_DATE,
                REVIEW_COUNT,
                SCORE
            )
            val chunkedSelection = selectionOptions.chunked(2)

            chunkedSelection.forEach { rowOptions ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    rowOptions.forEach { option ->
                        val isEnabled = if (option == REVIEW_COUNT) state.maxDeckReviews > 0 else true
                        ToggleButton(
                            text = option,
                            isSelected = state.selectionMode == option,
                            enabled = isEnabled,
                            onClick = {
                                actions.onModeChange(option)
                                // Defaults logic
                                if (option == REVIEW_DATE) actions.onFilterTypeChange("Exclude")
                                if (option == INCORRECT_DATE) actions.onFilterTypeChange("Include")
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (rowOptions.size < 2) {
                        Spacer(Modifier.weight(1f))
                    }
                }
                Spacer(Modifier.height(dimensions.spacingSmall))
            }

            Spacer(Modifier.height(dimensions.spacingSmall))

            when (state.selectionMode) {
                ANY -> Text("Selects from all cards in the deck.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                ALPHABET -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = state.alphabetStart,
                            onValueChange = { if (it.length <= 1) actions.onAlphabetStartChange(it.uppercase()) },
                            label = { Text("From") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                        Text("-", fontWeight = FontWeight.Bold)
                        OutlinedTextField(
                            value = state.alphabetEnd,
                            onValueChange = { if (it.length <= 1) actions.onAlphabetEndChange(it.uppercase()) },
                            label = { Text("To") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters)
                        )
                    }
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Front Side", style = MaterialTheme.typography.bodySmall)
                        Switch(
                            checked = state.filterSide == "Back",
                            onCheckedChange = { actions.onFilterSideChange(if (it) "Back" else "Front") },
                            modifier = Modifier.padding(horizontal = dimensions.paddingSmall)
                        )
                        Text("Back Side", style = MaterialTheme.typography.bodySmall)
                    }
                }

                CARD_ORDER -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Start Card: ${state.cardOrderStart}", style = MaterialTheme.typography.labelSmall)
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
                            Text("End Card: ${state.cardOrderEnd}", style = MaterialTheme.typography.labelSmall)
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

                REVIEW_DATE, INCORRECT_DATE -> {
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
                                    Text(state.timeUnit)
                                    Spacer(Modifier.weight(1f))
                                    Icon(Icons.Default.ArrowDropDown, null)
                                }
                                DropdownMenu(
                                    expanded = isUnitDropdownExpanded,
                                    onDismissRequest = { isUnitDropdownExpanded = false }
                                ) {
                                    listOf("Days", "Weeks", "Months", "Years").forEach { unit ->
                                        DropdownMenuItem(
                                            text = { Text(unit) },
                                            onClick = { actions.onTimeUnitChange(unit); isUnitDropdownExpanded = false }
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(dimensions.spacingMedium))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Include", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = state.filterType == "Exclude",
                                onCheckedChange = { actions.onFilterTypeChange(if (it) "Exclude" else "Include") },
                                modifier = Modifier.padding(horizontal = dimensions.paddingSmall)
                            )
                            Text("Exclude", style = MaterialTheme.typography.bodySmall)
                        }

                        Spacer(Modifier.height(dimensions.spacingSmall))
                        Text(
                            text = if (state.selectionMode == REVIEW_DATE) {
                                if (state.filterType == "Exclude") "Selects cards NOT reviewed in the last ${state.timeValue} ${state.timeUnit}."
                                else "Selects cards reviewed within the last ${state.timeValue} ${state.timeUnit}."
                            } else {
                                if (state.filterType == "Exclude") "Selects cards NOT incorrect in the last ${state.timeValue} ${state.timeUnit}."
                                else "Selects cards incorrect within the last ${state.timeValue} ${state.timeUnit}."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                REVIEW_COUNT -> {
                    Column {
                        val sliderColors = if (state.reviewDirection == "Minimum") {
                            SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary
                            )
                        } else SliderDefaults.colors()

                        Text("Reviews: ${state.reviewThreshold}", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.reviewThreshold.toFloat(),
                            onValueChange = { actions.onReviewThresholdChange(it.roundToInt()) },
                            valueRange = 0f..state.maxDeckReviews.toFloat(),
                            steps = 0,
                            colors = sliderColors
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Minimum", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = state.reviewDirection == "Maximum",
                                onCheckedChange = { actions.onReviewDirectionChange(if (it) "Maximum" else "Minimum") },
                                modifier = Modifier.padding(horizontal = dimensions.paddingSmall)
                            )
                            Text("Maximum", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                SCORE -> {
                    Column {
                        val sliderColors = if (state.scoreDirection == "Minimum") {
                            SliderDefaults.colors(
                                activeTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                                inactiveTrackColor = MaterialTheme.colorScheme.primary
                            )
                        } else SliderDefaults.colors()

                        Text("Score: ${state.scoreThreshold}%", style = MaterialTheme.typography.labelMedium)
                        Slider(
                            value = state.scoreThreshold.toFloat(),
                            onValueChange = { actions.onScoreThresholdChange(it.roundToInt()) },
                            valueRange = 0f..100f,
                            steps = 0,
                            colors = sliderColors
                        )

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Minimum", style = MaterialTheme.typography.bodySmall)
                            Switch(
                                checked = state.scoreDirection == "Maximum",
                                onCheckedChange = { actions.onScoreDirectionChange(if (it) "Maximum" else "Minimum") },
                                modifier = Modifier.padding(horizontal = dimensions.paddingSmall)
                            )
                            Text("Maximum", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }

                TAGS -> {
                    if (state.availableTags.isEmpty()) {
                        Text("No tags found in this deck.", color = MaterialTheme.colorScheme.error)
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

                DIFFICULTY -> {
                    Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                        (1..5).forEach { diff ->
                            val isSelected = diff in state.selectedDifficulties
                            OutlinedButton(
                                onClick = {
                                    val newDiffs = state.selectedDifficulties.toMutableList()
                                    if (isSelected) {
                                        if (newDiffs.size > 1) newDiffs.remove(diff)
                                    } else newDiffs.add(diff)
                                    actions.onDifficultiesChange(newDiffs)
                                },
                                colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
                                border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder,
                                shape = CircleShape,
                                contentPadding = PaddingValues(0.dp),
                                modifier = Modifier.size(48.dp) // M3 target size
                            ) { Text(diff.toString()) }
                        }
                    }
                }
            }
            Spacer(Modifier.height(dimensions.spacingLarge))
            // Global Exclude
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Exclude Known Cards", modifier = Modifier.weight(1f))
                Switch(checked = state.excludeKnown, onCheckedChange = actions.onExcludeKnownChange)
            }
            Text("Available Pool: ${state.availableCardsCount} cards", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
fun ToggleButton(text: String, isSelected: Boolean, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val dimensions = LocalStudiareDimensions.current
    val colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors()
    val border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder
    val containerColor = if (!enabled) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else colors.containerColor
    val contentColor = if (!enabled) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else colors.contentColor

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
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = if (isSelected) ButtonDefaults.buttonColors() else ButtonDefaults.outlinedButtonColors(),
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
@Composable
fun CommonFlashcard(
    frontText: String,
    backText: String,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    frontNotes: String? = null,
    backNotes: String? = null,
    showBackNavigation: Boolean = false,
    showFrontNavigation: Boolean = false,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    tags: List<TagDefinition> = emptyList(),
    containerColorFront: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColorFront: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColorBack: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColorBack: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    val dimensions = LocalStudiareDimensions.current

    // Expressive 3D Flip Animation
    val rotation by animateFloatAsState(
        targetValue = if (isFlipped) 180f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            easing = FastOutSlowInEasing
        ),
        label = "cardFlip"
    )

    // Determine which side is visible based on rotation
    val isBackVisible = rotation > 90f

    // Switch colors based on visible side
    val containerColor = if (isBackVisible) containerColorBack else containerColorFront
    val contentColor = if (isBackVisible) contentColorBack else contentColorFront

    val navButtonContainerColor = if (isBackVisible) containerColorFront else containerColorBack
    val navButtonContentColor = if (isBackVisible) contentColorFront else contentColorBack

    // Base Container
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimensions.cornerRadiusLarge))
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 12f * density // Adds depth perspective
            }
            .background(containerColor)
            .clickable { onFlip() },
        contentAlignment = Alignment.Center
    ) {
        // Content Wrapper
        // We must counteract the rotation when showing the back so text isn't mirrored
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensions.paddingLarge)
                .graphicsLayer {
                    if (isBackVisible) {
                        rotationY = 180f
                    }
                }
        ) {
            // 1. MAIN CONTENT (Centered)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    // Add bottom padding if tags exist so text doesn't overlap them
                    .padding(bottom = if (tags.isNotEmpty()) 32.dp else 0.dp)
            ) {
                // Main Text
                Text(
                    text = if (isBackVisible) backText else frontText,
                    style = MaterialTheme.typography.headlineMedium,
                    textAlign = TextAlign.Center,
                    color = contentColor
                )

                // Notes
                val currentNotes = if (isBackVisible) backNotes else frontNotes
                if (!currentNotes.isNullOrBlank()) {
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Text(
                        text = "($currentNotes)",
                        style = MaterialTheme.typography.bodyLarge,
                        fontStyle = FontStyle.Italic,
                        textAlign = TextAlign.Center,
                        color = contentColor.copy(alpha = 0.8f)
                    )
                }
            }

            // 2. TAGS (Bottom Left Row with color)
            if (tags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    tags.forEach { tag ->
                        val chipColor = parseHexColor(tag.color)
                        // Calculate a contrasting text color (white or black)
                        // Simple check: default to white for colored tags
                        val chipTextColor = Color.White

                        Surface(
                            shape = CircleShape,
                            color = chipColor, // Use the tag's specific color
                            contentColor = chipTextColor
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

        // Navigation Buttons (Overlay)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isBackVisible) rotationY = 180f
                }
        ) {
            if (showBackNavigation)
            {
                // Previous Button
                Box(modifier = Modifier.align(Alignment.CenterStart).padding(dimensions.paddingSmall)) {
                    StudyCardNavButton(
                        onClick = onPrevious,
                        icon = { Icon(Icons.Default.KeyboardArrowLeft, "Previous") },
                        containerColor = navButtonContainerColor
                    )
                }
            }

            if (showFrontNavigation)
            {
                // Next Button
                Box(modifier = Modifier.align(Alignment.CenterEnd).padding(dimensions.paddingSmall)) {
                    StudyCardNavButton(
                        onClick = onNext,
                        icon = { Icon(Icons.Default.KeyboardArrowRight, "Next") },
                        containerColor = navButtonContainerColor
                    )
                }
            }
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
    showNavigation: Boolean = true,
    tags: List<TagDefinition> = emptyList(),
    isCompact: Boolean = false // ADDED: Toggle for Hangman sizing
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    val promptText = if (state.quizPromptSide == "Front") card.front else card.back
    val promptNotes = if (state.quizPromptSide == "Front") card.frontNotes else card.backNotes


    CommonFlashcard(
        frontText = promptText,
        backText = "", // Not used in Quiz mode usually
        frontNotes = promptNotes,
        isFlipped = false, // Always show front
        onFlip = { /* Disable flip in Quiz mode if desired */ },
        showBackNavigation = state.currentCardIndex != 0,
        showFrontNavigation = state.currentCardIndex != state.shuffledCards.size -1,
        onPrevious = { viewModel.previousCard() },
        onNext = { viewModel.nextCard() },
        modifier = modifier,
        tags = tags,
        // Override colors to match Quiz styling (e.g., secondary container for Back prompts)
        containerColorFront = if (state.quizPromptSide == "Back") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColorFront = if (state.quizPromptSide == "Back") MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
    )

    if (showNavigation) {
        Spacer(Modifier.height(dimensions.spacingMedium))
        Text("${state.currentCardIndex + 1} / ${state.shuffledCards.size}")
    }
}