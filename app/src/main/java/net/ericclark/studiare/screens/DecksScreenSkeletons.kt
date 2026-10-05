package net.ericclark.studiare.screens

import androidx.compose.animation.core.EaseInOut
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.*
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.data.*
import androidx.compose.material3.SplitButtonDefaults

// ---------------------------------------------------------------------------
// Skeleton loader — shown while viewModel.isLoading == true.
// Mirrors the real grid layout (same columns, padding, spacing) so the
// transition into real content is seamless. The pulse is driven by a single
// shared InfiniteTransition so every placeholder beats in unison.
// ---------------------------------------------------------------------------

// Replacement Code
@Composable
fun DeckSkeletonLoader(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    dimensions: StudiareDimensions = LocalStudiareDimensions.current,
    snapshotCounts: List<Int>? = null,
    displaySetsUnderDecks: Boolean = true,
    useFlowLayout: Boolean = false
) {
    if (snapshotCounts == null) return // Wait until we know the snapshot counts to avoid flashing

    val infiniteTransition = rememberInfiniteTransition(label = "skeletonPulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.20f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = EaseInOut),
            repeatMode = RepeatMode.Reverse
        ),
        label = "skeletonAlpha"
    )

    val itemCount = if (snapshotCounts.isNotEmpty()) snapshotCounts.size else 0
    val skeletonFill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha)
    val skeletonFillDim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha * 0.55f)

    // Mirrors the chrome that sits above DeckGridContent so the placeholder cards land
    // exactly where the real ones do.
    Column(modifier = modifier.fillMaxSize()) {
    if (itemCount > 0) {
        Box(
            modifier = Modifier
                .widthIn(max = 480.dp)
                .fillMaxWidth()
                .align(Alignment.CenterHorizontally)
                .padding(horizontal = dimensions.paddingLarge, vertical = 8.dp)
                .height(40.dp)
                .clip(RoundedCornerShape(50))
                .background(skeletonFillDim)
        )
    }

    if (useFlowLayout) {
        DeckSetFlowSkeleton(
            snapshotCounts = snapshotCounts,
            dimensions = dimensions,
            displaySetsUnderDecks = displaySetsUnderDecks,
            pulseAlpha = pulseAlpha,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )
        return@Column
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 320.dp),
        contentPadding = contentPadding,
        verticalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
        userScrollEnabled = false,
        modifier = Modifier.weight(1f).fillMaxWidth()
    ) {
        items(itemCount) { index ->
            val setsCount = if (snapshotCounts.isNotEmpty()) snapshotCounts[index] else 0

            Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                DeckSkeletonItem(
                    pulseAlpha = pulseAlpha,
                    dimensions = dimensions,
                    setsCount = setsCount
                )

                if (displaySetsUnderDecks && setsCount > 0) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = dimensions.paddingSmall)
                    ) {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                            userScrollEnabled = false
                        ) {
                            items(setsCount) {
                                SetSkeletonItem(pulseAlpha = pulseAlpha, dimensions = dimensions)
                            }
                        }

                        if (setsCount > 1) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = dimensions.paddingSmall),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                for (i in 0 until setsCount) {
                                    val isSelected = i == 0
                                    val width = if (isSelected) 24.dp else 8.dp
                                    val color = if (isSelected) {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha)
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha * 0.3f)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp)
                                            .size(width = width, height = 8.dp)
                                            .clip(CircleShape)
                                            .background(color)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

