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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.draw.scale

/**
 * The main screen for the Quiz study mode.
 * @param navController The NavController for navigating back.
 * @param viewModel The ViewModel providing the study state.
 */
@Composable
fun QuizScreen(
    navController: NavController,
    viewModel: FlashcardViewModel,
    windowWidthSizeClass: WindowWidthSizeClass,
    windowHeightSizeClass: WindowHeightSizeClass
) {
    val state = viewModel.studyState ?: return
    val focusRequester = remember { FocusRequester() }
    var showEditDialog by remember { mutableStateOf(false) }

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

    // Request focus on the input field when the card changes or screen is first shown
    LaunchedEffect(state.currentCardIndex) {
        if (!state.correctAnswerFound) {
            delay(300) // Delay to allow UI to settle
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(), // Adjust for the on-screen keyboard
        topBar = {
            CustomTopAppBar(
                title = { Text(stringResource(R.string.deck_quiz_title_format, state.deckWithCards.deck.name)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.Default.ArrowBack, getText(R.string.back)) }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = state.correctAnswerFound
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_card))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                LandscapeQuizLayout(state = state, viewModel = viewModel, focusRequester = focusRequester)
            } else {
                PortraitQuizLayout(state = state, viewModel = viewModel, focusRequester = focusRequester)
            }
        }
    }
}

/**
 * The portrait layout for the Quiz study screen.
 * @param state The current study state.
 * @param viewModel The ViewModel providing business logic.
 * @param focusRequester The FocusRequester for the input field.
 */
@Composable
fun PortraitQuizLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    focusRequester: FocusRequester
) {
    val dimensions = LocalStudiareDimensions.current
    var userAnswer by remember(state.currentCardIndex, state.lastIncorrectAnswer) { mutableStateOf(state.lastIncorrectAnswer ?: "") }
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    // Animation Scope
    val scope = rememberCoroutineScope()
    var processingClick by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentCardIndex) {
        processingClick = false
    }

    val submitAction = {
        val answerWithoutSpaces = answerText.replace(" ", "")
        if (userAnswer.length == answerWithoutSpaces.length && !state.correctAnswerFound) {
            viewModel.submitQuizAnswer(userAnswer)
        }
    }

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
            QuizInteractionContent(
                state = state,
                userAnswer = userAnswer,
                onUserAnswerChange = { userAnswer = it },
                focusRequester = focusRequester,
                onSubmit = submitAction,
                viewModel = viewModel
            )
            if (state.correctAnswerFound) {
                Spacer(Modifier.height(dimensions.spacingMedium))
                var difficulty by remember(card) { mutableStateOf(card.difficulty) }
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
            // --- FSRS LOGIC ---
            androidx.compose.animation.AnimatedContent(
                targetState = state.correctAnswerFound,
                transitionSpec = {
                    val springSpec = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    )
                    (androidx.compose.animation.slideInVertically(animationSpec = springSpec, initialOffsetY = { it }) +
                            androidx.compose.animation.fadeIn() +
                            androidx.compose.animation.expandVertically()).togetherWith(
                        androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) +
                                androidx.compose.animation.fadeOut() +
                                androidx.compose.animation.shrinkVertically()
                    )
                },
                label = "quizBottomAnim",
                contentAlignment = Alignment.Center
            ) { isRevealed ->
                if (state.schedulingMode == SchedulingMode.FSRS && isRevealed) {
                    val isWrong = state.incorrectCardIds.contains(card.id)

                    if (!isWrong) {
                        // Correct: Show Grading Buttons (Hard/Good/Easy)
                        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall), modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall), modifier = Modifier.fillMaxWidth()) {
                                // M3 Expressive: Added Squish, 56dp minimum height, and explicit contrast colors
                                val hardInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isHardPressed by hardInteractionSource.collectIsPressedAsState()
                                val hardScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isHardPressed) 0.95f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "hardSquish")

                                Button(
                                    onClick = {
                                        if(!processingClick) {
                                            processingClick = true
                                            scope.launch { delay(150); viewModel.submitFsrsGrade(2) }
                                        }
                                    }, // Hard
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xfffcba03), contentColor = Color.Black),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).scale(hardScale),
                                    enabled = !processingClick,
                                    interactionSource = hardInteractionSource,
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = state.nextIntervals[2] ?: "", style = MaterialTheme.typography.labelSmall)
                                        Text(getText(R.string.rating_hard))
                                    }
                                }

                                val goodInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isGoodPressed by goodInteractionSource.collectIsPressedAsState()
                                val goodScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isGoodPressed) 0.95f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "goodSquish")

                                Button(
                                    onClick = {
                                        if(!processingClick) {
                                            processingClick = true
                                            scope.launch { delay(150); viewModel.submitFsrsGrade(3) }
                                        }
                                    }, // Good
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xff488c4b), contentColor = Color.White),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).scale(goodScale),
                                    enabled = !processingClick,
                                    interactionSource = goodInteractionSource,
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = state.nextIntervals[3] ?: "", style = MaterialTheme.typography.labelSmall)
                                        Text(getText(R.string.rating_good))
                                    }
                                }

                                val easyInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isEasyPressed by easyInteractionSource.collectIsPressedAsState()
                                val easyScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isEasyPressed) 0.95f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "easySquish")

                                Button(
                                    onClick = {
                                        if(!processingClick) {
                                            processingClick = true
                                            scope.launch { delay(150); viewModel.submitFsrsGrade(4) }
                                        }
                                    }, // Easy
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xff4287f5), contentColor = Color.White),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).scale(easyScale),
                                    enabled = !processingClick,
                                    interactionSource = easyInteractionSource,
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = state.nextIntervals[4] ?: "", style = MaterialTheme.typography.labelSmall)
                                        Text(getText(R.string.rating_easy))
                                    }
                                }
                            }
                        }
                    } else {
                        // Incorrect (FSRS): Show "Next Card" button
                        QuizBottomButton(state = state, viewModel = viewModel, onSubmit = submitAction)
                    }
                } else {
                    // Normal Mode
                    QuizBottomButton(state = state, viewModel = viewModel, onSubmit = submitAction)
                }
            }
        }
    }
}

