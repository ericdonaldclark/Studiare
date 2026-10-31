package net.ericclark.studiare.screens.Screens
import net.ericclark.studiare.*
import net.ericclark.studiare.R

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.screens.Dialogs.CardEditDialog
import net.ericclark.studiare.screens.Dialogs.CardEditDialogMode
import net.ericclark.studiare.screens.Dialogs.studyRouteFor
import net.ericclark.studiare.screens.UI_Components.DeckListItem
import net.ericclark.studiare.screens.UI_Components.PinnedSortDialog
import net.ericclark.studiare.screens.UI_Components.SessionTile
import net.ericclark.studiare.screens.UI_Components.rememberDeckSessionActions
import net.ericclark.studiare.screens.UI_Components.sortedPinnedDecks
import net.ericclark.studiare.screens.UI_Components.sortedTiles
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * The bottom dock's "Pinned" screen — a cross-cutting view of everything the user has pinned
 * across the app: decks/sets, individual cards, and saved study sessions. Three fixed sections
 * (not an open/groupable set, unlike the Study Hub), each with its own sort control via
 * [PinnedSortDialog]. Reuses the same tiles their native screens use — [DeckListItem] (Decks/Sets
 * screens) and [CardTile] (Simple Editor) — rather than bespoke rows, so behavior (study, edit,
 * delete, resume, etc.) stays identical everywhere a deck/set/card appears. Unpinning from this
 * screen removes the item from view, so every unpin here gets an undo snackbar.
 */