/** Mirrors [FlowArrangement]'s layout so the desktop flow view's loading state lands seamlessly. */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun DeckSetFlowSkeleton(
    snapshotCounts: List<Int>,
    dimensions: StudiareDimensions,
    displaySetsUnderDecks: Boolean,
    pulseAlpha: Float,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.layout.BoxWithConstraints(modifier) {
        val minDeckWidth = 320.dp
        val columns = (maxWidth / minDeckWidth).toInt().coerceAtLeast(1)
        val deckWidth = maxWidth / columns

        androidx.compose.foundation.layout.FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(0.dp),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingLarge)
        ) {
            snapshotCounts.forEachIndexed { deckIndex, setsCount ->
                if (deckIndex > 0) Spacer(Modifier.width(dimensions.spacingLarge))
                Box(modifier = Modifier.width(deckWidth).align(Alignment.Top)) {
                    DeckSkeletonItem(pulseAlpha = pulseAlpha, dimensions = dimensions, setsCount = setsCount)
                }
                if (displaySetsUnderDecks) {
                    repeat(setsCount) { setIndex ->
                        Spacer(Modifier.width(if (setIndex == 0) dimensions.spacingLarge else dimensions.spacingLarge / 2))
                        Box(modifier = Modifier.align(Alignment.Bottom)) {
                            SetSkeletonItem(pulseAlpha = pulseAlpha, dimensions = dimensions)
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DeckSkeletonItem(
    pulseAlpha: Float,
    dimensions: StudiareDimensions,
    setsCount: Int
) {
    val fill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha)
    val fillDim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha * 0.55f)

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Box {
                        Text(
                            text = "Deck Name",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.graphicsLayer { alpha = 0f }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(fill)
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box {
                        SuggestionChip(
                            onClick = {},
                            label = { Text("000 Cards") },
                            colors = SuggestionChipDefaults.suggestionChipColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                            border = null,
                            modifier = Modifier.graphicsLayer { alpha = 0f }
                        )
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(50))
                                .background(fillDim)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        TextButton(
                            onClick = {},
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            modifier = Modifier.graphicsLayer { alpha = 0f },
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Icon(Icons.Default.AccountTree, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.sets_count_simple, setsCount))
                        }
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(RoundedCornerShape(8.dp))
                                .background(fillDim)
                        )
                    }
                }
            }

            Spacer(Modifier.height(dimensions.paddingLarge))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box {
                    IconButton(onClick = {}, modifier = Modifier.graphicsLayer { alpha = 0f }) {
                        Icon(Icons.Default.Edit, null)
                    }
                    Box(modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(fillDim))
                }
                Box {
                    IconButton(onClick = {}, modifier = Modifier.graphicsLayer { alpha = 0f }) {
                        Icon(Icons.Default.Delete, null)
                    }
                    Box(modifier = Modifier
                        .matchParentSize()
                        .clip(CircleShape)
                        .background(fillDim))
                }
                Spacer(Modifier.width(dimensions.spacingSmall))
                Box {
                    StudySplitButton(
                        onStudyMain = {},
                        onStudyOption = {},
                        modifier = Modifier.graphicsLayer { alpha = 0f }
                    )
                    Row(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = dimensions.cornerRadiusButton,
                                        bottomStart = dimensions.cornerRadiusButton,
                                        topEnd = 0.dp,
                                        bottomEnd = 0.dp
                                    )
                                )
                                .background(fill)
                        )
                        Spacer(Modifier.width(SplitButtonDefaults.Spacing))
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .fillMaxHeight()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 0.dp,
                                        bottomStart = 0.dp,
                                        topEnd = dimensions.cornerRadiusButton,
                                        bottomEnd = dimensions.cornerRadiusButton
                                    )
                                )
                                .background(fill)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun SetSkeletonItem(
    pulseAlpha: Float,
    dimensions: StudiareDimensions
) {
    val fill = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha)
    val fillDim = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = pulseAlpha * 0.55f)

    Card(
        modifier = Modifier
            .width(190.dp)
            .height(160.dp),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(dimensions.paddingMedium)
        ) {
            Column {
                Box {
                    Text(
                        "Set Name",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.graphicsLayer { alpha = 0f }
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(fill)
                    )
                }
                Spacer(Modifier.height(4.dp))
                Box {
                    Text(
                        "0 cards",
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.graphicsLayer { alpha = 0f })
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clip(RoundedCornerShape(4.dp))
                            .background(fillDim)
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Box {
                    StudySplitButton(
                        onStudyMain = {},
                        onStudyOption = {},
                        modifier = Modifier.graphicsLayer { alpha = 0f }
                    )
                    Row(modifier = Modifier.matchParentSize()) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = dimensions.cornerRadiusButton,
                                        bottomStart = dimensions.cornerRadiusButton,
                                        topEnd = 0.dp,
                                        bottomEnd = 0.dp
                                    )
                                )
                                .background(fill)
                        )
                        Spacer(Modifier.width(SplitButtonDefaults.Spacing))
                        Box(
                            modifier = Modifier
                                .width(48.dp)
                                .fillMaxHeight()
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 0.dp,
                                        bottomStart = 0.dp,
                                        topEnd = dimensions.cornerRadiusButton,
                                        bottomEnd = dimensions.cornerRadiusButton
                                    )
                                )
                                .background(fill)
                        )
                    }
                }
            }
        }
    }
}
