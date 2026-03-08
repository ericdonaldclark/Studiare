package net.ericclark.studiare.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import net.ericclark.studiare.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import net.ericclark.studiare.R
import androidx.compose.ui.res.pluralStringResource
import net.ericclark.studiare.components.*

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreateStudySessionDialog(
    deck: DeckWithCards,
    availableTags: List<String>,
    allTagDefinitions: List<TagDefinition>,
    onDismiss: () -> Unit,
    onStartSession: (
        mode: SessionMode, isWeighted: Boolean, numCards: Int, quizPromptSide: CardSide, numAnswers: Int,
        showCorrectLetters: Boolean, limitAnswerPool: Boolean, isGraded: Boolean, selectAnswer: Boolean,
        allowMultipleGuesses: Boolean, enableStt: Boolean, hideAnswerText: Boolean, fingersAndToes: Boolean,
        maxMemoryTiles: Int, gridDensity: Int, showCorrectWords: Boolean, config: AutoSetConfig
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

    // --- Session Settings State ---
    var selectedPreset by rememberSaveable { mutableStateOf(StudyPreset.STUDY) }
    var selectedMode by rememberSaveable { mutableStateOf(SessionMode.FLASHCARD) }

    // Mode specific options
    var isWeighted by rememberSaveable { mutableStateOf(false) }
    var numberOfAnswers by rememberSaveable { mutableStateOf(4) }
    var showCorrectLetters by rememberSaveable { mutableStateOf(true) }
    var limitAnswerPool by rememberSaveable { mutableStateOf(true) }
    var isGraded by rememberSaveable { mutableStateOf(false) }
    var selectAnswer by rememberSaveable { mutableStateOf(false) }
    var allowMultipleGuesses by rememberSaveable { mutableStateOf(true) }
    var enableStt by rememberSaveable { mutableStateOf(false) }
    var hideAnswerText by rememberSaveable { mutableStateOf(false) }
    var fingersAndToes by rememberSaveable { mutableStateOf(false) }
    var maxMemoryTiles by rememberSaveable { mutableStateOf(20) }
    var gridDensity by rememberSaveable { mutableStateOf(2) }
    var showCorrectWords by rememberSaveable { mutableStateOf(true) }
    var quizPromptSide by rememberSaveable { mutableStateOf(defaultPromptSide) }

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
    var modeExpanded by rememberSaveable { mutableStateOf(true) }
    var modeSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var selectionExpanded by rememberSaveable { mutableStateOf(true) }
    var sortExpanded by rememberSaveable { mutableStateOf(false) }
    var promptSideExpanded by rememberSaveable { mutableStateOf(false) }
    var numberExpanded by rememberSaveable { mutableStateOf(false) }

    // --- Logic ---
    val applyPreset: (StudyPreset) -> Unit = { preset ->
        selectedPreset = preset
        if (preset == StudyPreset.GAMES) {
            if (selectedMode !in listOf(SessionMode.ANAGRAM, SessionMode.CROSSWORD, SessionMode.HANGMAN, SessionMode.MEMORY)) selectedMode = SessionMode.ANAGRAM
        } else {
            if (selectedMode in listOf(SessionMode.ANAGRAM, SessionMode.CROSSWORD, SessionMode.HANGMAN, SessionMode.MEMORY)) selectedMode = SessionMode.FLASHCARD
        }

        if (preset == StudyPreset.STUDY) {
            if (selectedMode == SessionMode.FLASHCARD) { isGraded = false; selectAnswer = false }
            if (selectedMode == SessionMode.TYPING) { isGraded = false; showCorrectLetters = true }
            if (selectedMode == SessionMode.MATCHING || selectedMode == SessionMode.MULTIPLE_CHOICE) { isGraded = false; allowMultipleGuesses = true }
            if (selectedMode == SessionMode.AUDIO) { isGraded = false; enableStt = false; hideAnswerText = false }
        } else if (preset == StudyPreset.QUIZ) {
            if (selectedMode == SessionMode.FLASHCARD) { isGraded = true; selectAnswer = true }
            if (selectedMode == SessionMode.TYPING) { isGraded = true; showCorrectLetters = true }
            if (selectedMode == SessionMode.MATCHING || selectedMode == SessionMode.MULTIPLE_CHOICE) { isGraded = true; allowMultipleGuesses = false }
            if (selectedMode == SessionMode.AUDIO) { isGraded = true; enableStt = true; hideAnswerText = true }
        }
    }

    LaunchedEffect(selectedMode) { applyPreset(selectedPreset) }
    LaunchedEffect(isGraded, selectedMode) { if (selectedMode == SessionMode.AUDIO && isGraded) enableStt = true }

    val availableCardsCount = remember(
        deck, selectionMode, selectedTags, selectedDifficulties.toList(),
        excludeKnown, alphabetStart, alphabetEnd, filterSide, cardOrderStart, cardOrderEnd,
        timeValue, timeUnit, filterType, reviewThreshold, reviewDirection, scoreThreshold, scoreDirection
    ) {
        calculateAvailableCardsCount(
            deck, selectionMode, selectedTags, selectedDifficulties, excludeKnown, alphabetStart, alphabetEnd, filterSide,
            cardOrderStart, cardOrderEnd, timeValue, timeUnit, filterType, reviewThreshold, reviewDirection, scoreThreshold, scoreDirection
        )
    }

    var numberOfCards by rememberSaveable(inputs = arrayOf(availableCardsCount)) { mutableStateOf(availableCardsCount) }
    val isMcModeInvalid = selectedMode == SessionMode.MULTIPLE_CHOICE && deck.cards.size < numberOfAnswers
    val isButtonEnabled = availableCardsCount > 0 && !isMcModeInvalid

    val context = LocalContext.current
    var startSessionCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        startSessionCallback?.invoke()
        startSessionCallback = null
    }

    val configuration = LocalConfiguration.current
    val useSideBySide = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            modifier = Modifier.fillMaxHeight(0.9f).fillMaxWidth(0.9f),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {

                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = getText(R.string.study_session_create),
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = getText(R.string.close))
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(top = dimensions.spacingMedium, bottom = dimensions.spacingSmall))

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = false,
                    verticalAlignment = Alignment.Top
                ) { page ->
                    if (useSideBySide) {
                        // --- LANDSCAPE LAYOUT ---
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = dimensions.paddingMedium)) {
                                if (page == 0) {
                                    TopSliderDialogSection(
                                        listOf(StudyPreset.STUDY.asString(), StudyPreset.QUIZ.asString(), StudyPreset.GAMES.asString()),
                                        selectedPreset.asString()
                                    ) { applyPreset(it.toStudyPreset()) }
                                    Spacer(Modifier.height(dimensions.spacingMedium))

                                    // FORCE SEPARATION OF BRANCHES
                                    when (selectedPreset) {
                                        StudyPreset.STUDY -> {
                                            ModeSelectionSection(
                                                selectedPreset, selectedMode, { selectedMode = it }, modeExpanded,
                                                { modeExpanded = it }, isFsrs = false)
                                        }
                                        StudyPreset.QUIZ -> {
                                            ModeSelectionSection(
                                                selectedPreset, selectedMode, { selectedMode = it }, modeExpanded,
                                                { modeExpanded = it }, isFsrs = false)
                                        }
                                        StudyPreset.GAMES -> {
                                            ModeSelectionSection(
                                                selectedPreset, selectedMode, { selectedMode = it }, modeExpanded,
                                                { modeExpanded = it }, isFsrs = false)
                                        }
                                    }
                                } else {
                                    val selectionState = SelectionSectionState(
                                        selectionMode, selectedTags, selectedDifficulties, excludeKnown, alphabetStart, alphabetEnd, filterSide, cardOrderStart,
                                        cardOrderEnd, timeValue, timeUnit, filterType, reviewThreshold, reviewDirection, scoreThreshold, scoreDirection,
                                        availableTags, allTagDefinitions, availableCardsCount, totalCards, maxDeckReviews)
                                    val selectionActions = SelectionSectionActions(
                                        { selectionMode = it }, { selectedTags = it },
                                        { diffs -> selectedDifficulties.clear(); selectedDifficulties.addAll(diffs) },
                                        { excludeKnown = it }, { alphabetStart = it },
                                        { alphabetEnd = it }, { filterSide = it },
                                        { cardOrderStart = it }, { cardOrderEnd = it },
                                        { timeValue = it }, { timeUnit = it }, { filterType = it },
                                        { reviewThreshold = it }, { reviewDirection = it },
                                        { scoreThreshold = it }, { scoreDirection = it })
                                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                        SelectionModeDialogSection(state = selectionState, actions = selectionActions, isExpanded = selectionExpanded, onToggleExpand = { selectionExpanded = !selectionExpanded })
                                    }
                                }
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = dimensions.paddingMedium)) {
                                if (page == 0) {
                                    // FORCE SEPARATION OF BRANCHES
                                    when (selectedPreset) {
                                        StudyPreset.STUDY -> {
                                            ModeSettingsSection(
                                                selectedPreset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                                                isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                                                showCorrectLetters, { showCorrectLetters = it }, isGraded,
                                                { isGraded = it }, selectAnswer, { selectAnswer = it },
                                                allowMultipleGuesses, { allowMultipleGuesses = it }, enableStt,
                                                { enableStt = it }, hideAnswerText, { hideAnswerText = it }, fingersAndToes,
                                                { fingersAndToes = it }, maxMemoryTiles, { maxMemoryTiles = it },
                                                gridDensity, { gridDensity = it }, showCorrectWords,
                                                { showCorrectWords = it })
                                        }
                                        StudyPreset.QUIZ -> {
                                            ModeSettingsSection(
                                                selectedPreset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                                                isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                                                showCorrectLetters, { showCorrectLetters = it }, isGraded,
                                                { isGraded = it }, selectAnswer, { selectAnswer = it },
                                                allowMultipleGuesses, { allowMultipleGuesses = it }, enableStt,
                                                { enableStt = it }, hideAnswerText, { hideAnswerText = it }, fingersAndToes,
                                                { fingersAndToes = it }, maxMemoryTiles, { maxMemoryTiles = it },
                                                gridDensity, { gridDensity = it }, showCorrectWords,
                                                { showCorrectWords = it })
                                        }
                                        StudyPreset.GAMES -> {
                                            ModeSettingsSection(
                                                selectedPreset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                                                isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                                                showCorrectLetters, { showCorrectLetters = it }, isGraded,
                                                { isGraded = it }, selectAnswer, { selectAnswer = it },
                                                allowMultipleGuesses, { allowMultipleGuesses = it }, enableStt,
                                                { enableStt = it }, hideAnswerText, { hideAnswerText = it }, fingersAndToes,
                                                { fingersAndToes = it }, maxMemoryTiles, { maxMemoryTiles = it },
                                                gridDensity, { gridDensity = it }, showCorrectWords,
                                                { showCorrectWords = it })
                                        }
                                    }

                                    DialogSection(
                                        title = getText(R.string.prompt_side),
                                        subtitle = quizPromptSide.asString(),
                                        isExpanded = promptSideExpanded,
                                        onToggle = { promptSideExpanded = !promptSideExpanded }) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                                            ToggleButton(
                                                CardSide.FRONT.asString(), quizPromptSide == CardSide.FRONT,
                                                { quizPromptSide = CardSide.FRONT }, Modifier.weight(1f))
                                            ToggleButton(
                                                CardSide.BACK.asString(), quizPromptSide == CardSide.BACK,
                                                { quizPromptSide = CardSide.BACK }, Modifier.weight(1f))
                                        }
                                    }
                                    CardCountSection(numberOfCards, availableCardsCount, numberExpanded, { numberExpanded = it }, { numberOfCards = it })
                                } else {
                                    SortModeDialogSection(
                                        sortMode, { sortMode = it }, sortDirection, { sortDirection = it },
                                        sortSide, { sortSide = it }, sortExpanded, { sortExpanded = !sortExpanded })
                                    CardCountSection(numberOfCards, availableCardsCount, numberExpanded, { numberExpanded = it }, { numberOfCards = it })
                                }
                            }
                        }
                    } else {
                        // --- PORTRAIT LAYOUT ---
                        if (page == 0) {
                            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                TopSliderDialogSection(
                                    options = listOf(StudyPreset.STUDY.asString(), StudyPreset.QUIZ.asString(), StudyPreset.GAMES.asString()),
                                    selectedMode = selectedPreset.asString(),
                                    onModeChange = { applyPreset(it.toStudyPreset()) }
                                )
                                Spacer(Modifier.height(dimensions.spacingMedium))

                                // FORCE SEPARATION OF BRANCHES
                                when (selectedPreset) {
                                    StudyPreset.STUDY -> {
                                        ModeSelectionSection(
                                            selectedPreset, selectedMode, { selectedMode = it }, modeExpanded,
                                            { modeExpanded = it }, isFsrs = false)
                                        ModeSettingsSection(
                                            selectedPreset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                                            isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                                            showCorrectLetters, { showCorrectLetters = it }, isGraded, { isGraded = it },
                                            selectAnswer, { selectAnswer = it }, allowMultipleGuesses,
                                            { allowMultipleGuesses = it }, enableStt, { enableStt = it }, hideAnswerText,
                                            { hideAnswerText = it }, fingersAndToes, { fingersAndToes = it },
                                            maxMemoryTiles, { maxMemoryTiles = it }, gridDensity, { gridDensity = it },
                                            showCorrectWords, { showCorrectWords = it })
                                    }
                                    StudyPreset.QUIZ -> {
                                        ModeSelectionSection(
                                            selectedPreset, selectedMode, { selectedMode = it }, modeExpanded,
                                            { modeExpanded = it }, isFsrs = false)
                                        ModeSettingsSection(
                                            selectedPreset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                                            isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                                            showCorrectLetters, { showCorrectLetters = it }, isGraded, { isGraded = it },
                                            selectAnswer, { selectAnswer = it }, allowMultipleGuesses,
                                            { allowMultipleGuesses = it }, enableStt, { enableStt = it }, hideAnswerText,
                                            { hideAnswerText = it }, fingersAndToes, { fingersAndToes = it },
                                            maxMemoryTiles, { maxMemoryTiles = it }, gridDensity, { gridDensity = it },
                                            showCorrectWords, { showCorrectWords = it })
                                    }
                                    StudyPreset.GAMES -> {
                                        ModeSelectionSection(
                                            selectedPreset, selectedMode, { selectedMode = it }, modeExpanded,
                                            { modeExpanded = it }, isFsrs = false)
                                        ModeSettingsSection(
                                            selectedPreset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                                            isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                                            showCorrectLetters, { showCorrectLetters = it }, isGraded, { isGraded = it },
                                            selectAnswer, { selectAnswer = it }, allowMultipleGuesses,
                                            { allowMultipleGuesses = it }, enableStt, { enableStt = it }, hideAnswerText,
                                            { hideAnswerText = it }, fingersAndToes, { fingersAndToes = it },
                                            maxMemoryTiles, { maxMemoryTiles = it }, gridDensity, { gridDensity = it },
                                            showCorrectWords, { showCorrectWords = it })
                                    }
                                }

                                DialogSection(
                                    title = getText(R.string.prompt_side),
                                    subtitle = quizPromptSide.asString(),
                                    isExpanded = promptSideExpanded,
                                    onToggle = { promptSideExpanded = !promptSideExpanded }) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                                        ToggleButton(CardSide.FRONT.asString(), quizPromptSide == CardSide.FRONT, { quizPromptSide = CardSide.FRONT }, Modifier.weight(1f))
                                        ToggleButton(CardSide.BACK.asString(), quizPromptSide == CardSide.BACK, { quizPromptSide = CardSide.BACK }, Modifier.weight(1f))
                                    }
                                }
                            }
                        } else {
                            // Filters Page
                            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
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
                                SelectionModeDialogSection(
                                    state = selectionState, actions = selectionActions, isExpanded = selectionExpanded, onToggleExpand = { selectionExpanded = !selectionExpanded })
                                SortModeDialogSection(
                                    sortMode, { sortMode = it }, sortDirection, { sortDirection = it }, sortSide,
                                    { sortSide = it }, sortExpanded, { sortExpanded = !sortExpanded })
                            }
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingSmall))
                if (!useSideBySide) {
                    HorizontalDivider()
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    CardCountSection(numberOfCards, availableCardsCount, numberExpanded, { numberExpanded = it }, { numberOfCards = it })
                    Spacer(Modifier.height(dimensions.spacingSmall))
                }

                if (isMcModeInvalid) Text(pluralStringResource(R.plurals.mc_requirement, numberOfAnswers), color = MaterialTheme.colorScheme.error)

                Button(
                    onClick = {
                        val currentConfig = AutoSetConfig(
                            mode = AutoSetCreationMode.ONE, numSets = 1, maxCardsPerSet = numberOfCards, selectionMode = selectionMode, selectedTags = selectedTags,
                            selectedDifficulties = selectedDifficulties.toList(), excludeKnown = excludeKnown, sortMode = sortMode, sortDirection = sortDirection,
                            sortSide = sortSide, alphabetStart = alphabetStart, alphabetEnd = alphabetEnd, filterSide = filterSide, cardOrderStart = cardOrderStart,
                            cardOrderEnd = cardOrderEnd, timeValue = timeValue, timeUnit = timeUnit, filterType = filterType, reviewCountThreshold = reviewThreshold,
                            reviewCountDirection = reviewDirection, scoreThreshold = scoreThreshold, scoreDirection = scoreDirection, schedulingMode = SchedulingMode.NORMAL)
                        val action =
                            { onStartSession(selectedMode, isWeighted, numberOfCards, quizPromptSide, numberOfAnswers,
                                showCorrectLetters, limitAnswerPool, isGraded, selectAnswer,
                                allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes,
                                maxMemoryTiles, gridDensity, showCorrectWords, currentConfig) }
                        if (selectedMode == SessionMode.AUDIO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) action()
                            else { startSessionCallback = action; permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        } else action()
                    },
                    modifier = Modifier.fillMaxWidth(if (useSideBySide) 0.5f else 1f).align(Alignment.CenterHorizontally),
                    enabled = isButtonEnabled
                ) { Text(getText(R.string.session_start)) }

                Spacer(Modifier.height(dimensions.spacingSmall))

                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Transparent, RoundedCornerShape(dimensions.cornerRadiusMedium))
                        .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                ) {
                    Tab(selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text(getText(R.string.session_settings)) })
                    Tab(selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text(getText(R.string.filter_and_sort)) })
                }
            }
        }
    }
}

