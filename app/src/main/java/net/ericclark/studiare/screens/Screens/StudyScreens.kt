package net.ericclark.studiare.screens.Screens
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
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
import kotlinx.coroutines.flow.first
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
import net.ericclark.studiare.screens.Dialogs.createStudySessionRoute
import net.ericclark.studiare.screens.Dialogs.studyRouteFor
import net.ericclark.studiare.screens.UI_Components.CategoryPickerContent
import net.ericclark.studiare.screens.UI_Components.categoryIcon
import net.ericclark.studiare.screens.UI_Components.PresetCarousel
import net.ericclark.studiare.screens.UI_Components.SessionSection
import net.ericclark.studiare.screens.UI_Components.SessionSortDialog
import net.ericclark.studiare.screens.UI_Components.modeOrderToStoredString
import net.ericclark.studiare.screens.UI_Components.parseModeOrder
import net.ericclark.studiare.screens.UI_Components.SessionTileCarousel
import net.ericclark.studiare.screens.UI_Components.SessionTileGrid
import net.ericclark.studiare.screens.UI_Components.StudyHubSortButton
import net.ericclark.studiare.screens.UI_Components.sortedCategories
import net.ericclark.studiare.screens.UI_Components.sortedSections
import net.ericclark.studiare.screens.UI_Components.sortedTiles

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
    // Create Study Session is its own screen now (it creates something new and navigates to it,
    // unlike every other dialog on this screen), so every entry point below navigates there
    // instead of showing dialog state.
    val navigateToCreateSession: (StudyCategory) -> Unit = { category ->
        navController.navigate(createStudySessionRoute(deck.deck.id, category))
    }
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
                "learn" -> navigateToCreateSession(StudyCategory.LEARN)
                "study" -> navigateToCreateSession(StudyCategory.PRACTICE)
                "quiz" -> navigateToCreateSession(StudyCategory.QUIZ)
                "game" -> navigateToCreateSession(StudyCategory.GAMES)
                "fsrs" -> navigateToCreateSession(StudyCategory.GUIDED)
            }
            hasAutoOpened = true
        }
    }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val sections = listOf(
        // Flashcard is also offered as a Learn mode (modesForCategory), but an ungraded Flashcard
        // ActiveSession has no stored field saying which screen it was started from (see
        // StudyState.currentCategory's comment in data/Values.kt) - so this necessarily shows the
        // same sessions as flashcards_practice below. Harmless duplication, same tradeoff already
        // accepted there.
        SessionSection("flashcards_learn", stringResource(R.string.section_flashcards_learn), StudyCategory.LEARN) { it.mode == SessionMode.FLASHCARD && !it.isGraded },
        SessionSection("flashcards_practice", stringResource(R.string.section_flashcards_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.FLASHCARD && !it.isGraded },
        SessionSection("flashcards_quiz", stringResource(R.string.section_flashcards_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.FLASHCARD && it.isGraded },
        SessionSection("freeform", stringResource(R.string.section_freeform), StudyCategory.LEARN) { it.mode == SessionMode.FREEFORM },
        SessionSection("picking_practice", stringResource(R.string.section_picking_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.LIST && !it.isGraded },
        SessionSection("picking_quiz", stringResource(R.string.section_picking_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.LIST && it.isGraded },
        SessionSection("mc_practice", stringResource(R.string.section_mc_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.MULTIPLE_CHOICE && !it.isGraded },
        SessionSection("mc_quiz", stringResource(R.string.section_mc_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.MULTIPLE_CHOICE && it.isGraded },
        SessionSection("matching_practice", stringResource(R.string.section_matching_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.MATCHING && !it.isGraded },
        SessionSection("matching_quiz", stringResource(R.string.section_matching_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.MATCHING && it.isGraded },
        SessionSection("typing", stringResource(R.string.section_typing), StudyCategory.LEARN) { it.mode == SessionMode.TYPING },
        SessionSection("typing_practice", stringResource(R.string.section_typing_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.TYPING_SCORED && it.showCorrectLetters },
        SessionSection("typing_quiz", stringResource(R.string.section_typing_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.TYPING_SCORED && !it.showCorrectLetters },
        SessionSection("audio", stringResource(R.string.section_audio), StudyCategory.LEARN) { it.mode == SessionMode.AUDIO },
        SessionSection("listening_practice", stringResource(R.string.section_listening_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.LISTEN_TYPE && !it.isGraded },
        SessionSection("listening_quiz", stringResource(R.string.section_listening_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.LISTEN_TYPE && it.isGraded },
        SessionSection("speaking_practice", stringResource(R.string.section_speaking_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.SEE_SPEAK && !it.isGraded },
        SessionSection("speaking_quiz", stringResource(R.string.section_speaking_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.SEE_SPEAK && it.isGraded },
        SessionSection("hear_match_practice", stringResource(R.string.section_hear_match_practice), StudyCategory.PRACTICE) { it.mode == SessionMode.HEAR_MATCH && !it.isGraded },
        SessionSection("hear_match_quiz", stringResource(R.string.section_hear_match_quiz), StudyCategory.QUIZ) { it.mode == SessionMode.HEAR_MATCH && it.isGraded },
        SessionSection("anagram", stringResource(R.string.section_anagram), StudyCategory.GAMES) { it.mode == SessionMode.ANAGRAM },
        SessionSection("hangman", stringResource(R.string.section_hangman), StudyCategory.GAMES) { it.mode == SessionMode.HANGMAN },
        SessionSection("memory", stringResource(R.string.section_memory), StudyCategory.GAMES) { it.mode == SessionMode.MEMORY },
        SessionSection("crossword", stringResource(R.string.section_crossword), StudyCategory.GAMES) { it.mode == SessionMode.CROSSWORD },
        SessionSection("word_search", stringResource(R.string.section_word_search), StudyCategory.GAMES) { it.mode == SessionMode.WORD_SEARCH }
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
    val useCustomCategoryOrder by viewModel.useCustomCategoryOrder.collectAsState()
    val customCategoryOrder by viewModel.customCategoryOrder.collectAsState()
    val presetPosition by viewModel.presetPosition.collectAsState()
    val useCustomModeOrder by viewModel.useCustomModeOrder.collectAsState()
    val customModeOrderRaw by viewModel.customModeOrderRaw.collectAsState()
    val customModeOrder = remember(customModeOrderRaw, sections) { parseModeOrder(customModeOrderRaw, sections) }

    // Dialog States
    var showRestartDialog by remember { mutableStateOf<ActiveSession?>(null) }
    var showDeleteDialog by remember { mutableStateOf<ActiveSession?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showUnpinPresetDialog by remember { mutableStateOf<SessionPreset?>(null) }

    var expandedStates by remember { mutableStateOf(mapOf<String, Boolean>()) }
    var showBulkDeleteDialog by remember { mutableStateOf<List<ActiveSession>?>(null) }
    var showBulkRestartDialog by remember { mutableStateOf<List<ActiveSession>?>(null) }

    // Multi-select (see the roadmap plan file's "App-wide multi-select" section). Mixed-type
    // (session + preset) selection is allowed, same as Pinned — the action bar just narrows to
    // whatever's common (only "unpin" — a preset's own "unpin" already deletes it outright, so it
    // doubles as that type's only bulk action).
    val selection = net.ericclark.studiare.screens.UI_Components.rememberSelectionController()
    BackHandler(enabled = selection.inSelectionMode) { selection.exit() }

    val context = LocalContext.current

    val toastMessage = viewModel.toastMessage
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    // Confirmation Dialogs
    showRestartDialog?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.restart_session_title),
            text = getText(R.string.restart_session_desc),
            onConfirm = {
                val reset = viewModel.restartSession(session)
                showRestartDialog = null
                pendingSessionId = reset.id
                pendingNavigationRoute = studyRouteFor(reset.mode)
                viewModel.resumeStudySession(reset)
            },
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

    showUnpinPresetDialog?.let { preset ->
        ConfirmationDialog(
            title = getText(R.string.unpin_preset_title),
            text = getText(R.string.unpin_preset_desc),
            onConfirm = { viewModel.unpinPreset(preset); showUnpinPresetDialog = null },
            onDismiss = { showUnpinPresetDialog = null }
        )
    }

    showBulkRestartDialog?.let { sessions ->
        ConfirmationDialog(
            title = getText(R.string.restart_session_title),
            text = getText(R.string.restart_session_desc),
            onConfirm = {
                sessions.forEach { viewModel.restartSession(it) }
                showBulkRestartDialog = null
                selection.exit()
            },
            onDismiss = { showBulkRestartDialog = null }
        )
    }

    showBulkDeleteDialog?.let { sessions ->
        ConfirmationDialog(
            title = getText(R.string.delete_session_title),
            text = stringResource(R.string.delete_sessions_confirm_format, sessions.size),
            onConfirm = {
                sessions.forEach { viewModel.deleteSession(it) }
                showBulkDeleteDialog = null
                selection.exit()
            },
            onDismiss = { showBulkDeleteDialog = null }
        )
    }

    val groupedSessions by produceState(initialValue = emptyMap<String, List<ActiveSession>>(), activeSessions, sessionTileSortMode, sessionTileSortDirection) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            val displayed = activeSessions.filter { it.schedulingMode != SchedulingMode.FSRS }
            sections.associate { section ->
                section.key to sortedTiles(displayed.filter(section.filter), sessionTileSortMode, sessionTileSortDirection)
            }
        }
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
            initialUseCustomCategoryOrder = useCustomCategoryOrder,
            initialCustomCategoryOrder = customCategoryOrder,
            initialPresetPosition = presetPosition,
            initialUseCustomModeOrder = useCustomModeOrder,
            initialCustomModeOrder = customModeOrder,
            sections = sections,
            onSave = { newGroupByCategory, newGroupByMode, newCategorySortMode, newCategorySortDirection, newModeSortMode, newModeSortDirection, newTileSortMode, newTileSortDirection, newUseCustomCategoryOrder, newCustomCategoryOrder, newPresetPosition, newUseCustomModeOrder, newCustomModeOrder ->
                viewModel.setGroupByCategory(newGroupByCategory)
                viewModel.setGroupByMode(newGroupByMode)
                viewModel.setCategorySortMode(newCategorySortMode)
                viewModel.setCategorySortDirection(newCategorySortDirection)
                viewModel.setModeSortMode(newModeSortMode)
                viewModel.setModeSortDirection(newModeSortDirection)
                viewModel.setSessionTileSortMode(newTileSortMode)
                viewModel.setSessionTileSortDirection(newTileSortDirection)
                viewModel.setUseCustomCategoryOrder(newUseCustomCategoryOrder)
                viewModel.setCustomCategoryOrder(newCustomCategoryOrder)
                viewModel.setPresetPosition(newPresetPosition)
                viewModel.setUseCustomModeOrder(newUseCustomModeOrder)
                viewModel.setCustomModeOrder(modeOrderToStoredString(newCustomModeOrder))
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    val isDataLoaded by viewModel.isInitialDataLoaded.collectAsState()

    // isDataLoaded/the active-sessions flow below are both long-lived ViewModel state that's
    // already populated by the time a user navigates here a second time, so without this gate
    // the STATE 2 branch's session carousel/grid composes immediately on entry — its real cost
    // (24 possible sections, tile grid/carousel layout) is enough to outlast NavHost's own
    // enterTransition, producing a zero-frame cut on arrival instead of a visible slide. Same
    // root cause/fix as SimpleEditorScreen's grid and DecksScreen's own deck grid.
    //
    // DEBUG-only — see SimpleEditorScreen.kt's matching mainContentReady comment for why: a
    // release build's AOT-compiled hot paths (baseline profile) were confirmed on-device not to
    // need this safety net, while debug is always JIT-interpreted regardless. (Unlike
    // DecksScreen.kt's transitionSettled, this one has no other, correctness-only use elsewhere
    // in this file — safe to bypass directly, no separate derived value needed.)
    val navAnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    var transitionSettled by remember { mutableStateOf(!BuildConfig.DEBUG || navAnimatedVisibilityScope == null) }
    LaunchedEffect(navAnimatedVisibilityScope) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        val transition = navAnimatedVisibilityScope?.transition ?: return@LaunchedEffect
        snapshotFlow { transition.currentState }.first { it == EnterExitState.Visible }
        transitionSettled = true
    }

    val allDecksState by viewModel.allDecks.observeAsState(emptyList())
    // `deck` is a set (child deck) when it has a parentDeckId — in that case its own `name` is
    // the set's name, and the real deck name has to be looked up from its parent.
    val sessionTileSetName = if (deck.deck.parentDeckId != null) deck.deck.name else null
    val sessionTileDeckName = if (deck.deck.parentDeckId != null) {
        allDecksState.find { it.deck.id == deck.deck.parentDeckId }?.deck?.name ?: ""
    } else {
        deck.deck.name
    }
    val presets by viewModel.presets.collectAsState()
    val deckPresets = remember(presets, deck.deck.id) { presets.filter { it.deckId == deck.deck.id } }
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
                        StudyHubSortButton(onClick = { showSortDialog = true })
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
                                navigateToCreateSession(StudyCategory.LEARN)
                                return@onPreviewKeyEvent true
                            }
                            startStudyKey -> {
                                navigateToCreateSession(StudyCategory.PRACTICE)
                                return@onPreviewKeyEvent true
                            }
                            startQuizKey -> {
                                navigateToCreateSession(StudyCategory.QUIZ)
                                return@onPreviewKeyEvent true
                            }
                            startGameKey -> {
                                navigateToCreateSession(StudyCategory.GAMES)
                                return@onPreviewKeyEvent true
                            }
                            startSpacedRepKey -> {
                                navigateToCreateSession(StudyCategory.GUIDED)
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
                    !transitionSettled || !isDataLoaded || !sessionsLoaded -> 0 // STATE 0: Loading
                    activeSessions.isEmpty() -> 1                  // STATE 1: Empty
                    else -> 2                                         // STATE 2: Populated
                },
                transitionSpec = {
                    // Pure fade, no expand/shrink — this was the actual source of "sessions slide
                    // in from the top": switching from STATE 0 (Loading) to STATE 2 (Populated)
                    // played an 800ms expandVertically/shrinkVertically here, which reads as a
                    // vertical slide/reveal, not Android's fade-through standard (see
                    // MainActivity.kt's NavHost comment). Matches that same pure-fade timing.
                    fadeIn(animationSpec = androidx.compose.animation.core.tween(300)).togetherWith(
                        fadeOut(animationSpec = androidx.compose.animation.core.tween(200))
                    )
                },
                label = "sessionsScreenTransition"
            ) { targetState ->
                when (targetState) {
                    0 -> {
                        // STATE 0: Loading. isLoading is read live here (not frozen per-branch), so the
                        // spinner hides immediately once data arrives even during AnimatedContent's own
                        // exit transition, instead of lingering for its length.
                        DelayedLoadingIndicator(isLoading = !transitionSettled || !isDataLoaded || !sessionsLoaded)
                    }
                    1 -> {
                        // STATE 1: Empty State — the preset carousel still shows here (decision D:
                        // presets are most useful exactly when there's nothing in progress yet),
                        // composing nothing extra when this deck has none (or "Preset position" is
                        // set to Hide — same sort-dialog setting the populated list below honors).
                        @Composable
                        fun EmptyStatePresets() {
                            if (deckPresets.isNotEmpty() && presetPosition != PresetPosition.HIDE) {
                                PresetSectionHeader()
                                PresetCarousel(
                                    presets = deckPresets,
                                    deckName = sessionTileDeckName,
                                    setName = sessionTileSetName,
                                    decks = allDecksState,
                                    onCreateSession = { preset -> viewModel.startSessionFromPreset(preset, deck) { navController.navigate(studyRouteFor(preset.sessionMode)) } },
                                    onEditSettings = { preset -> navController.navigate(createStudySessionRoute(deck.deck.id, preset.displayCategory(), preset.id)) },
                                    onUnpin = { preset -> showUnpinPresetDialog = preset },
                                    onRename = { preset, newName -> viewModel.renamePreset(preset, newName) },
                                    onChangeDeckStartSession = { preset, newDeckId ->
                                        allDecksState.find { it.deck.id == newDeckId }?.let { newDeck ->
                                            viewModel.startSessionFromPreset(preset, newDeck) { navController.navigate(studyRouteFor(preset.sessionMode)) }
                                        }
                                    },
                                    onChangeDeckSave = { preset, newDeckId -> viewModel.updatePresetDeck(preset, newDeckId) },
                                    onChangeDeckPinAsNew = { preset, newDeckId, name -> viewModel.pinPresetWithNewDeck(preset, newDeckId, name) },
                                    modifier = Modifier.padding(dimensions.paddingMedium),
                                    selection = selection
                                )
                            }
                        }

                        Column(modifier = Modifier.fillMaxSize()) {
                            if (presetPosition != PresetPosition.LAST) EmptyStatePresets()
                            CategoryPickerContent(
                                title = getText(R.string.no_active_sessions),
                                subtitle = getText(R.string.no_active_sessions_description),
                                onCategorySelected = { category -> navigateToCreateSession(category) },
                                onGuidedSelected = { navigateToCreateSession(StudyCategory.GUIDED) },
                                modifier = Modifier.weight(1f)
                            )
                            if (presetPosition == PresetPosition.LAST) EmptyStatePresets()
                        }
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
                        val onPinSession: (ActiveSession, String) -> Unit = { session, name -> viewModel.pinSession(session, name) }
                        val onUnpinSession: (ActiveSession) -> Unit = { session -> viewModel.unpinSession(session) }
                        val onRenameSession: (ActiveSession, String?) -> Unit = { session, name -> viewModel.renameSession(session, name) }

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
                                        // Was headlineMedium/bold — now the mode header's old text
                                        // style (the two swapped: mode headers took on the Pinned
                                        // tab's SectionHeader look, so category headers took on
                                        // mode headers' previous one), with the same chevron/
                                        // collapse/count behavior this header already had.
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = categoryIcon(category),
                                                contentDescription = null,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = category.asString(),
                                                style = MaterialTheme.typography.headlineSmall
                                            )
                                        }
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
                                            expandedStates = expandedStates + (section.key to !isExpanded)
                                        }
                                        .padding(vertical = dimensions.paddingSmall, horizontal = dimensions.paddingSmall),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column {
                                        // Section titles no longer carry a "- Practice"/"- Quiz" suffix
                                        // (the category header above already says "Practice"/"Quiz").
                                        val modeTitle = section.title
                                        // Same text style as the Pinned tab's own section headers
                                        // (SectionHeader, PinnedScreen.kt) — chevron/collapse/count
                                        // behavior unchanged, just restyled per the user's request.
                                        Text(
                                            text = modeTitle,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
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
                                        SessionTileCarousel(sessions, deck, sessionTileDeckName, sessionTileSetName, showModeLabel, showCategoryIcon, onResumeSession, onCopySession, onRestartSession, onDeleteSession, onPinSession, onUnpinSession, onRenameSession, selection)
                                    }
                                } else {
                                    SessionTileCarousel(sessions, deck, sessionTileDeckName, sessionTileSetName, showModeLabel, showCategoryIcon, onResumeSession, onCopySession, onRestartSession, onDeleteSession, onPinSession, onUnpinSession, onRenameSession, selection)
                                }
                            }
                        }

                        fun LazyListScope.gridItem(sessions: List<ActiveSession>, showModeLabel: Boolean, showCategoryIcon: Boolean) {
                            item {
                                SessionTileGrid(sessions, deck, sessionTileDeckName, sessionTileSetName, showModeLabel, showCategoryIcon, onResumeSession, onCopySession, onRestartSession, onDeleteSession, onPinSession, onUnpinSession, onRenameSession, selection)
                            }
                        }

                        // Non-empty mode-sections per category, in the user's chosen mode order.
                        // "Use custom order for modes" overrides modeSortMode/modeSortDirection here,
                        // but not groupByMode - same override relationship as the category version above.
                        fun sectionsFor(category: StudyCategory): List<SessionSection> {
                            val nonEmpty = sections.filter { it.category == category }.filter { (groupedSessions[it.key] ?: emptyList()).isNotEmpty() }
                            return if (useCustomModeOrder) {
                                val order = customModeOrder[category].orEmpty()
                                nonEmpty.sortedBy { section -> order.indexOf(section.key).let { if (it < 0) Int.MAX_VALUE else it } }
                            } else {
                                sortedSections(nonEmpty, modeSortMode, modeSortDirection, groupedSessions)
                            }
                        }

                        val sessionsByCategory = categoryOrder.associateWith { category -> sectionsFor(category).flatMap { groupedSessions[it.key] ?: emptyList() } }
                        // "Use custom order for categories" overrides categorySortMode/categorySortDirection
                        // here, but not groupByCategory - whether categories get their own header at all
                        // is decided independently, further down.
                        val categoriesToShow = (
                            if (useCustomCategoryOrder) customCategoryOrder.filter { it in categoryOrder }
                            else sortedCategories(categoryOrder, categorySortMode, categorySortDirection, sessionsByCategory, context)
                        ).filter { (sessionsByCategory[it] ?: emptyList()).isNotEmpty() }

                        // "Preset position" (sort dialog) - Hide drops the carousel entirely; First/Last
                        // just move this same pair of items to the top or bottom of the LazyColumn.
                        fun LazyListScope.presetItems() {
                            if (deckPresets.isNotEmpty() && presetPosition != PresetPosition.HIDE) {
                                item { PresetSectionHeader() }
                                item {
                                    PresetCarousel(
                                        presets = deckPresets,
                                        deckName = sessionTileDeckName,
                                        setName = sessionTileSetName,
                                        decks = allDecksState,
                                        // setStudyState is already called (synchronously, on Main)
                                        // before onSessionCreated fires, unlike onResumeSession's
                                        // pendingSessionId/LaunchedEffect dance a few lines below
                                        // (that one's for loading an *existing* session back in) —
                                        // same direct-navigate pattern CreateStudySessionScreen's
                                        // own "Start Session" already uses.
                                        onCreateSession = { preset -> viewModel.startSessionFromPreset(preset, deck) { navController.navigate(studyRouteFor(preset.sessionMode)) } },
                                        onEditSettings = { preset -> navController.navigate(createStudySessionRoute(deck.deck.id, preset.displayCategory(), preset.id)) },
                                        onUnpin = { preset -> showUnpinPresetDialog = preset },
                                        onRename = { preset, newName -> viewModel.renamePreset(preset, newName) },
                                        onChangeDeckStartSession = { preset, newDeckId ->
                                            allDecksState.find { it.deck.id == newDeckId }?.let { newDeck ->
                                                viewModel.startSessionFromPreset(preset, newDeck) { navController.navigate(studyRouteFor(preset.sessionMode)) }
                                            }
                                        },
                                        onChangeDeckSave = { preset, newDeckId -> viewModel.updatePresetDeck(preset, newDeckId) },
                                        onChangeDeckPinAsNew = { preset, newDeckId, name -> viewModel.pinPresetWithNewDeck(preset, newDeckId, name) },
                                        selection = selection
                                    )
                                }
                            }
                        }

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
                            if (presetPosition != PresetPosition.LAST) presetItems()
                            if (groupByCategory) {
                                categoriesToShow.forEach { category ->
                                    val orderedSections = sectionsFor(category)
                                    val totalSessionsInCategory = orderedSections.sumOf { (groupedSessions[it.key] ?: emptyList()).size }
                                    val isCategoryExpanded = expandedStates["category:${category.name}"] ?: true
                                    categoryHeaderItem(category, isCategoryExpanded, totalSessionsInCategory)

                                    if (isCategoryExpanded) {
                                        if (groupByMode) {
                                            // Case 1: nested category > mode > tiles (today's behavior).
                                            orderedSections.forEach { section ->
                                                val sessionsInSection = groupedSessions[section.key] ?: emptyList()
                                                val isExpanded = expandedStates[section.key] ?: true
                                                modeHeaderItem(section, isExpanded, sessionsInSection.size)
                                                carouselItem(sessionsInSection, showModeLabel = false, showCategoryIcon = false, animatedVisible = isExpanded)
                                            }
                                        } else {
                                            // Case 2: category header stays, modes flattened into one
                                            // carousel (mode-sort groups same-mode tiles together).
                                            val merged = orderedSections.flatMap { groupedSessions[it.key] ?: emptyList() }
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
                                        val sessionsInSection = groupedSessions[section.key] ?: emptyList()
                                        val isExpanded = expandedStates[section.key] ?: true
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
                                val merged = categoriesToShow.flatMap { category -> sectionsFor(category).flatMap { groupedSessions[it.key] ?: emptyList() } }
                                gridItem(merged, showModeLabel = true, showCategoryIcon = true)
                            }
                            if (presetPosition == PresetPosition.LAST) presetItems()
                        }
                }
            }
            } // closes AnimatedContent's trailing lambda

            if (selection.inSelectionMode) {
                val selectedSessions = remember(selection.selected, activeSessions) {
                    activeSessions.filter { "session-${it.id}" in selection.selected }
                }
                val selectedPresets = remember(selection.selected, deckPresets) {
                    deckPresets.filter { "preset-${it.id}" in selection.selected }
                }
                val allVisibleKeys = remember(activeSessions, deckPresets) {
                    activeSessions.map { "session-${it.id}" }.toSet() + deckPresets.map { "preset-${it.id}" }.toSet()
                }
                val selectAll = net.ericclark.studiare.screens.UI_Components.SelectionAction(
                    id = "selectAll",
                    icon = Icons.Default.SelectAll,
                    label = getText(context, R.string.select_all),
                    onClick = { selection.toggleAllWithin(allVisibleKeys) }
                )
                val cardActions = remember(selection.selected, activeSessions, deckPresets) {
                    if (selection.selected.isEmpty()) {
                        listOf(selectAll)
                    } else {
                        // Sessions support the full set; a preset only supports "unpin" (which
                        // deletes it outright) — a mixed selection narrows to just that, the one
                        // concept both types share.
                        val onlyPresetsSelected = selectedSessions.isEmpty() && selectedPresets.isNotEmpty()
                        val sessionOnlyActions = if (onlyPresetsSelected) emptyList() else listOf(
                            net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                id = "copy",
                                icon = Icons.Default.ContentCopy,
                                label = getText(context, R.string.copy),
                                onClick = { selectedSessions.forEach { viewModel.copySession(it) }; selection.exit() }
                            ),
                            net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                id = "restart",
                                icon = Icons.Default.RestartAlt,
                                label = getText(context, R.string.restart),
                                onClick = { showBulkRestartDialog = selectedSessions }
                            ),
                            net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                id = "unpin",
                                icon = Icons.Outlined.PushPin,
                                label = getText(context, R.string.unpin),
                                onClick = {
                                    selectedSessions.forEach { viewModel.unpinSession(it) }
                                    selectedPresets.forEach { viewModel.unpinPreset(it) }
                                    selection.exit()
                                }
                            ),
                            net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                id = "pin",
                                icon = Icons.Default.PushPin,
                                label = getText(context, R.string.pin),
                                onClick = { selectedSessions.forEach { viewModel.pinSession(it, it.name ?: "") } }
                            ),

                            net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                id = "delete",
                                icon = Icons.Default.Delete,
                                label = getText(context, R.string.delete),
                                isDestructive = true,
                                onClick = { showBulkDeleteDialog = selectedSessions }
                            )
                        )
                        // Pin/unpin kept adjacent (see the roadmap plan's action-ordering
                        // convention, also followed by Decks/Set Manager's own bars) rather than
                        // separated by Delete. A preset-only/mixed selection still narrows to just
                        // "unpin" via the fallback below.
                        if (onlyPresetsSelected) {
                            listOf(
                                net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                    id = "unpin",
                                    icon = Icons.Outlined.PushPin,
                                    label = getText(context, R.string.unpin),
                                    onClick = {
                                        selectedSessions.forEach { viewModel.unpinSession(it) }
                                        selectedPresets.forEach { viewModel.unpinPreset(it) }
                                        selection.exit()
                                    }
                                )
                            ) + selectAll
                        } else {
                            sessionOnlyActions + selectAll
                        }
                    }
                }
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .fillMaxWidth()
                        .padding(dimensions.paddingMedium),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                ) {
                    net.ericclark.studiare.screens.UI_Components.SelectionActionBar(
                        actions = cardActions,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        FloatingActionButton(
                            onClick = { selection.exit() },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Icon(Icons.Default.Close, contentDescription = getText(R.string.cancel))
                        }
                        net.ericclark.studiare.screens.UI_Components.SelectionCountTile(
                            selectedCount = selection.selected.size,
                            totalCount = allVisibleKeys.size,
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-32).dp)
                        )
                    }
                }
            } else {
            // --- FLOATING ACTION BUTTON: opens the full-page category picker directly ---
            // Axis-based expand/collapse from the bottom (where this FAB is anchored), not
            // scale — matches M3's Android enter/exit guidance. Exit uses the fast spec so the
            // FAB clears quickly instead of cross-fading at full duration.
            val fabMotionScheme = MaterialTheme.motionScheme
            AnimatedVisibility(
                visible = activeSessions.isNotEmpty(),
                enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom, animationSpec = fabMotionScheme.defaultSpatialSpec()) +
                    fadeIn(animationSpec = fabMotionScheme.defaultEffectsSpec()),
                exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = fabMotionScheme.fastSpatialSpec()) +
                    fadeOut(animationSpec = fabMotionScheme.fastEffectsSpec()),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(dimensions.paddingMedium)
            ) {
                ExtendedFloatingActionButton(
                    onClick = { navigateToCreateSession(viewModel.lastStudyCategory.value) },
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    icon = {
                        Icon(imageVector = Icons.Default.Add, contentDescription = null)
                    },
                    text = {
                        Text(
                            getText(R.string.create_session_label),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                )
            }
            }
        }
    }

    if (isPane) {
        // Title lives in the shared app bar (via PaneChrome), not in the pane.
        Box(Modifier.fillMaxSize()) { paneContent(PaddingValues(0.dp)) }
    } else {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
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
                                StudyHubSortButton(onClick = { showSortDialog = true })
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

/**
 * The "Presets" label above [PresetCarousel] — same look/size as a mode section's own header
 * (`modeHeaderItem`'s `Text(..., style = MaterialTheme.typography.headlineSmall)`), but static:
 * presets aren't an expand/collapse section like category/mode groups are, so no chevron/count.
 */
@Composable
private fun PresetSectionHeader() {
    val dimensions = LocalStudiareDimensions.current
    Text(
        text = getText(R.string.presets),
        style = MaterialTheme.typography.headlineSmall,
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall, horizontal = dimensions.paddingSmall)
    )
}
