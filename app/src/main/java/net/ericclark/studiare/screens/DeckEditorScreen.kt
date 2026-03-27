package net.ericclark.studiare.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import net.ericclark.studiare.components.*
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.DialogSection
import net.ericclark.studiare.DifficultySlider
import net.ericclark.studiare.MarkKnownButton
import net.ericclark.studiare.TextFieldWithNotes
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.foundation.text.KeyboardOptions // Added for numeric input
import androidx.compose.material3.Switch // Added for switch
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType // Added for numeric input
import net.ericclark.studiare.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color


/**
 * A screen for creating a new deck or editing an existing one.
 * It provides fields for the deck name and a list of cards with fronts, backs, and difficulties.
 * @param navController The NavController for navigating back.
 * @param deckWithCards The existing deck to edit, or null if creating a new one.
 * @param viewModel The ViewModel providing data and business logic.
 */
@Composable
fun DeckEditorScreen(navController: NavController, deckWithCards: DeckWithCards?, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val context = LocalContext.current
    val dimensions = LocalStudiareDimensions.current

    // State for the deck name
    var deckName by remember { mutableStateOf(deckWithCards?.deck?.name ?: "") }

    // State for deck settings
    var normalizationType by remember { mutableStateOf(deckWithCards?.deck?.normalizationType ?: NormalizationType.NONE) }
    var sortType by remember { mutableStateOf(deckWithCards?.deck?.deckSortMode ?: DeckSortMode.DATE_ADDED_OLD_TO_NEW) }

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
                tags = mutableStateOf(it.tags),
                isSuspended = mutableStateOf(it.isSuspended),
                flag = mutableStateOf(it.flag),
                createdAt = mutableLongStateOf(it.createdAt),
                updatedAt = mutableStateOf(it.updatedAt)
            )
        } ?: listOf(
            // Start with one empty card if creating a new deck
            CardEditorState(
                id = UUID.randomUUID().toString(),
                front = mutableStateOf(""),
                back = mutableStateOf(""),
                frontNotes = mutableStateOf(null),
                backNotes = mutableStateOf(null),
                difficulty = mutableStateOf(DifficultySetting.ONE),
                isKnown = mutableStateOf(false),
                reviewedCount = mutableStateOf(0),
                gradedAttempts = mutableStateOf(emptyList()),
                incorrectAttempts = mutableStateOf(emptyList()),
                tags = mutableStateOf(emptyList()),
                isSuspended = mutableStateOf(false),
                flag = mutableStateOf(CardFlag.NONE),
                createdAt = mutableLongStateOf(System.currentTimeMillis()),
                updatedAt = mutableStateOf(System.currentTimeMillis())
            )
        )
        mutableStateListOf(*initialCards.toTypedArray())
    }

    // --- State Change Detection ---
    val isDirty by remember(deckName, normalizationType, sortType, frontLanguage, backLanguage, cards.toList(), cards.map { it.tags.value }) {
        derivedStateOf {
            val originalName = deckWithCards?.deck?.name ?: ""
            val originalNorm = deckWithCards?.deck?.normalizationType ?: 0
            val originalSort = deckWithCards?.deck?.deckSortMode ?: 0
            val originalFrontLang = deckWithCards?.deck?.frontLanguage ?: Locale.getDefault().language
            val originalBackLang = deckWithCards?.deck?.backLanguage ?: Locale.getDefault().language

            val originalCards = deckWithCards?.cards?.map {
                CardDataForSave(
                    id = it.id,
                    front = it.front,
                    back = it.back,
                    frontNotes = it.frontNotes,
                    backNotes = it.backNotes,
                    difficulty = it.difficulty,
                    isKnown = it.isKnown,
                    reviewedCount = it.reviewedCount,
                    gradedAttempts = it.gradedAttempts,
                    incorrectAttempts = it.incorrectAttempts,
                    tags = it.tags,
                    isSuspended = it.isSuspended,
                    flag = it.flag,
                    createdAt = it.createdAt,
                    updatedAt = it.updatedAt
                )
            } ?: listOf(
                CardDataForSave(
                    id = "",
                    front = "",
                    back = "",
                    frontNotes = null,
                    backNotes = null,
                    difficulty = DifficultySetting.ONE,
                    isKnown = false,
                    reviewedCount = 0,
                    gradedAttempts = emptyList(),
                    incorrectAttempts = emptyList(),
                    tags = emptyList(),
                    isSuspended = false,
                    flag = CardFlag.NONE,
                    createdAt = System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis()
                )
            )

            val currentCards = cards.map {
                CardDataForSave(
                    id = it.id,
                    front = it.front.value,
                    back = it.back.value,
                    frontNotes = it.frontNotes.value,
                    backNotes = it.backNotes.value,
                    difficulty = it.difficulty.value,
                    isKnown = it.isKnown.value,
                    reviewedCount = it.reviewedCount.value,
                    gradedAttempts = it.gradedAttempts.value,
                    incorrectAttempts = it.incorrectAttempts.value,
                    tags = it.tags.value,
                    isSuspended = it.isSuspended.value,
                    flag = it.flag.value,
                    createdAt = it.createdAt.value,
                    updatedAt = it.updatedAt.value
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
    fun applyNormalization(type: NormalizationType) {
        cards.forEach { card ->
            when (type) {
                NormalizationType.UPPERCASE_FIRST_LETTER  -> { // Uppercase
                    card.front.value = card.front.value.replaceFirstChar { it.uppercase() }
                    card.back.value = card.back.value.replaceFirstChar { it.uppercase() }
                    card.frontNotes.value = card.frontNotes.value?.replaceFirstChar { it.uppercase() }
                    card.backNotes.value = card.backNotes.value?.replaceFirstChar { it.uppercase() }
                }
                NormalizationType.UPPERCASE_ALL_LETTERS -> { // Uppercase
                    card.front.value = card.front.value.uppercase()
                    card.back.value = card.back.value.uppercase()
                    card.frontNotes.value = card.frontNotes.value?.uppercase()
                    card.backNotes.value = card.backNotes.value?.uppercase()
                }
                NormalizationType.UPPERCASE_EACH_WORD -> {
                    card.front.value = card.front.value.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                    card.back.value = card.back.value.split(" ").joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                    card.frontNotes.value = card.frontNotes.value?.split(" ")?.joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                    card.backNotes.value = card.backNotes.value?.split(" ")?.joinToString(" ") { word ->
                        word.replaceFirstChar { it.uppercase() }
                    }
                }
                NormalizationType.LOWERCASE_FIRST_LETTER  -> { // Lowercase
                    card.front.value = card.front.value.replaceFirstChar { it.lowercase() }
                    card.back.value = card.back.value.replaceFirstChar { it.lowercase() }
                    card.frontNotes.value = card.frontNotes.value?.replaceFirstChar { it.lowercase() }
                    card.backNotes.value = card.backNotes.value?.replaceFirstChar { it.lowercase() }
                }
                NormalizationType.LOWERCASE_ALL_LETTERS -> { // Lowercase
                    card.front.value = card.front.value.lowercase()
                    card.back.value = card.back.value.lowercase()
                    card.frontNotes.value = card.frontNotes.value?.lowercase()
                    card.backNotes.value = card.backNotes.value?.lowercase()
                }
                NormalizationType.NONE -> {
                    card.front.value = card.front.value
                    card.back.value = card.back.value
                    card.frontNotes.value = card.frontNotes.value
                    card.backNotes.value = card.backNotes.value
                }
            }
        }
    }

    fun applySorting(type: DeckSortMode) {
        val sorted = when (type) {
            DeckSortMode.A_TO_Z -> cards.sortedBy { it.front.value.lowercase() }
            DeckSortMode.Z_TO_A -> cards.sortedByDescending { it.front.value.lowercase() }
            DeckSortMode.ONE_TO_FIVE -> cards.sortedWith(compareBy<CardEditorState> { it.difficulty.value }.thenBy { it.front.value.lowercase() })
            DeckSortMode.FIVE_TO_ONE -> cards.sortedWith(compareByDescending<CardEditorState> { it.difficulty.value }.thenBy { it.front.value.lowercase() })
            DeckSortMode.DATE_ADDED_OLD_TO_NEW -> cards.sortedWith(compareBy<CardEditorState> { it.createdAt.value }.thenBy { it.front.value.lowercase() })
            DeckSortMode.DATE_ADDED_NEW_TO_OLD -> cards.sortedWith(compareByDescending<CardEditorState> { it.createdAt.value }.thenBy { it.front.value.lowercase() })
            DeckSortMode.DATE_MODIFIED_NEW_TO_OLD -> cards.sortedWith(compareBy<CardEditorState> { it.updatedAt.value }.thenBy { it.front.value.lowercase() })
            DeckSortMode.DATE_MODIFIED_OLD_TO_NEW -> cards.sortedWith(compareByDescending<CardEditorState> { it.updatedAt.value }.thenBy { it.front.value.lowercase() })
        }
        if (sorted != null) {
            cards.clear()
            cards.addAll(sorted)
        }
    }

    // --- Save Action ---
    val saveAction = {
        val cardData = cards.mapIndexed { index, it ->
            CardDataForSave(
                id = it.id,
                front = it.front.value.trim(),
                back = it.back.value.trim(),
                frontNotes = it.frontNotes.value?.trim(),
                backNotes = it.backNotes.value?.trim(),
                difficulty = it.difficulty.value,
                isKnown = it.isKnown.value,
                reviewedCount = it.reviewedCount.value,
                gradedAttempts = it.gradedAttempts.value,
                incorrectAttempts = it.incorrectAttempts.value,
                tags = it.tags.value,
                isSuspended = it.isSuspended.value,
                flag = it.flag.value,
                createdAt = it.createdAt.value,
                updatedAt = it.updatedAt.value
            )
        }.filter { it.front.isNotBlank() && it.back.isNotBlank() }

        viewModel.checkForDuplicatesInEditor(
            deckWithCards?.deck?.id,
            deckName,
            cardData,
            normalizationType,
            sortType,
            deckWithCards?.deck?.parentDeckId,
            frontLanguage,
            backLanguage,
            // Pass existing Deck fields (since no UI exists to edit them)
            deckWithCards?.deck?.description ?: "",
            deckWithCards?.deck?.dailyNewCardLimit ?: 20,
            deckWithCards?.deck?.dailyReviewLimit ?: 200
        )

        if (viewModel.editorDuplicateResult.value == null) {
            navController.popBackStack()
        }
    }

    // --- Dialogs ---
    if (showSettingsDialog) {
        DeckSettingsDialog(
            initialNormalizationType = normalizationType,
            initialDeckSort = sortType,
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
        getText(context,R.string.language) + ": $frontIso3 -> $backIso3"
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
                        if (showDeckNameInAppBar) deckName
                        else (if (deckWithCards == null) getText(R.string.deck_create)
                        else getText(R.string.deck_edit)),
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
                    }) { Icon(Icons.Default.ArrowBack, getText(R.string.back)) }
                },
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, getText(R.string.deck_settings))
                    }
                    // Button to toggle the filter text field
                    IconButton(onClick = {
                        showFilter = !showFilter
                        coroutineScope.launch { lazyListState.scrollToItem(0) }
                    }) {
                        Icon(Icons.Default.Search, getText(R.string.cards_filter))
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
                    Row(
                        modifier = Modifier.padding(dimensions.paddingMedium),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            OutlinedTextField(
                                value = deckName,
                                onValueChange = { deckName = it },
                                label = { Text(getText(R.string.deck_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                            )

                            // NEW: Language Selector (Wide Layout)
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .clickable { showLanguageDialog = true }
                                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(dimensions.cornerRadiusMedium)),
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Row(
                                    modifier = Modifier.padding(dimensions.paddingMedium),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(languageDisplayString, style = MaterialTheme.typography.bodyLarge)
                                    Icon(Icons.Default.ArrowDropDown, contentDescription = getText(R.string.language_select))
                                }
                            }

                            Spacer(Modifier.height(dimensions.spacingMedium))
                            if (deckWithCards != null) {
                                DeckStats(deckWithCards = deckWithCards)
                            }
                            AnimatedVisibility(
                                visible = showFilter,
                                enter = slideInVertically() + fadeIn() + expandVertically(),
                                exit = slideOutVertically() + fadeOut() + shrinkVertically()
                            ) {
                                androidx.compose.material3.TextField(
                                    value = filterText,
                                    onValueChange = { filterText = it },
                                    placeholder = { Text(getText(R.string.cards_filter_)) },
                                    modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
                                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                    shape = CircleShape,
                                    colors = androidx.compose.material3.TextFieldDefaults.colors(
                                        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                        focusedIndicatorColor = Color.Transparent,
                                        unfocusedIndicatorColor = Color.Transparent,
                                        disabledIndicatorColor = Color.Transparent
                                    ),
                                    singleLine = true
                                )
                            }
                        }
                        // Right column for the list of cards
                        Column(modifier = Modifier.weight(1.5f)) {
                            Box(modifier = Modifier.weight(1f)) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
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
                                        val addInteractionSource = remember { MutableInteractionSource() }
                                        val isAddPressed by addInteractionSource.collectIsPressedAsState()
                                        val addScale by animateFloatAsState(
                                            targetValue = if (isAddPressed) 0.95f else 1f,
                                            animationSpec = spring(
                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                stiffness = Spring.StiffnessMedium
                                            ),
                                            label = "addSquish"
                                        )
                                        // Upgraded to FilledTonalButton with CircleShape and Icon
                                        FilledTonalButton(
                                            onClick = {
                                                cards.add(CardEditorState(
                                                    id = UUID.randomUUID().toString(), front = mutableStateOf(""), back = mutableStateOf(""),
                                                    frontNotes = mutableStateOf(null), backNotes = mutableStateOf(null),
                                                    difficulty = mutableStateOf(DifficultySetting.ONE), isKnown = mutableStateOf(false),
                                                    reviewedCount = mutableStateOf(0), gradedAttempts = mutableStateOf(emptyList()),
                                                    incorrectAttempts = mutableStateOf(emptyList()), tags = mutableStateOf(emptyList()),
                                                    isSuspended = mutableStateOf(false), flag = mutableStateOf(CardFlag.NONE),
                                                    createdAt = mutableLongStateOf(System.currentTimeMillis()), updatedAt = mutableStateOf(System.currentTimeMillis())))
                                            },
                                            interactionSource = addInteractionSource,
                                            modifier = Modifier.fillMaxWidth().scale(addScale).padding(vertical = dimensions.paddingSmall),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(getText(R.string.card_add), style = MaterialTheme.typography.labelLarge)
                                        }
                                    }
                                }

                                // Custom Fast Scroll Slider (Narrow Mode) - REPLACED
                                CustomVerticalScrollbar(
                                    listState = lazyListState,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(30.dp)
                                        .fillMaxHeight()
                                        .padding(vertical = dimensions.paddingMedium)
                                )
                            }

                            // Save button
                            val saveInteractionSource = remember { MutableInteractionSource() }
                            val isSavePressed by saveInteractionSource.collectIsPressedAsState()
                            val saveScale by animateFloatAsState(
                                targetValue = if (isSavePressed) 0.95f else 1f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium
                                ),
                                label = "saveSquish"
                            )
                            Button(
                                onClick = { saveAction() },
                                interactionSource = saveInteractionSource,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(dimensions.paddingMedium)
                                    .scale(saveScale),
                                enabled = deckName.isNotBlank() && cards.any { it.front.value.isNotBlank() && it.back.value.isNotBlank() },
                                shape = CircleShape // M3 Expressive Pill Shape
                            ) { Text(getText(R.string.deck_save), style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                } else {
                    // NARROW SCREEN LAYOUT
                    Column {
                        Box(modifier = Modifier.weight(1f)) {
                            LazyColumn(
                                state = lazyListState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(
                                    start = dimensions.paddingMedium,
                                    end = dimensions.paddingMedium,
                                    top = dimensions.paddingMedium,
                                    bottom = 80.dp
                                ),
                                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                            ) {
                                item {
                                    // WRAP in Column scope to fix AnimatedVisibility error
                                    Column {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            OutlinedTextField(
                                                value = deckName,
                                                onValueChange = { deckName = it },
                                                label = { Text(getText(R.string.deck_name)) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                            )
                                            if (deckWithCards != null) {
                                                IconButton(onClick = { showStats = !showStats }) {
                                                    val rotation by animateFloatAsState(
                                                        targetValue = if (showStats) 180f else 0f,
                                                        animationSpec = spring(
                                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                                            stiffness = Spring.StiffnessMedium
                                                        ),
                                                        label = "statsRotation"
                                                    )
                                                    Icon(
                                                        Icons.Default.KeyboardArrowDown,
                                                        contentDescription = getText(R.string.toggle_stats),
                                                        modifier = Modifier.rotate(rotation)
                                                    )
                                                }
                                            }
                                        }

                                        // NEW: Language Selector (Narrow Layout)
                                        Spacer(Modifier.height(dimensions.spacingSmall))
                                        Surface(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                                .clickable { showLanguageDialog = true }
                                                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(dimensions.cornerRadiusMedium)),
                                            color = MaterialTheme.colorScheme.surface
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(dimensions.paddingMedium),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(languageDisplayString, style = MaterialTheme.typography.bodyLarge)
                                                Icon(Icons.Default.ArrowDropDown, contentDescription = getText(R.string.language_select))
                                            }
                                        }

                                        if (deckWithCards != null) {
                                            AnimatedVisibility(
                                                visible = showStats,
                                                enter = slideInVertically() + fadeIn() + expandVertically(),
                                                exit = slideOutVertically() + fadeOut() + shrinkVertically()
                                            ) {
                                                DeckStats(deckWithCards = deckWithCards)
                                            }
                                        }
                                        Spacer(Modifier.height(dimensions.spacingSmall))
                                        AnimatedVisibility(
                                            visible = showFilter,
                                            enter = slideInVertically() + fadeIn() + expandVertically(),
                                            exit = slideOutVertically() + fadeOut() + shrinkVertically()
                                        ) {
                                            androidx.compose.material3.TextField(
                                                value = filterText,
                                                onValueChange = { filterText = it },
                                                placeholder = { Text(getText(R.string.cards_filter_)) },
                                                modifier = Modifier.fillMaxWidth(),
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                                shape = CircleShape,
                                                colors = androidx.compose.material3.TextFieldDefaults.colors(
                                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent,
                                                    disabledIndicatorColor = Color.Transparent
                                                ),
                                                singleLine = true
                                            )
                                        }
                                        Spacer(Modifier.height(dimensions.spacingMedium))
                                        Text(getText(R.string.cards), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = dimensions.paddingSmall))
                                    }
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
                                    val addInteractionSource = remember { MutableInteractionSource() }
                                    val isAddPressed by addInteractionSource.collectIsPressedAsState()
                                    val addScale by animateFloatAsState(
                                        targetValue = if (isAddPressed) 0.95f else 1f,
                                        animationSpec = spring(
                                            dampingRatio = Spring.DampingRatioMediumBouncy,
                                            stiffness = Spring.StiffnessMedium
                                        ),
                                        label = "addSquish"
                                    )
                                    Button(
                                        onClick = {
                                            cards.add(CardEditorState(
                                                id = UUID.randomUUID().toString(), front = mutableStateOf(""), back = mutableStateOf(""),
                                                frontNotes = mutableStateOf(null), backNotes = mutableStateOf(null),
                                                difficulty = mutableStateOf(DifficultySetting.ONE), isKnown = mutableStateOf(false),
                                                reviewedCount = mutableStateOf(0), gradedAttempts = mutableStateOf(emptyList()),
                                                incorrectAttempts = mutableStateOf(emptyList()), tags = mutableStateOf(emptyList()),
                                                isSuspended = mutableStateOf(false), flag = mutableStateOf(CardFlag.NONE),
                                                createdAt = mutableLongStateOf(System.currentTimeMillis()), updatedAt = mutableStateOf(System.currentTimeMillis())))
                                        },
                                        interactionSource = addInteractionSource,
                                        modifier = Modifier.fillMaxWidth().scale(addScale),
                                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                    ) { Text(getText(R.string.card_add)) }
                                }
                            }

                            // Custom Fast Scroll Slider (Narrow Mode) - REPLACED
                            CustomVerticalScrollbar(
                                listState = lazyListState,
                                modifier = Modifier
                                    .align(Alignment.CenterEnd)
                                    .width(30.dp)
                                    .fillMaxHeight()
                                    .padding(vertical = dimensions.paddingMedium)
                            )
                        }

                        // Save button
                        val saveInteractionSource = remember { MutableInteractionSource() }
                        val isSavePressed by saveInteractionSource.collectIsPressedAsState()
                        val saveScale by animateFloatAsState(
                            targetValue = if (isSavePressed) 0.95f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "saveSquish"
                        )
                        Button(
                            onClick = { saveAction() },
                            interactionSource = saveInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(dimensions.paddingMedium)
                                .scale(saveScale),
                            enabled = deckName.isNotBlank() && cards.any { it.front.value.isNotBlank() && it.back.value.isNotBlank() },
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                        ) { Text(getText(R.string.deck_save)) }
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
    val dimensions = LocalStudiareDimensions.current
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
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    getText(R.string.language_set),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Front Side Selector
                Text(getText(R.string.language_front), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(dimensions.spacingSmall))
                LanguageDropdown(
                    languages = availableLanguages,
                    selectedCode = frontLanguage,
                    onLanguageSelected = { frontLanguage = it }
                )

                Spacer(Modifier.height(dimensions.spacingMedium))

                // Back Side Selector
                Text(getText(R.string.language_back), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(dimensions.spacingSmall))
                LanguageDropdown(
                    languages = availableLanguages,
                    selectedCode = backLanguage,
                    onLanguageSelected = { backLanguage = it }
                )

                Spacer(Modifier.height(dimensions.spacingLarge))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(onClick = { onSave(frontLanguage, backLanguage) }) {
                        Text(getText(R.string.save))
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
    val dimensions = LocalStudiareDimensions.current
    var expanded by remember { mutableStateOf(false) }
    val selectedName = languages.find { it.first == selectedCode }?.second ?: getText(R.string.unknown)

    androidx.compose.material3.ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                androidx.compose.material3.ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = androidx.compose.material3.ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
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
fun DeckStats(deckWithCards: DeckWithCards) {
    val dimensions = LocalStudiareDimensions.current
    val difficultyCounts = deckWithCards.cards.groupingBy { it.difficulty }.eachCount()
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy, h:mm a", Locale.getDefault()) }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
        shape = RoundedCornerShape(dimensions.cornerRadiusLarge), // Larger rounding
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) // Stronger contrast
    ) {
        Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
            Text(getText(R.string.deck_statistics), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(dimensions.spacingMedium))

            Text(stringResource(R.string.created_dt, "${dateFormat.format(Date(deckWithCards.deck.createdAt))}"), style = MaterialTheme.typography.bodyMedium)
            Text(stringResource(R.string.last_modified_dt, "${dateFormat.format(Date(deckWithCards.deck.updatedAt))}"), style = MaterialTheme.typography.bodyMedium)

            deckWithCards.deck.averageQuizScore?.let {
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(stringResource(R.string.avg_score_percent,{(it * 100).roundToInt()}), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(dimensions.spacingMedium))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(dimensions.spacingMedium))

            Text(getText(R.string.difficulty_breakdown), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(dimensions.spacingSmall))

            // Vertical list layout for stats
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp) // Tighter vertical spacing
            ) {
                DifficultySetting.entries.forEach { difficulty ->
                    val count = difficultyCounts[difficulty] ?: 0
                    androidx.compose.material3.AssistChip(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth(), // Makes them all the same width
                        label = {
                            Text(
                                text = stringResource(R.string.difficulty_count, difficulty.value, count),
                                modifier = Modifier.fillMaxWidth(),
                                textAlign = TextAlign.Center // Centers the text within the full-width chip
                            )
                        },
                        border = null,
                        colors = androidx.compose.material3.AssistChipDefaults.assistChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    )
                }
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
    allTags: List<TagDefinition>,
    currentDeckTags: Set<String>,
    onUpdateTags: (Set<String>) -> Unit,
    onCreateTag: (String, String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var showSettingsDialog by remember { mutableStateOf(false) }

    if (showSettingsDialog) {
        CardSettingsDialog(
            currentIsSuspended = cardState.isSuspended.value,
            currentFlag = cardState.flag.value,
            onDismiss = { showSettingsDialog = false },
            onSave = { newSuspended, newFlag ->
                cardState.isSuspended.value = newSuspended
                cardState.flag.value = newFlag
                showSettingsDialog = false
            }
        )
    }

    // M3 Expressive: Use elevated card for list items to give them separation
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(Modifier.padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.blank_of_blank, cardNumber, totalCards),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))

                // ADDED: Settings Gear Button
                IconButton(onClick = { showSettingsDialog = true }) {
                    Icon(Icons.Default.Settings, getText(R.string.card_settings))
                }

                IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, getText(R.string.card_delete)) }
            }
            Spacer(Modifier.height(dimensions.spacingSmall))

            // Front Text Field with optional Notes
            TextFieldWithNotes(
                mainText = cardState.front.value,
                onMainTextChange = { cardState.front.value = it },
                mainLabel = CardSide.FRONT.asString(),
                notesText = cardState.frontNotes.value,
                onNotesTextChange = { cardState.frontNotes.value = it },
                notesLabel = getText(R.string.notes_front)
            )

            Spacer(Modifier.height(dimensions.spacingSmall))

            // Back Text Field with optional Notes
            TextFieldWithNotes(
                mainText = cardState.back.value,
                onMainTextChange = { cardState.back.value = it },
                mainLabel = CardSide.BACK.asString(),
                notesText = cardState.backNotes.value,
                onNotesTextChange = { cardState.backNotes.value = it },
                notesLabel = getText(R.string.notes_back)
            )

            // ADDED: Card Tag Row
            Spacer(Modifier.height(dimensions.spacingSmall))
            CardTagRow(
                cardTags = cardState.tags.value,
                allTags = allTags,
                currentDeckTags = currentDeckTags,
                onUpdateTags = onUpdateTags,
                onCreateTag = onCreateTag
            )

            Spacer(Modifier.height(dimensions.spacingSmall))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                DifficultySlider(
                    label = stringResource(R.string.difficulty),
                    difficulty = cardState.difficulty.value,
                    onDifficultyChange = { cardState.difficulty.value = it },
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(dimensions.spacingMedium))
                Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                    MarkKnownButton(
                        isKnown = cardState.isKnown.value,
                        onClick = onKnownClick
                    )
                }
            }
        }
    }
}

