package net.ericclark.studiare.studymodes
import net.ericclark.studiare.components.typingAnswerMatches

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.TooltipIconButton
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.components.speech.AnswerMatcher
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * Typed-answer listening mode — Speech-to-Text (practice) and Listen & Type (quiz) collapsed into
 * one screen, since the only real difference between them is `state.isGraded`:
 * - **Practice** (`!isGraded`, "Speech-to-Text"): dictation. The back audio auto-plays right after
 *   the front — that *is* the exercise, hearing it and writing down what you heard — and stays
 *   ungraded (ordinary review bookkeeping only, same as `TypingMode.kt`'s practice screen).
 * - **Quiz** (`isGraded`, "Listen & Type"): translation/recall. Only the front plays; the back is
 *   never given as an audio hint and must be recalled from memory, graded via
 *   [FlashcardViewModel.submitListenAnswer] (Unicode-aware exact match via [AnswerMatcher], not
 *   Typing's ASCII space-stripped compare).
 *
 * Both share the same submit/reveal path (`submitListenAnswer` already branches on `isGraded`
 * internally via `processCardReview`), so there's no separate ungraded code path to maintain.
 */
@Composable
fun TypedListenScreen(navController: NavController, viewModel: FlashcardViewModel) {
    val audioController = rememberListenAudioController()

    ListenModeShell(
        navController, viewModel, ShortcutScreen.TYPED_LISTEN,
        titleFormatRes = R.string.deck_listening_title_format
    ) { state, padding ->
        ApplyAutoAdvance(state, viewModel)
        val dimensions = LocalStudiareDimensions.current
        val card = state.shuffledCards[state.currentCardIndex]
        // Per-session listening settings: playback speed and how many times a clip plays.
        audioController.speechRate = state.audioPlaybackSpeed
        audioController.replayCount = state.audioReplayCount
        val deck = state.deckWithCards.deck
        val promptSide = state.quizPromptSide
        val answerSide = promptSide.opposite()
        val answerText = card.textForSide(answerSide)
        val isGraded = state.isGraded

        var answerInput by remember(state.currentCardIndex) { mutableStateOf("") }
        val inputController = net.ericclark.studiare.components.rememberLetterInputController()
        // The answer is typed into the same input as Typing, which opens the keyboard when the card loads
        LaunchedEffect(state.currentCardIndex, state.correctAnswerFound) {
            if (!state.correctAnswerFound) inputController.show()
        }

        // Practice: the back audio IS the prompt (dictation) — auto-play it right after the
        // front, same as the original Speech-to-Text always did. Quiz: only the front plays; the
        // back must be recalled from memory (Listen & Type never gives it as an audio hint).
        // playAndAwait (not play() + a guessed delay) waits for the front clip to actually finish
        // before starting the back one — a fixed delay either cut the front off early or left
        // dead air, and either way the second speak() call would preempt the first mid-sentence
        // since they share one underlying engine.
        LaunchedEffect(state.currentCardIndex, card.id, isGraded) {
            audioController.playAndAwait(card.textForSide(promptSide), card.notesForSide(promptSide), deck.languageForSide(promptSide))
            if (!isGraded && !state.correctAnswerFound) {
                audioController.playAndAwait(answerText, card.notesForSide(answerSide), deck.languageForSide(answerSide))
            }
        }

        fun playAnswerAudio() {
            audioController.play(answerText, card.notesForSide(answerSide), deck.languageForSide(answerSide))
        }

        fun replay() {
            // Replays whatever audio *is* the exercise: the dictation target in practice, the
            // prompt cue in quiz.
            if (isGraded) {
                audioController.play(card.textForSide(promptSide), card.notesForSide(promptSide), deck.languageForSide(promptSide))
            } else {
                playAnswerAudio()
            }
        }

        fun submit(answer: String = answerInput) {
            val correct = typingAnswerMatches(answer, answerText, state.typingIgnoreFormatting)
            viewModel.submitListenAnswer(answer, correct)
            if (correct) playAnswerAudio()
        }

        // Typing the last letter of a correct answer submits it straight away, same as Scored Typing.
        // A wrong answer waits for Submit or Enter. The auto-advance option decides whether it then moves on.
        fun onAnswerTyped(typed: String) {
            val letterCount = answerText.count { !it.isWhitespace() }
            val next = typed.filter { !it.isWhitespace() }.take(letterCount)
            answerInput = next
            if (next.length == letterCount && typingAnswerMatches(next, answerText, state.typingIgnoreFormatting)) {
                submit(next)
            }
        }

        ListenModeLayout(
            state = state,
            viewModel = viewModel,
            padding = padding,
            overrideSide = if (state.correctAnswerFound) answerSide else promptSide
        ) {
            TooltipIconButton(
                description = getText(R.string.replay),
                onClick = { replay() }
            ) { Icon(Icons.Default.Replay, contentDescription = getText(R.string.replay)) }

            Spacer(Modifier.height(dimensions.spacingMedium))

            if (!state.correctAnswerFound) {
                // Same input as Typing: letter boxes with the length hint, the centred outlined box without it
                TypingScoredInput(
                    value = answerInput,
                    onValueChange = { typed -> onAnswerTyped(typed) },
                    answerText = answerText,
                    isError = state.lastIncorrectAnswer != null,
                    inputController = inputController,
                    onSubmit = { submit() },
                    showCorrectLetters = state.showCorrectLetters,
                    correctAnswer = answerText,
                    enabled = true,
                    showLengthHint = state.typingShowLengthHint,
                    disableAutocorrect = state.typingDisableAutocorrect
                )
                if (state.lastIncorrectAnswer != null) {
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    val message = if (state.lastIncorrectAnswer.isNotEmpty()) {
                        stringResource(R.string.incorrect_guess_feedback_format, state.lastIncorrectAnswer)
                    } else {
                        getText(R.string.try_again_reveal_feedback)
                    }
                    Text(message, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                }
                Spacer(Modifier.height(dimensions.spacingSmall))
                Button(
                    onClick = { submit() },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) { Text(getText(R.string.submit)) }
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
        }
    }
}
