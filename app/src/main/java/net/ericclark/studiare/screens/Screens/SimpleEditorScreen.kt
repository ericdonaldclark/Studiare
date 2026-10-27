package net.ericclark.studiare.screens.Screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingToolbarDefaults
import androidx.compose.material3.HorizontalFloatingToolbar
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import kotlinx.coroutines.launch
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.runtime.snapshotFlow
import net.ericclark.studiare.LocalNavAnimatedVisibilityScope
import net.ericclark.studiare.TooltipIconButton
import kotlinx.coroutines.flow.first
import net.ericclark.studiare.components.CardMetadataContent
import net.ericclark.studiare.components.FlippableCardShell
import net.ericclark.studiare.components.FloatingFilterOverlay
import net.ericclark.studiare.components.TagChip
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.components.jumpThenAnimateTo
import net.ericclark.studiare.data.Card as CardEntity
import net.ericclark.studiare.data.CardDataForSave
import net.ericclark.studiare.data.CardEditorState
import net.ericclark.studiare.data.CardFlag
import net.ericclark.studiare.data.DeckSortMode
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.DifficultySetting
import net.ericclark.studiare.data.NoteField
import net.ericclark.studiare.data.NormalizationType
import net.ericclark.studiare.data.TagDefinition
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.data.toCardDataForSave
import net.ericclark.studiare.data.toEditorState
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import java.util.UUID
import net.ericclark.studiare.screens.Dialogs.AdvancedDeckEditorDialog
import net.ericclark.studiare.screens.Dialogs.CardEditDialog
import net.ericclark.studiare.screens.Dialogs.CardEditDialogMode
import net.ericclark.studiare.screens.UI_Components.DeckInfoDialog
import net.ericclark.studiare.screens.UI_Components.DeckSettingsDialog
import net.ericclark.studiare.screens.UI_Components.FlowRow

