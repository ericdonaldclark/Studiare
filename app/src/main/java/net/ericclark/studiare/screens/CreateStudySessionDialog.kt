package net.ericclark.studiare.screens

import android.Manifest
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CreateStudySessionDialog(
    deck: net.ericclark.studiare.data.DeckWithCards,
    availableTags: List<String>,
    allTagDefinitions: List<net.ericclark.studiare.data.TagDefinition>,
    onDismiss: () -> Unit,
    onStartSession: (
        mode: String, isWeighted: Boolean, numCards: Int, quizPromptSide: String, numAnswers: Int,
        showCorrectLetters: Boolean, limitAnswerPool: Boolean, isGraded: Boolean, selectAnswer: Boolean,
        allowMultipleGuesses: Boolean, enableStt: Boolean, hideAnswerText: Boolean, fingersAndToes: Boolean,
        maxMemoryTiles: Int, gridDensity: Int, showCorrectWords: Boolean, config: net.ericclark.studiare.data.AutoSetConfig
    ) -> Unit
) {
    // --- 1. Intelligent Default for Prompt Side ---
    val defaultPromptSide = remember(deck) {
        val cards = deck.cards
        if (cards.isEmpty()) "Front" else {
            val avgFront = cards.map { it.front.length }.average()
            val avgBack = cards.map { it.back.length }.average()
            if (avgBack > (avgFront * 2)) "Back" else "Front"
        }
    }

    // --- 2. Session Settings State ---
    var selectedPreset by rememberSaveable { mutableStateOf("Study") }
    var selectedMode by rememberSaveable { mutableStateOf("Flashcard") }

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

    // --- 3. Selection & Sorting State ---
    var selectionMode by rememberSaveable { mutableStateOf("Any") }
    var selectedTags by rememberSaveable { mutableStateOf<List<String>>(emptyList()) }
    val listSaver = listSaver<SnapshotStateList<Int>, Int>(save = { it.toList() }, restore = { it.toMutableStateList() })
    val selectedDifficulties = rememberSaveable(saver = listSaver) { mutableStateListOf(1, 2, 3, 4, 5) }
    var excludeKnown by rememberSaveable { mutableStateOf(true) }

    var alphabetStart by rememberSaveable { mutableStateOf("A") }
    var alphabetEnd by rememberSaveable { mutableStateOf("Z") }
    var filterSide by rememberSaveable { mutableStateOf("Front") }

    val totalCards = deck.cards.size
    var cardOrderStart by rememberSaveable { mutableIntStateOf(1) }
    var cardOrderEnd by rememberSaveable { mutableIntStateOf(if (totalCards > 0) totalCards else 1) }

    var timeValue by rememberSaveable { mutableIntStateOf(7) }
    var timeUnit by rememberSaveable { mutableStateOf("Days") }
    var filterType by rememberSaveable { mutableStateOf("Exclude") }

    val maxDeckReviews = remember(deck) { deck.cards.maxOfOrNull { it.reviewedCount } ?: 0 }
    var reviewThreshold by rememberSaveable { mutableIntStateOf(0) }
    var reviewDirection by rememberSaveable { mutableStateOf("Minimum") }
    var scoreThreshold by rememberSaveable { mutableIntStateOf(0) }
    var scoreDirection by rememberSaveable { mutableStateOf("Minimum") }

    var sortMode by rememberSaveable { mutableStateOf("Random") }
    var sortDirection by rememberSaveable { mutableStateOf("ASC") }
    var sortSide by rememberSaveable { mutableStateOf("Front") }

    // --- 4. Expansion States ---
    var modeExpanded by rememberSaveable { mutableStateOf(true) }
    var modeSettingsExpanded by rememberSaveable { mutableStateOf(true) }
    var selectionExpanded by rememberSaveable { mutableStateOf(true) }
    var sortExpanded by rememberSaveable { mutableStateOf(false) }
    var promptSideExpanded by rememberSaveable { mutableStateOf(false) }
    var numberExpanded by rememberSaveable { mutableStateOf(false) }

    // --- 5. Helper Logic ---
    val applyPreset: (String) -> Unit = { preset ->
        selectedPreset = preset
        if (preset == "Games") {
            if (selectedMode !in listOf("Anagram", "Crossword", "Hangman", "Memory")) selectedMode = "Anagram"
        } else {
            if (selectedMode in listOf("Anagram", "Crossword", "Hangman", "Memory")) selectedMode = "Flashcard"
        }

        if (preset == "Study") {
            if (selectedMode == "Flashcard") { isGraded = false; selectAnswer = false }
            if (selectedMode == "Typing") { isGraded = false; showCorrectLetters = true }
            if (selectedMode == "Matching" || selectedMode == "Multiple Choice") { isGraded = false; allowMultipleGuesses = true }
            if (selectedMode == "Audio") { isGraded = false; enableStt = false; hideAnswerText = false }
        } else if (preset == "Quiz") {
            if (selectedMode == "Flashcard") { isGraded = true; selectAnswer = true }
            if (selectedMode == "Typing") { isGraded = true; showCorrectLetters = true }
            if (selectedMode == "Matching" || selectedMode == "Multiple Choice") { isGraded = true; allowMultipleGuesses = false }
            if (selectedMode == "Audio") { isGraded = true; enableStt = true; hideAnswerText = true }
        }
    }

    LaunchedEffect(selectedMode) { applyPreset(selectedPreset) }
    LaunchedEffect(isGraded, selectedMode) { if (selectedMode == "Audio" && isGraded) enableStt = true }

    // Calculate Available Cards
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
    val isMcModeInvalid = selectedMode == "Multiple Choice" && deck.cards.size < numberOfAnswers
    val isButtonEnabled = availableCardsCount > 0 && !isMcModeInvalid

    val context = LocalContext.current
    var startSessionCallback by remember { mutableStateOf<(() -> Unit)?>(null) }
    val permissionLauncher = rememberLauncherForActivityResult(contract = ActivityResultContracts.RequestPermission()) { isGranted ->
        startSessionCallback?.invoke()
        startSessionCallback = null
    }

    // UI Configuration
    val configuration = LocalConfiguration.current
    val useSideBySide = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE || configuration.screenWidthDp >= 600
    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    // --- Content Blocks ---
    val settingsContent: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            TopSliderDialogSection(
                options = listOf("Study", "Quiz", "Games"),
                selectedMode = selectedPreset,
                onModeChange = { applyPreset(it) }
            )
            Spacer(Modifier.height(16.dp))

            ModeSelectionSection(selectedPreset, selectedMode, { selectedMode = it }, modeExpanded, { modeExpanded = it })

            ModeSettingsSection(
                selectedPreset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                showCorrectLetters, { showCorrectLetters = it }, isGraded, { isGraded = it },
                selectAnswer, { selectAnswer = it }, allowMultipleGuesses, { allowMultipleGuesses = it },
                enableStt, { enableStt = it }, hideAnswerText, { hideAnswerText = it },
                fingersAndToes, { fingersAndToes = it }, maxMemoryTiles, { maxMemoryTiles = it },
                gridDensity, { gridDensity = it }, showCorrectWords, { showCorrectWords = it }
            )

            DialogSection(
                title = "Prompt Side",
                subtitle = quizPromptSide,
                isExpanded = promptSideExpanded,
                onToggle = { promptSideExpanded = !promptSideExpanded }) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        text = "Front",
                        isSelected = quizPromptSide == "Front",
                        onClick = { quizPromptSide = "Front" },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleButton(
                        text = "Back",
                        isSelected = quizPromptSide == "Back",
                        onClick = { quizPromptSide = "Back" },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }

    val filtersContent: @Composable () -> Unit = {
        Column(modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())) {
            val selectionState =
                SelectionSectionState(
                    selectionMode,
                    selectedTags,
                    selectedDifficulties,
                    excludeKnown,
                    alphabetStart,
                    alphabetEnd,
                    filterSide,
                    cardOrderStart,
                    cardOrderEnd,
                    timeValue,
                    timeUnit,
                    filterType,
                    reviewThreshold,
                    reviewDirection,
                    scoreThreshold,
                    scoreDirection,
                    availableTags,
                    allTagDefinitions,
                    availableCardsCount,
                    totalCards,
                    maxDeckReviews
                )
            val selectionActions =
                SelectionSectionActions(
                    { selectionMode = it },
                    { selectedTags = it },
                    { diffs -> selectedDifficulties.clear(); selectedDifficulties.addAll(diffs) },
                    { excludeKnown = it },
                    { alphabetStart = it },
                    { alphabetEnd = it },
                    { filterSide = it },
                    { cardOrderStart = it },
                    { cardOrderEnd = it },
                    { timeValue = it },
                    { timeUnit = it },
                    { filterType = it },
                    { reviewThreshold = it },
                    { reviewDirection = it },
                    { scoreThreshold = it },
                    { scoreDirection = it }
                )

            SelectionModeDialogSection(
                state = selectionState,
                actions = selectionActions,
                isExpanded = selectionExpanded,
                onToggleExpand = { selectionExpanded = !selectionExpanded })

            SortModeDialogSection(
                sortMode,
                { sortMode = it },
                sortDirection,
                { sortDirection = it },
                sortSide,
                { sortSide = it },
                sortExpanded,
                { sortExpanded = !sortExpanded }
            )
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxHeight(0.9f).fillMaxWidth(0.9f)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {

                // 1. Static Title with Close Button
                Box(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Create Study Session",
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Divider(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))

                // 2. Pager Content
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f),
                    userScrollEnabled = false,
                    verticalAlignment = Alignment.Top
                ) { page ->
                    if (useSideBySide) {
                        // --- LANDSCAPE LAYOUT ---
                        Row(modifier = Modifier.fillMaxSize()) {
                            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(end = 16.dp)) {
                                if (page == 0) {
                                    TopSliderDialogSection(
                                        listOf("Study", "Quiz", "Games"),
                                        selectedPreset
                                    ) { applyPreset(it) }
                                    Spacer(Modifier.height(16.dp))
                                    ModeSelectionSection(selectedPreset, selectedMode, { selectedMode = it }, modeExpanded, { modeExpanded = it })
                                } else {
                                    // Page 2 Left content (Selection)
                                    val selectionState =
                                        SelectionSectionState(
                                            selectionMode,
                                            selectedTags,
                                            selectedDifficulties,
                                            excludeKnown,
                                            alphabetStart,
                                            alphabetEnd,
                                            filterSide,
                                            cardOrderStart,
                                            cardOrderEnd,
                                            timeValue,
                                            timeUnit,
                                            filterType,
                                            reviewThreshold,
                                            reviewDirection,
                                            scoreThreshold,
                                            scoreDirection,
                                            availableTags,
                                            allTagDefinitions,
                                            availableCardsCount,
                                            totalCards,
                                            maxDeckReviews
                                        )
                                    val selectionActions =
                                        SelectionSectionActions(
                                            { selectionMode = it },
                                            { selectedTags = it },
                                            { diffs ->
                                                selectedDifficulties.clear(); selectedDifficulties.addAll(
                                                diffs
                                            )
                                            },
                                            { excludeKnown = it },
                                            { alphabetStart = it },
                                            { alphabetEnd = it },
                                            { filterSide = it },
                                            { cardOrderStart = it },
                                            { cardOrderEnd = it },
                                            { timeValue = it },
                                            { timeUnit = it },
                                            { filterType = it },
                                            { reviewThreshold = it },
                                            { reviewDirection = it },
                                            { scoreThreshold = it },
                                            { scoreDirection = it }
                                        )
                                    Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                                        SelectionModeDialogSection(
                                            state = selectionState,
                                            actions = selectionActions,
                                            isExpanded = selectionExpanded,
                                            onToggleExpand = {
                                                selectionExpanded = !selectionExpanded
                                            })
                                    }
                                }
                            }
                            VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(start = 16.dp)) {
                                if (page == 0) {
                                    ModeSettingsSection(
                                        selectedPreset, selectedMode, modeSettingsExpanded, { modeSettingsExpanded = it },
                                        isWeighted, { isWeighted = it }, numberOfAnswers, { numberOfAnswers = it },
                                        showCorrectLetters, { showCorrectLetters = it }, isGraded, { isGraded = it },
                                        selectAnswer, { selectAnswer = it }, allowMultipleGuesses, { allowMultipleGuesses = it },
                                        enableStt, { enableStt = it }, hideAnswerText, { hideAnswerText = it },
                                        fingersAndToes, { fingersAndToes = it }, maxMemoryTiles, { maxMemoryTiles = it },
                                        gridDensity, { gridDensity = it }, showCorrectWords, { showCorrectWords = it }
                                    )
                                    DialogSection(
                                        title = "Prompt Side",
                                        subtitle = quizPromptSide,
                                        isExpanded = promptSideExpanded,
                                        onToggle = { promptSideExpanded = !promptSideExpanded }) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            ToggleButton(
                                                text = "Front",
                                                isSelected = quizPromptSide == "Front",
                                                onClick = { quizPromptSide = "Front" },
                                                modifier = Modifier.weight(1f)
                                            )
                                            ToggleButton(
                                                text = "Back",
                                                isSelected = quizPromptSide == "Back",
                                                onClick = { quizPromptSide = "Back" },
                                                modifier = Modifier.weight(1f)
                                            )
                                        }
                                    }
                                    CardCountSection(
                                        numberOfCards,
                                        availableCardsCount,
                                        numberExpanded,
                                        { numberExpanded = it },
                                        { numberOfCards = it })
                                } else {
                                    SortModeDialogSection(
                                        sortMode,
                                        { sortMode = it },
                                        sortDirection,
                                        { sortDirection = it },
                                        sortSide,
                                        { sortSide = it },
                                        sortExpanded,
                                        { sortExpanded = !sortExpanded }
                                    )
                                    CardCountSection(
                                        numberOfCards,
                                        availableCardsCount,
                                        numberExpanded,
                                        { numberExpanded = it },
                                        { numberOfCards = it })
                                }
                            }
                        }
                    } else {
                        // --- PORTRAIT LAYOUT ---
                        // Re-use content lambdas for simplicity, or inline
                        if (page == 0) settingsContent() else filtersContent()
                    }
                }

                // 3. Persistent Footer
                Spacer(Modifier.height(8.dp))
                // Only show Card Count here in Portrait (it's in right column in Landscape)
                if (!useSideBySide) {
                    Divider()
                    Spacer(Modifier.height(8.dp))
                    CardCountSection(
                        numberOfCards,
                        availableCardsCount,
                        numberExpanded,
                        { numberExpanded = it },
                        { numberOfCards = it })
                    Spacer(Modifier.height(8.dp))
                }

                if (isMcModeInvalid) Text("Multiple Choice requires $numberOfAnswers cards.", color = MaterialTheme.colorScheme.error)

                Button(
                    onClick = {
                        val currentConfig =
                            AutoSetConfig(
                                mode = "One",
                                numSets = 1,
                                maxCardsPerSet = numberOfCards,
                                selectionMode = selectionMode,
                                selectedTags = selectedTags,
                                selectedDifficulties = selectedDifficulties.toList(),
                                excludeKnown = excludeKnown,
                                sortMode = sortMode,
                                sortDirection = sortDirection,
                                sortSide = sortSide,
                                alphabetStart = alphabetStart,
                                alphabetEnd = alphabetEnd,
                                filterSide = filterSide,
                                cardOrderStart = cardOrderStart,
                                cardOrderEnd = cardOrderEnd,
                                timeValue = timeValue,
                                timeUnit = timeUnit,
                                filterType = filterType,
                                reviewCountThreshold = reviewThreshold,
                                reviewCountDirection = reviewDirection,
                                scoreThreshold = scoreThreshold,
                                scoreDirection = scoreDirection
                            )

                        val action = {
                            onStartSession(
                                selectedMode, isWeighted, numberOfCards, quizPromptSide, numberOfAnswers,
                                showCorrectLetters, limitAnswerPool, isGraded, selectAnswer, allowMultipleGuesses,
                                enableStt, hideAnswerText, fingersAndToes, maxMemoryTiles, gridDensity,
                                showCorrectWords, currentConfig
                            )
                        }

                        if (selectedMode == "Audio" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) action()
                            else { startSessionCallback = action; permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }
                        } else action()
                    },
                    modifier = Modifier.fillMaxWidth(if (useSideBySide) 0.5f else 1f).align(Alignment.CenterHorizontally),
                    enabled = isButtonEnabled
                ) {
                    Text("Start Session")
                }

                Spacer(Modifier.height(8.dp))

                // 4. Bottom Tabs
                TabRow(
                    selectedTabIndex = pagerState.currentPage,
                    containerColor = Color.Transparent,
                    contentColor = MaterialTheme.colorScheme.primary,
                    divider = {},
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, Color.Transparent, RoundedCornerShape(8.dp))
                        .clip(RoundedCornerShape(8.dp))
                ) {
                    Tab(
                        selected = pagerState.currentPage == 0,
                        onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                        text = { Text("Session Settings") }
                    )
                    Tab(
                        selected = pagerState.currentPage == 1,
                        onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                        text = { Text("Filter & Sort") }
                    )
                }
            }
        }
    }
}

