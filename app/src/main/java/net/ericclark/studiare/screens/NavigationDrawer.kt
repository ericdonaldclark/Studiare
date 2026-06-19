package net.ericclark.studiare.screens

import androidx.compose.animation.core.animateFloat
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
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.asString

@Composable
fun AppNavigationDrawer(
    decks: List<DeckWithCards>,
    sessions: List<ActiveSession>,
    isLoading: Boolean,
    navController: NavController,
    onCloseAction: () -> Unit,
    onNavigateAction: () -> Unit
) {
    ModalDrawerSheet(
        modifier = Modifier.width(340.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCloseAction) { // Menu icon on the left
                Icon(Icons.Default.Menu, contentDescription = "Close Drawer")
            }
            Text(
                text = getText(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = {
                navController.navigate("deckList") { popUpTo(0) }
                onNavigateAction()
            }) { // App Logo on the right
                Image(
                    painter = painterResource(id = R.drawable.studiare_solid),
                    contentDescription = "Home",
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Spacer(Modifier.height(8.dp))

        if (isLoading) {
            DrawerSkeletonLoader(modifier = Modifier.weight(1f))
        } else {
            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                val rootDecks = decks.filter { it.deck.parentDeckId == null }
                items(rootDecks, key = { it.deck.id }) { rootDeck ->
                    DrawerDeckHierarchyNode(
                        deckWithCards = rootDeck,
                        allDecks = decks,
                        allSessions = sessions,
                        navController = navController,
                        onNavigateAction = onNavigateAction,
                        depth = 0
                    )
                }
            }
        }

        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            selected = false,
            shape = androidx.compose.ui.graphics.RectangleShape,
            onClick = {
                navController.navigate("settings")
                onNavigateAction()
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrawerDeckHierarchyNode(
    deckWithCards: DeckWithCards,
    allDecks: List<DeckWithCards>,
    allSessions: List<ActiveSession>,
    navController: NavController,
    onNavigateAction: () -> Unit,
    depth: Int
) {
    var expanded by remember { mutableStateOf(false) }

    val childSets = allDecks.filter { it.deck.parentDeckId == deckWithCards.deck.id }
    val deckSessions = allSessions.filter { it.deckId == deckWithCards.deck.id }

    val isDeck = deckWithCards.deck.parentDeckId == null
    val canExpand = childSets.isNotEmpty() || deckSessions.isNotEmpty() || deckWithCards.cards.isNotEmpty() || isDeck

    val outlineColor = MaterialTheme.colorScheme.outlineVariant

    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

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
            onClick = {
                if (canExpand) expanded = !expanded
                else {
                    navController.navigate("studyModeSelection/${deckWithCards.deck.id}")
                    onNavigateAction()
                }
            }
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

                // Right: Expand Chevron
                if (canExpand) {
                    val rotation by animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "expandRot"
                    )
                    Icon(
                        Icons.Default.ExpandMore,
                        contentDescription = "Expand",
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
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
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            item { ReversedActionButton(Icons.Default.Edit, "Edit") { navController.navigate("deckEditor?deckId=${deckWithCards.deck.id}"); onNavigateAction() } }
                            item { ReversedActionButton(Icons.Default.PlayArrow, "Study") { navController.navigate("studyModeSelection/${deckWithCards.deck.id}"); onNavigateAction() } }
                            item { ReversedActionButton(Icons.AutoMirrored.Filled.MenuBook, "Practice") { navController.navigate("studyModeSelection/${deckWithCards.deck.id}?autoOpen=Practice"); onNavigateAction() } }
                            item { ReversedActionButton(Icons.Default.Quiz, "Quiz") { navController.navigate("studyModeSelection/${deckWithCards.deck.id}?autoOpen=Quiz"); onNavigateAction() } }
                            item { ReversedActionButton(Icons.Default.SportsEsports, "Game") { navController.navigate("studyModeSelection/${deckWithCards.deck.id}?autoOpen=Game"); onNavigateAction() } }
                            item { ReversedActionButton(Icons.Default.Schedule, "Spaced Repetition") { navController.navigate("studyModeSelection/${deckWithCards.deck.id}?autoOpen=SpacedRepetition"); onNavigateAction() } }
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

                                ElevatedCard(
                                    interactionSource = sessionInteractionSource,
                                    modifier = Modifier.border(if (isSessionFocused) 2.dp else 0.dp, sessionBorderColor, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    onClick = {
                                        navController.navigate("studyModeSelection/${deckWithCards.deck.id}")
                                        onNavigateAction()
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.PlayCircle, contentDescription = null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            stringResource(R.string.session_type, session.mode.asString()),
                                            style = MaterialTheme.typography.bodyMedium
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
                        onNavigateAction = onNavigateAction,
                        depth = depth + 1
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