package net.ericclark.studiare.studymodes

import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import net.ericclark.studiare.data.DifficultySetting
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.EditCardDialog
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.LocalWindowWidthSizeClass
import net.ericclark.studiare.QuizCardContent
import net.ericclark.studiare.R
import net.ericclark.studiare.StudyCompletionScreen
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.CardSide
import net.ericclark.studiare.data.SchedulingMode
import net.ericclark.studiare.data.StudyState
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * A new screen for the Flashcard Quiz mode.
 * Displays the card prompt at the top and a scrollable picker list at the bottom.
 */
@Composable
fun FlashcardQuizScreen(
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

    if (state.isComplete) {
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
        return
    }

    // Scroll state for the Rolodex picker list
    val listState = rememberLazyListState()

    // Currently selected answer in the picker (locally)
    var selectedPickerOption by remember { mutableStateOf<String?>(null) }
    // Flag to track if we should auto-scroll (only on "Get Answer")
    var scrollOnReveal by remember { mutableStateOf(false) }

    val focusRequester = remember { FocusRequester() }
    val coroutineScope = rememberCoroutineScope()

    // Reset selection and scroll flag when card changes
    LaunchedEffect(state.currentCardIndex) {
        focusRequester.requestFocus()
        selectedPickerOption = null
        scrollOnReveal = false
    }

    // Auto-scroll to correct answer ONLY if "Get Answer" was used (or FSRS Wrong)

    // Auto-scroll to correct answer ONLY if "Get Answer" was used (or FSRS Wrong)
    LaunchedEffect(state.correctAnswerFound) {
        if (state.correctAnswerFound && scrollOnReveal) {
            val card = state.shuffledCards.getOrNull(state.currentCardIndex)
            if (card != null) {
                val correct = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
                val index = state.pickerOptions.indexOf(correct)
                if (index != -1) {
                    listState.animateScrollToItem(index)
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text(stringResource(R.string.deck_quiz_title_format, state.deckWithCards.deck.name)) },
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
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex) ?: return@onPreviewKeyEvent false
                    val isRevealed = state.correctAnswerFound || state.attemptedCardIds.contains(currentCard.id)

                    val isHandledKey = event.key in listOf(
                        Key.Spacebar, Key.Enter, Key.NumPadEnter,
                        Key.DirectionLeft, Key.DirectionRight, Key.DirectionUp, Key.DirectionDown,
                        Key.K, Key.U,
                        Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
                        Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5
                    )

                    if (!isHandledKey) return@onPreviewKeyEvent false

                    if (event.type == KeyEventType.KeyUp) {
                        if (isRevealed) {
                            when (event.key) {
                                Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionRight -> viewModel.nextCard()
                                Key.DirectionLeft -> viewModel.previousCard()
                                Key.K, Key.U -> viewModel.toggleCardKnownStatus(currentCard)
                                Key.One, Key.NumPad1 -> if (state.schedulingMode == SchedulingMode.FSRS) viewModel.submitFsrsGrade(1) else viewModel.updateCardDifficulty(currentCard, DifficultySetting.ONE)
                                Key.Two, Key.NumPad2 -> if (state.schedulingMode == SchedulingMode.FSRS) viewModel.submitFsrsGrade(2) else viewModel.updateCardDifficulty(currentCard, DifficultySetting.TWO)
                                Key.Three, Key.NumPad3 -> if (state.schedulingMode == SchedulingMode.FSRS) viewModel.submitFsrsGrade(3) else viewModel.updateCardDifficulty(currentCard, DifficultySetting.THREE)
                                Key.Four, Key.NumPad4 -> if (state.schedulingMode == SchedulingMode.FSRS) viewModel.submitFsrsGrade(4) else viewModel.updateCardDifficulty(currentCard, DifficultySetting.FOUR)
                                Key.Five, Key.NumPad5 -> if (state.schedulingMode != SchedulingMode.FSRS) viewModel.updateCardDifficulty(currentCard, DifficultySetting.FIVE)
                            }
                        } else {
                            when (event.key) {
                                Key.DirectionUp -> {
                                    val currentIndex = state.pickerOptions.indexOf(selectedPickerOption)
                                    if (currentIndex > 0) {
                                        val newIndex = currentIndex - 1
                                        selectedPickerOption = state.pickerOptions[newIndex]
                                        coroutineScope.launch { listState.animateScrollToItem(newIndex) }
                                    } else if (currentIndex == -1 && state.pickerOptions.isNotEmpty()) {
                                        selectedPickerOption = state.pickerOptions.last()
                                        coroutineScope.launch { listState.animateScrollToItem(state.pickerOptions.size - 1) }
                                    }
                                }
                                Key.DirectionDown -> {
                                    val currentIndex = state.pickerOptions.indexOf(selectedPickerOption)
                                    if (currentIndex < state.pickerOptions.size - 1 && currentIndex != -1) {
                                        val newIndex = currentIndex + 1
                                        selectedPickerOption = state.pickerOptions[newIndex]
                                        coroutineScope.launch { listState.animateScrollToItem(newIndex) }
                                    } else if (currentIndex == -1 && state.pickerOptions.isNotEmpty()) {
                                        selectedPickerOption = state.pickerOptions.first()
                                        coroutineScope.launch { listState.animateScrollToItem(0) }
                                    }
                                }
                                Key.Enter, Key.NumPadEnter -> {
                                    if (selectedPickerOption != null) {
                                        scrollOnReveal = true
                                        viewModel.submitFlashcardQuizAnswer(selectedPickerOption!!)
                                    }
                                }
                                Key.Spacebar -> {
                                    scrollOnReveal = true
                                    selectedPickerOption = null
                                    viewModel.revealQuizAnswer()
                                }
                            }
                        }
                    }
                    true // Consume handled keys
                }
        ) {
            if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                LandscapeFlashcardQuizLayout(
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    selectedPickerOption = selectedPickerOption,
                    onOptionSelected = { selectedPickerOption = it },
                    onReveal = {
                        scrollOnReveal = true
                        selectedPickerOption = null
                        viewModel.revealQuizAnswer()
                    },
                    onCheck = { scrollOnReveal = true } // Trigger scroll if wrong
                )
            } else {
                PortraitFlashcardQuizLayout(
                    state = state,
                    viewModel = viewModel,
                    listState = listState,
                    selectedPickerOption = selectedPickerOption,
                    onOptionSelected = { selectedPickerOption = it },
                    onReveal = {
                        scrollOnReveal = true
                        selectedPickerOption = null
                        viewModel.revealQuizAnswer()
                    },
                    onCheck = { scrollOnReveal = true } // Trigger scroll if wrong
                )
            }
        }
    }
}

