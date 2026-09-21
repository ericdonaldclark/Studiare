package net.ericclark.studiare.studymodes

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.foundation.focusable
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.delay
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun AnagramScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val state = viewModel.studyState ?: return
    val inputController = net.ericclark.studiare.components.rememberLetterInputController()
    var showEditDialog by remember { mutableStateOf(false) }

    if (showEditDialog) {
        val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex)
        if (currentCard != null) {
            EditCardDialog(
                cardToEdit = currentCard,
                viewModel = viewModel,
                onDismiss = { showEditDialog = false }
            )
        }
    }

    if (state.isComplete) {
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
        return
    }

    LaunchedEffect(state.currentCardIndex) {
        if (!state.correctAnswerFound) {
            delay(300)
            inputController.show()
        }
    }

    Scaffold(
        // Tapping empty space dismisses the keyboard (taps on buttons and tiles are handled first and don't reach this).
        modifier = Modifier
            .imePadding()
            .pointerInput(Unit) { detectTapGestures { inputController.hide() } },
        topBar = {
            CustomTopAppBar(
                title = { Text(stringResource(R.string.deck_anagram_title_format, state.deckWithCards.deck.name)) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
                },
                actions = {
                    IconButton(
                        onClick = { showEditDialog = true },
                        enabled = state.correctAnswerFound
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_card))
                    }
                }
            )
        }
    ) { padding ->
        val rootFocusRequester = remember { FocusRequester() }

        // When the card is answered, the text field becomes disabled and loses focus.
        // We explicitly grab focus on the root box so hardware keys keep working.
        LaunchedEffect(state.correctAnswerFound) {
            if (state.correctAnswerFound) {
                rootFocusRequester.requestFocus()
            }
        }

        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .focusRequester(rootFocusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    val currentCard = state.shuffledCards.getOrNull(state.currentCardIndex) ?: return@onPreviewKeyEvent false

                    val isHandledKeyDown = event.type == KeyEventType.KeyDown && (
                            (state.correctAnswerFound && event.key in listOf(Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionRight, Key.DirectionLeft, Key.K, Key.U, Key.One, Key.Two, Key.Three, Key.Four, Key.Five, Key.NumPad1, Key.NumPad2, Key.NumPad3, Key.NumPad4, Key.NumPad5)) ||
                                    (!state.correctAnswerFound && event.key in listOf(Key.Enter, Key.NumPadEnter))
                            )

                    // Consume handled down presses to stop default UI scrolling
                    if (isHandledKeyDown) return@onPreviewKeyEvent true

                    if (event.type == KeyEventType.KeyUp) {
                        if (state.correctAnswerFound) {
                            // Card is solved: Intercept navigation and grading
                            when (event.key) {
                                Key.Spacebar, Key.Enter, Key.NumPadEnter, Key.DirectionRight -> { viewModel.nextCard(); return@onPreviewKeyEvent true }
                                Key.DirectionLeft -> { viewModel.previousCard(); return@onPreviewKeyEvent true }
                                Key.K, Key.U -> { viewModel.toggleCardKnownStatus(currentCard); return@onPreviewKeyEvent true }
                                Key.One, Key.NumPad1 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.ONE); return@onPreviewKeyEvent true }
                                Key.Two, Key.NumPad2 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.TWO); return@onPreviewKeyEvent true }
                                Key.Three, Key.NumPad3 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.THREE); return@onPreviewKeyEvent true }
                                Key.Four, Key.NumPad4 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.FOUR); return@onPreviewKeyEvent true }
                                Key.Five, Key.NumPad5 -> { viewModel.updateCardDifficulty(currentCard, DifficultySetting.FIVE); return@onPreviewKeyEvent true }
                            }
                        } else {
                            // User is actively typing: Only intercept Enter to reveal the answer
                            when (event.key) {
                                Key.Enter, Key.NumPadEnter -> { viewModel.revealQuizAnswer(); return@onPreviewKeyEvent true }
                            }
                        }
                    }
                    false
                }
        ) {
            if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                LandscapeAnagramLayout(state = state, viewModel = viewModel, inputController = inputController)
            } else {
                PortraitAnagramLayout(state = state, viewModel = viewModel, inputController = inputController)
            }
        }
    }
}

