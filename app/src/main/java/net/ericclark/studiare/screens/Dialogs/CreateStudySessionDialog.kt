package net.ericclark.studiare.screens.Dialogs

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import net.ericclark.studiare.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
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
import net.ericclark.studiare.screens.UI_Components.ShowCorrectLetters
import net.ericclark.studiare.screens.UI_Components.ShowCorrectWords
import net.ericclark.studiare.screens.UI_Components.TypingDisableAutocorrect
import net.ericclark.studiare.screens.UI_Components.TypingIgnoreFormatting
import net.ericclark.studiare.screens.UI_Components.TypingShowLengthHint
import net.ericclark.studiare.screens.UI_Components.TypedChipGrid
import net.ericclark.studiare.screens.UI_Components.ModeDefaultSettingsSaver

@Composable
fun CreateStudySessionDialog(
    deck: DeckWithCards,
    initialCategory: StudyCategory,
    onCategoryChosen: (StudyCategory) -> Unit = {}, // Reports the category in use so the next + opens with it
    notificationPromptShown: Boolean = true, // The notification explainer is shown only until it has been seen once
    onNotificationPromptShown: () -> Unit = {},
    availableTags: List<String>,
    allTagDefinitions: List<TagDefinition>,
    modeDefaults: Map<Pair<StudyCategory, SessionMode>, ModeDefaultSettings> = emptyMap(),
    onDismiss: () -> Unit,
    onStartSession: (
        mode: SessionMode, isWeighted: Boolean, numCards: Int, quizPromptSide: CardSide, numAnswers: Int,
        showCorrectLetters: Boolean, limitAnswerPool: Boolean, isGraded: Boolean, allowMultipleGuesses: Boolean,
        maxMemoryTiles: Int, gridDensity: Int,
        showCorrectWords: Boolean, freeformLayoutVertical: Boolean, config: AutoSetConfig
    ) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val defaultPromptSide = remember(deck) {
        val cards = deck.cards
        if (cards.isEmpty()) CardSide.FRONT else {
            val avgFront = cards.map { it.front.length }.average()
            val avgBack = cards.map { it.back.length }.average()
            if (avgBack > (avgFront * 2)) CardSide.BACK else CardSide.FRONT
        }
    }

    // The category can be switched inside the dialog, so it's dialog state seeded from the caller.
    var category by rememberSaveable { mutableStateOf(initialCategory) }
    LaunchedEffect(category) { onCategoryChosen(category) }
    val isGuided = category == StudyCategory.GUIDED

    // --- Session Settings State ---
    var selectedMode by rememberSaveable {
        mutableStateOf(modesForCategory(category).firstOrNull() ?: SessionMode.FLASHCARD)
    }

    // Session options (mode-specific settings and difficulty weighting) live in one ModeDefaultSettings:
    // seeded from Settings → Mode Defaults for this (category, mode) pair, then overridable per session.
    val initialModeDefaults = modeDefaults[category to selectedMode]
    var optionValues by rememberSaveable(stateSaver = ModeDefaultSettingsSaver) {
        mutableStateOf(ModeDefaultSettings(quizPromptSide = defaultPromptSide).overlaidWith(initialModeDefaults))
    }
    val expandedOptionIds = rememberSaveable(saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() })) {
        mutableStateListOf<String>()
    }
    val numberOfAnswers = NumberOfAnswers.valueIn(optionValues)
    val showCorrectLetters = ShowCorrectLetters.valueIn(optionValues, category)
    val maxMemoryTiles = MaxMemoryTiles.valueIn(optionValues)
    val gridDensity = GridDensity.valueIn(optionValues)
    val showCorrectWords = ShowCorrectWords.valueIn(optionValues)
    val freeformLayoutVertical = FreeformLayout.valueIn(optionValues)
    val quizPromptSide = PromptSide.valueIn(optionValues)
    val isWeighted = !isGuided && DifficultyWeighting.isWeighted(optionValues)

    var limitAnswerPool by rememberSaveable { mutableStateOf(true) }
    var isGraded by rememberSaveable { mutableStateOf(false) }
    var allowMultipleGuesses by rememberSaveable { mutableStateOf(true) }

    // --- Selection & Sorting State ---
    var selectionMode by rememberSaveable { mutableStateOf(SelectionMode.ANY) }
    var selectedTags by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val listSaver = listSaver<SnapshotStateList<Int>, Int>(save = { it.toList() }, restore = { it.toMutableStateList() })
    val selectedDifficulties = rememberSaveable(saver = listSaver) { mutableStateListOf(1, 2, 3, 4, 5) }
    var excludeKnown by rememberSaveable { mutableStateOf(true) }

    var alphabetStart by rememberSaveable { mutableStateOf("A") }
    var alphabetEnd by rememberSaveable { mutableStateOf("Z") }
    var filterSide by rememberSaveable { mutableStateOf(CardSide.FRONT) }

    val totalCards = deck.cards.size
    var cardOrderStart by rememberSaveable { mutableIntStateOf(1) }
    var cardOrderEnd by rememberSaveable { mutableIntStateOf(if (totalCards > 0) totalCards else 1) }

    var timeValue by rememberSaveable { mutableIntStateOf(7) }
    var timeUnit by rememberSaveable { mutableStateOf(TimeUnit.DAYS) }
    var filterType by rememberSaveable { mutableStateOf(FilterType.EXCLUDE) }

    val maxDeckReviews = remember(deck) { deck.cards.maxOfOrNull { it.reviewedCount } ?: 0 }
    var reviewThreshold by rememberSaveable { mutableIntStateOf(0) }
    var reviewDirection by rememberSaveable { mutableStateOf(Direction.ASC) }
    var scoreThreshold by rememberSaveable { mutableIntStateOf(0) }
    var scoreDirection by rememberSaveable { mutableStateOf(Direction.ASC) }

    var sortMode by rememberSaveable { mutableStateOf(SortMode.RANDOM) }
    var sortDirection by rememberSaveable { mutableStateOf(Direction.ASC) }
    var sortSide by rememberSaveable { mutableStateOf(CardSide.FRONT) }

    // --- Expansion States ---
    var categoryExpanded by rememberSaveable { mutableStateOf(true) }
    var modeExpanded by rememberSaveable { mutableStateOf(true) }
    var modeSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var selectionExpanded by rememberSaveable { mutableStateOf(true) }
    var sortExpanded by rememberSaveable { mutableStateOf(false) }
    var numberExpanded by rememberSaveable { mutableStateOf(false) }

    // --- Logic ---
    // Learn/Practice/Quiz/Games/Smart reorg: the graded/non-graded toggle is gone — each tab fully
    // and permanently determines isGraded (and every other practice-vs-quiz setting) for every
    // mode it offers, with no user override. Typing is the one mode split across two SessionMode
    // values: TYPING (Learn, always ungraded, its own screen) and TYPING_SCORED (Practice/Quiz,
    // same screen, hints forced on/off instead of user-toggled).
    val applyCategory = {
        val gameModes = listOf(SessionMode.ANAGRAM, SessionMode.CROSSWORD, SessionMode.HANGMAN, SessionMode.MEMORY, SessionMode.WORD_SEARCH)
        val learnModes = listOf(SessionMode.TYPING, SessionMode.FREEFORM, SessionMode.AUDIO)
        if (category == StudyCategory.GAMES) {
            if (selectedMode !in gameModes) selectedMode = modesForCategory(StudyCategory.GAMES).first()
        } else if (category == StudyCategory.LEARN) {
            if (selectedMode !in learnModes) selectedMode = modesForCategory(StudyCategory.LEARN).first()
        } else if (category == StudyCategory.GUIDED) {
            if (selectedMode !in modesForCategory(StudyCategory.GUIDED)) selectedMode = modesForCategory(StudyCategory.GUIDED).first()
        } else {
            if (selectedMode in gameModes || selectedMode in learnModes) selectedMode = modesForCategory(StudyCategory.PRACTICE).first()
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
        } else if (category == StudyCategory.PRACTICE) {
            if (selectedMode == SessionMode.FLASHCARD) { isGraded = false }
            if (selectedMode == SessionMode.LIST) { isGraded = false }
            if (selectedMode == SessionMode.TYPING_SCORED) { isGraded = false }
            if (selectedMode == SessionMode.MATCHING) { isGraded = false }
            if (selectedMode == SessionMode.MULTIPLE_CHOICE) { isGraded = false }
            if (selectedMode == SessionMode.TYPED_LISTEN || selectedMode == SessionMode.SPOKEN_LISTEN) { isGraded = false }
        } else if (category == StudyCategory.QUIZ || category == StudyCategory.GUIDED) {
            if (selectedMode == SessionMode.FLASHCARD) { isGraded = true }
            if (selectedMode == SessionMode.LIST) { isGraded = true }
            if (selectedMode == SessionMode.TYPING_SCORED) { isGraded = true }
            if (selectedMode == SessionMode.MATCHING) { isGraded = true }
            if (selectedMode == SessionMode.MULTIPLE_CHOICE) { isGraded = true }
            if (selectedMode == SessionMode.TYPED_LISTEN || selectedMode == SessionMode.SPOKEN_LISTEN) { isGraded = true }
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
    val optionContext = ModeOptionContext(maxForDifficulty = { availableByDifficulty[it] ?: 0 }, availableCardsCount = availableCardsCount, category = category, mode = selectedMode)
    fun weightedCountFor(difficulty: Int): Int = DifficultyWeighting.effectiveCountFor(optionValues, difficulty, optionContext)
    val weightedTotal = DifficultyWeighting.totalFor(optionValues, optionContext)

    var numberOfCards by rememberSaveable(inputs = arrayOf(availableCardsCount)) { mutableStateOf(availableCardsCount) }
    val effectiveCardCount = if (isWeighted) weightedTotal else numberOfCards
    val isMcModeInvalid = selectedMode == SessionMode.MULTIPLE_CHOICE && deck.cards.size < numberOfAnswers
    val hasCards = if (isWeighted) weightedTotal > 0 else availableCardsCount > 0
    val isButtonEnabled = hasCards && !isMcModeInvalid

    val context = LocalContext.current
    var startSessionCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        startSessionCallback?.invoke()
        startSessionCallback = null
    }
    var showNotificationExplainer by remember { mutableStateOf(false) }
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

    val configuration = LocalConfiguration.current
    val useSideBySide = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            modifier = if (useSideBySide) Modifier.fillMaxHeight(0.9f).fillMaxWidth(0.9f)
                else Modifier.fillMaxHeight(0.98f).fillMaxWidth(0.98f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingMedium)) {

                if (!useSideBySide)
                {
                    Box(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = getText(R.string.study_session_create),
                            style = MaterialTheme.typography.headlineMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(dimensions.spacingSmall))
                }


                if (useSideBySide) {
                    // --- LANDSCAPE LAYOUT ---
                    // Bypass the Pager and Tabs entirely. Split the screen directly.
                    Row(modifier = Modifier.weight(1f).fillMaxWidth()) {

                        // LEFT COLUMN: Session Settings + Card Count
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(end = dimensions.paddingMedium)
                        ) {
                            CategorySelectionSection(
                                category = category,
                                isExpanded = categoryExpanded,
                                onToggle = { categoryExpanded = !categoryExpanded },
                                onCategoryChange = { newCategory ->
                                    category = newCategory
                                    selectedMode = modesForCategory(newCategory).first()
                                }
                            )
                            ModeSelectionSection(
                                category = category,
                                mode = selectedMode,
                                onModeChange = { selectedMode = it },
                                isExpanded = modeExpanded,
                                onExpandedChange = { modeExpanded = it },
                                isFsrs = false
                            )
                            ModeSettingsSection(selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it }, optionValues, optionContext) { optionValues = it }
                            ModeOptionDialogSections(optionContext.category, selectedMode, optionValues, optionContext, { optionValues = it }, expandedOptionIds)

                            Spacer(Modifier.height(dimensions.spacingMedium))

                            // Number of cards explicitly placed in the left column for landscape (weighting sets its own total)
                            if (!isWeighted) CardCountSection(numberOfCards, availableCardsCount, numberExpanded, { numberExpanded = it }, { numberOfCards = it })
                        }

                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // RIGHT COLUMN: Filter & Sort
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState())
                                .padding(start = dimensions.paddingMedium)
                        ) {
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

                            if (!isGuided) SelectionModeDialogSection(
                                state = selectionState, actions = selectionActions, isExpanded = selectionExpanded, onToggleExpand = { selectionExpanded = !selectionExpanded })

                            if (!isGuided) SortModeDialogSection(
                                sortMode, { sortMode = it }, sortDirection, { sortDirection = it }, sortSide,
                                { sortSide = it }, sortExpanded, { sortExpanded = !sortExpanded })
                        }
                    }
                } else {
                    // --- PORTRAIT LAYOUT ---
                    // M3 Expressive: one continuous scroll instead of tabs hiding Filter & Sort
                    // behind a tap — mirrors the wide layout's "everything visible" approach,
                    // just stacked instead of side-by-side.
                    Column(
                        modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())
                    ) {
                        Text(
                            getText(R.string.session_settings),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                        )
                        CategorySelectionSection(
                            category = category,
                            isExpanded = categoryExpanded,
                            onToggle = { categoryExpanded = !categoryExpanded },
                            onCategoryChange = { newCategory ->
                                category = newCategory
                                selectedMode = modesForCategory(newCategory).first()
                            }
                        )
                        ModeSelectionSection(
                            category = category,
                            mode = selectedMode,
                            onModeChange = { selectedMode = it },
                            isExpanded = modeExpanded,
                            onExpandedChange = { modeExpanded = it },
                            isFsrs = false
                        )
                        ModeSettingsSection(selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it }, optionValues, optionContext) { optionValues = it }
                        ModeOptionDialogSections(optionContext.category, selectedMode, optionValues, optionContext, { optionValues = it }, expandedOptionIds)

                        Spacer(Modifier.height(dimensions.spacingSmall))

                        if (!isGuided) Text(
                            getText(R.string.filter_and_sort),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                        )

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
                        if (!isGuided) SelectionModeDialogSection(
                            state = selectionState, actions = selectionActions, isExpanded = selectionExpanded, onToggleExpand = { selectionExpanded = !selectionExpanded })
                        if (!isGuided) SortModeDialogSection(
                            sortMode, { sortMode = it }, sortDirection, { sortDirection = it }, sortSide,
                            { sortSide = it }, sortExpanded, { sortExpanded = !sortExpanded })
                    }
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    if (!isWeighted) CardCountSection(numberOfCards, availableCardsCount, numberExpanded, { numberExpanded = it }, { numberOfCards = it })
                    Spacer(Modifier.height(dimensions.spacingSmall))
                }

                if (isMcModeInvalid) Text(pluralStringResource(R.plurals.mc_requirement, numberOfAnswers), color = MaterialTheme.colorScheme.error)

                // Tactile squish for the main Start button
                val startInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                val isStartPressed by startInteractionSource.collectIsPressedAsState()
                val startScale by androidx.compose.animation.core.animateFloatAsState(
                    targetValue = if (isStartPressed) 0.95f else 1f,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                    ),
                    label = "startSquish"
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.cancel))
                    }

                    if (useSideBySide) {
                        Text(
                            text = getText(R.string.study_session_create),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Button(
                        onClick = {
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
                                memoryWrongPairMs = optionValues.memoryWrongPairMs ?: ModeOptionDefaults.MEMORY_WRONG_PAIR_MS,
                                memoryCorrectPairMs = optionValues.memoryCorrectPairMs ?: ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS,
                                memorySubmitAnswer = optionValues.memorySubmitAnswer ?: ModeOptionDefaults.MEMORY_SUBMIT_ANSWER,
                                wordSearchHideFound = optionValues.wordSearchHideFound ?: ModeOptionDefaults.WORD_SEARCH_HIDE_FOUND,
                                wordSearchHighlightColor = optionValues.wordSearchHighlightColor ?: ModeOptionDefaults.WORD_SEARCH_HIGHLIGHT_COLOR)
                            // List/Matching/Multiple Choice's value now comes from their own
                            // user-configurable "show correct on a miss" option instead of a
                            // category-forced local var (which no longer sets this for any of them).
                            val effectiveAllowMultipleGuesses = if (selectedMode in listOf(SessionMode.LIST, SessionMode.MATCHING, SessionMode.MULTIPLE_CHOICE)) {
                                optionValues.allowMultipleGuesses ?: ModeOptionDefaults.allowMultipleGuessesFor(category, selectedMode)
                            } else allowMultipleGuesses
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
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 56.dp)
                            .scale(startScale),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                        enabled = isButtonEnabled,
                        interactionSource = startInteractionSource
                    ) { Text(getText(R.string.session_start)) }
                }
            }
        }
    }
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

