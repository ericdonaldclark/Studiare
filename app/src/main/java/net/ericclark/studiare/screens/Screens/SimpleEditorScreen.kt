package net.ericclark.studiare.screens.Screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DriveFileMove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import kotlinx.coroutines.launch
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.snapshotFlow
import net.ericclark.studiare.BuildConfig
import net.ericclark.studiare.LocalNavAnimatedVisibilityScope
import net.ericclark.studiare.TooltipIconButton
import kotlinx.coroutines.flow.first
import net.ericclark.studiare.components.CardMetadataContent
import net.ericclark.studiare.components.FlippableCardShell
import net.ericclark.studiare.components.FloatingFilterOverlay
import net.ericclark.studiare.components.TagChip
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.components.jumpThenAnimateTo
import net.ericclark.studiare.data.Card as CardEntity
import net.ericclark.studiare.data.CardDataForSave
import net.ericclark.studiare.data.CardEditorState
import net.ericclark.studiare.data.CardFlag
import net.ericclark.studiare.data.DeckSortMode
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.DifficultySetting
import net.ericclark.studiare.data.NoteField
import net.ericclark.studiare.data.NormalizationType
import net.ericclark.studiare.data.TagDefinition
import net.ericclark.studiare.data.TemplateMode
import net.ericclark.studiare.data.TemplateScope
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.data.toCardDataForSave
import net.ericclark.studiare.data.toEditorState
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import java.util.UUID
import net.ericclark.studiare.screens.Dialogs.AdvancedDeckEditorDialog
import net.ericclark.studiare.screens.Dialogs.CardEditDialog
import net.ericclark.studiare.screens.Dialogs.CardEditDialogMode
import net.ericclark.studiare.screens.UI_Components.DeckInfoDialog
import net.ericclark.studiare.screens.UI_Components.DeckSettingsDialog
import net.ericclark.studiare.screens.UI_Components.FlowRow
import net.ericclark.studiare.screens.UI_Components.SelectableTileOverlay
import net.ericclark.studiare.screens.UI_Components.SelectionAction
import net.ericclark.studiare.screens.UI_Components.SelectionActionBar
import net.ericclark.studiare.screens.UI_Components.SelectionCountTile
import net.ericclark.studiare.screens.UI_Components.rememberSelectionController
import androidx.activity.compose.BackHandler

