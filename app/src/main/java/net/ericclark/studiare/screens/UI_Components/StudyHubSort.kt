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
import androidx.compose.ui.text.style.TextAlign
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
    val directed = if (direction == Direction.DESC) ordered.reversed() else ordered
    // Pinned sessions always float to the top, ahead of every other sort rule - stable sort, so
    // each pinned/unpinned group keeps the relative order it already has from the mode/direction above.
    return directed.sortedByDescending { it.isPinned }
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
        GroupSortMode.MOST_RECENT -> compareBy { groupedSessions[it.key]?.maxOfOrNull { s -> s.lastAccessed } ?: 0L }
        GroupSortMode.SESSION_COUNT -> compareBy { groupedSessions[it.key]?.size ?: 0 }
    }
    val ordered = sectionsIn.sortedWith(comparator)
    return if (direction == Direction.DESC) ordered.reversed() else ordered
}

/** Serializes the Custom Mode Order dialog's per-category key lists for storage - see [parseModeOrder]. */
internal fun modeOrderToStoredString(order: Map<StudyCategory, List<String>>): String =
    order.entries.joinToString("|") { (category, keys) -> "${category.name}:${keys.joinToString(",")}" }

/**
 * Parses the stored Custom Mode Order string back into a full per-category list of [SessionSection.key]s,
 * one list per category present in [sections]. Falls back to alphabetical-by-title (the dialog's
 * documented default) for any category with nothing stored yet, drops any stale key that no longer
 * matches a real section (e.g. after an app update removes one), and slots any section missing from a
 * stored list (e.g. one added in a later app version) in alphabetically by title among whatever's
 * already there - not always at the end, since an otherwise-untouched (still-alphabetical) stored
 * list should stay fully alphabetical once the gap is patched, rather than jumping its new entry to
 * the back. Storing [SessionSection.key] rather than the localized [SessionSection.title] is what
 * keeps a saved order intact across a locale change.
 */
internal fun parseModeOrder(stored: String?, sections: List<SessionSection>): Map<StudyCategory, List<String>> {
    val titleByKey = sections.associate { it.key to it.title }
    val alphabeticalDefaults = sections.groupBy { it.category }.mapValues { (_, list) -> list.sortedBy { it.title }.map { it.key } }
    val storedByCategory = stored?.takeIf { it.isNotBlank() }?.split("|")?.mapNotNull { entry ->
        val sep = entry.indexOf(":")
        if (sep < 0) return@mapNotNull null
        val category = runCatching { StudyCategory.valueOf(entry.substring(0, sep)) }.getOrNull() ?: return@mapNotNull null
        category to entry.substring(sep + 1).split(",").filter { it.isNotBlank() }
    }?.toMap().orEmpty()

    return alphabeticalDefaults.mapValues { (category, defaultKeys) ->
        val storedKeys = storedByCategory[category] ?: return@mapValues defaultKeys
        val valid = storedKeys.filter { it in defaultKeys }.toMutableList()
        val missing = defaultKeys.filter { it !in valid }
        for (key in missing) {
            val title = titleByKey[key].orEmpty()
            val insertAt = valid.indexOfFirst { (titleByKey[it].orEmpty()) > title }.let { if (it < 0) valid.size else it }
            valid.add(insertAt, key)
        }
        valid
    }
}

/**
 * The Study Hub's app bar sort button — only shown when the deck has active sessions. Used to
 * live behind an overflow menu alongside "Delete all sessions"; both the menu and that action
 * were removed (user-requested — "we will add that back in later"), leaving just this direct
 * button.
 */
