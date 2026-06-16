package net.ericclark.studiare.studymodes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
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
import net.ericclark.studiare.R
import net.ericclark.studiare.StudyCompletionScreen
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import androidx.compose.runtime.rememberCoroutineScope
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.LocalWindowHeightSizeClass
import net.ericclark.studiare.LocalWindowWidthSizeClass

@Composable
fun MultipleChoiceScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val state = viewModel.studyState ?: return
    var showEditDialog by remember { mutableStateOf(false) }
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

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
                    if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
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
                title = { Text(stringResource(R.string.deck_multiple_choice_title_format, state.deckWithCards.deck.name)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_card))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
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
    viewModel:FlashcardViewModel,
    options: List<String>
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex)
    val correctAnswer = if (state.quizPromptSide == CardSide.FRONT) currentCard?.back else currentCard?.front

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
                showNavigation = true, // We handle nav via selection
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
                        label = getText(R.string.difficulty),
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
                Spacer(Modifier.height(dimensions.spacingMedium))
            }

            // 4. Feedback / Grading Area
            MCFeedbackArea(state = state, viewModel = viewModel)
        }
    }
}

@Composable
fun LandscapeMCLayout(
    state: StudyState,
    viewModel:FlashcardViewModel,
    options: List<String>
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }
    val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex)
    val correctAnswer = if (state.quizPromptSide == CardSide.FRONT) currentCard?.back else currentCard?.front

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
                showNavigation = true,
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
            val windowHeightSizeClass = LocalWindowHeightSizeClass.current
            // M3 Expressive: Dynamic columns and removed the OutlinedCard box
            val visibleOptions = if (state.correctAnswerFound) listOfNotNull(correctAnswer) else options

            val columns = if (windowHeightSizeClass == WindowHeightSizeClass.Compact &&
                visibleOptions.size > 3) 2 else 1

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(vertical = dimensions.paddingSmall),
                horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall, Alignment.Bottom)
            ) {
                items(visibleOptions) { option ->
                    MCChoiceButton(
                        text = option,
                        state = state,
                        onClick = { viewModel.submitFlashcardQuizAnswer(option) }
                    )
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
                        label = getText(R.string.difficulty),
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
                Spacer(Modifier.height(dimensions.spacingMedium))
            }

            MCFeedbackArea(state = state, viewModel = viewModel)
        }
    }
}

@Composable
fun MCChoiceButton(
    text: String,
    state: StudyState,
    onClick: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards.getOrNull(state.currentCardIndex)
    val correctAnswer = if (state.quizPromptSide == CardSide.FRONT) card?.back else card?.front

    // State Logic
    val isCorrectAnswer = text == correctAnswer
    val isSelectedWrong = !state.correctAnswerFound && state.lastIncorrectAnswer == text
    val isRevealed = state.correctAnswerFound

    // Colors
    val correctColor = Color(0xFF22C55E)
    val errorColor = MaterialTheme.colorScheme.error

    // M3 Expressive: Solid, tactile resting surfaces instead of outlines
    val defaultContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val defaultContentColor = MaterialTheme.colorScheme.onSurface

    val targetContainerColor = when {
        isRevealed && isCorrectAnswer -> correctColor.copy(alpha = 0.2f)
        isSelectedWrong -> errorColor
        else -> defaultContainerColor
    }

    val targetBorderColor = when {
        isRevealed && isCorrectAnswer -> correctColor
        isSelectedWrong -> errorColor
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val targetContentColor = defaultContentColor

    // M3 Expressive: Upgraded from tween(300) to organic springs
    val colorSpring = androidx.compose.animation.core.spring<Color>(
        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
    )

    val containerColor by animateColorAsState(targetContainerColor, tween(300), label = "containerColor")
    val borderColor by animateColorAsState(targetBorderColor, tween(300), label = "borderColor")
    val contentColor by animateColorAsState(targetContentColor, animationSpec = colorSpring, label = "contentColor")

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "mcSquish"
    )

    // M3 Expressive: Filled Button with no borders for a cleaner, bolder look
    Button(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 64.dp).scale(scale), // Chunkier touch target
        enabled = !state.correctAnswerFound,
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = if (isCorrectAnswer) correctColor else MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = if (isCorrectAnswer) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(dimensions.paddingMedium),
        interactionSource = interactionSource
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun MCFeedbackArea(state: StudyState, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val scope = rememberCoroutineScope()
    var processingClick by remember { mutableStateOf(false) }

    // Reset processing state when card changes
    LaunchedEffect(state.currentCardIndex) {
        processingClick = false
    }

    // M3 Expressive: Reclaim screen real estate completely when not visible
    androidx.compose.animation.AnimatedVisibility(
        visible = state.correctAnswerFound,
        enter = androidx.compose.animation.slideInVertically(
            animationSpec = androidx.compose.animation.core.spring(
                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
            ),
            initialOffsetY = { it }
        ) + androidx.compose.animation.fadeIn() + androidx.compose.animation.expandVertically(),
        exit = androidx.compose.animation.slideOutVertically(targetOffsetY = { it }) +
                androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkVertically()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 72.dp),
            contentAlignment = Alignment.Center
        ) {
            val card = state.shuffledCards[state.currentCardIndex]
            val isFsrs = state.schedulingMode == SchedulingMode.FSRS
            val isWrong = state.incorrectCardIds.contains(card.id)

            if (isFsrs && !isWrong) {
                // FSRS Grading Buttons
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val hardInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val isHardPressed by hardInteractionSource.collectIsPressedAsState()
                    val hardScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (isHardPressed) 0.95f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "hardSquish")

                    Button(
                        onClick = {
                            if (!processingClick) {
                                processingClick = true
                                scope.launch { delay(150); viewModel.submitFsrsGrade(2) }
                            }
                        },
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
                            if (!processingClick) {
                                processingClick = true
                                scope.launch { delay(150); viewModel.submitFsrsGrade(3) }
                            }
                        },
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
                            if (!processingClick) {
                                processingClick = true
                                scope.launch { delay(150); viewModel.submitFsrsGrade(4) }
                            }
                        },
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
            } else {
                // Standard Mode or FSRS Incorrect -> Show "Next"
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
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .scale(nextScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    interactionSource = nextInteractionSource
                ) {
                    Text(getText(R.string.next_card))
                }
            }
        }
    }
}