@Composable
fun UnsavedChangesDialog(onDismiss: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getText(R.string.unsaved_changes), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) },
        text = { Text(getText(R.string.unsaved_changes_save)) },
        shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {
            Button(onClick = onSave) { Text(getText(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard) { Text(getText(R.string.discard)) }
        },
    )
}

@Composable
fun SettingsFilterChipGroup(options: List<String>, selectedItem: String, onSelect: (String) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
        verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
    ) {
        options.forEach { text ->
            androidx.compose.material3.FilterChip(
                selected = selectedItem == text,
                onClick = { onSelect(text) },
                label = { Text(text) },
                leadingIcon = if (selectedItem == text) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(androidx.compose.material3.FilterChipDefaults.IconSize)) }
                } else null
            )
        }
    }
}

@Composable
fun DeckSettingsDialog(
    initialNormalizationType: NormalizationType,
    initialDeckSort: DeckSortMode,
    onDismiss: () -> Unit,
    onSave: (NormalizationType, DeckSortMode) -> Unit,
    onClearReviewData: () -> Unit // --- ADDED PARAMETER ---
) {
    val dimensions = LocalStudiareDimensions.current
    var normalizationType by remember { mutableStateOf(initialNormalizationType) }
    var sortType by remember { mutableStateOf(initialDeckSort) }
    var showClearConfirm by remember { mutableStateOf(false) } // Local state for confirmation

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(getText(R.string.review_data_clear_question)) },
            text = { Text(getText(R.string.review_data_clear_message)) },
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
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
                TextButton(onClick = { showClearConfirm = false }) { Text(getText(R.string.cancel)) }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .verticalScroll(rememberScrollState())
            ) {
                Text("Deck Options", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Normalization Section
                DialogSection(title = getText(R.string.case_normalization)) {
                    SettingsFilterChipGroup(
                        options = NormalizationType.entries.map { it.asString() },
                        selectedItem = normalizationType.asString(),
                        onSelect = { normalizationType = it.toNormalizationType() }
                    )
                }

                // Sorting Section
                DialogSection(title = stringResource(R.string.sort_card_order)) {
                    SettingsFilterChipGroup(
                        options = DeckSortMode.entries.map { it.asString() }, // Fixed to map to Strings
                        selectedItem = sortType.asString(),
                        onSelect = { sortType = it.toDeckSortMode() }
                    )
                }

                Spacer(Modifier.height(dimensions.spacingLarge))

                // --- NEW BUTTON SECTION ---
                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(getText(R.string.review_data_clear))
                }
                // --------------------------

                Spacer(Modifier.height(dimensions.spacingMedium))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(onClick = { onSave(normalizationType, sortType) }) {
                        Text(getText(R.string.save_and_close))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsRadioGroup(options: List<String>, selectedItem: String, onSelect: (String) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    Column {
        options.forEach { text ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                    .clickable { onSelect(text) }
                    .padding(vertical = dimensions.paddingSmall)
            ) {
                RadioButton(
                    selected = selectedItem == text,
                    onClick = { onSelect(text) }
                )
                Spacer(Modifier.width(dimensions.spacingSmall))
                Text(text)
            }
        }
    }
}

@Composable
fun CardSettingsDialog(
    currentIsSuspended: Boolean,
    currentFlag: CardFlag,
    onDismiss: () -> Unit,
    onSave: (Boolean, CardFlag) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var isSuspended by remember { mutableStateOf(currentIsSuspended) }
    var flagText by remember { mutableStateOf(currentFlag.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(getText(R.string.card_settings)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(getText(R.string.suspended), modifier = Modifier.weight(1f))
                    Switch(
                        checked = isSuspended,
                        onCheckedChange = { isSuspended = it }
                    )
                }

                OutlinedTextField(
                    value = flagText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) flagText = it },
                    label = { Text(getText(R.string.flag)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        isSuspended,
                        currentFlag
                    )
                }
            ) { Text(getText(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
        }
    )
}

@Composable
fun CustomVerticalScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val totalItemsCount = listState.layoutInfo.totalItemsCount
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0f) }

    if (totalItemsCount > 1) {
        Box(
            modifier = modifier
                .onSizeChanged { barHeight = it.height.toFloat() }
                .pointerInput(totalItemsCount, barHeight) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            if (barHeight > 0) {
                                val percentage = (offset.y / barHeight).coerceIn(0f, 1f)
                                val index = (percentage * (totalItemsCount - 1)).toInt()
                                coroutineScope.launch { listState.scrollToItem(index) }
                            }
                        },
                        onVerticalDrag = { change, _ ->
                            if (barHeight > 0) {
                                val percentage = (change.position.y / barHeight).coerceIn(0f, 1f)
                                val index = (percentage * (totalItemsCount - 1)).toInt()
                                coroutineScope.launch { listState.scrollToItem(index) }
                            }
                        }
                    )
                }
        ) {
            if (barHeight > 0 && totalItemsCount > 0) {
                val visibleItems = listState.layoutInfo.visibleItemsInfo.size
                val thumbHeightPx = (barHeight * visibleItems / totalItemsCount).coerceAtLeast(100f)
                val firstVisible = listState.firstVisibleItemIndex
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