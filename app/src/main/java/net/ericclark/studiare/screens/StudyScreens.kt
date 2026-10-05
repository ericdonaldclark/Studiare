package net.ericclark.studiare

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.graphics.graphicsLayer
import androidx.navigation.NavController
import java.util.*
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.components.getText
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.focus.FocusRequester
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
    var showCreateSessionDialog by rememberSaveable { mutableStateOf<StudyCategory?>(null) }
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
                "study" -> showCreateSessionDialog = StudyCategory.PRACTICE
                "quiz" -> showCreateSessionDialog = StudyCategory.QUIZ
                "game" -> showCreateSessionDialog = StudyCategory.GAMES
                "fsrs" -> showCreateSessionDialog = StudyCategory.GUIDED
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
        SessionSection(stringResource(R.string.section_flashcards_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.FLASHCARD && !it.isGraded },
        SessionSection(stringResource(R.string.section_flashcards_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.FLASHCARD && it.isGraded },
        SessionSection(stringResource(R.string.section_freeform), StudyCategory.LEARN) { it.mode == SessionMode.FREEFORM },
        SessionSection(stringResource(R.string.section_picking_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.LIST && !it.isGraded },
        SessionSection(stringResource(R.string.section_picking_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.LIST && it.isGraded },
        SessionSection(stringResource(R.string.section_mc_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.MULTIPLE_CHOICE && !it.isGraded },
        SessionSection(stringResource(R.string.section_mc_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.MULTIPLE_CHOICE && it.isGraded },
        SessionSection(stringResource(R.string.section_matching_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.MATCHING && !it.isGraded },
        SessionSection(stringResource(R.string.section_matching_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.MATCHING && it.isGraded },
        SessionSection(stringResource(R.string.section_typing), StudyCategory.LEARN) { it.mode == SessionMode.TYPING },
        SessionSection(stringResource(R.string.section_typing_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.TYPING_SCORED && it.showCorrectLetters },
        SessionSection(stringResource(R.string.section_typing_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.TYPING_SCORED && !it.showCorrectLetters },
        SessionSection(stringResource(R.string.section_audio), StudyCategory.LEARN) { it.mode == SessionMode.AUDIO },
        SessionSection(stringResource(R.string.section_listening_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.TYPED_LISTEN && !it.isGraded },
        SessionSection(stringResource(R.string.section_listening_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.TYPED_LISTEN && it.isGraded },
        SessionSection(stringResource(R.string.section_speaking_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.SPOKEN_LISTEN && !it.isGraded },
        SessionSection(stringResource(R.string.section_speaking_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.SPOKEN_LISTEN && it.isGraded },
        SessionSection(stringResource(R.string.section_anagram), StudyCategory.GAMES) { it.mode == SessionMode.ANAGRAM },
        SessionSection(stringResource(R.string.section_hangman), StudyCategory.GAMES) { it.mode == SessionMode.HANGMAN },
        SessionSection(stringResource(R.string.section_memory), StudyCategory.GAMES) { it.mode == SessionMode.MEMORY },
        SessionSection(stringResource(R.string.section_crossword), StudyCategory.GAMES) { it.mode == SessionMode.CROSSWORD },
        SessionSection(stringResource(R.string.section_word_search), StudyCategory.GAMES) { it.mode == SessionMode.WORD_SEARCH }
    )

    // Display order for the new outer category rows in the populated grid view.
    val categoryOrder = listOf(StudyCategory.LEARN, StudyCategory.PRACTICE, StudyCategory.QUIZ, StudyCategory.GAMES)

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

    showCreateSessionDialog?.let { category ->
        CreateStudySessionDialog(
            deck = deck,
            initialCategory = category,
            availableTags = parentDeckTags,
            allTagDefinitions = allTags,
            modeDefaults = viewModel.modeDefaultSettings.collectAsState().value,
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
                                showCreateSessionDialog = StudyCategory.PRACTICE
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
                                showCreateSessionDialog = StudyCategory.GUIDED
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
                            onGuidedSelected = { showCreateSessionDialog = StudyCategory.GUIDED }
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
                    onClick = { showCreateSessionDialog = StudyCategory.LEARN },
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
                            TooltipIconButton(description = getText(R.string.back), onClick = navigateUp) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
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
}
