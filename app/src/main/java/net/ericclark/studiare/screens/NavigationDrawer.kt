package net.ericclark.studiare.screens

import androidx.compose.animation.core.animateFloat
import net.ericclark.studiare.TooltipIconButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
    orderedSetIds: Map<String, List<String>> = emptyMap()
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current

    if (isLoading) {
        DrawerSkeletonLoader(modifier = Modifier.fillMaxWidth())
    } else {
        // Cap the width and center it so the tree doesn't stretch across wide screens.
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        LazyColumn(modifier = Modifier.widthIn(max = 960.dp).fillMaxWidth()) {
            val rootDecks = if (orderedRootIds != null) {
                val byId = decks.associateBy { it.deck.id }
                orderedRootIds.mapNotNull { byId[it] }
            } else decks.filter { it.deck.parentDeckId == null }
            items(rootDecks, key = { it.deck.id }) { rootDeck ->
                DrawerDeckHierarchyNode(
                    deckWithCards = rootDeck,
                    allDecks = decks,
                    allSessions = sessions,
                    navController = navController,
                    viewModel = viewModel,
                    windowWidthSizeClass = windowWidthSizeClass,
                    onNavigateAction = onNavigateAction,
                    depth = 0,
                    orderedSetIds = orderedSetIds
                )
            }
        }
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
    orderedSetIds: Map<String, List<String>> = emptyMap()
) {
    var expanded by remember { mutableStateOf(false) }
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
                                    expanded = true
                                    return@onPreviewKeyEvent true
                                }
                            }
                            Key.DirectionLeft -> {
                                if (expanded) {
                                    expanded = false
                                    return@onPreviewKeyEvent true
                                }
                            }
                        }
                    }
                    false
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
            onClick = { if (canExpand) expanded = !expanded }
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
                            .clickable { expanded = !expanded },
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
                                        Row(
                                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                session.mode.asString(),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                net.ericclark.studiare.components.formatTimeAgo(session.lastAccessed),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                    DropdownMenu(
                                        expanded = sessionMenuId == session.id,
                                        onDismissRequest = { sessionMenuId = null }
                                    ) {
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
fun DrawerSkeletonLoader(modifier: Modifier = Modifier, showDummySets: Boolean = false) {
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

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        userScrollEnabled = false
    ) {
        items(4) { index ->
            ElevatedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
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
                            .fillMaxWidth(0.7f)
                            .height(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(fill)
                    )
                }
            }
            if (index == 0 && showDummySets) {
                // Render one indented fake set below the first deck
                ElevatedCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 40.dp, end = 16.dp, top = 4.dp, bottom = 4.dp)
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
                                .fillMaxWidth(0.5f)
                                .height(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(fill)
                        )
                    }
                }
            }
        }
    }
}