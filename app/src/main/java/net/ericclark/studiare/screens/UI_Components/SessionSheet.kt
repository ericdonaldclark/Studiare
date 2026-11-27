package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.CarouselDotsIndicator
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.KeyboardShortcutsContent
import net.ericclark.studiare.LocalWindowWidthSizeClass
import net.ericclark.studiare.DetailedTimedSessionCard
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import kotlin.math.roundToInt

/** Which tab of the study-session side sheet is showing, and the order they appear in. */
enum class SessionSheetTab { PROGRESS, MODE_OPTIONS, SHORTCUTS }

/** Every card in [SessionStatCarousel] shares this exact size, regardless of how little or much
 *  content it holds, so the carousel reads as one uniform row rather than cards jumping around in
 *  width/height as you swipe. */
private val StatCardWidth = 140.dp
private val StatCardHeight = 112.dp

/**
 * The single integration point a study-mode screen wraps its existing `Scaffold { padding -> ... }`
 * body in to get the side sheet: a *standard* (persistent, docked, no scrim) panel on expanded
 * width, or a *modal* (overlay + scrim, dismissible) panel otherwise — mirroring every other
 * adaptive surface in this app (e.g. `SettingsScreen.kt`'s `isWideLayout` check) rather than
 * inventing a new width threshold. [sheetTab] is `null` when the sheet is closed, in which case
 * [content] renders exactly as it did before this wrapper existed.
 */
@Composable
fun SessionSheetHost(
    state: StudyState,
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: SessionSheetTab?,
    onTabChange: (SessionSheetTab) -> Unit,
    onDismiss: () -> Unit,
    padding: PaddingValues,
    content: @Composable (PaddingValues) -> Unit
) {
    val isWideLayout = LocalWindowWidthSizeClass.current >= WindowWidthSizeClass.Expanded

    if (isWideLayout && sheetTab != null) {
        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content(PaddingValues(0.dp))
            }
            VerticalDivider()
            SessionSheetPanel(state, viewModel, screenId, sheetTab, onTabChange, onDismiss)
        }
    } else {
        content(padding)
        if (sheetTab != null) {
            SessionSheetModal(state, viewModel, screenId, sheetTab, onTabChange, onDismiss)
        }
    }
}

/** Compact/medium width: an edge-anchored overlay with a scrim, dismissible by tapping outside it. */
@Composable
private fun SessionSheetModal(
    state: StudyState,
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: SessionSheetTab,
    onTabChange: (SessionSheetTab) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        contentAlignment = Alignment.CenterEnd,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxHeight().widthIn(max = 400.dp),
            shape = RoundedCornerShape(topStart = dimensions.cornerRadiusLarge, bottomStart = dimensions.cornerRadiusLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            SessionSheetBody(
                state = state,
                viewModel = viewModel,
                screenId = screenId,
                sheetTab = sheetTab,
                onTabChange = onTabChange,
                onClose = onDismiss,
                closeIcon = Icons.Default.Close,
                modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge)
            )
        }
    }
}

/** Expanded width: a persistent, docked panel (no scrim) meant to sit as the trailing item of a [Row]. */
@Composable
private fun SessionSheetPanel(
    state: StudyState,
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: SessionSheetTab,
    onTabChange: (SessionSheetTab) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Surface(
        modifier = Modifier.fillMaxHeight().width(380.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        SessionSheetBody(
            state = state,
            viewModel = viewModel,
            screenId = screenId,
            sheetTab = sheetTab,
            onTabChange = onTabChange,
            onClose = onDismiss,
            closeIcon = Icons.AutoMirrored.Filled.ArrowBack,
            modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge)
        )
    }
}

