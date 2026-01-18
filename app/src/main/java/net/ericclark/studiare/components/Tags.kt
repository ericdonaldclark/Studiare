package net.ericclark.studiare.components

import android.graphics.Color as AndroidColor
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.data.*
import kotlin.collections.forEach

fun parseHexColor(hex: String): Color {
    return try {
        Color(AndroidColor.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray // Fallback
    }
}

@Composable
fun SimpleColorPicker(
    selectedColor: String,
    onColorSelected: (String) -> Unit
) {
    // UPDATED: Palette using Hex Strings
    // https://htmlcolorcodes.com/color-chart/material-design-color-chart/
    val colors = listOf(
        /*
            // Red
            "#FFEBEE", "#FFCDD2", "#EF9A9A", "#E57373", "#EF5350", "#F44336", "#E53935", "#D32F2F", "#C62828", "#B71C1C",
            // Pink
            "#FCE4EC", "#F8BBD0", "#F48FB1", "#F06292", "#EC407A", "#E91E63", "#D81B60", "#C2185B", "#AD1457", "#880E4F",
            // Purple
            "#F3E5F5", "#E1BEE7", "#CE93D8", "#BA68C8", "#AB47BC", "#9C27B0", "#8E24AA", "#7B1FA2", "#6A1B9A", "#4A148C",
            // Deep Purple
            "#EDE7F6", "#D1C4E9", "#B39DDB", "#9575CD", "#7E57C2", "#673AB7", "#5E35B1", "#512DA8", "#4527A0", "#311B92",
            // Indigo
            "#E8EAF6", "#C5CAE9", "#9FA8DA", "#7986CB", "#5C6BC0", "#3F51B5", "#3949AB", "#303F9F", "#283593", "#1A237E"
        */
        "#B71C1C", "#880E4F", "#4A148C", "#311B92", "#1A237E", "#0D47A1", "#01579B", "#006064", "#004D40", "#1B5E20",
        "#33691E", "#827717", "#F57F17", "#FF6F00", "#E65100", "#BF360C", "#3E2723", "#212121", "#263238", "#FFFFFF",
        "#000000"

    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        colors.forEach { hexColor ->
            val isSelected = hexColor.equals(selectedColor, ignoreCase = true)
            val parsedColor = parseHexColor(hexColor)

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(parsedColor)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onColorSelected(hexColor) }
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "Selected",
                        tint = if (parsedColor.luminance() > 0.5f) Color.Black else Color.White,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
        }
    }
}

@Composable
fun TagEditorDialog(
    tag: net.ericclark.studiare.data.TagDefinition?,
    existingTags: List<net.ericclark.studiare.data.TagDefinition>,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String) -> Unit
) {
    var name by remember { mutableStateOf(tag?.name ?: "") }
    // UPDATED: State uses String
    var color by remember { mutableStateOf(tag?.color ?: "#0D47A1") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = if (tag == null) "Create New Tag" else "Edit Tag",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }

                Spacer(Modifier.height(16.dp))

                // UPDATED: Hex Code Input
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = color,
                        onValueChange = { color = it },
                        label = { Text("Hex Color") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(color))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                Text("Presets", style = MaterialTheme.typography.titleSmall)
                SimpleColorPicker(selectedColor = color, onColorSelected = { color = it })

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        val trimmedName = name.trim()
                        // Validate Hex format
                        val isValidHex = try {
                            AndroidColor.parseColor(color)
                            true
                        } catch (e: Exception) { false }

                        if (trimmedName.isEmpty()) {
                            errorText = "Name cannot be empty"
                        } else if (!isValidHex) {
                            errorText = "Invalid Hex Color (e.g. #FF0000)"
                        } else if (tag == null && existingTags.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                            errorText = "Tag already exists"
                        } else if (tag != null && !trimmedName.equals(tag.name, ignoreCase = true) && existingTags.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                            // UPDATED CONDITION:
                            // We only flag a duplicate if the new name is DIFFERENT from the old name (ignoring case)
                            // AND it matches another existing tag.
                            // This allows renaming "Example" -> "example".
                            errorText = "Name already taken"
                        } else {
                            onSave(trimmedName, color)
                        }
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}

