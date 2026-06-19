package net.ericclark.studiare.studymodes

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.screens.FlowRow
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import kotlin.text.isLetter
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.draw.scale
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass

@Composable
fun HangmanNavigationRow(
    currentIndex: Int,
    totalCards: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    showNext: Boolean
) {
    val dimensions = LocalStudiareDimensions.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(top = dimensions.spacingSmall)
    ) {
        // M3 Expressive: Upgraded to FilledTonalIconButton
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onPrev,
            enabled = currentIndex > 0
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = getText(R.string.previous),
                // Let the component handle disabled tint automatically instead of forcing transparent
            )
        }

        // Count Text
        Text(
            text = stringResource(R.string.card_index_of_total, currentIndex + 1, totalCards),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = dimensions.paddingMedium)
        )

        // M3 Expressive: Upgraded to FilledTonalIconButton
        androidx.compose.material3.FilledTonalIconButton(
            onClick = onNext,
            enabled = showNext
        ) {
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = getText(R.string.next),
            )
        }
    }
}

@Composable
fun HangmanScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
    val state = viewModel.studyState ?: return
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showEditDialog by remember { mutableStateOf(false) }
    var textInput by remember { mutableStateOf("") }

    if (showEditDialog) {
        val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex)
        if (currentCard != null) {
            EditCardDialog(
                cardToEdit = currentCard,
                viewModel = viewModel,
                onDismiss = { showEditDialog = false })
        }
    }

    if (state.isComplete) {
        StudyCompletionScreen(navController = navController, viewModel = viewModel)
        return
    }

    // Auto-open keyboard
    LaunchedEffect(state.currentCardIndex) {
        if (!state.correctAnswerFound) {
            delay(300)
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val isCompactHeight = windowHeightSizeClass == WindowHeightSizeClass.Compact

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            if (!isCompactHeight) {
                CustomTopAppBar(
                    title = { Text(stringResource(R.string.deck_hangman_title_format, state.deckWithCards.deck.name)) },
                    navigationIcon = {
                        IconButton(onClick = { viewModel.endStudySession(); navController.popBackStack() }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    },
                    actions = {
                        androidx.compose.material3.FilledTonalIconButton(
                            onClick = { showEditDialog = true },
                            enabled = state.correctAnswerFound
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_card))
                        }
                    }
                )
            }
        }
    ) { padding ->
        val rootFocusRequester = remember { FocusRequester() }

        // When the card is answered, grab focus on the root box so hardware keys keep working for navigation
        LaunchedEffect(state.correctAnswerFound) {
            if (state.correctAnswerFound) {
                rootFocusRequester.requestFocus()
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex) ?: return@onPreviewKeyEvent false

                    val isHandledKeyDown = event.type == KeyEventType.KeyDown && (
                            (state.correctAnswerFound && event.key in listOf(Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionRight, Key.DirectionLeft, Key.K, Key.U, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5)) ||
                                    (!state.correctAnswerFound && event.key in listOf(Key.Enter, Key.NumPadEnter))
                            )

                    // Consume handled down presses to stop default UI scrolling
                    if (isHandledKeyDown) return@onPreviewKeyEvent true

                    if (event.type == KeyEventType.KeyUp) {
                        if (state.correctAnswerFound) {
                            // Card is solved: Intercept navigation and grading
                            when (event.key) {
                                Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionRight -> { viewModel.nextCard(); return@onPreviewKeyEvent true }
                                Key.DirectionLeft -> { viewModel.previousCard(); return@onPreviewKeyEvent true }
                                Key.K, Key.U -> { viewModel.toggleCardKnownStatus(currentCard); return@onPreviewKeyEvent true }
                                Key.One, Key.NumPad1 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.ONE); return@onPreviewKeyEvent true }
                                Key.Two, Key.NumPad2 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.TWO); return@onPreviewKeyEvent true }
                                Key.Three, Key.NumPad3 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.THREE); return@onPreviewKeyEvent true }
                                Key.Four, Key.NumPad4 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.FOUR); return@onPreviewKeyEvent true }
                                Key.Five, Key.NumPad5 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.FIVE); return@onPreviewKeyEvent true }
                            }
                        } else {
                            // User is actively guessing: Only intercept Enter to reveal the answer completely
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter -> { viewModel.revealQuizAnswer(); return@onPreviewKeyEvent true }
                            }
                        }
                    }
                    false
                }
        ) {
            BasicTextField(
                value = textInput,
                onValueChange = { newValue ->
                    if (!state.correctAnswerFound) {
                        val char = newValue.lastOrNull()
                        if (char != null && char.isLetter()) {
                            viewModel.submitHangmanGuess(char)
                        }
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            // Any size class larger than Compact is wider than 600dp
            if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                LandscapeHangmanLayout(state, viewModel, focusRequester, isCompactHeight)
            } else {
                PortraitHangmanLayout(state, viewModel, focusRequester)
            }
        }
    }
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortraitHangmanLayout(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel, focusRequester: FocusRequester) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    Column(modifier = Modifier.fillMaxSize().padding(dimensions.paddingMedium)) {

        // 1. Top Section: Card & Navigation (Weighted to shrink)
        Column(
            modifier = Modifier.weight(1.2f).fillMaxWidth(),
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                QuizCardContent(
                    state = state,
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize(),
                    showNavigation = false,
                    tags = cardTags
                )
            }

            /*
            HangmanNavigationRow(
                currentIndex = state.currentCardIndex,
                totalCards = state.shuffledCards.size,
                onPrev = { viewModel.previousCard() },
                onNext = { viewModel.nextCard() },
                showNext = state.correctAnswerFound
            )
            */
        }

        Spacer(Modifier.height(dimensions.spacingMedium))

        // 2. Middle Section: Drawing & Misses (Weighted to shrink)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
        ) {
            androidx.compose.material3.OutlinedCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(modifier = Modifier.fillMaxSize().padding(dimensions.paddingSmall), contentAlignment = Alignment.Center) {
                    HangmanDrawing(mistakes = state.hangmanMistakes, fingersAndToes = state.fingersAndToes)
                }
            }

            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
            ) {
                Column(
                    modifier = Modifier.padding(dimensions.paddingMedium).fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(getText(R.string.misses), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingSmall))

                    val answerText = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
                    val incorrectGuesses = state.guessedLetters.filter { !answerText.contains(it, ignoreCase = true) }.sorted()

                    FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)) {
                        if (incorrectGuesses.isEmpty()) {
                            Text("-", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else {
                            incorrectGuesses.forEach { char ->
                                Text(char.toString(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(dimensions.spacingLarge))

        // 3. Bottom Section: Input & Controls (Rigid block, pushes the top sections to shrink)
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            HangmanInput(state = state, focusRequester = focusRequester, viewModel = viewModel)

            Spacer(Modifier.height(dimensions.spacingLarge))

            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
                label = "hangmanButtonSquish"
            )

            Button(
                onClick = {
                    if (state.correctAnswerFound) viewModel.nextCard() else viewModel.revealQuizAnswer()
                },
                modifier = Modifier.fillMaxWidth(0.8f).defaultMinSize(minHeight = 56.dp).scale(scale),
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                interactionSource = interactionSource
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = state.correctAnswerFound,
                    transitionSpec = {
                        val springSpec = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium)
                        (androidx.compose.animation.slideInVertically(animationSpec = springSpec, initialOffsetY = { it }) + androidx.compose.animation.fadeIn()).togetherWith(androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) + androidx.compose.animation.fadeOut())
                    },
                    label = "hangmanButtonAnim"
                ) { isRevealed ->
                    Text(getText(if (isRevealed) R.string.next_card else R.string.get_answer))
                }
            }
        }
    }
}


