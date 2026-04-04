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
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import kotlin.collections.forEach
import net.ericclark.studiare.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.animation.AnimatedVisibility

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

            val interactionSource = remember { MutableInteractionSource() }
            val isPressed by interactionSource.collectIsPressedAsState()
            val scale by animateFloatAsState(
                targetValue = if (isPressed) 0.85f else 1f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                label = "colorSquish"
            )

            Box(
                modifier = Modifier
                    .size(40.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(parsedColor)
                    .border(
                        width = if (isSelected) 3.dp else 1.dp,
                        color = if (isSelected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable(
                        interactionSource = interactionSource,
                        indication = LocalIndication.current
                    ) { onColorSelected(hexColor) }
            ) {
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = getText(R.string.selected_label),
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
    tag: TagDefinition?,
    existingTags: List<TagDefinition>,
    onDismiss: () -> Unit,
    onSave: (name: String, color: String) -> Unit
) {
    val context = LocalContext.current
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
                    text = if (tag == null) getText(R.string.tag_create_new) else getText(R.string.tag_edit),
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        errorText = null
                    },
                    label = { Text(getText(R.string.tag_name)) },
                    singleLine = true,
                    isError = errorText != null,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                )
                AnimatedVisibility(
                    visible = errorText != null,
                    enter = slideInVertically() + fadeIn() + expandVertically(),
                    exit = slideOutVertically() + fadeOut() + shrinkVertically()
                ) {
                    Text(
                        text = errorText ?: "",
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
                        label = { Text(getText(R.string.hex_color)) },
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
                Text(getText(R.string.presets), style = MaterialTheme.typography.titleSmall)
                SimpleColorPicker(selectedColor = color, onColorSelected = { color = it })

                Spacer(Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val cancelInteractionSource = remember { MutableInteractionSource() }
                    val isCancelPressed by cancelInteractionSource.collectIsPressedAsState()
                    val cancelScale by animateFloatAsState(targetValue = if (isCancelPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "cancelSquish")
                    TextButton(onClick = onDismiss, interactionSource = cancelInteractionSource, modifier = Modifier.scale(cancelScale)) { Text(getText(R.string.cancel)) }

                    Spacer(Modifier.width(dimensions.spacingSmall))

                    val saveInteractionSource = remember { MutableInteractionSource() }
                    val isSavePressed by saveInteractionSource.collectIsPressedAsState()
                    val saveScale by animateFloatAsState(targetValue = if (isSavePressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "saveSquish")
                    Button(
                        interactionSource = saveInteractionSource,
                        modifier = Modifier.scale(saveScale),
                        onClick = {
                            val trimmedName = name.trim()
                            val isValidHex = try {
                            AndroidColor.parseColor(color)
                            true
                        } catch (e: Exception) { false }

                        if (trimmedName.isEmpty()) {
                            errorText = getText(context, R.string.tag_error_empty)
                        } else if (!isValidHex) {
                            errorText = getText(context, R.string.tag_error_hex)
                        } else if (tag == null && existingTags.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                            errorText = getText(context, R.string.tag_error_exists)
                        } else if (tag != null && !trimmedName.equals(tag.name, ignoreCase = true) && existingTags.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                            errorText = getText(context, R.string.tag_error_name)
                        } else {
                            onSave(trimmedName, color)
                        }
                    }) {
                        Text(getText(R.string.save))
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
    var decksWithTaggedCards by remember { mutableStateOf<List<DeckWithCards>?>(null) }
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
                    text = getText(R.string.cards_manage) + ": $tagName",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    text = getText(R.string.tag_remove_from_cards),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                AnimatedContent(
                    targetState = when {
                        decksWithTaggedCards == null -> 0
                        decksWithTaggedCards!!.isEmpty() -> 1
                        else -> 2
                    },
                    transitionSpec = {
                        (slideInVertically() + fadeIn() + expandVertically()).togetherWith(
                            slideOutVertically() + fadeOut() + shrinkVertically()
                        )
                    },
                    label = "tagCleanupTransition",
                    modifier = Modifier.weight(1f, fill = false)
                ) { targetState ->
                    if (targetState == 0) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    } else if (targetState == 1) {
                        Box(Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                            Text(getText(R.string.tag_no_cards), fontStyle = FontStyle.Italic)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
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
                                    val rowInteractionSource = remember { MutableInteractionSource() }
                                    val isRowPressed by rowInteractionSource.collectIsPressedAsState()
                                    val rowScale by animateFloatAsState(targetValue = if (isRowPressed) 0.98f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "cardRowSquish")

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .scale(rowScale)
                                            .clickable(
                                                interactionSource = rowInteractionSource,
                                                indication = LocalIndication.current
                                            ) {
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
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    val closeInteractionSource = remember { MutableInteractionSource() }
                    val isClosePressed by closeInteractionSource.collectIsPressedAsState()
                    val closeScale by animateFloatAsState(targetValue = if (isClosePressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "closeSquish")
                    TextButton(onClick = onDismiss, interactionSource = closeInteractionSource, modifier = Modifier.scale(closeScale)) { Text(getText(R.string.close)) }

                    Spacer(Modifier.width(dimensions.spacingSmall))

                    val removeInteractionSource = remember { MutableInteractionSource() }
                    val isRemovePressed by removeInteractionSource.collectIsPressedAsState()
                    val removeScale by animateFloatAsState(targetValue = if (isRemovePressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "removeSquish")
                    Button(
                        onClick = {
                            viewModel.removeTagFromCards(tagName, selectedIdsToRemove.toList())
                            onDismiss()
                        },
                        interactionSource = removeInteractionSource,
                        modifier = Modifier.scale(removeScale),
                        enabled = selectedIdsToRemove.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(pluralStringResource(R.plurals.tags_remove, selectedIdsToRemove.size))
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

    val chipInteractionSource = remember { MutableInteractionSource() }
    val isChipPressed by chipInteractionSource.collectIsPressedAsState()
    val chipScale by animateFloatAsState(targetValue = if (isChipPressed && onClick != null) 0.9f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "chipSquish")

    Surface(
        color = tagColor,
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // Expressive Squircle
        modifier = modifier
            .padding(end = dimensions.spacingSmall)
            .scale(chipScale)
            .then(if (onClick != null) Modifier.clickable(interactionSource = chipInteractionSource, indication = LocalIndication.current) { onClick() } else Modifier)
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
                val deleteInteractionSource = remember { MutableInteractionSource() }
                val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
                val deleteScale by animateFloatAsState(
                    targetValue = if (isDeletePressed) 0.8f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "deleteSquish"
                )
                Icon(
                    imageVector = Icons.Default.Clear,
                    contentDescription = getText(R.string.remove),
                    tint = if (tagColor.luminance() > 0.5f) Color.Black else Color.White,
                    modifier = Modifier
                        .size(16.dp)
                        .scale(deleteScale)
                        .clickable(
                            interactionSource = deleteInteractionSource,
                            indication = LocalIndication.current
                        ) { onDelete() }
                )
            }
        }
    }
}

// --- REUSABLE COMPONENT: CardTagRow ---
@Composable
fun CardTagRow(
    cardTags: List<String>,
    allTags: List<TagDefinition>,
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
        val addInteractionSource = remember { MutableInteractionSource() }
        val isAddPressed by addInteractionSource.collectIsPressedAsState()
        val addScale by animateFloatAsState(targetValue = if (isAddPressed) 0.85f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "addTagSquish")

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.scale(addScale).clickable(interactionSource = addInteractionSource, indication = LocalIndication.current) { showAddDialog = true }
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = 6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = getText(R.string.tag_add),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = getText(R.string.tags),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
fun TagSelectionDialog(
    currentlyOnCard: Set<String>,
    allTags: List<TagDefinition>,
    currentDeckTags: Set<String>,
    onDismiss: () -> Unit,
    onSave: (Set<String>) -> Unit,
    onCreateTag: (String, String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val selectedTags = remember { mutableStateListOf(*currentlyOnCard.toTypedArray()) }
    val locallyCreatedTags = remember { mutableStateListOf<TagDefinition>() }

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
                    text = getText(R.string.tags_manage),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = dimensions.spacingMedium)
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    @Composable
                    fun TagItem(tag: TagDefinition) {
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
                            getText(R.string.tags_in_this_deck),
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
                            getText(R.string.tags_from_other_decks),
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
                            getText(R.string.tags_none_found),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(bottom = dimensions.spacingMedium)
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                Text(
                    getText(R.string.tag_create_new),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                )

                OutlinedTextField(
                    value = newTagName,
                    onValueChange = { newTagName = it },
                    label = { Text(getText(R.string.tag_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                )

                Spacer(Modifier.height(dimensions.spacingSmall))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newTagColor,
                        onValueChange = { newTagColor = it },
                        label = { Text(getText(R.string.hex_color)) },
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
                    val createInteractionSource = remember { MutableInteractionSource() }
                    val isCreatePressed by createInteractionSource.collectIsPressedAsState()
                    val createScale by animateFloatAsState(targetValue = if (isCreatePressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "createTagSquish")
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
                        interactionSource = createInteractionSource,
                        modifier = Modifier.scale(createScale),
                        enabled = isCreateEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text(getText(R.string.create))
                    }

                    Row {
                        val cancelSelInteractionSource = remember { MutableInteractionSource() }
                        val isCancelSelPressed by cancelSelInteractionSource.collectIsPressedAsState()
                        val cancelSelScale by animateFloatAsState(targetValue = if (isCancelSelPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "cancelSelSquish")
                        TextButton(onClick = onDismiss, interactionSource = cancelSelInteractionSource, modifier = Modifier.scale(cancelSelScale)) { Text(getText(R.string.cancel)) }

                        Spacer(Modifier.width(dimensions.spacingSmall))

                        val saveSelInteractionSource = remember { MutableInteractionSource() }
                        val isSaveSelPressed by saveSelInteractionSource.collectIsPressedAsState()
                        val saveSelScale by animateFloatAsState(targetValue = if (isSaveSelPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "saveSelSquish")
                        Button(
                            onClick = { onSave(selectedTags.toSet()) },
                            interactionSource = saveSelInteractionSource,
                            modifier = Modifier.scale(saveSelScale),
                            enabled = isSaveEnabled || selectedTags.isNotEmpty()
                        ) {
                            Text(getText(R.string.save))
                        }
                    }
                }
            }
        }
    }
}