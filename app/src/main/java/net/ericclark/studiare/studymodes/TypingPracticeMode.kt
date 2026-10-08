package net.ericclark.studiare.studymodes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.components.typingAnswerMatches
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.draw.scale
import net.ericclark.studiare.screens.Screens.EditCardDialog
import net.ericclark.studiare.screens.Screens.StudyCompletionScreen

/**
 * The main screen for the Typing study mode.
 * Copied from TypingScoredScreen and adapted.
 */
@Composable
fun TypingScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val state = viewModel.studyState ?: return
    AutoAdvanceAfterCorrect(state, viewModel)
    val inputController = net.ericclark.studiare.components.rememberLetterInputController()
    var showEditDialog by remember { mutableStateOf(false) }
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    if (showEditDialog) {
        val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex)
        if (currentCard != null) {
            EditCardDialog(
                cardToEdit = currentCard,
                viewModel = viewModel,
                onDismiss = { showEditDialog = false }
            )
        }
    }

    if (state.isComplete) {
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
        return
    }

    // Auto-focus input
    LaunchedEffect(state.currentCardIndex) {
        if (!state.correctAnswerFound) {
            delay(300)
            inputController.show()
        }
    }

    Scaffold(
        // Tapping empty space dismisses the keyboard (taps on buttons are handled first).
        modifier = Modifier
            .imePadding()
            .pointerInput(Unit) { detectTapGestures { inputController.hide() } },
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.TYPING,
                title = { Text(stringResource(R.string.deck_typing_title_format, state.deckWithCards.deck.name)) },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = { viewModel.endStudySession(); navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                    }
                },
                actions = {
                    TooltipIconButton(description = getText(R.string.edit_card), 
                        onClick = { showEditDialog = true },
                        enabled = state.correctAnswerFound
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_card))
                    }
                }
            )
        }
    ) { padding ->
        val rootFocusRequester = remember { FocusRequester() }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .autoFocusable(rootFocusRequester)
                .onPreviewKeyEvent { event ->
                    val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex) ?: return@onPreviewKeyEvent false

                    val isHandledKeyDown = event.type == KeyEventType.KeyDown && (
                            state.correctAnswerFound && event.key in listOf(
                                Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionRight, Key.DirectionLeft,
                                Key.K, Key.U, Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
                                Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5
                            )
                            )

                    if (isHandledKeyDown) return@onPreviewKeyEvent true

                    if (event.type == KeyEventType.KeyUp) {
                        if (state.correctAnswerFound) {
                            when (event.key) {
                                Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionRight -> { viewModel.nextCard(); return@onPreviewKeyEvent true }
                                Key.DirectionLeft -> { viewModel.previousCard(); return@onPreviewKeyEvent true }
                                Key.K, Key.U -> { viewModel.toggleCardKnownStatus(currentCard); return@onPreviewKeyEvent true }
                                Key.One, Key.NumPad1 -> { if (state.schedulingMode == SchedulingMode.FSRS) viewModel.submitFsrsGrade(1) else viewModel.updateCardDifficulty(currentCard, DifficultySetting.ONE); return@onPreviewKeyEvent true }
                                Key.Two, Key.NumPad2 -> { if (state.schedulingMode == SchedulingMode.FSRS) viewModel.submitFsrsGrade(2) else viewModel.updateCardDifficulty(currentCard, DifficultySetting.TWO); return@onPreviewKeyEvent true }
                                Key.Three, Key.NumPad3 -> { if (state.schedulingMode == SchedulingMode.FSRS) viewModel.submitFsrsGrade(3) else viewModel.updateCardDifficulty(currentCard, DifficultySetting.THREE); return@onPreviewKeyEvent true }
                                Key.Four, Key.NumPad4 -> { if (state.schedulingMode == SchedulingMode.FSRS) viewModel.submitFsrsGrade(4) else viewModel.updateCardDifficulty(currentCard, DifficultySetting.FOUR); return@onPreviewKeyEvent true }
                                Key.Five, Key.NumPad5 -> { if (state.schedulingMode != SchedulingMode.FSRS) viewModel.updateCardDifficulty(currentCard, DifficultySetting.FIVE); return@onPreviewKeyEvent true }
                            }
                        }
                    }
                    false
                }
        ) {
            if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                LandscapeTypingLayout(state = state, viewModel = viewModel, inputController = inputController)
            } else {
                PortraitTypingLayout(state = state, viewModel = viewModel, inputController = inputController)
            }
        }
    }
}

