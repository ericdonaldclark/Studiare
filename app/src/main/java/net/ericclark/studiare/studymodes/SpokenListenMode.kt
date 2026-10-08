package net.ericclark.studiare.studymodes

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.TooltipIconButton
import net.ericclark.studiare.components.SoundEffect
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.components.rememberSoundEffectPlayer
import net.ericclark.studiare.components.speech.AnswerMatcher
import net.ericclark.studiare.components.speech.RecognitionFailureReason
import net.ericclark.studiare.components.speech.WhisperModelSize
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * Spoken-answer listening mode — Text-to-Speech (practice) and Listen & Speak (quiz) collapsed
 * into one screen, since most of what differs between them is `state.isGraded`:
 * - **Practice** (`!isGraded`, "Text-to-Speech"): the back *text* is shown once the front finishes
 *   (a pronunciation prompt, no recall needed) and "I Said It" is a permanent, always-available
 *   self-mark/escape hatch (no attempt limit — nothing's graded, so there's nothing to protect by
 *   capping attempts). The mic is an optional way to have it verified automatically instead of
 *   just self-reporting.
 * - **Quiz** (`isGraded`, "Listen & Speak"): the back text stays hidden — must be recalled from
 *   memory and spoken — graded via [FlashcardViewModel.submitListenAnswer], capped at 3 attempts
 *   before falling back to a typed-answer field (still graded — self-marking would defeat the
 *   grade, so the escape hatch is a different but still-verifiable input method, not a shortcut).
 *
 * Both use the exact same mic pipeline (down to [classifySpokenAttempt] and the
 * [WhisperFirstUsePrompt]), just gated differently once an attempt comes back.
 */