fun calculateAvailableCardsCount(
    deck: net.ericclark.studiare.data.DeckWithCards,
    selectionMode: String, selectedTags: List<String>, selectedDifficulties: List<Int>,
    excludeKnown: Boolean, alphabetStart: String, alphabetEnd: String, filterSide: String,
    cardOrderStart: Int, cardOrderEnd: Int, timeValue: Int, timeUnit: String, filterType: String,
    reviewThreshold: Int, reviewDirection: String, scoreThreshold: Int, scoreDirection: String
): Int {
    var pool = deck.cards
    if (excludeKnown) pool = pool.filter { !it.isKnown }

    val timeMultiplier = when (timeUnit) {
        "Days" -> 24 * 60 * 60 * 1000L
        "Weeks" -> 7 * 24 * 60 * 60 * 1000L
        "Months" -> 30 * 24 * 60 * 60 * 1000L
        "Years" -> 365 * 24 * 60 * 60 * 1000L
        else -> 0L
    }
    val cutoffTime = System.currentTimeMillis() - (timeValue * timeMultiplier)

    pool = when (selectionMode) {
        "Difficulty" -> pool.filter { it.difficulty in selectedDifficulties }
        "Tags" -> pool.filter { card -> card.tags.any { it in selectedTags } }
        "Alphabet" -> {
            val start = alphabetStart.uppercase()
            val end = alphabetEnd.uppercase()
            pool.filter { card ->
                val text = if (filterSide == "Front") card.front else card.back
                val firstChar = text.trim().uppercase(java.util.Locale.getDefault()).firstOrNull()?.toString()
                firstChar != null && firstChar >= start && firstChar <= end
            }
        }
        "Card Order" -> {
            val s = (cardOrderStart - 1).coerceAtLeast(0)
            val e = (cardOrderEnd - 1).coerceAtMost(deck.cards.size - 1)
            if (s <= e && deck.cards.isNotEmpty()) {
                val allowedIds = deck.cards.slice(s..e).map { it.id }.toSet()
                pool.filter { it.id in allowedIds }
            } else emptyList()
        }
        "Review Date" -> {
            if (filterType == "Include") pool.filter { it.reviewedAt != null && it.reviewedAt >= cutoffTime }
            else pool.filter { it.reviewedAt == null || it.reviewedAt < cutoffTime }
        }
        "Incorrect Date" -> {
            if (filterType == "Include") pool.filter { card -> card.incorrectAttempts.maxOrNull()?.let { last -> last >= cutoffTime } == true }
            else pool.filter { card -> card.incorrectAttempts.isEmpty() || card.incorrectAttempts.maxOrNull()!! < cutoffTime }
        }
        "Review Count" -> {
            if (reviewDirection == "Maximum") pool.filter { it.reviewedCount <= reviewThreshold }
            else pool.filter { it.reviewedCount >= reviewThreshold }
        }
        "Score" -> {
            val getScore: (net.ericclark.studiare.data.Card) -> Float = { card ->
                val total = card.gradedAttempts.size
                if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
            }
            val threshold = scoreThreshold.toFloat() / 100f
            if (scoreDirection == "Maximum") pool.filter { getScore(it) <= threshold }
            else pool.filter { getScore(it) >= threshold }
        }
        else -> pool
    }
    return pool.size
}