/**
 * The default destination when editing an existing deck: a condensed grid of read-only card
 * tiles. Tapping a tile opens [CardEditDialog] for a focused single-card edit; adding a card
 * opens the same dialog blank. Every dialog action (Save/Add/Add More/Delete) persists
 * immediately via [FlashcardViewModel.saveSingleCard]/[FlashcardViewModel.deleteSingleCard] —
 * there's no top-level Save here, unlike the full "Bulk Editor" ([DeckEditorScreen]) this
 * screen's Bulk Editor button leads to for anything beyond quick per-card edits.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SimpleEditorScreen(
    navController: NavController,
    deckWithCards: DeckWithCards?,
    viewModel: FlashcardViewModel
) {
    // Confirmed via timing logs: this screen's first composition costs ~500ms (body-start to
    // committed), which is what was swallowing the NavHost's enter transition whole — any
    // transition duration shorter than that never gets a chance to paint an intermediate frame,
    // since by the time the first real frame is drawn, the animation's own clock already reports
    // past its end. Same root cause already diagnosed for Settings' "Customization" category
    // (see its own comment: "the heavy first-time class-load/JIT of that category"), not an
    // algorithmic cost in this screen's own code — grep/read of the grid and its tiles found
    // nothing expensive, and the cost doesn't scale with card count (198 cards here; the grid is
    // properly lazy, only ~6-12 tiles actually compose up front). Same fix as Settings: let the
    // transition play over empty chrome first, defer the heavy content until it's visually
    // settled instead of composing it mid-transition.
    //
    // DEBUG-only: user experimentally disabled this gate entirely and verified on-device, on a
    // release build with a baseline profile covering this screen (generated specifically to
    // exercise a many-card deck open — see baselineprofile module's BaselineProfileGenerator.kt),
    // against the largest real deck in the app (198 cards) — the transition held up reliably
    // without the gate. A release build's AOT-compiled hot paths make the first composition fast
    // enough not to need this safety net; a debug build is always JIT-interpreted regardless of
    // the baseline profile (which only applies to release-type builds at all), so the original
    // swallowed-transition risk this gate exists for is still real there. No evidence yet either
    // way for low-end release-build devices — if that turns out to be a problem, this is the
    // place to broaden the condition, not rebuild the gate from scratch.
    val navAnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    var mainContentReady by remember { mutableStateOf(!BuildConfig.DEBUG || navAnimatedVisibilityScope == null) }
    LaunchedEffect(navAnimatedVisibilityScope) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        val transition = navAnimatedVisibilityScope?.transition ?: return@LaunchedEffect
        snapshotFlow { transition.currentState }.first { it == EnterExitState.Visible }
        mainContentReady = true
    }
    val dimensions = LocalStudiareDimensions.current
    val allTags by viewModel.tags.collectAsState()
    val savedTemplateSets by viewModel.templateSets.collectAsState()
    val alwaysOpenBulkEditor by viewModel.alwaysOpenBulkEditor.collectAsState()
    val allDecksState by viewModel.allDecks.observeAsState(emptyList())
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var editingCard by remember { mutableStateOf<CardEntity?>(null) }
    var showTagDialogForSelection by remember { mutableStateOf<Set<String>?>(null) }
    var showMoveDialogForSelection by remember { mutableStateOf<Set<String>?>(null) }
    var showUseTemplateDialogForSelection by remember { mutableStateOf<Set<String>?>(null) }
    // Set only when the Template Editor was opened via the Use Template dialog's own "Open
    // Template Editor" button — Cancel/Save there should return to that dialog, not Deck Options.
    var pendingUseTemplateIds by remember { mutableStateOf<Set<String>?>(null) }
    var createCardState by remember { mutableStateOf<CardEditorState?>(null) }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var showFilterOverlay by remember { mutableStateOf(false) }
    var showDeckInfo by remember { mutableStateOf(false) }
    var showDeckOptions by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    val lazyGridState = rememberLazyGridState()
    // Restores position only if Bulk Editor's toggle button just handed one off for this deck
    // (see goToBulkEditor's capture above) — any other way of arriving here (Decks list, back
    // navigation, etc.) finds nothing to consume and keeps the grid's normal default position.
    LaunchedEffect(deckWithCards?.deck?.id) {
        val deckId = deckWithCards?.deck?.id ?: return@LaunchedEffect
        val handoffPosition = viewModel.consumeSimpleEditorScrollHandoff(deckId) ?: return@LaunchedEffect
        lazyGridState.scrollToItem(handoffPosition.first, handoffPosition.second)
    }
    val selection = rememberSelectionController()
    BackHandler(enabled = selection.inSelectionMode) { selection.exit() }

    val currentDeckTags = remember(deckWithCards?.cards) {
        deckWithCards?.cards?.flatMap { it.tags }?.toSet() ?: emptySet()
    }

    val sortedCards = remember(deckWithCards?.cards, deckWithCards?.deck?.deckSortMode, deckWithCards?.deck?.sortPinnedCardsFirst) {
        val cards = deckWithCards?.cards ?: emptyList()
        val byMode = when (deckWithCards?.deck?.deckSortMode ?: DeckSortMode.DATE_ADDED_OLD_TO_NEW) {
            DeckSortMode.A_TO_Z -> cards.sortedBy { it.front.lowercase() }
            DeckSortMode.Z_TO_A -> cards.sortedByDescending { it.front.lowercase() }
            DeckSortMode.ONE_TO_FIVE -> cards.sortedWith(compareBy<CardEntity> { it.difficulty.value }.thenBy { it.front.lowercase() })
            DeckSortMode.FIVE_TO_ONE -> cards.sortedWith(compareByDescending<CardEntity> { it.difficulty.value }.thenBy { it.front.lowercase() })
            DeckSortMode.DATE_ADDED_OLD_TO_NEW -> cards.sortedWith(compareBy<CardEntity> { it.createdAt }.thenBy { it.front.lowercase() })
            DeckSortMode.DATE_ADDED_NEW_TO_OLD -> cards.sortedWith(compareByDescending<CardEntity> { it.createdAt }.thenBy { it.front.lowercase() })
            DeckSortMode.DATE_MODIFIED_NEW_TO_OLD -> cards.sortedWith(compareByDescending<CardEntity> { it.updatedAt }.thenBy { it.front.lowercase() })
            DeckSortMode.DATE_MODIFIED_OLD_TO_NEW -> cards.sortedWith(compareBy<CardEntity> { it.updatedAt }.thenBy { it.front.lowercase() })
        }
        // Stable sort: pinned cards float to the top without disturbing byMode's order within each group.
        if (deckWithCards?.deck?.sortPinnedCardsFirst != false) byMode.sortedByDescending { it.isPinned } else byMode
    }

    val displayedCards = remember(sortedCards, filterQuery) {
        val trimmed = filterQuery.trim()
        if (trimmed.isBlank()) {
            sortedCards
        } else {
            sortedCards.filter {
                it.front.contains(trimmed, ignoreCase = true) || it.back.contains(trimmed, ignoreCase = true)
            }
        }
    }

    // A brand-new card is always "new" for TemplateScope's purposes, so both NEW_CARDS and
    // ALL_CARDS scope seed it the same way here — only MANUAL mode skips the auto-seed.
    fun blankEditorState(): CardEditorState {
        val deckTemplateMode = deckWithCards?.deck?.templateMode ?: TemplateMode.MANUAL
        return CardEditorState(
            id = UUID.randomUUID().toString(),
            front = mutableStateOf(""), frontRichTextInfo = mutableStateOf(null), isFrontRichText = mutableStateOf(false),
            back = mutableStateOf(""), backRichTextInfo = mutableStateOf(null), isBackRichText = mutableStateOf(false),
            frontNotes = mutableStateOf(if (deckTemplateMode != TemplateMode.MANUAL) deckWithCards?.deck?.frontNoteTemplates ?: emptyList() else emptyList()),
            backNotes = mutableStateOf(if (deckTemplateMode != TemplateMode.MANUAL) deckWithCards?.deck?.backNoteTemplates ?: emptyList() else emptyList()),
            difficulty = mutableStateOf(DifficultySetting.FIVE), isKnown = mutableStateOf(false),
            reviewedCount = mutableStateOf(0), gradedAttempts = mutableStateOf(emptyList()),
            incorrectAttempts = mutableStateOf(emptyList()), reviewLogs = mutableStateOf(emptyList()),
            absoluteDueDate = mutableStateOf(null), tags = mutableStateOf(emptyList()),
            isSuspended = mutableStateOf(false), flag = mutableStateOf(CardFlag.NONE),
            isPinned = mutableStateOf(false),
            createdAt = mutableLongStateOf(System.currentTimeMillis()), updatedAt = mutableStateOf(System.currentTimeMillis()),
            isDeckTemplateEnforced = mutableStateOf(deckTemplateMode == TemplateMode.ENFORCE)
        )
    }

    fun cardDataFrom(state: CardEditorState): CardDataForSave = CardDataForSave(
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
        updatedAt = System.currentTimeMillis(),
        enforcedTemplateSetId = state.enforcedTemplateSetId.value,
        isDeckTemplateEnforced = state.isDeckTemplateEnforced.value
    )

    fun goToBulkEditor() {
        val id = deckWithCards?.deck?.id ?: return
        // Saved under Simple Editor's own key, not Bulk's — lets *returning* to Simple Editor
        // (via its own toggle button) restore where Simple Editor itself was left.
        viewModel.setSimpleEditorScrollHandoff(id, lazyGridState.firstVisibleItemIndex, lazyGridState.firstVisibleItemScrollOffset)
        // Replace this screen in the back stack instead of pushing on top of it, so Bulk
        // Editor's back button (and save/discard) exits straight past Simple Editor.
        val simpleEditorRoute = navController.currentBackStackEntry?.destination?.route ?: "simpleEditor?deckId={deckId}"
        navController.navigate("deckEditor?deckId=$id") {
            popUpTo(simpleEditorRoute) { inclusive = true }
        }
    }

    fun deleteCardsWithUndo(toDelete: List<CardEntity>) {
        val deck = deckWithCards ?: return
        if (toDelete.isEmpty()) return
        toDelete.forEach { viewModel.deleteSingleCard(deck, it.id) }
        editingCard = null
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val message = if (toDelete.size == 1) getText(context, R.string.card_deleted) else context.getString(R.string.cards_deleted_format, toDelete.size)
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = getText(context, R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                toDelete.forEach { viewModel.saveSingleCard(deck, it.toCardDataForSave()) }
            }
        }
    }

    fun deleteCard(card: CardEntity) = deleteCardsWithUndo(listOf(card))

    fun setPinnedForSelection(ids: Set<String>, pinned: Boolean) {
        val deck = deckWithCards ?: return
        deck.cards.filter { it.id in ids }.forEach { viewModel.saveSingleCard(deck, it.toCardDataForSave().copy(isPinned = pinned)) }
    }

    fun setKnownForSelection(ids: Set<String>, known: Boolean) {
        val deck = deckWithCards ?: return
        deck.cards.filter { it.id in ids }.forEach { viewModel.saveSingleCard(deck, it.toCardDataForSave().copy(isKnown = known)) }
    }

    fun applyTagsForSelection(ids: Set<String>, addTags: Set<String>, removeTags: Set<String>) {
        val deck = deckWithCards ?: return
        deck.cards.filter { it.id in ids }.forEach { card ->
            val newTags = (card.tags.toSet() + addTags - removeTags).toList()
            viewModel.saveSingleCard(deck, card.toCardDataForSave().copy(tags = newTags))
        }
    }

    fun cloneCardsForSelection(ids: Set<String>) {
        val deck = deckWithCards ?: return
        viewModel.cloneCardsForSelection(deck, ids)
    }

    fun moveCardsForSelectionToExisting(ids: Set<String>, toDeckId: String) {
        val deck = deckWithCards ?: return
        val cardsToMove = deck.cards.filter { it.id in ids }.map { it.toCardDataForSave() }
        viewModel.moveCardsToDeck(cardsToMove, fromDeckId = deck.deck.id, toDeckId = toDeckId)
        showMoveDialogForSelection = null
    }

    fun moveCardsForSelectionToNewDeck(ids: Set<String>, newDeckName: String) {
        val deck = deckWithCards ?: return
        val cardsToMove = deck.cards.filter { it.id in ids }.map { it.toCardDataForSave() }
        viewModel.moveCardsToNewDeck(cardsToMove, fromDeckId = deck.deck.id, newDeckName = newDeckName) {}
        showMoveDialogForSelection = null
    }

    fun applyTemplateForSelection(ids: Set<String>, templateSet: net.ericclark.studiare.data.TemplateSet) {
        val deck = deckWithCards ?: return
        viewModel.applyTemplateToCards(deck, ids, templateSet)
        showUseTemplateDialogForSelection = null
    }

    fun enforceTemplateForSelection(ids: Set<String>, templateSet: net.ericclark.studiare.data.TemplateSet) {
        val deck = deckWithCards ?: return
        viewModel.enforceTemplateOnCards(deck, ids, templateSet)
        showUseTemplateDialogForSelection = null
    }

    fun clearTemplateEnforcementForSelection(ids: Set<String>) {
        val deck = deckWithCards ?: return
        viewModel.clearTemplateEnforcement(deck, ids)
        showUseTemplateDialogForSelection = null
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.DECK_EDITOR,
                title = { Text(deckWithCards?.deck?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = ::goToBulkEditor,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.bulk_editor))
                    }
                }
            )
        },
        floatingActionButton = {
            if (selection.inSelectionMode) {
                // Same action set/ordering convention as the Bulk Editor's selection bar (see
                // SelectionController.kt) — Select All always present and rightmost next to
                // Close, "Edit" only once exactly one card is selected.
                val cardActions = remember(selection.selected, displayedCards) {
                    val selectAll = SelectionAction(
                        id = "selectAll",
                        icon = Icons.Default.SelectAll,
                        label = getText(context, R.string.select_all),
                        onClick = { selection.toggleAllWithin(displayedCards.map { it.id }.toSet()) }
                    )
                    if (selection.selected.isEmpty()) {
                        listOf(selectAll)
                    } else {
                        val others = listOf(
                            SelectionAction(
                                id = "unpin",
                                icon = Icons.Outlined.PushPin,
                                label = getText(context, R.string.mark_as_not_pinned),
                                onClick = { setPinnedForSelection(selection.selected, false) }
                            ),
                            SelectionAction(
                                id = "pin",
                                icon = Icons.Default.PushPin,
                                label = getText(context, R.string.mark_as_pinned),
                                onClick = { setPinnedForSelection(selection.selected, true) }
                            ),
                            SelectionAction(
                                id = "unknown",
                                icon = Icons.Default.Close,
                                label = getText(context, R.string.mark_as_not_known),
                                onClick = { setKnownForSelection(selection.selected, false) }
                            ),
                            SelectionAction(
                                id = "known",
                                icon = Icons.Default.Check,
                                label = getText(context, R.string.mark_as_known),
                                onClick = { setKnownForSelection(selection.selected, true) }
                            ),
                            SelectionAction(
                                id = "tag",
                                icon = Icons.Default.Label,
                                label = getText(context, R.string.tag),
                                onClick = { showTagDialogForSelection = selection.selected }
                            ),
                            SelectionAction(
                                id = "clone",
                                icon = Icons.Default.ContentCopy,
                                label = getText(context, R.string.clone),
                                onClick = {
                                    val ids = selection.selected
                                    selection.exit()
                                    cloneCardsForSelection(ids)
                                }
                            ),
                            SelectionAction(
                                id = "move",
                                icon = Icons.Default.DriveFileMove,
                                label = getText(context, R.string.move),
                                onClick = {
                                    val ids = selection.selected
                                    selection.exit()
                                    showMoveDialogForSelection = ids
                                }
                            ),
                            SelectionAction(
                                id = "useTemplate",
                                icon = Icons.Default.Description,
                                label = getText(context, R.string.use_template),
                                onClick = {
                                    val ids = selection.selected
                                    selection.exit()
                                    showUseTemplateDialogForSelection = ids
                                }
                            ),
                            SelectionAction(
                                id = "edit",
                                icon = Icons.Default.Edit,
                                label = getText(context, R.string.edit),
                                onClick = {
                                    val targetId = selection.selected.firstOrNull()
                                    editingCard = displayedCards.firstOrNull { it.id == targetId }
                                    selection.exit()
                                }
                            ),
                            SelectionAction(
                                id = "delete",
                                icon = Icons.Default.Delete,
                                label = getText(context, R.string.delete),
                                isDestructive = true,
                                onClick = {
                                    val toDelete = displayedCards.filter { it.id in selection.selected }
                                    selection.exit()
                                    deleteCardsWithUndo(toDelete)
                                }
                            )
                        )
                        others.filter { it.id != "edit" || selection.selected.size == 1 } + selectAll
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimensions.spacingSmall)
                ) {
                    SelectionActionBar(
                        actions = cardActions,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        FloatingActionButton(
                            onClick = { selection.exit() },
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        ) {
                            Icon(Icons.Default.Close, contentDescription = getText(R.string.cancel))
                        }
                        SelectionCountTile(
                            selectedCount = selection.selected.size,
                            totalCount = deckWithCards?.cards?.size ?: 0,
                            modifier = Modifier.align(Alignment.TopCenter).offset(y = (-32).dp)
                        )
                    }
                }
            } else {
                HorizontalFloatingToolbar(
                    expanded = true,
                    // "Vibrant" toolbar colors tint the bar with primaryContainer instead of the
                    // standard variant's surfaceContainer — matches the Bulk Editor's toolbar.
                    colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                    floatingActionButton = {
                        FloatingActionButton(onClick = { createCardState = blankEditorState() }) {
                            Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add))
                        }
                    }
                ) {
                    TooltipIconButton(
                        description = getText(R.string.scroll_to_bottom),
                        onClick = { coroutineScope.launch { lazyGridState.jumpThenAnimateTo((displayedCards.size - 1).coerceAtLeast(0), scrollOffset = Int.MAX_VALUE) } }
                    ) {
                        Icon(Icons.Default.ArrowDownward, contentDescription = getText(R.string.scroll_to_bottom))
                    }
                    TooltipIconButton(
                        description = getText(R.string.scroll_to_top),
                        onClick = { coroutineScope.launch { lazyGridState.jumpThenAnimateTo(0) } }
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = getText(R.string.scroll_to_top))
                    }
                    TooltipIconButton(description = getText(R.string.cards_filter_), onClick = { showFilterOverlay = !showFilterOverlay }) {
                        Icon(Icons.Default.Search, contentDescription = getText(R.string.cards_filter_))
                    }
                    TooltipIconButton(description = getText(R.string.deck_statistics), onClick = { showDeckInfo = true }) {
                        Icon(Icons.Default.Info, contentDescription = getText(R.string.deck_statistics))
                    }
                    TooltipIconButton(description = getText(R.string.deck_settings), onClick = { showDeckOptions = true }) {
                        Icon(Icons.Default.Settings, contentDescription = getText(R.string.deck_settings))
                    }
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (filterQuery.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(start = dimensions.paddingMedium, end = dimensions.paddingMedium, top = dimensions.paddingMedium)
                ) {
                    AssistChip(
                        onClick = { showFilterOverlay = true },
                        label = { Text(stringResource(R.string.filter_applied_format, filterQuery.trim())) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = getText(R.string.remove),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { filterQuery = "" }
                            )
                        }
                    )
                }
            }
            // Gated on mainContentReady (see the comment above) so the grid's genuinely expensive
            // first composition happens after, not during, the NavHost transition into this
            // screen — a plain fade covers its otherwise-abrupt appearance. DEBUG-only; see
            // mainContentReady's own declaration for why.
            AnimatedVisibility(
                visible = mainContentReady,
                enter = fadeIn(),
                exit = ExitTransition.None,
                modifier = Modifier.fillMaxSize()
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = dimensions.paddingMedium,
                        end = dimensions.paddingMedium,
                        top = dimensions.paddingMedium,
                        bottom = 120.dp
                    ),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimensions.spacingSmall),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimensions.spacingSmall)
                ) {
                    itemsIndexed(displayedCards, key = { _, card -> card.id }) { index, card ->
                        CardTile(
                            card = card,
                            cardNumber = index + 1,
                            totalCards = displayedCards.size,
                            allTags = allTags,
                            toEditorState = { it.toEditorState() },
                            onClick = { if (selection.inSelectionMode) selection.toggle(card.id) else editingCard = card },
                            selected = selection.isSelected(card.id),
                            onLongClick = { selection.enterWith(card.id) }
                        )
                    }
                }
                // Same grabbable fast-scroll slider the Bulk Editor's card list uses — stops
                // short of the bottom (matching the grid's own 120dp bottom content padding) so
                // it doesn't sit behind the selection action bar/Close FAB that floats over this
                // screen while selecting.
                if (displayedCards.size > 3) {
                    net.ericclark.studiare.screens.UI_Components.CustomVerticalScrollbar(
                        gridState = lazyGridState,
                        bottomClearance = 120.dp,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .width(30.dp)
                            .fillMaxHeight()
                            .padding(top = dimensions.paddingMedium)
                    )
                }
                }
            }
        }
    }

    showTagDialogForSelection?.let { ids ->
        val deck = deckWithCards
        if (deck != null) {
            net.ericclark.studiare.components.MultiCardTagSelectionDialog(
                selectedCardsTags = deck.cards.filter { it.id in ids }.map { it.tags.toSet() },
                allTags = allTags,
                currentDeckTags = currentDeckTags,
                onDismiss = { showTagDialogForSelection = null },
                onSave = { addTags, removeTags ->
                    applyTagsForSelection(ids, addTags, removeTags)
                    showTagDialogForSelection = null
                },
                onCreateTag = { name, color -> viewModel.saveTagDefinition(TagDefinition(name = name, color = color)) }
            )
        }
    }

    showMoveDialogForSelection?.let { ids ->
        net.ericclark.studiare.screens.Dialogs.MoveCardsDialog(
            decks = allDecksState,
            onDismiss = { showMoveDialogForSelection = null },
            onMoveToExisting = { toDeckId -> moveCardsForSelectionToExisting(ids, toDeckId) },
            onMoveToNewDeck = { name -> moveCardsForSelectionToNewDeck(ids, name) }
        )
    }

    showUseTemplateDialogForSelection?.let { ids ->
        net.ericclark.studiare.screens.Dialogs.UseTemplateDialog(
            templateSets = savedTemplateSets,
            onDismiss = { showUseTemplateDialogForSelection = null },
            onApply = { templateSet -> applyTemplateForSelection(ids, templateSet) },
            onEnforce = { templateSet -> enforceTemplateForSelection(ids, templateSet) },
            onOpenTemplateEditor = {
                pendingUseTemplateIds = ids
                showUseTemplateDialogForSelection = null
                showTemplateEditor = true
            },
            onRemoveEnforcement = { clearTemplateEnforcementForSelection(ids) }
        )
    }

    editingCard?.let { card ->
        val editState = remember(card.id) { card.toEditorState() }
        val totalCards = deckWithCards?.cards?.size ?: 1
        val cardNumber = ((deckWithCards?.cards?.indexOfFirst { it.id == card.id } ?: 0) + 1).coerceAtLeast(1)
        val lockedFields = net.ericclark.studiare.data.effectiveLockedFields(
            enforcedTemplateSetId = card.enforcedTemplateSetId,
            isDeckTemplateEnforced = card.isDeckTemplateEnforced,
            templateSets = savedTemplateSets,
            deck = deckWithCards?.deck
        )
        CardEditDialog(
            cardState = editState,
            cardNumber = cardNumber,
            totalCards = totalCards,
            allTags = allTags,
            currentDeckTags = currentDeckTags,
            onUpdateTags = { newTags -> editState.tags.value = newTags.toList() },
            onCreateTag = { name, color -> viewModel.saveTagDefinition(TagDefinition(name = name, color = color)) },
            mode = CardEditDialogMode.Edit(
                onSave = {
                    deckWithCards?.let { viewModel.saveSingleCard(it, cardDataFrom(editState)) }
                    editingCard = null
                },
                onDelete = { deleteCard(card) },
                onClone = {
                    cloneCardsForSelection(setOf(card.id))
                    editingCard = null
                },
                onMove = {
                    showMoveDialogForSelection = setOf(card.id)
                    editingCard = null
                },
                onUseTemplate = {
                    showUseTemplateDialogForSelection = setOf(card.id)
                    editingCard = null
                }
            ),
            onCancel = { editingCard = null },
            normalizationType = deckWithCards?.deck?.normalizationType ?: NormalizationType.NONE,
            lockedFields = lockedFields
        )
    }

    createCardState?.let { state ->
        val totalCards = (deckWithCards?.cards?.size ?: 0) + 1
        CardEditDialog(
            cardState = state,
            cardNumber = totalCards,
            totalCards = totalCards,
            allTags = allTags,
            currentDeckTags = currentDeckTags,
            onUpdateTags = { newTags -> state.tags.value = newTags.toList() },
            onCreateTag = { name, color -> viewModel.saveTagDefinition(TagDefinition(name = name, color = color)) },
            mode = CardEditDialogMode.Create(
                onAdd = {
                    deckWithCards?.let { viewModel.saveSingleCard(it, cardDataFrom(state)) }
                    createCardState = null
                },
                onAddMore = {
                    deckWithCards?.let { viewModel.saveSingleCard(it, cardDataFrom(state)) }
                    createCardState = blankEditorState()
                }
            ),
            onCancel = { createCardState = null },
            normalizationType = deckWithCards?.deck?.normalizationType ?: NormalizationType.NONE
        )
    }

    if (showTemplateEditor && deckWithCards != null) {
        AdvancedDeckEditorDialog(
            frontTemplates = deckWithCards.deck.frontNoteTemplates,
            backTemplates = deckWithCards.deck.backNoteTemplates,
            onDismiss = {
                // Template Editor is reached either from Deck Options' "Open Template Editor"
                // button, or from the Use Template dialog's own button of the same name — so
                // cancelling here (via the Cancel button, back, or tap-outside) needs to reopen
                // whichever of those it came from, or the user lands back on the editor screen
                // with both dialogs gone.
                showTemplateEditor = false
                val returnIds = pendingUseTemplateIds
                if (returnIds != null) {
                    pendingUseTemplateIds = null
                    showUseTemplateDialogForSelection = returnIds
                } else {
                    showDeckOptions = true
                }
            },
            onSave = { newFront, newBack, templateMode, templateScope, templateName ->
                viewModel.saveNoteTemplates(deckWithCards, templateName, newFront, newBack, templateMode, templateScope)
                showTemplateEditor = false
                val returnIds = pendingUseTemplateIds
                if (returnIds != null) {
                    pendingUseTemplateIds = null
                    showUseTemplateDialogForSelection = returnIds
                } else {
                    showDeckOptions = true
                }
            },
            initialTemplateMode = deckWithCards.deck.templateMode,
            initialTemplateScope = deckWithCards.deck.templateScope,
            initialTemplateName = savedTemplateSets.find { it.id == deckWithCards.deck.activeTemplateSetId }?.name ?: "",
            savedTemplateSets = savedTemplateSets,
            onSaveAsTemplate = { name, front, back -> viewModel.saveAsTemplateSet(name, front, back) },
            onDeleteTemplateSet = { templateSet -> viewModel.deleteTemplateSet(templateSet) }
        )
    }

    FloatingFilterOverlay(
        visible = showFilterOverlay,
        filterText = filterQuery,
        onFilterTextChange = { filterQuery = it },
        onDismiss = { showFilterOverlay = false }
    )

    if (showDeckInfo && deckWithCards != null) {
        DeckInfoDialog(deckWithCards = deckWithCards, onDismiss = { showDeckInfo = false })
    }

    if (showDeckOptions && deckWithCards != null) {
        DeckSettingsDialog(
            initialDeckName = deckWithCards.deck.name,
            initialNormalizationType = deckWithCards.deck.normalizationType,
            initialDeckSort = deckWithCards.deck.deckSortMode,
            initialSortPinnedCardsFirst = deckWithCards.deck.sortPinnedCardsFirst,
            initialDefaultPromptSide = deckWithCards.deck.defaultPromptSide,
            initialFrontLanguage = deckWithCards.deck.frontLanguage,
            initialBackLanguage = deckWithCards.deck.backLanguage,
            onDismiss = { showDeckOptions = false },
            onSave = { newName, newNorm, newSort, newPinnedFirst, newPromptSide, newFrontLang, newBackLang ->
                viewModel.updateDeckOptions(deckWithCards, newName, newNorm, newSort, newPinnedFirst, newPromptSide, newFrontLang, newBackLang)
                showDeckOptions = false
            },
            onOpenTemplateEditor = { showTemplateEditor = true },
            onClearReviewData = {
                viewModel.clearDeckReviewData(deckWithCards.deck.id)
                showDeckOptions = false
            },
            alwaysOpenBulkEditor = alwaysOpenBulkEditor,
            onSetAlwaysOpenBulkEditor = { viewModel.setAlwaysOpenBulkEditor(it) }
        )
    }
}

/** A note's content as shown in [CardTile]'s preview text. Media notes (image/video/audio) hold a
 * full internal-storage file path there — truncated to the last 3 segments via
 * [MediaStorageUtils.truncatedDisplayPath]. Checked by content, not just [NoteField.type]: a note
 * can end up holding an internal media path while still tagged [MediaType.PLAIN_TEXT] (e.g.
 * imported/legacy data whose type never got corrected), and that path is just as unreadable
 * untruncated regardless of what the field is nominally tagged. */