@Composable
fun PortraitAnagramLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    inputController: net.ericclark.studiare.components.LetterInputController
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    var userAnswer by remember(state.currentCardIndex) { mutableStateOf("") }

    val allTags by viewModel.tags.collectAsState()

    // 2. Filter to find the full definitions for this card's tags
    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMedium),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Top
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                tags = cardTags
            )
            Spacer(Modifier.height(dimensions.spacingSmall))

            AnagramInteractionContent(
                state = state,
                userAnswer = userAnswer,
                onUserAnswerChange = { userAnswer = it },
                inputController = inputController,
                viewModel = viewModel
            )

            if (state.correctAnswerFound) {
                Spacer(Modifier.height(dimensions.paddingMedium))
                val card = state.shuffledCards[state.currentCardIndex]
                var difficulty by remember(card.id) { mutableStateOf(card.difficulty) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    DifficultySlider(
                        label = getText(R.string.rate_difficulty),
                        difficulty = difficulty,
                        onDifficultyChange = {
                            difficulty = it
                            viewModel.updateCardDifficulty(card, it)
                        },
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(dimensions.spacingMedium))
                    Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                        MarkKnownButton(
                            isKnown = card.isKnown,
                            onClick = { viewModel.toggleCardKnownStatus(card) }
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = dimensions.paddingMedium),
            horizontalArrangement = Arrangement.Center
        ) {
            // Smooth text crossfade instead of instant button snap
            Button(
                onClick = {
                    if (state.correctAnswerFound) {
                        viewModel.nextCard()
                    } else {
                        viewModel.revealQuizAnswer()
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f).defaultMinSize(minHeight = 56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = state.correctAnswerFound,
                    label = "anagramButtonAnim"
                ) { isRevealed ->
                    Text(getText(if (isRevealed) R.string.next_card else R.string.get_answer))
                }
            }
        }
    }
}

