package net.ericclark.studiare.studymodes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.StudyCompletionScreen
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.StudyState
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import kotlin.math.abs
import kotlin.math.roundToInt

@Composable
fun WordSearchMode(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val dimensions = LocalStudiareDimensions.current
    val state = viewModel.studyState ?: return

    if (state.isComplete) {
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
        return
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text(getText(R.string.mode_word_search)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.endStudySession(); navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    .clip(RoundedCornerShape(bottomStart = dimensions.cornerRadiusLarge, bottomEnd = dimensions.cornerRadiusLarge))
            ) {
                WordSearchGridArea(state, viewModel)
            }

            WordSearchClueList(state)
        }
    }
}

@Composable
fun WordSearchGridArea(state: StudyState, viewModel: FlashcardViewModel) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 4f)
        offset += panChange
    }

    val cellSize = 40.dp
    val gridW = state.wordSearchGridWidth
    val gridH = state.wordSearchGridHeight

    var dragStartCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragCurrentCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val density = LocalDensity.current

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
        val widthPx = with(density) { (gridW * cellSize.toPx()).toDp() }
        val heightPx = with(density) { (gridH * cellSize.toPx()).toDp() }

        Box(
            modifier = Modifier
                .size(widthPx, heightPx)
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { startOffset ->
                            val startX = (startOffset.x / cellSize.toPx()).toInt()
                            val startY = (startOffset.y / cellSize.toPx()).toInt()
                            if (startX in 0 until gridW && startY in 0 until gridH) {
                                dragStartCell = startX to startY
                                dragCurrentCell = startX to startY
                            }
                        },
                        onDrag = { change, _ ->
                            val currentX = (change.position.x / cellSize.toPx()).toInt()
                            val currentY = (change.position.y / cellSize.toPx()).toInt()
                            if (currentX in 0 until gridW && currentY in 0 until gridH) {
                                // Constrain to straight lines or diagonals
                                dragStartCell?.let { start ->
                                    val dx = currentX - start.first
                                    val dy = currentY - start.second

                                    // Snap to direction
                                    if (dx == 0 || dy == 0 || abs(dx) == abs(dy)) {
                                        dragCurrentCell = currentX to currentY
                                    }
                                }
                            }
                        },
                        onDragEnd = {
                            if (dragStartCell != null && dragCurrentCell != null) {
                                viewModel.submitWordSearchMatch(dragStartCell!!, dragCurrentCell!!)
                            }
                            dragStartCell = null
                            dragCurrentCell = null
                        },
                        onDragCancel = {
                            dragStartCell = null
                            dragCurrentCell = null
                        }
                    )
                }
        ) {
            val primaryColor = MaterialTheme.colorScheme.primary

            androidx.compose.foundation.Canvas(modifier = Modifier.matchParentSize()) {
                val halfCell = cellSize.toPx() / 2f
                val strokeWidth = cellSize.toPx() * 0.8f

                // Draw found words
                val foundColor = Color(0xFF22C55E).copy(alpha = 0.4f)
                state.wordSearchWords.filter { it.id in state.wordSearchFoundWordIds }.forEach { word ->
                    val startOffset = Offset(word.startX * cellSize.toPx() + halfCell, word.startY * cellSize.toPx() + halfCell)
                    val endOffset = Offset(word.endX * cellSize.toPx() + halfCell, word.endY * cellSize.toPx() + halfCell)
                    drawLine(
                        color = foundColor,
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }

                // Draw current drag line
                if (dragStartCell != null && dragCurrentCell != null) {
                    val startOffset = Offset(dragStartCell!!.first * cellSize.toPx() + halfCell, dragStartCell!!.second * cellSize.toPx() + halfCell)
                    val endOffset = Offset(dragCurrentCell!!.first * cellSize.toPx() + halfCell, dragCurrentCell!!.second * cellSize.toPx() + halfCell)
                    drawLine(
                        color = primaryColor.copy(alpha = 0.4f),
                        start = startOffset,
                        end = endOffset,
                        strokeWidth = strokeWidth,
                        cap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                }
            }

            for (y in 0 until gridH) {
                for (x in 0 until gridW) {
                    val char = state.wordSearchGrid.getOrNull(y)?.getOrNull(x) ?: ' '

                    WordSearchCellView(
                        char = char,
                        modifier = Modifier
                            .size(cellSize)
                            .offset(
                                x = with(density) { (x * cellSize.toPx()).toDp() },
                                y = with(density) { (y * cellSize.toPx()).toDp() }
                            )
                    )
                }
            }
        }
    }
}

@Composable
fun WordSearchCellView(
    char: Char,
    modifier: Modifier
) {

    val borderWidth = 1.dp
    val borderColor = MaterialTheme.colorScheme.outlineVariant

    Box(
        modifier = modifier
            .border(borderWidth, borderColor)
    ) {
        Text(
            text = char.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun WordSearchClueList(state: StudyState) {
    val dimensions = LocalStudiareDimensions.current
    val sortedWords = remember(state.wordSearchWords) { state.wordSearchWords.sortedBy { it.clue.lowercase() } }

    Column(modifier = Modifier.height(250.dp).background(MaterialTheme.colorScheme.surfaceContainer)) {
        HorizontalDivider()

        Text(
            text = "Clues to Find",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(dimensions.paddingMedium)
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = dimensions.paddingMedium, end = dimensions.paddingMedium, bottom = dimensions.paddingMedium)
        ) {
            items(sortedWords) { word ->
                val isCompleted = word.id in state.wordSearchFoundWordIds

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "•",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(24.dp),
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f) else MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = word.clue,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}