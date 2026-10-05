package net.ericclark.studiare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.isAltPressed
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.ui.draw.*
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.derivedStateOf
import androidx.activity.compose.BackHandler

@Composable
fun SetManagerScreen(
    navController: NavController,
    parentDeck: DeckWithCards,
    sets: List<DeckSummary>,
    viewModel: FlashcardViewModel,
    isPane: Boolean = false,
    onChromeChanged: (PaneChrome) -> Unit = {}
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<DeckSummary?>(null) }
    var setToEdit by remember { mutableStateOf<DeckSummary?>(null) }
    var creationAction by remember { mutableStateOf<SetCreationAction?>(null) }

    val allDecksWithCards by viewModel.allDecks.observeAsState(emptyList())

    val allTags by viewModel.tags.collectAsState()
    val parentDeckTags = remember(parentDeck) {
        parentDeck.cards.flatMap { it.tags }.distinct().sorted()
    }

    val spacingMode by viewModel.spacingMode.collectAsState()
    val animationMode by viewModel.animationMode.collectAsState()
    val storedDeckSetsDisplayMode by viewModel.deckSetsDisplayMode.collectAsState()
    val deckSetsDisplayMode = DeckSetsDisplayMode.resolve(storedDeckSetsDisplayMode, windowWidthSizeClass, windowHeightSizeClass)
    val displaySetsUnderDecks = deckSetsDisplayMode != DeckSetsDisplayMode.OFF

    // Determine Dimensions based on ViewModel state
    val dimensions = when (spacingMode) {
        SpacingMode.COMPACT -> CompactDimensions
        SpacingMode.NORMAL -> NormalDimensions
        else -> ComfortableDimensions
    }

    // Provide these dimensions to all child composables
    CompositionLocalProvider(LocalStudiareDimensions provides dimensions) {

        SetCreationDialogHost(
            parentDeck = parentDeck,
            action = creationAction,
            viewModel = viewModel,
            onDismiss = { creationAction = null }
        )

        setToEdit?.let { aSetSummary ->
            val heavySet = allDecksWithCards.find { it.deck.id == aSetSummary.deck.id }
            if (heavySet != null) {
                ManualSetEditorDialog(
                    navController = navController,
                    parentDeck = parentDeck,
                    setForEditing = heavySet,
                    viewModel = viewModel,
                    onDismiss = { setToEdit = null }
                )
            }
        }

        showDeleteDialog?.let { deckToDelete ->
            ConfirmationDialog(
                title = getText(R.string.delete_set_question),
                text = stringResource(R.string.delete_set_confirm, deckToDelete.deck.name),
                onConfirm = {
                    viewModel.deleteDeck(deckToDelete.deck.id)
                    showDeleteDialog = null
                },
                onDismiss = { showDeleteDialog = null }
            )
        }

        val parentId = parentDeck.deck.parentDeckId
        val navigateUp = {
            if (isPane) {
                viewModel.closePane("set:${parentDeck.deck.id}")
            } else {
                if (parentId == null) {
                    navController.navigate("deckList") { popUpTo(0) }
                } else {
                    navController.navigate("setManager/$parentId") {
                        popUpTo("setManager/$parentId") { inclusive = true }
                    }
                }
            }
        }

        BackHandler(enabled = !isPane, onBack = navigateUp)

        val screenTitle = stringResource(R.string.deck_sets_title_format, parentDeck.deck.name)

        if (isPane) {
            // Report chrome to the owning Scaffold instead of drawing our own.
            LaunchedEffect(screenTitle) {
                onChromeChanged(
                    PaneChrome(
                        title = { Text(screenTitle, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        screenId = ShortcutScreen.SETS
                    )
                )
            }
        }

        // ── everything below is the SAME body as before, just assigned to a
        // named lambda so it can be reused from either the pane branch or the
        // standalone Scaffold branch below. ──────────────────────────────────
        val paneContent: @Composable (PaddingValues) -> Unit = { padding ->
            val sortedSets = remember(sets) {
                val setComparator = compareBy<DeckSummary, Int?>(nullsLast()) {
                    it.deck.name.removePrefix("Set ").toIntOrNull()
                }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.deck.name }

                sets.sortedWith(
                    compareByDescending<DeckSummary> { it.deck.isStarred }
                        .then(setComparator)
                )
            }

            val focusRequester = remember { FocusRequester() }
            var fabMenuExpanded by remember { mutableStateOf(false) }
            val openCreateMenuKey = resolveShortcutKey(LocalShortcutRemaps.current, "sets.open_create_menu", Key.N)

            Box(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .autoFocusable(focusRequester)
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyUp) {
                            when {
                                event.key == Key.Backspace -> {
                                    navigateUp()
                                    return@onPreviewKeyEvent true
                                }

                                event.key == openCreateMenuKey -> {
                                    fabMenuExpanded = !fabMenuExpanded
                                    return@onPreviewKeyEvent true
                                }

                                (event.isCtrlPressed && event.key == Key.F) || event.key == Key.Slash -> {
                                    // Focus search bar when implemented in the future
                                    return@onPreviewKeyEvent true
                                }

                                event.isAltPressed -> {
                                    val num = when (event.key) {
                                        Key.One, Key.NumPad1 -> 0
                                        Key.Two, Key.NumPad2 -> 1
                                        Key.Three, Key.NumPad3 -> 2
                                        Key.Four, Key.NumPad4 -> 3
                                        Key.Five, Key.NumPad5 -> 4
                                        Key.Six, Key.NumPad6 -> 5
                                        Key.Seven, Key.NumPad7 -> 6
                                        Key.Eight, Key.NumPad8 -> 7
                                        Key.Nine, Key.NumPad9 -> 8
                                        else -> -1
                                    }
                                    if (num in sortedSets.indices) {
                                        val deckId = sortedSets[num].deck.id
                                        navController.navigate("studyModeSelection/$deckId")
                                        return@onPreviewKeyEvent true
                                    }
                                }
                            }
                        }
                        false
                    }
            ) {

                AnimatedContent(
                    targetState = sortedSets.isEmpty(),
                    transitionSpec = {
                        (fadeIn(animationSpec = androidx.compose.animation.core.tween(400)) +
                                slideInVertically(
                                    animationSpec = androidx.compose.animation.core.tween(
                                        400
                                    ), initialOffsetY = { it / 4 }))
                            .togetherWith(
                                fadeOut(animationSpec = androidx.compose.animation.core.tween(400)) +
                                        slideOutVertically(
                                            animationSpec = androidx.compose.animation.core.tween(
                                                400
                                            ), targetOffsetY = { it / 4 })
                            )
                    },
                    label = "setsListTransition"
                ) { isEmpty ->
                    if (isEmpty) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = getText(R.string.no_sets_yet),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )

                                Spacer(Modifier.height(32.dp))

                                FilledTonalButton(
                                    onClick = { creationAction = SetCreationAction.CLONE },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.ContentCopy,
                                            contentDescription = null,
                                            modifier = Modifier.align(Alignment.CenterStart)
                                        )
                                        Text(
                                            text = "Clone Entire Deck",
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                FilledTonalButton(
                                    onClick = { creationAction = SetCreationAction.PICK_AND_CHOOSE },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.ChecklistRtl,
                                            contentDescription = null,
                                            modifier = Modifier.align(Alignment.CenterStart)
                                        )
                                        Text(
                                            text = getText(R.string.pick_and_choose),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }

                                Spacer(Modifier.height(16.dp))

                                Button(
                                    onClick = { creationAction = SetCreationAction.FILTER_AND_SORT },
                                    modifier = Modifier.fillMaxWidth().height(56.dp),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                                    contentPadding = PaddingValues(horizontal = 24.dp)
                                ) {
                                    Box(modifier = Modifier.fillMaxSize()) {
                                        Icon(
                                            imageVector = Icons.Default.FilterAlt,
                                            contentDescription = null,
                                            modifier = Modifier.align(Alignment.CenterStart)
                                        )
                                        Text(
                                            text = getText(R.string.filter_and_sort),
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.align(Alignment.Center)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 320.dp),
                            contentPadding = PaddingValues(
                                start = dimensions.paddingLarge,
                                end = dimensions.paddingLarge,
                                top = dimensions.paddingLarge,
                                bottom = dimensions.paddingLarge
                            ),
                            verticalArrangement = Arrangement.spacedBy(dimensions.spacingLarge),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingLarge)
                        ) {
                            itemsIndexed(sortedSets) { index, set ->
                                val subSets =
                                    allDecksWithCards.filter { it.deck.parentDeckId == set.deck.id }
                                val childSetsCount = subSets.size

                                Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                                    DeckListItem(
                                        deck = set,
                                        dimensions = dimensions,
                                        setsCount = childSetsCount,
                                        onStudy = { autoOpen ->
                                            if (isPane) {
                                                viewModel.pushPaneAfter("set:${parentDeck.deck.id}", net.ericclark.studiare.PaneDestination.StudyModeSelection(set.deck.id, autoOpen))
                                            } else {
                                                val route =
                                                    if (autoOpen != null) "studyModeSelection/${set.deck.id}?autoOpen=$autoOpen" else "studyModeSelection/${set.deck.id}"
                                                if (set.totalCards > 0) navController.navigate(route)
                                            }
                                        },
                                        onEdit = { setToEdit = set },
                                        onDelete = { showDeleteDialog = set },
                                        onManageSets = {
                                            if (isPane) {
                                                viewModel.pushPaneAfter("set:${parentDeck.deck.id}", net.ericclark.studiare.PaneDestination.SetManager(set.deck.id))
                                            } else {
                                                navController.navigate("setManager/${set.deck.id}")
                                            }
                                        },
                                        onToggleStar = { viewModel.toggleDeckStar(set.deck) },
                                        showManageSetsButton = true,
                                        tapOpensStudy = false,
                                        index = index
                                    )

                                    AnimatedVisibility(
                                        visible = subSets.isNotEmpty() && displaySetsUnderDecks,
                                        enter = slideInVertically() + fadeIn() + expandVertically(),
                                        exit = slideOutVertically() + fadeOut() + shrinkVertically()
                                    ) {
                                        Column(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = dimensions.paddingSmall)
                                        ) {
                                            val listState = rememberLazyListState()

                                            LazyRow(
                                                state = listState,
                                                horizontalArrangement = Arrangement.spacedBy(
                                                    dimensions.spacingSmall
                                                )
                                            ) {
                                                items(subSets) { subset ->
                                                    SubSetListItem(
                                                        deck = subset,
                                                        dimensions = dimensions,
                                                        onStudy = { autoOpen ->
                                                            if (isPane) {
                                                                viewModel.pushPaneAfter("set:${parentDeck.deck.id}", net.ericclark.studiare.PaneDestination.StudyModeSelection(subset.deck.id, autoOpen))
                                                            } else {
                                                                val route =
                                                                    if (autoOpen != null) "studyModeSelection/${subset.deck.id}?autoOpen=$autoOpen" else "studyModeSelection/${subset.deck.id}"
                                                                if (subset.cards.isNotEmpty()) navController.navigate(
                                                                    route
                                                                )
                                                            }
                                                        },
                                                        onManageSets = {
                                                            if (isPane) {
                                                                viewModel.pushPaneAfter("set:${parentDeck.deck.id}", net.ericclark.studiare.PaneDestination.SetManager(subset.deck.id))
                                                            } else {
                                                                navController.navigate("setManager/${subset.deck.id}")
                                                            }
                                                        }
                                                    )
                                                }
                                            }

                                            if (subSets.size > 1) {
                                                val currentIndex by remember {
                                                    derivedStateOf {
                                                        val layoutInfo = listState.layoutInfo
                                                        val visibleItemsInfo =
                                                            layoutInfo.visibleItemsInfo
                                                        if (visibleItemsInfo.isEmpty()) {
                                                            0
                                                        } else {
                                                            val viewportStart =
                                                                layoutInfo.viewportStartOffset
                                                            val viewportEnd =
                                                                layoutInfo.viewportEndOffset
                                                            val viewportCenter =
                                                                viewportStart + (viewportEnd - viewportStart) / 2
                                                            visibleItemsInfo.minByOrNull {
                                                                kotlin.math.abs((it.offset + it.size / 2) - viewportCenter)
                                                            }?.index ?: 0
                                                        }
                                                    }
                                                }

                                                Row(
                                                    modifier = Modifier.fillMaxWidth()
                                                        .padding(top = dimensions.paddingSmall),
                                                    horizontalArrangement = Arrangement.Center,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    subSets.indices.forEach { index ->
                                                        val isSelected = index == currentIndex
                                                        val width by animateDpAsState(
                                                            targetValue = if (isSelected) 24.dp else 8.dp,
                                                            animationSpec = spring(
                                                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                                                stiffness = Spring.StiffnessLow
                                                            ),
                                                            label = "dotWidth"
                                                        )
                                                        val color by animateColorAsState(
                                                            targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                                                alpha = 0.3f
                                                            ),
                                                            label = "dotColor"
                                                        )

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

                // Scrim overlay to click-away and close the menu
                if (fabMenuExpanded) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.3f))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) { fabMenuExpanded = false }
                    )
                }

                AnimatedVisibility(
                    visible = sortedSets.isNotEmpty(),
                    enter = fadeIn() + androidx.compose.animation.scaleIn(
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            1f,
                            1f
                        )
                    ),
                    exit = fadeOut() + androidx.compose.animation.scaleOut(
                        transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                            1f,
                            1f
                        )
                    ),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(dimensions.paddingMedium)
                ) {
                    // This Single Box properly layers the Menu and the Main FAB so they never overlap.
                    Box(contentAlignment = Alignment.BottomEnd) {
                        androidx.compose.animation.AnimatedVisibility(
                            visible = fabMenuExpanded,
                            enter = fadeIn() + androidx.compose.animation.scaleIn(
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                    1f,
                                    1f
                                )
                            ),
                            exit = fadeOut() + androidx.compose.animation.scaleOut(
                                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(
                                    1f,
                                    1f
                                )
                            ),
                            modifier = Modifier.padding(bottom = 56.dp + dimensions.spacingMedium) // Perfectly clear the main FAB
                        ) {
                            Column(
                                horizontalAlignment = Alignment.End,
                                verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                            ) {
                                // Delete All Sets Option (Only show if there are sets)
                                if (sortedSets.isNotEmpty()) {
                                    androidx.compose.material3.ExtendedFloatingActionButton(
                                        onClick = {
                                            fabMenuExpanded = false
                                            creationAction = SetCreationAction.DELETE_ALL
                                        },
                                        modifier = Modifier.withShortcut(Key.Delete, "Del", id = "sets.delete_all") {
                                            fabMenuExpanded = false
                                            creationAction = SetCreationAction.DELETE_ALL
                                        },
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                                        icon = {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = null
                                            )
                                        },
                                        text = {
                                            Text(
                                                getText(R.string.delete_all_sets),
                                                style = MaterialTheme.typography.labelLarge
                                            )
                                        },
                                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                    )
                                }

                                // Clone Option
                                androidx.compose.material3.ExtendedFloatingActionButton(
                                    onClick = {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.CLONE
                                    },
                                    modifier = Modifier.withShortcut(Key.C, "C", id = "sets.clone") {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.CLONE
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    icon = {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(
                                            "Clone Entire Deck",
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                )

                                // Manual Option
                                androidx.compose.material3.ExtendedFloatingActionButton(
                                    onClick = {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.PICK_AND_CHOOSE
                                    },
                                    modifier = Modifier.withShortcut(Key.M, "M", id = "sets.manual") {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.PICK_AND_CHOOSE
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    icon = {
                                        Icon(
                                            Icons.Default.ChecklistRtl,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(
                                            getText(R.string.pick_and_choose),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                )

                                // Automatic Option
                                androidx.compose.material3.ExtendedFloatingActionButton(
                                    onClick = {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.FILTER_AND_SORT
                                    },
                                    modifier = Modifier.withShortcut(Key.A, "A", id = "sets.automatic") {
                                        fabMenuExpanded = false
                                        creationAction = SetCreationAction.FILTER_AND_SORT
                                    },
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    icon = {
                                        Icon(
                                            Icons.Default.FilterAlt,
                                            contentDescription = null
                                        )
                                    },
                                    text = {
                                        Text(
                                            getText(R.string.filter_and_sort),
                                            style = MaterialTheme.typography.labelLarge
                                        )
                                    },
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                                )
                            }
                        }

                        val mainFabRotation by animateFloatAsState(
                            targetValue = if (fabMenuExpanded) 45f else 0f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "fabRotation"
                        )

                        val addInteractionSource = remember { MutableInteractionSource() }
                        val isAddPressed by addInteractionSource.collectIsPressedAsState()
                        val addScale by animateFloatAsState(
                            targetValue = if (isAddPressed) 0.85f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "addFabSquish"
                        )

                        // FIX: Replaced standard FAB with ExtendedFloatingActionButton to match DecksScreen!
                        androidx.compose.material3.ExtendedFloatingActionButton(
                            onClick = { fabMenuExpanded = !fabMenuExpanded },
                            interactionSource = addInteractionSource,
                            modifier = Modifier.scale(addScale),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // M3 Expressive Pill shape
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            icon = {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = getText(R.string.set_create),
                                    modifier = Modifier.rotate(mainFabRotation)
                                )
                            },
                            text = {
                                Text(
                                    text = getText(R.string.set_create), // Displays "Create Set"
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        )
                    }
                }
            }
        } // <-- THIS is the brace that was missing: closes `val paneContent = { padding -> ... }`

        if (isPane) {
            // Title lives in the shared app bar (via PaneChrome), not in the pane.
            Box(Modifier.fillMaxSize()) { paneContent(PaddingValues(0.dp)) }
        } else {
            Scaffold(
                topBar = {
                    Column {
                        CustomTopAppBar(
                            viewModel = viewModel,
                            screenId = ShortcutScreen.SETS,
                            title = { Text(screenTitle) },
                            navigationIcon = {
                                TooltipIconButton(description = getText(R.string.back), onClick = navigateUp) {
                                    Icon(Icons.Default.ArrowBack, contentDescription = getText(R.string.back))
                                }
                            }
                        )
                        BreadcrumbsBar(
                            currentDeck = parentDeck.deck,
                            allDecks = allDecksWithCards.map { it.deck },
                            onNavigateHome = { navController.navigate("deckList") { popUpTo(0) } },
                            onNavigateToDeck = { deckId ->
                                navController.navigate("setManager/$deckId") {
                                    popUpTo("deckList") { inclusive = false }
                                }
                            }
                        )
                        HorizontalDivider(
                            color = MaterialTheme.colorScheme.outlineVariant.copy(
                                alpha = 0.5f
                            )
                        )
                    }
                }
            ) { padding -> paneContent(padding) }
        }
    }
}

@Composable
fun CreateSetDialog(
    onDismiss: () -> Unit,
    onAutomatic: () -> Unit,
    onManual: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    getText(R.string.set_create),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                val autoInteractionSource = remember { MutableInteractionSource() }
                val isAutoPressed by autoInteractionSource.collectIsPressedAsState()
                val autoScale by animateFloatAsState(
                    targetValue = if (isAutoPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "autoSquish"
                )
                Button(onClick = onAutomatic, interactionSource = autoInteractionSource, modifier = Modifier.fillMaxWidth().scale(autoScale), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                    Text(getText(R.string.automatic))
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                val manualInteractionSource = remember { MutableInteractionSource() }
                val isManualPressed by manualInteractionSource.collectIsPressedAsState()
                val manualScale by animateFloatAsState(
                    targetValue = if (isManualPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "manualSquish"
                )
                Button(onClick = onManual, interactionSource = manualInteractionSource, modifier = Modifier.fillMaxWidth().scale(manualScale), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                    Text(getText(R.string.manual))
                }
            }
        }
    }
}

@Composable
fun CardSelectItem(
    card: Card,
    index: Int,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "itemSquish"
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .background(if (index % 2 != 0) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onToggle
            )
            .padding(horizontal = dimensions.paddingSmall, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(card.front, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
        IconButton(
            onClick = onToggle,
            colors = IconButtonDefaults.iconButtonColors(contentColor = MaterialTheme.colorScheme.primary)
        ) {
            icon()
        }
    }
}
