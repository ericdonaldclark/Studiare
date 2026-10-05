package net.ericclark.studiare.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.animateBounds
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import java.util.*
import net.ericclark.studiare.*
import net.ericclark.studiare.components.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.data.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay

internal fun computeColumns(
    availableWidth: androidx.compose.ui.unit.Dp,
    minDeckWidth: androidx.compose.ui.unit.Dp = 320.dp
): Int = (availableWidth / minDeckWidth).toInt().coerceAtLeast(1)

/**
 * Only use the flowing layout (sets beside their deck) when the deck-list pane has the screen to
 * itself; once another pane opens and shares the width, fall back to the legacy grid (sets below
 * decks, in a scrollable row). Shared by the real content and its loading skeleton so the two
 * can't drift out of sync.
 */
internal fun shouldUseFlowLayout(
    gridLargeScreenLayout: Boolean,
    windowWidthSizeClass: WindowWidthSizeClass,
    visiblePaneCount: Int
): Boolean =
    gridLargeScreenLayout && windowWidthSizeClass != WindowWidthSizeClass.Compact && visiblePaneCount <= 1

@Composable
fun DeckGridContent(
    deckGroups: List<Pair<DeckSummary, List<DeckSummary>>>,
    dimensions: StudiareDimensions,
    navController: NavController,
    viewModel: FlashcardViewModel,
    displaySetsUnderDecks: Boolean,
    onDeleteRequested: (DeckSummary) -> Unit,
    useFlowLayout: Boolean = false,
    availableWidth: androidx.compose.ui.unit.Dp? = null
) {
    @Composable
    fun content(resolvedWidth: androidx.compose.ui.unit.Dp) {
        DeckSetGrid(
            deckGroups = deckGroups,
            dimensions = dimensions,
            navController = navController,
            viewModel = viewModel,
            displaySetsUnderDecks = displaySetsUnderDecks,
            onDeleteRequested = onDeleteRequested,
            useFlowLayout = useFlowLayout,
            availableWidth = resolvedWidth
        )
    }

    // Prefer the caller-supplied, already-settled width (e.g. the pane row's un-animated
    // targetPaneWidth) over measuring our own live constraints — see the call site's comment for
    // why: sizing off a width that's still mid-spring forces a full relayout on every animation
    // frame, on top of whatever else is animating.
    if (availableWidth != null) {
        content(availableWidth)
    } else {
        androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxSize()) {
            content(maxWidth)
        }
    }
}

/**
 * Container transform between the flowing layout (sets beside their deck) and the legacy layout
 * (sets below their deck, in a scrollable row).
 *
 * Deck/set cards are *not* shared composable instances across the two arrangements (an earlier
 * version of this tried that via movableContentOf, so a card could glide across the mode switch
 * itself). That was reverted: moving a movableContentOf slot between two differently-shaped parent
 * layouts briefly deactivates its node mid-move, and if a forced full-subtree remeasure lands on
 * that exact frame — which is exactly what happens when the Home button backgrounds the Activity —
 * LookaheadScope tries to remeasure the deactivated node and crashes with "measure is called on a
 * deactivated node". This is the same class of jank/stuck-layout risk an even earlier
 * SharedTransitionLayout-based attempt hit for the same reason (bridging two independently
 * composed layouts), just surfacing as a hard crash instead of visual glitches this time.
 *
 * What *is* kept from that attempt: within a single arrangement, reflow (e.g. column count
 * changing because the pane got wider/narrower while staying in the same mode) still glides via
 * [Modifier.animateBounds] inside [LookaheadScope] — that only ever repositions a card within its
 * own arrangement's existing composition tree, never moves it to a different parent, so it doesn't
 * hit the same hazard. The flow<->legacy mode switch itself instead crossfades below.
 *
 * The surrounding pane width keeps animating independently via animateDpAsState (unchanged); this
 * composable still sizes off the settled [availableWidth], not a live/animating one, for the same
 * reason as before — sizing off a width that's still mid-spring would force a full relayout on
 * every animation frame, on top of the crossfade already running.
 */
