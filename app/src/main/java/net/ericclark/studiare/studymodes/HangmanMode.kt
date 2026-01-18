package net.ericclark.studiare.studymodes

import androidx.compose.foundation.Canvas
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
import androidx.compose.material.icons.Icons
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.EditCardDialog
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.screens.FlowRow
import net.ericclark.studiare.QuizCardContent
import net.ericclark.studiare.StudyCompletionScreen
import net.ericclark.studiare.data.*
import kotlinx.coroutines.delay
import kotlin.text.isLetter

@Composable
fun HangmanNavigationRow(
    currentIndex: Int,
    totalCards: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    showNext: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        // Previous Button
        IconButton(
            onClick = onPrev,
            enabled = currentIndex > 0
        ) {
            Icon(
                Icons.Default.KeyboardArrowLeft,
                contentDescription = "Previous",
                tint = if (currentIndex > 0) MaterialTheme.colorScheme.onSurface else Color.Transparent
            )
        }

        // Count Text
        Text(
            text = "${currentIndex + 1} / $totalCards",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Next Button (Only show if solved/revealed)
        IconButton(
            onClick = onNext,
            enabled = showNext
        ) {
            Icon(
                Icons.Default.KeyboardArrowRight,
                contentDescription = "Next",
                tint = if (showNext) MaterialTheme.colorScheme.onSurface else Color.Transparent
            )
        }
    }
}
@Composable
fun HangmanScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val state = viewModel.studyState ?: return
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var showEditDialog by remember { mutableStateOf(false) }

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
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
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

    Scaffold(
        modifier = Modifier.imePadding(),
        topBar = {
            CustomTopAppBar(
                title = { Text("${state.deckWithCards.deck.name} - Hangman") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.endStudySession(); navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back"
                        )
                    }
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
                LandscapeHangmanLayout(state, viewModel, focusRequester)
            } else {
                PortraitHangmanLayout(state, viewModel, focusRequester)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PortraitHangmanLayout(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel, focusRequester: FocusRequester) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {

        // 1. Top Section: Card (Left) & Drawing (Right)
        Row(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Left Column: Card + Nav
            Column(
                modifier = Modifier.weight(1f).fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    QuizCardContent(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth().aspectRatio(1f),
                        showNavigation = false, // Hide internal nav
                        isCompact = true
                    )
                }
                // External Nav
                HangmanNavigationRow(
                    currentIndex = state.currentCardIndex,
                    totalCards = state.shuffledCards.size,
                    onPrev = { viewModel.previousCard() },
                    onNext = { viewModel.nextCard() },
                    showNext = state.correctAnswerFound
                )
            }

            // Right Box: Hangman Drawing
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                HangmanDrawing(mistakes = state.hangmanMistakes, fingersAndToes = state.fingersAndToes)
            }
        }

        Spacer(Modifier.height(16.dp))

        // 2. Middle Section: Misses (Full Width)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Misses", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                val card = state.shuffledCards[state.currentCardIndex]
                val answerText = if (state.quizPromptSide == "Front") card.back else card.front
                val incorrectGuesses = state.guessedLetters.filter { !answerText.contains(it, ignoreCase = true) }.sorted()

                FlowRow(horizontalArrangement = Arrangement.Center) {
                    if (incorrectGuesses.isEmpty()) {
                        Text(
                            "-",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        incorrectGuesses.forEach { char ->
                            Text(
                                text = "$char ",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))

        // 3. Bottom Section: Input & Controls
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            HangmanInput(state = state, focusRequester = focusRequester, viewModel = viewModel)

            Spacer(Modifier.height(24.dp))

            if (state.correctAnswerFound) {
                Button(onClick = { viewModel.nextCard() }, modifier = Modifier.fillMaxWidth(0.8f)) {
                    Text("Next Card")
                }
            } else {
                Button(onClick = { viewModel.revealQuizAnswer() }, modifier = Modifier.fillMaxWidth(0.8f)) {
                    Text("Get Answer")
                }
            }
        }
    }
}

@Composable
fun LandscapeHangmanLayout(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel, focusRequester: FocusRequester) {
    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Left Column: Card + Nav + Misses
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    QuizCardContent(
                        state = state,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxSize(),
                        showNavigation = false,
                        isCompact = true
                    )
                }
                HangmanNavigationRow(
                    currentIndex = state.currentCardIndex,
                    totalCards = state.shuffledCards.size,
                    onPrev = { viewModel.previousCard() },
                    onNext = { viewModel.nextCard() },
                    showNext = state.correctAnswerFound
                )
            }

            Card(
                modifier = Modifier.weight(0.6f).fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Misses", style = MaterialTheme.typography.labelSmall)
                    val card = state.shuffledCards[state.currentCardIndex]
                    val answerText = if (state.quizPromptSide == "Front") card.back else card.front
                    val incorrectGuesses = state.guessedLetters.filter { !answerText.contains(it, ignoreCase = true) }.sorted()
                    Text(incorrectGuesses.joinToString(" "), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }

        Spacer(Modifier.width(16.dp))

        // Center: Word & Controls
        Column(modifier = Modifier.weight(1.5f), horizontalAlignment = Alignment.CenterHorizontally) {
            HangmanInput(state = state, focusRequester = focusRequester, viewModel = viewModel)
            Spacer(Modifier.height(16.dp))
            if (state.correctAnswerFound) {
                Button(onClick = { viewModel.nextCard() }) { Text("Next Card") }
            } else {
                Button(onClick = { viewModel.revealQuizAnswer() }) { Text("Get Answer") }
            }
        }

        Spacer(Modifier.width(16.dp))

        // Right: Drawing
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            HangmanDrawing(mistakes = state.hangmanMistakes, fingersAndToes = state.fingersAndToes)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HangmanInput(state: net.ericclark.studiare.data.StudyState, focusRequester: FocusRequester, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val keyboardController = LocalSoftwareKeyboardController.current
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == "Front") card.back else card.front

    // Invisible Input to capture key presses
    // We pass a dummy value because we handle state accumulation in ViewModel
    var textInput by remember { mutableStateOf("") }

    val maxMistakes = if (state.fingersAndToes) 27 else 7
    val isWin = state.correctAnswerFound && state.hangmanMistakes < maxMistakes

    BasicTextField(
        value = textInput,
        onValueChange = { newValue ->
            if (!state.correctAnswerFound) {
                val char = newValue.lastOrNull()
                if (char != null && char.isLetter()) {
                    viewModel.submitHangmanGuess(char)
                }
                textInput = "" // clear immediately
            }
        },
        modifier = Modifier
            .focusRequester(focusRequester)
            .alpha(0f)
            .size(1.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
    )

    // Visual Word Display
    Box(
        modifier = Modifier.clickable {
            if(!state.correctAnswerFound) {
                focusRequester.requestFocus()
                keyboardController?.show()
            }
        }
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val words = answerText.split(' ')
            words.forEachIndexed { index, word ->
                Row {
                    word.forEach { char ->
                        val isGuessed =
                            state.guessedLetters.contains(char.uppercaseChar()) || !char.isLetter() || state.correctAnswerFound
                        val displayChar = if (isGuessed) char.toString().uppercase() else "_"
                        val color = when {
                            isWin -> Color(0xFF22C55E) // Green if won
                            state.correctAnswerFound && !state.guessedLetters.contains(char.uppercaseChar()) && char.isLetter() -> MaterialTheme.colorScheme.error // Red if missed (Loss)
                            else -> LocalContentColor.current
                        }

                        Text(
                            text = "$displayChar ",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = color,
                            modifier = Modifier.padding(horizontal = 2.dp)
                        )
                    }
                }
                if (index < words.size - 1) {
                    Spacer(modifier = Modifier.width(24.dp)) // Space between words
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