@Composable
fun LandscapeQuizLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    focusRequester: FocusRequester
) {
    val dimensions = LocalStudiareDimensions.current
    var userAnswer by remember(state.currentCardIndex, state.lastIncorrectAnswer) { mutableStateOf(state.lastIncorrectAnswer ?: "") }
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
    var difficulty by remember(card) { mutableStateOf(card.difficulty) }

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    // Animation Scope
    val scope = rememberCoroutineScope()
    var processingClick by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentCardIndex) {
        processingClick = false
    }

    val submitAction = {
        val answerWithoutSpaces = answerText.replace(" ", "")
        if (userAnswer.length == answerWithoutSpaces.length && !state.correctAnswerFound) {
            viewModel.submitQuizAnswer(userAnswer)
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left column for the card prompt
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                tags = cardTags
            )
        }
        Spacer(Modifier.width(dimensions.spacingLarge))
        // Right column for the input and controls
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
                QuizInteractionContent(
                    state = state,
                    userAnswer = userAnswer,
                    onUserAnswerChange = { userAnswer = it },
                    focusRequester = focusRequester,
                    onSubmit = submitAction,
                    viewModel = viewModel
                )
                if (state.correctAnswerFound) {
                    Spacer(Modifier.height(dimensions.spacingMedium))
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

            // --- FSRS LOGIC ---
            androidx.compose.animation.AnimatedContent(
                targetState = state.correctAnswerFound,
                transitionSpec = {
                    val springSpec = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    )
                    (androidx.compose.animation.slideInVertically(animationSpec = springSpec, initialOffsetY = { it }) +
                            androidx.compose.animation.fadeIn() +
                            androidx.compose.animation.expandVertically()).togetherWith(
                        androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) +
                                androidx.compose.animation.fadeOut() +
                                androidx.compose.animation.shrinkVertically()
                    )
                },
                label = "quizBottomAnim",
                contentAlignment = Alignment.Center
            ) { isRevealed ->
                if (state.schedulingMode == SchedulingMode.FSRS && isRevealed) {
                    val isWrong = state.incorrectCardIds.contains(card.id)
                    if (!isWrong) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall), modifier = Modifier.fillMaxWidth()) {
                            Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall), modifier = Modifier.fillMaxWidth()) {
                                // M3 Expressive: Added Squish, 56dp minimum height, and explicit contrast colors
                                val hardInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isHardPressed by hardInteractionSource.collectIsPressedAsState()
                                val hardScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isHardPressed) 0.95f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "hardSquish")

                                Button(
                                    onClick = {
                                        if(!processingClick) {
                                            processingClick = true
                                            scope.launch { delay(150); viewModel.submitFsrsGrade(2) }
                                        }
                                    }, // Hard
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xfffcba03), contentColor = Color.Black),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).scale(hardScale),
                                    enabled = !processingClick,
                                    interactionSource = hardInteractionSource,
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = state.nextIntervals[2] ?: "", style = MaterialTheme.typography.labelSmall)
                                        Text(getText(R.string.rating_hard))
                                    }
                                }

                                val goodInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isGoodPressed by goodInteractionSource.collectIsPressedAsState()
                                val goodScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isGoodPressed) 0.95f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "goodSquish")

                                Button(
                                    onClick = {
                                        if(!processingClick) {
                                            processingClick = true
                                            scope.launch { delay(150); viewModel.submitFsrsGrade(3) }
                                        }
                                    }, // Good
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xff488c4b), contentColor = Color.White),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).scale(goodScale),
                                    enabled = !processingClick,
                                    interactionSource = goodInteractionSource,
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = state.nextIntervals[3] ?: "", style = MaterialTheme.typography.labelSmall)
                                        Text(getText(R.string.rating_good))
                                    }
                                }

                                val easyInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isEasyPressed by easyInteractionSource.collectIsPressedAsState()
                                val easyScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isEasyPressed) 0.95f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "easySquish")

                                Button(
                                    onClick = {
                                        if(!processingClick) {
                                            processingClick = true
                                            scope.launch { delay(150); viewModel.submitFsrsGrade(4) }
                                        }
                                    }, // Easy
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xff4287f5), contentColor = Color.White),
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).scale(easyScale),
                                    enabled = !processingClick,
                                    interactionSource = easyInteractionSource,
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(text = state.nextIntervals[4] ?: "", style = MaterialTheme.typography.labelSmall)
                                        Text(getText(R.string.rating_easy))
                                    }
                                }
                            }
                        }
                    } else {
                        QuizBottomButton(state = state, viewModel = viewModel, onSubmit = submitAction)
                    }
                } else {
                    QuizBottomButton(state = state, viewModel = viewModel, onSubmit = submitAction)
                }
            }
        }
    }
}

