package net.ericclark.studiare.studymodes

import android.Manifest
import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun AudioStudyScreen(navController: NavController, viewModel:FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val state = viewModel.studyState ?: return
    val context = LocalContext.current

    // Permission Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            if (!isGranted) {
                Toast.makeText(context, context.getString(R.string.audio_permission_needed), Toast.LENGTH_LONG).show()
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
    val promptIsFront = state.quizPromptSide == CardSide.FRONT
    val isBackShowing = isFlipped
    // If prompt is Front, Answer is Back. If Back is showing, Answer is showing.
    // If prompt is Back, Answer is Front. If Back is NOT showing, Answer is showing.
    val isShowingAnswer = if (promptIsFront) isBackShowing else !isBackShowing

    val showRevealButton = state.enableStt && state.hideAnswerText && !isShowingAnswer

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text(getText(R.string.audio_study)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.Default.ArrowBack, getText(R.string.back)) }
                },
                actions = {
                    var showSettings by remember { mutableStateOf(false) }
                    IconButton(onClick = { showSettings = !showSettings }) {
                        Icon(Icons.Default.Settings, getText(R.string.audio_settings))
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
                        Text(getText(R.string.session_complete))
                        Spacer(Modifier.height(dimensions.spacingMedium))
                        Button(onClick = {
                            viewModel.endStudySession()
                            navController.popBackStack()
                        }) {
                            Text(getText(R.string.back_to_decks))
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
    val dimensions = LocalStudiareDimensions.current
    val displayFeedback = when(feedback) {
        "Tap to Retry" -> stringResource(R.string.tap_to_retry)
        "Retrying..." -> stringResource(R.string.retrying)
        "Try Again" -> stringResource(R.string.try_again)
        "Correct!" -> stringResource(R.string.correct_exclamation)
        else -> feedback ?: ""
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        AudioFlashcardView(
            card = card,
            isFlipped = isFlipped,
            modifier = Modifier.fillMaxWidth().aspectRatio(1.6f)
        )

        Spacer(Modifier.height(dimensions.spacingMedium))

        // Feedback / Listening Indicator / Buttons
        Box(modifier = Modifier.height(50.dp), contentAlignment = Alignment.Center) {
            if (waitingForGrade) {
                // FSRS Grading Buttons: Hard(2), Good(3), Easy(4)
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    Button(
                        onClick = { onRateCard(2) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                    ) { Text(getText(R.string.rating_hard)) }
                    Button(
                        onClick = { onRateCard(3) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                    ) { Text(getText(R.string.rating_good)) }
                    Button(
                        onClick = { onRateCard(4) },
                        colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4))
                    ) { Text(getText(R.string.rating_easy)) }
                }
            } else if (feedback == "Tap to Retry") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(
                        onClick = onTogglePlay,
                        contentPadding = PaddingValues(horizontal = dimensions.paddingMedium, vertical = 0.dp),
                        modifier = Modifier.height(40.dp)
                    ) {
                        Text(getText(R.string.retry))
                    }
                    Spacer(Modifier.width(dimensions.spacingMedium))
                    OutlinedButton(
                        onClick = onSkipStt,
                        contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(getText(R.string.skip))
                    }
                }
            } else if (isListening || feedback == "Retrying..." || feedback == "Try Again") {
                // Show controls during active listening or between attempts
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isListening) {
                        Icon(Icons.Default.Mic, contentDescription = getText(R.string.listening_cd), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Text(getText(R.string.listening), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    } else {
                        // Display "Retrying..." or "Try Again"
                        Text(
                            text = displayFeedback,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(Modifier.width(dimensions.spacingMedium))

                    if (showRevealButton) {
                        OutlinedButton(
                            onClick = onReveal,
                            contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(getText(R.string.reveal))
                        }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                    }

                    OutlinedButton(
                        onClick = onSkipStt,
                        contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = 0.dp),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text(getText(R.string.skip))
                    }
                }
            } else if (feedback != null) {
                // Success case or other feedback
                Text(displayFeedback, style = MaterialTheme.typography.titleLarge, color = if (feedback == "Correct!") Color(0xFF22C55E) else MaterialTheme.colorScheme.error)
            }
        }

        Spacer(Modifier.weight(1f))
        Text(text = stringResource(R.string.card_index_of_total, currentIndex + 1, totalCards), style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(dimensions.spacingLarge))
        AudioControls(isPlaying, onTogglePlay, onNext, onPrev)
        Spacer(Modifier.height(dimensions.spacingLarge))
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
    val dimensions = LocalStudiareDimensions.current
    val displayFeedback = when(feedback) {
        "Tap to Retry" -> stringResource(R.string.tap_to_retry)
        "Retrying..." -> stringResource(R.string.retrying)
        "Try Again" -> stringResource(R.string.try_again)
        "Correct!" -> stringResource(R.string.correct_exclamation)
        else -> feedback ?: ""
    }

    Row(modifier = Modifier.fillMaxSize().padding(dimensions.paddingMedium)) {
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

        Spacer(Modifier.width(dimensions.spacingLarge))

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
                    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        Button(
                            onClick = { onRateCard(2) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800))
                        ) { Text(getText(R.string.rating_hard)) }
                        Button(
                            onClick = { onRateCard(3) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50))
                        ) { Text(getText(R.string.rating_good)) }
                        Button(
                            onClick = { onRateCard(4) },
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = Color(0xFF03A9F4))
                        ) { Text(getText(R.string.rating_easy)) }
                    }
                } else if (feedback == "Tap to Retry") {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = onTogglePlay,
                            contentPadding = PaddingValues(horizontal = dimensions.paddingMedium, vertical = 0.dp),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text(getText(R.string.retry))
                        }
                        Spacer(Modifier.width(dimensions.spacingMedium))
                        OutlinedButton(
                            onClick = onSkipStt,
                            contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(getText(R.string.skip))
                        }
                    }
                } else if (isListening || feedback == "Retrying..." || feedback == "Try Again") {
                    // Show controls during active listening or between attempts
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (isListening) {
                            Icon(Icons.Default.Mic, contentDescription = getText(R.string.listening_cd), tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(dimensions.spacingSmall))
                            Text(getText(R.string.listening), color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        } else {
                            // Display "Retrying..." or "Try Again"
                            Text(
                                text = displayFeedback,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Spacer(Modifier.width(dimensions.spacingMedium))

                        if (showRevealButton) {
                            OutlinedButton(
                                onClick = onReveal,
                                contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = 0.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(getText(R.string.reveal))
                            }
                            Spacer(Modifier.width(dimensions.spacingSmall))
                        }

                        OutlinedButton(
                            onClick = onSkipStt,
                            contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = 0.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(getText(R.string.skip))
                        }
                    }
                } else if (feedback != null) {
                    // Success case or other feedback
                    Text(displayFeedback, style = MaterialTheme.typography.titleLarge, color = if (feedback == "Correct!") Color(0xFF22C55E) else MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.height(dimensions.spacingMedium))
            Text(text = stringResource(R.string.card_index_of_total, currentIndex + 1, totalCards), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(dimensions.spacingLarge))
            AudioControls(isPlaying, onTogglePlay, onNext, onPrev)
        }
    }
}

