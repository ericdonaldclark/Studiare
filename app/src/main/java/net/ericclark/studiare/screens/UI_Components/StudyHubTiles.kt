package net.ericclark.studiare.screens.UI_Components
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
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
import net.ericclark.studiare.ui.theme.pressSquish
import net.ericclark.studiare.data.Direction
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.components.formatTimeAgoWritten
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.input.key.key
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
    showModeName: Boolean,
    showCategoryNameIcon: Boolean,
    onResume: (ActiveSession) -> Unit,
    onCopy: (ActiveSession) -> Unit,
    onRestart: (ActiveSession) -> Unit,
    onDelete: (ActiveSession) -> Unit,
    onPin: (ActiveSession, String) -> Unit,
    onUnpin: (ActiveSession) -> Unit
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
                        deckName = deck.deck.name,
                        onResume = { onResume(session) },
                        onCopy = { onCopy(session) },
                        onRestart = { onRestart(session) },
                        onDelete = { onDelete(session) },
                        onPin = { name -> onPin(session, name) },
                        onUnpin = { onUnpin(session) },
                        showModeName = showModeName,
                        showCategoryNameIcon = showCategoryNameIcon
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
    showModeName: Boolean,
    showCategoryNameIcon: Boolean,
    onResume: (ActiveSession) -> Unit,
    onCopy: (ActiveSession) -> Unit,
    onRestart: (ActiveSession) -> Unit,
    onDelete: (ActiveSession) -> Unit,
    onPin: (ActiveSession, String) -> Unit,
    onUnpin: (ActiveSession) -> Unit
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
                    deckName = deck.deck.name,
                    onResume = { onResume(session) },
                    onCopy = { onCopy(session) },
                    onRestart = { onRestart(session) },
                    onDelete = { onDelete(session) },
                    onPin = { name -> onPin(session, name) },
                    onUnpin = { onUnpin(session) },
                    showModeName = showModeName,
                    showCategoryNameIcon = showCategoryNameIcon
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
/**
 * A composable that displays a single study session tile, redesign "Option D": a title row (pin
 * icon + name-or-time, auto-scrolling if it overflows) and a subtitle row (category icon +
 * category/mode/time, or a standalone time, or blank) above a divider, then a preview thumbnail
 * next to a progress text/bar and two alternating-color rows of mode-specific chips — one per
 * applicable option, driven by the same registry Settings → Mode Defaults uses
 * ([modeOptionsFor]/[ActiveSession.toModeDefaultSettings]) rather than a hand-picked per-mode
 * subset, so a newly added mode option shows up here automatically. Worked out and iterated
 * entirely in the debug UI Playground (see the plan file's "Debug UI Playground" section) against
 * live Compose rendering before landing here, rather than shipped straight from a static mockup.
 * @param session The study session to display.
 * @param card The current card in the session for the preview.
 * @param showCategoryNameIcon Whether this tile needs to self-identify its category — on whenever
 * the caller isn't already grouping/labeling sessions by category (e.g. a section header).
 * @param showModeName Whether this tile needs to self-identify its mode — on whenever the caller
 * isn't already grouping/labeling sessions by mode.
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
    deckName: String,
    modifier: Modifier = Modifier,
    onResume: () -> Unit,
    onCopy: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    onPin: (name: String) -> Unit,
    onUnpin: () -> Unit,
    showCategoryNameIcon: Boolean = false,
    showModeName: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showPinDialog) {
        PinNamingDialog(
            defaultName = defaultSessionPinName(context, session.mode, deckName),
            onDismiss = { showPinDialog = false },
            onConfirm = { name -> onPin(name); showPinDialog = false }
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    if (showInfoDialog) {
        SessionInfoDialog(session = session, onDismiss = { showInfoDialog = false })
    }

    ElevatedCard(
        onClick = onResume,
        modifier = modifier
            .fillMaxWidth()
            .pressSquish(interactionSource)
            .border(if (isFocused) 6.dp else 0.dp, borderColor, RoundedCornerShape(dimensions.cornerRadiusMedium)),
        interactionSource = interactionSource,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation, pressedElevation = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
            val completedCrosswordCount = remember(session.crosswordWords, session.crosswordUserInputs) {
                completedCrosswordWordCount(session)
            }

            // "12 / 40 cards" style, dynamically singular/plural on the total — shown below the
            // divider, above the progress bar (see the right-hand Column further down).
            val progressText = when (session.mode) {
                SessionMode.MEMORY -> formatSessionTileProgressText(session.matchedPairs.size, session.totalCards, "pair", "pairs")
                SessionMode.MATCHING -> formatSessionTileProgressText(session.matchedPairs.size, session.totalCards, "pair", "pairs")
                SessionMode.CROSSWORD -> formatSessionTileProgressText(completedCrosswordCount, session.crosswordWords.size, "word", "words")
                SessionMode.WORD_SEARCH -> formatSessionTileProgressText(session.wordSearchFoundWordIds.size, session.wordSearchWords.size, "word", "words")
                else -> formatSessionTileProgressText(session.currentCardIndex, session.totalCards, "card", "cards")
            }

            // --- TITLE / SUBTITLE: the top splits into two rows. The title shows the session's
            // own name if it has one, else the written-out time. The subtitle shows category/mode
            // (whichever this caller needs — see this function's own doc comment) plus a
            // short-form time, or a standalone written-out time if neither is needed and the
            // title isn't already showing it, or nothing at all. Time never appears in both rows.
            val headerIconSize = 20.dp
            val titleShowsTime = session.name.isNullOrBlank()
            val titleText = if (titleShowsTime) formatTimeAgoWritten(session.lastAccessed) else session.name!!

            val categoryOrModeShown = showCategoryNameIcon || showModeName
            val subtitleNameParts = buildList {
                if (showCategoryNameIcon) add(session.displayCategory().asString())
                if (showModeName) add(session.mode.asString())
            }
            val subtitleText: String? = when {
                categoryOrModeShown -> (subtitleNameParts + if (titleShowsTime) emptyList() else listOf(net.ericclark.studiare.components.formatTimeAgo(session.lastAccessed))).joinToString(" · ")
                !titleShowsTime -> formatTimeAgoWritten(session.lastAccessed)
                else -> null
            }

            // --- TITLE ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Pin icon — only composed (and only takes up space) when the session is
                    // actually pinned, not reserved ahead of time.
                    if (session.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = getText(R.string.pinned_label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(headerIconSize)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee()
                    )
                }

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
                            DropdownMenuItem(
                                text = { Text(if (session.isPinned) getText(R.string.unpin) else getText(R.string.pin)) },
                                leadingIcon = { Icon(if (session.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, null) },
                                onClick = {
                                    showMenu = false
                                    if (session.isPinned) onUnpin() else showPinDialog = true
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                            )
                            DropdownMenuItem(text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                        }
                    }
                }
            }

            // --- SUBTITLE ROW --- category icon (if shown) + subtitle text, neither reserved
            // ahead of time. Skipped entirely, along with its spacing above, when there's nothing
            // to show (subtitleText is only ever null when the category icon is also off).
            if (subtitleText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    if (showCategoryNameIcon) {
                        Icon(
                            imageVector = sessionModeIcon(session),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(headerIconSize)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- DIVIDER (the progress bar used to live here; it's now to the right of the
            // preview, above the chips, and this full-width divider takes its old spot) ---
            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // --- BOTTOM CONTENT: Preview + chip-based settings ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(120.dp)
                        .then(
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
                                repeat(2) { r -> Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(2) { c -> Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if ((r + c) % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(2.dp))) } } }
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
                            val resolvedPromptSide = card?.let { resolvePromptSide(session.quizPromptSide, it, session.resolvedAutoByDeckSide) } ?: session.quizPromptSide
                            val cardColor = when (session.mode) {
                                SessionMode.TYPING_SCORED, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN -> if (resolvedPromptSide == CardSide.BACK) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                                else -> if (session.isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                            }
                            Box(modifier = Modifier.fillMaxSize().background(cardColor).padding(8.dp), contentAlignment = Alignment.Center) {
                                if (card != null) {
                                    val textToShow = when (session.mode) {
                                        SessionMode.TYPING_SCORED, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN, SessionMode.CROSSWORD -> if (resolvedPromptSide == CardSide.BACK) card.back else card.front
                                        else -> if (session.isFlipped) card.back else card.front
                                    }
                                    Text(text = textToShow, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Settings area — a single non-scrollable, 2-line-max flow of fit-content chips
                // (overflow past 2 lines is dropped, not wrapped further or scrolled — see
                // FlowRowOverflow.Clip below). Chips are non-scrollable because an independently
                // scrollable row here fights the Study Hub carousel's own horizontal swipe-between-
                // cards gesture (same axis, same touch area — a real on-device conflict the user
                // found: swiping to the next card could grab a chip row instead, and vice versa).
                // Only *non-default* options get a chip at all (an option whose session value
                // matches what it would resolve to with nothing customized at all is skipped) —
                // this is what keeps the realistic chip count low enough to fit in 2 lines without
                // scrolling for most sessions, not just the line cap by itself.
                val category = remember(session.mode, session.schedulingMode, session.isGraded) { session.displayCategory() }
                val applicableOptions = remember(category, session.mode) { modeOptionsFor(category, session.mode) }
                val modeOptionValues = remember(session) { session.toModeDefaultSettings() }
                val optionContext = remember(category, session.mode, session.totalCards) {
                    ModeOptionContext(maxForDifficulty = { 100 }, availableCardsCount = session.totalCards, category = category, mode = session.mode)
                }
                // Priority chips (non-default info) always get a slot first; if any room is left
                // over, it's filled with default-valued chips — shortest text first, so the fill
                // logic packs in as many as will actually fit rather than gambling on long ones.
                // Filter/Sort stay priority-only (never fillers): their default state is "Any"/
                // "Random", which is the overwhelming common case and would dominate the filler
                // slots with near-zero information if treated as fillable, unlike a default mode
                // option (e.g. "Auto Flip" off) which is still a meaningful, specific fact.
                val priorityChips = mutableListOf<SessionTileChipData>()
                val fillChips = mutableListOf<SessionTileChipData>()
                if (session.selectionMode != SelectionMode.ANY) {
                    priorityChips.add(SessionTileChipData(stringResource(R.string.filter_applied_format, session.selectionMode.asString()), true))
                }
                if (session.cardOrder != SortMode.RANDOM) {
                    priorityChips.add(SessionTileChipData(stringResource(R.string.sort_applied_format, "${session.cardOrder.asString()} (${session.sortDirection.asString()})"), true))
                }
                applicableOptions.filter { it.isVisible(modeOptionValues) }.forEach { option ->
                    val actual = sessionTileChipDataFor(option, modeOptionValues, optionContext)
                    val default = sessionTileChipDataFor(option, ModeDefaultSettings(), optionContext)
                    if (actual != default) priorityChips.add(actual) else fillChips.add(default)
                }
                if (session.difficulties.isNotEmpty()) {
                    val difficultyChip = SessionTileChipData("${getText(R.string.difficulty)} ${formatDifficultyRanges(session.difficulties)}", true)
                    // All 5 difficulties selected means no actual filtering is happening — that's
                    // the default state (a filler candidate), not something worth prioritizing.
                    if (session.difficulties.toSet() == setOf(1, 2, 3, 4, 5)) fillChips.add(difficultyChip) else priorityChips.add(difficultyChip)
                }
                val chipData = priorityChips + fillChips.sortedBy { it.text.length }
                // Fixed to the same 120.dp height as the preview box so the chip block's bottom
                // lands on the preview's bottom edge; the one weighted spacer pushes it there.
                Column(modifier = Modifier.weight(1f).height(120.dp)) {
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val progressValue = sessionProgressFraction(session)
                    androidx.compose.material3.LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(androidx.compose.foundation.shape.CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    SessionTileChipGrid(chipData, modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

/** One chip's resolved display text and on/off state (kept on the data even though every chip renders filled — see [SessionTileSettingsChip]), computed once per [ModeOption] so the full set can be split across rows and colored before being rendered. */
internal data class SessionTileChipData(val text: String, val on: Boolean)