/**
 * The interactive content area in Quiz mode, including the input field and feedback messages.
 * @param state The current study state.
 * @param userAnswer The user's current input.
 * @param onUserAnswerChange Callback for when the user's input changes.
 * @param focusRequester The FocusRequester for the input field.
 * @param onSubmit Callback for when the user submits their answer.
 */
@Composable
fun QuizInteractionContent(
    state: StudyState,
    userAnswer: String,
    onUserAnswerChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    viewModel: FlashcardViewModel
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
    val answerNotes = if (state.quizPromptSide == CardSide.FRONT) card.backNotes else card.frontNotes
    val answerWithoutSpaces = remember(answerText) { answerText.replace(" ", "") }

    var cachedAnswerText by remember { mutableStateOf("") }
    var cachedAnswerNotes by remember { mutableStateOf<String?>(null) }

    if (state.correctAnswerFound) {
        cachedAnswerText = answerText
        cachedAnswerNotes = answerNotes
    }

    val onAnswerChangeWithAutoSubmit = { newValue: String ->
        if (!state.correctAnswerFound) {
            val filteredValue = newValue.filter { it != ' ' }
            if (filteredValue.length <= answerWithoutSpaces.length) {
                onUserAnswerChange(filteredValue) // Update parent state

                // Check for auto-submit
                if (filteredValue.length == answerWithoutSpaces.length &&
                    filteredValue.equals(answerWithoutSpaces, ignoreCase = true)
                ) {
                    viewModel.submitQuizAnswer(filteredValue)
                }
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        // Show feedback when the answer is correct
        AnimatedVisibility(visible = state.correctAnswerFound) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.lastIncorrectAnswer == null) {
                    Text(getText(R.string.correct_exclamation), color = Color(0xFF22C55E), style = MaterialTheme.typography.titleLarge)
                }
                Text(getText(R.string.correct_answer_is), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = dimensions.spacingSmall))

                Text(cachedAnswerText, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(dimensions.spacingSmall))

                if (!cachedAnswerNotes.isNullOrBlank()) {
                    Text(text = "($cachedAnswerNotes)", fontSize = 16.sp, fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.height(dimensions.spacingSmall))
            }
        }

        // Always show the input field, but disable it and fill with the correct answer when found.
        QuizInput(
            value = if (state.correctAnswerFound) cachedAnswerText.replace(" ", "") else userAnswer,
            onValueChange = onAnswerChangeWithAutoSubmit,
            answerText = if (state.correctAnswerFound) cachedAnswerText else answerText,
            isError = state.lastIncorrectAnswer != null && !state.correctAnswerFound,
            focusRequester = focusRequester,
            onSubmit = onSubmit,
            showCorrectLetters = state.showCorrectLetters,
            correctAnswer = if (state.correctAnswerFound) cachedAnswerText else answerText,
            enabled = !state.correctAnswerFound
        )

        // Show an error message if the last answer was incorrect
        if (state.lastIncorrectAnswer != null && !state.correctAnswerFound) {
            val message = if (state.lastIncorrectAnswer.isNotEmpty()) {
                stringResource(R.string.incorrect_guess_feedback_format, state.lastIncorrectAnswer)
            } else {
                getText(R.string.try_again_reveal_feedback)
            }
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = dimensions.spacingSmall),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * The bottom button in Quiz mode, which is either "Submit" or "Next Card".
 * @param state The current study state.
 * @param viewModel The ViewModel providing business logic.
 * @param onSubmit Callback for the submit action.
 */
@Composable
fun QuizBottomButton(state: StudyState, viewModel: FlashcardViewModel, onSubmit: () -> Unit) {
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
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            interactionSource = nextInteractionSource
        ) { Text(getText(R.string.next_card)) }
    } else {
        Button(
            onClick = { viewModel.revealQuizAnswer() },
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .defaultMinSize(minHeight = 56.dp)
                .scale(nextScale),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            interactionSource = nextInteractionSource
        ) { Text(getText(R.string.get_answer)) }
    }
}

