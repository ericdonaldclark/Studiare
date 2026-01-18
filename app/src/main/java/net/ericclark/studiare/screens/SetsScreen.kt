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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import net.ericclark.studiare.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import net.ericclark.studiare.data.*

@Composable
fun SetManagerScreen(
    navController: NavController,
    parentDeck: net.ericclark.studiare.data.DeckWithCards,
    sets: List<net.ericclark.studiare.data.DeckWithCards>,
    viewModel: net.ericclark.studiare.FlashcardViewModel
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<net.ericclark.studiare.data.DeckWithCards?>(null) }
    var showDeleteAllSetsDialog by remember { mutableStateOf(false) }
    var showAutoCreator by remember { mutableStateOf(false) }
    var showRangeSelector by remember { mutableStateOf<Pair<net.ericclark.studiare.data.AutoSetConfig, List<net.ericclark.studiare.data.Card>>?>(null) }
    var showManualCreateDialog by remember { mutableStateOf(false) }
    var setToEdit by remember { mutableStateOf<net.ericclark.studiare.data.DeckWithCards?>(null) }

    val allTags by viewModel.tags.collectAsState()
    val parentDeckTags = remember(parentDeck) {
        parentDeck.cards.flatMap { it.tags }.distinct().sorted()
    }

    if (showCreateDialog) {
        CreateSetDialog(
            onDismiss = { showCreateDialog = false },
            onAutomatic = {
                showCreateDialog = false
                showAutoCreator = true
            },
            onManual = {
                showCreateDialog = false
                showManualCreateDialog = true
            }
        )
    }

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
                // Direct creation without range selection
                viewModel.createAutomaticSets(parentDeck, config)
                showAutoCreator = false
            },
            onPickStartCard = { config ->
                // Filter and Sort logic to prepare the list for the user to pick from
                var pool = parentDeck.cards

                // 1. Filtering
                if (config.excludeKnown) pool = pool.filter { !it.isKnown }

                // Time Helpers
                val timeMultiplier = when (config.timeUnit) {
                    "Days" -> 24 * 60 * 60 * 1000L
                    "Weeks" -> 7 * 24 * 60 * 60 * 1000L
                    "Months" -> 30 * 24 * 60 * 60 * 1000L
                    "Years" -> 365 * 24 * 60 * 60 * 1000L
                    else -> 0L
                }
                val cutoffTime = System.currentTimeMillis() - (config.timeValue * timeMultiplier)

                pool = when (config.selectionMode) {
                    DIFFICULTY -> pool.filter { it.difficulty in config.selectedDifficulties }
                    TAGS -> pool.filter { card -> card.tags.any { it in config.selectedTags } }
                    ALPHABET -> {
                        val start = config.alphabetStart.uppercase()
                        val end = config.alphabetEnd.uppercase()
                        pool.filter { card ->
                            val text = if (config.filterSide == "Front") card.front else card.back
                            val firstChar = text.trim().uppercase(java.util.Locale.getDefault()).firstOrNull()?.toString()
                            firstChar != null && firstChar >= start && firstChar <= end
                        }
                    }
                    CARD_ORDER -> {
                        val s = (config.cardOrderStart - 1).coerceAtLeast(0)
                        val e = (config.cardOrderEnd - 1).coerceAtMost(parentDeck.cards.size - 1)
                        if (s <= e && parentDeck.cards.isNotEmpty()) {
                            val allowedIds = parentDeck.cards.slice(s..e).map { it.id }.toSet()
                            pool.filter { it.id in allowedIds }
                        } else emptyList()
                    }
                    REVIEW_DATE -> {
                        if (config.filterType == "Include") pool.filter { it.reviewedAt != null && it.reviewedAt >= cutoffTime }
                        else pool.filter { it.reviewedAt == null || it.reviewedAt < cutoffTime }
                    }
                    INCORRECT_DATE -> {
                        if (config.filterType == "Include") pool.filter { card -> card.incorrectAttempts.maxOrNull()?.let { last -> last >= cutoffTime } == true }
                        else pool.filter { card -> card.incorrectAttempts.isEmpty() || card.incorrectAttempts.maxOrNull()!! < cutoffTime }
                    }
                    REVIEW_COUNT -> {
                        if (config.reviewCountDirection == "Maximum") pool.filter { it.reviewedCount <= config.reviewCountThreshold }
                        else pool.filter { it.reviewedCount >= config.reviewCountThreshold }
                    }
                    SCORE -> {
                        val getScore: (net.ericclark.studiare.data.Card) -> Float = { card ->
                            val total = card.gradedAttempts.size
                            if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                        }
                        val threshold = config.scoreThreshold.toFloat() / 100f
                        if (config.scoreDirection == "Maximum") pool.filter { getScore(it) <= threshold }
                        else pool.filter { getScore(it) >= threshold }
                    }
                    else -> pool
                }

                // 2. Sorting
                // Helper extractors
                val getScore: (net.ericclark.studiare.data.Card) -> Float = { card ->
                    val total = card.gradedAttempts.size
                    if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                }
                val isAsc = config.sortDirection == "ASC"

                val sorted = when (config.sortMode) {
                    ALPHABETICAL -> {
                        val selector: (net.ericclark.studiare.data.Card) -> String = { if (config.sortSide == "Front") it.front.lowercase() else it.back.lowercase() }
                        if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
                    }
                    REVIEW_DATE -> {
                        // Nulls last usually for dates
                        val selector: (net.ericclark.studiare.data.Card) -> Long? = { it.reviewedAt }
                        if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector))
                        else pool.sortedWith(compareByDescending(nullsLast(), selector))
                    }
                    INCORRECT_DATE -> {
                        val selector: (net.ericclark.studiare.data.Card) -> Long? = { it.incorrectAttempts.maxOrNull() }
                        if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector))
                        else pool.sortedWith(compareByDescending(nullsLast(), selector))
                    }
                    REVIEW_COUNT -> {
                        if (isAsc) pool.sortedBy { it.reviewedCount } else pool.sortedByDescending { it.reviewedCount }
                    }
                    SCORE -> {
                        if (isAsc) pool.sortedBy(getScore) else pool.sortedByDescending(getScore)
                    }
                    CARD_ORDER -> {
                        // Original order in parent deck
                        val indexMap = parentDeck.cards.mapIndexed { index, card -> card.id to index }.toMap()
                        val selector: (net.ericclark.studiare.data.Card) -> Int = { indexMap[it.id] ?: Int.MAX_VALUE }
                        if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
                    }
                    RANDOM -> pool.shuffled()
                    else -> pool
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
            title = "Delete Set?",
            text = "Are you sure you want to delete the set \"${deckToDelete.deck.name}\"?",
            onConfirm = {
                viewModel.deleteDeck(deckToDelete.deck.id)
                showDeleteDialog = null
            },
            onDismiss = { showDeleteDialog = null }
        )
    }

    if (showDeleteAllSetsDialog) {
        ConfirmationDialog(
            title = "Delete All Sets?",
            text = "Are you sure you want to delete all sets for \"${parentDeck.deck.name}\"? This will not delete the cards themselves.",
            onConfirm = {
                viewModel.deleteAllSetsForDeck(parentDeck.deck.id)
                showDeleteAllSetsDialog = false
            },
            onDismiss = { showDeleteAllSetsDialog = false },
            confirmButtonText = "Delete All"
        )
    }


    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("${parentDeck.deck.name} - sets") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            val sortedSets = remember(sets) {
                val setComparator = compareBy<net.ericclark.studiare.data.DeckWithCards, Int?>(nullsLast()) {
                    it.deck.name.removePrefix("Set ").toIntOrNull()
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.deck.name }

                sets.sortedWith(
                    compareByDescending<net.ericclark.studiare.data.DeckWithCards> { it.deck.isStarred }
                        .then(setComparator)
                )
            }

            if (sortedSets.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No sets yet. Create one to get started!", textAlign = TextAlign.Center)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(sortedSets) { set ->
                        DeckListItem(
                            deck = set,
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

            FloatingActionButton(
                onClick = { showCreateDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Create Set")
            }

            if (sortedSets.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showDeleteAllSetsDialog = true },
                    modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete All Sets")
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
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    "Create Set",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Button(onClick = onAutomatic, modifier = Modifier.fillMaxWidth()) {
                    Text("Automatic")
                }
                Spacer(Modifier.height(8.dp))
                Button(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                    Text("Manual")
                }
            }
        }
    }
}

