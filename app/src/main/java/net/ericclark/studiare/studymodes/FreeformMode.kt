package net.ericclark.studiare.studymodes

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.*
import net.ericclark.studiare.data.CardSide
import net.ericclark.studiare.data.SessionMode
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import kotlin.math.absoluteValue
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml

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
                title = { Text(SessionMode.FREEFORM.asString()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        // Mark session as complete, adjust method name based on your VM
                        viewModel.completeFreeformSession()
                        navController.navigate("studyCompletion") {
                            popUpTo("freeformStudy") { inclusive = true }
                        }
                    }) {
                        Icon(Icons.Default.Check, contentDescription = "Complete Session")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Optional Session Progress Indicator
            LinearProgressIndicator(
                progress = { if (cards.isNotEmpty()) (pagerState.currentPage + 1).toFloat() / cards.size else 0f },
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primary
            )

            // Reimplementation of M3 Uncontained Carousel supporting rapid swiping
            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 48.dp),
                pageSpacing = 16.dp,
                flingBehavior = PagerDefaults.flingBehavior(
                    state = pagerState,
                    pagerSnapDistance = PagerSnapDistance.atMost(10) // Enables fast multi-card swiping
                ),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = dimensions.paddingLarge)
            ) { page ->
                val card = cards[page]

                // M3 Carousel effect calculation (scales adjacent items down slightly)
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                val absOffset = pageOffset.absoluteValue
                val scale = 1f - (0.15f * absOffset).coerceAtMost(0.15f)
                val alpha = 1f - (0.5f * absOffset).coerceAtMost(0.5f)

                Card(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            this.alpha = alpha
                        },
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    elevation = CardDefaults.cardElevation(defaultElevation = if (absOffset < 0.5f) 8.dp else 2.dp)
                ) {
                    val isVertical = state.freeformLayoutVertical

                    // Respect the Prompt Side setting to dictate which side appears first
                    val isFrontFirst = state.quizPromptSide == CardSide.FRONT

                    val firstSide = if (isFrontFirst) card.front else card.back
                    val secondSide = if (isFrontFirst) card.back else card.front

                    val firstLabel = if (isFrontFirst) getText(R.string.front) else getText(R.string.back)
                    val secondLabel = if (isFrontFirst) getText(R.string.back) else getText(R.string.front)

                    if (isVertical) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(dimensions.paddingMedium)
                        ) {
                            FreeformSideDisplay(
                                text = firstSide,
                                label = firstLabel,
                                modifier = Modifier.weight(1f)
                            )
                            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.paddingSmall))
                            FreeformSideDisplay(
                                text = secondSide,
                                label = secondLabel,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(dimensions.paddingMedium)
                        ) {
                            FreeformSideDisplay(
                                text = firstSide,
                                label = firstLabel,
                                modifier = Modifier.weight(1f)
                            )
                            VerticalDivider(modifier = Modifier.padding(horizontal = dimensions.paddingSmall))
                            FreeformSideDisplay(
                                text = secondSide,
                                label = secondLabel,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
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
        Text(
            text = AnnotatedString.fromHtml(text),
            style = MaterialTheme.typography.headlineMedium,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}