package net.ericclark.studiare.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import net.ericclark.studiare.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import net.ericclark.studiare.R
import androidx.compose.ui.res.pluralStringResource
import net.ericclark.studiare.components.*
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.TabRowDefaults.SecondaryIndicator
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.ui.res.stringResource

@Composable
fun CreateStudySessionDialog(
    deck: DeckWithCards,
    preset: StudyPreset,
    availableTags: List<String>,
    allTagDefinitions: List<TagDefinition>,
    onDismiss: () -> Unit,
    onStartSession: (
        mode: SessionMode, isWeighted: Boolean, numCards: Int, quizPromptSide: CardSide, numAnswers: Int,
        showCorrectLetters: Boolean, limitAnswerPool: Boolean, isGraded: Boolean, selectAnswer: Boolean,
        allowMultipleGuesses: Boolean, enableStt: Boolean, hideAnswerText: Boolean, fingersAndToes: Boolean,
        maxMemoryTiles: Int, gridDensity: Int, showCorrectWords: Boolean, freeformLayoutVertical: Boolean, config: AutoSetConfig
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
    var selectedMode by rememberSaveable {
        mutableStateOf(if (preset == StudyPreset.GAMES) SessionMode.ANAGRAM else SessionMode.FLASHCARD)
    }

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
    var freeformLayoutVertical by rememberSaveable { mutableStateOf(false) }
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
    val applyPreset = {
        if (preset == StudyPreset.GAMES) {
            if (selectedMode !in listOf(SessionMode.ANAGRAM, SessionMode.CROSSWORD, SessionMode.HANGMAN, SessionMode.MEMORY)) selectedMode = SessionMode.ANAGRAM
        } else {
            if (selectedMode in listOf(SessionMode.ANAGRAM, SessionMode.CROSSWORD, SessionMode.HANGMAN, SessionMode.MEMORY)) selectedMode = SessionMode.FLASHCARD
        }

        if (preset == StudyPreset.STUDY) {
            if (selectedMode == SessionMode.FLASHCARD) { isGraded = false; selectAnswer = false }
            if (selectedMode == SessionMode.FREEFORM) { isGraded = false }
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

    LaunchedEffect(selectedMode, preset) { applyPreset() }
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
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            modifier = Modifier.fillMaxHeight(0.9f).fillMaxWidth(0.9f),
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
                            ModeSelectionSection(
                                preset = preset,
                                mode = selectedMode,
                                onModeChange = { selectedMode = it },
                                isExpanded = modeExpanded,
                                onExpandedChange = { modeExpanded = it },
                                isFsrs = false
                            )
                            ModeSettingsSection(
                                preset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                                isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                                showCorrectLetters, { showCorrectLetters = it }, isGraded, { isGraded = it },
                                selectAnswer, { selectAnswer = it }, allowMultipleGuesses,
                                { allowMultipleGuesses = it }, enableStt, { enableStt = it }, hideAnswerText,
                                { hideAnswerText = it }, fingersAndToes, { fingersAndToes = it },
                                maxMemoryTiles, { maxMemoryTiles = it }, gridDensity, { gridDensity = it },
                                showCorrectWords, { showCorrectWords = it }, freeformLayoutVertical, {freeformLayoutVertical = it}
                            )

                            DialogSection(
                                title = getText(R.string.prompt_side),
                                subtitle = quizPromptSide.asString(),
                                isExpanded = promptSideExpanded,
                                onToggle = { promptSideExpanded = !promptSideExpanded }) {

                                SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                    SegmentedButton(
                                        selected = quizPromptSide == CardSide.FRONT,
                                        onClick = { quizPromptSide = CardSide.FRONT },
                                        shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                    ) {
                                        Text(CardSide.FRONT.asString(), style = MaterialTheme.typography.labelLarge)
                                    }
                                    SegmentedButton(
                                        selected = quizPromptSide == CardSide.BACK,
                                        onClick = { quizPromptSide = CardSide.BACK },
                                        shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                    ) {
                                        Text(CardSide.BACK.asString(), style = MaterialTheme.typography.labelLarge)
                                    }
                                }
                            }
                            Spacer(Modifier.height(dimensions.spacingMedium))

                            // Number of cards explicitly placed in the left column for landscape
                            CardCountSection(numberOfCards, availableCardsCount, numberExpanded, { numberExpanded = it }, { numberOfCards = it })
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

                            SelectionModeDialogSection(
                                state = selectionState, actions = selectionActions, isExpanded = selectionExpanded, onToggleExpand = { selectionExpanded = !selectionExpanded })

                            SortModeDialogSection(
                                sortMode, { sortMode = it }, sortDirection, { sortDirection = it }, sortSide,
                                { sortSide = it }, sortExpanded, { sortExpanded = !sortExpanded })
                        }
                    }
                } else {
                    // --- PORTRAIT LAYOUT ---
                    SecondaryTabRow(
                        selectedTabIndex = pagerState.currentPage,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                        contentColor = MaterialTheme.colorScheme.primary,
                        divider = {},
                        indicator = {
                            SecondaryIndicator(
                                modifier = Modifier.tabIndicatorOffset(pagerState.currentPage),
                                height = 3.dp,
                                color = MaterialTheme.colorScheme.primary
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dimensions.cornerRadiusLarge))
                    ) {
                        Tab(selected = pagerState.currentPage == 0, onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                            text = { Text(getText(R.string.session_settings), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) })
                        Tab(selected = pagerState.currentPage == 1, onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                            text = { Text(getText(R.string.filter_and_sort), fontWeight = androidx.compose.ui.text.font.FontWeight.Bold) })
                    }

                    HorizontalDivider(modifier = Modifier.padding(top = dimensions.spacingMedium, bottom = dimensions.spacingSmall))

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                        userScrollEnabled = false,
                        verticalAlignment = Alignment.Top
                    ) { page ->
                        if (page == 0) {
                            Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
                                ModeSelectionSection(
                                    preset = preset,
                                    mode = selectedMode,
                                    onModeChange = { selectedMode = it },
                                    isExpanded = modeExpanded,
                                    onExpandedChange = { modeExpanded = it },
                                    isFsrs = false
                                )
                                ModeSettingsSection(
                                    preset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                                    isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                                    showCorrectLetters, { showCorrectLetters = it }, isGraded, { isGraded = it },
                                    selectAnswer, { selectAnswer = it }, allowMultipleGuesses,
                                    { allowMultipleGuesses = it }, enableStt, { enableStt = it }, hideAnswerText,
                                    { hideAnswerText = it }, fingersAndToes, { fingersAndToes = it },
                                    maxMemoryTiles, { maxMemoryTiles = it }, gridDensity, { gridDensity = it },
                                    showCorrectWords, { showCorrectWords = it }, freeformLayoutVertical, {freeformLayoutVertical = it}
                                )

                                DialogSection(
                                    title = getText(R.string.prompt_side),
                                    subtitle = quizPromptSide.asString(),
                                    isExpanded = promptSideExpanded,
                                    onToggle = { promptSideExpanded = !promptSideExpanded }) {

                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        SegmentedButton(
                                            selected = quizPromptSide == CardSide.FRONT,
                                            onClick = { quizPromptSide = CardSide.FRONT },
                                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                                        ) {
                                            Text(CardSide.FRONT.asString(), style = MaterialTheme.typography.labelLarge)
                                        }
                                        SegmentedButton(
                                            selected = quizPromptSide == CardSide.BACK,
                                            onClick = { quizPromptSide = CardSide.BACK },
                                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                                        ) {
                                            Text(CardSide.BACK.asString(), style = MaterialTheme.typography.labelLarge)
                                        }
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
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    CardCountSection(numberOfCards, availableCardsCount, numberExpanded, { numberExpanded = it }, { numberOfCards = it })
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
                    TextButton(onClick = onDismiss) {
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
                                mode = AutoSetCreationMode.ONE, numSets = 1, maxCardsPerSet = numberOfCards, selectionMode = selectionMode, selectedTags = selectedTags,
                                selectedDifficulties = selectedDifficulties.toList(), excludeKnown = excludeKnown, sortMode = sortMode, sortDirection = sortDirection,
                                sortSide = sortSide, alphabetStart = alphabetStart, alphabetEnd = alphabetEnd, filterSide = filterSide, cardOrderStart = cardOrderStart,
                                cardOrderEnd = cardOrderEnd, timeValue = timeValue, timeUnit = timeUnit, filterType = filterType, reviewCountThreshold = reviewThreshold,
                                reviewCountDirection = reviewDirection, scoreThreshold = scoreThreshold, scoreDirection = scoreDirection, schedulingMode = SchedulingMode.NORMAL)
                            val action =
                                { onStartSession(selectedMode, isWeighted, numberOfCards, quizPromptSide, numberOfAnswers,
                                    showCorrectLetters, limitAnswerPool, isGraded, selectAnswer,
                                    allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes,
                                    maxMemoryTiles, gridDensity, showCorrectWords, freeformLayoutVertical,currentConfig) }
                            if (selectedMode == SessionMode.AUDIO && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) action()
                                else { startSessionCallback = action; permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                            } else action()
                        },
                        modifier = Modifier
                            .defaultMinSize(minHeight = 56.dp)
                            .scale(startScale),
                        shape = androidx.compose.foundation.shape.CircleShape,
                        enabled = isButtonEnabled,
                        interactionSource = startInteractionSource
                    ) { Text(getText(R.string.session_start)) }
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

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
        ) {
            if (preset == StudyPreset.GAMES) {
                val gameModes = listOf(SessionMode.ANAGRAM, SessionMode.CROSSWORD, SessionMode.HANGMAN, SessionMode.MEMORY)
                gameModes.forEach { gameMode ->
                    val isEnabled = if (gameMode in listOf(SessionMode.ANAGRAM, SessionMode.CROSSWORD)) !isFsrs else true
                    FilterChip(
                        selected = mode == gameMode,
                        onClick = { onModeChange(gameMode) },
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                        label = { Text(gameMode.asString(), maxLines = 1, softWrap = false) },
                        enabled = isEnabled,
                        leadingIcon = if (mode == gameMode) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }
            } else if (preset == StudyPreset.STUDY) {
                val studyModes = listOf(SessionMode.FLASHCARD, SessionMode.MATCHING, SessionMode.MULTIPLE_CHOICE, SessionMode.TYPING, SessionMode.AUDIO, SessionMode.FREEFORM)
                studyModes.forEach { studyMode ->
                    FilterChip(
                        selected = mode == studyMode,
                        onClick = { onModeChange(studyMode) },
                        modifier = Modifier.animateContentSize(
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioNoBouncy,
                                stiffness = Spring.StiffnessMedium
                            )
                        ),
                        label = { Text(studyMode.asString(), maxLines = 1, softWrap = false) },
                        leadingIcon = if (mode == studyMode) {
                            { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                        } else null
                    )
                }
            }
            else {
            val studyModes = listOf(SessionMode.FLASHCARD, SessionMode.MATCHING, SessionMode.MULTIPLE_CHOICE, SessionMode.TYPING, SessionMode.AUDIO)
            studyModes.forEach { studyMode ->
                FilterChip(
                    selected = mode == studyMode,
                    onClick = { onModeChange(studyMode) },
                    modifier = Modifier.animateContentSize(
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioNoBouncy,
                            stiffness = Spring.StiffnessMedium
                        )
                    ),
                    label = { Text(studyMode.asString(), maxLines = 1, softWrap = false) },
                    leadingIcon = if (mode == studyMode) {
                        { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                    } else null
                )
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
    showCorrectWords: Boolean, onShowCorrectWordsChange: (Boolean) -> Unit,
    freeformLayoutVertical: Boolean, onFreeformLayoutChange: (Boolean) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    // Generate Subtitle Logic locally or pass it in. Keeping it simple here.
    val subtitle = getText(R.string.configure) + mode.asString()

    DialogSection(
        title = getText(R.string.mode_settings),
        subtitle = subtitle,
        isExpanded = isExpanded,
        onToggle = { onToggle(!isExpanded) }) {

        // PHASE 3: Spatial Animated Content for Settings Swap
        androidx.compose.animation.AnimatedContent(
            targetState = mode,
            transitionSpec = {
                androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(220, delayMillis = 90)) togetherWith
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(90)) using
                        androidx.compose.animation.SizeTransform(clip = false)
            },
            label = "modeSettingsAnim"
        ) { targetMode ->
            Column {
                if (targetMode == SessionMode.FLASHCARD) {
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
                if (targetMode == SessionMode.TYPING) {
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
                if (targetMode == SessionMode.MATCHING || targetMode == SessionMode.MULTIPLE_CHOICE) {
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
                if (targetMode == SessionMode.AUDIO) {
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
                if (targetMode == SessionMode.FLASHCARD || targetMode == SessionMode.MULTIPLE_CHOICE) {
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
                if (targetMode == SessionMode.MULTIPLE_CHOICE) {

                    // M3 Expressive Update: Tonal Value Indicator pattern
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = dimensions.paddingMedium).fillMaxWidth()
                    ) {
                        Text(getText(R.string.answers), modifier = Modifier.weight(1f))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            FilledTonalIconButton(
                                onClick = { if (numberOfAnswers > 2) onAnswersChange(numberOfAnswers - 1) },
                                enabled = numberOfAnswers > 2
                            ) { Icon(Icons.Default.Remove, getText(R.string.less)) }

                            Spacer(Modifier.width(dimensions.spacingSmall))

                            Surface(
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ) {
                                Text(
                                    text = numberOfAnswers.toString(),
                                    fontSize = 20.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                    modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
                                )
                            }

                            Spacer(Modifier.width(dimensions.spacingSmall))

                            FilledTonalIconButton(
                                onClick = { if (numberOfAnswers < 8) onAnswersChange(numberOfAnswers + 1) },
                                enabled = numberOfAnswers < 8
                            ) { Icon(Icons.Default.Add, getText(R.string.more)) }
                        }
                    }
                }
                if (targetMode == SessionMode.ANAGRAM) {
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
                if (targetMode == SessionMode.HANGMAN) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            getText(R.string.fingers_and_toes),
                            modifier = Modifier.weight(1f)
                        ); Switch(checked = fingersAndToes, onCheckedChange = onFingersToesChange)
                    }
                }
                if (targetMode == SessionMode.MEMORY) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        IconButton(
                            onClick = { if (maxMemoryTiles > 4) onTilesChange(maxMemoryTiles - 2) },
                            enabled = maxMemoryTiles > 4
                        ) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
                        Surface(
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                "$maxMemoryTiles Tiles",
                                fontSize = 20.sp,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
                            )
                        }
                        IconButton(
                            onClick = { if (maxMemoryTiles < 100) onTilesChange(maxMemoryTiles + 2) },
                            enabled = maxMemoryTiles < 100
                        ) { Icon(Icons.Default.Add, getText(R.string.increase)) }
                    }
                }
                if (targetMode == SessionMode.CROSSWORD) {
                    val densityLabel = when (gridDensity) {
                        1 -> getText(R.string.sparse); 2 -> getText(R.string.balanced); else -> getText(R.string.compact)
                    }
                    val densityInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    Text(getText(R.string.grid_density) + ": $densityLabel", modifier = Modifier.padding(top = dimensions.paddingSmall))
                    Slider(
                        value = gridDensity.toFloat(),
                        onValueChange = { onDensityChange(it.roundToInt()) },
                        valueRange = 1f..3f,
                        steps = 1,
                        interactionSource = densityInteractionSource
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
                if (targetMode == SessionMode.FREEFORM) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            stringResource(R.string.vertical_layout),
                            modifier = Modifier.weight(1f)
                        ); Switch(
                        checked = freeformLayoutVertical,
                        onCheckedChange = onFreeformLayoutChange
                    )
                    }
                }
            }
        }
    }
}