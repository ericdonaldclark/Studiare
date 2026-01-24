package net.ericclark.studiare.studymodes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.data.*
import kotlinx.coroutines.launch

/**
 * The main screen for the Flashcard study mode.
 * @param navController The NavController for navigating back.
 * @param viewModel The ViewModel providing the study state.
 */
@Composable
fun FlashcardScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val state = viewModel.studyState ?: return
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
                    }) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    // Button to flip the front and back
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = state.isCardRevealed
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Card")
                    }
                    IconButton(onClick = { viewModel.flipStudyMode() }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = "Flip Front and Back")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(modifier = Modifier.padding(padding).fillMaxSize()) {
            val isLandscape =this.maxWidth > 600.dp
            // Use different layouts for portrait and landscape orientations
            if (isLandscape) {
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
fun PortraitFlashcardLayout(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val card = state.shuffledCards[state.currentCardIndex]
    var difficulty by remember(card) { mutableStateOf(card.difficulty) }

    val frontText = if (state.isFlipped) card.back else card.front
    val frontNotes = if (state.isFlipped) card.backNotes else card.frontNotes
    val backText = if (state.isFlipped) card.front else card.back
    val backNotes = if (state.isFlipped) card.frontNotes else card.backNotes

    // --- MODIFIED: Set card color based on which side is showing ---
    val cardColor = if (state.showFront) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (state.showFront) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            // The main flashcard area
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1.6f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardColor)
                    .clickable { viewModel.flipCard() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    val textToShow = if (state.showFront) frontText else backText
                    val notesToShow = if (state.showFront) frontNotes else backNotes
                    Text(text = textToShow, fontSize = 32.sp, textAlign = TextAlign.Center, color = textColor)
                    if (!notesToShow.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(text = "($notesToShow)", fontSize = 18.sp, textAlign = TextAlign.Center, fontStyle = FontStyle.Italic, color = textColor)
                    }
                }
                // Nav buttons
                if (state.currentCardIndex > 0) {
                    StudyCardNavButton(
                        onClick = { viewModel.previousCard() },
                        icon = {
                            Icon(
                                Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Previous Card"
                            )
                        },
                        modifier = Modifier.align(Alignment.CenterStart).padding(8.dp)
                    )
                }
                // Show Next button arrow ONLY if NOT graded or if card not revealed yet
                if (!state.isGraded) {
                    StudyCardNavButton(
                        onClick = { viewModel.nextCard() },
                        icon = {
                            Icon(
                                Icons.Default.KeyboardArrowRight,
                                contentDescription = "Next Card"
                            )
                        },
                        modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
            // Progress indicator
            Text("${state.currentCardIndex + 1} / ${state.shuffledCards.size}")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            // Difficulty slider
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
            Spacer(Modifier.height(16.dp))
            // BUTTON LOGIC:
            // If Graded AND Card is flipped (Revealed) -> Show Correct/Incorrect buttons
            if (state.schedulingMode == "Spaced Repetition" && state.isCardRevealed) {
                // FSRS Grading Buttons (Only show when answer is revealed)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.submitFsrsGrade(1) }, // Again
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xffb82741)),
                            modifier = Modifier.weight(1f)
                        ) { Text("Again") }

                        Button(
                            onClick = { viewModel.submitFsrsGrade(2) }, // Hard
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xfffcba03)), // Orange-ish
                            modifier = Modifier.weight(1f)
                        ) { Text("Hard") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.submitFsrsGrade(3) }, // Good
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xff488c4b)), // Green
                            modifier = Modifier.weight(1f)
                        ) { Text("Good") }

                        Button(
                            onClick = { viewModel.submitFsrsGrade(4) }, // Easy
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xff4287f5)),
                            modifier = Modifier.weight(1f)
                        ) { Text("Easy") }
                    }
                }
            }
            else if (state.isGraded && !state.showFront) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Button(
                        onClick = { viewModel.submitSelfGradedResult(false) },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.weight(1f)
                    ) { Text("Incorrect") }

                    Button(
                        onClick = { viewModel.submitSelfGradedResult(true) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF22C55E)), // Green
                        modifier = Modifier.weight(1f)
                    ) { Text("Correct") }
                }
            } else {
                // Standard behavior
                Button(
                    onClick = {
                        if (state.isCardRevealed) {
                            viewModel.nextCard()
                        } else {
                            viewModel.flipCard()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(0.8f)
                ) { Text(if (state.isCardRevealed) "Next Card" else "Flip Card") }
            }
        }
    }
}

