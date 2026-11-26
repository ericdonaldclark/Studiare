package net.ericclark.studiare.screens.Dialogs

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PushPin as FilledPushPin
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.data.*
import kotlinx.coroutines.flow.first
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.StudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish
import kotlin.math.roundToInt
import net.ericclark.studiare.R
import androidx.compose.ui.res.pluralStringResource
import net.ericclark.studiare.components.*
import androidx.compose.animation.togetherWith
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.screens.UI_Components.AudioAnswerDelay
import net.ericclark.studiare.screens.UI_Components.AudioAutoAdvance
import net.ericclark.studiare.screens.UI_Components.AudioNextCardDelay
import net.ericclark.studiare.screens.UI_Components.AudioPlaybackSpeed
import net.ericclark.studiare.screens.UI_Components.AudioReplayCount
import net.ericclark.studiare.screens.UI_Components.DifficultyWeighting
import net.ericclark.studiare.screens.UI_Components.FlashcardAutoFlip
import net.ericclark.studiare.screens.UI_Components.FlashcardDoubleTap
import net.ericclark.studiare.screens.UI_Components.FlashcardRandomizeSide
import net.ericclark.studiare.screens.UI_Components.FreeformLayout
import net.ericclark.studiare.screens.UI_Components.FreeformShowBothSides
import net.ericclark.studiare.screens.UI_Components.FreeformSwipeNavigation
import net.ericclark.studiare.screens.UI_Components.GridDensity
import net.ericclark.studiare.screens.UI_Components.MaxMemoryTiles
import net.ericclark.studiare.screens.UI_Components.ModeOptionContext
import net.ericclark.studiare.screens.UI_Components.ModeOptionDialogSections
import net.ericclark.studiare.screens.UI_Components.ModeOptionsInline
import net.ericclark.studiare.screens.UI_Components.NumberOfAnswers
import net.ericclark.studiare.screens.UI_Components.PromptSide
import net.ericclark.studiare.screens.UI_Components.SaveAsDefaultButtons
import net.ericclark.studiare.screens.UI_Components.ShowCorrectLetters
import net.ericclark.studiare.screens.UI_Components.ShowCorrectWords
import net.ericclark.studiare.screens.UI_Components.TypingDisableAutocorrect
import net.ericclark.studiare.screens.UI_Components.TypingIgnoreFormatting
import net.ericclark.studiare.screens.UI_Components.TypingShowLengthHint
import net.ericclark.studiare.screens.UI_Components.TypedListenMultipleChoice
import net.ericclark.studiare.screens.UI_Components.HearMatchAutoPlayAll
import net.ericclark.studiare.screens.UI_Components.HideDifficultySelection
import net.ericclark.studiare.screens.UI_Components.ContinuousSession
import net.ericclark.studiare.screens.UI_Components.TypedChipGrid
import net.ericclark.studiare.screens.UI_Components.ModeDefaultSettingsSaver
import net.ericclark.studiare.screens.UI_Components.modeOptionsFor