@Composable
fun LandscapeHangmanLayout(
    state: net.ericclark.studiare.data.StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    focusRequester: FocusRequester,
    isCompactHeight: Boolean
) {
    val dimensions = LocalStudiareDimensions.current
    Row(modifier = Modifier.fillMaxSize().padding(dimensions.paddingMedium)) {
        // Left Column: Card + Nav + Misses
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    QuizCardContent(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        showNavigation = false
                    )
                }
                /*
                HangmanNavigationRow(
                    currentIndex = state.currentCardIndex,
                    totalCards = state.shuffledCards.size,
                    onPrev = { viewModel.previousCard() },
                    onNext = { viewModel.nextCard() },
                    showNext = state.correctAnswerFound
                )
                 */
            }

            if (!isCompactHeight) {
                Card(
                    modifier = Modifier.weight(0.6f).fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                ) {
                    Column(modifier = Modifier.padding(dimensions.paddingMedium)) { // Increased padding slightly
                        // M3 Expressive: Bumped typography to titleSmall
                        Text(getText(R.string.misses), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))

                        val card = state.shuffledCards[state.currentCardIndex]
                        val answerText = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
                        val incorrectGuesses = state.guessedLetters.filter { !answerText.contains(it, ignoreCase = true) }.sorted()
                        Text(incorrectGuesses.joinToString("  "), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineMedium) // Larger text with wider spacing
                    }
                }
            }
        }

        Spacer(Modifier.width(dimensions.spacingMedium))

        // Center: Word & Controls
        Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
            HangmanInput(state = state, focusRequester = focusRequester, viewModel = viewModel)
            Spacer(Modifier.height(dimensions.spacingLarge))

            // PHASE 3: Smooth text crossfade instead of instant button snap
            // M3 Expressive: Tactile interaction for primary button
            val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by androidx.compose.animation.core.animateFloatAsState(
                targetValue = if (isPressed) 0.95f else 1f,
                animationSpec = androidx.compose.animation.core.spring(
                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                    stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                ),
                label = "hangmanButtonSquish"
            )

            // Smooth text crossfade instead of instant button snap
            Button(
                onClick = {
                    if (state.correctAnswerFound) {
                        viewModel.nextCard()
                    } else {
                        viewModel.revealQuizAnswer()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f)
                    .defaultMinSize(minHeight = 56.dp) // M3 Accessible touch target
                    .scale(scale),
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                interactionSource = interactionSource
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = state.correctAnswerFound,
                    transitionSpec = {
                        val springSpec = androidx.compose.animation.core.spring<androidx.compose.ui.unit.IntOffset>(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        )
                        (androidx.compose.animation.slideInVertically(animationSpec = springSpec, initialOffsetY = { it }) +
                                androidx.compose.animation.fadeIn()).togetherWith(
                            androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }) +
                                    androidx.compose.animation.fadeOut()
                        )
                    },
                    label = "hangmanButtonAnim"
                ) { isRevealed ->
                    Text(getText(if (isRevealed) R.string.next_card else R.string.get_answer))
                }
            }
        }

        Spacer(Modifier.width(dimensions.spacingMedium))

        // Right: Drawing or Misses
        if (isCompactHeight) {
            Card(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingMedium).fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                    Text(getText(R.string.misses), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))

                    val card = state.shuffledCards[state.currentCardIndex]
                    val answerText = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
                    val incorrectGuesses = state.guessedLetters.filter { !answerText.contains(it, ignoreCase = true) }.sorted()
                    Text(incorrectGuesses.joinToString("  "), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.displaySmall)
                }
            }
        } else {
            OutlinedCard(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                colors = CardDefaults.outlinedCardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize().padding(dimensions.paddingSmall),
                    contentAlignment = Alignment.Center
                ) {
                    HangmanDrawing(mistakes = state.hangmanMistakes, fingersAndToes = state.fingersAndToes)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HangmanInput(state: net.ericclark.studiare.data.StudyState, focusRequester: FocusRequester, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front

    val maxMistakes = if (state.fingersAndToes) 27 else 7
    val isWin = state.correctAnswerFound && state.hangmanMistakes < maxMistakes

    // PHASE 5: Tactile Squish for keyboard trigger area
    val interactionSource =
        remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "hangmanInputSquish"
    )

    // Visual Display Box - Clickable to open keyboard
    Box(
        modifier = Modifier
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null
            ) {
                if (!state.correctAnswerFound) {
                    focusRequester.requestFocus()
                    keyboardController?.show()
                }
            },
        contentAlignment = Alignment.Center
    ) {
        // Visible UI (No hidden TextField here anymore!)
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
            modifier = Modifier.fillMaxWidth()
        ) {
            val words = answerText.split(' ')
            words.forEachIndexed { index, word ->
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    word.forEach { char ->
                        val isGuessed = state.guessedLetters.contains(char.uppercaseChar()) || !char.isLetter() || state.correctAnswerFound
                        val displayChar = if (isGuessed) char.toString().uppercase() else "_"

                        val targetColor = when {
                            isWin -> Color(0xFF22C55E)
                            state.correctAnswerFound && !state.guessedLetters.contains(char.uppercaseChar()) && char.isLetter() -> MaterialTheme.colorScheme.error
                            else -> LocalContentColor.current
                        }

                        val animatedColor by androidx.compose.animation.animateColorAsState(
                            targetValue = targetColor,
                            animationSpec = androidx.compose.animation.core.tween(300),
                            label = "letterColorAnim"
                        )

                        Text(
                            text = displayChar,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = animatedColor
                        )
                    }
                }
                if (index < words.size - 1) {
                    Spacer(modifier = Modifier.width(dimensions.spacingLarge))
                }
            }
        }
    }
}
@Composable
fun HangmanDrawing(mistakes: Int, fingersAndToes: Boolean) {
    val color = MaterialTheme.colorScheme.onSurface

    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val stroke = 4.dp.toPx()

        // Geometry Calculations
        val poleX = w * 0.2f
        val ropeX = w * 0.6f
        val topY = h * 0.1f
        val bottomY = h * 0.9f
        val ropeLength = h * 0.1f
        val ropeEndY = topY + ropeLength

        // Head Radius (Dynamic but limited)
        val headRadius = (w * 0.08f).coerceAtMost(h * 0.08f)
        val headCenterY = ropeEndY + headRadius

        // Connection points
        val neckY = headCenterY + headRadius // Bottom of head
        val torsoEndY = neckY + (h * 0.25f) // Length of torso

        // Always draw scaffolding if mistakes > 0
        if (mistakes >= 1) {
            // Base
            drawLine(color, Offset(w * 0.1f, bottomY), Offset(w * 0.9f, bottomY), stroke, cap = StrokeCap.Round)
            // Pole
            drawLine(color, Offset(poleX, bottomY), Offset(poleX, topY), stroke, cap = StrokeCap.Round)
            // Top Bar
            drawLine(color, Offset(poleX, topY), Offset(ropeX, topY), stroke, cap = StrokeCap.Round)
            // Rope
            drawLine(color, Offset(ropeX, topY), Offset(ropeX, ropeEndY), stroke, cap = StrokeCap.Round)
        }

        // Stick Figure
        if (mistakes >= 2) { // Head
            drawCircle(color, radius = headRadius, center = Offset(ropeX, headCenterY), style = Stroke(stroke))
        }
        if (mistakes >= 3) { // Torso (Starts exactly at bottom of head circle)
            drawLine(color, Offset(ropeX, neckY), Offset(ropeX, torsoEndY), stroke, cap = StrokeCap.Round)
        }

        // Limbs (Connected to Torso)
        val shoulderY = neckY + (torsoEndY - neckY) * 0.2f // Arms start slightly down from neck
        val hipY = torsoEndY // Legs start at bottom of torso

        if (mistakes >= 4) { // Left Arm
            drawLine(color, Offset(ropeX, shoulderY), Offset(ropeX - w * 0.15f, shoulderY + h * 0.1f), stroke, cap = StrokeCap.Round)
        }
        if (mistakes >= 5) { // Right Arm
            drawLine(color, Offset(ropeX, shoulderY), Offset(ropeX + w * 0.15f, shoulderY + h * 0.1f), stroke, cap = StrokeCap.Round)
        }
        if (mistakes >= 6) { // Left Leg
            drawLine(color, Offset(ropeX, hipY), Offset(ropeX - w * 0.1f, hipY + h * 0.2f), stroke, cap = StrokeCap.Round)
        }
        if (mistakes >= 7) { // Right Leg
            drawLine(color, Offset(ropeX, hipY), Offset(ropeX + w * 0.1f, hipY + h * 0.2f), stroke, cap = StrokeCap.Round)
        }

        // Fingers and Toes Logic
        if (fingersAndToes) {
            // Hand Ends
            val lHand = Offset(ropeX - w * 0.15f, shoulderY + h * 0.1f)
            val rHand = Offset(ropeX + w * 0.15f, shoulderY + h * 0.1f)

            // Foot Ends
            val lFoot = Offset(ropeX - w * 0.1f, hipY + h * 0.2f)
            val rFoot = Offset(ropeX + w * 0.1f, hipY + h * 0.2f)

            // Left Fingers
            for (i in 1..5) {
                if (mistakes >= 7 + i) {
                    drawLine(color, lHand, Offset(lHand.x - 10f, lHand.y + (i*6f) - 18f), 2.dp.toPx())
                }
            }

            // Right Fingers
            for (i in 1..5) {
                if (mistakes >= 12 + i) {
                    drawLine(color, rHand, Offset(rHand.x + 10f, rHand.y + (i*6f) - 18f), 2.dp.toPx())
                }
            }

            // Left Toes
            for (i in 1..5) {
                if (mistakes >= 17 + i) {
                    drawLine(color, lFoot, Offset(lFoot.x - 12f + (i*5f), lFoot.y + 10f), 2.dp.toPx())
                }
            }

            // Right Toes
            for (i in 1..5) {
                if (mistakes >= 22 + i) {
                    drawLine(color, rFoot, Offset(rFoot.x - 12f + (i*5f), rFoot.y + 10f), 2.dp.toPx())
                }
            }
        }
    }
}