/**
 * The landscape layout for the Flashcard study screen.
 * @param state The current study state.
 * @param viewModel The ViewModel providing business logic.
 */
@Composable
fun LandscapeFlashcardLayout(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val card = state.shuffledCards[state.currentCardIndex]
    var difficulty by remember(card) { mutableStateOf(card.difficulty) }

    val frontText = if (state.isFlipped) card.back else card.front
    val frontNotes = if (state.isFlipped) card.backNotes else card.frontNotes
    val backText = if (state.isFlipped) card.front else card.back
    val backNotes = if (state.isFlipped) card.frontNotes else card.backNotes

    // --- MODIFIED: Set card color based on which side is showing ---
    val cardColor = if (state.showFront) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
    val textColor = if (state.showFront) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Left column for the flashcard
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize() // UPDATED: Fills the left pane instead of forcing 1.6 AR
                    .clip(RoundedCornerShape(16.dp))
                    .background(cardColor)
                    .clickable { viewModel.flipCard() },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(32.dp)
                ) {
                    val textToShow = if (state.showFront) frontText else backText
                    val notesToShow = if (state.showFront) frontNotes else backNotes
                    Text(text = textToShow, fontSize = 32.sp, textAlign = TextAlign.Center, color = textColor)
                    if (!notesToShow.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(text = "($notesToShow)", fontSize = 18.sp, textAlign = TextAlign.Center, fontStyle = FontStyle.Italic, color = textColor)
                    }
                }
                if (state.currentCardIndex > 0) {
                    StudyCardNavButton(
                        onClick = { viewModel.previousCard() },
                        icon = {
                            Icon(
                                Icons.Default.KeyboardArrowLeft,
                                contentDescription = "Previous Card"
                            )
                        },
                        modifier = Modifier.align(Alignment.CenterStart).padding(8.dp)
                    )
                }
                StudyCardNavButton(
                    onClick = { viewModel.nextCard() },
                    icon = {
                        Icon(
                            Icons.Default.KeyboardArrowRight,
                            contentDescription = "Next Card"
                        )
                    },
                    modifier = Modifier.align(Alignment.CenterEnd).padding(8.dp)
                )
            }
            Spacer(Modifier.height(16.dp))
            Text("${state.currentCardIndex + 1} / ${state.shuffledCards.size}")
        }

        Spacer(Modifier.width(16.dp))

        // Right column for controls
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
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
            Spacer(Modifier.height(16.dp))
            // BUG FIX: The logic for the button's text and action is now corrected.
            Button(
                onClick = {
                    if (state.isCardRevealed) {
                        viewModel.nextCard()
                    } else {
                        viewModel.flipCard()
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) { Text(if (state.isCardRevealed) "Next Card" else "Flip Card") }
        }
    }
}

/**
 * A new screen for the Flashcard Quiz mode.
 * Displays the card prompt at the top and a scrollable picker list at the bottom.
 */
@Composable
fun FlashcardQuizScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val state = viewModel.studyState ?: return
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

    // Scroll state for the Rolodex picker list
    val listState = rememberLazyListState()

    // Currently selected answer in the picker (locally)
    var selectedPickerOption by remember { mutableStateOf<String?>(null) }
    // Flag to track if we should auto-scroll (only on "Get Answer")
    var scrollOnReveal by remember { mutableStateOf(false) }

    // Reset selection and scroll flag when card changes
    LaunchedEffect(state.currentCardIndex) {
        selectedPickerOption = null
        scrollOnReveal = false
    }

    // Auto-scroll to correct answer ONLY if "Get Answer" was used
    LaunchedEffect(state.correctAnswerFound) {
        if (state.correctAnswerFound && scrollOnReveal) {
            val card = state.shuffledCards.getOrNull(state.currentCardIndex)
            if (card != null) {
                val correct = if (state.quizPromptSide == "Front") card.back else card.front
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
                title = { Text("${state.deckWithCards.deck.name} - Quiz") },
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
                    }
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
                    }
                )
            }
        }
    }
}

