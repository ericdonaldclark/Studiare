package net.ericclark.studiare.studymodes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
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
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*

/**
 * The main screen for the Quiz study mode.
 * @param navController The NavController for navigating back.
 * @param viewModel The ViewModel providing the study state.
 */
@Composable
fun QuizScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
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
        // When the quiz is done, the keyboard is no longer needed.
        // The StudyCompletionScreen does not have a text field, so the keyboard will hide automatically.
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
                title = { Text("${state.deckWithCards.deck.name} - Quiz") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = state.correctAnswerFound
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Card")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val isWideScreen =this.maxWidth > 600.dp
            if (isWideScreen) {
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
    state: net.ericclark.studiare.data.StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    focusRequester: FocusRequester
) {
    var userAnswer by remember(state.currentCardIndex, state.lastIncorrectAnswer) { mutableStateOf(state.lastIncorrectAnswer ?: "") }
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == "Front") card.back else card.front

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
            .padding(16.dp),
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
                viewModel = viewModel
            )
            Spacer(Modifier.height(16.dp))
            QuizInteractionContent(
                state = state,
                userAnswer = userAnswer,
                onUserAnswerChange = { userAnswer = it },
                focusRequester = focusRequester,
                onSubmit = submitAction,
                viewModel = viewModel
            )
            if (state.correctAnswerFound) {
                Spacer(Modifier.height(16.dp))
                var difficulty by remember(card) { mutableStateOf(card.difficulty) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DifficultySlider(
                        label = "Rate Difficulty",
                        difficulty = difficulty,
                        onDifficultyChange = {
                            difficulty = it
                            viewModel.updateCardDifficulty(card, it)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    MarkKnownButton(
                        isKnown = card.isKnown,
                        onClick = { viewModel.toggleCardKnownStatus(card) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // --- FSRS LOGIC ---
            if (state.schedulingMode == "Spaced Repetition" && state.correctAnswerFound) {
                val isWrong = state.incorrectCardIds.contains(card.id)

                if (!isWrong) {
                    // Correct: Show Grading Buttons (Hard/Good/Easy)
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = {
                                    if(!processingClick) {
                                        processingClick = true
                                        scope.launch { delay(150); viewModel.submitFsrsGrade(2) }
                                    }
                                }, // Hard
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xfffcba03)),
                                modifier = Modifier.weight(1f),
                                enabled = !processingClick
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = state.nextIntervals[2] ?: "", style = MaterialTheme.typography.labelSmall)
                                    Text("Hard")
                                }
                            }
                            Button(
                                onClick = {
                                    if(!processingClick) {
                                        processingClick = true
                                        scope.launch { delay(150); viewModel.submitFsrsGrade(3) }
                                    }
                                }, // Good
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff488c4b)),
                                modifier = Modifier.weight(1f),
                                enabled = !processingClick
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = state.nextIntervals[3] ?: "", style = MaterialTheme.typography.labelSmall)
                                    Text("Good")
                                }
                            }
                            Button(
                                onClick = {
                                    if(!processingClick) {
                                        processingClick = true
                                        scope.launch { delay(150); viewModel.submitFsrsGrade(4) }
                                    }
                                }, // Easy
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff4287f5)),
                                modifier = Modifier.weight(1f),
                                enabled = !processingClick
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = state.nextIntervals[4] ?: "", style = MaterialTheme.typography.labelSmall)
                                    Text("Easy")
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