@Composable
internal fun DeckSetGrid(
    deckGroups: List<Pair<DeckSummary, List<DeckSummary>>>,
    dimensions: StudiareDimensions,
    navController: NavController,
    viewModel: FlashcardViewModel,
    displaySetsUnderDecks: Boolean,
    onDeleteRequested: (DeckSummary) -> Unit,
    useFlowLayout: Boolean,
    availableWidth: androidx.compose.ui.unit.Dp
) {
    val motionScheme = MaterialTheme.motionScheme
    val density = LocalDensity.current
    val scrollState = rememberScrollState()

    val columns = computeColumns(availableWidth)
    val deckWidth = if (useFlowLayout) {
        availableWidth / columns
    } else {
        (availableWidth - dimensions.spacingLarge * (columns - 1).coerceAtLeast(0)) / columns
    }

    // A card moving many columns at once (e.g. 3 columns -> 1) would otherwise glide slowly
    // across unrelated cards; snap larger reflows to a faster spec instead of a slow diagonal
    // drift, while still keeping it a spatial animation rather than a separate fade path.
    val largeJumpThresholdPx = with(density) { (deckWidth * 2).toPx() }
    val boundsTransform = remember(motionScheme, largeJumpThresholdPx) {
        BoundsTransform { initial, target ->
            val dx = target.left - initial.left
            val dy = target.top - initial.top
            val distance = kotlin.math.sqrt(dx * dx + dy * dy)
            if (distance > largeJumpThresholdPx) motionScheme.fastSpatialSpec() else motionScheme.defaultSpatialSpec()
        }
    }

    AnimatedContent(
        targetState = useFlowLayout,
        transitionSpec = {
            (fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                scaleIn(initialScale = 0.98f, animationSpec = motionScheme.defaultSpatialSpec()))
                .togetherWith(
                    fadeOut(animationSpec = motionScheme.defaultEffectsSpec()) +
                        scaleOut(targetScale = 0.98f, animationSpec = motionScheme.defaultSpatialSpec())
                )
        },
        label = "deckGridLayoutSwitch"
    ) { flow ->
        if (flow) {
            FlowArrangement(
                boundsTransform = boundsTransform,
                deckGroups = deckGroups,
                dimensions = dimensions,
                navController = navController,
                viewModel = viewModel,
                onDeleteRequested = onDeleteRequested,
                deckWidth = deckWidth,
                displaySetsUnderDecks = displaySetsUnderDecks,
                scrollState = scrollState
            )
        } else {
            LegacyArrangement(
                boundsTransform = boundsTransform,
                deckGroups = deckGroups,
                dimensions = dimensions,
                navController = navController,
                viewModel = viewModel,
                onDeleteRequested = onDeleteRequested,
                columns = columns,
                deckWidth = deckWidth,
                displaySetsUnderDecks = displaySetsUnderDecks,
                motionScheme = motionScheme,
                scrollState = scrollState
            )
        }
    }
}

/**
 * Legacy arrangement: decks in a chunked grid, each one's sets in a horizontally scrollable row
 * (with a paging-dot indicator) directly below it.
 */