@Composable
fun TagCleanupDialog(
    tagName: String,
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    onDismiss: () -> Unit
) {
    // State to hold the fetched cards
    var decksWithTaggedCards by remember { mutableStateOf<List<net.ericclark.studiare.data.DeckWithCards>?>(null) }
    val selectedIdsToRemove = remember { mutableStateListOf<String>() }

    // Fetch data on launch
    LaunchedEffect(tagName) {
        // This function needs to be accessed from ViewModel
        decksWithTaggedCards = viewModel.getCardsForTag(tagName).sortedBy { it.deck.name.lowercase() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Manage Cards: $tagName",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Select cards to remove this tag from.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))

                if (decksWithTaggedCards == null) {
                    Box(Modifier
                        .fillMaxWidth()
                        .height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (decksWithTaggedCards!!.isEmpty()) {
                    Box(Modifier
                        .fillMaxWidth()
                        .height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No cards have this tag.", fontStyle = FontStyle.Italic)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(8.dp)
                            )
                    ) {
                        decksWithTaggedCards!!.forEach { deckGroup ->
                            item {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(
                                        text = deckGroup.deck.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 8.dp),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            items(deckGroup.cards) { card ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (selectedIdsToRemove.contains(card.id)) {
                                                selectedIdsToRemove.remove(card.id)
                                            } else {
                                                selectedIdsToRemove.add(card.id)
                                            }
                                        }
                                        .padding(horizontal = 16.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedIdsToRemove.contains(card.id),
                                        onCheckedChange = {
                                            if (it) selectedIdsToRemove.add(card.id) else selectedIdsToRemove.remove(card.id)
                                        }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = card.front,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1
                                        )
                                        Text(
                                            text = card.back,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
                            }
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = {
                            viewModel.removeTagFromCards(tagName, selectedIdsToRemove.toList())
                            onDismiss()
                        },
                        enabled = selectedIdsToRemove.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Remove Tag (${selectedIdsToRemove.size})")
                    }
                }
            }
        }
    }
}

// --- REUSABLE COMPONENT: Tag Chip ---
@Composable
fun TagChip(
    text: String,
    colorHex: String,
    onDelete: (() -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val tagColor = parseHexColor(colorHex)

    // Truncation Logic: If > 16 chars, take first 13 and add "..."
    val displayText = if (text.length > 16) {
        text.take(13) + "..."
    } else {
        text
    }

    Surface(
        color = tagColor,
        shape = RoundedCornerShape(50),
        modifier = modifier
            .padding(end = 8.dp)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                text = displayText,
                // Calculate contrast
                color = if (tagColor.luminance() > 0.5f) Color.Black else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (onDelete != null) {
                Spacer(Modifier.width(4.dp))
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = "Remove",
                    tint = if (tagColor.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onDelete() }
                )
            }
        }
    }
}

// --- NEW: Card Editor Components ---

// --- REUSABLE COMPONENT: CardTagRow ---
@Composable
fun CardTagRow(
    cardTags: List<String>,        // Tags currently on this specific card
    allTags: List<net.ericclark.studiare.data.TagDefinition>,  // All tags defined in the app (from ViewModel)
    currentDeckTags: Set<String>,  // Names of tags used by ANY card in the current deck
    onUpdateTags: (Set<String>) -> Unit, // CHANGED: Single callback for final state
    onCreateTag: (String, String) -> Unit // Name, HexColor
) {
    var showAddDialog by remember { mutableStateOf(false) }

    // Map tag names to actual definitions for display (color)
    val tagsToDisplay = cardTags.sortedBy { it.lowercase()}.mapNotNull { name ->
        allTags.find { it.name == name }
    }

    if (showAddDialog) {
        TagSelectionDialog(
            currentlyOnCard = cardTags.toSet(),
            allTags = allTags,
            currentDeckTags = currentDeckTags,
            onDismiss = { showAddDialog = false },
            onSave = { newTagSet ->
                onUpdateTags(newTagSet)
                showAddDialog = false
            },
            onCreateTag = onCreateTag
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Display existing tags
        tagsToDisplay.forEach { tagDef ->
            TagChip(
                text = tagDef.name,
                colorHex = tagDef.color,
                // We use onUpdateTags to remove, creating a new set minus this tag
                onDelete = { onUpdateTags(cardTags.toSet() - tagDef.name) }
            )
        }

        // Plus Button (Pill Shape)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(50),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.clickable { showAddDialog = true }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Add Tag",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun TagSelectionDialog(
    currentlyOnCard: Set<String>,
    allTags: List<net.ericclark.studiare.data.TagDefinition>,
    currentDeckTags: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit, // CHANGED: Returns the final set of tags to apply
    onCreateTag: (String, String) -> Unit
) {
    // Local state for the selection
    val selectedTags = remember { mutableStateListOf(*currentlyOnCard.toTypedArray()) }

    // Track locally created tags so they appear immediately in the list even if DB is slow
    val locallyCreatedTags = remember { mutableStateListOf<net.ericclark.studiare.data.TagDefinition>() }

    // Combine existing tags with locally created ones
    val combinedTags = remember(allTags, locallyCreatedTags.toList()) {
        (allTags + locallyCreatedTags).distinctBy { it.name }
    }

    // 1. Tags in THIS deck
    val thisDeckTags = combinedTags.filter { it.name in currentDeckTags }.sortedBy { it.name.lowercase() }

    // 2. Tags from OTHER decks
    val otherDeckTags = combinedTags.filter { it.name !in currentDeckTags }.sortedBy { it.name.lowercase() }

    // State for "Create Tag" section
    var newTagName by remember { mutableStateOf("") }
    var newTagColor by remember { mutableStateOf("#0D47A1") } // Default Blue

    // Validation for "Create" button
    val isCreateEnabled = newTagName.isNotBlank()

    // Validation for "Save" button (enabled if selection changed OR new tag waiting to be saved?)
    // Actually, prompt says "Save should only be enabled when either a new tag has been added or a card has [changed]"
    // We'll interpret this as: Enabled if the selected set is different from the original set.
    val isSaveEnabled = remember(selectedTags.toList()) {
        val currentSet = selectedTags.toSet()
        currentSet != currentlyOnCard
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Manage Tags",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // SCROLLABLE CONTENT (Sections 1 & 2)
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Helper to render a tag item
                    @Composable
                    fun TagItem(tag: net.ericclark.studiare.data.TagDefinition) {
                        val isSelected = tag.name in selectedTags

                        // If selected: Show filled color with X (to remove)
                        // If NOT selected: Show filled color with click to add
                        // Note: The prompt asked for "X in the pill" when selected.
                        // Our TagChip has onDelete which renders an X.

                        TagChip(
                            text = tag.name,
                            colorHex = tag.color,
                            // If selected, clicking X removes it
                            onDelete = if (isSelected) { { selectedTags.remove(tag.name) } } else null,
                            // If NOT selected, clicking body adds it
                            onClick = if (!isSelected) { { selectedTags.add(tag.name) } } else null,
                            // Add some visual distinction for unselected tags if desired,
                            // though the prompt implies just the X presence indicates selection.
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    // SECTION 1: Tags in This Deck
                    if (thisDeckTags.isNotEmpty()) {
                        Text(
                            "In This Deck",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 16.dp)
                        ) {
                            thisDeckTags.sortedBy { it.name.lowercase() }.forEach { TagItem(it) }
                        }
                    }

                    // SECTION 2: Tags from Other Decks
                    if (otherDeckTags.isNotEmpty()) {
                        Text(
                            "From Other Decks",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = 16.dp)
                        ) {
                            otherDeckTags.sortedBy { it.name.lowercase() }.forEach { TagItem(it) }
                        }
                    }

                    if (thisDeckTags.isEmpty() && otherDeckTags.isEmpty()) {
                        Text(
                            "No tags found.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(bottom = 16.dp)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                // SECTION 3: Create Tag Inputs
                Text(
                    "Create New Tag",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )

                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(8.dp))

                // UPDATED: Hex Color Input (Like Settings Page)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagColor,
                        onValueChange = { newTagColor = it },
                        label = { Text("Hex Color") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(parseHexColor(newTagColor))
                                    .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                            )
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                SimpleColorPicker(
                    selectedColor = newTagColor,
                    onColorSelected = { newTagColor = it }
                )

                Spacer(Modifier.height(16.dp))

                // BOTTOM BUTTONS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Create Button (Bottom Left)
                    Button(
                        onClick = {
                            if (isCreateEnabled) {
                                val name = newTagName.trim()
                                // 1. Fire callback to save definition to DB
                                onCreateTag(name, newTagColor)
                                // 2. Add to local list so it shows in UI immediately
                                locallyCreatedTags.add(
                                    TagDefinition(
                                        name = name,
                                        color = newTagColor
                                    )
                                )
                                // 3. Auto-select the new tag
                                if (name !in selectedTags) {
                                    selectedTags.add(name)
                                }
                                // 4. Clear inputs, but DO NOT close dialog
                                newTagName = ""
                                // Keep color or reset? Usually nicer to keep if creating similar tags,
                                // but we'll reset to default blue for cleanliness.
                                newTagColor = "#0D47A1"
                            }
                        },
                        enabled = isCreateEnabled,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary
                        )
                    ) {
                        Text("Create")
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(8.dp))
                        // Save Button (Bottom Right)
                        Button(
                            onClick = { onSave(selectedTags.toSet()) },
                            enabled = isSaveEnabled || selectedTags.isNotEmpty() // Allow saving if user just wants to confirm the current state?
                            // Strict interpretation: "Save should only be enabled when either a new tag has been added [to selection] or a card has [changed tags]"
                            // But usually, if they just created a tag and want to save, `isSaveEnabled` checks `selectedTags != currentlyOnCard`.
                            // If they created a tag and it auto-added to `selectedTags`, then `isSaveEnabled` is true.
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}