package net.ericclark.studiare.studymodes

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.components.typingAnswerMatches
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.ui.draw.scale

/**
 * The bottom button in Typing's graded/scored screen, which is either "Submit" or "Next Card".
 * @param state The current study state.
 * @param viewModel The ViewModel providing business logic.
 * @param onSubmit Callback for the submit action.
 */
@Composable
fun TypingScoredBottomButton(state: StudyState, viewModel: FlashcardViewModel, onSubmit: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current

    val nextInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isNextPressed by nextInteractionSource.collectIsPressedAsState()
    val nextScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isNextPressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "nextButtonSquish"
    )

    if (state.correctAnswerFound) {
        Button(
            onClick = { viewModel.nextCard() },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .defaultMinSize(minHeight = 56.dp)
                .scale(nextScale),
            shape = RoundedCornerShape(dimensions.cornerRadiusButton),
            interactionSource = nextInteractionSource
        ) { Text(getText(R.string.next_card)) }
    } else {
        Row(
            modifier = Modifier.fillMaxWidth(0.8f),
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
        ) {
            OutlinedButton(
                onClick = { viewModel.revealAnswer() },
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) { Text(getText(R.string.get_answer)) }
            Button(
                onClick = onSubmit,
                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) { Text(getText(R.string.submit)) }
        }
    }
}

/**
 * A custom input field for Typing's graded/scored screen, displayed as a series of character boxes.
 * @param value The current input value.
 * @param onValueChange Callback for when the input value changes.
 * @param answerText The correct answer text, used to determine the number of boxes.
 * @param isError Whether the input is currently in an error state.
 * @param focusRequester The FocusRequester for the input field.
 * @param onSubmit Callback for when the user submits their answer.
 * @param showCorrectLetters Whether to show real-time feedback for each letter.
 * @param correctAnswer The correct answer string for comparison.
 * @param enabled Controls if the text field can be interacted with.
 */
/**
 * The scored typing input with the length hint off: a standard outlined text box that shows the typed
 * letters with the same spacing as the boxes, and has no blanks for the letters still to type.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun TypingPlainTextBox(
    value: String,
    isError: Boolean,
    showCorrectLetters: Boolean,
    correctChars: String
) {
    val dimensions = LocalStudiareDimensions.current
    val borderColor = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    val correctColor = Color(0xFF22C55E)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .border(BorderStroke(1.dp, borderColor), RoundedCornerShape(dimensions.cornerRadiusSmall))
            .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
        contentAlignment = Alignment.Center
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.Center
        ) {
            value.forEachIndexed { index, letter ->
                // With correct letters on: green where the letter matches the answer at that position, red where it doesn't.
                val textColor = when {
                    !showCorrectLetters -> LocalContentColor.current
                    letter.lowercaseChar() == correctChars.getOrNull(index) -> correctColor
                    else -> MaterialTheme.colorScheme.error
                }
                Text(
                    text = letter.uppercase(),
                    style = MaterialTheme.typography.headlineSmall,
                    color = textColor,
                    modifier = Modifier.width(32.dp).padding(horizontal = 2.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/** Learn's Submit: a correct typed answer moves on; a wrong one is flagged and can be tried again. */
internal fun submitLearnAnswer(state: StudyState, userAnswer: String, viewModel: FlashcardViewModel, onWrong: () -> Unit) {
    val card = state.shuffledCards.getOrNull(state.currentCardIndex) ?: return
    val answer = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
    if (typingAnswerMatches(userAnswer, answer, state.typingIgnoreFormatting)) viewModel.submitTypingCorrect() else onWrong()
}
