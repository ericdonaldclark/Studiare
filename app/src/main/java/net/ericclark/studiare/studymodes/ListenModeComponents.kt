package net.ericclark.studiare.studymodes

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.LocalWindowWidthSizeClass
import net.ericclark.studiare.QuizCardContent
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.StudyCompletionScreen
import net.ericclark.studiare.TooltipIconButton
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.components.speech.AnswerMatcher
import net.ericclark.studiare.components.speech.RecognitionFailureReason
import net.ericclark.studiare.components.speech.RecognitionResult
import net.ericclark.studiare.components.speech.SpeechEngine
import net.ericclark.studiare.components.speech.SpeechRecognitionEngine
import net.ericclark.studiare.components.speech.WhisperModelSize
import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.CardSide
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.data.Deck
import net.ericclark.studiare.data.MediaType
import net.ericclark.studiare.data.SchedulingMode
import net.ericclark.studiare.data.StudyState
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * Shared plumbing for the two speech-based study modes (Typed Listen — "Listening" — and Spoken
 * Listen — "Speaking" — see the Studiare roadmap plan; each pair collapsed into one mode since
 * practice vs quiz is purely a function of `isGraded`). Handles the completion-screen hand-off and
 * top app bar exactly like every other mode screen; the mode-specific body (card, input, buttons)
 * is supplied by [content]. [titleFormatRes] names the deck-title format (the mode name itself no
 * longer varies by practice/quiz, just like every other mode).
 */
@Composable
fun ListenModeShell(
    navController: NavController,
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    titleFormatRes: Int,
    content: @Composable (state: StudyState, padding: PaddingValues) -> Unit
) {
    val state = viewModel.studyState ?: return

    if (state.isComplete) {
        StudyCompletionScreen(navController = navController, viewModel = viewModel)
        return
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = screenId,
                title = {
                    Text(stringResource(titleFormatRes, state.deckWithCards.deck.name))
                },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back)) }
                }
            )
        }
    ) { padding -> content(state, padding) }
}

/**
 * The card + mode-specific body for all four modes, branching on window width the same way
 * every other quiz-style mode does (`TypingMode.kt`'s `PortraitQuizLayout`/`LandscapeQuizLayout`)
 * — the 4 new modes originally used one plain vertical `Column` regardless of width. On a wide
 * window (Chromebook, tablet landscape) `QuizCardContent`'s default sizing is `fillMaxWidth()` +
 * `aspectRatio(1.6f)`, so its *height* scales with the window's full width — on a 1600dp-wide
 * Chromebook window that's an ~1000dp-tall card, leaving no room for anything below it. Confining
 * the card to one side of a `Row` (as every other mode already does) bounds it by that half's
 * width instead of the whole window's.
 *
 * [belowCard] is the mode-specific area (replay/mic button, input field, footer) — scrollable on
 * the wide layout since it sits in a fixed-height column alongside the card.
 */
@Composable
fun ListenModeLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    padding: PaddingValues,
    overrideSide: CardSide?,
    belowCard: @Composable () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
        Row(
            modifier = Modifier.padding(padding).fillMaxSize().padding(dimensions.paddingMedium),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                QuizCardContent(state = state, viewModel = viewModel, modifier = Modifier.fillMaxSize(), overrideSide = overrideSide)
            }
            Spacer(Modifier.width(dimensions.spacingLarge))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                belowCard()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .padding(dimensions.paddingMedium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuizCardContent(state = state, viewModel = viewModel, overrideSide = overrideSide)
            Spacer(Modifier.height(dimensions.spacingMedium))
            belowCard()
        }
    }
}

/** The text on [side] of [this] card — front or back, whichever is asked for. */
fun Card.textForSide(side: CardSide): String = if (side == CardSide.FRONT) front else back

/**
 * The plain-text/rich-text notes on [side], HTML-stripped and joined — the same construction
 * `AudioStudyService` uses for what it speaks alongside the card text.
 */
fun Card.notesForSide(side: CardSide): String {
    val notes = if (side == CardSide.FRONT) frontNotes else backNotes
    return notes.filter { it.type == MediaType.PLAIN_TEXT || it.type == MediaType.RICH_TEXT }
        .joinToString(". ") { it.content.replace(Regex("<[^>]*>"), "") }
}

/** The deck's language code for [side]. */
fun Deck.languageForSide(side: CardSide): String = if (side == CardSide.FRONT) frontLanguage else backLanguage

