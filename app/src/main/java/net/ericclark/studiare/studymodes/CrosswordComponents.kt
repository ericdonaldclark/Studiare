package net.ericclark.studiare.studymodes

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material3.Tab
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.ui.input.key.key
import androidx.compose.material3.Button
import kotlin.collections.component1
import kotlin.collections.component2
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.material3.SecondaryTabRow
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.data.StudyState
import androidx.compose.ui.layout.onSizeChanged

@Composable
fun CrosswordGridArea(state: StudyState, viewModel: FlashcardViewModel, resetViewTrigger: Int = 0) {
    // Zoom state
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var layoutSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }

    LaunchedEffect(resetViewTrigger) {
        if (resetViewTrigger > 0) {
            scale = 1f
            offset = Offset.Zero
        }
    }

    // Pan so the selected word sits at the center of the visible grid area.
    val centerDensity = LocalDensity.current
    LaunchedEffect(state.crosswordSelectedWordId, layoutSize) {
        val word = state.crosswordWords.find { it.id == state.crosswordSelectedWordId } ?: return@LaunchedEffect
        if (layoutSize == androidx.compose.ui.unit.IntSize.Zero) return@LaunchedEffect
        val cellPx = with(centerDensity) { 40.dp.toPx() }
        val len = word.word.length
        val wordCx = if (word.isAcross) word.startX + len / 2f else word.startX + 0.5f
        val wordCy = if (word.isAcross) word.startY + 0.5f else word.startY + len / 2f
        // The grid box is clamped to the viewport, so its origin is centered on the clamped size.
        val originX = (layoutSize.width - minOf(state.crosswordGridWidth * cellPx, layoutSize.width.toFloat())) / 2f
        val originY = (layoutSize.height - minOf(state.crosswordGridHeight * cellPx, layoutSize.height.toFloat())) / 2f
        val target = Offset(
            x = -scale * (originX + wordCx * cellPx - layoutSize.width / 2f),
            y = -scale * (originY + wordCy * cellPx - layoutSize.height / 2f)
        )
        val start = offset
        androidx.compose.animation.core.animate(0f, 1f) { f, _ ->
            offset = Offset(start.x + (target.x - start.x) * f, start.y + (target.y - start.y) * f)
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
    val gridW = state.crosswordGridWidth
    val gridH = state.crosswordGridHeight

    // Helper data class for cell rendering (Internal to this function context)
    data class CellRenderData(
        val char: Char?,
        val number: Int?,
        val isSelected: Boolean,
        val isActiveWord: Boolean,
        val isWordCompleted: Boolean,
        val isWrong: Boolean = false
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .onSizeChanged { layoutSize = it }
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
        val uniqueCells = remember(state.crosswordWords, state.completedWordIds, state.crosswordUserInputs, state.crosswordSelectedCell, state.crosswordSelectedWordId, state.crosswordHighlightWord, state.crosswordFeedbackMode) {
            val map = mutableMapOf<String, CellRenderData>()

            state.crosswordWords.forEach { word ->
                val isWordComplete = word.id in state.completedWordIds
                val isWordActive = word.id == state.crosswordSelectedWordId

                // Word feedback: once every letter of a word is filled and it isn't complete, the whole word is wrong.
                val wordFilled = word.word.indices.all { i ->
                    val x = if (word.isAcross) word.startX + i else word.startX
                    val y = if (word.isAcross) word.startY else word.startY + i
                    state.crosswordUserInputs["$x,$y"] != null
                }
                val wordWrong = state.crosswordFeedbackMode == "WORD" && wordFilled && !isWordComplete

                for (i in word.word.indices) {
                    val x = if (word.isAcross) word.startX + i else word.startX
                    val y = if (word.isAcross) word.startY else word.startY + i
                    val key = "$x,$y"

                    val existing = map[key]

                    val char = state.crosswordUserInputs[key]
                    val isSelected = state.crosswordSelectedCell == (x to y)
                    // Letter feedback: a typed letter that doesn't match the answer is wrong on its own.
                    val letterWrong = state.crosswordFeedbackMode == "LETTER" && char != null && char.uppercaseChar() != word.word[i].uppercaseChar()
                    val isWrong = letterWrong || wordWrong

                    // Logic: If ANY word at this cell is complete, the cell is complete (Green)
                    val mergedComplete = (existing?.isWordCompleted == true) || isWordComplete
                    // Logic: If ANY word at this cell is active, the cell is active
                    val mergedActive = (existing?.isActiveWord == true) || (isWordActive && state.crosswordHighlightWord)
                    // Logic: Keep number if already present, else add if start of this word
                    val number = existing?.number ?: if (i == 0) word.number else null

                    map[key] = CellRenderData(char, number, isSelected, mergedActive, mergedComplete, isWrong = (existing?.isWrong == true) || isWrong)
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
                    isWrong = data.isWrong,
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
fun CrosswordClueList(
    state: StudyState,
    viewModel: FlashcardViewModel,
    isListFocused: Boolean,
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    acrossWords: List<net.ericclark.studiare.data.CrosswordWord>,
    downWords: List<net.ericclark.studiare.data.CrosswordWord>
) {
    val dimensions = LocalStudiareDimensions.current
    val tabs = listOf(getText(R.string.across), getText(R.string.down))

    val borderModifier = if (isListFocused) Modifier.border(2.dp, MaterialTheme.colorScheme.primary) else Modifier

    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer).then(borderModifier)) {
        HorizontalDivider()
        SecondaryTabRow( // M3 Expressive: Use the dedicated Secondary container
            selectedTabIndex = selectedTab,
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            contentColor = MaterialTheme.colorScheme.primary
        ) {
            tabs.forEachIndexed { index, title ->
                Tab( // Revert back to the standard Tab composable
                    selected = selectedTab == index,
                    onClick = { onTabSelected(index) },
                    text = { Text(title) }
                )
            }
        }

        val listToShow = if (selectedTab == 0) acrossWords else downWords
        val gridState = androidx.compose.foundation.lazy.grid.rememberLazyGridState()

        // Keep the selected clue centered in the list whenever the selection changes.
        LaunchedEffect(state.crosswordSelectedWordId, selectedTab) {
            val index = listToShow.indexOfFirst { it.id == state.crosswordSelectedWordId }
            if (index != -1) {
                if (gridState.layoutInfo.visibleItemsInfo.none { it.index == index }) {
                    gridState.animateScrollToItem(index)
                }
                val info = gridState.layoutInfo
                info.visibleItemsInfo.firstOrNull { it.index == index }?.let { item ->
                    val viewportCenter = (info.viewportStartOffset + info.viewportEndOffset) / 2f
                    val itemCenter = item.offset.y + item.size.height / 2f
                    gridState.animateScrollBy(itemCenter - viewportCenter)
                }
            }
        }

        androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
            state = gridState,
            columns = androidx.compose.foundation.lazy.grid.GridCells.Adaptive(minSize = 300.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(dimensions.paddingSmall)
        ) {
            items(listToShow.size) { index ->
                val word = listToShow[index]
                val isSelected = state.crosswordSelectedWordId == word.id
                val isCompleted = word.id in state.completedWordIds

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
                        .clickable {
                            viewModel.selectCrosswordWord(word.id)
                        }
                        .padding(dimensions.paddingMedium), // Inner padding inside the box
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${word.number}.",
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(32.dp)
                    )
                    Text(
                        text = word.clue,
                        style = MaterialTheme.typography.bodyLarge, // M3 Expressive: Larger list text
                        maxLines = if (state.crosswordCompactClues) 1 else Int.MAX_VALUE,
                        overflow = if (state.crosswordCompactClues) TextOverflow.Ellipsis else TextOverflow.Clip,
                        textDecoration = if (isCompleted) TextDecoration.LineThrough else null,
                        color = if (isCompleted) MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f) else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f)
                    )

                    // PHASE 3: Spatial Entrance for the Hint Button
                    androidx.compose.animation.AnimatedVisibility(
                        visible = isSelected && !isCompleted,
                        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.expandHorizontally(),
                        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.shrinkHorizontally()
                    ) {
                        // PHASE 5: Tactile Squish for Hint Button
                        val hintInteractionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                        val isHintPressed by hintInteractionSource.collectIsPressedAsState()
                        val hintScale by androidx.compose.animation.core.animateFloatAsState(
                            targetValue = if (isHintPressed) 0.85f else 1f,
                            animationSpec = androidx.compose.animation.core.spring(
                                dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
                            ),
                            label = "hintSquish"
                        )

                        Box(
                            modifier = Modifier
                                .padding(start = dimensions.spacingSmall)
                                .scale(hintScale)
                                .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .combinedClickable(
                                    interactionSource = hintInteractionSource,
                                    indication = androidx.compose.material3.ripple(),
                                    onLongClick = { viewModel.provideCrosswordHint(word.id, fillEntireWord = true) },
                                    onClick = { viewModel.provideCrosswordHint(word.id, fillEntireWord = false) }
                                )
                                .padding(horizontal = dimensions.paddingMedium, vertical = 4.dp)
                        ) {
                            Text(
                                text = getText(R.string.hint),
                                style = MaterialTheme.typography.labelLarge, // M3 Expressive: Bold button labels
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
