package net.ericclark.studiare.screens.Dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChecklistRtl
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/** The ways sets can be created for (or removed from) a deck; mirrors the sets screen's floating menu. */
enum class SetCreationAction { CLONE, PICK_AND_CHOOSE, FILTER_AND_SORT, DELETE_ALL }

/** Chooser listing the set-creation options, used where there is no floating menu (hierarchy view). */
@Composable
fun CreateSetOptionsDialog(
    hasSets: Boolean,
    onSelect: (SetCreationAction) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge), verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                Text(getText(R.string.set_create), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingSmall))
                @Composable
                fun Option(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, tonal: Boolean = true, action: SetCreationAction) {
                    FilledTonalButton(
                        onClick = { onSelect(action) },
                        modifier = Modifier.fillMaxWidth().height(56.dp),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                        colors = if (action == SetCreationAction.DELETE_ALL) ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ) else ButtonDefaults.filledTonalButtonColors()
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Icon(icon, contentDescription = null, modifier = Modifier.align(Alignment.CenterStart))
                            Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.align(Alignment.Center))
                        }
                    }
                }
                Option(Icons.Default.ContentCopy, "Clone Entire Deck", action = SetCreationAction.CLONE)
                Option(Icons.Default.ChecklistRtl, getText(R.string.pick_and_choose), action = SetCreationAction.PICK_AND_CHOOSE)
                Option(Icons.Default.FilterList, "Filter & Sort", action = SetCreationAction.FILTER_AND_SORT)
                if (hasSets) Option(Icons.Default.Delete, getText(R.string.delete_all_sets), action = SetCreationAction.DELETE_ALL)
                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier.align(Alignment.End),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.cancel)) }
            }
        }
    }
}

