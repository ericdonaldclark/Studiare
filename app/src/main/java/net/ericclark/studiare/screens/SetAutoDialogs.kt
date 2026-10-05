package net.ericclark.studiare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.*

@Composable
fun AutomaticSetCreatorDialog(
    parentDeck: DeckWithCards,
    availableTags: List<String>,
    allTagDefinitions: List<TagDefinition>,
    onDismiss: () -> Unit,
    onCreate: (config: AutoSetConfig) -> Unit,
    onPickStartCard: (config: AutoSetConfig) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    // --- State ---
    var setMode by rememberSaveable { mutableStateOf(AutoSetCreationMode.ONE) }

    // Configuration
    var numSets by rememberSaveable { mutableIntStateOf(3) }
    var maxCardsPerSet by rememberSaveable { mutableIntStateOf(25) }

    // Selection State
    var selectionMode by rememberSaveable { mutableStateOf(SelectionMode.ANY) }
    var selectedTags by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val selectedDifficulties = remember {
        mutableStateListOf(*DifficultySetting.entries.map { it.value }.toTypedArray())
    }
    var excludeKnown by rememberSaveable { mutableStateOf(true) }

    // Alphabet State
    var alphabetStart by rememberSaveable { mutableStateOf("A") }
    var alphabetEnd by rememberSaveable { mutableStateOf("Z") }
    var filterSide by rememberSaveable { mutableStateOf(CardSide.FRONT) }

    // Card Order Range State
    val totalCards = parentDeck.cards.size
    var cardOrderStart by rememberSaveable { mutableIntStateOf(1) }
    var cardOrderEnd by rememberSaveable { mutableIntStateOf(if (totalCards > 0) totalCards else 1) }

    // Time Filter State
    var timeValue by rememberSaveable { mutableIntStateOf(7) }
    var timeUnit by rememberSaveable { mutableStateOf(TimeUnit.DAYS) }
    var filterType by rememberSaveable { mutableStateOf(FilterType.EXCLUDE) }

    // Score & Review Count State
    val maxDeckReviews = remember(parentDeck) { parentDeck.cards.maxOfOrNull { it.reviewedCount } ?: 0 }
    var reviewThreshold by rememberSaveable { mutableIntStateOf(0) }
    var reviewDirection by rememberSaveable { mutableStateOf(Direction.ASC) }

    var scoreThreshold by rememberSaveable { mutableIntStateOf(0) }
    var scoreDirection by rememberSaveable { mutableStateOf(Direction.ASC) }

    // Sorting
    var sortMode by rememberSaveable { mutableStateOf(SortMode.RANDOM) }
    var sortDirection by rememberSaveable { mutableStateOf(Direction.ASC) }
    var sortSide by rememberSaveable { mutableStateOf(CardSide.FRONT) }

    // Expansion States
    var selectionExpanded by rememberSaveable { mutableStateOf(false) }
    var sortExpanded by rememberSaveable { mutableStateOf(false) }
    var sizeExpanded by rememberSaveable { mutableStateOf(true) }

    // --- Dynamic Pool Calculation ---
    val availableCardsCount = remember(
        parentDeck, selectionMode, selectedTags, selectedDifficulties.toList(),
        excludeKnown, alphabetStart, alphabetEnd, filterSide, cardOrderStart, cardOrderEnd,
        timeValue, timeUnit, filterType,
        reviewThreshold, reviewDirection, scoreThreshold, scoreDirection
    ) {
        var pool = parentDeck.cards
        if (excludeKnown) pool = pool.filter { !it.isKnown }

        val timeMultiplier = when (timeUnit) {
            TimeUnit.DAYS -> 24 * 60 * 60 * 1000L
            TimeUnit.WEEKS -> 7 * 24 * 60 * 60 * 1000L
            TimeUnit.MONTHS -> 30 * 24 * 60 * 60 * 1000L
            TimeUnit.YEARS -> 365 * 24 * 60 * 60 * 1000L
        }
        val cutoffTime = System.currentTimeMillis() - (timeValue * timeMultiplier)

        pool = when (selectionMode) {
            SelectionMode.DIFFICULTY -> pool.filter { it.difficulty.value in selectedDifficulties }
            SelectionMode.TAGS -> pool.filter { card -> card.tags.any { it in selectedTags } }
            SelectionMode.ALPHABET -> {
                val start = alphabetStart.uppercase()
                val end = alphabetEnd.uppercase()
                pool.filter { card ->
                    val text = if (filterSide == CardSide.FRONT) card.front else card.back
                    val firstChar = text.trim().uppercase(java.util.Locale.getDefault()).firstOrNull()?.toString()
                    firstChar != null && firstChar >= start && firstChar <= end
                }
            }
            SelectionMode.CARD_ORDER -> {
                val s = (cardOrderStart - 1).coerceAtLeast(0)
                val e = (cardOrderEnd - 1).coerceAtMost(parentDeck.cards.size - 1)
                if (s <= e && parentDeck.cards.isNotEmpty()) {
                    val allowedIds = parentDeck.cards.slice(s..e).map { it.id }.toSet()
                    pool.filter { it.id in allowedIds }
                } else {
                    emptyList()
                }
            }
            SelectionMode.REVIEW_DATE -> {
                if (filterType == FilterType.INCLUDE) pool.filter { it.reviewedAt != null && it.reviewedAt >= cutoffTime }
                else pool.filter { it.reviewedAt == null || it.reviewedAt < cutoffTime }
            }
            SelectionMode.INCORRECT_DATE -> {
                if (filterType == FilterType.INCLUDE) pool.filter { card -> card.incorrectAttempts.maxOrNull()?.let { last -> last >= cutoffTime } == true }
                else pool.filter { card -> card.incorrectAttempts.isEmpty() || card.incorrectAttempts.maxOrNull()!! < cutoffTime }
            }
            SelectionMode.REVIEW_COUNT -> {
                if (reviewDirection == Direction.DESC) pool.filter { it.reviewedCount <= reviewThreshold }
                else pool.filter { it.reviewedCount >= reviewThreshold }
            }
            SelectionMode.SCORE -> {
                val getScore: (Card) -> Float = { card ->
                    val total = card.gradedAttempts.size
                    if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                }
                val threshold = scoreThreshold.toFloat() / 100f
                if (scoreDirection == Direction.DESC) pool.filter { getScore(it) <= threshold }
                else pool.filter { getScore(it) >= threshold }
            }
            else -> pool
        }
        pool.size
    }

    // Helper to gather current config
    val currentConfig = AutoSetConfig(
        mode = setMode,
        numSets = numSets,
        maxCardsPerSet = maxCardsPerSet,
        selectionMode = selectionMode,
        selectedTags = selectedTags,
        selectedDifficulties = selectedDifficulties.toList(),
        excludeKnown = excludeKnown,
        sortMode = sortMode,
        sortDirection = sortDirection,
        sortSide = sortSide,
        alphabetStart = alphabetStart,
        alphabetEnd = alphabetEnd,
        filterSide = filterSide,
        cardOrderStart = cardOrderStart,
        cardOrderEnd = cardOrderEnd,
        timeValue = timeValue,
        timeUnit = timeUnit,
        filterType = filterType,
        reviewCountThreshold = reviewThreshold,
        reviewCountDirection = reviewDirection,
        scoreThreshold = scoreThreshold,
        scoreDirection = scoreDirection
    )

    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.fillMaxHeight(0.9f).fillMaxWidth(0.9f),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = getText(R.string.filter_and_sort),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                // 1. Top Slider Section
                TopSliderDialogSection(
                    options = AutoSetCreationMode.entries.map { it.asString() }, // <--- Pass specific options
                    selectedMode = setMode.asString(),
                    onModeChange = { setMode = it.toAutoSetCreationMode() }
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                // 2. Selection Mode Section
                val selectionState =
                    SelectionSectionState(
                        selectionMode = selectionMode,
                        selectedTags = selectedTags,
                        selectedDifficulties = selectedDifficulties,
                        excludeKnown = excludeKnown,
                        alphabetStart = alphabetStart,
                        alphabetEnd = alphabetEnd,
                        filterSide = filterSide,
                        cardOrderStart = cardOrderStart,
                        cardOrderEnd = cardOrderEnd,
                        timeValue = timeValue,
                        timeUnit = timeUnit,
                        filterType = filterType,
                        reviewThreshold = reviewThreshold,
                        reviewDirection = reviewDirection,
                        scoreThreshold = scoreThreshold,
                        scoreDirection = scoreDirection,
                        availableTags = availableTags,
                        allTagDefinitions = allTagDefinitions,
                        availableCardsCount = availableCardsCount,
                        totalCards = totalCards,
                        maxDeckReviews = maxDeckReviews
                    )

                val selectionActions =
                    SelectionSectionActions(
                        onModeChange = { selectionMode = it },
                        onTagsChange = { selectedTags = it },
                        onDifficultiesChange = { diffs ->
                            selectedDifficulties.clear()
                            selectedDifficulties.addAll(diffs)
                        },
                        onExcludeKnownChange = { excludeKnown = it },
                        onAlphabetStartChange = { alphabetStart = it },
                        onAlphabetEndChange = { alphabetEnd = it },
                        onFilterSideChange = { filterSide = it },
                        onCardOrderStartChange = { cardOrderStart = it },
                        onCardOrderEndChange = { cardOrderEnd = it },
                        onTimeValueChange = { timeValue = it },
                        onTimeUnitChange = { timeUnit = it },
                        onFilterTypeChange = { filterType = it },
                        onReviewThresholdChange = { reviewThreshold = it },
                        onReviewDirectionChange = { reviewDirection = it },
                        onScoreThresholdChange = { scoreThreshold = it },
                        onScoreDirectionChange = { scoreDirection = it }
                    )

                SelectionModeDialogSection(
                    state = selectionState,
                    actions = selectionActions,
                    isExpanded = selectionExpanded,
                    onToggleExpand = { selectionExpanded = !selectionExpanded }
                )

                // 3. Sort Mode Section
                SortModeDialogSection(
                    sortMode = sortMode,
                    onSortModeChange = { sortMode = it },
                    sortDirection = sortDirection,
                    onSortDirectionChange = { sortDirection = it },
                    sortSide = sortSide,
                    onSortSideChange = { sortSide = it },
                    sortExpanded = sortExpanded,
                    onToggleExpand = { sortExpanded = !sortExpanded }
                )

                // 4. Quantities Section
                SetQuantitiesDialogSection(
                    setMode = setMode,
                    numSets = numSets,
                    onNumSetsChange = { numSets = it },
                    maxCardsPerSet = maxCardsPerSet,
                    onMaxCardsPerSetChange = { maxCardsPerSet = it },
                    sizeExpanded = sizeExpanded,
                    onToggleExpand = { sizeExpanded = !sizeExpanded },
                    availableCardsCount = availableCardsCount
                )

                Spacer(Modifier.height(dimensions.spacingLarge))

                // Pick Starting Card Button
                val pickInteractionSource = remember { MutableInteractionSource() }
                val isPickPressed by pickInteractionSource.collectIsPressedAsState()
                val pickScale by animateFloatAsState(
                    targetValue = if (isPickPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "pickSquish"
                )
                OutlinedButton(
                    onClick = { onPickStartCard(currentConfig) },
                    interactionSource = pickInteractionSource,
                    modifier = Modifier.fillMaxWidth().scale(pickScale),
                    enabled = availableCardsCount > 0,
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.pick_starting_card))
                }

            }

            // Pinned footer, same layout as the create study session dialog.
            Spacer(Modifier.height(dimensions.spacingSmall))
            val createInteractionSource = remember { MutableInteractionSource() }
            val isCreatePressed by createInteractionSource.collectIsPressedAsState()
            val createScale by animateFloatAsState(
                targetValue = if (isCreatePressed) 0.95f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "createSquish"
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                    Text(getText(R.string.cancel))
                }
                Spacer(modifier = Modifier.weight(1f))
                Button(
                    onClick = { onCreate(currentConfig) },
                    interactionSource = createInteractionSource,
                    modifier = Modifier.scale(createScale),
                    enabled = availableCardsCount > 0,
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.create_sets))
                }
            }
            }
        }
    }
}