@Composable
fun CreateStudySessionDialog(
    navController: NavController,
    deck: DeckWithCards,
    viewModel: FlashcardViewModel,
    initialCategory: StudyCategory,
    // Non-null when arriving via a SessionPreset's settings-cog button (or right after this
    // screen's own Pin button creates one — see loadedPresetId below): pre-populates every field
    // from the preset's stored config, in an editing-this-preset mode.
    presetId: String? = null,
) {
    // Defers this screen's genuinely expensive first composition (session-config state, the
    // side-by-side/portrait form layout, all its sections) until after the NavHost transition
    // into this screen visually settles, instead of it swallowing the transition whole — same
    // proven pattern as DeckEditorScreen.kt/SimpleEditorScreen.kt. DEBUG-only: a release build's
    // baseline profile already covers this screen's first composition cost, but a debug build is
    // always JIT-interpreted regardless of the baseline profile, so the risk is still real there.
    val navAnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    var mainContentReady by remember { mutableStateOf(!BuildConfig.DEBUG || navAnimatedVisibilityScope == null) }
    LaunchedEffect(navAnimatedVisibilityScope) {
        if (!BuildConfig.DEBUG) return@LaunchedEffect
        val transition = navAnimatedVisibilityScope?.transition ?: return@LaunchedEffect
        snapshotFlow { transition.currentState }.first { it == EnterExitState.Visible }
        mainContentReady = true
    }

    // This used to be a dialog (AnimatedDialog + Card) hosted by whatever screen launched it, with
    // onDismiss/onStartSession/etc. passed in by that caller (see StudySessionDialogHost.kt, which
    // still owns the usesVoices()/VoicePromptState/VoiceDownloadPrompts helpers used below).
    // Starting a session is a "go somewhere new" action, not an in-place edit, so this is now its
    // own NavHost destination instead — it owns all of that logic directly rather than receiving
    // it as callbacks from a caller.
    val allTagDefinitions by viewModel.tags.collectAsState()
    val availableTags by produceState(initialValue = emptyList<String>(), key1 = deck.deck.id) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
            deck.cards.flatMap { it.tags }.distinct().sorted()
        }
    }
    val modeDefaults = viewModel.modeDefaultSettings.collectAsState().value
    val notificationPromptShown = viewModel.notificationPromptShown.collectAsState().value
    val onNotificationPromptShown = { viewModel.markNotificationPromptShown() }
    val onCategoryChosen: (StudyCategory) -> Unit = { viewModel.setLastStudyCategory(it) }
    val onDismiss: () -> Unit = { navController.popBackStack() }

    val voicePrompt = rememberVoicePromptState()
    VoiceDownloadPrompts(state = voicePrompt, viewModel = viewModel)

    // --- Preset loading (decision C in the plan file) ---
    // loadedPresetId starts from the nav arg and is reassigned to a freshly-Pinned preset's own
    // id, so a second round of edits without saving shows the Save button too, not just the
    // settings-cog entry path. loadedPresetOverride is set synchronously by the Pin/Save handlers
    // to the object they just built — presets.find{} alone would lag a frame or two behind the
    // async DB write, which would otherwise leave isPresetDirty wrongly stuck reporting "clean"
    // right after a fresh Pin.
    val presets by viewModel.presets.collectAsState()
    var loadedPresetId by rememberSaveable { mutableStateOf(presetId) }
    var loadedPresetOverride by remember { mutableStateOf<SessionPreset?>(null) }
    val loadedPreset = loadedPresetOverride ?: presets.find { it.id == loadedPresetId }

    // The category can be switched inside the dialog, so it's dialog state seeded from the caller
    // (or from loadedPreset, when arriving via a preset's edit button). Declared before
    // onStartSession below (moved up from its original spot further down this function) so that
    // closure can read it directly, since it's what gets stamped as the started session's/preset's
    // own startedCategory.
    var category by rememberSaveable { mutableStateOf(loadedPreset?.displayCategory() ?: initialCategory) }
    LaunchedEffect(category) { onCategoryChosen(category) }

    val onStartSession: (
        mode: SessionMode, isWeighted: Boolean, numCards: Int, quizPromptSide: CardSide, numAnswers: Int,
        showCorrectLetters: Boolean, limitAnswerPool: Boolean, isGraded: Boolean, allowMultipleGuesses: Boolean,
        maxMemoryTiles: Int, gridDensity: Int,
        showCorrectWords: Boolean, freeformLayoutVertical: Boolean, config: AutoSetConfig
    ) -> Unit = { mode, isWeighted, numCards, quizPromptSide, numAnswers, showLetters, limitPool, isGraded,
                   allowMultipleGuesses, maxMemoryTiles, gridDensity, _, freeformVerticalLayout, config ->
        val route = studyRouteFor(mode)
        val startAction = {
            viewModel.startStudySession(
                parentDeck = deck,
                mode = mode,
                category = category,
                isWeighted = isWeighted,
                numCards = numCards,
                quizPromptSide = quizPromptSide,
                numAnswers = numAnswers,
                showCorrectLetters = showLetters,
                limitAnswerPool = limitPool,
                isGraded = isGraded,
                allowMultipleGuesses = allowMultipleGuesses,
                maxMemoryTiles = maxMemoryTiles,
                gridDensity = gridDensity,
                config = config,
                freeformLayoutVertical = freeformVerticalLayout
            ) {
                // Replace, not stack: back from the study screen should return to wherever the
                // user was before opening this screen, not to this now-stale form.
                navController.popBackStack()
                navController.navigate(route)
            }
        }
        if (mode.usesVoices() && viewModel.pendingVoiceLanguages().isNotEmpty()) {
            voicePrompt.request(startAction)
        } else {
            startAction()
        }
    }

    val dimensions = LocalStudiareDimensions.current
    val deckDefaultPromptSide = deck.deck.defaultPromptSide
    val isGuided = category == StudyCategory.GUIDED

    // --- Session Settings State ---
    var selectedMode by rememberSaveable {
        mutableStateOf(loadedPreset?.sessionMode ?: (modesForCategory(category).firstOrNull() ?: SessionMode.FLASHCARD))
    }

    // Session options (mode-specific settings and difficulty weighting) live in one ModeDefaultSettings:
    // seeded from Settings → Mode Defaults for this (category, mode) pair, then overridable per session.
    val initialModeDefaults = modeDefaults[category to selectedMode]
    var optionValues by rememberSaveable(stateSaver = ModeDefaultSettingsSaver) {
        // loadedPreset's own settings win over the generic Settings → Mode Defaults overlay —
        // applied last, same overlaidWith "non-null fields of other win" semantics.
        mutableStateOf(
            ModeDefaultSettings(quizPromptSide = deckDefaultPromptSide)
                .overlaidWith(initialModeDefaults)
                .overlaidWith(loadedPreset?.toModeDefaultSettings())
        )
    }
    val numberOfAnswers = NumberOfAnswers.valueIn(optionValues)
    val showCorrectLetters = ShowCorrectLetters.valueIn(optionValues, category)
    val maxMemoryTiles = MaxMemoryTiles.valueIn(optionValues)
    val gridDensity = GridDensity.valueIn(optionValues)
    val showCorrectWords = ShowCorrectWords.valueIn(optionValues)
    val freeformLayoutVertical = FreeformLayout.valueIn(optionValues)
    val quizPromptSide = PromptSide.valueIn(optionValues)
    val isWeighted = !isGuided && DifficultyWeighting.isWeighted(optionValues)

    var limitAnswerPool by rememberSaveable { mutableStateOf(loadedPreset?.limitAnswerPool ?: true) }
    var isGraded by rememberSaveable { mutableStateOf(loadedPreset?.isGraded ?: false) }
    var allowMultipleGuesses by rememberSaveable { mutableStateOf(loadedPreset?.allowMultipleGuesses ?: true) }

    // --- Selection & Sorting State ---
    var selectionMode by rememberSaveable { mutableStateOf(loadedPreset?.selectionMode ?: SelectionMode.ANY) }
    var selectedTags by rememberSaveable { mutableStateOf<List<String>>(loadedPreset?.selectedTags ?: emptyList()) }
    val listSaver = listSaver<SnapshotStateList<Int>, Int>(save = { it.toList() }, restore = { it.toMutableStateList() })
    val selectedDifficulties = rememberSaveable(saver = listSaver) {
        mutableStateListOf(*(loadedPreset?.selectedDifficulties?.takeIf { it.isNotEmpty() } ?: listOf(1, 2, 3, 4, 5)).toTypedArray())
    }
    var excludeKnown by rememberSaveable { mutableStateOf(loadedPreset?.excludeKnown ?: true) }

    var alphabetStart by rememberSaveable { mutableStateOf(loadedPreset?.alphabetStart ?: "A") }
    var alphabetEnd by rememberSaveable { mutableStateOf(loadedPreset?.alphabetEnd ?: "Z") }
    var filterSide by rememberSaveable { mutableStateOf(loadedPreset?.filterSide ?: CardSide.FRONT) }

    val totalCards = deck.cards.size
    var cardOrderStart by rememberSaveable { mutableIntStateOf(loadedPreset?.cardOrderStart ?: 1) }
    var cardOrderEnd by rememberSaveable { mutableIntStateOf(loadedPreset?.cardOrderEnd ?: (if (totalCards > 0) totalCards else 1)) }

    var timeValue by rememberSaveable { mutableIntStateOf(loadedPreset?.timeValue ?: 7) }
    var timeUnit by rememberSaveable { mutableStateOf(loadedPreset?.timeUnit ?: TimeUnit.DAYS) }
    var filterType by rememberSaveable { mutableStateOf(loadedPreset?.filterType ?: FilterType.EXCLUDE) }

    val maxDeckReviews = remember(deck) { deck.cards.maxOfOrNull { it.reviewedCount } ?: 0 }
    var reviewThreshold by rememberSaveable { mutableIntStateOf(loadedPreset?.reviewCountThreshold ?: 0) }
    var reviewDirection by rememberSaveable { mutableStateOf(loadedPreset?.reviewCountDirection ?: Direction.ASC) }
    var scoreThreshold by rememberSaveable { mutableIntStateOf(loadedPreset?.scoreThreshold ?: 0) }
    var scoreDirection by rememberSaveable { mutableStateOf(loadedPreset?.scoreDirection ?: Direction.ASC) }

    var sortMode by rememberSaveable { mutableStateOf(loadedPreset?.sortMode ?: SortMode.RANDOM) }
    var sortDirection by rememberSaveable { mutableStateOf(loadedPreset?.sortDirection ?: Direction.ASC) }
    var sortSide by rememberSaveable { mutableStateOf(loadedPreset?.sortSide ?: CardSide.FRONT) }

    // --- Logic ---
    // Learn/Practice/Quiz/Games/Smart reorg: the graded/non-graded toggle is gone — each tab fully
    // and permanently determines isGraded (and every other practice-vs-quiz setting) for every
    // mode it offers, with no user override. Typing is the one mode split across two SessionMode
    // values: TYPING (Learn, always ungraded, its own screen) and TYPING_SCORED (Practice/Quiz,
    // same screen, hints forced on/off instead of user-toggled).
    val applyCategory = {
        val gameModes = listOf(SessionMode.ANAGRAM, SessionMode.CROSSWORD, SessionMode.HANGMAN, SessionMode.MEMORY, SessionMode.WORD_SEARCH)
        // Learn-exclusive modes (unlike Flashcard, which Learn shares with Practice/Quiz/Guided):
        // switching away from Learn only needs to reset the selection for these.
        val learnExclusiveModes = listOf(SessionMode.TYPING, SessionMode.FREEFORM, SessionMode.AUDIO)
        if (category == StudyCategory.GAMES) {
            if (selectedMode !in gameModes) selectedMode = modesForCategory(StudyCategory.GAMES).first()
        } else if (category == StudyCategory.LEARN) {
            if (selectedMode !in modesForCategory(StudyCategory.LEARN)) selectedMode = modesForCategory(StudyCategory.LEARN).first()
        } else if (category == StudyCategory.GUIDED) {
            if (selectedMode !in modesForCategory(StudyCategory.GUIDED)) selectedMode = modesForCategory(StudyCategory.GUIDED).first()
        } else {
            if (selectedMode in gameModes || selectedMode in learnExclusiveModes) selectedMode = modesForCategory(StudyCategory.PRACTICE).first()
        }

        // Stored defaults (Settings → Mode Defaults) overlay the session options; anything not stored keeps
        // its current value. The category-forced overrides below always win over them.
        optionValues = optionValues.overlaidWith(modeDefaults[category to selectedMode])

        // List/Matching/Multiple Choice's allowMultipleGuesses is no longer forced here — it's now
        // the user-configurable "show correct on a miss"-style option (AllowMultipleGuesses for
        // List/Multiple Choice, MatchingAllowMultipleGuesses for Matching), resolved at session
        // start from optionValues/ModeOptionDefaults instead (see the onStartSession call below).
        if (category == StudyCategory.LEARN) {
            if (selectedMode == SessionMode.TYPING) { isGraded = false }
            if (selectedMode == SessionMode.FREEFORM) { isGraded = false }
            if (selectedMode == SessionMode.AUDIO) { isGraded = false }
            if (selectedMode == SessionMode.FLASHCARD) { isGraded = false }
        } else if (category == StudyCategory.PRACTICE) {
            if (selectedMode == SessionMode.FLASHCARD) { isGraded = false }
            if (selectedMode == SessionMode.LIST) { isGraded = false }
            if (selectedMode == SessionMode.TYPING_SCORED) { isGraded = false }
            if (selectedMode == SessionMode.MATCHING) { isGraded = false }
            if (selectedMode == SessionMode.MULTIPLE_CHOICE) { isGraded = false }
            if (selectedMode == SessionMode.HEAR_MATCH) { isGraded = false }
            if (selectedMode == SessionMode.LISTEN_TYPE || selectedMode == SessionMode.SEE_SPEAK) { isGraded = false }
        } else if (category == StudyCategory.QUIZ || category == StudyCategory.GUIDED) {
            if (selectedMode == SessionMode.FLASHCARD) { isGraded = true }
            if (selectedMode == SessionMode.LIST) { isGraded = true }
            if (selectedMode == SessionMode.TYPING_SCORED) { isGraded = true }
            if (selectedMode == SessionMode.MATCHING) { isGraded = true }
            if (selectedMode == SessionMode.MULTIPLE_CHOICE) { isGraded = true }
            if (selectedMode == SessionMode.HEAR_MATCH) { isGraded = true }
            if (selectedMode == SessionMode.LISTEN_TYPE || selectedMode == SessionMode.SEE_SPEAK) { isGraded = true }
        }
    }

    LaunchedEffect(selectedMode, category, modeDefaults) { applyCategory() }

    val selectedPool = remember(
        deck, category, selectionMode, selectedTags, selectedDifficulties.toList(),
        excludeKnown, alphabetStart, alphabetEnd, filterSide, cardOrderStart, cardOrderEnd,
        timeValue, timeUnit, filterType, reviewThreshold, reviewDirection, scoreThreshold, scoreDirection
    ) {
        // Guided sessions pick their cards by FSRS due date; the filters don't apply to them.
        if (isGuided) deck.cards.filter { it.isDueForFsrs(System.currentTimeMillis()) }
        else selectCardPool(
            deck, selectionMode, selectedTags, selectedDifficulties, excludeKnown, alphabetStart, alphabetEnd, filterSide,
            cardOrderStart, cardOrderEnd, timeValue, timeUnit, filterType, reviewThreshold, reviewDirection, scoreThreshold, scoreDirection
        )
    }
    val availableCardsCount = selectedPool.size
    val availableByDifficulty = remember(selectedPool) { selectedPool.groupingBy { it.difficulty.value }.eachCount() }

    // Weighted counts are capped by what the current filters leave for that difficulty.
    val preCountOptionContext = ModeOptionContext(maxForDifficulty = { availableByDifficulty[it] ?: 0 }, availableCardsCount = availableCardsCount, category = category, mode = selectedMode, deckDefaultPromptSide = deckDefaultPromptSide)
    fun weightedCountFor(difficulty: Int): Int = DifficultyWeighting.effectiveCountFor(optionValues, difficulty, preCountOptionContext)
    val weightedTotal = DifficultyWeighting.totalFor(optionValues, preCountOptionContext)

    var numberOfCards by rememberSaveable(inputs = arrayOf(availableCardsCount)) { mutableStateOf(loadedPreset?.numCards ?: availableCardsCount) }
    val effectiveCardCount = if (isWeighted) weightedTotal else numberOfCards
    // Now that the session's real card count is known, build the context the rest of this screen
    // actually renders options against - see ModeOptionContext.sessionCardCount's doc comment.
    val optionContext = ModeOptionContext(maxForDifficulty = { availableByDifficulty[it] ?: 0 }, availableCardsCount = availableCardsCount, sessionCardCount = effectiveCardCount, category = category, mode = selectedMode, deckDefaultPromptSide = deckDefaultPromptSide)
    val isMcModeInvalid = selectedMode == SessionMode.MULTIPLE_CHOICE && deck.cards.size < numberOfAnswers
    val hasCards = if (isWeighted) weightedTotal > 0 else availableCardsCount > 0
    val isButtonEnabled = hasCards && !isMcModeInvalid

    val context = LocalContext.current
    val toastMessage = viewModel.toastMessage
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            android.widget.Toast.makeText(context, toastMessage, android.widget.Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }
    var startSessionCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showNotificationExplainer by remember { mutableStateOf(false) }

    // Shared by startSession and pinSession below - both need the exact same gathered
    // AutoSetConfig/effectiveAllowMultipleGuesses, just do different things with them afterward.
    fun buildSessionConfig(): Pair<AutoSetConfig, Boolean> {
        val currentConfig = AutoSetConfig(
            mode = AutoSetCreationMode.ONE, numSets = 1, maxCardsPerSet = effectiveCardCount, selectionMode = if (isGuided) SelectionMode.ANY else selectionMode, selectedTags = selectedTags,
            selectedDifficulties = selectedDifficulties.toList(), excludeKnown = excludeKnown && !isGuided, sortMode = if (isGuided) SortMode.REVIEW_DATE else sortMode, sortDirection = if (isGuided) Direction.ASC else sortDirection,
            sortSide = sortSide, alphabetStart = alphabetStart, alphabetEnd = alphabetEnd, filterSide = filterSide, cardOrderStart = cardOrderStart,
            cardOrderEnd = cardOrderEnd, timeValue = timeValue, timeUnit = timeUnit, filterType = filterType, reviewCountThreshold = reviewThreshold,
            reviewCountDirection = reviewDirection, scoreThreshold = scoreThreshold, scoreDirection = scoreDirection, schedulingMode = if (isGuided) SchedulingMode.FSRS else SchedulingMode.NORMAL,
            difficultyCounts = if (isWeighted) (1..5).associateWith { weightedCountFor(it) } else null,
            audioPlaybackSpeed = AudioPlaybackSpeed.valueIn(optionValues),
            audioReplayCount = AudioReplayCount.valueIn(optionValues),
            audioAutoAdvance = AudioAutoAdvance.valueIn(optionValues),
            audioAnswerDelaySeconds = AudioAnswerDelay.valueIn(optionValues),
            audioNextCardDelaySeconds = AudioNextCardDelay.valueIn(optionValues),
            freeformShowBothSides = FreeformShowBothSides.valueIn(optionValues),
            freeformSwipeNavigation = FreeformSwipeNavigation.valueIn(optionValues),
            typingIgnoreFormatting = TypingIgnoreFormatting.valueIn(optionValues),
            typingDisableAutocorrect = TypingDisableAutocorrect.valueIn(optionValues),
            typingShowLengthHint = TypingShowLengthHint.valueIn(optionValues, optionContext),
            typedListenUseMultipleChoice = TypedListenMultipleChoice.valueIn(optionValues),
            hearMatchAutoPlayAllOptions = HearMatchAutoPlayAll.valueIn(optionValues),
            hideDifficultySelection = HideDifficultySelection.valueIn(optionValues),
            continuousSession = ContinuousSession.isEnabled(optionValues),
            flashcardAutoFlipSeconds = optionValues.flashcardAutoFlipSeconds ?: ModeOptionDefaults.FLASHCARD_AUTO_FLIP_SECONDS,
            flashcardAutoFlip = FlashcardAutoFlip.valueIn(optionValues),
            flashcardDoubleTapToFlip = FlashcardDoubleTap.valueIn(optionValues),
            flashcardRandomizeFirstSide = FlashcardRandomizeSide.valueIn(optionValues),
            requireConfirmTap = optionValues.requireConfirmTap ?: ModeOptionDefaults.requireConfirmTapFor(category, selectedMode),
            autoAdvance = optionValues.autoAdvance ?: ModeOptionDefaults.AUTO_ADVANCE,
            autoAdvanceDelaySeconds = optionValues.autoAdvanceDelaySeconds ?: ModeOptionDefaults.AUTO_ADVANCE_DELAY_SECONDS,
            autoListen = optionValues.autoListen ?: ModeOptionDefaults.AUTO_LISTEN,
            listenStartSound = optionValues.listenStartSound ?: ModeOptionDefaults.LISTEN_START_SOUND,
            listenCorrectSound = optionValues.listenCorrectSound ?: ModeOptionDefaults.LISTEN_CORRECT_SOUND,
            listenIncorrectSound = optionValues.listenIncorrectSound ?: ModeOptionDefaults.LISTEN_INCORRECT_SOUND,
            speakingFrontSpeed = optionValues.speakingFrontSpeed ?: ModeOptionDefaults.SPEAKING_FRONT_SPEED,
            speakingBackSpeed = optionValues.speakingBackSpeed ?: ModeOptionDefaults.SPEAKING_BACK_SPEED,
            listResetPosition = optionValues.listResetPosition ?: ModeOptionDefaults.LIST_RESET_POSITION,
            listDimWrongGuesses = optionValues.listDimWrongGuesses ?: ModeOptionDefaults.LIST_DIM_WRONG_GUESSES,
            listRemoveGuessed = optionValues.listRemoveGuessed ?: ModeOptionDefaults.LIST_REMOVE_GUESSED,
            matchingHighlightStyle = optionValues.matchingHighlightStyle ?: ModeOptionDefaults.MATCHING_HIGHLIGHT_STYLE,
            matchingWrongDelayMs = optionValues.matchingWrongDelayMs ?: ModeOptionDefaults.MATCHING_WRONG_DELAY_MS,
            matchingCorrectHighlightMs = optionValues.matchingCorrectHighlightMs ?: ModeOptionDefaults.MATCHING_CORRECT_HIGHLIGHT_MS,
            matchingShowCorrectDialog = optionValues.matchingShowCorrectDialog ?: ModeOptionDefaults.MATCHING_SHOW_CORRECT_DIALOG,
            anagramFirstLetterHint = optionValues.anagramFirstLetterHint ?: ModeOptionDefaults.ANAGRAM_FIRST_LETTER_HINT,
            anagramUppercase = optionValues.anagramUppercase ?: ModeOptionDefaults.ANAGRAM_UPPERCASE,
            anagramColorVowels = optionValues.anagramColorVowels ?: ModeOptionDefaults.ANAGRAM_COLOR_VOWELS,
            crosswordHighlightWord = optionValues.crosswordHighlightWord ?: ModeOptionDefaults.CROSSWORD_HIGHLIGHT_WORD,
            crosswordAutoAdvanceCell = optionValues.crosswordAutoAdvanceCell ?: ModeOptionDefaults.CROSSWORD_AUTO_ADVANCE_CELL,
            crosswordCompactClues = optionValues.crosswordCompactClues ?: ModeOptionDefaults.CROSSWORD_COMPACT_CLUES,
            hangmanMaxMistakes = optionValues.hangmanMaxMistakes ?: ModeOptionDefaults.HANGMAN_MAX_MISTAKES,
            hangmanHideVisual = optionValues.hangmanHideVisual ?: ModeOptionDefaults.HANGMAN_HIDE_VISUAL,
            memoryGrayMatched = optionValues.memoryGrayMatched ?: ModeOptionDefaults.MEMORY_GRAY_MATCHED,
            memoryPeekSeconds = optionValues.memoryPeekSeconds ?: ModeOptionDefaults.MEMORY_PEEK_SECONDS,
            memoryGridColumnsPortrait = optionValues.memoryGridColumnsPortrait ?: ModeOptionDefaults.MEMORY_GRID_COLUMNS_PORTRAIT,
            memoryGridColumnsLandscape = optionValues.memoryGridColumnsLandscape ?: ModeOptionDefaults.MEMORY_GRID_COLUMNS_LANDSCAPE,
            memoryWrongPairMs = optionValues.memoryWrongPairMs ?: ModeOptionDefaults.MEMORY_WRONG_PAIR_MS,
            memoryCorrectPairMs = optionValues.memoryCorrectPairMs ?: ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS,
            memorySubmitAnswer = optionValues.memorySubmitAnswer ?: ModeOptionDefaults.MEMORY_SUBMIT_ANSWER,
            wordSearchHideFound = optionValues.wordSearchHideFound ?: ModeOptionDefaults.WORD_SEARCH_HIDE_FOUND,
            wordSearchHighlightColor = optionValues.wordSearchHighlightColor ?: ModeOptionDefaults.WORD_SEARCH_HIGHLIGHT_COLOR,
            timedSessionEnabled = optionValues.timedSessionEnabled ?: ModeOptionDefaults.TIMED_SESSION_ENABLED,
            timedSessionDurationSeconds = optionValues.timedSessionDurationSeconds ?: ModeOptionDefaults.TIMED_SESSION_DURATION_SECONDS,
            timedSessionPauseOutsideSession = optionValues.timedSessionPauseOutsideSession ?: ModeOptionDefaults.TIMED_SESSION_PAUSE_OUTSIDE_SESSION,
            timedSessionDisplayUpdateSecondsUnder60 = optionValues.timedSessionDisplayUpdateSecondsUnder60 ?: ModeOptionDefaults.TIMED_SESSION_DISPLAY_UPDATE_SECONDS_UNDER60,
            timedSessionDisplayUpdateSecondsOver60 = optionValues.timedSessionDisplayUpdateSecondsOver60 ?: ModeOptionDefaults.TIMED_SESSION_DISPLAY_UPDATE_SECONDS_OVER60)
        // List/Matching/Multiple Choice's value now comes from their own
        // user-configurable "show correct on a miss" option instead of a
        // category-forced local var (which no longer sets this for any of them).
        val effectiveAllowMultipleGuesses = if (selectedMode in listOf(SessionMode.LIST, SessionMode.MATCHING, SessionMode.MULTIPLE_CHOICE)) {
            optionValues.allowMultipleGuesses ?: ModeOptionDefaults.allowMultipleGuessesFor(category, selectedMode)
        } else allowMultipleGuesses
        return currentConfig to effectiveAllowMultipleGuesses
    }

    // --- Preset dirty-tracking (decision C) --- snapshot of the config a loaded preset was last
    // clean against; null means "no preset loaded, or we haven't captured one yet." Set once on
    // arrival (below) and again, synchronously, by pinAsNewPreset/saveToLoadedPreset.
    var savedPresetSnapshot by remember { mutableStateOf<PresetSnapshot?>(null) }
    LaunchedEffect(Unit) {
        loadedPreset?.let {
            val (config, effectiveAllowMultipleGuesses) = buildSessionConfig()
            savedPresetSnapshot = PresetSnapshot(config, isWeighted, effectiveCardCount, quizPromptSide, numberOfAnswers, showCorrectLetters, limitAnswerPool, isGraded, effectiveAllowMultipleGuesses, maxMemoryTiles, gridDensity, freeformLayoutVertical)
        }
    }
    val isPresetDirty = savedPresetSnapshot?.let { snapshot ->
        val (currentConfig, currentAllowMultipleGuesses) = buildSessionConfig()
        currentConfig != snapshot.config || isWeighted != snapshot.isWeighted || effectiveCardCount != snapshot.numCards ||
            quizPromptSide != snapshot.quizPromptSide || numberOfAnswers != snapshot.numberOfAnswers ||
            showCorrectLetters != snapshot.showCorrectLetters || limitAnswerPool != snapshot.limitAnswerPool ||
            isGraded != snapshot.isGraded || currentAllowMultipleGuesses != snapshot.allowMultipleGuesses ||
            maxMemoryTiles != snapshot.maxMemoryTiles || gridDensity != snapshot.gridDensity ||
            freeformLayoutVertical != snapshot.freeformLayoutVertical
    } ?: false

    val startSession: () -> Unit = {
        val (currentConfig, effectiveAllowMultipleGuesses) = buildSessionConfig()
        val action =
            { onStartSession(selectedMode, isWeighted, effectiveCardCount, quizPromptSide, numberOfAnswers,
                showCorrectLetters, limitAnswerPool, isGraded, effectiveAllowMultipleGuesses,
                maxMemoryTiles, gridDensity,
                showCorrectWords, freeformLayoutVertical,currentConfig) }
        if (selectedMode == SessionMode.AUDIO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) action()
            else if (!notificationPromptShown) { startSessionCallback = action; showNotificationExplainer = true }
            else action() // Already explained: audio works in-app without the notification permission
        } else action()
    }

    // The session-creation screen's Pin button: creates a new SessionPreset (a reusable
    // deck+mode+settings template, NOT a session) from the currently-configured settings — this
    // repurposes the button that used to wrongly create-and-pin a real ActiveSession (see
    // FlashcardViewModel.createPresetFromCurrentConfig). Save updates the already-loaded preset
    // in place instead, with no naming dialog (decision C).
    var showPinPresetDialog by remember { mutableStateOf(false) }
    val pinAsNewPreset: (name: String) -> Unit = { name ->
        val (currentConfig, effectiveAllowMultipleGuesses) = buildSessionConfig()
        val newPreset = viewModel.createPresetFromCurrentConfig(
            deckId = deck.deck.id, name = name.ifBlank { null }, config = currentConfig, sessionMode = selectedMode, category = category,
            isWeighted = isWeighted, numCards = effectiveCardCount, quizPromptSide = quizPromptSide,
            numberOfAnswers = numberOfAnswers, showCorrectLetters = showCorrectLetters, limitAnswerPool = limitAnswerPool,
            isGraded = isGraded, allowMultipleGuesses = effectiveAllowMultipleGuesses, maxMemoryTiles = maxMemoryTiles,
            gridDensity = gridDensity, freeformLayoutVertical = freeformLayoutVertical
        )
        loadedPresetOverride = newPreset
        loadedPresetId = newPreset.id
        savedPresetSnapshot = PresetSnapshot(currentConfig, isWeighted, effectiveCardCount, quizPromptSide, numberOfAnswers, showCorrectLetters, limitAnswerPool, isGraded, effectiveAllowMultipleGuesses, maxMemoryTiles, gridDensity, freeformLayoutVertical)
        showPinPresetDialog = false
        android.widget.Toast.makeText(context, context.getString(R.string.session_pinned_toast), android.widget.Toast.LENGTH_SHORT).show()
    }
    val saveToLoadedPreset: () -> Unit = {
        val current = loadedPreset
        if (current != null) {
            val (currentConfig, effectiveAllowMultipleGuesses) = buildSessionConfig()
            val updated = viewModel.updatePresetInPlace(
                preset = current, config = currentConfig, sessionMode = selectedMode, category = category, isWeighted = isWeighted,
                numCards = effectiveCardCount, quizPromptSide = quizPromptSide, numberOfAnswers = numberOfAnswers,
                showCorrectLetters = showCorrectLetters, limitAnswerPool = limitAnswerPool, isGraded = isGraded,
                allowMultipleGuesses = effectiveAllowMultipleGuesses, maxMemoryTiles = maxMemoryTiles, gridDensity = gridDensity,
                freeformLayoutVertical = freeformLayoutVertical
            )
            loadedPresetOverride = updated
            savedPresetSnapshot = PresetSnapshot(currentConfig, isWeighted, effectiveCardCount, quizPromptSide, numberOfAnswers, showCorrectLetters, limitAnswerPool, isGraded, effectiveAllowMultipleGuesses, maxMemoryTiles, gridDensity, freeformLayoutVertical)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        startSessionCallback?.invoke()
        startSessionCallback = null
    }
    if (showNotificationExplainer) {
        // Explains the optional notification before the system asks for it. Either button starts the session.
        ConfirmationDialog(
            title = getText(R.string.notification_explainer_title),
            text = getText(R.string.notification_explainer_desc),
            confirmButtonText = getText(R.string.notification_explainer_allow),
            onConfirm = {
                showNotificationExplainer = false
                onNotificationPromptShown()
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            dismissButtonText = getText(R.string.notification_explainer_not_now),
            onDismiss = {
                showNotificationExplainer = false
                onNotificationPromptShown()
                startSessionCallback?.invoke()
                startSessionCallback = null
            }
        )
    }

    if (showPinPresetDialog) {
        net.ericclark.studiare.screens.UI_Components.PinNamingDialog(
            defaultName = net.ericclark.studiare.screens.UI_Components.defaultSessionPinName(context, selectedMode, deck.deck.name),
            onDismiss = { showPinPresetDialog = false },
            onConfirm = { name -> pinAsNewPreset(name) }
        )
    }

    val snackbarHostState = remember { SnackbarHostState() }

    // Only tabs with something to show for the current (category, mode) appear, so none ever
    // render empty. Guided already excludes Difficulty Weighting/Timed Session/Continuous Session
    // from every GUIDED_* key in mode-options.yaml, so "sessionoptions" disappears there on its
    // own via the .any{} check below - Filter & Sort needs its own isGuided check since FSRS
    // picking its own cards isn't registry-driven.
    val tabs = remember(category, selectedMode, optionValues, isGuided) {
        buildList {
            add("setup")
            if (modeOptionsFor(category, selectedMode).any { !it.dialogSection && it.isVisible(optionValues) }) add("modeoptions")
            if (modeOptionsFor(category, selectedMode).any { it.dialogSection && it.isVisible(optionValues) }) add("sessionoptions")
            if (!isGuided) add("filter")
            add("cards")
        }
    }
    var selectedTab by rememberSaveable { mutableStateOf("setup") }
    LaunchedEffect(tabs) { if (selectedTab !in tabs) selectedTab = tabs.first() }

    @Composable
    fun tabLabel(id: String): String = when (id) {
        "setup" -> getText(R.string.setup)
        "modeoptions" -> getText(R.string.mode_options)
        "sessionoptions" -> getText(R.string.session_options)
        "filter" -> getText(R.string.filter_and_sort)
        else -> getText(R.string.cards)
    }

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        // Gated on mainContentReady (see its declaration above) so this form's genuinely expensive
        // first composition happens after, not during, the NavHost transition into this screen - a
        // plain fade covers its otherwise-abrupt appearance. DEBUG-only; see mainContentReady's own
        // declaration for why.
        AnimatedVisibility(
            visible = mainContentReady,
            enter = fadeIn(),
            exit = ExitTransition.None
        ) {
        Card(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation)
        ) {
            Column(Modifier.fillMaxHeight()) {
                Text(
                    getText(R.string.study_session_create),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.fillMaxWidth()
                        .padding(start = dimensions.paddingLarge, end = dimensions.paddingLarge, top = dimensions.paddingMedium, bottom = dimensions.paddingSmall)
                )

                ScrollableTabRow(selectedTabIndex = tabs.indexOf(selectedTab).coerceAtLeast(0), edgePadding = dimensions.paddingMedium) {
                    tabs.forEach { id ->
                        Tab(selected = selectedTab == id, onClick = { selectedTab = id }, text = { Text(tabLabel(id)) })
                    }
                }

                Column(
                    // fill = true (the default): always expands to the Card's full remaining
                    // height, so the footer stays pinned at the bottom even when a tab's own
                    // content (e.g. Cards) is short, instead of the Card shrink-wrapping it.
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())
                        .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
                ) {
                    when (selectedTab) {
                        "setup" -> {
                            CategorySelectionSection(
                                category = category,
                                onCategoryChange = { newCategory ->
                                    category = newCategory
                                    selectedMode = modesForCategory(newCategory).first()
                                }
                            )
                            ModeSelectionSection(category = category, mode = selectedMode, onModeChange = { selectedMode = it }, showDivider = false)
                        }
                        "modeoptions" -> ModeOptionsInline(category, selectedMode, optionValues, optionContext) { optionValues = it }
                        "sessionoptions" -> {
                            ModeOptionDialogSections(category, selectedMode, optionValues, optionContext) { optionValues = it }
                            SaveAsDefaultButtons(category, selectedMode, optionValues, viewModel, snackbarHostState, modifier = Modifier.padding(top = dimensions.spacingSmall))
                        }
                        "filter" -> {
                            val selectionState = SelectionSectionState(
                                selectionMode, selectedTags, selectedDifficulties, excludeKnown, alphabetStart, alphabetEnd, filterSide, cardOrderStart, cardOrderEnd,
                                timeValue, timeUnit, filterType, reviewThreshold, reviewDirection, scoreThreshold, scoreDirection, availableTags, allTagDefinitions,
                                availableCardsCount, totalCards, maxDeckReviews)
                            val selectionActions = SelectionSectionActions(
                                { selectionMode = it }, { selectedTags = it }, { diffs -> selectedDifficulties.clear();
                                    selectedDifficulties.addAll(diffs) }, { excludeKnown = it }, { alphabetStart = it },
                                { alphabetEnd = it }, { filterSide = it }, { cardOrderStart = it },
                                { cardOrderEnd = it }, { timeValue = it }, { timeUnit = it },
                                { filterType = it }, { reviewThreshold = it }, { reviewDirection = it },
                                { scoreThreshold = it }, { scoreDirection = it })
                            SelectionModeDialogSection(state = selectionState, actions = selectionActions, isExpanded = true, onToggleExpand = {}, collapsible = false, alphabetical = true)
                            SortModeDialogSection(
                                sortMode, { sortMode = it }, sortDirection, { sortDirection = it }, sortSide,
                                { sortSide = it }, sortExpanded = true, onToggleExpand = {}, showDivider = false, collapsible = false, alphabetical = true)
                            if (isMcModeInvalid) Text(pluralStringResource(R.plurals.mc_requirement, numberOfAnswers), color = MaterialTheme.colorScheme.error)
                        }
                        "cards" -> {
                            if (!isWeighted) {
                                CardCountSection(numberOfCards, availableCardsCount, { numberOfCards = it }, showDivider = false)
                            } else {
                                Text(
                                    getText(R.string.card_count_follows_weighting),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                        }
                    }
                }

                // Card-count preview: pinned above the footer on every tab, tapping it jumps to
                // the Cards tab directly (always one of [tabs], so no guard needed).
                if (selectedTab != "cards") {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        modifier = Modifier.fillMaxWidth()
                            .clickable { selectedTab = "cards" }
                            .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingMedium),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(R.string.cards_preview_format, effectiveCardCount, availableCardsCount),
                            modifier = Modifier.weight(1f),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                if (isMcModeInvalid && selectedTab != "filter") Text(
                    pluralStringResource(R.plurals.mc_requirement, numberOfAnswers),
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = dimensions.paddingLarge)
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(
                    modifier = Modifier.fillMaxWidth().padding(dimensions.paddingMedium),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.cancel))
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    PresetPinSaveButtons(
                        hasLoadedPreset = loadedPreset != null,
                        isDirty = isPresetDirty,
                        onPin = { showPinPresetDialog = true },
                        onSave = saveToLoadedPreset
                    )
                    // Omitted on the last visible tab - nowhere left to advance to, so
                    // Start Session alone gets the spotlight there.
                    val currentTabIndex = tabs.indexOf(selectedTab)
                    if (currentTabIndex in 0 until tabs.lastIndex) {
                        OutlinedButton(
                            onClick = { selectedTab = tabs[currentTabIndex + 1] },
                            modifier = Modifier.defaultMinSize(minHeight = 56.dp),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(R.string.next))
                        }
                    }
                    StartSessionButton(onStart = startSession, enabled = isButtonEnabled, dimensions = dimensions)
                }
            }
        }
        }
    }
}

