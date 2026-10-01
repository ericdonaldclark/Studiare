package net.ericclark.studiare

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.components.CardTagRow
import net.ericclark.studiare.data.Direction
import net.ericclark.studiare.components.getText
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
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.launch
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.livedata.observeAsState

// `category` groups sections under an outer collapsible row per StudyCategory in the populated
// grid view (see StudyModeSelectionScreen's STATE 2) — FSRS/Guided sessions never reach this
// screen's list at all (filtered out of groupedSessions), so no section maps to StudyCategory.SMART.
private data class SessionSection(val title: String, val category: StudyCategory, val filter: (ActiveSession) -> Boolean)

/** How many of a crossword's words are fully filled in correctly — shared by [SessionTile]'s progress text/bar and [sessionProgressFraction]. */
private fun completedCrosswordWordCount(session: ActiveSession): Int {
    if (session.mode != SessionMode.CROSSWORD) return 0
    return session.crosswordWords.count { word ->
        word.word.indices.all { i ->
            val x = if (word.isAcross) word.startX + i else word.startX
            val y = if (word.isAcross) word.startY else word.startY + i
            session.crosswordUserInputs["$x,$y"] == word.word[i].toString()
        }
    }
}

/** A session's completion fraction (0f-1f), per-mode — the same math as [SessionTile]'s progress bar, reused as the sort key for [SessionTileSortMode.PROGRESS]. */
private fun sessionProgressFraction(session: ActiveSession): Float = when (session.mode) {
    SessionMode.MEMORY, SessionMode.MATCHING -> if (session.totalCards > 0) session.matchedPairs.size.toFloat() / session.totalCards else 0f
    SessionMode.CROSSWORD -> if (session.crosswordWords.isNotEmpty()) completedCrosswordWordCount(session).toFloat() / session.crosswordWords.size else 0f
    SessionMode.WORD_SEARCH -> if (session.wordSearchWords.isNotEmpty()) session.wordSearchFoundWordIds.size.toFloat() / session.wordSearchWords.size else 0f
    else -> if (session.totalCards > 0) session.currentCardIndex.toFloat() / session.totalCards else 0f
}

/** Orders session tiles against each other within a mode group (or a flattened merge of groups). */
private fun sortedTiles(sessions: List<ActiveSession>, mode: SessionTileSortMode, direction: Direction): List<ActiveSession> {
    val comparator: Comparator<ActiveSession> = when (mode) {
        SessionTileSortMode.LAST_ACCESSED -> compareBy { it.lastAccessed }
        SessionTileSortMode.DATE_CREATED -> compareBy { it.createdAt }
        SessionTileSortMode.PROGRESS -> compareBy { sessionProgressFraction(it) }
    }
    val ordered = sessions.sortedWith(comparator)
    return if (direction == Direction.DESC) ordered.reversed() else ordered
}

/** Orders the outer category rows. `DEFAULT` keeps [categories]'s incoming (curated) order. */
private fun sortedCategories(
    categories: List<StudyCategory>,
    mode: GroupSortMode,
    direction: Direction,
    sessionsByCategory: Map<StudyCategory, List<ActiveSession>>,
    context: android.content.Context
): List<StudyCategory> {
    if (mode == GroupSortMode.DEFAULT) return categories
    val comparator: Comparator<StudyCategory> = when (mode) {
        GroupSortMode.ALPHABETICAL -> compareBy { it.asString(context) }
        GroupSortMode.MOST_RECENT -> compareBy { sessionsByCategory[it]?.maxOfOrNull { s -> s.lastAccessed } ?: 0L }
        GroupSortMode.SESSION_COUNT -> compareBy { sessionsByCategory[it]?.size ?: 0 }
        GroupSortMode.DEFAULT -> compareBy { 0 } // unreachable, guarded above
    }
    val ordered = categories.sortedWith(comparator)
    return if (direction == Direction.DESC) ordered.reversed() else ordered
}

/** Orders the mode-section rows within a category. `DEFAULT` keeps [sectionsIn]'s incoming (curated) order. */
private fun sortedSections(
    sectionsIn: List<SessionSection>,
    mode: GroupSortMode,
    direction: Direction,
    groupedSessions: Map<String, List<ActiveSession>>
): List<SessionSection> {
    if (mode == GroupSortMode.DEFAULT) return sectionsIn
    val comparator: Comparator<SessionSection> = when (mode) {
        GroupSortMode.ALPHABETICAL -> compareBy { it.title }
        GroupSortMode.MOST_RECENT -> compareBy { groupedSessions[it.title]?.maxOfOrNull { s -> s.lastAccessed } ?: 0L }
        GroupSortMode.SESSION_COUNT -> compareBy { groupedSessions[it.title]?.size ?: 0 }
        GroupSortMode.DEFAULT -> compareBy { 0 } // unreachable, guarded above
    }
    val ordered = sectionsIn.sortedWith(comparator)
    return if (direction == Direction.DESC) ordered.reversed() else ordered
}

/**
 * A screen that displays all active study sessions for a specific deck,
 * grouped by study mode. It allows users to resume, copy, restart, or delete sessions.
 * @param navController The NavController for navigating to study screens.
 * @param deck The deck for which to display study sessions.
 * @param viewModel The ViewModel providing data and business logic.
 */
