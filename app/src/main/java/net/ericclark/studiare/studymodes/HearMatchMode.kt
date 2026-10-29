package net.ericclark.studiare.studymodes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.AdaptiveSessionTitle
import net.ericclark.studiare.rememberMainContentReady
import net.ericclark.studiare.sessionCardContainerTransform
import net.ericclark.studiare.DifficultySlider
import net.ericclark.studiare.MarkKnownButton
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.LocalWindowWidthSizeClass
import net.ericclark.studiare.QuizCardContent
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.TooltipIconButton
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.CardSide
import net.ericclark.studiare.data.resolvePromptSide
import net.ericclark.studiare.data.SchedulingMode
import net.ericclark.studiare.data.StudyState
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.screens.Screens.EditCardDialog
import net.ericclark.studiare.screens.Screens.StudyCompletionScreen
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * Audio-answer multiple choice: the prompt shows as text (like Multiple Choice), but the N
 * candidate answers are unlabeled audio clips — tap one to hear it and arm it, then tap the
 * dedicated Submit button to commit it. The button never shows the answer text, even on reveal,
 * since identifying it by ear is the entire point of the exercise (showing the text would make it
 * indistinguishable from Multiple Choice).
 */
@Composable
fun HearMatchScreen(navController: NavController, viewModel: FlashcardViewModel) {
    val state = viewModel.studyState ?: return
    val mainContentReady = rememberMainContentReady()
    ApplyAutoAdvance(state, viewModel)
    val audioController = rememberListenAudioController()
    audioController.speechRate = state.audioPlaybackSpeed
    audioController.replayCount = state.audioReplayCount

    LaunchedEffect(state.currentCardIndex, state.sessionId) {
        viewModel.generateOptionsForCurrentCardIfNeeded()
    }

    val card = state.shuffledCards.getOrNull(state.currentCardIndex)
    val promptSide = card?.let { resolvePromptSide(state.quizPromptSide, it, state.resolvedAutoByDeckSide) } ?: CardSide.FRONT
    val answerSide = promptSide.opposite()
    val deck = state.deckWithCards.deck
    val optionCards = remember(state.currentCardIndex, state.mcOptions) {
        val optionIds = state.mcOptions[card?.id] ?: emptyList()
        optionIds.mapNotNull { id -> state.deckWithCards.cards.find { it.id == id } }
    }
    // Resolved purely as the TTS source text / the value submitted — never rendered.
    val optionAnswerText = remember(optionCards, promptSide) { optionCards.map { it.textForSide(answerSide) } }
    val correctAnswer = card?.textForSide(answerSide)

    fun playOption(index: Int) {
        val optionCard = optionCards.getOrNull(index) ?: return
        audioController.play(optionCard.textForSide(answerSide), optionCard.notesForSide(answerSide), deck.languageForSide(answerSide))
    }

    suspend fun playOptionAndAwait(index: Int) {
        val optionCard = optionCards.getOrNull(index) ?: return
        audioController.playAndAwait(optionCard.textForSide(answerSide), optionCard.notesForSide(answerSide), deck.languageForSide(answerSide))
    }

    var armedIndex by remember(state.currentCardIndex) { mutableStateOf<Int?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        card?.let { c -> EditCardDialog(cardToEdit = c, viewModel = viewModel, onDismiss = { showEditDialog = false }) }
    }

    // Auto-play every option once, front to back, when the setting is on — sequenced with
    // playAndAwait so clips never overlap/cut each other off on the single shared SpeechEngine.
    LaunchedEffect(state.currentCardIndex, card?.id, state.hearMatchAutoPlayAllOptions) {
        if (state.hearMatchAutoPlayAllOptions && !state.correctAnswerFound) {
            optionCards.indices.forEach { index -> playOptionAndAwait(index) }
        }
    }

    fun tap(index: Int) {
        if (state.correctAnswerFound) return
        armedIndex = index
        playOption(index)
    }

    fun submitArmed() {
        val index = armedIndex ?: return
        val answer = optionAnswerText.getOrNull(index) ?: return
        viewModel.submitFlashcardQuizAnswer(answer)
        armedIndex = null
    }

    if (state.isComplete) {
        StudyCompletionScreen(navController = navController, viewModel = viewModel)
        return
    }

    Scaffold(
        modifier = Modifier.sessionCardContainerTransform(state.sessionId),
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.HEAR_MATCH,
                title = { AdaptiveSessionTitle(state.deckWithCards.deck.name, state.studyMode.asString()) },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back)) }
                },
                actions = {
                    TooltipIconButton(
                        description = getText(R.string.edit_card),
                        onClick = { showEditDialog = true },
                        enabled = state.correctAnswerFound
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_card))
                    }
                }
            )
        }
    ) { padding ->
        val windowWidthSizeClass = LocalWindowWidthSizeClass.current
        val dimensions = LocalStudiareDimensions.current

        AnimatedVisibility(
            visible = mainContentReady,
            enter = fadeIn(),
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize()
        ) {
        if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
            Row(modifier = Modifier.padding(padding).fillMaxSize().padding(dimensions.paddingMedium)) {
                Column(modifier = Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.Center) {
                    QuizCardContent(state = state, viewModel = viewModel, modifier = Modifier.fillMaxSize(), showNavigation = true)
                }
                Spacer(Modifier.width(dimensions.spacingLarge))
                Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    HearMatchOptionList(state, optionAnswerText, correctAnswer, armedIndex, audioController.isPlaying, ::tap)
                    HearMatchSubmitButton(state = state, armedIndex = armedIndex, onSubmit = ::submitArmed)
                    if (state.schedulingMode != SchedulingMode.FSRS && !state.hideDifficultySelection && state.correctAnswerFound && card != null) {
                        var difficulty by remember(card) { mutableStateOf(card.difficulty) }
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
                                MarkKnownButton(isKnown = card.isKnown, onClick = { viewModel.toggleCardKnownStatus(card) })
                            }
                        }
                    }
                    MCFeedbackArea(state = state, viewModel = viewModel)
                }
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize().padding(dimensions.paddingMedium)) {
                Box(modifier = Modifier.weight(0.4f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    QuizCardContent(state = state, viewModel = viewModel, modifier = Modifier.fillMaxSize(), showNavigation = true)
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                Column(modifier = Modifier.weight(0.6f).fillMaxWidth()) {
                    HearMatchOptionList(state, optionAnswerText, correctAnswer, armedIndex, audioController.isPlaying, ::tap)
                    HearMatchSubmitButton(state = state, armedIndex = armedIndex, onSubmit = ::submitArmed)
                    if (state.schedulingMode != SchedulingMode.FSRS && !state.hideDifficultySelection && state.correctAnswerFound && card != null) {
                        var difficulty by remember(card) { mutableStateOf(card.difficulty) }
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
                                MarkKnownButton(isKnown = card.isKnown, onClick = { viewModel.toggleCardKnownStatus(card) })
                            }
                        }
                    }
                    MCFeedbackArea(state = state, viewModel = viewModel)
                }
            }
        }
        }
    }
}

