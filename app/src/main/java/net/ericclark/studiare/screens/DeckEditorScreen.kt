package net.ericclark.studiare.screens

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
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
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlin.math.roundToInt
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import net.ericclark.studiare.R
import androidx.compose.ui.platform.LocalContext
import net.ericclark.studiare.AnimatedHamburgerMenu
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.LocalWindowWidthSizeClass
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

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
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    // State for the deck name
    var deckName by remember { mutableStateOf(deckWithCards?.deck?.name ?: "") }

    // State for deck settings
    var normalizationType by remember { mutableStateOf(deckWithCards?.deck?.normalizationType ?: NormalizationType.NONE) }
    var sortType by remember { mutableStateOf(deckWithCards?.deck?.deckSortMode ?: DeckSortMode.DATE_ADDED_OLD_TO_NEW) }

    // State for Languages (Default to system default if new, or load from deck)
    var frontLanguage by remember { mutableStateOf(deckWithCards?.deck?.frontLanguage ?: Locale.getDefault().language) }
    var backLanguage by remember { mutableStateOf(deckWithCards?.deck?.backLanguage ?: Locale.getDefault().language) }

    // NEW: State for Note Templates and Advanced Editor
    var frontNoteTemplates by remember { mutableStateOf(deckWithCards?.deck?.frontNoteTemplates ?: emptyList()) }
    var backNoteTemplates by remember { mutableStateOf(deckWithCards?.deck?.backNoteTemplates ?: emptyList()) }
    var showAdvancedEditor by remember { mutableStateOf(false) }

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
                frontRichTextInfo = mutableStateOf(null),
                isFrontRichText = mutableStateOf(false),
                back = mutableStateOf(""),
                backRichTextInfo = mutableStateOf(null),
                isBackRichText = mutableStateOf(false),
                frontNotes = mutableStateOf(deckWithCards?.deck?.frontNoteTemplates ?: emptyList()),
                backNotes = mutableStateOf(deckWithCards?.deck?.backNoteTemplates ?: emptyList()),
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
                    AnimatedHamburgerMenu(viewModel = viewModel, windowWidthSizeClass = windowWidthSizeClass)
                },
                actions = {
                    IconButton(onClick = { showAdvancedEditor = true }) {
                        Icon(Icons.Default.Build, contentDescription = "Advanced Editor")
                    }
                    // Action 1: Settings (Icon Button)
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, getText(R.string.deck_settings))
                    }

                    Spacer(Modifier.width(8.dp))

                    // Action 2: Prominent Save (Filled Tonal Button)
                    FilledTonalButton(
                        onClick = { saveAction() },
                        enabled = deckName.isNotBlank() && cards.any { it.front.value.isNotBlank() && it.back.value.isNotBlank() },
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(end = 8.dp)
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(getText(R.string.save))
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = {
                    cards.add(CardEditorState(
                        id = UUID.randomUUID().toString(), front = mutableStateOf(""), frontRichTextInfo = mutableStateOf(null), isFrontRichText = mutableStateOf(false),
                        back = mutableStateOf(""), backRichTextInfo = mutableStateOf(null), isBackRichText = mutableStateOf(false),
                        frontNotes = mutableStateOf(frontNoteTemplates), backNotes = mutableStateOf(backNoteTemplates),
                        difficulty = mutableStateOf(DifficultySetting.ONE), isKnown = mutableStateOf(false),
                        reviewedCount = mutableStateOf(0), gradedAttempts = mutableStateOf(emptyList()),
                        incorrectAttempts = mutableStateOf(emptyList()), tags = mutableStateOf(emptyList()),
                        isSuspended = mutableStateOf(false), flag = mutableStateOf(CardFlag.NONE),
                        createdAt = mutableLongStateOf(System.currentTimeMillis()), updatedAt = mutableStateOf(System.currentTimeMillis())))
                },
                expanded = lazyListState.firstVisibleItemIndex == 0, // M3 Expressive: Expanded at top, shrinks on scroll
                icon = { Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add)) },
                text = { Text(getText(R.string.card_add)) },
                shape = CircleShape
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
                                            coroutineScope = coroutineScope
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
                                                FilledTonalIconButton(onClick = { showStats = !showStats }) {
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
                                        coroutineScope = coroutineScope
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

@Composable
fun LanguageDropdown(
    languages: List<Pair<String, String>>,
    selectedCode: String,
    onLanguageSelected: (String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var expanded by remember { mutableStateOf(false) }
    val selectedName = languages.find { it.first == selectedCode }?.second ?: getText(R.string.unknown)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            // M3 Expressive Update: Tinted container with no harsh outline borders
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = CircleShape // Enforce the M3 Expressive pill shape
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
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // Larger rounding
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
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
    allTags: List<TagDefinition>,
    currentDeckTags: Set<String>,
    onUpdateTags: (Set<String>) -> Unit,
    onCreateTag: (String, String) -> Unit,
    onOpenRichTextEditor: (target: String, initialHtml: String, title: String) -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope
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

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(Modifier
            .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
            .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                SuggestionChip(
                    onClick = { },
                    label = { Text(stringResource(R.string.blank_of_blank, cardNumber, totalCards)) },
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = null
                )
                Spacer(modifier = Modifier.weight(1f))

                val deleteInteractionSource = remember { MutableInteractionSource() }
                val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
                val deleteScale by animateFloatAsState(
                    targetValue = if (isDeletePressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "deleteSquish"
                )
                IconButton(onClick = onDelete, interactionSource = deleteInteractionSource, modifier = Modifier.scale(deleteScale)) {
                    Icon(Icons.Default.Delete, getText(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }

                val settingsInteractionSource = remember { MutableInteractionSource() }
                val isSettingsPressed by settingsInteractionSource.collectIsPressedAsState()
                val settingsScale by animateFloatAsState(
                    targetValue = if (isSettingsPressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "settingsSquish"
                )
                IconButton(onClick = { showSettingsDialog = true}, interactionSource = settingsInteractionSource, modifier = Modifier.scale(settingsScale)) {
                    Icon(Icons.Default.Settings, getText(R.string.card_settings), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(dimensions.spacingSmall))

            // --- FRONT ---
            CardSideEditor(
                sideLabel = CardSide.FRONT.asString(),
                plainText = cardState.front.value,
                onPlainTextChange = { cardState.front.value = it },
                isRichText = cardState.isFrontRichText.value,
                onToggleRichText = { isRich ->
                    cardState.isFrontRichText.value = isRich
                    if (!isRich) {
                        cardState.frontRichTextInfo.value = null
                        cardState.front.value = cardState.front.value.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                    }
                },
                onEditRichTextClick = {
                    onOpenRichTextEditor("front", cardState.frontRichTextInfo.value ?: cardState.front.value, "Edit Front (Rich Text)")
                },
                actionIcon = {
                    IconButton(onClick = {
                        cardState.frontNotes.value = cardState.frontNotes.value + NoteField("", "", MediaType.PLAIN_TEXT.toString())
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Front Note", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )

            cardState.frontNotes.value.forEachIndexed { index, note ->
                val enterTransition = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                AnimatedVisibility(
                    visibleState = enterTransition,
                    enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                ) {
                    DynamicNoteEditor(
                        note = note,
                        noteIndex = index,
                        onNoteChange = { updatedNote ->
                            val newList = cardState.frontNotes.value.toMutableList()
                            newList[index] = updatedNote
                            cardState.frontNotes.value = newList
                        },
                        onEditRichTextClick = {
                            onOpenRichTextEditor("frontNote_$index", note.content, "Edit ${note.name}")
                        },
                        onRemove = {
                            val removedNote = cardState.frontNotes.value[index]
                            val newList = cardState.frontNotes.value.toMutableList()
                            newList.removeAt(index)
                            cardState.frontNotes.value = newList

                            coroutineScope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = "Note removed",
                                    actionLabel = "Undo",
                                    duration = androidx.compose.material3.SnackbarDuration.Short
                                )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    val restoreList = cardState.frontNotes.value.toMutableList()
                                    restoreList.add(index.coerceIn(0, restoreList.size), removedNote)
                                    cardState.frontNotes.value = restoreList
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(dimensions.spacingMedium))

            // --- BACK ---
            CardSideEditor(
                sideLabel = CardSide.BACK.asString(),
                plainText = cardState.back.value,
                onPlainTextChange = { cardState.back.value = it },
                isRichText = cardState.isBackRichText.value,
                onToggleRichText = { isRich ->
                    cardState.isBackRichText.value = isRich
                    if (!isRich) {
                        cardState.backRichTextInfo.value = null
                        cardState.back.value = cardState.back.value.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                    }
                },
                onEditRichTextClick = {
                    onOpenRichTextEditor("back", cardState.backRichTextInfo.value ?: cardState.back.value, "Edit Back (Rich Text)")
                },
                actionIcon = {
                    IconButton(onClick = {
                        cardState.backNotes.value = cardState.backNotes.value + NoteField("", "", MediaType.PLAIN_TEXT.toString())
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Back Note", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            )

            cardState.backNotes.value.forEachIndexed { index, note ->
                val enterTransition = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                AnimatedVisibility(
                    visibleState = enterTransition,
                    enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                ) {
                    DynamicNoteEditor(
                        note = note,
                        noteIndex = index,
                        onNoteChange = { updatedNote ->
                            val newList = cardState.backNotes.value.toMutableList()
                            newList[index] = updatedNote
                            cardState.backNotes.value = newList
                        },
                        onEditRichTextClick = {
                            onOpenRichTextEditor("backNote_$index", note.content, "Edit ${note.name}")
                        },
                        onRemove = {
                            val removedNote = cardState.backNotes.value[index]
                            val newList = cardState.backNotes.value.toMutableList()
                            newList.removeAt(index)
                            cardState.backNotes.value = newList

                            coroutineScope.launch {
                                snackbarHostState.currentSnackbarData?.dismiss()
                                val result = snackbarHostState.showSnackbar(
                                    message = "Note removed",
                                    actionLabel = "Undo",
                                    duration = androidx.compose.material3.SnackbarDuration.Short // FIXED: Prevent permanent snackbar
                                )
                                if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                    val restoreList = cardState.frontNotes.value.toMutableList()
                                    restoreList.add(index.coerceIn(0, restoreList.size), removedNote)
                                    cardState.frontNotes.value = restoreList
                                }
                            }
                        }
                    )
                }
            }

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
                Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall).size(48.dp)) {
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
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        confirmButton = {
            Button(onClick = onSave, shape = CircleShape) { Text(getText(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDiscard, shape = CircleShape) { Text(getText(R.string.discard)) }
        },
    )
}

@Composable
fun DeckSettingsDialog(
    initialNormalizationType: NormalizationType,
    initialDeckSort: DeckSortMode,
    initialFrontLanguage: String,
    initialBackLanguage: String,
    onDismiss: () -> Unit,
    onSave: (NormalizationType, DeckSortMode, String, String) -> Unit,
    onClearReviewData: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var normalizationType by remember { mutableStateOf(initialNormalizationType) }
    var sortType by remember { mutableStateOf(initialDeckSort) }
    var frontLanguage by remember { mutableStateOf(initialFrontLanguage) }
    var backLanguage by remember { mutableStateOf(initialBackLanguage) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Moved from the old LanguageSelectionDialog
    val availableLanguages = remember {
        Locale.getAvailableLocales()
            .map { it.language to it.displayLanguage }
            .filter { it.second.isNotEmpty() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false },
            title = { Text(getText(R.string.review_data_clear_question)) },
            text = { Text(getText(R.string.review_data_clear_message)) },
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            confirmButton = {
                Button(
                    onClick = {
                        showClearConfirm = false
                        onClearReviewData()
                    },
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Clear Data") }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false }, shape = CircleShape) { Text(getText(R.string.cancel)) }
            }
        )
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier.padding(dimensions.paddingLarge)
            ) {
                Text("Deck Options", style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Scrollable content area
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false) // Allows scrolling without forcing full screen height
                        .verticalScroll(rememberScrollState())
                ) {
                    // NEW: Language Section
                    DialogSection(title = getText(R.string.language_set)) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        Text(
                            getText(R.string.language_front),
                            style = MaterialTheme.typography.labelMedium
                        )
                        LanguageDropdown(
                            languages = availableLanguages,
                            selectedCode = frontLanguage,
                            onLanguageSelected = { frontLanguage = it }
                        )
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        Text(
                            getText(R.string.language_back),
                            style = MaterialTheme.typography.labelMedium
                        )
                        LanguageDropdown(
                            languages = availableLanguages,
                            selectedCode = backLanguage,
                            onLanguageSelected = { backLanguage = it }
                        )
                    }
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

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
                        options = DeckSortMode.entries.map { it.asString() },
                        selectedItem = sortType.asString(),
                        onSelect = { sortType = it.toDeckSortMode() }
                    )
                }

                Spacer(Modifier.height(dimensions.spacingLarge))

                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(getText(R.string.review_data_clear))
                }
            }
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = CircleShape) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(onClick = { onSave(normalizationType, sortType, frontLanguage, backLanguage) }, shape = CircleShape) {
                        Text(getText(R.string.save_and_close))
                    }
                }
            }
        }
    }
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
            FilterChip(
                selected = selectedItem == text,
                onClick = { onSelect(text) },
                modifier = Modifier.animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                ),
                label = { Text(text, maxLines = 1, softWrap = false) },
                leadingIcon = if (selectedItem == text) {
                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(
                        FilterChipDefaults.IconSize)) }
                } else null
            )
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

                //Button Group instead of a Switch for distinct state choices
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = !isSuspended,
                        onClick = { isSuspended = false },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text("Active") } // (You may want to extract "Active" to strings.xml)
                    SegmentedButton(
                        selected = isSuspended,
                        onClick = { isSuspended = true },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(getText(R.string.suspended)) }
                }

                TextField(
                    value = flagText,
                    onValueChange = { if (it.all { char -> char.isDigit() }) flagText = it },
                    label = { Text(getText(R.string.flag)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
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
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        isSuspended,
                        currentFlag
                    )
                },
                shape = CircleShape
            ) { Text(getText(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, shape = CircleShape) { Text(getText(R.string.cancel)) }
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

@Composable
fun CardSideEditor(
    sideLabel: String,
    plainText: String,
    onPlainTextChange: (String) -> Unit,
    isRichText: Boolean,
    onToggleRichText: (Boolean) -> Unit,
    onEditRichTextClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: @Composable (() -> Unit)? = null
) {
    val dimensions = LocalStudiareDimensions.current
    Column(modifier = modifier) {
        Text(sideLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = dimensions.paddingSmall))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                if (isRichText) {
                    Box(modifier = Modifier.fillMaxWidth().clickable { onEditRichTextClick() }) {
                        TextField(
                            value = plainText.takeIf { it.isNotBlank() } ?: "Click to edit rich text...",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            trailingIcon = {
                                TextButton(onClick = { onToggleRichText(false) }) {
                                    Text("Rich Text")
                                }
                            }
                        )
                    }
                } else {
                    TextField(
                        value = plainText,
                        onValueChange = onPlainTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            TextButton(onClick = { onToggleRichText(true) }) {
                                Text("Plain Text")
                            }
                        }
                    )
                }
            }
            if (actionIcon != null) {
                actionIcon()
            }
        }
    }
}

@Composable
fun DynamicNoteEditor(
    note: NoteField,
    noteIndex: Int,
    onNoteChange: (NoteField) -> Unit,
    onEditRichTextClick: () -> Unit,
    onRemove: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var showTypeDropdown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary

    val visualMediaLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                val localPath = MediaStorageUtils.copyMediaToInternalStorage(context, it, "media")
                if (localPath != null) onNoteChange(note.copy(content = localPath))
            }
        }
    )

    val audioLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val localPath = MediaStorageUtils.copyMediaToInternalStorage(context, it, "audio")
                if (localPath != null) onNoteChange(note.copy(content = localPath))
            }
        }
    )

    val typeDropdown = @Composable {
        Box {
            TextButton(onClick = { showTypeDropdown = true }) {
                Text(note.type.toString())
            }
            androidx.compose.material3.DropdownMenu(
                expanded = showTypeDropdown,
                onDismissRequest = { showTypeDropdown = false }
            ) {
                MediaType.entries.forEach { mediaType ->
                    DropdownMenuItem(
                        text = { Text(mediaType.toString()) },
                        onClick = {
                            var newContent = note.content
                            val isOldMedia = note.type in listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO)
                            val isNewMedia = mediaType in listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO)

                            if (isOldMedia != isNewMedia) {
                                newContent = ""
                            }
                            else if (mediaType == MediaType.PLAIN_TEXT && (note.type == MediaType.RICH_TEXT || note.type == MediaType.HTML)) {
                                newContent = newContent.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                            }

                            onNoteChange(note.copy(type = mediaType, content = newContent))
                            showTypeDropdown = false
                        }
                    )
                }
            }
        }
    }

    // Standardized M3 Expressive Field Colors
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledIndicatorColor = Color.Transparent,
        disabledTextColor = MaterialTheme.colorScheme.onSurface
    )

    // Shapes to merge the two text fields seamlessly
    val topShape = RoundedCornerShape(
        topStart = dimensions.cornerRadiusMedium,
        topEnd = dimensions.cornerRadiusMedium,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
    val bottomShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = dimensions.cornerRadiusMedium,
        bottomEnd = dimensions.cornerRadiusMedium
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimensions.spacingMedium),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                // The visual grouping line
                .drawBehind {
                    val dotRadius = 4.dp.toPx() // Adjust this value to make the dot larger or smaller
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.4f), // Remove the .copy() if you want a solid color
                        radius = dotRadius,
                        center = androidx.compose.ui.geometry.Offset(
                            x = dotRadius,     // Places the dot right on the left edge
                            y = size.height / 2f    // Centers the dot vertically
                        )
                    )
                }
                .padding(start = dimensions.paddingMedium) // Indent away from the line
        ) {
            // TOP LINE: Field Name
            TextField(
                value = note.name,
                onValueChange = { onNoteChange(note.copy(name = it)) },
                placeholder = { Text("Field ${noteIndex + 1}") },
                modifier = Modifier.fillMaxWidth(),
                shape = topShape,
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium.copy(color = primaryColor),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = primaryColor,
                    unfocusedTextColor = primaryColor,
                    focusedPlaceholderColor = primaryColor.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = primaryColor.copy(alpha = 0.6f)
                )
            )

            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // BOTTOM LINE: Content
            when (note.type) {
                MediaType.PLAIN_TEXT, MediaType.WEB_LINK, MediaType.HTML -> {
                    TextField(
                        value = note.content,
                        onValueChange = { onNoteChange(note.copy(content = it)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = bottomShape,
                        colors = textFieldColors,
                        trailingIcon = typeDropdown
                    )
                }
                MediaType.RICH_TEXT -> {
                    Box(modifier = Modifier.fillMaxWidth().clickable { onEditRichTextClick() }) {
                        TextField(
                            value = note.content.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim().takeIf { it.isNotBlank() } ?: "Click to edit rich text...",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = bottomShape,
                            colors = textFieldColors,
                            trailingIcon = typeDropdown
                        )
                    }
                }
                MediaType.IMAGE, MediaType.VIDEO -> {
                    val hasMedia = note.content.isNotBlank() && note.content.startsWith(context.filesDir.absolutePath)
                    Box(modifier = Modifier.fillMaxWidth().clickable {
                        val mimeType = if (note.type == MediaType.IMAGE) arrayOf("image/*") else arrayOf("video/*")
                        visualMediaLauncher.launch(mimeType)
                    }) {
                        TextField(
                            value = if (hasMedia) "Media selected" else "Select Media...",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = bottomShape,
                            colors = textFieldColors,
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            trailingIcon = typeDropdown
                        )
                    }
                }
                MediaType.AUDIO -> {
                    val hasMedia = note.content.isNotBlank() && note.content.startsWith(context.filesDir.absolutePath)
                    Box(modifier = Modifier.fillMaxWidth().clickable { audioLauncher.launch("audio/*") }) {
                        TextField(
                            value = if (hasMedia) "Audio selected" else "Add Audio...",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = bottomShape,
                            colors = textFieldColors,
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            trailingIcon = typeDropdown
                        )
                    }
                }
            }
        }

        IconButton(
            onClick = onRemove,
            modifier = Modifier.padding(start = dimensions.spacingSmall, top = 8.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove Note", tint = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
fun AdvancedDeckEditorDialog(
    frontTemplates: List<NoteField>,
    backTemplates: List<NoteField>,
    onDismiss: () -> Unit,
    onSave: (List<NoteField>, List<NoteField>, Boolean) -> Unit // CHANGED: Added Boolean
) {
    val dimensions = LocalStudiareDimensions.current
    var localFront by remember { mutableStateOf(frontTemplates) }
    var localBack by remember { mutableStateOf(backTemplates) }
    var addToExistingCards by remember { mutableStateOf(false) } // NEW: Switch state

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text("Advanced Editor", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text("Define default note fields for new cards.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(dimensions.spacingLarge))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item { Text("Front Note Templates", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
                    itemsIndexed(localFront) { index, template ->
                        TemplateRow(
                            template = template,
                            onUpdate = { updated -> localFront = localFront.toMutableList().apply { this[index] = updated } },
                            onRemove = { localFront = localFront.toMutableList().apply { removeAt(index) } }
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { localFront = localFront + NoteField(name = "New Field", content = "", type = MediaType.PLAIN_TEXT) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Front Field", style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(dimensions.spacingLarge))
                    }

                    item { Text("Back Note Templates", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
                    itemsIndexed(localBack) { index, template ->
                        TemplateRow(
                            template = template,
                            onUpdate = { updated -> localBack = localBack.toMutableList().apply { this[index] = updated } },
                            onRemove = { localBack = localBack.toMutableList().apply { removeAt(index) } }
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { localBack = localBack + NoteField(name = "New Field", content = "", type = MediaType.PLAIN_TEXT) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Add Back Field", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                // NEW: Add to Existing Cards Switch
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Add missing fields to existing cards", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    androidx.compose.material3.Switch(
                        checked = addToExistingCards,
                        onCheckedChange = { addToExistingCards = it }
                    )
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel", style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(localFront, localBack, addToExistingCards) }) { Text("Save Templates", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }
}

@Composable
fun TemplateRow(template: NoteField, onUpdate: (NoteField) -> Unit, onRemove: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    var showTypeDropdown by remember { mutableStateOf(false) }

    // NEW: Type Dropdown (mirrored from DynamicNoteEditor)
    val typeDropdown = @Composable {
        Box {
            TextButton(onClick = { showTypeDropdown = true }) {
                Text(template.type.toString())
            }
            androidx.compose.material3.DropdownMenu(
                expanded = showTypeDropdown,
                onDismissRequest = { showTypeDropdown = false }
            ) {
                MediaType.entries.forEach { mediaType ->
                    DropdownMenuItem(
                        text = { Text(mediaType.toString()) },
                        onClick = {
                            onUpdate(template.copy(type = mediaType, content = ""))
                            showTypeDropdown = false
                        }
                    )
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.spacingSmall)
    ) {
        TextField(
            value = template.name,
            onValueChange = { onUpdate(template.copy(name = it)) },
            modifier = Modifier.weight(1f),
            label = { Text("Field Name") },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            trailingIcon = typeDropdown // ADDED: Trailing Icon
        )
        Spacer(Modifier.width(8.dp))
        FilledTonalIconButton(
            onClick = onRemove,
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(Icons.Default.Close, contentDescription = "Remove Template")
        }
    }
}

@Composable
fun RichTextEditorDialog(
    initialHtml: String?,
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val state = rememberRichTextState()

    // Load initial HTML when the dialog opens
    LaunchedEffect(Unit) {
        if (!initialHtml.isNullOrBlank()) {
            state.setHtml(initialHtml)
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, "Cancel") }
                    },
                    actions = {
                        TextButton(onClick = { onSave(state.toHtml()) }) {
                            Text("Save")
                        }
                    }
                )
            }
        ) { paddingValues ->
            Column(modifier = Modifier.padding(paddingValues).fillMaxSize()) {
                // Toolbar
                Row(
                    modifier = Modifier.fillMaxWidth().padding(8.dp).horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Bold
                    FilterChip(
                        selected = state.currentSpanStyle.fontWeight == FontWeight.Bold,
                        onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)) },
                        label = { Text("B", fontWeight = FontWeight.Bold) }
                    )
                    // Italic
                    FilterChip(
                        selected = state.currentSpanStyle.fontStyle == FontStyle.Italic,
                        onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)) },
                        label = { Text("I", fontStyle = FontStyle.Italic) }
                    )
                    // Underline
                    FilterChip(
                        selected = state.currentSpanStyle.textDecoration == TextDecoration.Underline,
                        onClick = { state.toggleSpanStyle(SpanStyle(textDecoration = TextDecoration.Underline)) },
                        label = { Text("U", textDecoration = TextDecoration.Underline) }
                    )
                    // Red Text (Example color)
                    FilterChip(
                        selected = state.currentSpanStyle.color == Color.Red,
                        onClick = { state.toggleSpanStyle(SpanStyle(color = Color.Red)) },
                        label = { Text("Red", color = Color.Red) }
                    )
                }

                // Editor
                RichTextEditor(
                    state = state,
                    modifier = Modifier.fillMaxSize().padding(16.dp),
                    placeholder = { Text("Start typing...") }
                )
            }
        }
    }
}