package net.ericclark.studiare.studymodes

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.ui.draw.scale
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
import androidx.compose.material.icons.filled.Explore
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlin.math.roundToInt
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onSizeChanged

@Composable
fun WordSearchMode(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val dimensions = LocalStudiareDimensions.current
    val state = viewModel.studyState ?: return

    val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
    var resetViewTrigger by remember { mutableIntStateOf(0) }

    var isListFocused by remember { mutableStateOf(false) }
    var selectedClueIndex by remember { mutableIntStateOf(0) }

    var keyboardCursor by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var keyboardSelectionStart by remember { mutableStateOf<Pair<Int, Int>?>(null) }

    val sortedWords = remember(state.wordSearchWords) { state.wordSearchWords.sortedBy { it.clue.lowercase() } }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

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
                title = { Text(getText(R.string.word_search)) },
                navigationIcon = {
                    IconButton(onClick = { viewModel.endStudySession(); navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { resetViewTrigger++ }) {
                        Icon(Icons.Default.Explore, contentDescription = "Reset View")
                    }
                }
            )
        }
    ) { padding ->
        BoxWithConstraints(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type == androidx.compose.ui.input.key.KeyEventType.KeyDown) {
                        // Toggle Clue List Focus (Alt + C)
                        if (event.isAltPressed && (event.key == androidx.compose.ui.input.key.Key.C || event.key == androidx.compose.ui.input.key.Key.Slash)) {
                            isListFocused = !isListFocused
                            if (!isListFocused && keyboardCursor == null) {
                                keyboardCursor = 0 to 0
                            }
                            return@onPreviewKeyEvent true
                        }

                        if (isListFocused) {
                            // --- LIST NAVIGATION ---
                            when (event.key) {
                                androidx.compose.ui.input.key.Key.DirectionUp, androidx.compose.ui.input.key.Key.DirectionLeft -> {
                                    if (selectedClueIndex > 0) selectedClueIndex--
                                    return@onPreviewKeyEvent true
                                }
                                androidx.compose.ui.input.key.Key.DirectionDown, androidx.compose.ui.input.key.Key.DirectionRight -> {
                                    if (selectedClueIndex < sortedWords.size - 1) selectedClueIndex++
                                    return@onPreviewKeyEvent true
                                }
                                androidx.compose.ui.input.key.Key.Enter, androidx.compose.ui.input.key.Key.NumPadEnter -> {
                                    val word = sortedWords.getOrNull(selectedClueIndex)
                                    if (word != null && word.id !in state.wordSearchFoundWordIds) {
                                        viewModel.submitWordSearchMatch(word.startX to word.startY, word.endX to word.endY)
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                androidx.compose.ui.input.key.Key.Escape -> {
                                    isListFocused = false
                                    if (keyboardCursor == null) keyboardCursor = 0 to 0
                                    return@onPreviewKeyEvent true
                                }
                            }
                        } else {
                            // --- GRID NAVIGATION ---
                            val currentCursor = keyboardCursor ?: (0 to 0)
                            val (x, y) = currentCursor
                            when (event.key) {
                                androidx.compose.ui.input.key.Key.DirectionUp -> { keyboardCursor = x to maxOf(0, y - 1); return@onPreviewKeyEvent true }
                                androidx.compose.ui.input.key.Key.DirectionDown -> { keyboardCursor = x to minOf(state.wordSearchGridHeight - 1, y + 1); return@onPreviewKeyEvent true }
                                androidx.compose.ui.input.key.Key.DirectionLeft -> { keyboardCursor = maxOf(0, x - 1) to y; return@onPreviewKeyEvent true }
                                androidx.compose.ui.input.key.Key.DirectionRight -> { keyboardCursor = minOf(state.wordSearchGridWidth - 1, x + 1) to y; return@onPreviewKeyEvent true }
                                androidx.compose.ui.input.key.Key.Escape -> {
                                    if (keyboardSelectionStart != null) {
                                        keyboardSelectionStart = null
                                    } else {
                                        keyboardCursor = null // Hide cursor
                                    }
                                    return@onPreviewKeyEvent true
                                }
                                androidx.compose.ui.input.key.Key.Enter, androidx.compose.ui.input.key.Key.NumPadEnter, androidx.compose.ui.input.key.Key.Spacebar -> {
                                    keyboardCursor = currentCursor // ensure it stays visible
                                    if (keyboardSelectionStart == null) {
                                        keyboardSelectionStart = currentCursor // Start drag
                                    } else {
                                        viewModel.submitWordSearchMatch(keyboardSelectionStart!!, currentCursor) // End drag
                                        keyboardSelectionStart = null
                                    }
                                    return@onPreviewKeyEvent true
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

            // Controls the percentage of the screen given to the clue list
            var listFraction by remember { mutableFloatStateOf(0.4f) }

            if (isLandscape) {
                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(if (listFraction > 0f) 1f - listFraction else 1f)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clip(RoundedCornerShape(topEnd = dimensions.cornerRadiusLarge, bottomEnd = dimensions.cornerRadiusLarge))
                    ) {
                        WordSearchGridArea(
                            state = state,
                            viewModel = viewModel,
                            keyboardCursor = keyboardCursor,
                            keyboardSelectionStart = keyboardSelectionStart,
                            resetViewTrigger = resetViewTrigger
                        )
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
                            WordSearchClueList(
                                state = state,
                                viewModel = viewModel,
                                isListFocused = isListFocused,
                                selectedClueIndex = selectedClueIndex
                            )
                        }
                    }
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(if (listFraction > 0f) 1f - listFraction else 1f)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clip(RoundedCornerShape(bottomStart = dimensions.cornerRadiusLarge, bottomEnd = dimensions.cornerRadiusLarge))
                    ) {
                        WordSearchGridArea(
                            state = state,
                            viewModel = viewModel,
                            keyboardCursor = keyboardCursor,
                            keyboardSelectionStart = keyboardSelectionStart,
                            resetViewTrigger = resetViewTrigger
                        )
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
                            WordSearchClueList(
                                state = state,
                                viewModel = viewModel,
                                isListFocused = isListFocused,
                                selectedClueIndex = selectedClueIndex
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WordSearchGridArea(
    state: StudyState,
    viewModel: FlashcardViewModel,
    keyboardCursor: Pair<Int, Int>?,
    keyboardSelectionStart: Pair<Int, Int>?,
    resetViewTrigger: Int = 0
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var layoutSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    LaunchedEffect(resetViewTrigger) {
        if (resetViewTrigger > 0) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    val transformableState = rememberTransformableState { centroid: Offset, zoomChange: Float, panChange: Offset, rotationChange: Float ->
        val oldScale = scale
        scale = (scale * zoomChange).coerceIn(0.5f, 4f)
        val actualZoom = scale / oldScale

        if (layoutSize != androidx.compose.ui.unit.IntSize.Zero) {
            // Explicitly separate the float math to prevent Offset operator inference errors
            val cx = layoutSize.width / 2f
            val cy = layoutSize.height / 2f

            val diffX = centroid.x - cx
            val diffY = centroid.y - cy

            offset = Offset(
                x = (diffX * (1f - actualZoom)) + (offset.x * actualZoom) + panChange.x,
                y = (diffY * (1f - actualZoom)) + (offset.y * actualZoom) + panChange.y
            )
        } else {
            offset = Offset(
                x = offset.x + panChange.x,
                y = offset.y + panChange.y
            )
        }
    }

    val cellSize = 40.dp
    val gridW = state.wordSearchGridWidth
    val gridH = state.wordSearchGridHeight

    var dragStartCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dragCurrentCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    val density = LocalDensity.current

    val borderWidth = 48.dp

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { layoutSize = it }
            .transformable(state = transformableState)
    ) {
        val cellSizePx = with(density) { cellSize.toPx() }
        val widthPx = gridW * cellSizePx
        val heightPx = gridH * cellSizePx

        // 1. Scaled & Panned Content (Background + Grid)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offset.x,
                    translationY = offset.y
                ),
            contentAlignment = Alignment.Center
        ) {
            // Grid Box with direct Pointer Input
            Box(
                modifier = Modifier
                    .size(
                        width = with(density) { widthPx.toDp() },
                        height = with(density) { heightPx.toDp() }
                    )
                    .pointerInput(Unit) {
                        detectDragGestures(
                            onDragStart = { startOffset ->
                                val startX = (startOffset.x / cellSizePx).toInt()
                                val startY = (startOffset.y / cellSizePx).toInt()
                                if (startX in 0 until gridW && startY in 0 until gridH) {
                                    dragStartCell = startX to startY
                                    dragCurrentCell = startX to startY
                                }
                            },
                            onDrag = { change, _ ->
                                val currentX = (change.position.x / cellSizePx).toInt()
                                val currentY = (change.position.y / cellSizePx).toInt()
                                if (currentX in 0 until gridW && currentY in 0 until gridH) {
                                    dragStartCell?.let { start ->
                                        val dx = currentX - start.first
                                        val dy = currentY - start.second
                                        // Snap to straight lines or diagonals
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
                    val halfCell = cellSizePx / 2f
                    val strokeWidth = cellSizePx * 0.8f

                    // Draw found words
                    val foundColor = Color(0xFF22C55E).copy(alpha = 0.4f)
                    state.wordSearchWords.filter { it.id in state.wordSearchFoundWordIds }.forEach { word ->
                        val startOffset = Offset(word.startX * cellSizePx + halfCell, word.startY * cellSizePx + halfCell)
                        val endOffset = Offset(word.endX * cellSizePx + halfCell, word.endY * cellSizePx + halfCell)
                        drawLine(
                            color = foundColor,
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }

                    // Draw current pointer drag line
                    if (dragStartCell != null && dragCurrentCell != null) {
                        val startOffset = Offset(dragStartCell!!.first * cellSizePx + halfCell, dragStartCell!!.second * cellSizePx + halfCell)
                        val endOffset = Offset(dragCurrentCell!!.first * cellSizePx + halfCell, dragCurrentCell!!.second * cellSizePx + halfCell)
                        drawLine(
                            color = primaryColor.copy(alpha = 0.4f),
                            start = startOffset,
                            end = endOffset,
                            strokeWidth = strokeWidth,
                            cap = androidx.compose.ui.graphics.StrokeCap.Round
                        )
                    }

                    // Draw keyboard selection line
                    if (keyboardSelectionStart != null && keyboardCursor != null) {
                        val startOffset = Offset(keyboardSelectionStart.first * cellSizePx + halfCell, keyboardSelectionStart.second * cellSizePx + halfCell)
                        val endOffset = Offset(keyboardCursor.first * cellSizePx + halfCell, keyboardCursor.second * cellSizePx + halfCell)
                        drawLine(
                            color = primaryColor.copy(alpha = 0.6f),
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
                        val isCursor = keyboardCursor == (x to y)

                        WordSearchCellView(
                            char = char,
                            isCursor = isCursor,
                            modifier = Modifier
                                .size(cellSize)
                                .offset(
                                    x = with(density) { (x * cellSizePx).toDp() },
                                    y = with(density) { (y * cellSizePx).toDp() }
                                )
                        )
                    }
                }
            }
        }

        // 2. Visual Border Overlay (Top, Bottom, Left, Right)
        // We use transformable on these explicit borders so touching the edge handles panning/zooming without selecting words.
        val overlayColor = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.85f)
        val borderMod = Modifier
            .background(overlayColor)
            .transformable(transformableState)

        Box(Modifier.align(Alignment.TopCenter).fillMaxWidth().height(borderWidth).then(borderMod))
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(borderWidth).then(borderMod))
        // Padding prevents the corner alpha colors from overlapping and turning black
        Box(Modifier.align(Alignment.CenterStart).width(borderWidth).fillMaxHeight().padding(vertical = borderWidth).then(borderMod))
        Box(Modifier.align(Alignment.CenterEnd).width(borderWidth).fillMaxHeight().padding(vertical = borderWidth).then(borderMod))
    }
}

@Composable
fun WordSearchCellView(
    char: Char,
    isCursor: Boolean = false,
    modifier: Modifier
) {
    val bgColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isCursor) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "cursorBgAnim"
    )
    val borderModifier = if (isCursor) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier

    Box(
        modifier = modifier
            .background(bgColor)
            .then(borderModifier)
    ) {
        Text(
            text = char.toString(),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = if (isCursor) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.align(Alignment.Center)
        )
    }
}

@Composable
fun WordSearchClueList(
    state: StudyState,
    viewModel: FlashcardViewModel,
    isListFocused: Boolean,
    selectedClueIndex: Int
) {
    val dimensions = LocalStudiareDimensions.current
    val sortedWords =
        remember(state.wordSearchWords) { state.wordSearchWords.sortedBy { it.clue.lowercase() } }

    val borderModifierOuter = if (isListFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier

    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).then(borderModifierOuter)
    ) {
        HorizontalDivider()

        Text(
            text = "Clues to Find",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = dimensions.paddingSmall),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

        LaunchedEffect(selectedClueIndex) {
            if (isListFocused && sortedWords.isNotEmpty()) {
                gridState.animateScrollToItem(selectedClueIndex)
            }
        }

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            state = gridState,
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(dimensions.paddingSmall)
        ) {
            // Using the native items(count) to avoid missing extension function imports
            items(sortedWords.size) { index ->
                val word = sortedWords[index]
                val isCompleted = word.id in state.wordSearchFoundWordIds
                val isSelected = isListFocused && index == selectedClueIndex

                val rowBgColor = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
                val borderModifierInner = if (isSelected) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(dimensions.cornerRadiusMedium)) else Modifier

                Row(
                    modifier = Modifier
                        .padding(dimensions.paddingSmall) // Outer spacing between grid items
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 56.dp)
                        .background(
                            color = rowBgColor,
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                        )
                        .then(borderModifierInner)
                        .padding(dimensions.paddingMedium), // Inner padding inside the box
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = word.clue,
                        style = MaterialTheme.typography.bodyLarge,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    val findInteractionSource =
                        remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    val isFindPressed by findInteractionSource.collectIsPressedAsState()
                    val findScale by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (isFindPressed && !isCompleted) 0.85f else 1f,
                        animationSpec = androidx.compose.animation.core.spring(
                            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                        ),
                        label = "findSquish"
                    )

                    val buttonBgColor = if (isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    } else {
                        MaterialTheme.colorScheme.primaryContainer
                    }
                    val buttonTextColor = if (isCompleted) {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    } else {
                        MaterialTheme.colorScheme.onPrimaryContainer
                    }

                    Box(
                        modifier = Modifier
                            .padding(start = dimensions.spacingSmall)
                            .scale(findScale)
                            .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                            .background(buttonBgColor)
                            .clickable(
                                interactionSource = findInteractionSource,
                                indication = androidx.compose.material3.ripple(),
                                enabled = !isCompleted
                            ) {
                                viewModel.submitWordSearchMatch(
                                    word.startX to word.startY,
                                    word.endX to word.endY
                                )
                            }
                            .defaultMinSize(minHeight = 48.dp)
                            .padding(horizontal = dimensions.paddingMedium, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Find",
                            style = MaterialTheme.typography.labelLarge,
                            color = buttonTextColor,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}