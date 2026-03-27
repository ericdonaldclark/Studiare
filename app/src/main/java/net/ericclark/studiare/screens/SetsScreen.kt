package net.ericclark.studiare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.*
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.layout.size

@Composable
fun SetManagerScreen(
    navController: NavController,
    parentDeck: DeckWithCards,
    sets: List<DeckWithCards>,
    viewModel: net.ericclark.studiare.FlashcardViewModel
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<DeckWithCards?>(null) }
    var showDeleteAllSetsDialog by remember { mutableStateOf(false) }
    var showAutoCreator by remember { mutableStateOf(false) }
    var showRangeSelector by remember { mutableStateOf<Pair<AutoSetConfig, List<Card>>?>(null) }
    var showManualCreateDialog by remember { mutableStateOf(false) }
    var setToEdit by remember { mutableStateOf<DeckWithCards?>(null) }

    val allTags by viewModel.tags.collectAsState()
    val parentDeckTags = remember(parentDeck) {
        parentDeck.cards.flatMap { it.tags }.distinct().sorted()
    }

    val spacingMode by viewModel.spacingMode.collectAsState()
    val animationMode by viewModel.animationMode.collectAsState()

    // Determine Dimensions based on ViewModel state
    val dimensions = when (spacingMode) {
        SpacingMode.COMPACT -> CompactDimensions
        SpacingMode.NORMAL -> NormalDimensions
        else -> ComfortableDimensions
    }

    // Provide these dimensions to all child composables
    CompositionLocalProvider(LocalStudiareDimensions provides dimensions) {

        if (showManualCreateDialog) {
            ManualSetCreatorDialog(
                parentDeck = parentDeck,
                viewModel = viewModel,
                onDismiss = { showManualCreateDialog = false }
            )
        }

        setToEdit?.let { aSet ->
            ManualSetEditorDialog(
                parentDeck = parentDeck,
                setForEditing = aSet,
                viewModel = viewModel,
                onDismiss = { setToEdit = null }
            )
        }

        if (showAutoCreator) {
            AutomaticSetCreatorDialog(
                parentDeck = parentDeck,
                availableTags = parentDeckTags,
                allTagDefinitions = allTags,
                onDismiss = { showAutoCreator = false },
                onCreate = { config ->
                    viewModel.createAutomaticSets(parentDeck, config)
                    showAutoCreator = false
                },
                onPickStartCard = { config ->
                    // ... (Logic remains identical)
                    var pool = parentDeck.cards
                    if (config.excludeKnown) pool = pool.filter { !it.isKnown }

                    val timeMultiplier = when (config.timeUnit) {
                        TimeUnit.DAYS -> 24 * 60 * 60 * 1000L
                        TimeUnit.WEEKS -> 7 * 24 * 60 * 60 * 1000L
                        TimeUnit.MONTHS -> 30 * 24 * 60 * 60 * 1000L
                        TimeUnit.YEARS -> 365 * 24 * 60 * 60 * 1000L
                    }
                    val cutoffTime = System.currentTimeMillis() - (config.timeValue * timeMultiplier)

                    pool = when (config.selectionMode) {
                        SelectionMode.DIFFICULTY -> pool.filter { it.difficulty.value in config.selectedDifficulties }
                        SelectionMode.TAGS -> pool.filter { card -> card.tags.any { it in config.selectedTags } }
                        SelectionMode.ALPHABET -> {
                            val start = config.alphabetStart.uppercase()
                            val end = config.alphabetEnd.uppercase()
                            pool.filter { card ->
                                val text = if (config.filterSide == CardSide.FRONT) card.front else card.back
                                val firstChar = text.trim().uppercase(java.util.Locale.getDefault()).firstOrNull()?.toString()
                                firstChar != null && firstChar >= start && firstChar <= end
                            }
                        }
                        SelectionMode.CARD_ORDER -> {
                            val s = (config.cardOrderStart - 1).coerceAtLeast(0)
                            val e = (config.cardOrderEnd - 1).coerceAtMost(parentDeck.cards.size - 1)
                            if (s <= e && parentDeck.cards.isNotEmpty()) {
                                val allowedIds = parentDeck.cards.slice(s..e).map { it.id }.toSet()
                                pool.filter { it.id in allowedIds }
                            } else emptyList()
                        }
                        SelectionMode.REVIEW_DATE -> {
                            if (config.filterType == FilterType.INCLUDE) pool.filter { it.reviewedAt != null && it.reviewedAt >= cutoffTime }
                            else pool.filter { it.reviewedAt == null || it.reviewedAt < cutoffTime }
                        }
                        SelectionMode.INCORRECT_DATE -> {
                            if (config.filterType == FilterType.INCLUDE) pool.filter { card -> card.incorrectAttempts.maxOrNull()?.let { last -> last >= cutoffTime } == true }
                            else pool.filter { card -> card.incorrectAttempts.isEmpty() || card.incorrectAttempts.maxOrNull()!! < cutoffTime }
                        }
                        SelectionMode.REVIEW_COUNT -> {
                            if (config.reviewCountDirection == Direction.DESC) pool.filter { it.reviewedCount <= config.reviewCountThreshold }
                            else pool.filter { it.reviewedCount >= config.reviewCountThreshold }
                        }
                        SelectionMode.SCORE -> {
                            val getScore: (Card) -> Float = { card ->
                                val total = card.gradedAttempts.size
                                if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                            }
                            val threshold = config.scoreThreshold.toFloat() / 100f
                            if (config.scoreDirection == Direction.DESC) pool.filter { getScore(it) <= threshold }
                            else pool.filter { getScore(it) >= threshold }
                        }
                        else -> pool
                    }

                    // Sorting Logic
                    val getScore: (Card) -> Float = { card ->
                        val total = card.gradedAttempts.size
                        if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                    }
                    val isAsc = config.sortDirection == Direction.ASC

                    val sorted = when (config.sortMode) {
                        SortMode.ALPHABETICAL -> {
                            val selector: (Card) -> String = { if (config.sortSide == CardSide.FRONT) it.front.lowercase() else it.back.lowercase() }
                            if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
                        }
                        SortMode.REVIEW_DATE -> {
                            val selector: (Card) -> Long? = { it.reviewedAt }
                            if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector))
                            else pool.sortedWith(compareByDescending(nullsLast(), selector))
                        }
                        SortMode.INCORRECT_DATE -> {
                            val selector: (Card) -> Long? = { it.incorrectAttempts.maxOrNull() }
                            if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector))
                            else pool.sortedWith(compareByDescending(nullsLast(), selector))
                        }
                        SortMode.REVIEW_COUNT -> {
                            if (isAsc) pool.sortedBy { it.reviewedCount } else pool.sortedByDescending { it.reviewedCount }
                        }
                        SortMode.SCORE -> {
                            if (isAsc) pool.sortedBy(getScore) else pool.sortedByDescending(getScore)
                        }
                        SortMode.CARD_ORDER -> {
                            val indexMap = parentDeck.cards.mapIndexed { index, card -> card.id to index }.toMap()
                            val selector: (Card) -> Int = { indexMap[it.id] ?: Int.MAX_VALUE }
                            if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
                        }
                        SortMode.RANDOM -> pool.shuffled()
                        SortMode.NONE -> pool
                    }

                    showRangeSelector = config to sorted
                    showAutoCreator = false
                }
            )
        }

        showRangeSelector?.let { (config, sortedCards) ->
            CardRangeSelectionDialog(
                sortedCards = sortedCards,
                onDismiss = { showRangeSelector = null },
                onConfirm = { startCardId ->
                    viewModel.createAutomaticSets(parentDeck, config, startCardId)
                    showRangeSelector = null
                }
            )
        }

        showDeleteDialog?.let { deckToDelete ->
            ConfirmationDialog(
                title = getText(R.string.delete_set_question),
                text = stringResource(R.string.delete_set_confirm, deckToDelete.deck.name),
                onConfirm = {
                    viewModel.deleteDeck(deckToDelete.deck.id)
                    showDeleteDialog = null
                },
                onDismiss = { showDeleteDialog = null }
            )
        }

        if (showDeleteAllSetsDialog) {
            ConfirmationDialog(
                title = getText(R.string.delete_all_sets_question),
                text = stringResource(R.string.delete_all_sets_confirm, parentDeck.deck.name),
                onConfirm = {
                    viewModel.deleteAllSetsForDeck(parentDeck.deck.id)
                    showDeleteAllSetsDialog = false
                },
                onDismiss = { showDeleteAllSetsDialog = false },
                confirmButtonText = getText(R.string.delete_all)
            )
        }

        Scaffold(
            topBar = {
                CustomTopAppBar(
                    title = { Text(stringResource(R.string.deck_sets_title_format, parentDeck.deck.name)) },
                    navigationIcon = {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = getText(R.string.back))
                        }
                    }
                )
            }
        ) { padding ->
            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                val sortedSets = remember(sets) {
                    val setComparator = compareBy<DeckWithCards, Int?>(nullsLast()) {
                        it.deck.name.removePrefix("Set ").toIntOrNull()
                    }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.deck.name }

                    sets.sortedWith(
                        compareByDescending<DeckWithCards> { it.deck.isStarred }
                            .then(setComparator)
                    )
                }

                AnimatedContent(
                    targetState = sortedSets.isEmpty(),
                    transitionSpec = {
                        (slideInVertically() + fadeIn() + expandVertically()).togetherWith(
                            slideOutVertically() + fadeOut() + shrinkVertically()
                        )
                    },
                    label = "setsListTransition"
                ) { isEmpty ->
                    if (isEmpty) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(getText(R.string.no_sets_yet), textAlign = TextAlign.Center)
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 300.dp),
                            contentPadding = PaddingValues(
                                start = dimensions.paddingMedium,
                                end = dimensions.paddingMedium,
                                top = dimensions.paddingMedium,
                                bottom = 120.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                        ) {
                            items(sortedSets) { set ->
                                // Use the DeckListItem from CommonUiComponents (assumed available and updated)
                                DeckListItem(
                                    deck = set,
                                    dimensions = dimensions,
                                    setsCount = 0,
                                    onStudy = { navController.navigate("studyModeSelection/${set.deck.id}") },
                                    onEdit = { setToEdit = set },
                                    onDelete = { showDeleteDialog = set },
                                    onManageSets = { /* Not used here */ },
                                    onToggleStar = { viewModel.toggleDeckStar(set.deck) },
                                    showManageSetsButton = false
                                )
                            }
                        }
                    }
                }

                var fabMenuExpanded by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimensions.paddingMedium),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                ) {
                    androidx.compose.animation.AnimatedVisibility(
                        visible = fabMenuExpanded,
                        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
                        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 })
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium) // Increased spacing for larger buttons
                        ) {
                            // Manual Option
                            androidx.compose.material3.ExtendedFloatingActionButton(
                                onClick = {
                                    fabMenuExpanded = false
                                    showManualCreateDialog = true
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                icon = { Icon(Icons.Default.ChecklistRtl, contentDescription = null) },
                                text = { Text(getText(R.string.pick_and_choose), style = MaterialTheme.typography.labelLarge) },
                                shape = RoundedCornerShape(dimensions.cornerRadiusLarge)
                            )

                            // Automatic Option
                            androidx.compose.material3.ExtendedFloatingActionButton(
                                onClick = {
                                    fabMenuExpanded = false
                                    showAutoCreator = true
                                },
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                icon = { Icon(Icons.Default.FilterAlt, contentDescription = null) },
                                text = { Text(getText(R.string.filter_and_sort), style = MaterialTheme.typography.labelLarge) },
                                shape = RoundedCornerShape(dimensions.cornerRadiusLarge)
                            )
                        }
                    }

                    val mainFabRotation by animateFloatAsState(
                        targetValue = if (fabMenuExpanded) 45f else 0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "fabRotation"
                    )

                    val addInteractionSource = remember { MutableInteractionSource() }
                    val isAddPressed by addInteractionSource.collectIsPressedAsState()
                    val addScale by animateFloatAsState(
                        targetValue = if (isAddPressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "addFabSquish"
                    )

                    FloatingActionButton(
                        onClick = { fabMenuExpanded = !fabMenuExpanded },
                        interactionSource = addInteractionSource,
                        modifier = Modifier.scale(addScale),
                        shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = getText(R.string.set_create),
                            modifier = Modifier
                                .rotate(mainFabRotation)
                                .size(28.dp) // Slightly larger icon for the main FAB
                        )
                    }
                }

                if (sortedSets.isNotEmpty()) {
                    val deleteInteractionSource = remember { MutableInteractionSource() }
                    val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
                    val deleteScale by animateFloatAsState(
                        targetValue = if (isDeletePressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "deleteFabSquish"
                    )
                    androidx.compose.material3.ExtendedFloatingActionButton(
                        onClick = { showDeleteAllSetsDialog = true },
                        interactionSource = deleteInteractionSource,
                        modifier = Modifier.align(Alignment.BottomStart).padding(dimensions.paddingMedium).scale(deleteScale),
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
                        icon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        text = { Text(getText(R.string.delete_all_sets)) }
                    )
                }
            }
        }
    }
}

