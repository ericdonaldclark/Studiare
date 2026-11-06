package net.ericclark.studiare.screens.Screens
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.util.*
import kotlin.math.roundToInt
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.pluralStringResource
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish
import net.ericclark.studiare.ui.theme.completionAccuracyBadgeShape
import net.ericclark.studiare.components.CardTagRow
import net.ericclark.studiare.components.getText
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.launch
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.livedata.observeAsState
import net.ericclark.studiare.screens.Dialogs.RichTextEditorDialog
import net.ericclark.studiare.screens.UI_Components.CardSideEditor
import net.ericclark.studiare.screens.UI_Components.DynamicNoteEditor

/**
 * Layout variants for [StudyCompletionBody]'s top section. Only [FULL] and [COMPACT] are used
 * today - both callers pick between them automatically (Full by default, Compact the moment the
 * Keep/Pin card expands), not as a free-form choice. [NO_ICON]/[MINIMAL] were explored in the UI
 * Playground (Settings → Debug) before that decision and are left in place as other ways to trim
 * the header's vertical space, in case that tradeoff is revisited.
 */
enum class CompletionHeaderStyle(val label: String) {
    FULL("Full"),
    NO_ICON("No Icon"),
    COMPACT("Compact (inline)"),
    MINIMAL("Minimal (1 line)")
}