@Composable
fun LandscapeQuizLayout(
    state: net.ericclark.studiare.data.StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    focusRequester: FocusRequester
) {
    var userAnswer by remember(state.currentCardIndex, state.lastIncorrectAnswer) { mutableStateOf(state.lastIncorrectAnswer ?: "") }
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == "Front") card.back else card.front
    var difficulty by remember(card) { mutableStateOf(card.difficulty) }

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
            .padding(16.dp),
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
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(16.dp))
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
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DifficultySlider(
                            label = "Rate Difficulty",
                            difficulty = difficulty,
                            onDifficultyChange = {
                                difficulty = it
                                viewModel.updateCardDifficulty(card, it)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MarkKnownButton(
                            isKnown = card.isKnown,
                            onClick = { viewModel.toggleCardKnownStatus(card) }
                        )
                    }
                }
            }

            // --- FSRS LOGIC ---
            if (state.schedulingMode == "Spaced Repetition" && state.correctAnswerFound) {
                val isWrong = state.incorrectCardIds.contains(card.id)
                if (!isWrong) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(
                                onClick = { if(!processingClick) { processingClick = true; scope.launch { delay(150); viewModel.submitFsrsGrade(2) } } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xfffcba03)), modifier = Modifier.weight(1f), enabled = !processingClick
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(text = state.nextIntervals[2] ?: "", style = MaterialTheme.typography.labelSmall); Text("Hard") }
                            }
                            Button(
                                onClick = { if(!processingClick) { processingClick = true; scope.launch { delay(150); viewModel.submitFsrsGrade(3) } } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff488c4b)), modifier = Modifier.weight(1f), enabled = !processingClick
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(text = state.nextIntervals[3] ?: "", style = MaterialTheme.typography.labelSmall); Text("Good") }
                            }
                            Button(
                                onClick = { if(!processingClick) { processingClick = true; scope.launch { delay(150); viewModel.submitFsrsGrade(4) } } },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff4287f5)), modifier = Modifier.weight(1f), enabled = !processingClick
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(text = state.nextIntervals[4] ?: "", style = MaterialTheme.typography.labelSmall); Text("Easy") }
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
    state: net.ericclark.studiare.data.StudyState,
    userAnswer: String,
    onUserAnswerChange: (String) -> Unit,
    focusRequester: FocusRequester,
    onSubmit: () -> Unit,
    viewModel: net.ericclark.studiare.FlashcardViewModel
) {
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == "Front") card.back else card.front
    val answerNotes = if (state.quizPromptSide == "Front") card.backNotes else card.frontNotes
    val answerWithoutSpaces = remember(answerText) { answerText.replace(" ", "") }

    // --- NEW: Cache the correct answer to prevent "next card" spoiler during transition ---
    var cachedAnswerText by remember { mutableStateOf("") }
    var cachedAnswerNotes by remember { mutableStateOf<String?>(null) }

    // Only update the cached text when the answer is actually found.
    // When moving to the next card (correctAnswerFound becomes false), this retains the OLD answer
    // allowing the exit animation to show the previous card, not the new one.
    if (state.correctAnswerFound) {
        cachedAnswerText = answerText
        cachedAnswerNotes = answerNotes
    }
    // -------------------------------------------------------------------------------------

    // New lambda to handle value changes and trigger auto-submit
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
                    Text("Correct!", color = Color(0xFF22C55E), style = MaterialTheme.typography.titleLarge)
                }
                Text("The correct answer is:", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))

                // UPDATED: Use cachedAnswerText to prevent flashing new answer during fade-out
                Text(cachedAnswerText, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(8.dp))

                // UPDATED: Use cachedAnswerNotes
                if (!cachedAnswerNotes.isNullOrBlank()) {
                    Text(text = "($cachedAnswerNotes)", fontSize = 16.sp, fontStyle = FontStyle.Italic)
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // Always show the input field, but disable it and fill with the correct answer when found.
        QuizInput(
            // UPDATED: If found, use the cached (frozen) answer. If typing, use the live answer.
            value = if (state.correctAnswerFound) cachedAnswerText.replace(" ", "") else userAnswer,
            onValueChange = onAnswerChangeWithAutoSubmit,
            answerText = if (state.correctAnswerFound) cachedAnswerText else answerText, // UPDATED
            isError = state.lastIncorrectAnswer != null && !state.correctAnswerFound,
            focusRequester = focusRequester,
            onSubmit = onSubmit,
            showCorrectLetters = state.showCorrectLetters,
            correctAnswer = if (state.correctAnswerFound) cachedAnswerText else answerText, // UPDATED
            enabled = !state.correctAnswerFound
        )

        // Show an error message if the last answer was incorrect
        if (state.lastIncorrectAnswer != null && !state.correctAnswerFound) {
            val message = if (state.lastIncorrectAnswer.isNotEmpty()) {
                "\"${state.lastIncorrectAnswer}\" is incorrect. Try again or tap the eye icon to reveal the answer."
            } else {
                "Try again or tap the eye icon to reveal the answer."
            }
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 8.dp),
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
fun QuizBottomButton(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel, onSubmit: () -> Unit) {
    if (state.correctAnswerFound) {
        Button(
            onClick = { viewModel.nextCard() },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) { Text("Next Card") }
    } else {
        Button(
            onClick = { viewModel.revealQuizAnswer() },
            modifier = Modifier.fillMaxWidth(0.8f)
        ) { Text("Get Answer") }
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
    val errorColor = MaterialTheme.colorScheme.error
    val correctColor = Color(0xFF22C55E)
    val defaultColor = MaterialTheme.colorScheme.onSurfaceVariant
    val answerWithoutSpaces = remember(answerText) { answerText.replace(" ", "") }
    val correctAnswerChars = remember(correctAnswer) { correctAnswer.replace(" ", "").lowercase() }

    BasicTextField(
        value = value,
        onValueChange = {
            // Pass the raw value up to the handler in QuizInteractionContent
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
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val words = answerText.split(' ')
                var charIndex = 0
                words.forEachIndexed { wordIndex, word ->
                    word.forEach {
                        val char = value.getOrNull(charIndex)
                        val borderColor = when {
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
                        val backgroundColor = if (!enabled) {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }

                        Box(
                            modifier = Modifier
                                .padding(horizontal = 2.dp)
                                .size(40.dp)
                                .background(backgroundColor, RoundedCornerShape(4.dp))
                                .border(
                                    BorderStroke(1.dp, borderColor),
                                    RoundedCornerShape(4.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (char != null) {
                                val textColor = when {
                                    !enabled -> correctColor
                                    isError -> errorColor
                                    else -> LocalContentColor.current
                                }
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
                        Spacer(modifier = Modifier.width(12.dp))
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
fun TypingScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
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
                title = { Text("${state.deckWithCards.deck.name} - Typing") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = state.correctAnswerFound
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Card")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val isWideScreen = this.maxWidth > 600.dp
            if (isWideScreen) {
                LandscapeTypingLayout(state = state, viewModel = viewModel, focusRequester = focusRequester)
            } else {
                PortraitTypingLayout(state = state, viewModel = viewModel, focusRequester = focusRequester)
            }
        }
    }
}

@Composable
fun PortraitTypingLayout(
    state: net.ericclark.studiare.data.StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    focusRequester: FocusRequester
) {
    var userAnswer by remember(state.currentCardIndex) { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
                viewModel = viewModel
            )
            Spacer(Modifier.height(16.dp))

            TypingInteractionContent(
                state = state,
                userAnswer = userAnswer,
                onUserAnswerChange = { userAnswer = it },
                focusRequester = focusRequester,
                viewModel = viewModel
            )

            if (state.correctAnswerFound) {
                Spacer(Modifier.height(16.dp))
                val card = state.shuffledCards[state.currentCardIndex]
                // Only initialize state once per card to prevent reset on recomposition
                var difficulty by remember(card.id) { mutableStateOf(card.difficulty) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DifficultySlider(
                        label = "Rate Difficulty",
                        difficulty = difficulty,
                        onDifficultyChange = {
                            difficulty = it
                            viewModel.updateCardDifficulty(card, it)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    MarkKnownButton(
                        isKnown = card.isKnown,
                        onClick = { viewModel.toggleCardKnownStatus(card) }
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            // Button is always visible, but enabled state depends on correctness
            Button(
                onClick = { viewModel.nextCard() },
                modifier = Modifier.fillMaxWidth(0.8f),
                enabled = state.correctAnswerFound
            ) { Text("Next Card") }
        }
    }
}

@Composable
fun LandscapeTypingLayout(
    state: net.ericclark.studiare.data.StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    focusRequester: FocusRequester
) {
    var userAnswer by remember(state.currentCardIndex) { mutableStateOf("") }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
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
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(16.dp))
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
                    Spacer(Modifier.height(16.dp))
                    val card = state.shuffledCards[state.currentCardIndex]
                    var difficulty by remember(card.id) { mutableStateOf(card.difficulty) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DifficultySlider(
                            label = "Rate Difficulty",
                            difficulty = difficulty,
                            onDifficultyChange = {
                                difficulty = it
                                viewModel.updateCardDifficulty(card, it)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        MarkKnownButton(
                            isKnown = card.isKnown,
                            onClick = { viewModel.toggleCardKnownStatus(card) }
                        )
                    }
                }
            }

            Button(
                onClick = { viewModel.nextCard() },
                modifier = Modifier.fillMaxWidth(0.8f),
                enabled = state.correctAnswerFound
            ) { Text("Next Card") }
        }
    }
}

@Composable
fun TypingInteractionContent(
    state: net.ericclark.studiare.data.StudyState,
    userAnswer: String,
    onUserAnswerChange: (String) -> Unit,
    focusRequester: FocusRequester,
    viewModel: net.ericclark.studiare.FlashcardViewModel
) {
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == "Front") card.back else card.front
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
            Text("Correct!", color = Color(0xFF22C55E), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom=8.dp))
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
            // We wrap the visual content in a Box that forces focus when clicked.
            // This ensures that even if the user taps the visual blocks, the keyboard opens.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { focusRequester.requestFocus() },
                contentAlignment = Alignment.Center
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.Center,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val words = answerText.split(' ')
                    var charIndex = 0

                    words.forEachIndexed { wordIndex, word ->
                        word.forEach { targetChar ->
                            val userChar = userValue.getOrNull(charIndex)

                            // Determine color based on logic:
                            // Blue (untyped/placeholder) -> Red (wrong) -> Green (correct)
                            val boxColor = when {
                                userChar == null -> untypedColor // Not typed yet -> Blue
                                userChar.equals(
                                    targetChar,
                                    ignoreCase = true
                                ) -> correctColor // Correct -> Green
                                else -> incorrectColor // Incorrect -> Red
                            }

                            Box(
                                modifier = Modifier
                                    .padding(horizontal = 2.dp)
                                    .size(40.dp)
                                    .background(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        RoundedCornerShape(4.dp)
                                    )
                                    .border(
                                        BorderStroke(2.dp, boxColor),
                                        RoundedCornerShape(4.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    // Always show the TARGET character so it looks "filled in"
                                    text = targetChar.toString().uppercase(),
                                    style = MaterialTheme.typography.headlineSmall,
                                    color = boxColor
                                )
                            }
                            charIndex++
                        }
                        if (wordIndex < words.size - 1) {
                            Spacer(modifier = Modifier.width(12.dp))
                        }
                    }
                }
            }
        }
    )
}