/** The header/tab row/tab body shared by both [SessionSheetModal] and [SessionSheetPanel]. */
@Composable
private fun SessionSheetBody(
    state: StudyState,
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: SessionSheetTab,
    onTabChange: (SessionSheetTab) -> Unit,
    onClose: () -> Unit,
    closeIcon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    // Same check CustomTopAppBar used to gate its own standalone keyboard-shortcuts button on -
    // there's nothing useful to show in the Keyboard tab without a physical keyboard, so it's left
    // out of the tab row entirely rather than shown empty/irrelevant.
    val hasHardwareKeyboard = androidx.compose.ui.platform.LocalConfiguration.current.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY
    val visibleTabs = remember(hasHardwareKeyboard) {
        if (hasHardwareKeyboard) SessionSheetTab.entries.toList() else SessionSheetTab.entries.filter { it != SessionSheetTab.SHORTCUTS }
    }
    // Covers a keyboard detaching while this sheet happens to be open on the now-hidden tab
    // (Chromebook/tablet with a detachable keyboard) - falls back to Progress instead of leaving
    // the tab row's selection pointed at a tab that's no longer there.
    LaunchedEffect(hasHardwareKeyboard, sheetTab) {
        if (!hasHardwareKeyboard && sheetTab == SessionSheetTab.SHORTCUTS) onTabChange(SessionSheetTab.PROGRESS)
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(getText(R.string.session_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(closeIcon, contentDescription = getText(R.string.close_capitalized))
            }
        }
        Spacer(Modifier.height(dimensions.spacingMedium))

        // Scrollable rather than a fixed TabRow: a fixed TabRow divides the available width evenly
        // among tabs, so a narrow sheet with a long label (or all 3 tabs' icon+label) can force a
        // tab's content onto a second line. Scrolling keeps every tab on one line regardless of width.
        ScrollableTabRow(
            selectedTabIndex = visibleTabs.indexOf(sheetTab).coerceAtLeast(0),
            edgePadding = 0.dp
        ) {
            Tab(selected = sheetTab == SessionSheetTab.PROGRESS, onClick = { onTabChange(SessionSheetTab.PROGRESS) }) {
                SessionSheetTabLabel(Icons.Default.Insights, getText(R.string.session_sheet_tab_progress))
            }
            Tab(selected = sheetTab == SessionSheetTab.MODE_OPTIONS, onClick = { onTabChange(SessionSheetTab.MODE_OPTIONS) }) {
                SessionSheetTabLabel(Icons.Default.Settings, getText(R.string.session_sheet_tab_options))
            }
            if (hasHardwareKeyboard) {
                Tab(selected = sheetTab == SessionSheetTab.SHORTCUTS, onClick = { onTabChange(SessionSheetTab.SHORTCUTS) }) {
                    SessionSheetTabLabel(Icons.Default.Keyboard, getText(R.string.session_sheet_tab_keyboard))
                }
            }
        }
        Spacer(Modifier.height(dimensions.spacingMedium))

        // M3 Expressive lateral tab transition: content slides in the direction of travel (toward
        // a later tab slides in from the right/exits to the left, toward an earlier one reverses
        // that), combined with a fade. Captured outside transitionSpec since that lambda isn't a
        // @Composable context; this app's own MotionScheme override (ui/theme/Motion.kt) collapses
        // both specs to a snap under reduced motion, so that's already respected here too.
        val motionScheme = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = sheetTab,
            modifier = Modifier.weight(1f, fill = false),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> if (forward) width else -width } +
                    fadeIn(animationSpec = motionScheme.defaultEffectsSpec()))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> if (forward) -width else width } +
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                    )
            },
            label = "sessionSheetTab"
        ) { tab ->
            if (tab == SessionSheetTab.PROGRESS) {
                // A LazyColumn, not a plain Column + verticalScroll: the expanded "show more" card
                // list used to be a second, fixed-height verticalScroll nested inside this tab's
                // own verticalScroll, which fought it for drag gestures. Folding the full card list
                // into this same LazyColumn's items removes the nested scrollable entirely (one
                // scroll container for the whole tab) and, as a bonus, only composes the rows
                // actually on screen instead of every card in the deck up front.
                var cardsExpanded by rememberSaveable(state.sessionId) { mutableStateOf(false) }
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                ) {
                    sessionProgressItems(state, cardsExpanded) { cardsExpanded = it }
                }
            } else {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    when (tab) {
                        SessionSheetTab.MODE_OPTIONS -> {
                            val snackbarHostState = remember { SnackbarHostState() }
                            Box {
                                SessionOptionsContent(
                                    category = state.currentCategory(),
                                    mode = state.studyMode,
                                    values = state.sessionOptions(),
                                    deckDefaultPromptSide = state.deckWithCards.deck.defaultPromptSide,
                                    viewModel = viewModel,
                                    snackbarHostState = snackbarHostState,
                                    onChange = { viewModel.updateSessionOptions(it) }
                                )
                                SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
                            }
                        }
                        // listMaxHeight set large: this tab's own scroll is this Column's
                        // verticalScroll above, not KeyboardShortcutsContent's internal cap (which
                        // exists for the standalone KeyboardShortcutsDialog, where there's no outer
                        // scroll to defer to).
                        SessionSheetTab.SHORTCUTS -> KeyboardShortcutsContent(
                            viewModel = viewModel,
                            currentScreen = screenId,
                            listMaxHeight = 2000.dp
                        )
                        SessionSheetTab.PROGRESS -> {} // handled above
                    }
                }
            }
        }
    }
}