@Composable
fun StudyModeSelectionScreen(
    navController: NavController,
    deck: DeckWithCards,
    viewModel: FlashcardViewModel,
    autoOpen: String? = null,
    isPane: Boolean = false,
    onChromeChanged: (PaneChrome) -> Unit = {}
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    val dimensions = LocalStudiareDimensions.current
    var showCreateSessionDialog by rememberSaveable { mutableStateOf<StudyCategory?>(null) }
    var showFsrsConfigDialog by rememberSaveable { mutableStateOf<SessionMode?>(null) }
    var showFsrsModeDialog by rememberSaveable { mutableStateOf(false) }
    var showCategoryPickerDialog by rememberSaveable { mutableStateOf(false) }
    val allActiveSessionsOrNull by viewModel.allActiveSessionsOrNull.collectAsState()
    val sessionsLoaded = allActiveSessionsOrNull != null
    val activeSessions = remember(allActiveSessionsOrNull, deck.deck.id) {
        allActiveSessionsOrNull.orEmpty().filter { it.deckId == deck.deck.id }
    }

    // NEW: State for synchronized navigation
    var pendingNavigationRoute by remember { mutableStateOf<String?>(null) }
    var pendingSessionId by remember { mutableStateOf<String?>(null) }
    val currentStudyState = viewModel.studyState

    LaunchedEffect(currentStudyState?.sessionId, pendingNavigationRoute, pendingSessionId) {
        // Wait until the ViewModel has successfully loaded the requested session
        if (pendingNavigationRoute != null && currentStudyState?.sessionId == pendingSessionId) {
            navController.navigate(pendingNavigationRoute!!)
            pendingNavigationRoute = null
            pendingSessionId = null
        }
    }

    // Handle Auto Open on first load from Split Button
    var hasAutoOpened by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(autoOpen) {
        if (!hasAutoOpened && autoOpen != null) {
            when(autoOpen) {
                "learn" -> showCreateSessionDialog = StudyCategory.LEARN
                "study" -> showCreateSessionDialog = StudyCategory.STUDY
                "quiz" -> showCreateSessionDialog = StudyCategory.QUIZ
                "game" -> showCreateSessionDialog = StudyCategory.GAMES
                "fsrs" -> showFsrsModeDialog = true
            }
            hasAutoOpened = true
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- Data Preparation for Dialog ---
    val allTags by viewModel.tags.collectAsState()
    val parentDeckTags by produceState(initialValue = emptyList<String>(), key1 = deck.deck.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            deck.cards.flatMap { it.tags }.distinct().sorted()
        }
    }

    val sections = listOf(
        SessionSection(stringResource(R.string.section_flashcards_practice), StudyCategory.STUDY) { it.mode == SessionMode.FLASHCARD && !it.isGraded },
        SessionSection(stringResource(R.string.section_flashcards_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.FLASHCARD && it.isGraded },
        SessionSection(stringResource(R.string.section_freeform), StudyCategory.LEARN) { it.mode == SessionMode.FREEFORM },
        SessionSection(stringResource(R.string.section_picking_practice), StudyCategory.STUDY) { it.mode == SessionMode.LIST && !it.isGraded },
        SessionSection(stringResource(R.string.section_picking_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.LIST && it.isGraded },
        SessionSection(stringResource(R.string.section_mc_practice), StudyCategory.STUDY) { it.mode == SessionMode.MULTIPLE_CHOICE && !it.isGraded },
        SessionSection(stringResource(R.string.section_mc_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.MULTIPLE_CHOICE && it.isGraded },
        SessionSection(stringResource(R.string.section_matching_practice), StudyCategory.STUDY) { it.mode == SessionMode.MATCHING && !it.isGraded },
        SessionSection(stringResource(R.string.section_matching_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.MATCHING && it.isGraded },
        SessionSection(stringResource(R.string.section_typing), StudyCategory.LEARN) { it.mode == SessionMode.TYPING },
        SessionSection(stringResource(R.string.section_typing_practice), StudyCategory.STUDY) { it.mode == SessionMode.TYPING_SCORED && it.showCorrectLetters },
        SessionSection(stringResource(R.string.section_typing_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.TYPING_SCORED && !it.showCorrectLetters },
        SessionSection(stringResource(R.string.section_audio), StudyCategory.LEARN) { it.mode == SessionMode.AUDIO },
        SessionSection(stringResource(R.string.section_listening_practice), StudyCategory.STUDY) { it.mode == SessionMode.TYPED_LISTEN && !it.isGraded },
        SessionSection(stringResource(R.string.section_listening_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.TYPED_LISTEN && it.isGraded },
        SessionSection(stringResource(R.string.section_speaking_practice), StudyCategory.STUDY) { it.mode == SessionMode.SPOKEN_LISTEN && !it.isGraded },
        SessionSection(stringResource(R.string.section_speaking_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.SPOKEN_LISTEN && it.isGraded },
        SessionSection(stringResource(R.string.section_anagram), StudyCategory.GAMES) { it.mode == SessionMode.ANAGRAM },
        SessionSection(stringResource(R.string.section_hangman), StudyCategory.GAMES) { it.mode == SessionMode.HANGMAN },
        SessionSection(stringResource(R.string.section_memory), StudyCategory.GAMES) { it.mode == SessionMode.MEMORY },
        SessionSection(stringResource(R.string.section_crossword), StudyCategory.GAMES) { it.mode == SessionMode.CROSSWORD },
        SessionSection(stringResource(R.string.section_word_search), StudyCategory.GAMES) { it.mode == SessionMode.WORD_SEARCH }
    )

    // Display order for the new outer category rows in the populated grid view.
    val categoryOrder = listOf(StudyCategory.LEARN, StudyCategory.STUDY, StudyCategory.QUIZ, StudyCategory.GAMES)

    // Sort/grouping settings for the populated grid view (global, same for every deck).
    val groupByCategory by viewModel.groupByCategory.collectAsState()
    val groupByMode by viewModel.groupByMode.collectAsState()
    val categorySortMode by viewModel.categorySortMode.collectAsState()
    val categorySortDirection by viewModel.categorySortDirection.collectAsState()
    val modeSortMode by viewModel.modeSortMode.collectAsState()
    val modeSortDirection by viewModel.modeSortDirection.collectAsState()
    val sessionTileSortMode by viewModel.sessionTileSortMode.collectAsState()
    val sessionTileSortDirection by viewModel.sessionTileSortDirection.collectAsState()

    // Dialog States
    var showRestartDialog by remember { mutableStateOf<ActiveSession?>(null) }
    var showDeleteDialog by remember { mutableStateOf<ActiveSession?>(null) }
    var showDeleteAllSessionsDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }

    var expandedStates by remember { mutableStateOf(mapOf<String, Boolean>()) }

    // HD Audio Prompt States
    val hasPromptedHd by viewModel.hasPromptedHdLanguages.collectAsState()
    var showHdPromptDialog by remember { mutableStateOf(false) }
    var showHdSelectionDialog by remember { mutableStateOf(false) }

    val downloadedHdLanguages by viewModel.downloadedHdLanguages.collectAsState()
    // Store pending session start action to execute after dialogs
    var pendingSessionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val context = LocalContext.current

    val toastMessage = viewModel.toastMessage
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    // --- HD Audio Dialogs ---

    if (showHdPromptDialog) {
        ConfirmationDialog(
            title = getText(R.string.download_hd_languages_title),
            text = getText(R.string.download_hd_languages_desc),
            confirmButtonText = getText(R.string.yes),
            onConfirm = {
                showHdPromptDialog = false
                showHdSelectionDialog = true
            },
            dismissButtonText = getText(R.string.no),
            onDismiss = {
                showHdPromptDialog = false
                viewModel.setHdAudioPrompted() // Mark as asked so we don't ask again
                pendingSessionAction?.invoke()
                pendingSessionAction = null
            }
        )
    }

    if (showFsrsModeDialog) {
        FsrsModeSelectionDialog(
            onDismiss = { showFsrsModeDialog = false },
            onModeSelected = { mode ->
                showFsrsModeDialog = false
                showFsrsConfigDialog = mode
            }
        )
    }

    if (showHdSelectionDialog) {
        val uniqueLangs = remember(deck.deck.id) { viewModel.getUniqueDeckLanguages() }
        val languageSizes = remember(uniqueLangs) {
            uniqueLangs.associateWith { lang ->
                viewModel.getFormattedModelSize(lang)
            }
        }


        HdLanguageSelectionDialog(
            languages = uniqueLangs,
            downloadedLanguages = downloadedHdLanguages,
            languageSizes = languageSizes,
            onDismiss = {
                showHdSelectionDialog = false
                viewModel.setHdAudioPrompted()
                pendingSessionAction?.invoke()
                pendingSessionAction = null
            },
            onDownload = { selectedLangs ->
                showHdSelectionDialog = false
                viewModel.startHdLanguageDownload(context, selectedLangs)
                pendingSessionAction?.invoke()
                pendingSessionAction = null

                coroutineScope.launch {
                    snackbarHostState.showSnackbar(
                        message = getText(context, R.string.download_language_later),
                        duration = androidx.compose.material3.SnackbarDuration.Short
                    )
                }
            }
        )
    }

    showCreateSessionDialog?.let { category ->
        CreateStudySessionDialog(
            deck = deck,
            category = category, // NEW: Pass the selected category from the FAB menu
            availableTags = parentDeckTags,
            allTagDefinitions = allTags,
            onDismiss = { showCreateSessionDialog = null },
            onStartSession = { mode, isWeighted, numCards, quizPromptSide, numAnswers, showLetters, limitPool,
                               isGraded, allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes,
                               maxMemoryTiles, gridDensity, showCorrectWords, freeformVerticalLayout, config,  ->
                showCreateSessionDialog = null

                // Logic for NEW sessions
                val route = studyRouteFor(mode)

                // Define the action to start the session
                val startAction = {
                    viewModel.startStudySession(
                        parentDeck = deck,
                        mode = mode,
                        isWeighted = isWeighted,
                        // difficulties removed (in config)
                        numCards = numCards,
                        quizPromptSide = quizPromptSide,
                        numAnswers = numAnswers,
                        showCorrectLetters = showLetters,
                        limitAnswerPool = limitPool,
                        // cardOrder removed (in config)
                        isGraded = isGraded,
                        allowMultipleGuesses = allowMultipleGuesses,
                        enableStt = enableStt,
                        hideAnswerText = hideAnswerText,
                        fingersAndToes = fingersAndToes,
                        maxMemoryTiles = maxMemoryTiles,
                        gridDensity = gridDensity,
                        config = config, // Pass the config object
                        freeformLayoutVertical = freeformVerticalLayout
                    ) {
                        navController.navigate(route)
                    }
                }

                // Intercept if this mode plays TTS audio and the HD-voice prompt hasn't been
                // shown yet — was Audio-only, but the listening modes play the exact same
                // per-language HD voices and deserve the same nudge.
                val playsAudio = mode == SessionMode.AUDIO || mode == SessionMode.TYPED_LISTEN || mode == SessionMode.SPOKEN_LISTEN
                if (playsAudio && !hasPromptedHd) {
                    pendingSessionAction = startAction
                    showHdPromptDialog = true
                } else {
                    startAction()
                }
            }
        )
    }

    // Confirmation Dialogs
    showRestartDialog?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.restart_session_title),
            text = getText(R.string.restart_session_desc),
            onConfirm = { viewModel.restartSession(session); showRestartDialog = null },
            onDismiss = { showRestartDialog = null }
        )
    }

    showDeleteDialog?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.delete_session_title),
            text = getText(R.string.delete_session_desc),
            onConfirm = { viewModel.deleteSession(session); showDeleteDialog = null },
            onDismiss = { showDeleteDialog = null }
        )
    }

    val groupedSessions by produceState(initialValue = emptyMap<String, List<ActiveSession>>(), activeSessions, sessionTileSortMode, sessionTileSortDirection) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val displayed = activeSessions.filter { it.schedulingMode != SchedulingMode.FSRS }
            sections.associate { section ->
                section.title to sortedTiles(displayed.filter(section.filter), sessionTileSortMode, sessionTileSortDirection)
            }
        }
    }

    if (showDeleteAllSessionsDialog) {
        ConfirmationDialog(
            title = getText(R.string.delete_all_sessions_title),
            text = getText(R.string.delete_all_sessions_desc),
            onConfirm = { viewModel.deleteAllSessionsForDeck(deck.deck.id); showDeleteAllSessionsDialog = false },
            onDismiss = { showDeleteAllSessionsDialog = false }
        )
    }

    if (showSortDialog) {
        SessionSortDialog(
            initialGroupByCategory = groupByCategory,
            initialGroupByMode = groupByMode,
            initialCategorySortMode = categorySortMode,
            initialCategorySortDirection = categorySortDirection,
            initialModeSortMode = modeSortMode,
            initialModeSortDirection = modeSortDirection,
            initialTileSortMode = sessionTileSortMode,
            initialTileSortDirection = sessionTileSortDirection,
            onSave = { newGroupByCategory, newGroupByMode, newCategorySortMode, newCategorySortDirection, newModeSortMode, newModeSortDirection, newTileSortMode, newTileSortDirection ->
                viewModel.setGroupByCategory(newGroupByCategory)
                viewModel.setGroupByMode(newGroupByMode)
                viewModel.setCategorySortMode(newCategorySortMode)
                viewModel.setCategorySortDirection(newCategorySortDirection)
                viewModel.setModeSortMode(newModeSortMode)
                viewModel.setModeSortDirection(newModeSortDirection)
                viewModel.setSessionTileSortMode(newTileSortMode)
                viewModel.setSessionTileSortDirection(newTileSortDirection)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    val isDataLoaded by viewModel.isInitialDataLoaded.collectAsState()

    val allDecksState by viewModel.allDecks.observeAsState(emptyList())
    val navigateUp = {
        if (isPane) {
            viewModel.closePane("study:${deck.deck.id}")
        } else {
            val parentId = deck.deck.parentDeckId
            if (parentId == null) {
                // It's a top-level deck, go back to Home
                navController.navigate("deckList") { popUpTo(0) }
            } else {
                // It's a set, go back to its parent's Set Manager
                navController.navigate("setManager/$parentId") {
                    popUpTo("setManager/$parentId") { inclusive = true }
                }
            }
        }
    }

    BackHandler(enabled = !isPane, onBack = navigateUp)

    if (isPane) {
        // Keyed on activeSessions.isNotEmpty() too (not just the deck name) so the overflow menu
        // in `actions` appears/disappears correctly as sessions are created or deleted, instead of
        // freezing at whatever it was when this effect last ran.
        LaunchedEffect(deck.deck.name, activeSessions.isNotEmpty()) {
            onChromeChanged(PaneChrome(
                title = { Text(deck.deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (activeSessions.isNotEmpty()) {
                        StudyHubOverflowMenu(onSortClick = { showSortDialog = true }, onDeleteAllSessions = { showDeleteAllSessionsDialog = true })
                    }
                },
                screenId = ShortcutScreen.STUDY_HUB
            ))
        }
    }

    val paneContent: @Composable (PaddingValues) -> Unit = { padding ->
        val focusRequester = remember { FocusRequester() }
        val remaps = LocalShortcutRemaps.current
        val startLearnKey = resolveShortcutKey(remaps, "study_hub.start_learn", Key.L)
        val startStudyKey = resolveShortcutKey(remaps, "study_hub.start_study", Key.P)
        val startQuizKey = resolveShortcutKey(remaps, "study_hub.start_quiz", Key.Q)
        val startGameKey = resolveShortcutKey(remaps, "study_hub.start_game", Key.G)
        val startSpacedRepKey = resolveShortcutKey(remaps, "study_hub.start_spaced_repetition", Key.S)

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .autoFocusable(focusRequester)
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) {
                        when (event.key) {
                            Key.Backspace -> {
                                navigateUp()
                                return@onPreviewKeyEvent true
                            }
                            startLearnKey -> {
                                showCreateSessionDialog = StudyCategory.LEARN
                                return@onPreviewKeyEvent true
                            }
                            startStudyKey -> {
                                showCreateSessionDialog = StudyCategory.STUDY
                                return@onPreviewKeyEvent true
                            }
                            startQuizKey -> {
                                showCreateSessionDialog = StudyCategory.QUIZ
                                return@onPreviewKeyEvent true
                            }
                            startGameKey -> {
                                showCreateSessionDialog = StudyCategory.GAMES
                                return@onPreviewKeyEvent true
                            }
                            startSpacedRepKey -> {
                                showFsrsModeDialog = true
                                return@onPreviewKeyEvent true
                            }
                        }
                    }
                    false
                }
        ) {

            // --- STATE SWITCHER: Handles Loading, Empty, and Populated Lists ---
            AnimatedContent(
                targetState = when {
                    !isDataLoaded || !sessionsLoaded -> 0 // STATE 0: Loading
                    activeSessions.isEmpty() -> 1                  // STATE 1: Empty
                    else -> 2                                         // STATE 2: Populated
                },
                transitionSpec = {
                    (fadeIn(animationSpec = androidx.compose.animation.core.tween(800)) +
                            expandVertically(animationSpec = androidx.compose.animation.core.tween(800))).togetherWith(
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(800)) +
                                shrinkVertically(animationSpec = androidx.compose.animation.core.tween(800))
                    )
                },
                label = "sessionsScreenTransition"
            ) { targetState ->
                when (targetState) {
                    0 -> {
                        // STATE 0: Loading Spinner. Held back briefly so fast loads never flash it.
                        var showSpinner by remember { mutableStateOf(false) }
                        LaunchedEffect(Unit) {
                            kotlinx.coroutines.delay(400)
                            showSpinner = true
                        }
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (showSpinner) LoadingIndicator()
                        }
                    }
                    1 -> {
                        // STATE 1: Empty State
                        CategoryPickerContent(
                            title = getText(R.string.no_active_sessions),
                            subtitle = getText(R.string.no_active_sessions_description),
                            onCategorySelected = { category -> showCreateSessionDialog = category },
                            onGuidedSelected = { showFsrsModeDialog = true }
                        )
                    }
                    2 -> {
                        // STATE 2: Populated List
                        val onResumeSession: (ActiveSession) -> Unit = { session ->
                            val route = studyRouteFor(session.mode)
                            // THE FIX: Set pending state to wait for ViewModel load
                            pendingSessionId = session.id
                            pendingNavigationRoute = route
                            viewModel.resumeStudySession(session)
                        }
                        val onCopySession: (ActiveSession) -> Unit = { session -> viewModel.copySession(session) }
                        val onRestartSession: (ActiveSession) -> Unit = { session -> showRestartDialog = session }
                        val onDeleteSession: (ActiveSession) -> Unit = { session -> showDeleteDialog = session }

                        fun LazyListScope.categoryHeaderItem(category: StudyCategory, isExpanded: Boolean, totalSessions: Int) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                                        .clickable {
                                            expandedStates = expandedStates + ("category:${category.name}" to !isExpanded)
                                        }
                                        .padding(horizontal = dimensions.paddingSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        Text(
                                            text = category.asString(),
                                            style = MaterialTheme.typography.headlineMedium,
                                            fontWeight = FontWeight.Bold
                                        )
                                        if (!isExpanded) {
                                            Text(
                                                text = "$totalSessions Sessions",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    val iconRotation by animateFloatAsState(
                                        targetValue = if (isExpanded) 180f else 0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                                        label = "categoryChevronRotation"
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation }
                                    )
                                }
                            }
                        }

                        fun LazyListScope.modeHeaderItem(section: SessionSection, isExpanded: Boolean, sessionCount: Int) {
                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                                        .clickable {
                                            expandedStates = expandedStates + (section.title to !isExpanded)
                                        }
                                        .padding(vertical = dimensions.paddingSmall, horizontal = dimensions.paddingSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        // The category header above already says "Practice"/"Quiz" —
                                        // don't repeat it on every mode title underneath it.
                                        val modeTitle = if (groupByCategory) section.title.substringBefore(" - ") else section.title
                                        Text(text = modeTitle, style = MaterialTheme.typography.headlineSmall)
                                        if (!isExpanded) {
                                            Text(
                                                text = "$sessionCount Sessions",
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    val iconRotation by animateFloatAsState(
                                        targetValue = if (isExpanded) 180f else 0f,
                                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium),
                                        label = "chevronRotation"
                                    )
                                    Icon(
                                        imageVector = Icons.Default.ExpandMore,
                                        contentDescription = if (isExpanded) "Collapse" else "Expand",
                                        modifier = Modifier.graphicsLayer { rotationZ = iconRotation }
                                    )
                                }
                            }
                        }

                        fun LazyListScope.carouselItem(sessions: List<ActiveSession>, showModeLabel: Boolean, showCategoryIcon: Boolean, animatedVisible: Boolean? = null) {
                            item {
                                if (animatedVisible != null) {
                                    AnimatedVisibility(visible = animatedVisible) {
                                        SessionTileCarousel(sessions, deck, showModeLabel, showCategoryIcon, onResumeSession, onCopySession, onRestartSession, onDeleteSession)
                                    }
                                } else {
                                    SessionTileCarousel(sessions, deck, showModeLabel, showCategoryIcon, onResumeSession, onCopySession, onRestartSession, onDeleteSession)
                                }
                            }
                        }

                        fun LazyListScope.gridItem(sessions: List<ActiveSession>, showModeLabel: Boolean, showCategoryIcon: Boolean) {
                            item {
                                SessionTileGrid(sessions, deck, showModeLabel, showCategoryIcon, onResumeSession, onCopySession, onRestartSession, onDeleteSession)
                            }
                        }

                        // Non-empty mode-sections per category, in the user's chosen mode order.
                        fun sectionsFor(category: StudyCategory): List<SessionSection> {
                            val nonEmpty = sections.filter { it.category == category }.filter { (groupedSessions[it.title] ?: emptyList()).isNotEmpty() }
                            return sortedSections(nonEmpty, modeSortMode, modeSortDirection, groupedSessions)
                        }

                        val sessionsByCategory = categoryOrder.associateWith { category -> sectionsFor(category).flatMap { groupedSessions[it.title] ?: emptyList() } }
                        val categoriesToShow = sortedCategories(categoryOrder, categorySortMode, categorySortDirection, sessionsByCategory, context)
                            .filter { (sessionsByCategory[it] ?: emptyList()).isNotEmpty() }

                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = dimensions.paddingMedium,
                                top = dimensions.paddingMedium,
                                end = dimensions.paddingMedium,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                        ) {
                            if (groupByCategory) {
                                categoriesToShow.forEach { category ->
                                    val orderedSections = sectionsFor(category)
                                    val totalSessionsInCategory = orderedSections.sumOf { (groupedSessions[it.title] ?: emptyList()).size }
                                    val isCategoryExpanded = expandedStates["category:${category.name}"] ?: true
                                    categoryHeaderItem(category, isCategoryExpanded, totalSessionsInCategory)

                                    if (isCategoryExpanded) {
                                        if (groupByMode) {
                                            // Case 1: nested category > mode > tiles (today's behavior).
                                            orderedSections.forEach { section ->
                                                val sessionsInSection = groupedSessions[section.title] ?: emptyList()
                                                val isExpanded = expandedStates[section.title] ?: true
                                                modeHeaderItem(section, isExpanded, sessionsInSection.size)
                                                carouselItem(sessionsInSection, showModeLabel = false, showCategoryIcon = false, animatedVisible = isExpanded)
                                            }
                                        } else {
                                            // Case 2: category header stays, modes flattened into one
                                            // carousel (mode-sort groups same-mode tiles together).
                                            val merged = orderedSections.flatMap { groupedSessions[it.title] ?: emptyList() }
                                            carouselItem(merged, showModeLabel = true, showCategoryIcon = false)
                                        }
                                    }
                                }
                            } else if (groupByMode) {
                                // Case 3: no category headers; mode-section blocks render flat,
                                // ordered by category-sort then mode-sort so same-category modes
                                // stay adjacent even without a wrapping header.
                                categoriesToShow.forEach { category ->
                                    sectionsFor(category).forEach { section ->
                                        val sessionsInSection = groupedSessions[section.title] ?: emptyList()
                                        val isExpanded = expandedStates[section.title] ?: true
                                        modeHeaderItem(section, isExpanded, sessionsInSection.size)
                                        // The mode header still fully identifies the mode, but with no
                                        // category header, the category icon is the only remaining clue
                                        // to which category this mode belongs to.
                                        carouselItem(sessionsInSection, showModeLabel = false, showCategoryIcon = true, animatedVisible = isExpanded)
                                    }
                                }
                            } else {
                                // Case 4: no headers at all — one fully flat grid (not a scrolling
                                // carousel, since there's no mode/category grouping left to browse by),
                                // ordered by category-sort → mode-sort → tile-sort (already applied
                                // per-section).
                                val merged = categoriesToShow.flatMap { category -> sectionsFor(category).flatMap { groupedSessions[it.title] ?: emptyList() } }
                                gridItem(merged, showModeLabel = true, showCategoryIcon = true)
                            }
                        }
                }
            }
            } // closes AnimatedContent's trailing lambda

            // --- FLOATING ACTION BUTTON: opens the full-page category picker directly ---
            AnimatedVisibility(
                visible = activeSessions.isNotEmpty(),
                enter = fadeIn() + androidx.compose.animation.scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                exit = fadeOut() + androidx.compose.animation.scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(dimensions.paddingMedium)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { showCategoryPickerDialog = true },
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    },
                    text = {
                        Text(
                            getText(R.string.session_start),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
        }
    }

    if (showCategoryPickerDialog) {
        Dialog(
            onDismissRequest = { showCategoryPickerDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {},
                        navigationIcon = {
                            TooltipIconButton(description = "Close", onClick = { showCategoryPickerDialog = false }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                            }
                        }
                    )
                }
            ) { padding ->
                CategoryPickerContent(
                    title = getText(R.string.pick_a_category),
                    subtitle = null,
                    onCategorySelected = { category -> showCategoryPickerDialog = false; showCreateSessionDialog = category },
                    onGuidedSelected = { showCategoryPickerDialog = false; showFsrsModeDialog = true },
                    modifier = Modifier.padding(padding).fillMaxSize()
                )
            }
        }
    }
    if (isPane) {
        // Title lives in the shared app bar (via PaneChrome), not in the pane.
        Box(Modifier.fillMaxSize()) { paneContent(PaddingValues(0.dp)) }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    CustomTopAppBar(
                        viewModel = viewModel,
                        screenId = ShortcutScreen.STUDY_HUB,
                        title = { Text(deck.deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            TooltipIconButton(description = "Back", onClick = navigateUp) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                            }
                        },
                        actions = {
                            if (activeSessions.isNotEmpty()) {
                                StudyHubOverflowMenu(onSortClick = { showSortDialog = true }, onDeleteAllSessions = { showDeleteAllSessionsDialog = true })
                            }
                        }
                    )
                    BreadcrumbsBar(
                        currentDeck = deck.deck,
                        allDecks = allDecksState.map { it.deck },
                        onNavigateHome = { navController.navigate("deckList") { popUpTo(0) } },
                        onNavigateToDeck = { deckId ->
                            navController.navigate("setManager/$deckId") {
                                popUpTo("deckList") { inclusive = false }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        ) { padding -> paneContent(padding) }
    }
    // --- FSRS Config Dialog ---
    if (showFsrsConfigDialog != null) {
        FsrsConfigDialog(
            mode = showFsrsConfigDialog!!,
            deck = deck,
            onDismiss = { showFsrsConfigDialog = null },
            onStart = { config, finalMode, isWeighted, promptSide, numAnswers, showLetters, limitPool, selectAnswer, multiGuess, stt, hideText, fingers, maxTiles, density ->
                showFsrsConfigDialog = null

                var internalMode = finalMode
                if (finalMode == SessionMode.FLASHCARD && selectAnswer) internalMode = SessionMode.LIST
                if (finalMode == SessionMode.TYPING) internalMode = SessionMode.TYPING_SCORED

                val route = studyRouteFor(internalMode)

                viewModel.startStudySession(
                    parentDeck = deck,
                    mode = internalMode,
                    isWeighted = isWeighted,
                    numCards = config.maxCardsPerSet, // This will be handled by FSRS filter logic
                    quizPromptSide = promptSide,
                    numAnswers = numAnswers,
                    showCorrectLetters = showLetters,
                    limitAnswerPool = limitPool,
                    isGraded = true, // Always true for FSRS
                    allowMultipleGuesses = multiGuess,
                    enableStt = stt,
                    hideAnswerText = hideText,
                    fingersAndToes = fingers,
                    maxMemoryTiles = maxTiles,
                    gridDensity = density,
                    config = config,
                    freeformLayoutVertical = false,
                    onSessionCreated = { navController.navigate(route) }
                )
            }
        )
    }
}

/** The Study Hub's overflow menu — only shown when the deck has active sessions. */
@Composable
private fun StudyHubOverflowMenu(onSortClick: () -> Unit, onDeleteAllSessions: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(description = getText(R.string.options_more), onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.options_more))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(16.dp)
            )
        ) {
            DropdownMenuItem(
                text = { Text(getText(R.string.sort)) },
                leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                onClick = { expanded = false; onSortClick() }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.delete_all)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { expanded = false; onDeleteAllSessions() }
            )
        }
    }
}

