package net.ericclark.studiare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.components.CardTagRow
import net.ericclark.studiare.components.getText
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.input.key.type
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.livedata.observeAsState

/**
 * A screen displayed when a study session is completed.
 * It shows a congratulatory message and provides options to restart the session,
 * start a new one, or go back to the deck list.
 * @param navController The NavController for navigating back.
 * @param viewModel The ViewModel providing the study state.
 */
@Composable
fun StudyCompletionScreen(navController: NavController, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val state = viewModel.studyState ?: return
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    val incorrectCards = remember(state.shuffledCards, state.incorrectCardIds) {
        state.shuffledCards.filter { it.id in state.incorrectCardIds }
    }

    var notScored = false
    if (state.studyMode == SessionMode.FLASHCARD || state.studyMode == SessionMode.TYPING || state.studyMode == SessionMode.CROSSWORD ||
        state.studyMode == SessionMode.MEMORY || state.studyMode == SessionMode.ANAGRAM || state.studyMode == SessionMode.HANGMAN ||
        state.studyMode == SessionMode.FREEFORM || state.studyMode == SessionMode.WORD_SEARCH)
        notScored = true
    // Typing mode shouldn't show review button as it forces correctness before moving on
    val showReviewButton = incorrectCards.isNotEmpty() && (notScored)

    val allDecksState by viewModel.allDecks.observeAsState(emptyList())
    val navigateUp = {
        viewModel.deleteCurrentStudySession()
        viewModel.endStudySession()
        // The study route was opened on top of wherever the session list lives (a pane
        // on the deck list, or a standalone route), so simply popping returns there.
        navController.popBackStack()
        Unit
    }

    BackHandler(onBack = navigateUp)

    Scaffold(
        topBar = {
            Column {
                CustomTopAppBar(
                    viewModel = viewModel,
                    screenId = ShortcutScreen.OTHER,
                    title = { Text(state.studyMode.asString(), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        TooltipIconButton(description = getText(R.string.back), onClick = navigateUp) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                        }
                    }
                )
                if (state.deckWithCards != null) {
                    BreadcrumbsBar(
                        currentDeck = state.deckWithCards!!.deck,
                        allDecks = allDecksState.map { it.deck },
                        onNavigateHome = {
                            viewModel.deleteCurrentStudySession()
                            viewModel.endStudySession()
                            navController.navigate("deckList") { popUpTo(0) }
                        },
                        onNavigateToDeck = { deckId ->
                            viewModel.deleteCurrentStudySession()
                            viewModel.endStudySession()
                            navController.navigate("setManager/$deckId") {
                                popUpTo("deckList") { inclusive = false }
                            }
                        }
                    )
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(dimensions.paddingMedium),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
            ) {
                // Expressive Celebration Icon
                Icon(
                    imageVector = Icons.Default.EmojiEvents,
                    contentDescription = null,
                    modifier = Modifier.size(80.dp),
                    tint = MaterialTheme.colorScheme.primary
                )

                Text(getText(R.string.congratulations), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Text(getText(R.string.completed_session_msg), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

                // Hide accuracy score for Typing mode, show it expressively otherwise
                if (!notScored) {
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Surface(
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        val score = (state.firstTryCorrectCount.toFloat() / state.shuffledCards.size * 100).roundToInt()
                        Text(
                            text = stringResource(R.string.first_try_accuracy_format, score),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium)
                        )
                    }
                }

                Spacer(Modifier.height(dimensions.spacingLarge))

                AnimatedVisibility(
                    visible = showReviewButton,
                    enter = slideInVertically() + fadeIn() + expandVertically(),
                    exit = slideOutVertically() + fadeOut() + shrinkVertically()
                ) {
                    Button(
                        onClick = {
                            viewModel.startReviewSession { route ->
                                navController.popBackStack() // Go back to session selection
                                navController.navigate(route) // Go to the new review session
                            }
                        },
                        modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton), // M3 Expressive Pill shape
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.review_incorrect_cards_format, incorrectCards.size), style = MaterialTheme.typography.labelLarge)
                    }
                }

                val backSessionsInteractionSource = remember { MutableInteractionSource() }
                val isBackSessionsPressed by backSessionsInteractionSource.collectIsPressedAsState()
                val backSessionsScale by animateFloatAsState(
                    targetValue = if (isBackSessionsPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "backSessionsSquish"
                )
                FilledTonalButton(
                    onClick = {
                        viewModel.deleteCurrentStudySession()
                        viewModel.endStudySession()
                        navController.popBackStack()
                    },
                    interactionSource = backSessionsInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp).scale(backSessionsScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.back_to_sessions))
                }

                val restartInteractionSource = remember { MutableInteractionSource() }
                val isRestartPressed by restartInteractionSource.collectIsPressedAsState()
                val restartScale by animateFloatAsState(
                    targetValue = if (isRestartPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "restartSquish"
                )
                Button(
                    onClick = { viewModel.restartSameSession() },
                    interactionSource = restartInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp).scale(restartScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.restart_this_session), style = MaterialTheme.typography.labelLarge)
                }

                // M3 Expressive Secondary Actions: Tonal Buttons
                val startInteractionSource = remember { MutableInteractionSource() }
                val isStartPressed by startInteractionSource.collectIsPressedAsState()
                val startScale by animateFloatAsState(
                    targetValue = if (isStartPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "startSquish"
                )
                FilledTonalButton(
                    onClick = { viewModel.restartStudySession() },
                    interactionSource = startInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp).scale(startScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.start_new_session))
                }

                val backDecksInteractionSource = remember { MutableInteractionSource() }
                val isBackDecksPressed by backDecksInteractionSource.collectIsPressedAsState()
                val backDecksScale by animateFloatAsState(
                    targetValue = if (isBackDecksPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "backDecksSquish"
                )
                FilledTonalButton(
                    onClick = {
                        viewModel.deleteCurrentStudySession()
                        viewModel.endStudySession()
                        if (state.deckWithCards?.deck?.parentDeckId != null) {
                            navController.navigate("setManager/${state.deckWithCards!!.deck.parentDeckId}") {
                                popUpTo("setManager/${state.deckWithCards!!.deck.parentDeckId}") { inclusive = true }
                            }
                        } else {
                            navController.popBackStack("deckList", inclusive = false)
                        }
                    },
                    interactionSource = backDecksInteractionSource,
                    modifier = Modifier.fillMaxWidth(0.85f).defaultMinSize(minHeight = 56.dp).scale(backDecksScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.back_to_decks))
                }
            }
        }
    }
}


