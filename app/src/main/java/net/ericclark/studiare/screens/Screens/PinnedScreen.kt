package net.ericclark.studiare.screens.Screens
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.screens.Dialogs.CardEditDialog
import net.ericclark.studiare.screens.Dialogs.CardEditDialogMode
import net.ericclark.studiare.screens.Dialogs.createStudySessionRoute
import net.ericclark.studiare.screens.Dialogs.studyRouteFor
import net.ericclark.studiare.screens.UI_Components.DeckListItem
import net.ericclark.studiare.screens.UI_Components.PinnedSortDialog
import net.ericclark.studiare.screens.UI_Components.SessionPresetCard
import net.ericclark.studiare.screens.UI_Components.SessionTile
import net.ericclark.studiare.screens.UI_Components.rememberDeckSessionActions
import net.ericclark.studiare.screens.UI_Components.sortedPinnedDecks
import net.ericclark.studiare.screens.UI_Components.sortedTiles
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * The bottom dock's "Pinned" screen — a cross-cutting view of everything the user has pinned
 * across the app. Five fixed sections, in this order — Presets, Saved Sessions, Decks, Sets,
 * Cards — each its own horizontally-scrolling carousel with a position-indicator dot row, the
 * same look [SessionTileCarousel] (Study Hub) uses, via the shared [PinnedCarouselSection]. Decks
 * and sets sort together (one sort control, [PinnedSortDialog]) but display as two separate
 * carousels since they're visually distinct tiles. Reuses the same tiles their native screens
 * use — [DeckListItem] for both decks and sets (sets render full-size here, the same way the Set
 * Manager screen shows them, not the smaller [net.ericclark.studiare.screens.UI_Components.SetListItem]
 * the Decks screen's grid uses) and [CardTile] (Simple Editor) — rather than bespoke rows, so
 * behavior (study, edit, delete, resume, etc.) stays identical everywhere a deck/set/card appears.
 * A set's tile shows its parent-deck path (e.g. "Nouns -> Set 1", or "Nouns...Set 1" with
 * generations skipped — and a tooltip with the full path — when nested more than one level deep
 * under another set) via [DeckListItem]'s `displayName`/`fullPathTooltip` params, computed by
 * [setAncestryChain]. Unpinning from this screen removes the item from view, so every unpin here
 * gets an undo snackbar.
 *
 * Presets (cross-deck, same as every other section here) shows [SessionPreset]s via
 * [SessionPresetCard] (`StudyHubTiles.kt`) with `showTitle = true` always — unlike the Study
 * Hub's own preset carousel, which hides the title unless the preset has a name — falling back to
 * the same deck/set-name pattern [SessionTile]'s `showDeckNameInTitle` uses. No independent sort
 * control: the DAO's own `ORDER BY createdAt DESC` is the only ordering. A preset only ever
 * exists pinned (no separate pinned/unpinned state), so unpinning it from the card's overflow
 * menu removes it outright (via [FlashcardViewModel.setPresetDeleted], the same undo-snackbar
 * pattern as every other section); [hasAnyPinnedContent]/"Unpin All" both include presets too.
 */