/** A tab's icon-then-label content — [Tab]'s own `text`+`icon` params stack them vertically
 *  (icon above text), which isn't what's wanted here, so this builds the leading-icon layout
 *  directly as the tab's plain content lambda instead. */
@Composable
private fun SessionSheetTabLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(vertical = 12.dp)
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
        Text(label)
    }
}

/** Modes with a single linear current/next card sequence — the only ones the previous/next/full
 *  card list section in [sessionProgressItems] makes sense for. Crossword/Memory/Matching/Word
 *  Search are grid/puzzle modes with no single "current card" to center that section on. */
private val linearCardSequenceModes = setOf(
    SessionMode.FLASHCARD, SessionMode.LIST, SessionMode.MULTIPLE_CHOICE, SessionMode.TYPING,
    SessionMode.ANAGRAM, SessionMode.HANGMAN, SessionMode.AUDIO, SessionMode.TYPING_SCORED, SessionMode.FREEFORM
)

/**
 * The Progress tab: a live timer, a card holding a centered ring chart (correct/incorrect/
 * completed/remaining, correct/incorrect left out entirely for an ungraded mode - see
 * [StudyState.isGraded]) with its legend below it, a swipeable carousel of small stat cards
 * ([SessionStatCarousel]: avg. time/card, deck size, known cards, first-try-vs-retried, FSRS next
 * review, mode-specific progress, a difficulty-breakdown bar chart), and — for modes with a linear
 * card sequence — the previous and next cards, expandable to the full session list. Every number
 * here comes from [StudyState]/[DeckWithCards] fields the app already tracks; nothing here is
 * invented (no streak/fastest-card stats — this app doesn't track those today).
 */