private fun noteDisplayContent(note: NoteField, internalFilesDir: String): String =
    // Just the filename here, not DeckEditorCards.kt's 3-segment truncatedDisplayPath — this tile
    // preview has far less room than the note-field editor (Bulk Editor, and the dialog this
    // screen opens when a tile is tapped, both still show the fuller 3-segment path).
    if (note.content.startsWith(internalFilesDir)) note.content.substringAfterLast('/')
    else note.content

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
/**
 * Reused as-is by [net.ericclark.studiare.screens.Screens.PinnedScreen]'s Cards section — that
 * screen doesn't wire multi-select up yet, so it leaves [selected]/[onLongClick] at their defaults
 * and behaves exactly as before.
 */
@Composable
internal fun CardTile(
    card: CardEntity,
    cardNumber: Int,
    totalCards: Int,
    allTags: List<TagDefinition>,
    toEditorState: (CardEntity) -> CardEditorState,
    onClick: () -> Unit,
    selected: Boolean = false,
    onLongClick: () -> Unit = {}
) {
    val dimensions = LocalStudiareDimensions.current
    var showInfo by remember { mutableStateOf(false) }
    SelectableTileOverlay(selected = selected, modifier = Modifier.fillMaxWidth()) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        FlippableCardShell(
            cardNumber = cardNumber,
            totalCards = totalCards,
            showInfo = showInfo,
            onToggleInfo = { showInfo = !showInfo },
            modifier = Modifier.padding(
                start = dimensions.paddingMedium,
                end = dimensions.paddingMedium,
                bottom = dimensions.paddingMedium,
                top = dimensions.paddingSmall
            ),
            contentTopSpacing = 0.dp,
            frontTopBarExtra = {
                if (card.isPinned) {
                    Icon(
                        imageVector = Icons.Filled.PushPin,
                        contentDescription = getText(R.string.pinned_label),
                        modifier = Modifier.size(18.dp).padding(end = 4.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            infoContent = { CardMetadataContent(remember(card.id) { toEditorState(card) }) },
            frontContent = {
                @Composable
                fun DifficultyBadge() {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = getText(R.string.difficulty),
                        modifier = Modifier.size(18.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                    Text(
                        card.difficulty.value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (card.isKnown) {
                        androidx.compose.foundation.layout.Spacer(Modifier.width(dimensions.spacingSmall))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = getText(R.string.mark_as_not_known),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val internalFilesDir = LocalContext.current.filesDir.absolutePath
                    val paragraph = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.Bold)) {
                            append(card.front)
                        }
                        card.frontNotes.filter { it.content.isNotBlank() }.forEach { note ->
                            withStyle(SpanStyle(color = dimColor, fontStyle = FontStyle.Italic)) {
                                append(" ${note.name}")
                            }
                            withStyle(SpanStyle(color = dimColor)) {
                                append(": ${noteDisplayContent(note, internalFilesDir)}")
                            }
                        }
                        withStyle(SpanStyle(color = dimColor)) {
                            append(" → ")
                        }
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.Bold)) {
                            append(card.back)
                        }
                        card.backNotes.filter { it.content.isNotBlank() }.forEach { note ->
                            withStyle(SpanStyle(color = dimColor, fontStyle = FontStyle.Italic)) {
                                append(" ${note.name}")
                            }
                            withStyle(SpanStyle(color = dimColor)) {
                                append(": ${noteDisplayContent(note, internalFilesDir)}")
                            }
                        }
                    }

                    if (card.tags.isEmpty()) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                paragraph,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.width(dimensions.spacingSmall))
                            Row(verticalAlignment = Alignment.CenterVertically) { DifficultyBadge() }
                        }
                    } else {
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = dimensions.spacingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.layout.FlowRow(modifier = Modifier.weight(1f)) {
                                card.tags.forEach { tagName ->
                                    val def = allTags.find { it.name == tagName }
                                    TagChip(text = tagName, colorHex = def?.color ?: "#9E9E9E")
                                }
                            }
                            androidx.compose.foundation.layout.Spacer(Modifier.width(dimensions.spacingSmall))
                            Row(verticalAlignment = Alignment.CenterVertically) { DifficultyBadge() }
                        }
                    }
                }
            }
        )
    }
    }
}