/**
 * The Study Hub's in-depth sort/customization dialog (opened from [StudyHubOverflowMenu]'s "Sort"
 * item). Covers all three nesting levels of the populated grid view: whether categories/modes get
 * their own collapsible header at all (`groupBy*`), and how categories, modes, and session tiles
 * are each ordered. Edits are staged locally and only committed (globally, for every deck) when
 * Save is pressed — Cancel/the close icon discard them, and Reset reverts the staged values to
 * defaults without saving, so Save still has to be pressed to persist a reset.
 */
@Composable
private fun SessionSortDialog(
    initialGroupByCategory: Boolean,
    initialGroupByMode: Boolean,
    initialCategorySortMode: GroupSortMode,
    initialCategorySortDirection: Direction,
    initialModeSortMode: GroupSortMode,
    initialModeSortDirection: Direction,
    initialTileSortMode: SessionTileSortMode,
    initialTileSortDirection: Direction,
    onSave: (Boolean, Boolean, GroupSortMode, Direction, GroupSortMode, Direction, SessionTileSortMode, Direction) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current

    var groupByCategory by remember { mutableStateOf(initialGroupByCategory) }
    var groupByMode by remember { mutableStateOf(initialGroupByMode) }
    var categorySortMode by remember { mutableStateOf(initialCategorySortMode) }
    var categorySortDirection by remember { mutableStateOf(initialCategorySortDirection) }
    var modeSortMode by remember { mutableStateOf(initialModeSortMode) }
    var modeSortDirection by remember { mutableStateOf(initialModeSortDirection) }
    var tileSortMode by remember { mutableStateOf(initialTileSortMode) }
    var tileSortDirection by remember { mutableStateOf(initialTileSortDirection) }

    fun resetToDefaults() {
        groupByCategory = true
        groupByMode = true
        categorySortMode = GroupSortMode.DEFAULT
        categorySortDirection = Direction.ASC
        modeSortMode = GroupSortMode.DEFAULT
        modeSortDirection = Direction.ASC
        tileSortMode = SessionTileSortMode.LAST_ACCESSED
        tileSortDirection = Direction.DESC
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = getText(R.string.sort_sessions_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    TooltipIconButton(description = "Close", onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
                Spacer(Modifier.height(dimensions.spacingSmall))
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    DialogSection(title = getText(R.string.sort_section_categories)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(getText(R.string.group_by_category_label), modifier = Modifier.weight(1f))
                            Switch(checked = groupByCategory, onCheckedChange = { groupByCategory = it })
                        }
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        GroupSortRow(categorySortMode, { categorySortMode = it }, categorySortDirection, { categorySortDirection = it })
                    }
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    DialogSection(title = getText(R.string.sort_section_modes)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(getText(R.string.group_by_mode_label), modifier = Modifier.weight(1f))
                            Switch(checked = groupByMode, onCheckedChange = { groupByMode = it })
                        }
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        GroupSortRow(modeSortMode, { modeSortMode = it }, modeSortDirection, { modeSortDirection = it })
                    }
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    DialogSection(title = getText(R.string.sort_section_cards)) {
                        TileSortRow(tileSortMode, { tileSortMode = it }, tileSortDirection, { tileSortDirection = it })
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Pinned to the bottom, below the scrollable content — none of these need a
                // confirmation prompt, and nothing above is applied until Save is pressed.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { resetToDefaults() }) { Text(getText(R.string.reset)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
                        Button(onClick = {
                            onSave(
                                groupByCategory, groupByMode,
                                categorySortMode, categorySortDirection,
                                modeSortMode, modeSortDirection,
                                tileSortMode, tileSortDirection
                            )
                        }) { Text(getText(R.string.save)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupSortRow(
    mode: GroupSortMode, onModeChange: (GroupSortMode) -> Unit,
    direction: Direction, onDirectionChange: (Direction) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
        ) {
            GroupSortMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = { onModeChange(option) },
                    // Animates the chip's own width growing to fit the checkmark before it appears,
                    // instead of popping to its new size instantly and jolting the FlowRow onto a
                    // new line mid-frame (matches SortModeDialogSection's chips, CommonUiComponents.kt).
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                    ),
                    label = { Text(option.asString(), maxLines = 1, softWrap = false) },
                    leadingIcon = if (mode == option) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null
                )
            }
        }
        if (mode != GroupSortMode.DEFAULT) {
            Spacer(Modifier.height(dimensions.spacingSmall))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = direction == Direction.ASC,
                    onClick = { onDirectionChange(Direction.ASC) },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                ) { Text(getText(R.string.ascending)) }
                SegmentedButton(
                    selected = direction == Direction.DESC,
                    onClick = { onDirectionChange(Direction.DESC) },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                ) { Text(getText(R.string.descending)) }
            }
        }
    }
}