@Composable
fun AudioControls(isPlaying: Boolean, onTogglePlay: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.FastRewind, contentDescription = getText(R.string.previous_card), modifier = Modifier.size(32.dp))
        }

        IconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = getText(if (isPlaying) R.string.pause else R.string.play),
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            )
        }

        IconButton(onClick = onNext, modifier = Modifier.size(48.dp)) {
            Icon(Icons.Default.FastForward, contentDescription = getText(R.string.next_card), modifier = Modifier.size(32.dp))
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
    val dimensions = LocalStudiareDimensions.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(getText(R.string.audio_settings), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Answer Delay
                Text(getText(R.string.answer_delay), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    FilledTonalIconButton(onClick = { if (answerDelay > 0.5) onAnswerDelayChange(answerDelay - 0.5) }) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
                    Text(text = stringResource(R.string.time_seconds_format, answerDelay), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = dimensions.paddingMedium))
                    FilledTonalIconButton(onClick = { onAnswerDelayChange(answerDelay + 0.5) }) { Icon(Icons.Default.Add, getText(R.string.increase)) }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                // Next Card Delay
                Text(getText(R.string.next_card_delay), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    FilledTonalIconButton(onClick = { if (nextCardDelay > 0.5) onNextCardDelayChange(nextCardDelay - 0.5) }) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
                    Text(text = stringResource(R.string.time_seconds_format, nextCardDelay), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = dimensions.paddingMedium))
                    FilledTonalIconButton(onClick = { onNextCardDelayChange(nextCardDelay + 0.5) }) { Icon(Icons.Default.Add, getText(R.string.increase)) }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                HorizontalDivider()
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Continuous Play Toggle
                Row(
                    modifier = Modifier.fillMaxWidth().clickable { onContinuousPlayChange(!continuousPlay) },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(getText(R.string.continuous_play), style = MaterialTheme.typography.titleMedium)
                        Text(getText(R.string.continuous_play_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = continuousPlay, onCheckedChange = onContinuousPlayChange)
                }

                Spacer(Modifier.height(dimensions.paddingLarge))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(getText(R.string.done))
                }
            }
        }
    }
}

