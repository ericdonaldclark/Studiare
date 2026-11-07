package net.ericclark.studiare.screens.Screens
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
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
    SESSION_PRESET_CARD("Preset Card"),
    COMPLETION_SCREEN("Completion Screen")
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
                PlaygroundExperiment.COMPLETION_SCREEN -> StudyCompletionPlayground()
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

    // skipHiddenState = false lets the sheet be dragged all the way down and out of sight, not
    // just between peeked/expanded - PlaygroundRevealSheetFab below is the way back once it's hidden.
    val sheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetPeekHeight = PlaygroundSheetPeekHeight,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PlaygroundSheetMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimensions.paddingLarge)
                    .padding(bottom = dimensions.paddingLarge),
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
            }
        }
    ) { _ ->
        // The sheet overlays the content rather than pushing it up, so the full screen
        // (not innerPadding, which always reserves sheetPeekHeight at the bottom even when
        // the sheet is hidden) is what the preview and the reveal FAB should lay out against.
        Box(modifier = Modifier.fillMaxSize()) {
            // A darker, recessed canvas color (this app's established surfaceDim convention for
            // screen backgrounds/gutters) behind the preview card, so its edges read clearly
            // against the surrounding area instead of blending into a similarly-light tone.
            Surface(color = MaterialTheme.colorScheme.surfaceDim, modifier = Modifier.fillMaxSize()) {
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
                        onRename = { name -> sessionName = name ?: ""; toast(if (name != null) "Renamed to \"$name\"" else "Name cleared") },
                        showCategoryNameIcon = showCategoryNameIcon,
                        showModeName = showModeName
                    )
                }
            }
            PlaygroundRevealSheetFab(sheetState, coroutineScope)
        }
    }
}

/**
 * "Create session" shortcut: a small, always-pinned preset — mode/category plus whatever
 * mode-specific options were configured — that creates a real session (with those settings) only
 * once tapped, rather than being a session itself. Now exercises the real, promoted
 * [SessionPresetCard] (in `StudyHubTiles.kt`) against a throwaway in-memory [SessionPreset] built
 * from this screen's live controls, the same way [SessionTilePlayground] above exercises the real
 * production [SessionTile] rather than keeping its own duplicate.
 */