fun LazyListScope.sessionProgressItems(state: StudyState, cardsExpanded: Boolean, onCardsExpandedChange: (Boolean) -> Unit) {
    val totalCount = state.shuffledCards.size
    val attemptedCount = state.attemptedCardIds.size
    // Ungraded modes (plain Flashcard Learn/Practice, Crossword, Memory, Word Search, Hangman...)
    // never populate firstTryCorrectCount/incorrectCardIds at all, so there's nothing honest to
    // show for "correct"/"incorrect" there - state.isGraded is the same per-session flag every
    // mode already sets at session creation to mean exactly this.
    val isGraded = state.isGraded
    val correctCount = if (isGraded) state.firstTryCorrectCount else 0
    val incorrectCount = if (isGraded) state.incorrectCardIds.size else 0
    // furthestCardIndex advances on every card navigated to, regardless of whether the mode grades
    // answers at all - this is what lets "completed"/"remaining" move for ungraded modes too, where
    // attemptedCardIds never populates. "Completed" is whatever's been reached but has no
    // correct/incorrect verdict - the whole ring for an ungraded session, or just the current card
    // mid-grading for a graded one.
    val reachedCount = (state.furthestCardIndex + 1).coerceIn(0, totalCount)
    val completedCount = (reachedCount - correctCount - incorrectCount).coerceAtLeast(0)
    val remainingCount = (totalCount - reachedCount).coerceAtLeast(0)
    // Same formula StudyCompletionScreen uses for its own accuracy badge.
    val accuracyPercent = if (totalCount == 0) 0 else (state.firstTryCorrectCount.toFloat() / totalCount * 100).roundToInt()
    val avgSecondsPerCard = if (attemptedCount == 0) null else state.elapsedSeconds() / attemptedCount

    item { DetailedTimedSessionCard(state) }

    item {
        val dimensions = LocalStudiareDimensions.current
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            // surfaceContainerHighest, not ...High - the sheet itself is already surfaceContainerHigh
            // (plus its own tonal elevation), so matching that token here left this card visually
            // indistinguishable from its background; one step up the tonal ladder plus its own
            // elevation is what actually reads as a raised card sitting on top of the sheet.
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(dimensions.paddingLarge),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
            ) {
                SessionProgressRing(correctCount, incorrectCount, completedCount, remainingCount, accuracyPercent, isGraded)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    if (isGraded) {
                        SessionLegendRow(MaterialTheme.colorScheme.primary, getText(R.string.session_sheet_correct), correctCount)
                        SessionLegendRow(MaterialTheme.colorScheme.error, getText(R.string.session_sheet_incorrect), incorrectCount)
                    }
                    SessionLegendRow(MaterialTheme.colorScheme.tertiary, getText(R.string.session_sheet_completed), completedCount)
                    SessionLegendRow(MaterialTheme.colorScheme.outlineVariant, getText(R.string.session_sheet_remaining), remainingCount)
                }
            }
        }
    }

    item { SessionStatCarousel(state, avgSecondsPerCard) }

    if (state.studyMode in linearCardSequenceModes) {
        sessionCardListItems(state, cardsExpanded, onCardsExpandedChange)
    }
}

/**
 * A swipeable row of small stat cards below the ring chart — same carousel-with-dots pattern
 * used for session tiles/presets ([CarouselDotsIndicator]). Every card here is derived purely
 * from [StudyState]/[DeckWithCards] fields the app already tracks; a card is left out entirely
 * when it wouldn't have anything meaningful to show (e.g. no FSRS card on a non-FSRS session).
 */