/**
 * The default destination when editing an existing deck: a condensed grid of read-only card
 * tiles. Tapping a tile opens [CardEditDialog] for a focused single-card edit; adding a card
 * opens the same dialog blank. Every dialog action (Save/Add/Add More/Delete) persists
 * immediately via [FlashcardViewModel.saveSingleCard]/[FlashcardViewModel.deleteSingleCard] —
 * there's no top-level Save here, unlike the full "Bulk Editor" ([DeckEditorScreen]) this
 * screen's Bulk Editor button leads to for anything beyond quick per-card edits.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun SimpleEditorScreen(
    navController: NavController,
    deckWithCards: DeckWithCards?,
    viewModel: FlashcardViewModel
) {
    // Confirmed via timing logs: this screen's first composition costs ~500ms (body-start to
    // committed), which is what was swallowing the NavHost's enter transition whole — any
    // transition duration shorter than that never gets a chance to paint an intermediate frame,
    // since by the time the first real frame is drawn, the animation's own clock already reports
    // past its end. Same root cause already diagnosed for Settings' "Customization" category
    // (see its own comment: "the heavy first-time class-load/JIT of that category"), not an
    // algorithmic cost in this screen's own code — grep/read of the grid and its tiles found
    // nothing expensive, and the cost doesn't scale with card count (198 cards here; the grid is
    // properly lazy, only ~6-12 tiles actually compose up front). Same fix as Settings: let the
    // transition play over empty chrome first, defer the heavy content until it's visually
    // settled instead of composing it mid-transition.
    val navAnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
    var mainContentReady by remember { mutableStateOf(navAnimatedVisibilityScope == null) }
    LaunchedEffect(navAnimatedVisibilityScope) {
        val transition = navAnimatedVisibilityScope?.transition ?: return@LaunchedEffect
        snapshotFlow { transition.currentState }.first { it == EnterExitState.Visible }
        mainContentReady = true
    }
    val dimensions = LocalStudiareDimensions.current
    val allTags by viewModel.tags.collectAsState()
    val savedTemplateSets by viewModel.templateSets.collectAsState()
    val alwaysOpenBulkEditor by viewModel.alwaysOpenBulkEditor.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    var editingCard by remember { mutableStateOf<CardEntity?>(null) }
    var createCardState by remember { mutableStateOf<CardEditorState?>(null) }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var showFilterOverlay by remember { mutableStateOf(false) }
    var showDeckInfo by remember { mutableStateOf(false) }
    var showDeckOptions by remember { mutableStateOf(false) }
    var filterQuery by remember { mutableStateOf("") }
    val lazyGridState = rememberLazyGridState()

    val currentDeckTags = remember(deckWithCards?.cards) {
        deckWithCards?.cards?.flatMap { it.tags }?.toSet() ?: emptySet()
    }

    val sortedCards = remember(deckWithCards?.cards, deckWithCards?.deck?.deckSortMode) {
        val cards = deckWithCards?.cards ?: emptyList()
        when (deckWithCards?.deck?.deckSortMode ?: DeckSortMode.DATE_ADDED_OLD_TO_NEW) {
            DeckSortMode.A_TO_Z -> cards.sortedBy { it.front.lowercase() }
            DeckSortMode.Z_TO_A -> cards.sortedByDescending { it.front.lowercase() }
            DeckSortMode.ONE_TO_FIVE -> cards.sortedWith(compareBy<CardEntity> { it.difficulty.value }.thenBy { it.front.lowercase() })
            DeckSortMode.FIVE_TO_ONE -> cards.sortedWith(compareByDescending<CardEntity> { it.difficulty.value }.thenBy { it.front.lowercase() })
            DeckSortMode.DATE_ADDED_OLD_TO_NEW -> cards.sortedWith(compareBy<CardEntity> { it.createdAt }.thenBy { it.front.lowercase() })
            DeckSortMode.DATE_ADDED_NEW_TO_OLD -> cards.sortedWith(compareByDescending<CardEntity> { it.createdAt }.thenBy { it.front.lowercase() })
            DeckSortMode.DATE_MODIFIED_NEW_TO_OLD -> cards.sortedWith(compareByDescending<CardEntity> { it.updatedAt }.thenBy { it.front.lowercase() })
            DeckSortMode.DATE_MODIFIED_OLD_TO_NEW -> cards.sortedWith(compareBy<CardEntity> { it.updatedAt }.thenBy { it.front.lowercase() })
        }
    }

    val displayedCards = remember(sortedCards, filterQuery) {
        val trimmed = filterQuery.trim()
        if (trimmed.isBlank()) {
            sortedCards
        } else {
            sortedCards.filter {
                it.front.contains(trimmed, ignoreCase = true) || it.back.contains(trimmed, ignoreCase = true)
            }
        }
    }

    fun blankEditorState(): CardEditorState = CardEditorState(
        id = UUID.randomUUID().toString(),
        front = mutableStateOf(""), frontRichTextInfo = mutableStateOf(null), isFrontRichText = mutableStateOf(false),
        back = mutableStateOf(""), backRichTextInfo = mutableStateOf(null), isBackRichText = mutableStateOf(false),
        frontNotes = mutableStateOf(deckWithCards?.deck?.frontNoteTemplates ?: emptyList()),
        backNotes = mutableStateOf(deckWithCards?.deck?.backNoteTemplates ?: emptyList()),
        difficulty = mutableStateOf(DifficultySetting.FIVE), isKnown = mutableStateOf(false),
        reviewedCount = mutableStateOf(0), gradedAttempts = mutableStateOf(emptyList()),
        incorrectAttempts = mutableStateOf(emptyList()), reviewLogs = mutableStateOf(emptyList()),
        absoluteDueDate = mutableStateOf(null), tags = mutableStateOf(emptyList()),
        isSuspended = mutableStateOf(false), flag = mutableStateOf(CardFlag.NONE),
        createdAt = mutableLongStateOf(System.currentTimeMillis()), updatedAt = mutableStateOf(System.currentTimeMillis())
    )

    fun cardDataFrom(state: CardEditorState): CardDataForSave = CardDataForSave(
        id = state.id,
        front = state.front.value.trim(),
        frontRichText = state.frontRichTextInfo.value?.trim(),
        back = state.back.value.trim(),
        backRichText = state.backRichTextInfo.value?.trim(),
        frontNotes = state.frontNotes.value,
        backNotes = state.backNotes.value,
        difficulty = state.difficulty.value,
        isKnown = state.isKnown.value,
        reviewedCount = state.reviewedCount.value,
        gradedAttempts = state.gradedAttempts.value,
        incorrectAttempts = state.incorrectAttempts.value,
        reviewLogs = state.reviewLogs.value,
        absoluteDueDate = state.absoluteDueDate.value,
        tags = state.tags.value,
        isSuspended = state.isSuspended.value,
        flag = state.flag.value,
        createdAt = state.createdAt.value,
        updatedAt = System.currentTimeMillis()
    )

    fun goToBulkEditor() {
        val id = deckWithCards?.deck?.id ?: return
        // Replace this screen in the back stack instead of pushing on top of it, so Bulk
        // Editor's back button (and save/discard) exits straight past Simple Editor.
        val simpleEditorRoute = navController.currentBackStackEntry?.destination?.route ?: "simpleEditor?deckId={deckId}"
        navController.navigate("deckEditor?deckId=$id") {
            popUpTo(simpleEditorRoute) { inclusive = true }
        }
    }

    fun deleteCard(card: CardEntity) {
        val deck = deckWithCards ?: return
        viewModel.deleteSingleCard(deck, card.id)
        editingCard = null
        coroutineScope.launch {
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = getText(context, R.string.card_deleted),
                actionLabel = getText(context, R.string.undo),
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.saveSingleCard(deck, card.toCardDataForSave())
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.DECK_EDITOR,
                title = { Text(deckWithCards?.deck?.name ?: "", maxLines = 1, overflow = TextOverflow.Ellipsis) },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                    }
                },
                actions = {
                    FilledTonalButton(
                        onClick = ::goToBulkEditor,
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        modifier = Modifier.padding(end = 8.dp),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.bulk_editor))
                    }
                }
            )
        },
        floatingActionButton = {
            HorizontalFloatingToolbar(
                expanded = true,
                // "Vibrant" toolbar colors tint the bar with primaryContainer instead of the
                // standard variant's surfaceContainer — matches the Bulk Editor's toolbar.
                colors = FloatingToolbarDefaults.vibrantFloatingToolbarColors(),
                floatingActionButton = {
                    FloatingActionButton(onClick = { createCardState = blankEditorState() }) {
                        Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add))
                    }
                }
            ) {
                TooltipIconButton(
                    description = getText(R.string.scroll_to_bottom),
                    onClick = { coroutineScope.launch { lazyGridState.jumpThenAnimateTo((displayedCards.size - 1).coerceAtLeast(0), scrollOffset = Int.MAX_VALUE) } }
                ) {
                    Icon(Icons.Default.ArrowDownward, contentDescription = getText(R.string.scroll_to_bottom))
                }
                TooltipIconButton(
                    description = getText(R.string.scroll_to_top),
                    onClick = { coroutineScope.launch { lazyGridState.jumpThenAnimateTo(0) } }
                ) {
                    Icon(Icons.Default.ArrowUpward, contentDescription = getText(R.string.scroll_to_top))
                }
                TooltipIconButton(description = getText(R.string.cards_filter_), onClick = { showFilterOverlay = !showFilterOverlay }) {
                    Icon(Icons.Default.Search, contentDescription = getText(R.string.cards_filter_))
                }
                TooltipIconButton(description = getText(R.string.deck_statistics), onClick = { showDeckInfo = true }) {
                    Icon(Icons.Default.Info, contentDescription = getText(R.string.deck_statistics))
                }
                TooltipIconButton(description = getText(R.string.deck_settings), onClick = { showDeckOptions = true }) {
                    Icon(Icons.Default.Settings, contentDescription = getText(R.string.deck_settings))
                }
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (filterQuery.isNotBlank()) {
                Row(
                    modifier = Modifier.padding(start = dimensions.paddingMedium, end = dimensions.paddingMedium, top = dimensions.paddingMedium)
                ) {
                    AssistChip(
                        onClick = { showFilterOverlay = true },
                        label = { Text(stringResource(R.string.filter_applied_format, filterQuery.trim())) },
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = getText(R.string.remove),
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { filterQuery = "" }
                            )
                        }
                    )
                }
            }
            // Gated on mainContentReady (see the comment above) so the grid's genuinely expensive
            // first composition happens after, not during, the NavHost transition into this
            // screen — a plain fade covers its otherwise-abrupt appearance.
            AnimatedVisibility(
                visible = mainContentReady,
                enter = fadeIn(),
                exit = ExitTransition.None,
                modifier = Modifier.fillMaxSize()
            ) {
                LazyVerticalGrid(
                    state = lazyGridState,
                    columns = GridCells.Adaptive(minSize = 260.dp),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = dimensions.paddingMedium,
                        end = dimensions.paddingMedium,
                        top = dimensions.paddingMedium,
                        bottom = 120.dp
                    ),
                    horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimensions.spacingSmall),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(dimensions.spacingSmall)
                ) {
                    itemsIndexed(displayedCards, key = { _, card -> card.id }) { index, card ->
                        CardTile(
                            card = card,
                            cardNumber = index + 1,
                            totalCards = displayedCards.size,
                            allTags = allTags,
                            toEditorState = { it.toEditorState() },
                            onClick = { editingCard = card }
                        )
                    }
                }
            }
        }
    }

    editingCard?.let { card ->
        val editState = remember(card.id) { card.toEditorState() }
        val totalCards = deckWithCards?.cards?.size ?: 1
        val cardNumber = ((deckWithCards?.cards?.indexOfFirst { it.id == card.id } ?: 0) + 1).coerceAtLeast(1)
        CardEditDialog(
            cardState = editState,
            cardNumber = cardNumber,
            totalCards = totalCards,
            allTags = allTags,
            currentDeckTags = currentDeckTags,
            onUpdateTags = { newTags -> editState.tags.value = newTags.toList() },
            onCreateTag = { name, color -> viewModel.saveTagDefinition(TagDefinition(name = name, color = color)) },
            mode = CardEditDialogMode.Edit(
                onSave = {
                    deckWithCards?.let { viewModel.saveSingleCard(it, cardDataFrom(editState)) }
                    editingCard = null
                },
                onDelete = { deleteCard(card) }
            ),
            onCancel = { editingCard = null },
            normalizationType = deckWithCards?.deck?.normalizationType ?: NormalizationType.NONE
        )
    }

    createCardState?.let { state ->
        val totalCards = (deckWithCards?.cards?.size ?: 0) + 1
        CardEditDialog(
            cardState = state,
            cardNumber = totalCards,
            totalCards = totalCards,
            allTags = allTags,
            currentDeckTags = currentDeckTags,
            onUpdateTags = { newTags -> state.tags.value = newTags.toList() },
            onCreateTag = { name, color -> viewModel.saveTagDefinition(TagDefinition(name = name, color = color)) },
            mode = CardEditDialogMode.Create(
                onAdd = {
                    deckWithCards?.let { viewModel.saveSingleCard(it, cardDataFrom(state)) }
                    createCardState = null
                },
                onAddMore = {
                    deckWithCards?.let { viewModel.saveSingleCard(it, cardDataFrom(state)) }
                    createCardState = blankEditorState()
                }
            ),
            onCancel = { createCardState = null },
            normalizationType = deckWithCards?.deck?.normalizationType ?: NormalizationType.NONE
        )
    }

    if (showTemplateEditor && deckWithCards != null) {
        AdvancedDeckEditorDialog(
            frontTemplates = deckWithCards.deck.frontNoteTemplates,
            backTemplates = deckWithCards.deck.backNoteTemplates,
            onDismiss = {
                // Template Editor is only ever reached from Deck Options' "Open Template Editor"
                // button, which closes Deck Options before opening this — so cancelling here (via
                // the Cancel button, back, or tap-outside) needs to explicitly reopen it, or the
                // user lands back on the editor screen with both dialogs gone.
                showTemplateEditor = false
                showDeckOptions = true
            },
            onSave = { newFront, newBack, addToExistingCards ->
                viewModel.saveNoteTemplates(deckWithCards, newFront, newBack, addToExistingCards)
                showTemplateEditor = false
                showDeckOptions = true
            },
            savedTemplateSets = savedTemplateSets,
            onSaveAsTemplate = { name, front, back -> viewModel.saveAsTemplateSet(name, front, back) },
            onDeleteTemplateSet = { templateSet -> viewModel.deleteTemplateSet(templateSet) }
        )
    }

    FloatingFilterOverlay(
        visible = showFilterOverlay,
        filterText = filterQuery,
        onFilterTextChange = { filterQuery = it },
        onDismiss = { showFilterOverlay = false }
    )

    if (showDeckInfo && deckWithCards != null) {
        DeckInfoDialog(deckWithCards = deckWithCards, onDismiss = { showDeckInfo = false })
    }

    if (showDeckOptions && deckWithCards != null) {
        DeckSettingsDialog(
            initialDeckName = deckWithCards.deck.name,
            initialNormalizationType = deckWithCards.deck.normalizationType,
            initialDeckSort = deckWithCards.deck.deckSortMode,
            initialDefaultPromptSide = deckWithCards.deck.defaultPromptSide,
            initialFrontLanguage = deckWithCards.deck.frontLanguage,
            initialBackLanguage = deckWithCards.deck.backLanguage,
            onDismiss = { showDeckOptions = false },
            onSave = { newName, newNorm, newSort, newPromptSide, newFrontLang, newBackLang ->
                viewModel.updateDeckOptions(deckWithCards, newName, newNorm, newSort, newPromptSide, newFrontLang, newBackLang)
                showDeckOptions = false
            },
            onOpenTemplateEditor = { showTemplateEditor = true },
            onClearReviewData = {
                viewModel.clearDeckReviewData(deckWithCards.deck.id)
                showDeckOptions = false
            },
            alwaysOpenBulkEditor = alwaysOpenBulkEditor,
            onSetAlwaysOpenBulkEditor = { viewModel.setAlwaysOpenBulkEditor(it) }
        )
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun CardTile(
    card: CardEntity,
    cardNumber: Int,
    totalCards: Int,
    allTags: List<TagDefinition>,
    toEditorState: (CardEntity) -> CardEditorState,
    onClick: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var showInfo by remember { mutableStateOf(false) }
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        FlippableCardShell(
            cardNumber = cardNumber,
            totalCards = totalCards,
            showInfo = showInfo,
            onToggleInfo = { showInfo = !showInfo },
            modifier = Modifier.padding(
                start = dimensions.paddingMedium,
                end = dimensions.paddingMedium,
                bottom = dimensions.paddingMedium,
                top = dimensions.paddingSmall
            ),
            contentTopSpacing = 0.dp,
            infoContent = { CardMetadataContent(remember(card.id) { toEditorState(card) }) },
            frontContent = {
                @Composable
                fun DifficultyBadge() {
                    Icon(
                        imageVector = Icons.Default.Speed,
                        contentDescription = getText(R.string.difficulty),
                        modifier = Modifier.size(18.dp)
                    )
                    androidx.compose.foundation.layout.Spacer(Modifier.width(4.dp))
                    Text(
                        card.difficulty.value.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (card.isKnown) {
                        androidx.compose.foundation.layout.Spacer(Modifier.width(dimensions.spacingSmall))
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = getText(R.string.mark_as_not_known),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Column(modifier = Modifier.fillMaxWidth()) {
                    val dimColor = MaterialTheme.colorScheme.onSurfaceVariant
                    val paragraph = buildAnnotatedString {
                        withStyle(SpanStyle(fontSize = MaterialTheme.typography.titleMedium.fontSize, fontWeight = FontWeight.Bold)) {
                            append(card.front)
                        }
                        card.frontNotes.filter { it.content.isNotBlank() }.forEach { note ->
                            withStyle(SpanStyle(color = dimColor)) {
                                append(" ${note.name}: ${note.content}")
                            }
                        }
                        withStyle(SpanStyle(color = dimColor)) {
                            append(" → ")
                            append(card.back)
                        }
                        card.backNotes.filter { it.content.isNotBlank() }.forEach { note ->
                            withStyle(SpanStyle(color = dimColor)) {
                                append(" ${note.name}: ${note.content}")
                            }
                        }
                    }

                    if (card.tags.isEmpty()) {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                paragraph,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 6,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            androidx.compose.foundation.layout.Spacer(Modifier.width(dimensions.spacingSmall))
                            Row(verticalAlignment = Alignment.CenterVertically) { DifficultyBadge() }
                        }
                    } else {
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 6,
                            overflow = TextOverflow.Ellipsis
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(top = dimensions.spacingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            androidx.compose.foundation.layout.FlowRow(modifier = Modifier.weight(1f)) {
                                card.tags.forEach { tagName ->
                                    val def = allTags.find { it.name == tagName }
                                    TagChip(text = tagName, colorHex = def?.color ?: "#9E9E9E")
                                }
                            }
                            androidx.compose.foundation.layout.Spacer(Modifier.width(dimensions.spacingSmall))
                            Row(verticalAlignment = Alignment.CenterVertically) { DifficultyBadge() }
                        }
                    }
                }
            }
        )
    }
}

