package net.ericclark.studiare.screens

import androidx.activity.compose.BackHandler
import net.ericclark.studiare.TooltipFilledTonalIconButton
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.TooltipIconButton
import net.ericclark.studiare.withShortcut
import androidx.compose.ui.input.key.Key
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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import net.ericclark.studiare.components.*
import net.ericclark.studiare.CustomTopAppBar
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.TextField
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.mutableLongStateOf
import net.ericclark.studiare.R
import androidx.compose.ui.platform.LocalContext
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.LocalWindowWidthSizeClass

/**
 * A screen for creating a new deck or editing an existing one.
 * It provides fields for the deck name and a list of cards with fronts, backs, and difficulties.
 * @param navController The NavController for navigating back.
 * @param deckWithCards The existing deck to edit, or null if creating a new one.
 * @param viewModel The ViewModel providing data and business logic.
 */
@Composable
fun DeckEditorScreen(
    navController: NavController,
    deckWithCards: DeckWithCards?,
    viewModel: FlashcardViewModel
) {
    val context = LocalContext.current
    val dimensions = LocalStudiareDimensions.current
    val motionScheme = MaterialTheme.motionScheme
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    // State for the deck name
    var deckName by remember { mutableStateOf(deckWithCards?.deck?.name ?: "") }

    // State for deck settings
    var normalizationType by remember { mutableStateOf(deckWithCards?.deck?.normalizationType ?: NormalizationType.NONE) }
    var sortType by remember { mutableStateOf(deckWithCards?.deck?.deckSortMode ?: DeckSortMode.DATE_ADDED_OLD_TO_NEW) }

    // State for Languages (Default to system default if new, or load from deck)
    var frontLanguage by remember { mutableStateOf(deckWithCards?.deck?.frontLanguage ?: Locale.getDefault().language) }
    var backLanguage by remember { mutableStateOf(deckWithCards?.deck?.backLanguage ?: Locale.getDefault().language) }

    // State for Note Templates and Advanced Editor
    // Keyed on the deck's id so that if `deckWithCards` is still null/loading on this screen's
    // first composition (e.g. the ViewModel's deck list hasn't finished loading yet) and then
    // resolves moments later, this re-initializes from the real saved templates instead of
    // permanently locking onto the empty-list fallback it saw on that first pass.
    var frontNoteTemplates by remember(deckWithCards?.deck?.id) { mutableStateOf(deckWithCards?.deck?.frontNoteTemplates ?: emptyList()) }
    var backNoteTemplates by remember(deckWithCards?.deck?.id) { mutableStateOf(deckWithCards?.deck?.backNoteTemplates ?: emptyList()) }
    var showAdvancedEditor by remember { mutableStateOf(false) }

    // Linkage State
    var linkageSettings by remember { mutableStateOf(deckWithCards?.deck?.linkageSettings ?: LinkageSettings()) }
    var showLinkageDialog by remember { mutableStateOf(false) }
    val isChildSet = deckWithCards?.deck?.parentDeckId != null

    // Rich Text Editor State
    var richTextCardIndex by remember { mutableStateOf<Int?>(null) }
    var richTextEditorTarget by remember { mutableStateOf<String?>(null) } // "front", "back", "frontNote_X", "backNote_X"
    var richTextInitialHtml by remember { mutableStateOf("") }
    var richTextTitle by remember { mutableStateOf("") }

    // State for the filter text to search for specific cards
    var filterText by remember { mutableStateOf("") }
    val lazyListState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    // State for Language Dialog

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
                frontRichTextInfo = mutableStateOf(it.frontRichText),
                isFrontRichText = mutableStateOf(it.frontRichText != null && it.frontRichText!!.isNotBlank()),
                back = mutableStateOf(it.back),
                backRichTextInfo = mutableStateOf(it.backRichText),
                isBackRichText = mutableStateOf(it.backRichText != null && it.backRichText!!.isNotBlank()),
                frontNotes = mutableStateOf(it.frontNotes),
                backNotes = mutableStateOf(it.backNotes),
                difficulty = mutableStateOf(it.difficulty),
                isKnown = mutableStateOf(it.isKnown),
                reviewedCount = mutableStateOf(it.reviewedCount),
                gradedAttempts = mutableStateOf(it.gradedAttempts),
                incorrectAttempts = mutableStateOf(it.incorrectAttempts),
                reviewLogs = mutableStateOf(it.reviewLogs),
                absoluteDueDate = mutableStateOf(it.absoluteDueDate),
                tags = mutableStateOf(it.tags),
                isSuspended = mutableStateOf(it.isSuspended),
                flag = mutableStateOf(it.flag),
                createdAt = mutableLongStateOf(it.createdAt),
                updatedAt = mutableStateOf(it.updatedAt),
                fsrsStability = mutableStateOf(it.fsrsStability),
                fsrsDifficulty = mutableStateOf(it.fsrsDifficulty),
                fsrsElapsedDays = mutableStateOf(it.fsrsElapsedDays),
                fsrsScheduledDays = mutableStateOf(it.fsrsScheduledDays),
                fsrsState = mutableStateOf(it.fsrsState),
                fsrsLastReview = mutableStateOf(it.fsrsLastReview),
                fsrsLapses = mutableStateOf(it.fsrsLapses),
                lastReviewDurationMs = mutableStateOf(it.lastReviewDurationMs)
            )
        } ?: listOf(
            // Start with one empty card if creating a new deck
            CardEditorState(
                id = UUID.randomUUID().toString(),
                front = mutableStateOf(""),
                frontRichTextInfo = mutableStateOf(null),
                isFrontRichText = mutableStateOf(false),
                back = mutableStateOf(""),
                backRichTextInfo = mutableStateOf(null),
                isBackRichText = mutableStateOf(false),
                frontNotes = mutableStateOf(deckWithCards?.deck?.frontNoteTemplates ?: emptyList()),
                backNotes = mutableStateOf(deckWithCards?.deck?.backNoteTemplates ?: emptyList()),
                difficulty = mutableStateOf(DifficultySetting.FIVE),
                isKnown = mutableStateOf(false),
                reviewedCount = mutableStateOf(0),
                gradedAttempts = mutableStateOf(emptyList()),
                incorrectAttempts = mutableStateOf(emptyList()),
                reviewLogs = mutableStateOf(emptyList()),
                absoluteDueDate = mutableStateOf(null),
                tags = mutableStateOf(emptyList()),
                isSuspended = mutableStateOf(false),
                flag = mutableStateOf(CardFlag.NONE),
                createdAt = mutableLongStateOf(System.currentTimeMillis()),
                updatedAt = mutableStateOf(System.currentTimeMillis())
            )
        )
        mutableStateListOf(*sortCardStates(initialCards, sortType).toTypedArray())
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
                    frontRichText = it.frontRichText,
                    back = it.back,
                    backRichText = it.backRichText,
                    frontNotes = it.frontNotes,
                    backNotes = it.backNotes,
                    difficulty = it.difficulty,
                    isKnown = it.isKnown,
                    reviewedCount = it.reviewedCount,
                    gradedAttempts = it.gradedAttempts,
                    incorrectAttempts = it.incorrectAttempts,
                    reviewLogs = it.reviewLogs,
                    absoluteDueDate = it.absoluteDueDate,
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
                    frontRichText = null,
                    back = "",
                    backRichText = null,
                    frontNotes = emptyList(),
                    backNotes = emptyList(),
                    difficulty = DifficultySetting.FIVE,
                    isKnown = false,
                    reviewedCount = 0,
                    gradedAttempts = emptyList(),
                    incorrectAttempts = emptyList(),
                    reviewLogs = emptyList(),
                    absoluteDueDate = null,
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
                    frontRichText = it.frontRichTextInfo.value,
                    back = it.back.value,
                    backRichText = it.backRichTextInfo.value,
                    frontNotes = it.frontNotes.value,
                    backNotes = it.backNotes.value,
                    difficulty = it.difficulty.value,
                    isKnown = it.isKnown.value,
                    reviewedCount = it.reviewedCount.value,
                    gradedAttempts = it.gradedAttempts.value,
                    incorrectAttempts = it.incorrectAttempts.value,
                    reviewLogs = it.reviewLogs.value,
                    absoluteDueDate = it.absoluteDueDate.value,
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
                    frontNoteTemplates != (deckWithCards?.deck?.frontNoteTemplates ?: emptyList<NoteField>()) ||
                    backNoteTemplates != (deckWithCards?.deck?.backNoteTemplates ?: emptyList<NoteField>()) ||
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
            val normalize = { text: String ->
                when (type) {
                    NormalizationType.UPPERCASE_FIRST_LETTER -> text.replaceFirstChar { it.uppercase() }
                    NormalizationType.UPPERCASE_ALL_LETTERS -> text.uppercase()
                    NormalizationType.UPPERCASE_EACH_WORD -> text.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
                    NormalizationType.LOWERCASE_FIRST_LETTER -> text.replaceFirstChar { it.lowercase() }
                    NormalizationType.LOWERCASE_ALL_LETTERS -> text.lowercase()
                    NormalizationType.LOWERCASE_EACH_WORD -> text.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.lowercase() } }
                    NormalizationType.NONE -> text
                }
            }
            card.front.value = normalize(card.front.value)
            card.back.value = normalize(card.back.value)
            card.frontNotes.value = card.frontNotes.value.map { note ->
                if (note.type == MediaType.PLAIN_TEXT) note.copy(content = normalize(note.content)) else note
            }
            card.backNotes.value = card.backNotes.value.map { note ->
                if (note.type == MediaType.PLAIN_TEXT) note.copy(content = normalize(note.content)) else note
            }
        }
    }

    fun applySorting(type: DeckSortMode) {
        val sorted = sortCardStates(cards, type)
        cards.clear()
        cards.addAll(sorted)
    }

    // --- Save Action ---
    val saveAction = {
        // Sanitize field names to prevent empty or duplicate fields
        var templateCounter = 1
        val usedTemplateNames = mutableSetOf<String>()
        fun sanitizeTemplates(templates: List<NoteField>): List<NoteField> {
            return templates.map { temp ->
                var finalName = temp.name.trim()
                if (finalName.isBlank()) finalName = "Field $templateCounter"
                while (usedTemplateNames.contains(finalName) || finalName == "Front" || finalName == "Back") {
                    templateCounter++
                    finalName = if (temp.name.trim().isBlank()) "Field $templateCounter" else "${temp.name.trim()} $templateCounter"
                }
                usedTemplateNames.add(finalName)
                temp.copy(name = finalName)
            }
        }
        frontNoteTemplates = sanitizeTemplates(frontNoteTemplates)
        backNoteTemplates = sanitizeTemplates(backNoteTemplates)

        cards.forEach { cardState ->
            var fieldCounter = 1
            val usedNames = mutableSetOf<String>()

            fun sanitizeNotes(notes: List<NoteField>): List<NoteField> {
                return notes.map { note ->
                    var finalName = note.name.trim()
                    if (finalName.isBlank()) finalName = "Field $fieldCounter"
                    while (usedNames.contains(finalName) || finalName == "Front" || finalName == "Back") {
                        fieldCounter++
                        finalName = if (note.name.trim().isBlank()) "Field $fieldCounter" else "${note.name.trim()} $fieldCounter"
                    }
                    usedNames.add(finalName)
                    note.copy(name = finalName)
                }
            }
            cardState.frontNotes.value = sanitizeNotes(cardState.frontNotes.value)
            cardState.backNotes.value = sanitizeNotes(cardState.backNotes.value)
        }

        val cardData = cards.mapIndexed { index, it ->
            CardDataForSave(
                id = it.id,
                front = it.front.value.trim(),
                frontRichText = it.frontRichTextInfo.value?.trim(),
                back = it.back.value.trim(),
                backRichText = it.backRichTextInfo.value?.trim(),
                frontNotes = it.frontNotes.value,
                backNotes = it.backNotes.value,
                difficulty = it.difficulty.value,
                isKnown = it.isKnown.value,
                reviewedCount = it.reviewedCount.value,
                gradedAttempts = it.gradedAttempts.value,
                incorrectAttempts = it.incorrectAttempts.value,
                reviewLogs = it.reviewLogs.value,
                absoluteDueDate = it.absoluteDueDate.value,
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
            deckWithCards?.deck?.dailyReviewLimit ?: 200,
            frontNoteTemplates, // NEW: Pass templates
            backNoteTemplates   // NEW: Pass templates
        )

        if (viewModel.editorDuplicateResult.value == null) {
            if (isChildSet && deckWithCards != null) {
                viewModel.updateLinkageSettings(deckWithCards.deck.id, linkageSettings)
            }
            navController.popBackStack()
        }
    }

    // --- Dialogs ---
    if (showSettingsDialog) {
        DeckSettingsDialog(
            initialNormalizationType = normalizationType,
            initialDeckSort = sortType,
            initialFrontLanguage = frontLanguage, // NEW: Pass initial languages
            initialBackLanguage = backLanguage,
            onDismiss = { showSettingsDialog = false },
            onSave = { newNorm, newSort, newFrontLang, newBackLang -> // NEW: Receive updated languages
                normalizationType = newNorm
                sortType = newSort
                frontLanguage = newFrontLang
                backLanguage = newBackLang
                applyNormalization(newNorm)
                applySorting(newSort)
                showSettingsDialog = false
            },
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
                    card.reviewLogs.value = emptyList()
                }
                showSettingsDialog = false
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

    if (showAdvancedEditor) {
        AdvancedDeckEditorDialog(
            frontTemplates = frontNoteTemplates,
            backTemplates = backNoteTemplates,
            onDismiss = { showAdvancedEditor = false },
            onSave = { newFront, newBack, addToExistingCards ->
                frontNoteTemplates = newFront
                backNoteTemplates = newBack

                if (addToExistingCards) {
                    cards.forEach { card ->
                        val currentFrontNames = card.frontNotes.value.map { it.name }
                        val currentBackNames = card.backNotes.value.map { it.name }

                        val missingFront = newFront
                            .filter { it.name !in currentFrontNames }
                            .map { it.copy(content = "") } // Clear content just in case

                        val missingBack = newBack
                            .filter { it.name !in currentBackNames }
                            .map { it.copy(content = "") }

                        if (missingFront.isNotEmpty()) {
                            card.frontNotes.value = card.frontNotes.value + missingFront
                        }
                        if (missingBack.isNotEmpty()) {
                            card.backNotes.value = card.backNotes.value + missingBack
                        }
                    }
                }

                showAdvancedEditor = false
            }
        )
    }

    // Linkage Dialog Trigger
    if (showLinkageDialog) {
        LinkageSettingsDialog(
            currentSettings = linkageSettings,
            onDismiss = { showLinkageDialog = false },
            onSave = { newSettings ->
                linkageSettings = newSettings
                showLinkageDialog = false
            }
        )
    }

    if (richTextCardIndex != null && richTextEditorTarget != null) {
        val cardState = cards[richTextCardIndex!!]
        RichTextEditorDialog(
            initialHtml = richTextInitialHtml,
            title = richTextTitle,
            onDismiss = {
                richTextCardIndex = null
                richTextEditorTarget = null
            },
            onSave = { savedHtml ->
                val plainText = savedHtml.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()

                when {
                    richTextEditorTarget == "front" -> {
                        cardState.frontRichTextInfo.value = savedHtml
                        cardState.front.value = plainText
                    }
                    richTextEditorTarget == "back" -> {
                        cardState.backRichTextInfo.value = savedHtml
                        cardState.back.value = plainText
                    }
                    richTextEditorTarget?.startsWith("frontNote_") == true -> {
                        val index = richTextEditorTarget!!.substringAfter("_").toInt()
                        val currentList = cardState.frontNotes.value.toMutableList()
                        currentList[index] = currentList[index].copy(content = savedHtml)
                        cardState.frontNotes.value = currentList
                    }
                    richTextEditorTarget?.startsWith("backNote_") == true -> {
                        val index = richTextEditorTarget!!.substringAfter("_").toInt()
                        val currentList = cardState.backNotes.value.toMutableList()
                        currentList[index] = currentList[index].copy(content = savedHtml)
                        cardState.backNotes.value = currentList
                    }
                }
                richTextCardIndex = null
                richTextEditorTarget = null
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

    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    Scaffold(
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.DECK_EDITOR,
                title = {
                    Text(
                        if (showDeckNameInAppBar) deckName
                        else (if (deckWithCards == null) getText(R.string.deck_create)
                        else getText(R.string.deck_edit)),
                        textAlign = TextAlign.Left,
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = {
                        if (isDirty) {
                            showUnsavedDialog = true
                        } else {
                            navController.popBackStack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                    }
                },
                actions = {
                    TooltipIconButton(description = getText(R.string.advanced_editor), 
                        onClick = { showAdvancedEditor = true },
                        enabled = !(isChildSet && linkageSettings.linkFieldConfig)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = getText(R.string.advanced_editor))
                    }
                    if (isChildSet) {
                        TooltipIconButton(description = getText(R.string.linkage_settings), onClick = { showLinkageDialog = true }) {
                            Icon(Icons.Default.Link, contentDescription = getText(R.string.linkage_settings))
                        }
                    }
                    // Action 1: Settings (Icon Button)
                    TooltipIconButton(description = getText(R.string.deck_settings), onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, getText(R.string.deck_settings))
                    }

                    Spacer(Modifier.width(8.dp))

                    // Action 2: Prominent Save (Filled Tonal Button)
                    FilledTonalButton(
                        onClick = { saveAction() },
                        enabled = deckName.isNotBlank() && cards.any { it.front.value.isNotBlank() && it.back.value.isNotBlank() },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(getText(R.string.save))
                    }
                }
            )
        },
        floatingActionButton = {
            val addCard: () -> Unit = {
                cards.add(CardEditorState(
                    id = UUID.randomUUID().toString(), front = mutableStateOf(""), frontRichTextInfo = mutableStateOf(null), isFrontRichText = mutableStateOf(false),
                    back = mutableStateOf(""), backRichTextInfo = mutableStateOf(null), isBackRichText = mutableStateOf(false),
                    frontNotes = mutableStateOf(frontNoteTemplates), backNotes = mutableStateOf(backNoteTemplates),
                    difficulty = mutableStateOf(DifficultySetting.FIVE), isKnown = mutableStateOf(false),
                    reviewedCount = mutableStateOf(0), gradedAttempts = mutableStateOf(emptyList()),
                    incorrectAttempts = mutableStateOf(emptyList()), reviewLogs = mutableStateOf(emptyList()),
                    absoluteDueDate = mutableStateOf(null), tags = mutableStateOf(emptyList()),
                    isSuspended = mutableStateOf(false), flag = mutableStateOf(CardFlag.NONE),
                    createdAt = mutableLongStateOf(System.currentTimeMillis()), updatedAt = mutableStateOf(System.currentTimeMillis())))
                // New cards are appended to the end, so their index in the list matches cards.size-1
                // — only true 1:1 against what's on-screen when no search filter is narrowing it down.
                coroutineScope.launch {
                    if (filterText.isBlank()) {
                        lazyListState.scrollToItem((cards.size - 1).coerceAtLeast(0))
                    }
                }
            }
            ExtendedFloatingActionButton(
                onClick = addCard,
                modifier = Modifier.withShortcut(Key.N, "N", id = "deck_editor.add_card") { addCard() },
                expanded = lazyListState.firstVisibleItemIndex == 0, // M3 Expressive: Expanded at top, shrinks on scroll
                icon = { Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add)) },
                text = { Text(getText(R.string.card_add)) },
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box {
                if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                    // WIDE SCREEN LAYOUT
                    Row(
                        modifier = Modifier.padding(dimensions.paddingMedium),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                    ) {
                        Column(modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 100.dp)) {
                            TextField(
                                value = deckName,
                                onValueChange = { deckName = it },
                                label = { Text(getText(R.string.deck_name)) },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )

                            Spacer(Modifier.height(dimensions.spacingMedium))
                            if (deckWithCards != null) {
                                DeckStats(deckWithCards = deckWithCards)
                            }
                            // M3 Expressive: Permanent Search/Filter bar instead of hidden behind a toggle
                            TextField(
                                value = filterText,
                                onValueChange = { filterText = it },
                                placeholder = { Text(getText(R.string.cards_filter_)) },
                                modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                ),
                                singleLine = true
                            )
                        }
                        // Right column for the list of cards
                        Column(modifier = Modifier.weight(1.75f)) {
                            Box(modifier = Modifier.weight(1f)) {
                                LazyColumn(
                                    state = lazyListState,
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                                    contentPadding = PaddingValues(bottom = 80.dp)
                                ) {
                                    itemsIndexed (
                                        filteredCards,
                                        key = { _, item -> item.id }) { index, cardState ->
                                        CardEditor(
                                            cardState = cardState,
                                            cardNumber = index + 1,
                                            totalCards = filteredCards.size,
                                            onDelete = {
                                                if (cards.size > 1) {
                                                    val removedIndex = cards.indexOf(cardState)
                                                    cards.remove(cardState)
                                                    coroutineScope.launch {
                                                        snackbarHostState.currentSnackbarData?.dismiss()
                                                        val snackbarJob = launch {
                                                            val result = snackbarHostState.showSnackbar(
                                                                message = getText(context, R.string.card_deleted),
                                                                actionLabel = getText(context, R.string.undo),
                                                                duration = androidx.compose.material3.SnackbarDuration.Indefinite
                                                            )
                                                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                                                cards.add(removedIndex.coerceIn(0, cards.size), cardState)
                                                            }
                                                        }
                                                        kotlinx.coroutines.delay(5000)
                                                        snackbarJob.cancel()
                                                    }
                                                }
                                            },
                                            onKnownClick = {
                                                cardState.isKnown.value = !cardState.isKnown.value
                                            },
                                            allTags = allTags,
                                            currentDeckTags = currentDeckTags,
                                            onUpdateTags = { newTags ->
                                                cardState.tags.value = newTags.toList()
                                            },
                                            onCreateTag = { name, color ->
                                                viewModel.saveTagDefinition(
                                                    TagDefinition(
                                                        name = name,
                                                        color = color
                                                    )
                                                )
                                            },
                                            onOpenRichTextEditor = { target, initialHtml, title ->
                                                richTextCardIndex = index
                                                richTextEditorTarget = target
                                                richTextInitialHtml = initialHtml
                                                richTextTitle = title
                                            },
                                            snackbarHostState = snackbarHostState,
                                            coroutineScope = coroutineScope,
                                            modifier = Modifier.animateItem(
                                                fadeInSpec = motionScheme.defaultEffectsSpec(),
                                                fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                                placementSpec = motionScheme.defaultSpatialSpec()
                                            )
                                        )
                                    }
                                }
                                if (filteredCards.size > 3)
                                {
                                    // Custom Fast Scroll Slider (Wide Mode)
                                    CustomVerticalScrollbar(
                                        listState = lazyListState,
                                        modifier = Modifier
                                            .align(Alignment.CenterEnd)
                                            .width(30.dp)
                                            .fillMaxHeight()
                                            .padding(vertical = dimensions.paddingMedium)
                                    )
                                }
                            }
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
                                            androidx.compose.material3.TextField(
                                                value = deckName,
                                                onValueChange = { deckName = it },
                                                label = { Text(getText(R.string.deck_name)) },
                                                modifier = Modifier.weight(1f),
                                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                                singleLine = true,
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                )
                                            )
                                            if (deckWithCards != null) {
                                                TooltipFilledTonalIconButton(description = getText(R.string.toggle_stats), onClick = { showStats = !showStats }) {
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
                                            TextField(
                                                value = filterText,
                                                onValueChange = { filterText = it },
                                                placeholder = { Text(getText(R.string.cards_filter_)) },
                                                modifier = Modifier.fillMaxWidth(),
                                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                                colors = TextFieldDefaults.colors(
                                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                                    focusedIndicatorColor = Color.Transparent,
                                                    unfocusedIndicatorColor = Color.Transparent
                                                ),
                                                singleLine = true
                                            )
                                        Spacer(Modifier.height(dimensions.spacingMedium))
                                        Text(getText(R.string.cards), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = dimensions.paddingSmall))
                                    }
                                }
                                itemsIndexed(filteredCards, key = { _, item -> item.id }) { index, cardState ->
                                    CardEditor(
                                        cardState = cardState,
                                        cardNumber = index + 1,
                                        totalCards = filteredCards.size,
                                        onDelete = {
                                            if (cards.size > 1) {
                                                val removedIndex = cards.indexOf(cardState)
                                                cards.remove(cardState)
                                                coroutineScope.launch {
                                                    snackbarHostState.currentSnackbarData?.dismiss()
                                                    val snackbarJob = launch {
                                                        val result = snackbarHostState.showSnackbar(
                                                            message = "Card deleted",
                                                            actionLabel = "Undo",
                                                            duration = androidx.compose.material3.SnackbarDuration.Indefinite
                                                        )
                                                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                                            cards.add(removedIndex.coerceIn(0, cards.size), cardState)
                                                        }
                                                    }
                                                    kotlinx.coroutines.delay(5000)
                                                    snackbarJob.cancel()
                                                }
                                            }
                                        },
                                        onKnownClick = { cardState.isKnown.value = !cardState.isKnown.value },
                                        allTags = allTags,
                                        currentDeckTags = currentDeckTags,
                                        onUpdateTags = { newTags -> cardState.tags.value = newTags.toList() },
                                        onCreateTag = { name, color -> viewModel.saveTagDefinition(
                                            TagDefinition(
                                                name = name,
                                                color = color
                                            )
                                        ) },
                                        onOpenRichTextEditor = { target, initialHtml, title ->
                                            richTextCardIndex = index
                                            richTextEditorTarget = target
                                            richTextInitialHtml = initialHtml
                                            richTextTitle = title
                                        },
                                        snackbarHostState = snackbarHostState,
                                        coroutineScope = coroutineScope,
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                            placementSpec = motionScheme.defaultSpatialSpec()
                                        )
                                    )
                                }
                            }

                            if (filteredCards.size > 3)
                            {
                                // Custom Fast Scroll Slider (Narrow Mode)
                                CustomVerticalScrollbar(
                                    listState = lazyListState,
                                    modifier = Modifier
                                        .align(Alignment.CenterEnd)
                                        .width(30.dp)
                                        .fillMaxHeight()
                                        .padding(vertical = dimensions.paddingMedium)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
