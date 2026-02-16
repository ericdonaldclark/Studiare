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
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.horizontalScroll
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.components.CardTagRow

/**
 * A screen that displays all active study sessions for a specific deck,
 * grouped by study mode. It allows users to resume, copy, restart, or delete sessions.
 * @param navController The NavController for navigating to study screens.
 * @param deck The deck for which to display study sessions.
 * @param viewModel The ViewModel providing data and business logic.
 */
@Composable
fun StudyModeSelectionScreen(navController: NavController, deck: net.ericclark.studiare.data.DeckWithCards, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    var showCreateSessionDialog by rememberSaveable { mutableStateOf(false) }
    var showFsrsConfigDialog by rememberSaveable { mutableStateOf<String?>(null) } // holds the selected mode
    val activeSessions by viewModel.activeSessions.collectAsState()

    // --- Data Preparation for Dialog ---
    val allTags by viewModel.tags.collectAsState()
    val parentDeckTags = remember(deck) {
        deck.cards.flatMap { it.tags }.distinct().sorted()
    }

    // Define sections explicitly
    data class SessionSection(val title: String, val filter: (net.ericclark.studiare.data.ActiveSession) -> Boolean)

    val sections = listOf(
        SessionSection("Flashcards") { it.mode == "Flashcard" },
        SessionSection("Flashcard Picker") { it.mode == "Flashcard Quiz" },
        SessionSection("Multiple Choice - Study") { it.mode == "Multiple Choice" && !it.isGraded },
        SessionSection("Multiple Choice - Quiz") { it.mode == "Multiple Choice" && it.isGraded },
        SessionSection("Matching - Study") { it.mode == "Matching" && !it.isGraded },
        SessionSection("Matching - Quiz") { it.mode == "Matching" && it.isGraded },
        SessionSection("Typing - Study") { it.mode == "Typing" },
        SessionSection("Typing - Quiz") { it.mode == "Quiz" },
        // Audio Sections
        SessionSection("Audio - Study") { it.mode == "Audio" && !it.isGraded },
        SessionSection("Audio - Quiz") { it.mode == "Audio" && it.isGraded },
        SessionSection("Anagram") { it.mode == "Anagram" },
        SessionSection("Hangman") { it.mode == "Hangman" },
        SessionSection("Memory") { it.mode == "Memory" },
        SessionSection("Crossword") { it.mode == "Crossword" }
    )

    // Dialog States
    var showRestartDialog by remember { mutableStateOf<net.ericclark.studiare.data.ActiveSession?>(null) }
    var showDeleteDialog by remember { mutableStateOf<net.ericclark.studiare.data.ActiveSession?>(null) }
    var showDeleteAllSessionsDialog by remember { mutableStateOf(false) }

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
            title = "Download HD Languages?",
            text = "Would you like to download high-quality language models for better speech recognition and playback?",
            confirmButtonText = "Yes",
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
                if (mode == "Flashcard" && selectAnswer) {
                    internalMode = "Flashcard Quiz"
                } else if (mode == "Typing" && isGraded) {
                    internalMode = "Quiz"
                }

                // Logic for NEW sessions
                val route = when (internalMode) {
                    "Flashcard" -> "flashcardStudy"
                    "Flashcard Quiz" -> "flashcardQuizStudy"
                    "Multiple Choice" -> "mcStudy"
                    "Matching" -> "matchingStudy"
                    "Typing" -> "typingStudy"
                    "Quiz" -> "quizStudy"
                    "Audio" -> "audioStudy"
                    "Anagram" -> "anagramStudy"
                    "Hangman" -> "hangmanStudy"
                    "Memory" -> "memoryStudy"
                    "Crossword" -> "crosswordStudy"
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
                if (mode == "Audio" && !hasPromptedHd) {
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
            title = "Restart Session?",
            text = "Are you sure you want to restart this session? Your progress will be reset to the beginning.",
            onConfirm = { viewModel.restartSession(session); showRestartDialog = null },
            onDismiss = { showRestartDialog = null }
        )
    }

    showDeleteDialog?.let { session ->
        ConfirmationDialog(
            title = "Delete Session?",
            text = "Are you sure you want to permanently delete this study session?",
            onConfirm = { viewModel.deleteSession(session); showDeleteDialog = null },
            onDismiss = { showDeleteDialog = null }
        )
    }

    val displayedSessions = activeSessions.filter { it.schedulingMode != "Spaced Repetition" }

    if (showDeleteAllSessionsDialog) {
        ConfirmationDialog(
            title = "Delete All Sessions?",
            text = "Are you sure you want to delete all active study sessions for this deck?",
            onConfirm = { viewModel.deleteAllSessionsForDeck(deck.deck.id); showDeleteAllSessionsDialog = false },
            onDismiss = { showDeleteAllSessionsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text(deck.deck.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = { IconButton(onClick = { navController.popBackStack() }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back to Decks") } }
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
                        text = "Start a spaced repetition session",
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
                        val fsrsModes = listOf("Flashcard", "Multiple Choice", "Typing", "Audio")
                        fsrsModes.forEach { mode ->
                            ToggleButton(
                                text = mode,
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
                        Text("No active normal sessions. Create one to get started!", fontSize = 18.sp, color = Color.Gray, textAlign = TextAlign.Center, modifier = Modifier.padding(dimensions.paddingMedium))
                    }
                } else {
                    sections.forEach { section ->
                        val sessionsInSection = displayedSessions.filter(section.filter).sortedByDescending { it.lastAccessed }
                        if (sessionsInSection.isNotEmpty()) {
                            item {
                                Text(
                                    text = section.title,
                                    style = MaterialTheme.typography.headlineSmall,
                                    modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                                )
                            }
                            item {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(minSize = 350.dp),
                                    verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
                                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
                                    modifier = Modifier.height((sessionsInSection.size * 160).dp)
                                ) {
                                    items(sessionsInSection, key = { it.id }) { session ->
                                        val cardIdToShow = if (session.mode == "Matching" && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(session.currentCardIndex)
                                        val card = deck.cards.find { it.id == cardIdToShow }

                                        SessionTile(
                                            session = session,
                                            card = card,
                                            onResume = {
                                                viewModel.resumeStudySession(session)
                                                val route = when (session.mode) {
                                                    "Flashcard" -> "flashcardStudy"
                                                    "Flashcard Quiz" -> "flashcardQuizStudy"
                                                    "Multiple Choice" -> "mcStudy"
                                                    "Matching" -> "matchingStudy"
                                                    "Typing" -> "typingStudy"
                                                    "Quiz" -> "quizStudy"
                                                    "Audio" -> "audioStudy"
                                                    // FIX: Added missing modes to prevent fallback to quizStudy
                                                    "Memory" -> "memoryStudy"
                                                    "Hangman" -> "hangmanStudy"
                                                    "Anagram" -> "anagramStudy"
                                                    "Crossword" -> "crosswordStudy"
                                                    else -> "quizStudy"
                                                }
                                                navController.navigate(route)
                                            },
                                            onCopy = { viewModel.copySession(session) },
                                            onRestart = { showRestartDialog = session },
                                            onDelete = { showDeleteDialog = session }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            FloatingActionButton(
                onClick = { showCreateSessionDialog = true },
                modifier = Modifier.align(Alignment.BottomEnd).padding(dimensions.paddingMedium)
            ) { Icon(Icons.Default.Add, contentDescription = "Create Study Session") }

            if (displayedSessions.isNotEmpty()) {
                FloatingActionButton(
                    onClick = { showDeleteAllSessionsDialog = true },
                    modifier = Modifier.align(Alignment.BottomStart).padding(dimensions.paddingMedium),
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ) { Icon(Icons.Default.Delete, contentDescription = "Delete All Sessions") }
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
                if (finalMode == "Flashcard" && selectAnswer) internalMode = "Flashcard Quiz"
                if (finalMode == "Typing") internalMode = "Quiz" // --- FIX: Map Typing to Quiz for FSRS ---

                val route = when (internalMode) {
                    "Flashcard" -> "flashcardStudy"
                    "Flashcard Quiz" -> "flashcardQuizStudy"
                    "Multiple Choice" -> "mcStudy"
                    "Matching" -> "matchingStudy"
                    "Typing" -> "typingStudy"
                    "Quiz" -> "quizStudy"
                    "Audio" -> "audioStudy"
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
    mode: String,
    deck: net.ericclark.studiare.data.DeckWithCards,
    onDismiss: () -> Unit,
    onStart: (
        config: net.ericclark.studiare.data.AutoSetConfig,
        mode: String, isWeighted: Boolean, promptSide: String, numAnswers: Int,
        showLetters: Boolean, limitPool: Boolean, selectAnswer: Boolean,
        multiGuess: Boolean, stt: Boolean, hideText: Boolean, fingers: Boolean,
        maxTiles: Int, density: Int
    ) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val defaultPromptSide = remember(deck) {
        val cards = deck.cards
        if (cards.isEmpty()) "Front" else {
            val avgFront = cards.map { it.front.length }.average()
            val avgBack = cards.map { it.back.length }.average()
            if (avgBack > (avgFront * 2)) "Back" else "Front"
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
                            text = mode,
                            style = MaterialTheme.typography.headlineSmall,
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "(Spaced Repetition)",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                            textAlign = TextAlign.Center
                        )
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.TopEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                Text("Prompt Side", style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    ToggleButton("Front", quizPromptSide == "Front", { quizPromptSide = "Front" }, Modifier.weight(1f))
                    ToggleButton("Back", quizPromptSide == "Back", { quizPromptSide = "Back" }, Modifier.weight(1f))
                }
                Spacer(Modifier.height(dimensions.spacingSmall))

                if (mode == "Multiple Choice") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Answers: $numberOfAnswers", modifier = Modifier.weight(1f))
                        IconButton(onClick = { if (numberOfAnswers > 2) numberOfAnswers-- }) { Icon(Icons.Default.Remove, "Less") }
                        IconButton(onClick = { if (numberOfAnswers < 8) numberOfAnswers++ }) { Icon(Icons.Default.Add, "More") }
                    }
                }
                if (mode == "Flashcard") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Select answer (Picker)", modifier = Modifier.weight(1f))
                        Switch(checked = selectAnswer, onCheckedChange = { selectAnswer = it })
                    }
                }
                if (mode == "Typing") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Show correct letters", modifier = Modifier.weight(1f))
                        Switch(checked = showCorrectLetters, onCheckedChange = { showCorrectLetters = it })
                    }
                }

                Spacer(Modifier.height(dimensions.spacingLarge))

                Button(
                    onClick = {
                        val config = AutoSetConfig(
                            mode = "One", numSets = 1, maxCardsPerSet = 9999,
                            selectionMode = "Any", selectedTags = emptyList(), selectedDifficulties = emptyList(),
                            excludeKnown = false, sortMode = "Review Date", sortDirection = "ASC", sortSide = "Front",
                            schedulingMode = "Spaced Repetition"
                        )
                        // FIX: Pass limitPool = false so options are generated from the whole deck
                        onStart(config, mode, false, quizPromptSide, numberOfAnswers, showCorrectLetters, false, selectAnswer, allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes, maxMemoryTiles, 2)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Start Session")
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
                    Text("Language", modifier = Modifier.weight(0.5f).padding(dimensions.paddingMedium), fontWeight = FontWeight.Bold)
                    VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                    // Size Header
                    Text("Size", modifier = Modifier.weight(0.3f).padding(dimensions.paddingSmall), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                    Text("Download", modifier = Modifier.weight(0.2f).padding(dimensions.paddingSmall), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // --- LIST CONTENT ---
                LazyColumn {
                    itemsIndexed(languages) { index, code ->
                        val name = languageDisplayMap[code] ?: code
                        val isDownloaded = downloadedLanguages.contains(code)
                        val size = languageSizes[code] ?: "?"

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .clickable(enabled = !isDownloaded) {
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
                TextButton(onClick = {
                    selectedLanguages.clear()
                    selectedLanguages.addAll(languages.filter { !downloadedLanguages.contains(it) })
                }) { Text("Select All") }
                TextButton(onClick = { selectedLanguages.clear() }) { Text("Deselect All") }
            }

            Spacer(Modifier.height(dimensions.spacingMedium))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("Cancel") }
                Spacer(Modifier.width(dimensions.spacingSmall))
                Button(
                    onClick = { onDownload(selectedLanguages.toList()) },
                    // Enable only if there are NEW selections
                    enabled = selectedLanguages.isNotEmpty()
                ) { Text("Download") }
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
    session: net.ericclark.studiare.data.ActiveSession,
    card: net.ericclark.studiare.data.Card?,
    onResume: () -> Unit,
    onCopy: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy 'at' h:mm a", Locale.getDefault()) }
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
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
            if (session.mode == "Memory") {
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
            else if (session.mode == "Crossword") {
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
                        "Quiz", "Typing", "Flashcard Quiz", "Anagram", "Hangman" -> if (session.quizPromptSide == "Back") MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
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
                                "Quiz", "Typing", "Flashcard Quiz", "Anagram", "Hangman", "Crossword" -> if (session.quizPromptSide == "Back") card.back else card.front
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
                // Update Progress Text for Memory
                val progressText = when (session.mode) {
                    "Memory" -> "Pairs: ${session.matchedPairs.size} / ${session.totalCards}"
                    "Matching" -> "Matched: ${session.matchedPairs.size} / ${session.totalCards}"
                    "Crossword" -> {
                        // Calculate completed words dynamically
                        val completedCount = session.crosswordWords.count { word ->
                            word.word.indices.all { i ->
                                val x = if (word.isAcross) word.startX + i else word.startX
                                val y = if (word.isAcross) word.startY else word.startY + i
                                session.crosswordUserInputs["$x,$y"] == word.word[i].toString()
                            }
                        }
                        "Words: $completedCount / ${session.crosswordWords.size}"
                    }
                    else -> "Progress: ${session.currentCardIndex} / ${session.totalCards}"
                }

                Text(text = progressText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                Text(
                    "Difficulties: ${session.difficulties.joinToString()}",
                    fontSize = 13.sp,
                    lineHeight = 15.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (session.mode == "Flashcard" || session.mode == "Flashcard Quiz") {
                    Text(
                        "Graded: ${if (session.isGraded) "Yes" else "No"}",
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (session.mode == "Flashcard Quiz") {
                        Text(
                            "Prompt: ${session.quizPromptSide}",
                            fontSize = 13.sp,
                            lineHeight = 15.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else if (session.mode == "Quiz" || session.mode == "Typing" || session.mode == "Anagram" || session.mode == "Crossword") {
                    Text(
                        "Prompt: ${session.quizPromptSide}",
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else if (session.mode == "Multiple Choice" || session.mode == "Matching") {
                    Text(
                        "Graded: ${if (session.isGraded) "Yes" else "No"}",
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "Reveal When Wrong: ${if (!session.allowMultipleGuesses) "Yes" else "No"}",
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Weighted: ${if (session.isWeighted) "Yes" else "No"}",
                        fontSize = 13.sp,
                        lineHeight = 15.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    "Last Used: ${dateFormat.format(Date(session.lastAccessed))}",
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(
                    "Created: ${dateFormat.format(Date(session.createdAt))}",
                    fontSize = 11.sp,
                    lineHeight = 13.sp,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Box {
                IconButton(onClick = { showMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Session Options")
                }
                DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                    DropdownMenuItem(text = { Text("Copy") }, onClick = { onCopy(); showMenu = false })
                    DropdownMenuItem(text = { Text("Restart") }, onClick = { onRestart(); showMenu = false })
                    DropdownMenuItem(text = { Text("Delete") }, onClick = { onDelete(); showMenu = false })
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
    if (state.studyMode == "Flashcard" || state.studyMode == "Typing" || state.studyMode == "Crossword" ||
        state.studyMode == "Memory" || state.studyMode == "Anagram" || state.studyMode == "Hangman")
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
                Text("Congratulations!", style = MaterialTheme.typography.headlineLarge)
                Text("You've completed the session.", style = MaterialTheme.typography.titleMedium)

                // Hide accuracy score for Typing mode
                if (!notScored) {
                    val score = (state.firstTryCorrectCount.toFloat() / state.shuffledCards.size * 100).roundToInt()
                    Text("First Try Accuracy: $score%", style = MaterialTheme.typography.titleLarge)
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Button(
                    onClick = { viewModel.restartSameSession() },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Restart This Session")
                }
                Button(
                    onClick = { viewModel.restartStudySession() },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Start New Session (New Shuffle)")
                }
                AnimatedVisibility(visible = showReviewButton) {
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
                        Text("Review ${incorrectCards.size} Incorrect Cards")
                    }
                }
                OutlinedButton(
                    onClick = {
                        viewModel.deleteCurrentStudySession()
                        viewModel.endStudySession()
                        navController.popBackStack()
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Back to Sessions")
                }
                OutlinedButton(
                    onClick = {
                        viewModel.deleteCurrentStudySession()
                        viewModel.endStudySession()
                        navController.popBackStack("deckList", inclusive = false)
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) {
                    Text("Back to Decks")
                }
            }
        }
    }
}


@Composable
fun EditCardDialog(
    cardToEdit: net.ericclark.studiare.data.Card,
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
                        "Edit Card",
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Discard Changes")
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))

                TextFieldWithNotes(
                    mainText = front,
                    onMainTextChange = { front = it },
                    mainLabel = "Front",
                    notesText = frontNotes,
                    onNotesTextChange = { frontNotes = it },
                    notesLabel = "Front Notes"
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                TextFieldWithNotes(
                    mainText = back,
                    onMainTextChange = { back = it },
                    mainLabel = "Back",
                    notesText = backNotes,
                    onNotesTextChange = { backNotes = it },
                    notesLabel = "Back Notes"
                )

                Spacer(Modifier.height(dimensions.spacingSmall))

                // --- NEW: Tag Row Component ---
                Text("Tags", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
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
                        label = "Difficulty",
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
                    modifier = Modifier.fillMaxWidth(),
                    enabled = front.isNotBlank() && back.isNotBlank()
                ) {
                    Text("Save Changes")
                }
            }
        }
    }
}