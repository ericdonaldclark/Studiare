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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.screens.*
import kotlinx.coroutines.delay
import net.ericclark.studiare.data.*

@Composable
fun AnagramScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
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
                title = { Text("${state.deckWithCards.deck.name} - Anagram") },
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
                LandscapeAnagramLayout(state = state, viewModel = viewModel, focusRequester = focusRequester)
            } else {
                PortraitAnagramLayout(state = state, viewModel = viewModel, focusRequester = focusRequester)
            }
        }
    }
}

@Composable
fun PortraitAnagramLayout(
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
            Spacer(Modifier.height(2.dp))

            AnagramInteractionContent(
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

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp),
            horizontalArrangement = Arrangement.Center
        ) {
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
    }
}

@Composable
fun LandscapeAnagramLayout(
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
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }
        Spacer(Modifier.width(16.dp))
        // Right Column
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
                AnagramInteractionContent(
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
    }
}

@Composable
fun AnagramInteractionContent(
    state: net.ericclark.studiare.data.StudyState,
    userAnswer: String,
    onUserAnswerChange: (String) -> Unit,
    focusRequester: FocusRequester,
    viewModel: net.ericclark.studiare.FlashcardViewModel
) {
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == "Front") card.back else card.front
    val answerWithoutSpaces = remember(answerText) { answerText.replace(" ", "") }

    // Shuffling Logic
    val shuffledLetters = remember(answerText) {
        val clean = answerText.replace(" ", "").uppercase()
        if (clean.length <= 1) return@remember clean

        val chars = clean.toCharArray()
        var shuffled: CharArray
        var attempts = 0

        do {
            shuffled = chars.clone()
            shuffled.shuffle()

            var matches = 0
            for (i in chars.indices) {
                if (chars[i] == shuffled[i]) matches++
            }
            attempts++
        } while (matches > 1 && attempts < 100)

        String(shuffled)
    }

    val onAnswerChange = { newValue: String ->
        if (!state.correctAnswerFound) {
            val filteredValue = newValue.filter { it != ' ' }
            if (filteredValue.length <= answerWithoutSpaces.length) {
                onUserAnswerChange(filteredValue)
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

        AnagramInput(
            userValue = if (state.correctAnswerFound) answerWithoutSpaces else userAnswer,
            onValueChange = onAnswerChange,
            answerText = answerText,
            shuffledLetters = shuffledLetters,
            focusRequester = focusRequester,
            enabled = !state.correctAnswerFound,
            showCorrectLetters = state.showCorrectLetters
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnagramInput(
    userValue: String,
    onValueChange: (String) -> Unit,
    answerText: String,
    shuffledLetters: String,
    focusRequester: FocusRequester,
    enabled: Boolean,
    showCorrectLetters: Boolean
) {
    val correctColor = Color(0xFF22C55E)
    val incorrectColor = MaterialTheme.colorScheme.error
    val defaultBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val filledColor = MaterialTheme.colorScheme.primary
    val keyboardController = LocalSoftwareKeyboardController.current

    // LOGIC: Determine which scrambled letters are "used"
    val usedIndices = remember(userValue, shuffledLetters) {
        val used = BooleanArray(shuffledLetters.length)
        val userChars = userValue.toMutableList() // Copy mutable list of chars typed by user

        // Iterate through scrambled source. If a char matches one the user typed, mark used.
        for (i in shuffledLetters.indices) {
            val char = shuffledLetters[i]
            val foundIndex = userChars.indexOfFirst { it.equals(char, true) }
            if (foundIndex != -1) {
                used[i] = true
                userChars.removeAt(foundIndex) // Consume this char so we don't mark duplicates
            }
        }
        used
    }

    BasicTextField(
        value = userValue,
        onValueChange = onValueChange,
        enabled = enabled,
        modifier = Modifier
            .focusRequester(focusRequester)
            .fillMaxWidth(),
        textStyle = androidx.compose.ui.text.TextStyle(color = Color.Transparent),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        decorationBox = {
            // UPDATED: Keyboard trigger
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) {
                        focusRequester.requestFocus()
                        keyboardController?.show()
                    },
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // 1. Source Row (Scrambled Letters)
                    Text("Unscramble:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))

                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val words = answerText.split(' ')
                        var charIndex = 0

                        words.forEachIndexed { wordIndex, word ->
                            word.forEach { _ ->
                                val charToShow =
                                    shuffledLetters.getOrNull(charIndex)?.toString() ?: ""
                                val isUsed = usedIndices.getOrElse(charIndex) { false }

                                // UPDATED: Visual removal of box if used
                                val boxBackground =
                                    if (isUsed) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer
                                val textColor =
                                    if (isUsed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSecondaryContainer

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .size(40.dp)
                                        .background(boxBackground, RoundedCornerShape(4.dp)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = charToShow,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = textColor
                                    )
                                }
                                charIndex++
                            }
                            if (wordIndex < words.size - 1) {
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // 2. Target Row (User Input) remains same ...
                    Text("Your Answer:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))

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

                                val borderColor = when {
                                    !enabled -> correctColor
                                    userChar == null -> defaultBorderColor
                                    showCorrectLetters -> {
                                        if (userChar.equals(
                                                targetChar,
                                                ignoreCase = true
                                            )
                                        ) correctColor else incorrectColor
                                    }

                                    else -> filledColor
                                }

                                val boxBackground = MaterialTheme.colorScheme.surface

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .size(40.dp)
                                        .background(boxBackground, RoundedCornerShape(4.dp))
                                        .border(
                                            BorderStroke(2.dp, borderColor),
                                            RoundedCornerShape(4.dp)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (userChar != null) {
                                        val textColor = if (showCorrectLetters && enabled) {
                                            if (userChar.equals(
                                                    targetChar,
                                                    ignoreCase = true
                                                )
                                            ) correctColor else incorrectColor
                                        } else if (!enabled) {
                                            correctColor
                                        } else {
                                            LocalContentColor.current
                                        }

                                        Text(
                                            text = userChar.toString().uppercase(),
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
            }
        }
    )
}