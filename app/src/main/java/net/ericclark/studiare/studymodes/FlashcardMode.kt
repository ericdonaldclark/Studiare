package net.ericclark.studiare.studymodes

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.ui.draw.scale
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

/**
 * The main screen for the Flashcard study mode.
 * @param navController The NavController for navigating back.
 * @param viewModel The ViewModel providing the study state.
 */
@Composable
fun FlashcardScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val state = viewModel.studyState ?: return
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

    // Navigate to completion screen when the session is over
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
                title = { Text(state.deckWithCards.deck.name) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    // Button to flip the front and back
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = state.isCardRevealed || state.currentCardIndex < state.furthestCardIndex
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_card))
                    }
                    IconButton(onClick = { viewModel.flipStudyMode() }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = getText(R.string.flip_front_and_back))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            // Use different layouts for portrait and landscape orientations
            if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                LandscapeFlashcardLayout(state = state, viewModel = viewModel)
            } else {
                PortraitFlashcardLayout(state = state, viewModel = viewModel)
            }
        }
    }
}

/**
 * The portrait layout for the Flashcard study screen.
 * @param state The current study state.
 * @param viewModel The ViewModel providing business logic.
 */
@Composable
fun PortraitFlashcardLayout(state: StudyState, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    var difficulty by remember(card) { mutableStateOf(card.difficulty) }

    val allTags by viewModel.tags.collectAsState()
    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    val frontText = if (state.isFlipped) card.backRichText?.takeIf { it.isNotBlank() } ?: card.back else card.frontRichText?.takeIf { it.isNotBlank() } ?: card.front
    val frontNotes = if (state.isFlipped) card.backNotes else card.frontNotes
    val backText = if (state.isFlipped) card.frontRichText?.takeIf { it.isNotBlank() } ?: card.front else card.backRichText?.takeIf { it.isNotBlank() } ?: card.back
    val backNotes = if (state.isFlipped) card.frontNotes else card.backNotes

    val cardColor = if (state.showFront) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (state.showFront) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMedium)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            CommonFlashcard(
                frontText = frontText,
                isFrontRichText = card.frontRichText?.isNotBlank() == true,
                frontNotes = frontNotes,
                backText = backText,
                isBackRichText = card.backRichText?.isNotBlank() == true,
                backNotes = backNotes,
                isFlipped = !state.showFront,
                onFlip = {
                    if (state.isCardRevealed) {
                        // If already revealed, tapping usually goes to next card or flips back depending on preference
                        // For standard flashcards, we usually just flip back and forth
                        viewModel.flipCard()
                    } else {
                        viewModel.flipCard()
                    }
                },
                showBackNavigation = state.currentCardIndex != 0,
                showFrontNavigation = (state.currentCardIndex < state.furthestCardIndex) || (state.currentCardIndex != state.shuffledCards.size -1 && state.isCardRevealed),
                onPrevious = { viewModel.previousCard() },
                // Only show Next arrow if it's NOT a graded session (graded requires button press)
                onNext = { viewModel.nextCard() },
                tags = cardTags, // Optional: Pass tags if you want them displayed
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f),
                cardIndex = state.currentCardIndex,
                totalCards = state.shuffledCards.size,
                sessionId = state.sessionId
            )
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // Difficulty slider
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
                Spacer(Modifier.width(dimensions.spacingMedium))

                Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                    MarkKnownButton(
                        isKnown = card.isKnown,
                        onClick = { viewModel.toggleCardKnownStatus(card) }
                    )
                }
            }
            Spacer(Modifier.height(dimensions.spacingMedium))
            // Unified Button Logic Component
            FlashcardActionButtons(state = state, viewModel = viewModel)
        }
    }
}

/**
 * The landscape layout for the Flashcard study screen.
 * @param state The current study state.
 * @param viewModel The ViewModel providing business logic.
 */
