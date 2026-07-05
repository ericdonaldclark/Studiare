package net.ericclark.studiare

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.platform.LocalLocale
import kotlinx.coroutines.launch
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.livedata.observeAsState

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
    var showCreateSessionDialog by rememberSaveable { mutableStateOf<StudyPreset?>(null) }
    var showFsrsConfigDialog by rememberSaveable { mutableStateOf<SessionMode?>(null) }
    var showFsrsModeDialog by rememberSaveable { mutableStateOf(false) }
    var fabExpanded by rememberSaveable { mutableStateOf(false) }
    val allActiveSessions by viewModel.allActiveSessions.collectAsState()
    val activeSessions = remember(allActiveSessions, deck.deck.id) {
        allActiveSessions.filter { it.deckId == deck.deck.id }
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
                "study" -> showCreateSessionDialog = StudyPreset.STUDY
                "quiz" -> showCreateSessionDialog = StudyPreset.QUIZ
                "game" -> showCreateSessionDialog = StudyPreset.GAMES
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

    // Define sections explicitly
    data class SessionSection(val title: String, val filter: (ActiveSession) -> Boolean)

    val sections = listOf(
        SessionSection(stringResource(R.string.section_flashcards)) { it.mode == SessionMode.FLASHCARD },
        SessionSection(stringResource(R.string.section_freeform)) { it.mode == SessionMode.FREEFORM },
        SessionSection(stringResource(R.string.section_list)) { it.mode == SessionMode.LIST },
        SessionSection(stringResource(R.string.section_mc_practice)) { it.mode == SessionMode.MULTIPLE_CHOICE && !it.isGraded },
        SessionSection(stringResource(R.string.section_mc_quiz)) { it.mode == SessionMode.MULTIPLE_CHOICE && it.isGraded },
        SessionSection(stringResource(R.string.section_matching_practice)) { it.mode == SessionMode.MATCHING && !it.isGraded },
        SessionSection(stringResource(R.string.section_matching_quiz)) { it.mode == SessionMode.MATCHING && it.isGraded },
        SessionSection(stringResource(R.string.section_typing_practice)) { it.mode == SessionMode.TYPING },
        SessionSection(stringResource(R.string.section_typing_quiz)) { it.mode == SessionMode.QUIZ },
        SessionSection(stringResource(R.string.section_audio_practice)) { it.mode == SessionMode.AUDIO && !it.isGraded },
        SessionSection(stringResource(R.string.section_audio_quiz)) { it.mode == SessionMode.AUDIO && it.isGraded },
        SessionSection(stringResource(R.string.section_anagram)) { it.mode == SessionMode.ANAGRAM },
        SessionSection(stringResource(R.string.section_hangman)) { it.mode == SessionMode.HANGMAN },
        SessionSection(stringResource(R.string.section_memory)) { it.mode == SessionMode.MEMORY },
        SessionSection(stringResource(R.string.section_crossword)) { it.mode == SessionMode.CROSSWORD },
        SessionSection(stringResource(R.string.section_word_search)) { it.mode == SessionMode.WORD_SEARCH }
    )

    // Dialog States
    var showRestartDialog by remember { mutableStateOf<ActiveSession?>(null) }
    var showDeleteDialog by remember { mutableStateOf<ActiveSession?>(null) }
    var showDeleteAllSessionsDialog by remember { mutableStateOf(false) }

    var expandedStates by remember { mutableStateOf(mapOf<String, Boolean>()) }

    // HD Audio Prompt States
    val hasPromptedHd by viewModel.hasPromptedHdLanguages.collectAsState()
    var showHdPromptDialog by remember { mutableStateOf(false) }
    var showHdSelectionDialog by remember { mutableStateOf(false) }

    val downloadedHdLanguages by viewModel.downloadedHdLanguages.collectAsState()
    // Store pending session start action to execute after dialogs
    var pendingSessionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val context = LocalContext.current

    DisposableEffect(deck.deck.id) {
        viewModel.setCurrentDeckId(deck.deck.id)
        onDispose {
            viewModel.setCurrentDeckId(null)
        }
    }

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

    showCreateSessionDialog?.let { preset ->
        CreateStudySessionDialog(
            deck = deck,
            preset = preset, // NEW: Pass the selected preset from the FAB menu
            availableTags = parentDeckTags,
            allTagDefinitions = allTags,
            onDismiss = { showCreateSessionDialog = null },
            onStartSession = { mode, isWeighted, numCards, quizPromptSide, numAnswers, showLetters, limitPool,
                               isGraded, allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes,
                               maxMemoryTiles, gridDensity, showCorrectWords, freeformVerticalLayout, config,  ->
                showCreateSessionDialog = null

                var internalMode = mode
                if (mode == SessionMode.TYPING && isGraded) {
                    internalMode = SessionMode.QUIZ
                }

                // Logic for NEW sessions
                val route = when (internalMode) {
                    SessionMode.FLASHCARD -> "flashcardStudy"
                    SessionMode.LIST -> "flashcardQuizStudy"
                    SessionMode.MULTIPLE_CHOICE -> "mcStudy"
                    SessionMode.MATCHING -> "matchingStudy"
                    SessionMode.TYPING -> "typingStudy"
                    SessionMode.QUIZ -> "quizStudy"
                    SessionMode.AUDIO -> "audioStudy"
                    SessionMode.ANAGRAM -> "anagramStudy"
                    SessionMode.HANGMAN -> "hangmanStudy"
                    SessionMode.MEMORY -> "memoryStudy"
                    SessionMode.CROSSWORD -> "crosswordStudy"
                    SessionMode.WORD_SEARCH -> "wordSearchStudy"
                    SessionMode.FREEFORM -> "freeformStudy"
                    else -> "flashcardStudy"
                }

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

                // Intercept if Audio mode and never prompted
                if (mode == SessionMode.AUDIO && !hasPromptedHd) {
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

    val groupedSessions by produceState(initialValue = emptyMap<String, List<ActiveSession>>(), key1 = activeSessions) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val displayed = activeSessions.filter { it.schedulingMode != SchedulingMode.FSRS }
            sections.associate { section ->
                section.title to displayed.filter(section.filter).sortedByDescending { it.lastAccessed }
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

    val isDataLoaded by viewModel.isInitialDataLoaded.collectAsState()

    val allDecksState by viewModel.allDecks.observeAsState(emptyList())
    val navigateUp = {
        if (isPane) {
            viewModel.setCurrentSetId(null)
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
        LaunchedEffect(deck.deck.name) {
            onChromeChanged(PaneChrome(title = { Text(deck.deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis) }))
        }
    }

    val paneContent: @Composable (PaddingValues) -> Unit = { padding ->
        val focusRequester = remember { FocusRequester() }
        LaunchedEffect(Unit) { focusRequester.requestFocus() }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyUp) {
                        when (event.key) {
                            Key.Backspace -> {
                                navigateUp()
                                return@onPreviewKeyEvent true
                            }
                            Key.P -> {
                                showCreateSessionDialog = StudyPreset.STUDY
                                return@onPreviewKeyEvent true
                            }
                            Key.Q -> {
                                showCreateSessionDialog = StudyPreset.QUIZ
                                return@onPreviewKeyEvent true
                            }
                            Key.G -> {
                                showCreateSessionDialog = StudyPreset.GAMES
                                return@onPreviewKeyEvent true
                            }
                            Key.S -> {
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
                    !isDataLoaded -> 0 // STATE 0: Loading
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
                        // STATE 0: Loading Spinner (Prevents flashing)
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            LoadingIndicator()
                        }
                    }
                    1 -> {
                        // STATE 1: Empty State
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = getText(R.string.no_active_sessions),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = getText(R.string.no_active_sessions_description),
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )

                                Spacer(Modifier.height(32.dp))

                                FilledTonalButton(
                                    onClick = { showCreateSessionDialog = StudyPreset.STUDY },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.MenuBook,
                                            contentDescription = null,
                                            modifier = Modifier.align(Alignment.CenterStart)
                                        )
                                        Text(
                                            text = getText(R.string.preset_practice),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                FilledTonalButton(
                                    onClick = { showCreateSessionDialog = StudyPreset.QUIZ },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.Quiz,
                                            contentDescription = null,
                                            modifier = Modifier.align(Alignment.CenterStart)
                                        )
                                        Text(
                                            text = getText(R.string.preset_quiz),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                FilledTonalButton(
                                    onClick = { showCreateSessionDialog = StudyPreset.GAMES },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.SportsEsports,
                                            contentDescription = null,
                                            modifier = Modifier.align(Alignment.CenterStart)
                                        )
                                        Text(
                                            text = getText(R.string.preset_game),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Button(
                                    onClick = { showFsrsModeDialog = true },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
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
                            }
                        }
                    }
                    2 -> {
                        // STATE 2: Populated List
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(
                                start = dimensions.paddingMedium,
                                top = dimensions.paddingMedium,
                                end = dimensions.paddingMedium,
                                bottom = 80.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                        ) {
                            sections.forEach { section ->
                                val sessionsInSection = groupedSessions[section.title] ?: emptyList()
                                if (sessionsInSection.isNotEmpty()) {
                                    val isExpanded = expandedStates[section.title] ?: true

                                    // 1. Collapsible Header Row
                                    item {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                                                .clickable {
                                                    expandedStates = expandedStates + (section.title to !isExpanded)
                                                }
                                                .padding(
                                                    vertical = dimensions.paddingSmall,
                                                    horizontal = dimensions.paddingSmall
                                                ),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column {
                                                Text(
                                                    text = section.title,
                                                    style = MaterialTheme.typography.headlineSmall
                                                )
                                                if (!isExpanded) {
                                                    Text(
                                                        text = "${sessionsInSection.size} Sessions",
                                                        style = MaterialTheme.typography.bodyMedium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }

                                            val iconRotation by animateFloatAsState(
                                                targetValue = if (isExpanded) 180f else 0f,
                                                animationSpec = spring(
                                                    dampingRatio = Spring.DampingRatioNoBouncy,
                                                    stiffness = Spring.StiffnessMedium
                                                ),
                                                label = "chevronRotation"
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ExpandMore,
                                                contentDescription = if (isExpanded) "Collapse" else "Expand",
                                                modifier = Modifier.graphicsLayer {
                                                    rotationZ = iconRotation
                                                }
                                            )
                                        }
                                    }

                                    // 2. Expandable Content Area
                                    item {
                                        AnimatedVisibility(visible = isExpanded) {
                                            Column(
                                                modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingMedium)
                                            ) {
                                                val listState = androidx.compose.foundation.lazy.rememberLazyListState()

                                                androidx.compose.foundation.lazy.LazyRow(
                                                    state = listState,
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
                                                    contentPadding = PaddingValues(horizontal = dimensions.paddingSmall)
                                                ) {
                                                    itemsIndexed(
                                                        items = sessionsInSection,
                                                        key = { _, session -> session.id }
                                                    ) { _, session ->
                                                        Box(modifier = Modifier.width(360.dp)) {
                                                            val cardIdToShow = if (session.mode == SessionMode.MATCHING && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(session.currentCardIndex)
                                                            val card = deck.cards.find { it.id == cardIdToShow }

                                                            SessionTile(
                                                                session = session,
                                                                card = card,
                                                                onResume = {
                                                                    val route = when (session.mode) {
                                                                        SessionMode.FLASHCARD -> "flashcardStudy"
                                                                        SessionMode.FREEFORM -> "freeformStudy"
                                                                        SessionMode.LIST -> "flashcardQuizStudy"
                                                                        SessionMode.MULTIPLE_CHOICE -> "mcStudy"
                                                                        SessionMode.MATCHING -> "matchingStudy"
                                                                        SessionMode.TYPING -> "typingStudy"
                                                                        SessionMode.QUIZ -> "quizStudy"
                                                                        SessionMode.AUDIO -> "audioStudy"
                                                                        SessionMode.MEMORY -> "memoryStudy"
                                                                        SessionMode.HANGMAN -> "hangmanStudy"
                                                                        SessionMode.ANAGRAM -> "anagramStudy"
                                                                        SessionMode.CROSSWORD -> "crosswordStudy"
                                                                        SessionMode.WORD_SEARCH -> "wordSearchStudy"
                                                                        else -> "quizStudy"
                                                                    }
                                                                    // THE FIX: Set pending state to wait for ViewModel load
                                                                    pendingSessionId = session.id
                                                                    pendingNavigationRoute = route
                                                                    viewModel.resumeStudySession(session)
                                                                },
                                                                onCopy = { viewModel.copySession(session) },
                                                                onRestart = { showRestartDialog = session },
                                                                onDelete = { showDeleteDialog = session }
                                                            )
                                                        }
                                                    }
                                                }

                                                // 3. Scroll Indicator
                                                if (sessionsInSection.size > 1) {
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
                                                        sessionsInSection.indices.forEach { index ->
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
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- FLOATING ACTION BUTTON CODE REMAINS UNTOUCHED BELOW THIS ---
            if (fabExpanded) {
                Box(modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.3f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { fabExpanded = false }
                )
            }
            AnimatedVisibility(
                visible = activeSessions.isNotEmpty(),
                enter = fadeIn() + androidx.compose.animation.scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                exit = fadeOut() + androidx.compose.animation.scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(dimensions.paddingMedium)
            ) {
                // This Single Box properly layers the Menu and the Main FAB so they never overlap.
                Box(contentAlignment = Alignment.BottomEnd) {
                    AnimatedVisibility(
                        visible = fabExpanded,
                        enter = fadeIn() + androidx.compose.animation.scaleIn(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                        exit = fadeOut() + androidx.compose.animation.scaleOut(transformOrigin = androidx.compose.ui.graphics.TransformOrigin(1f, 1f)),
                        modifier = Modifier.padding(bottom = 56.dp + dimensions.spacingMedium) // Perfectly clear the main FAB
                    ) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
                            modifier = Modifier
                                .heightIn(max = 350.dp) // Cap height so it handles scrolling correctly
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (activeSessions.isNotEmpty()) {
                                FabMenuItem(
                                    getText(R.string.delete_all),
                                    Icons.Default.Delete,
                                    MaterialTheme.colorScheme.errorContainer,
                                    MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.withShortcut(Key.Delete, "Del") { fabExpanded = false; showDeleteAllSessionsDialog = true }
                                ) {
                                    fabExpanded = false; showDeleteAllSessionsDialog = true
                                }
                            }
                            FabMenuItem(
                                getText(R.string.spaced_repetition_label),
                                Icons.Default.Schedule,
                                MaterialTheme.colorScheme.secondaryContainer,
                                MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.withShortcut(Key.S, "S") { fabExpanded = false; showFsrsModeDialog = true }
                            ) {
                                fabExpanded = false; showFsrsModeDialog = true
                            }
                            FabMenuItem(
                                getText(R.string.preset_game),
                                Icons.Default.SportsEsports,
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.withShortcut(Key.G, "G") { fabExpanded = false; showCreateSessionDialog = StudyPreset.GAMES }
                            ) {
                                fabExpanded = false; showCreateSessionDialog = StudyPreset.GAMES
                            }
                            FabMenuItem(
                                getText(R.string.preset_quiz),
                                Icons.Default.Quiz,
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.withShortcut(Key.Q, "Q") { fabExpanded = false; showCreateSessionDialog = StudyPreset.QUIZ }
                            ) {
                                fabExpanded = false; showCreateSessionDialog = StudyPreset.QUIZ
                            }
                            FabMenuItem(
                                getText(R.string.preset_practice),
                                Icons.Default.MenuBook,
                                MaterialTheme.colorScheme.surfaceContainerHigh,
                                MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.withShortcut(Key.P, "P") { fabExpanded = false; showCreateSessionDialog = StudyPreset.STUDY }
                            ) {
                                fabExpanded = false; showCreateSessionDialog = StudyPreset.STUDY
                            }
                        }
                    }

                    ExtendedFloatingActionButton(
                        onClick = { fabExpanded = !fabExpanded },
                        shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        icon = {
                            val rotation by animateFloatAsState(
                                targetValue = if (fabExpanded) 45f else 0f,
                                label = "fabRotate"
                            )
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = null,
                                modifier = Modifier.graphicsLayer { rotationZ = rotation }
                            )
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
    }
    if (isPane) {
        Column(Modifier.fillMaxSize()) {
            PaneHeader(title = deck.deck.name, onBack = navigateUp)
            Box(Modifier.weight(1f)) { paneContent(PaddingValues(0.dp)) }
        }
    } else {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                Column {
                    CustomTopAppBar(
                        title = { Text(deck.deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            IconButton(onClick = navigateUp) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                if (finalMode == SessionMode.TYPING) internalMode = SessionMode.QUIZ

                val route = when (internalMode) {
                    SessionMode.FLASHCARD -> "flashcardStudy"
                    SessionMode.LIST -> "flashcardQuizStudy"
                    SessionMode.MULTIPLE_CHOICE -> "mcStudy"
                    SessionMode.MATCHING -> "matchingStudy"
                    SessionMode.TYPING -> "typingStudy"
                    SessionMode.QUIZ -> "quizStudy"
                    SessionMode.AUDIO -> "audioStudy"
                    else -> "flashcardStudy"
                }

                viewModel.startStudySession(
                    parentDeck = deck,
                    mode = finalMode,
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

    Dialog(onDismissRequest = onDismiss) {
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
                    IconButton(
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
                                FilledTonalIconButton(onClick = { if (numberOfAnswers > 2) numberOfAnswers-- }, interactionSource = lessInteractionSource, modifier = Modifier.scale(lessScale)) { Icon(Icons.Default.Remove, getText(R.string.less)) }

                                Spacer(Modifier.width(dimensions.spacingSmall))

                                val moreInteractionSource = remember { MutableInteractionSource() }
                                val isMorePressed by moreInteractionSource.collectIsPressedAsState()
                                val moreScale by animateFloatAsState(targetValue = if (isMorePressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "moreSquish")
                                FilledTonalIconButton(onClick = { if (numberOfAnswers < 8) numberOfAnswers++ }, interactionSource = moreInteractionSource, modifier = Modifier.scale(moreScale)) { Icon(Icons.Default.Add, getText(R.string.more)) }
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
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(startSessionScale)
                ) {
                    Text(getText(R.string.start_session))
                }
            }
        }
    }
}

@Composable
fun FabMenuItem(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    androidx.compose.material3.ExtendedFloatingActionButton(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
        containerColor = containerColor,
        contentColor = contentColor,
        icon = { Icon(icon, contentDescription = null) },
        text = { Text(text, style = MaterialTheme.typography.labelLarge) }
    )
}

@Composable
fun FsrsModeSelectionDialog(onDismiss: () -> Unit, onModeSelected: (SessionMode) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(getText(R.string.spaced_repetition_label), style = MaterialTheme.typography.headlineSmall)
                Text(getText(R.string.mode_select), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(dimensions.spacingLarge))

                val modes = listOf(SessionMode.FLASHCARD, SessionMode.MULTIPLE_CHOICE, SessionMode.TYPING, SessionMode.AUDIO)
                modes.forEach { mode ->
                    Button(
                        onClick = { onModeSelected(mode) },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).padding(bottom = dimensions.spacingSmall),
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    ) { Text(mode.asString()) }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
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

    Dialog(onDismissRequest = onDismiss) {
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
                    modifier = Modifier.scale(selectAllScale)
                ) { Text(getText(R.string.select_all)) }

                val deselectAllInteractionSource = remember { MutableInteractionSource() }
                val isDeselectAllPressed by deselectAllInteractionSource.collectIsPressedAsState()
                val deselectAllScale by animateFloatAsState(targetValue = if (isDeselectAllPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "deselectAllSquish")
                TextButton(
                    onClick = { selectedLanguages.clear() },
                    interactionSource = deselectAllInteractionSource,
                    modifier = Modifier.scale(deselectAllScale)
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
                    modifier = Modifier.scale(cancelLangScale)
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
                    enabled = selectedLanguages.isNotEmpty()
                ) { Text(getText(R.string.download)) }
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
    onDelete: () -> Unit
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
        var completedCrosswordCount = 0
        Column(
            modifier = Modifier.padding(dimensions.paddingMedium)
        ) {
            // --- TOP HEADER: Progress Text & Actions ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                completedCrosswordCount = remember(session.crosswordWords, session.crosswordUserInputs) {
                    if (session.mode == SessionMode.CROSSWORD) {
                        session.crosswordWords.count { word -> word.word.indices.all { i -> val x = if (word.isAcross) word.startX + i else word.startX; val y = if (word.isAcross) word.startY else word.startY + i; session.crosswordUserInputs["$x,$y"] == word.word[i].toString() } }
                    } else 0
                }

                val progressText = when (session.mode) {
                    SessionMode.MEMORY -> stringResource(R.string.pairs_progress_format, session.matchedPairs.size, session.totalCards)
                    SessionMode.MATCHING -> stringResource(R.string.matched_progress_format, session.matchedPairs.size, session.totalCards)
                    SessionMode.CROSSWORD -> stringResource(R.string.words_progress_format, completedCrosswordCount, session.crosswordWords.size)
                    SessionMode.WORD_SEARCH -> stringResource(R.string.words_progress_format, session.wordSearchFoundWordIds.size, session.wordSearchWords.size)
                    else -> stringResource(R.string.progress_format, session.currentCardIndex, session.totalCards)
                }

                Text(text = progressText, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))

                // Actions Row
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = { showInfoDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Info, contentDescription = "Session Info", tint = MaterialTheme.colorScheme.secondary)
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
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
            val progressValue = when (session.mode) {
                SessionMode.MEMORY -> if (session.totalCards > 0) session.matchedPairs.size.toFloat() / session.totalCards else 0f
                SessionMode.MATCHING -> if (session.totalCards > 0) session.matchedPairs.size.toFloat() / session.totalCards else 0f
                SessionMode.CROSSWORD -> if (session.crosswordWords.isNotEmpty()) completedCrosswordCount.toFloat() / session.crosswordWords.size else 0f
                SessionMode.WORD_SEARCH -> if (session.wordSearchWords.isNotEmpty()) session.wordSearchFoundWordIds.size.toFloat() / session.wordSearchWords.size else 0f
                else -> if (session.totalCards > 0) session.currentCardIndex.toFloat() / session.totalCards else 0f
            }

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
                                SessionMode.QUIZ, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN -> if (session.quizPromptSide == CardSide.BACK) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                                else -> if (session.isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                            }
                            Box(modifier = Modifier.fillMaxSize().background(cardColor).padding(8.dp), contentAlignment = Alignment.Center) {
                                if (card != null) {
                                    val textToShow = when (session.mode) {
                                        SessionMode.QUIZ, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN, SessionMode.CROSSWORD -> if (session.quizPromptSide == CardSide.BACK) card.back else card.front
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
                            } else if (session.mode == SessionMode.QUIZ || session.mode == SessionMode.TYPING || session.mode == SessionMode.ANAGRAM || session.mode == SessionMode.CROSSWORD) {
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

    Dialog(onDismissRequest = onDismiss) {
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
                val selectionText = buildString {
                    append(session.selectionMode.name.lowercase().replaceFirstChar { it.titlecase(LocalLocale.current.platformLocale) })

                    // Append specific data based on the mode chosen!
                    when (session.selectionMode) {
                        SelectionMode.DIFFICULTY -> if (session.difficulties.isNotEmpty()) append(" (${session.difficulties.joinToString()})")
                        SelectionMode.TAGS -> if (session.selectedTags.isNotEmpty()) append(" (${session.selectedTags.joinToString()})")
                        SelectionMode.ALPHABET -> append(" (${session.alphabetStart} to ${session.alphabetEnd})")
                        SelectionMode.CARD_ORDER -> append(" (#${session.cardOrderStart} to #${session.cardOrderEnd})")
                        SelectionMode.REVIEW_DATE, SelectionMode.INCORRECT_DATE -> append(" (${session.filterType.name.lowercase()} past ${session.timeValue} ${session.timeUnit.name.lowercase()})")
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
                val orderStr = session.cardOrder.name.lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase(LocalLocale.current.platformLocale) else it.toString() }
                    .replace("_", " ")

                ListItem(
                    headlineContent = { Text("Sort & Priority", color = MaterialTheme.colorScheme.primary) },
                    supportingContent = {
                        val priorityStr = if (session.schedulingMode == SchedulingMode.FSRS) "FSRS" else if (session.isWeighted) "Weighted" else "Standard"
                        Text("$orderStr (${session.sortDirection.name}) • $priorityStr", style = MaterialTheme.typography.bodyLarge)
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
                        .scale(dismissScale)
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
        state.deckWithCards?.deck?.id?.let { deckId ->
            navController.navigate("studyModeSelection/$deckId") {
                popUpTo("studyModeSelection/$deckId") { inclusive = true }
            }
        } ?: navController.navigate("deckList") { popUpTo(0) }
    }

    BackHandler(onBack = navigateUp)

    Scaffold(
        topBar = {
            Column {
                CustomTopAppBar(
                    title = { Text(state.studyMode.asString(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = navigateUp) {
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
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // M3 Expressive Pill shape
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
                    shape = CircleShape
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
                    shape = CircleShape
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
                    shape = CircleShape
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
                    shape = CircleShape
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

    Dialog(onDismissRequest = onDismiss) {
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
                    IconButton(
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
                        IconButton(onClick = {
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
                        IconButton(onClick = {
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
                    enabled = front.isNotBlank() && back.isNotBlank()
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