/** How the Restart/Back-to-Sessions/Back-to-Home destination buttons stack, also a Playground-only knob for now. */
enum class CompletionButtonLayout(val label: String) {
    SINGLE_COLUMN("Stacked"),
    TWO_COLUMN("Back buttons side-by-side")
}

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

    val missedCards = remember(state.shuffledCards, state.incorrectCardIds) {
        state.shuffledCards.filter { it.id in state.incorrectCardIds }
    }

    var notScored = false
    if (state.studyMode == SessionMode.FLASHCARD || state.studyMode == SessionMode.TYPING || state.studyMode == SessionMode.CROSSWORD ||
        state.studyMode == SessionMode.MEMORY || state.studyMode == SessionMode.ANAGRAM || state.studyMode == SessionMode.HANGMAN ||
        state.studyMode == SessionMode.FREEFORM || state.studyMode == SessionMode.WORD_SEARCH)
        notScored = true
    // Which modes can ever have a missed card to act on: all scored modes (they always track right/
    // wrong), plus the 3 notScored modes that track misses via a self-report or give-up action
    // (Flashcard's "Missed it"/FSRS Again, Hangman running out of guesses, Anagram's reveal-answer).
    // Crossword/Memory/Word Search/Freeform never populate incorrectCardIds at all, so they're
    // excluded even though they're also notScored. Drives both the Review Missed Cards button and
    // the "Save only missed cards" toggle identically.
    val missedCardsEligibleMode = state.studyMode !in setOf(
        SessionMode.TYPING, SessionMode.CROSSWORD, SessionMode.MEMORY, SessionMode.FREEFORM, SessionMode.WORD_SEARCH
    )
    val showMissedCardsButton = missedCardsEligibleMode && missedCards.isNotEmpty()

    val allDecksState by viewModel.allDecks.observeAsState(emptyList())
    val context = androidx.compose.ui.platform.LocalContext.current

    // "Keep this session" just means "don't delete it when I leave" - a plain, unpinned active
    // session. Pinning is the separate, actual pin mechanic (always sorts first, survives every
    // exit path) - its toggle (and the name field/card-order choice) only appear once something is
    // being kept at all. None of this is persisted until an exit/restart action is actually tapped
    // (same staged-local-state convention as the app's other dialogs) - including the system back
    // button and the breadcrumbs bar, which now go through the same exitAndNavigate below instead
    // of unconditionally deleting.
    var keep by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(false) }
    var reshuffle by remember { mutableStateOf(false) }
    var missedOnly by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    val defaultName = remember(state.sessionId, state.deckWithCards) {
        net.ericclark.studiare.screens.UI_Components.defaultSessionPinName(
            context, state.studyMode, state.deckWithCards?.deck?.name ?: ""
        )
    }

    fun exitAndNavigate(navigate: () -> Unit) {
        val nameOrNull = name.trim().ifBlank { null }
        when {
            !keep -> {
                viewModel.deleteCurrentSessionUnconditionally()
                viewModel.endStudySession()
                navigate()
            }
            missedOnly -> {
                // Same machinery as the Restart button's missed-only case, just navigating away in
                // the completion callback instead of letting the new session become active here.
                viewModel.restartWithMissedCardsOnly(pin, nameOrNull) {
                    viewModel.endStudySession()
                    navigate()
                }
            }
            !reshuffle -> {
                viewModel.finishKeepingSession(pin, nameOrNull)
                viewModel.endStudySession()
                navigate()
            }
            else -> {
                // Same machinery as the Restart button's reshuffled case, just navigating away in
                // the completion callback instead of letting the new session become active here.
                viewModel.restartStudySession(pin, nameOrNull) {
                    viewModel.endStudySession()
                    navigate()
                }
            }
        }
    }

    val navigateUp = {
        // The study route was opened on top of wherever the session list lives (a pane
        // on the deck list, or a standalone route), so simply popping returns there.
        exitAndNavigate { navController.popBackStack() }
    }
    val navigateHome = {
        exitAndNavigate {
            if (state.deckWithCards?.deck?.parentDeckId != null) {
                navController.navigate("setManager/${state.deckWithCards!!.deck.parentDeckId}") {
                    popUpTo("setManager/${state.deckWithCards!!.deck.parentDeckId}") { inclusive = true }
                }
            } else {
                navController.popBackStack("deckList", inclusive = false)
            }
        }
    }

    BackHandler(onBack = navigateUp)

    // Was a hard `if (state.isComplete) { StudyCompletionScreen(...); return }` swap in every
    // study mode screen — an instant, unanimated cut (confirmed via reading all 14 of them: the
    // exact same pattern everywhere). Restructuring each of those screens' own unique Scaffold
    // into a shared AnimatedContent would be a lot of invasive, repeated surgery for comparatively
    // little payback, so instead this screen gives itself a one-shot entrance animation the moment
    // it's first composed (which is exactly when a study mode screen swaps to this one) — a
    // lateral slide only, no fade, matching the Bulk Editor <-> Simple Editor lateral-replace
    // convention elsewhere in the app. The outgoing study mode screen itself doesn't animate away
    // (it's instantly covered), which is the one piece this approach doesn't fully match the
    // catalog's two-sided "lateral slide" description, but it's self-contained to this one file
    // instead of 14, and still replaces a zero-animation cut with real motion.
    val entranceState = remember { MutableTransitionState(false) }.apply { targetState = true }
    AnimatedVisibility(
        visibleState = entranceState,
        enter = slideInHorizontally(animationSpec = tween(300, easing = FastOutSlowInEasing)) { fullWidth -> fullWidth / 4 },
        exit = ExitTransition.None
    ) {
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
                            exitAndNavigate { navController.navigate("deckList") { popUpTo(0) } }
                        },
                        onNavigateToDeck = { deckId ->
                            exitAndNavigate {
                                navController.navigate("setManager/$deckId") {
                                    popUpTo("deckList") { inclusive = false }
                                }
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
        ) {
            val score = remember(state.firstTryCorrectCount, state.shuffledCards) {
                if (state.shuffledCards.isEmpty()) 0 else (state.firstTryCorrectCount.toFloat() / state.shuffledCards.size * 100).roundToInt()
            }
            StudyCompletionBody(
                // Full header by default; shrinks to Compact the moment the Keep/Pin card expands
                // (Keep or Pin toggled on), so the header only takes the smaller, denser shape once
                // the user actually needs the extra room the card's name/order controls ask for.
                headerStyle = if (keep) CompletionHeaderStyle.COMPACT else CompletionHeaderStyle.FULL,
                buttonLayout = CompletionButtonLayout.TWO_COLUMN,
                showAccuracy = !notScored,
                accuracyPercent = score,
                showMissedCardsButton = showMissedCardsButton,
                missedCardsCount = missedCards.size,
                keep = keep,
                onKeepChange = { keep = it },
                pin = pin,
                onPinChange = { checked -> pin = checked; if (checked) keep = true },
                reshuffle = reshuffle,
                onReshuffleChange = { reshuffle = it },
                missedOnly = missedOnly,
                onMissedOnlyChange = { missedOnly = it },
                name = name,
                onNameChange = { name = it },
                defaultName = defaultName,
                onRestartSame = { viewModel.restartSameSession(pin, name.trim().ifBlank { null }) },
                onRestartReshuffled = { viewModel.restartStudySession(pin, name.trim().ifBlank { null }) {} },
                onRestartMissedOnly = { viewModel.restartWithMissedCardsOnly(pin, name.trim().ifBlank { null }) {} },
                onBackToSessions = { exitAndNavigate { navController.popBackStack() } },
                onBackToHome = { navigateHome() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(dimensions.paddingMedium)
            )
        }
    }
    }
}

