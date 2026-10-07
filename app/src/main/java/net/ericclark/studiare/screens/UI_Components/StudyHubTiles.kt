package net.ericclark.studiare.screens.UI_Components
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.text.SimpleDateFormat
import java.util.*
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.data.Direction
import net.ericclark.studiare.components.getText
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.key.key
import androidx.compose.ui.draw.scale
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import net.ericclark.studiare.screens.Screens.StudyModeSelectionScreen

// `category` groups sections under an outer collapsible row per StudyCategory in the populated
// grid view (see StudyModeSelectionScreen's STATE 2) — FSRS/Guided sessions never reach this
// screen's list at all (filtered out of groupedSessions), so no section maps to StudyCategory.SMART.
internal data class SessionSection(val title: String, val category: StudyCategory, val filter: (ActiveSession) -> Boolean)

/**
 * A horizontally-scrolling carousel of [SessionTile]s (plus a position indicator when there's more
 * than one) — a mode-section's content, or (when mode grouping is off but category grouping is on)
 * a merged carousel spanning every mode in a category.
 */
@Composable
internal fun SessionTileCarousel(
    sessions: List<ActiveSession>,
    deck: DeckWithCards,
    showModeLabel: Boolean,
    showCategoryIcon: Boolean,
    onResume: (ActiveSession) -> Unit,
    onCopy: (ActiveSession) -> Unit,
    onRestart: (ActiveSession) -> Unit,
    onDelete: (ActiveSession) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingSmall)) {
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()

        androidx.compose.foundation.lazy.LazyRow(
            state = listState,
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
            contentPadding = PaddingValues(horizontal = dimensions.paddingSmall)
        ) {
            itemsIndexed(items = sessions, key = { _, session -> session.id }) { _, session ->
                Box(modifier = Modifier.width(360.dp)) {
                    val cardIdToShow = if (session.mode == SessionMode.MATCHING && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(session.currentCardIndex)
                    val card = deck.cards.find { it.id == cardIdToShow }

                    SessionTile(
                        session = session,
                        card = card,
                        onResume = { onResume(session) },
                        onCopy = { onCopy(session) },
                        onRestart = { onRestart(session) },
                        onDelete = { onDelete(session) },
                        showModeLabel = showModeLabel,
                        showCategoryIcon = showCategoryIcon
                    )
                }
            }
        }

        // Scroll Indicator
        if (sessions.size > 1) {
            val currentIndex by remember {
                derivedStateOf {
                    val layoutInfo = listState.layoutInfo
                    val visibleItemsInfo = layoutInfo.visibleItemsInfo
                    if (visibleItemsInfo.isEmpty()) {
                        0
                    } else {
                        val viewportStart = layoutInfo.viewportStartOffset
                        val viewportEnd = layoutInfo.viewportEndOffset
                        val viewportCenter = viewportStart + (viewportEnd - viewportStart) / 2
                        visibleItemsInfo.minByOrNull {
                            kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                        }?.index ?: 0
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                sessions.indices.forEach { index ->
                    val isSelected = index == currentIndex
                    val width by androidx.compose.animation.core.animateDpAsState(
                        targetValue = if (isSelected) 24.dp else 8.dp,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessLow
                        ),
                        label = "dotWidth"
                    )
                    val color by androidx.compose.animation.animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        label = "dotColor"
                    )

                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(width = width, height = 8.dp)
                            .clip(CircleShape)
                            .background(color)
                    )
                }
            }
        }
    }
}

/**
 * A wrapping grid of [SessionTile]s — used only when both category and mode grouping are off,
 * since at that point there's no longer a single mode/category to browse through with a carousel;
 * everything is shown at once instead.
 */