@Composable
fun PortraitFlashcardQuizLayout(
    state: net.ericclark.studiare.data.StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedPickerOption: String?,
    onOptionSelected: (String) -> Unit,
    onReveal: () -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        // 1. Prompt Area (Top)
        Column(
            modifier = Modifier
                .weight(0.4f)
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
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
            onReveal = onReveal
        )
    }
}

@Composable
fun LandscapeFlashcardQuizLayout(
    state: net.ericclark.studiare.data.StudyState,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedPickerOption: String?,
    onOptionSelected: (String) -> Unit,
    onReveal: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Left Column: Card
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize()
            )
        }

        Spacer(Modifier.width(16.dp))

        // Right Column: Picker List + Buttons
        Column(
            modifier = Modifier.weight(1f).fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
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
                onReveal = onReveal
            )
        }
    }
}

@Composable
fun PickerListContent(
    state: net.ericclark.studiare.data.StudyState,
    listState: androidx.compose.foundation.lazy.LazyListState,
    selectedPickerOption: String?,
    onOptionSelected: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // WRAPPER BOX: Provides BoxScope for alignment and overlays the scrollbar on the list
    Box(modifier = Modifier.fillMaxSize()) {

        // The List
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(state.pickerOptions) { option ->
                val isSelected = selectedPickerOption == option

                // If answer is found, highlight the correct one in Green
                val card = state.shuffledCards[state.currentCardIndex]
                val correct = if (state.quizPromptSide == "Front") card.back else card.front
                val isRealAnswer = option == correct

                // Check if this was the last incorrect guess
                val isWrongAnswer = !state.correctAnswerFound && state.lastIncorrectAnswer == option

                // Determine background color
                val bgColor = when {
                    state.correctAnswerFound && isRealAnswer -> Color(0xFF22C55E).copy(alpha = 0.3f)
                    isWrongAnswer -> MaterialTheme.colorScheme.error.copy(alpha = 0.1f)
                    isSelected -> MaterialTheme.colorScheme.primaryContainer
                    else -> Color.Transparent
                }

                val textColor = when {
                    state.correctAnswerFound && isRealAnswer -> Color(0xFF22C55E)
                    isWrongAnswer -> MaterialTheme.colorScheme.error
                    else -> LocalContentColor.current
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .clickable(enabled = !state.correctAnswerFound) {
                            onOptionSelected(option)
                        }
                        .padding(horizontal = 16.dp, vertical = 16.dp),
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
                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
        }

        // Custom Fast Scroll Slider
        val totalItems = state.pickerOptions.size
        if (totalItems > 1) {
            var barHeight by remember { mutableStateOf(0f) }
            val density = LocalDensity.current

            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(30.dp)
                    .align(Alignment.CenterEnd) // This now works because it is inside the parent Box
                    .padding(vertical = 8.dp)
                    .onSizeChanged { barHeight = it.height.toFloat() }
                    .pointerInput(totalItems, barHeight) {
                        detectVerticalDragGestures(
                            onDragStart = { offset ->
                                if (barHeight > 0) {
                                    val percentage = (offset.y / barHeight).coerceIn(0f, 1f)
                                    val index = (percentage * (totalItems - 1)).toInt()
                                    coroutineScope.launch { listState.scrollToItem(index) }
                                }
                            },
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

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(y = scrollOffsetDp)
                            .width(6.dp)
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
    onReveal: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        if (state.correctAnswerFound) {
            Button(
                onClick = { viewModel.nextCard() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Next Card")
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Get Answer Button (Left)
                OutlinedButton(
                    onClick = onReveal,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Get Answer")
                }

                // Check Answer Button (Right)
                Button(
                    onClick = {
                        selectedPickerOption?.let {
                            viewModel.submitFlashcardQuizAnswer(it)
                        }
                    },
                    modifier = Modifier.weight(1f),
                    enabled = selectedPickerOption != null
                ) {
                    Text("Check Answer")
                }
            }
        }
    }
}