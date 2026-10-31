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
import net.ericclark.studiare.ConfirmationDialog
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.LocalNavAnimatedVisibilityScope
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.withShortcut
import net.ericclark.studiare.screens.Dialogs.studyRouteFor
import net.ericclark.studiare.screens.UI_Components.SessionTile

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

    var showRestartDialog by remember { mutableStateOf<ActiveSession?>(null) }
    var showDeleteDialog by remember { mutableStateOf<ActiveSession?>(null) }

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
                    val cardIdToShow = if (session.mode == net.ericclark.studiare.data.SessionMode.MATCHING && session.matchedPairs.isNotEmpty()) session.matchedPairs.last() else session.shuffledCardIds.getOrNull(session.currentCardIndex)
                    val card = deck?.cards?.find { it.id == cardIdToShow }
                    val onOpen = {
                        pendingSessionId = session.id
                        pendingRoute = studyRouteFor(session.mode)
                        viewModel.resumeStudySession(session)
                    }

                    val motionScheme = MaterialTheme.motionScheme
                    SessionTile(
                        session = session,
                        card = card,
                        deckName = deckName,
                        // Container transform now lives inside SessionTile itself (on its preview
                        // thumbnail, keyed the same way Study Hub/Pinned already do) rather than
                        // on the whole card as this screen's old bespoke card did — keeping both
                        // active at once would register the same shared-element key twice.
                        modifier = Modifier
                            .animateItem(
                                fadeInSpec = motionScheme.defaultEffectsSpec(),
                                fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                placementSpec = motionScheme.defaultSpatialSpec()
                            )
                            .let {
                                if (index in 0..8) it.withShortcut(keyMap[index], "${index + 1}") { onOpen() } else it
                            },
                        onResume = onOpen,
                        onCopy = { viewModel.copySession(session) },
                        onRestart = { showRestartDialog = session },
                        onDelete = { showDeleteDialog = session },
                        onPin = { name -> viewModel.pinSession(session, name) },
                        onUnpin = { viewModel.unpinSession(session) },
                        // Recents shows every session regardless of deck/mode/category, with no
                        // grouping of its own to lean on — so unlike Study Hub, there's no section
                        // header that could already be identifying this tile's category or mode.
                        showCategoryNameIcon = true,
                        showModeName = true
                    )
                }
            }
        }
        }
    }

    showRestartDialog?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.restart_session_title),
            text = getText(R.string.restart_session_desc),
            onConfirm = {
                val reset = viewModel.restartSession(session)
                showRestartDialog = null
                pendingSessionId = reset.id
                pendingRoute = studyRouteFor(reset.mode)
                viewModel.resumeStudySession(reset)
            },
            onDismiss = { showRestartDialog = null }
        )
    }

    showDeleteDialog?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.delete_session_title),
            text = getText(R.string.delete_session_desc),
            onConfirm = { viewModel.deleteSession(session); showDeleteDialog = null },
            onDismiss = { showDeleteDialog = null }
        )
    }
}