@Composable
private fun SessionPresetCardPlayground() {
    val dimensions = LocalStudiareDimensions.current
    val coroutineScope = rememberCoroutineScope()
    // skipHiddenState = false lets the sheet be dragged all the way down and out of sight, not
    // just between peeked/expanded - PlaygroundRevealSheetFab below is the way back once it's hidden.
    val sheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

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

    // A throwaway, never-persisted SessionPreset built purely to drive the real SessionPresetCard
    // live — mirrors how SessionTilePlayground builds a sample ActiveSession for the same reason.
    val samplePreset = remember(mode, modeOptionValues, showDifficulties, presetName) {
        SessionPreset(
            id = "preview-preset",
            deckId = "preview-deck",
            name = presetName.ifBlank { null },
            sessionMode = mode,
            numCards = totalCards,
            selectedDifficulties = if (showDifficulties) listOf(1, 2, 3, 5) else emptyList(),
            isGraded = category == StudyCategory.QUIZ
        ).let { preset ->
            // Overlay the live-edited mode options the same way applyModeDefaults does for a
            // sample ActiveSession — every ModeDefaultSettings field maps 1:1 by name.
            val defaults = ModeDefaultSettings()
            preset.copy(
                numberOfAnswers = modeOptionValues.numberOfAnswers ?: defaults.numberOfAnswers ?: preset.numberOfAnswers,
                showCorrectLetters = modeOptionValues.showCorrectLetters ?: preset.showCorrectLetters,
                maxMemoryTiles = modeOptionValues.maxMemoryTiles ?: preset.maxMemoryTiles,
                gridDensity = modeOptionValues.gridDensity ?: preset.gridDensity,
                freeformLayoutVertical = modeOptionValues.freeformLayoutVertical ?: preset.freeformLayoutVertical,
                quizPromptSide = modeOptionValues.quizPromptSide ?: preset.quizPromptSide,
                isWeighted = modeOptionValues.difficultyWeighted ?: preset.isWeighted,
                audioPlaybackSpeed = modeOptionValues.audioPlaybackSpeed ?: preset.audioPlaybackSpeed,
                audioReplayCount = modeOptionValues.audioReplayCount ?: preset.audioReplayCount,
                audioAutoAdvance = modeOptionValues.audioAutoAdvance ?: preset.audioAutoAdvance,
                audioAnswerDelaySeconds = modeOptionValues.audioAnswerDelaySeconds ?: preset.audioAnswerDelaySeconds,
                audioNextCardDelaySeconds = modeOptionValues.audioNextCardDelaySeconds ?: preset.audioNextCardDelaySeconds,
                freeformShowBothSides = modeOptionValues.freeformShowBothSides ?: preset.freeformShowBothSides,
                freeformSwipeNavigation = modeOptionValues.freeformSwipeNavigation ?: preset.freeformSwipeNavigation,
                typingIgnoreFormatting = modeOptionValues.typingIgnoreFormatting ?: preset.typingIgnoreFormatting,
                typingDisableAutocorrect = modeOptionValues.typingDisableAutocorrect ?: preset.typingDisableAutocorrect,
                typingShowLengthHint = modeOptionValues.typingShowLengthHint ?: preset.typingShowLengthHint,
                typedListenUseMultipleChoice = modeOptionValues.typedListenUseMultipleChoice ?: preset.typedListenUseMultipleChoice,
                hearMatchAutoPlayAllOptions = modeOptionValues.hearMatchAutoPlayAllOptions ?: preset.hearMatchAutoPlayAllOptions,
                hideDifficultySelection = modeOptionValues.hideDifficultySelection ?: preset.hideDifficultySelection,
                flashcardAutoFlipSeconds = modeOptionValues.flashcardAutoFlipSeconds ?: preset.flashcardAutoFlipSeconds,
                flashcardAutoFlip = modeOptionValues.flashcardAutoFlip ?: preset.flashcardAutoFlip,
                flashcardDoubleTapToFlip = modeOptionValues.flashcardDoubleTapToFlip ?: preset.flashcardDoubleTapToFlip,
                flashcardRandomizeFirstSide = modeOptionValues.flashcardRandomizeFirstSide ?: preset.flashcardRandomizeFirstSide,
                requireConfirmTap = modeOptionValues.requireConfirmTap ?: preset.requireConfirmTap,
                allowMultipleGuesses = modeOptionValues.allowMultipleGuesses ?: preset.allowMultipleGuesses,
                autoAdvance = modeOptionValues.autoAdvance ?: preset.autoAdvance,
                autoAdvanceDelaySeconds = modeOptionValues.autoAdvanceDelaySeconds ?: preset.autoAdvanceDelaySeconds,
                autoListen = modeOptionValues.autoListen ?: preset.autoListen,
                listenStartSound = modeOptionValues.listenStartSound ?: preset.listenStartSound,
                listenCorrectSound = modeOptionValues.listenCorrectSound ?: preset.listenCorrectSound,
                listenIncorrectSound = modeOptionValues.listenIncorrectSound ?: preset.listenIncorrectSound,
                speakingFrontSpeed = modeOptionValues.speakingFrontSpeed ?: preset.speakingFrontSpeed,
                speakingBackSpeed = modeOptionValues.speakingBackSpeed ?: preset.speakingBackSpeed,
                listResetPosition = modeOptionValues.listResetPosition ?: preset.listResetPosition,
                listDimWrongGuesses = modeOptionValues.listDimWrongGuesses ?: preset.listDimWrongGuesses,
                listRemoveGuessed = modeOptionValues.listRemoveGuessed ?: preset.listRemoveGuessed,
                matchingHighlightStyle = modeOptionValues.matchingHighlightStyle ?: preset.matchingHighlightStyle,
                matchingWrongDelayMs = modeOptionValues.matchingWrongDelayMs ?: preset.matchingWrongDelayMs,
                matchingCorrectHighlightMs = modeOptionValues.matchingCorrectHighlightMs ?: preset.matchingCorrectHighlightMs,
                matchingShowCorrectDialog = modeOptionValues.matchingShowCorrectDialog ?: preset.matchingShowCorrectDialog,
                anagramFirstLetterHint = modeOptionValues.anagramFirstLetterHint ?: preset.anagramFirstLetterHint,
                anagramUppercase = modeOptionValues.anagramUppercase ?: preset.anagramUppercase,
                anagramColorVowels = modeOptionValues.anagramColorVowels ?: preset.anagramColorVowels,
                crosswordHighlightWord = modeOptionValues.crosswordHighlightWord ?: preset.crosswordHighlightWord,
                crosswordAutoAdvanceCell = modeOptionValues.crosswordAutoAdvanceCell ?: preset.crosswordAutoAdvanceCell,
                crosswordCompactClues = modeOptionValues.crosswordCompactClues ?: preset.crosswordCompactClues,
                hangmanMaxMistakes = modeOptionValues.hangmanMaxMistakes ?: preset.hangmanMaxMistakes,
                hangmanHideVisual = modeOptionValues.hangmanHideVisual ?: preset.hangmanHideVisual,
                memoryGrayMatched = modeOptionValues.memoryGrayMatched ?: preset.memoryGrayMatched,
                memoryPeekSeconds = modeOptionValues.memoryPeekSeconds ?: preset.memoryPeekSeconds,
                memoryGridColumnsPortrait = modeOptionValues.memoryGridColumnsPortrait ?: preset.memoryGridColumnsPortrait,
                memoryGridColumnsLandscape = modeOptionValues.memoryGridColumnsLandscape ?: preset.memoryGridColumnsLandscape,
                memoryWrongPairMs = modeOptionValues.memoryWrongPairMs ?: preset.memoryWrongPairMs,
                memoryCorrectPairMs = modeOptionValues.memoryCorrectPairMs ?: preset.memoryCorrectPairMs,
                memorySubmitAnswer = modeOptionValues.memorySubmitAnswer ?: preset.memorySubmitAnswer,
                wordSearchHideFound = modeOptionValues.wordSearchHideFound ?: preset.wordSearchHideFound,
                wordSearchHighlightColor = modeOptionValues.wordSearchHighlightColor ?: preset.wordSearchHighlightColor
            )
        }
    }

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        sheetPeekHeight = PlaygroundSheetPeekHeight,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PlaygroundSheetMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimensions.paddingLarge)
                    .padding(bottom = dimensions.paddingLarge),
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
            }
        }
    ) { _ ->
        // The sheet overlays the content rather than pushing it up, so the full screen
        // (not innerPadding, which always reserves sheetPeekHeight at the bottom even when
        // the sheet is hidden) is what the preview and the reveal FAB should lay out against.
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceDim, modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.padding(dimensions.paddingLarge)) {
                    SessionPresetCard(
                        preset = samplePreset,
                        deckName = deckName,
                        showTitle = showTitle,
                        decks = emptyList(),
                        onCreateSession = {},
                        onEditSettings = {},
                        onUnpin = {},
                        onRename = {},
                        onChangeDeckStartSession = {},
                        onChangeDeckSave = {},
                        onChangeDeckPinAsNew = { _, _ -> }
                    )
                }
            }
            PlaygroundRevealSheetFab(sheetState, coroutineScope)
        }
    }
}