/**
 * Chip-only shortened labels, keyed by [ModeOption.id] — a compact chip has much less room than
 * Settings → Mode Defaults' own full-width rows, so these stay short ("Weighted" rather than
 * "Difficulty Weighting") without touching the real string resources those other surfaces use.
 * Falls back to the real, full-length label for any option not listed here.
 */
private val sessionTileChipShortLabels: Map<String, String> = mapOf(
    "NumberOfAnswers" to "Answers",
    "ShowCorrectLetters" to "Correct Letters",
    "MaxMemoryTiles" to "Tiles",
    "GridDensity" to "Density",
    "ShowCorrectWords" to "Correct Words",
    "FreeformLayout" to "Vertical",
    "DifficultyWeighting" to "Weighted",
    "AudioPlaybackSpeed" to "Speed",
    "AudioReplayCount" to "Replays",
    "AudioAnswerDelay" to "Answer Delay",
    "AudioNextCardDelay" to "Next Delay",
    "FreeformShowBothSides" to "Both Sides",
    "FreeformSwipeNavigation" to "Swipe Nav",
    "TypingIgnoreFormatting" to "Ignore Format",
    "TypingDisableAutocorrect" to "No Autocorrect",
    "TypingShowLengthHint" to "Length Hint",
    "TypedListenMultipleChoice" to "Multiple Choice",
    "HearMatchAutoPlayAll" to "Auto Play",
    "HideDifficultySelection" to "Hide Difficulty",
    "FlashcardDoubleTap" to "Double Tap",
    "FlashcardRandomizeSide" to "Random Prompt",
    "AnagramFirstLetterHint" to "Letter Hint",
    "PromptSide" to "Prompt",
    "MemoryGridColumnsPortrait" to "Portrait Cols",
    "MemoryGridColumnsLandscape" to "Landscape Cols",
    "MemoryWrongPair" to "Wrong Delay",
    "MemoryCorrectPair" to "Correct Delay",
    "RequireConfirmTap" to "Confirm Tap",
    "AutoAdvanceDelay" to "Advance Delay",
    "MatchingWrongDelay" to "Wrong Delay",
    "MatchingCorrectHighlight" to "Correct Highlight",
    "MatchingHighlight" to "Highlight",
    "AllowMultipleGuesses" to "Multi Guess",
    "MatchingAllowMultipleGuesses" to "Multi Guess",
    "CrosswordAutoAdvanceCell" to "Advance Cell",
    "ListenIncorrectSound" to "Wrong Sound"
)

