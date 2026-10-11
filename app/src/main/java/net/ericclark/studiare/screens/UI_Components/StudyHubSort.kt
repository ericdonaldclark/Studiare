package net.ericclark.studiare.screens.UI_Components
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.util.*
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.data.Direction
import net.ericclark.studiare.components.getText
import androidx.compose.ui.input.key.key

/** How many of a crossword's words are fully filled in correctly — shared by [SessionTile]'s progress text/bar and [sessionProgressFraction]. */
internal fun completedCrosswordWordCount(session: ActiveSession): Int {
    if (session.mode != SessionMode.CROSSWORD) return 0
    return session.crosswordWords.count { word ->
        word.word.indices.all { i ->
            val x = if (word.isAcross) word.startX + i else word.startX
            val y = if (word.isAcross) word.startY else word.startY + i
            session.crosswordUserInputs["$x,$y"] == word.word[i].toString()
        }
    }
}

/** A session's completion fraction (0f-1f), per-mode — the same math as [SessionTile]'s progress bar, reused as the sort key for [SessionTileSortMode.PROGRESS]. */
internal fun sessionProgressFraction(session: ActiveSession): Float = when (session.mode) {
    SessionMode.MEMORY, SessionMode.MATCHING -> if (session.totalCards > 0) session.matchedPairs.size.toFloat() / session.totalCards else 0f
    SessionMode.CROSSWORD -> if (session.crosswordWords.isNotEmpty()) completedCrosswordWordCount(session).toFloat() / session.crosswordWords.size else 0f
    SessionMode.WORD_SEARCH -> if (session.wordSearchWords.isNotEmpty()) session.wordSearchFoundWordIds.size.toFloat() / session.wordSearchWords.size else 0f
    else -> if (session.totalCards > 0) session.currentCardIndex.toFloat() / session.totalCards else 0f
}

/** Orders session tiles against each other within a mode group (or a flattened merge of groups). */
internal fun sortedTiles(sessions: List<ActiveSession>, mode: SessionTileSortMode, direction: Direction): List<ActiveSession> {
    val comparator: Comparator<ActiveSession> = when (mode) {
        SessionTileSortMode.LAST_ACCESSED -> compareBy { it.lastAccessed }
        SessionTileSortMode.DATE_CREATED -> compareBy { it.createdAt }
        SessionTileSortMode.PROGRESS -> compareBy { sessionProgressFraction(it) }
    }
    val ordered = sessions.sortedWith(comparator)
    return if (direction == Direction.DESC) ordered.reversed() else ordered
}

/** Orders the outer category rows. */
internal fun sortedCategories(
    categories: List<StudyCategory>,
    mode: GroupSortMode,
    direction: Direction,
    sessionsByCategory: Map<StudyCategory, List<ActiveSession>>,
    context: android.content.Context
): List<StudyCategory> {
    val comparator: Comparator<StudyCategory> = when (mode) {
        GroupSortMode.ALPHABETICAL -> compareBy { it.asString(context) }
        GroupSortMode.MOST_RECENT -> compareBy { sessionsByCategory[it]?.maxOfOrNull { s -> s.lastAccessed } ?: 0L }
        GroupSortMode.SESSION_COUNT -> compareBy { sessionsByCategory[it]?.size ?: 0 }
    }
    val ordered = categories.sortedWith(comparator)
    return if (direction == Direction.DESC) ordered.reversed() else ordered
}

/** Orders the mode-section rows within a category. */
internal fun sortedSections(
    sectionsIn: List<SessionSection>,
    mode: GroupSortMode,
    direction: Direction,
    groupedSessions: Map<String, List<ActiveSession>>
): List<SessionSection> {
    val comparator: Comparator<SessionSection> = when (mode) {
        GroupSortMode.ALPHABETICAL -> compareBy { it.title }
        GroupSortMode.MOST_RECENT -> compareBy { groupedSessions[it.title]?.maxOfOrNull { s -> s.lastAccessed } ?: 0L }
        GroupSortMode.SESSION_COUNT -> compareBy { groupedSessions[it.title]?.size ?: 0 }
    }
    val ordered = sectionsIn.sortedWith(comparator)
    return if (direction == Direction.DESC) ordered.reversed() else ordered
}

/** The Study Hub's overflow menu — only shown when the deck has active sessions. */
@Composable
internal fun StudyHubOverflowMenu(onSortClick: () -> Unit, onDeleteAllSessions: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        TooltipIconButton(description = getText(R.string.options_more), onClick = { expanded = true }) {
            Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.options_more))
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(16.dp)
            )
        ) {
            DropdownMenuItem(
                text = { Text(getText(R.string.sort)) },
                leadingIcon = { Icon(Icons.Default.Sort, contentDescription = null) },
                onClick = { expanded = false; onSortClick() }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.delete_all)) },
                leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                onClick = { expanded = false; onDeleteAllSessions() }
            )
        }
    }
}