@Composable
fun PinnedScreen(navController: NavController, viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val allDecks by viewModel.allDecks.observeAsState(emptyList())
    val pinnedCards by viewModel.pinnedCards.collectAsState()
    val activeSessions by viewModel.allActiveSessions.collectAsState()
    val allTagDefinitions by viewModel.tags.collectAsState()

    val deckSortMode by viewModel.pinnedScreenDeckSortMode.collectAsState()
    val cardSortMode by viewModel.pinnedScreenCardSortMode.collectAsState()
    val sessionSortMode by viewModel.pinnedScreenSessionSortMode.collectAsState()
    val sessionSortDirection by viewModel.pinnedScreenSessionSortDirection.collectAsState()

    val pinnedDeckSummaries = remember(allDecks, deckSortMode) {
        val sorted = sortedPinnedDecks(allDecks.map { it.deck }.filter { it.isPinned }, deckSortMode)
        sorted.map { deck ->
            DeckSummary(
                deck = deck,
                totalCards = allDecks.find { it.deck.id == deck.id }?.cards?.size ?: 0
            )
        }
    }
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
    var showUnpinAllDialog by remember { mutableStateOf(false) }
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

    val hasAnyPinnedContent = pinnedDeckSummaries.isNotEmpty() || sortedPinnedCards.isNotEmpty() || pinnedSessions.isNotEmpty()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                title = { Text(getText(R.string.pinned), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold) },
                actions = {
                    var expanded by remember { mutableStateOf(false) }
                    Box {
                        TooltipIconButton(description = getText(R.string.options_more), onClick = { expanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.options_more))
                        }
                        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                            DropdownMenuItem(
                                text = { Text(getText(R.string.sort)) },
                                leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                                onClick = { expanded = false; showSortDialog = true }
                            )
                            if (hasAnyPinnedContent) {
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.unpin_all)) },
                                    leadingIcon = { Icon(Icons.Outlined.PushPin, contentDescription = null) },
                                    onClick = { expanded = false; showUnpinAllDialog = true }
                                )
                            }
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
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
                if (pinnedDeckSummaries.isNotEmpty()) {
                    item { SectionHeader(getText(R.string.pinned_decks_and_sets)) }
                    items(pinnedDeckSummaries, key = { "deck-${it.deck.id}" }) { summary ->
                        SwipeToUnpinItem(onUnpin = { unpinDeckWithUndo(summary.deck) }) {
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
                                onCreateSession = { sessionActions.startNewSession(summary.deck.id) }
                            )
                        }
                    }
                }
                if (sortedPinnedCards.isNotEmpty()) {
                    item { SectionHeader(getText(R.string.pinned_cards)) }
                    items(sortedPinnedCards, key = { "card-${it.id}" }) { card ->
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
                            SwipeToUnpinItem(onUnpin = { unpinCardWithUndo(card) }) {
                                CardTile(
                                    card = card,
                                    cardNumber = 1,
                                    totalCards = 1,
                                    allTags = allTagDefinitions,
                                    toEditorState = { it.toEditorState() },
                                    onClick = { editingCard = card }
                                )
                            }
                        }
                    }
                }
                if (pinnedSessions.isNotEmpty()) {
                    item { SectionHeader(getText(R.string.pinned_saved_sessions)) }
                    items(pinnedSessions, key = { "session-${it.id}" }) { session ->
                        val deck = allDecks.find { it.deck.id == session.deckId }
                        val card = deck?.cards?.find {
                            it.id == (if (session.mode == SessionMode.MATCHING && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(session.currentCardIndex))
                        }
                        SwipeToUnpinItem(onUnpin = { unpinSessionWithUndo(session) }) {
                            SessionTile(
                                session = session,
                                card = card,
                                deckName = deck?.deck?.name ?: "",
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
                                showModeName = true,
                                showCategoryNameIcon = true
                            )
                        }
                    }
                }
                item {
                    Text(
                        text = getText(R.string.swipe_to_unpin_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = dimensions.spacingSmall)
                    )
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
            onSave = { newDeckSort, newCardSort, newSessionSort, newSessionDirection ->
                viewModel.setPinnedScreenDeckSortMode(newDeckSort)
                viewModel.setPinnedScreenCardSortMode(newCardSort)
                viewModel.setPinnedScreenSessionSortMode(newSessionSort)
                viewModel.setPinnedScreenSessionSortDirection(newSessionDirection)
                showSortDialog = false
            },
            onDismiss = { showSortDialog = false }
        )
    }

    if (showUnpinAllDialog) {
        ConfirmationDialog(
            title = getText(R.string.unpin_all_title),
            text = getText(R.string.unpin_all_desc),
            onConfirm = {
                pinnedDeckSummaries.forEach { viewModel.setDeckPinned(it.deck, false) }
                sortedPinnedCards.forEach { viewModel.setCardPinned(it, false) }
                pinnedSessions.forEach { viewModel.unpinSession(it) }
                showUnpinAllDialog = false
            },
            onDismiss = { showUnpinAllDialog = false }
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
 * Wraps a pinned-item row (deck/set tile, card tile, session tile) in a [SwipeToDismissBox] so
 * swiping it either direction unpins it — the same [onUnpin] already backing each section's other
 * unpin entry points (a tile's own built-in pin toggle, a session's overflow menu), so swiping
 * gets the exact same undo-snackbar behavior as those, not a separate code path. Always confirms
 * the swipe (`true`) rather than snapping back, since [onUnpin] itself handles removing the item
 * from the underlying pinned list — once that happens this composable's call site simply stops
 * being composed, same as any other item leaving a list backed by live data.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeToUnpinItem(onUnpin: () -> Unit, content: @Composable () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value != SwipeToDismissBoxValue.Settled) onUnpin()
            true
        }
    )
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val alignment = if (dismissState.dismissDirection == SwipeToDismissBoxValue.StartToEnd) Alignment.CenterStart else Alignment.CenterEnd
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer, RoundedCornerShape(dimensions.cornerRadiusMedium))
                    .padding(horizontal = dimensions.paddingLarge),
                contentAlignment = alignment
            ) {
                Icon(
                    imageVector = Icons.Outlined.PushPin,
                    contentDescription = getText(R.string.unpin),
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        }
    ) {
        content()
    }
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