@Composable
fun SpokenListenScreen(navController: NavController, viewModel: FlashcardViewModel) {
    val audioController = rememberListenAudioController()
    val recognitionEngine = rememberSpeechRecognitionEngine()
    val soundPlayer = rememberSoundEffectPlayer()
    val whisperSizeId by viewModel.whisperModelSize.collectAsState()
    val whisperSize = remember(whisperSizeId) { WhisperModelSize.fromId(whisperSizeId) }
    val scope = rememberCoroutineScope()

    var micPermissionGranted by remember { mutableStateOf(recognitionEngine.hasRecordAudioPermission()) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        micPermissionGranted = granted
    }

    // First-use prompt: nudge toward downloading an on-device Whisper model instead of silently
    // relying on the system recognizer fallback the whole session. Only relevant once — dismissing
    // either way sets HAS_PROMPTED_WHISPER_MODEL so it never nags again — and only offered while no
    // size is picked yet. `promptDecided` gates the card's own audio autoplay below (see its
    // LaunchedEffect) so the front word doesn't start playing before this dialog — or, further up
    // the navigation stack, the HD-voice download prompt — has had its chance to show; both would
    // otherwise race the very first card's autoplay.
    val hasPromptedWhisperModel by viewModel.hasPromptedWhisperModel.collectAsState()
    var showWhisperPrompt by remember { mutableStateOf(false) }
    var promptDecided by remember { mutableStateOf(false) }
    LaunchedEffect(hasPromptedWhisperModel, whisperSizeId) {
        showWhisperPrompt = !hasPromptedWhisperModel && whisperSizeId == null
        promptDecided = true
    }
    if (showWhisperPrompt) {
        WhisperFirstUsePrompt(
            viewModel = viewModel,
            onDismiss = { viewModel.setHasPromptedWhisperModel(); showWhisperPrompt = false }
        )
    }

    ListenModeShell(
        navController, viewModel, ShortcutScreen.SPOKEN_LISTEN,
        titleFormatRes = R.string.deck_speaking_title_format
    ) { state, padding ->
        AutoAdvanceAfterCorrect(state, viewModel)
        val dimensions = LocalStudiareDimensions.current
        val card = state.shuffledCards[state.currentCardIndex]
        val deck = state.deckWithCards.deck
        val promptSide = state.quizPromptSide
        val answerSide = promptSide.opposite()
        val answerText = card.textForSide(answerSide)
        val answerLang = deck.languageForSide(answerSide)
        val isGraded = state.isGraded

        var revealed by remember(state.currentCardIndex) { mutableStateOf(false) } // practice only: back text shown
        var attempts by remember(state.currentCardIndex) { mutableIntStateOf(0) } // quiz only: capped at 3
        var isListening by remember(state.currentCardIndex) { mutableStateOf(false) }
        var feedback by remember(state.currentCardIndex) { mutableStateOf<String?>(null) }
        var showTypedFallback by remember(state.currentCardIndex) { mutableStateOf(false) } // quiz only

        var typedFallbackInput by remember(state.currentCardIndex) { mutableStateOf("") }

        var listenRequest by remember { mutableIntStateOf(0) }
    LaunchedEffect(state.currentCardIndex, card.id, isGraded, promptDecided, showWhisperPrompt) {
            revealed = false
            attempts = 0
            feedback = null
            showTypedFallback = false
            typedFallbackInput = ""
            // Don't say anything until the first-use Whisper prompt (and, further up the nav
            // stack before this screen ever mounted, the HD-voice download prompt) is resolved —
            // otherwise the front word can start playing just before/under that dialog.
            if (!promptDecided || showWhisperPrompt) return@LaunchedEffect
            audioController.speechRate = state.speakingFrontSpeed
            audioController.playAndAwait(card.textForSide(promptSide), card.notesForSide(promptSide), deck.languageForSide(promptSide))
            listenRequest++ // auto-listen starts once the prompt has finished
            if (!isGraded) revealed = true // practice: reveal the text once the prompt's actually finished
        }

        fun playAnswerAudio() {
            audioController.speechRate = state.speakingBackSpeed
            audioController.play(answerText, card.notesForSide(answerSide), answerLang)
        }

        // Resolved here (a @Composable context) since listenOnce() below runs inside a launched
        // coroutine, which can't call getText()/stringResource() itself.
        val listeningText = getText(R.string.listening_ellipsis)
        val notQuiteText = getText(R.string.not_quite_try_again)
        val micPermissionNeededText = getText(R.string.microphone_permission_needed)
        val speechUnavailableText = getText(R.string.speech_recognition_unavailable)
        val noSpeechText = getText(R.string.no_speech_heard_try_again)

        fun listenOnce() {
            scope.launch {
                isListening = true
                feedback = listeningText
                if (state.listenStartSound) soundPlayer.play(SoundEffect.LISTEN_START)
                val outcome = recognitionEngine.classifySpokenAttempt(answerLang, whisperSize, answerText)
                isListening = false
                when (outcome) {
                    is SpokenAnswerOutcome.Correct -> {
                        feedback = null
                        if (state.listenCorrectSound) soundPlayer.play(SoundEffect.LISTEN_CORRECT)
                        if (isGraded) viewModel.submitListenAnswer(outcome.heard, true) else viewModel.submitTypingCorrect()
                        playAnswerAudio()
                    }
                    SpokenAnswerOutcome.NoMatch -> {
                        if (state.listenIncorrectSound) soundPlayer.play(SoundEffect.LISTEN_INCORRECT)
                        if (isGraded) { attempts++; feedback = if (attempts >= 3) null else notQuiteText } else feedback = notQuiteText
                    }
                    SpokenAnswerOutcome.NoSpeech -> {
                        if (state.listenIncorrectSound) soundPlayer.play(SoundEffect.LISTEN_INCORRECT)
                        if (isGraded) { attempts++; feedback = if (attempts >= 3) null else noSpeechText } else feedback = noSpeechText
                    }
                    is SpokenAnswerOutcome.Unusable -> {
                        feedback = if (outcome.reason == RecognitionFailureReason.NO_PERMISSION) micPermissionNeededText else speechUnavailableText
                        // Quiz can't just self-mark past an unusable mic (that'd defeat the grade)
                        // — substitute a still-verifiable typed field instead. Practice already has
                        // its own always-available escape hatch ("I Said It" in the footer below).
                        if (isGraded) showTypedFallback = true
                    }
                }
            }
        }

        // Auto-listen: once the prompt has been played, the mic starts by itself (when the option is on).
        LaunchedEffect(listenRequest) {
            if (listenRequest > 0 && state.autoListen && !state.correctAnswerFound) listenOnce()
        }

        fun submitTyped() {
            val correct = AnswerMatcher.matchesTyped(typedFallbackInput, answerText)
            viewModel.submitListenAnswer(typedFallbackInput, correct)
            if (correct) playAnswerAudio()
        }

        ListenModeLayout(
            state = state,
            viewModel = viewModel,
            padding = padding,
            overrideSide = if (revealed || state.correctAnswerFound) answerSide else promptSide
        ) {
            if (isGraded) {
                if (!state.correctAnswerFound) {
                    when {
                        showTypedFallback -> {
                            OutlinedTextField(
                                value = typedFallbackInput,
                                onValueChange = { typedFallbackInput = it },
                                label = { Text(getText(R.string.type_your_answer_instead)) },
                                singleLine = true,
                                isError = state.lastIncorrectAnswer != null,
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { submitTyped() }),
                                modifier = Modifier.fillMaxWidth(0.8f)
                            )
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            Button(
                                onClick = { submitTyped() },
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) { Text(getText(R.string.submit)) }
                        }
                        !micPermissionGranted -> {
                            Text(getText(R.string.microphone_permission_needed), textAlign = TextAlign.Center)
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                                Text(getText(R.string.grant_permission))
                            }
                        }
                        attempts >= 3 -> {
                            // Matches TypingMode's escape hatch: "correct_answer_is" is reserved for
                            // once the answer is actually revealed, alongside the real text —
                            // showing it here with nothing after it would just be a lie. Point at
                            // the always-available "Get Answer" button below instead of
                            // auto-revealing, so this is a manual tap, like every other quiz mode.
                            Text(getText(R.string.out_of_attempts_tap_get_answer), textAlign = TextAlign.Center)
                        }
                        else -> {
                            FilledIconButton(
                                onClick = { listenOnce() },
                                enabled = !isListening,
                                modifier = Modifier.size(64.dp),
                                shape = CircleShape,
                                colors = if (isListening) {
                                    IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                                } else {
                                    IconButtonDefaults.filledIconButtonColors()
                                }
                            ) { Icon(Icons.Default.Mic, contentDescription = getText(R.string.tap_to_speak), modifier = Modifier.size(32.dp)) }
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            Text(feedback ?: getText(R.string.tap_to_speak), textAlign = TextAlign.Center)
                        }
                    }
                } else if (state.lastIncorrectAnswer == null) {
                    Text(getText(R.string.correct_exclamation), color = Color(0xFF22C55E), style = MaterialTheme.typography.titleLarge)
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                ListenModeFooter(
                    state = state,
                    viewModel = viewModel,
                    primaryLabel = getText(R.string.get_answer),
                    onPrimaryAction = {
                        viewModel.revealAnswer()
                        playAnswerAudio()
                    }
                )
            } else if (revealed) {
                Text(
                    getText(R.string.say_the_word_shown),
                    style = MaterialTheme.typography.bodyMedium,
                    fontStyle = FontStyle.Italic
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                TooltipIconButton(
                    description = getText(R.string.replay),
                    onClick = { audioController.play(card.textForSide(promptSide), card.notesForSide(promptSide), deck.languageForSide(promptSide)) }
                ) { Icon(Icons.Default.Replay, contentDescription = getText(R.string.replay)) }

                Spacer(Modifier.height(dimensions.spacingMedium))

                if (!state.correctAnswerFound) {
                    if (!micPermissionGranted) {
                        Text(getText(R.string.microphone_permission_needed), textAlign = TextAlign.Center)
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        OutlinedButton(onClick = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                            Text(getText(R.string.grant_permission))
                        }
                    } else {
                        FilledIconButton(
                            onClick = { listenOnce() },
                            enabled = !isListening,
                            modifier = Modifier.size(64.dp),
                            shape = CircleShape,
                            colors = if (isListening) {
                                IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.error)
                            } else {
                                IconButtonDefaults.filledIconButtonColors()
                            }
                        ) { Icon(Icons.Default.Mic, contentDescription = getText(R.string.tap_to_speak), modifier = Modifier.size(32.dp)) }
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        Text(feedback ?: getText(R.string.tap_to_speak), textAlign = TextAlign.Center)
                    }
                } else {
                    // Was silently missing — success only showed up as the footer swapping to
                    // "Next Card", with nothing confirming the mic actually heard you right.
                    Text(getText(R.string.correct_exclamation), color = Color(0xFF22C55E), style = MaterialTheme.typography.titleLarge)
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                ListenModeFooter(
                    state = state,
                    viewModel = viewModel,
                    primaryLabel = getText(R.string.i_said_it),
                    onPrimaryAction = { viewModel.submitTypingCorrect() }
                )
            }
        }
    }
}
