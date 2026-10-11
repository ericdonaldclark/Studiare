package net.ericclark.studiare.studymodes

import androidx.compose.foundation.background
import net.ericclark.studiare.TooltipIconButton
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.material.icons.filled.Explore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.navigation.NavController
import kotlin.collections.component1
import kotlin.collections.component2
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.LocalWindowWidthSizeClass
import net.ericclark.studiare.LocalWindowHeightSizeClass
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Surface
import androidx.compose.ui.input.pointer.pointerInput
import net.ericclark.studiare.screens.Screens.StudyCompletionScreen

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CrosswordScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
    val dimensions = LocalStudiareDimensions.current
    val state = viewModel.studyState ?: return
    val inputController = net.ericclark.studiare.components.rememberLetterInputController()

    val isCompact = windowWidthSizeClass == WindowWidthSizeClass.Compact || windowHeightSizeClass == WindowHeightSizeClass.Compact
    val isImeVisible = WindowInsets.isImeVisible
    val showFloatingClue = isCompact && isImeVisible && state.crosswordSelectedWordId != null

    if (state.isComplete) {
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
        return
    }

    // Autofocus logic to handle keyboard input
    LaunchedEffect(state.crosswordSelectedCell) {
        if (state.crosswordSelectedCell != null) {
            inputController.show()
        }
    }

    var resetViewTrigger by remember { mutableIntStateOf(0) }

    var showJumpDialog by remember { mutableStateOf(false) }
    var jumpText by remember { mutableStateOf("") }
    val jumpFocusRequester = remember { FocusRequester() }

    var isListFocused by remember { mutableStateOf(false) }
    var selectedTab by remember { mutableStateOf(0) }

    val acrossWords = remember(state.crosswordWords) { state.crosswordWords.filter { it.isAcross }.sortedBy { it.number } }
    val downWords = remember(state.crosswordWords) { state.crosswordWords.filter { !it.isAcross }.sortedBy { it.number } }

    LaunchedEffect(state.crosswordSelectedWordId) {
        val activeWord = state.crosswordWords.find { it.id == state.crosswordSelectedWordId }
        if (activeWord != null) {
            selectedTab = if (activeWord.isAcross) 0 else 1
        }
    }

    // Request focus on the jump dialog text field when it opens
    LaunchedEffect(showJumpDialog) {
        if (showJumpDialog) jumpFocusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.CROSSWORD,
                title = { Text(getText(R.string.crossword)) },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = { viewModel.endStudySession(); navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                    }
                },
                actions = {
                    TooltipIconButton(description = getText(R.string.reset_view), onClick = { resetViewTrigger++ }) {
                        Icon(Icons.Default.Explore, contentDescription = getText(R.string.reset_view))
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type == KeyEventType.KeyDown) {
                        val currentList = if (selectedTab == 0) acrossWords else downWords

                        // 1. Trigger Jump Dialog (Slash or Ctrl+J)
                        if (event.key == Key.Slash || (event.isCtrlPressed && event.key == Key.J)) {
                            showJumpDialog = true
                            return@onPreviewKeyEvent true
                        }

                        // 2. Toggle Clue List Focus (Alt + C)
                        if (event.isAltPressed && (event.key == Key.C || event.key == Key.Slash)) {
                            isListFocused = !isListFocused
                            return@onPreviewKeyEvent true
                        }

                        if (isListFocused) {
                            // --- LIST NAVIGATION MODE ---
                            when (event.key) {
                                Key.Tab -> {
                                    selectedTab = if (selectedTab == 0) 1 else 0
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionUp -> {
                                    val currentIndex = currentList.indexOfFirst { it.id == state.crosswordSelectedWordId }
                                    if (currentIndex > 0) {
                                        viewModel.selectCrosswordWord(currentList[currentIndex - 1].id)
                                    } else if (currentIndex == -1 && currentList.isNotEmpty()) {
                                        viewModel.selectCrosswordWord(currentList.last().id)
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                Key.DirectionDown -> {
                                    val currentIndex = currentList.indexOfFirst { it.id == state.crosswordSelectedWordId }
                                    if (currentIndex < currentList.size - 1 && currentIndex != -1) {
                                        viewModel.selectCrosswordWord(currentList[currentIndex + 1].id)
                                    } else if (currentIndex == -1 && currentList.isNotEmpty()) {
                                        viewModel.selectCrosswordWord(currentList.first().id)
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                Key.H -> {
                                    val wordId = state.crosswordSelectedWordId
                                    if (wordId != null) {
                                        viewModel.provideCrosswordHint(wordId, fillEntireWord = event.isShiftPressed)
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                Key.Enter, Key.NumPadEnter, Key.Escape -> {
                                    isListFocused = false
                                    return@onPreviewKeyEvent true
                                }
                            }
                        } else {
                            // --- GRID NAVIGATION MODE ---
                            val currentCell = state.crosswordSelectedCell
                            if (currentCell != null && !showJumpDialog) {
                                val (x, y) = currentCell
                                when (event.key) {
                                    Key.Backspace, Key.Delete -> { viewModel.deleteCrosswordChar(); return@onPreviewKeyEvent true }
                                    Key.DirectionUp -> { viewModel.selectCrosswordCell(x, maxOf(0, y - 1)); return@onPreviewKeyEvent true }
                                    Key.DirectionDown -> { viewModel.selectCrosswordCell(x, minOf(state.crosswordGridHeight - 1, y + 1)); return@onPreviewKeyEvent true }
                                    Key.DirectionLeft -> { viewModel.selectCrosswordCell(maxOf(0, x - 1), y); return@onPreviewKeyEvent true }
                                    Key.DirectionRight -> { viewModel.selectCrosswordCell(minOf(state.crosswordGridWidth - 1, x + 1), y); return@onPreviewKeyEvent true }

                                    Key.Enter, Key.NumPadEnter -> {
                                        val overlappingWords = state.crosswordWords.filter { word ->
                                            if (word.isAcross) y == word.startY && x in word.startX until (word.startX + word.word.length)
                                            else x == word.startX && y in word.startY until (word.startY + word.word.length)
                                        }
                                        if (overlappingWords.size > 1) {
                                            val nextSelected = overlappingWords.firstOrNull { it.id != state.crosswordSelectedWordId }
                                            if (nextSelected != null) {
                                                viewModel.selectCrosswordWord(nextSelected.id)
                                                viewModel.selectCrosswordCell(x, y) // Keep cursor on current cell
                                            }
                                        }
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            }
                        }
                    }
                    false
                }
        ) {
            val totalWidthPx = constraints.maxWidth.toFloat()
            val totalHeightPx = constraints.maxHeight.toFloat()
            val isLandscape = maxWidth > maxHeight

            var listFraction by remember { mutableFloatStateOf(0.4f) }

            // Hidden Input for Keyboard capture
            // We use a 1x1 pixel field to capture input and route it to the ViewModel
            // Hidden input that captures the soft/hardware keyboard and routes it to the ViewModel.
            net.ericclark.studiare.components.LetterInput(
                controller = inputController,
                onText = { typed -> typed.filter { it.isLetterOrDigit() }.forEach { viewModel.submitCrosswordChar(it) } },
                onBackspace = { viewModel.deleteCrosswordChar() },
                modifier = Modifier.size(1.dp).alpha(0f)
            )

            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f - listFraction)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clip(RoundedCornerShape(topEnd = dimensions.cornerRadiusLarge, bottomEnd = dimensions.cornerRadiusLarge))
                    ) {
                        CrosswordGridArea(state, viewModel, resetViewTrigger)
                    }

                    // Vertical Drag Handle for Side Sheet
                    Box(
                        modifier = Modifier
                            .width(24.dp)
                            .fillMaxHeight()
                            .clickable {
                                listFraction = if (listFraction > 0f) 0f else 0.4f
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaFraction = dragAmount.x / totalWidthPx
                                    val proposedFraction = listFraction - deltaFraction

                                    if (listFraction > 0f && proposedFraction < 0.10f) {
                                        listFraction = 0f // Snap closed
                                    } else if (listFraction == 0f && deltaFraction < 0) {
                                        listFraction = 0.25f // Snap open
                                    } else if (listFraction > 0f) {
                                        listFraction = proposedFraction.coerceIn(0.10f, 0.8f)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(48.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                        )
                    }

                    if (listFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(listFraction)
                                .fillMaxHeight()
                        ) {
                            CrosswordClueList(
                                state = state,
                                viewModel = viewModel,
                                isListFocused = isListFocused,
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                acrossWords = acrossWords,
                                downWords = downWords
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f - listFraction)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clip(RoundedCornerShape(bottomStart = dimensions.cornerRadiusLarge, bottomEnd = dimensions.cornerRadiusLarge))
                    ) {
                        CrosswordGridArea(state, viewModel, resetViewTrigger)
                    }

                    // Horizontal Drag Handle for Bottom Sheet
                    Box(
                        modifier = Modifier
                            .height(24.dp)
                            .fillMaxWidth()
                            .clickable {
                                listFraction = if (listFraction > 0f) 0f else 0.4f
                            }
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    val deltaFraction = dragAmount.y / totalHeightPx
                                    val proposedFraction = listFraction - deltaFraction

                                    if (listFraction > 0f && proposedFraction < 0.10f) {
                                        listFraction = 0f // Snap closed
                                    } else if (listFraction == 0f && deltaFraction < 0) {
                                        listFraction = 0.25f // Snap open
                                    } else if (listFraction > 0f) {
                                        listFraction = proposedFraction.coerceIn(0.10f, 0.8f)
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .height(4.dp)
                                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                        )
                    }

                    if (listFraction > 0f) {
                        Box(
                            modifier = Modifier
                                .weight(listFraction)
                                .fillMaxWidth()
                        ) {
                            CrosswordClueList(
                                state = state,
                                viewModel = viewModel,
                                isListFocused = isListFocused,
                                selectedTab = selectedTab,
                                onTabSelected = { selectedTab = it },
                                acrossWords = acrossWords,
                                downWords = downWords
                            )
                        }
                    }
                }
            }

            // Floating Clue Bubble for Compact Screens
            androidx.compose.animation.AnimatedVisibility(
                visible = showFloatingClue,
                enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInVertically(initialOffsetY = { -it }),
                exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutVertically(targetOffsetY = { -it }),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(dimensions.paddingMedium)
                    .zIndex(10f)
            ) {
                val activeWord = state.crosswordWords.find { it.id == state.crosswordSelectedWordId }
                if (activeWord != null) {
                    // Across clues first, then down, matching the clue list; wraps at both ends.
                    val orderedClues = acrossWords + downWords
                    val activeIndex = orderedClues.indexOfFirst { it.id == activeWord.id }
                    val selectClueOffset = { delta: Int ->
                        if (orderedClues.isNotEmpty() && activeIndex != -1) {
                            val target = orderedClues[(activeIndex + delta + orderedClues.size) % orderedClues.size]
                            viewModel.selectCrosswordWord(target.id)
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(24.dp), // M3 Expressive pill/bubble shape
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                        shadowElevation = 6.dp,
                        modifier = Modifier.widthIn(max = 360.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            net.ericclark.studiare.TooltipIconButton(
                                description = getText(R.string.previous_clue),
                                onClick = { selectClueOffset(-1) }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = getText(R.string.previous_clue))
                            }
                            Text(
                                text = "${activeWord.number}${if (activeWord.isAcross) "A" else "D"}: ${activeWord.clue}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f, fill = false).padding(vertical = 12.dp)
                            )
                            net.ericclark.studiare.TooltipIconButton(
                                description = getText(R.string.next_clue),
                                onClick = { selectClueOffset(1) }
                            ) {
                                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = getText(R.string.next_clue))
                            }
                        }
                    }
                }
            }
        }
        if (showJumpDialog) {
            val executeJump = {
                val isAcross = jumpText.contains("a", ignoreCase = true) || jumpText.contains("across", ignoreCase = true)
                val isDown = jumpText.contains("d", ignoreCase = true) || jumpText.contains("down", ignoreCase = true)
                val number = jumpText.filter { it.isDigit() }.toIntOrNull()

                if (number != null) {
                    val word = state.crosswordWords.find {
                        it.number == number && (
                                (isAcross && it.isAcross) ||
                                        (isDown && !it.isAcross) ||
                                        (!isAcross && !isDown) // Fallback if they just type a number
                                )
                    } ?: state.crosswordWords.find { it.number == number }

                    if (word != null) {
                        viewModel.selectCrosswordWord(word.id)
                    }
                }
                showJumpDialog = false
                jumpText = ""
            }

            net.ericclark.studiare.AnimatedDialog(onDismissRequest = { showJumpDialog = false; jumpText = "" }) {
                androidx.compose.material3.Surface(
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                        Text(getText(R.string.jump_to_clue_title), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(dimensions.spacingMedium))
                        OutlinedTextField(
                            value = jumpText,
                            onValueChange = { jumpText = it },
                            placeholder = { Text(getText(R.string.crossword_goto_placeholder)) },
                            singleLine = true,
                            modifier = Modifier
                                .focusRequester(jumpFocusRequester)
                                .onPreviewKeyEvent { event ->
                                    if (event.type == KeyEventType.KeyDown && event.key == Key.Escape) {
                                        showJumpDialog = false
                                        jumpText = ""
                                        true
                                    } else {
                                        false
                                    }
                                },
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                            keyboardActions = KeyboardActions(onGo = { executeJump() })
                        )
                        Spacer(Modifier.height(dimensions.spacingLarge))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            androidx.compose.material3.TextButton(onClick = { showJumpDialog = false; jumpText = "" }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                                Text(getText(R.string.cancel))
                            }
                            Spacer(Modifier.width(dimensions.spacingSmall))
                            Button(onClick = executeJump, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.go)) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun CrosswordCellView(
    char: Char?,
    number: Int?,
    isSelected: Boolean,
    isActiveWord: Boolean,
    isWordCompleted: Boolean,
    modifier: Modifier,
    isWrong: Boolean = false
) {
    // Fluid Color Transitions for Grid Cells
    val targetBgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer // Highlights specific cell
        isActiveWord -> MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.4f) // Highlights word track
        else -> MaterialTheme.colorScheme.surface
    }

    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "cellBgAnim"
    )

    // Border Logic
    val targetBorderColor = if (isWordCompleted) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline
    val borderColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetBorderColor,
        animationSpec = androidx.compose.animation.core.tween(200),
        label = "cellBorderAnim"
    )
    val borderWidth = if (isWordCompleted) 2.dp else 1.dp

    Box(
        modifier = modifier
            .border(borderWidth, borderColor)
            .background(bgColor)
    ) {
        if (number != null) {
            Text(
                text = number.toString(),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(2.dp).align(Alignment.TopStart),
                fontSize = 8.sp
            )
        }
        if (char != null) {
            Text(
                text = char.toString(),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = if (isWrong) MaterialTheme.colorScheme.error else LocalContentColor.current,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
