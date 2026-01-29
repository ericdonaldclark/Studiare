package net.ericclark.studiare.studymodes

import android.Manifest
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.data.*
import java.util.Locale



@Composable
fun AudioStudyScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val state = viewModel.studyState ?: return
    val context = LocalContext.current

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, "Audio permission needed for Speech-to-Text", Toast.LENGTH_LONG).show()
            }
        }
    )

    // Request Permission on Start if STT is enabled
    LaunchedEffect(Unit) {
        if (state.enableStt) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    DisposableEffect(Unit) {
        viewModel.bindAudioService()
        onDispose { viewModel.unbindAudioService() }
    }

    val currentIndex by viewModel.audioCardIndex.collectAsState()
    val isFlipped by viewModel.audioIsFlipped.collectAsState()
    val isPlaying by viewModel.audioIsPlaying.collectAsState()
    val isListening by viewModel.audioIsListening.collectAsState()
    val feedbackMessage by viewModel.audioFeedback.collectAsState()
    val waitingForGrade by viewModel.audioWaitingForGrade.collectAsState()

    var answerDelay by remember { mutableStateOf(2.0) }
    var nextCardDelay by remember { mutableStateOf(2.0) }
    var continuousPlay by remember { mutableStateOf(true) }

    LaunchedEffect(answerDelay, nextCardDelay, continuousPlay) {
        viewModel.updateAudioDelays(answerDelay, nextCardDelay)
        viewModel.setAudioContinuousPlay(continuousPlay)
    }

    val currentCard = state.shuffledCards.getOrNull(currentIndex)

    // Determine Reveal Button Visibility
    val promptIsFront = state.quizPromptSide == "Front"
    val isBackShowing = isFlipped
    // If prompt is Front, Answer is Back. If Back is showing, Answer is showing.
    // If prompt is Back, Answer is Front. If Back is NOT showing, Answer is showing.
    val isShowingAnswer = if (promptIsFront) isBackShowing else !isBackShowing

    val showRevealButton = state.enableStt && state.hideAnswerText && !isShowingAnswer

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("Audio Study") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.Default.ArrowBack, "Back") }
                },
                actions = {
                    var showSettings by remember { mutableStateOf(false) }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, "Audio Settings")
                    }

                    if (showSettings) {
                        AudioSettingsDialog(
                            answerDelay = answerDelay,
                            onAnswerDelayChange = { answerDelay = it },
                            nextCardDelay = nextCardDelay,
                            onNextCardDelayChange = { nextCardDelay = it },
                            continuousPlay = continuousPlay,
                            onContinuousPlayChange = { continuousPlay = it },
                            onDismiss = { showSettings = false }
                        )
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val isWideScreen = this.maxWidth > 600.dp

            if (currentCard != null) {
                if (isWideScreen) {
                    LandscapeAudioLayout(
                        card = currentCard, isFlipped = isFlipped, currentIndex = currentIndex,
                        totalCards = state.shuffledCards.size, isPlaying = isPlaying,
                        onTogglePlay = { viewModel.toggleAudioPlayPause() },
                        onNext = { viewModel.skipAudioNext() },
                        onPrev = { viewModel.skipAudioPrevious() },
                        isListening = isListening,
                        feedback = feedbackMessage,
                        waitingForGrade = waitingForGrade,
                        onRateCard = { rating -> viewModel.submitAudioFsrsGrade(rating) },
                        onSkipStt = { viewModel.skipAudioStt() },
                        showRevealButton = showRevealButton,
                        onReveal = { viewModel.revealAudioAnswer() }
                    )
                } else {
                    PortraitAudioLayout(
                        card = currentCard, isFlipped = isFlipped, currentIndex = currentIndex,
                        totalCards = state.shuffledCards.size, isPlaying = isPlaying,
                        onTogglePlay = { viewModel.toggleAudioPlayPause() },
                        onNext = { viewModel.skipAudioNext() },
                        onPrev = { viewModel.skipAudioPrevious() },
                        isListening = isListening,
                        feedback = feedbackMessage,
                        waitingForGrade = waitingForGrade,
                        onRateCard = { rating -> viewModel.submitAudioFsrsGrade(rating) },
                        onSkipStt = { viewModel.skipAudioStt() },
                        showRevealButton = showRevealButton,
                        onReveal = { viewModel.revealAudioAnswer() }
                    )
                }
            } else {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Session Complete")
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = {
                            viewModel.endStudySession()
                            navController.popBackStack()
                        }) {
                            Text("Back to Decks")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PortraitAudioLayout(
    card: net.ericclark.studiare.data.Card, isFlipped: Boolean, currentIndex: Int, totalCards: Int, isPlaying: Boolean,
    onTogglePlay: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    isListening: Boolean, feedback: String?,
    waitingForGrade: Boolean, onRateCard: (Int) -> Unit, // NEW Params
    onSkipStt: () -> Unit,
    showRevealButton: Boolean, onReveal: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AudioFlashcardView(
            card = card,
            isFlipped = isFlipped,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.6f)
        )

        Spacer(Modifier.height(16.dp))

        // Feedback / Listening Indicator / Buttons
        Box(modifier = Modifier.height(50.dp), contentAlignment = Alignment.Center) {
            if (waitingForGrade) {
                // FSRS Grading Buttons: Hard(2), Good(3), Easy(4)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = { onRateCard(2) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) { Text("Hard") }
                    Button(
                        onClick = { onRateCard(3) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text("Good") }
                    Button(
                        onClick = { onRateCard(4) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4))
                    ) { Text("Easy") }
                }
            } else if (feedback == "Tap to Retry") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onTogglePlay,
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text("Retry")
                    }
                    Spacer(Modifier.width(16.dp))
                    OutlinedButton(
                        onClick = onSkipStt,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Skip")
                    }
                }
            } else if (isListening || feedback == "Retrying..." || feedback == "Try Again") {
                // Show controls during active listening or between attempts
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isListening) {
                        Icon(Icons.Default.Mic, contentDescription = "Listening", tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(8.dp))
                        Text("Listening...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else {
                        // Display "Retrying..." or "Try Again"
                        Text(
                            text = feedback ?: "",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(16.dp))

                    if (showRevealButton) {
                        OutlinedButton(
                            onClick = onReveal,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Reveal")
                        }
                        Spacer(Modifier.width(8.dp))
                    }

                    OutlinedButton(
                        onClick = onSkipStt,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("Skip")
                    }
                }
            } else if (feedback != null) {
                // Success case or other feedback
                Text(feedback, style = MaterialTheme.typography.titleLarge, color = if (feedback == "Correct!") Color(0xFF22C55E) else MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.weight(1f))
        Text(text = "${currentIndex + 1} / $totalCards", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(32.dp))
        AudioControls(isPlaying, onTogglePlay, onNext, onPrev)
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
fun LandscapeAudioLayout(
    card: net.ericclark.studiare.data.Card, isFlipped: Boolean, currentIndex: Int, totalCards: Int, isPlaying: Boolean,
    onTogglePlay: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    isListening: Boolean, feedback: String?,
    waitingForGrade: Boolean, onRateCard: (Int) -> Unit, // NEW
    onSkipStt: () -> Unit,
    showRevealButton: Boolean, onReveal: () -> Unit
) {
    Row(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Left Column: Card
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            AudioFlashcardView(
                card = card,
                isFlipped = isFlipped,
                modifier = Modifier
                    .fillMaxSize()
            )
        }

        Spacer(Modifier.width(32.dp))

        // Right Column: Controls
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Feedback Area
            Box(modifier = Modifier.height(50.dp), contentAlignment = Alignment.Center) {
                if (waitingForGrade) {
                    // FSRS Grading Buttons: Hard(2), Good(3), Easy(4)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { onRateCard(2) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) { Text("Hard") }
                        Button(
                            onClick = { onRateCard(3) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) { Text("Good") }
                        Button(
                            onClick = { onRateCard(4) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4))
                        ) { Text("Easy") }
                    }
                } else if (feedback == "Tap to Retry") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onTogglePlay,
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 0.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Retry")
                        }
                        Spacer(Modifier.width(16.dp))
                        OutlinedButton(
                            onClick = onSkipStt,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Skip")
                        }
                    }
                } else if (isListening || feedback == "Retrying..." || feedback == "Try Again") {
                    // Show controls during active listening or between attempts
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isListening) {
                            Icon(Icons.Default.Mic, contentDescription = "Listening", tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(8.dp))
                            Text("Listening...", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        } else {
                            // Display "Retrying..." or "Try Again"
                            Text(
                                text = feedback ?: "",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(16.dp))

                        if (showRevealButton) {
                            OutlinedButton(
                                onClick = onReveal,
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Reveal")
                            }
                            Spacer(Modifier.width(8.dp))
                        }

                        OutlinedButton(
                            onClick = onSkipStt,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Skip")
                        }
                    }
                } else if (feedback != null) {
                    // Success case or other feedback
                    Text(feedback, style = MaterialTheme.typography.titleLarge, color = if (feedback == "Correct!") Color(0xFF22C55E) else MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(text = "${currentIndex + 1} / $totalCards", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(32.dp))
            AudioControls(isPlaying, onTogglePlay, onNext, onPrev)
        }
    }
}

