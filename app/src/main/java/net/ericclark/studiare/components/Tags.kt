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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.TooltipFilledTonalIconButton
import net.ericclark.studiare.TooltipIconButton
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import kotlin.collections.forEach
import net.ericclark.studiare.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.draw.clip
import androidx.compose.animation.AnimatedVisibility
import net.ericclark.studiare.ui.theme.pressSquish

fun parseHexColor(hex: String): Color {
    return try {
        Color(AndroidColor.parseColor(hex))
    } catch (e: Exception) {
        Color.Gray // Fallback
    }
}

/** One circular, 40dp color swatch — a preset-palette entry, a tag color, or a fixed "frozen preset" indicator. */
@Composable
fun ColorSwatch(hexColor: String, isSelected: Boolean, onClick: () -> Unit) {
    val parsedColor = parseHexColor(hexColor)
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = Modifier
            .size(40.dp)
            .pressSquish(interactionSource, 0.85f)
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
            ) { onClick() }
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

@Composable
fun SimpleColorPicker(
    selectedColor: String,
    onColorSelected: (String) -> Unit,
    modifier: Modifier = Modifier.fillMaxWidth()
) {
    val dimensions = LocalStudiareDimensions.current
    // UPDATED: Palette using Hex Strings
    val colors = listOf(
        "#B71C1C", "#880E4F", "#4A148C", "#311B92", "#1A237E", "#0D47A1", "#01579B", "#006064", "#004D40", "#1B5E20",
        "#33691E", "#827717", "#F57F17", "#FF6F00", "#E65100", "#BF360C", "#3E2723", "#212121", "#263238", "#FFFFFF",
        "#000000"
    )

    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(vertical = dimensions.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
    ) {
        colors.forEach { hexColor ->
            ColorSwatch(
                hexColor = hexColor,
                isSelected = hexColor.equals(selectedColor, ignoreCase = true),
                onClick = { onColorSelected(hexColor) }
            )
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

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp)
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
                        style = MaterialTheme.typography.bodyMedium,
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
                    TextButton(onClick = onDismiss, interactionSource = cancelInteractionSource, modifier = Modifier.pressSquish(cancelInteractionSource, 0.95f), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }

                    Spacer(Modifier.width(dimensions.spacingSmall))

                    val saveInteractionSource = remember { MutableInteractionSource() }
                    Button(
                        interactionSource = saveInteractionSource,
                        modifier = Modifier.pressSquish(saveInteractionSource, 0.95f),
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
                    },
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
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

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            modifier = Modifier
                .widthIn(min = 280.dp, max = 560.dp)
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
                if (selectedIdsToRemove.isNotEmpty()) {
                    Text(
                        "${selectedIdsToRemove.size} selected",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
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
                            CircularWavyProgressIndicator()
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

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .pressSquish(rowInteractionSource, 0.98f)
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
                                                style = MaterialTheme.typography.bodyMedium,
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
                    TextButton(onClick = onDismiss, interactionSource = closeInteractionSource, modifier = Modifier.pressSquish(closeInteractionSource, 0.95f), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.close)) }

                    Spacer(Modifier.width(dimensions.spacingSmall))

                    val removeInteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = {
                            viewModel.removeTagFromCards(tagName, selectedIdsToRemove.toList())
                            onDismiss()
                        },
                        interactionSource = removeInteractionSource,
                        modifier = Modifier.pressSquish(removeInteractionSource, 0.95f),
                        enabled = selectedIdsToRemove.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
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
    /** Non-interactive icon shown after the text when [onDelete] is null — used to mark the one
     * chip currently open in [TagSelectionDialog]'s edit mode. */
    trailingIcon: androidx.compose.ui.graphics.vector.ImageVector? = null,
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
    val chipShape = RoundedCornerShape(dimensions.cornerRadiusMedium)

    Surface(
        color = tagColor,
        shape = chipShape, // Expressive Squircle
        modifier = modifier
            .padding(end = dimensions.spacingSmall)
            // Explicit clip before the clickable below, so its ripple is bounded to the chip's
            // rounded shape instead of the square it defaults to when the shape only comes from
            // Surface's own (differently-ordered) internal clip.
            .clip(chipShape)
            .pressSquish(chipInteractionSource, 0.9f)
            .then(if (onClick != null) Modifier.clickable(interactionSource = chipInteractionSource, indication = LocalIndication.current) { onClick() } else Modifier)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .padding(horizontal = dimensions.paddingMedium, vertical = 6.dp)
                .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh))
        ) {
            Text(
                text = displayText,
                color = if (tagColor.luminance() > 0.5f) Color.Black else Color.White,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            val trailingState = when {
                onDelete != null -> TagChipTrailing.DELETE
                trailingIcon != null -> TagChipTrailing.STATIC_ICON
                else -> TagChipTrailing.NONE
            }
            val iconTint = if (tagColor.luminance() > 0.5f) Color.Black else Color.White
            AnimatedContent(
                targetState = trailingState,
                transitionSpec = {
                    val enterSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessHigh)
                    val exitSpring = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessMedium)
                    (fadeIn(animationSpec = enterSpring) + scaleIn(initialScale = 0.6f, animationSpec = enterSpring)) togetherWith
                        (fadeOut(animationSpec = exitSpring) + scaleOut(targetScale = 0.6f, animationSpec = exitSpring))
                },
                label = "tagChipTrailing"
            ) { state ->
                when (state) {
                    TagChipTrailing.DELETE -> {
                        val deleteInteractionSource = remember { MutableInteractionSource() }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(dimensions.spacingSmall))
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = getText(R.string.remove),
                                tint = iconTint,
                                modifier = Modifier
                                    .size(16.dp)
                                    .pressSquish(deleteInteractionSource, 0.8f)
                                    .clickable(
                                        interactionSource = deleteInteractionSource,
                                        indication = LocalIndication.current
                                    ) { onDelete?.invoke() }
                            )
                        }
                    }
                    TagChipTrailing.STATIC_ICON -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Spacer(Modifier.width(dimensions.spacingSmall))
                            trailingIcon?.let {
                                Icon(
                                    imageVector = it,
                                    contentDescription = null,
                                    tint = iconTint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                    TagChipTrailing.NONE -> {}
                }
            }
        }
    }
}