@Composable
fun AudioFlashcardView(card: net.ericclark.studiare.data.Card, isFlipped: Boolean, modifier: Modifier = Modifier) {
    val dimensions = LocalStudiareDimensions.current
    val cardColor = if (isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
    val textColor = if (isFlipped) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer

    val textToShow = if (isFlipped) card.back else card.front
    val notesToShow = if (isFlipped) card.backNotes else card.frontNotes

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(dimensions.cornerRadiusLarge))
            .background(cardColor),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(dimensions.paddingLarge).verticalScroll(rememberScrollState())
        ) {
            Text(
                text = textToShow,
                fontSize = 32.sp,
                textAlign = TextAlign.Center,
                color = textColor,
                fontWeight = FontWeight.Bold
            )
            if (!notesToShow.isNullOrBlank()) {
                Spacer(Modifier.height(dimensions.spacingMedium))
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
            contentDescription = getText(if (isPlaying) R.string.pause else R.string.play),
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
    val dimensions = LocalStudiareDimensions.current
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge), horizontalAlignment = Alignment.CenterHorizontally) {

                Text(getText(R.string.answer_delay), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(dimensions.spacingSmall))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(onClick = { if (answerDelay > 0.5) onAnswerDelayChange(answerDelay - 0.5) }) {
                        Icon(Icons.Default.Remove, getText(R.string.decrease))
                    }
                    Text(
                        text = stringResource(R.string.time_seconds_format, answerDelay),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = dimensions.paddingMedium)
                    )
                    FilledTonalIconButton(onClick = { onAnswerDelayChange(answerDelay + 0.5) }) {
                        Icon(Icons.Default.Add, getText(R.string.increase))
                    }
                }

                Spacer(Modifier.height(dimensions.paddingLarge))

                Text(getText(R.string.next_card_delay), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(dimensions.spacingSmall))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    FilledTonalIconButton(onClick = { if (nextCardDelay > 0.5) onNextCardDelayChange(nextCardDelay - 0.5) }) {
                        Icon(Icons.Default.Remove, getText(R.string.decrease))
                    }
                    Text(
                        text = stringResource(R.string.time_seconds_format, nextCardDelay),
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.padding(horizontal = dimensions.paddingMedium)
                    )
                    FilledTonalIconButton(onClick = { onNextCardDelayChange(nextCardDelay + 0.5) }) {
                        Icon(Icons.Default.Add, getText(R.string.increase))
                    }
                }

                Spacer(Modifier.height(dimensions.paddingLarge))
                Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text(getText(R.string.done))
                }
            }
        }
    }
}