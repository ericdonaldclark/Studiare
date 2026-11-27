package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import java.util.Locale

/** Which decks a single Deck Options apply action targets. */
private enum class DeckOptionsApplyScope { ALL_CURRENT, ALL_CURRENT_AND_FUTURE, ALL_FUTURE_ONLY, SELECTED_CURRENT }

/** A button row + (optionally) dialog tapped [fields] with [scope] — set by any of this screen's
 * per-section rows (one [DeckOptionField] each) or the bottom "apply everything" row (all four).
 * [SELECTED_CURRENT] needs a deck pick first, so it's resolved inside the dialog that reads this
 * rather than at the tap site; every other scope is ready to confirm immediately. */
private data class PendingApply(val fields: Set<DeckOptionField>, val scope: DeckOptionsApplyScope)

/**
 * Settings → Deck Options: configure Card Order, Default Prompt Side, Deck Language, and Text
 * Normalization, each independently applicable (its own scrollable row of "apply to" buttons
 * right below it), plus a bottom section that applies all four together. Reuses the same controls
 * [net.ericclark.studiare.screens.UI_Components.DeckSettingsDialog] already renders per-deck
 * ([ExpressiveDropdownMenuField], [LanguageDropdown], the shared
 * [net.ericclark.studiare.screens.Dialogs.TextNormalizationDialog]). "Future" scopes merge into
 * the stored [DefaultDeckOptions] via [FlashcardViewModel.setDefaultDeckOptions]; "current"/
 * "selected" scopes bulk-apply via [FlashcardViewModel.applyDeckOptionsToDecks] — both merge only
 * the tapped [DeckOptionField]s (see [withFieldsFrom]), leaving every other setting, per deck,
 * exactly as it was. Every apply goes behind an upfront confirmation (a deck picker, for
 * "Selected current decks") and a post-apply Undo snackbar.
 */
