package net.ericclark.studiare.screens.UI_Components
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.Deck
import net.ericclark.studiare.data.DeckSortMode
import net.ericclark.studiare.data.Direction
import net.ericclark.studiare.data.PinnedSectionType
import net.ericclark.studiare.data.SessionTileSortMode
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/** Orders pinned decks/sets against each other on the Pinned screen — reuses [DeckSortMode] since
 * its comparator logic (alphabetical/difficulty-free dates) already fits a flat list of decks. */
internal fun sortedPinnedDecks(decks: List<Deck>, mode: DeckSortMode): List<Deck> = when (mode) {
    DeckSortMode.A_TO_Z -> decks.sortedBy { it.name.lowercase() }
    DeckSortMode.Z_TO_A -> decks.sortedByDescending { it.name.lowercase() }
    DeckSortMode.DATE_ADDED_NEW_TO_OLD -> decks.sortedByDescending { it.createdAt }
    DeckSortMode.DATE_ADDED_OLD_TO_NEW -> decks.sortedBy { it.createdAt }
    DeckSortMode.DATE_MODIFIED_NEW_TO_OLD -> decks.sortedByDescending { it.updatedAt }
    DeckSortMode.DATE_MODIFIED_OLD_TO_NEW -> decks.sortedBy { it.updatedAt }
    DeckSortMode.ONE_TO_FIVE, DeckSortMode.FIVE_TO_ONE -> decks.sortedBy { it.name.lowercase() } // not meaningful for decks; falls back
}

/**
 * The Pinned screen's sort controls — the Sort tab's body in its shared
 * [net.ericclark.studiare.screens.UI_Components.SortShortcutsSheetHost] sheet. One picker per
 * fixed section (Decks & Sets, Cards, Saved Sessions), staged locally with an explicit Save,
 * same convention as the Study Hub's [SessionSortContent]. Unlike that one, there's no "group by"
 * toggle since these sections are fixed, not an open set.
 *
 * Section order (the 4 reorderable sections: Presets, Saved Sessions, Decks, Sets — Cards' position
 * is fixed last, not reorderable) used to be an inline [ReorderableList] at the top of this same
 * staged form, back when it was a standalone dialog. Nested here inside the sheet's own
 * vertically-scrolling tab content, that list's internal drag-to-reorder gesture would fight the
 * tab's scroll for the same vertical drag (the same class of nested-scroll conflict the Progress
 * tab's card list hit earlier) - so it now opens as its own small, separate [PinnedSectionOrderDialog]
 * instead of living inline, and applies live (immediately, via [onSectionOrderChange]) rather than
 * waiting on this form's own Save, since there's no reasonable way to "cancel" a drag reorder once
 * the list has already visually reordered itself in front of the user.
 */
@Composable
internal fun PinnedSortContent(
    initialDeckSortMode: DeckSortMode,
    initialCardSortMode: DeckSortMode,
    initialSessionSortMode: SessionTileSortMode,
    initialSessionSortDirection: Direction,
    sectionOrder: List<PinnedSectionType>,
    onSectionOrderChange: (List<PinnedSectionType>) -> Unit,
    onSave: (DeckSortMode, DeckSortMode, SessionTileSortMode, Direction) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var deckSortMode by remember { mutableStateOf(initialDeckSortMode) }
    var cardSortMode by remember { mutableStateOf(initialCardSortMode) }
    var sessionSortMode by remember { mutableStateOf(initialSessionSortMode) }
    var sessionSortDirection by remember { mutableStateOf(initialSessionSortDirection) }
    var showSectionOrderDialog by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            DialogSection(title = getText(R.string.section_order_title)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().clickable { showSectionOrderDialog = true }
                ) {
                    // joinToString's transform lambda isn't inline (it's nullable), so a @Composable
                    // call (asString()) can't go directly inside it - map to strings first instead.
                    Text(sectionOrder.map { it.asString() }.joinToString(", "), modifier = Modifier.weight(1f))
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    FilledTonalButton(onClick = { showSectionOrderDialog = true }) {
                        Text(getText(R.string.edit_section_order))
                    }
                }
            }
            DialogSection(title = getText(R.string.pinned_decks_and_sets)) {
                ExpressiveDropdownMenuField(
                    options = DeckSortMode.entries.filter { it != DeckSortMode.ONE_TO_FIVE && it != DeckSortMode.FIVE_TO_ONE },
                    selectedOption = deckSortMode,
                    optionLabel = { it.asString() },
                    onSelect = { deckSortMode = it }
                )
            }
            Spacer(Modifier.height(dimensions.spacingMedium))
            DialogSection(title = getText(R.string.pinned_cards)) {
                ExpressiveDropdownMenuField(
                    options = DeckSortMode.entries,
                    selectedOption = cardSortMode,
                    optionLabel = { it.asString() },
                    onSelect = { cardSortMode = it }
                )
            }
            Spacer(Modifier.height(dimensions.spacingMedium))
            // No divider here - the content's own HorizontalDivider right below the scrollable
            // content already separates the last section from the button row.
            DialogSection(title = getText(R.string.pinned_saved_sessions), showDivider = false) {
                TileSortRow(sessionSortMode, { sessionSortMode = it }, sessionSortDirection, { sessionSortDirection = it })
            }
        }

        Spacer(Modifier.height(dimensions.spacingMedium))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(dimensions.spacingMedium))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
            Spacer(Modifier.width(dimensions.spacingSmall))
            Button(onClick = {
                onSave(deckSortMode, cardSortMode, sessionSortMode, sessionSortDirection)
                onDismiss()
            }) {
                Text(getText(R.string.save))
            }
        }
    }

    if (showSectionOrderDialog) {
        PinnedSectionOrderDialog(
            order = sectionOrder,
            onReorder = onSectionOrderChange,
            onDismiss = { showSectionOrderDialog = false }
        )
    }
}

/**
 * A small standalone dialog just for reordering the Pinned screen's 4 reorderable sections - split
 * out of [PinnedSortContent] so its [ReorderableList] drag gesture never has to share a scroll
 * container with anything else (see that function's doc for why). Live-apply: every reorder goes
 * straight to [onReorder], and "Done" just closes the dialog rather than committing a staged value.
 */
@Composable
internal fun PinnedSectionOrderDialog(
    order: List<PinnedSectionType>,
    onReorder: (List<PinnedSectionType>) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = getText(R.string.section_order_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                ReorderableList(items = order, onReorder = onReorder, key = { it }) { section ->
                    Text(section.asString(), style = MaterialTheme.typography.bodyLarge)
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(getText(R.string.done)) }
                }
            }
        }
    }
}