@Composable
private fun TileSortRow(
    mode: SessionTileSortMode, onModeChange: (SessionTileSortMode) -> Unit,
    direction: Direction, onDirectionChange: (Direction) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
        ) {
            SessionTileSortMode.entries.forEach { option ->
                FilterChip(
                    selected = mode == option,
                    onClick = { onModeChange(option) },
                    // See GroupSortRow's identical chip for why this is animated.
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                    ),
                    label = { Text(option.asString(), maxLines = 1, softWrap = false) },
                    leadingIcon = if (mode == option) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null
                )
            }
        }
        Spacer(Modifier.height(dimensions.spacingSmall))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = direction == Direction.ASC,
                onClick = { onDirectionChange(Direction.ASC) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(getText(R.string.ascending)) }
            SegmentedButton(
                selected = direction == Direction.DESC,
                onClick = { onDirectionChange(Direction.DESC) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(getText(R.string.descending)) }
        }
    }
}

@Composable
fun FsrsConfigDialog(
    mode: SessionMode,
    deck: DeckWithCards,
    onDismiss: () -> Unit,
    onStart: (
        config: AutoSetConfig,
        mode: SessionMode, isWeighted: Boolean, promptSide: CardSide, numAnswers: Int,
        showLetters: Boolean, limitPool: Boolean, selectAnswer: Boolean,
        multiGuess: Boolean, stt: Boolean, hideText: Boolean, fingers: Boolean,
        maxTiles: Int, density: Int
    ) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val defaultPromptSide = remember(deck) {
        val cards = deck.cards
        if (cards.isEmpty()) CardSide.FRONT else {
            val avgFront = cards.map { it.front.length }.average()
            val avgBack = cards.map { it.back.length }.average()
            if (avgBack > (avgFront * 2)) CardSide.BACK else CardSide.FRONT
        }
    }

    var quizPromptSide by rememberSaveable { mutableStateOf(defaultPromptSide) }
    var numberOfAnswers by rememberSaveable { mutableStateOf(4) }
    var showCorrectLetters by rememberSaveable { mutableStateOf(true) }
    var selectAnswer by rememberSaveable { mutableStateOf(false) }
    var allowMultipleGuesses by rememberSaveable { mutableStateOf(true) }
    var enableStt by rememberSaveable { mutableStateOf(true) }
    var hideAnswerText by rememberSaveable { mutableStateOf(true) }
    var fingersAndToes by rememberSaveable { mutableStateOf(false) }
    var maxMemoryTiles by rememberSaveable { mutableStateOf(20) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge).verticalScroll(rememberScrollState())) {

                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            // FSRS sessions are always graded — TYPED_LISTEN/SPOKEN_LISTEN read
                            // as their quiz names ("Listen & Type"/"Listen & Speak") here.
                            text = mode.asString(),
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = getText(R.string.spaced_repetition_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    val closeFsrsInteractionSource = remember { MutableInteractionSource() }
                    val isCloseFsrsPressed by closeFsrsInteractionSource.collectIsPressedAsState()
                    val closeFsrsScale by animateFloatAsState(
                        targetValue = if (isCloseFsrsPressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "closeFsrsSquish"
                    )
                    TooltipIconButton(description = getText(R.string.close_capitalized), 
                        onClick = onDismiss,
                        interactionSource = closeFsrsInteractionSource,
                        modifier = Modifier.align(Alignment.TopEnd).scale(closeFsrsScale)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = getText(R.string.close_capitalized))
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                Spacer(Modifier.height(dimensions.spacingSmall))

                // M3 Expressive: Replace custom ToggleButtons with a Segmented Button Row
                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = quizPromptSide == CardSide.FRONT,
                        onClick = { quizPromptSide = CardSide.FRONT },
                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                    ) { Text(CardSide.FRONT.asString()) }
                    SegmentedButton(
                        selected = quizPromptSide == CardSide.BACK,
                        onClick = { quizPromptSide = CardSide.BACK },
                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                    ) { Text(CardSide.BACK.asString()) }
                }
                Spacer(Modifier.height(dimensions.spacingSmall))

                if (mode == SessionMode.MULTIPLE_CHOICE) {
                    // M3 Expressive: Use ListItem and upgrade to Tonal Icon Buttons
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.answers_count_format, numberOfAnswers)) },
                        trailingContent = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val lessInteractionSource = remember { MutableInteractionSource() }
                                val isLessPressed by lessInteractionSource.collectIsPressedAsState()
                                val lessScale by animateFloatAsState(targetValue = if (isLessPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "lessSquish")
                                TooltipFilledTonalIconButton(description = getText(R.string.less), onClick = { if (numberOfAnswers > 2) numberOfAnswers-- }, interactionSource = lessInteractionSource, modifier = Modifier.scale(lessScale)) { Icon(Icons.Default.Remove, getText(R.string.less)) }

                                Spacer(Modifier.width(dimensions.spacingSmall))

                                val moreInteractionSource = remember { MutableInteractionSource() }
                                val isMorePressed by moreInteractionSource.collectIsPressedAsState()
                                val moreScale by animateFloatAsState(targetValue = if (isMorePressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "moreSquish")
                                TooltipFilledTonalIconButton(description = getText(R.string.more), onClick = { if (numberOfAnswers < 8) numberOfAnswers++ }, interactionSource = moreInteractionSource, modifier = Modifier.scale(moreScale)) { Icon(Icons.Default.Add, getText(R.string.more)) }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
                if (mode == SessionMode.FLASHCARD) {
                    // M3 Expressive: Upgrade custom switch row to ListItem
                    ListItem(
                        headlineContent = { Text(getText(R.string.select_answer_picker)) },
                        trailingContent = { Switch(checked = selectAnswer, onCheckedChange = { selectAnswer = it }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = LocalIndication.current
                            ) { selectAnswer = !selectAnswer }
                    )
                }
                if (mode == SessionMode.TYPING) {
                    // M3 Expressive: Upgrade custom switch row to ListItem
                    ListItem(
                        headlineContent = { Text(getText(R.string.show_correct_letters)) },
                        trailingContent = { Switch(checked = showCorrectLetters, onCheckedChange = { showCorrectLetters = it }) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = LocalIndication.current
                            ) { showCorrectLetters = !showCorrectLetters }
                    )
                }

                Spacer(Modifier.height(dimensions.spacingLarge))

                val startSessionInteractionSource = remember { MutableInteractionSource() }
                val isStartSessionPressed by startSessionInteractionSource.collectIsPressedAsState()
                val startSessionScale by animateFloatAsState(
                    targetValue = if (isStartSessionPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "startSessionSquish"
                )
                // Enforce 56dp Height
                Button(
                    onClick = {
                        val config = AutoSetConfig(
                            mode = AutoSetCreationMode.ONE, numSets = 1, maxCardsPerSet = 9999,
                            selectionMode = SelectionMode.ANY, selectedTags = emptyList(), selectedDifficulties = emptyList(),
                            excludeKnown = false, sortMode = SortMode.REVIEW_DATE, sortDirection = Direction.ASC, sortSide = CardSide.FRONT,
                            schedulingMode = SchedulingMode.FSRS,
                        )
                        onStart(config, mode, false, quizPromptSide, numberOfAnswers, showCorrectLetters, false, selectAnswer, allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes, maxMemoryTiles, 2)
                    },
                    interactionSource = startSessionInteractionSource,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(startSessionScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.start_session))
                }
            }
        }
    }
}

