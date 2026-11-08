package net.ericclark.studiare.screens.Screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.draw.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.runtime.derivedStateOf
import androidx.activity.compose.BackHandler
import net.ericclark.studiare.screens.Dialogs.DeckSortDialog
import net.ericclark.studiare.screens.Dialogs.ManualSetEditorDialog
import net.ericclark.studiare.screens.Dialogs.SetCreationAction
import net.ericclark.studiare.screens.Dialogs.SetCreationDialogHost
import net.ericclark.studiare.screens.UI_Components.DeckListItem
import net.ericclark.studiare.screens.UI_Components.SubSetListItem
import net.ericclark.studiare.screens.UI_Components.rememberDeckSessionActions

@Composable
fun SetManagerScreen(
    navController: NavController,
    parentDeck: DeckWithCards,
    sets: List<DeckSummary>,
    viewModel: FlashcardViewModel,
    isPane: Boolean = false,
    onChromeChanged: (PaneChrome) -> Unit = {}
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val context = LocalContext.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<DeckSummary?>(null) }
    var setToEdit by remember { mutableStateOf<DeckSummary?>(null) }
    var creationAction by remember { mutableStateOf<SetCreationAction?>(null) }
    var showSortDialog by remember { mutableStateOf(false) }
    var showBulkDeleteDialog by remember { mutableStateOf<List<DeckSummary>?>(null) }
    var showAdvancedSelectionDialog by remember { mutableStateOf(false) }
    val sessionActions = rememberDeckSessionActions(viewModel, navController)
    val selection = net.ericclark.studiare.screens.UI_Components.rememberSelectionController()
    BackHandler(enabled = selection.inSelectionMode) { selection.exit() }

    // Same global sort settings the Decks screen's own sort button/dialog use - already applied to
    // this same deck's sets when they're shown nested under it there (groupedAndSortedDecks), so
    // reusing them here keeps the two screens' set ordering consistent instead of this screen having
    // its own separate, natural-order-only sort.
    val deckSortMode by viewModel.deckSortMode.collectAsState()
    val showPinnedFirst by viewModel.showPinnedDecksFirst.collectAsState()

    val allDecksWithCards by viewModel.allDecks.observeAsState(emptyList())

    val allTags by viewModel.tags.collectAsState()
    val parentDeckTags = remember(parentDeck) {
        parentDeck.cards.flatMap { it.tags }.distinct().sorted()
    }

    val spacingMode by viewModel.spacingMode.collectAsState()
    val animationMode by viewModel.animationMode.collectAsState()
    val storedDeckSetsDisplayMode by viewModel.deckSetsDisplayMode.collectAsState()
    val deckSetsDisplayMode = DeckSetsDisplayMode.resolve(storedDeckSetsDisplayMode, windowWidthSizeClass, windowHeightSizeClass)
    val displaySetsUnderDecks = deckSetsDisplayMode != DeckSetsDisplayMode.OFF

    // Determine Dimensions based on ViewModel state
    val dimensions = when (spacingMode) {
        SpacingMode.COMPACT -> CompactDimensions
        SpacingMode.NORMAL -> NormalDimensions
        else -> ComfortableDimensions
    }

    // Provide these dimensions to all child composables
    CompositionLocalProvider(LocalStudiareDimensions provides dimensions) {

        SetCreationDialogHost(
            parentDeck = parentDeck,
            action = creationAction,
            viewModel = viewModel,
            onDismiss = { creationAction = null }
        )

        setToEdit?.let { aSetSummary ->
            val heavySet = allDecksWithCards.find { it.deck.id == aSetSummary.deck.id }
            if (heavySet != null) {
                ManualSetEditorDialog(
                    navController = navController,
                    parentDeck = parentDeck,
                    setForEditing = heavySet,
                    viewModel = viewModel,
                    onDismiss = { setToEdit = null }
                )
            }
        }

        showDeleteDialog?.let { deckToDelete ->
            ConfirmationDialog(
                title = getText(R.string.delete_set_question),
                text = stringResource(R.string.delete_set_confirm, deckToDelete.deck.name),
                onConfirm = {
                    viewModel.deleteDeck(deckToDelete.deck.id)
                    showDeleteDialog = null
                },
                onDismiss = { showDeleteDialog = null }
            )
        }

        showBulkDeleteDialog?.let { toDelete ->
            ConfirmationDialog(
                title = getText(R.string.delete_set_question),
                text = stringResource(R.string.delete_decks_confirm_format, toDelete.size),
                onConfirm = {
                    toDelete.forEach { viewModel.deleteDeck(it.deck.id) }
                    showBulkDeleteDialog = null
                    selection.exit()
                },
                onDismiss = { showBulkDeleteDialog = null }
            )
        }

        if (showSortDialog) {
            DeckSortDialog(
                currentSortMode = deckSortMode,
                showPinnedFirst = showPinnedFirst,
                onDismiss = { showSortDialog = false },
                onSortModeSelected = { viewModel.setDeckSortMode(it) },
                onShowPinnedFirstChange = { viewModel.setShowPinnedDecksFirst(it) }
            )
        }

        val parentId = parentDeck.deck.parentDeckId
        val navigateUp = {
            if (isPane) {
                viewModel.closePane("set:${parentDeck.deck.id}")
            } else {
                if (parentId == null) {
                    navController.navigate("deckList") { popUpTo(0) }
                } else {
                    navController.navigate("setManager/$parentId") {
                        popUpTo("setManager/$parentId") { inclusive = true }
                    }
                }
            }
        }

        BackHandler(enabled = !isPane, onBack = navigateUp)

        val screenTitle = stringResource(R.string.deck_sets_title_format, parentDeck.deck.name)

        if (isPane) {
            // Report chrome to the owning Scaffold instead of drawing our own.
            LaunchedEffect(screenTitle) {
                onChromeChanged(
                    PaneChrome(
                        title = { Text(screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        actions = {
                            TooltipIconButton(description = getText(R.string.sort_decks), onClick = { showSortDialog = true }) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = getText(R.string.sort_decks))
                            }
                        },
                        screenId = ShortcutScreen.SETS
                    )
                )
            }
        }

        // ── everything below is the SAME body as before, just assigned to a
        // named lambda so it can be reused from either the pane branch or the
        // standalone Scaffold branch below. ──────────────────────────────────
        val paneContent: @Composable (PaddingValues) -> Unit = { padding ->
            val sortedSets = remember(sets, deckSortMode, showPinnedFirst) {
                sets.sortedWith(viewModel.getDeckComparator(deckSortMode, showPinnedFirst))
            }
            // Every id currently on screen — the top-level sets plus (when shown) each one's own
            // child sets — used for Select All's scope. Resolving a selected id back to a Deck for
            // bulk pin/delete just uses allDecksWithCards directly (it already holds every deck/set
            // in the app), so no separate lookup structure is needed for that.
            val allVisibleSetIds = remember(sortedSets, allDecksWithCards, displaySetsUnderDecks) {
                val topIds = sortedSets.map { it.deck.id }
                val subIds = if (displaySetsUnderDecks) {
                    sortedSets.flatMap { set -> allDecksWithCards.filter { it.deck.parentDeckId == set.deck.id }.map { it.deck.id } }
                } else emptyList()
                (topIds + subIds).toSet()
            }

            // This screen's own set subtree (top-level sets under parentDeck plus every one of
            // their descendants, to arbitrary depth) — the scope for the Advanced Selection
            // dialog, which deliberately shows nested children regardless of displaySetsUnderDecks
            // since its whole point is picking across levels in one pass.
            val advancedSelectionDecks = remember(sortedSets, allDecksWithCards) {
                val byId = allDecksWithCards.associateBy { it.deck.id }
                val result = mutableListOf<net.ericclark.studiare.data.DeckWithCards>()
                val queue = ArrayDeque(sortedSets.mapNotNull { byId[it.deck.id] })
                val seen = mutableSetOf<String>()
                while (queue.isNotEmpty()) {
                    val node = queue.removeFirst()
                    if (!seen.add(node.deck.id)) continue
                    result.add(node)
                    queue.addAll(allDecksWithCards.filter { it.deck.parentDeckId == node.deck.id })
                }
                result
            }

            val focusRequester = remember { FocusRequester() }
            var fabMenuExpanded by remember { mutableStateOf(false) }
            val openCreateMenuKey = resolveShortcutKey(LocalShortcutRemaps.current, "sets.open_create_menu", Key.N)

            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .autoFocusable(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp) {
                            when {
                                event.key == Key.Backspace -> {
                                    navigateUp()
                                    return@onPreviewKeyEvent true
                                }

                                event.key == openCreateMenuKey -> {
                                    fabMenuExpanded = !fabMenuExpanded
                                    return@onPreviewKeyEvent true
                                }

                                (event.isCtrlPressed && event.key == Key.F) || event.key == Key.Slash -> {
                                    // Focus search bar when implemented in the future
                                    return@onPreviewKeyEvent true
                                }

                                event.isAltPressed -> {
                                    val num = when (event.key) {
                                        Key.One, Key.NumPad1 -> 0
                                        Key.Two, Key.NumPad2 -> 1
                                        Key.Three, Key.NumPad3 -> 2
                                        Key.Four, Key.NumPad4 -> 3
                                        Key.Five, Key.NumPad5 -> 4
                                        Key.Six, Key.NumPad6 -> 5
                                        Key.Seven, Key.NumPad7 -> 6
                                        Key.Eight, Key.NumPad8 -> 7
                                        Key.Nine, Key.NumPad9 -> 8
                                        else -> -1
                                    }
                                    if (num in sortedSets.indices) {
                                        val deckId = sortedSets[num].deck.id
                                        navController.navigate("studyModeSelection/$deckId")
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            }
                        }
                        false
                    }
            ) {

                AnimatedContent(
                    targetState = sortedSets.isEmpty(),
                    transitionSpec = {
                        (fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) +
                                slideInVertically(
                                    animationSpec = androidx.compose.animation.core.tween(
                                        400
                                    ), initialOffsetY = { it / 4 }))
                            .togetherWith(
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(400)) +
                                        slideOutVertically(
                                            animationSpec = androidx.compose.animation.core.tween(
                                                400
                                            ), targetOffsetY = { it / 4 })
                            )
                    },
                    label = "setsListTransition"
                ) { isEmpty ->
                    if (isEmpty) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = getText(R.string.no_sets_yet),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(32.dp))

                                FilledTonalButton(
                                    onClick = { creationAction = SetCreationAction.CLONE },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.align(Alignment.CenterStart)
                                        )
                                        Text(
                                            text = "Clone Entire Deck",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                FilledTonalButton(
                                    onClick = { creationAction = SetCreationAction.PICK_AND_CHOOSE },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.ChecklistRtl,
                                            contentDescription = null,
                                            modifier = Modifier.align(Alignment.CenterStart)
                                        )
                                        Text(
                                            text = getText(R.string.pick_and_choose),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Button(
                                    onClick = { creationAction = SetCreationAction.FILTER_AND_SORT },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.FilterAlt,
                                            contentDescription = null,
                                            modifier = Modifier.align(Alignment.CenterStart)
                                        )
                                        Text(
                                            text = getText(R.string.filter_and_sort),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            contentPadding = PaddingValues(
                                start = dimensions.paddingLarge,
                                end = dimensions.paddingLarge,
                                top = dimensions.paddingLarge,
                                bottom = dimensions.paddingLarge
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge)
                        ) {
                            itemsIndexed(sortedSets) { index, set ->
                                val subSets =
                                    allDecksWithCards.filter { it.deck.parentDeckId == set.deck.id }
                                val childSetsCount = subSets.size

                                Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                                    DeckListItem(
                                        deck = set,
                                        dimensions = dimensions,
                                        setsCount = childSetsCount,
                                        onStudy = { autoOpen ->
                                            if (isPane) {
                                                viewModel.pushPaneAfter("set:${parentDeck.deck.id}", net.ericclark.studiare.PaneDestination.StudyModeSelection(set.deck.id, autoOpen))
                                            } else {
                                                val route =
                                                    if (autoOpen != null) "studyModeSelection/${set.deck.id}?autoOpen=$autoOpen" else "studyModeSelection/${set.deck.id}"
                                                if (set.totalCards > 0) navController.navigate(route)
                                            }
                                        },
                                        onEdit = { setToEdit = set },
                                        onDelete = { showDeleteDialog = set },
                                        onManageSets = {
                                            if (isPane) {
                                                viewModel.pushPaneAfter("set:${parentDeck.deck.id}", net.ericclark.studiare.PaneDestination.SetManager(set.deck.id))
                                            } else {
                                                navController.navigate("setManager/${set.deck.id}")
                                            }
                                        },
                                        onTogglePin = { viewModel.toggleDeckPinned(set.deck) },
                                        showManageSetsButton = true,
                                        tapOpensStudy = false,
                                        index = index,
                                        activeSession = sessionActions.latestFor(set.deck.id),
                                        onResumeSession = { sessionActions.resume(it) },
                                        onRestartSession = { sessionActions.restart(it) },
                                        onCreateSession = { sessionActions.startNewSession(set.deck.id) },
                                        selected = selection.isSelected(set.deck.id),
                                        selectionMode = selection.inSelectionMode,
                                        onToggleSelect = { selection.toggle(set.deck.id) },
                                        onLongClick = { selection.enterWith(set.deck.id) }
                                    )

                                    AnimatedVisibility(
                                        visible = subSets.isNotEmpty() && displaySetsUnderDecks,
                                        enter = slideInVertically() + fadeIn() + expandVertically(),
                                        exit = slideOutVertically() + fadeOut() + shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = dimensions.paddingSmall)
                                        ) {
                                            val listState = rememberLazyListState()

                                            LazyRow(
                                                state = listState,
                                                horizontalArrangement = Arrangement.spacedBy(
                                                    dimensions.spacingSmall
                                                )
                                            ) {
                                                items(subSets) { subset ->
                                                    SubSetListItem(
                                                        deck = subset,
                                                        dimensions = dimensions,
                                                        onStudy = { autoOpen ->
                                                            if (isPane) {
                                                                viewModel.pushPaneAfter("set:${parentDeck.deck.id}", net.ericclark.studiare.PaneDestination.StudyModeSelection(subset.deck.id, autoOpen))
                                                            } else {
                                                                val route =
                                                                    if (autoOpen != null) "studyModeSelection/${subset.deck.id}?autoOpen=$autoOpen" else "studyModeSelection/${subset.deck.id}"
                                                                if (subset.cards.isNotEmpty()) navController.navigate(
                                                                    route
                                                                )
                                                            }
                                                        },
                                                        onManageSets = {
                                                            if (isPane) {
                                                                viewModel.pushPaneAfter("set:${parentDeck.deck.id}", net.ericclark.studiare.PaneDestination.SetManager(subset.deck.id))
                                                            } else {
                                                                navController.navigate("setManager/${subset.deck.id}")
                                                            }
                                                        },
                                                        activeSession = sessionActions.latestFor(subset.deck.id),
                                                        onResumeSession = { sessionActions.resume(it) },
                                                        onRestartSession = { sessionActions.restart(it) },
                                                        onCreateSession = { sessionActions.startNewSession(subset.deck.id) },
                                                        selected = selection.isSelected(subset.deck.id),
                                                        selectionMode = selection.inSelectionMode,
                                                        onToggleSelect = { selection.toggle(subset.deck.id) },
                                                        onLongClick = { selection.enterWith(subset.deck.id) }
                                                    )
                                                }
                                            }

                                            if (subSets.size > 1) {
                                                val currentIndex by rememberCarouselIndicatorIndex(listState)

                                                Row(
                                                    modifier = Modifier.fillMaxWidth()
                                                        .padding(top = dimensions.paddingSmall),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    subSets.indices.forEach { index ->
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
                                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.3f
                                                            ),
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

                // Scrim overlay to click-away and close the menu
                if (fabMenuExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { fabMenuExpanded = false }
                    )
                }

                // Axis-based expand/collapse from the bottom (where both FABs below are
                // anchored), not scale — matches M3's Android enter/exit guidance. Exit uses the
                // fast spec so the FAB clears quickly instead of cross-fading at full duration.
                val fabMotionScheme = MaterialTheme.motionScheme
                if (selection.inSelectionMode) {
                    val cardActions = remember(selection.selected, allVisibleSetIds) {
                        val selectAll = net.ericclark.studiare.screens.UI_Components.SelectionAction(
                            id = "selectAll",
                            icon = Icons.Default.SelectAll,
                            label = getText(context, R.string.select_all),
                            onClick = { selection.toggleAllWithin(allVisibleSetIds) }
                        )
                        val advancedSelection = net.ericclark.studiare.screens.UI_Components.SelectionAction(
                            id = "advancedSelection",
                            icon = Icons.Default.AccountTree,
                            label = getText(context, R.string.advanced_selection),
                            onClick = { showAdvancedSelectionDialog = true }
                        )
                        if (selection.selected.isEmpty()) {
                            listOf(advancedSelection, selectAll)
                        } else {
                            val others = listOf(
                                net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                    id = "unpin",
                                    icon = Icons.Outlined.PushPin,
                                    label = getText(context, R.string.unpin),
                                    onClick = {
                                        selection.selected.forEach { id ->
                                            allDecksWithCards.find { it.deck.id == id }?.let { viewModel.setDeckPinned(it.deck, false) }
                                        }
                                    }
                                ),
                                net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                    id = "pin",
                                    icon = Icons.Default.PushPin,
                                    label = getText(context, R.string.pin),
                                    onClick = {
                                        selection.selected.forEach { id ->
                                            allDecksWithCards.find { it.deck.id == id }?.let { viewModel.setDeckPinned(it.deck, true) }
                                        }
                                    }
                                ),
                                net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                    id = "edit",
                                    icon = Icons.Default.Edit,
                                    label = getText(context, R.string.edit),
                                    onClick = {
                                        val targetId = selection.selected.firstOrNull()
                                        selection.exit()
                                        val target = allDecksWithCards.find { it.deck.id == targetId }
                                        if (target != null) setToEdit = DeckSummary(target.deck, target.cards.size)
                                    }
                                ),
                                net.ericclark.studiare.screens.UI_Components.SelectionAction(
                                    id = "delete",
                                    icon = Icons.Default.Delete,
                                    label = getText(context, R.string.delete),
                                    isDestructive = true,
                                    onClick = {
                                        showBulkDeleteDialog = allDecksWithCards
                                            .filter { it.deck.id in selection.selected }
                                            .map { DeckSummary(it.deck, it.cards.size) }
                                    }
                                )
                            )
                            listOf(advancedSelection) + others.filter { it.id != "edit" || selection.selected.size == 1 } + selectAll
                        }
                    }
                    Row(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .fillMaxWidth()
                            .padding(dimensions.paddingMedium),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                    ) {
                        net.ericclark.studiare.screens.UI_Components.SelectionActionBar(
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
                            net.ericclark.studiare.screens.UI_Components.SelectionCountTile(
                                selectedCount = selection.selected.size,
                                totalCount = allVisibleSetIds.size,
                                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-32).dp)
                            )
                        }
                    }
                    if (showAdvancedSelectionDialog) {
                        net.ericclark.studiare.screens.Dialogs.AdvancedSelectionDialog(
                            decks = advancedSelectionDecks,
                            selection = selection,
                            actions = cardActions,
                            onDismiss = { showAdvancedSelectionDialog = false }
                        )
                    }
                } else {
                AnimatedVisibility(
                    visible = sortedSets.isNotEmpty(),
                    enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom, animationSpec = fabMotionScheme.defaultSpatialSpec()) +
                        fadeIn(animationSpec = fabMotionScheme.defaultEffectsSpec()),
                    exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = fabMotionScheme.fastSpatialSpec()) +
                        fadeOut(animationSpec = fabMotionScheme.fastEffectsSpec()),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimensions.paddingMedium)
                ) {
                    // This Single Box properly layers the Menu and the Main FAB so they never overlap.
                    Box(contentAlignment = Alignment.BottomEnd) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = fabMenuExpanded,
                            enter = androidx.compose.animation.expandVertically(expandFrom = Alignment.Bottom, animationSpec = fabMotionScheme.defaultSpatialSpec()) +
                                fadeIn(animationSpec = fabMotionScheme.defaultEffectsSpec()),
                            exit = androidx.compose.animation.shrinkVertically(shrinkTowards = Alignment.Bottom, animationSpec = fabMotionScheme.fastSpatialSpec()) +
                                fadeOut(animationSpec = fabMotionScheme.fastEffectsSpec()),
                            modifier = Modifier.padding(bottom = 56.dp + dimensions.spacingMedium) // Perfectly clear the main FAB
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                            ) {
                                // Delete All Sets Option (Only show if there are sets)
                                if (sortedSets.isNotEmpty()) {
                                    androidx.compose.material3.ExtendedFloatingActionButton(
                                        onClick = {
                                            fabMenuExpanded = false
                                            creationAction = SetCreationAction.DELETE_ALL
                                        },
                                        modifier = Modifier.withShortcut(Key.Delete, "Del", id = "sets.delete_all") {
                                            fabMenuExpanded = false
                                            creationAction = SetCreationAction.DELETE_ALL
                                        },
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        icon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null
                                            )
                                        },
                                        text = {
                                            Text(
                                                getText(R.string.delete_all_sets),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        },
                                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                    )
                                }

                                // Clone Option
                                androidx.compose.material3.ExtendedFloatingActionButton(
                                    onClick = {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.CLONE
                                    },
                                    modifier = Modifier.withShortcut(Key.C, "C", id = "sets.clone") {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.CLONE
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    icon = {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(
                                            "Clone Entire Deck",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                )

                                // Manual Option
                                androidx.compose.material3.ExtendedFloatingActionButton(
                                    onClick = {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.PICK_AND_CHOOSE
                                    },
                                    modifier = Modifier.withShortcut(Key.M, "M", id = "sets.manual") {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.PICK_AND_CHOOSE
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    icon = {
                                        Icon(
                                            Icons.Default.ChecklistRtl,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(
                                            getText(R.string.pick_and_choose),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                )

                                // Automatic Option
                                androidx.compose.material3.ExtendedFloatingActionButton(
                                    onClick = {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.FILTER_AND_SORT
                                    },
                                    modifier = Modifier.withShortcut(Key.A, "A", id = "sets.automatic") {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.FILTER_AND_SORT
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    icon = {
                                        Icon(
                                            Icons.Default.FilterAlt,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(
                                            getText(R.string.filter_and_sort),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                )
                            }
                        }

                        val mainFabRotation by animateFloatAsState(
                            targetValue = if (fabMenuExpanded) 45f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "fabRotation"
                        )

                        val addInteractionSource = remember { MutableInteractionSource() }

                        // FIX: Replaced standard FAB with ExtendedFloatingActionButton to match DecksScreen!
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            onClick = { fabMenuExpanded = !fabMenuExpanded },
                            interactionSource = addInteractionSource,
                            modifier = Modifier.pressSquish(addInteractionSource, 0.85f),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // M3 Expressive Pill shape
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = getText(R.string.set_create),
                                    modifier = Modifier.rotate(mainFabRotation)
                                )
                            },
                            text = {
                                Text(
                                    text = getText(R.string.set_create), // Displays "Create Set"
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                }
                }
            }
        } // <-- THIS is the brace that was missing: closes `val paneContent = { padding -> ... }`

        if (isPane) {
            // Title lives in the shared app bar (via PaneChrome), not in the pane.
            Box(Modifier.fillMaxSize()) { paneContent(PaddingValues(0.dp)) }
        } else {
            Scaffold(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
                topBar = {
                    Column {
                        CustomTopAppBar(
                            viewModel = viewModel,
                            screenId = ShortcutScreen.SETS,
                            title = { Text(screenTitle) },
                            navigationIcon = {
                                TooltipIconButton(description = getText(R.string.back), onClick = navigateUp) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = getText(R.string.back))
                                }
                            },
                            actions = {
                                TooltipIconButton(description = getText(R.string.sort_decks), onClick = { showSortDialog = true }) {
                                    Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = getText(R.string.sort_decks))
                                }
                            }
                        )
                        BreadcrumbsBar(
                            currentDeck = parentDeck.deck,
                            allDecks = allDecksWithCards.map { it.deck },
                            onNavigateHome = { navController.navigate("deckList") { popUpTo(0) } },
                            onNavigateToDeck = { deckId ->
                                navController.navigate("setManager/$deckId") {
                                    popUpTo("deckList") { inclusive = false }
                                }
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.5f
                            )
                        )
                    }
                }
            ) { padding -> paneContent(padding) }
        }
    }
}

@Composable
fun CreateSetDialog(
    onDismiss: () -> Unit,
    onAutomatic: () -> Unit,
    onManual: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    getText(R.string.set_create),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                val autoInteractionSource = remember { MutableInteractionSource() }
                Button(onClick = onAutomatic, interactionSource = autoInteractionSource, modifier = Modifier.fillMaxWidth().pressSquish(autoInteractionSource), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                    Text(getText(R.string.automatic))
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                val manualInteractionSource = remember { MutableInteractionSource() }
                Button(onClick = onManual, interactionSource = manualInteractionSource, modifier = Modifier.fillMaxWidth().pressSquish(manualInteractionSource), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                    Text(getText(R.string.manual))
                }
            }
        }
    }
}

@Composable
fun CardSelectItem(
    card: Card,
    index: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressSquish(interactionSource)
            .background(if (index % 2 != 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onToggle
            )
            .padding(horizontal = dimensions.paddingSmall, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(card.front, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        IconButton(
            onClick = onToggle,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            icon()
        }
    }
}