@Composable
fun PortraitTypingLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    inputController: net.ericclark.studiare.components.LetterInputController
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }
    var userAnswer by remember(state.currentCardIndex) { mutableStateOf("") }
    var showWrongAnswer by remember(state.currentCardIndex) { mutableStateOf(false) }
    LaunchedEffect(userAnswer) { showWrongAnswer = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                tags = cardTags
            )
            Spacer(Modifier.height(dimensions.spacingMedium))

            TypingInteractionContent(
                state = state,
                userAnswer = userAnswer,
                onUserAnswerChange = { userAnswer = it },
                inputController = inputController,
                viewModel = viewModel,
                showWrongAnswer = showWrongAnswer
            )

            if (state.correctAnswerFound) {
                Spacer(Modifier.height(dimensions.spacingMedium))
                val card = state.shuffledCards[state.currentCardIndex]
                var difficulty by remember(card.id) { mutableStateOf(card.difficulty) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DifficultySlider(
                        label = getText(R.string.rate_difficulty),
                        difficulty = difficulty,
                        onDifficultyChange = {
                            difficulty = it
                            viewModel.updateCardDifficulty(card, it)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                        MarkKnownButton(
                            isKnown = card.isKnown,
                            onClick = { viewModel.toggleCardKnownStatus(card) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensions.spacingMedium),
            horizontalArrangement = Arrangement.Center
        ) {
            // M3 Expressive: Tactile Squish and 56dp minimum height
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

            Button(
                onClick = { if (state.correctAnswerFound) viewModel.nextCard() else submitLearnAnswer(state, userAnswer, viewModel) { showWrongAnswer = true } },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .defaultMinSize(minHeight = 56.dp)
                    .scale(nextScale),
                enabled = state.correctAnswerFound,
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                interactionSource = nextInteractionSource
            ) { Text(getText(if (state.correctAnswerFound) R.string.next_card else R.string.submit)) }
        }
    }
}

@Composable
fun LandscapeTypingLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    inputController: net.ericclark.studiare.components.LetterInputController
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }
    var userAnswer by remember(state.currentCardIndex) { mutableStateOf("") }
    var showWrongAnswer by remember(state.currentCardIndex) { mutableStateOf(false) }
    LaunchedEffect(userAnswer) { showWrongAnswer = false }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // UPDATED: Fills the left pane
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                tags = cardTags
            )
        }
        Spacer(Modifier.width(dimensions.spacingLarge))
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                TypingInteractionContent(
                    state = state,
                    userAnswer = userAnswer,
                    onUserAnswerChange = { userAnswer = it },
                    inputController = inputController,
                    viewModel = viewModel,
                showWrongAnswer = showWrongAnswer
                )

                if (state.correctAnswerFound) {
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    val card = state.shuffledCards[state.currentCardIndex]
                    var difficulty by remember(card.id) { mutableStateOf(card.difficulty) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DifficultySlider(
                            label = getText(R.string.rate_difficulty),
                            difficulty = difficulty,
                            onDifficultyChange = {
                                difficulty = it
                                viewModel.updateCardDifficulty(card, it)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                            MarkKnownButton(
                                isKnown = card.isKnown,
                                onClick = { viewModel.toggleCardKnownStatus(card) }
                            )
                        }
                    }
                }
            }

            // M3 Expressive: Tactile Squish and 56dp minimum height
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

            Button(
                onClick = { if (state.correctAnswerFound) viewModel.nextCard() else submitLearnAnswer(state, userAnswer, viewModel) { showWrongAnswer = true } },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .defaultMinSize(minHeight = 56.dp)
                    .scale(nextScale),
                enabled = state.correctAnswerFound,
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                interactionSource = nextInteractionSource
            ) { Text(getText(if (state.correctAnswerFound) R.string.next_card else R.string.submit)) }
        }
    }
}

