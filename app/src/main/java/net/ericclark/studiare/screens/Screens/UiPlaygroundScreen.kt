package net.ericclark.studiare.screens.Screens
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
// Explicit, not just the layout.* wildcard above: UI_Components.* below also wildcard-imports a
// same-named custom FlowRow (DecksScreenLayouts.kt) — an explicit single-name import wins over
// both star imports and resolves the ambiguity in favor of the real Compose Foundation one.
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import net.ericclark.studiare.components.formatTimeAgo
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.screens.UI_Components.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish
import kotlin.math.roundToInt

/**
 * Debug-only sandbox for trying out in-progress UI components against live Compose rendering
 * (not a static mockup) before wiring them into real screens — reachable from Settings → Debug.
 * Reached the hard way: a session-card redesign shipped straight from an HTML mockup to real call
 * sites and turned out materially worse once actually running, costing a wasted round-trip this
 * screen exists to avoid next time. Each experiment is a self-contained composable below; add a
 * new one to [PlaygroundExperiment] and its `when` branch to extend.
 */
private enum class PlaygroundExperiment(val label: String) {
    SESSION_TILE("Session Tile")
}

@Composable
fun UiPlaygroundScreen(navController: NavController, viewModel: FlashcardViewModel) {
    var experiment by remember { mutableStateOf(PlaygroundExperiment.SESSION_TILE) }
    val dimensions = LocalStudiareDimensions.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.OTHER,
                title = { Text("UI Playground") },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (PlaygroundExperiment.entries.size > 1) {
                Box(modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)) {
                    TypedChipGrid(
                        items = PlaygroundExperiment.entries,
                        selected = experiment,
                        labelFor = { it.label },
                        onSelected = { experiment = it }
                    )
                }
            }
            when (experiment) {
                PlaygroundExperiment.SESSION_TILE -> SessionTilePlayground()
            }
        }
    }
}