@Composable
internal fun StudyHubSortButton(onClick: () -> Unit) {
    TooltipIconButton(description = getText(R.string.sort), onClick = onClick) {
        Icon(Icons.Default.Sort, contentDescription = getText(R.string.sort))
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
    initialUseCustomCategoryOrder: Boolean,
    initialCustomCategoryOrder: List<StudyCategory>,
    initialPresetPosition: PresetPosition,
    initialUseCustomModeOrder: Boolean,
    initialCustomModeOrder: Map<StudyCategory, List<String>>,
    sections: List<SessionSection>,
    onSave: (Boolean, Boolean, GroupSortMode, Direction, GroupSortMode, Direction, SessionTileSortMode, Direction, Boolean, List<StudyCategory>, PresetPosition, Boolean, Map<StudyCategory, List<String>>) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    // Same look as the Study Hub's own mode-section headers (modeHeaderItem, StudyScreens.kt) -
    // titleMedium/bold/primary instead of DialogSection's plain default, so this dialog's section
    // titles read consistently with the screen it's customizing.
    val sectionTitleColor = MaterialTheme.colorScheme.primary
    val sectionTitleWeight = FontWeight.Bold

    var groupByCategory by remember { mutableStateOf(initialGroupByCategory) }
    var groupByMode by remember { mutableStateOf(initialGroupByMode) }
    var categorySortMode by remember { mutableStateOf(initialCategorySortMode) }
    var categorySortDirection by remember { mutableStateOf(initialCategorySortDirection) }
    var modeSortMode by remember { mutableStateOf(initialModeSortMode) }
    var modeSortDirection by remember { mutableStateOf(initialModeSortDirection) }
    var tileSortMode by remember { mutableStateOf(initialTileSortMode) }
    var tileSortDirection by remember { mutableStateOf(initialTileSortDirection) }
    var useCustomCategoryOrder by remember { mutableStateOf(initialUseCustomCategoryOrder) }
    var customCategoryOrder by remember { mutableStateOf(initialCustomCategoryOrder) }
    var presetPosition by remember { mutableStateOf(initialPresetPosition) }
    var useCustomModeOrder by remember { mutableStateOf(initialUseCustomModeOrder) }
    var customModeOrder by remember { mutableStateOf(initialCustomModeOrder) }
    var modeOrderCategory by remember { mutableStateOf(StudyCategory.DEFAULT_ORDER.first()) }
    val sectionsByKey = remember(sections) { sections.associateBy { it.key } }

    fun resetToDefaults() {
        groupByCategory = true
        groupByMode = true
        categorySortMode = GroupSortMode.MOST_RECENT
        categorySortDirection = Direction.DESC
        modeSortMode = GroupSortMode.MOST_RECENT
        modeSortDirection = Direction.DESC
        tileSortMode = SessionTileSortMode.LAST_ACCESSED
        tileSortDirection = Direction.DESC
        useCustomCategoryOrder = false
        customCategoryOrder = StudyCategory.DEFAULT_ORDER
        presetPosition = PresetPosition.FIRST
        useCustomModeOrder = false
        customModeOrder = parseModeOrder(null, sections)
    }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).heightIn(max = 700.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = getText(R.string.sort_sessions_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                // Every section lives in this one scrollable Column - including Preset
                // Position/Custom Category Order, which used to sit above it as fixed, unscrollable
                // content and could squeeze the rest of the sections down to zero height on short
                // screens (or whenever Custom Category Order's reorder list was expanded), leaving
                // Categories/Modes/Sessions unreachable.
                Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                    DialogSection(title = getText(R.string.sort_section_preset_position), titleColor = sectionTitleColor, titleFontWeight = sectionTitleWeight) {
                        ConnectedButtonGroup(
                            items = listOf(PresetPosition.HIDE, PresetPosition.FIRST, PresetPosition.LAST),
                            selected = presetPosition,
                            onSelected = { presetPosition = it },
                            labelFor = { it.asString() },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    DialogSection(title = getText(R.string.sort_section_custom_category_order), titleColor = sectionTitleColor, titleFontWeight = sectionTitleWeight) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(getText(R.string.use_custom_category_order_label), modifier = Modifier.weight(1f))
                            StudiareSwitch(checked = useCustomCategoryOrder, onCheckedChange = { useCustomCategoryOrder = it })
                        }
                        if (useCustomCategoryOrder) {
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            ReorderableList(
                                items = customCategoryOrder,
                                onReorder = { customCategoryOrder = it },
                                key = { it }
                            ) { category ->
                                Text(category.asString(), style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    DialogSection(title = getText(R.string.sort_section_custom_mode_order), titleColor = sectionTitleColor, titleFontWeight = sectionTitleWeight) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(getText(R.string.use_custom_mode_order_label), modifier = Modifier.weight(1f))
                            StudiareSwitch(checked = useCustomModeOrder, onCheckedChange = { useCustomModeOrder = it })
                        }
                        if (useCustomModeOrder) {
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            // Same "pick a chip, edit the list below it" shape as Settings' Mode
                            // Defaults category/mode chip rows.
                            TypedChipRow(
                                items = StudyCategory.DEFAULT_ORDER,
                                selected = modeOrderCategory,
                                labelFor = { it.asString() },
                                onSelected = { modeOrderCategory = it }
                            )
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            val orderedSections = customModeOrder[modeOrderCategory].orEmpty().mapNotNull { sectionsByKey[it] }
                            ReorderableList(
                                items = orderedSections,
                                onReorder = { newOrder -> customModeOrder = customModeOrder + (modeOrderCategory to newOrder.map { it.key }) },
                                key = { it.key }
                            ) { section ->
                                Text(section.title, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                    DialogSection(title = getText(R.string.sort_section_categories), titleColor = sectionTitleColor, titleFontWeight = sectionTitleWeight) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(getText(R.string.group_by_category_label), modifier = Modifier.weight(1f))
                            StudiareSwitch(checked = groupByCategory, onCheckedChange = { groupByCategory = it })
                        }
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        GroupSortRow(categorySortMode, { categorySortMode = it }, categorySortDirection, { categorySortDirection = it })
                    }
                    DialogSection(title = getText(R.string.sort_section_modes), titleColor = sectionTitleColor, titleFontWeight = sectionTitleWeight) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(getText(R.string.group_by_mode_label), modifier = Modifier.weight(1f))
                            StudiareSwitch(checked = groupByMode, onCheckedChange = { groupByMode = it })
                        }
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        GroupSortRow(modeSortMode, { modeSortMode = it }, modeSortDirection, { modeSortDirection = it })
                    }
                    // No divider here - the dialog's own HorizontalDivider right below the
                    // scrollable content already separates the last section from the button row.
                    DialogSection(title = getText(R.string.sort_section_cards), showDivider = false, titleColor = sectionTitleColor, titleFontWeight = sectionTitleWeight) {
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
                                tileSortMode, tileSortDirection,
                                useCustomCategoryOrder, customCategoryOrder,
                                presetPosition,
                                useCustomModeOrder, customModeOrder
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
        // 3 options: a ConnectedButtonGroup forces an even 1/3 split regardless of label length,
        // which truncates the longest one on narrow phones. TypedChipGrid sizes each chip to its
        // own label and wraps to a new line instead.
        TypedChipGrid(
            items = GroupSortMode.entries,
            selected = mode,
            labelFor = { it.asString() },
            onSelected = onModeChange
        )
        Spacer(Modifier.height(dimensions.spacingSmall))
        ConnectedButtonGroup(
            items = listOf(Direction.ASC, Direction.DESC),
            selected = direction,
            onSelected = onDirectionChange,
            labelFor = { if (it == Direction.ASC) getText(R.string.ascending) else getText(R.string.descending) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
internal fun TileSortRow(
    mode: SessionTileSortMode, onModeChange: (SessionTileSortMode) -> Unit,
    direction: Direction, onDirectionChange: (Direction) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Column {
        TypedChipGrid(
            items = SessionTileSortMode.entries,
            selected = mode,
            labelFor = { it.asString() },
            onSelected = onModeChange
        )
        Spacer(Modifier.height(dimensions.spacingSmall))
        ConnectedButtonGroup(
            items = listOf(Direction.ASC, Direction.DESC),
            selected = direction,
            onSelected = onDirectionChange,
            labelFor = { if (it == Direction.ASC) getText(R.string.ascending) else getText(R.string.descending) },
            modifier = Modifier.fillMaxWidth()
        )
    }
}