/**
 * The Study Hub's in-depth sort/customization dialog (opened from [StudyHubOverflowMenu]'s "Sort"
 * item). Covers all three nesting levels of the populated grid view: whether categories/modes get
 * their own collapsible header at all (`groupBy*`), and how categories, modes, and session tiles
 * are each ordered. Edits are staged locally and only committed (globally, for every deck) when
 * Save is pressed — Cancel/the close icon discard them, and Reset reverts the staged values to
 * defaults without saving, so Save still has to be pressed to persist a reset.
 */
@Composable
internal fun SessionSortDialog(
    initialGroupByCategory: Boolean,
    initialGroupByMode: Boolean,
    initialCategorySortMode: GroupSortMode,
    initialCategorySortDirection: Direction,
    initialModeSortMode: GroupSortMode,
    initialModeSortDirection: Direction,
    initialTileSortMode: SessionTileSortMode,
    initialTileSortDirection: Direction,
    onSave: (Boolean, Boolean, GroupSortMode, Direction, GroupSortMode, Direction, SessionTileSortMode, Direction) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current

    var groupByCategory by remember { mutableStateOf(initialGroupByCategory) }
    var groupByMode by remember { mutableStateOf(initialGroupByMode) }
    var categorySortMode by remember { mutableStateOf(initialCategorySortMode) }
    var categorySortDirection by remember { mutableStateOf(initialCategorySortDirection) }
    var modeSortMode by remember { mutableStateOf(initialModeSortMode) }
    var modeSortDirection by remember { mutableStateOf(initialModeSortDirection) }
    var tileSortMode by remember { mutableStateOf(initialTileSortMode) }
    var tileSortDirection by remember { mutableStateOf(initialTileSortDirection) }

    fun resetToDefaults() {
        groupByCategory = true
        groupByMode = true
        categorySortMode = GroupSortMode.MOST_RECENT
        categorySortDirection = Direction.DESC
        modeSortMode = GroupSortMode.MOST_RECENT
        modeSortDirection = Direction.DESC
        tileSortMode = SessionTileSortMode.LAST_ACCESSED
        tileSortDirection = Direction.DESC
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth().heightIn(max = 700.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = getText(R.string.sort_sessions_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    DialogSection(title = getText(R.string.sort_section_categories)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(getText(R.string.group_by_category_label), modifier = Modifier.weight(1f))
                            Switch(checked = groupByCategory, onCheckedChange = { groupByCategory = it })
                        }
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        GroupSortRow(categorySortMode, { categorySortMode = it }, categorySortDirection, { categorySortDirection = it })
                    }
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    DialogSection(title = getText(R.string.sort_section_modes)) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(getText(R.string.group_by_mode_label), modifier = Modifier.weight(1f))
                            Switch(checked = groupByMode, onCheckedChange = { groupByMode = it })
                        }
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        GroupSortRow(modeSortMode, { modeSortMode = it }, modeSortDirection, { modeSortDirection = it })
                    }
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    DialogSection(title = getText(R.string.sort_section_cards)) {
                        TileSortRow(tileSortMode, { tileSortMode = it }, tileSortDirection, { tileSortDirection = it })
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Pinned to the bottom, below the scrollable content — none of these need a
                // confirmation prompt, and nothing above is applied until Save is pressed.
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = { resetToDefaults() }) { Text(getText(R.string.reset)) }
                    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
                        Button(onClick = {
                            onSave(
                                groupByCategory, groupByMode,
                                categorySortMode, categorySortDirection,
                                modeSortMode, modeSortDirection,
                                tileSortMode, tileSortDirection
                            )
                        }) { Text(getText(R.string.save)) }
                    }
                }
            }
        }
    }
}

@Composable
internal fun GroupSortRow(
    mode: GroupSortMode, onModeChange: (GroupSortMode) -> Unit,
    direction: Direction, onDirectionChange: (Direction) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
        ) {
            GroupSortMode.entries.forEach { option ->
                SequencedSelectionChip(
                    selected = mode == option,
                    onClick = { onModeChange(option) },
                    label = { Text(option.asString(), maxLines = 1, softWrap = false) }
                )
            }
        }
        Spacer(Modifier.height(dimensions.spacingSmall))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = direction == Direction.ASC,
                onClick = { onDirectionChange(Direction.ASC) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(getText(R.string.ascending)) }
            SegmentedButton(
                selected = direction == Direction.DESC,
                onClick = { onDirectionChange(Direction.DESC) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(getText(R.string.descending)) }
        }
    }
}

@Composable
internal fun TileSortRow(
    mode: SessionTileSortMode, onModeChange: (SessionTileSortMode) -> Unit,
    direction: Direction, onDirectionChange: (Direction) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Column {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
        ) {
            SessionTileSortMode.entries.forEach { option ->
                SequencedSelectionChip(
                    selected = mode == option,
                    onClick = { onModeChange(option) },
                    label = { Text(option.asString(), maxLines = 1, softWrap = false) }
                )
            }
        }
        Spacer(Modifier.height(dimensions.spacingSmall))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = direction == Direction.ASC,
                onClick = { onDirectionChange(Direction.ASC) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(getText(R.string.ascending)) }
            SegmentedButton(
                selected = direction == Direction.DESC,
                onClick = { onDirectionChange(Direction.DESC) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(getText(R.string.descending)) }
        }
    }
}