private enum class TagChipTrailing { NONE, DELETE, STATIC_ICON }

// --- REUSABLE COMPONENT: CardTagRow ---
@Composable
fun CardTagRow(
    cardTags: List<String>,
    allTags: List<TagDefinition>,
    currentDeckTags: Set<String>,
    onUpdateTags: (Set<String>) -> Unit,
    onCreateTag: (String, String) -> Unit,
    onRenameTag: (TagDefinition, String) -> Unit,
    onDeleteTag: (TagDefinition) -> Unit
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
            onCreateTag = onCreateTag,
            onRenameTag = onRenameTag,
            onDeleteTag = onDeleteTag
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

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            modifier = Modifier.pressSquish(addInteractionSource, 0.85f).clickable(interactionSource = addInteractionSource, indication = LocalIndication.current) { showAddDialog = true }
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
    onCreateTag: (String, String) -> Unit,
    onRenameTag: (TagDefinition, String) -> Unit,
    onDeleteTag: (TagDefinition) -> Unit
) {
    val context = LocalContext.current
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

    // Edit mode (toggled by the header pencil) repurposes the tag list above the divider from
    // apply/remove into "pick a tag to edit," and swaps the "create new tag" section below the
    // divider for a TagEditorDialog-style rename/recolor/delete form for whichever tag was picked.
    var editMode by remember { mutableStateOf(false) }
    // The tag currently loaded into the editor, captured when picked rather than re-looked-up from
    // combinedTags on every recomposition — allTags only catches up with a rename/delete after the
    // Room flow round-trips, and re-deriving from it in the meantime briefly finds nothing, which
    // was dropping the editor to its shorter empty-state placeholder and back (a visible resize).
    var editingTagRef by remember { mutableStateOf<TagDefinition?>(null) }
    var editDraftName by remember { mutableStateOf("") }
    var editDraftColor by remember { mutableStateOf("") }
    var editNameError by remember { mutableStateOf<String?>(null) }

    fun exitEditMode() {
        editMode = false
        editingTagRef = null
        editNameError = null
    }

    val tagSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            modifier = Modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .heightIn(max = 700.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Box {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Box(modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.spacingMedium)) {
                    Text(
                        text = getText(R.string.tags_manage),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    // Wrapping Box, not the tooltip buttons' own `modifier` param, is what Box.align
                    // actually sees — it only honors align() on its direct child, and that param is
                    // applied two layers deeper inside TooltipBox/IconButton, so it was a no-op and
                    // the button fell back to Box's default TopStart.
                    Box(modifier = Modifier.align(Alignment.CenterEnd)) {
                        if (editMode) {
                            // Active: shown with its normal (non-highlighted) filled-tonal background
                            // to read as "toggled on," not as an accent-colored call to action.
                            TooltipFilledTonalIconButton(
                                description = getText(R.string.edit),
                                onClick = { exitEditMode() }
                            ) {
                                Icon(Icons.Default.Edit, getText(R.string.edit))
                            }
                        } else {
                            // Inactive: bare icon, no button container behind it. Disabled when
                            // there's nothing to edit at all — entering edit mode with zero tags
                            // would have nothing to auto-select, which is exactly the shortened
                            // placeholder state this dialog must never show.
                            TooltipIconButton(
                                description = getText(R.string.edit),
                                enabled = combinedTags.isNotEmpty(),
                                onClick = {
                                    editMode = true
                                    // Auto-select the first tag so the editor form fills the same
                                    // space the apply/remove list did — no "pick a tag" empty state.
                                    val firstTag = (thisDeckTags + otherDeckTags).firstOrNull()
                                    editingTagRef = firstTag
                                    if (firstTag != null) {
                                        editDraftName = firstTag.name
                                        editDraftColor = firstTag.color
                                        editNameError = null
                                    }
                                }
                            ) {
                                Icon(Icons.Default.Edit, getText(R.string.edit))
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    @Composable
                    fun TagItem(tag: TagDefinition) {
                        if (editMode) {
                            val isEditingThis = tag.name == editingTagRef?.name
                            TagChip(
                                text = tag.name,
                                colorHex = tag.color,
                                onClick = {
                                    // Tapping the tag already open in the editor is a no-op — there
                                    // is no "deselect" affordance that would leave the dialog with
                                    // nothing loaded below the divider.
                                    if (!isEditingThis) {
                                        editingTagRef = tag
                                        editDraftName = tag.name
                                        editDraftColor = tag.color
                                        editNameError = null
                                    }
                                },
                                trailingIcon = if (isEditingThis) Icons.Default.Edit else null,
                                modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                            )
                        } else {
                            val isSelected = tag.name in selectedTags
                            TagChip(
                                text = tag.name,
                                colorHex = tag.color,
                                onClick = {
                                    if (isSelected) selectedTags.remove(tag.name) else selectedTags.add(tag.name)
                                },
                                // Just a visual marker now — the whole chip (not only this icon)
                                // unapplies the tag, so it's non-interactive here.
                                trailingIcon = if (isSelected) Icons.Default.Clear else null,
                                modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                            )
                        }
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

                if (editMode) {
                    val editingTag = editingTagRef
                    if (editingTag != null) {
                        Text(
                            getText(R.string.tag_edit),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                        )

                        OutlinedTextField(
                            value = editDraftName,
                            onValueChange = { editDraftName = it; editNameError = null },
                            label = { Text(getText(R.string.tag_name)) },
                            singleLine = true,
                            isError = editNameError != null,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                        )
                        AnimatedVisibility(
                            visible = editNameError != null,
                            enter = slideInVertically() + fadeIn() + expandVertically(),
                            exit = slideOutVertically() + fadeOut() + shrinkVertically()
                        ) {
                            Text(
                                text = editNameError ?: "",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = dimensions.paddingMedium, top = 4.dp)
                            )
                        }

                        Spacer(Modifier.height(dimensions.spacingSmall))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(
                                value = editDraftColor,
                                onValueChange = { editDraftColor = it },
                                label = { Text(getText(R.string.hex_color)) },
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                trailingIcon = {
                                    Box(
                                        modifier = Modifier
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(parseHexColor(editDraftColor))
                                            .border(1.dp, MaterialTheme.colorScheme.outline, CircleShape)
                                    )
                                }
                            )
                        }

                        Spacer(Modifier.height(dimensions.spacingSmall))
                        SimpleColorPicker(
                            selectedColor = editDraftColor,
                            onColorSelected = { editDraftColor = it }
                        )

                        Spacer(Modifier.height(dimensions.spacingMedium))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val cancelEditInteractionSource = remember { MutableInteractionSource() }
                            TextButton(onClick = { exitEditMode() }, interactionSource = cancelEditInteractionSource, modifier = Modifier.pressSquish(cancelEditInteractionSource, 0.95f), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                            Spacer(Modifier.width(dimensions.spacingSmall))

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val deleteInteractionSource = remember { MutableInteractionSource() }
                                Button(
                                    onClick = {
                                        val deletedTag = editingTag
                                        val wasApplied = deletedTag.name in selectedTags
                                        onDeleteTag(deletedTag)
                                        selectedTags.remove(deletedTag.name)
                                        val nextTag = (thisDeckTags + otherDeckTags).firstOrNull { it.name != deletedTag.name }
                                        if (nextTag != null) {
                                            editingTagRef = nextTag
                                            editDraftName = nextTag.name
                                            editDraftColor = nextTag.color
                                        } else {
                                            // Nothing left to edit — leave edit mode entirely rather
                                            // than show the dialog with the empty placeholder.
                                            exitEditMode()
                                        }

                                        coroutineScope.launch {
                                            val result = tagSnackbarHostState.showSnackbar(
                                                message = getText(context, R.string.tag_deleted),
                                                actionLabel = getText(context, R.string.undo)
                                            )
                                            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                                // Re-saving under its own unchanged name takes renameTag's
                                                // short path straight to saveTagDefinition, clearing the
                                                // isDeleted flag the soft-delete set — a true restore.
                                                onRenameTag(deletedTag, deletedTag.name)
                                                if (wasApplied) selectedTags.add(deletedTag.name)
                                            }
                                        }
                                    },
                                    interactionSource = deleteInteractionSource,
                                    modifier = Modifier.pressSquish(deleteInteractionSource, 0.95f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(dimensions.spacingSmall))
                                    Text(getText(R.string.delete))
                                }

                                Spacer(Modifier.width(dimensions.spacingSmall))

                                val saveEditInteractionSource = remember { MutableInteractionSource() }
                                Button(
                                    onClick = {
                                        val trimmedName = editDraftName.trim()
                                        val isValidHex = try {
                                            AndroidColor.parseColor(editDraftColor)
                                            true
                                        } catch (e: Exception) { false }

                                        if (trimmedName.isEmpty()) {
                                            editNameError = getText(context, R.string.tag_error_empty)
                                        } else if (!isValidHex) {
                                            editNameError = getText(context, R.string.tag_error_hex)
                                        } else if (!trimmedName.equals(editingTag.name, ignoreCase = true) &&
                                            combinedTags.any { it.name.equals(trimmedName, ignoreCase = true) }) {
                                            editNameError = getText(context, R.string.tag_error_name)
                                        } else {
                                            val oldName = editingTag.name
                                            val renamedTag = editingTag.copy(name = trimmedName, color = editDraftColor)
                                            onRenameTag(renamedTag, oldName)
                                            if (oldName != trimmedName && oldName in selectedTags) {
                                                selectedTags.remove(oldName)
                                                selectedTags.add(trimmedName)
                                            }
                                            // Stay on this tag (under its possibly-new name) instead
                                            // of dropping back to the "select a tag" placeholder —
                                            // tracked by our own captured reference, not a re-lookup
                                            // in allTags, which hasn't caught up with the rename yet.
                                            editingTagRef = renamedTag
                                            editDraftName = trimmedName
                                        }
                                    },
                                    interactionSource = saveEditInteractionSource,
                                    modifier = Modifier.pressSquish(saveEditInteractionSource, 0.95f),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) {
                                    Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(Modifier.width(dimensions.spacingSmall))
                                    Text(getText(R.string.save))
                                }
                            }
                        }
                    } else {
                        Text(
                            getText(R.string.tag_select_to_edit),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(bottom = dimensions.spacingMedium)
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            val closeInteractionSource = remember { MutableInteractionSource() }
                            TextButton(onClick = { exitEditMode() }, interactionSource = closeInteractionSource, modifier = Modifier.pressSquish(closeInteractionSource, 0.95f), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                        }
                    }
                } else {
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
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val cancelSelInteractionSource = remember { MutableInteractionSource() }
                        TextButton(onClick = onDismiss, interactionSource = cancelSelInteractionSource, modifier = Modifier.pressSquish(cancelSelInteractionSource, 0.95f), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                        Spacer(Modifier.width(dimensions.spacingSmall))

                        Row {
                            val createInteractionSource = remember { MutableInteractionSource() }
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
                                modifier = Modifier.pressSquish(createInteractionSource, 0.95f),
                                enabled = isCreateEnabled,
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(dimensions.spacingSmall))
                                Text(getText(R.string.create))
                            }

                            Spacer(Modifier.width(dimensions.spacingSmall))

                            val saveSelInteractionSource = remember { MutableInteractionSource() }
                            Button(
                                onClick = { onSave(selectedTags.toSet()) },
                                interactionSource = saveSelInteractionSource,
                                modifier = Modifier.pressSquish(saveSelInteractionSource, 0.95f),
                                enabled = isSaveEnabled || selectedTags.isNotEmpty(),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(dimensions.spacingSmall))
                                Text(getText(R.string.save))
                            }
                        }
                    }
                }
            }

            androidx.compose.material3.SnackbarHost(tagSnackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** Same chip [TagChip] uses, but for a tag shared across several cards at once: [state] is `true`
 * when every selected card already has the tag (tap removes it from all), `false` when none do
 * (tap adds it to all), or `null` when only some do — tapping a `null` chip resolves it to `true`
 * (add to every card missing it), the same direction Android's own tri-state checkbox resolves an
 * indeterminate state on the next click. */
@Composable
fun TriStateTagChip(
    text: String,
    colorHex: String,
    state: Boolean?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    val tagColor = parseHexColor(colorHex)
    val displayText = if (text.length > 16) text.take(13) + "..." else text
    val chipInteractionSource = remember { MutableInteractionSource() }
    // Fill communicates the state on its own (not just the icon, which is easy to miss at a
    // glance): NONE is an outline only, matching CardTagRow's own "+" add-tag affordance; SOME is
    // half-filled, visually between the other two; ALL is fully filled, same as every other tag
    // chip in the app.
    val fillColor = when (state) {
        true -> tagColor
        null -> tagColor.copy(alpha = 0.5f)
        false -> Color.Transparent
    }
    val contentColor = when (state) {
        false -> tagColor
        else -> if (tagColor.luminance() > 0.5f) Color.Black else Color.White
    }

    Surface(
        color = fillColor,
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        border = if (state == false) BorderStroke(1.dp, tagColor) else null,
        modifier = modifier
            .padding(end = dimensions.spacingSmall)
            .pressSquish(chipInteractionSource, 0.9f)
            .clickable(interactionSource = chipInteractionSource, indication = LocalIndication.current, onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = 6.dp)
        ) {
            Text(
                text = displayText,
                color = contentColor,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
            if (state != false) {
                Spacer(Modifier.width(dimensions.spacingSmall))
                Icon(
                    imageVector = if (state == true) Icons.Default.Clear else Icons.Default.Remove,
                    contentDescription = if (state == true) getText(R.string.remove) else getText(R.string.tag_partially_selected),
                    tint = contentColor,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * The multi-select "Tag" action's dialog (Simple/Bulk Editor) — same shape as [TagSelectionDialog]
 * but for several cards' tags at once via [TriStateTagChip]. [selectedCardsTags] is each selected
 * card's own current tag set; [onSave] reports only the tags the user actually touched (as
 * add-to-every-card / remove-from-every-card sets) — any tag never tapped is left exactly as it
 * was per card, mixed selections included.
 */
@Composable
fun MultiCardTagSelectionDialog(
    selectedCardsTags: List<Set<String>>,
    allTags: List<TagDefinition>,
    currentDeckTags: Set<String>,
    onDismiss: () -> Unit,
    onSave: (addTags: Set<String>, removeTags: Set<String>) -> Unit,
    onCreateTag: (String, String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val locallyCreatedTags = remember { mutableStateListOf<TagDefinition>() }
    // Only ever holds a tag once the user has explicitly tapped it — its presence (not its value)
    // is what isSaveEnabled below checks, since the initial, untouched tri-state is never itself
    // something to save.
    val overrides = remember { mutableStateMapOf<String, Boolean>() }

    val combinedTags = remember(allTags, locallyCreatedTags.toList()) {
        (allTags + locallyCreatedTags).distinctBy { it.name }
    }
    val thisDeckTags = combinedTags.filter { it.name in currentDeckTags }.sortedBy { it.name.lowercase() }
    val otherDeckTags = combinedTags.filter { it.name !in currentDeckTags }.sortedBy { it.name.lowercase() }

    fun initialState(tagName: String): Boolean? {
        val presentCount = selectedCardsTags.count { tagName in it }
        return when (presentCount) {
            0 -> false
            selectedCardsTags.size -> true
            else -> null
        }
    }
    fun displayState(tagName: String): Boolean? = if (overrides.containsKey(tagName)) overrides[tagName] else initialState(tagName)
    fun toggle(tagName: String) {
        overrides[tagName] = displayState(tagName) != true
    }

    var newTagName by remember { mutableStateOf("") }
    var newTagColor by remember { mutableStateOf("#0D47A1") }
    val isCreateEnabled = newTagName.isNotBlank()
    val isSaveEnabled = overrides.isNotEmpty()

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            modifier = Modifier
                .widthIn(min = 280.dp, max = 560.dp)
                .heightIn(max = 700.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    text = getText(R.string.tags_manage),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(bottom = dimensions.spacingSmall)
                )
                Text(
                    getText(R.string.tag_multi_select_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = dimensions.spacingMedium)
                )

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    @Composable
                    fun TagItem(tag: TagDefinition) {
                        TriStateTagChip(
                            text = tag.name,
                            colorHex = tag.color,
                            state = displayState(tag.name),
                            onClick = { toggle(tag.name) },
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
                    Button(
                        onClick = {
                            if (isCreateEnabled) {
                                val name = newTagName.trim()
                                onCreateTag(name, newTagColor)
                                locallyCreatedTags.add(TagDefinition(name = name, color = newTagColor))
                                overrides[name] = true
                                newTagName = ""
                                newTagColor = "#0D47A1"
                            }
                        },
                        interactionSource = createInteractionSource,
                        modifier = Modifier.pressSquish(createInteractionSource, 0.95f),
                        enabled = isCreateEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.create))
                    }

                    Row {
                        val cancelSelInteractionSource = remember { MutableInteractionSource() }
                        TextButton(onClick = onDismiss, interactionSource = cancelSelInteractionSource, modifier = Modifier.pressSquish(cancelSelInteractionSource, 0.95f), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }

                        Spacer(Modifier.width(dimensions.spacingSmall))

                        val saveSelInteractionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = {
                                onSave(
                                    overrides.filterValues { it }.keys.toSet(),
                                    overrides.filterValues { !it }.keys.toSet()
                                )
                            },
                            interactionSource = saveSelInteractionSource,
                            modifier = Modifier.pressSquish(saveSelInteractionSource, 0.95f),
                            enabled = isSaveEnabled,
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(R.string.save))
                        }
                    }
                }
            }
        }
    }
}