/**
 * The "pick a category" content: a title/subtitle followed by one button per [StudyCategory] plus
 * Guided (FSRS), each with a short description underneath. Shared by the Study Hub's empty state
 * (title="No Active Sessions" + a subtitle) and the full-page dialog the FAB opens once sessions
 * already exist (title="Pick a Category", no subtitle) — see [StudyModeSelectionScreen].
 */
@Composable
fun CategoryPickerContent(
    title: String,
    subtitle: String?,
    onCategorySelected: (StudyCategory) -> Unit,
    onGuidedSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            FilledTonalButton(
                onClick = { onCategorySelected(StudyCategory.LEARN) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.category_learn),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_learn_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { onCategorySelected(StudyCategory.STUDY) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.category_practice),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_practice_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { onCategorySelected(StudyCategory.QUIZ) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.category_quiz),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_quiz_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { onCategorySelected(StudyCategory.GAMES) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.category_game),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_game_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onGuidedSelected,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.spaced_repetition_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_smart_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun FsrsModeSelectionDialog(onDismiss: () -> Unit, onModeSelected: (SessionMode) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(getText(R.string.spaced_repetition_label), style = MaterialTheme.typography.headlineSmall)
                Text(getText(R.string.mode_select), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(dimensions.spacingLarge))

                val modes = listOf(SessionMode.FLASHCARD, SessionMode.LIST, SessionMode.MULTIPLE_CHOICE, SessionMode.TYPING, SessionMode.SPOKEN_LISTEN, SessionMode.TYPED_LISTEN)
                modes.forEach { mode ->
                    Button(
                        onClick = { onModeSelected(mode) },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(bottom = dimensions.spacingSmall),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) { Text(mode.asString()) } // FSRS is always graded
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
            }
        }
    }
}