@Composable
internal fun SessionTileGrid(
    sessions: List<ActiveSession>,
    deck: DeckWithCards,
    showModeLabel: Boolean,
    showCategoryIcon: Boolean,
    onResume: (ActiveSession) -> Unit,
    onCopy: (ActiveSession) -> Unit,
    onRestart: (ActiveSession) -> Unit,
    onDelete: (ActiveSession) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    FlowRow(
        modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
        verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
    ) {
        sessions.forEach { session ->
            Box(modifier = Modifier.width(360.dp)) {
                val cardIdToShow = if (session.mode == SessionMode.MATCHING && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(session.currentCardIndex)
                val card = deck.cards.find { it.id == cardIdToShow }

                SessionTile(
                    session = session,
                    card = card,
                    onResume = { onResume(session) },
                    onCopy = { onCopy(session) },
                    onRestart = { onRestart(session) },
                    onDelete = { onDelete(session) },
                    showModeLabel = showModeLabel,
                    showCategoryIcon = showCategoryIcon
                )
            }
        }
    }
}

/**
 * A composable that displays a single study session tile.
 * It shows a preview of the current card, session settings, and provides an overflow menu
 * with options to copy, restart, or delete the session.
 * @param session The study session to display.
 * @param card The current card in the session for the preview.
 * @param onResume Callback for when the tile is clicked.
 * @param onCopy Callback for the copy action.
 * @param onRestart Callback for the restart action.
 * @param onDelete Callback for the delete action.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SessionTile(
    session: ActiveSession,
    card: Card?,
    onResume: () -> Unit,
    onCopy: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    showModeLabel: Boolean = false,
    showCategoryIcon: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }

    val yesStr = stringResource(R.string.yes)
    val noStr = stringResource(R.string.no)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "tileSquish"
    )

    if (showInfoDialog) {
        SessionInfoDialog(session = session, onDismiss = { showInfoDialog = false })
    }

    ElevatedCard(
        onClick = onResume,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .border(if (isFocused) 6.dp else 0.dp, borderColor, RoundedCornerShape(dimensions.cornerRadiusMedium)),
        interactionSource = interactionSource,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation, pressedElevation = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier.padding(dimensions.paddingMedium)
        ) {
            // --- TOP HEADER: Progress/Mode Label & Actions ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val completedCrosswordCount = remember(session.crosswordWords, session.crosswordUserInputs) {
                    completedCrosswordWordCount(session)
                }

                val progressText = when (session.mode) {
                    SessionMode.MEMORY -> stringResource(R.string.pairs_progress_format, session.matchedPairs.size, session.totalCards)
                    SessionMode.MATCHING -> stringResource(R.string.matched_progress_format, session.matchedPairs.size, session.totalCards)
                    SessionMode.CROSSWORD -> stringResource(R.string.words_progress_format, completedCrosswordCount, session.crosswordWords.size)
                    SessionMode.WORD_SEARCH -> stringResource(R.string.words_progress_format, session.wordSearchFoundWordIds.size, session.wordSearchWords.size)
                    else -> stringResource(R.string.progress_format, session.currentCardIndex, session.totalCards)
                }

                // When mode grouping is off there's no mode header to identify this tile, so the
                // title swaps to the mode name. The category icon is independent of that — it shows
                // whenever category grouping is off, whether or not the mode label is also showing,
                // since either way there's no category header left to say which one this is. The
                // progress bar below is unaffected either way, so the numeric progress is never lost.
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    if (showCategoryIcon) {
                        Icon(
                            imageVector = sessionModeIcon(session),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp).padding(end = 4.dp)
                        )
                    }
                    Text(
                        text = if (showModeLabel) session.mode.asString() else progressText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text(
                    text = net.ericclark.studiare.components.formatTimeAgo(session.lastAccessed),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(end = 4.dp)
                )

                // Actions Row
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TooltipIconButton(description = getText(R.string.session_info), onClick = { showInfoDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Info, contentDescription = getText(R.string.session_info), tint = MaterialTheme.colorScheme.secondary)
                    }
                    Box {
                        TooltipIconButton(description = getText(R.string.session_options), onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.session_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(getText(R.string.copy)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { onCopy(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                            DropdownMenuItem(text = { Text(getText(R.string.restart)) }, leadingIcon = { Icon(Icons.Default.RestartAlt, null) }, onClick = { onRestart(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                            DropdownMenuItem(text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- PROGRESS BAR ---
            val progressValue = sessionProgressFraction(session)

            androidx.compose.material3.LinearProgressIndicator(
                progress = { progressValue },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(androidx.compose.foundation.shape.CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(16.dp))

            // --- BOTTOM CONTENT: Preview & Nested Info Cards ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
                // 1. Preview Area (Left side)

                // Fetch the transition scopes
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(120.dp)
                        .then(
                            // Apply the SharedBounds modifier to map this preview to the study card
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "session_card_${session.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    if (session.mode == SessionMode.MEMORY) {
                        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(dimensions.cornerRadiusSmall), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(2) { r -> Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(2) { c -> Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if ((r + c) % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer, RoundedCornerShape(2.dp))) } } }
                            }
                        }
                    } else if (session.mode == SessionMode.CROSSWORD) {
                        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(dimensions.cornerRadiusSmall), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            val activeCells = remember(session.crosswordWords) { val cells = mutableSetOf<Pair<Int, Int>>(); session.crosswordWords.forEach { word -> for (i in word.word.indices) { cells.add((if (word.isAcross) word.startX + i else word.startX) to (if (word.isAcross) word.startY else word.startY + i)) } }; cells }
                            val cellColor = MaterialTheme.colorScheme.primaryContainer
                            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                if (session.crosswordGridWidth > 0 && session.crosswordGridHeight > 0) {
                                    val gw = session.crosswordGridWidth.toFloat(); val gh = session.crosswordGridHeight.toFloat()
                                    val cellSize = kotlin.math.min(size.width / gw, size.height / gh)
                                    val offsetX = (size.width - (cellSize * gw)) / 2; val offsetY = (size.height - (cellSize * gh)) / 2
                                    activeCells.forEach { (x, y) -> drawRect(color = cellColor, topLeft = Offset(offsetX + (x * cellSize), offsetY + (y * cellSize)), size = androidx.compose.ui.geometry.Size(cellSize - 2f, cellSize - 2f)) }
                                }
                            }
                        }
                    } else {
                        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(dimensions.cornerRadiusSmall), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            val cardColor = when (session.mode) {
                                SessionMode.TYPING_SCORED, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN -> if (session.quizPromptSide == CardSide.BACK) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                                else -> if (session.isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                            }
                            Box(modifier = Modifier.fillMaxSize().background(cardColor).padding(8.dp), contentAlignment = Alignment.Center) {
                                if (card != null) {
                                    val textToShow = when (session.mode) {
                                        SessionMode.TYPING_SCORED, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN, SessionMode.CROSSWORD -> if (session.quizPromptSide == CardSide.BACK) card.back else card.front
                                        else -> if (session.isFlipped) card.back else card.front
                                    }
                                    Text(text = textToShow, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // 2. Information Cards Area (Right side)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Settings Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                        shape = RoundedCornerShape(dimensions.cornerRadiusSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            if (session.mode == SessionMode.FLASHCARD || session.mode == SessionMode.LIST) {
                                Text(stringResource(R.string.graded_format, if (session.isGraded) yesStr else noStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                if (session.mode == SessionMode.LIST) {
                                    Text(stringResource(R.string.prompt_format, session.quizPromptSide.asString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                }
                            } else if (session.mode == SessionMode.TYPING_SCORED || session.mode == SessionMode.TYPING || session.mode == SessionMode.ANAGRAM || session.mode == SessionMode.CROSSWORD) {
                                Text(stringResource(R.string.prompt_format, session.quizPromptSide.asString()), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            } else if (session.mode == SessionMode.MULTIPLE_CHOICE || session.mode == SessionMode.MATCHING) {
                                Text(stringResource(R.string.graded_format, if (session.isGraded) yesStr else noStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                                Text(stringResource(R.string.reveal_when_wrong_format, if (!session.allowMultipleGuesses) yesStr else noStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            } else {
                                Text(stringResource(R.string.weighted_format, if (session.isWeighted) yesStr else noStr), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                            }
                        }
                    }

                    // Difficulty Card
                    if (session.difficulties.isNotEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
                            shape = RoundedCornerShape(dimensions.cornerRadiusSmall),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.difficulties_format, session.difficulties.joinToString()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SessionInfoDialog(
    session: ActiveSession,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy 'at' h:mm a", Locale.getDefault()) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = "Session Details",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                // 1. Selection Mode Breakdown
                val selectionModeLabel = session.selectionMode.asString()
                val filterTypeLabel = session.filterType.asString()
                val timeUnitLabel = session.timeUnit.asString()
                val selectionText = buildString {
                    append(selectionModeLabel)

                    // Append specific data based on the mode chosen!
                    when (session.selectionMode) {
                        SelectionMode.DIFFICULTY -> if (session.difficulties.isNotEmpty()) append(" (${session.difficulties.joinToString()})")
                        SelectionMode.TAGS -> if (session.selectedTags.isNotEmpty()) append(" (${session.selectedTags.joinToString()})")
                        SelectionMode.ALPHABET -> append(" (${session.alphabetStart} to ${session.alphabetEnd})")
                        SelectionMode.CARD_ORDER -> append(" (#${session.cardOrderStart} to #${session.cardOrderEnd})")
                        SelectionMode.REVIEW_DATE, SelectionMode.INCORRECT_DATE -> append(" ($filterTypeLabel past ${session.timeValue} $timeUnitLabel)")
                        SelectionMode.REVIEW_COUNT -> append(" (${if (session.reviewCountDirection == Direction.ASC) ">=" else "<="} ${session.reviewCountThreshold})")
                        SelectionMode.SCORE -> append(" (${if (session.scoreDirection == Direction.ASC) ">=" else "<="} ${session.scoreThreshold}%)")
                        else -> {}
                    }
                }

                ListItem(
                    headlineContent = { Text(getText(R.string.selection_mode), color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(selectionText, style = MaterialTheme.typography.bodyLarge) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                // 2. Sort & Priority Breakdown
                val orderStr = session.cardOrder.asString()
                val sortDirectionLabel = session.sortDirection.asString()

                ListItem(
                    headlineContent = { Text(getText(R.string.sort_and_priority), color = MaterialTheme.colorScheme.primary) },
                    supportingContent = {
                        val priorityStr = if (session.schedulingMode == SchedulingMode.FSRS) "FSRS" else if (session.isWeighted) "Weighted" else "Standard"
                        Text("$orderStr ($sortDirectionLabel) • $priorityStr", style = MaterialTheme.typography.bodyLarge)
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                // 3. Size
                ListItem(
                    headlineContent = { Text(getText(R.string.total_cards), color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(session.totalCards.toString(), style = MaterialTheme.typography.bodyLarge) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                // 4. Dates
                ListItem(
                    headlineContent = { Text(getText(R.string.date_created), color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(dateFormat.format(Date(session.createdAt)), style = MaterialTheme.typography.bodyLarge) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                ListItem(
                    headlineContent = { Text(getText(R.string.last_used), color = MaterialTheme.colorScheme.primary) },
                    supportingContent = { Text(dateFormat.format(Date(session.lastAccessed)), style = MaterialTheme.typography.bodyLarge) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                )

                Spacer(Modifier.height(dimensions.spacingLarge))

                val dismissInteractionSource = remember { MutableInteractionSource() }
                val isDismissPressed by dismissInteractionSource.collectIsPressedAsState()
                val dismissScale by animateFloatAsState(
                    targetValue = if (isDismissPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "dismissSquish"
                )

                Button(
                    onClick = onDismiss,
                    interactionSource = dismissInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .scale(dismissScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.close_capitalized))
                }
            }
        }
    }
}