@Composable
private fun SessionStatCarousel(state: StudyState, avgSecondsPerCard: Long?) {
    val dimensions = LocalStudiareDimensions.current
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val deckCards = state.deckWithCards.cards
    val deckSize = deckCards.size

    val cards = buildList<@Composable () -> Unit> {
        if (avgSecondsPerCard != null) {
            add { SessionSimpleStatCard(getText(R.string.session_sheet_avg_time_per_card), formatCountdown(avgSecondsPerCard)) }
        }
        if (deckSize > 0) {
            add { SessionSimpleStatCard(getText(R.string.session_sheet_deck_size), "${state.shuffledCards.size} / $deckSize") }
            val knownCount = deckCards.count { it.isKnown }
            add { SessionSimpleStatCard(getText(R.string.session_sheet_known_in_deck), "$knownCount / $deckSize") }
        }
        if (state.allowMultipleGuesses && state.attemptedCardIds.isNotEmpty()) {
            add { SessionSimpleStatCard(getText(R.string.session_sheet_first_try), "${state.firstTryCorrectCount} / ${state.attemptedCardIds.size}") }
        }
        if (state.schedulingMode == SchedulingMode.FSRS) {
            add { SessionFsrsNextReviewCard(state) }
        }
        when (state.studyMode) {
            SessionMode.CROSSWORD -> if (state.crosswordWords.isNotEmpty()) {
                add { SessionSimpleStatCard(getText(R.string.session_sheet_words_solved), "${state.completedWordIds.size} / ${state.crosswordWords.size}") }
            }
            SessionMode.WORD_SEARCH -> if (state.wordSearchWords.isNotEmpty()) {
                add { SessionSimpleStatCard(getText(R.string.session_sheet_words_found), "${state.wordSearchFoundWordIds.size} / ${state.wordSearchWords.size}") }
            }
            SessionMode.MEMORY -> {
                val totalPairs = state.memoryActiveCardIds.size / 2
                if (totalPairs > 0) {
                    add { SessionSimpleStatCard(getText(R.string.session_sheet_pairs_matched), "${state.successfullyMatchedPairs.size} / $totalPairs") }
                }
            }
            SessionMode.HANGMAN -> add {
                SessionSimpleStatCard(getText(R.string.session_sheet_guesses_left), "${(state.hangmanMaxMistakes - state.hangmanMistakes).coerceAtLeast(0)}")
            }
            else -> {}
        }
        if (state.shuffledCards.isNotEmpty()) {
            add { SessionDifficultyBreakdownCard(state.shuffledCards) }
        }
    }

    if (cards.isEmpty()) return

    androidx.compose.foundation.lazy.LazyRow(
        state = listState,
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(cards.size) { index -> cards[index]() }
    }
    if (cards.size > 1) {
        CarouselDotsIndicator(cards.size, listState, Modifier.padding(top = dimensions.spacingSmall))
    }
}

@Composable
private fun SessionSimpleStatCard(label: String, value: String) {
    val dimensions = LocalStudiareDimensions.current
    Surface(
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.width(StatCardWidth).height(StatCardHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimensions.paddingMedium),
            verticalArrangement = Arrangement.Center
        ) {
            Text(value, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 2)
        }
    }
}

/** The per-rating "if you tap this, here's your next review" preview already shown under the
 *  grading buttons ([state.nextIntervals], keyed 1-4 Again/Hard/Good/Easy) — surfaced here too so
 *  it doesn't require flipping the card to see. */
@Composable
private fun SessionFsrsNextReviewCard(state: StudyState) {
    val dimensions = LocalStudiareDimensions.current
    val onColor = MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.width(StatCardWidth).height(StatCardHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimensions.paddingMedium),
            verticalArrangement = Arrangement.Center
        ) {
            Text(getText(R.string.session_sheet_next_review), style = MaterialTheme.typography.bodySmall, color = onColor)
            Spacer(Modifier.height(4.dp))
            listOf(
                1 to R.string.rating_again, 2 to R.string.rating_hard,
                3 to R.string.rating_good, 4 to R.string.rating_easy
            ).forEach { (rating, labelRes) ->
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(getText(labelRes), style = MaterialTheme.typography.labelSmall, color = onColor)
                    Text(state.nextIntervals[rating] ?: "–", style = MaterialTheme.typography.labelSmall, color = onColor)
                }
            }
        }
    }
}

