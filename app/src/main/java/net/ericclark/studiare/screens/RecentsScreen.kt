package net.ericclark.studiare.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.withShortcut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val activeSessions by viewModel.allActiveSessions.collectAsState()
    val allDecks by viewModel.allDecks.observeAsState(emptyList())
    val dimensions = LocalStudiareDimensions.current

    // Sort active sessions by the most recently accessed
    val sortedSessions = remember(activeSessions) {
        activeSessions.sortedByDescending { it.lastAccessed }
    }

    Scaffold(
        topBar = {
            net.ericclark.studiare.CustomTopAppBar(
                viewModel = viewModel,
                screenId = net.ericclark.studiare.ShortcutScreen.RECENTS,
                title = {
                    Text("Recent Sessions", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            )
        }
    ) { padding ->
        if (sortedSessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No recent sessions found.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = dimensions.paddingLarge,
                    end = dimensions.paddingLarge,
                    top = dimensions.paddingLarge,
                    bottom = dimensions.paddingLarge
                ),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
            ) {
                val keyMap = listOf(
                    Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
                    Key.Six, Key.Seven, Key.Eight, Key.Nine
                )
                itemsIndexed(sortedSessions, key = { _, s -> s.id }) { index, session ->
                    val deck = allDecks.find { it.deck.id == session.deckId }
                    val deckName = deck?.deck?.name ?: "Unknown Deck"
                    val onOpen = { navController.navigate("studyModeSelection/${session.deckId}") }

                    val motionScheme = MaterialTheme.motionScheme
                    ElevatedCard(
                        onClick = onOpen,
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = motionScheme.defaultEffectsSpec(),
                                fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                placementSpec = motionScheme.defaultSpatialSpec()
                            )
                            .let {
                                if (index in 0..8) it.withShortcut(keyMap[index], "${index + 1}") { onOpen() } else it
                            },
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(dimensions.cornerRadiusMedium),
                        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
                    ) {
                        Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
                            Text(
                                text = deckName,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Mode: ${session.mode.asString()}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(12.dp))

                            val progress = if (session.totalCards > 0) session.currentCardIndex.toFloat() / session.totalCards else 0f
                            LinearProgressIndicator(
                                progress = { progress },
                                modifier = Modifier.fillMaxWidth(),
                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                            )
                            Text(
                                text = "${session.currentCardIndex} / ${session.totalCards} cards",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}