@Composable
fun TypingInteractionContent(
    state: StudyState,
    userAnswer: String,
    onUserAnswerChange: (String) -> Unit,
    inputController: net.ericclark.studiare.components.LetterInputController,
    viewModel: FlashcardViewModel,
    showWrongAnswer: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
    val answerWithoutSpaces = remember(answerText) { answerText.replace(" ", "") }

    // Logic to handle typing and auto-completion
    val onAnswerChange = { newValue: String ->
        if (!state.correctAnswerFound) {
            val filteredValue = newValue.filter { it != ' ' }
            // Allow typing up to length of answer
            if (filteredValue.length <= answerWithoutSpaces.length) {
                onUserAnswerChange(filteredValue)

                // A correct answer is submitted as soon as the full answer is typed (auto-advance then moves on)
                if (filteredValue.length == answerWithoutSpaces.length &&
                    typingAnswerMatches(filteredValue, answerWithoutSpaces, state.typingIgnoreFormatting)
                ) {
                    viewModel.submitTypingCorrect()
                }
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        AnimatedVisibility(visible = state.correctAnswerFound) {
            Text(
                getText(R.string.correct_exclamation),
                color = Color(0xFF22C55E),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = dimensions.spacingSmall)
            )
        }

        TypingInput(
            userValue = userAnswer,
            onValueChange = onAnswerChange,
            answerText = answerText,
            inputController = inputController,
            enabled = !state.correctAnswerFound,
            onDone = {
                if (typingAnswerMatches(userAnswer, answerWithoutSpaces, state.typingIgnoreFormatting)) viewModel.submitTypingCorrect()
            },
            disableAutocorrect = state.typingDisableAutocorrect
        )
        if (showWrongAnswer && !state.correctAnswerFound) {
            Text(
                getText(R.string.try_again),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = dimensions.spacingSmall)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypingInput(
    userValue: String,
    onValueChange: (String) -> Unit,
    answerText: String,
    inputController: net.ericclark.studiare.components.LetterInputController,
    enabled: Boolean,
    onDone: () -> Unit = {},
    disableAutocorrect: Boolean = true
) {
    val dimensions = LocalStudiareDimensions.current
    // Colors for typing mode
    val correctColor = Color(0xFF22C55E)
    val incorrectColor = MaterialTheme.colorScheme.error
    // Use a distinct blue for the "filled in" but untyped letters
    val untypedColor = Color(0xFF2196F3)

    Box(
        modifier = Modifier
            .fillMaxWidth()
    ) {
        if (enabled) {
            net.ericclark.studiare.components.LetterInput(
                controller = inputController,
                onText = { typed -> onValueChange(userValue + typed) },
                onBackspace = { onValueChange(userValue.dropLast(1)) },
                onDone = onDone,
                modifier = Modifier.size(1.dp).alpha(0f),
                disableAutocorrect = disableAutocorrect
            )
        }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { inputController.show() },
                contentAlignment = Alignment.Center
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val words = answerText.split(' ')
                    var charIndex = 0

                    words.forEachIndexed { wordIndex, word ->
                        word.forEach { targetChar ->
                            val userChar = userValue.getOrNull(charIndex)

                            // Determine target color based on logic
                            val targetBoxColor = when {
                                userChar == null -> untypedColor // Not typed yet -> Primary
                                userChar.equals(targetChar, ignoreCase = true) -> correctColor // Correct -> Green
                                else -> incorrectColor // Incorrect -> Red
                            }

                            // M3 Expressive: Fluid organic springs for color transitions
                            val boxColor by androidx.compose.animation.animateColorAsState(
                                targetValue = targetBoxColor,
                                animationSpec = androidx.compose.animation.core.spring(
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                ),
                                label = "typingColorTransition"
                            )

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(dimensions.cornerRadiusSmall)
                                    )
                                    .border(
                                        BorderStroke(2.dp, boxColor),
                                        RoundedCornerShape(dimensions.cornerRadiusSmall)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = targetChar.toString().uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = boxColor
                                )
                            }
                            charIndex++
                        }
                        if (wordIndex < words.size - 1) {
                            Spacer(modifier = Modifier.width(dimensions.spacingLarge))
                        }
                    }
                }
            }
            }
}