/**
 * Exercises the real [StudyCompletionBody] (extracted from `StudyCompletion.kt` for exactly this
 * purpose) against sample data. The header now shrinks to Compact automatically once Keep/Pin is
 * on, same as the real screen - [CompletionButtonLayout] remains a live Playground-only knob.
 */
@Composable
private fun StudyCompletionPlayground() {
    val dimensions = LocalStudiareDimensions.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    fun toast(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    var buttonLayout by remember { mutableStateOf(CompletionButtonLayout.TWO_COLUMN) }
    var showAccuracy by remember { mutableStateOf(true) }
    var accuracyPercent by remember { mutableFloatStateOf(92f) }
    var showMissedCardsButton by remember { mutableStateOf(true) }
    var missedCardsCount by remember { mutableFloatStateOf(4f) }
    var keep by remember { mutableStateOf(false) }
    var pin by remember { mutableStateOf(false) }
    var reshuffle by remember { mutableStateOf(false) }
    var missedOnly by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var defaultName by remember { mutableStateOf("Flashcard - Adjectives - 11/05/26") }

    // skipHiddenState = false lets the sheet be dragged all the way down and out of sight, not
    // just between peeked/expanded - PlaygroundRevealSheetFab below is the way back once it's hidden.
    val sheetState = rememberStandardBottomSheetState(skipHiddenState = false)
    val scaffoldState = rememberBottomSheetScaffoldState(bottomSheetState = sheetState)

    BottomSheetScaffold(
        scaffoldState = scaffoldState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        sheetPeekHeight = PlaygroundSheetPeekHeight,
        sheetContent = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = PlaygroundSheetMaxHeight)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = dimensions.paddingLarge)
                    .padding(bottom = dimensions.paddingLarge),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
            ) {
                PlaygroundSectionLabel("Space-Saving Layout")
                PlaygroundLabel("Button layout")
                TypedChipGrid(items = CompletionButtonLayout.entries, selected = buttonLayout, labelFor = { it.label }, onSelected = { buttonLayout = it })

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Session")
                SettingSwitchItem("Scored Mode (shows First Try Accuracy)", "Mirrors the real screen's notScored check - off hides the accuracy badge entirely, same as Crossword/Memory/etc.", showAccuracy) { showAccuracy = it }
                if (showAccuracy) {
                    Text("Accuracy: ${accuracyPercent.roundToInt()}%", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = accuracyPercent, onValueChange = { accuracyPercent = it }, valueRange = 0f..100f)
                }
                SettingSwitchItem("Has Missed Cards", "Mirrors the real screen's missedCardsEligibleMode + missedCards.isNotEmpty() gate - drives the Review Missed Cards button and the \"Save only missed cards\" toggle", showMissedCardsButton) { showMissedCardsButton = it }
                if (showMissedCardsButton) {
                    Text("Missed cards: ${missedCardsCount.roundToInt()}", style = MaterialTheme.typography.bodyMedium)
                    Slider(value = missedCardsCount, onValueChange = { missedCardsCount = it }, valueRange = 1f..20f)
                }

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Keep / Pin / Order")
                SettingSwitchItem("Keep this session", "Expands the Pin/Name/Card order section", keep) { keep = it }
                SettingSwitchItem("Pin this session", "Also forces Keep on, same as the real toggle", pin) { checked -> pin = checked; if (checked) keep = true }
                SettingSwitchItem("Reshuffle on restart", "Hidden once \"Save only missed cards\" is on", reshuffle) { reshuffle = it }
                SettingSwitchItem("Save only missed cards", "Hides the Card Order control while on", missedOnly) { missedOnly = it }

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Names")
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Custom session name") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = defaultName, onValueChange = { defaultName = it }, label = { Text("Default name (placeholder)") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            }
        }
    ) { _ ->
        // The sheet overlays the content rather than pushing it up, so the full screen
        // (not innerPadding, which always reserves sheetPeekHeight at the bottom even when
        // the sheet is hidden) is what the preview and the reveal FAB should lay out against.
        Box(modifier = Modifier.fillMaxSize()) {
            // A darker, recessed canvas color (this app's established surfaceDim convention for
            // screen backgrounds/gutters) behind the preview, so its edges read clearly against
            // the surrounding area instead of blending into a similarly-light tone.
            Surface(color = MaterialTheme.colorScheme.surfaceDim, modifier = Modifier.fillMaxSize()) {
                StudyCompletionBody(
                    // Mirrors the real screen exactly - the header's Full/Compact choice is made
                    // internally (see AdaptiveCompletionTop in StudyCompletion.kt), not a param here.
                    buttonLayout = buttonLayout,
                    showAccuracy = showAccuracy,
                    accuracyPercent = accuracyPercent.roundToInt(),
                    showMissedCardsButton = showMissedCardsButton,
                    missedCardsCount = missedCardsCount.roundToInt(),
                    keep = keep,
                    onKeepChange = { keep = it },
                    pin = pin,
                    onPinChange = { checked -> pin = checked; if (checked) keep = true },
                    reshuffle = reshuffle,
                    onReshuffleChange = { reshuffle = it },
                    missedOnly = missedOnly,
                    onMissedOnlyChange = { missedOnly = it },
                    name = name,
                    onNameChange = { name = it },
                    defaultName = defaultName,
                    onRestartSame = { toast("Restart (same order) tapped") },
                    onRestartReshuffled = { toast("Restart (reshuffled) tapped") },
                    onRestartMissedOnly = { toast("Restart (missed only) tapped") },
                    onBackToSessions = { toast("Back to Sessions tapped") },
                    onBackToHome = { toast("Back to Home tapped") },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(dimensions.paddingMedium)
                )
            }
            PlaygroundRevealSheetFab(sheetState, coroutineScope)
        }
    }
}

// Shared across every experiment's BottomSheetScaffold: tall enough to peek a section label plus
// a couple of controls without covering most of the preview; capped so a fully-dragged-up sheet on
// a long options list still scrolls internally instead of pushing past the top of the screen.
private val PlaygroundSheetPeekHeight = 220.dp
private val PlaygroundSheetMaxHeight = 520.dp

/**
 * Lets the options sheet be dragged all the way down and out of sight (not just peeked/expanded) -
 * `skipHiddenState = false` on the sheet's own state is what allows that; this is just the way
 * back once it's hidden, since the drag handle itself is off-screen at that point.
 */
@Composable
private fun BoxScope.PlaygroundRevealSheetFab(sheetState: SheetState, scope: CoroutineScope) {
    AnimatedVisibility(
        visible = sheetState.currentValue == SheetValue.Hidden,
        modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
        enter = fadeIn(),
        exit = fadeOut()
    ) {
        SmallFloatingActionButton(onClick = { scope.launch { sheetState.show() } }) {
            Icon(Icons.Default.Tune, contentDescription = "Show options")
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
