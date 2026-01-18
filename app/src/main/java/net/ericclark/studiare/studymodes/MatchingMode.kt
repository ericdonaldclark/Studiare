package net.ericclark.studiare.studymodes

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.data.*
import kotlinx.coroutines.delay
import kotlin.math.floor

@Composable
fun MatchingScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val state = viewModel.studyState ?: return
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current

    var incorrectMatchTrigger by remember { mutableStateOf<Pair<Pair<String, String>, Pair<String, String>>?>(null) }
    LaunchedEffect(state.incorrectlyMatchedPair) {
        if (state.incorrectlyMatchedPair != null) {
            incorrectMatchTrigger = state.incorrectlyMatchedPair
            delay(1000)
            incorrectMatchTrigger = null
        }
    }

    if (state.isComplete) {
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
        return
    }

    LaunchedEffect(state.currentCardIndex, size) {
        if (size.height > 0 && state.studyMode == "Matching") {
            val buttonHeight = with(density) { 60.dp.toPx() }
            val spacing = with(density) { 8.dp.toPx() }
            val totalItemHeight = buttonHeight + spacing
            val cardsPerColumn = (floor(size.height / totalItemHeight).toInt() - 1).coerceAtLeast(1)

            // --- UPDATED: Fix for Resume Bug ---
            // Only start a new round if the screen is empty.
            // If resuming, matchingCardsOnScreen will already be populated from the session.
            if (state.matchingCardsOnScreen.isEmpty()) {
                viewModel.startNewMatchingRound(cardsPerColumn)
            }
        }
    }

    LaunchedEffect(state.successfullyMatchedPairs.size, state.matchingCardsOnScreen.size) {
        if (state.matchingCardsOnScreen.isNotEmpty() && state.successfullyMatchedPairs.size == state.matchingCardsOnScreen.size) {
            delay(500)
            viewModel.advanceMatchingRound()
        }
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("${state.deckWithCards.deck.name} - Matching") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.Default.ArrowBack, "Back") }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { size = it }
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (state.matchingCardsOnScreen.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        state.matchingCardsOnScreen.forEach { card ->
                            MatchingButton(
                                card = card,
                                side = "front",
                                state = state,
                                incorrectMatchTrigger = incorrectMatchTrigger,
                                onClick = { viewModel.selectMatchingItem(card.id, "front") }
                            )
                        }
                    }
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically)
                    ) {
                        val shuffledBacks = remember(state.matchingCardsOnScreen) {
                            state.matchingCardsOnScreen.shuffled()
                        }
                        shuffledBacks.forEach { card ->
                            MatchingButton(
                                card = card,
                                side = "back",
                                state = state,
                                incorrectMatchTrigger = incorrectMatchTrigger,
                                onClick = { viewModel.selectMatchingItem(card.id, "back") }
                            )
                        }
                    }
                }
            }

            if (state.matchingCardsPerColumn > 0 && state.shuffledCards.isNotEmpty()) {
                val totalPages = (state.shuffledCards.size + state.matchingCardsPerColumn - 1) / state.matchingCardsPerColumn
                val currentPage = (state.currentCardIndex / state.matchingCardsPerColumn) + 1
                Text(
                    text = "Page $currentPage of $totalPages",
                    modifier = Modifier
                        .align(Alignment.CenterHorizontally)
                        .padding(bottom = 16.dp),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        }
    }
}

@Composable
fun MatchingButton(
    card: net.ericclark.studiare.data.Card,
    side: String,
    state: net.ericclark.studiare.data.StudyState,
    incorrectMatchTrigger: Pair<Pair<String, String>, Pair<String, String>>?,
    onClick: () -> Unit
) {
    val text = if (side == "front") card.front else card.back
    val notes = if (side == "front") card.frontNotes else card.backNotes
    val isSelected = state.selectedMatchingItem?.first == card.id && state.selectedMatchingItem?.second == side
    val isMatched = card.id in state.successfullyMatchedPairs

    val isRevealed = card.id in state.matchingRevealPair

    val isIncorrectlyTriggered = incorrectMatchTrigger?.let {
        (it.first.first == card.id && it.first.second == side) || (it.second.first == card.id && it.second.second == side)
    } ?: false

    val correctColor = Color(0xFF22C55E)
    val incorrectColor = MaterialTheme.colorScheme.error
    val selectedColor = MaterialTheme.colorScheme.primary
    val defaultColor = MaterialTheme.colorScheme.surfaceVariant

    val targetColor = when {
        isMatched || isRevealed -> correctColor // Treat Revealed same as Matched for color
        isIncorrectlyTriggered -> incorrectColor
        isSelected -> selectedColor
        else -> defaultColor
    }
    val color by animateColorAsState(targetValue = targetColor, animationSpec = tween(durationMillis = 300), label = "button color")

    val textColor = when {
        isMatched || isIncorrectlyTriggered || isSelected -> Color.White
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val buttonAlpha = if (isMatched) 0f else 1f
    val alphaAnim = animateFloatAsState(targetValue = buttonAlpha, animationSpec = tween(delayMillis = 200), label = "alpha animation")

    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 60.dp) // Give a min height for calculation
            .graphicsLayer(alpha = alphaAnim.value),
        shape = RoundedCornerShape(12.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color),
        contentPadding = PaddingValues(12.dp)
    ) {
        Text(
            text = buildAnnotatedString {
                append(text)
                if (!notes.isNullOrBlank()) {
                    withStyle(style = SpanStyle(fontStyle = FontStyle.Italic, fontSize = 12.sp)) {
                        append("\n($notes)")
                    }
                }
            },
            color = textColor,
            textAlign = TextAlign.Center
        )
    }
}