@Composable
fun CreateSetDialog(
    onDismiss: () -> Unit,
    onAutomatic: () -> Unit,
    onManual: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    getText(R.string.set_create),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                val autoInteractionSource = remember { MutableInteractionSource() }
                val isAutoPressed by autoInteractionSource.collectIsPressedAsState()
                val autoScale by animateFloatAsState(
                    targetValue = if (isAutoPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "autoSquish"
                )
                Button(onClick = onAutomatic, interactionSource = autoInteractionSource, modifier = Modifier.fillMaxWidth().scale(autoScale)) {
                    Text(getText(R.string.automatic))
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                val manualInteractionSource = remember { MutableInteractionSource() }
                val isManualPressed by manualInteractionSource.collectIsPressedAsState()
                val manualScale by animateFloatAsState(
                    targetValue = if (isManualPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "manualSquish"
                )
                Button(onClick = onManual, interactionSource = manualInteractionSource, modifier = Modifier.fillMaxWidth().scale(manualScale)) {
                    Text(getText(R.string.manual))
                }
            }
        }
    }
}

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

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(dimensions.paddingLarge)
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
                    enabled = availableCardsCount > 0
                ) {
                    Text(getText(R.string.pick_starting_card))
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                val createInteractionSource = remember { MutableInteractionSource() }
                val isCreatePressed by createInteractionSource.collectIsPressedAsState()
                val createScale by animateFloatAsState(
                    targetValue = if (isCreatePressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "createSquish"
                )
                Button(
                    onClick = { onCreate(currentConfig) },
                    interactionSource = createInteractionSource,
                    modifier = Modifier.fillMaxWidth().scale(createScale),
                    enabled = availableCardsCount > 0
                ) {
                    Text(getText(R.string.create_sets))
                }
            }
        }
    }
}

