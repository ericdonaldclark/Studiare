package net.ericclark.studiare.studymodes

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import net.ericclark.studiare.data.NoteField
import net.ericclark.studiare.data.MediaType
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText

@Composable
fun AudioStudyScreen(
    navController: NavController,
    viewModel:FlashcardViewModel
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val dimensions = LocalStudiareDimensions.current
    val state = viewModel.studyState ?: return

    DisposableEffect(Unit) {
        viewModel.bindAudioService()
        onDispose { viewModel.unbindAudioService() }
    }

    val currentIndex by viewModel.audioCardIndex.collectAsState()
    val isFlipped by viewModel.audioIsFlipped.collectAsState()
    val isPlaying by viewModel.audioIsPlaying.collectAsState()
    val feedbackMessage by viewModel.audioFeedback.collectAsState()

    var answerDelay by remember { mutableStateOf(2.0) }
    var nextCardDelay by remember { mutableStateOf(2.0) }
    var continuousPlay by remember { mutableStateOf(true) }

    LaunchedEffect(answerDelay, nextCardDelay, continuousPlay) {
        viewModel.updateAudioDelays(answerDelay, nextCardDelay)
        viewModel.setAudioContinuousPlay(continuousPlay)
    }

    val currentCard = state.shuffledCards.getOrNull(currentIndex)

    Scaffold(
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.AUDIO,
                title = { Text(getText(R.string.audio_study)) },
                navigationIcon = {
                    TooltipIconButton(description = "Back", onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    var showSettings by remember { mutableStateOf(false) }
                    TooltipIconButton(description = getText(R.string.audio_settings), onClick = { showSettings = !showSettings }) {
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
        val focusRequester = remember { FocusRequester() }

        // Re-request focus whenever the card changes
        LaunchedEffect(currentIndex) {
            focusRequester.requestFocus()
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .autoFocusable(focusRequester)
                .onPreviewKeyEvent { event ->
                    val isHandledKey = event.key in listOf(
                        Key.Spacebar, Key.Enter, Key.NumPadEnter,
                        Key.DirectionLeft, Key.DirectionRight
                    )

                    if (!isHandledKey) return@onPreviewKeyEvent false

                    if (event.type == KeyEventType.KeyUp) {
                        when (event.key) {
                            Key.Spacebar, Key.Enter, Key.NumPadEnter -> viewModel.toggleAudioPlayPause()
                            Key.DirectionLeft -> viewModel.skipAudioPrevious()
                            Key.DirectionRight -> viewModel.skipAudioNext()
                        }
                    }
                    true // Consume handled keys so Spacebar doesn't scroll the screen
                }
        ) {
            if (currentCard != null) {
                if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                    LandscapeAudioLayout(
                        card = currentCard, isFlipped = isFlipped, currentIndex = currentIndex,
                        totalCards = state.shuffledCards.size, isPlaying = isPlaying,
                        onTogglePlay = { viewModel.toggleAudioPlayPause() },
                        onNext = { viewModel.skipAudioNext() },
                        onPrev = { viewModel.skipAudioPrevious() },
                        feedback = feedbackMessage
                    )
                } else {
                    PortraitAudioLayout(
                        card = currentCard, isFlipped = isFlipped, currentIndex = currentIndex,
                        totalCards = state.shuffledCards.size, isPlaying = isPlaying,
                        onTogglePlay = { viewModel.toggleAudioPlayPause() },
                        onNext = { viewModel.skipAudioNext() },
                        onPrev = { viewModel.skipAudioPrevious() },
                        feedback = feedbackMessage
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
                        },
                            modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) {
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
    card: Card, isFlipped: Boolean, currentIndex: Int, totalCards: Int, isPlaying: Boolean,
    onTogglePlay: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    feedback: String?
) {
    val dimensions = LocalStudiareDimensions.current

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

        // Feedback area — only ever surfaces a TTS failure message now (see speakText in
        // AudioStudyService); STT/grading feedback moved to TYPED_LISTEN/SPOKEN_LISTEN.
        Box(modifier = Modifier.height(50.dp), contentAlignment = Alignment.Center) {
            androidx.compose.animation.AnimatedContent(
                targetState = feedback != null,
                transitionSpec = quizButtonTransitionSpec(),
                label = "audioFeedbackAnim",
                contentAlignment = Alignment.Center
            ) { hasFeedback ->
                if (hasFeedback) {
                    Text(feedback ?: "", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                } else {
                    Spacer(modifier = Modifier.fillMaxSize())
                }
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
    card: Card, isFlipped: Boolean, currentIndex: Int, totalCards: Int, isPlaying: Boolean,
    onTogglePlay: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit,
    feedback: String?
) {
    val dimensions = LocalStudiareDimensions.current

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
            // Feedback area — only ever surfaces a TTS failure message now (see speakText in
            // AudioStudyService); STT/grading feedback moved to TYPED_LISTEN/SPOKEN_LISTEN.
            Box(modifier = Modifier.height(50.dp), contentAlignment = Alignment.Center) {
                androidx.compose.animation.AnimatedContent(
                    targetState = feedback != null,
                    transitionSpec = quizButtonTransitionSpec(),
                    label = "audioFeedbackAnim",
                    contentAlignment = Alignment.Center
                ) { hasFeedback ->
                    if (hasFeedback) {
                        Text(feedback ?: "", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.error)
                    } else {
                        Spacer(modifier = Modifier.fillMaxSize())
                    }
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

    // Tactile Micro-interactions
    val prevInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val prevPressed by prevInteraction.collectIsPressedAsState()
    val prevScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (prevPressed) 0.85f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "prevSquish")

    val playInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val playPressed by playInteraction.collectIsPressedAsState()
    val playScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (playPressed) 0.85f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "playSquish")

    val nextInteraction = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val nextPressed by nextInteraction.collectIsPressedAsState()
    val nextScale by androidx.compose.animation.core.animateFloatAsState(targetValue = if (nextPressed) 0.85f else 1f, animationSpec = androidx.compose.animation.core.spring(dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy, stiffness = androidx.compose.animation.core.Spring.StiffnessMedium), label = "nextSquish")

    Row(
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TooltipIconButton(description = getText(R.string.previous_card), onClick = onPrev, modifier = Modifier.size(48.dp).scale(prevScale), interactionSource = prevInteraction) {
            Icon(Icons.Default.FastRewind, contentDescription = getText(R.string.previous_card), modifier = Modifier.size(32.dp))
        }

        androidx.compose.material3.FilledIconButton(
            onClick = onTogglePlay,
            modifier = Modifier.size(80.dp).scale(playScale),
            shape = if (!isPlaying) CircleShape else RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = androidx.compose.material3.IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            interactionSource = playInteraction
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = getText(if (isPlaying) R.string.pause else R.string.play),
                modifier = Modifier.size(48.dp)
            )
        }

        TooltipIconButton(description = getText(R.string.next_card), onClick = onNext, modifier = Modifier.size(48.dp).scale(nextScale), interactionSource = nextInteraction) {
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
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(getText(R.string.audio_settings), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Answer Delay
                Text(getText(R.string.answer_delay), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    TooltipFilledTonalIconButton(description = getText(R.string.decrease), onClick = { if (answerDelay > 0.5) onAnswerDelayChange(answerDelay - 0.5) }) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
                    Text(text = stringResource(R.string.time_seconds_format, answerDelay), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = dimensions.paddingMedium))
                    TooltipFilledTonalIconButton(description = getText(R.string.increase), onClick = { onAnswerDelayChange(answerDelay + 0.5) }) { Icon(Icons.Default.Add, getText(R.string.increase)) }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                // Next Card Delay
                Text(getText(R.string.next_card_delay), style = MaterialTheme.typography.titleMedium)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                    TooltipFilledTonalIconButton(description = getText(R.string.decrease), onClick = { if (nextCardDelay > 0.5) onNextCardDelayChange(nextCardDelay - 0.5) }) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
                    Text(text = stringResource(R.string.time_seconds_format, nextCardDelay), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = dimensions.paddingMedium))
                    TooltipFilledTonalIconButton(description = getText(R.string.increase), onClick = { onNextCardDelayChange(nextCardDelay + 0.5) }) { Icon(Icons.Default.Add, getText(R.string.increase)) }
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
                Button(
                    onClick = onDismiss,
                    // M3 Expressive: 56dp minimum height
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.done))
                }
            }
        }
    }
}

@Composable
fun AudioFlashcardView(card: Card, isFlipped: Boolean, modifier: Modifier = Modifier) {
    val dimensions = LocalStudiareDimensions.current
    // Smooth Color Crossfade
    val cardColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "audioCardBgAnim"
    )
    val textColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isFlipped) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "audioCardTextAnim"
    )

    val textToShow = if (isFlipped) card.backRichText?.takeIf { it.isNotBlank() } ?: card.back else card.frontRichText?.takeIf { it.isNotBlank() } ?: card.front
    val notesToShow = if (isFlipped) card.backNotes else card.frontNotes

    androidx.compose.material3.ElevatedCard(
        modifier = modifier,
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(
            containerColor = cardColor,
            contentColor = textColor
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(dimensions.paddingLarge).verticalScroll(rememberScrollState())
            ) {
                // If it contains HTML from rich text, parse it
                if (textToShow.contains("<")) {
                    val state = rememberRichTextState()
                    LaunchedEffect(textToShow) { state.setHtml(textToShow) }
                    RichText(
                        state = state,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 32.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
                    )
                } else {
                    Text(
                        text = textToShow,
                        fontSize = 32.sp,
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (notesToShow.isNotEmpty()) {
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    val textNotes = notesToShow.filter { it.type == MediaType.PLAIN_TEXT || it.type == MediaType.RICH_TEXT || it.type == MediaType.HTML }
                    textNotes.forEach { note ->
                        val cleanContent = note.content.replace(Regex("<[^>]*>"), "")
                        Text(
                            text = "(${note.name}: $cleanContent)",
                            fontSize = 20.sp,
                            textAlign = TextAlign.Center,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.alpha(0.8f).padding(bottom = 4.dp)
                        )
                    }
                }
            }
        }
    }
}