@Composable
fun PinnedScreen(navController: NavController, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val allDecks by viewModel.allDecks.observeAsState(emptyList())
    val presets by viewModel.presets.collectAsState()
    val pinnedCards by viewModel.pinnedCards.collectAsState()
    val activeSessions by viewModel.allActiveSessions.collectAsState()
    val allTagDefinitions by viewModel.tags.collectAsState()

    val deckSortMode by viewModel.pinnedScreenDeckSortMode.collectAsState()
    val cardSortMode by viewModel.pinnedScreenCardSortMode.collectAsState()
    val sessionSortMode by viewModel.pinnedScreenSessionSortMode.collectAsState()
    val sessionSortDirection by viewModel.pinnedScreenSessionSortDirection.collectAsState()
    val sectionOrder by viewModel.pinnedScreenSectionOrder.collectAsState()

    val pinnedDeckSummaries = remember(allDecks, deckSortMode) {
        val sorted = sortedPinnedDecks(allDecks.map { it.deck }.filter { it.isPinned }, deckSortMode)
        sorted.map { deck ->
            DeckSummary(
                deck = deck,
                totalCards = allDecks.find { it.deck.id == deck.id }?.cards?.size ?: 0
            )
        }
    }
    // Decks and sets sort together (one sort mode/control) but display as two separate carousels
    // — a set is just a Deck row with a non-null parentDeckId.
    val pinnedDecksOnly = remember(pinnedDeckSummaries) { pinnedDeckSummaries.filter { it.deck.parentDeckId == null } }
    val pinnedSetsOnly = remember(pinnedDeckSummaries) { pinnedDeckSummaries.filter { it.deck.parentDeckId != null } }
    val sortedPinnedCards = remember(pinnedCards, cardSortMode) {
        when (cardSortMode) {
            DeckSortMode.A_TO_Z -> pinnedCards.sortedBy { it.front.lowercase() }
            DeckSortMode.Z_TO_A -> pinnedCards.sortedByDescending { it.front.lowercase() }
            DeckSortMode.ONE_TO_FIVE -> pinnedCards.sortedBy { it.difficulty.value }
            DeckSortMode.FIVE_TO_ONE -> pinnedCards.sortedByDescending { it.difficulty.value }
            DeckSortMode.DATE_ADDED_NEW_TO_OLD -> pinnedCards.sortedByDescending { it.createdAt }
            DeckSortMode.DATE_ADDED_OLD_TO_NEW -> pinnedCards.sortedBy { it.createdAt }
            DeckSortMode.DATE_MODIFIED_NEW_TO_OLD -> pinnedCards.sortedByDescending { it.updatedAt }
            DeckSortMode.DATE_MODIFIED_OLD_TO_NEW -> pinnedCards.sortedBy { it.updatedAt }
        }
    }
    val pinnedSessions = remember(activeSessions, sessionSortMode, sessionSortDirection) {
        sortedTiles(activeSessions.filter { it.isPinned }, sessionSortMode, sessionSortDirection)
    }

    var showSortDialog by remember { mutableStateOf(false) }
    var editingCard by remember { mutableStateOf<Card?>(null) }
    var showRestartDialog by remember { mutableStateOf<ActiveSession?>(null) }
    var showDeleteSessionDialog by remember { mutableStateOf<ActiveSession?>(null) }
    var showDeleteDeckDialog by remember { mutableStateOf<DeckSummary?>(null) }

    // Same helper DecksScreen/SetsScreen use for a deck tile's own embedded "resume/restart/
    // create session" actions — separate from the Saved Sessions section's own SessionTiles below.
    val sessionActions = rememberDeckSessionActions(viewModel, navController)

    // Resume a tapped SessionTile, then open its study screen once the ViewModel has loaded it —
    // same pattern as RecentsScreen.kt and StudyScreens.kt.
    var pendingSessionId by remember { mutableStateOf<String?>(null) }
    var pendingRoute by remember { mutableStateOf<String?>(null) }
    val currentStudyState = viewModel.studyState
    LaunchedEffect(currentStudyState?.sessionId, pendingRoute, pendingSessionId) {
        if (pendingRoute != null && currentStudyState?.sessionId == pendingSessionId) {
            navController.navigate(pendingRoute!!)
            pendingRoute = null
            pendingSessionId = null
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    // Multi-select (see the roadmap plan file's "App-wide multi-select" section). Mixed-type
    // selection across every carousel is deliberately allowed — Pinned's whole point is a
    // cross-cutting view, and Unpin applies identically to every type here. This also resolves
    // the old "Unpin All" promise the overflow-menu removal left orphaned (see history): Select
    // All + Unpin is a strictly better replacement (selective, not all-or-nothing), so the
    // now-dead showUnpinAllDialog state/dialog that was kept "ready for whatever this turns out
    // to be" was removed rather than left orphaned now that it's clear what that is.
    val selection = net.ericclark.studiare.screens.UI_Components.rememberSelectionController()
    androidx.activity.compose.BackHandler(enabled = selection.inSelectionMode) { selection.exit() }

    fun showUndoSnackbar(onUndo: () -> Unit) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = getText(context, R.string.item_unpinned_toast),
                actionLabel = getText(context, R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) onUndo()
        }
    }
    fun unpinDeckWithUndo(deck: Deck) {
        viewModel.setDeckPinned(deck, false)
        showUndoSnackbar { viewModel.setDeckPinned(deck, true) }
    }
    fun unpinCardWithUndo(card: Card) {
        viewModel.setCardPinned(card, false)
        showUndoSnackbar { viewModel.setCardPinned(card, true) }
    }
    fun unpinSessionWithUndo(session: ActiveSession) {
        viewModel.unpinSession(session)
        showUndoSnackbar { viewModel.pinSession(session, session.name ?: "") }
    }
    fun unpinPresetWithUndo(preset: SessionPreset) {
        viewModel.setPresetDeleted(preset, true)
        showUndoSnackbar { viewModel.setPresetDeleted(preset, false) }
    }
    // Bulk version of the four functions above — one undo snackbar for the whole selection
    // instead of each selected item's own unpin triggering (and immediately dismissing) its own.
    fun unpinSelectedWithUndo() {
        val ids = selection.selected
        val undos = mutableListOf<() -> Unit>()
        pinnedDeckSummaries.filter { "deck-${it.deck.id}" in ids }.forEach { summary ->
            viewModel.setDeckPinned(summary.deck, false)
            undos.add { viewModel.setDeckPinned(summary.deck, true) }
        }
        sortedPinnedCards.filter { "card-${it.id}" in ids }.forEach { card ->
            viewModel.setCardPinned(card, false)
            undos.add { viewModel.setCardPinned(card, true) }
        }
        pinnedSessions.filter { "session-${it.id}" in ids }.forEach { session ->
            viewModel.unpinSession(session)
            undos.add { viewModel.pinSession(session, session.name ?: "") }
        }
        presets.filter { "preset-${it.id}" in ids }.forEach { preset ->
            viewModel.setPresetDeleted(preset, true)
            undos.add { viewModel.setPresetDeleted(preset, false) }
        }
        selection.exit()
        if (undos.isNotEmpty()) showUndoSnackbar { undos.forEach { it() } }
    }

    // Every selectable key currently on screen, across all five sections — Select All's scope.
    // Keyed the same way each section's own LazyColumn item key already is ("deck-$id" doubles
    // for both decks and sets, same as PinnedCarouselSection's own key lambdas below).
    val allVisiblePinnedKeys = remember(presets, pinnedSessions, pinnedDecksOnly, pinnedSetsOnly, sortedPinnedCards) {
        buildSet {
            presets.forEach { add("preset-${it.id}") }
            pinnedSessions.forEach { add("session-${it.id}") }
            pinnedDecksOnly.forEach { add("deck-${it.deck.id}") }
            pinnedSetsOnly.forEach { add("deck-${it.deck.id}") }
            sortedPinnedCards.forEach { add("card-${it.id}") }
        }
    }

    val hasAnyPinnedContent = pinnedDeckSummaries.isNotEmpty() || sortedPinnedCards.isNotEmpty() || pinnedSessions.isNotEmpty() || presets.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                title = { Text(getText(R.string.pinned), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                actions = {
                    TooltipIconButton(description = getText(R.string.sort), onClick = { showSortDialog = true }) {
                        Icon(Icons.Default.Sort, contentDescription = getText(R.string.sort))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (selection.inSelectionMode) {
                val selectAll = net.ericclark.studiare.screens.UI_Components.SelectionAction(
                    id = "selectAll",
                    icon = androidx.compose.material.icons.Icons.Default.SelectAll,
                    label = getText(context, R.string.select_all),
                    onClick = { selection.toggleAllWithin(allVisiblePinnedKeys) }
                )
                val cardActions = remember(selection.selected, allVisiblePinnedKeys) {
                    if (selection.selected.isEmpty()) {
                        listOf(selectAll)
                    } else {
                        listOf(
                            net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                id = "unpin",
                                icon = androidx.compose.material.icons.Icons.Outlined.PushPin,
                                label = getText(context, R.string.unpin),
                                onClick = { unpinSelectedWithUndo() }
                            ),
                            selectAll
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                ) {
                    net.ericclark.studiare.screens.UI_Components.SelectionActionBar(
                        actions = cardActions,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        androidx.compose.material3.FloatingActionButton(
                            onClick = { selection.exit() },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Icon(Icons.Default.Close, contentDescription = getText(R.string.cancel))
                        }
                        net.ericclark.studiare.screens.UI_Components.SelectionCountTile(
                            selectedCount = selection.selected.size,
                            totalCount = allVisiblePinnedKeys.size,
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-32).dp)
                        )
                    }
                }
            }
        }
    ) { padding ->
        if (!hasAnyPinnedContent) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(getText(R.string.nothing_pinned_yet), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Text(
                        getText(R.string.nothing_pinned_yet_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = dimensions.paddingLarge, end = dimensions.paddingLarge,
                    top = dimensions.paddingLarge, bottom = dimensions.paddingLarge
                ),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
            ) {
                // Four of this screen's five sections (everything except Cards) are reorderable
                // from the sort dialog's new drag/arrow editor at the top — rendered here in
                // whatever order `sectionOrder` currently says, via the `when` below, instead of
                // a fixed sequence of `item { }` calls.
                sectionOrder.forEach { sectionType ->
                    when (sectionType) {
                        // Cross-deck, same as the other sections — every preset regardless of
                        // which deck/set it belongs to. No independent sort control (unlike its
                        // sibling sections): the DAO's own `ORDER BY createdAt DESC` is the only
                        // ordering for now, matching PresetCarousel's own lack of one on the Study
                        // Hub.
                        PinnedSectionType.PRESETS -> item {
                            PinnedCarouselSection(
                                title = getText(R.string.pinned_presets),
                                items = presets,
                                key = { "preset-${it.id}" },
                                itemWidth = 360.dp
                            ) { preset ->
                                val deck = allDecks.find { it.deck.id == preset.deckId }
                                // `deck` is a set (child deck) when it has a parentDeckId — its
                                // own `name` is then the set's name, and the real deck name comes
                                // from its parent.
                                val setName = if (deck?.deck?.parentDeckId != null) deck.deck.name else null
                                val deckName = if (deck?.deck?.parentDeckId != null) {
                                    allDecks.find { it.deck.id == deck.deck.parentDeckId }?.deck?.name ?: ""
                                } else {
                                    deck?.deck?.name ?: ""
                                }
                                SessionPresetCard(
                                    preset = preset,
                                    deckName = deckName,
                                    setName = setName,
                                    showTitle = true,
                                    decks = allDecks,
                                    onCreateSession = {
                                        if (deck != null) {
                                            viewModel.startSessionFromPreset(preset, deck) {
                                                navController.navigate(studyRouteFor(preset.sessionMode))
                                            }
                                        }
                                    },
                                    onEditSettings = {
                                        navController.navigate(createStudySessionRoute(preset.deckId, preset.displayCategory(), preset.id))
                                    },
                                    onUnpin = { unpinPresetWithUndo(preset) },
                                    onRename = { newName -> viewModel.renamePreset(preset, newName) },
                                    onChangeDeckStartSession = { newDeckId ->
                                        allDecks.find { it.deck.id == newDeckId }?.let { newDeck ->
                                            viewModel.startSessionFromPreset(preset, newDeck) {
                                                navController.navigate(studyRouteFor(preset.sessionMode))
                                            }
                                        }
                                    },
                                    onChangeDeckSave = { newDeckId -> viewModel.updatePresetDeck(preset, newDeckId) },
                                    onChangeDeckPinAsNew = { newDeckId, name -> viewModel.pinPresetWithNewDeck(preset, newDeckId, name) },
                                    selected = selection.isSelected("preset-${preset.id}"),
                                    selectionMode = selection.inSelectionMode,
                                    onToggleSelect = { selection.toggle("preset-${preset.id}") },
                                    onLongClick = { selection.enterWith("preset-${preset.id}") }
                                )
                            }
                        }
                        PinnedSectionType.SAVED_SESSIONS -> item {
                            PinnedCarouselSection(
                                title = getText(R.string.pinned_saved_sessions),
                                items = pinnedSessions,
                                key = { "session-${it.id}" },
                                itemWidth = 360.dp
                            ) { session ->
                                val deck = allDecks.find { it.deck.id == session.deckId }
                                val card = deck?.cards?.find {
                                    it.id == (if (session.mode == SessionMode.MATCHING && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(session.currentCardIndex))
                                }
                                // `deck` is a set (child deck) when it has a parentDeckId — its
                                // own `name` is then the set's name, and the real deck name comes
                                // from its parent.
                                val setName = if (deck?.deck?.parentDeckId != null) deck.deck.name else null
                                val deckName = if (deck?.deck?.parentDeckId != null) {
                                    allDecks.find { it.deck.id == deck.deck.parentDeckId }?.deck?.name ?: ""
                                } else {
                                    deck?.deck?.name ?: ""
                                }
                                SessionTile(
                                    session = session,
                                    card = card,
                                    deckName = deckName,
                                    setName = setName,
                                    onResume = {
                                        pendingSessionId = session.id
                                        pendingRoute = studyRouteFor(session.mode)
                                        viewModel.resumeStudySession(session)
                                    },
                                    onCopy = { viewModel.copySession(session) },
                                    onRestart = { showRestartDialog = session },
                                    onDelete = { showDeleteSessionDialog = session },
                                    onPin = { name -> viewModel.pinSession(session, name) },
                                    onUnpin = { unpinSessionWithUndo(session) },
                                    onRename = { name -> viewModel.renameSession(session, name) },
                                    showModeName = true,
                                    showCategoryNameIcon = true,
                                    showDeckNameInTitle = true,
                                    selected = selection.isSelected("session-${session.id}"),
                                    selectionMode = selection.inSelectionMode,
                                    onToggleSelect = { selection.toggle("session-${session.id}") },
                                    onLongClick = { selection.enterWith("session-${session.id}") }
                                )
                            }
                        }
                        PinnedSectionType.DECKS -> item {
                            PinnedCarouselSection(
                                title = getText(R.string.pinned_decks),
                                items = pinnedDecksOnly,
                                key = { "deck-${it.deck.id}" },
                                itemWidth = 360.dp
                            ) { summary ->
                                DeckListItem(
                                    deck = summary,
                                    dimensions = dimensions,
                                    setsCount = allDecks.count { it.deck.parentDeckId == summary.deck.id },
                                    onStudy = { autoOpen ->
                                        val route = if (autoOpen != null) "studyModeSelection/${summary.deck.id}?autoOpen=$autoOpen" else "studyModeSelection/${summary.deck.id}"
                                        if (summary.totalCards > 0) navController.navigate(route)
                                    },
                                    onEdit = { navController.navigate(viewModel.deckEditRoute(summary.deck.id)) },
                                    onDelete = { showDeleteDeckDialog = summary },
                                    onTogglePin = { unpinDeckWithUndo(summary.deck) },
                                    onManageSets = { navController.navigate("setManager/${summary.deck.id}") },
                                    activeSession = sessionActions.latestFor(summary.deck.id),
                                    onResumeSession = { sessionActions.resume(it) },
                                    onRestartSession = { sessionActions.restart(it) },
                                    onCreateSession = { sessionActions.startNewSession(summary.deck.id) },
                                    selected = selection.isSelected("deck-${summary.deck.id}"),
                                    selectionMode = selection.inSelectionMode,
                                    onToggleSelect = { selection.toggle("deck-${summary.deck.id}") },
                                    onLongClick = { selection.enterWith("deck-${summary.deck.id}") }
                                )
                            }
                        }
                        PinnedSectionType.SETS -> item {
                            // Full-size, same as the Set Manager screen (SetsScreen.kt) — it
                            // renders sets via DeckListItem (tapOpensStudy = false,
                            // showManageSetsButton = true), not the smaller SetListItem the Decks
                            // screen's grid uses.
                            PinnedCarouselSection(
                                title = getText(R.string.pinned_sets),
                                items = pinnedSetsOnly,
                                key = { "deck-${it.deck.id}" },
                                itemWidth = 360.dp
                            ) { summary ->
                                // Root deck first, this set last — a set's parentDeckId can point
                                // at another set (nested sets), not just a root deck, confirmed by
                                // SetsScreen.kt's own child-set lookup.
                                val ancestryChain = setAncestryChain(summary.deck, allDecks)
                                val setDisplayName = if (ancestryChain.size > 2) {
                                    "${ancestryChain.first()}...${ancestryChain.last()}"
                                } else {
                                    ancestryChain.joinToString(" -> ")
                                }
                                val setFullPathTooltip = if (ancestryChain.size > 2) ancestryChain.joinToString(" -> ") else null
                                DeckListItem(
                                    deck = summary,
                                    dimensions = dimensions,
                                    setsCount = allDecks.count { it.deck.parentDeckId == summary.deck.id },
                                    onStudy = { autoOpen ->
                                        val route = if (autoOpen != null) "studyModeSelection/${summary.deck.id}?autoOpen=$autoOpen" else "studyModeSelection/${summary.deck.id}"
                                        if (summary.totalCards > 0) navController.navigate(route)
                                    },
                                    onEdit = { navController.navigate(viewModel.deckEditRoute(summary.deck.id)) },
                                    onDelete = { showDeleteDeckDialog = summary },
                                    onTogglePin = { unpinDeckWithUndo(summary.deck) },
                                    onManageSets = { navController.navigate("setManager/${summary.deck.id}") },
                                    showManageSetsButton = true,
                                    tapOpensStudy = false,
                                    activeSession = sessionActions.latestFor(summary.deck.id),
                                    onResumeSession = { sessionActions.resume(it) },
                                    onRestartSession = { sessionActions.restart(it) },
                                    onCreateSession = { sessionActions.startNewSession(summary.deck.id) },
                                    displayName = setDisplayName,
                                    fullPathTooltip = setFullPathTooltip,
                                    selected = selection.isSelected("deck-${summary.deck.id}"),
                                    selectionMode = selection.inSelectionMode,
                                    onToggleSelect = { selection.toggle("deck-${summary.deck.id}") },
                                    onLongClick = { selection.enterWith("deck-${summary.deck.id}") }
                                )
                            }
                        }
                    }
                }
                item {
                    PinnedCarouselSection(
                        title = getText(R.string.pinned_cards),
                        items = sortedPinnedCards,
                        key = { "card-${it.id}" },
                        itemWidth = 320.dp
                    ) { card ->
                        val cardDeckName = allDecks.find { it.cards.any { c -> c.id == card.id } }?.deck?.name ?: ""
                        Column {
                            if (cardDeckName.isNotBlank()) {
                                Text(
                                    cardDeckName,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = dimensions.paddingSmall, bottom = 2.dp)
                                )
                            }
                            CardTile(
                                card = card,
                                cardNumber = 1,
                                totalCards = 1,
                                allTags = allTagDefinitions,
                                toEditorState = { it.toEditorState() },
                                onClick = { if (selection.inSelectionMode) selection.toggle("card-${card.id}") else editingCard = card },
                                selected = selection.isSelected("card-${card.id}"),
                                onLongClick = { selection.enterWith("card-${card.id}") }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSortDialog) {
        PinnedSortDialog(
            initialDeckSortMode = deckSortMode,
            initialCardSortMode = cardSortMode,
            initialSessionSortMode = sessionSortMode,
            initialSessionSortDirection = sessionSortDirection,
            initialSectionOrder = sectionOrder,
            onSave = { newDeckSort, newCardSort, newSessionSort, newSessionDirection, newSectionOrder ->
                viewModel.setPinnedScreenDeckSortMode(newDeckSort)
                viewModel.setPinnedScreenCardSortMode(newCardSort)
                viewModel.setPinnedScreenSessionSortMode(newSessionSort)
                viewModel.setPinnedScreenSessionSortDirection(newSessionDirection)
                viewModel.setPinnedScreenSectionOrder(newSectionOrder)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }


    showRestartDialog?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.restart_session_title),
            text = getText(R.string.restart_session_desc),
            onConfirm = {
                val reset = viewModel.restartSession(session)
                showRestartDialog = null
                pendingSessionId = reset.id
                pendingRoute = studyRouteFor(reset.mode)
                viewModel.resumeStudySession(reset)
            },
            onDismiss = { showRestartDialog = null }
        )
    }

    showDeleteSessionDialog?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.delete_session_title),
            text = getText(R.string.delete_session_desc),
            onConfirm = { viewModel.deleteSession(session); showDeleteSessionDialog = null },
            onDismiss = { showDeleteSessionDialog = null }
        )
    }

    showDeleteDeckDialog?.let { deckToDelete ->
        ConfirmationDialog(
            title = getText(R.string.delete_deck_question),
            text = androidx.compose.ui.res.stringResource(R.string.delete_deck_confirm, deckToDelete.deck.name),
            icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
            confirmButtonText = getText(R.string.delete),
            onConfirm = { viewModel.deleteDeck(deckToDelete.deck.id); showDeleteDeckDialog = null },
            onDismiss = { showDeleteDeckDialog = null }
        )
    }

    editingCard?.let { card ->
        val deck = remember(card.id, allDecks) { allDecks.find { it.cards.any { c -> c.id == card.id } } }
        val editState = remember(card.id) { card.toEditorState() }
        val currentDeckTags = remember(deck?.cards) { deck?.cards?.flatMap { it.tags }?.toSet() ?: emptySet() }
        CardEditDialog(
            cardState = editState,
            cardNumber = 1,
            totalCards = 1,
            allTags = allTagDefinitions,
            currentDeckTags = currentDeckTags,
            onUpdateTags = { newTags -> editState.tags.value = newTags.toList() },
            onCreateTag = { name, color -> viewModel.saveTagDefinition(TagDefinition(name = name, color = color)) },
            mode = CardEditDialogMode.Edit(
                onSave = {
                    deck?.let { viewModel.saveSingleCard(it, cardDataFromState(editState)) }
                    editingCard = null
                },
                onDelete = {
                    deck?.let { d ->
                        viewModel.deleteSingleCard(d, card.id)
                        coroutineScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            val result = snackbarHostState.showSnackbar(
                                message = getText(context, R.string.card_deleted),
                                actionLabel = getText(context, R.string.undo),
                                duration = SnackbarDuration.Short
                            )
                            if (result == SnackbarResult.ActionPerformed) {
                                viewModel.saveSingleCard(d, card.toCardDataForSave())
                            }
                        }
                    }
                    editingCard = null
                }
            ),
            onCancel = { editingCard = null },
            normalizationType = deck?.deck?.normalizationType ?: NormalizationType.NONE
        )
    }
}

/** Mirrors SimpleEditorScreen.kt's own local cardDataFrom() — duplicated rather than shared since
 * that one is a private function scoped to that screen's own blank-card-template defaults. */
private fun cardDataFromState(state: CardEditorState): CardDataForSave = CardDataForSave(
    id = state.id,
    front = state.front.value.trim(),
    frontRichText = state.frontRichTextInfo.value?.trim(),
    back = state.back.value.trim(),
    backRichText = state.backRichTextInfo.value?.trim(),
    frontNotes = state.frontNotes.value,
    backNotes = state.backNotes.value,
    difficulty = state.difficulty.value,
    isKnown = state.isKnown.value,
    reviewedCount = state.reviewedCount.value,
    gradedAttempts = state.gradedAttempts.value,
    incorrectAttempts = state.incorrectAttempts.value,
    reviewLogs = state.reviewLogs.value,
    absoluteDueDate = state.absoluteDueDate.value,
    tags = state.tags.value,
    isSuspended = state.isSuspended.value,
    flag = state.flag.value,
    isPinned = state.isPinned.value,
    createdAt = state.createdAt.value,
    updatedAt = System.currentTimeMillis()
)

/**
 * Walks a set's parent chain up to its ultimate root deck, returning the full chain of names —
 * root deck first, the set itself last. A set's `parentDeckId` can point at another set (nested
 * sets), not just a root deck, so this can't just read one level up. `visited` guards against a
 * corrupted/cyclic `parentDeckId` chain looping forever. Falls back to a single-element chain
 * (just the set's own name) if the parent chain is broken (a `parentDeckId` pointing at a deck
 * that no longer exists).
 */
private fun setAncestryChain(set: Deck, allDecks: List<DeckWithCards>): List<String> {
    val chain = mutableListOf(set.name)
    val visited = mutableSetOf(set.id)
    var currentParentId = set.parentDeckId
    while (currentParentId != null && currentParentId !in visited) {
        val parent = allDecks.find { it.deck.id == currentParentId }?.deck ?: break
        visited.add(parent.id)
        chain.add(0, parent.name)
        currentParentId = parent.parentDeckId
    }
    return chain
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

/**
 * One section of the Pinned screen: a [SectionHeader], a horizontally-scrolling [LazyRow] of
 * [itemContent] tiles, and (when there's more than one) a position-indicator dot row below it —
 * the same carousel-with-dots look [net.ericclark.studiare.screens.UI_Components.SessionTileCarousel]
 * uses for Study Hub's session tiles, generalized here over any item type so Decks/Sets/Cards/
 * Sessions/Presets can all share it instead of each reimplementing the indicator math. No-ops
 * (renders nothing, not even the header) when [items] is empty, so callers can call every section
 * unconditionally and let empty ones simply not appear.
 *
 * @param itemWidth Fixed width given to each item's wrapping [Box]. Pass `null` when the item
 * composable already sizes itself (e.g. [net.ericclark.studiare.screens.UI_Components.SetListItem],
 * which sets its own fixed width internally) — forcing a width on top of that would be redundant.
 */
@Composable
private fun <T> PinnedCarouselSection(
    title: String,
    items: List<T>,
    key: (T) -> Any,
    itemWidth: Dp? = null,
    itemContent: @Composable (T) -> Unit
) {
    if (items.isEmpty()) return
    val dimensions = LocalStudiareDimensions.current

    SectionHeader(title)
    val listState = rememberLazyListState()
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium),
        contentPadding = PaddingValues(horizontal = dimensions.paddingSmall)
    ) {
        itemsIndexed(items, key = { _, item -> key(item) }) { _, item ->
            if (itemWidth != null) {
                Box(modifier = Modifier.width(itemWidth)) { itemContent(item) }
            } else {
                itemContent(item)
            }
        }
    }

    if (items.size > 1) {
        val currentIndex by rememberCarouselIndicatorIndex(listState)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            items.indices.forEach { index ->
                val isSelected = index == currentIndex
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