/** Category chooser at the top of the session settings. All categories show at once (wrapping), not in a scrolling row. */
@Composable
fun CategorySelectionSection(
    category: StudyCategory,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    onCategoryChange: (StudyCategory) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(
        title = getText(R.string.category),
        subtitle = category.asString(),
        isExpanded = isExpanded,
        onToggle = onToggle
    ) {
        TypedChipGrid(
            items = listOf(
                StudyCategory.LEARN, StudyCategory.PRACTICE, StudyCategory.QUIZ, StudyCategory.GAMES, StudyCategory.GUIDED
            ),
            selected = category,
            labelFor = { it.asString() },
            onSelected = onCategoryChange
        )
    }
}

@Composable
fun ModeSelectionSection(
    category: StudyCategory,
    mode: SessionMode,
    onModeChange: (SessionMode) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isFsrs: Boolean // New parameter
) {
    val dimensions = LocalStudiareDimensions.current
    @Composable
    fun chipLabel(m: SessionMode): String = m.asString()
    DialogSection(
        title = getText(R.string.mode) ,
        subtitle = chipLabel(mode),
        isExpanded = isExpanded,
        onToggle = { onExpandedChange(!isExpanded) }) {

        TypedChipGrid(
            items = modesForCategory(category),
            selected = mode,
            labelFor = { chipLabel(it) },
            onSelected = onModeChange
        )
    }
}


/** Mode Settings: the mode's inline options (the ones with no section of their own) in one collapsible section. */
@Composable
fun ModeSettingsSection(
    mode: SessionMode, isExpanded: Boolean, onToggle: (Boolean) -> Unit,
    values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit
) {
    DialogSection(
        title = getText(R.string.mode_settings),
        subtitle = getText(R.string.configure) + " " + mode.asString(),
        isExpanded = isExpanded,
        onToggle = { onToggle(!isExpanded) },
        contentTopSpacing = 0.dp
    ) {
        ModeOptionsInline(context.category, mode, values, context, onChange)
    }
}