@Composable
fun EditCardDialog(
    cardToEdit: Card,
    viewModel: FlashcardViewModel,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var front by remember { mutableStateOf(cardToEdit.front) }
    var frontRichText by remember { mutableStateOf(cardToEdit.frontRichText) }
    var isFrontRichText by remember { mutableStateOf(!cardToEdit.frontRichText.isNullOrBlank()) }
    var back by remember { mutableStateOf(cardToEdit.back) }
    var backRichText by remember { mutableStateOf(cardToEdit.backRichText) }
    var isBackRichText by remember { mutableStateOf(!cardToEdit.backRichText.isNullOrBlank()) }
    var frontNotes by remember { mutableStateOf(cardToEdit.frontNotes) }
    var backNotes by remember { mutableStateOf(cardToEdit.backNotes) }
    var difficulty by remember { mutableStateOf(cardToEdit.difficulty) }

    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    // --- NEW: Tag State ---
    var tags by remember { mutableStateOf(cardToEdit.tags) }

    var richTextTarget by remember { mutableStateOf<String?>(null) }
    var richTextHtml by remember { mutableStateOf("") }
    var richTextTitle by remember { mutableStateOf("") }

    // Collect all tags to pass to the picker
    val allTags by viewModel.tags.collectAsState()

    // Determine tags in the current deck for "Quick Select" (Context aware)
    val studyState = viewModel.studyState
    val currentDeckTags = remember(studyState) {
        studyState?.deckWithCards?.cards?.flatMap { it.tags }?.toSet() ?: emptySet()
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        getText(R.string.edit_card),
                        style = MaterialTheme.typography.headlineSmall,
                    )

                    val closeInteractionSource = remember { MutableInteractionSource() }
                    val isClosePressed by closeInteractionSource.collectIsPressedAsState()
                    val closeScale by animateFloatAsState(
                        targetValue = if (isClosePressed) 0.85f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "closeSquish"
                    )
                    TooltipIconButton(description = getText(R.string.discard_changes), 
                        onClick = onDismiss,
                        interactionSource = closeInteractionSource,
                        modifier = Modifier.scale(closeScale)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = getText(R.string.discard_changes))
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))

                CardSideEditor(
                    sideLabel = CardSide.FRONT.asString(),
                    plainText = front,
                    onPlainTextChange = { front = it },
                    isRichText = isFrontRichText,
                    onToggleRichText = { isRich ->
                        isFrontRichText = isRich
                        if (!isRich) {
                            frontRichText = null
                            front = front.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                        }
                    },
                    onEditRichTextClick = {
                        richTextHtml = frontRichText ?: front
                        richTextTitle = "Edit Front (Rich Text)"
                        richTextTarget = "front"
                    },
                    actionIcon = {
                        TooltipIconButton(description = getText(R.string.add_front_note), onClick = {
                            frontNotes = frontNotes + NoteField(name = "Front Note", content = "", type = MediaType.PLAIN_TEXT)
                        }) {
                            Icon(Icons.Default.Add, contentDescription = getText(R.string.add_front_note), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                frontNotes.forEachIndexed { index, note ->
                    val enterTransition = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                    AnimatedVisibility(
                        visibleState = enterTransition,
                        enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    ) {
                        DynamicNoteEditor(
                            note = note,
                            noteIndex = index,
                            onNoteChange = { updatedNote ->
                                val newList = frontNotes.toMutableList()
                                newList[index] = updatedNote
                                frontNotes = newList
                            },
                            onEditRichTextClick = {
                                richTextHtml = note.content
                                richTextTitle = "Edit ${note.name}"
                                richTextTarget = "frontNote_$index"
                            },
                            onRemove = {
                                val removedNote = frontNotes[index]
                                val newList = frontNotes.toMutableList()
                                newList.removeAt(index)
                                frontNotes = newList

                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar("Note removed", "Undo")
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        val restoreList = frontNotes.toMutableList()
                                        restoreList.add(index.coerceIn(0, restoreList.size), removedNote)
                                        frontNotes = restoreList
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                CardSideEditor(
                    sideLabel = CardSide.BACK.asString(),
                    plainText = back,
                    onPlainTextChange = { back = it },
                    isRichText = isBackRichText,
                    onToggleRichText = { isRich ->
                        isBackRichText = isRich
                        if (!isRich) {
                            backRichText = null
                            back = back.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                        }
                    },
                    onEditRichTextClick = {
                        richTextHtml = backRichText ?: back
                        richTextTitle = "Edit Back (Rich Text)"
                        richTextTarget = "back"
                    },
                    actionIcon = {
                        TooltipIconButton(description = getText(R.string.add_back_note), onClick = {
                            backNotes = backNotes + NoteField(name = "Back Note", content = "", type = MediaType.PLAIN_TEXT)
                        }) {
                            Icon(Icons.Default.Add, contentDescription = getText(R.string.add_back_note), tint = MaterialTheme.colorScheme.primary)
                        }
                    }
                )

                backNotes.forEachIndexed { index, note ->
                    val enterTransition = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                    AnimatedVisibility(
                        visibleState = enterTransition,
                        enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                    ) {
                        DynamicNoteEditor(
                            note = note,
                            noteIndex = index,
                            onNoteChange = { updatedNote ->
                                val newList = backNotes.toMutableList()
                                newList[index] = updatedNote
                                backNotes = newList
                            },
                            onEditRichTextClick = {
                                richTextHtml = note.content
                                richTextTitle = "Edit ${note.name}"
                                richTextTarget = "backNote_$index"
                            },
                            onRemove = {
                                val removedNote = backNotes[index]
                                val newList = backNotes.toMutableList()
                                newList.removeAt(index)
                                backNotes = newList

                                coroutineScope.launch {
                                    snackbarHostState.currentSnackbarData?.dismiss()
                                    val result = snackbarHostState.showSnackbar("Note removed", "Undo")
                                    if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                        val restoreList = backNotes.toMutableList()
                                        restoreList.add(index.coerceIn(0, restoreList.size), removedNote)
                                        backNotes = restoreList
                                    }
                                }
                            }
                        )
                    }
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                // --- NEW: Tag Row Component ---
                Text(getText(R.string.tags), style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 4.dp))
                CardTagRow(
                    cardTags = tags,
                    allTags = allTags,
                    currentDeckTags = currentDeckTags,
                    onUpdateTags = { newTags -> tags = newTags.toList() },
                    onCreateTag = { name, color ->
                        viewModel.saveTagDefinition(TagDefinition(name = name, color = color))
                    }
                )

                Spacer(Modifier.height(dimensions.spacingSmall))

                val currentCardFromState = viewModel.studyState?.deckWithCards?.cards?.find { it.id == cardToEdit.id } ?: cardToEdit

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DifficultySlider(
                        label = getText(R.string.difficulty),
                        difficulty = difficulty,
                        onDifficultyChange = { difficulty = it },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(dimensions.spacingMedium))
                    Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                        MarkKnownButton(
                            isKnown = currentCardFromState.isKnown,
                            onClick = { viewModel.toggleCardKnownStatus(currentCardFromState) }
                        )
                    }
                }
                Spacer(Modifier.height(dimensions.spacingLarge))

                val saveInteractionSource = remember { MutableInteractionSource() }
                val isSavePressed by saveInteractionSource.collectIsPressedAsState()
                val saveScale by animateFloatAsState(
                    targetValue = if (isSavePressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "saveCardSquish"
                )
                Button(
                    onClick = {
                        val updatedCard = cardToEdit.copy(
                            front = front.trim(),
                            frontRichText = frontRichText?.trim()?.takeIf { it.isNotBlank() },
                            back = back.trim(),
                            backRichText = backRichText?.trim()?.takeIf { it.isNotBlank() },
                            frontNotes = frontNotes,
                            backNotes = backNotes,
                            difficulty = difficulty,
                            tags = tags // Save updated tags
                        )
                        viewModel.updateCard(updatedCard)
                        onDismiss()
                    },
                    interactionSource = saveInteractionSource,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(saveScale),
                    enabled = front.isNotBlank() && back.isNotBlank(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.save_changes))
                }
            }
        }
        if (richTextTarget != null) {
            RichTextEditorDialog(
                initialHtml = richTextHtml,
                title = richTextTitle,
                onDismiss = { richTextTarget = null },
                onSave = { savedHtml ->
                    val plainText = savedHtml.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                    when {
                        richTextTarget == "front" -> {
                            frontRichText = savedHtml
                            front = plainText
                        }
                        richTextTarget == "back" -> {
                            backRichText = savedHtml
                            back = plainText
                        }
                        richTextTarget?.startsWith("frontNote_") == true -> {
                            val index = richTextTarget!!.substringAfter("_").toInt()
                            val currentList = frontNotes.toMutableList()
                            currentList[index] = currentList[index].copy(content = savedHtml)
                            frontNotes = currentList
                        }
                        richTextTarget?.startsWith("backNote_") == true -> {
                            val index = richTextTarget!!.substringAfter("_").toInt()
                            val currentList = backNotes.toMutableList()
                            currentList[index] = currentList[index].copy(content = savedHtml)
                            backNotes = currentList
                        }
                    }
                    richTextTarget = null
                }
            )
        }
    }
}