@Composable
fun HdLanguageSelectionDialog(
    languages: List<String>,
    downloadedLanguages: Set<String>, // NEW PARAMETER
    languageSizes: Map<String, String>,
    onDismiss: () -> Unit,
    onDownload: (List<String>) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val languageDisplayMap = remember(languages) {
        languages.associateWith { code ->
            try {
                Locale(code).displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            } catch (e: Exception) {
                code
            }
        }
    }

    // State for checkboxes: Initialize with ALL languages selected by default,
    // BUT exclude those already downloaded from the *active* selection set (since we can't download them again).
    val selectedLanguages = remember {
        mutableStateListOf<String>().apply {
            addAll(languages.filter { !downloadedLanguages.contains(it) })
        }
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusSmall))
                    .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
            ) {
                // --- HEADER ROW ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(getText(R.string.language), modifier = Modifier.weight(0.5f).padding(dimensions.paddingMedium), fontWeight = FontWeight.Bold)
                    VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                    // Size Header
                    Text(getText(R.string.size), modifier = Modifier.weight(0.3f).padding(dimensions.paddingSmall), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(getText(R.string.download), modifier = Modifier.weight(0.2f).padding(dimensions.paddingSmall), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // --- LIST CONTENT ---
                LazyColumn {
                    itemsIndexed(languages) { index, code ->
                        val name = languageDisplayMap[code] ?: code
                        val isDownloaded = downloadedLanguages.contains(code)
                        val size = languageSizes[code] ?: "?"

                        val rowInteractionSource = remember { MutableInteractionSource() }
                        val isRowPressed by rowInteractionSource.collectIsPressedAsState()
                        val rowScale by animateFloatAsState(
                            targetValue = if (isRowPressed && !isDownloaded) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "langRowSquish"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .scale(rowScale)
                                .clickable(
                                    interactionSource = rowInteractionSource,
                                    indication = LocalIndication.current,
                                    enabled = !isDownloaded
                                ) {
                                    if (selectedLanguages.contains(code)) selectedLanguages.remove(code)
                                    else selectedLanguages.add(code)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                modifier = Modifier.weight(0.5f).padding(dimensions.paddingMedium),
                                color = if(isDownloaded) MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f) else MaterialTheme.colorScheme.onSurface
                            )

                            VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)

                            // Size Value
                            Text(
                                size,
                                modifier = Modifier.weight(0.3f).padding(dimensions.paddingSmall),
                                textAlign = TextAlign.Center,
                                color = if(isDownloaded) Color.Gray else LocalContentColor.current
                            )

                            VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)

                            Box(
                                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDownloaded) {
                                    // Show Disabled Checked Box or Icon
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = null,
                                        enabled = false
                                    )
                                } else {
                                    Checkbox(
                                        checked = selectedLanguages.contains(code),
                                        onCheckedChange = { checked ->
                                            if (checked) selectedLanguages.add(code)
                                            else selectedLanguages.remove(code)
                                        }
                                    )
                                }
                            }
                        }

                        if (index < languages.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(dimensions.spacingMedium))

            // Select/Deselect All Buttons
            // Only affect languages that are NOT already downloaded
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val selectAllInteractionSource = remember { MutableInteractionSource() }
                val isSelectAllPressed by selectAllInteractionSource.collectIsPressedAsState()
                val selectAllScale by animateFloatAsState(targetValue = if (isSelectAllPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "selectAllSquish")
                TextButton(
                    onClick = {
                        selectedLanguages.clear()
                        selectedLanguages.addAll(languages.filter { !downloadedLanguages.contains(it) })
                    },
                    interactionSource = selectAllInteractionSource,
                    modifier = Modifier.scale(selectAllScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.select_all)) }

                val deselectAllInteractionSource = remember { MutableInteractionSource() }
                val isDeselectAllPressed by deselectAllInteractionSource.collectIsPressedAsState()
                val deselectAllScale by animateFloatAsState(targetValue = if (isDeselectAllPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "deselectAllSquish")
                TextButton(
                    onClick = { selectedLanguages.clear() },
                    interactionSource = deselectAllInteractionSource,
                    modifier = Modifier.scale(deselectAllScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.deselect_all)) }
            }

            Spacer(Modifier.height(dimensions.spacingMedium))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val cancelLangInteractionSource = remember { MutableInteractionSource() }
                val isCancelLangPressed by cancelLangInteractionSource.collectIsPressedAsState()
                val cancelLangScale by animateFloatAsState(
                    targetValue = if (isCancelLangPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "cancelLangSquish"
                )
                TextButton(
                    onClick = onDismiss,
                    interactionSource = cancelLangInteractionSource,
                    modifier = Modifier.scale(cancelLangScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.cancel)) }
                Spacer(Modifier.width(dimensions.spacingSmall))

                val downloadInteractionSource = remember { MutableInteractionSource() }
                val isDownloadPressed by downloadInteractionSource.collectIsPressedAsState()
                val downloadScale by animateFloatAsState(
                    targetValue = if (isDownloadPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "downloadLangSquish"
                )
                Button(
                    onClick = { onDownload(selectedLanguages.toList()) },
                    interactionSource = downloadInteractionSource,
                    modifier = Modifier.defaultMinSize(minHeight = 56.dp).scale(downloadScale),
                    // Enable only if there are NEW selections
                    enabled = selectedLanguages.isNotEmpty(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.download)) }
            }
        }
    }
}


/**
 * A horizontally-scrolling carousel of [SessionTile]s (plus a position indicator when there's more
 * than one) — a mode-section's content, or (when mode grouping is off but category grouping is on)
 * a merged carousel spanning every mode in a category.
 */
