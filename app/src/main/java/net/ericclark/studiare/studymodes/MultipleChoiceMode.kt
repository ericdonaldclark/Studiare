package net.ericclark.studiare.studymodes

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.data.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.collections.get
import kotlin.collections.mapNotNull

/**
 * The main screen for the Multiple Choice study mode.
 * @param navController The NavController for navigating back.
 * @param viewModel The ViewModel providing the study state.
 */
@Composable
fun MultipleChoiceScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
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

    val context = LocalContext.current
    val toastMessage = viewModel.toastMessage
    // Show a toast message for incorrect answers
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    // --- MODIFIED: Trigger option generation when card changes ---
    LaunchedEffect(state.currentCardIndex) {
        viewModel.generateOptionsForCurrentCardIfNeeded()
    }

    if (state.isComplete) {
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
        return
    }

    // Show an error message if there are not enough cards for this mode
    if (state.deckWithCards.cards.size < state.numberOfAnswers) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
                Text("Not enough cards for multiple choice.", textAlign = TextAlign.Center, style = MaterialTheme.typography.titleLarge)
                Text("You need at least ${state.numberOfAnswers} cards in a deck to use this mode.", textAlign = TextAlign.Center)
                Spacer(Modifier.height(16.dp))
                Button(onClick = {
                    viewModel.endStudySession()
                    navController.popBackStack("deckList", false)
                }) { Text("Back to Decks") }
            }
        }
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
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = state.correctAnswerFound
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
            if (isLandscape) {
                LandscapeMCLayout(state = state, viewModel = viewModel)
            } else {
                PortraitMCLayout(state = state, viewModel = viewModel)
            }
        }
    }
}

/**
 * The portrait layout for the Multiple Choice study screen.
 * @param state The current study state.
 * @param viewModel The ViewModel providing business logic.
 */
@Composable
fun PortraitMCLayout(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val card = state.shuffledCards[state.currentCardIndex]
    var difficulty by remember(card) { mutableStateOf(card.difficulty) }

    val scope = rememberCoroutineScope()
    var processingClick by remember { mutableStateOf(false) }

    LaunchedEffect(state.currentCardIndex) {
        processingClick = false
    }

    val options = remember(state.currentCardIndex, state.mcOptions) {
        val cardId = state.shuffledCards.getOrNull(state.currentCardIndex)?.id
        val optionIds = state.mcOptions[cardId]
        if (cardId != null && optionIds != null) {
            optionIds.mapNotNull { id -> state.deckWithCards.cards.find { card -> card.id == id } }
        } else {
            emptyList()
        }
    }

    val promptText = if (state.isFlipped) card.back else card.front
    val promptNotes = if (state.isFlipped) card.backNotes else card.frontNotes
    val cardColor = if (state.isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (state.isFlipped) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(16.dp)).background(cardColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text(text = promptText, fontSize = 32.sp, textAlign = TextAlign.Center, color = textColor)
                    if (!promptNotes.isNullOrBlank()) {
                        Spacer(Modifier.height(8.dp))
                        Text(text = "($promptNotes)", fontSize = 18.sp, textAlign = TextAlign.Center, fontStyle = FontStyle.Italic, color = textColor)
                    }
                }
                if (state.currentCardIndex > 0) {
                    StudyCardNavButton(onClick = { viewModel.previousCard() }, icon = { Icon(Icons.Default.KeyboardArrowLeft, "Previous") }, modifier = Modifier.align(Alignment.CenterStart).padding(8.dp))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text("${state.currentCardIndex + 1} / ${state.shuffledCards.size}")
            Spacer(Modifier.height(16.dp))
            options.forEach { optionCard -> AnswerButton(optionCard = optionCard, state = state, viewModel = viewModel) }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            if (state.correctAnswerFound) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    DifficultySlider(label = "Rate Difficulty", difficulty = difficulty, onDifficultyChange = { difficulty = it; viewModel.updateCardDifficulty(card, it) }, modifier = Modifier.weight(1f))
                    MarkKnownButton(isKnown = card.isKnown, onClick = { viewModel.toggleCardKnownStatus(card) })
                }
            }
            Spacer(Modifier.height(16.dp))

            if (state.schedulingMode == "Spaced Repetition" && state.correctAnswerFound) {
                val isWrong = state.incorrectCardIds.contains(card.id)
                if (!isWrong) {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { if(!processingClick) { processingClick = true; scope.launch { delay(150); viewModel.submitFsrsGrade(2) } } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xfffcba03)), modifier = Modifier.weight(1f), enabled = !processingClick) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(text = state.nextIntervals[2] ?: "", style = MaterialTheme.typography.labelSmall); Text("Hard") }
                            }
                            Button(onClick = { if(!processingClick) { processingClick = true; scope.launch { delay(150); viewModel.submitFsrsGrade(3) } } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xff488c4b)), modifier = Modifier.weight(1f), enabled = !processingClick) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(text = state.nextIntervals[3] ?: "", style = MaterialTheme.typography.labelSmall); Text("Good") }
                            }
                            Button(onClick = { if(!processingClick) { processingClick = true; scope.launch { delay(150); viewModel.submitFsrsGrade(4) } } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xff4287f5)), modifier = Modifier.weight(1f), enabled = !processingClick) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(text = state.nextIntervals[4] ?: "", style = MaterialTheme.typography.labelSmall); Text("Easy") }
                            }
                        }
                    }
                } else {
                    Button(onClick = { viewModel.nextCard() }, modifier = Modifier.fillMaxWidth(0.8f)) { Text("Next Card") }
                }
            } else {
                // Normal Mode or Incorrect FSRS
                Button(onClick = { viewModel.nextCard() }, modifier = Modifier.fillMaxWidth(0.8f), enabled = state.correctAnswerFound) { Text("Next Card") }
            }
        }
    }
}

