package net.ericclark.studiare.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import net.ericclark.studiare.screens.CARD_ORDER
import net.ericclark.studiare.components.*
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.screens.DIFFICULTY
import net.ericclark.studiare.DialogSection
import net.ericclark.studiare.screens.DuplicateWarningDialog
import net.ericclark.studiare.DifficultySlider
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.MarkKnownButton
import net.ericclark.studiare.TextFieldWithNotes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import net.ericclark.studiare.data.*

data class CardEditorState(
    val id: String,
    var front: MutableState<String>,
    var back: MutableState<String>,
    var frontNotes: MutableState<String?>,
    var backNotes: MutableState<String?>,
    var difficulty: MutableState<Int>,
    var isKnown: MutableState<Boolean>,
    var reviewedCount: MutableState<Int>,
    var gradedAttempts: MutableState<List<Long>>,
    var incorrectAttempts: MutableState<List<Long>>,
    var tags: MutableState<List<String>>
)

/**
 * A screen for creating a new deck or editing an existing one.
 * It provides fields for the deck name and a list of cards with fronts, backs, and difficulties.
 * @param navController The NavController for navigating back.
 * @param deckWithCards The existing deck to edit, or null if creating a new one.
 * @param viewModel The ViewModel providing data and business logic.
 */
@Composable
fun DeckEditorScreen(navController: NavController, deckWithCards: net.ericclark.studiare.data.DeckWithCards?, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    // State for the deck name
    var deckName by remember { mutableStateOf(deckWithCards?.deck?.name ?: "") }

    // State for deck settings
    var normalizationType by remember { mutableStateOf(deckWithCards?.deck?.normalizationType ?: 0) }
    var sortType by remember { mutableStateOf(deckWithCards?.deck?.sortType ?: 0) }

    // NEW: State for Languages (Default to system default if new, or load from deck)
    var frontLanguage by remember { mutableStateOf(deckWithCards?.deck?.frontLanguage ?: Locale.getDefault().language) }
    var backLanguage by remember { mutableStateOf(deckWithCards?.deck?.backLanguage ?: Locale.getDefault().language) }

    // State for the filter text to search for specific cards
    var filterText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // State for showing the filter text field
    var showFilter by remember { mutableStateOf(false) }
    // State for Language Dialog
    var showLanguageDialog by remember { mutableStateOf(false) }

    // State for the duplicate card warning dialog
    val editorDuplicateResult by viewModel.editorDuplicateResult.collectAsState()
    // State for showing the deck statistics
    var showStats by remember { mutableStateOf(false) }
    // State for showing the settings dialog
    var showSettingsDialog by remember { mutableStateOf(false) }
    // State for showing the unsaved changes dialog
    var showUnsavedDialog by remember { mutableStateOf(false) }

    val allTags by viewModel.tags.collectAsState()

    // State for the list of cards in the editor
    val cards = remember {
        val initialCards = deckWithCards?.cards?.map {
            CardEditorState(
                id = it.id,
                front = mutableStateOf(it.front),
                back = mutableStateOf(it.back),
                frontNotes = mutableStateOf(it.frontNotes),
                backNotes = mutableStateOf(it.backNotes),
                difficulty = mutableStateOf(it.difficulty),
                isKnown = mutableStateOf(it.isKnown),
                reviewedCount = mutableStateOf(it.reviewedCount),
                gradedAttempts = mutableStateOf(it.gradedAttempts),
                incorrectAttempts = mutableStateOf(it.incorrectAttempts),
                tags = mutableStateOf(it.tags)
            )
        } ?: listOf(
            // Start with one empty card if creating a new deck
            CardEditorState(
                id = UUID.randomUUID().toString(),
                front = mutableStateOf(""),
                back = mutableStateOf(""),
                frontNotes = mutableStateOf(null),
                backNotes = mutableStateOf(null),
                difficulty = mutableStateOf(1),
                isKnown = mutableStateOf(false),
                reviewedCount = mutableStateOf(0),
                gradedAttempts = mutableStateOf(emptyList()),
                incorrectAttempts = mutableStateOf(emptyList()),
                tags = mutableStateOf(emptyList())
            )
        )
        mutableStateListOf(*initialCards.toTypedArray())
    }

    // --- State Change Detection ---
    val isDirty by remember(deckName, normalizationType, sortType, frontLanguage, backLanguage, cards.toList(), cards.map { it.tags.value }) {
        derivedStateOf {
            val originalName = deckWithCards?.deck?.name ?: ""
            val originalNorm = deckWithCards?.deck?.normalizationType ?: 0
            val originalSort = deckWithCards?.deck?.sortType ?: 0
            val originalFrontLang = deckWithCards?.deck?.frontLanguage ?: Locale.getDefault().language
            val originalBackLang = deckWithCards?.deck?.backLanguage ?: Locale.getDefault().language

            val originalCards = deckWithCards?.cards?.map {
                CardDataForSave(
                    it.id,
                    it.front,
                    it.back,
                    it.frontNotes,
                    it.backNotes,
                    it.difficulty,
                    it.isKnown,
                    it.reviewedCount,
                    it.gradedAttempts,
                    it.incorrectAttempts,
                    it.tags
                )
            } ?: listOf(
                CardDataForSave(
                    "",
                    "",
                    "",
                    null,
                    null,
                    1,
                    false,
                    0,
                    emptyList(),
                    emptyList(),
                    emptyList()
                )
            )

            val currentCards = cards.map {
                CardDataForSave(
                    it.id,
                    it.front.value,
                    it.back.value,
                    it.frontNotes.value,
                    it.backNotes.value,
                    it.difficulty.value,
                    it.isKnown.value,
                    it.reviewedCount.value,
                    it.gradedAttempts.value,
                    it.incorrectAttempts.value,
                    it.tags.value
                )
            }

            deckName != originalName ||
                    normalizationType != originalNorm ||
                    sortType != originalSort ||
                    frontLanguage != originalFrontLang ||
                    backLanguage != originalBackLang ||
                    currentCards.size != originalCards.size ||
                    !currentCards.containsAll(originalCards) ||
                    !originalCards.containsAll(currentCards)
        }
    }

    // --- Back Navigation Handler ---
    BackHandler(enabled = isDirty) {
        showUnsavedDialog = true
    }

    // --- Helper Functions for Applying Settings ---
    fun applyNormalization(type: Int) {
        cards.forEach { card ->
            when (type) {
                1 -> { // Uppercase
                    card.front.value = card.front.value.replaceFirstChar { it.uppercase() }
                    card.back.value = card.back.value.replaceFirstChar { it.uppercase() }
                    card.frontNotes.value = card.frontNotes.value?.replaceFirstChar { it.uppercase() }
                    card.backNotes.value = card.backNotes.value?.replaceFirstChar { it.uppercase() }
                }
                2 -> { // Lowercase
                    card.front.value = card.front.value.replaceFirstChar { it.lowercase() }
                    card.back.value = card.back.value.replaceFirstChar { it.lowercase() }
                    card.frontNotes.value = card.frontNotes.value?.replaceFirstChar { it.lowercase() }
                    card.backNotes.value = card.backNotes.value?.replaceFirstChar { it.lowercase() }
                }
            }
        }
    }

    fun applySorting(type: Int) {
        val sorted = when (type) {
            1 -> cards.sortedBy { it.front.value.lowercase() }
            2 -> cards.sortedByDescending { it.front.value.lowercase() }
            3 -> cards.sortedWith(compareBy<CardEditorState> { it.difficulty.value }.thenBy { it.front.value.lowercase() })
            4 -> cards.sortedWith(compareByDescending<CardEditorState> { it.difficulty.value }.thenBy { it.front.value.lowercase() })
            else -> null
        }
        if (sorted != null) {
            cards.clear()
            cards.addAll(sorted)
        }
    }

    // --- Save Action ---
    val saveAction = {
        val cardData = cards.map {
            CardDataForSave(
                it.id,
                it.front.value.trim(),
                it.back.value.trim(),
                it.frontNotes.value?.trim(),
                it.backNotes.value?.trim(),
                it.difficulty.value,
                it.isKnown.value,
                it.reviewedCount.value,
                it.gradedAttempts.value,
                it.incorrectAttempts.value,
                it.tags.value
            )
        }.filter { it.front.isNotBlank() && it.back.isNotBlank() }

        viewModel.checkForDuplicatesInEditor(
            deckWithCards?.deck?.id,
            deckName,
            cardData,
            normalizationType,
            sortType,
            deckWithCards?.deck?.parentDeckId,
            frontLanguage, // Pass new fields
            backLanguage
        )

        if (viewModel.editorDuplicateResult.value == null) {
            navController.popBackStack()
        }
    }

    // --- Dialogs ---
    if (showSettingsDialog) {
        DeckSettingsDialog(
            initialNormalizationType = normalizationType,
            initialSortType = sortType,
            onDismiss = { showSettingsDialog = false },
            onSave = { newNorm, newSort ->
                normalizationType = newNorm
                sortType = newSort
                applyNormalization(newNorm)
                applySorting(newSort)
                showSettingsDialog = false
            },
            // --- NEW CALLBACK ---
            onClearReviewData = {
                // 1. Call ViewModel to reset persistent data (if deck exists)
                if (deckWithCards != null) {
                    viewModel.clearDeckReviewData(deckWithCards.deck.id)
                }
                // 2. Reset Local Editor State immediately
                cards.forEach { card ->
                    card.reviewedCount.value = 0
                    card.isKnown.value = false
                    card.gradedAttempts.value = emptyList()
                    card.incorrectAttempts.value = emptyList()
                }
                showSettingsDialog = false
            }
        )
    }

    if (showLanguageDialog) {
        LanguageSelectionDialog(
            currentFront = frontLanguage,
            currentBack = backLanguage,
            onDismiss = { showLanguageDialog = false },
            onSave = { newFront, newBack ->
                frontLanguage = newFront
                backLanguage = newBack
                showLanguageDialog = false
            }
        )
    }

    // Unsaved Changes Dialog Logic
    if (showUnsavedDialog) {
        UnsavedChangesDialog(
            onDismiss = { showUnsavedDialog = false },
            onDiscard = { navController.popBackStack() },
            onSave = {
                saveAction()
                showUnsavedDialog = false
            }
        )
    }

    // Show the deck name in the app bar when scrolling down
    val showDeckNameInAppBar by remember {
        derivedStateOf { lazyListState.firstVisibleItemIndex > 0 }
    }

    // Show a dialog if duplicate cards are found when saving
    if (editorDuplicateResult != null) {
        DuplicateWarningDialog(
            result = editorDuplicateResult!!,
            onDismiss = { viewModel.dismissEditorDuplicateWarning() },
            onConfirmRemove = {
                viewModel.saveEditorWithDuplicatesRemoved()
                navController.popBackStack()
            },
            onConfirmSaveAnyway = {
                viewModel.saveEditorIgnoringDuplicates()
                navController.popBackStack()
            }
        )
    }

    // Helper to format language for display (e.g., "ENG -> ITA")
    val languageDisplayString = remember(frontLanguage, backLanguage) {
        val frontIso3 = try { Locale(frontLanguage).getISO3Language().uppercase() } catch(e: Exception) { frontLanguage.uppercase() }
        val backIso3 = try { Locale(backLanguage).getISO3Language().uppercase() } catch(e: Exception) { backLanguage.uppercase() }
        "Language: $frontIso3 -> $backIso3"
    }

    // Filter the cards based on the filter text
    val filteredCards = remember(filterText.trim(), cards.toList()) {
        val trimmedFilterText = filterText.trim()
        if (trimmedFilterText.isBlank()) {
            cards
        } else {
            cards.filter {
                it.front.value.contains(trimmedFilterText, ignoreCase = true) ||
                        it.back.value.contains(trimmedFilterText, ignoreCase = true)
            }
        }
    }

    val currentDeckTags = remember(cards.map { it.tags.value }) {
        cards.flatMap { it.tags.value }.toSet()
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = {
                    Text(
                        if (showDeckNameInAppBar) deckName else (if (deckWithCards == null) "Create Deck" else "Edit Deck"),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (isDirty) {
                            showUnsavedDialog = true
                        } else {
                            navController.popBackStack()
                        }
                    }) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, "Deck Settings")
                    }
                    // Button to toggle the filter text field
                    IconButton(onClick = {
                        showFilter = !showFilter
                        coroutineScope.launch { lazyListState.scrollToItem(0) }
                    }) {
                        Icon(Icons.Default.Search, "Filter Cards")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            BoxWithConstraints {
                val isWideScreen = this.maxWidth > 600.dp
                if (isWideScreen) {
                    // WIDE SCREEN LAYOUT
                    Row(modifier = Modifier.padding(16.dp)) {
                        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                            OutlinedTextField(value = deckName, onValueChange = { deckName = it }, label = { Text("Deck Name") }, modifier = Modifier.fillMaxWidth())

                            // NEW: Language Selector (Wide Layout)
                            Spacer(Modifier.height(8.dp))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp))
                                    .clickable { showLanguageDialog = true }
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                    .padding(16.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(languageDisplayString, style = MaterialTheme.typography.bodyLarge)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Language")
                                }
                            }

                            Spacer(Modifier.height(8.dp))
                            if (deckWithCards != null) {
                                DeckStats(deckWithCards = deckWithCards)
                            }
                            AnimatedVisibility(visible = showFilter) {
                                OutlinedTextField(
                                    value = filterText,
                                    onValueChange = { filterText = it },
                                    label = { Text("Filter cards...") },
                                    modifier = Modifier.fillMaxWidth(),
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                )
                            }
                        }
                        // Right column for the list of cards
                        Column(modifier = Modifier.weight(1.5f)) {
                            Box(modifier = Modifier.weight(1f)) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    itemsIndexed(filteredCards, key = { _, item -> item.id }) { index, cardState ->
                                        CardEditor(
                                            cardState = cardState,
                                            cardNumber = index + 1,
                                            totalCards = filteredCards.size,
                                            onDelete = { if (cards.size > 1) cards.remove(cardState) },
                                            onKnownClick = { cardState.isKnown.value = !cardState.isKnown.value },
                                            // ADDED TAGS
                                            allTags = allTags,
                                            currentDeckTags = currentDeckTags,
                                            onUpdateTags = { newTags -> cardState.tags.value = newTags.toList() },
                                            onCreateTag = { name, color -> viewModel.saveTagDefinition(
                                                TagDefinition(
                                                    name = name,
                                                    color = color
                                                )
                                            ) }
                                        )
                                    }
                                    item {
                                        Button(
                                            onClick = {
                                                cards.add(CardEditorState(id = UUID.randomUUID().toString(), front = mutableStateOf(""), back = mutableStateOf(""), frontNotes = mutableStateOf(null), backNotes = mutableStateOf(null), difficulty = mutableStateOf(1), isKnown = mutableStateOf(false), reviewedCount = mutableStateOf(0), gradedAttempts = mutableStateOf(emptyList()), incorrectAttempts = mutableStateOf(emptyList()), tags = mutableStateOf(emptyList())))
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) { Text("Add Card") }
                                    }
                                }

                                // Custom Fast Scroll Slider (Wide Mode)
                                val totalItemsCount = lazyListState.layoutInfo.totalItemsCount
                                if (totalItemsCount > 1) {
                                    var barHeight by remember { mutableStateOf(0f) }
                                    val density = LocalDensity.current

                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(30.dp)
                                            .fillMaxHeight()
                                            .padding(vertical = 8.dp)
                                            .onSizeChanged { barHeight = it.height.toFloat() }
                                            .pointerInput(totalItemsCount, barHeight) {
                                                detectVerticalDragGestures(
                                                    onDragStart = { offset ->
                                                        if (barHeight > 0) {
                                                            val percentage = (offset.y / barHeight).coerceIn(0f, 1f)
                                                            val index = (percentage * (totalItemsCount - 1)).toInt()
                                                            coroutineScope.launch { lazyListState.scrollToItem(index) }
                                                        }
                                                    },
                                                    onVerticalDrag = { change, _ ->
                                                        if (barHeight > 0) {
                                                            val percentage = (change.position.y / barHeight).coerceIn(0f, 1f)
                                                            val index = (percentage * (totalItemsCount - 1)).toInt()
                                                            coroutineScope.launch { lazyListState.scrollToItem(index) }
                                                        }
                                                    }
                                                )
                                            }
                                    ) {
                                        if (barHeight > 0 && totalItemsCount > 0) {
                                            val visibleItems = lazyListState.layoutInfo.visibleItemsInfo.size
                                            val thumbHeightPx = (barHeight * visibleItems / totalItemsCount).coerceAtLeast(100f)
                                            val firstVisible = lazyListState.firstVisibleItemIndex
                                            val scrollOffsetPx = (firstVisible.toFloat() / totalItemsCount) * barHeight

                                            val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
                                            val scrollOffsetDp = with(density) { scrollOffsetPx.toDp() }

                                            Box(
                                                modifier = Modifier
                                                    .align(Alignment.TopCenter)
                                                    .offset(y = scrollOffsetDp)
                                                    .width(6.dp)
                                                    .height(thumbHeightDp)
                                                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Button(
                                onClick = { saveAction() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = deckName.isNotBlank() && cards.any { it.front.value.isNotBlank() && it.back.value.isNotBlank() }
                            ) { Text("Save Deck") }
                        }
                    }
                } else {
                    // NARROW SCREEN LAYOUT
                    Column {
                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                item {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedTextField(
                                            value = deckName,
                                            onValueChange = { deckName = it },
                                            label = { Text("Deck Name") },
                                            modifier = Modifier.weight(1f)
                                        )
                                        if (deckWithCards != null) {
                                            IconButton(onClick = { showStats = !showStats }) {
                                                Icon(
                                                    if (showStats) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                                    contentDescription = "Toggle Stats"
                                                )
                                            }
                                        }
                                    }

                                    // NEW: Language Selector (Narrow Layout)
                                    Spacer(Modifier.height(8.dp))
                                    Surface(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp))
                                            .clickable { showLanguageDialog = true }
                                            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
                                            .padding(16.dp)
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(languageDisplayString, style = MaterialTheme.typography.bodyLarge)
                                            Icon(Icons.Default.ArrowDropDown, contentDescription = "Select Language")
                                        }
                                    }

                                    if (deckWithCards != null) {
                                        androidx.compose.animation.AnimatedVisibility(visible = showStats) {
                                            DeckStats(deckWithCards = deckWithCards)
                                        }
                                    }
                                    Spacer(Modifier.height(8.dp))
                                    androidx.compose.animation.AnimatedVisibility(visible = showFilter) {
                                        OutlinedTextField(
                                            value = filterText,
                                            onValueChange = { filterText = it },
                                            label = { Text("Filter cards...") },
                                            modifier = Modifier.fillMaxWidth(),
                                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                                        )
                                    }
                                    Spacer(Modifier.height(16.dp))
                                    Text("Cards", style = MaterialTheme.typography.titleMedium)
                                }
                                itemsIndexed(filteredCards, key = { _, item -> item.id }) { index, cardState ->
                                    CardEditor(
                                        cardState = cardState,
                                        cardNumber = index + 1,
                                        totalCards = filteredCards.size,
                                        onDelete = { if (cards.size > 1) cards.remove(cardState) },
                                        onKnownClick = { cardState.isKnown.value = !cardState.isKnown.value },
                                        // ADDED TAGS
                                        allTags = allTags,
                                        currentDeckTags = currentDeckTags,
                                        onUpdateTags = { newTags -> cardState.tags.value = newTags.toList() },
                                        onCreateTag = { name, color -> viewModel.saveTagDefinition(
                                            TagDefinition(
                                                name = name,
                                                color = color
                                            )
                                        ) }
                                    )
                                }
                                item {
                                    Button(
                                        onClick = {
                                            cards.add(CardEditorState(id = UUID.randomUUID().toString(), front = mutableStateOf(""), back = mutableStateOf(""), frontNotes = mutableStateOf(null), backNotes = mutableStateOf(null), difficulty = mutableStateOf(1), isKnown = mutableStateOf(false), reviewedCount = mutableStateOf(0), gradedAttempts = mutableStateOf(emptyList()), incorrectAttempts = mutableStateOf(emptyList()), tags = mutableStateOf(emptyList())))
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) { Text("Add Card") }
                                }
                            }

                            // Custom Fast Scroll Slider (Narrow Mode)
                            val totalItemsCount = lazyListState.layoutInfo.totalItemsCount
                            if (totalItemsCount > 1) {
                                var barHeight by remember { mutableStateOf(0f) }
                                val density = LocalDensity.current

                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(30.dp)
                                        .fillMaxHeight()
                                        .padding(vertical = 8.dp)
                                        .onSizeChanged { barHeight = it.height.toFloat() }
                                        .pointerInput(totalItemsCount, barHeight) {
                                            detectVerticalDragGestures(
                                                onDragStart = { offset ->
                                                    if (barHeight > 0) {
                                                        val percentage = (offset.y / barHeight).coerceIn(0f, 1f)
                                                        val index = (percentage * (totalItemsCount - 1)).toInt()
                                                        coroutineScope.launch { lazyListState.scrollToItem(index) }
                                                    }
                                                },
                                                onVerticalDrag = { change, _ ->
                                                    if (barHeight > 0) {
                                                        val percentage = (change.position.y / barHeight).coerceIn(0f, 1f)
                                                        val index = (percentage * (totalItemsCount - 1)).toInt()
                                                        coroutineScope.launch { lazyListState.scrollToItem(index) }
                                                    }
                                                }
                                            )
                                        }
                                ) {
                                    if (barHeight > 0 && totalItemsCount > 0) {
                                        val visibleItems = lazyListState.layoutInfo.visibleItemsInfo.size
                                        val thumbHeightPx = (barHeight * visibleItems / totalItemsCount).coerceAtLeast(100f)
                                        val firstVisible = lazyListState.firstVisibleItemIndex
                                        val scrollOffsetPx = (firstVisible.toFloat() / totalItemsCount) * barHeight

                                        val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
                                        val scrollOffsetDp = with(density) { scrollOffsetPx.toDp() }

                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopCenter)
                                                .offset(y = scrollOffsetDp)
                                                .width(6.dp)
                                                .height(thumbHeightDp)
                                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                                        )
                                    }
                                }
                            }
                        }

                        // Save button
                        Button(
                            onClick = { saveAction() },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            enabled = deckName.isNotBlank() && cards.any { it.front.value.isNotBlank() && it.back.value.isNotBlank() }
                        ) { Text("Save Deck") }
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageSelectionDialog(
    currentFront: String,
    currentBack: String,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var frontLanguage by remember { mutableStateOf(currentFront) }
    var backLanguage by remember { mutableStateOf(currentBack) }

    // Create a sorted list of unique available languages
    val availableLanguages = remember {
        Locale.getAvailableLocales()
            .map { it.language to it.displayLanguage }
            .filter { it.second.isNotEmpty() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    "Set Deck Languages",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(24.dp))

                // Front Side Selector
                Text("Front Side Language", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LanguageDropdown(
                    languages = availableLanguages,
                    selectedCode = frontLanguage,
                    onLanguageSelected = { frontLanguage = it }
                )

                Spacer(Modifier.height(24.dp))

                // Back Side Selector
                Text("Back Side Language", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                LanguageDropdown(
                    languages = availableLanguages,
                    selectedCode = backLanguage,
                    onLanguageSelected = { backLanguage = it }
                )

                Spacer(Modifier.height(32.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(frontLanguage, backLanguage) }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageDropdown(
    languages: List<Pair<String, String>>,
    selectedCode: String,
    onLanguageSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedName = languages.find { it.first == selectedCode }?.second ?: "Unknown"

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(4.dp))
            .clickable { expanded = true }
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = selectedName)
            Icon(Icons.Default.ArrowDropDown, contentDescription = null)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .heightIn(max = 300.dp) // Limit height
                .fillMaxWidth(0.8f)
        ) {
            languages.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onLanguageSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DeckStats(deckWithCards: net.ericclark.studiare.data.DeckWithCards) {
    val difficultyCounts = deckWithCards.cards.groupingBy { it.difficulty }.eachCount()
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy, h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        elevation = CardDefaults.cardElevation(2.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Deck Statistics", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("Created: ${dateFormat.format(Date(deckWithCards.deck.createdAt))}")
            Text("Last Modified: ${dateFormat.format(Date(deckWithCards.deck.updatedAt))}")
            deckWithCards.deck.averageQuizScore?.let {
                Text("Avg. Quiz Score: ${(it * 100).roundToInt()}%")
            }
            Spacer(Modifier.height(8.dp))
            Text("Difficulty Breakdown:", fontWeight = FontWeight.Bold)
            (1..5).forEach { difficulty ->
                val count = difficultyCounts[difficulty] ?: 0
                Text("Difficulty $difficulty: $count cards")
            }
        }
    }
}



@Composable
fun CardEditor(
    cardState: CardEditorState,
    cardNumber: Int,
    totalCards: Int,
    onDelete: () -> Unit,
    onKnownClick: () -> Unit,
    // UPDATED: Tag parameters
    allTags: List<net.ericclark.studiare.data.TagDefinition>,
    currentDeckTags: Set<String>,
    onUpdateTags: (Set<String>) -> Unit, // Changed to single update callback
    onCreateTag: (String, String) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "$cardNumber of $totalCards",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Delete Card") }
            }
            Spacer(Modifier.height(4.dp))

            // Front Text Field with optional Notes
            TextFieldWithNotes(
                mainText = cardState.front.value,
                onMainTextChange = { cardState.front.value = it },
                mainLabel = "Front",
                notesText = cardState.frontNotes.value,
                onNotesTextChange = { cardState.frontNotes.value = it },
                notesLabel = "Front Notes"
            )

            Spacer(Modifier.height(8.dp))

            // Back Text Field with optional Notes
            TextFieldWithNotes(
                mainText = cardState.back.value,
                onMainTextChange = { cardState.back.value = it },
                mainLabel = "Back",
                notesText = cardState.backNotes.value,
                onNotesTextChange = { cardState.backNotes.value = it },
                notesLabel = "Back Notes"
            )

            // ADDED: Card Tag Row
            Spacer(Modifier.height(8.dp))
            CardTagRow(
                cardTags = cardState.tags.value,
                allTags = allTags,
                currentDeckTags = currentDeckTags,
                onUpdateTags = onUpdateTags,
                onCreateTag = onCreateTag
            )

            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DifficultySlider(
                    label = DIFFICULTY,
                    difficulty = cardState.difficulty.value,
                    onDifficultyChange = { cardState.difficulty.value = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(16.dp))
                MarkKnownButton(
                    isKnown = cardState.isKnown.value,
                    onClick = onKnownClick
                )
            }
        }
    }
}

