package net.ericclark.studiare.screens

import android.widget.Toast
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*

/** Route that shows the study screen for [mode]. */
fun studyRouteFor(mode: SessionMode): String = when (mode) {
    SessionMode.FLASHCARD -> "flashcardStudy"
    SessionMode.FREEFORM -> "freeformStudy"
    SessionMode.LIST -> "flashcardQuizStudy"
    SessionMode.MULTIPLE_CHOICE -> "mcStudy"
    SessionMode.MATCHING -> "matchingStudy"
    SessionMode.TYPING -> "typingStudy"
    SessionMode.TYPING_SCORED -> "typingScoredStudy"
    SessionMode.AUDIO -> "audioStudy"
    SessionMode.MEMORY -> "memoryStudy"
    SessionMode.HANGMAN -> "hangmanStudy"
    SessionMode.ANAGRAM -> "anagramStudy"
    SessionMode.CROSSWORD -> "crosswordStudy"
    SessionMode.WORD_SEARCH -> "wordSearchStudy"
    SessionMode.TYPED_LISTEN -> "typedListenStudy"
    SessionMode.SPOKEN_LISTEN -> "spokenListenStudy"
}

/**
 * Shows the "create study session" dialog (or the spaced repetition dialogs) on top of whatever
 * screen hosts it, then starts the session and navigates straight to its study screen.
 */
@Composable
fun StudySessionDialogHost(
    deck: DeckWithCards,
    category: StudyCategory?,
    viewModel: FlashcardViewModel,
    navController: NavController,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val allTags by viewModel.tags.collectAsState()
    val parentDeckTags by produceState(initialValue = emptyList<String>(), key1 = deck.deck.id) {
        value = withContext(Dispatchers.Default) { deck.cards.flatMap { it.tags }.distinct().sorted() }
    }

    val hasPromptedHd by viewModel.hasPromptedHdLanguages.collectAsState()
    val downloadedHdLanguages by viewModel.downloadedHdLanguages.collectAsState()
    var showHdPromptDialog by remember { mutableStateOf(false) }
    var showHdSelectionDialog by remember { mutableStateOf(false) }
    var pendingSessionAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    var showCreateDialog by remember { mutableStateOf(category != null) }

    val toastMessage = viewModel.toastMessage
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    if (showHdPromptDialog) {
        ConfirmationDialog(
            title = getText(R.string.download_hd_languages_title),
            text = getText(R.string.download_hd_languages_desc),
            confirmButtonText = getText(R.string.yes),
            onConfirm = { showHdPromptDialog = false; showHdSelectionDialog = true },
            dismissButtonText = getText(R.string.no),
            onDismiss = {
                showHdPromptDialog = false
                viewModel.setHdAudioPrompted()
                pendingSessionAction?.invoke()
                pendingSessionAction = null
                onDismiss()
            }
        )
    }

    if (showHdSelectionDialog) {
        val uniqueLangs = remember(deck.deck.id) { viewModel.getUniqueDeckLanguages() }
        val languageSizes = remember(uniqueLangs) { uniqueLangs.associateWith { viewModel.getFormattedModelSize(it) } }
        HdLanguageSelectionDialog(
            languages = uniqueLangs,
            downloadedLanguages = downloadedHdLanguages,
            languageSizes = languageSizes,
            onDismiss = {
                showHdSelectionDialog = false
                viewModel.setHdAudioPrompted()
                pendingSessionAction?.invoke()
                pendingSessionAction = null
                onDismiss()
            },
            onDownload = { selectedLangs ->
                showHdSelectionDialog = false
                viewModel.startHdLanguageDownload(context, selectedLangs)
                pendingSessionAction?.invoke()
                pendingSessionAction = null
                onDismiss()
            }
        )
    }

    if (showCreateDialog && category != null) {
        CreateStudySessionDialog(
            deck = deck,
            initialCategory = category,
            onCategoryChosen = { viewModel.setLastStudyCategory(it) },
            availableTags = parentDeckTags,
            allTagDefinitions = allTags,
            modeDefaults = viewModel.modeDefaultSettings.collectAsState().value,
            onDismiss = { showCreateDialog = false; onDismiss() },
            onStartSession = { mode, isWeighted, numCards, quizPromptSide, numAnswers, showLetters, limitPool,
                               isGraded, allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes,
                               maxMemoryTiles, gridDensity, _, freeformVerticalLayout, config ->
                showCreateDialog = false

                val route = studyRouteFor(mode)

                val startAction = {
                    viewModel.startStudySession(
                        parentDeck = deck,
                        mode = mode,
                        isWeighted = isWeighted,
                        numCards = numCards,
                        quizPromptSide = quizPromptSide,
                        numAnswers = numAnswers,
                        showCorrectLetters = showLetters,
                        limitAnswerPool = limitPool,
                        isGraded = isGraded,
                        allowMultipleGuesses = allowMultipleGuesses,
                        enableStt = enableStt,
                        hideAnswerText = hideAnswerText,
                        fingersAndToes = fingersAndToes,
                        maxMemoryTiles = maxMemoryTiles,
                        gridDensity = gridDensity,
                        config = config,
                        freeformLayoutVertical = freeformVerticalLayout
                    ) {
                        navController.navigate(route)
                    }
                }

                if (mode == SessionMode.AUDIO && !hasPromptedHd) {
                    pendingSessionAction = startAction
                    showHdPromptDialog = true
                } else {
                    startAction()
                    onDismiss()
                }
            }
        )
    }
}
