package net.ericclark.studiare

import androidx.compose.foundation.combinedClickable
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.data.*
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontStyle
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.TagDefinition
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import coil.compose.AsyncImage
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichText
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange

// Helper to parse hex color safely
fun parseHexColor(hex: String): Color {
    return try {
        Color(android.graphics.Color.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray // Fallback color
    }
}

/**
 * A highly reusable, expressive Flashcard component that supports 3D flipping,
 * navigation, and tag display.
 *
 * @param frontText The text to display on the front.
 * @param backText The text to display on the back.
 * @param isFlipped Whether the card is currently showing the back.
 * @param onFlip Callback triggered when the card is tapped.
 * @param modifier Modifier for the card container.
 * @param frontNotes Optional notes for the front side.
 * @param backNotes Optional notes for the back side.
 * @param showNavigation Whether to show the Next/Previous arrow buttons.
 * @param onNext Callback for the Next button.
 * @param onPrevious Callback for the Previous button.
 * @param tags Optional list of tags to display at the bottom of the card.
 * @param containerColorFront Background color for the front.
 * @param contentColorFront Text color for the front.
 * @param containerColorBack Background color for the back.
 * @param contentColorBack Text color for the back.
 */
/**
 * Swipe gestures for a study card: a horizontal swipe goes back or forward and a vertical swipe flips the card.
 * A direction is only taken when the matching button is enabled ([canPrevious], [canNext]) or flipping is allowed
 * ([canFlip]). A drag on any other axis is left alone, so a pager or scrollable underneath still gets it.
 */
fun Modifier.cardSwipeGestures(
    canPrevious: Boolean,
    canNext: Boolean,
    canFlip: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onFlip: () -> Unit
): Modifier = if (!canPrevious && !canNext && !canFlip) this else pointerInput(canPrevious, canNext, canFlip, onPrevious, onNext, onFlip) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val slop = viewConfiguration.touchSlop
        val threshold = 48.dp.toPx()
        var total = Offset.Zero
        var horizontal: Boolean? = null // Locked once the drag clearly leans one way
        do {
            val event = awaitPointerEvent()
            val change = event.changes.firstOrNull { it.id == down.id } ?: break
            total += change.positionChange()
            if (horizontal == null && total.getDistance() > slop) {
                horizontal = abs(total.x) > abs(total.y)
            }
            val handled = when (horizontal) {
                true -> canPrevious || canNext
                false -> canFlip
                null -> false
            }
            if (handled) change.consume()
        } while (event.changes.any { it.pressed })

        when (horizontal) {
            true -> when {
                total.x <= -threshold && canNext -> onNext()
                total.x >= threshold && canPrevious -> onPrevious()
            }
            false -> if (abs(total.y) >= threshold && canFlip) onFlip()
            null -> Unit
        }
    }
}

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun CommonFlashcard(
    frontText: String,
    isFrontRichText: Boolean = false,
    backText: String,
    isBackRichText: Boolean = false,
    isFlipped: Boolean,
    onFlip: () -> Unit,
    modifier: Modifier = Modifier,
    doubleTapToFlip: Boolean = false,
    frontNotes: List<NoteField> = emptyList(),
    backNotes: List<NoteField> = emptyList(),
    showBackNavigation: Boolean = false,
    showFrontNavigation: Boolean = false,
    showIndex: Boolean = true,
    onNext: () -> Unit = {},
    onPrevious: () -> Unit = {},
    tags: List<TagDefinition> = emptyList(),
    containerColorFront: Color = MaterialTheme.colorScheme.primaryContainer,
    contentColorFront: Color = MaterialTheme.colorScheme.onPrimaryContainer,
    containerColorBack: Color = MaterialTheme.colorScheme.secondaryContainer,
    contentColorBack: Color = MaterialTheme.colorScheme.onSecondaryContainer,
    cardIndex: Int,
    totalCards: Int,
    sessionId: String = "", // NEW: Pass the sessionId down to link the animation!
    completelyHideNavigation: Boolean = false,
    swipeFlips: Boolean = true // Vertical swipe flips the card; quiz prompts turn this off when a tap doesn't flip
) {
    val dimensions = LocalStudiareDimensions.current
    val context = LocalContext.current

    val sharedTransitionScope = LocalSharedTransitionScope.current
    val animatedVisibilityScope = LocalNavAnimatedVisibilityScope.current

    // Generate the shared bounds modifier based on the session ID
    val sharedModifier = if (sharedTransitionScope != null && animatedVisibilityScope != null && sessionId.isNotEmpty()) {
        with(sharedTransitionScope) {
            Modifier.sharedBounds(
                sharedContentState = rememberSharedContentState(key = "session_card_$sessionId"),
                animatedVisibilityScope = animatedVisibilityScope,
                resizeMode = SharedTransitionScope.ResizeMode.scaleToBounds()
            )
        }
    } else Modifier

    // Independent axes of rotation
    val rotationX = remember { androidx.compose.animation.core.Animatable(if (isFlipped) 180f else 0f) }
    val rotationY = remember { androidx.compose.animation.core.Animatable(0f) }
    // Flip animations run on the card's own scope, not the LaunchedEffect below: that effect restarts whenever
    // the card's content changes, and used to cancel a flip partway through, leaving the card stuck mid-turn.
    val flipScope = rememberCoroutineScope()
    val animateFlip = net.ericclark.studiare.ui.theme.LocalCardFlipAnimated.current

    // State holding what is CURRENTLY being rendered so we can swap it mid-flip
    var renderFrontText by remember { mutableStateOf(frontText) }
    var renderIsFrontRichText by remember { mutableStateOf(isFrontRichText) }
    var renderBackText by remember { mutableStateOf(backText) }
    var renderIsBackRichText by remember { mutableStateOf(isBackRichText) }
    var renderFrontNotes by remember { mutableStateOf(frontNotes) }
    var renderBackNotes by remember { mutableStateOf(backNotes) }
    var renderTags by remember { mutableStateOf(tags) }

    var renderIsFlipped by remember { mutableStateOf(isFlipped) }
    var fullScreenNote by remember { mutableStateOf<NoteField?>(null) }

    var prevIndex by remember { mutableIntStateOf(cardIndex) }
    var prevIsFlipped by remember { mutableStateOf(isFlipped) }

    LaunchedEffect(cardIndex, isFlipped, frontText, backText, frontNotes, backNotes, tags) {
        if (cardIndex == prevIndex && isFlipped == prevIsFlipped) {
            renderFrontText = frontText
            renderBackText = backText
            renderFrontNotes = frontNotes
            renderBackNotes = backNotes
            renderTags = tags
            return@LaunchedEffect
        }

        if (cardIndex != prevIndex) {
            // Horizontal flip for Next/Prev card
            val dir = if (cardIndex > prevIndex) 180f else -180f

            if (animateFlip) flipScope.launch {
                rotationY.animateTo(
                    targetValue = rotationY.targetValue + dir,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    )
                )
            } else rotationY.snapTo(rotationY.targetValue + dir)

            // Wait for halfway point of the flip to swap the text
            if (animateFlip) kotlinx.coroutines.delay(150)

            renderFrontText = frontText
            renderIsFrontRichText = isFrontRichText
            renderBackText = backText
            renderIsBackRichText = isBackRichText
            renderFrontNotes = frontNotes
            renderBackNotes = backNotes
            renderTags = tags
            renderIsFlipped = isFlipped

        } else if (isFlipped != prevIsFlipped) {
            // Vertical flip for turning card
            val dir = if (isFlipped) 180f else -180f

            // Update text immediately (the natural flip hides it)
            renderFrontText = frontText
            renderIsFrontRichText = isFrontRichText
            renderBackText = backText
            renderIsBackRichText = isBackRichText
            renderFrontNotes = frontNotes
            renderBackNotes = backNotes
            renderIsFlipped = isFlipped

            if (animateFlip) flipScope.launch {
                rotationX.animateTo(
                    targetValue = rotationX.targetValue + dir,
                    animationSpec = androidx.compose.animation.core.spring(
                        dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
                        stiffness = androidx.compose.animation.core.Spring.StiffnessLow
                    )
                )
            } else rotationX.snapTo(rotationX.targetValue + dir)
        }

        prevIndex = cardIndex
        prevIsFlipped = isFlipped
    }

    // Determine current visual state based on absolute accumulated rotations
    val currentRotY = Math.abs(rotationY.value)
    val currentRotX = Math.abs(rotationX.value)

    val yFlips = ((currentRotY + 90f) / 180f).toInt()
    val xFlips = ((currentRotX + 90f) / 180f).toInt()

    val isYFlipped = yFlips % 2 != 0
    val isXFlipped = xFlips % 2 != 0

    // The back is logical if it has been flipped an ODD number of times total
    val isBackVisible = renderIsFlipped

    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isBackVisible) containerColorBack else containerColorFront,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "cardBgColor"
    )

    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isBackVisible) contentColorBack else contentColorFront,
        animationSpec = androidx.compose.animation.core.tween(150),
        label = "cardTextColor"
    )

    val navButtonContainerColor by androidx.compose.animation.animateColorAsState(
        targetValue = if (isBackVisible) containerColorFront else containerColorBack,
        label = "navBgColor"
    )

    Box(
        modifier = modifier
            .then(sharedModifier)
            .clip(RoundedCornerShape(dimensions.cornerRadiusLarge))
            .graphicsLayer {
                this.rotationY = rotationY.value
                this.rotationX = rotationX.value
                cameraDistance = 12f * density
            }
            .background(containerColor)
            .combinedClickable(
                onClick = { if (!doubleTapToFlip) onFlip() },
                onDoubleClick = if (doubleTapToFlip) onFlip else null
            )
            .cardSwipeGestures(
                canPrevious = showBackNavigation && !completelyHideNavigation,
                canNext = showFrontNavigation && !completelyHideNavigation,
                canFlip = swipeFlips,
                onPrevious = onPrevious,
                onNext = onNext,
                onFlip = onFlip
            ),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(dimensions.paddingLarge)
                .graphicsLayer {
                    // Counteract rotations to keep text right-side up and un-mirrored
                    if (isYFlipped) this.rotationY = 180f
                    if (isXFlipped) this.rotationX = 180f
                }
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.Center)
                    .padding(bottom = if (renderTags.isNotEmpty()) 32.dp else 0.dp)
            ) {
                val currentText = if (isBackVisible) renderBackText else renderFrontText
                val isCurrentRichText = if (isBackVisible) renderIsBackRichText else renderIsFrontRichText

                if (isCurrentRichText) {
                    val state = rememberRichTextState()
                    LaunchedEffect(currentText) { state.setHtml(currentText) }

                    RichText(
                        state = state,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = contentColor
                    )
                } else {
                    Text(
                        text = currentText,
                        style = MaterialTheme.typography.headlineMedium,
                        textAlign = TextAlign.Center,
                        color = contentColor
                    )
                }

                val currentNotes = if (isBackVisible) renderBackNotes else renderFrontNotes
                if (currentNotes.isNotEmpty()) {
                    Spacer(Modifier.height(dimensions.spacingSmall))

                    val textNotes = currentNotes.filter { it.type == MediaType.PLAIN_TEXT || it.type == MediaType.RICH_TEXT || it.type == MediaType.HTML || it.type == MediaType.WEB_LINK }
                    val mediaNotes = currentNotes.filter {
                        (it.type == MediaType.IMAGE || it.type == MediaType.VIDEO || it.type == MediaType.AUDIO) &&
                                it.content.isNotBlank() &&
                                it.content.startsWith(context.filesDir.absolutePath)
                    }

                    textNotes.forEach { note ->
                        when (note.type) {
                            MediaType.PLAIN_TEXT -> {
                                Text(
                                    text = "${note.name}\n${note.content}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontStyle = FontStyle.Italic,
                                    textAlign = TextAlign.Center,
                                    color = contentColor.copy(alpha = 0.8f)
                                )
                            }
                            MediaType.RICH_TEXT, MediaType.HTML -> {
                                val state = rememberRichTextState()
                                LaunchedEffect(note.content) { state.setHtml(note.content) }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("${note.name}\n", style = MaterialTheme.typography.labelSmall, color = contentColor.copy(alpha = 0.6f))
                                    RichText(state = state, style = MaterialTheme.typography.bodyLarge, color = contentColor.copy(alpha = 0.8f))
                                }
                            }
                            MediaType.WEB_LINK -> {
                                Text(
                                    text = "${note.name}\n${note.content}",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline
                                )
                            }
                            else -> {}
                        }
                        Spacer(Modifier.height(4.dp))
                    }

                    if (mediaNotes.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.Center,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                        ) {
                            mediaNotes.forEach { note ->
                                MediaThumbnail(note = note, onClick = { fullScreenNote = note }, contentColor = contentColor)
                            }
                        }
                    }
                }
            }

            if (renderTags.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(
                            start = if (showBackNavigation) 64.dp else 0.dp,
                            end = if (showFrontNavigation) 64.dp else 0.dp
                        )
                        .horizontalScroll(rememberScrollState())
                ) {
                    renderTags.forEach { tag ->
                        val chipColor = parseHexColor(tag.color)
                        Surface(
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            color = chipColor,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = tag.name,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isYFlipped) this.rotationY = 180f
                    if (isXFlipped) this.rotationX = 180f
                }
        ) {
            if (totalCards > 0 && showIndex) {
                SuggestionChip(
                    onClick = { },
                    label = { Text(stringResource(R.string.card_index_of_total, cardIndex + 1, totalCards)) },
                    // Separate the padding directions to override the invisible touch target boundary
                    modifier = Modifier.align(Alignment.TopStart).padding(start = dimensions.paddingSmall, top = 0.dp),
                    colors = SuggestionChipDefaults.suggestionChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                    ),
                    border = null
                )
            }

            if (!completelyHideNavigation)
            {
                Box(modifier = Modifier.align(Alignment.BottomStart).padding(dimensions.paddingSmall)) {
                    StudyCardNavButton(
                        onClick = onPrevious,
                        icon = { Icon(Icons.Default.KeyboardArrowLeft, getText(R.string.previous)) },
                        containerColor = navButtonContainerColor,
                        enabled = showBackNavigation
                    )
                }

                Box(modifier = Modifier.align(Alignment.BottomEnd).padding(dimensions.paddingSmall)) {
                    StudyCardNavButton(
                        onClick = onNext,
                        icon = { Icon(Icons.Default.KeyboardArrowRight, getText(R.string.next)) },
                        containerColor = navButtonContainerColor,
                        enabled = showFrontNavigation
                    )
                }
            }
        }
        if (fullScreenNote != null) {
            FullScreenMediaViewerDialog(
                note = fullScreenNote!!,
                onDismiss = { fullScreenNote = null }
            )
        }
    }
}