/**
 * A frozen copy of the config a loaded [SessionPreset] was last clean against — [AutoSetConfig]
 * is a plain `data class`, so structural `!=` against a fresh [AutoSetConfig] does the heavy
 * lifting; the rest are the scalars `buildSessionConfig()` keeps separate from it.
 */
private data class PresetSnapshot(
    val config: AutoSetConfig, val isWeighted: Boolean, val numCards: Int, val quizPromptSide: CardSide,
    val numberOfAnswers: Int, val showCorrectLetters: Boolean, val limitAnswerPool: Boolean, val isGraded: Boolean,
    val allowMultipleGuesses: Boolean, val maxMemoryTiles: Int, val gridDensity: Int, val freeformLayoutVertical: Boolean
)

/**
 * The preset Pin/Save button pair (decision C) — shared by both dialog layouts. Pin always opens
 * the naming dialog and creates a brand-new preset; Save (only shown once there's a loaded preset
 * with unsaved edits) updates it in place with no dialog. Pin's own glyph tracks "this exact
 * config is saved as the loaded preset" (filled) vs. dirty-or-nothing-loaded (outlined) — the
 * same filled/outline convention `SessionTile`'s own `isPinned` glyph already uses.
 */
@Composable
private fun PresetPinSaveButtons(hasLoadedPreset: Boolean, isDirty: Boolean, onPin: () -> Unit, onSave: () -> Unit) {
    if (hasLoadedPreset && isDirty) {
        IconButton(onClick = onSave) {
            Icon(Icons.Default.Save, contentDescription = getText(R.string.save_preset))
        }
    }
    IconButton(onClick = onPin) {
        Icon(
            imageVector = if (hasLoadedPreset && !isDirty) Icons.Default.FilledPushPin else Icons.Outlined.PushPin,
            contentDescription = getText(R.string.pin_preset)
        )
    }
}

