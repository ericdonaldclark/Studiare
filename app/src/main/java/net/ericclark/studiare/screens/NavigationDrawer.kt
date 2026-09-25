package net.ericclark.studiare.screens

import androidx.compose.animation.core.animateFloat
import net.ericclark.studiare.SessionInfoDialog
import net.ericclark.studiare.TooltipIconButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.layout.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import net.ericclark.studiare.withShortcut
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import net.ericclark.studiare.ConfirmationDialog
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.data.StudyPreset
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.asString
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import net.ericclark.studiare.LocalWindowWidthSizeClass

@Composable
fun DeckHierarchyTree(
    decks: List<DeckWithCards>,
    sessions: List<ActiveSession>,
    isLoading: Boolean,
    navController: NavController,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    onNavigateAction: () -> Unit,
    // Deck ids in the order (and with the collection filter) the grid view uses.
    orderedRootIds: List<String>? = null,
    orderedSetIds: Map<String, List<String>> = emptyMap(),
    // Settings: the desktop column browser (off = original accordion) and loading indicator (off = skeleton).
    useLargeScreenLayout: Boolean = true,
    useLoadingIndicator: Boolean = true
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    val rootDecks = if (orderedRootIds != null) {
        val byId = decks.associateBy { it.deck.id }
        orderedRootIds.mapNotNull { byId[it] }
    } else decks.filter { it.deck.parentDeckId == null }

    if (isLoading && useLoadingIndicator) {
        net.ericclark.studiare.DelayedLoadingIndicator()
    } else if (isLoading) {
        DrawerSkeletonLoader(
            modifier = Modifier.fillMaxWidth(),
            isWideScreen = useLargeScreenLayout && windowWidthSizeClass != WindowWidthSizeClass.Compact
        )
    } else if (useLargeScreenLayout && windowWidthSizeClass != WindowWidthSizeClass.Compact) {
        // Desktop: expanding a deck or set opens its content in a new column to the right
        // (a Miller-column browser) instead of accordion-expanding below it.
        DeckHierarchyColumns(
            rootDecks = rootDecks,
            allDecks = decks,
            allSessions = sessions,
            navController = navController,
            viewModel = viewModel,
            orderedSetIds = orderedSetIds
        )
    } else {
        // Cap the width and center it so the tree doesn't stretch across wide screens.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth()) {
            itemsIndexed(rootDecks, key = { _, d -> d.deck.id }) { index, rootDeck ->
                DrawerDeckHierarchyNode(
                    deckWithCards = rootDeck,
                    allDecks = decks,
                    allSessions = sessions,
                    navController = navController,
                    viewModel = viewModel,
                    windowWidthSizeClass = windowWidthSizeClass,
                    onNavigateAction = onNavigateAction,
                    depth = 0,
                    orderedSetIds = orderedSetIds,
                    // 1-9 open root decks, matching the grid's own numbered shortcuts.
                    shortcutIndex = index
                )
            }
        }
        }
    }
}

/**
 * Desktop tree layout: a Miller-column browser. Column 0 lists root decks; selecting one opens a
 * new column to its right showing that deck's actions, saved sessions and child sets; selecting a
 * child does the same one level deeper, and so on. Clicking the already-selected row again closes
 * it (and anything deeper).
 */