/**
 * The content of the card prompt area in Quiz mode.
 * @param state The current study state.
 * @param viewModel The ViewModel providing business logic.
 */
// [Update QuizCardContent for Compact Mode support]
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun QuizCardContent(
    state: StudyState,
    viewModel: FlashcardViewModel,
    // null = default sizing: a roomy card normally, a compact one while the keyboard is open so the
    // answer area stays visible above it.
    modifier: Modifier? = null,
    showNavigation: Boolean = true, // Parameter kept for compatibility, but ignored for nav logic
    showIndex: Boolean = true,
    tags: List<TagDefinition> = emptyList(),
    overrideSide: CardSide? = null,
    overrideCardIndex: Int? = null,
    completelyHideNav: Boolean = false,
    onTap: (() -> Unit)? = null, // Tap callers (Freeform's reveal); the card's own click otherwise swallows taps
    swipeFlips: Boolean = onTap != null
) {
    val dimensions = LocalStudiareDimensions.current
    val imeVisible = WindowInsets.isImeVisible
    // When a caller supplies its own layout modifier (landscape/split-pane callers that must fill
    // a weighted Row/Column), still shrink the card height while the keyboard is open — passing a
    // modifier used to silently disable the keyboard-aware sizing below entirely, leaving the
    // card obscured behind the IME in those layouts.
    val cardModifier = if (modifier != null) {
        modifier.animateContentSize().let { if (imeVisible) it.height(150.dp) else it }
    } else {
        Modifier
            .animateContentSize()
            .fillMaxWidth()
            .let { if (imeVisible) it.height(150.dp) else it.aspectRatio(1.6f) }
    }
    val currentIndex = overrideCardIndex ?: state.currentCardIndex
    val card = state.shuffledCards[currentIndex]
    val effectiveSide = overrideSide ?: state.quizPromptSide
    val promptText = if (effectiveSide == CardSide.FRONT) card.front else card.back
    val promptRichText = if (effectiveSide == CardSide.FRONT) card.frontRichText else card.backRichText
    val promptNotes = if (effectiveSide == CardSide.FRONT) card.frontNotes else card.backNotes

    CommonFlashcard(
        frontText = promptText,
        isFrontRichText = promptRichText?.isNotBlank() == true,
        backText = "", // Not used in Quiz mode usually
        frontNotes = promptNotes,
        isFlipped = false, // Always show front
        onFlip = { onTap?.invoke() },

        // Respect showNavigation flag while dynamically enabling/disabling them using Quiz-specific rules
        showBackNavigation = showNavigation && currentIndex > 0,
        showFrontNavigation = showNavigation && (currentIndex < state.furthestCardIndex || (state.correctAnswerFound && currentIndex < state.shuffledCards.size - 1)),

        showIndex = showIndex,
        onPrevious = { viewModel.previousCard() },
        onNext = { viewModel.nextCard() },
        modifier = cardModifier,
        tags = tags,
        // Override colors to match Quiz styling (e.g., secondary container for Back prompts)
        containerColorFront = if (effectiveSide == CardSide.BACK) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        contentColorFront = if (effectiveSide == CardSide.BACK) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
        cardIndex = currentIndex,
        totalCards = state.shuffledCards.size,
        sessionId = state.sessionId,
        completelyHideNavigation = completelyHideNav,
        swipeFlips = swipeFlips
    )
}