@Composable
fun PortraitFlashcardQuizLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedPickerOption: String?,
    onOptionSelected: (String) -> Unit,
    onReveal: () -> Unit,
    onCheck: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Prompt Area (Top)
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth()
                .padding(dimensions.paddingMedium),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                tags = cardTags
            )
        }

        HorizontalDivider()

        // 2. Rolodex Picker Area (Middle)
        Box(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
            PickerListContent(
                state = state,
                listState = listState,
                selectedPickerOption = selectedPickerOption,
                onOptionSelected = onOptionSelected
            )
        }

        // 3. Action Buttons (Bottom)
        PickerActionButtons(
            state = state,
            viewModel = viewModel,
            selectedPickerOption = selectedPickerOption,
            onReveal = onReveal,
            onCheck = onCheck
        )
    }
}

@Composable
fun LandscapeFlashcardQuizLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedPickerOption: String?,
    onOptionSelected: (String) -> Unit,
    onReveal: () -> Unit,
    onCheck: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    Row(modifier = Modifier.fillMaxSize().padding(dimensions.paddingMedium)) {
        // Left Column: Card
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                tags = cardTags
            )
        }

        Spacer(Modifier.width(dimensions.spacingLarge))

        // Right Column: Picker List + Buttons
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            androidx.compose.material3.OutlinedCard(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                colors = androidx.compose.material3.CardDefaults.outlinedCardColors(
                    containerColor = Color.Transparent
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                PickerListContent(
                    state = state,
                    listState = listState,
                    selectedPickerOption = selectedPickerOption,
                    onOptionSelected = onOptionSelected
                )
            }

            PickerActionButtons(
                state = state,
                viewModel = viewModel,
                selectedPickerOption = selectedPickerOption,
                onReveal = onReveal,
                onCheck = onCheck
            )
        }
    }
}

