package net.ericclark.studiare.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.asString

@Composable
fun AppNavigationDrawer(
    decks: List<DeckWithCards>,
    sessions: List<ActiveSession>,
    navController: NavController,
    onCloseAction: () -> Unit,     // THE FIX: Specific action for the close button
    onNavigateAction: () -> Unit   // THE FIX: Specific action for navigation links
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
            IconButton(onClick = onCloseAction) { // Menu icon now on the left
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
            }) { // App Logo now on the right
                Image(
                    painter = painterResource(id = R.drawable.studiare_solid),
                    contentDescription = "Home",
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            val rootDecks = decks.filter { it.deck.parentDeckId == null }
            items(rootDecks, key = { it.deck.id }) { rootDeck ->
                DrawerDeckHierarchyNode(
                    deckWithCards = rootDeck,
                    allDecks = decks,
                    allSessions = sessions,
                    navController = navController,
                    onNavigateAction = onNavigateAction, // Pass it down
                    depth = 0
                )
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
                onNavigateAction() // Use navigate action here
            },
            modifier = Modifier.padding(16.dp)
        )
    }
}

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

    // Find children associated with THIS deck
    val childSets = allDecks.filter { it.deck.parentDeckId == deckWithCards.deck.id }
    val deckSessions = allSessions.filter { it.deckId == deckWithCards.deck.id }
    val hasChildren = childSets.isNotEmpty() || deckSessions.isNotEmpty()

    // Determine if this is a top-level Deck or a Set
    val isDeck = deckWithCards.deck.parentDeckId == null

    Column(modifier = Modifier.fillMaxWidth()) {
        ElevatedCard(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    start = (16 + (depth * 24)).dp, // Indent inward based on depth
                    end = 16.dp,
                    top = 6.dp,
                    bottom = 6.dp
                ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (hasChildren) expanded = !expanded
                        else {
                            navController.navigate("studyModeSelection/${deckWithCards.deck.id}")
                            onNavigateAction()
                        }
                    }
                    .padding(vertical = 16.dp, horizontal = 8.dp), // Slightly reduced horizontal padding to accommodate the buttons cleanly
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // --- Row 1: Deck Name & Edit Button ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left balancer to keep text perfectly centered
                    if (isDeck) {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Text(
                        text = deckWithCards.deck.name,
                        style = if (depth == 0) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                        fontWeight = if (depth == 0) FontWeight.Bold else FontWeight.Normal,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )

                    // Right Edit Button
                    if (isDeck) {
                        val editInteractionSource = remember { MutableInteractionSource() }
                        val isEditPressed by editInteractionSource.collectIsPressedAsState()
                        val editScale by animateFloatAsState(
                            targetValue = if (isEditPressed) 0.85f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "editSquish"
                        )
                        IconButton(
                            onClick = {
                                navController.navigate("deckEditor?deckId=${deckWithCards.deck.id}")
                                onNavigateAction()
                            },
                            interactionSource = editInteractionSource,
                            modifier = Modifier.scale(editScale)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = getText(R.string.edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // --- Row 2: Study Button & Expand Carrot ---
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left balancer to keep study button perfectly centered
                    if (hasChildren) {
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        StudySplitButton(
                            onStudyMain = {
                                navController.navigate("studyModeSelection/${deckWithCards.deck.id}")
                                onNavigateAction()
                            },
                            onStudyOption = { autoOpen ->
                                navController.navigate("studyModeSelection/${deckWithCards.deck.id}?autoOpen=$autoOpen")
                                onNavigateAction()
                            },
                            enabled = deckWithCards.cards.isNotEmpty()
                        )
                    }

                    // Right Expand Carrot
                    if (hasChildren) {
                        val rotation by animateFloatAsState(
                            targetValue = if (expanded) 180f else 0f,
                            label = "expandRot"
                        )
                        IconButton(onClick = { expanded = !expanded }) {
                            Icon(
                                Icons.Default.ExpandMore,
                                contentDescription = "Expand",
                                modifier = Modifier.graphicsLayer { rotationZ = rotation }
                            )
                        }
                    }
                }
            }
        }

        // The Expandable Content
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Saved Sessions (Scrollable Row)
                if (deckSessions.isNotEmpty()) {
                    androidx.compose.foundation.lazy.LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(
                            start = (16 + ((depth + 1) * 24)).dp,
                            end = 16.dp,
                            top = 4.dp,
                            bottom = 8.dp
                        ),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(deckSessions) { session ->
                            ElevatedCard(
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

                // Child Sets
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