@Composable
fun MediaThumbnail(note: NoteField, onClick: () -> Unit, contentColor: Color) {
    val dimensions = LocalStudiareDimensions.current
    Surface(
        modifier = Modifier.size(64.dp).clickable(onClick = onClick),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        color = contentColor.copy(alpha = 0.1f),
        border = androidx.compose.foundation.BorderStroke(1.dp, contentColor.copy(alpha = 0.2f))
    ) {
        when (note.type) {
            MediaType.IMAGE -> {
                AsyncImage(
                    model = note.content,
                    contentDescription = note.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            MediaType.VIDEO, MediaType.AUDIO -> {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.PlayArrow, contentDescription = note.name, tint = contentColor)
                        Text(if (note.type == MediaType.VIDEO) "Video" else "Audio", style = MaterialTheme.typography.labelSmall, color = contentColor)
                    }
                }
            }
            else -> {}
        }
    }
}

@Composable
fun BreadcrumbsBar(
    currentDeck: Deck,
    allDecks: List<Deck>,
    onNavigateHome: () -> Unit,
    onNavigateToDeck: (String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val path = remember(currentDeck, allDecks) {
        val list = mutableListOf<Deck>()
        var current: Deck? = currentDeck
        while (current != null) {
            list.add(0, current)
            current = allDecks.find { it.id == current!!.parentDeckId }
        }
        list
    }

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = dimensions.paddingMedium, vertical = 4.dp)
        ) {
            // Home Icon
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                    .clickable { onNavigateHome() }
                    .padding(horizontal = dimensions.paddingSmall, vertical = 4.dp)
            ) {
                Icon(
                    Icons.Default.Home,
                    contentDescription = getText(R.string.home),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            path.forEachIndexed { index, deck ->
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val isLast = index == path.lastIndex
                val textColor = if (isLast) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                val fontWeight = if (isLast) FontWeight.Bold else FontWeight.Normal

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .clickable(enabled = !isLast) { onNavigateToDeck(deck.id) }
                        .padding(horizontal = dimensions.paddingSmall, vertical = 4.dp)
                ) {
                    Text(
                        text = deck.name,
                        style = MaterialTheme.typography.labelLarge,
                        color = textColor,
                        fontWeight = fontWeight,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/**
 * The vertical slide+fade+resize used across every study mode for its "quiz button" state swap
 * (Flip↔Next Card, Get Answer↔Next Card, ...) — built from [MaterialTheme.motionScheme] instead
 * of the ad hoc `spring()` literals each screen used to carry its own copy of, so this one
 * motion stays in sync with the rest of the app's M3 Expressive spec (and with reduced motion).
 */
@Composable
fun <T> quizButtonTransitionSpec(): androidx.compose.animation.AnimatedContentTransitionScope<T>.() -> androidx.compose.animation.ContentTransform {
    val motionScheme = MaterialTheme.motionScheme
    val spatialOffset = motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntOffset>()
    val spatialSize = motionScheme.defaultSpatialSpec<androidx.compose.ui.unit.IntSize>()
    val effects = motionScheme.defaultEffectsSpec<Float>()
    return {
        (androidx.compose.animation.slideInVertically(animationSpec = spatialOffset, initialOffsetY = { it }) +
            androidx.compose.animation.fadeIn(animationSpec = effects) +
            androidx.compose.animation.expandVertically(animationSpec = spatialSize)) togetherWith
            (androidx.compose.animation.slideOutVertically(animationSpec = spatialOffset, targetOffsetY = { it }) +
                androidx.compose.animation.fadeOut(animationSpec = effects) +
                androidx.compose.animation.shrinkVertically(animationSpec = spatialSize))
    }
}
