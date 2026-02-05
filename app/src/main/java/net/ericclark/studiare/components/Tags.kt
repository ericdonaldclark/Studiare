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
import androidx.compose.material3.CardDefaults
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
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
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
    val dimensions = LocalStudiareDimensions.current
    // UPDATED: Palette using Hex Strings
    val colors = listOf(
        "#B71C1C", "#880E4F", "#4A148C", "#311B92", "#1A237E", "#0D47A1", "#01579B", "#006064", "#004D40", "#1B5E20",
        "#33691E", "#827717", "#F57F17", "#FF6F00", "#E65100", "#BF360C", "#3E2723", "#212121", "#263238", "#FFFFFF",
        "#000000"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(vertical = dimensions.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
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
    val dimensions = LocalStudiareDimensions.current
    var name by remember { mutableStateOf(tag?.name ?: "") }
    var color by remember { mutableStateOf(tag?.color ?: "#0D47A1") }
    var errorText by remember { mutableStateOf<String?>(null) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    text = if (tag == null) "Create New Tag" else "Edit Tag",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                )
                if (errorText != null) {
                    Text(
                        text = errorText!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(start = dimensions.paddingMedium, top = 4.dp)
                    )
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                // UPDATED: Hex Code Input
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = color,
                        onValueChange = { color = it },
                        label = { Text("Hex Color") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
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

                Spacer(Modifier.height(dimensions.spacingSmall))
                Text("Presets", style = MaterialTheme.typography.titleSmall)
                SimpleColorPicker(selectedColor = color, onColorSelected = { color = it })

                Spacer(Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(onClick = {
                        val trimmedName = name.trim()
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
    val dimensions = LocalStudiareDimensions.current
    var decksWithTaggedCards by remember { mutableStateOf<List<net.ericclark.studiare.data.DeckWithCards>?>(null) }
    val selectedIdsToRemove = remember { mutableStateListOf<String>() }

    LaunchedEffect(tagName) {
        decksWithTaggedCards = viewModel.getCardsForTag(tagName).sortedBy { it.deck.name.lowercase() }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    text = "Manage Cards: $tagName",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = "Select cards to remove this tag from.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                if (decksWithTaggedCards == null) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (decksWithTaggedCards!!.isEmpty()) {
                    Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        Text("No cards have this tag.", fontStyle = FontStyle.Italic)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .border(
                                1.dp,
                                MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(dimensions.cornerRadiusSmall)
                            )
                            .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                    ) {
                        decksWithTaggedCards!!.forEach { deckGroup ->
                            item {
                                Surface(color = MaterialTheme.colorScheme.surfaceVariant) {
                                    Text(
                                        text = deckGroup.deck.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
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
                                        .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = selectedIdsToRemove.contains(card.id),
                                        onCheckedChange = {
                                            if (it) selectedIdsToRemove.add(card.id) else selectedIdsToRemove.remove(card.id)
                                        }
                                    )
                                    Spacer(Modifier.width(dimensions.spacingSmall))
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

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Close") }
                    Spacer(Modifier.width(dimensions.spacingSmall))
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
    val dimensions = LocalStudiareDimensions.current
    val tagColor = parseHexColor(colorHex)

    val displayText = if (text.length > 16) {
        text.take(13) + "..."
    } else {
        text
    }

    Surface(
        color = tagColor,
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // Expressive Squircle
        modifier = modifier
            .padding(end = dimensions.spacingSmall)
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = 6.dp)
        ) {
            Text(
                text = displayText,
                color = if (tagColor.luminance() > 0.5f) Color.Black else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (onDelete != null) {
                Spacer(Modifier.width(dimensions.spacingSmall))
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

// --- REUSABLE COMPONENT: CardTagRow ---
@Composable
fun CardTagRow(
    cardTags: List<String>,
    allTags: List<net.ericclark.studiare.data.TagDefinition>,
    currentDeckTags: Set<String>,
    onUpdateTags: (Set<String>) -> Unit,
    onCreateTag: (String, String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var showAddDialog by remember { mutableStateOf(false) }

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
            .padding(vertical = dimensions.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        tagsToDisplay.forEach { tagDef ->
            TagChip(
                text = tagDef.name,
                colorHex = tagDef.color,
                onDelete = { onUpdateTags(cardTags.toSet() - tagDef.name) }
            )
        }

        // Plus Button (Pill Shape)
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.clickable { showAddDialog = true }
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = 6.dp)
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
    onSave: (Set<String>) -> Unit,
    onCreateTag: (String, String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val selectedTags = remember { mutableStateListOf(*currentlyOnCard.toTypedArray()) }
    val locallyCreatedTags = remember { mutableStateListOf<net.ericclark.studiare.data.TagDefinition>() }

    val combinedTags = remember(allTags, locallyCreatedTags.toList()) {
        (allTags + locallyCreatedTags).distinctBy { it.name }
    }

    val thisDeckTags = combinedTags.filter { it.name in currentDeckTags }.sortedBy { it.name.lowercase() }
    val otherDeckTags = combinedTags.filter { it.name !in currentDeckTags }.sortedBy { it.name.lowercase() }

    var newTagName by remember { mutableStateOf("") }
    var newTagColor by remember { mutableStateOf("#0D47A1") }

    val isCreateEnabled = newTagName.isNotBlank()
    val isSaveEnabled = remember(selectedTags.toList()) {
        val currentSet = selectedTags.toSet()
        currentSet != currentlyOnCard
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 700.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    text = "Manage Tags",
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = dimensions.spacingMedium)
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    @Composable
                    fun TagItem(tag: net.ericclark.studiare.data.TagDefinition) {
                        val isSelected = tag.name in selectedTags
                        TagChip(
                            text = tag.name,
                            colorHex = tag.color,
                            onDelete = if (isSelected) { { selectedTags.remove(tag.name) } } else null,
                            onClick = if (!isSelected) { { selectedTags.add(tag.name) } } else null,
                            modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                        )
                    }

                    if (thisDeckTags.isNotEmpty()) {
                        Text(
                            "In This Deck",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = dimensions.spacingMedium)
                        ) {
                            thisDeckTags.forEach { TagItem(it) }
                        }
                    }

                    if (otherDeckTags.isNotEmpty()) {
                        Text(
                            "From Other Decks",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(bottom = dimensions.spacingMedium)
                        ) {
                            otherDeckTags.forEach { TagItem(it) }
                        }
                    }

                    if (thisDeckTags.isEmpty() && otherDeckTags.isEmpty()) {
                        Text(
                            "No tags found.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(bottom = dimensions.spacingMedium)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                Text(
                    "Create New Tag",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                )

                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text("Tag Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                )

                Spacer(Modifier.height(dimensions.spacingSmall))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagColor,
                        onValueChange = { newTagColor = it },
                        label = { Text("Hex Color") },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
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

                Spacer(Modifier.height(dimensions.spacingSmall))
                SimpleColorPicker(
                    selectedColor = newTagColor,
                    onColorSelected = { newTagColor = it }
                )

                Spacer(Modifier.height(dimensions.spacingMedium))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = {
                            if (isCreateEnabled) {
                                val name = newTagName.trim()
                                onCreateTag(name, newTagColor)
                                locallyCreatedTags.add(TagDefinition(name = name, color = newTagColor))
                                if (name !in selectedTags) {
                                    selectedTags.add(name)
                                }
                                newTagName = ""
                                newTagColor = "#0D47A1"
                            }
                        },
                        enabled = isCreateEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Create")
                    }

                    Row {
                        TextButton(onClick = onDismiss) { Text("Cancel") }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Button(
                            onClick = { onSave(selectedTags.toSet()) },
                            enabled = isSaveEnabled || selectedTags.isNotEmpty()
                        ) {
                            Text("Save")
                        }
                    }
                }
            }
        }
    }
}