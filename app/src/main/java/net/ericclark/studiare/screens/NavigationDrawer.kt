package net.ericclark.studiare.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.data.DeckWithCards

@Composable
fun AppNavigationDrawer(
    decks: List<DeckWithCards>,
    sessions: List<ActiveSession>,
    navController: NavController,
    drawerState: DrawerState
) {
    val scope = rememberCoroutineScope()
    val closeDrawer: () -> Unit = {
        scope.launch { drawerState.close() }
    }

    ModalDrawerSheet(
        modifier = Modifier.width(340.dp),
        drawerContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Studiare",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
        )
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

        LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Start the hierarchy with root decks (decks with no parent)
            val rootDecks = decks.filter { it.deck.parentDeckId == null }
            items(rootDecks, key = { it.deck.id }) { rootDeck ->
                DrawerDeckHierarchyNode(
                    deckWithCards = rootDeck,
                    allDecks = decks,
                    allSessions = sessions,
                    navController = navController,
                    closeDrawer = closeDrawer,
                    depth = 0
                )
            }
        }

        HorizontalDivider()
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
            selected = false,
            onClick = {
                navController.navigate("settings")
                closeDrawer()
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
    closeDrawer: () -> Unit,
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
        // The Deck/Set Header with Inline Actions
        NavigationDrawerItem(
            label = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(vertical = 8.dp)
                ) {
                    Text(deckWithCards.deck.name, maxLines = 1, style = MaterialTheme.typography.titleLarge)

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val editInteractionSource = remember { MutableInteractionSource() }
                        val isEditPressed by editInteractionSource.collectIsPressedAsState()
                        val editScale by animateFloatAsState(
                            targetValue = if (isEditPressed) 0.85f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "editSquish"
                        )
                        // Only show the Edit Pencil for root Decks
                        if (isDeck) {
                            IconButton(
                                onClick = {
                                    navController.navigate("deckEditor?deckId=${deckWithCards.deck.id}")
                                    closeDrawer()
                                },
                                interactionSource = editInteractionSource, modifier = Modifier.scale(editScale)
                            ) {
                                Icon(Icons.Default.Edit, getText(R.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }

                        StudySplitButton(
                            onStudyMain = {
                                navController.navigate("studyModeSelection/${deckWithCards.deck.id}")
                                closeDrawer()
                            },
                            onStudyOption = { autoOpen ->
                                navController.navigate("studyModeSelection/${deckWithCards.deck.id}?autoOpen=$autoOpen")
                                closeDrawer()
                            },
                            enabled = deckWithCards.cards.isNotEmpty()
                        )
                    }
                }
            },
            selected = false,
            onClick = {
                if (hasChildren) expanded = !expanded
                else {
                    navController.navigate("studyModeSelection/${deckWithCards.deck.id}")
                    closeDrawer()
                }
            },
            modifier = Modifier.padding(start = (16 + (depth * 16)).dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            badge = {
                if (hasChildren) {
                    val rotation by androidx.compose.animation.core.animateFloatAsState(if (expanded) 180f else 0f, label = "expandRot")
                    Icon(Icons.Default.ExpandMore, null, modifier = Modifier.graphicsLayer { rotationZ = rotation })
                }
            }
        )

        // The Expandable Content
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Saved Sessions
                deckSessions.forEach { session ->
                    NavigationDrawerItem(
                        label = { Text("${session.mode.name} Session", style = MaterialTheme.typography.bodyMedium) },
                        icon = { Icon(Icons.Default.Bookmark, null, modifier = Modifier.size(16.dp)) },
                        selected = false,
                        onClick = {
                            navController.navigate("studyModeSelection/${deckWithCards.deck.id}")
                            closeDrawer()
                        },
                        modifier = Modifier.padding(start = (32 + (depth * 16)).dp, end = 16.dp, bottom = 4.dp)
                    )
                }

                // Child Sets
                childSets.forEach { childSet ->
                    DrawerDeckHierarchyNode(
                        deckWithCards = childSet,
                        allDecks = allDecks,
                        allSessions = allSessions,
                        navController = navController,
                        closeDrawer = closeDrawer,
                        depth = depth + 1
                    )
                }
            }
        }
    }
}