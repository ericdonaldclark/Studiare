package net.ericclark.studiare

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.unit.sp
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.components.CardTagRow
import net.ericclark.studiare.data.Direction
import net.ericclark.studiare.components.getText
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale

/**
 * A screen that displays all active study sessions for a specific deck,
 * grouped by study mode. It allows users to resume, copy, restart, or delete sessions.
 * @param navController The NavController for navigating to study screens.
 * @param deck The deck for which to display study sessions.
 * @param viewModel The ViewModel providing data and business logic.
 */
@Composable
fun StudyModeSelectionScreen(navController: NavController, deck: DeckWithCards, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    var showCreateSessionDialog by rememberSaveable { mutableStateOf(false) }
    var showFsrsConfigDialog by rememberSaveable { mutableStateOf<SessionMode?>(null) } // holds the selected mode
    val activeSessions by viewModel.activeSessions.collectAsState()

    // --- Data Preparation for Dialog ---
    val allTags by viewModel.tags.collectAsState()
    val parentDeckTags = remember(deck) {
        deck.cards.flatMap { it.tags }.distinct().sorted()
    }

    // Define sections explicitly
    data class SessionSection(val title: String, val filter: (ActiveSession) -> Boolean)

    val sections = listOf(
        SessionSection(stringResource(R.string.section_flashcards)) { it.mode == SessionMode.FLASHCARD },
        SessionSection(stringResource(R.string.section_flashcard_picker)) { it.mode == SessionMode.FLASHCARD_QUIZ },
        SessionSection(stringResource(R.string.section_mc_study)) { it.mode == SessionMode.MULTIPLE_CHOICE && !it.isGraded },
        SessionSection(stringResource(R.string.section_mc_quiz)) { it.mode == SessionMode.MULTIPLE_CHOICE && it.isGraded },
        SessionSection(stringResource(R.string.section_matching_study)) { it.mode == SessionMode.MATCHING && !it.isGraded },
        SessionSection(stringResource(R.string.section_matching_quiz)) { it.mode == SessionMode.MATCHING && it.isGraded },
        SessionSection(stringResource(R.string.section_typing_study)) { it.mode == SessionMode.TYPING },
        SessionSection(stringResource(R.string.section_typing_quiz)) { it.mode == SessionMode.QUIZ },
        // Audio Sections
        SessionSection(stringResource(R.string.section_audio_study)) { it.mode == SessionMode.AUDIO && !it.isGraded },
        SessionSection(stringResource(R.string.section_audio_quiz)) { it.mode == SessionMode.AUDIO && it.isGraded },
        SessionSection(stringResource(R.string.section_anagram)) { it.mode == SessionMode.ANAGRAM },
        SessionSection(stringResource(R.string.section_hangman)) { it.mode == SessionMode.HANGMAN },
        SessionSection(stringResource(R.string.section_memory)) { it.mode == SessionMode.MEMORY },
        SessionSection(stringResource(R.string.section_crossword)) { it.mode == SessionMode.CROSSWORD }
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
            onDismiss = {
                showHdPromptDialog = false
                viewModel.setHdAudioPrompted() // Mark as asked so we don't ask again
                pendingSessionAction?.invoke()
                pendingSessionAction = null
            }
        )
    }

    val languageSizes = remember(viewModel.getUniqueDeckLanguages()) {
        viewModel.getUniqueDeckLanguages().associateWith { lang ->
            viewModel.getFormattedModelSize(lang)
        }
    }

    if (showHdSelectionDialog) {
        HdLanguageSelectionDialog(
            languages = viewModel.getUniqueDeckLanguages(),
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
            }
        )
    }

    if (showCreateSessionDialog) {
        CreateStudySessionDialog(
            deck = deck,
            availableTags = parentDeckTags,
            allTagDefinitions = allTags,
            onDismiss = { showCreateSessionDialog = false },
            onStartSession = { mode, isWeighted, numCards, quizPromptSide, numAnswers, showLetters, limitPool, isGraded, selectAnswer, allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes, maxMemoryTiles, gridDensity, showCorrectWords, config ->
                showCreateSessionDialog = false

                var internalMode = mode
                if (mode == SessionMode.FLASHCARD && selectAnswer) {
                    internalMode = SessionMode.FLASHCARD_QUIZ
                } else if (mode == SessionMode.TYPING && isGraded) {
                    internalMode = SessionMode.QUIZ
                }

                // Logic for NEW sessions
                val route = when (internalMode) {
                    SessionMode.FLASHCARD -> "flashcardStudy"
                    SessionMode.FLASHCARD_QUIZ -> "flashcardQuizStudy"
                    SessionMode.MULTIPLE_CHOICE -> "mcStudy"
                    SessionMode.MATCHING -> "matchingStudy"
                    SessionMode.TYPING -> "typingStudy"
                    SessionMode.QUIZ -> "quizStudy"
                    SessionMode.AUDIO -> "audioStudy"
                    SessionMode.ANAGRAM -> "anagramStudy"
                    SessionMode.HANGMAN -> "hangmanStudy"
                    SessionMode.MEMORY -> "memoryStudy"
                    SessionMode.CROSSWORD -> "crosswordStudy"
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
                        selectAnswer = selectAnswer,
                        allowMultipleGuesses = allowMultipleGuesses,
                        enableStt = enableStt,
                        hideAnswerText = hideAnswerText,
                        fingersAndToes = fingersAndToes,
                        maxMemoryTiles = maxMemoryTiles,
                        gridDensity = gridDensity,
                        config = config // Pass the config object
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

    val displayedSessions = activeSessions.filter { it.schedulingMode != SchedulingMode.FSRS }

    if (showDeleteAllSessionsDialog) {
        ConfirmationDialog(
            title = getText(R.string.delete_all_sessions_title),
            text = getText(R.string.delete_all_sessions_desc),
            onConfirm = { viewModel.deleteAllSessionsForDeck(deck.deck.id); showDeleteAllSessionsDialog = false },
            onDismiss = { showDeleteAllSessionsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text(deck.deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = getText(R.string.back_to_decks)) } }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
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
                // --- 1. NEW: FSRS Start Section ---
                item {
                    Text(
                        text = getText(R.string.start_fsrs_session),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(bottom = dimensions.paddingSmall),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                    ) {
                        val fsrsModes = listOf(
                            SessionMode.FLASHCARD,
                            SessionMode.MULTIPLE_CHOICE,
                            SessionMode.TYPING,
                            SessionMode.AUDIO
                        )
                        fsrsModes.forEach { mode ->
                            ToggleButton(
                                text = mode.asString(),
                                isSelected = false,
                                onClick = { showFsrsConfigDialog = mode }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(top = dimensions.paddingMedium))
                }

                // --- 2. Existing Saved Sessions List ---
                if (displayedSessions.isEmpty()) {
                    item {
                        Text(
                            getText(R.string.no_active_sessions),
                            fontSize = 18.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(dimensions.paddingMedium)
                        )
                    }
                } else {
                    sections.forEach { section ->
                        val sessionsInSection = displayedSessions.filter(section.filter)
                            .sortedByDescending { it.lastAccessed }
                        if (sessionsInSection.isNotEmpty()) {
                            val isExpanded = expandedStates[section.title] ?: true

                            item {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                                        .clickable {
                                            expandedStates =
                                                expandedStates + (section.title to !isExpanded)
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

                                    val iconRotation by androidx.compose.animation.core.animateFloatAsState(
                                        targetValue = if (isExpanded) 180f else 0f,
                                        animationSpec = androidx.compose.animation.core.spring(
                                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
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

                            item {
                                androidx.compose.animation.AnimatedVisibility(visible = isExpanded) {
                                    BoxWithConstraints(
                                        modifier = Modifier.fillMaxWidth()
                                            .padding(bottom = dimensions.paddingMedium)
                                    ) {
                                        // Dynamically calculate how many 350dp items can fit in the row
                                        val columns = maxOf(1, (maxWidth / 350.dp).toInt())
                                        val chunkedSessions = sessionsInSection.chunked(columns)

                                        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
                                            chunkedSessions.forEach { rowSessions ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(
                                                        dimensions.spacingMedium
                                                    )
                                                ) {
                                                    rowSessions.forEach { session ->
                                                        Box(modifier = Modifier.weight(1f)) {
                                                            val cardIdToShow =
                                                                if (session.mode == SessionMode.MATCHING && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(
                                                                    session.currentCardIndex
                                                                )
                                                            val card =
                                                                deck.cards.find { it.id == cardIdToShow }

                                                            SessionTile(
                                                                session = session,
                                                                card = card,
                                                                onResume = {
                                                                    viewModel.resumeStudySession(
                                                                        session
                                                                    )
                                                                    val route =
                                                                        when (session.mode) {
                                                                            SessionMode.FLASHCARD -> "flashcardStudy"
                                                                            SessionMode.FLASHCARD_QUIZ -> "flashcardQuizStudy"
                                                                            SessionMode.MULTIPLE_CHOICE -> "mcStudy"
                                                                            SessionMode.MATCHING -> "matchingStudy"
                                                                            SessionMode.TYPING -> "typingStudy"
                                                                            SessionMode.QUIZ -> "quizStudy"
                                                                            SessionMode.AUDIO -> "audioStudy"
                                                                            SessionMode.MEMORY -> "memoryStudy"
                                                                            SessionMode.HANGMAN -> "hangmanStudy"
                                                                            SessionMode.ANAGRAM -> "anagramStudy"
                                                                            SessionMode.CROSSWORD -> "crosswordStudy"
                                                                            else -> "quizStudy"
                                                                        }
                                                                    navController.navigate(route)
                                                                },
                                                                onCopy = {
                                                                    viewModel.copySession(
                                                                        session
                                                                    )
                                                                },
                                                                onRestart = {
                                                                    showRestartDialog = session
                                                                },
                                                                onDelete = {
                                                                    showDeleteDialog = session
                                                                }
                                                            )
                                                        }
                                                    }
                                                    // Fill empty spaces with invisible spacers to maintain grid alignment
                                                    repeat(columns - rowSessions.size) {
                                                        Spacer(modifier = Modifier.weight(1f))
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
            val addInteractionSource = remember { MutableInteractionSource() }
            val isAddPressed by addInteractionSource.collectIsPressedAsState()
            val addScale by animateFloatAsState(
                targetValue = if (isAddPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "addFabSquish"
            )
            MediumFloatingActionButton(
                onClick = { showCreateSessionDialog = true },
                interactionSource = addInteractionSource,
                modifier = Modifier.align(Alignment.BottomEnd).padding(dimensions.paddingMedium).scale(addScale)
            ) { Icon(Icons.Default.Add, contentDescription = getText(R.string.create_study_session)) }

            if (displayedSessions.isNotEmpty()) {
                val deleteInteractionSource = remember { MutableInteractionSource() }
                val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
                val deleteScale by animateFloatAsState(
                    targetValue = if (isDeletePressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "deleteFabSquish"
                )
                MediumFloatingActionButton(
                    onClick = { showDeleteAllSessionsDialog = true },
                    interactionSource = deleteInteractionSource,
                    modifier = Modifier.align(Alignment.BottomStart).padding(dimensions.paddingMedium).scale(deleteScale),
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) { Icon(Icons.Default.Delete, contentDescription = getText(R.string.delete_all_sessions)) }
            }
        }
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
                if (finalMode == SessionMode.FLASHCARD && selectAnswer) internalMode = SessionMode.FLASHCARD_QUIZ
                if (finalMode == SessionMode.TYPING) internalMode = SessionMode.QUIZ

                val route = when (internalMode) {
                    SessionMode.FLASHCARD -> "flashcardStudy"
                    SessionMode.FLASHCARD_QUIZ -> "flashcardQuizStudy"
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
                    selectAnswer = selectAnswer,
                    allowMultipleGuesses = multiGuess,
                    enableStt = stt,
                    hideAnswerText = hideText,
                    fingersAndToes = fingers,
                    maxMemoryTiles = maxTiles,
                    gridDensity = density,
                    config = config,
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
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
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

                Text(getText(R.string.prompt_side), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    ToggleButton(CardSide.FRONT.asString(), quizPromptSide == CardSide.FRONT, { quizPromptSide = CardSide.FRONT }, Modifier.weight(1f))
                    ToggleButton(CardSide.BACK.asString(), quizPromptSide == CardSide.BACK, { quizPromptSide = CardSide.BACK }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(dimensions.spacingSmall))

                if (mode == SessionMode.MULTIPLE_CHOICE) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(stringResource(R.string.answers_count_format, numberOfAnswers), modifier = Modifier.weight(1f))

                        val lessInteractionSource = remember { MutableInteractionSource() }
                        val isLessPressed by lessInteractionSource.collectIsPressedAsState()
                        val lessScale by animateFloatAsState(targetValue = if (isLessPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "lessSquish")
                        IconButton(onClick = { if (numberOfAnswers > 2) numberOfAnswers-- }, interactionSource = lessInteractionSource, modifier = Modifier.scale(lessScale)) { Icon(Icons.Default.Remove, getText(R.string.less)) }

                        val moreInteractionSource = remember { MutableInteractionSource() }
                        val isMorePressed by moreInteractionSource.collectIsPressedAsState()
                        val moreScale by animateFloatAsState(targetValue = if (isMorePressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "moreSquish")
                        IconButton(onClick = { if (numberOfAnswers < 8) numberOfAnswers++ }, interactionSource = moreInteractionSource, modifier = Modifier.scale(moreScale)) { Icon(Icons.Default.Add, getText(R.string.more)) }
                    }
                }
                if (mode == SessionMode.FLASHCARD) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(getText(R.string.select_answer_picker), modifier = Modifier.weight(1f))
                        Switch(checked = selectAnswer, onCheckedChange = { selectAnswer = it })
                    }
                }
                if (mode == SessionMode.TYPING) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(getText(R.string.show_correct_letters), modifier = Modifier.weight(1f))
                        Switch(checked = showCorrectLetters, onCheckedChange = { showCorrectLetters = it })
                    }
                }

                Spacer(Modifier.height(dimensions.spacingLarge))

                val startSessionInteractionSource = remember { MutableInteractionSource() }
                val isStartSessionPressed by startSessionInteractionSource.collectIsPressedAsState()
                val startSessionScale by animateFloatAsState(
                    targetValue = if (isStartSessionPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "startSessionSquish"
                )
                Button(
                    onClick = {
                        val config = AutoSetConfig(
                            mode = AutoSetCreationMode.ONE, numSets = 1, maxCardsPerSet = 9999,
                            selectionMode = SelectionMode.ANY, selectedTags = emptyList(), selectedDifficulties = emptyList(),
                            excludeKnown = false, sortMode = SortMode.REVIEW_DATE, sortDirection = Direction.ASC, sortSide = CardSide.FRONT,
                            schedulingMode = SchedulingMode.FSRS,
                        )
                        // FIX: Pass limitPool = false so options are generated from the whole deck
                        onStart(config, mode, false, quizPromptSide, numberOfAnswers, showCorrectLetters, false, selectAnswer, allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes, maxMemoryTiles, 2)
                    },
                    interactionSource = startSessionInteractionSource,
                    modifier = Modifier.fillMaxWidth().scale(startSessionScale)
                ) {
                    Text(getText(R.string.start_session))
                }
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
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
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
                    modifier = Modifier.scale(downloadScale),
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
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy 'at' h:mm a", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }

    val yesStr = stringResource(R.string.yes)
    val noStr = stringResource(R.string.no)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tileSquish"
    )

    Card(
        modifier = Modifier.fillMaxWidth().scale(scale),
        interactionSource = interactionSource,
        elevation = CardDefaults.cardElevation(dimensions.cardElevation),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        onClick = onResume
    ) {
        Row(
            modifier = Modifier
                .padding(dimensions.paddingMedium)
                .height(IntrinsicSize.Min)
        ) {
            // Memory Preview Tile
            if (session.mode == SessionMode.MEMORY) {
                Card(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusSmall),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        repeat(2) { r ->
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                repeat(2) { c ->
                                    val color = if ((r + c) % 2 == 0)
                                        MaterialTheme.colorScheme.primaryContainer
                                    else
                                        MaterialTheme.colorScheme.tertiaryContainer

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                            .background(color, RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        }
                    }
                }
            }
            // --- NEW: Crossword Preview Tile ---
            else if (session.mode == SessionMode.CROSSWORD) {
                Card(
                    modifier = Modifier
                        .width(100.dp)
                        .fillMaxHeight(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusSmall),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    // Calculate active cells for the preview
                    val activeCells = remember(session.crosswordWords) {
                        val cells = mutableSetOf<Pair<Int, Int>>()
                        session.crosswordWords.forEach { word ->
                            for (i in word.word.indices) {
                                val x = if (word.isAcross) word.startX + i else word.startX
                                val y = if (word.isAcross) word.startY else word.startY + i
                                cells.add(x to y)
                            }
                        }
                        cells
                    }

                    val cellColor = MaterialTheme.colorScheme.primaryContainer

                    Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                        if (session.crosswordGridWidth > 0 && session.crosswordGridHeight > 0) {
                            val gw = session.crosswordGridWidth.toFloat()
                            val gh = session.crosswordGridHeight.toFloat()

                            // Calculate cell size to fit the grid within the canvas
                            val cellW = size.width / gw
                            val cellH = size.height / gh
                            val cellSize = kotlin.math.min(cellW, cellH)

                            // Center the grid
                            val offsetX = (size.width - (cellSize * gw)) / 2
                            val offsetY = (size.height - (cellSize * gh)) / 2

                            activeCells.forEach { (x, y) ->
                                drawRect(
                                    color = cellColor,
                                    topLeft = Offset(offsetX + (x * cellSize), offsetY + (y * cellSize)),
                                    size = androidx.compose.ui.geometry.Size(cellSize - 2f, cellSize - 2f) // -2f for grid gap
                                )
                            }
                        }
                    }
                }
            } else {
                // Standard Single Card Preview
                Card(
                    modifier = Modifier.width(100.dp).fillMaxHeight(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusSmall),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    val cardColor = when (session.mode) {
                        SessionMode.QUIZ, SessionMode.TYPING, SessionMode.FLASHCARD_QUIZ, SessionMode.ANAGRAM, SessionMode.HANGMAN -> if (session.quizPromptSide == CardSide.BACK) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                        else -> if (session.isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(cardColor)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (card != null) {
                            val textToShow = when (session.mode) {
                                // --- ADDED: "Crossword" to this list ---
                                SessionMode.QUIZ, SessionMode.TYPING, SessionMode.FLASHCARD_QUIZ, SessionMode.ANAGRAM, SessionMode.HANGMAN, SessionMode.CROSSWORD -> if (session.quizPromptSide == CardSide.BACK) card.back else card.front
                                else -> if (session.isFlipped) card.back else card.front
                            }
                            Text(
                                text = textToShow,
                                textAlign = TextAlign.Center,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.padding(6.dp))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                // Pre-calculate completed count for Crossword to use in both text and progress bar
                val completedCrosswordCount = remember(session.crosswordWords, session.crosswordUserInputs) {
                    if (session.mode == SessionMode.CROSSWORD) {
                        session.crosswordWords.count { word ->
                            word.word.indices.all { i ->
                                val x = if (word.isAcross) word.startX + i else word.startX
                                val y = if (word.isAcross) word.startY else word.startY + i
                                session.crosswordUserInputs["$x,$y"] == word.word[i].toString()
                            }
                        }
                    } else 0
                }

                val progressText = when (session.mode) {
                    SessionMode.MEMORY -> stringResource(R.string.pairs_progress_format, session.matchedPairs.size, session.totalCards)
                    SessionMode.MATCHING -> stringResource(R.string.matched_progress_format, session.matchedPairs.size, session.totalCards)
                    SessionMode.CROSSWORD -> stringResource(R.string.words_progress_format, completedCrosswordCount, session.crosswordWords.size)
                    else -> stringResource(R.string.progress_format, session.currentCardIndex, session.totalCards)
                }

                // Calculate the float value for the expressive progress bar
                val progressValue = when (session.mode) {
                    SessionMode.MEMORY -> if (session.totalCards > 0) session.matchedPairs.size.toFloat() / session.totalCards else 0f
                    SessionMode.MATCHING -> if (session.totalCards > 0) session.matchedPairs.size.toFloat() / session.totalCards else 0f
                    SessionMode.CROSSWORD -> if (session.crosswordWords.isNotEmpty()) completedCrosswordCount.toFloat() / session.crosswordWords.size else 0f
                    else -> if (session.totalCards > 0) session.currentCardIndex.toFloat() / session.totalCards else 0f
                }

                Text(text = progressText, fontSize = 16.sp, fontWeight = FontWeight.Bold)

                Spacer(modifier = Modifier.height(4.dp))

                // M3 Expressive Progress Indicator
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { progressValue },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp) // Thicker height for M3 Expressive style
                        .clip(androidx.compose.foundation.shape.CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Text(
                    stringResource(R.string.difficulties_format, session.difficulties.joinToString()),
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (session.mode == SessionMode.FLASHCARD || session.mode == SessionMode.FLASHCARD_QUIZ) {
                    Text(
                        stringResource(R.string.graded_format, if (session.isGraded) yesStr else noStr),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (session.mode == SessionMode.FLASHCARD_QUIZ) {
                        Text(
                            stringResource(R.string.prompt_format, session.quizPromptSide.asString()),
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (session.mode == SessionMode.QUIZ || session.mode == SessionMode.TYPING || session.mode == SessionMode.ANAGRAM || session.mode == SessionMode.CROSSWORD) {
                    Text(
                        stringResource(R.string.prompt_format, session.quizPromptSide.asString()),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (session.mode == SessionMode.MULTIPLE_CHOICE || session.mode == SessionMode.MATCHING) {
                    Text(
                        stringResource(R.string.graded_format, if (session.isGraded) yesStr else noStr),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        stringResource(R.string.reveal_when_wrong_format, if (!session.allowMultipleGuesses) yesStr else noStr),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        stringResource(R.string.weighted_format, if (session.isWeighted) yesStr else noStr),
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    stringResource(R.string.last_used_format, dateFormat.format(Date(session.lastAccessed))),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    stringResource(R.string.created_format, dateFormat.format(Date(session.createdAt))),
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Box {
                val menuInteractionSource = remember { MutableInteractionSource() }
                val isMenuPressed by menuInteractionSource.collectIsPressedAsState()
                val menuScale by animateFloatAsState(
                    targetValue = if (isMenuPressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "menuSquish"
                )
                IconButton(
                    onClick = { showMenu = true },
                    interactionSource = menuInteractionSource,
                    modifier = Modifier.scale(menuScale)
                ) {
                    Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.session_options))
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(
                        text = { Text(getText(R.string.copy)) },
                        leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                        onClick = { onCopy(); showMenu = false })
                    DropdownMenuItem(
                        text = { Text(getText(R.string.restart)) },
                        leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) },
                        onClick = { onRestart(); showMenu = false })
                    DropdownMenuItem(
                        text = { Text(getText(R.string.delete)) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                        onClick = { onDelete(); showMenu = false })
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

    val incorrectCards = remember(state.shuffledCards, state.incorrectCardIds) {
        state.shuffledCards.filter { it.id in state.incorrectCardIds }
    }

    var notScored = false
    if (state.studyMode == SessionMode.FLASHCARD || state.studyMode == SessionMode.TYPING || state.studyMode == SessionMode.CROSSWORD ||
        state.studyMode == SessionMode.MEMORY || state.studyMode == SessionMode.ANAGRAM || state.studyMode == SessionMode.HANGMAN)
        notScored = true
    // Typing mode shouldn't show review button as it forces correctness before moving on
    val showReviewButton = incorrectCards.isNotEmpty() && (notScored)


    Scaffold { padding ->
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
                Text(getText(R.string.congratulations), style = MaterialTheme.typography.headlineLarge)
                Text(getText(R.string.completed_session_msg), style = MaterialTheme.typography.titleMedium)

                // Hide accuracy score for Typing mode
                if (!notScored) {
                    val score = (state.firstTryCorrectCount.toFloat() / state.shuffledCards.size * 100).roundToInt()
                    Text(stringResource(R.string.first_try_accuracy_format, score), style = MaterialTheme.typography.titleLarge)
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

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
                    modifier = Modifier.fillMaxWidth(0.8f).scale(restartScale)
                ) {
                    Text(getText(R.string.restart_this_session))
                }

                val startInteractionSource = remember { MutableInteractionSource() }
                val isStartPressed by startInteractionSource.collectIsPressedAsState()
                val startScale by animateFloatAsState(
                    targetValue = if (isStartPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "startSquish"
                )
                Button(
                    onClick = { viewModel.restartStudySession() },
                    interactionSource = startInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.8f).scale(startScale)
                ) {
                    Text(getText(R.string.start_new_session))
                }

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
                        modifier = Modifier.fillMaxWidth(0.8f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError
                        )
                    ) {
                        Text(stringResource(R.string.review_incorrect_cards_format, incorrectCards.size))
                    }
                }
                val backSessionsInteractionSource = remember { MutableInteractionSource() }
                val isBackSessionsPressed by backSessionsInteractionSource.collectIsPressedAsState()
                val backSessionsScale by animateFloatAsState(
                    targetValue = if (isBackSessionsPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "backSessionsSquish"
                )
                OutlinedButton(
                    onClick = {
                        viewModel.deleteCurrentStudySession()
                        viewModel.endStudySession()
                        navController.popBackStack()
                    },
                    interactionSource = backSessionsInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.8f).scale(backSessionsScale)
                ) {
                    Text(getText(R.string.back_to_sessions))
                }

                val backDecksInteractionSource = remember { MutableInteractionSource() }
                val isBackDecksPressed by backDecksInteractionSource.collectIsPressedAsState()
                val backDecksScale by animateFloatAsState(
                    targetValue = if (isBackDecksPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "backDecksSquish"
                )
                OutlinedButton(
                    onClick = {
                        viewModel.deleteCurrentStudySession()
                        viewModel.endStudySession()
                        navController.popBackStack("deckList", inclusive = false)
                    },
                    interactionSource = backDecksInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.8f).scale(backDecksScale)
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
    var front by rememberSaveable(cardToEdit) { mutableStateOf(cardToEdit.front) }
    var back by rememberSaveable(cardToEdit) { mutableStateOf(cardToEdit.back) }
    var frontNotes by rememberSaveable(cardToEdit) { mutableStateOf(cardToEdit.frontNotes) }
    var backNotes by rememberSaveable(cardToEdit) { mutableStateOf(cardToEdit.backNotes) }
    var difficulty by rememberSaveable(cardToEdit) { mutableStateOf(cardToEdit.difficulty) }

    // --- NEW: Tag State ---
    var tags by remember { mutableStateOf(cardToEdit.tags) }

    // Collect all tags to pass to the picker
    val allTags by viewModel.tags.collectAsState()

    // Determine tags in the current deck for "Quick Select" (Context aware)
    val studyState = viewModel.studyState
    val currentDeckTags = remember(studyState) {
        studyState?.deckWithCards?.cards?.flatMap { it.tags }?.toSet() ?: emptySet()
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

                TextFieldWithNotes(
                    mainText = front,
                    onMainTextChange = { front = it },
                    mainLabel = getText(R.string.front),
                    notesText = frontNotes,
                    onNotesTextChange = { frontNotes = it },
                    notesLabel = getText(R.string.notes_front)
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                TextFieldWithNotes(
                    mainText = back,
                    onMainTextChange = { back = it },
                    mainLabel = getText(R.string.back),
                    notesText = backNotes,
                    onNotesTextChange = { backNotes = it },
                    notesLabel = getText(R.string.notes_back)
                )

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
                            back = back.trim(),
                            frontNotes = frontNotes?.trim()?.takeIf { it.isNotBlank() },
                            backNotes = backNotes?.trim()?.takeIf { it.isNotBlank() },
                            difficulty = difficulty,
                            tags = tags // Save updated tags
                        )
                        viewModel.updateCard(updatedCard)
                        onDismiss()
                    },
                    interactionSource = saveInteractionSource,
                    modifier = Modifier.fillMaxWidth().scale(saveScale),
                    enabled = front.isNotBlank() && back.isNotBlank()
                ) {
                    Text(getText(R.string.save_changes))
                }
            }
        }
    }
}