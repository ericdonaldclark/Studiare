package net.ericclark.studiare.screens.Screens
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.screens.UI_Components.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
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
    SESSION_TILE("Session Tile"),
    SESSION_PRESET_CARD("Preset Card")
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
                PlaygroundExperiment.SESSION_PRESET_CARD -> SessionPresetCardPlayground()
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

    // The two toggles SessionTile itself takes — see its own doc comment for how they combine
    // with the session's own name (always shown in the title when set) to decide what the
    // title/subtitle rows show.
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
            // 1,2,3,5 demonstrates the compressed range format ("Difficulty 1-3,5") with both a
            // run and a lone value in the same chip. This is the session's difficulty *filter*
            // (SelectionMode.DIFFICULTY), a different concept from the "Difficulty Weighting"
            // mode option below (how many cards per difficulty to pull when generating a session).
            difficulties = if (showDifficulties) listOf(1, 2, 3, 5) else emptyList(),
            totalCards = totalCards,
            currentCardIndex = currentIndex,
            furthestCardIndex = currentIndex,
            matchedPairs = if (mode == SessionMode.MEMORY || mode == SessionMode.MATCHING) List(currentIndex) { "pair-$it" } else emptyList(),
            isFlipped = isFlipped,
            isPinned = isPinned,
            name = sessionName.ifBlank { null },
            lastAccessed = System.currentTimeMillis() - 2 * 60 * 60 * 1000
            // Every mode-specific field (isWeighted, quizPromptSide, allowMultipleGuesses, etc.)
            // comes from the live-edited `modeOptionValues` below via `applyModeDefaults` — the
            // real `SessionTile` now derives its chips from the session's own fields
            // (`toModeDefaultSettings`), so the "Mode-Specific Options" controls have to land on
            // this session, not on a separately-tracked settings object, to actually show up.
        ).applyModeDefaults(modeOptionValues)
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
                    // The real production component, not a duplicate — now that SessionTile
                    // itself supports this design (ported from here), the playground's job is to
                    // exercise it live rather than keep a parallel copy in sync.
                    SessionTile(
                        session = sampleSession,
                        card = sampleCard,
                        deckName = deckName,
                        onResume = { toast("Resume tapped") },
                        onCopy = { toast("Copy tapped") },
                        onRestart = { toast("Restart tapped") },
                        onDelete = { toast("Delete tapped") },
                        onPin = { name -> isPinned = true; sessionName = name; toast("Pinned as \"$name\"") },
                        onUnpin = { isPinned = false; toast("Unpinned") },
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
                SettingSwitchItem("Show Category Name & Icon", "Category icon in the subtitle row's leading slot, category name in the subtitle text", showCategoryNameIcon) { showCategoryNameIcon = it }
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
                OutlinedTextField(value = sessionName, onValueChange = { sessionName = it }, label = { Text("Custom session name — shown in the title when set") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = deckName, onValueChange = { deckName = it }, label = { Text("Deck name — only used for the pin dialog's suggested name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)

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
 * "Create session" shortcut, meant for Recents: a small, always-pinned preset — mode/category
 * plus whatever mode-specific options were configured — that creates a real session (with those
 * settings) only once tapped, rather than being a session itself. Design worked out as a static
 * mockup first (see the plan file); this is the live Compose version, still playground-only
 * ("for now") — not yet wired into Recents or a real creation flow.
 */
@Composable
private fun SessionPresetCardPlayground() {
    val dimensions = LocalStudiareDimensions.current

    var showTitle by remember { mutableStateOf(true) }
    var category by remember { mutableStateOf(StudyCategory.PRACTICE) }
    val modesInCategory = remember(category) { modesForCategory(category) }
    var modeChoice by remember { mutableStateOf(SessionMode.FLASHCARD) }
    val mode = if (modeChoice in modesInCategory) modeChoice else modesInCategory.first()
    var showDifficulties by remember { mutableStateOf(true) }
    var presetName by remember { mutableStateOf("") }
    var deckName by remember { mutableStateOf("Adjectives") }

    val totalCards = 40

    // Same registry-driven option set as SessionTilePlayground above, reused here so the chips
    // stay driven by the real per-mode option list rather than a hand-picked subset.
    var modeOptionValues by remember(category, mode) { mutableStateOf(ModeDefaultSettings()) }
    val optionContext = remember(category, mode) {
        ModeOptionContext(maxForDifficulty = { 100 }, availableCardsCount = totalCards, category = category, mode = mode, deckDefaultPromptSide = CardSide.AUTO_BY_DECK)
    }
    val applicableOptions = remember(category, mode) { modeOptionsFor(category, mode) }

    // A preset has no "last accessed" moment of its own — unlike SessionTile, its title never
    // falls back to a written-out time, only ever the preset's own name or the deck it studies.
    val sampleSession = remember(mode, modeOptionValues, showDifficulties) {
        ActiveSession(
            id = "preview-preset",
            deckId = "preview-deck",
            mode = mode,
            difficulties = if (showDifficulties) listOf(1, 2, 3, 5) else emptyList(),
            totalCards = totalCards,
            isPinned = true
        ).applyModeDefaults(modeOptionValues)
    }

    val titleText = presetName.ifBlank { deckName }
    val subtitleText = "${category.asString()} · ${mode.asString()}"

    // Not wrapped in `remember` — it calls @Composable helpers (sessionTileChipDataFor,
    // getText), same as the real SessionTile's identical chip-building block does, inline in
    // the composable body rather than inside a non-composable lambda.
    val priorityChips = buildList {
        val fill = mutableListOf<SessionTileChipData>()
        applicableOptions.filter { it.isVisible(modeOptionValues) }.forEach { option ->
            val actual = sessionTileChipDataFor(option, modeOptionValues, optionContext)
            val default = sessionTileChipDataFor(option, ModeDefaultSettings(), optionContext)
            if (actual != default) add(actual) else fill.add(default)
        }
        if (sampleSession.difficulties.isNotEmpty()) {
            val difficultyChip = SessionTileChipData("${getText(R.string.difficulty)} ${formatDifficultyRanges(sampleSession.difficulties)}", true)
            if (sampleSession.difficulties.toSet() == setOf(1, 2, 3, 4, 5)) fill.add(difficultyChip) else add(difficultyChip)
        }
        addAll(fill.sortedBy { it.text.length })
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Surface(color = MaterialTheme.colorScheme.surfaceDim, modifier = Modifier.fillMaxWidth()) {
                Box(modifier = Modifier.padding(dimensions.paddingLarge)) {
                    SessionPresetCard(
                        title = titleText,
                        subtitle = subtitleText,
                        subtitleIcon = sessionModeIcon(sampleSession),
                        showTitle = showTitle,
                        chipData = priorityChips,
                        onCreateSession = {},
                        onEditSettings = {}
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
                SettingSwitchItem("Show Title", "Hides just the title line and shrinks the card accordingly — the subtitle (category/mode) stays, since it's the only thing identifying the preset once the title's gone", showTitle) { showTitle = it }

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Session")
                PlaygroundLabel("Category")
                TypedChipGrid(items = StudyCategory.entries, selected = category, labelFor = { it.asString() }, onSelected = { category = it })
                PlaygroundLabel("Mode")
                TypedChipGrid(items = modesInCategory, selected = mode, labelFor = { it.asString() }, onSelected = { modeChoice = it })
                SettingSwitchItem("Has Difficulty Filter", "Shows the \"Difficulty\" chip (compressed range format)", showDifficulties) { showDifficulties = it }

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

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Names")
                OutlinedTextField(value = presetName, onValueChange = { presetName = it }, label = { Text("Preset name — shown in the title when set, else the deck name (never a time)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = deckName, onValueChange = { deckName = it }, label = { Text("Deck name — the title's fallback") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                Spacer(Modifier.height(dimensions.paddingLarge))
            }
        }
    }
}

/**
 * Not yet a real app component — "for now" lives only in the playground above (see that
 * composable's doc comment). A compact, always-pinned preset tile for Recents: tapping it will
 * create and start a session with these settings rather than resuming one, so it skips
 * everything about an in-progress session (preview thumbnail, progress text/bar, divider) that
 * [SessionTile] shows. The right-hand quarter — split off by [VerticalDivider] — holds the two
 * actions a preset actually needs: create the session now, or edit its settings.
 */
@Composable
private fun SessionPresetCard(
    title: String,
    subtitle: String,
    subtitleIcon: androidx.compose.ui.graphics.vector.ImageVector,
    showTitle: Boolean,
    chipData: List<SessionTileChipData>,
    onCreateSession: () -> Unit,
    onEditSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    val density = androidx.compose.ui.platform.LocalDensity.current
    // The divider/button column need to match the left column's height, but that left column
    // contains SessionTileChipGrid, which is built on BoxWithConstraints (a SubcomposeLayout) —
    // Modifier.height(IntrinsicSize.Min) crashes outright on those ("Asking for intrinsic
    // measurements of SubcomposeLayout layouts is not supported"), so the height is captured from
    // a real layout pass instead, the same way CreateStudySessionScreen.kt's cardCountFooterHeight
    // already does for an unrelated footer.
    var leftColumnHeight by remember { mutableStateOf(0.dp) }
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Row(
            modifier = Modifier.padding(dimensions.paddingMedium),
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
        ) {
            // Left 3/4: title + subtitle (optional), then chips — capped at 2 rows by
            // SessionTileChipGrid itself.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .onGloballyPositioned { leftColumnHeight = with(density) { it.size.height.toDp() } }
            ) {
                if (showTitle) {
                    TextWithOverflowTooltip(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(subtitleIcon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(subtitle, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(14.dp))
                SessionTileChipGrid(chipData, modifier = Modifier.fillMaxWidth())
            }

            // Both buttons are sized off the same leftColumnHeight-derived value, so hiding the
            // title shrinks them by the same amount instead of the first staying full-size while
            // the second one alone gets squeezed into an oval.
            //
            // The button COLUMN's width, though, is a fixed constant, not derived from that same
            // height — letting it vary with leftColumnHeight (as a first version of this did, by
            // sizing the column to the buttons themselves) created a layout feedback loop: a
            // narrower/wider button column changes how much width Row hands the weighted left
            // column, which changes how SessionTileChipGrid packs its chips, which can change the
            // left column's height, which feeds back into button size again — visible on-device as
            // several frames of chips re-wrapping mid-toggle before things settled. Fixing the
            // column's width breaks that loop: changing leftColumnHeight now only ever changes the
            // right side's *height*, never the left column's available width.
            val buttonGap = 12.dp
            val buttonColumnWidth = 64.dp
            val buttonSize = ((leftColumnHeight - buttonGap) / 2).coerceIn(0.dp, buttonColumnWidth)
            val buttonShape = RoundedCornerShape(dimensions.cornerRadiusSmall)

            VerticalDivider(modifier = Modifier.height(leftColumnHeight))

            // Right 1/4: create-session and edit-settings actions.
            Column(
                modifier = Modifier.width(buttonColumnWidth).height(leftColumnHeight),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                FilledTonalIconButton(onClick = onCreateSession, shape = buttonShape, modifier = Modifier.size(buttonSize)) {
                    Icon(Icons.Default.AutoAwesomeMotion, contentDescription = getText(R.string.create_session_from_preset))
                }
                Spacer(Modifier.height(buttonGap))
                FilledTonalIconButton(onClick = onEditSettings, shape = buttonShape, modifier = Modifier.size(buttonSize)) {
                    Icon(Icons.Default.Settings, contentDescription = getText(R.string.edit_preset_settings))
                }
            }
        }
    }
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