// LandscapeMCLayout uses same logic structure (updated in full file)
@Composable
fun LandscapeMCLayout(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val card = state.shuffledCards[state.currentCardIndex]
    var difficulty by remember(card) { mutableStateOf(card.difficulty) }
    val scope = rememberCoroutineScope()
    var processingClick by remember { mutableStateOf(false) }
    LaunchedEffect(state.currentCardIndex) { processingClick = false }

    val options = remember(state.currentCardIndex, state.mcOptions) {
        val cardId = state.shuffledCards.getOrNull(state.currentCardIndex)?.id
        state.mcOptions[cardId]?.mapNotNull { id -> state.deckWithCards.cards.find { it.id == id } } ?: emptyList()
    }

    val promptText = if (state.isFlipped) card.back else card.front
    val promptNotes = if (state.isFlipped) card.backNotes else card.frontNotes
    val cardColor = if (state.isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (state.isFlipped) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Column(modifier = Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(16.dp)).background(cardColor), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(32.dp)) {
                    Text(text = promptText, fontSize = 32.sp, textAlign = TextAlign.Center, color = textColor)
                    if (!promptNotes.isNullOrBlank()) { Spacer(Modifier.height(8.dp)); Text(text = "($promptNotes)", fontSize = 18.sp, textAlign = TextAlign.Center, fontStyle = FontStyle.Italic, color = textColor) }
                }
                if (state.currentCardIndex > 0) StudyCardNavButton(onClick = { viewModel.previousCard() }, icon = { Icon(Icons.Default.KeyboardArrowLeft, "Previous") }, modifier = Modifier.align(Alignment.CenterStart).padding(8.dp))
            }
            Spacer(Modifier.height(16.dp)); Text("${state.currentCardIndex + 1} / ${state.shuffledCards.size}")
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f).fillMaxHeight().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceBetween) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) { options.forEach { optionCard -> AnswerButton(optionCard = optionCard, state = state, viewModel = viewModel) } }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                if (state.correctAnswerFound) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        DifficultySlider(label = "Rate Difficulty", difficulty = difficulty, onDifficultyChange = { difficulty = it; viewModel.updateCardDifficulty(card, it) }, modifier = Modifier.weight(1f))
                        MarkKnownButton(isKnown = card.isKnown, onClick = { viewModel.toggleCardKnownStatus(card) })
                    }
                }
                Spacer(Modifier.height(16.dp))
                if (state.schedulingMode == "Spaced Repetition" && state.correctAnswerFound) {
                    if (!state.incorrectCardIds.contains(card.id)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                            Button(onClick = { if(!processingClick) { processingClick = true; scope.launch { delay(150); viewModel.submitFsrsGrade(2) } } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xfffcba03)), modifier = Modifier.weight(1f), enabled = !processingClick) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.nextIntervals[2] ?: "", style = MaterialTheme.typography.labelSmall); Text("Hard") } }
                            Button(onClick = { if(!processingClick) { processingClick = true; scope.launch { delay(150); viewModel.submitFsrsGrade(3) } } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xff488c4b)), modifier = Modifier.weight(1f), enabled = !processingClick) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.nextIntervals[3] ?: "", style = MaterialTheme.typography.labelSmall); Text("Good") } }
                            Button(onClick = { if(!processingClick) { processingClick = true; scope.launch { delay(150); viewModel.submitFsrsGrade(4) } } }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xff4287f5)), modifier = Modifier.weight(1f), enabled = !processingClick) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(state.nextIntervals[4] ?: "", style = MaterialTheme.typography.labelSmall); Text("Easy") } }
                        }
                    } else {
                        Button(onClick = { viewModel.nextCard() }, modifier = Modifier.fillMaxWidth(0.8f)) { Text("Next Card") }
                    }
                } else {
                    Button(onClick = { viewModel.nextCard() }, modifier = Modifier.fillMaxWidth(0.8f), enabled = state.correctAnswerFound) { Text("Next Card") }
                }
            }
        }
    }
}

@Composable
fun AnswerButton(optionCard: net.ericclark.studiare.data.Card, state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val currentCard = state.shuffledCards[state.currentCardIndex]
    val optionText = if (state.isFlipped) optionCard.front else optionCard.back
    val optionNotes = if (state.isFlipped) optionCard.frontNotes else optionCard.backNotes

    val correctAnswerText = if (state.isFlipped) currentCard.front else currentCard.back

    val isCorrect = optionText == correctAnswerText
    val hasBeenSelectedIncorrectly = state.wrongSelections.contains(optionText)

    val color = when {
        state.correctAnswerFound && isCorrect -> Color(0xFF22C55E) // Green for correct
        hasBeenSelectedIncorrectly -> Color(0xFFEF4444) // Red for incorrect
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    val textColor = when {
        (state.correctAnswerFound && isCorrect) || hasBeenSelectedIncorrectly -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Button(
        onClick = {
            if (state.correctAnswerFound) {
                if (!isCorrect) {
                    viewModel.getIncorrectCardInfo(optionText)
                }
            } else {
                viewModel.selectAnswer(optionText)
            }
        },
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        enabled = if (state.correctAnswerFound) {
            !isCorrect
        } else {
            !hasBeenSelectedIncorrectly
        },
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            disabledContainerColor = color,
            contentColor = textColor,
            disabledContentColor = textColor
        )
    ) {
        Text(
            text = buildAnnotatedString {
                append(optionText)
                if (!optionNotes.isNullOrBlank()) {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic, fontSize = 12.sp)) {
                        append(" ($optionNotes)")
                    }
                }
            }
        )
    }
}