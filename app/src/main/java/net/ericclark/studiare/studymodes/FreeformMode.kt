package net.ericclark.studiare.studymodes

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import androidx.navigation.NavController
import net.ericclark.studiare.AnimatedHamburgerMenu
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.*
import net.ericclark.studiare.data.CardSide
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

@Composable
fun FreeformScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val state = viewModel.studyState ?: return
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val dimensions = LocalStudiareDimensions.current
    val pagerState = rememberPagerState(pageCount = { state.shuffledCards.size })

    // Sync pager state with ViewModel index for persistence/resume
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != state.currentCardIndex) {
            // We use nextCard/previousCard logic or a new seek function if added to VM
            // For now, we assume the pager is the source of truth for "Freeform"
        }
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text(state.deckWithCards.deck.name) },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.endStudySession()
                        navController.popBackStack()
                    }) {
                        AnimatedHamburgerMenu(
                            viewModel = viewModel,
                            windowWidthSizeClass = windowWidthSizeClass
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.flipStudyMode() }) {
                        Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    }
                }
            )
        }
    ) { padding ->
        val coroutineScope = rememberCoroutineScope()
        val density = androidx.compose.ui.platform.LocalDensity.current
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = Modifier.fillMaxSize())
            {
                HorizontalPager(
                    state = pagerState,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 16.dp,
                    modifier = Modifier.fillMaxWidth().weight(1f)
                ) { page ->
                    val card = state.shuffledCards[page]
                    var isLocalFlipped by remember { mutableStateOf(false) }

                    // Reset flip state when navigating away/back to card
                    LaunchedEffect(pagerState.currentPage) { isLocalFlipped = false }

                    val frontText = if (state.isFlipped) card.back else card.front
                    val backText = if (state.isFlipped) card.front else card.back

                    Box(
                        modifier = Modifier
                            .graphicsLayer {
                                val pageOffset =
                                    ((pagerState.currentPage - page) + pagerState.currentPageOffsetFraction).absoluteValue
                                // MD3 Hero Carousel scaling effect
                                alpha = lerp(
                                    start = 0.5f,
                                    stop = 1f,
                                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                )
                                scaleY = lerp(
                                    start = 0.8f,
                                    stop = 1f,
                                    fraction = 1f - pageOffset.coerceIn(0f, 1f)
                                )
                            }
                    ) {
                        CommonFlashcard(
                            frontText = frontText,
                            frontNotes = if (state.isFlipped) card.backNotes else card.frontNotes,
                            backText = backText,
                            backNotes = if (state.isFlipped) card.frontNotes else card.backNotes,
                            isFlipped = isLocalFlipped,
                            onFlip = { isLocalFlipped = !isLocalFlipped },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.8f),
                            cardIndex = page,
                            totalCards = state.shuffledCards.size,
                            sessionId = state.sessionId,
                            showBackNavigation = false,
                            showFrontNavigation = false,
                            hideNavigation = true
                        )
                    }
                }
                // Custom Horizontal Fast Scroll Slider
                val totalItems = state.shuffledCards.size
                if (totalItems > 1) {
                    var barWidth by remember { mutableStateOf(0f) }
                    var isDragging by remember { mutableStateOf(false) }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .padding(horizontal = dimensions.paddingLarge)
                            .onSizeChanged { barWidth = it.width.toFloat() }
                            .pointerInput(totalItems, barWidth) {
                                detectHorizontalDragGestures(
                                    onDragStart = { offset ->
                                        isDragging = true
                                        if (barWidth > 0) {
                                            val percentage = (offset.x / barWidth).coerceIn(0f, 1f)
                                            val index = (percentage * (totalItems - 1)).toInt()
                                            coroutineScope.launch { pagerState.scrollToPage(index) }
                                        }
                                    },
                                    onDragEnd = { isDragging = false },
                                    onDragCancel = { isDragging = false },
                                    onHorizontalDrag = { change, _ ->
                                        if (barWidth > 0) {
                                            val percentage =
                                                (change.position.x / barWidth).coerceIn(0f, 1f)
                                            val index = (percentage * (totalItems - 1)).toInt()
                                            coroutineScope.launch { pagerState.scrollToPage(index) }
                                        }
                                    }
                                )
                            },
                        contentAlignment = Alignment.CenterStart
                    ) {
                        if (barWidth > 0) {
                            // Calculate thumb size and position based on pager state
                            val thumbWidthPx = (barWidth / totalItems).coerceAtLeast(100f)
                            val scrollOffsetPx =
                                (pagerState.currentPage.toFloat() / totalItems) * barWidth

                            val thumbWidthDp = with(density) { thumbWidthPx.toDp() }
                            val scrollOffsetDp = with(density) { scrollOffsetPx.toDp() }

                            val thumbHeight by androidx.compose.animation.core.animateDpAsState(
                                targetValue = if (isDragging) 12.dp else 6.dp,
                                animationSpec = androidx.compose.animation.core.spring(
                                    dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                                    stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                                ),
                                label = "thumbHeightAnim"
                            )

                            Box(
                                modifier = Modifier
                                    .offset(x = scrollOffsetDp)
                                    .height(thumbHeight)
                                    .width(thumbWidthDp)
                                    .background(
                                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                        shape = androidx.compose.foundation.shape.CircleShape
                                    )
                            )
                        }
                    }
                }
            }
        }
    }
}