@Composable
fun ModeSelectionSection(
    preset: String,
    mode: String,
    onModeChange: (String) -> Unit,
    isExpanded: Boolean,
    onExpandedChange: (Boolean) -> Unit
) {
    DialogSection(
        title = "Mode",
        subtitle = mode,
        isExpanded = isExpanded,
        onToggle = { onExpandedChange(!isExpanded) }) {
        if (preset == "Games") {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        text = "Anagram",
                        isSelected = mode == "Anagram",
                        onClick = { onModeChange("Anagram") },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleButton(
                        text = "Crossword",
                        isSelected = mode == "Crossword",
                        onClick = { onModeChange("Crossword") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        text = "Hangman",
                        isSelected = mode == "Hangman",
                        onClick = { onModeChange("Hangman") },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleButton(
                        text = "Memory",
                        isSelected = mode == "Memory",
                        onClick = { onModeChange("Memory") },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        } else {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        text = "Flashcard",
                        isSelected = mode == "Flashcard",
                        onClick = { onModeChange("Flashcard") },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleButton(
                        text = "Matching",
                        isSelected = mode == "Matching",
                        onClick = { onModeChange("Matching") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        text = "Multiple Choice",
                        isSelected = mode == "Multiple Choice",
                        onClick = { onModeChange("Multiple Choice") },
                        modifier = Modifier.weight(1f)
                    )
                    ToggleButton(
                        text = "Typing",
                        isSelected = mode == "Typing",
                        onClick = { onModeChange("Typing") },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ToggleButton(
                        text = "Audio",
                        isSelected = mode == "Audio",
                        onClick = { onModeChange("Audio") },
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
    preset: String, mode: String, isExpanded: Boolean, onToggle: (Boolean) -> Unit,
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
    // Generate Subtitle Logic locally or pass it in. Keeping it simple here.
    val subtitle = "Configure $mode"

    DialogSection(
        title = "Mode Settings",
        subtitle = subtitle,
        isExpanded = isExpanded,
        onToggle = { onToggle(!isExpanded) }) {
        Column {
            if (mode == "Flashcard") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Select answer (Picker)",
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = selectAnswer, onCheckedChange = onSelectAnswerChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Graded",
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isGraded, onCheckedChange = onGradedChange)
                }
            }
            if (mode == "Typing") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Graded",
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isGraded, onCheckedChange = onGradedChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Show correct letters",
                        modifier = Modifier.weight(1f)
                    ); Switch(
                    checked = showCorrectLetters,
                    onCheckedChange = onCorrectLettersChange,
                    enabled = preset != "Study"
                )
                }
            }
            if (mode == "Matching" || mode == "Multiple Choice") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Graded",
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isGraded, onCheckedChange = onGradedChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Reveal When Wrong",
                        modifier = Modifier.weight(1f)
                    ); Switch(
                    checked = !allowMultipleGuesses,
                    onCheckedChange = { onMultiGuessChange(!it) })
                }
            }
            if (mode == "Audio") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Graded",
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isGraded, onCheckedChange = onGradedChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Hide Answer Text",
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = hideAnswerText, onCheckedChange = onHideTextChange)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Speech-to-Text",
                        modifier = Modifier.weight(1f)
                    ); Switch(
                    checked = enableStt,
                    onCheckedChange = onSttChange,
                    enabled = !isGraded
                )
                }
            }
            if (mode == "Flashcard" || mode == "Multiple Choice") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text(
                        "Difficulty Weighting",
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = isWeighted, onCheckedChange = onWeightedChange)
                }
            }
            if (mode == "Multiple Choice") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 8.dp)
                ) {
                    Text("Answers: $numberOfAnswers", modifier = Modifier.weight(1f))
                    IconButton(onClick = { if (numberOfAnswers > 2) onAnswersChange(numberOfAnswers - 1) }) {
                        Icon(
                            Icons.Default.Remove,
                            "Less"
                        )
                    }
                    IconButton(onClick = { if (numberOfAnswers < 8) onAnswersChange(numberOfAnswers + 1) }) {
                        Icon(
                            Icons.Default.Add,
                            "More"
                        )
                    }
                }
            }
            if (mode == "Anagram") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Show correct letters",
                        modifier = Modifier.weight(1f)
                    ); Switch(
                    checked = showCorrectLetters,
                    onCheckedChange = onCorrectLettersChange
                )
                }
            }
            if (mode == "Hangman") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Fingers & Toes (+20 guesses)",
                        modifier = Modifier.weight(1f)
                    ); Switch(checked = fingersAndToes, onCheckedChange = onFingersToesChange)
                }
            }
            if (mode == "Memory") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    IconButton(
                        onClick = { if (maxMemoryTiles > 4) onTilesChange(maxMemoryTiles - 2) },
                        enabled = maxMemoryTiles > 4
                    ) { Icon(Icons.Default.Remove, "Decrease") }
                    Box(
                        modifier = Modifier.border(
                            1.dp,
                            MaterialTheme.colorScheme.primary,
                            RoundedCornerShape(8.dp)
                        ).padding(horizontal = 24.dp, vertical = 12.dp)
                    ) { Text("$maxMemoryTiles Tiles", fontSize = 20.sp) }
                    IconButton(
                        onClick = { if (maxMemoryTiles < 100) onTilesChange(maxMemoryTiles + 2) },
                        enabled = maxMemoryTiles < 100
                    ) { Icon(Icons.Default.Add, "Increase") }
                }
            }
            if (mode == "Crossword") {
                val densityLabel = when (gridDensity) {
                    1 -> "Sparse"; 2 -> "Balanced"; else -> "Compact"
                }
                Text("Grid Density: $densityLabel", modifier = Modifier.padding(top = 8.dp))
                Slider(
                    value = gridDensity.toFloat(),
                    onValueChange = { onDensityChange(it.roundToInt()) },
                    valueRange = 1f..3f,
                    steps = 1
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Show Correct Words",
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