/** Looks up [option]'s shortened chip label, falling back to its real full-length [ModeOption.labelRes] text. */
@Composable
private fun sessionTileChipLabel(option: ModeOption): String = sessionTileChipShortLabels[option.id] ?: getText(option.labelRes)

/** Shortened chip-only display for a [CardSide] value — e.g. "Auto by Deck" -> "Auto Deck" — distinct from [CardSide.asString], which stays full-length everywhere else it's used. */
private fun sessionTilePromptSideChipValue(side: CardSide): String = when (side) {
    CardSide.FRONT -> "Front"
    CardSide.BACK -> "Back"
    CardSide.AUTO_BY_DECK -> "Auto Deck"
    CardSide.AUTO_BY_CARD -> "Auto Card"
}

/** [FreeformSwipeNavigation]'s raw `Int` (`SWIPE`/`BUTTON`/`BOTH`) as the same short label its own `Control()` already uses, instead of the bare number. */
@Composable
private fun sessionTileFreeformSwipeNavValue(value: Int): String = when (value) {
    FreeformSwipeNavigation.SWIPE -> getText(R.string.freeform_nav_swipe)
    FreeformSwipeNavigation.BUTTON -> getText(R.string.freeform_nav_button)
    else -> getText(R.string.freeform_nav_both)
}

