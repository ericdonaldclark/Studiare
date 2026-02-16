package net.ericclark.studiare.studymodes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.DifficultySlider
import net.ericclark.studiare.EditCardDialog
import net.ericclark.studiare.MarkKnownButton
import net.ericclark.studiare.QuizCardContent
import net.ericclark.studiare.StudyCompletionScreen
import net.ericclark.studiare.data.*
import androidx.compose.runtime.rememberCoroutineScope
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

@Composable
fun MultipleChoiceScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val state = viewModel.studyState ?: return
    var showEditDialog by remember { mutableStateOf(false) }

    // Ensure options are generated
    LaunchedEffect(state.currentCardIndex, state.sessionId) {
        viewModel.generateOptionsForCurrentCardIfNeeded()
    }

    // Determine which options to display (Picker vs MC)
    val displayOptions = remember(state.currentCardIndex, state.mcOptions, state.pickerOptions, state.isFlipped) {
        state.pickerOptions.ifEmpty {
            val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex)
            val optionIds = state.mcOptions[currentCard?.id] ?: emptyList()
            optionIds.mapNotNull { id ->
                val card = state.deckWithCards.cards.find { it.id == id }
                if (card != null) {
                    if (state.quizPromptSide == "Front") card.back else card.front
                } else null
            }
        }
    }

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

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("${state.deckWithCards.deck.name} - Multiple Choice") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Card")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val isLandscape = this.maxWidth > 600.dp
            if (isLandscape) {
                LandscapeMCLayout(state, viewModel, displayOptions)
            } else {
                PortraitMCLayout(state, viewModel, displayOptions)
            }
        }
    }
}

@Composable
fun PortraitMCLayout(
    state: StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    options: List<String>
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex)
    val correctAnswer = if (state.quizPromptSide == "Front") currentCard?.back else currentCard?.front

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMedium)
    ) {
        // 1. Card Area
        Box(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                showNavigation = false, // We handle nav via selection
                tags = cardTags

            )
        }

        Spacer(Modifier.height(dimensions.spacingMedium))

        // 2. Choices Area
        Column(
            modifier = Modifier
                .weight(0.6f)
                .fillMaxWidth()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(vertical = dimensions.paddingSmall),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
            ) {
                items(options) { option ->
                    // SHOW logic: Always show if not answered yet.
                    // If answered (correctAnswerFound), ONLY show the correct answer.
                    if (!state.correctAnswerFound || option == correctAnswer) {
                        MCChoiceButton(
                            text = option,
                            state = state,
                            onClick = { viewModel.submitFlashcardQuizAnswer(option) }
                        )
                    }
                }
            }

            // 3. Difficulty & Mark Known (Visible only when correct answer found)
            if (state.correctAnswerFound && currentCard != null) {
                var difficulty by remember(currentCard) { mutableStateOf(currentCard.difficulty) }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DifficultySlider(
                        label = "Difficulty",
                        difficulty = difficulty,
                        onDifficultyChange = {
                            difficulty = it
                            viewModel.updateCardDifficulty(currentCard, it)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(dimensions.spacingMedium))
                    Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                        MarkKnownButton(
                            isKnown = currentCard.isKnown,
                            onClick = { viewModel.toggleCardKnownStatus(currentCard) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(dimensions.spacingMedium))

            // 4. Feedback / Grading Area
            MCFeedbackArea(state = state, viewModel = viewModel)
        }
    }
}