/**
 * A custom input field for the Quiz mode, displayed as a series of character boxes.
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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QuizInput(
    value: String,
    onValueChange: (String) -> Unit,
    answerText: String,
    isError: Boolean,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    showCorrectLetters: Boolean,
    correctAnswer: String,
    enabled: Boolean
) {
    val dimensions = LocalStudiareDimensions.current
    val errorColor = MaterialTheme.colorScheme.error
    val correctColor = Color(0xFF22C55E)
    val defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
    val answerWithoutSpaces = remember(answerText) { answerText.replace(" ", "") }
    val correctAnswerChars = remember(correctAnswer) { correctAnswer.replace(" ", "").lowercase() }

    BasicTextField(
        value = value,
        onValueChange = {
            onValueChange(it)
        },
        enabled = enabled,
        modifier = Modifier
            .focusRequester(focusRequester)
            .onKeyEvent {
                if (it.key == Key.Enter) {
                    onSubmit()
                    true
                } else {
                    false
                }
            },
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { onSubmit() }),
        decorationBox = {
            FlowRow(
                horizontalArrangement = Arrangement.Center,
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                modifier = Modifier.fillMaxWidth()
            ) {
                val words = answerText.split(' ')
                var charIndex = 0
                words.forEachIndexed { wordIndex, word ->
                    word.forEach {
                        val char = value.getOrNull(charIndex)
                        val targetBorderColor = when {
                            !enabled -> correctColor
                            showCorrectLetters && char != null -> {
                                if (char.lowercaseChar() == correctAnswerChars.getOrNull(charIndex)) {
                                    correctColor
                                } else {
                                    errorColor
                                }
                            }
                            isError -> errorColor
                            else -> defaultColor
                        }

                        // M3 Expressive: Fluid organic springs for color transitions
                        val borderColor by androidx.compose.animation.animateColorAsState(
                            targetValue = targetBorderColor,
                            animationSpec = androidx.compose.animation.core.spring(
                                stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                            ),
                            label = "quizBorderColorTransition"
                        )

                        val backgroundColor = if (!enabled) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(40.dp)
                                .background(backgroundColor, RoundedCornerShape(dimensions.cornerRadiusSmall))
                                .border(
                                    BorderStroke(1.dp, borderColor),
                                    RoundedCornerShape(dimensions.cornerRadiusSmall)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (char != null) {
                                val targetTextColor = when {
                                    !enabled -> correctColor
                                    isError -> errorColor
                                    else -> LocalContentColor.current
                                }
                                val textColor by androidx.compose.animation.animateColorAsState(
                                    targetValue = targetTextColor,
                                    animationSpec = androidx.compose.animation.core.spring(
                                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                    ),
                                    label = "quizTextColorTransition"
                                )

                                Text(
                                    text = char.toString().uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = textColor
                                )
                            }
                        }
                        charIndex++
                    }

                    if (wordIndex < words.size - 1) {
                        Spacer(modifier = Modifier.width(dimensions.spacingMedium))
                    }
                }
            }
        }
    )
}

/**
 * The main screen for the Typing study mode.
 * Copied from QuizScreen and adapted.
 */