/**
 * Study Completion's actual content - header, Review Missed Cards shortcut, the Keep/Pin/Name/Card
 * order module, and the Restart/Back-to-Sessions/Back-to-Home buttons. Pulled out of
 * [StudyCompletionScreen] so the UI Playground (Settings → Debug) can exercise this exact
 * production composable against sample data instead of a parallel copy, the same way SessionTile/
 * SessionPresetCard are shared with their own playground experiments - and so [headerStyle]/
 * [buttonLayout] can be tried live before either becomes the real screen's default (currently
 * always FULL/SINGLE_COLUMN there).
 */
@Composable
fun StudyCompletionBody(
    headerStyle: CompletionHeaderStyle,
    buttonLayout: CompletionButtonLayout,
    showAccuracy: Boolean,
    accuracyPercent: Int,
    showMissedCardsButton: Boolean,
    missedCardsCount: Int,
    keep: Boolean,
    onKeepChange: (Boolean) -> Unit,
    pin: Boolean,
    onPinChange: (Boolean) -> Unit,
    reshuffle: Boolean,
    onReshuffleChange: (Boolean) -> Unit,
    missedOnly: Boolean,
    onMissedOnlyChange: (Boolean) -> Unit,
    name: String,
    onNameChange: (String) -> Unit,
    defaultName: String,
    onRestartSame: () -> Unit,
    onRestartReshuffled: () -> Unit,
    onRestartMissedOnly: () -> Unit,
    onBackToSessions: () -> Unit,
    onBackToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        // Scrollable: header + the Keep/Pin card. Given its own weight so the destination buttons
        // below stay pinned to the bottom of the screen instead of scrolling away with everything
        // else - they're always reachable without hunting for them.
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
        ) {
            CompletionHeader(headerStyle = headerStyle, showAccuracy = showAccuracy, accuracyPercent = accuracyPercent)

            // --- Keep / Pin / Name / Card order module ---
            // "Keep" controls whether the session survives at all. Pin is always visible right
            // below it so pinning takes one tap instead of two - toggling it on implies "keep"
            // too, same as if the Keep switch had been flipped first (hence the "Keep and Pin"
            // label while still minimized: either switch expands the card). The name field,
            // missed-only toggle, and card order only matter once something is being kept.
            Surface(
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.fillMaxWidth(0.92f)
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingMedium), verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(getText(R.string.keep_this_session), style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                if (keep) getText(R.string.keep_this_session_caption_on) else getText(R.string.keep_this_session_caption_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // Locked on while Pin is on - pinning requires keeping, so turning Keep
                        // back off here would silently leave the session pinned but not kept,
                        // a state this screen never otherwise lets you reach.
                        StudiareSwitch(checked = keep, onCheckedChange = onKeepChange, enabled = !pin)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Icon(
                            if (pin) Icons.Default.PushPin else Icons.Outlined.PushPin,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                if (keep) getText(R.string.pin_this_session) else getText(R.string.keep_and_pin_this_session),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                if (pin) getText(R.string.pin_this_session_caption_on) else getText(R.string.pin_this_session_caption_off),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        StudiareSwitch(checked = pin, onCheckedChange = onPinChange)
                    }

                    AnimatedVisibility(visible = keep, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

                            OutlinedTextField(
                                value = name,
                                onValueChange = onNameChange,
                                placeholder = { Text(defaultName) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Same gating as the Review Missed Cards button below.
                            if (showMissedCardsButton) {
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(Modifier.width(dimensions.spacingSmall))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(getText(R.string.save_only_missed_cards), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            if (missedOnly) stringResource(R.string.save_only_missed_cards_caption_on_format, missedCardsCount)
                                            else getText(R.string.save_only_missed_cards_caption_off),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    StudiareSwitch(checked = missedOnly, onCheckedChange = onMissedOnlyChange)
                                }
                            }

                            // Missed-only always rebuilds the session from scratch (crossword/word
                            // search puzzle data included) rather than reusing shuffledCardIds
                            // directly, so "same order" doesn't apply to it the way it does here.
                            if (!missedOnly) {
                                Column {
                                    Text(
                                        getText(R.string.card_order_label).uppercase(),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(Modifier.height(dimensions.spacingSmall))
                                    net.ericclark.studiare.screens.UI_Components.ConnectedButtonGroup(
                                        items = listOf(false, true),
                                        selected = reshuffle,
                                        onSelected = onReshuffleChange,
                                        labelFor = { if (it) getText(R.string.card_order_reshuffled) else getText(R.string.card_order_same) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(dimensions.spacingSmall))
        }

        // --- Destinations: pinned to the bottom of the screen (see the weight(1f) scrollable
        // Column above), not part of the scrollable area, so they're always reachable without
        // scrolling. Restart is always visible for discoverability - pressing it without ever
        // touching "Keep this session" is treated as if Keep had been toggled on with no other
        // changes (reshuffle/pin/name/missed-only all stay at whatever they already were, which
        // is "off" if untouched). Review Missed Cards sits alongside it when there's anything to
        // review - same discoverability shortcut as before (Keep + "Save only missed cards" +
        // Restart), just living next to the button it mirrors.
        val restartLabel = when {
            missedOnly -> getText(R.string.restart_missed_only)
            reshuffle -> getText(R.string.restart_reshuffled)
            else -> getText(R.string.restart_this_session)
        }
        val onRestart: () -> Unit = {
            if (!keep) onKeepChange(true)
            when {
                missedOnly -> onRestartMissedOnly()
                reshuffle -> onRestartReshuffled()
                else -> onRestartSame()
            }
        }
        val reviewMissedLabel = pluralStringResource(R.plurals.review_missed_cards_format, missedCardsCount, missedCardsCount)
        val onReviewMissed: () -> Unit = {
            onKeepChange(true)
            onMissedOnlyChange(true)
            onRestartMissedOnly()
        }
        val backSessionsLabel = getText(R.string.back_to_sessions)
        val backHomeLabel = getText(R.string.back_to_home)

        // If any one button's label would wrap to a second line, every button is forced onto two
        // lines - not just given reserved height, since a short label like "Back to Home" doesn't
        // naturally wrap on its own and would otherwise just sit centered in a taller button next
        // to one that's actually split across two lines. Detected from the actual rendered layout
        // of the plain (unforced) label via onTextLayout, and re-armed whenever labels change;
        // once detected, every label is explicitly broken at its middle word boundary (see
        // [breakNearMiddle]) rather than left to whatever Compose's own wrap would pick.
        var anyButtonWraps by remember(restartLabel, reviewMissedLabel, backSessionsLabel, backHomeLabel, showMissedCardsButton) { mutableStateOf(false) }
        val onButtonTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = { result ->
            if (result.lineCount > 1 && !anyButtonWraps) anyButtonWraps = true
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
        ) {
            when (buttonLayout) {
                CompletionButtonLayout.SINGLE_COLUMN -> {
                    if (showMissedCardsButton) {
                        CompletionReviewMissedButton(reviewMissedLabel, onReviewMissed, Modifier.fillMaxWidth(0.92f), anyButtonWraps, onButtonTextLayout)
                    }
                    CompletionRestartButton(restartLabel, onRestart, Modifier.fillMaxWidth(0.92f), anyButtonWraps, onButtonTextLayout)
                    CompletionBackButton(backSessionsLabel, onBackToSessions, Modifier.fillMaxWidth(0.92f), anyButtonWraps, onButtonTextLayout)
                    CompletionBackButton(backHomeLabel, onBackToHome, Modifier.fillMaxWidth(0.92f), anyButtonWraps, onButtonTextLayout)
                }
                CompletionButtonLayout.TWO_COLUMN -> {
                    if (showMissedCardsButton) {
                        Row(modifier = Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                            CompletionReviewMissedButton(reviewMissedLabel, onReviewMissed, Modifier.weight(1f), anyButtonWraps, onButtonTextLayout)
                            CompletionRestartButton(restartLabel, onRestart, Modifier.weight(1f), anyButtonWraps, onButtonTextLayout)
                        }
                    } else {
                        CompletionRestartButton(restartLabel, onRestart, Modifier.fillMaxWidth(0.92f), anyButtonWraps, onButtonTextLayout)
                    }
                    Row(modifier = Modifier.fillMaxWidth(0.92f), horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        CompletionBackButton(backSessionsLabel, onBackToSessions, Modifier.weight(1f), anyButtonWraps, onButtonTextLayout)
                        CompletionBackButton(backHomeLabel, onBackToHome, Modifier.weight(1f), anyButtonWraps, onButtonTextLayout)
                    }
                }
            }

            Spacer(Modifier.height(dimensions.spacingSmall))
        }
    }
}

// Full <-> Compact is a real size/shape change (80dp icon down to 32dp, a block-shaped accuracy
// badge down to a trailing chip, subtitle moving inline), not just a content swap - AnimatedContent
// cross-fades the two and animates the bounds between their different measured sizes, rather than
// the hard cut a plain `when` would give.
@Composable
private fun CompletionHeader(headerStyle: CompletionHeaderStyle, showAccuracy: Boolean, accuracyPercent: Int) {
    AnimatedContent(
        targetState = headerStyle,
        transitionSpec = {
            (fadeIn(animationSpec = tween(220, delayMillis = 90)) + scaleIn(initialScale = 0.92f, animationSpec = tween(220, delayMillis = 90)))
                .togetherWith(fadeOut(animationSpec = tween(90)))
                .using(SizeTransform(clip = false))
        },
        label = "completion-header"
    ) { style ->
        CompletionHeaderContent(headerStyle = style, showAccuracy = showAccuracy, accuracyPercent = accuracyPercent)
    }
}

@Composable
private fun CompletionHeaderContent(headerStyle: CompletionHeaderStyle, showAccuracy: Boolean, accuracyPercent: Int) {
    val dimensions = LocalStudiareDimensions.current
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
    when (headerStyle) {
        CompletionHeaderStyle.FULL -> {
            Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
            Text(getText(R.string.congratulations), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(getText(R.string.completed_session_msg), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showAccuracy) {
                Spacer(Modifier.height(dimensions.spacingSmall))
                Surface(
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.first_try_accuracy_format, accuracyPercent),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium)
                    )
                }
            }
        }
        // Drops just the 80dp icon + its spacing - cheapest space saving that changes nothing else.
        CompletionHeaderStyle.NO_ICON -> {
            Text(getText(R.string.congratulations), style = MaterialTheme.typography.displaySmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            Text(getText(R.string.completed_session_msg), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showAccuracy) {
                Spacer(Modifier.height(dimensions.spacingSmall))
                Surface(
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                ) {
                    Text(
                        text = stringResource(R.string.first_try_accuracy_format, accuracyPercent),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium)
                    )
                }
            }
        }
        // Icon shrinks and sits inline with the title/subtitle on one row; the accuracy badge
        // becomes a fixed-size trailing shape on that same row instead of its own block below.
        CompletionHeaderStyle.COMPACT -> {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall), modifier = Modifier.fillMaxWidth(0.92f)) {
                Icon(imageVector = Icons.Default.EmojiEvents, contentDescription = null, modifier = Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
                Column(modifier = Modifier.weight(1f)) {
                    Text(getText(R.string.congratulations), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text(getText(R.string.completed_session_msg), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (showAccuracy) {
                    // A fixed square size, not wrap-content padding, so the cookie's points stay
                    // legible regardless of whether the text is "0%" or "100%".
                    Surface(
                        shape = completionAccuracyBadgeShape(),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(R.string.completion_accuracy_percent_format, accuracyPercent),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
        // No icon, no subtitle, no badge surface - one line of text, title and accuracy merged.
        // The smallest the header can get while still saying what happened.
        CompletionHeaderStyle.MINIMAL -> {
            Text(
                text = if (showAccuracy) stringResource(R.string.first_try_accuracy_format, accuracyPercent) else getText(R.string.congratulations),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
        }
    }
    }
}

// Breaks `text` at whichever space sits closest to its midpoint, e.g. "Back to Home" -> "Back
// to\nHome". Used to force every destination button onto the same two lines once any one of them
// needs it (see `anyButtonWraps` in [StudyCompletionBody]) - a plain word-wrap would leave a short
// label like "Back to Home" on one line while its neighbor splits onto two, so the break has to be
// explicit rather than left to Compose's own line-breaking.
private fun breakNearMiddle(text: String): String {
    val spaceIndices = text.indices.filter { text[it] == ' ' }
    if (spaceIndices.isEmpty()) return text
    val middle = text.length / 2.0
    val breakIndex = spaceIndices.minBy { kotlin.math.abs(it - middle) }
    return text.substring(0, breakIndex) + "\n" + text.substring(breakIndex + 1)
}

// Shared by every destination button below: icon + label. [forceTwoLines]/[onTextLayout] are how
// the caller keeps every button's line count in sync: see the `anyButtonWraps` logic in
// [StudyCompletionBody]. Before [forceTwoLines] is set, [onTextLayout] measures the plain label to
// detect whether it would wrap on its own; once it's true the *displayed* text switches to the
// explicit [breakNearMiddle] split instead of just reserving more height, so a label that happens
// to fit on one line is still broken onto two to match its neighbors - every line centered, not
// left-hung off the icon.
@Composable
private fun CompletionButtonContent(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    forceTwoLines: Boolean = false,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
) {
    Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
    Spacer(Modifier.width(8.dp))
    Text(
        if (forceTwoLines) breakNearMiddle(label) else label,
        style = MaterialTheme.typography.labelLarge,
        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        onTextLayout = onTextLayout
    )
}

@Composable
private fun CompletionRestartButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    forceTwoLines: Boolean = false,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.defaultMinSize(minHeight = 56.dp).pressSquish(interactionSource),
        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
    ) {
        CompletionButtonContent(Icons.Default.RestartAlt, label, forceTwoLines, onTextLayout)
    }
}

@Composable
private fun CompletionReviewMissedButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    forceTwoLines: Boolean = false,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    Button(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.defaultMinSize(minHeight = 56.dp).pressSquish(interactionSource),
        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        CompletionButtonContent(Icons.Default.Replay, label, forceTwoLines, onTextLayout)
    }
}

@Composable
private fun CompletionBackButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    forceTwoLines: Boolean = false,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    FilledTonalButton(
        onClick = onClick,
        interactionSource = interactionSource,
        modifier = modifier.defaultMinSize(minHeight = 56.dp).pressSquish(interactionSource),
        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
    ) {
        CompletionButtonContent(Icons.AutoMirrored.Filled.ArrowBack, label, forceTwoLines, onTextLayout)
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

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp)
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
                    TooltipIconButton(description = getText(R.string.discard_changes),
                        onClick = onDismiss,
                        interactionSource = closeInteractionSource,
                        modifier = Modifier.pressSquish(closeInteractionSource, 0.85f)
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
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressSquish(saveInteractionSource),
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