@Composable
fun LandscapeMCLayout(
    state: net.ericclark.studiare.data.StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    options: List<String>
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }
    val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex)
    val correctAnswer = if (state.quizPromptSide == "Front") currentCard?.back else currentCard?.front

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMedium)
    ) {
        // Left: Card
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            verticalArrangement = Arrangement.Center
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                showNavigation = false,
                tags = cardTags
            )
        }

        Spacer(Modifier.width(dimensions.spacingLarge))

        // Right: Choices + Settings + Feedback
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
        ) {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .padding(dimensions.paddingMedium),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
            ) {
                items(options) { option ->
                    // SHOW logic: Always show if not answered yet.
                    // If answered, ONLY show the correct answer.
                    if (!state.correctAnswerFound || option == correctAnswer) {
                        MCChoiceButton(
                            text = option,
                            state = state,
                            onClick = { viewModel.submitFlashcardQuizAnswer(option) }
                        )
                    }
                }
            }

            // Difficulty & Mark Known (Visible only when correct answer found)
            if (state.correctAnswerFound && currentCard != null) {
                var difficulty by remember(currentCard) { mutableStateOf(currentCard.difficulty) }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DifficultySlider(
                        label = "Difficulty",
                        difficulty = difficulty,
                        onDifficultyChange = {
                            difficulty = it
                            viewModel.updateCardDifficulty(currentCard, it)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(dimensions.spacingMedium))
                    Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                        MarkKnownButton(
                            isKnown = currentCard.isKnown,
                            onClick = { viewModel.toggleCardKnownStatus(currentCard) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(dimensions.spacingMedium))

            MCFeedbackArea(state = state, viewModel = viewModel)
        }
    }
}

@Composable
fun MCChoiceButton(
    text: String,
    state: net.ericclark.studiare.data.StudyState,
    onClick: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards.getOrNull(state.currentCardIndex)
    val correctAnswer = if (state.quizPromptSide == "Front") card?.back else card?.front

    // State Logic
    val isCorrectAnswer = text == correctAnswer
    val isSelectedWrong = !state.correctAnswerFound && state.lastIncorrectAnswer == text
    val isRevealed = state.correctAnswerFound

    // Colors
    val correctColor = Color(0xFF22C55E)
    val errorColor = MaterialTheme.colorScheme.error
    val defaultContainerColor = Color.Transparent
    val defaultContentColor = MaterialTheme.colorScheme.primary

    val targetContainerColor = when {
        isRevealed && isCorrectAnswer -> correctColor.copy(alpha = 0.2f)
        isSelectedWrong -> errorColor.copy(alpha = 0.2f)
        else -> defaultContainerColor
    }

    val targetBorderColor = when {
        isRevealed && isCorrectAnswer -> correctColor
        isSelectedWrong -> errorColor
        else -> MaterialTheme.colorScheme.outline
    }

    val targetContentColor = when {
        isRevealed && isCorrectAnswer -> correctColor.copy(alpha = 1f) // Darker green for text
        isSelectedWrong -> errorColor
        else -> defaultContentColor
    }

    val containerColor by animateColorAsState(targetContainerColor, tween(300), label = "containerColor")
    val borderColor by animateColorAsState(targetBorderColor, tween(300), label = "borderColor")
    val contentColor by animateColorAsState(targetContentColor, tween(300), label = "contentColor")

    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        enabled = !state.correctAnswerFound, // Disable input if already answered correctly
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = if (isCorrectAnswer) correctColor.copy(alpha = 0.2f) else Color.Transparent,
            disabledContentColor = if (isCorrectAnswer) correctColor else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        border = androidx.compose.foundation.BorderStroke(1.dp, if (state.correctAnswerFound && isCorrectAnswer) correctColor else borderColor),
        contentPadding = PaddingValues(dimensions.paddingMedium)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MCFeedbackArea(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val scope = rememberCoroutineScope()
    var processingClick by remember { mutableStateOf(false) }

    // Reset processing state when card changes
    LaunchedEffect(state.currentCardIndex) {
        processingClick = false
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp), // Fixed height to prevent layout jump
        contentAlignment = Alignment.Center
    ) {
        if (state.correctAnswerFound) {
            val card = state.shuffledCards[state.currentCardIndex]
            val isFsrs = state.schedulingMode == "Spaced Repetition"
            // If FSRS active and this card was NOT marked incorrect in this session (i.e. first try correct), show grading
            val isWrong = state.incorrectCardIds.contains(card.id)

            if (isFsrs && !isWrong) {
                // FSRS Grading Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(
                        onClick = {
                            if (!processingClick) {
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
                            if (!processingClick) {
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
                            if (!processingClick) {
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
            } else {
                // Standard Mode or FSRS Incorrect -> Show "Next"
                Button(
                    onClick = { viewModel.nextCard() },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                ) {
                    Text("Next Card")
                }
            }
        } else {
            // Hint or Empty Space
            Text(
                "Select the correct answer",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}