@Composable
fun TypingScreen(
    navController: NavController,
    viewModel: FlashcardViewModel,
    windowWidthSizeClass: WindowWidthSizeClass,
    windowHeightSizeClass: WindowHeightSizeClass
) {
    val state = viewModel.studyState ?: return
    val focusRequester = remember { FocusRequester() }
    var showEditDialog by remember { mutableStateOf(false) }

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
            focusRequester.requestFocus()
        }
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            CustomTopAppBar(
                title = { Text(stringResource(R.string.deck_typing_title_format, state.deckWithCards.deck.name)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.Default.ArrowBack, getText(R.string.back)) }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = state.correctAnswerFound
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_card))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                LandscapeTypingLayout(state = state, viewModel = viewModel, focusRequester = focusRequester)
            } else {
                PortraitTypingLayout(state = state, viewModel = viewModel, focusRequester = focusRequester)
            }
        }
    }
}

@Composable
fun PortraitTypingLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    focusRequester: FocusRequester
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }
    var userAnswer by remember(state.currentCardIndex) { mutableStateOf("") }

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
                focusRequester = focusRequester,
                viewModel = viewModel
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
                onClick = { viewModel.nextCard() },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .defaultMinSize(minHeight = 56.dp)
                    .scale(nextScale),
                enabled = state.correctAnswerFound,
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                interactionSource = nextInteractionSource
            ) { Text(getText(R.string.next_card)) }
        }
    }
}

@Composable
fun LandscapeTypingLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    focusRequester: FocusRequester
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }
    var userAnswer by remember(state.currentCardIndex) { mutableStateOf("") }

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
                    focusRequester = focusRequester,
                    viewModel = viewModel
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
                onClick = { viewModel.nextCard() },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .defaultMinSize(minHeight = 56.dp)
                    .scale(nextScale),
                enabled = state.correctAnswerFound,
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                interactionSource = nextInteractionSource
            ) { Text(getText(R.string.next_card)) }
        }
    }
}

@Composable
fun TypingInteractionContent(
    state: StudyState,
    userAnswer: String,
    onUserAnswerChange: (String) -> Unit,
    focusRequester: FocusRequester,
    viewModel: FlashcardViewModel
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

                // Check correctness
                if (filteredValue.length == answerWithoutSpaces.length &&
                    filteredValue.equals(answerWithoutSpaces, ignoreCase = true)
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
            focusRequester = focusRequester,
            enabled = !state.correctAnswerFound
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TypingInput(
    userValue: String,
    onValueChange: (String) -> Unit,
    answerText: String,
    focusRequester: FocusRequester,
    enabled: Boolean
) {
    val dimensions = LocalStudiareDimensions.current
    // Colors for typing mode
    val correctColor = Color(0xFF22C55E)
    val incorrectColor = MaterialTheme.colorScheme.error
    // Use a distinct blue for the "filled in" but untyped letters
    val untypedColor = Color(0xFF2196F3)

    BasicTextField(
        value = userValue,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier
            .focusRequester(focusRequester)
            .fillMaxWidth(), // Ensure the input takes full width for easier tapping
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { focusRequester.requestFocus() },
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
    )
}