@Composable
fun CardRangeSelectionDialog(
    sortedCards: List<Card>,
    viewModel: FlashcardViewModel,
    onDismiss: () -> Unit,
    onConfirm: (startCardId: String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var selectedStartCardId by rememberSaveable { mutableStateOf<String?>(null) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                CustomTopAppBar(
                    viewModel = viewModel,
                    screenId = ShortcutScreen.OTHER,
                    title = {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(getText(R.string.select_starting_card))
                        }
                    },
                    navigationIcon = {}, // Empty to help with centering
                    actions = {
                        TooltipIconButton(description = getText(R.string.close_capitalized), onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = getText(R.string.close_capitalized))
                        }
                    }
                )
            },
            bottomBar = {
                Surface(
                    shadowElevation = dimensions.cardElevation,
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Button(
                        onClick = { selectedStartCardId?.let { onConfirm(it) } },
                        enabled = selectedStartCardId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(dimensions.paddingMedium),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.confirm))
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).padding(dimensions.paddingMedium)) {
                Text(getText(R.string.select_start_card_desc),
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(dimensions.spacingMedium))
                LazyColumn(
                    modifier = Modifier
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                        .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                ) {
                    itemsIndexed(sortedCards, key = { _, card -> card.id }) { index, card ->
                        val backgroundColor = when {
                            card.id == selectedStartCardId -> MaterialTheme.colorScheme.primaryContainer
                            index % 2 != 0 -> MaterialTheme.colorScheme.surfaceContainerHigh
                            else -> Color.Transparent
                        }
                        val interactionSource = remember { MutableInteractionSource() }
                        val isPressed by interactionSource.collectIsPressedAsState()
                        val scale by animateFloatAsState(
                            targetValue = if (isPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "cardRowSquish"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .scale(scale)
                                .background(backgroundColor)
                                .clickable(
                                    interactionSource = interactionSource,
                                    indication = LocalIndication.current
                                ) { selectedStartCardId = card.id }
                                .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                card.front,
                                color = if (card.id == selectedStartCardId) MaterialTheme.colorScheme.onPrimaryContainer else LocalContentColor.current
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SetQuantitiesDialogSection(
    setMode: AutoSetCreationMode,
    numSets: Int, onNumSetsChange: (Int) -> Unit,
    maxCardsPerSet: Int, onMaxCardsPerSetChange: (Int) -> Unit,
    sizeExpanded: Boolean, onToggleExpand: () -> Unit,
    availableCardsCount: Int
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(
        title = getText(R.string.set_size),
        subtitle = if (setMode == AutoSetCreationMode.MULTIPLE) stringResource(R.string.sets_of_cards_format, numSets, maxCardsPerSet) else stringResource(R.string.max_cards_format, maxCardsPerSet),
        isExpanded = sizeExpanded,
        onToggle = { onToggleExpand() }
    ) {
        Column {
            // --- Dynamic Limit Calculations ---
            val maxCardsLimit = max(1, availableCardsCount).toFloat()
            val maxSetsLimit = if (maxCardsPerSet > 0) {
                kotlin.math.ceil(availableCardsCount.toDouble() / maxCardsPerSet).toFloat()
            } else 2f

            // Ensure valid state
            LaunchedEffect(maxCardsLimit) {
                if (maxCardsPerSet > maxCardsLimit) onMaxCardsPerSetChange(maxCardsLimit.toInt())
            }
            LaunchedEffect(maxSetsLimit) {
                if (numSets > maxSetsLimit) onNumSetsChange(maxSetsLimit.toInt().coerceAtLeast(2))
            }

            if (setMode == AutoSetCreationMode.MULTIPLE) {
                Text(stringResource(R.string.number_of_sets_format, numSets))
                val safeMaxSets = maxSetsLimit.coerceAtLeast(2f)
                Slider(
                    value = numSets.toFloat().coerceIn(2f, safeMaxSets),
                    onValueChange = { onNumSetsChange(it.roundToInt()) },
                    valueRange = 2f..safeMaxSets,
                    steps = (safeMaxSets.toInt() - 2 - 1).coerceAtLeast(0)
                )
                PresetChips(current = numSets, max = safeMaxSets.toInt(), min = 2, onSelect = onNumSetsChange)
            }

            CountPicker(
                label = if (setMode == AutoSetCreationMode.ONE) stringResource(R.string.cards_in_set_format, maxCardsPerSet) else stringResource(R.string.cards_per_set_format, maxCardsPerSet),
                value = maxCardsPerSet,
                max = availableCardsCount,
                onValueChange = onMaxCardsPerSetChange
            )

            // Estimation
            val totalCardsUsed = if (setMode == AutoSetCreationMode.ONE) maxCardsPerSet
            else if (setMode == AutoSetCreationMode.MULTIPLE) numSets * maxCardsPerSet
            else availableCardsCount
            val estimatedSets = if (setMode == AutoSetCreationMode.ONE) 1
            else if (setMode == AutoSetCreationMode.MULTIPLE) numSets
            else kotlin.math.ceil(availableCardsCount.toDouble() / maxCardsPerSet).toInt()

            Text(
                stringResource(R.string.result_sets_estimation, estimatedSets, min(totalCardsUsed, availableCardsCount)),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = dimensions.paddingSmall)
            )
        }
    }
}