@Composable
fun AutomaticSetCreatorDialog(
    parentDeck: net.ericclark.studiare.data.DeckWithCards,
    availableTags: List<String>,
    allTagDefinitions: List<net.ericclark.studiare.data.TagDefinition>,
    onDismiss: () -> Unit,
    onCreate: (config: net.ericclark.studiare.data.AutoSetConfig) -> Unit,
    onPickStartCard: (config: net.ericclark.studiare.data.AutoSetConfig) -> Unit
) {
    // --- State ---
    var setMode by rememberSaveable { mutableStateOf("One") } // "One", "Multiple", "Split All"

    // Configuration
    var numSets by rememberSaveable { mutableIntStateOf(3) }
    var maxCardsPerSet by rememberSaveable { mutableIntStateOf(25) }

    // Selection State
    val ANY = ANY
    var selectionMode by rememberSaveable { mutableStateOf(ANY) }
    var selectedTags by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val selectedDifficulties = remember { mutableStateListOf(1, 2, 3, 4, 5) }
    var excludeKnown by rememberSaveable { mutableStateOf(true) }

    // Alphabet State
    var alphabetStart by rememberSaveable { mutableStateOf("A") }
    var alphabetEnd by rememberSaveable { mutableStateOf("Z") }
    var filterSide by rememberSaveable { mutableStateOf("Front") }

    // Card Order Range State
    val totalCards = parentDeck.cards.size
    var cardOrderStart by rememberSaveable { mutableIntStateOf(1) }
    var cardOrderEnd by rememberSaveable { mutableIntStateOf(if (totalCards > 0) totalCards else 1) }

    // Time Filter State
    var timeValue by rememberSaveable { mutableIntStateOf(7) }
    var timeUnit by rememberSaveable { mutableStateOf("Days") }
    var filterType by rememberSaveable { mutableStateOf("Exclude") }

    // Score & Review Count State
    val maxDeckReviews = remember(parentDeck) { parentDeck.cards.maxOfOrNull { it.reviewedCount } ?: 0 }
    var reviewThreshold by rememberSaveable { mutableIntStateOf(0) }
    var reviewDirection by rememberSaveable { mutableStateOf("Minimum") }

    var scoreThreshold by rememberSaveable { mutableIntStateOf(0) }
    var scoreDirection by rememberSaveable { mutableStateOf("Minimum") }

    // Sorting
    var sortMode by rememberSaveable { mutableStateOf(RANDOM) }
    var sortDirection by rememberSaveable { mutableStateOf("ASC") }
    var sortSide by rememberSaveable { mutableStateOf("Front") }

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
            "Days" -> 24 * 60 * 60 * 1000L
            "Weeks" -> 7 * 24 * 60 * 60 * 1000L
            "Months" -> 30 * 24 * 60 * 60 * 1000L
            "Years" -> 365 * 24 * 60 * 60 * 1000L
            else -> 0L
        }
        val cutoffTime = System.currentTimeMillis() - (timeValue * timeMultiplier)

        pool = when (selectionMode) {
            DIFFICULTY -> pool.filter { it.difficulty in selectedDifficulties }
            TAGS -> pool.filter { card -> card.tags.any { it in selectedTags } }
            ALPHABET -> {
                val start = alphabetStart.uppercase()
                val end = alphabetEnd.uppercase()
                pool.filter { card ->
                    val text = if (filterSide == "Front") card.front else card.back
                    val firstChar = text.trim().uppercase(java.util.Locale.getDefault()).firstOrNull()?.toString()
                    firstChar != null && firstChar >= start && firstChar <= end
                }
            }
            CARD_ORDER -> {
                val s = (cardOrderStart - 1).coerceAtLeast(0)
                val e = (cardOrderEnd - 1).coerceAtMost(parentDeck.cards.size - 1)
                if (s <= e && parentDeck.cards.isNotEmpty()) {
                    val allowedIds = parentDeck.cards.slice(s..e).map { it.id }.toSet()
                    pool.filter { it.id in allowedIds }
                } else {
                    emptyList()
                }
            }
            REVIEW_DATE -> {
                if (filterType == "Include") pool.filter { it.reviewedAt != null && it.reviewedAt >= cutoffTime }
                else pool.filter { it.reviewedAt == null || it.reviewedAt < cutoffTime }
            }
            INCORRECT_DATE -> {
                if (filterType == "Include") pool.filter { card -> card.incorrectAttempts.maxOrNull()?.let { last -> last >= cutoffTime } == true }
                else pool.filter { card -> card.incorrectAttempts.isEmpty() || card.incorrectAttempts.maxOrNull()!! < cutoffTime }
            }
            REVIEW_COUNT -> {
                if (reviewDirection == "Maximum") pool.filter { it.reviewedCount <= reviewThreshold }
                else pool.filter { it.reviewedCount >= reviewThreshold }
            }
            SCORE -> {
                val getScore: (net.ericclark.studiare.data.Card) -> Float = { card ->
                    val total = card.gradedAttempts.size
                    if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                }
                val threshold = scoreThreshold.toFloat() / 100f
                if (scoreDirection == "Maximum") pool.filter { getScore(it) <= threshold }
                else pool.filter { getScore(it) >= threshold }
            }
            else -> pool
        }
        pool.size
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = "Automatic Set Creator",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))

                // 1. Top Slider Section
                TopSliderDialogSection(
                    options = listOf("One", "Multiple", "Split All"), // <--- Pass specific options
                    selectedMode = setMode,
                    onModeChange = { setMode = it }
                )
                Spacer(Modifier.height(16.dp))

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

                Spacer(Modifier.height(24.dp))
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

                // NEW: Pick Starting Card Button
                OutlinedButton(
                    onClick = { onPickStartCard(currentConfig) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = availableCardsCount > 0
                ) {
                    Text("Pick Starting Card")
                }

                Spacer(Modifier.height(8.dp))

                Button(
                    onClick = { onCreate(currentConfig) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = availableCardsCount > 0
                ) {
                    Text("Create Sets")
                }
            }
        }
    }
}