@Composable
fun LandscapeFlashcardLayout(state: StudyState, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    var difficulty by remember(card) { mutableStateOf(card.difficulty) }

    val allTags by viewModel.tags.collectAsState()
    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    val frontText = if (state.isFlipped) card.backRichText?.takeIf { it.isNotBlank() } ?: card.back else card.frontRichText?.takeIf { it.isNotBlank() } ?: card.front
    val frontNotes = if (state.isFlipped) card.backNotes else card.frontNotes
    val backText = if (state.isFlipped) card.frontRichText?.takeIf { it.isNotBlank() } ?: card.front else card.backRichText?.takeIf { it.isNotBlank() } ?: card.back
    val backNotes = if (state.isFlipped) card.frontNotes else card.backNotes

    // Set card color based on which side is showing
    val cardColor = if (state.showFront) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (state.showFront) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Row(modifier = Modifier.fillMaxSize().padding(dimensions.paddingMedium)) {
        // Left column for the flashcard
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CommonFlashcard(
                frontText = frontText,
                isFrontRichText = card.frontRichText?.isNotBlank() == true,
                frontNotes = frontNotes,
                backText = backText,
                isBackRichText = card.backRichText?.isNotBlank() == true,
                backNotes = backNotes,
                isFlipped = !state.showFront,
                onFlip = {
                    if (state.isCardRevealed) {
                        // If already revealed, tapping usually goes to next card or flips back depending on preference
                        // For standard flashcards, we usually just flip back and forth
                        viewModel.flipCard()
                    } else {
                        viewModel.flipCard()
                    }
                },
                showBackNavigation = state.currentCardIndex != 0,
                showFrontNavigation = (state.currentCardIndex < state.furthestCardIndex) || (state.currentCardIndex != state.shuffledCards.size -1 && state.isCardRevealed),
                onPrevious = { viewModel.previousCard() },
                // Only show Next arrow if it's NOT a graded session (graded requires button press)
                onNext = { viewModel.nextCard() },
                tags = cardTags, // Optional: Pass tags if you want them displayed
                modifier = Modifier.fillMaxWidth(),
                cardIndex = state.currentCardIndex,
                totalCards = state.shuffledCards.size,
                sessionId = state.sessionId
            )
        }



        Spacer(Modifier.width(dimensions.spacingLarge))

        // Right column for controls
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
                Spacer(Modifier.width(dimensions.spacingMedium))
                Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                    MarkKnownButton(
                        isKnown = card.isKnown,
                        onClick = { viewModel.toggleCardKnownStatus(card) }
                    )
                }
            }
            Spacer(Modifier.height(dimensions.spacingMedium))

            // Unified Button Logic Component
            FlashcardActionButtons(state = state, viewModel = viewModel)
        }
    }
}

@Composable
fun FlashcardActionButtons(
    state: StudyState,
    viewModel: FlashcardViewModel,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    val scope = rememberCoroutineScope()
    var processingClick by remember { mutableStateOf(false) }

    // Reset processing state when card changes
    LaunchedEffect(state.currentCardIndex) {
        processingClick = false
    }

    val card = state.shuffledCards.getOrNull(state.currentCardIndex)
    val isAnswered = card != null && state.attemptedCardIds.contains(card.id)

    val targetMode = when {
        isAnswered -> "STANDARD"
        state.schedulingMode == SchedulingMode.FSRS && state.isCardRevealed -> "FSRS"
        state.isGraded && !state.showFront -> "GRADED"
        else -> "STANDARD"
    }

    androidx.compose.animation.AnimatedContent(
        targetState = targetMode,
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
        label = "buttonStateAnim",
        contentAlignment = Alignment.Center,
        modifier = modifier.fillMaxWidth()
    ) { targetMode ->
        when (targetMode) {
            "FSRS" -> {
                Column(
                    verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ElevatedButton(
                            onClick = {
                                if (!processingClick) {
                                    processingClick = true
                                    scope.launch { delay(150); viewModel.submitFsrsGrade(1) }
                                }
                            }, // Again
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xffb82741),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            enabled = !processingClick,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.nextIntervals[1] ?: "",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(getText(R.string.rating_again))
                            }
                        }

                        ElevatedButton(
                            onClick = {
                                if (!processingClick) {
                                    processingClick = true
                                    scope.launch { delay(150); viewModel.submitFsrsGrade(2) }
                                }
                            }, // Hard
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xfffcba03),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            enabled = !processingClick,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.nextIntervals[2] ?: "",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(getText(R.string.rating_hard))
                            }
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        ElevatedButton(
                            onClick = {
                                if (!processingClick) {
                                    processingClick = true
                                    scope.launch { delay(150); viewModel.submitFsrsGrade(3) }
                                }
                            }, // Good
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xff488c4b),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            enabled = !processingClick,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.nextIntervals[3] ?: "",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(getText(R.string.rating_good))
                            }
                        }

                        ElevatedButton(
                            onClick = {
                                if (!processingClick) {
                                    processingClick = true
                                    scope.launch { delay(150); viewModel.submitFsrsGrade(4) }
                                }
                            }, // Easy
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xff4287f5),
                                contentColor = Color.White
                            ),
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            enabled = !processingClick,
                            contentPadding = PaddingValues(horizontal = 4.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = state.nextIntervals[4] ?: "",
                                    style = MaterialTheme.typography.labelSmall
                                )
                                Text(getText(R.string.rating_easy))
                            }
                        }
                    }
                }
            }
            "GRADED" -> {
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.submitSelfGradedResult(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text(getText(R.string.incorrect)) }

                    Button(
                        onClick = { viewModel.submitSelfGradedResult(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)), // Green
                        modifier = Modifier.weight(1f)
                    ) { Text(getText(R.string.correct)) }
                }
            }
            "STANDARD" -> {
                val showNext = state.isCardRevealed || isAnswered
                Button(
                    onClick = {
                        if (showNext) {
                            viewModel.nextCard()
                        } else {
                            viewModel.flipCard()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.8f).defaultMinSize(minHeight = 56.dp)
                ) {
                    androidx.compose.animation.AnimatedContent(
                        targetState = showNext,
                        label = "buttonTextAnim"
                    ) { showNextMode ->
                        Text(getText(if (showNextMode) R.string.next_card else R.string.flip_card))
                    }
                }
            }
        }
    }
}