fun calculateAvailableCardsCount(
    deck: DeckWithCards,
    selectionMode: SelectionMode, selectedTags: List<String>, selectedDifficulties: List<Int>,
    excludeKnown: Boolean, alphabetStart: String, alphabetEnd: String, filterSide: CardSide,
    cardOrderStart: Int, cardOrderEnd: Int, timeValue: Int, timeUnit: TimeUnit, filterType: FilterType,
    reviewThreshold: Int, reviewDirection: Direction, scoreThreshold: Int, scoreDirection: Direction,
    schedulingMode: SchedulingMode = SchedulingMode.NORMAL
): Int {

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
    return pool.size
}

@Composable
fun ModeSelectionSection(
    preset: StudyPreset,
    mode: SessionMode,
    onModeChange: (SessionMode) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    isFsrs: Boolean // New parameter
) {
    val dimensions = LocalStudiareDimensions.current
    DialogSection(
        title = getText(R.string.mode) ,
        subtitle = mode.asString(),
        isExpanded = isExpanded,
        onToggle = { onExpandedChange(!isExpanded) }) {
        if (preset == StudyPreset.GAMES) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    ToggleButton(
                        text = SessionMode.ANAGRAM.asString(),
                        isSelected = mode == SessionMode.ANAGRAM,
                        onClick = { onModeChange(SessionMode.ANAGRAM) },
                        modifier = Modifier.weight(1f),
                        enabled = !isFsrs // Disable in FSRS
                    )
                    ToggleButton(
                        text = SessionMode.CROSSWORD.asString(),
                        isSelected = mode == SessionMode.CROSSWORD,
                        onClick = { onModeChange(SessionMode.CROSSWORD) },
                        modifier = Modifier.weight(1f),
                        enabled = !isFsrs // Disable in FSRS
                    )
                }
                Spacer(Modifier.height(dimensions.spacingSmall))
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    ToggleButton(
                        text = SessionMode.HANGMAN.asString(),
                        isSelected = mode == SessionMode.HANGMAN,
                        onClick = { onModeChange(SessionMode.HANGMAN) },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleButton(
                        text = SessionMode.MEMORY.asString(),
                        isSelected = mode == SessionMode.MEMORY,
                        onClick = { onModeChange(SessionMode.MEMORY) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    ToggleButton(
                        text = SessionMode.FLASHCARD.asString(),
                        isSelected = mode == SessionMode.FLASHCARD,
                        onClick = { onModeChange(SessionMode.FLASHCARD) },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleButton(
                        text = SessionMode.MATCHING.asString(),
                        isSelected = mode == SessionMode.MATCHING,
                        onClick = { onModeChange(SessionMode.MATCHING) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(dimensions.spacingSmall))
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    ToggleButton(
                        text = SessionMode.MULTIPLE_CHOICE.asString(),
                        isSelected = mode == SessionMode.MULTIPLE_CHOICE,
                        onClick = { onModeChange(SessionMode.MULTIPLE_CHOICE) },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleButton(
                        text = SessionMode.TYPING.asString(),
                        isSelected = mode == SessionMode.TYPING,
                        onClick = { onModeChange(SessionMode.TYPING) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(dimensions.spacingSmall))
                Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                    ToggleButton(
                        text = SessionMode.AUDIO.asString(),
                        isSelected = mode == SessionMode.AUDIO,
                        onClick = { onModeChange(SessionMode.AUDIO) },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}


@Composable
fun ModeSettingsSection(
    preset: StudyPreset, mode: SessionMode, isExpanded: Boolean, onToggle: (Boolean) -> Unit,
    isWeighted: Boolean, onWeightedChange: (Boolean) -> Unit,
    numberOfAnswers: Int, onAnswersChange: (Int) -> Unit,
    showCorrectLetters: Boolean, onCorrectLettersChange: (Boolean) -> Unit,
    isGraded: Boolean, onGradedChange: (Boolean) -> Unit,
    selectAnswer: Boolean, onSelectAnswerChange: (Boolean) -> Unit,
    allowMultipleGuesses: Boolean, onMultiGuessChange: (Boolean) -> Unit,
    enableStt: Boolean, onSttChange: (Boolean) -> Unit,
    hideAnswerText: Boolean, onHideTextChange: (Boolean) -> Unit,
    fingersAndToes: Boolean, onFingersToesChange: (Boolean) -> Unit,
    maxMemoryTiles: Int, onTilesChange: (Int) -> Unit,
    gridDensity: Int, onDensityChange: (Int) -> Unit,
    showCorrectWords: Boolean, onShowCorrectWordsChange: (Boolean) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    // Generate Subtitle Logic locally or pass it in. Keeping it simple here.
    val subtitle = getText(R.string.configure) + mode.asString()

    DialogSection(
        title = getText(R.string.mode_settings),
        subtitle = subtitle,
        isExpanded = isExpanded,
        onToggle = { onToggle(!isExpanded) }) {
        Column {
            if (mode == SessionMode.FLASHCARD) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.fc_select_answer),
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = selectAnswer, onCheckedChange = onSelectAnswerChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.graded),
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isGraded, onCheckedChange = onGradedChange)
                }
            }
            if (mode == SessionMode.TYPING) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.graded),
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isGraded, onCheckedChange = onGradedChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.show_correct_letters),
                        modifier = Modifier.weight(1f)
                    ); Switch(
                    checked = showCorrectLetters,
                    onCheckedChange = onCorrectLettersChange,
                    enabled = preset != StudyPreset.STUDY
                )
                }
            }
            if (mode == SessionMode.MATCHING || mode == SessionMode.MULTIPLE_CHOICE) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.graded),
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isGraded, onCheckedChange = onGradedChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.reveal_when_wrong),
                        modifier = Modifier.weight(1f)
                    ); Switch(
                    checked = !allowMultipleGuesses,
                    onCheckedChange = { onMultiGuessChange(!it) })
                }
            }
            if (mode == SessionMode.AUDIO) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.graded),
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isGraded, onCheckedChange = onGradedChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.hide_answer_text),
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = hideAnswerText, onCheckedChange = onHideTextChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.speech_to_text),
                        modifier = Modifier.weight(1f)
                    ); Switch(
                    checked = enableStt,
                    onCheckedChange = onSttChange,
                    enabled = !isGraded
                )
                }
            }
            if (mode == SessionMode.FLASHCARD || mode == SessionMode.MULTIPLE_CHOICE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = dimensions.paddingSmall)
                ) {
                    Text(
                        getText(R.string.difficulty_weighting),
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isWeighted, onCheckedChange = onWeightedChange)
                }
            }
            if (mode == SessionMode.MULTIPLE_CHOICE) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = dimensions.paddingSmall)
                ) {
                    Text(getText(R.string.answers) + ": $numberOfAnswers", modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (numberOfAnswers > 2) onAnswersChange(numberOfAnswers - 1) }) {
                        Icon(
                            Icons.Default.Remove,
                            getText(R.string.less)
                        )
                    }
                    IconButton(onClick = { if (numberOfAnswers < 8) onAnswersChange(numberOfAnswers + 1) }) {
                        Icon(
                            Icons.Default.Add,
                            getText(R.string.more)                        )
                    }
                }
            }
            if (mode == SessionMode.ANAGRAM) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.show_correct_letters),
                        modifier = Modifier.weight(1f)
                    ); Switch(
                    checked = showCorrectLetters,
                    onCheckedChange = onCorrectLettersChange
                )
                }
            }
            if (mode == SessionMode.HANGMAN) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.fingers_and_toes),
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = fingersAndToes, onCheckedChange = onFingersToesChange)
                }
            }
            if (mode == SessionMode.MEMORY) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { if (maxMemoryTiles > 4) onTilesChange(maxMemoryTiles - 2) },
                        enabled = maxMemoryTiles > 4
                    ) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
                    Box(
                        modifier = Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(dimensions.cornerRadiusMedium)
                        ).padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
                    ) { Text("$maxMemoryTiles Tiles", fontSize = 20.sp) }
                    IconButton(
                        onClick = { if (maxMemoryTiles < 100) onTilesChange(maxMemoryTiles + 2) },
                        enabled = maxMemoryTiles < 100
                    ) { Icon(Icons.Default.Add, getText(R.string.increase)) }
                }
            }
            if (mode == SessionMode.CROSSWORD) {
                val densityLabel = when (gridDensity) {
                    1 -> getText(R.string.sparse); 2 -> getText(R.string.balanced); else -> getText(R.string.compact)
                }
                Text(getText(R.string.grid_density) + ": $densityLabel", modifier = Modifier.padding(top = dimensions.paddingSmall))
                Slider(
                    value = gridDensity.toFloat(),
                    onValueChange = { onDensityChange(it.roundToInt()) },
                    valueRange = 1f..3f,
                    steps = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        getText(R.string.show_correct_words),
                        modifier = Modifier.weight(1f)
                    ); Switch(
                    checked = showCorrectWords,
                    onCheckedChange = onShowCorrectWordsChange
                )
                }
            }
        }
    }
}