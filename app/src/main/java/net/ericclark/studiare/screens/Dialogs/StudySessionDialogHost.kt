package net.ericclark.studiare.screens.Dialogs

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
import net.ericclark.studiare.screens.UI_Components.HdLanguageSelectionDialog

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

    val voicePrompt = rememberVoicePromptState()
    VoiceDownloadPrompts(state = voicePrompt, viewModel = viewModel, onFinished = onDismiss)

    var showCreateDialog by remember { mutableStateOf(category != null) }

    val toastMessage = viewModel.toastMessage
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    if (showCreateDialog && category != null) {
        CreateStudySessionDialog(
            deck = deck,
            initialCategory = category,
            onCategoryChosen = { viewModel.setLastStudyCategory(it) },
            notificationPromptShown = viewModel.notificationPromptShown.collectAsState().value,
            onNotificationPromptShown = { viewModel.markNotificationPromptShown() },
            availableTags = parentDeckTags,
            allTagDefinitions = allTags,
            modeDefaults = viewModel.modeDefaultSettings.collectAsState().value,
            onDismiss = { showCreateDialog = false; onDismiss() },
            onStartSession = { mode, isWeighted, numCards, quizPromptSide, numAnswers, showLetters, limitPool,
                               isGraded, allowMultipleGuesses,
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
                        maxMemoryTiles = maxMemoryTiles,
                        gridDensity = gridDensity,
                        config = config,
                        freeformLayoutVertical = freeformVerticalLayout
                    ) {
                        navController.navigate(route)
                    }
                }

                if (mode.usesVoices() && viewModel.pendingVoiceLanguages().isNotEmpty()) {
                    voicePrompt.request(startAction)
                } else {
                    startAction()
                    onDismiss()
                }
            }
        )
    }
}

/** Whether a session in [this] mode reads cards aloud with the per-language voices (the first-run prompt applies). */
internal fun SessionMode.usesVoices(): Boolean =
    this == SessionMode.AUDIO || this == SessionMode.TYPED_LISTEN || this == SessionMode.SPOKEN_LISTEN

/** Holds the first-run voice prompt for a screen that starts audio sessions. */
class VoicePromptState {
    var showDialog by mutableStateOf(false)
    internal var pendingSessionAction: (() -> Unit)? = null

    /** Shows the voice prompt; [action] starts the session when the prompt is closed. */
    fun request(action: () -> Unit) {
        pendingSessionAction = action
        showDialog = true
    }
}

@Composable
fun rememberVoicePromptState(): VoicePromptState = remember { VoicePromptState() }

/**
 * The first-run voice prompt: one dialog for the deck languages that are still pending (not downloaded, not
 * dismissed). Shared by every screen that starts an audio session. Any way out starts the pending session; only
 * the two dismiss buttons save anything.
 */
@Composable
fun VoiceDownloadPrompts(
    state: VoicePromptState,
    viewModel: FlashcardViewModel,
    onFinished: () -> Unit = {}
) {
    val context = LocalContext.current
    val downloadedLanguages by viewModel.downloadedHdLanguages.collectAsState()
    val voiceDownload by viewModel.voiceDownload.collectAsState()

    fun finish() {
        state.showDialog = false
        state.pendingSessionAction?.invoke()
        state.pendingSessionAction = null
        onFinished()
    }

    if (state.showDialog) {
        // Fixed for this showing, so rows don't vanish as their downloads finish
        val pending = remember { viewModel.pendingVoiceLanguages() }
        HdLanguageSelectionDialog(
            languages = pending,
            downloadedLanguages = downloadedLanguages,
            languageSizes = remember(pending) { pending.associateWith { viewModel.getFormattedModelSize(it) } },
            voiceDownload = voiceDownload,
            onDownload = { viewModel.startHdLanguageDownload(context, it) }, // the dialog stays open to show progress
            onCancelDownload = { viewModel.cancelVoiceDownload() },
            onStartSession = { finish() },
            onDismissForThese = { viewModel.dismissVoicePromptFor(pending); finish() },
            onDismissForAll = { viewModel.dismissVoicePromptForAll(); finish() },
            onClose = { finish() }
        )
    }
}

/**
 * The languages the voice prompt would still ask about: the deck languages that are neither downloaded nor
 * dismissed. Nothing is pending once the prompt was dismissed for all languages.
 */
internal fun pendingVoiceLanguagesFor(
    deckLanguages: List<String>,
    downloaded: Set<String>,
    dismissed: Set<String>,
    allDismissed: Boolean
): List<String> {
    if (allDismissed) return emptyList()
    return deckLanguages.filter { it !in downloaded && it !in dismissed }
}

