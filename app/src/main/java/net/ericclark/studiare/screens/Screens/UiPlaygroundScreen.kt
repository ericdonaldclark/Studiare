package net.ericclark.studiare.screens.Screens
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material3.ripple
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.Hyphens
import androidx.compose.ui.text.style.LineBreak
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.screens.Dialogs.TextNormalizationDialog
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
    COMPLETION_SCREEN("Completion Screen"),
    TEXT_NORMALIZATION("Text Normalization"),
    DECK_TILE_REDESIGN("Deck Tile Redesign")
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
                    TypedChipRow(
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
                PlaygroundExperiment.TEXT_NORMALIZATION -> TextNormalizationPlayground()
                PlaygroundExperiment.DECK_TILE_REDESIGN -> DeckTileRedesignPlayground()
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
                    elapsedSeconds = 754L, // Playground sample only (12:34) - no slider for this yet.
                    endedByTimeout = false,
                    attemptedCount = 0,
                    totalCount = 0,
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

/**
 * Demos the real [TextNormalizationDialog] (the shared dialog now also wired into Deck Options)
 * against a scratch [NormalizationType] that nothing persists — just somewhere to see it opened
 * and confirmed/cancelled without a real deck in context.
 */
@Composable
private fun TextNormalizationPlayground() {
    val dimensions = LocalStudiareDimensions.current
    var showDialog by remember { mutableStateOf(false) }
    var currentType by remember { mutableStateOf(NormalizationType.NONE) }
    var currentCorrections by remember { mutableStateOf(TextCorrectionSettings()) }

    Column(
        modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        PlaygroundSectionLabel("Text Normalization dialog")
        Text(
            "The same dialog now wired into Deck Options, replacing its old dropdown.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(dimensions.spacingSmall))
        Text(
            "Currently selected: ${currentType.asString()}",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(dimensions.spacingMedium))
        Button(onClick = { showDialog = true }) {
            Text("Open Text Normalization Dialog")
        }
    }

    if (showDialog) {
        TextNormalizationDialog(
            initialType = currentType,
            initialCorrections = currentCorrections,
            onDismiss = { showDialog = false },
            onConfirm = { newType, newCorrections -> currentType = newType; currentCorrections = newCorrections; showDialog = false }
        )
    }
}

/**
 * Deck tile redesign exploration — "Proposal C" from the deck/set tile grid work: the deck name
 * gets up to 2 lines instead of single-line ellipsis, and the top-right "N Sets"/"N Cards" pair
 * is combined into one stack, with the Sets line given a pressable-looking filled pill (answering
 * "how do we make it obvious that's tappable" — a plain colored-text label reads as just
 * information, the way the card-count label already does; a filled tonal pill reads as a button
 * the same way it does everywhere else in this app).
 *
 * [DeckTileProposalC] below is a deliberate FORK of [DeckListItem] (DecksScreenItems.kt), not a
 * shared component or a wrapper around it — the real deck tile is untouched by this experiment,
 * on purpose, so this copy can be torn up freely. If a direction here gets picked, port it into
 * DeckListItem by hand; don't start importing this from real screens.
 */
@Composable
private fun DeckTileRedesignPlayground() {
    val dimensions = LocalStudiareDimensions.current
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    fun toast(message: String) {
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(message, duration = SnackbarDuration.Short)
        }
    }

    var deckName by remember { mutableStateOf("Advanced Conversational Spanish Vocabulary") }
    var cardCountText by remember { mutableStateOf("212") }
    var setsCountText by remember { mutableStateOf("6") }
    var isPinned by remember { mutableStateOf(false) }
    var deckNameFontOption by remember { mutableStateOf(DeckNameFontOption.HEADLINE_SMALL) }
    var deckCountLayout by remember { mutableStateOf(DeckCardCountLayout.COMBINED_PILL) }
    val cardCount = cardCountText.toIntOrNull() ?: 0
    val setsCount = setsCountText.toIntOrNull() ?: 0

    var setName by remember { mutableStateOf("Irregular Preterite Verbs — Part Two") }
    var setCardCountText by remember { mutableStateOf("34") }
    var setIsPinned by remember { mutableStateOf(false) }
    val setCardCount = setCardCountText.toIntOrNull() ?: 0

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
                PlaygroundSectionLabel("Deck Tile — Proposal C (combined Sets/Cards, pressable pill)")
                Text(
                    "Try a long name below to see the 2-line wrap, and tap the \"Sets\" pill — it's now a real filled chip, not just colored text.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Names & Counts")
                OutlinedTextField(value = deckName, onValueChange = { deckName = it }, label = { Text("Deck name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = cardCountText, onValueChange = { cardCountText = it }, label = { Text("Card count") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                OutlinedTextField(value = setsCountText, onValueChange = { setsCountText = it }, label = { Text("Sets count") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                SettingSwitchItem("Pinned", "Pin icon tint", isPinned) { isPinned = it }

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Deck Name Font Size")
                TypedChipRow(
                    items = DeckNameFontOption.entries,
                    selected = deckNameFontOption,
                    labelFor = { it.label },
                    onSelected = { deckNameFontOption = it }
                )

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Card Count / Manage Sets Position")
                Text(
                    "Production row: name gets a permanent 2nd line, Manage Sets moves down into a row with the card count — the gap above the Study button shrinks to keep the card's total height the same as decksscreen today.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TypedChipRow(
                    items = DeckCardCountLayout.entries,
                    selected = deckCountLayout,
                    labelFor = { it.label },
                    onSelected = { deckCountLayout = it }
                )

                Spacer(Modifier.height(dimensions.spacingSmall))
                PlaygroundSectionLabel("Set Tile — card count moved under overflow, 2-line name")
                OutlinedTextField(value = setName, onValueChange = { setName = it }, label = { Text("Set name") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = setCardCountText, onValueChange = { setCardCountText = it }, label = { Text("Set card count") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
                SettingSwitchItem("Pinned", "Pin icon tint", setIsPinned) { setIsPinned = it }
            }
        }
    ) { _ ->
        Box(modifier = Modifier.fillMaxSize()) {
            Surface(color = MaterialTheme.colorScheme.surfaceDim, modifier = Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(dimensions.paddingLarge),
                    verticalArrangement = Arrangement.spacedBy(dimensions.paddingLarge)
                ) {
                    DeckTileProposalC(
                        name = deckName,
                        cardCount = cardCount,
                        setsCount = setsCount,
                        isPinned = isPinned,
                        fontOption = deckNameFontOption,
                        countLayout = deckCountLayout,
                        onEdit = { toast("Edit tapped") },
                        onTogglePin = { isPinned = !isPinned },
                        onDelete = { toast("Delete tapped") },
                        onManageSets = { toast("Manage Sets tapped") },
                        onStudy = { toast("Study tapped") }
                    )
                    SetTileProposalC(
                        name = setName,
                        cardCount = setCardCount,
                        isPinned = setIsPinned,
                        onEdit = { toast("Edit tapped") },
                        onTogglePin = { setIsPinned = !setIsPinned },
                        onDelete = { toast("Delete tapped") },
                        onManageSets = { toast("Manage Sets tapped") },
                        onStudy = { toast("Study tapped") }
                    )
                }
            }
            PlaygroundRevealSheetFab(sheetState, coroutineScope)
        }
    }
}

private enum class DeckNameFontOption(val label: String, val style: @Composable () -> TextStyle) {
    HEADLINE_SMALL("Headline Small", { MaterialTheme.typography.headlineSmall }),
    HEADLINE_MEDIUM("Headline Medium", { MaterialTheme.typography.headlineMedium })
}

private enum class DeckCardCountLayout(val label: String) {
    COMBINED_PILL("Combined pill (current)"),
    PRODUCTION_ROW("Production row (name 2 lines, count+manage below)")
}

@Composable
private fun DeckTileProposalC(
    name: String,
    cardCount: Int,
    setsCount: Int,
    isPinned: Boolean,
    fontOption: DeckNameFontOption,
    countLayout: DeckCardCountLayout,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onManageSets: () -> Unit,
    onStudy: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val density = LocalDensity.current

    // Headline Medium is the preferred size; if the name would still be ellipsized at 3 lines in
    // Headline Medium, drop to Headline Small instead (which may still truncate — that's fine,
    // there's no smaller fallback). Only applies when the user picked Headline Medium in the
    // sheet — picking Headline Small still forces it unconditionally, same as before. Resets
    // whenever fontOption itself changes, so switching back to Headline Medium always retries it
    // rather than staying stuck on a previous downgrade.
    var useMediumIfItFits by remember(fontOption) { mutableStateOf(fontOption == DeckNameFontOption.HEADLINE_MEDIUM) }
    val renderMedium = fontOption == DeckNameFontOption.HEADLINE_MEDIUM && useMediumIfItFits
    val nameStyle = (if (renderMedium) DeckNameFontOption.HEADLINE_MEDIUM.style() else DeckNameFontOption.HEADLINE_SMALL.style())
        .copy(hyphens = Hyphens.Auto, lineBreak = LineBreak.Paragraph)

    // Both layout options below share this exact same scaffold — name row (always 3 lines), small
    // gap, a controls row, reduced gap — so they come out the same total height BY CONSTRUCTION,
    // not by two separate formulas trying to land on the same number (which is what kept
    // producing mismatched heights before). Only what's rendered inside the controls row differs
    // between the two options.
    var nameHeightPx by remember { mutableFloatStateOf(0f) }
    fun onNameTextLayout(result: androidx.compose.ui.text.TextLayoutResult) {
        nameHeightPx = result.size.height.toFloat()
        if (renderMedium && result.hasVisualOverflow) {
            useMediumIfItFits = false
        }
    }
    // The real reference — "the card size we currently have using Headline Small" — is this
    // style's OWN 3-line height (2 * its line height, the already-verified-matching baseline),
    // not an assumption that the rendered style is small. Computed analytically from font
    // metrics (not measured) so it doesn't depend on which style is actually on screen, and it
    // correctly scales with the device's font-size/accessibility settings the same way real
    // rendering does.
    val smallBaselineHeightPx = with(density) {
        2f * DeckNameFontOption.HEADLINE_SMALL.style().lineHeight.toPx()
    }
    // How much taller the name block is right now than that Headline Small baseline — zero when
    // actually rendering Headline Small (3-line small height minus 2-line small height is exactly
    // one small line, which is what the gaps below were already tuned to cancel), and larger when
    // Headline Medium is in use, since its lines are taller.
    val extraVsSmallBaselineDp = with(density) { (nameHeightPx - smallBaselineHeightPx).toDp() }
    val basePaddingV = dimensions.paddingMedium.coerceAtLeast(dimensions.cornerRadiusMedium)
    // Each layout's own internal gap(s) below can absorb only so much extra height before
    // flooring at 0dp — Combined Pill's one gap starts with paddingLarge+spacingSmall of slack,
    // Production Row's only real slack is its smaller post-name gap (its main gap has none, even
    // at the Headline Small baseline). Headline Medium's taller lines can exceed either budget
    // (confirmed on-device: Combined Pill alone overshot by 16px with Headline Medium), so
    // whatever's left after each branch's own gap floors at 0 comes out of the card's own
    // top/bottom padding instead, split evenly — same technique, just no longer Production-Row-only.
    val branchSlack = when (countLayout) {
        DeckCardCountLayout.COMBINED_PILL -> dimensions.paddingLarge + dimensions.spacingSmall
        DeckCardCountLayout.PRODUCTION_ROW -> dimensions.spacingSmall
    }
    val remainingDeficit = (extraVsSmallBaselineDp - branchSlack).coerceAtLeast(0.dp)
    val paddingV = (basePaddingV - remainingDeficit / 2).coerceAtLeast(0.dp)

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation, pressedElevation = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = basePaddingV, vertical = paddingV)) {
            if (countLayout == DeckCardCountLayout.COMBINED_PILL) {
                // Pill sits beside the name in the SAME row, same spot production's Manage Sets
                // button uses — name gets weight(1f) so the pill has room, same as production's
                // name+chip Column vs Manage Sets TextButton split. Measured on-device: the
                // pill's own height (118px) is shorter than the 2-line name (156px), so this row's
                // height is governed by the name either way — putting the pill beside it instead
                // of below it does not add any extra card height.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        name,
                        style = nameStyle,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        minLines = 3,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(end = dimensions.spacingSmall),
                        onTextLayout = ::onNameTextLayout
                    )
                    Surface(
                        onClick = onManageSets,
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                Icons.Default.AccountTree,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    "$setsCount Sets",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "$cardCount Cards",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingSmall))
            } else {
                Text(
                    name,
                    style = nameStyle,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    minLines = 3,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(),
                    onTextLayout = ::onNameTextLayout
                )

                // Production Row's main gap below is already floored at 0dp (see its measurement
                // note), so there's no room left there to absorb the new 3rd line — this smaller
                // gap is the only slack left, so the line's extra height comes out of it instead.
                Spacer(Modifier.height((dimensions.spacingSmall - extraVsSmallBaselineDp).coerceAtLeast(0.dp)))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Same positions production's DeckListItem already uses for these two
                    // controls — card count chip on the left, Manage Sets on the right.
                    SuggestionChip(
                        onClick = { },
                        label = { Text("$cardCount Cards") },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = null
                    )
                    TextButton(onClick = onManageSets) {
                        Icon(Icons.Default.AccountTree, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("$setsCount Sets")
                    }
                }
            }

            // Both gaps below are set from real on-device uiautomator measurements against the
            // actual decksscreen DeckListItem (924x530px at this device's Comfortable density),
            // not from a formula guessed on paper — the previous "shrink by half the measured
            // name height" formula was checked against real bounds and was wrong for both cases.
            // Combined Pill measured 924x527 (needs the full gap plus a small top-up, since its
            // Row 1 has no separate card-count chip stacked under the name the way production
            // does — the pill already shows the count). Production Row's own content row (name +
            // chip+button row) already reaches ~924x615 with the full gap, which overshoots by
            // 85px — a 0dp gap there measured 924x615-85=924x~530, matching production almost
            // exactly (within ~9px).
            val gap = when (countLayout) {
                DeckCardCountLayout.COMBINED_PILL ->
                    (dimensions.paddingLarge + dimensions.spacingSmall - extraVsSmallBaselineDp).coerceAtLeast(0.dp)
                DeckCardCountLayout.PRODUCTION_ROW -> 0.dp
            }
            Spacer(Modifier.height(gap))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                // Un-gapped inner Row, same as production: the outer Row's spacedBy only
                // separates this icon cluster from the Study button, not the icons from each other.
                Row(verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(description = getText(R.string.edit), onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TooltipIconButton(
                    description = if (isPinned) getText(R.string.unpin_deck) else getText(R.string.pin_deck),
                    onClick = onTogglePin
                ) {
                    Icon(
                        Icons.Rounded.PushPin,
                        contentDescription = null,
                        tint = if (isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TooltipIconButton(description = getText(R.string.delete), onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = getText(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
                }
                StudySplitButton(onStudyMain = onStudy, enabled = cardCount > 0)
            }
        }
    }
}

/**
 * [SetTileProposalC] is a deliberate FORK of [SetListItem] (DecksScreenItems.kt), not a shared
 * component or a wrapper around it — the real set tile is untouched by this experiment.
 *
 * Two changes versus production: (1) the card count moves from under the name to under the
 * overflow button on the far right, and (2) the set name is allowed to wrap to 2 lines instead
 * of being truncated to 1.
 *
 * The overflow trigger is a plain Icon with an explicit 24dp clickable/ripple box, NOT
 * TooltipIconButton/IconButton — Material3's IconButton enforces an internal minimum 48dp
 * interactive size no matter what size modifier you give it, which is what made a plain
 * Alignment.Top placement (and, before that, runtime pixel-offset math trying to compensate for
 * it) put the visible glyph well below the name's top instead of flush with it. With no hidden
 * minimum to correct for, Alignment.Top alone puts the glyph exactly where it's placed — no
 * measurement, no offset modifier, nothing to get subtly wrong. Trade-off: this loses the
 * tooltip-on-long-press and the full 48dp accessibility touch target TooltipIconButton gives for
 * free; worth revisiting before this ever reaches production code.
 */
@Composable
private fun SetTileProposalC(
    name: String,
    cardCount: Int,
    isPinned: Boolean,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit,
    onManageSets: () -> Unit,
    onStudy: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var showOverflow by remember { mutableStateOf(false) }
    val overflowInteractionSource = remember { MutableInteractionSource() }

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation, pressedElevation = 8.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.width(dimensions.setTileWidth)
    ) {
        Column(modifier = Modifier.padding(dimensions.paddingMedium.coerceAtLeast(dimensions.cornerRadiusMedium))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium.copy(
                        hyphens = Hyphens.Auto,
                        lineBreak = LineBreak.Paragraph
                    ),
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f).padding(end = dimensions.spacingSmall)
                )
                Column(horizontalAlignment = Alignment.End) {
                    Box {
                        // Deliberately NOT TooltipIconButton/IconButton here: Material3's
                        // IconButton enforces an internal minimum 48dp interactive size no
                        // matter what size you request (the same thing that made shrinking the
                        // touch target in production not work), so the glyph ends up centered in
                        // an invisible box well below where it's placed. A plain, explicitly-sized
                        // clickable box has no hidden minimum, so top-aligning it next to the name
                        // puts the glyph flush with the name's top with no measurement/offset math
                        // needed at all.
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = getText(R.string.options_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = overflowInteractionSource,
                                    indication = ripple(bounded = false, radius = 20.dp),
                                    onClickLabel = getText(R.string.options_more)
                                ) { showOverflow = true }
                        )
                        DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                            DropdownMenuItem(
                                text = { Text(getText(R.string.edit)) },
                                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                onClick = { showOverflow = false; onEdit() }
                            )
                            DropdownMenuItem(
                                text = { Text(getText(if (isPinned) R.string.unpin_set else R.string.pin_set)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.PushPin,
                                        contentDescription = null,
                                        tint = if (isPinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                },
                                onClick = { showOverflow = false; onTogglePin() }
                            )
                            DropdownMenuItem(
                                text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                onClick = { showOverflow = false; onDelete() }
                            )
                            DropdownMenuItem(
                                text = { Text(getText(R.string.manage_sets)) },
                                leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                                onClick = { showOverflow = false; onManageSets() }
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(R.plurals.cards_count_lowercase, cardCount, cardCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(dimensions.paddingLarge))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                StudySplitButton(onStudyMain = onStudy, enabled = cardCount > 0)
            }
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