@Composable
internal fun DeckOptionsCategoryContent(viewModel: FlashcardViewModel, snackbarHostState: SnackbarHostState) {
    val dimensions = LocalStudiareDimensions.current
    val scope = rememberCoroutineScope()
    val androidContext = LocalContext.current
    val undoLabel = getText(R.string.undo)

    val savedDefault by viewModel.defaultDeckOptions.collectAsState()
    val allDecks by viewModel.allDecks.observeAsState(emptyList())

    var deckSortMode by remember { mutableStateOf(savedDefault?.deckSortMode ?: DeckSortMode.DATE_ADDED_OLD_TO_NEW) }
    var defaultPromptSide by remember { mutableStateOf(savedDefault?.defaultPromptSide ?: CardSide.AUTO_BY_DECK) }
    var frontLanguage by remember { mutableStateOf(savedDefault?.frontLanguage ?: Locale.getDefault().language) }
    var backLanguage by remember { mutableStateOf(savedDefault?.backLanguage ?: Locale.getDefault().language) }
    var normalizationType by remember { mutableStateOf(savedDefault?.normalizationType ?: NormalizationType.NONE) }
    var textCorrections by remember { mutableStateOf(savedDefault?.textCorrections ?: TextCorrectionSettings()) }
    var showTextNormalizationDialog by remember { mutableStateOf(false) }

    val selectedDecks = remember { mutableStateListOf<DeckWithCards>() }
    var isApplying by remember { mutableStateOf(false) }
    var pendingApply by remember { mutableStateOf<PendingApply?>(null) }

    val availableLanguages = remember {
        Locale.getAvailableLocales()
            .map { it.language to it.displayLanguage }
            .filter { it.second.isNotEmpty() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    fun currentOptions() = DefaultDeckOptions(
        deckSortMode = deckSortMode,
        defaultPromptSide = defaultPromptSide,
        frontLanguage = frontLanguage,
        backLanguage = backLanguage,
        normalizationType = normalizationType,
        textCorrections = textCorrections
    )

    /** Mirrors [FlashcardViewModel.applyDeckOptionsToDecks]'s own "did this deck's merged
     * normalization actually change" check, so a preview count matches what will really happen. */
    fun previewCardCount(targets: List<DeckWithCards>, options: DefaultDeckOptions, fields: Set<DeckOptionField>): Int {
        if (DeckOptionField.NORMALIZATION !in fields) return 0
        return targets.filter { it.deck.normalizationType != options.normalizationType || it.deck.textCorrections != options.textCorrections }
            .sumOf { it.cards.size }
    }

    fun runApply(fields: Set<DeckOptionField>, scopeChosen: DeckOptionsApplyScope, deckIds: Set<String>?) {
        val options = currentOptions()
        val savesFuture = scopeChosen == DeckOptionsApplyScope.ALL_CURRENT_AND_FUTURE || scopeChosen == DeckOptionsApplyScope.ALL_FUTURE_ONLY
        if (savesFuture) {
            viewModel.setDefaultDeckOptions((savedDefault ?: DefaultDeckOptions()).withFieldsFrom(options, fields))
        }
        val targets = when (scopeChosen) {
            DeckOptionsApplyScope.ALL_FUTURE_ONLY -> emptyList()
            DeckOptionsApplyScope.SELECTED_CURRENT -> allDecks.filter { it.deck.id in (deckIds ?: emptySet()) }
            else -> allDecks
        }
        if (targets.isEmpty()) {
            scope.launch { snackbarHostState.showSnackbar(androidContext.getString(R.string.deck_options_future_default_saved)) }
            return
        }
        viewModel.applyDeckOptionsToDecks(
            deckIds = if (scopeChosen == DeckOptionsApplyScope.SELECTED_CURRENT) deckIds else null,
            options = options,
            fields = fields,
            onProgress = { isApplying = it },
            onComplete = { result ->
                scope.launch {
                    val message = if (result.cardCount > 0) {
                        androidContext.getString(R.string.deck_options_applied_with_cards, result.deckCount, result.cardCount)
                    } else {
                        androidContext.getString(R.string.deck_options_applied_decks_only, result.deckCount)
                    }
                    val snackResult = snackbarHostState.showSnackbar(message = message, actionLabel = undoLabel)
                    if (snackResult == SnackbarResult.ActionPerformed) {
                        viewModel.undoDeckOptionsApply(result.snapshotDecks, result.snapshotCards)
                    }
                }
            }
        )
    }

    Column {
        Text(
            getText(R.string.deck_options_desc),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimensions.spacingMedium)
        )

        Text(getText(R.string.sort_card_order), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(dimensions.spacingSmall))
        ExpressiveDropdownMenuField(
            options = DeckSortMode.entries,
            selectedOption = deckSortMode,
            optionLabel = { it.asString() },
            onSelect = { deckSortMode = it }
        )
        Spacer(Modifier.height(dimensions.spacingSmall))
        DeckOptionsApplyButtonRow(setOf(DeckOptionField.CARD_ORDER)) { fields, s -> pendingApply = PendingApply(fields, s) }
        Spacer(Modifier.height(dimensions.spacingMedium))

        Text(getText(R.string.default_prompt_side), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(dimensions.spacingSmall))
        ExpressiveDropdownMenuField(
            options = listOf(CardSide.AUTO_BY_DECK, CardSide.AUTO_BY_CARD, CardSide.FRONT, CardSide.BACK),
            selectedOption = defaultPromptSide,
            optionLabel = { it.asString() },
            onSelect = { defaultPromptSide = it }
        )
        Spacer(Modifier.height(dimensions.spacingSmall))
        DeckOptionsApplyButtonRow(setOf(DeckOptionField.PROMPT_SIDE)) { fields, s -> pendingApply = PendingApply(fields, s) }
        Spacer(Modifier.height(dimensions.spacingMedium))

        Text(getText(R.string.language_set), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(dimensions.spacingSmall))
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
            LanguageDropdown(
                languages = availableLanguages,
                selectedCode = frontLanguage,
                onLanguageSelected = { frontLanguage = it },
                label = getText(R.string.language_front)
            )
            LanguageDropdown(
                languages = availableLanguages,
                selectedCode = backLanguage,
                onLanguageSelected = { backLanguage = it },
                label = getText(R.string.language_back)
            )
        }
        Spacer(Modifier.height(dimensions.spacingSmall))
        DeckOptionsApplyButtonRow(setOf(DeckOptionField.LANGUAGE)) { fields, s -> pendingApply = PendingApply(fields, s) }
        Spacer(Modifier.height(dimensions.spacingMedium))

        Text(getText(R.string.case_normalization), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(dimensions.spacingSmall))
        Text(normalizationType.asString(), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(dimensions.spacingSmall))
        FilledTonalButton(
            onClick = { showTextNormalizationDialog = true },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
        ) {
            // Text centered on the full button width, icon pinned to the trailing edge — matches
            // DeckSettingsDialog's own Text Normalization/Template Editor buttons, which use this
            // exact layout to signal "opens in its own dialog" without crowding the label.
            Box(modifier = Modifier.fillMaxWidth()) {
                Text(getText(R.string.case_normalization), modifier = Modifier.align(Alignment.Center))
                Icon(
                    Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
        Spacer(Modifier.height(dimensions.spacingSmall))
        DeckOptionsApplyButtonRow(setOf(DeckOptionField.NORMALIZATION)) { fields, s -> pendingApply = PendingApply(fields, s) }

        Spacer(Modifier.height(dimensions.spacingMedium))
        HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.spacingMedium))

        Text(getText(R.string.deck_options_apply_all_title), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(dimensions.spacingSmall))
        DeckOptionsApplyButtonRow(DeckOptionField.ALL) { fields, s -> pendingApply = PendingApply(fields, s) }

        if (isApplying) {
            Spacer(Modifier.height(dimensions.spacingMedium))
            LoadingOverlay()
        }
    }

    pendingApply?.let { pending ->
        DeckOptionsConfirmDialog(
            pending = pending,
            allDecks = allDecks,
            selectedDecks = selectedDecks,
            options = currentOptions(),
            previewCardCount = { targets, options -> previewCardCount(targets, options, pending.fields) },
            onDismiss = { pendingApply = null },
            onConfirm = { deckIds ->
                pendingApply = null
                runApply(pending.fields, pending.scope, deckIds)
            }
        )
    }
}

/** A horizontally scrollable row of the 4 "apply to" buttons, shared by every section's own row
 * (one [DeckOptionField] each) and the bottom "apply everything" row. Tapping a button doesn't
 * apply anything itself — it hands [fields] + the tapped scope to [onScopeChosen], which opens the
 * confirmation (or, for "Selected current decks", the deck picker) before anything changes. */
@Composable
private fun DeckOptionsApplyButtonRow(fields: Set<DeckOptionField>, onScopeChosen: (Set<DeckOptionField>, DeckOptionsApplyScope) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    val scopeLabels = listOf(
        DeckOptionsApplyScope.ALL_CURRENT to getText(R.string.deck_options_scope_all_current),
        DeckOptionsApplyScope.ALL_CURRENT_AND_FUTURE to getText(R.string.deck_options_scope_all_current_and_future),
        DeckOptionsApplyScope.ALL_FUTURE_ONLY to getText(R.string.deck_options_scope_all_future_only),
        DeckOptionsApplyScope.SELECTED_CURRENT to getText(R.string.deck_options_scope_selected_current)
    )
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
    ) {
        scopeLabels.forEach { (s, label) ->
            Button(
                onClick = { onScopeChosen(fields, s) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                ),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) {
                Text(label)
            }
        }
    }
}