@Composable
internal fun LegacyArrangement(
    boundsTransform: BoundsTransform,
    deckGroups: List<Pair<DeckSummary, List<DeckSummary>>>,
    dimensions: StudiareDimensions,
    navController: NavController,
    viewModel: FlashcardViewModel,
    onDeleteRequested: (DeckSummary) -> Unit,
    columns: Int,
    deckWidth: androidx.compose.ui.unit.Dp,
    displaySetsUnderDecks: Boolean,
    motionScheme: MotionScheme,
    scrollState: ScrollState
) {
    val reducedMotion = LocalReducedMotion.current

    LookaheadScope {
        val lookaheadScope = this
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(
                    start = dimensions.paddingLarge,
                    end = dimensions.paddingLarge,
                    top = 0.dp,
                    bottom = dimensions.paddingLarge
                ),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingLarge)
        ) {
            deckGroups.withIndex().toList().chunked(columns).forEach { rowGroups ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge)
                ) {
                    rowGroups.forEach { (index, group) ->
                        val (mainDeck, sets) = group
                        key(mainDeck.deck.id) {
                            Column(
                                modifier = Modifier
                                    .width(deckWidth)
                                    .animateBounds(lookaheadScope, boundsTransform = boundsTransform),
                                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                            ) {
                                DeckListItem(
                                    deck = mainDeck,
                                    dimensions = dimensions,
                                    setsCount = sets.size,
                                    onStudy = { autoOpen ->
                                        viewModel.pushPaneAfter("deckList", PaneDestination.StudyModeSelection(mainDeck.deck.id, autoOpen))
                                    },
                                    onEdit = { navController.navigate(viewModel.deckEditRoute(mainDeck.deck.id)) },
                                    onDelete = { onDeleteRequested(mainDeck) },
                                    onToggleStar = { viewModel.toggleDeckStar(mainDeck.deck) },
                                    onManageSets = {
                                        viewModel.setCurrentDeckId(mainDeck.deck.id)
                                        viewModel.setCurrentSetId(null)
                                    },
                                    index = index
                                )

                                // Plays whether sets are appearing because the preference was just
                                // turned on, or because a second pane just closed and this
                                // arrangement just became active — both read the same way: "the
                                // carousel is appearing."
                                AnimatedVisibility(
                                    visible = sets.isNotEmpty() && displaySetsUnderDecks,
                                    enter = slideInVertically(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        initialOffsetY = { it / 4 }
                                    ) + fadeIn() + expandVertically(),
                                    exit = slideOutVertically(
                                        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                                        targetOffsetY = { -it / 4 }
                                    ) + fadeOut() + shrinkVertically()
                                ) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = dimensions.paddingSmall)
                                    ) {
                                        val listState = rememberLazyListState()

                                        LazyRow(
                                            state = listState,
                                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                                        ) {
                                            // Deliberately no Modifier.animateBounds here: LazyRow
                                            // recycles off-screen items into a reuse pool
                                            // (deactivating their layout nodes), and a bounds
                                            // animation still tracking a node when it gets
                                            // deactivated crashes the next forced full-subtree
                                            // remeasure (e.g. the one Activity.onPause triggers)
                                            // with "measure is called on a deactivated node".
                                            items(sets, key = { it.deck.id }) { set ->
                                                SetListItem(
                                                    deck = set,
                                                    dimensions = dimensions,
                                                    onStudy = { autoOpen ->
                                                        viewModel.pushPaneAfter("deckList", PaneDestination.StudyModeSelection(set.deck.id, autoOpen))
                                                    },
                                                    onOpenSets = {
                                                        viewModel.setCurrentDeckId(mainDeck.deck.id)
                                                        viewModel.setCurrentSetId(null)
                                                    }
                                                )
                                            }
                                        }

                                        if (sets.size > 1) {
                                            // Staggered fade-in: the dots settle in slightly after
                                            // the row itself, instead of popping in at the same
                                            // instant.
                                            var dotsVisible by remember(mainDeck.deck.id) { mutableStateOf(false) }
                                            LaunchedEffect(mainDeck.deck.id, reducedMotion) {
                                                dotsVisible = false
                                                if (!reducedMotion) delay(120)
                                                dotsVisible = true
                                            }

                                            AnimatedVisibility(
                                                visible = dotsVisible,
                                                enter = fadeIn(motionScheme.fastEffectsSpec())
                                            ) {
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
                                                    sets.indices.forEach { dotIndex ->
                                                        val isSelected = dotIndex == currentIndex
                                                        val width by animateDpAsState(
                                                            targetValue = if (isSelected) 24.dp else 8.dp,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            ),
                                                            label = "dotWidth"
                                                        )
                                                        val color by animateColorAsState(
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
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Desktop layout: sets flow to the right of their parent deck instead of peeking in a
 * horizontal strip beneath it. Every deck card and every set card is wrapped to the same row
 * height, with sets bottom-aligned within it, so a row of pure set continuations (once a deck's
 * sets run past the end of a line) is exactly as tall as a row that starts with a deck. Wraps via
 * a single continuous [FlowRow]: a deck's sets flow right until they hit the edge, wrap to a new
 * full-height line, and once a deck runs out of sets the next deck continues the same flow.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun FlowArrangement(
    boundsTransform: BoundsTransform,
    deckGroups: List<Pair<DeckSummary, List<DeckSummary>>>,
    dimensions: StudiareDimensions,
    navController: NavController,
    viewModel: FlashcardViewModel,
    onDeleteRequested: (DeckSummary) -> Unit,
    deckWidth: androidx.compose.ui.unit.Dp,
    displaySetsUnderDecks: Boolean,
    scrollState: ScrollState
) {
    LookaheadScope {
        val lookaheadScope = this
        Box(modifier = Modifier.verticalScroll(scrollState)) {
            // Uses the real FlowRow (not the local wrapper above) so each item can be aligned
            // within its own row: a row's height is naturally whichever item in it is tallest,
            // so a row that starts with a deck is deck-height with its sets bottom-aligned to
            // match, while a row of pure set continuations is just the (shorter) height of a set.
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = dimensions.paddingLarge,
                        end = dimensions.paddingLarge,
                        top = 0.dp,
                        bottom = dimensions.paddingLarge
                    ),
                // Spacing is applied manually below instead of uniformly here, so sets can sit
                // closer together than the gap around each deck.
                horizontalArrangement = Arrangement.spacedBy(0.dp),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingLarge)
            ) {
                deckGroups.forEachIndexed { deckIndex, (mainDeck, sets) ->
                    if (deckIndex > 0) Spacer(Modifier.width(dimensions.spacingLarge))
                    key(mainDeck.deck.id) {
                        Box(
                            modifier = Modifier
                                .width(deckWidth)
                                .align(Alignment.Top)
                                .animateBounds(lookaheadScope, boundsTransform = boundsTransform)
                        ) {
                            DeckListItem(
                                deck = mainDeck,
                                dimensions = dimensions,
                                setsCount = sets.size,
                                onStudy = { autoOpen ->
                                    viewModel.pushPaneAfter("deckList", PaneDestination.StudyModeSelection(mainDeck.deck.id, autoOpen))
                                },
                                onEdit = { navController.navigate(viewModel.deckEditRoute(mainDeck.deck.id)) },
                                onDelete = { onDeleteRequested(mainDeck) },
                                onToggleStar = { viewModel.toggleDeckStar(mainDeck.deck) },
                                onManageSets = {
                                    viewModel.setCurrentDeckId(mainDeck.deck.id)
                                    viewModel.setCurrentSetId(null)
                                }
                            )
                        }
                    }
                    if (displaySetsUnderDecks) {
                        sets.forEachIndexed { setIndex, set ->
                            Spacer(Modifier.width(if (setIndex == 0) dimensions.spacingLarge else dimensions.spacingLarge / 2))
                            key(set.deck.id) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Bottom)
                                        .animateBounds(lookaheadScope, boundsTransform = boundsTransform)
                                ) {
                                    SetListItem(
                                        deck = set,
                                        dimensions = dimensions,
                                        onStudy = { autoOpen ->
                                            viewModel.pushPaneAfter("deckList", PaneDestination.StudyModeSelection(set.deck.id, autoOpen))
                                        },
                                        onOpenSets = {
                                            viewModel.setCurrentDeckId(mainDeck.deck.id)
                                            viewModel.setCurrentSetId(null)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FlowRow(
    modifier: Modifier = Modifier,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit
) {
    androidx.compose.foundation.layout.FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = verticalArrangement,
        content = { content() }
    )
}
