package net.ericclark.studiare.screens.Screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
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
import kotlinx.coroutines.flow.first
import net.ericclark.studiare.BuildConfig
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.LocalNavAnimatedVisibilityScope
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.withShortcut
import net.ericclark.studiare.screens.Dialogs.studyRouteFor
import net.ericclark.studiare.screens.UI_Components.sessionModeDescription
import net.ericclark.studiare.screens.UI_Components.sessionModeIcon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecentsScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    // Confirmed via timing logs: this screen's first composition this session costs ~187ms
    // (body-start to its LaunchedEffect(Unit) committing), which swallowed the NavHost's 200-300ms
    // top-level fade whole — the transition was being correctly requested (verified separately),
    // but by the time the first real frame painted, the fade's own clock already reported past its
    // end. Same root cause already diagnosed and fixed for SimpleEditorScreen and Settings'
    // "Customization" category — one-time class-load/JIT cost, not algorithmic (this screen has no
    // heavy work: a handful of active-session cards). Same fix: let the transition play over empty
    // chrome first, defer this screen's content until it's visually settled.
    //
    // DEBUG-only — see SimpleEditorScreen.kt's matching mainContentReady comment for why: a
    // release build's AOT-compiled hot paths (baseline profile) were confirmed on-device not to
    // need this safety net, while debug is always JIT-interpreted regardless.
    val navAnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    var mainContentReady by remember { mutableStateOf(!BuildConfig.DEBUG || navAnimatedVisibilityScope == null) }
    LaunchedEffect(navAnimatedVisibilityScope) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        val transition = navAnimatedVisibilityScope?.transition ?: return@LaunchedEffect
        snapshotFlow { transition.currentState }.first { it == EnterExitState.Visible }
        mainContentReady = true
    }
    val activeSessions by viewModel.allActiveSessions.collectAsState()
    val allDecks by viewModel.allDecks.observeAsState(emptyList())
    // Resume the tapped session, then open its study screen once the ViewModel has loaded it.
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
    val dimensions = LocalStudiareDimensions.current

    // Sort active sessions by the most recently accessed
    val sortedSessions = remember(activeSessions) {
        activeSessions.sortedByDescending { it.lastAccessed }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            net.ericclark.studiare.CustomTopAppBar(
                viewModel = viewModel,
                screenId = net.ericclark.studiare.ShortcutScreen.RECENTS,
                title = {
                    Text(getText(R.string.recent_sessions), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                }
            )
        }
    ) { padding ->
        // Gated on mainContentReady (see the comment above) so this screen's first composition
        // happens after, not during, the NavHost transition into it — a plain fade covers its
        // otherwise-abrupt appearance.
        AnimatedVisibility(
            visible = mainContentReady,
            enter = fadeIn(),
            exit = ExitTransition.None,
            modifier = Modifier.fillMaxSize()
        ) {
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
                    val onOpen = {
                        pendingSessionId = session.id
                        pendingRoute = studyRouteFor(session.mode)
                        viewModel.resumeStudySession(session)
                    }

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
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    sessionModeIcon(session),
                                    contentDescription = sessionModeDescription(session),
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "Mode: ${session.mode.asString()}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
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
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
        }
    }
}