@Composable
fun CardRangeSelectionDialog(
    sortedCards: List<net.ericclark.studiare.data.Card>,
    onDismiss: () -> Unit,
    onConfirm: (startCardId: String) -> Unit
) {
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
                            Text("Select Starting Card")
                        }
                    },
                    navigationIcon = {}, // Empty to help with centering
                    actions = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.Default.Close, contentDescription = "Close")
                        }
                    }
                )
            },
            bottomBar = {
                Surface(shadowElevation = 8.dp) {
                    Button(
                        onClick = { selectedStartCardId?.let { onConfirm(it) } },
                        enabled = selectedStartCardId != null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    ) {
                        Text("Confirm")
                    }
                }
            }
        ) { padding ->
            Column(modifier = Modifier.padding(padding).padding(16.dp)) {
                Text("Select the card you want your new set to start with. The rest of the cards will follow in the sorted order.",
                    style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                LazyColumn(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                    itemsIndexed(sortedCards, key = { _, card -> card.id }) { index, card ->
                        val backgroundColor = when {
                            card.id == selectedStartCardId -> MaterialTheme.colorScheme.primaryContainer
                            index % 2 != 0 -> MaterialTheme.colorScheme.secondaryContainer
                            else -> Color.Transparent
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(backgroundColor)
                                .clickable { selectedStartCardId = card.id }
                                .padding(12.dp),
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
    parentDeck: net.ericclark.studiare.data.DeckWithCards,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    onDismiss: () -> Unit
) {
    var setName by rememberSaveable { mutableStateOf("") }
    val selectedCards = remember { mutableStateListOf<net.ericclark.studiare.data.Card>() }

    val availableCards = remember(parentDeck.cards, selectedCards.toList()) {
        parentDeck.cards.filter { it !in selectedCards }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .heightIn(max = 600.dp)
            ) {
                Text(
                    text = "Create Manual Set",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = setName,
                    onValueChange = { setName = it },
                    label = { Text("Set Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Available Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Available", style = MaterialTheme.typography.titleMedium)
                        Text("(${availableCards.size})", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                            itemsIndexed(availableCards, key = { _, card -> "available-${card.id}" }) { index, card ->
                                CardSelectItem(card = card, index = index, onToggle = { selectedCards.add(card) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Card")
                                }
                            }
                        }
                    }
                    // Right Column: Selected Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Selected", style = MaterialTheme.typography.titleMedium)
                        Text("(${selectedCards.size})", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                            itemsIndexed(selectedCards, key = { _, card -> "selected-${card.id}" }) { index, card ->
                                CardSelectItem(card = card, index = index, onToggle = { selectedCards.remove(card) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Remove Card")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.createSet(parentDeck.deck.id, setName, selectedCards.map { it.id })
                            onDismiss()
                        },
                        enabled = setName.isNotBlank() && selectedCards.isNotEmpty()
                    ) {
                        Text("Save Set")
                    }
                }
            }
        }
    }
}

@Composable
fun ManualSetEditorDialog(
    parentDeck: net.ericclark.studiare.data.DeckWithCards,
    setForEditing: net.ericclark.studiare.data.DeckWithCards,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    onDismiss: () -> Unit
) {
    var setName by rememberSaveable { mutableStateOf(setForEditing.deck.name) }
    val selectedCards = remember { mutableStateListOf(*setForEditing.cards.toTypedArray()) }

    val availableCards = remember(parentDeck.cards, selectedCards.toList()) {
        parentDeck.cards.filter { it !in selectedCards }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .heightIn(max = 600.dp)
            ) {
                Text(
                    text = "Edit Set",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                OutlinedTextField(
                    value = setName,
                    onValueChange = { setName = it },
                    label = { Text("Set Name") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left Column: Available Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Available", style = MaterialTheme.typography.titleMedium)
                        Text("(${availableCards.size})", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                            itemsIndexed(availableCards, key = { _, card -> "available-${card.id}" }) { index, card ->
                                CardSelectItem(card = card, index = index, onToggle = { selectedCards.add(card) }) {
                                    Icon(Icons.Default.Add, contentDescription = "Add Card")
                                }
                            }
                        }
                    }
                    // Right Column: Selected Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Selected", style = MaterialTheme.typography.titleMedium)
                        Text("(${selectedCards.size})", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.height(8.dp))
                        LazyColumn(modifier = Modifier.border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))) {
                            itemsIndexed(selectedCards, key = { _, card -> "selected-${card.id}" }) { index, card ->
                                CardSelectItem(card = card, index = index, onToggle = { selectedCards.remove(card) }) {
                                    Icon(Icons.Default.Remove, contentDescription = "Remove Card")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.updateSet(setForEditing.deck.id, setName, selectedCards.map { it.id })
                            onDismiss()
                        },
                        enabled = setName.isNotBlank() && selectedCards.isNotEmpty()
                    ) {
                        Text("Save Changes")
                    }
                }
            }
        }
    }
}

@Composable
fun CardSelectItem(
    card: net.ericclark.studiare.data.Card,
    index: Int,
    onToggle: () -> Unit,
    icon: @Composable () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (index % 2 != 0) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
            .clickable(onClick = onToggle)
            .padding(horizontal = 8.dp, vertical = 2.dp),
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
    setMode: String,
    numSets: Int, onNumSetsChange: (Int) -> Unit,
    maxCardsPerSet: Int, onMaxCardsPerSetChange: (Int) -> Unit,
    sizeExpanded: Boolean, onToggleExpand: () -> Unit,
    availableCardsCount: Int
) {
    DialogSection(
        title = "Set Size",
        subtitle = if (setMode == "Multiple") "$numSets sets of $maxCardsPerSet" else "Max $maxCardsPerSet cards",
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

            if (setMode == "Multiple") {
                Text("Number of Sets: $numSets")
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
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (setMode == "One") "Cards in Set: $maxCardsPerSet" else "Cards per Set: $maxCardsPerSet")
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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
            }

            // Estimation
            val totalCardsUsed = if (setMode == "One") maxCardsPerSet
            else if (setMode == "Multiple") numSets * maxCardsPerSet
            else availableCardsCount
            val estimatedSets = if (setMode == "One") 1
            else if (setMode == "Multiple") numSets
            else kotlin.math.ceil(availableCardsCount.toDouble() / maxCardsPerSet).toInt()

            Text(
                "Result: ~$estimatedSets sets using ~${
                    min(
                        totalCardsUsed,
                        availableCardsCount
                    )
                } cards",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}