@Composable
fun CardRangeSelectionDialog(
    sortedCards: List<Card>,
    onDismiss: () -> Unit,
    onConfirm: (startCardId: String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var selectedStartCardId by rememberSaveable { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Scaffold(
            topBar = {
                CustomTopAppBar(
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
                        IconButton(onClick = onDismiss) {
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
                            .padding(dimensions.paddingMedium)
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
fun ManualSetCreatorDialog(
    parentDeck: DeckWithCards,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var setName by rememberSaveable { mutableStateOf("") }
    val selectedCards = remember { mutableStateListOf<Card>() }

    val availableCards = remember(parentDeck.cards, selectedCards.toList()) {
        parentDeck.cards.filter { it !in selectedCards }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .heightIn(max = 600.dp)
            ) {
                Text(
                    text = getText(R.string.pick_and_choose),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))
                OutlinedTextField(
                    value = setName,
                    onValueChange = { setName = it },
                    label = { Text(getText(R.string.default_set_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                )
                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                ) {
                    // Left Column: Available Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(getText(R.string.available), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.count_parentheses_format, availableCards.size), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        LazyColumn(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                        ) {
                            itemsIndexed(availableCards, key = { _, card -> "available-${card.id}" }) { index, card ->
                                CardSelectItem(card = card, index = index, onToggle = { selectedCards.add(card) }) {
                                    Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add))
                                }
                            }
                        }
                    }
                    // Right Column: Selected Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(getText(R.string.selected_label), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.count_parentheses_format, selectedCards.size), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        LazyColumn(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                        ) {
                            itemsIndexed(selectedCards, key = { _, card -> "selected-${card.id}" }) { index, card ->
                                CardSelectItem(card = card, index = index, onToggle = { selectedCards.remove(card) }) {
                                    Icon(Icons.Default.Remove, contentDescription = getText(R.string.card_remove))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(
                        onClick = {
                            viewModel.createSet(parentDeck.deck.id, setName, selectedCards.map { it.id })
                            onDismiss()
                        },
                        enabled = setName.isNotBlank() && selectedCards.isNotEmpty()
                    ) {
                        Text(getText(R.string.save_set))
                    }
                }
            }
        }
    }
}

@Composable
fun ManualSetEditorDialog(
    parentDeck: DeckWithCards,
    setForEditing: DeckWithCards,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var setName by rememberSaveable { mutableStateOf(setForEditing.deck.name) }
    val selectedCards = remember { mutableStateListOf(*setForEditing.cards.toTypedArray()) }

    val availableCards = remember(parentDeck.cards, selectedCards.toList()) {
        parentDeck.cards.filter { it !in selectedCards }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .heightIn(max = 600.dp)
            ) {
                Text(
                    text = getText(R.string.set_edit),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))
                OutlinedTextField(
                    value = setName,
                    onValueChange = { setName = it },
                    label = { Text(getText(R.string.default_set_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                )
                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                ) {
                    // Left Column: Available Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(getText(R.string.available), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.count_parentheses_format, availableCards.size), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        LazyColumn(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                        ) {
                            itemsIndexed(availableCards, key = { _, card -> "available-${card.id}" }) { index, card ->
                                CardSelectItem(card = card, index = index, onToggle = { selectedCards.add(card) }) {
                                    Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add))
                                }
                            }
                        }
                    }
                    // Right Column: Selected Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(getText(R.string.selected_label), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.count_parentheses_format, selectedCards.size), style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        LazyColumn(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                        ) {
                            itemsIndexed(selectedCards, key = { _, card -> "selected-${card.id}" }) { index, card ->
                                CardSelectItem(card = card, index = index, onToggle = { selectedCards.remove(card) }) {
                                    Icon(Icons.Default.Remove, contentDescription = getText(R.string.card_remove))
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(
                        onClick = {
                            viewModel.updateSet(setForEditing.deck.id, setName, selectedCards.map { it.id })
                            onDismiss()
                        },
                        enabled = setName.isNotBlank() && selectedCards.isNotEmpty()
                    ) {
                        Text(getText(R.string.save_changes))
                    }
                }
            }
        }
    }
}

@Composable
fun CardSelectItem(
    card: Card,
    index: Int,
    onToggle: () -> Unit,
    icon: @Composable () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "itemSquish"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .background(if (index % 2 != 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onToggle
            )
            .padding(horizontal = dimensions.paddingSmall, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(card.front, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        IconButton(
            onClick = onToggle,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            icon()
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
        onToggle = onToggleExpand
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
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (setMode == AutoSetCreationMode.ONE) stringResource(R.string.cards_in_set_format, maxCardsPerSet) else stringResource(R.string.cards_per_set_format, maxCardsPerSet))
                    Slider(
                        value = maxCardsPerSet.toFloat().coerceIn(1f, maxCardsLimit),
                        onValueChange = { onMaxCardsPerSetChange(it.roundToInt()) },
                        valueRange = 1f..maxCardsLimit,
                        steps = (maxCardsLimit.toInt() - 1 - 1).coerceAtLeast(0)
                    )
                }
                OutlinedTextField(
                    value = maxCardsPerSet.toString(),
                    onValueChange = {
                        val safeMax = max(1, availableCardsCount)
                        onMaxCardsPerSetChange((it.toIntOrNull() ?: 1).coerceIn(1, safeMax))
                    },
                    modifier = Modifier.width(60.dp),
                    singleLine = true,
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

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