@Composable
fun PickerListContent(
    state: StudyState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedPickerOption: String?,
    onOptionSelected: (String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val coroutineScope = rememberCoroutineScope()

    // WRAPPER BOX: Provides BoxScope for alignment and overlays the scrollbar on the list
    Box(modifier = Modifier.fillMaxSize()) {

        // The List
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = dimensions.paddingSmall)
        ) {
            items(state.pickerOptions) { option ->
                val isSelected = selectedPickerOption == option

                // If answer is found, highlight the correct one in Green
                val card = state.shuffledCards[state.currentCardIndex]
                val correct = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
                val isRealAnswer = option == correct

                // Check if this was the last incorrect guess
                val isWrongAnswer = !state.correctAnswerFound && state.lastIncorrectAnswer == option

                // Determine background color
                val targetBgColor = when {
                    state.correctAnswerFound && isRealAnswer -> Color(0xFF22C55E).copy(alpha = 0.3f)
                    isWrongAnswer -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }

                val bgColor by androidx.compose.animation.animateColorAsState(
                    targetValue = targetBgColor,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "pickerBgColor"
                )

                val targetTextColor = when {
                    state.correctAnswerFound && isRealAnswer -> Color(0xFF22C55E)
                    isWrongAnswer -> MaterialTheme.colorScheme.error
                    else -> LocalContentColor.current
                }

                val textColor by androidx.compose.animation.animateColorAsState(
                    targetValue = targetTextColor,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "pickerTextColor"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .background(bgColor)
                        .clickable(enabled = !state.correctAnswerFound) {
                            onOptionSelected(option)
                        }
                        .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingMedium),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = option,
                        style = MaterialTheme.typography.bodyLarge,
                        color = textColor,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }

        // Custom Fast Scroll Slider
        val totalItems = state.pickerOptions.size
        if (totalItems > 1) {
            var barHeight by remember { mutableStateOf(0f) }
            var isDragging by remember { mutableStateOf(false) }
            val density = LocalDensity.current

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(30.dp)
                    .align(Alignment.CenterEnd) // This now works because it is inside the parent Box
                    .padding(vertical = dimensions.paddingSmall)
                    .onSizeChanged { barHeight = it.height.toFloat() }
                    .pointerInput(totalItems, barHeight) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                isDragging = true
                                if (barHeight > 0) {
                                    val percentage = (offset.y / barHeight).coerceIn(0f, 1f)
                                    val index = (percentage * (totalItems - 1)).toInt()
                                    coroutineScope.launch { listState.scrollToItem(index) }
                                }
                            },
                            onDragEnd = { isDragging = false },
                            onDragCancel = { isDragging = false },
                            onVerticalDrag = { change, _ ->
                                if (barHeight > 0) {
                                    val percentage = (change.position.y / barHeight).coerceIn(0f, 1f)
                                    val index = (percentage * (totalItems - 1)).toInt()
                                    coroutineScope.launch { listState.scrollToItem(index) }
                                }
                            }
                        )
                    }
            ) {
                if (barHeight > 0 && totalItems > 0) {
                    val visibleItems = listState.layoutInfo.visibleItemsInfo.size
                    val thumbHeightPx = (barHeight * visibleItems / totalItems).coerceAtLeast(100f)
                    val firstVisible = listState.firstVisibleItemIndex
                    val scrollOffsetPx = (firstVisible.toFloat() / totalItems) * barHeight

                    val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
                    val scrollOffsetDp = with(density) { scrollOffsetPx.toDp() }

                    val thumbWidth by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isDragging) 12.dp else 6.dp,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                        ),
                        label = "thumbWidthAnim"
                    )

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = scrollOffsetDp)
                            .width(thumbWidth)
                            .height(thumbHeightDp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun PickerActionButtons(
    state: net.ericclark.studiare.data.StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    selectedPickerOption: String?,
    onReveal: () -> Unit,
    onCheck: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val scope = rememberCoroutineScope()
    var processingClick by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentCardIndex) {
        processingClick = false
    }

    val card = state.shuffledCards.getOrNull(state.currentCardIndex)
    val isAnswered = card != null && state.attemptedCardIds.contains(card.id)
    val showResultState = state.correctAnswerFound || isAnswered

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(dimensions.paddingMedium),
        contentAlignment = Alignment.Center
    ) {
        // NEW: Spring-based Animated Content for Quiz Buttons
        androidx.compose.animation.AnimatedContent(
            targetState = showResultState,
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
            label = "quizButtonStateAnim",
            contentAlignment = Alignment.Center
        ) { isAnswerFound ->
            if (isAnswerFound) {
                val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex)
                val isFsrs = state.schedulingMode == SchedulingMode.FSRS
                val isWrong = currentCard != null && state.incorrectCardIds.contains(currentCard.id)

                if (isFsrs && !isWrong && !isAnswered) {
                    // Correct in FSRS: Show Grading Buttons
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    if(!processingClick) {
                                        processingClick = true
                                        scope.launch { delay(150); viewModel.submitFsrsGrade(2) }
                                    }
                                }, // Hard
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xfffcba03), contentColor = Color.White),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                                enabled = !processingClick,
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = state.nextIntervals[2] ?: "", style = MaterialTheme.typography.labelSmall)
                                    Text(getText(R.string.rating_hard))
                                }
                            }
                            Button(
                                onClick = {
                                    if(!processingClick) {
                                        processingClick = true
                                        scope.launch { delay(150); viewModel.submitFsrsGrade(3) }
                                    }
                                }, // Good
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff488c4b), contentColor = Color.White),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                                enabled = !processingClick,
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = state.nextIntervals[3] ?: "", style = MaterialTheme.typography.labelSmall)
                                    Text(getText(R.string.rating_good))
                                }
                            }
                            Button(
                                onClick = {
                                    if(!processingClick) {
                                        processingClick = true
                                        scope.launch { delay(150); viewModel.submitFsrsGrade(4) }
                                    }
                                }, // Easy
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xff4287f5), contentColor = Color.White),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp),
                                enabled = !processingClick,
                                contentPadding = PaddingValues(horizontal = 4.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(text = state.nextIntervals[4] ?: "", style = MaterialTheme.typography.labelSmall)
                                    Text(getText(R.string.rating_easy))
                                }
                            }
                        }
                    }
                }
                else {
                    // Normal Mode OR FSRS Incorrect OR Answered: Show Next Card Button
                    Button(
                        onClick = { viewModel.nextCard() },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp)
                    ) {
                        Text(getText(R.string.next_card))
                    }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                ) {
                    val getAnswerInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val getAnswerPressed by getAnswerInteraction.collectIsPressedAsState()
                    val getAnswerScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (getAnswerPressed) 0.95f else 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        ),
                        label = "getAnswerSquish"
                    )

                    // Get Answer Button (Left)
                    OutlinedButton(
                        onClick = onReveal,
                        modifier = Modifier.weight(1f).scale(getAnswerScale).defaultMinSize(minHeight = 56.dp),
                        interactionSource = getAnswerInteraction
                    ) {
                        Text(getText(R.string.get_answer))
                    }

                    val checkAnswerInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val checkAnswerPressed by checkAnswerInteraction.collectIsPressedAsState()
                    val checkAnswerScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (checkAnswerPressed) 0.95f else 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        ),
                        label = "checkAnswerSquish"
                    )

                    // Check Answer Button (Right)
                    Button(
                        onClick = {
                            onCheck() // Trigger scroll
                            selectedPickerOption?.let {
                                viewModel.submitFlashcardQuizAnswer(it)
                            }
                        },
                        modifier = Modifier.weight(1f).scale(checkAnswerScale).defaultMinSize(minHeight = 56.dp),
                        enabled = selectedPickerOption != null,
                        interactionSource = checkAnswerInteraction
                    ) {
                        Text(getText(R.string.check_answer))
                    }
                }
            }
        }
    }
}