@Composable
fun AudioControls(isPlaying: Boolean, onTogglePlay: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(24.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.FastRewind, contentDescription = "Previous Card", modifier = Modifier.size(32.dp))
        }

        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            )
        }

        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.FastForward, contentDescription = "Next Card", modifier = Modifier.size(32.dp))
        }
    }
}

@Composable
fun AudioSettingsDialog(
    answerDelay: Double,
    onAnswerDelayChange: (Double) -> Unit,
    nextCardDelay: Double,
    onNextCardDelayChange: (Double) -> Unit,
    continuousPlay: Boolean,
    onContinuousPlayChange: (Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Audio Settings", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                // Answer Delay
                Text("Answer Delay", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    FilledTonalIconButton(onClick = { if (answerDelay > 0.5) onAnswerDelayChange(answerDelay - 0.5) }) { Icon(Icons.Default.Remove, "Decrease") }
                    Text(text = String.format("%.1fs", answerDelay), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                    FilledTonalIconButton(onClick = { onAnswerDelayChange(answerDelay + 0.5) }) { Icon(Icons.Default.Add, "Increase") }
                }

                Spacer(Modifier.height(16.dp))

                // Next Card Delay
                Text("Next Card Delay", style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    FilledTonalIconButton(onClick = { if (nextCardDelay > 0.5) onNextCardDelayChange(nextCardDelay - 0.5) }) { Icon(Icons.Default.Remove, "Decrease") }
                    Text(text = String.format("%.1fs", nextCardDelay), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                    FilledTonalIconButton(onClick = { onNextCardDelayChange(nextCardDelay + 0.5) }) { Icon(Icons.Default.Add, "Increase") }
                }

                Spacer(Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(Modifier.height(16.dp))

                // Continuous Play Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onContinuousPlayChange(!continuousPlay) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Continuous Play", style = MaterialTheme.typography.titleMedium)
                        Text("Automatically play next card", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = continuousPlay, onCheckedChange = onContinuousPlayChange)
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}

@Composable
fun AudioFlashcardView(card: net.ericclark.studiare.data.Card, isFlipped: Boolean, modifier: Modifier = Modifier) {
    val cardColor = if (isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isFlipped) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    val textToShow = if (isFlipped) card.back else card.front
    val notesToShow = if (isFlipped) card.backNotes else card.frontNotes

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(cardColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp).verticalScroll(rememberScrollState())
        ) {
            Text(
                text = textToShow,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
            if (!notesToShow.isNullOrBlank()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "($notesToShow)",
                    fontSize = 20.sp,
                    textAlign = TextAlign.Center,
                    fontStyle = FontStyle.Italic,
                    color = textColor.copy(alpha = 0.8f)
                )
            }
        }
    }
}

@Composable
fun PlayPauseButton(isPlaying: Boolean, onClick: () -> Unit) {
    IconButton(
        onClick = onClick,
        modifier = Modifier
            .size(80.dp)
            .background(MaterialTheme.colorScheme.primary, CircleShape)
    ) {
        Icon(
            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(48.dp)
        )
    }
}

@Composable
fun DelaySettingsDialog(
    answerDelay: Double,
    onAnswerDelayChange: (Double) -> Unit,
    nextCardDelay: Double,
    onNextCardDelayChange: (Double) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {

                Text("Answer Delay", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(onClick = { if (answerDelay > 0.5) onAnswerDelayChange(answerDelay - 0.5) }) {
                        Icon(Icons.Default.Remove, "Decrease")
                    }
                    Text(
                        text = String.format("%.1fs", answerDelay),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    FilledTonalIconButton(onClick = { onAnswerDelayChange(answerDelay + 0.5) }) {
                        Icon(Icons.Default.Add, "Increase")
                    }
                }

                Spacer(Modifier.height(24.dp))

                Text("Next Card Delay", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(onClick = { if (nextCardDelay > 0.5) onNextCardDelayChange(nextCardDelay - 0.5) }) {
                        Icon(Icons.Default.Remove, "Decrease")
                    }
                    Text(
                        text = String.format("%.1fs", nextCardDelay),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                    FilledTonalIconButton(onClick = { onNextCardDelayChange(nextCardDelay + 0.5) }) {
                        Icon(Icons.Default.Add, "Increase")
                    }
                }

                Spacer(Modifier.height(24.dp))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("Done")
                }
            }
        }
    }
}