/** [GridDensity]'s raw `Int` (1/2/3) as the same Sparse/Balanced/Compact label its own `Control()` already uses, instead of the bare number. */
@Composable
private fun sessionTileGridDensityValue(value: Int): String = when (value) {
    1 -> getText(R.string.sparse)
    2 -> getText(R.string.balanced)
    else -> getText(R.string.compact)
}

/** [MatchingHighlight]'s raw stored string ("FILL"/"BORDER") as the same label its own `Control()` already uses, instead of the raw constant. */
@Composable
private fun sessionTileMatchingHighlightValue(value: String): String =
    if (value == "FILL") getText(R.string.highlight_fill) else getText(R.string.highlight_border)

/**
 * Resolves one [ModeOption] to its chip text/on-state, dispatched by its concrete type since each
 * one's value has a different shape (boolean toggle, enum, numeric). Every plain boolean option —
 * the large majority, all [SwitchModeOption] subclasses — is handled by one generic branch;
 * everything else gets its own branch so its value can be shown (PromptSide's chosen side, a
 * stepper's current number, etc.). The `else` fallback (label only) means a brand-new option type
 * added later still shows *something* here rather than silently vanishing.
 */
@Composable
private fun sessionTileChipDataFor(option: ModeOption, values: ModeDefaultSettings, context: ModeOptionContext): SessionTileChipData {
    return when (option) {
        is SwitchModeOption -> SessionTileChipData(sessionTileChipLabel(option), option.valueIn(values))
        PromptSide -> SessionTileChipData("${sessionTileChipLabel(option)}: ${sessionTilePromptSideChipValue(PromptSide.valueIn(values, context))}", true)
        DifficultyWeighting -> SessionTileChipData(sessionTileChipLabel(option), DifficultyWeighting.isWeighted(values))
        ShowCorrectLetters -> SessionTileChipData(sessionTileChipLabel(option), ShowCorrectLetters.valueIn(values, context.category))
        TypingShowLengthHint -> SessionTileChipData(sessionTileChipLabel(option), TypingShowLengthHint.valueIn(values, context))
        RequireConfirmTap -> SessionTileChipData(sessionTileChipLabel(option), RequireConfirmTap.valueIn(values, context))
        AllowMultipleGuesses -> SessionTileChipData(sessionTileChipLabel(option), AllowMultipleGuesses.valueIn(values, context))
        MatchingAllowMultipleGuesses -> SessionTileChipData(sessionTileChipLabel(option), MatchingAllowMultipleGuesses.valueIn(values, context))
        FlashcardAutoFlip -> SessionTileChipData(sessionTileChipLabel(option), FlashcardAutoFlip.valueIn(values))
        NumberOfAnswers -> SessionTileChipData("${sessionTileChipLabel(option)}: ${NumberOfAnswers.valueIn(values)}", true)
        MaxMemoryTiles -> SessionTileChipData("${sessionTileChipLabel(option)}: ${MaxMemoryTiles.valueIn(values)}", true)
        GridDensity -> SessionTileChipData("${sessionTileChipLabel(option)}: ${sessionTileGridDensityValue(GridDensity.valueIn(values))}", true)
        AudioPlaybackSpeed -> SessionTileChipData("${sessionTileChipLabel(option)}: ${AudioPlaybackSpeed.valueIn(values)}x", true)
        AudioReplayCount -> SessionTileChipData("${sessionTileChipLabel(option)}: ${AudioReplayCount.valueIn(values)}", true)
        AudioAnswerDelay -> SessionTileChipData("${sessionTileChipLabel(option)}: ${AudioAnswerDelay.valueIn(values)}s", true)
        AudioNextCardDelay -> SessionTileChipData("${sessionTileChipLabel(option)}: ${AudioNextCardDelay.valueIn(values)}s", true)
        FreeformSwipeNavigation -> SessionTileChipData("${sessionTileChipLabel(option)}: ${sessionTileFreeformSwipeNavValue(FreeformSwipeNavigation.valueIn(values))}", true)
        HangmanMaxMistakes -> SessionTileChipData("${sessionTileChipLabel(option)}: ${HangmanMaxMistakes.valueIn(values)}", true)
        MemoryPeek -> SessionTileChipData("${sessionTileChipLabel(option)}: ${MemoryPeek.valueIn(values)}s", true)
        MemoryGridColumnsPortrait -> SessionTileChipData("${sessionTileChipLabel(option)}: ${MemoryGridColumnsPortrait.valueIn(values)}", true)
        MemoryGridColumnsLandscape -> SessionTileChipData("${sessionTileChipLabel(option)}: ${MemoryGridColumnsLandscape.valueIn(values)}", true)
        MemoryWrongPair -> SessionTileChipData("${sessionTileChipLabel(option)}: ${MemoryWrongPair.valueIn(values)}ms", true)
        MemoryCorrectPair -> SessionTileChipData("${sessionTileChipLabel(option)}: ${MemoryCorrectPair.valueIn(values)}ms", true)
        WordSearchHighlightColor -> SessionTileChipData("${sessionTileChipLabel(option)}: ${WordSearchHighlightColor.valueIn(values)}", true)
        AutoAdvanceDelay -> SessionTileChipData("${sessionTileChipLabel(option)}: ${AutoAdvanceDelay.valueIn(values)}s", true)
        MatchingWrongDelay -> SessionTileChipData("${sessionTileChipLabel(option)}: ${MatchingWrongDelay.valueIn(values)}ms", true)
        MatchingCorrectHighlight -> SessionTileChipData("${sessionTileChipLabel(option)}: ${MatchingCorrectHighlight.valueIn(values)}ms", true)
        MatchingHighlight -> SessionTileChipData("${sessionTileChipLabel(option)}: ${sessionTileMatchingHighlightValue(MatchingHighlight.valueIn(values))}", true)
        SpeakingFrontSpeed -> SessionTileChipData("${sessionTileChipLabel(option)}: ${SpeakingFrontSpeed.valueIn(values)}x", true)
        SpeakingBackSpeed -> SessionTileChipData("${sessionTileChipLabel(option)}: ${SpeakingBackSpeed.valueIn(values)}x", true)
        else -> SessionTileChipData(sessionTileChipLabel(option), true)
    }
}