@Composable
fun LandscapeAnagramLayout(
    state: StudyState,
    viewModel: FlashcardViewModel,
    inputController: net.ericclark.studiare.components.LetterInputController
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    var userAnswer by remember(state.currentCardIndex) { mutableStateOf("") }

    val allTags by viewModel.tags.collectAsState()

    val cardTags = remember(card.tags, allTags) {
        allTags.filter { it.name in card.tags }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(dimensions.paddingMedium),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            QuizCardContent(
                state = state,
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                tags = cardTags
            )
        }
        Spacer(Modifier.width(dimensions.paddingMedium))
        // Right Column
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnagramInteractionContent(
                    state = state,
                    userAnswer = userAnswer,
                    onUserAnswerChange = { userAnswer = it },
                    inputController = inputController,
                    viewModel = viewModel
                )

                if (state.correctAnswerFound) {
                    Spacer(Modifier.height(dimensions.paddingMedium))
                    val card = state.shuffledCards[state.currentCardIndex]
                    var difficulty by remember(card.id) { mutableStateOf(card.difficulty) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DifficultySlider(
                            label = getText(R.string.rate_difficulty),
                            difficulty = difficulty,
                            onDifficultyChange = {
                                difficulty = it
                                viewModel.updateCardDifficulty(card, it)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(dimensions.spacingMedium))
                        Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
                            MarkKnownButton(
                                isKnown = card.isKnown,
                                onClick = { viewModel.toggleCardKnownStatus(card) }
                            )
                        }
                    }
                }
            }

            // Smooth text crossfade instead of instant button snap
            Button(
                onClick = {
                    if (state.correctAnswerFound) {
                        viewModel.nextCard()
                    } else {
                        viewModel.revealQuizAnswer()
                    }
                },
                modifier = Modifier.fillMaxWidth(0.8f).defaultMinSize(minHeight = 56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) {
                androidx.compose.animation.AnimatedContent(
                    targetState = state.correctAnswerFound,
                    label = "anagramButtonAnim"
                ) { isRevealed ->
                    Text(getText(if (isRevealed) R.string.next_card else R.string.get_answer))
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun AnagramInteractionContent(
    state: StudyState,
    userAnswer: String,
    onUserAnswerChange: (String) -> Unit,
    inputController: net.ericclark.studiare.components.LetterInputController,
    viewModel: FlashcardViewModel
) {
    val dimensions = LocalStudiareDimensions.current
    val card = state.shuffledCards[state.currentCardIndex]
    val answerText = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
    val answerWithoutSpaces = remember(answerText) { answerText.replace(" ", "") }

    // Shuffling Logic
    val shuffledLetters = remember(answerText) {
        val clean = answerText.replace(" ", "").uppercase()
        if (clean.length <= 1) return@remember clean

        val chars = clean.toCharArray()
        var shuffled: CharArray
        var attempts = 0

        do {
            shuffled = chars.clone()
            shuffled.shuffle()

            var matches = 0
            for (i in chars.indices) {
                if (chars[i] == shuffled[i]) matches++
            }
            attempts++
        } while (matches > 1 && attempts < 100)

        String(shuffled)
    }

    // Only letters still available in the bank can be entered (each tile can be used once).
    val bankCounts = remember(shuffledLetters) { shuffledLetters.groupingBy { it.uppercaseChar() }.eachCount() }

    val onAnswerChange = { newValue: String ->
        if (!state.correctAnswerFound) {
            val remaining = bankCounts.toMutableMap()
            val filteredValue = buildString {
                for (c in newValue) {
                    if (c == ' ') continue
                    val left = remaining[c.uppercaseChar()] ?: 0
                    if (left > 0) { append(c); remaining[c.uppercaseChar()] = left - 1 }
                }
            }
            if (filteredValue.length <= answerWithoutSpaces.length) {
                onUserAnswerChange(filteredValue)
                if (filteredValue.length == answerWithoutSpaces.length &&
                    filteredValue.equals(answerWithoutSpaces, ignoreCase = true)
                ) {
                    viewModel.submitTypingCorrect()
                }
            }
        }
    }

    // The hidden input isn't a Compose text field, so keep the answer tiles visible above the keyboard ourselves.
    val bringIntoView = remember { androidx.compose.foundation.relocation.BringIntoViewRequester() }
    val imeVisible = androidx.compose.foundation.layout.WindowInsets.isImeVisible
    LaunchedEffect(imeVisible, state.currentCardIndex) {
        if (imeVisible) {
            delay(200)
            bringIntoView.bringIntoView()
        }
    }

    Column(
        modifier = Modifier.bringIntoViewRequester(bringIntoView),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        AnimatedVisibility(visible = state.correctAnswerFound) {
            Text(
                getText(R.string.correct_exclamation),
                color = Color(0xFF22C55E),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = dimensions.paddingSmall)
            )
        }

        AnagramInput(
            userValue = if (state.correctAnswerFound) answerWithoutSpaces else userAnswer,
            onValueChange = onAnswerChange,
            answerText = answerText,
            shuffledLetters = shuffledLetters,
            inputController = inputController,
            enabled = !state.correctAnswerFound,
            showCorrectLetters = state.showCorrectLetters
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AnagramInput(
    userValue: String,
    onValueChange: (String) -> Unit,
    answerText: String,
    shuffledLetters: String,
    inputController: net.ericclark.studiare.components.LetterInputController,
    enabled: Boolean,
    showCorrectLetters: Boolean
) {
    val dimensions = LocalStudiareDimensions.current
    val correctColor = Color(0xFF22C55E)
    val incorrectColor = MaterialTheme.colorScheme.error
    val defaultBorderColor = MaterialTheme.colorScheme.onSurfaceVariant
    val filledColor = MaterialTheme.colorScheme.primary
    // LOGIC: Determine which scrambled letters are "used"
    val usedIndices = remember(userValue, shuffledLetters) {
        val used = BooleanArray(shuffledLetters.length)
        val userChars = userValue.toMutableList() // Copy mutable list of chars typed by user

        // Iterate through scrambled source. If a char matches one the user typed, mark used.
        for (i in shuffledLetters.indices) {
            val char = shuffledLetters[i]
            val foundIndex = userChars.indexOfFirst { it.equals(char, true) }
            if (foundIndex != -1) {
                used[i] = true
                userChars.removeAt(foundIndex) // Consume this char so we don't mark duplicates
            }
        }
        used
    }

            // Keyboard trigger and hidden input
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(enabled = enabled) { inputController.show() },
                contentAlignment = Alignment.Center
            ) {
                if (enabled) {
                    net.ericclark.studiare.components.LetterInput(
                        controller = inputController,
                        onText = { typed -> onValueChange(userValue + typed) },
                        onBackspace = { onValueChange(userValue.dropLast(1)) },
                        modifier = Modifier.size(1.dp).alpha(0f)
                    )
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {

                    // 1. Source Row (Scrambled Letters)
                    Text(getText(R.string.unscramble_colon), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(dimensions.spacingSmall))

                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val words = answerText.split(' ')
                        var charIndex = 0

                        words.forEachIndexed { wordIndex, word ->
                            word.forEach { _ ->
                                val charToShow =
                                    shuffledLetters.getOrNull(charIndex)?.toString() ?: ""
                                val isUsed = usedIndices.getOrElse(charIndex) { false }

                                // Visual removal of box if used
                                val boxBackground =
                                    if (isUsed) Color.Transparent else MaterialTheme.colorScheme.secondaryContainer
                                val textColor =
                                    if (isUsed) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f) else MaterialTheme.colorScheme.onSecondaryContainer

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .size(40.dp) // Fixed size for tiles is usually better for alignment
                                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                                        .background(boxBackground)
                                        // Unused bank letters can be tapped to type them.
                                        // Only attach a click handler when it can act; a disabled one still swallows the tap.
                                        .then(
                                            if (enabled && !isUsed) Modifier.clickable { onValueChange(userValue + charToShow) }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = charToShow,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = textColor
                                    )
                                }
                                charIndex++
                            }
                            if (wordIndex < words.size - 1) {
                                Spacer(modifier = Modifier.width(dimensions.spacingLarge))
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    // 2. Target Row (User Input)
                    Text(getText(R.string.your_answer_colon), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(dimensions.spacingSmall))

                    FlowRow(
                        horizontalArrangement = Arrangement.Center,
                        verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val words = answerText.split(' ')
                        var charIndex = 0

                        words.forEachIndexed { wordIndex, word ->
                            word.forEach { targetChar ->
                                val userChar = userValue.getOrNull(charIndex)

                                val borderColor = when {
                                    !enabled -> correctColor
                                    userChar == null -> defaultBorderColor
                                    showCorrectLetters -> {
                                        if (userChar.equals(
                                                targetChar,
                                                ignoreCase = true
                                            )
                                        ) correctColor else incorrectColor
                                    }

                                    else -> filledColor
                                }

                                val boxBackground = MaterialTheme.colorScheme.surface

                                // Wrong letters (or any letter, when wrong ones aren't revealed)
                                // can be tapped to remove them from the answer.
                                val isRemovable = enabled && userChar != null &&
                                        (!showCorrectLetters || !userChar.equals(targetChar, ignoreCase = true))
                                val tileIndex = charIndex

                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                                        .background(boxBackground)
                                        .border(
                                            BorderStroke(2.dp, borderColor),
                                            RoundedCornerShape(dimensions.cornerRadiusSmall)
                                        )
                                        .then(
                                            if (isRemovable) Modifier.clickable { onValueChange(userValue.removeRange(tileIndex, tileIndex + 1)) }
                                            else Modifier
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (userChar != null) {
                                        val textColor = if (showCorrectLetters && enabled) {
                                            if (userChar.equals(
                                                    targetChar,
                                                    ignoreCase = true
                                                )
                                            ) correctColor else incorrectColor
                                        } else if (!enabled) {
                                            correctColor
                                        } else {
                                            LocalContentColor.current
                                        }

                                        Text(
                                            text = userChar.toString().uppercase(),
                                            style = MaterialTheme.typography.headlineSmall,
                                            color = textColor
                                        )
                                    }
                                }
                                charIndex++
                            }
                            if (wordIndex < words.size - 1) {
                                Spacer(modifier = Modifier.width(dimensions.spacingLarge))
                            }
                        }
                    }
                }
            }
}