/** Hosts the dialogs behind each [SetCreationAction] so any screen can offer them. */
@Composable
fun SetCreationDialogHost(
    parentDeck: DeckWithCards,
    action: SetCreationAction?,
    viewModel: FlashcardViewModel,
    onDismiss: () -> Unit
) {
    if (action == null) return
    val dimensions = LocalStudiareDimensions.current
    val allTags by viewModel.tags.collectAsState()
    val parentDeckTags = remember(parentDeck) { parentDeck.cards.flatMap { it.tags }.distinct().sorted() }
    var showRangeSelector by remember { mutableStateOf<Pair<AutoSetConfig, List<Card>>?>(null) }

        if (action == SetCreationAction.PICK_AND_CHOOSE) {
            ManualSetCreatorDialog(
                parentDeck = parentDeck,
                viewModel = viewModel,
                onDismiss = { onDismiss() }
            )
        }


        if (action == SetCreationAction.CLONE) {
            var cloneName by remember { mutableStateOf("${parentDeck.deck.name} (Clone)") }
            AnimatedDialog(onDismissRequest = { onDismiss() }) {
                Surface(
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    tonalElevation = 6.dp
                ) {
                    Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                        Text(getText(R.string.set_create), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(dimensions.spacingMedium))
                        OutlinedTextField(
                            value = cloneName,
                            onValueChange = { cloneName = it },
                            label = { Text(getText(R.string.deck_name)) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(dimensions.spacingLarge))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { onDismiss() }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                            Spacer(Modifier.width(dimensions.spacingSmall))
                            Button(onClick = {
                                viewModel.cloneDeckAsSet(parentDeck, cloneName)
                                onDismiss()
                            },
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.save)) }
                        }
                    }
                }
            }
        }


        if (action == SetCreationAction.FILTER_AND_SORT && showRangeSelector == null) {
            AutomaticSetCreatorDialog(
                parentDeck = parentDeck,
                availableTags = parentDeckTags,
                allTagDefinitions = allTags,
                onDismiss = { onDismiss() },
                onCreate = { config ->
                    viewModel.createAutomaticSets(parentDeck, config)
                    onDismiss()
                },
                onPickStartCard = { config ->
                    // ... (Logic remains identical)
                    var pool = parentDeck.cards
                    if (config.excludeKnown) pool = pool.filter { !it.isKnown }

                    val timeMultiplier = when (config.timeUnit) {
                        TimeUnit.DAYS -> 24 * 60 * 60 * 1000L
                        TimeUnit.WEEKS -> 7 * 24 * 60 * 60 * 1000L
                        TimeUnit.MONTHS -> 30 * 24 * 60 * 60 * 1000L
                        TimeUnit.YEARS -> 365 * 24 * 60 * 60 * 1000L
                    }
                    val cutoffTime = System.currentTimeMillis() - (config.timeValue * timeMultiplier)

                    pool = when (config.selectionMode) {
                        SelectionMode.DIFFICULTY -> pool.filter { it.difficulty.value in config.selectedDifficulties }
                        SelectionMode.TAGS -> pool.filter { card -> card.tags.any { it in config.selectedTags } }
                        SelectionMode.ALPHABET -> {
                            val start = config.alphabetStart.uppercase()
                            val end = config.alphabetEnd.uppercase()
                            pool.filter { card ->
                                val text = if (config.filterSide == CardSide.FRONT) card.front else card.back
                                val firstChar = text.trim().uppercase(java.util.Locale.getDefault()).firstOrNull()?.toString()
                                firstChar != null && firstChar >= start && firstChar <= end
                            }
                        }
                        SelectionMode.CARD_ORDER -> {
                            val s = (config.cardOrderStart - 1).coerceAtLeast(0)
                            val e = (config.cardOrderEnd - 1).coerceAtMost(parentDeck.cards.size - 1)
                            if (s <= e && parentDeck.cards.isNotEmpty()) {
                                val allowedIds = parentDeck.cards.slice(s..e).map { it.id }.toSet()
                                pool.filter { it.id in allowedIds }
                            } else emptyList()
                        }
                        SelectionMode.REVIEW_DATE -> {
                            if (config.filterType == FilterType.INCLUDE) pool.filter { it.reviewedAt != null && it.reviewedAt >= cutoffTime }
                            else pool.filter { it.reviewedAt == null || it.reviewedAt < cutoffTime }
                        }
                        SelectionMode.INCORRECT_DATE -> {
                            if (config.filterType == FilterType.INCLUDE) pool.filter { card -> card.incorrectAttempts.maxOrNull()?.let { last -> last >= cutoffTime } == true }
                            else pool.filter { card -> card.incorrectAttempts.isEmpty() || card.incorrectAttempts.maxOrNull()!! < cutoffTime }
                        }
                        SelectionMode.REVIEW_COUNT -> {
                            if (config.reviewCountDirection == Direction.DESC) pool.filter { it.reviewedCount <= config.reviewCountThreshold }
                            else pool.filter { it.reviewedCount >= config.reviewCountThreshold }
                        }
                        SelectionMode.SCORE -> {
                            val getScore: (Card) -> Float = { card ->
                                val total = card.gradedAttempts.size
                                if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                            }
                            val threshold = config.scoreThreshold.toFloat() / 100f
                            if (config.scoreDirection == Direction.DESC) pool.filter { getScore(it) <= threshold }
                            else pool.filter { getScore(it) >= threshold }
                        }
                        else -> pool
                    }

                    // Sorting Logic
                    val getScore: (Card) -> Float = { card ->
                        val total = card.gradedAttempts.size
                        if (total == 0) 0f else (total - card.incorrectAttempts.size).toFloat() / total
                    }
                    val isAsc = config.sortDirection == Direction.ASC

                    val sorted = when (config.sortMode) {
                        SortMode.ALPHABETICAL -> {
                            val selector: (Card) -> String = { if (config.sortSide == CardSide.FRONT) it.front.lowercase() else it.back.lowercase() }
                            if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
                        }
                        SortMode.REVIEW_DATE -> {
                            val selector: (Card) -> Long? = { it.reviewedAt }
                            if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector))
                            else pool.sortedWith(compareByDescending(nullsLast(), selector))
                        }
                        SortMode.INCORRECT_DATE -> {
                            val selector: (Card) -> Long? = { it.incorrectAttempts.maxOrNull() }
                            if (isAsc) pool.sortedWith(compareBy(nullsLast(), selector))
                            else pool.sortedWith(compareByDescending(nullsLast(), selector))
                        }
                        SortMode.REVIEW_COUNT -> {
                            if (isAsc) pool.sortedBy { it.reviewedCount } else pool.sortedByDescending { it.reviewedCount }
                        }
                        SortMode.SCORE -> {
                            if (isAsc) pool.sortedBy(getScore) else pool.sortedByDescending(getScore)
                        }
                        SortMode.CARD_ORDER -> {
                            val indexMap = parentDeck.cards.mapIndexed { index, card -> card.id to index }.toMap()
                            val selector: (Card) -> Int = { indexMap[it.id] ?: Int.MAX_VALUE }
                            if (isAsc) pool.sortedBy(selector) else pool.sortedByDescending(selector)
                        }
                        SortMode.RANDOM -> pool.shuffled()
                        SortMode.NONE -> pool
                    }

                    showRangeSelector = config to sorted
                }
            )
        }


        showRangeSelector?.let { (config, sortedCards) ->
            CardRangeSelectionDialog(
                sortedCards = sortedCards,
                viewModel = viewModel,
                onDismiss = { showRangeSelector = null; onDismiss() },
                onConfirm = { startCardId ->
                    viewModel.createAutomaticSets(parentDeck, config, startCardId)
                    showRangeSelector = null
                    onDismiss()
                }
            )
        }


        if (action == SetCreationAction.DELETE_ALL) {
            ConfirmationDialog(
                title = getText(R.string.delete_all_sets_question),
                text = stringResource(R.string.delete_all_sets_confirm, parentDeck.deck.name),
                onConfirm = {
                    viewModel.deleteAllSetsForDeck(parentDeck.deck.id)
                    onDismiss()
                },
                onDismiss = { onDismiss() },
                confirmButtonText = getText(R.string.delete_all)
            )
        }


}
