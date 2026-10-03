package net.ericclark.studiare.studymodes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.clickable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import kotlinx.coroutines.launch
import net.ericclark.studiare.FlashcardViewModel
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import net.ericclark.studiare.*
import net.ericclark.studiare.data.CardSide
import net.ericclark.studiare.data.SessionMode
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import kotlin.math.absoluteValue
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FreeformScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val dimensions = LocalStudiareDimensions.current
    val state = viewModel.studyState ?: return
    val cards = state.shuffledCards

    if (cards.isEmpty()) return

    val pagerState = rememberPagerState(
        initialPage = state.currentCardIndex,
        pageCount = { cards.size }
    )

    // Cards whose second side has been revealed (one-side-at-a-time view), keyed by page
    val revealed = remember { mutableStateMapOf<Int, Boolean>() }

    // Save progress transparently as the user swipes
    LaunchedEffect(pagerState.currentPage) {
        if (pagerState.currentPage != state.currentCardIndex) {
            // Adjust to your actual progress update method if it's named differently
            viewModel.updateFreeformIndex(pagerState.currentPage)
        }
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.FREEFORM,
                title = { Text(SessionMode.FREEFORM.asString()) },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                    }
                },
                actions = {
                    TooltipIconButton(description = getText(R.string.complete_session), onClick = {
                        // Mark session as complete, adjust method name based on your VM
                        viewModel.completeFreeformSession()
                        navController.navigate("studyCompletion") {
                            popUpTo("freeformStudy") { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = getText(R.string.complete_session))
                    }
                }
            )
        }
    ) { padding ->
        val focusRequester = remember { FocusRequester() }
        val coroutineScope = rememberCoroutineScope()

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .autoFocusable(focusRequester)
                .onPreviewKeyEvent { event ->
                    val isHandledKey = event.key in listOf(
                        Key.DirectionLeft, Key.DirectionRight,
                        Key.DirectionUp, Key.DirectionDown
                    )

                    if (!isHandledKey) return@onPreviewKeyEvent false

                    if (event.type == KeyEventType.KeyUp) {
                        when (event.key) {
                            Key.DirectionLeft, Key.DirectionUp -> {
                                if (pagerState.currentPage > 0) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                    }
                                }
                            }
                            Key.DirectionRight, Key.DirectionDown -> {
                                if (pagerState.currentPage < cards.size - 1) {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                    }
                                }
                            }
                        }
                    }
                    true // Consume handled keys to prevent unwanted scroll propagation
                }
        ) {
            // Optional Session Progress Indicator
            LinearProgressIndicator(
                progress = { if (cards.isNotEmpty()) (pagerState.currentPage + 1).toFloat() / cards.size else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )

            // Reimplementation of M3 Uncontained Carousel supporting rapid swiping
            val isVertical = state.freeformLayoutVertical

            // A tap reveals the second side when only one side is shown; after that (or straight away when both
            // sides are shown) it moves on, but only with swipe navigation off, since swiping already does that.
            val onCardTap: (Int) -> Unit = { page ->
                if (!state.freeformShowBothSides && revealed[page] != true) {
                    revealed[page] = true
                } else if (!state.freeformSwipeNavigation && page < cards.size - 1) {
                    coroutineScope.launch { pagerState.animateScrollToPage(page + 1) }
                }
            }

            val pageContent: @Composable (page: Int) -> Unit = { page ->
                // M3 Carousel effect calculation (scales adjacent items down slightly)
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absOffset = pageOffset.absoluteValue
                val scale = 1f - (0.15f * absOffset).coerceAtMost(0.15f)
                val alpha = 1f - (0.5f * absOffset).coerceAtMost(0.5f)

                val isFrontFirst = state.quizPromptSide == CardSide.FRONT
                val firstSide = if (isFrontFirst) CardSide.FRONT else CardSide.BACK
                val secondSide = if (isFrontFirst) CardSide.BACK else CardSide.FRONT

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (state.freeformShowBothSides) {
                        QuizCardContent(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            showNavigation = false,
                            showIndex = false,
                            overrideSide = firstSide,
                            overrideCardIndex = page,
                            completelyHideNav = true
                        )
                        QuizCardContent(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f).fillMaxWidth(),
                            showNavigation = false,
                            showIndex = false,
                            overrideSide = secondSide,
                            overrideCardIndex = page,
                            completelyHideNav = true
                        )
                    } else {
                        QuizCardContent(
                            state = state,
                            viewModel = viewModel,
                            modifier = Modifier.weight(1f).fillMaxWidth().clickable { onCardTap(page) },
                            showNavigation = false,
                            showIndex = false,
                            overrideSide = if (revealed[page] == true) secondSide else firstSide,
                            overrideCardIndex = page,
                            completelyHideNav = true
                        )
                    }
                }
            }

            if (isVertical) {
                VerticalPager(
                    state = pagerState,
                    userScrollEnabled = state.freeformSwipeNavigation,
                    contentPadding = PaddingValues(vertical = 48.dp),
                    pageSpacing = 16.dp,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        pagerSnapDistance = PagerSnapDistance.atMost(10)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = dimensions.paddingLarge)
                ) { page ->
                    pageContent(page)
                }
            } else {
                // Reimplementation of M3 Uncontained Carousel supporting rapid swiping
                HorizontalPager(
                    state = pagerState,
                    userScrollEnabled = state.freeformSwipeNavigation,
                    contentPadding = PaddingValues(horizontal = 48.dp),
                    pageSpacing = 16.dp,
                    flingBehavior = PagerDefaults.flingBehavior(
                        state = pagerState,
                        pagerSnapDistance = PagerSnapDistance.atMost(10) // Enables fast multi-card swiping
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(vertical = dimensions.paddingLarge)
                ) { page ->
                    pageContent(page)
                }
            }
            if (!state.freeformSwipeNavigation) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingMedium)
                ) {
                    TooltipIconButton(description = getText(R.string.previous_card), onClick = {
                        if (pagerState.currentPage > 0) coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    }) { Icon(Icons.Default.KeyboardArrowLeft, getText(R.string.previous_card)) }
                    TooltipIconButton(description = getText(R.string.next_card), onClick = {
                        if (pagerState.currentPage < cards.size - 1) coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }) { Icon(Icons.Default.KeyboardArrowRight, getText(R.string.next_card)) }
                }
            }
        }
    }
}

@Composable
fun FreeformSideDisplay(text: String, label: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        val state = rememberRichTextState()
        LaunchedEffect(text) {
            state.setHtml(text)
        }

        RichText(
            state = state,
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}