@Composable
private fun DeckHierarchyColumns(
    rootDecks: List<DeckWithCards>,
    allDecks: List<DeckWithCards>,
    allSessions: List<ActiveSession>,
    navController: NavController,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    orderedSetIds: Map<String, List<String>>
) {
    // selectedPath[d] = the id selected in column d; column d+1 (if present) shows its content.
    // Lives on the ViewModel (not remember) so it survives navigating to another screen and back.
    val selectedPath = viewModel.treeSelectedPath

    fun childrenOf(parentId: String?): List<DeckWithCards> {
        val children = if (parentId == null) rootDecks else allDecks.filter { it.deck.parentDeckId == parentId }
        val order = if (parentId == null) null else orderedSetIds[parentId]
        return if (order == null) children else {
            val rank = order.withIndex().associate { it.value to it.index }
            children.sortedBy { rank[it.deck.id] ?: Int.MAX_VALUE }
        }
    }

    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    LaunchedEffect(selectedPath.size) {
        scope.launch { scrollState.animateScrollTo(scrollState.maxValue) }
    }

    // Column width: with just the root list open, it's half the screen, centered. Opening a
    // deck splits the available 2/3 of the screen into two 1/3-wide columns, still centered.
    // A third column fills the rest of the screen (three 1/3-wide columns). Beyond that, columns
    // stay 1/3-wide and the row scrolls horizontally instead of shrinking further.
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        val totalColumns = 1 + selectedPath.size
        val targetColumnWidth = maxWidth / minOf(totalColumns + 1, 3)
        val targetBlockWidth = targetColumnWidth * totalColumns
        val targetViewportWidth = targetBlockWidth.coerceAtMost(maxWidth)

        // M3 Expressive fluid motion: existing columns resize smoothly (rather than snapping)
        // whenever a column opens or closes elsewhere in the row.
        val motionScheme = MaterialTheme.motionScheme
        val columnWidth by androidx.compose.animation.core.animateDpAsState(
            targetValue = targetColumnWidth,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "treeColumnWidth"
        )
        val viewportWidth by androidx.compose.animation.core.animateDpAsState(
            targetValue = targetViewportWidth,
            animationSpec = motionScheme.defaultSpatialSpec(),
            label = "treeViewportWidth"
        )

        Box(
            modifier = Modifier
                .width(viewportWidth)
                .fillMaxHeight()
                .horizontalScroll(scrollState)
        ) {
            Row(modifier = Modifier.fillMaxHeight()) {
                TreeColumn(
                    node = null,
                    children = childrenOf(null),
                    selectedChildId = selectedPath.getOrNull(0),
                    width = columnWidth,
                    allDecks = allDecks,
                    allSessions = allSessions,
                    navController = navController,
                    viewModel = viewModel,
                    onSelectChild = { id ->
                        if (selectedPath.getOrNull(0) == id) selectedPath.clear()
                        else { selectedPath.clear(); selectedPath.add(id) }
                    }
                )
                var depth = 0
                while (depth < selectedPath.size) {
                    val node = allDecks.find { it.deck.id == selectedPath[depth] }
                    if (node == null) break
                    val atDepth = depth
                    key(node.deck.id) {
                        // Newly opened columns fade and slide in from the right, alongside their
                        // divider, instead of popping in abruptly.
                        val visibleState = remember(node.deck.id) {
                            androidx.compose.animation.core.MutableTransitionState(false)
                        }.apply { targetState = true }
                        androidx.compose.animation.AnimatedVisibility(
                            visibleState = visibleState,
                            enter = androidx.compose.animation.fadeIn(animationSpec = motionScheme.defaultEffectsSpec()) +
                                    androidx.compose.animation.slideInHorizontally(
                                        animationSpec = motionScheme.defaultSpatialSpec(),
                                        initialOffsetX = { fullWidth -> fullWidth / 3 }
                                    )
                        ) {
                            Row {
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                TreeColumn(
                                    node = node,
                                    children = childrenOf(node.deck.id),
                                    selectedChildId = selectedPath.getOrNull(atDepth + 1),
                                    width = columnWidth,
                                    allDecks = allDecks,
                                    allSessions = allSessions,
                                    navController = navController,
                                    viewModel = viewModel,
                                    onSelectChild = { id ->
                                        if (selectedPath.getOrNull(atDepth + 1) == id) {
                                            while (selectedPath.size > atDepth + 1) selectedPath.removeAt(selectedPath.size - 1)
                                        } else {
                                            while (selectedPath.size > atDepth + 1) selectedPath.removeAt(selectedPath.size - 1)
                                            selectedPath.add(id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                    depth++
                }
            }
        }
    }
}

/**
 * One column of the desktop tree browser. When [node] is null this is the root deck list (no
 * header/actions). Otherwise it shows [node]'s own action buttons and saved sessions above a list
 * of its children, each of which opens the next column when tapped.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TreeColumn(
    node: DeckWithCards?,
    children: List<DeckWithCards>,
    selectedChildId: String?,
    width: androidx.compose.ui.unit.Dp,
    allDecks: List<DeckWithCards>,
    allSessions: List<ActiveSession>,
    navController: NavController,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    onSelectChild: (String) -> Unit
) {
    Column(modifier = Modifier.width(width).fillMaxHeight()) {
        if (node != null) {
            Text(
                text = node.deck.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp)
            )
            TreeNodeDetail(node = node, allDecks = allDecks, allSessions = allSessions, navController = navController, viewModel = viewModel)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
            if (children.isEmpty() && node != null) {
                item {
                    Text(
                        text = "No sub-sets",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            } else {
                itemsIndexed(children, key = { _, c -> c.deck.id }) { index, child ->
                    TreeChildRow(
                        deckWithCards = child,
                        allDecks = allDecks,
                        viewModel = viewModel,
                        isSelected = child.deck.id == selectedChildId,
                        onClick = { onSelectChild(child.deck.id) },
                        // 1-9 open the root deck list's items, matching the grid's own shortcuts;
                        // deeper columns don't reuse the same keys.
                        shortcutIndex = if (node == null) index else -1
                    )
                }
            }
        }
    }
}

/** Action buttons, saved sessions and their dialogs for whichever node a column's header represents. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TreeNodeDetail(
    node: DeckWithCards,
    allDecks: List<DeckWithCards>,
    allSessions: List<ActiveSession>,
    navController: NavController,
    viewModel: net.ericclark.studiare.FlashcardViewModel
) {
    var createPreset by remember(node.deck.id) { mutableStateOf<StudyPreset?>(null) }
    var showSetEditor by remember(node.deck.id) { mutableStateOf(false) }
    var showSpacedRepetition by remember(node.deck.id) { mutableStateOf(false) }
    var pendingResume by remember(node.deck.id) { mutableStateOf<ActiveSession?>(null) }
    var sessionMenuId by remember(node.deck.id) { mutableStateOf<String?>(null) }
    var sessionToRestart by remember(node.deck.id) { mutableStateOf<ActiveSession?>(null) }
    var sessionForDetails by remember(node.deck.id) { mutableStateOf<ActiveSession?>(null) }
    var sessionToDelete by remember(node.deck.id) { mutableStateOf<ActiveSession?>(null) }

    val activeStudyState = viewModel.studyState
    LaunchedEffect(activeStudyState?.sessionId, pendingResume) {
        val pending = pendingResume
        if (pending != null && activeStudyState?.sessionId == pending.id) {
            navController.navigate(studyRouteFor(pending.mode))
            pendingResume = null
        }
    }

    val isDeck = node.deck.parentDeckId == null
    val deckSessions = allSessions.filter { it.deckId == node.deck.id }.sortedByDescending { it.lastAccessed }

    sessionForDetails?.let { session -> SessionInfoDialog(session = session, onDismiss = { sessionForDetails = null }) }
    sessionToRestart?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.restart_session_title),
            text = getText(R.string.restart_session_desc),
            onConfirm = { viewModel.restartSession(session); sessionToRestart = null },
            onDismiss = { sessionToRestart = null }
        )
    }
    sessionToDelete?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.delete_session_title),
            text = getText(R.string.delete_session_desc),
            onConfirm = { viewModel.deleteSession(session); sessionToDelete = null },
            onDismiss = { sessionToDelete = null }
        )
    }
    if (showSetEditor) {
        val parentDeck = allDecks.find { it.deck.id == node.deck.parentDeckId }
        if (parentDeck != null) {
            ManualSetEditorDialog(
                navController = navController,
                parentDeck = parentDeck,
                setForEditing = node,
                viewModel = viewModel,
                onDismiss = { showSetEditor = false }
            )
        }
    }
    if (createPreset != null || showSpacedRepetition) {
        StudySessionDialogHost(
            deck = node,
            preset = createPreset,
            showSpacedRepetition = showSpacedRepetition,
            viewModel = viewModel,
            navController = navController,
            onDismiss = { createPreset = null; showSpacedRepetition = false }
        )
    }

    if (node.cards.isNotEmpty() || isDeck) {
        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ReversedActionButton(Icons.Default.Edit, "Edit") {
                if (isDeck) navController.navigate("deckEditor?deckId=${node.deck.id}") else showSetEditor = true
            }
            ReversedActionButton(Icons.Default.PlayArrow, "Study") { createPreset = StudyPreset.STUDY }
            ReversedActionButton(Icons.AutoMirrored.Filled.MenuBook, "Practice") { createPreset = StudyPreset.STUDY }
            ReversedActionButton(Icons.Default.Quiz, "Quiz") { createPreset = StudyPreset.QUIZ }
            ReversedActionButton(Icons.Default.SportsEsports, "Game") { createPreset = StudyPreset.GAMES }
            ReversedActionButton(Icons.Default.Schedule, "Spaced Repetition") { showSpacedRepetition = true }
        }
        Spacer(Modifier.height(8.dp))
    }

    if (deckSessions.isNotEmpty()) {
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(deckSessions) { session ->
                val sessionInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                Box {
                    ElevatedCard(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .combinedClickable(
                                interactionSource = sessionInteractionSource,
                                indication = androidx.compose.foundation.LocalIndication.current,
                                onClick = { pendingResume = session; viewModel.resumeStudySession(session) },
                                onLongClick = { sessionMenuId = session.id }
                            ),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        // width(IntrinsicSize.Min) keeps the tile hugging the row's own content
                        // width; a plain fillMaxWidth() here would stretch the whole tile to the
                        // LazyRow's full available width instead of staying compact.
                        Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    sessionModeIcon(session.mode, session.isGraded),
                                    contentDescription = sessionModeDescription(session.mode, session.isGraded),
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(session.mode.asString(), style = MaterialTheme.typography.bodyMedium, maxLines = 1, softWrap = false)
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    net.ericclark.studiare.components.formatTimeAgo(session.lastAccessed),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    softWrap = false
                                )
                            }
                            // Experimental: a thin progress line under the session tile. May be reverted.
                            // A hand-drawn bar (not LinearProgressIndicator) so it has no library-imposed
                            // minimum width and stays exactly as wide as the row above it.
                            val sessionProgress = if (session.totalCards > 0) session.currentCardIndex.toFloat() / session.totalCards else 0f
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp)
                                    .clip(RoundedCornerShape(1.5.dp))
                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(sessionProgress.coerceIn(0f, 1f))
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(1.5.dp))
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        }
                    }
                    DropdownMenu(expanded = sessionMenuId == session.id, onDismissRequest = { sessionMenuId = null }) {
                        DropdownMenuItem(
                            text = { Text("Details") },
                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                            onClick = { sessionMenuId = null; sessionForDetails = session }
                        )
                        DropdownMenuItem(
                            text = { Text(getText(R.string.copy)) },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                            onClick = { sessionMenuId = null; viewModel.copySession(session) }
                        )
                        DropdownMenuItem(
                            text = { Text("Restart") },
                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                            onClick = { sessionMenuId = null; sessionToRestart = session }
                        )
                        DropdownMenuItem(
                            text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { sessionMenuId = null; sessionToDelete = session }
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** A single selectable row in a desktop tree column; opens the next column when tapped. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TreeChildRow(
    deckWithCards: DeckWithCards,
    allDecks: List<DeckWithCards>,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    isSelected: Boolean,
    onClick: () -> Unit,
    shortcutIndex: Int = -1
) {
    var showOverflow by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateSetOptions by remember { mutableStateOf(false) }
    var setCreationAction by remember { mutableStateOf<SetCreationAction?>(null) }
    val isDeck = deckWithCards.deck.parentDeckId == null

    if (showDeleteConfirm) {
        if (isDeck) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                title = { Text(getText(R.string.delete_deck_question)) },
                text = { Text(stringResource(R.string.delete_deck_confirm, deckWithCards.deck.name)) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.deleteDeck(deckWithCards.deck.id); showDeleteConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(LocalStudiareDimensions.current.cornerRadiusButton)
                    ) { Text(getText(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirm = false },
                        shape = RoundedCornerShape(LocalStudiareDimensions.current.cornerRadiusButton)
                    ) { Text(getText(R.string.cancel)) }
                }
            )
        } else {
            ConfirmationDialog(
                title = getText(R.string.delete_set_question),
                text = stringResource(R.string.delete_set_confirm, deckWithCards.deck.name),
                onConfirm = { viewModel.deleteDeck(deckWithCards.deck.id); showDeleteConfirm = false },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
    if (showCreateSetOptions) {
        val hasSets = allDecks.any { it.deck.parentDeckId == deckWithCards.deck.id }
        CreateSetOptionsDialog(
            hasSets = hasSets,
            onSelect = { showCreateSetOptions = false; setCreationAction = it },
            onDismiss = { showCreateSetOptions = false }
        )
    }
    SetCreationDialogHost(
        parentDeck = deckWithCards,
        action = setCreationAction,
        viewModel = viewModel,
        onDismiss = { setCreationAction = null }
    )

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val keyMap = listOf(
        Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
        Key.Six, Key.Seven, Key.Eight, Key.Nine
    )
    ElevatedCard(
        interactionSource = interactionSource,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .let {
                if (shortcutIndex in 0..8) it.withShortcut(keyMap[shortcutIndex], "${shortcutIndex + 1}") { onClick() } else it
            },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainer
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = deckWithCards.deck.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(R.string.cards_count, deckWithCards.cards.size),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Box {
                TooltipIconButton(description = getText(R.string.options_more), onClick = { showOverflow = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.options_more))
                }
                DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                    DropdownMenuItem(
                        text = { Text(getText(R.string.set_create)) },
                        leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                        onClick = { showOverflow = false; showCreateSetOptions = true }
                    )
                    val starred = deckWithCards.deck.isStarred
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (isDeck) getText(if (starred) R.string.unstar_deck else R.string.star_deck)
                                else getText(if (starred) R.string.unstar_set else R.string.star_set)
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Rounded.Star,
                                contentDescription = null,
                                tint = if (starred) Color(0xFFFFD700) else LocalContentColor.current
                            )
                        },
                        onClick = { showOverflow = false; viewModel.toggleDeckStar(deckWithCards.deck) }
                    )
                    DropdownMenuItem(
                        text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = { showOverflow = false; showDeleteConfirm = true }
                    )
                }
            }
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerDeckHierarchyNode(
    deckWithCards: DeckWithCards,
    allDecks: List<DeckWithCards>,
    allSessions: List<ActiveSession>,
    navController: NavController,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    windowWidthSizeClass: WindowWidthSizeClass,
    onNavigateAction: () -> Unit,
    depth: Int,
    orderedSetIds: Map<String, List<String>> = emptyMap(),
    // 1-9 shortcut to open this node; only meaningful (and only ever passed) for root-level items.
    shortcutIndex: Int = -1
) {
    // Backed by the ViewModel (not remember) so expand state survives navigating away and back.
    val expanded = viewModel.treeExpandedNodeIds.contains(deckWithCards.deck.id)
    fun setExpanded(value: Boolean) {
        if (value) viewModel.treeExpandedNodeIds.add(deckWithCards.deck.id)
        else viewModel.treeExpandedNodeIds.remove(deckWithCards.deck.id)
    }
    // Study dialogs open right here over the tree instead of navigating to another page.
    var createPreset by remember { mutableStateOf<StudyPreset?>(null) }
    var showSetEditor by remember { mutableStateOf(false) }
    var showSpacedRepetition by remember { mutableStateOf(false) }
    var pendingResume by remember { mutableStateOf<ActiveSession?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showCreateSetOptions by remember { mutableStateOf(false) }
    var setCreationAction by remember { mutableStateOf<SetCreationAction?>(null) }
    var showOverflow by remember { mutableStateOf(false) }
    var sessionMenuId by remember { mutableStateOf<String?>(null) }
    var sessionToRestart by remember { mutableStateOf<ActiveSession?>(null) }
    var sessionForDetails by remember { mutableStateOf<ActiveSession?>(null) }
    var sessionToDelete by remember { mutableStateOf<ActiveSession?>(null) }
    val activeStudyState = viewModel.studyState
    LaunchedEffect(activeStudyState?.sessionId, pendingResume) {
        val pending = pendingResume
        if (pending != null && activeStudyState?.sessionId == pending.id) {
            navController.navigate(studyRouteFor(pending.mode))
            pendingResume = null
        }
    }

    val childSets = allDecks.filter { it.deck.parentDeckId == deckWithCards.deck.id }.let { children ->
        val order = orderedSetIds[deckWithCards.deck.id]
        if (order == null) children else {
            val rank = order.withIndex().associate { it.value to it.index }
            children.sortedBy { rank[it.deck.id] ?: Int.MAX_VALUE }
        }
    }
    val deckSessions = allSessions.filter { it.deckId == deckWithCards.deck.id }.sortedByDescending { it.lastAccessed }

    val isDeck = deckWithCards.deck.parentDeckId == null
    val canExpand = childSets.isNotEmpty() || deckSessions.isNotEmpty() || deckWithCards.cards.isNotEmpty() || isDeck

    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    if (showDeleteConfirm) {
        val isTopLevel = deckWithCards.deck.parentDeckId == null
        if (isTopLevel) {
            AlertDialog(
                onDismissRequest = { showDeleteConfirm = false },
                icon = { Icon(Icons.Default.DeleteForever, contentDescription = null) },
                title = { Text(getText(R.string.delete_deck_question)) },
                text = { Text(stringResource(R.string.delete_deck_confirm, deckWithCards.deck.name)) },
                confirmButton = {
                    Button(
                        onClick = { viewModel.deleteDeck(deckWithCards.deck.id); showDeleteConfirm = false },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(LocalStudiareDimensions.current.cornerRadiusButton)
                    ) { Text(getText(R.string.delete)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = { showDeleteConfirm = false },
                        shape = RoundedCornerShape(LocalStudiareDimensions.current.cornerRadiusButton)
                    ) { Text(getText(R.string.cancel)) }
                }
            )
        } else {
            ConfirmationDialog(
                title = getText(R.string.delete_set_question),
                text = stringResource(R.string.delete_set_confirm, deckWithCards.deck.name),
                onConfirm = { viewModel.deleteDeck(deckWithCards.deck.id); showDeleteConfirm = false },
                onDismiss = { showDeleteConfirm = false }
            )
        }
    }
    sessionForDetails?.let { session ->
        SessionInfoDialog(session = session, onDismiss = { sessionForDetails = null })
    }

    sessionToRestart?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.restart_session_title),
            text = getText(R.string.restart_session_desc),
            onConfirm = { viewModel.restartSession(session); sessionToRestart = null },
            onDismiss = { sessionToRestart = null }
        )
    }
    sessionToDelete?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.delete_session_title),
            text = getText(R.string.delete_session_desc),
            onConfirm = { viewModel.deleteSession(session); sessionToDelete = null },
            onDismiss = { sessionToDelete = null }
        )
    }
    if (showCreateSetOptions) {
        CreateSetOptionsDialog(
            hasSets = childSets.isNotEmpty(),
            onSelect = { showCreateSetOptions = false; setCreationAction = it },
            onDismiss = { showCreateSetOptions = false }
        )
    }
    SetCreationDialogHost(
        parentDeck = deckWithCards,
        action = setCreationAction,
        viewModel = viewModel,
        onDismiss = { setCreationAction = null }
    )

    if (showSetEditor) {
        // Sets use the pick-cards editor, same as the grid view.
        val parentDeck = allDecks.find { it.deck.id == deckWithCards.deck.parentDeckId }
        if (parentDeck != null) {
            ManualSetEditorDialog(
                navController = navController,
                parentDeck = parentDeck,
                setForEditing = deckWithCards,
                viewModel = viewModel,
                onDismiss = { showSetEditor = false }
            )
        }
    }

    if (createPreset != null || showSpacedRepetition) {
        StudySessionDialogHost(
            deck = deckWithCards,
            preset = createPreset,
            showSpacedRepetition = showSpacedRepetition,
            viewModel = viewModel,
            navController = navController,
            onDismiss = { createPreset = null; showSpacedRepetition = false }
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        ElevatedCard(
            interactionSource = interactionSource,
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = (16 + (depth * 24)).dp, // Indent inward based on depth
                    end = 16.dp,
                    top = 4.dp,
                    bottom = 4.dp
                )
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        when (event.key) {
                            Key.DirectionRight -> {
                                if (canExpand && !expanded) {
                                    setExpanded(true)
                                    return@onPreviewKeyEvent true
                                }
                            }
                            Key.DirectionLeft -> {
                                if (expanded) {
                                    setExpanded(false)
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                    }
                    false
                }
                .let {
                    val keyMap = listOf(
                        Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
                        Key.Six, Key.Seven, Key.Eight, Key.Nine
                    )
                    if (shortcutIndex in 0..8 && canExpand) {
                        it.withShortcut(keyMap[shortcutIndex], "${shortcutIndex + 1}") { setExpanded(true) }
                    } else it
                }
                .border(if (isFocused) 6.dp else 0.dp, borderColor, RoundedCornerShape(12.dp))
                .drawBehind {
                    // Draw the horizontal IDE branch line connecting to the trunk
                    if (depth > 0) {
                        val stroke = 2.dp.toPx()
                        val cy = size.height / 2
                        drawLine(
                            color = outlineColor,
                            start = Offset((-12).dp.toPx(), cy),
                            end = Offset(0f, cy),
                            strokeWidth = stroke
                        )
                    }
                },
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            onClick = { if (canExpand) setExpanded(!expanded) }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Name
                Text(
                    text = deckWithCards.deck.name,
                    style = if (depth == 0) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
                    fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )

                // Card count
                Text(
                    text = stringResource(R.string.cards_count, deckWithCards.cards.size),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )

                // Overflow menu: delete, create set
                Box {
                    TooltipIconButton(description = getText(R.string.options_more), onClick = { showOverflow = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.options_more))
                    }
                    DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                        DropdownMenuItem(
                            text = { Text(getText(R.string.set_create)) },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = { showOverflow = false; showCreateSetOptions = true }
                        )
                        val starred = deckWithCards.deck.isStarred
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (isDeck) getText(if (starred) R.string.unstar_deck else R.string.star_deck)
                                    else getText(if (starred) R.string.unstar_set else R.string.star_set)
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Star,
                                    contentDescription = null,
                                    tint = if (starred) Color(0xFFFFD700) else LocalContentColor.current
                                )
                            },
                            onClick = { showOverflow = false; viewModel.toggleDeckStar(deckWithCards.deck) }
                        )
                        DropdownMenuItem(
                            text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showOverflow = false; showDeleteConfirm = true }
                        )
                    }
                }

                // Right: Expand Chevron
                if (canExpand) {
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "expandRot"
                    )
                    // A wide target (about three icons across) so expanding/collapsing is easy to hit
                    // and stays clear of the overflow button.
                    Box(
                        modifier = Modifier
                            .width(72.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { setExpanded(!expanded) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier.graphicsLayer { rotationZ = rotation }
                        )
                    }
                }
            }
        }

        // The Expandable Content
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        // Draw the vertical IDE trunk line for children to connect to
                        val stroke = 2.dp.toPx()
                        val trunkX = (16 + depth * 24 + 12).dp.toPx()
                        drawLine(
                            color = outlineColor,
                            start = Offset(trunkX, 0f),
                            end = Offset(trunkX, size.height - 16.dp.toPx()),
                            strokeWidth = stroke
                        )
                    }
            ) {
                // 1. Interactive Action Row
                if (deckWithCards.cards.isNotEmpty() || isDeck) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = (16 + (depth + 1) * 24).dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 4.dp
                            )
                            .drawBehind {
                                val stroke = 2.dp.toPx()
                                val cy = size.height / 2
                                drawLine(
                                    color = outlineColor,
                                    start = Offset((-12).dp.toPx(), cy),
                                    end = Offset(0f, cy),
                                    strokeWidth = stroke
                                )
                            }
                    ) {
                        @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            ReversedActionButton(Icons.Default.Edit, "Edit") {
                                if (isDeck) navController.navigate("deckEditor?deckId=${deckWithCards.deck.id}") else showSetEditor = true
                            }

                            ReversedActionButton(Icons.Default.PlayArrow, "Study") { createPreset = StudyPreset.STUDY }
                            ReversedActionButton(Icons.AutoMirrored.Filled.MenuBook, "Practice") { createPreset = StudyPreset.STUDY }
                            ReversedActionButton(Icons.Default.Quiz, "Quiz") { createPreset = StudyPreset.QUIZ }
                            ReversedActionButton(Icons.Default.SportsEsports, "Game") { createPreset = StudyPreset.GAMES }
                            ReversedActionButton(Icons.Default.Schedule, "Spaced Repetition") { showSpacedRepetition = true }
                        }
                    }
                }

                // 2. Saved Sessions
                if (deckSessions.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(
                                start = (16 + (depth + 1) * 24).dp,
                                end = 16.dp,
                                top = 4.dp,
                                bottom = 4.dp
                            )
                            .drawBehind {
                                val stroke = 2.dp.toPx()
                                val cy = size.height / 2
                                drawLine(
                                    color = outlineColor,
                                    start = Offset((-12).dp.toPx(), cy),
                                    end = Offset(0f, cy),
                                    strokeWidth = stroke
                                )
                            }
                    ) {
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(deckSessions) { session ->
                                val sessionInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                                val isSessionFocused by sessionInteractionSource.collectIsFocusedAsState()
                                val sessionBorderColor = if (isSessionFocused) MaterialTheme.colorScheme.primary else Color.Transparent

                                Box {
                                    ElevatedCard(
                                        modifier = Modifier
                                            .border(if (isSessionFocused) 2.dp else 0.dp, sessionBorderColor, RoundedCornerShape(12.dp))
                                            .clip(RoundedCornerShape(12.dp))
                                            .combinedClickable(
                                                interactionSource = sessionInteractionSource,
                                                indication = androidx.compose.foundation.LocalIndication.current,
                                                onClick = {
                                                    pendingResume = session
                                                    viewModel.resumeStudySession(session)
                                                },
                                                onLongClick = { sessionMenuId = session.id }
                                            ),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                                    ) {
                                        Column(modifier = Modifier.width(IntrinsicSize.Min)) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    sessionModeIcon(session.mode, session.isGraded),
                                                    contentDescription = sessionModeDescription(session.mode, session.isGraded),
                                                    modifier = Modifier.size(20.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    session.mode.asString(),
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    net.ericclark.studiare.components.formatTimeAgo(session.lastAccessed),
                                                    style = MaterialTheme.typography.labelMedium,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    softWrap = false
                                                )
                                            }
                                            // Experimental: a thin progress line under the session tile. May be reverted.
                                            val sessionProgress = if (session.totalCards > 0) session.currentCardIndex.toFloat() / session.totalCards else 0f
                                            Box(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(3.dp)
                                                    .clip(RoundedCornerShape(1.5.dp))
                                                    .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f))
                                            ) {
                                                Box(
                                                    modifier = Modifier
                                                        .fillMaxWidth(sessionProgress.coerceIn(0f, 1f))
                                                        .fillMaxHeight()
                                                        .clip(RoundedCornerShape(1.5.dp))
                                                        .background(MaterialTheme.colorScheme.primary)
                                                )
                                            }
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = sessionMenuId == session.id,
                                        onDismissRequest = { sessionMenuId = null }
                                    ) {
                                        DropdownMenuItem(
                                            text = { Text("Details") },
                                            leadingIcon = { Icon(Icons.Default.Info, contentDescription = null) },
                                            onClick = { sessionMenuId = null; sessionForDetails = session }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(getText(R.string.copy)) },
                                            leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                            onClick = { sessionMenuId = null; viewModel.copySession(session) }
                                        )
                                        DropdownMenuItem(
                                            text = { Text("Restart") },
                                            leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                            onClick = { sessionMenuId = null; sessionToRestart = session }
                                        )
                                        DropdownMenuItem(
                                            text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                            onClick = { sessionMenuId = null; sessionToDelete = session }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 3. Child Sets
                childSets.forEach { childSet ->
                    DrawerDeckHierarchyNode(
                        deckWithCards = childSet,
                        allDecks = allDecks,
                        allSessions = allSessions,
                        navController = navController,
                        viewModel = viewModel,
                        windowWidthSizeClass = windowWidthSizeClass,
                        onNavigateAction = onNavigateAction,
                        depth = depth + 1,
                        orderedSetIds = orderedSetIds
                    )
                }
            }
        }
    }
}

// Same game-mode set the Create Study Session dialog uses for its "Game" preset chips.
private val gameSessionModes = listOf(
    net.ericclark.studiare.data.SessionMode.ANAGRAM,
    net.ericclark.studiare.data.SessionMode.CROSSWORD,
    net.ericclark.studiare.data.SessionMode.HANGMAN,
    net.ericclark.studiare.data.SessionMode.MEMORY,
    net.ericclark.studiare.data.SessionMode.WORD_SEARCH
)

/** Icon shown in place of a play button on a saved session tile: game, quiz or practice. */
private fun sessionModeIcon(mode: net.ericclark.studiare.data.SessionMode, isGraded: Boolean): ImageVector = when {
    mode in gameSessionModes -> Icons.Default.SportsEsports
    isGraded -> Icons.Default.Quiz
    else -> Icons.AutoMirrored.Filled.MenuBook
}

private fun sessionModeDescription(mode: net.ericclark.studiare.data.SessionMode, isGraded: Boolean): String = when {
    mode in gameSessionModes -> "Game"
    isGraded -> "Graded"
    else -> "Not graded"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReversedActionButton(icon: ImageVector, text: String, onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.Transparent

    ElevatedCard(
        interactionSource = interactionSource,
        modifier = Modifier.border(if (isFocused) 6.dp else 0.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = text, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary)
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
        }
    }
}

@Composable
fun DrawerSkeletonLoader(modifier: Modifier = Modifier, isWideScreen: Boolean = false) {
    val infiniteTransition = androidx.compose.animation.core.rememberInfiniteTransition(label = "drawerSkeletonPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.5f,
        targetValue  = 1.0f,
        animationSpec = androidx.compose.animation.core.infiniteRepeatable(
            animation  = androidx.compose.animation.core.tween(durationMillis = 900, easing = androidx.compose.animation.core.EaseInOut),
            repeatMode = androidx.compose.animation.core.RepeatMode.Reverse
        ),
        label = "drawerSkeletonAlpha"
    )
    val fill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.2f)

    // Matches the real desktop tree's root column: a flat list of rows (name, card-count chip,
    // overflow dot) rather than the old accordion's full-bleed, indented cards.
    val rows: @Composable (Modifier) -> Unit = { columnModifier ->
        LazyColumn(modifier = columnModifier, userScrollEnabled = false) {
            items(4) {
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                        .graphicsLayer { alpha = pulseAlpha },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(fill)
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(16.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(fill)
                        )
                        Spacer(Modifier.width(12.dp))
                        Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(fill))
                    }
                }
            }
        }
    }

    if (isWideScreen) {
        // The real tree centers a single ~half-width column while loading, before any column
        // has been opened; reuse that measurement so the skeleton doesn't look like a full-bleed
        // block on desktop.
        androidx.compose.foundation.layout.BoxWithConstraints(
            modifier = modifier.fillMaxWidth(),
            contentAlignment = Alignment.TopCenter
        ) {
            rows(Modifier.width(maxWidth / 2))
        }
    } else {
        rows(modifier.fillMaxWidth())
    }
}