@Composable
private fun SessionTilePlayground() {
    val dimensions = LocalStudiareDimensions.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    fun toast(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    // Option D's 5 independent title/subtitle toggles — see SessionTileOptionDPreview for how
    // they combine (which row shows names vs. time, when the short/long time forms are used).
    var showSessionName by remember { mutableStateOf(false) }
    var showDeckName by remember { mutableStateOf(false) }
    var showSetName by remember { mutableStateOf(false) }
    var showCategoryNameIcon by remember { mutableStateOf(false) }
    var showModeName by remember { mutableStateOf(false) }
    var isPinned by remember { mutableStateOf(false) }
    var category by remember { mutableStateOf(StudyCategory.PRACTICE) }
    val modesInCategory = remember(category) { modesForCategory(category) }
    var modeChoice by remember { mutableStateOf(SessionMode.FLASHCARD) }
    // Never lets `mode` sit on a value the current category doesn't offer, with no transient frame
    // where the dropdown's selection doesn't match any of its own options (a plain LaunchedEffect
    // fixup would have one, since it only reacts after a category-change recomposition).
    val mode = if (modeChoice in modesInCategory) modeChoice else modesInCategory.first()
    var showDifficulties by remember { mutableStateOf(true) }
    var isFlipped by remember { mutableStateOf(false) }
    var progressFraction by remember { mutableFloatStateOf(0.3f) }
    var sessionName by remember { mutableStateOf("") }
    var deckName by remember { mutableStateOf("Adjectives") }
    var setName by remember { mutableStateOf("Animals") }
    var frontText by remember { mutableStateOf("Resilient") }
    var backText by remember { mutableStateOf("Able to recover quickly from difficulties") }

    val totalCards = 40
    val currentIndex = (progressFraction * totalCards).roundToInt().coerceIn(0, totalCards)

    // Every mode-specific option (toggle, stepper, dropdown...) comes from the same registry Settings
    // → Mode Defaults and the session-creation dialog use (`modeOptionsFor`, screens/UI_Components/
    // ModeOptions.kt) — not hand-picked per mode, so every mode gets its full, real option set
    // automatically and stays correct as options are added/changed there.
    var modeOptionValues by remember(category, mode) { mutableStateOf(ModeDefaultSettings()) }
    val optionContext = remember(category, mode) {
        ModeOptionContext(maxForDifficulty = { 100 }, availableCardsCount = totalCards, category = category, mode = mode, deckDefaultPromptSide = CardSide.AUTO_BY_DECK)
    }
    val applicableOptions = remember(category, mode) { modeOptionsFor(category, mode) }

    val sampleSession = remember(mode, modeOptionValues, showDifficulties, isFlipped, currentIndex, isPinned, sessionName) {
        ActiveSession(
            id = "preview-session",
            deckId = "preview-deck",
            mode = mode,
            isWeighted = DifficultyWeighting.isWeighted(modeOptionValues),
            // 1,2,3,5 demonstrates the compressed range format ("Difficulty 1-3,5") with both a
            // run and a lone value in the same chip. This is the session's difficulty *filter*
            // (SelectionMode.DIFFICULTY), a different concept from the "Difficulty Weighting"
            // mode option below (how many cards per difficulty to pull when generating a session).
            difficulties = if (showDifficulties) listOf(1, 2, 3, 5) else emptyList(),
            totalCards = totalCards,
            currentCardIndex = currentIndex,
            furthestCardIndex = currentIndex,
            matchedPairs = if (mode == SessionMode.MEMORY || mode == SessionMode.MATCHING) List(currentIndex) { "pair-$it" } else emptyList(),
            quizPromptSide = PromptSide.valueIn(modeOptionValues, optionContext),
            isFlipped = isFlipped,
            allowMultipleGuesses = AllowMultipleGuesses.valueIn(modeOptionValues, optionContext),
            isPinned = isPinned,
            name = sessionName.ifBlank { null },
            lastAccessed = System.currentTimeMillis() - 2 * 60 * 60 * 1000
        )
    }
    val sampleCard = remember(frontText, backText) {
        Card(id = "preview-card", front = frontText, back = backText)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            // A darker, recessed canvas color (this app's established surfaceDim convention for
            // screen backgrounds/gutters) behind the preview card, so its edges read clearly
            // against the surrounding area instead of blending into a similarly-light tone.
            Surface(color = MaterialTheme.colorScheme.surfaceDim, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(dimensions.paddingLarge)) {
                    SessionTileOptionDPreview(
                        session = sampleSession,
                        card = sampleCard,
                        deckName = deckName,
                        setName = setName,
                        applicableOptions = applicableOptions,
                        modeOptionValues = modeOptionValues,
                        optionContext = optionContext,
                        onResume = { toast("Resume tapped") },
                        onCopy = { toast("Copy tapped") },
                        onRestart = { toast("Restart tapped") },
                        onDelete = { toast("Delete tapped") },
                        onPin = { name -> isPinned = true; sessionName = name; toast("Pinned as \"$name\"") },
                        onUnpin = { isPinned = false; toast("Unpinned") },
                        onRename = { name -> sessionName = name; toast("Renamed to \"$name\"") },
                        showSessionName = showSessionName,
                        showDeckName = showDeckName,
                        showSetName = showSetName,
                        showCategoryNameIcon = showCategoryNameIcon,
                        showModeName = showModeName
                    )
                }
            }
            HorizontalDivider()
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
            ) {
                PlaygroundSectionLabel("Title & Subtitle")
                SettingSwitchItem("Show Session Name", "Included in the title row's name list, if set", showSessionName) { showSessionName = it }
                SettingSwitchItem("Show Deck Name", "Included in the title row's name list, if set", showDeckName) { showDeckName = it }
                SettingSwitchItem("Show Set Name", "Included in the title row's name list, if set", showSetName) { showSetName = it }
                SettingSwitchItem("Show Category Name & Icon", "Category icon in the title row's leading slot, category name in the subtitle row", showCategoryNameIcon) { showCategoryNameIcon = it }
                SettingSwitchItem("Show Mode Name", "Mode name in the subtitle row", showModeName) { showModeName = it }

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Session")
                PlaygroundLabel("Category")
                TypedChipGrid(items = StudyCategory.entries, selected = category, labelFor = { it.asString() }, onSelected = { category = it })
                PlaygroundLabel("Mode")
                TypedChipGrid(items = modesInCategory, selected = mode, labelFor = { it.asString() }, onSelected = { modeChoice = it })
                SettingSwitchItem("Pinned", "Shows the pin badge and the overflow menu's \"Unpin\" item", isPinned) { isPinned = it }
                SettingSwitchItem("Has Difficulty Filter", "Shows the \"Difficulty\" chip (compressed range format) — the session's difficulty filter, not the Difficulty Weighting option below", showDifficulties) { showDifficulties = it }
                SettingSwitchItem("Flipped", "Preview shows the back face for flip-based modes", isFlipped) { isFlipped = it }

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Mode-Specific Options (${mode.asString()} · ${category.asString()}) — live, drives the chips above")
                applicableOptions.filter { it.isVisible(modeOptionValues) }.forEach { option ->
                    if (option.dialogSection) {
                        Text(getText(option.labelRes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(dimensions.spacingSmall))
                    }
                    option.Control(modeOptionValues, optionContext) { modeOptionValues = it }
                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingSmall))
                }

                PlaygroundSectionLabel("Progress")
                Text("$currentIndex / $totalCards cards", style = MaterialTheme.typography.bodyMedium)
                Slider(value = progressFraction, onValueChange = { progressFraction = it })

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Names")
                OutlinedTextField(value = sessionName, onValueChange = { sessionName = it }, label = { Text("Custom session name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = deckName, onValueChange = { deckName = it }, label = { Text("Deck name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = setName, onValueChange = { setName = it }, label = { Text("Set name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Sample Card")
                OutlinedTextField(value = frontText, onValueChange = { frontText = it }, label = { Text("Front") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = backText, onValueChange = { backText = it }, label = { Text("Back") }, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(dimensions.paddingLarge))
            }
        }
    }
}

/**
 * A preview-only copy of [net.ericclark.studiare.screens.UI_Components.SessionTile] — identical
 * except for the bottom-right settings area, redesigned per redesign Option D: no Graded line,
 * a single wrapping row of fit-content chips instead of two nested cards, every applicable
 * mode-specific chip shown at once (not one mutually-exclusive branch), and the difficulty chip
 * using a compressed range format ("Difficulty 1-3,5") instead of a flat comma list. Deliberately
 * duplicated rather than edited in place on the real `SessionTile` — this file exists specifically
 * so a redesign can be seen running before it touches production call sites again.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun SessionTileOptionDPreview(
    session: ActiveSession,
    card: Card?,
    deckName: String,
    setName: String,
    applicableOptions: List<ModeOption>,
    modeOptionValues: ModeDefaultSettings,
    optionContext: ModeOptionContext,
    onResume: () -> Unit,
    onCopy: () -> Unit,
    onRestart: () -> Unit,
    onDelete: () -> Unit,
    onPin: (name: String) -> Unit,
    onUnpin: () -> Unit,
    onRename: (name: String) -> Unit,
    showSessionName: Boolean,
    showDeckName: Boolean,
    showSetName: Boolean,
    showCategoryNameIcon: Boolean,
    showModeName: Boolean
) {
    val dimensions = LocalStudiareDimensions.current
    var showMenu by remember { mutableStateOf(false) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

    if (showPinDialog) {
        PinNamingDialog(
            defaultName = defaultSessionPinName(context, session.mode, deckName),
            onDismiss = { showPinDialog = false },
            onConfirm = { name -> onPin(name); showPinDialog = false }
        )
    }

    if (showRenameDialog) {
        RenameSessionDialog(
            currentName = session.name ?: "",
            onDismiss = { showRenameDialog = false },
            onConfirm = { name -> onRename(name); showRenameDialog = false }
        )
    }

    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    if (showInfoDialog) {
        SessionInfoDialog(session = session, onDismiss = { showInfoDialog = false })
    }

    ElevatedCard(
        onClick = onResume,
        modifier = Modifier
            .fillMaxWidth()
            .pressSquish(interactionSource)
            .border(if (isFocused) 6.dp else 0.dp, borderColor, RoundedCornerShape(dimensions.cornerRadiusMedium)),
        interactionSource = interactionSource,
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation, pressedElevation = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
            val completedCrosswordCount = remember(session.crosswordWords, session.crosswordUserInputs) {
                completedCrosswordWordCount(session)
            }

            // Option D: "12 / 40 cards" style, dynamically singular/plural on the total — shown
            // below the divider, above the progress bar (see the right-hand Column further down),
            // not in the header anymore. Hoisted above the header Row (rather than declared
            // inside it, as the old single-use `progressText` was) since it's needed there too.
            val progressText = when (session.mode) {
                SessionMode.MEMORY -> formatOptionDProgressText(session.matchedPairs.size, session.totalCards, "pair", "pairs")
                SessionMode.MATCHING -> formatOptionDProgressText(session.matchedPairs.size, session.totalCards, "pair", "pairs")
                SessionMode.CROSSWORD -> formatOptionDProgressText(completedCrosswordCount, session.crosswordWords.size, "word", "words")
                SessionMode.WORD_SEARCH -> formatOptionDProgressText(session.wordSearchFoundWordIds.size, session.wordSearchWords.size, "word", "words")
                else -> formatOptionDProgressText(session.currentCardIndex, session.totalCards, "card", "cards")
            }

            // --- TITLE / SUBTITLE (Option D): the top splits into two rows. Title content is
            // governed by the session/deck/set-name toggles; subtitle by the category/mode
            // toggles, with time filling whichever of the two has nothing else to show — never
            // both at once. See the long explanation this was worked out against in the plan
            // file's "Debug UI Playground" section.
            val headerIconSize = 20.dp

            // Only the on-and-non-blank names are included, in this fixed order.
            val titleNameParts = buildList {
                if (showSessionName && !session.name.isNullOrBlank()) add(session.name)
                if (showDeckName && deckName.isNotBlank()) add(deckName)
                if (showSetName && setName.isNotBlank()) add(setName)
            }
            val titleShowsTime = titleNameParts.isEmpty()
            val titleText = if (titleShowsTime) formatTimeAgoWritten(session.lastAccessed) else titleNameParts.joinToString(" · ")

            val categoryOrModeShown = showCategoryNameIcon || showModeName
            val subtitleNameParts = buildList {
                if (showCategoryNameIcon) add(session.displayCategory().asString())
                if (showModeName) add(session.mode.asString())
            }
            // Time never appears in both rows: the subtitle only gets a time of its own (long
            // form, standing in alone) when the title didn't already take the "time" slot; and
            // even when category/mode are shown, the short-form time tacked onto their list is
            // skipped if the title is already showing it.
            val subtitleText: String? = when {
                categoryOrModeShown -> (subtitleNameParts + if (titleShowsTime) emptyList() else listOf(formatTimeAgo(session.lastAccessed))).joinToString(" · ")
                !titleShowsTime -> formatTimeAgoWritten(session.lastAccessed)
                else -> null
            }

            // --- TITLE ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    // Pin icon — only composed (and only takes up space) when the session is
                    // actually pinned, not reserved ahead of time.
                    if (session.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = getText(R.string.pinned_label),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(headerIconSize)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee()
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TooltipIconButton(description = getText(R.string.session_info), onClick = { showInfoDialog = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Info, contentDescription = getText(R.string.session_info), tint = MaterialTheme.colorScheme.secondary)
                    }
                    Box {
                        TooltipIconButton(description = getText(R.string.session_options), onClick = { showMenu = true }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.session_options))
                        }
                        DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                            DropdownMenuItem(text = { Text(getText(R.string.copy)) }, leadingIcon = { Icon(Icons.Default.ContentCopy, null) }, onClick = { onCopy(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                            DropdownMenuItem(text = { Text(getText(R.string.restart)) }, leadingIcon = { Icon(Icons.Default.RestartAlt, null) }, onClick = { onRestart(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                            DropdownMenuItem(
                                text = { Text("Rename") },
                                leadingIcon = { Icon(Icons.Default.Edit, null) },
                                onClick = { showMenu = false; showRenameDialog = true },
                                modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                            )
                            DropdownMenuItem(
                                text = { Text(if (session.isPinned) getText(R.string.unpin) else getText(R.string.pin)) },
                                leadingIcon = { Icon(if (session.isPinned) Icons.Default.PushPin else Icons.Outlined.PushPin, null) },
                                onClick = {
                                    showMenu = false
                                    if (session.isPinned) onUnpin() else showPinDialog = true
                                },
                                modifier = Modifier.defaultMinSize(minHeight = 56.dp)
                            )
                            DropdownMenuItem(text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) }, leadingIcon = { Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) }, onClick = { onDelete(); showMenu = false }, modifier = Modifier.defaultMinSize(minHeight = 56.dp))
                        }
                    }
                }
            }

            // --- SUBTITLE ROW --- category icon (if shown) + subtitle text, neither reserved
            // ahead of time. Skipped entirely, along with its spacing above, when there's nothing
            // to show (subtitleText is only ever null when the category icon is also off — see
            // subtitleText's computation above — so there's genuinely nothing in this row then).
            if (subtitleText != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    // Category icon — only composed (and only takes up space) when it's actually
                    // shown, not reserved ahead of time.
                    if (showCategoryNameIcon) {
                        Icon(
                            imageVector = sessionModeIcon(session),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(headerIconSize)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                    Text(
                        text = subtitleText,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // --- DIVIDER (Option D: the progress bar used to live here; it's now to the right
            // of the preview, above the chips, and this full-width divider takes its old spot) ---
            HorizontalDivider()

            Spacer(modifier = Modifier.height(16.dp))

            // --- BOTTOM CONTENT: Preview (unchanged) + chip-based settings (Option D) ---
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
                val sharedTransitionScope = LocalSharedTransitionScope.current
                val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

                Box(
                    modifier = Modifier
                        .width(100.dp)
                        .height(120.dp)
                        .then(
                            if (sharedTransitionScope != null && animatedVisibilityScope != null) {
                                with(sharedTransitionScope) {
                                    Modifier.sharedBounds(
                                        sharedContentState = rememberSharedContentState(key = "playground_session_card_${session.id}"),
                                        animatedVisibilityScope = animatedVisibilityScope,
                                        resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    if (session.mode == SessionMode.MEMORY) {
                        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(dimensions.cornerRadiusSmall), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            Column(modifier = Modifier.fillMaxSize().padding(4.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(2) { r -> Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp)) { repeat(2) { c -> Box(modifier = Modifier.weight(1f).fillMaxHeight().background(if ((r + c) % 2 == 0) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer, RoundedCornerShape(2.dp))) } } }
                            }
                        }
                    } else if (session.mode == SessionMode.CROSSWORD) {
                        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(dimensions.cornerRadiusSmall), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                            val activeCells = remember(session.crosswordWords) { val cells = mutableSetOf<Pair<Int, Int>>(); session.crosswordWords.forEach { word -> for (i in word.word.indices) { cells.add((if (word.isAcross) word.startX + i else word.startX) to (if (word.isAcross) word.startY else word.startY + i)) } }; cells }
                            val cellColor = MaterialTheme.colorScheme.primaryContainer
                            Canvas(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                                if (session.crosswordGridWidth > 0 && session.crosswordGridHeight > 0) {
                                    val gw = session.crosswordGridWidth.toFloat(); val gh = session.crosswordGridHeight.toFloat()
                                    val cellSize = kotlin.math.min(size.width / gw, size.height / gh)
                                    val offsetX = (size.width - (cellSize * gw)) / 2; val offsetY = (size.height - (cellSize * gh)) / 2
                                    activeCells.forEach { (x, y) -> drawRect(color = cellColor, topLeft = Offset(offsetX + (x * cellSize), offsetY + (y * cellSize)), size = androidx.compose.ui.geometry.Size(cellSize - 2f, cellSize - 2f)) }
                                }
                            }
                        }
                    } else {
                        Card(modifier = Modifier.fillMaxSize(), shape = RoundedCornerShape(dimensions.cornerRadiusSmall), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
                            val resolvedPromptSide = card?.let { resolvePromptSide(session.quizPromptSide, it, session.resolvedAutoByDeckSide) } ?: session.quizPromptSide
                            val cardColor = when (session.mode) {
                                SessionMode.TYPING_SCORED, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN -> if (resolvedPromptSide == CardSide.BACK) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                                else -> if (session.isFlipped) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer
                            }
                            Box(modifier = Modifier.fillMaxSize().background(cardColor).padding(8.dp), contentAlignment = Alignment.Center) {
                                if (card != null) {
                                    val textToShow = when (session.mode) {
                                        SessionMode.TYPING_SCORED, SessionMode.TYPING, SessionMode.LIST, SessionMode.ANAGRAM, SessionMode.HANGMAN, SessionMode.CROSSWORD -> if (resolvedPromptSide == CardSide.BACK) card.back else card.front
                                        else -> if (session.isFlipped) card.back else card.front
                                    }
                                    Text(text = textToShow, textAlign = TextAlign.Center, maxLines = 4, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                // Settings area — two independently horizontally-scrollable rows of fit-content
                // chips instead of one wrapping row, every applicable mode-specific option shown
                // at once (not a mutually-exclusive if/else chain, and not a hand-picked subset),
                // no Graded chip. The option list and their live values are the same
                // registry-driven ones Settings → Mode Defaults uses, so this is complete and
                // accurate for every mode, not just the few modes a hardcoded chip list would
                // have remembered to cover. Chips alternate down each row between the two
                // accent colors, and the two rows start on opposite colors from each other, per
                // the user-requested layout.
                val chipData = buildList {
                    applicableOptions.filter { it.isVisible(modeOptionValues) }.forEach { option ->
                        add(modeOptionChipData(option, modeOptionValues, optionContext))
                    }
                    if (session.difficulties.isNotEmpty()) {
                        add(
                            PlaygroundChipData(
                                text = "${getText(R.string.difficulty)} ${formatDifficultyRanges(session.difficulties)}",
                                on = true
                            )
                        )
                    }
                }
                val chipRow1 = chipData.filterIndexed { index, _ -> index % 2 == 0 }
                val chipRow2 = chipData.filterIndexed { index, _ -> index % 2 == 1 }
                // Fixed to the same 120.dp height as the preview box so the bottom of the last
                // chip row lands exactly on the preview's bottom edge. No `spacedBy` here — the
                // two `Spacer(Modifier.weight(1f))`s below split whatever vertical room is left
                // after the text/bar/chip rows evenly between "above row 1" and "below row 1",
                // which both pins row 2 to the bottom and keeps row 1 evenly spaced between the
                // progress bar and row 2 as requested.
                Column(modifier = Modifier.weight(1f).height(120.dp)) {
                    // --- PROGRESS TEXT (Option D: "12 / 40 cards", left-aligned, directly above
                    // the progress bar — this is the text that used to occupy the header's title
                    // slot, computed above as `progressText`) ---
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    // --- PROGRESS BAR (Option D: moved here, to the right of the preview and
                    // above the chips — a `HorizontalDivider()` above now sits where this used
                    // to be, full-width above the preview/chips row) ---
                    val progressValue = sessionProgressFraction(session)
                    LinearProgressIndicator(
                        progress = { progressValue },
                        modifier = Modifier.fillMaxWidth().height(8.dp).clip(CircleShape),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        strokeCap = StrokeCap.Round
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    PlaygroundChipRow(chipRow1, startWithSecondaryColor = true)
                    Spacer(modifier = Modifier.weight(1f))
                    PlaygroundChipRow(chipRow2, startWithSecondaryColor = false)
                }
            }
        }
    }
}

/**
 * A minimal rename dialog, structurally mirroring [PinNamingDialog] but with its own title/button
 * text (not reused as-is — that dialog's copy is pin-specific, "Pin and name"/"Pin") and prefilled
 * with the session's current name rather than an empty field with a placeholder, since renaming an
 * already-named session should show what it's currently called.
 */
@Composable
private fun RenameSessionDialog(currentName: String, onDismiss: () -> Unit, onConfirm: (name: String) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    var typed by remember { mutableStateOf(currentName) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                Text("Rename Session", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingMedium))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    label = { Text("Session name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.cancel))
                    }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(onClick = { onConfirm(typed.trim()) }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

/** One chip's resolved display text and on/off state, computed once per [ModeOption] so it can be laid out (split across rows, colored) before being rendered. */
private data class PlaygroundChipData(val text: String, val on: Boolean)

/**
 * Chip-only shortened labels, keyed by [ModeOption.id]. These are display overrides for this
 * sandbox screen's compact chip format only — they do NOT touch the real string resources, which
 * stay full-length everywhere they're actually used (Settings → Mode Defaults, session creation,
 * and this same screen's own live "Mode-Specific Options" controls below, which call
 * [getText]`(option.labelRes)` directly rather than through this map). Falls back to the real,
 * full-length label for any option not listed here.
 */
private val playgroundChipShortLabels: Map<String, String> = mapOf(
    "NumberOfAnswers" to "Answers",
    "ShowCorrectLetters" to "Correct Letters",
    "MaxMemoryTiles" to "Tiles",
    "GridDensity" to "Density",
    "ShowCorrectWords" to "Correct Words",
    "FreeformLayout" to "Vertical",
    "DifficultyWeighting" to "Weighted",
    "AudioPlaybackSpeed" to "Speed",
    "AudioReplayCount" to "Replays",
    "AudioAnswerDelay" to "Answer Delay",
    "AudioNextCardDelay" to "Next Delay",
    "FreeformShowBothSides" to "Both Sides",
    "FreeformSwipeNavigation" to "Swipe Nav",
    "TypingIgnoreFormatting" to "Ignore Format",
    "TypingDisableAutocorrect" to "No Autocorrect",
    "TypingShowLengthHint" to "Length Hint",
    "TypedListenMultipleChoice" to "Multiple Choice",
    "HearMatchAutoPlayAll" to "Auto Play",
    "HideDifficultySelection" to "Hide Difficulty",
    "FlashcardDoubleTap" to "Double Tap",
    "FlashcardRandomizeSide" to "Random Prompt",
    "AnagramFirstLetterHint" to "Letter Hint",
    "PromptSide" to "Prompt",
    "MemoryGridColumnsPortrait" to "Portrait Cols",
    "MemoryGridColumnsLandscape" to "Landscape Cols",
    "MemoryWrongPair" to "Wrong Delay",
    "MemoryCorrectPair" to "Correct Delay",
    "RequireConfirmTap" to "Confirm Tap",
    "AutoAdvanceDelay" to "Advance Delay",
    "MatchingWrongDelay" to "Wrong Delay",
    "MatchingCorrectHighlight" to "Correct Highlight",
    "MatchingHighlight" to "Highlight",
    "AllowMultipleGuesses" to "Multi Guess",
    "MatchingAllowMultipleGuesses" to "Multi Guess",
    "CrosswordAutoAdvanceCell" to "Advance Cell",
    "ListenIncorrectSound" to "Wrong Sound"
)

/** Looks up [option]'s shortened chip label, falling back to its real full-length [ModeOption.labelRes] text. */
@Composable
private fun chipLabel(option: ModeOption): String = playgroundChipShortLabels[option.id] ?: getText(option.labelRes)

/** Shortened chip-only display for a [CardSide] value — e.g. "Auto by Deck" -> "Auto Deck" — distinct from [CardSide.asString], which stays full-length everywhere else it's used. */
private fun promptSideChipValue(side: CardSide): String = when (side) {
    CardSide.FRONT -> "Front"
    CardSide.BACK -> "Back"
    CardSide.AUTO_BY_DECK -> "Auto Deck"
    CardSide.AUTO_BY_CARD -> "Auto Card"
}

/**
 * Resolves one [ModeOption] to its chip text/on-state, dispatched by its concrete type since each
 * one's value has a different shape (boolean toggle, enum, numeric). Every applicable option gets a
 * chip regardless of its value — a boolean one stays visible but switches to a muted outlined style
 * when off (see [PlaygroundSettingsChip]'s `on` param), so the full option set for the selected mode
 * is always countable at a glance (e.g. exactly 6 for Practice → Flashcard), not just whichever
 * happen to be on right now. Every plain boolean option — the large majority, all [SwitchModeOption]
 * subclasses — is handled by one generic branch; everything else gets its own branch so its value
 * can be shown (PromptSide's chosen side, a stepper's current number, etc.). The `else` fallback
 * (label only, always "on"-styled) means a brand-new option type added later still shows *something*
 * here rather than silently vanishing.
 */
@Composable
private fun modeOptionChipData(option: ModeOption, values: ModeDefaultSettings, context: ModeOptionContext): PlaygroundChipData {
    return when (option) {
        is SwitchModeOption -> PlaygroundChipData(chipLabel(option), option.valueIn(values))
        PromptSide -> PlaygroundChipData("${chipLabel(option)}: ${promptSideChipValue(PromptSide.valueIn(values, context))}", true)
        DifficultyWeighting -> PlaygroundChipData(chipLabel(option), DifficultyWeighting.isWeighted(values))
        ShowCorrectLetters -> PlaygroundChipData(chipLabel(option), ShowCorrectLetters.valueIn(values, context.category))
        TypingShowLengthHint -> PlaygroundChipData(chipLabel(option), TypingShowLengthHint.valueIn(values, context))
        RequireConfirmTap -> PlaygroundChipData(chipLabel(option), RequireConfirmTap.valueIn(values, context))
        AllowMultipleGuesses -> PlaygroundChipData(chipLabel(option), AllowMultipleGuesses.valueIn(values, context))
        MatchingAllowMultipleGuesses -> PlaygroundChipData(chipLabel(option), MatchingAllowMultipleGuesses.valueIn(values, context))
        FlashcardAutoFlip -> PlaygroundChipData(chipLabel(option), FlashcardAutoFlip.valueIn(values))
        NumberOfAnswers -> PlaygroundChipData("${chipLabel(option)}: ${NumberOfAnswers.valueIn(values)}", true)
        MaxMemoryTiles -> PlaygroundChipData("${chipLabel(option)}: ${MaxMemoryTiles.valueIn(values)}", true)
        GridDensity -> PlaygroundChipData("${chipLabel(option)}: ${GridDensity.valueIn(values)}", true)
        AudioPlaybackSpeed -> PlaygroundChipData("${chipLabel(option)}: ${AudioPlaybackSpeed.valueIn(values)}x", true)
        AudioReplayCount -> PlaygroundChipData("${chipLabel(option)}: ${AudioReplayCount.valueIn(values)}", true)
        AudioAnswerDelay -> PlaygroundChipData("${chipLabel(option)}: ${AudioAnswerDelay.valueIn(values)}s", true)
        AudioNextCardDelay -> PlaygroundChipData("${chipLabel(option)}: ${AudioNextCardDelay.valueIn(values)}s", true)
        FreeformSwipeNavigation -> PlaygroundChipData("${chipLabel(option)}: ${FreeformSwipeNavigation.valueIn(values)}", true)
        HangmanMaxMistakes -> PlaygroundChipData("${chipLabel(option)}: ${HangmanMaxMistakes.valueIn(values)}", true)
        MemoryPeek -> PlaygroundChipData("${chipLabel(option)}: ${MemoryPeek.valueIn(values)}s", true)
        MemoryGridColumnsPortrait -> PlaygroundChipData("${chipLabel(option)}: ${MemoryGridColumnsPortrait.valueIn(values)}", true)
        MemoryGridColumnsLandscape -> PlaygroundChipData("${chipLabel(option)}: ${MemoryGridColumnsLandscape.valueIn(values)}", true)
        MemoryWrongPair -> PlaygroundChipData("${chipLabel(option)}: ${MemoryWrongPair.valueIn(values)}ms", true)
        MemoryCorrectPair -> PlaygroundChipData("${chipLabel(option)}: ${MemoryCorrectPair.valueIn(values)}ms", true)
        WordSearchHighlightColor -> PlaygroundChipData("${chipLabel(option)}: ${WordSearchHighlightColor.valueIn(values)}", true)
        AutoAdvanceDelay -> PlaygroundChipData("${chipLabel(option)}: ${AutoAdvanceDelay.valueIn(values)}s", true)
        MatchingWrongDelay -> PlaygroundChipData("${chipLabel(option)}: ${MatchingWrongDelay.valueIn(values)}ms", true)
        MatchingCorrectHighlight -> PlaygroundChipData("${chipLabel(option)}: ${MatchingCorrectHighlight.valueIn(values)}ms", true)
        MatchingHighlight -> PlaygroundChipData("${chipLabel(option)}: ${MatchingHighlight.valueIn(values)}", true)
        SpeakingFrontSpeed -> PlaygroundChipData("${chipLabel(option)}: ${SpeakingFrontSpeed.valueIn(values)}x", true)
        SpeakingBackSpeed -> PlaygroundChipData("${chipLabel(option)}: ${SpeakingBackSpeed.valueIn(values)}x", true)
        else -> PlaygroundChipData(chipLabel(option), true)
    }
}

/**
 * One horizontally-scrollable row of chips (independent scroll from the other row), alternating
 * between the secondary and tertiary accent colors. [startWithSecondaryColor] controls which color
 * the row's first chip uses, so the two rows rendered side-by-side (vertically stacked, really) start
 * on opposite colors from each other.
 */
@Composable
private fun PlaygroundChipRow(chips: List<PlaygroundChipData>, startWithSecondaryColor: Boolean) {
    if (chips.isEmpty()) return
    // A no-op clickable (indication = null) consumes the initial pointer-down itself, which
    // stops the ancestor ElevatedCard's own onClick/pressSquish from registering a press for
    // touches that start here — without it, starting a horizontal scroll drag on this row made
    // the whole card flash its press/squish state, since Compose's tap detector fires Press on
    // pointer-down before it knows the gesture will turn into a scroll. The nested
    // horizontalScroll still works normally: Compose's scrollable gesture detection doesn't
    // require an unconsumed down event, only the ancestor's tap detector does.
    val blockParentPressInteractionSource = remember { MutableInteractionSource() }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(interactionSource = blockParentPressInteractionSource, indication = null) {}
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        chips.forEachIndexed { index, chip ->
            val useSecondary = if (startWithSecondaryColor) index % 2 == 0 else index % 2 != 0
            if (useSecondary) {
                PlaygroundSettingsChip(chip.text)
            } else {
                PlaygroundSettingsChip(
                    text = chip.text,
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            }
        }
    }
}

/**
 * A fit-content, always-filled pill chip. Every applicable mode option gets one of these
 * regardless of its current value — the on/off state used to switch between a filled and a muted
 * outlined style, but per feedback every chip should read as solid-filled with its row's
 * alternating color, so the full option set stays equally legible at a glance whether on or off.
 */
@Composable
private fun PlaygroundSettingsChip(
    text: String,
    containerColor: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColor: Color = MaterialTheme.colorScheme.onSecondaryContainer
) {
    Box(
        modifier = Modifier
            .background(containerColor, RoundedCornerShape(50))
            .padding(horizontal = 9.dp, vertical = 4.dp)
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = contentColor, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Option D's progress-text format: "12 / 40 cards" rather than the real app's "Progress: 12 / 40"
 * — dynamically singular/plural on the total, e.g. `(0, 1, "card", "cards")` -> `"0 / 1 card"`.
 * Playground-only wording; doesn't touch the real `progress_format`/`pairs_progress_format`/etc.
 * string resources, which stay as-is everywhere else they're used.
 */
private fun formatOptionDProgressText(current: Int, total: Int, unitSingular: String, unitPlural: String): String =
    "$current / $total ${if (total == 1) unitSingular else unitPlural}"

/**
 * Written-out version of [formatTimeAgo] (e.g. "3h" -> "3 hours ago"), used only for the header
 * title slot when the mode label isn't shown (see [SessionTileOptionDPreview]) — playground-only,
 * doesn't touch the real, compact [formatTimeAgo] used everywhere else including this same
 * screen's own top-right time-ago text when the mode label IS shown.
 */
private fun formatTimeAgoWritten(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    val seconds = ((now - timestamp) / 1000).coerceAtLeast(0)
    val minutes = seconds / 60
    val hours = minutes / 60
    val days = hours / 24
    return when {
        days >= 365 -> formatAgoUnit(days / 365, "year")
        days >= 30 -> formatAgoUnit(days / 30, "month")
        days >= 1 -> formatAgoUnit(days, "day")
        hours >= 1 -> formatAgoUnit(hours, "hour")
        minutes >= 1 -> formatAgoUnit(minutes, "minute")
        else -> "Just now"
    }
}

private fun formatAgoUnit(amount: Long, unit: String): String = "$amount $unit${if (amount == 1L) "" else "s"} ago"

/**
 * Compresses a sorted-or-not list of small ints into a comma-joined run-length string, e.g.
 * `[1, 2, 3, 5]` -> `"1-3,5"`. Used for the Option D difficulty chip's compact label.
 */
private fun formatDifficultyRanges(values: List<Int>): String {
    val sorted = values.distinct().sorted()
    if (sorted.isEmpty()) return ""
    val ranges = mutableListOf<String>()
    var start = sorted[0]
    var end = sorted[0]
    for (i in 1 until sorted.size) {
        if (sorted[i] == end + 1) {
            end = sorted[i]
        } else {
            ranges.add(if (start == end) "$start" else "$start-$end")
            start = sorted[i]
            end = sorted[i]
        }
    }
    ranges.add(if (start == end) "$start" else "$start-$end")
    return ranges.joinToString(",")
}

@Composable
private fun PlaygroundSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
    )
}

@Composable
private fun PlaygroundLabel(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
}