/**
 * Packs [chips] into at most 2 non-scrollable rows, greedily measuring each chip's real rendered
 * width so only chips that completely fit are placed — anything left over is silently dropped,
 * same intent as a scroll/overflow cap, just computed by hand instead of via `FlowRow`'s own
 * (opaque, no per-item row feedback) wrapping. Colors checkerboard by `(row + col) % 2`, not by
 * flat list position — a chip's row is accounted for, not just its index, so e.g. the first chip
 * of row 2 doesn't end up the same color as the first chip of row 1 just because row 1 happened to
 * hold an even number of chips (the bug a flat `index % 2` had: it kept both columns a fixed color
 * down the whole grid instead of reading as alternating row-to-row too).
 */
@Composable
internal fun SessionTileChipGrid(chips: List<SessionTileChipData>, modifier: Modifier = Modifier) {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val textMeasurer = androidx.compose.ui.text.rememberTextMeasurer()
    val chipTextStyle = MaterialTheme.typography.labelSmall
    BoxWithConstraints(modifier = modifier) {
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val chipHorizontalPaddingPx = with(density) { (9.dp * 2).roundToPx() }
        val spacingPx = with(density) { 6.dp.roundToPx() }

        val placed = remember(chips, maxWidthPx) {
            data class Placed(val chip: SessionTileChipData, val row: Int, val col: Int)
            val result = mutableListOf<Placed>()
            var row = 0
            var col = 0
            var rowWidthUsedPx = 0
            for (chip in chips) {
                if (row >= 2) break
                val chipWidthPx = textMeasurer.measure(chip.text, chipTextStyle).size.width + chipHorizontalPaddingPx
                val neededWidthPx = if (col == 0) chipWidthPx else rowWidthUsedPx + spacingPx + chipWidthPx
                if (neededWidthPx <= maxWidthPx) {
                    result.add(Placed(chip, row, col))
                    rowWidthUsedPx = neededWidthPx
                    col++
                } else if (col == 0) {
                    // Doesn't fit even alone on an empty row — unplaceable at this width, skip it.
                } else {
                    row++
                    col = 0
                    rowWidthUsedPx = 0
                    if (row >= 2) break
                    if (chipWidthPx <= maxWidthPx) {
                        result.add(Placed(chip, row, col))
                        rowWidthUsedPx = chipWidthPx
                        col++
                    }
                }
            }
            result
        }

        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            (0..1).forEach { r ->
                val rowChips = placed.filter { it.row == r }
                if (rowChips.isNotEmpty()) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        rowChips.forEach { (chip, row, col) ->
                            if ((row + col) % 2 == 0) {
                                SessionTileSettingsChip(chip.text)
                            } else {
                                SessionTileSettingsChip(
                                    text = chip.text,
                                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A fit-content, solid-filled pill chip. */
@Composable
internal fun SessionTileSettingsChip(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = contentColor, fontWeight = FontWeight.SemiBold)
    }
}

/** "12 / 40 cards" rather than "Progress: 12 / 40" — dynamically singular/plural on the total, e.g. `(0, 1, "card", "cards")` -> `"0 / 1 card"`. */
internal fun formatSessionTileProgressText(current: Int, total: Int, unitSingular: String, unitPlural: String): String =
    "$current / $total ${if (total == 1) unitSingular else unitPlural}"

/** Compresses a sorted-or-not list of small ints into a comma-joined run-length string, e.g. `[1, 2, 3, 5]` -> `"1-3,5"`. Used for the difficulty chip's compact label. */
internal fun formatDifficultyRanges(values: List<Int>): String {
    val sorted = values.distinct().sorted()
    if (sorted.isEmpty()) return ""
    val ranges = mutableListOf<String>()
    var start = sorted[0]
    var end = sorted[0]
    for (i in 1 until sorted.size) {
        if (sorted[i] == end + 1) {
            end = sorted[i]
        } else {
            ranges.add(if (start == end) "$start" else "$start-$end")
            start = sorted[i]
            end = sorted[i]
        }
    }
    ranges.add(if (start == end) "$start" else "$start-$end")
    return ranges.joinToString(",")
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
                    supportingContent = { Text(session.totalCards.toString(), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary) },
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

                Button(
                    onClick = onDismiss,
                    interactionSource = dismissInteractionSource,
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .pressSquish(dismissInteractionSource),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Text(getText(R.string.close_capitalized))
                }
            }
        }
    }
}