/** Shows either a plain confirmation summary (every scope but "Selected current decks") or the
 * shared [DeckSetPickerGrid] (the same deck/set tree [net.ericclark.studiare.screens.Dialogs.ExportDecksDialog]
 * uses) whose live selection drives that same summary (for "Selected current decks") — one dialog
 * instead of a separate pick-then-confirm step. */
@Composable
private fun DeckOptionsConfirmDialog(
    pending: PendingApply,
    allDecks: List<DeckWithCards>,
    selectedDecks: SnapshotStateList<DeckWithCards>,
    options: DefaultDeckOptions,
    previewCardCount: (List<DeckWithCards>, DefaultDeckOptions) -> Int,
    onDismiss: () -> Unit,
    onConfirm: (deckIds: Set<String>?) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val isSelecting = pending.scope == DeckOptionsApplyScope.SELECTED_CURRENT
    val savesFuture = pending.scope == DeckOptionsApplyScope.ALL_CURRENT_AND_FUTURE || pending.scope == DeckOptionsApplyScope.ALL_FUTURE_ONLY

    val targets = if (isSelecting) selectedDecks.toList()
        else if (pending.scope == DeckOptionsApplyScope.ALL_FUTURE_ONLY) emptyList()
        else allDecks
    val cardCount = previewCardCount(targets, options)

    AnimatedDialog(onDismissRequest = onDismiss, dismissOnScrimClick = false) {
        Surface(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(getText(R.string.deck_options_confirm_title), style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(dimensions.spacingSmall))

                if (isSelecting) {
                    DeckSetPickerGrid(decks = allDecks, selectedDecks = selectedDecks)
                    Spacer(Modifier.height(dimensions.spacingSmall))
                }

                if (targets.isNotEmpty()) {
                    Text(
                        if (cardCount > 0) {
                            stringResource(R.string.deck_options_confirm_message_with_cards, targets.size, cardCount)
                        } else {
                            stringResource(R.string.deck_options_confirm_message_decks_only, targets.size)
                        }
                    )
                } else if (!isSelecting) {
                    Text(getText(R.string.deck_options_confirm_message_future))
                }
                if (savesFuture && targets.isNotEmpty()) {
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Text(getText(R.string.deck_options_confirm_message_future))
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(
                        onClick = { onConfirm(if (isSelecting) targets.map { it.deck.id }.toSet() else null) },
                        enabled = !isSelecting || targets.isNotEmpty()
                    ) { Text(getText(R.string.deck_options_apply_button)) }
                }
            }
        }
    }
}
