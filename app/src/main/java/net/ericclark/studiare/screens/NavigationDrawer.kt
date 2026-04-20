package net.ericclark.studiare.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
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
            IconButton(onClick = {
                navController.navigate("deckList") { popUpTo(0) }
                onNavigateAction() // Use navigate action here
            }) {
                Image(
                    painter = painterResource(id = R.drawable.studiare_solid),
                    contentDescription = "Home",
                    modifier = Modifier.size(36.dp).clip(CircleShape)
                )
            }
            Text(
                text = getText(R.string.app_name),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onCloseAction) { // Use explicit close action here
                Icon(Icons.Default.Menu, contentDescription = "Close Drawer")
            }
        }
        HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp))

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
    onNavigateAction: () -> Unit, // THE FIX: Renamed from closeDrawer
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

        // --- THE FIX: Custom Surface to mathematically match the Header Grid ---
        Surface(
            onClick = {
                if (hasChildren) expanded = !expanded
                else {
                    navController.navigate("studyModeSelection/${deckWithCards.deck.id}")
                    onNavigateAction()
                }
            },
            color = androidx.compose.ui.graphics.Color.Transparent, // Let the background show through
            shape = androidx.compose.ui.graphics.RectangleShape,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    // Exactly mirrors the horizontal padding of your Drawer Header
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. LEFT COLUMN: 48dp Box (Perfectly aligns with the Home Icon)
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
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
                            modifier = Modifier.scale(editScale).size(36.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = getText(R.string.edit),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 2. MIDDLE COLUMN: weight(1f) (Perfectly aligns with "Studiare" text)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        // If depth > 0 (it's a Set), indent it 24dp to the right!
                        .padding(start = (depth * 24).dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = deckWithCards.deck.name,
                        maxLines = 1,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

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

                // 3. RIGHT COLUMN: 48dp Box (Perfectly aligns with Hamburger Menu Icon)
                Box(modifier = Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (hasChildren) {
                        val rotation by androidx.compose.animation.core.animateFloatAsState(
                            if (expanded) 180f else 0f,
                            label = "expandRot"
                        )
                        Icon(
                            Icons.Default.ExpandMore,
                            contentDescription = null,
                            modifier = Modifier.graphicsLayer { rotationZ = rotation }
                        )
                    }
                }
            }
        }

        // The Expandable Content
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Saved Sessions
                deckSessions.forEach { session ->
                    NavigationDrawerItem(
                        label = {
                            Text(
                                stringResource(R.string.session_type, session.mode.asString()),
                                //"${session.mode.asString()} Session",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        icon = {
                            Icon(
                                Icons.Default.PlayCircle,
                                null,
                                modifier = Modifier.size(16.dp)
                            )
                        },
                        selected = false,
                        shape = androidx.compose.ui.graphics.RectangleShape,
                        onClick = {
                            navController.navigate("studyModeSelection/${deckWithCards.deck.id}")
                            onNavigateAction()
                        },
                        // Adjusted padding so sessions align neatly under the indented Sets
                        modifier = Modifier.padding(
                            start = (48 + ((depth + 1) * 24)).dp,
                            end = 16.dp,
                            bottom = 4.dp
                        )
                    )
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
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    }
}