/** The Start Session button, with its tactile press-squish animation, shared by both dialog layouts. */
@Composable
private fun StartSessionButton(onStart: () -> Unit, enabled: Boolean, dimensions: StudiareDimensions) {
    val startInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    Button(
        onClick = onStart,
        modifier = Modifier
            .defaultMinSize(minHeight = 56.dp)
            .pressSquish(startInteractionSource, 0.95f),
        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
        enabled = enabled,
        interactionSource = startInteractionSource
    ) { Text(getText(R.string.session_start)) }
}

/** The cards the Selection (Filter & Sort) settings leave available; its size is the available count. */
fun selectCardPool(
    deck: DeckWithCards,
    selectionMode: SelectionMode, selectedTags: List<String>, selectedDifficulties: List<Int>,
    excludeKnown: Boolean, alphabetStart: String, alphabetEnd: String, filterSide: CardSide,
    cardOrderStart: Int, cardOrderEnd: Int, timeValue: Int, timeUnit: TimeUnit, filterType: FilterType,
    reviewThreshold: Int, reviewDirection: Direction, scoreThreshold: Int, scoreDirection: Direction
): List<Card> {

    var pool = deck.cards
    if (excludeKnown) pool = pool.filter { !it.isKnown }

    val timeMultiplier = when (timeUnit) {
        TimeUnit.DAYS -> 24 * 60 * 60 * 1000L
        TimeUnit.WEEKS -> 7 * 24 * 60 * 60 * 1000L
        TimeUnit.MONTHS -> 30 * 24 * 60 * 60 * 1000L
        TimeUnit.YEARS -> 365 * 24 * 60 * 60 * 1000L
    }
    val cutoffTime = System.currentTimeMillis() - (timeValue * timeMultiplier)

    pool = when (selectionMode) {
        SelectionMode.DIFFICULTY -> pool.filter { it.difficulty?.value in selectedDifficulties }
        SelectionMode.TAGS -> pool.filter { card -> card.tags.any { it in selectedTags } }
        SelectionMode.ALPHABET -> {
            val start = alphabetStart.uppercase()
            val end = alphabetEnd.uppercase()
            pool.filter { card ->
                val text = if (filterSide == CardSide.FRONT) card.front else card.back
                val firstChar = text.trim().uppercase(java.util.Locale.getDefault()).firstOrNull()?.toString()
                firstChar != null && firstChar >= start && firstChar <= end
            }
        }
        SelectionMode.CARD_ORDER -> {
            val s = (cardOrderStart - 1).coerceAtLeast(0)
            val e = (cardOrderEnd - 1).coerceAtMost(deck.cards.size - 1)
            if (s <= e && deck.cards.isNotEmpty()) {
                val allowedIds = deck.cards.slice(s..e).map { it.id }.toSet()
                pool.filter { it.id in allowedIds }
            } else emptyList()
        }
        SelectionMode.REVIEW_DATE -> {
            if (filterType == FilterType.INCLUDE) pool.filter { it.reviewedAt != null && it.reviewedAt >= cutoffTime }
            else pool.filter { it.reviewedAt == null || it.reviewedAt < cutoffTime }
        }
        SelectionMode.INCORRECT_DATE -> {
            if (filterType == FilterType.INCLUDE) pool.filter { card -> card.incorrectAttempts.maxOrNull()?.let { last -> last >= cutoffTime } == true }
            else pool.filter { card -> card.incorrectAttempts.isEmpty() || card.incorrectAttempts.maxOrNull()!! < cutoffTime }
        }
        SelectionMode.REVIEW_COUNT -> {
            if (reviewDirection == Direction.ASC) pool.filter { it.reviewedCount <= reviewThreshold }
            else pool.filter { it.reviewedCount >= reviewThreshold }
        }
        SelectionMode.SCORE -> {
            val getScore: (Card) -> Float = { card ->
                val total = card.gradedAttempts.size
                if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
            }
            val threshold = scoreThreshold.toFloat() / 100f
            if (scoreDirection == Direction.ASC) pool.filter { getScore(it) <= threshold }
            else pool.filter { getScore(it) >= threshold }
        }
        else -> pool
    }
    return pool
}

