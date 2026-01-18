package net.ericclark.studiare.studymodes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.StudyCompletionScreen
import net.ericclark.studiare.data.*
import kotlin.collections.component1
import kotlin.collections.component2
import androidx.compose.ui.text.style.TextDecoration

@Composable
fun CrosswordScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val state = viewModel.studyState ?: return
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    if (state.isComplete) {
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
        return
    }

    // Auto-focus logic to handle keyboard input
    LaunchedEffect(state.crosswordSelectedCell) {
        if (state.crosswordSelectedCell != null) {
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("Crossword") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.endStudySession(); navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {

            // 1. Zoomable Grid Area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.3f))
                    .clip(RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp))
            ) {
                CrosswordGridArea(state, viewModel)
            }

            // 2. Hidden Input for Keyboard capture
            // We use a 1x1 pixel field to capture input and route it to the ViewModel
            var textInput by remember { mutableStateOf("") }
            BasicTextField(
                value = textInput,
                onValueChange = {
                    if (it.isNotEmpty()) {
                        viewModel.submitCrosswordChar(it.last())
                        textInput = ""
                    }
                },
                modifier = Modifier
                    .size(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            // 3. Clue List Area
            CrosswordClueList(state, viewModel)
        }
    }
}

@Composable
fun CrosswordGridArea(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 4f)
        offset += panChange
    }

    val cellSize = 40.dp
    val gridW = state.crosswordGridWidth
    val gridH = state.crosswordGridHeight

    // Helper data class for cell rendering (Internal to this function context)
    data class CellRenderData(
        val char: Char?,
        val number: Int?,
        val isSelected: Boolean,
        val isActiveWord: Boolean,
        val isWordCompleted: Boolean
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformableState)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
                translationX = offset.x,
                translationY = offset.y
            ),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val widthPx = with(density) { (gridW * cellSize.toPx()).toDp() }
        val heightPx = with(density) { (gridH * cellSize.toPx()).toDp() }

        // 1. Calculate Unique Cell States
        // Merges overlapping words so "Complete" status overrides "Incomplete" at intersections
        val uniqueCells = remember(state.crosswordWords, state.completedWordIds, state.crosswordUserInputs, state.crosswordSelectedCell, state.crosswordSelectedWordId) {
            val map = mutableMapOf<String, CellRenderData>()

            state.crosswordWords.forEach { word ->
                val isWordComplete = word.id in state.completedWordIds
                val isWordActive = word.id == state.crosswordSelectedWordId

                for (i in word.word.indices) {
                    val x = if (word.isAcross) word.startX + i else word.startX
                    val y = if (word.isAcross) word.startY else word.startY + i
                    val key = "$x,$y"

                    val existing = map[key]

                    val char = state.crosswordUserInputs[key]
                    val isSelected = state.crosswordSelectedCell == (x to y)

                    // Logic: If ANY word at this cell is complete, the cell is complete (Green)
                    val mergedComplete = (existing?.isWordCompleted == true) || isWordComplete
                    // Logic: If ANY word at this cell is active, the cell is active
                    val mergedActive = (existing?.isActiveWord == true) || isWordActive
                    // Logic: Keep number if already present, else add if start of this word
                    val number = existing?.number ?: if (i == 0) word.number else null

                    map[key] = CellRenderData(char, number, isSelected, mergedActive, mergedComplete)
                }
            }
            map
        }

        Box(modifier = Modifier.size(widthPx, heightPx)) {
            // 2. Render Unique Cells
            uniqueCells.forEach { (key, data) ->
                val parts = key.split(",")
                val x = parts[0].toInt()
                val y = parts[1].toInt()

                val xOffset = with(density) { (x * cellSize.toPx()).toDp() }
                val yOffset = with(density) { (y * cellSize.toPx()).toDp() }

                // Z-Index ensures green borders (completed) draw on top of adjacent gray borders
                val zIndex = if (data.isWordCompleted) 1f else 0f

                CrosswordCellView(
                    char = data.char,
                    number = data.number,
                    isSelected = data.isSelected,
                    isActiveWord = data.isActiveWord,
                    isWordCompleted = data.isWordCompleted,
                    modifier = Modifier
                        .size(cellSize)
                        .offset(xOffset, yOffset)
                        .zIndex(zIndex)
                        .clickable { viewModel.selectCrosswordCell(x, y) }
                )
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
    modifier: Modifier
) {
    val bgColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer // Highlights specific cell
        isActiveWord -> MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.4f) // Highlights word track
        else -> MaterialTheme.colorScheme.surface
    }

    // Border Logic
    val borderColor = if (isWordCompleted) Color(0xFF22C55E) else MaterialTheme.colorScheme.outline
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
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}

@Composable
fun CrosswordClueList(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Across", "Down")

    // Split clues
    val acrossWords = state.crosswordWords.filter { it.isAcross }.sortedBy { it.number }
    val downWords = state.crosswordWords.filter { !it.isAcross }.sortedBy { it.number }

    Column(modifier = Modifier.height(250.dp).background(MaterialTheme.colorScheme.surface)) {
        HorizontalDivider()
        TabRow(selectedTabIndex = selectedTab) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(title) }
                )
            }
        }

        val listToShow = if (selectedTab == 0) acrossWords else downWords

        LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
            items(listToShow) { word ->
                val isSelected = state.crosswordSelectedWordId == word.id
                val isCompleted = word.id in state.completedWordIds
                val backgroundColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(backgroundColor, RoundedCornerShape(4.dp))
                        .clickable {
                            viewModel.selectCrosswordWord(word.id)
                            // Auto-switch tab if user clicks on grid (handled via state observation ideally, but UI sync is fine here)
                        }
                        .padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${word.number}.",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp)
                    )
                    Text(
                        text = word.clue,
                        style = MaterialTheme.typography.bodyMedium,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f) else Color.Unspecified,
                        modifier = Modifier.weight(1f)
                    )

                    // NEW: Hint Button appearing only on selected row
                    if (isSelected && !isCompleted) {
                        Box(
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onLongPress = {
                                            viewModel.provideCrosswordHint(word.id, fillEntireWord = true)
                                        },
                                        onTap = {
                                            viewModel.provideCrosswordHint(word.id, fillEntireWord = false)
                                        }
                                    )
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "Hint",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }
    }
}

private data class CrosswordCellDisplayData(
    val number: Int? = null,
    val isWordCompleted: Boolean = false,
    val isActiveWord: Boolean = false
)