/** Bar graph of this session's cards by [DifficultySetting] (1-5). */
@Composable
private fun SessionDifficultyBreakdownCard(cards: List<Card>) {
    val dimensions = LocalStudiareDimensions.current
    val onColor = MaterialTheme.colorScheme.onSecondaryContainer
    val barColor = MaterialTheme.colorScheme.primary
    val counts = DifficultySetting.entries.map { d -> cards.count { it.difficulty == d } }
    val maxCount = (counts.maxOrNull() ?: 0).coerceAtLeast(1)

    Surface(
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.width(StatCardWidth).height(StatCardHeight)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(dimensions.paddingMedium),
            verticalArrangement = Arrangement.Center
        ) {
            Text(getText(R.string.session_sheet_difficulty_breakdown), style = MaterialTheme.typography.bodySmall, color = onColor)
            Spacer(Modifier.height(dimensions.spacingSmall))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                counts.forEach { count ->
                    Box(modifier = Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.BottomCenter) {
                        val barHeight = if (count == 0) 0.dp else 6.dp + (30.dp * (count.toFloat() / maxCount))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(barHeight)
                                .background(barColor, RoundedCornerShape(2.dp))
                        )
                    }
                }
            }
            Spacer(Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.fillMaxWidth()) {
                DifficultySetting.entries.forEach { d ->
                    Text(
                        "${d.value}",
                        style = MaterialTheme.typography.labelSmall,
                        color = onColor,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionProgressRing(
    correctCount: Int,
    incorrectCount: Int,
    completedCount: Int,
    remainingCount: Int,
    accuracyPercent: Int,
    isGraded: Boolean
) {
    val total = correctCount + incorrectCount + completedCount + remainingCount
    val correctColor = MaterialTheme.colorScheme.primary
    val incorrectColor = MaterialTheme.colorScheme.error
    val completedColor = MaterialTheme.colorScheme.tertiary
    // Not surfaceVariant - the ring now sits inside a surfaceContainerHighest card (see
    // sessionProgressItems), and that tone is too close to surfaceVariant to read as a ring
    // segment at all; outlineVariant is designed to stay visible against container surfaces.
    val remainingColor = MaterialTheme.colorScheme.outlineVariant
    val textColor = MaterialTheme.colorScheme.onSurface
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val primaryStyle = MaterialTheme.typography.headlineSmall
    val labelStyle = MaterialTheme.typography.labelMedium
    // Computed here (composable context) rather than inside the Canvas draw lambda below, which
    // is a plain DrawScope.() -> Unit and cannot call @Composable functions like getText(). An
    // accuracy percentage is meaningless without grading, so an ungraded session shows how much
    // of the deck has been completed instead.
    val primaryText = if (isGraded) "$accuracyPercent%" else "$completedCount/${total.coerceAtLeast(1)}"
    val primaryLabel = if (isGraded) getText(R.string.session_sheet_accuracy) else getText(R.string.session_sheet_completed)

    Box(modifier = Modifier.size(160.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 20.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            if (total <= 0) {
                drawArc(remainingColor, 0f, 360f, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth))
            } else {
                var startAngle = -90f
                listOf(
                    correctCount to correctColor, incorrectCount to incorrectColor,
                    completedCount to completedColor, remainingCount to remainingColor
                ).forEach { (count, color) ->
                    val sweep = 360f * count / total
                    if (sweep > 0f) {
                        drawArc(color, startAngle, sweep, useCenter = false, topLeft = topLeft, size = arcSize, style = Stroke(strokeWidth))
                        startAngle += sweep
                    }
                }
            }
            val primaryLayout = textMeasurer.measure(primaryText, primaryStyle)
            drawText(
                textLayoutResult = primaryLayout,
                color = textColor,
                topLeft = Offset(center.x - primaryLayout.size.width / 2f, center.y - primaryLayout.size.height)
            )
            val labelLayout = textMeasurer.measure(primaryLabel, labelStyle)
            drawText(
                textLayoutResult = labelLayout,
                color = labelColor,
                topLeft = Offset(center.x - labelLayout.size.width / 2f, center.y + 4.dp.toPx())
            )
        }
    }
}

@Composable
private fun SessionLegendRow(color: Color, label: String, count: Int) {
    val dimensions = LocalStudiareDimensions.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
        Box(modifier = Modifier.size(10.dp).background(color, RoundedCornerShape(5.dp)))
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text("$count", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * The collapsed previous/next preview, or (once expanded) the full session list - items in the
 * same [LazyListScope] the rest of [sessionProgressItems] feeds, rather than a second scrollable
 * nested inside the Progress tab's own list (which used to fight it for drag gestures). Expanding
 * no longer grows a capped inner box - the whole tab just gets longer, with one scroll container
 * for the entire thing.
 */
fun LazyListScope.sessionCardListItems(state: StudyState, expanded: Boolean, onExpandedChange: (Boolean) -> Unit) {
    val cards = state.shuffledCards
    val currentIndex = state.currentCardIndex

    fun answerFor(card: Card): String {
        val promptSide = resolvePromptSide(state.quizPromptSide, card, state.resolvedAutoByDeckSide)
        return if (promptSide == CardSide.FRONT) card.back else card.front
    }
    fun promptFor(card: Card): String {
        val promptSide = resolvePromptSide(state.quizPromptSide, card, state.resolvedAutoByDeckSide)
        return if (promptSide == CardSide.FRONT) card.front else card.back
    }

    if (!expanded) {
        val previousCard = cards.getOrNull(currentIndex - 1)
        val nextCard = cards.getOrNull(currentIndex + 1)
        if (previousCard != null) {
            item {
                SessionCardRow(
                    label = getText(R.string.session_sheet_previous_card),
                    cardNumber = currentIndex, // currentIndex is itself 1-based for the previous card (0-based index - 1 + 1)
                    totalCount = cards.size,
                    prompt = promptFor(previousCard),
                    answer = answerFor(previousCard),
                    isCorrect = if (state.isGraded) previousCard.id !in state.incorrectCardIds else null
                )
            }
        }
        if (nextCard != null) {
            item {
                SessionCardRow(
                    label = getText(R.string.session_sheet_next_card),
                    cardNumber = currentIndex + 2, // 0-based index + 1 + 1
                    totalCount = cards.size,
                    prompt = promptFor(nextCard),
                    answer = null,
                    isCorrect = null
                )
            }
        }
        if (previousCard != null || nextCard != null) {
            item {
                TextButton(onClick = { onExpandedChange(true) }) { Text(getText(R.string.session_sheet_show_more)) }
            }
        }
    } else {
        item {
            TextButton(onClick = { onExpandedChange(false) }) { Text(getText(R.string.session_sheet_show_less)) }
        }
        // furthestCardIndex is the farthest point ever reached, independent of grading - an
        // ungraded mode (plain Flashcard Learn/Practice, etc.) never populates attemptedCardIds at
        // all, so gating "show the answer" on attempted alone hid it for every completed card
        // there. A card counts as completed once you've passed it, whichever got you there -
        // grading or just navigating forward.
        val reachedIndex = maxOf(currentIndex, state.furthestCardIndex)
        items(cards.size, key = { index -> cards[index].id }) { index ->
            val card = cards[index]
            val attempted = card.id in state.attemptedCardIds
            val isCurrent = index == currentIndex
            val completed = !isCurrent && (attempted || index <= reachedIndex)
            SessionCardRow(
                label = null,
                cardNumber = index + 1,
                totalCount = cards.size,
                prompt = promptFor(card),
                answer = if (completed) answerFor(card) else null,
                isCorrect = if (attempted && state.isGraded) card.id !in state.incorrectCardIds else null,
                highlighted = isCurrent
            )
        }
    }
}

@Composable
private fun SessionCardRow(
    label: String?,
    cardNumber: Int,
    totalCount: Int,
    prompt: String,
    answer: String?,
    isCorrect: Boolean?,
    highlighted: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current
    Card(
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(
            containerColor = if (highlighted) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = dimensions.spacingSmall)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (label != null) {
                        Text(
                            label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (isCorrect != null) {
                        Text(
                            if (isCorrect) "✓" else "✕",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isCorrect) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                        )
                    }
                }
                Text(
                    stringResource(R.string.card_index_of_total, cardNumber, totalCount),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(prompt, style = MaterialTheme.typography.bodyMedium, maxLines = 2)
            if (answer != null) {
                Text(answer, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}