@Composable
private fun HearMatchOptionList(
    state: StudyState,
    optionAnswerText: List<String>,
    correctAnswer: String?,
    armedIndex: Int?,
    isPlaying: Boolean,
    onTap: (Int) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    LazyColumn(
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(vertical = dimensions.paddingSmall),
        verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
    ) {
        itemsIndexed(optionAnswerText) { index, answer ->
            if (!state.correctAnswerFound || answer == correctAnswer) {
                HearMatchOptionButton(
                    index = index,
                    isArmed = armedIndex == index,
                    isPlaying = isPlaying && armedIndex == index,
                    isCorrectOption = state.correctAnswerFound && answer == correctAnswer,
                    onTap = { onTap(index) }
                )
            }
        }
    }
}

/** Commits the armed option — the only way to answer, mirroring `MCConfirmButton`'s shape. */
@Composable
private fun HearMatchSubmitButton(state: StudyState, armedIndex: Int?, onSubmit: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    if (armedIndex != null && !state.correctAnswerFound) {
        Spacer(Modifier.height(dimensions.spacingSmall))
        Button(
            onClick = onSubmit,
            modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
        ) { Text(getText(R.string.submit)) }
    }
}

/**
 * One audio-answer option. Never shows the answer text — only its index — plus a speaker icon;
 * on reveal, only the correct option's border/background highlights, same color logic as
 * `MCChoiceButton` in `MultipleChoiceMode.kt`.
 */
@Composable
fun HearMatchOptionButton(
    index: Int,
    isArmed: Boolean,
    isPlaying: Boolean,
    isCorrectOption: Boolean,
    onTap: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val correctColor = Color(0xFF22C55E)
    val defaultContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val defaultContentColor = MaterialTheme.colorScheme.onSurface

    val targetContainerColor = if (isCorrectOption) correctColor.copy(alpha = 0.2f) else defaultContainerColor
    val targetBorderColor = when {
        isCorrectOption -> correctColor
        isArmed -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }

    val containerColor by animateColorAsState(targetContainerColor, tween(300), label = "hearMatchContainerColor")
    val borderColor by animateColorAsState(targetBorderColor, tween(300), label = "hearMatchBorderColor")

    Button(
        onClick = onTap,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp)
            .border(width = 2.dp, color = borderColor, shape = RoundedCornerShape(dimensions.cornerRadiusButton)),
        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = defaultContentColor,
            disabledContainerColor = if (isCorrectOption) correctColor else MaterialTheme.colorScheme.surfaceContainer,
            disabledContentColor = if (isCorrectOption) Color.White else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        ),
        contentPadding = PaddingValues(dimensions.paddingMedium)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Filled.VolumeUp, contentDescription = null, tint = if (isPlaying) MaterialTheme.colorScheme.primary else defaultContentColor)
            Spacer(Modifier.width(dimensions.spacingSmall))
            Text(
                text = stringResource(R.string.hear_match_option_label_format, index + 1),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