/** The same short "what this category is for" line the Study Hub's empty state ([CategoryPickerContent])
 *  shows under each category button. */
fun categoryDescriptionRes(category: StudyCategory): Int = when (category) {
    StudyCategory.LEARN -> R.string.category_learn_desc
    StudyCategory.PRACTICE -> R.string.category_practice_desc
    StudyCategory.QUIZ -> R.string.category_quiz_desc
    StudyCategory.GAMES -> R.string.category_game_desc
    StudyCategory.GUIDED -> R.string.category_smart_desc
}

/** Category chooser at the top of the session settings. All categories show at once (wrapping), not in a scrolling row. */
@Composable
fun CategorySelectionSection(
    category: StudyCategory,
    onCategoryChange: (StudyCategory) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(title = getText(R.string.category)) {
        TypedChipGrid(
            items = listOf(
                StudyCategory.LEARN, StudyCategory.PRACTICE, StudyCategory.QUIZ, StudyCategory.GUIDED, StudyCategory.GAMES
            ),
            selected = category,
            labelFor = { it.asString() },
            onSelected = onCategoryChange,
            iconFor = { net.ericclark.studiare.screens.UI_Components.categoryIcon(it) },
            showCheckmarkWhenSelected = true
        )
        Spacer(Modifier.height(dimensions.spacingSmall))
        Text(
            text = getText(categoryDescriptionRes(category)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** The short "what this mode does" line shown under the mode chips, keyed by the exact
 *  (category, mode) pair - every pair [modesForCategory] can produce has one. */
fun modeDescriptionRes(category: StudyCategory, mode: SessionMode): Int = when (category to mode) {
    StudyCategory.LEARN to SessionMode.AUDIO -> R.string.mode_learn_audio_desc
    StudyCategory.LEARN to SessionMode.FREEFORM -> R.string.mode_learn_freeform_desc
    StudyCategory.LEARN to SessionMode.TYPING -> R.string.mode_learn_typing_desc
    StudyCategory.LEARN to SessionMode.FLASHCARD -> R.string.mode_learn_flashcard_desc
    StudyCategory.PRACTICE to SessionMode.FLASHCARD -> R.string.mode_practice_flashcard_desc
    StudyCategory.PRACTICE to SessionMode.HEAR_MATCH -> R.string.mode_practice_hear_match_desc
    StudyCategory.PRACTICE to SessionMode.LISTEN_TYPE -> R.string.mode_practice_listen_type_desc
    StudyCategory.PRACTICE to SessionMode.MATCHING -> R.string.mode_practice_matching_desc
    StudyCategory.PRACTICE to SessionMode.MULTIPLE_CHOICE -> R.string.mode_practice_multiple_choice_desc
    StudyCategory.PRACTICE to SessionMode.LIST -> R.string.mode_practice_picking_desc
    StudyCategory.PRACTICE to SessionMode.SEE_SPEAK -> R.string.mode_practice_see_speak_desc
    StudyCategory.PRACTICE to SessionMode.TYPING_SCORED -> R.string.mode_practice_typing_desc
    StudyCategory.QUIZ to SessionMode.FLASHCARD -> R.string.mode_quiz_flashcard_desc
    StudyCategory.QUIZ to SessionMode.HEAR_MATCH -> R.string.mode_quiz_hear_match_desc
    StudyCategory.QUIZ to SessionMode.LISTEN_TYPE -> R.string.mode_quiz_listen_type_desc
    StudyCategory.QUIZ to SessionMode.MATCHING -> R.string.mode_quiz_matching_desc
    StudyCategory.QUIZ to SessionMode.MULTIPLE_CHOICE -> R.string.mode_quiz_multiple_choice_desc
    StudyCategory.QUIZ to SessionMode.LIST -> R.string.mode_quiz_picking_desc
    StudyCategory.QUIZ to SessionMode.SEE_SPEAK -> R.string.mode_quiz_see_speak_desc
    StudyCategory.QUIZ to SessionMode.TYPING_SCORED -> R.string.mode_quiz_typing_desc
    StudyCategory.GAMES to SessionMode.ANAGRAM -> R.string.mode_game_anagram_desc
    StudyCategory.GAMES to SessionMode.CROSSWORD -> R.string.mode_game_crossword_desc
    StudyCategory.GAMES to SessionMode.HANGMAN -> R.string.mode_game_hangman_desc
    StudyCategory.GAMES to SessionMode.MEMORY -> R.string.mode_game_memory_desc
    StudyCategory.GAMES to SessionMode.WORD_SEARCH -> R.string.mode_game_word_search_desc
    StudyCategory.GUIDED to SessionMode.FLASHCARD -> R.string.mode_guided_flashcard_desc
    StudyCategory.GUIDED to SessionMode.HEAR_MATCH -> R.string.mode_guided_hear_match_desc
    StudyCategory.GUIDED to SessionMode.LISTEN_TYPE -> R.string.mode_guided_listen_type_desc
    StudyCategory.GUIDED to SessionMode.MULTIPLE_CHOICE -> R.string.mode_guided_multiple_choice_desc
    StudyCategory.GUIDED to SessionMode.LIST -> R.string.mode_guided_picking_desc
    StudyCategory.GUIDED to SessionMode.SEE_SPEAK -> R.string.mode_guided_see_speak_desc
    StudyCategory.GUIDED to SessionMode.TYPING_SCORED -> R.string.mode_guided_typing_desc
    else -> error("No mode description for $category/$mode")
}

@Composable
fun ModeSelectionSection(
    category: StudyCategory,
    mode: SessionMode,
    onModeChange: (SessionMode) -> Unit,
    showDivider: Boolean = true
) {
    val dimensions = LocalStudiareDimensions.current
    @Composable
    fun chipLabel(m: SessionMode): String = m.asString()
    val modesInPriorityOrder = modesForCategory(category)
    val modeLabels = modesInPriorityOrder.associateWith { chipLabel(it) }
    DialogSection(title = getText(R.string.mode), showDivider = showDivider) {
        TypedChipGrid(
            items = modesInPriorityOrder.sortedBy { modeLabels[it] },
            selected = mode,
            labelFor = { chipLabel(it) },
            onSelected = onModeChange,
            showCheckmarkWhenSelected = true
        )
        Spacer(Modifier.height(dimensions.spacingSmall))
        Text(
            text = getText(modeDescriptionRes(category, mode)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}