fun CardSide.opposite(): CardSide = if (this == CardSide.FRONT) CardSide.BACK else CardSide.FRONT

/** Owns one [SpeechEngine] for a screen's lifetime and exposes simple play control. */
class ListenAudioController(private val engine: SpeechEngine) {
    var isPlaying by mutableStateOf(false)
        private set

    /** Per-session settings, set by the screen before it plays: speech rate (1.0 = normal) and how many times a clip plays. */
    var speechRate: Float = 1f
    var replayCount: Int = 1

    private val job = kotlinx.coroutines.SupervisorJob()
    private val scope = kotlinx.coroutines.CoroutineScope(job + kotlinx.coroutines.Dispatchers.Main)

    fun play(text: String, notes: String?, languageCode: String) {
        scope.launch {
            isPlaying = true
            try {
                repeat(replayCount.coerceAtLeast(1)) { engine.speak(text, notes, languageCode, speechRate = speechRate) }
            } finally {
                isPlaying = false
            }
        }
    }

    /**
     * Like [play], but suspends until the audio actually finishes instead of firing-and-forgetting
     * — for sequencing two clips back to back (e.g. Speech-to-Text's front-then-back autoplay),
     * where [play] plus a fixed `delay()` guess would either cut the first clip off (too short a
     * guess) or leave dead air (too long), and either way lets the second `speak()` call preempt
     * the first mid-sentence since they share one underlying engine.
     */
    suspend fun playAndAwait(text: String, notes: String?, languageCode: String) {
        isPlaying = true
        try {
            repeat(replayCount.coerceAtLeast(1)) { engine.speak(text, notes, languageCode, speechRate = speechRate) }
        } finally {
            isPlaying = false
        }
    }

    fun release() {
        job.cancel()
        engine.release()
    }
}

@Composable
fun rememberListenAudioController(): ListenAudioController {
    val context = LocalContext.current
    val controller = remember { ListenAudioController(SpeechEngine(context)) }
    DisposableEffect(Unit) { onDispose { controller.release() } }
    return controller
}

@Composable
fun rememberSpeechRecognitionEngine(): SpeechRecognitionEngine {
    val context = LocalContext.current
    val engine = remember { SpeechRecognitionEngine(context) }
    DisposableEffect(Unit) { onDispose { engine.release() } }
    return engine
}

/**
 * The bottom control for all four modes: while not yet answered, one primary action button
 * ([primaryLabel]/[onPrimaryAction] — "Reveal" for the verified modes, "I Said It" for
 * Text-to-Speech); once answered, either FSRS's Hard/Good/Easy (when the session is
 * FSRS-scheduled — practice modes can be FSRS-scheduled too, same as Typing) or a plain "Next
 * Card" button. Mirrors `QuizBottomButton`/the FSRS block in `TypingMode.kt`, generalized with a
 * custom primary action since Text-to-Speech has nothing to "reveal".
 */
@Composable
fun ListenModeFooter(
    state: StudyState,
    viewModel: FlashcardViewModel,
    primaryLabel: String,
    onPrimaryAction: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    val isFsrsGradingDue = state.schedulingMode == SchedulingMode.FSRS &&
        state.correctAnswerFound &&
        !state.incorrectCardIds.contains(card.id)

    when {
        isFsrsGradingDue -> FsrsGradingButtons(state = state, viewModel = viewModel)
        state.correctAnswerFound -> {
            Button(
                onClick = { viewModel.nextCard() },
                modifier = Modifier.fillMaxWidth(0.8f).defaultMinSize(minHeight = 56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) { Text(getText(R.string.next_card)) }
        }
        else -> {
            // Outlined, not filled — this is the secondary/give-up action (Get Answer, or I Said
            // It's self-mark) next to a mode's own primary Submit button, not the main call to
            // action.
            OutlinedButton(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(0.8f).defaultMinSize(minHeight = 56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) { Text(primaryLabel) }
        }
    }
}

@Composable
private fun FsrsGradingButtons(state: StudyState, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    var processingClick by remember(state.currentCardIndex) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall), modifier = Modifier.fillMaxWidth()) {
        listOf(
            Triple(2, Color(0xFFFCBA03) to Color.Black, R.string.rating_hard),
            Triple(3, Color(0xFF488C4B) to Color.White, R.string.rating_good),
            Triple(4, Color(0xFF4287F5) to Color.White, R.string.rating_easy)
        ).forEach { (rating, colors, labelRes) ->
            Button(
                onClick = {
                    if (!processingClick) {
                        processingClick = true
                        scope.launch { delay(150); viewModel.submitFsrsGrade(rating) }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = colors.first, contentColor = colors.second),
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                enabled = !processingClick,
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = state.nextIntervals[rating] ?: "", style = MaterialTheme.typography.labelSmall)
                    Text(getText(labelRes))
                }
            }
        }
    }
}