@Composable
fun UnsavedChangesDialog(onDismiss: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unsaved Changes", textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text("You have unsaved changes. Would you like to save them?") },
        confirmButton = {
            Button(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text("Discard") }
        },
    )
}

@Composable
fun DeckSettingsDialog(
    initialNormalizationType: Int,
    initialSortType: Int,
    onDismiss: () -> Unit,
    onSave: (Int, Int) -> Unit,
    onClearReviewData: () -> Unit // --- ADDED PARAMETER ---
) {
    var normalizationType by remember { mutableStateOf(initialNormalizationType) }
    var sortType by remember { mutableStateOf(initialSortType) }
    var showClearConfirm by remember { mutableStateOf(false) } // Local state for confirmation

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text("Clear Review Data?") },
            text = { Text("This will reset reviews, known status, and FSRS scheduling for all cards in this deck. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearReviewData()
                    },
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear Data") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }) { Text("Cancel") }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Deck Options", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(16.dp))

                // Normalization Section
                DialogSection(title = "First Letter Normalization") {
                    SettingsRadioGroup(
                        options = listOf("None", "Uppercase", "Lowercase"),
                        selectedIndex = normalizationType,
                        onSelect = { normalizationType = it }
                    )
                }

                // Sorting Section
                DialogSection(title = CARD_ORDER) {
                    SettingsRadioGroup(
                        options = listOf(
                            "Default",
                            "Alphabetical (A-Z)",
                            "Alphabetical (Z-A)",
                            "Difficulty (1-5)",
                            "Difficulty (5-1)"
                        ),
                        selectedIndex = sortType,
                        onSelect = { sortType = it }
                    )
                }

                Spacer(Modifier.height(24.dp))

                // --- NEW BUTTON SECTION ---
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Clear Review Data")
                }
                // --------------------------

                Spacer(Modifier.height(16.dp))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(normalizationType, sortType) }) {
                        Text("Save & Close")
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsRadioGroup(options: List<String>, selectedIndex: Int, onSelect: (Int) -> Unit) {
    Column {
        options.forEachIndexed { index, text ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(index) }
                    .padding(vertical = 4.dp)
            ) {
                RadioButton(
                    selected = selectedIndex == index,
                    onClick = { onSelect(index) }
                )
                Spacer(Modifier.width(8.dp))
                Text(text)
            }
        }
    }
}