@Composable
private fun SessionTileCarousel(
    sessions: List<ActiveSession>,
    deck: DeckWithCards,
    showModeLabel: Boolean,
    showCategoryIcon: Boolean,
    onResume: (ActiveSession) -> Unit,
    onCopy: (ActiveSession) -> Unit,
    onRestart: (ActiveSession) -> Unit,
    onDelete: (ActiveSession) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingSmall)) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
            contentPadding = PaddingValues(horizontal = dimensions.paddingSmall)
        ) {
            itemsIndexed(items = sessions, key = { _, session -> session.id }) { _, session ->
                Box(modifier = Modifier.width(360.dp)) {
                    val cardIdToShow = if (session.mode == SessionMode.MATCHING && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(session.currentCardIndex)
                    val card = deck.cards.find { it.id == cardIdToShow }

                    SessionTile(
                        session = session,
                        card = card,
                        onResume = { onResume(session) },
                        onCopy = { onCopy(session) },
                        onRestart = { onRestart(session) },
                        onDelete = { onDelete(session) },
                        showModeLabel = showModeLabel,
                        showCategoryIcon = showCategoryIcon
                    )
                }
            }
        }

        // Scroll Indicator
        if (sessions.size > 1) {
            val currentIndex by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    if (visibleItemsInfo.isEmpty()) {
                        0
                    } else {
                        val viewportStart = layoutInfo.viewportStartOffset
                        val viewportEnd = layoutInfo.viewportEndOffset
                        val viewportCenter = viewportStart + (viewportEnd - viewportStart) / 2
                        visibleItemsInfo.minByOrNull {
                            kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                        }?.index ?: 0
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                sessions.indices.forEach { index ->
                    val isSelected = index == currentIndex
                    val width by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "dotWidth"
                    )
                    val color by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        label = "dotColor"
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

/**
 * A wrapping grid of [SessionTile]s — used only when both category and mode grouping are off,
 * since at that point there's no longer a single mode/category to browse through with a carousel;
 * everything is shown at once instead.
 */
@Composable
private fun SessionTileGrid(
    sessions: List<ActiveSession>,
    deck: DeckWithCards,
    showModeLabel: Boolean,
    showCategoryIcon: Boolean,
    onResume: (ActiveSession) -> Unit,
    onCopy: (ActiveSession) -> Unit,
    onRestart: (ActiveSession) -> Unit,
    onDelete: (ActiveSession) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
    ) {
        sessions.forEach { session ->
            Box(modifier = Modifier.width(360.dp)) {
                val cardIdToShow = if (session.mode == SessionMode.MATCHING && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(session.currentCardIndex)
                val card = deck.cards.find { it.id == cardIdToShow }

                SessionTile(
                    session = session,
                    card = card,
                    onResume = { onResume(session) },
                    onCopy = { onCopy(session) },
                    onRestart = { onRestart(session) },
                    onDelete = { onDelete(session) },
                    showModeLabel = showModeLabel,
                    showCategoryIcon = showCategoryIcon
                )
            }
        }
    }
}

/**
 * A composable that displays a single study session tile.
 * It shows a preview of the current card, session settings, and provides an overflow menu
 * with options to copy, restart, or delete the session.
 * @param session The study session to display.
 * @param card The current card in the session for the preview.
 * @param onResume Callback for when the tile is clicked.
 * @param onCopy Callback for the copy action.
 * @param onRestart Callback for the restart action.
 * @param onDelete Callback for the delete action.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SessionTile(
    session: ActiveSession,
    card: Card?,
    onResume: () -> Unit,
    onCopy: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    showModeLabel: Boolean = false,
    showCategoryIcon: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val yesStr = stringResource(R.string.yes)
    val noStr = stringResource(R.string.no)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tileSquish"
    )

    if (showInfoDialog) {
        SessionInfoDialog(session = session, onDismiss = { showInfoDialog = false })
    }

    ElevatedCard(
        onClick = onResume,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .border(if (isFocused) 6.dp else 0.dp, borderColor, RoundedCornerShape(dimensions.cornerRadiusMedium)),
        interactionSource = interactionSource,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation, pressedElevation = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(dimensions.paddingMedium)
        ) {
            // --- TOP HEADER: Progress/Mode Label & Actions ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val completedCrosswordCount = remember(session.crosswordWords, session.crosswordUserInputs) {
                    completedCrosswordWordCount(session)
                }

                val progressText = when (session.mode) {
                    SessionMode.MEMORY -> stringResource(R.string.pairs_progress_format, session.matchedPairs.size, session.totalCards)
                    SessionMode.MATCHING -> stringResource(R.string.matched_progress_format, session.matchedPairs.size, session.totalCards)
                    SessionMode.CROSSWORD -> stringResource(R.string.words_progress_format, completedCrosswordCount, session.crosswordWords.size)
                    SessionMode.WORD_SEARCH -> stringResource(R.string.words_progress_format, session.wordSearchFoundWordIds.size, session.wordSearchWords.size)
                    else -> stringResource(R.string.progress_format, session.currentCardIndex, session.totalCards)
                }

                // When mode grouping is off there's no mode header to identify this tile, so the
                // title swaps to the mode name. The category icon is independent of that — it shows
                // whenever category grouping is off, whether or not the mode label is also showing,
                // since either way there's no category header left to say which one this is. The
                // progress bar below is unaffected either way, so the numeric progress is never lost.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (showCategoryIcon) {
                        Icon(
                            imageVector = sessionModeIcon(session),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp).padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = if (showModeLabel) session.mode.asString() else progressText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = net.ericclark.studiare.components.formatTimeAgo(session.lastAccessed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Actions Row
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TooltipIconButton(description = "Session Info", onClick = { showInfoDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Session Info", tint = MaterialTheme.colorScheme.secondary)
                    }
                    Box {
                        TooltipIconButton(description = getText(R.string.session_options), onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.session_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(getText(R.string.copy)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { onCopy(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                            DropdownMenuItem(text = { Text(getText(R.string.restart)) }, leadingIcon = { Icon(Icons.Default.RestartAlt, null) }, onClick = { onRestart(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                            DropdownMenuItem(text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- PROGRESS BAR ---
            val progressValue = sessionProgressFraction(session)

            androidx.compose.material3.LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- BOTTOM CONTENT: Preview & Nested Info Cards ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
                // 1. Preview Area (Left side)

                // Fetch the transition scopes
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(120.dp)
                        .then(
                            // Apply the SharedBounds modifier to map this preview to the study card
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "session_card_${session.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    if (session.mode == SessionMode.MEMORY) {
                        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(dimensions.cornerRadiusSmall), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(2) { r -> Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(2) { c -> Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if ((r + c) % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(2.dp))) } } }
                            }
                        }
                    } else if (session.mode == SessionMode.CROSSWORD) {
                        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(dimensions.cornerRadiusSmall), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            val activeCells = remember(session.crosswordWords) { val cells = mutableSetOf<Pair<Int, Int>>(); session.crosswordWords.forEach { word -> for (i in word.word.indices) { cells.add((if (word.isAcross) word.startX + i else word.startX) to (if (word.isAcross) word.startY else word.startY + i)) } }; cells }
                            val cellColor = MaterialTheme.colorScheme.primaryContainer
                            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                if (session.crosswordGridWidth > 0 && session.crosswordGridHeight > 0) {
                                    val gw = session.crosswordGridWidth.toFloat(); val gh = session.crosswordGridHeight.toFloat()
                                    val cellSize = kotlin.math.min(size.width / gw, size.height / gh)
                                    val offsetX = (size.width - (cellSize * gw)) / 2; val offsetY = (size.height - (cellSize * gh)) / 2
                                    activeCells.forEach { (x, y) -> drawRect(color = cellColor, topLeft = Offset(offsetX + (x * cellSize), offsetY + (y * cellSize)), size = androidx.compose.ui.geometry.Size(cellSize - 2f, cellSize - 2f)) }
                                }
                            }
                        }
                    } else {
                        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(dimensions.cornerRadiusSmall), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            val cardColor = when (session.mode) {
                                SessionMode.TYPING_SCORED, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN -> if (session.quizPromptSide == CardSide.BACK) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                                else -> if (session.isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                            }
                            Box(modifier = Modifier.fillMaxSize().background(cardColor).padding(8.dp), contentAlignment = Alignment.Center) {
                                if (card != null) {
                                    val textToShow = when (session.mode) {
                                        SessionMode.TYPING_SCORED, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN, SessionMode.CROSSWORD -> if (session.quizPromptSide == CardSide.BACK) card.back else card.front
                                        else -> if (session.isFlipped) card.back else card.front
                                    }
                                    Text(text = textToShow, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // 2. Information Cards Area (Right side)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Settings Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(dimensions.cornerRadiusSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (session.mode == SessionMode.FLASHCARD || session.mode == SessionMode.LIST) {
                                Text(stringResource(R.string.graded_format, if (session.isGraded) yesStr else noStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                if (session.mode == SessionMode.LIST) {
                                    Text(stringResource(R.string.prompt_format, session.quizPromptSide.asString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            } else if (session.mode == SessionMode.TYPING_SCORED || session.mode == SessionMode.TYPING || session.mode == SessionMode.ANAGRAM || session.mode == SessionMode.CROSSWORD) {
                                Text(stringResource(R.string.prompt_format, session.quizPromptSide.asString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            } else if (session.mode == SessionMode.MULTIPLE_CHOICE || session.mode == SessionMode.MATCHING) {
                                Text(stringResource(R.string.graded_format, if (session.isGraded) yesStr else noStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(stringResource(R.string.reveal_when_wrong_format, if (!session.allowMultipleGuesses) yesStr else noStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            } else {
                                Text(stringResource(R.string.weighted_format, if (session.isWeighted) yesStr else noStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    // Difficulty Card
                    if (session.difficulties.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = RoundedCornerShape(dimensions.cornerRadiusSmall),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.difficulties_format, session.difficulties.joinToString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionInfoDialog(
    session: ActiveSession,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy 'at' h:mm a", Locale.getDefault()) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Session Details",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                // 1. Selection Mode Breakdown
                val selectionModeLabel = session.selectionMode.asString()
                val filterTypeLabel = session.filterType.asString()
                val timeUnitLabel = session.timeUnit.asString()
                val selectionText = buildString {
                    append(selectionModeLabel)

                    // Append specific data based on the mode chosen!
                    when (session.selectionMode) {
                        SelectionMode.DIFFICULTY -> if (session.difficulties.isNotEmpty()) append(" (${session.difficulties.joinToString()})")
                        SelectionMode.TAGS -> if (session.selectedTags.isNotEmpty()) append(" (${session.selectedTags.joinToString()})")
                        SelectionMode.ALPHABET -> append(" (${session.alphabetStart} to ${session.alphabetEnd})")
                        SelectionMode.CARD_ORDER -> append(" (#${session.cardOrderStart} to #${session.cardOrderEnd})")
                        SelectionMode.REVIEW_DATE, SelectionMode.INCORRECT_DATE -> append(" ($filterTypeLabel past ${session.timeValue} $timeUnitLabel)")
                        SelectionMode.REVIEW_COUNT -> append(" (${if (session.reviewCountDirection == Direction.ASC) ">=" else "<="} ${session.reviewCountThreshold})")
                        SelectionMode.SCORE -> append(" (${if (session.scoreDirection == Direction.ASC) ">=" else "<="} ${session.scoreThreshold}%)")
                        else -> {}
                    }
                }

                ListItem(
                    headlineContent = { Text("Selection Mode", color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(selectionText, style = MaterialTheme.typography.bodyLarge) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                // 2. Sort & Priority Breakdown
                val orderStr = session.cardOrder.asString()
                val sortDirectionLabel = session.sortDirection.asString()

                ListItem(
                    headlineContent = { Text("Sort & Priority", color = MaterialTheme.colorScheme.primary) },
                    supportingContent = {
                        val priorityStr = if (session.schedulingMode == SchedulingMode.FSRS) "FSRS" else if (session.isWeighted) "Weighted" else "Standard"
                        Text("$orderStr ($sortDirectionLabel) • $priorityStr", style = MaterialTheme.typography.bodyLarge)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                // 3. Size
                ListItem(
                    headlineContent = { Text("Total Cards", color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(session.totalCards.toString(), style = MaterialTheme.typography.bodyLarge) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                // 4. Dates
                ListItem(
                    headlineContent = { Text("Date Created", color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(dateFormat.format(Date(session.createdAt)), style = MaterialTheme.typography.bodyLarge) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text("Last Used", color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(dateFormat.format(Date(session.lastAccessed)), style = MaterialTheme.typography.bodyLarge) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                Spacer(Modifier.height(dimensions.spacingLarge))

                val dismissInteractionSource = remember { MutableInteractionSource() }
                val isDismissPressed by dismissInteractionSource.collectIsPressedAsState()
                val dismissScale by animateFloatAsState(
                    targetValue = if (isDismissPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "dismissSquish"
                )

                Button(
                    onClick = onDismiss,
                    interactionSource = dismissInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .scale(dismissScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.close_capitalized))
                }
            }
        }
    }
}



/**
 * A screen displayed when a study session is completed.
 * It shows a congratulatory message and provides options to restart the session,
 * start a new one, or go back to the deck list.
 * @param navController The NavController for navigating back.
 * @param viewModel The ViewModel providing the study state.
 */
@Composable
fun StudyCompletionScreen(navController: NavController, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val state = viewModel.studyState ?: return
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    val incorrectCards = remember(state.shuffledCards, state.incorrectCardIds) {
        state.shuffledCards.filter { it.id in state.incorrectCardIds }
    }

    var notScored = false
    if (state.studyMode == SessionMode.FLASHCARD || state.studyMode == SessionMode.TYPING || state.studyMode == SessionMode.CROSSWORD ||
        state.studyMode == SessionMode.MEMORY || state.studyMode == SessionMode.ANAGRAM || state.studyMode == SessionMode.HANGMAN ||
        state.studyMode == SessionMode.FREEFORM || state.studyMode == SessionMode.WORD_SEARCH)
        notScored = true
    // Typing mode shouldn't show review button as it forces correctness before moving on
    val showReviewButton = incorrectCards.isNotEmpty() && (notScored)

    val allDecksState by viewModel.allDecks.observeAsState(emptyList())
    val navigateUp = {
        viewModel.deleteCurrentStudySession()
        viewModel.endStudySession()
        // The study route was opened on top of wherever the session list lives (a pane
        // on the deck list, or a standalone route), so simply popping returns there.
        navController.popBackStack()
        Unit
    }

    BackHandler(onBack = navigateUp)

    Scaffold(
        topBar = {
            Column {
                CustomTopAppBar(
                    viewModel = viewModel,
                    screenId = ShortcutScreen.OTHER,
                    title = { Text(state.studyMode.asString(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        TooltipIconButton(description = "Back", onClick = navigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
                if (state.deckWithCards != null) {
                    BreadcrumbsBar(
                        currentDeck = state.deckWithCards!!.deck,
                        allDecks = allDecksState.map { it.deck },
                        onNavigateHome = {
                            viewModel.deleteCurrentStudySession()
                            viewModel.endStudySession()
                            navController.navigate("deckList") { popUpTo(0) }
                        },
                        onNavigateToDeck = { deckId ->
                            viewModel.deleteCurrentStudySession()
                            viewModel.endStudySession()
                            navController.navigate("setManager/$deckId") {
                                popUpTo("deckList") { inclusive = false }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(dimensions.paddingMedium),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
            ) {
                // Expressive Celebration Icon
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(getText(R.string.congratulations), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(getText(R.string.completed_session_msg), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Hide accuracy score for Typing mode, show it expressively otherwise
                if (!notScored) {
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Surface(
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        val score = (state.firstTryCorrectCount.toFloat() / state.shuffledCards.size * 100).roundToInt()
                        Text(
                            text = stringResource(R.string.first_try_accuracy_format, score),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium)
                        )
                    }
                }

                Spacer(Modifier.height(dimensions.spacingLarge))

                AnimatedVisibility(
                    visible = showReviewButton,
                    enter = slideInVertically() + fadeIn() + expandVertically(),
                    exit = slideOutVertically() + fadeOut() + shrinkVertically()
                ) {
                    Button(
                        onClick = {
                            viewModel.startReviewSession { route ->
                                navController.popBackStack() // Go back to session selection
                                navController.navigate(route) // Go to the new review session
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton), // M3 Expressive Pill shape
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.review_incorrect_cards_format, incorrectCards.size), style = MaterialTheme.typography.labelLarge)
                    }
                }

                val backSessionsInteractionSource = remember { MutableInteractionSource() }
                val isBackSessionsPressed by backSessionsInteractionSource.collectIsPressedAsState()
                val backSessionsScale by animateFloatAsState(
                    targetValue = if (isBackSessionsPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "backSessionsSquish"
                )
                FilledTonalButton(
                    onClick = {
                        viewModel.deleteCurrentStudySession()
                        viewModel.endStudySession()
                        navController.popBackStack()
                    },
                    interactionSource = backSessionsInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp).scale(backSessionsScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.back_to_sessions))
                }

                val restartInteractionSource = remember { MutableInteractionSource() }
                val isRestartPressed by restartInteractionSource.collectIsPressedAsState()
                val restartScale by animateFloatAsState(
                    targetValue = if (isRestartPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "restartSquish"
                )
                Button(
                    onClick = { viewModel.restartSameSession() },
                    interactionSource = restartInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp).scale(restartScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.restart_this_session), style = MaterialTheme.typography.labelLarge)
                }

                // M3 Expressive Secondary Actions: Tonal Buttons
                val startInteractionSource = remember { MutableInteractionSource() }
                val isStartPressed by startInteractionSource.collectIsPressedAsState()
                val startScale by animateFloatAsState(
                    targetValue = if (isStartPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "startSquish"
                )
                FilledTonalButton(
                    onClick = { viewModel.restartStudySession() },
                    interactionSource = startInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp).scale(startScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.start_new_session))
                }

                val backDecksInteractionSource = remember { MutableInteractionSource() }
                val isBackDecksPressed by backDecksInteractionSource.collectIsPressedAsState()
                val backDecksScale by animateFloatAsState(
                    targetValue = if (isBackDecksPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "backDecksSquish"
                )
                FilledTonalButton(
                    onClick = {
                        viewModel.deleteCurrentStudySession()
                        viewModel.endStudySession()
                        if (state.deckWithCards?.deck?.parentDeckId != null) {
                            navController.navigate("setManager/${state.deckWithCards!!.deck.parentDeckId}") {
                                popUpTo("setManager/${state.deckWithCards!!.deck.parentDeckId}") { inclusive = true }
                            }
                        } else {
                            navController.popBackStack("deckList", inclusive = false)
                        }
                    },
                    interactionSource = backDecksInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp).scale(backDecksScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.back_to_decks))
                }
            }
        }
    }
}


@Composable
fun EditCardDialog(
    cardToEdit: Card,
    viewModel: FlashcardViewModel,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var front by remember { mutableStateOf(cardToEdit.front) }
    var frontRichText by remember { mutableStateOf(cardToEdit.frontRichText) }
    var isFrontRichText by remember { mutableStateOf(!cardToEdit.frontRichText.isNullOrBlank()) }
    var back by remember { mutableStateOf(cardToEdit.back) }
    var backRichText by remember { mutableStateOf(cardToEdit.backRichText) }
    var isBackRichText by remember { mutableStateOf(!cardToEdit.backRichText.isNullOrBlank()) }
    var frontNotes by remember { mutableStateOf(cardToEdit.frontNotes) }
    var backNotes by remember { mutableStateOf(cardToEdit.backNotes) }
    var difficulty by remember { mutableStateOf(cardToEdit.difficulty) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- NEW: Tag State ---
    var tags by remember { mutableStateOf(cardToEdit.tags) }

    var richTextTarget by remember { mutableStateOf<String?>(null) }
    var richTextHtml by remember { mutableStateOf("") }
    var richTextTitle by remember { mutableStateOf("") }

    // Collect all tags to pass to the picker
    val allTags by viewModel.tags.collectAsState()

    // Determine tags in the current deck for "Quick Select" (Context aware)
    val studyState = viewModel.studyState
    val currentDeckTags = remember(studyState) {
        studyState?.deckWithCards?.cards?.flatMap { it.tags }?.toSet() ?: emptySet()
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        getText(R.string.edit_card),
                        style = MaterialTheme.typography.headlineSmall,
                    )

                    val closeInteractionSource = remember { MutableInteractionSource() }
                    val isClosePressed by closeInteractionSource.collectIsPressedAsState()
                    val closeScale by animateFloatAsState(
                        targetValue = if (isClosePressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "closeSquish"
                    )
                    TooltipIconButton(description = getText(R.string.discard_changes), 
                        onClick = onDismiss,
                        interactionSource = closeInteractionSource,
                        modifier = Modifier.scale(closeScale)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = getText(R.string.discard_changes))
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))

                CardSideEditor(
                    sideLabel = CardSide.FRONT.asString(),
                    plainText = front,
                    onPlainTextChange = { front = it },
                    isRichText = isFrontRichText,
                    onToggleRichText = { isRich ->
                        isFrontRichText = isRich
                        if (!isRich) {
                            frontRichText = null
                            front = front.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                        }
                    },
                    onEditRichTextClick = {
                        richTextHtml = frontRichText ?: front
                        richTextTitle = "Edit Front (Rich Text)"
                        richTextTarget = "front"
                    },
                    actionIcon = {
                        TooltipIconButton(description = "Add Front Note", onClick = {
                            frontNotes = frontNotes + NoteField("Front Note", "", MediaType.PLAIN_TEXT.toString())
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Front Note", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                frontNotes.forEachIndexed { index, note ->
                    val enterTransition = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                    AnimatedVisibility(
                        visibleState = enterTransition,
                        enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    ) {
                        DynamicNoteEditor(
                            note = note,
                            noteIndex = index,
                            onNoteChange = { updatedNote ->
                                val newList = frontNotes.toMutableList()
                                newList[index] = updatedNote
                                frontNotes = newList
                            },
                            onEditRichTextClick = {
                                richTextHtml = note.content
                                richTextTitle = "Edit ${note.name}"
                                richTextTarget = "frontNote_$index"
                            },
                            onRemove = {
                                val removedNote = frontNotes[index]
                                val newList = frontNotes.toMutableList()
                                newList.removeAt(index)
                                frontNotes = newList

                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar("Note removed", "Undo")
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        val restoreList = frontNotes.toMutableList()
                                        restoreList.add(index.coerceIn(0, restoreList.size), removedNote)
                                        frontNotes = restoreList
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                CardSideEditor(
                    sideLabel = CardSide.BACK.asString(),
                    plainText = back,
                    onPlainTextChange = { back = it },
                    isRichText = isBackRichText,
                    onToggleRichText = { isRich ->
                        isBackRichText = isRich
                        if (!isRich) {
                            backRichText = null
                            back = back.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                        }
                    },
                    onEditRichTextClick = {
                        richTextHtml = backRichText ?: back
                        richTextTitle = "Edit Back (Rich Text)"
                        richTextTarget = "back"
                    },
                    actionIcon = {
                        TooltipIconButton(description = "Add Back Note", onClick = {
                            backNotes = backNotes + NoteField("Back Note", "", MediaType.PLAIN_TEXT.toString())
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add Back Note", tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                backNotes.forEachIndexed { index, note ->
                    val enterTransition = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                    AnimatedVisibility(
                        visibleState = enterTransition,
                        enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    ) {
                        DynamicNoteEditor(
                            note = note,
                            noteIndex = index,
                            onNoteChange = { updatedNote ->
                                val newList = backNotes.toMutableList()
                                newList[index] = updatedNote
                                backNotes = newList
                            },
                            onEditRichTextClick = {
                                richTextHtml = note.content
                                richTextTitle = "Edit ${note.name}"
                                richTextTarget = "backNote_$index"
                            },
                            onRemove = {
                                val removedNote = backNotes[index]
                                val newList = backNotes.toMutableList()
                                newList.removeAt(index)
                                backNotes = newList

                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar("Note removed", "Undo")
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        val restoreList = backNotes.toMutableList()
                                        restoreList.add(index.coerceIn(0, restoreList.size), removedNote)
                                        backNotes = restoreList
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                // --- NEW: Tag Row Component ---
                Text(getText(R.string.tags), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
                CardTagRow(
                    cardTags = tags,
                    allTags = allTags,
                    currentDeckTags = currentDeckTags,
                    onUpdateTags = { newTags -> tags = newTags.toList() },
                    onCreateTag = { name, color ->
                        viewModel.saveTagDefinition(TagDefinition(name = name, color = color))
                    }
                )

                Spacer(Modifier.height(dimensions.spacingSmall))

                val currentCardFromState = viewModel.studyState?.deckWithCards?.cards?.find { it.id == cardToEdit.id } ?: cardToEdit

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DifficultySlider(
                        label = getText(R.string.difficulty),
                        difficulty = difficulty,
                        onDifficultyChange = { difficulty = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(dimensions.spacingMedium))
                    Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                        MarkKnownButton(
                            isKnown = currentCardFromState.isKnown,
                            onClick = { viewModel.toggleCardKnownStatus(currentCardFromState) }
                        )
                    }
                }
                Spacer(Modifier.height(dimensions.spacingLarge))

                val saveInteractionSource = remember { MutableInteractionSource() }
                val isSavePressed by saveInteractionSource.collectIsPressedAsState()
                val saveScale by animateFloatAsState(
                    targetValue = if (isSavePressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "saveCardSquish"
                )
                Button(
                    onClick = {
                        val updatedCard = cardToEdit.copy(
                            front = front.trim(),
                            frontRichText = frontRichText?.trim()?.takeIf { it.isNotBlank() },
                            back = back.trim(),
                            backRichText = backRichText?.trim()?.takeIf { it.isNotBlank() },
                            frontNotes = frontNotes,
                            backNotes = backNotes,
                            difficulty = difficulty,
                            tags = tags // Save updated tags
                        )
                        viewModel.updateCard(updatedCard)
                        onDismiss()
                    },
                    interactionSource = saveInteractionSource,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(saveScale),
                    enabled = front.isNotBlank() && back.isNotBlank(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.save_changes))
                }
            }
        }
        if (richTextTarget != null) {
            RichTextEditorDialog(
                initialHtml = richTextHtml,
                title = richTextTitle,
                onDismiss = { richTextTarget = null },
                onSave = { savedHtml ->
                    val plainText = savedHtml.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                    when {
                        richTextTarget == "front" -> {
                            frontRichText = savedHtml
                            front = plainText
                        }
                        richTextTarget == "back" -> {
                            backRichText = savedHtml
                            back = plainText
                        }
                        richTextTarget?.startsWith("frontNote_") == true -> {
                            val index = richTextTarget!!.substringAfter("_").toInt()
                            val currentList = frontNotes.toMutableList()
                            currentList[index] = currentList[index].copy(content = savedHtml)
                            frontNotes = currentList
                        }
                        richTextTarget?.startsWith("backNote_") == true -> {
                            val index = richTextTarget!!.substringAfter("_").toInt()
                            val currentList = backNotes.toMutableList()
                            currentList[index] = currentList[index].copy(content = savedHtml)
                            backNotes = currentList
                        }
                    }
                    richTextTarget = null
                }
            )
        }
    }
}