/**
 * Outcome of one mic attempt against [expectedAnswer], shared by Listen & Speak (answer hidden)
 * and Text-to-Speech (answer shown) so their mic-handling logic — and which failure maps to which
 * message — can't quietly drift apart between the two screens.
 */
sealed class SpokenAnswerOutcome {
    data class Correct(val heard: String) : SpokenAnswerOutcome()
    /** Recognized speech, but it didn't match — counts against the attempt limit. */
    data object NoMatch : SpokenAnswerOutcome()
    /** Nothing was heard — counts against the attempt limit, distinct wording from [NoMatch]. */
    data object NoSpeech : SpokenAnswerOutcome()
    /** Recognition itself isn't usable (no permission, no model + no system recognizer) — the
     *  caller should offer its escape hatch immediately rather than burn an attempt on a mic that
     *  can't work. */
    data class Unusable(val reason: RecognitionFailureReason) : SpokenAnswerOutcome()
}

suspend fun SpeechRecognitionEngine.classifySpokenAttempt(
    languageCode: String,
    whisperSize: WhisperModelSize?,
    expectedAnswer: String
): SpokenAnswerOutcome = when (val result = listen(languageCode = languageCode, whisperSize = whisperSize)) {
    is RecognitionResult.Recognized ->
        if (AnswerMatcher.matchesSpoken(result.text, expectedAnswer)) SpokenAnswerOutcome.Correct(result.text)
        else SpokenAnswerOutcome.NoMatch
    is RecognitionResult.Failed -> when (result.reason) {
        RecognitionFailureReason.NO_SPEECH_DETECTED -> SpokenAnswerOutcome.NoSpeech
        else -> SpokenAnswerOutcome.Unusable(result.reason)
    }
}

/**
 * Shown once, the first time a mic-based mode (Listen & Speak, Text-to-Speech) is opened with no
 * Whisper size picked yet. Same three sizes/descriptions as the Settings → Speech Recognition
 * section ([WhisperModelSize]); picking one starts the same download the Settings screen would,
 * dismissing either way just marks the prompt as seen ([FlashcardViewModel.setHasPromptedWhisperModel]).
 */
@Composable
fun WhisperFirstUsePrompt(viewModel: FlashcardViewModel, onDismiss: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    var downloadingSize by remember { mutableStateOf<WhisperModelSize?>(null) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var downloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        downloadingSize = null
        downloadProgress = 0f
    }

    AlertDialog(
        // Tapping outside/back while downloading cancels rather than being blocked outright.
        onDismissRequest = { if (downloadingSize != null) cancelDownload() else onDismiss() },
        title = { Text(getText(R.string.whisper_first_use_title)) },
        text = {
            Column {
                Text(getText(R.string.whisper_first_use_body), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(dimensions.spacingMedium))
                WhisperModelSize.entries.forEach { size ->
                    val isDownloadingThis = downloadingSize == size
                    Card(
                        onClick = {
                            if (downloadingSize == null) {
                                downloadingSize = size
                                downloadProgress = 0f
                                downloadJob = viewModel.startWhisperModelDownload(
                                    size = size,
                                    onProgress = { downloadProgress = it },
                                    onComplete = { onDismiss() }
                                )
                            }
                        },
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
                            Text(
                                stringResource(R.string.whisper_model_name_and_size_format, size.asString(), stringResource(size.downloadSizeLabelResId)),
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(stringResource(size.descriptionResId), style = MaterialTheme.typography.bodySmall)
                            if (isDownloadingThis) {
                                Spacer(Modifier.height(dimensions.spacingSmall))
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                Text(
                    getText(R.string.speech_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            if (downloadingSize != null) {
                TextButton(onClick = { cancelDownload() }) { Text(getText(R.string.cancel)) }
            } else {
                TextButton(onClick = onDismiss) { Text(getText(R.string.not_now)) }
            }
        }
    )
}
