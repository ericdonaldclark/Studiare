package net.ericclark.studiare.screens.Dialogs

import net.ericclark.studiare.TooltipFilledTonalIconButton
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.DialogAccentCap
import net.ericclark.studiare.StudiareSwitch
import net.ericclark.studiare.TooltipIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.components.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.material3.TextField
import androidx.compose.runtime.LaunchedEffect
import net.ericclark.studiare.R
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.screens.Screens.DeckEditorScreen
import net.ericclark.studiare.screens.UI_Components.CardEditor
import net.ericclark.studiare.screens.UI_Components.DynamicNoteEditor

/** The action buttons at the bottom of a [CardEditDialog] — either Cancel/Save (editing an existing card) or Cancel/Add More/Add (creating a new one). */
sealed class CardEditDialogMode {
    data class Edit(val onSave: () -> Unit, val onDelete: () -> Unit) : CardEditDialogMode()
    data class Create(val onAdd: () -> Unit, val onAddMore: () -> Unit) : CardEditDialogMode()
}

/**
 * Simple Editor's per-card editing surface — reuses [CardEditor] as-is (minus its Info button,
 * which doesn't fit a focused single-card dialog) rather than a parallel UI, so both editors share
 * one set of fields/behavior. Rich-text editing state is hoisted here, scoped to this one dialog
 * instance (not by list index like [DeckEditorScreen]'s own usage), since a dialog only ever has
 * one card in scope — sidesteps that screen's filtered-vs-unfiltered index mismatch entirely.
 */
@Composable
fun CardEditDialog(
    cardState: CardEditorState,
    cardNumber: Int,
    totalCards: Int,
    allTags: List<TagDefinition>,
    currentDeckTags: Set<String>,
    onUpdateTags: (Set<String>) -> Unit,
    onCreateTag: (String, String) -> Unit,
    mode: CardEditDialogMode,
    onCancel: () -> Unit,
    normalizationType: NormalizationType = NormalizationType.NONE
) {
    val dimensions = LocalStudiareDimensions.current
    val dialogSnackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }
    val coroutineScope = rememberCoroutineScope()

    var richTextTarget by remember { mutableStateOf<String?>(null) }
    var richTextInitialHtml by remember { mutableStateOf("") }
    var richTextTitle by remember { mutableStateOf("") }

    if (richTextTarget != null) {
        RichTextEditorDialog(
            initialHtml = richTextInitialHtml,
            title = richTextTitle,
            onDismiss = { richTextTarget = null },
            onSave = { savedHtml ->
                val plainText = savedHtml.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                when {
                    richTextTarget == "front" -> {
                        cardState.frontRichTextInfo.value = savedHtml
                        cardState.front.value = plainText
                    }
                    richTextTarget == "back" -> {
                        cardState.backRichTextInfo.value = savedHtml
                        cardState.back.value = plainText
                    }
                    richTextTarget?.startsWith("frontNote_") == true -> {
                        val index = richTextTarget!!.substringAfter("_").toInt()
                        val currentList = cardState.frontNotes.value.toMutableList()
                        currentList[index] = currentList[index].copy(content = savedHtml)
                        cardState.frontNotes.value = currentList
                    }
                    richTextTarget?.startsWith("backNote_") == true -> {
                        val index = richTextTarget!!.substringAfter("_").toInt()
                        val currentList = cardState.backNotes.value.toMutableList()
                        currentList[index] = currentList[index].copy(content = savedHtml)
                        cardState.backNotes.value = currentList
                    }
                }
                richTextTarget = null
            }
        )
    }

    AnimatedDialog(onDismissRequest = onCancel, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 900.dp)
        ) {
            Box {
                Column(
                    modifier = Modifier
                        .padding(dimensions.paddingMedium)
                        .verticalScroll(rememberScrollState())
                ) {
                    CardEditor(
                        cardState = cardState,
                        cardNumber = cardNumber,
                        totalCards = totalCards,
                        onDelete = { if (mode is CardEditDialogMode.Edit) mode.onDelete() },
                        onKnownClick = { cardState.isKnown.value = !cardState.isKnown.value },
                        allTags = allTags,
                        currentDeckTags = currentDeckTags,
                        onUpdateTags = onUpdateTags,
                        onCreateTag = onCreateTag,
                        onOpenRichTextEditor = { target, initialHtml, title ->
                            richTextTarget = target
                            richTextInitialHtml = initialHtml
                            richTextTitle = title
                        },
                        snackbarHostState = dialogSnackbarHostState,
                        coroutineScope = coroutineScope,
                        showInfoButton = false,
                        normalizationType = normalizationType
                    )

                    Spacer(Modifier.height(dimensions.spacingMedium))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onCancel, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                            Text(getText(R.string.cancel))
                        }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        when (mode) {
                            is CardEditDialogMode.Edit -> {
                                val hasContent = cardState.front.value.isNotBlank() && cardState.back.value.isNotBlank()
                                Button(onClick = mode.onSave, enabled = hasContent, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                                    Text(getText(R.string.save))
                                }
                            }
                            is CardEditDialogMode.Create -> {
                                val hasContent = cardState.front.value.isNotBlank() && cardState.back.value.isNotBlank()
                                OutlinedButton(onClick = mode.onAddMore, enabled = hasContent, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                                    Text(getText(R.string.add_more))
                                }
                                Spacer(Modifier.width(dimensions.spacingSmall))
                                Button(onClick = mode.onAdd, enabled = hasContent, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                                    Text(getText(R.string.add))
                                }
                            }
                        }
                    }
                }

                androidx.compose.material3.SnackbarHost(dialogSnackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

@Composable
fun UnsavedChangesDialog(onDismiss: () -> Unit, onDiscard: () -> Unit, onSave: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp
        ) {
            Column {
                DialogAccentCap()
                Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                    Text(getText(R.string.unsaved_changes), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Text(getText(R.string.unsaved_changes_save))
                    Spacer(Modifier.height(dimensions.spacingLarge))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDiscard, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.discard)) }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Button(onClick = onSave, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.save)) }
                    }
                }
            }
        }
    }
}

@Composable
fun AdvancedDeckEditorDialog(
    frontTemplates: List<NoteField>,
    backTemplates: List<NoteField>,
    onDismiss: () -> Unit,
    onSave: (List<NoteField>, List<NoteField>, Boolean) -> Unit, // CHANGED: Added Boolean
    savedTemplateSets: List<TemplateSet> = emptyList(),
    onSaveAsTemplate: (String, List<NoteField>, List<NoteField>) -> Unit = { _, _, _ -> },
    onDeleteTemplateSet: (TemplateSet) -> Unit = {}
) {
    val dimensions = LocalStudiareDimensions.current
    // Keyed on the incoming templates so each fresh open of this dialog starts from whatever was
    // actually last saved, instead of a plain `remember {}` that (depending on how this
    // conditionally-composed dialog is recomposed) could keep reusing stale/empty state across
    // separate opens. The key only changes between opens (it's stable for the lifetime of a
    // single open session), so in-progress edits within one session are untouched.
    var localFront by remember(frontTemplates) { mutableStateOf(frontTemplates) }
    var localBack by remember(backTemplates) { mutableStateOf(backTemplates) }
    var addToExistingCards by remember { mutableStateOf(false) } // NEW: Switch state
    var showTemplatePicker by remember { mutableStateOf(false) }
    var showSaveTemplateDialog by remember { mutableStateOf(false) }

    AnimatedDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(getText(R.string.advanced_editor), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(4.dp))
                        Text(getText(R.string.define_default_note_fields_desc), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Box {
                        // Gated on real saved templates only — the always-present "Default" entry
                        // below doesn't count, since it's just a quick reset to the two locked
                        // fields rather than something worth enabling this button for on its own.
                        OutlinedButton(
                            onClick = { showTemplatePicker = true },
                            enabled = savedTemplateSets.isNotEmpty(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Icon(Icons.Default.FileOpen, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(getText(R.string.start_from_template), style = MaterialTheme.typography.labelLarge)
                        }
                        DropdownMenu(expanded = showTemplatePicker, onDismissRequest = { showTemplatePicker = false }) {
                            DropdownMenuItem(
                                text = { Text(getText(R.string.default_template_name)) },
                                onClick = {
                                    localFront = emptyList()
                                    localBack = emptyList()
                                    showTemplatePicker = false
                                }
                            )
                            savedTemplateSets.forEach { templateSet ->
                                DropdownMenuItem(
                                    text = { Text(templateSet.name) },
                                    onClick = {
                                        localFront = templateSet.frontFields
                                        localBack = templateSet.backFields
                                        showTemplatePicker = false
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.IconButton(onClick = { onDeleteTemplateSet(templateSet) }) {
                                            Icon(Icons.Default.Delete, contentDescription = getText(R.string.delete))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item { Text(getText(R.string.front_note_templates), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
                    item { LockedFieldRow(getText(R.string.front)) }
                    itemsIndexed(localFront) { index, template ->
                        TemplateRow(
                            template = template,
                            onUpdate = { updated -> localFront = localFront.toMutableList().apply { this[index] = updated } },
                            onRemove = { localFront = localFront.toMutableList().apply { removeAt(index) } },
                            onMoveUp = if (index > 0) { { localFront = localFront.toMutableList().apply { add(index - 1, removeAt(index)) } } } else null,
                            onMoveDown = if (index < localFront.lastIndex) { { localFront = localFront.toMutableList().apply { add(index + 1, removeAt(index)) } } } else null
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { localFront = localFront + NoteField(name = "New Field", content = "", type = MediaType.PLAIN_TEXT) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(getText(R.string.add_front_field), style = MaterialTheme.typography.labelLarge)
                        }
                        Spacer(Modifier.height(dimensions.spacingLarge))
                    }

                    item { Text(getText(R.string.back_note_templates), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
                    item { LockedFieldRow(getText(R.string.back)) }
                    itemsIndexed(localBack) { index, template ->
                        TemplateRow(
                            template = template,
                            onUpdate = { updated -> localBack = localBack.toMutableList().apply { this[index] = updated } },
                            onRemove = { localBack = localBack.toMutableList().apply { removeAt(index) } },
                            onMoveUp = if (index > 0) { { localBack = localBack.toMutableList().apply { add(index - 1, removeAt(index)) } } } else null,
                            onMoveDown = if (index < localBack.lastIndex) { { localBack = localBack.toMutableList().apply { add(index + 1, removeAt(index)) } } } else null
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { localBack = localBack + NoteField(name = "New Field", content = "", type = MediaType.PLAIN_TEXT) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(getText(R.string.add_back_field), style = MaterialTheme.typography.labelLarge)
                        }
                    }

                    // Moved out of the dialog's pinned footer — these act on the fields above
                    // rather than the dialog as a whole, so they scroll with that content instead
                    // of always staying on screen.
                    item {
                        Spacer(Modifier.height(dimensions.spacingLarge))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(getText(R.string.add_missing_fields_desc), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            StudiareSwitch(
                                checked = addToExistingCards,
                                onCheckedChange = { addToExistingCards = it }
                            )
                        }
                    }
                    item {
                        Spacer(Modifier.height(dimensions.spacingMedium))
                        Text(
                            getText(R.string.save_as_template_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        OutlinedButton(
                            onClick = { showSaveTemplateDialog = true },
                            enabled = localFront.isNotEmpty() || localBack.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.height(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(getText(R.string.save_as_template), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel), style = MaterialTheme.typography.labelLarge) }
                    Button(onClick = { onSave(localFront, localBack, addToExistingCards) }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.apply), style = MaterialTheme.typography.labelLarge) }
                }
            }
        }
    }

    if (showSaveTemplateDialog) {
        var templateName by remember { mutableStateOf("") }
        AnimatedDialog(onDismissRequest = { showSaveTemplateDialog = false }) {
            Surface(
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Column {
                    DialogAccentCap()
                    Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                        Text(getText(R.string.save_as_template), style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(dimensions.spacingMedium))
                        TextField(
                            value = templateName,
                            onValueChange = { templateName = it },
                            singleLine = true,
                            label = { Text(getText(R.string.template_name)) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                        )
                        Spacer(Modifier.height(dimensions.spacingLarge))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { showSaveTemplateDialog = false }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                            Spacer(Modifier.width(dimensions.spacingSmall))
                            Button(
                                onClick = {
                                    onSaveAsTemplate(templateName, localFront, localBack)
                                    showSaveTemplateDialog = false
                                },
                                enabled = templateName.isNotBlank(),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) { Text(getText(R.string.save)) }
                        }
                    }
                }
            }
        }
    }
}

/** A non-editable row showing a card's always-present base Front/Back field, so the custom
 * fields listed below it read as additions on top of it rather than a replacement for it. */
@Composable
fun LockedFieldRow(label: String) {
    val dimensions = LocalStudiareDimensions.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.spacingSmall)
    ) {
        TextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = TextFieldDefaults.colors(
                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                disabledTextColor = MaterialTheme.colorScheme.onSurface,
                disabledIndicatorColor = Color.Transparent
            ),
            trailingIcon = {
                Icon(Icons.Default.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        )
    }
}

@Composable
fun TemplateRow(
    template: NoteField,
    onUpdate: (NoteField) -> Unit,
    onRemove: () -> Unit,
    onMoveUp: (() -> Unit)? = null,
    onMoveDown: (() -> Unit)? = null
) {
    val dimensions = LocalStudiareDimensions.current
    var showTypeDropdown by remember { mutableStateOf(false) }

    // NEW: Type Dropdown (mirrored from DynamicNoteEditor)
    val typeDropdown = @Composable {
        Box {
            TextButton(onClick = { showTypeDropdown = true }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                Text(template.type.toString())
            }
            androidx.compose.material3.DropdownMenu(
                expanded = showTypeDropdown,
                onDismissRequest = { showTypeDropdown = false }
            ) {
                MediaType.entries.forEach { mediaType ->
                    DropdownMenuItem(
                        text = { Text(mediaType.toString()) },
                        onClick = {
                            onUpdate(template.copy(type = mediaType, content = ""))
                            showTypeDropdown = false
                        }
                    )
                }
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.spacingSmall)
    ) {
        TextField(
            value = template.name,
            onValueChange = { onUpdate(template.copy(name = it)) },
            modifier = Modifier.weight(1f),
            label = { Text(getText(R.string.field_name)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            trailingIcon = typeDropdown // ADDED: Trailing Icon
        )
        Spacer(Modifier.width(4.dp))
        TooltipIconButton(
            description = getText(R.string.move_field_up),
            onClick = { onMoveUp?.invoke() },
            enabled = onMoveUp != null
        ) {
            Icon(Icons.Default.KeyboardArrowUp, contentDescription = getText(R.string.move_field_up))
        }
        TooltipIconButton(
            description = getText(R.string.move_field_down),
            onClick = { onMoveDown?.invoke() },
            enabled = onMoveDown != null
        ) {
            Icon(Icons.Default.KeyboardArrowDown, contentDescription = getText(R.string.move_field_down))
        }
        Spacer(Modifier.width(4.dp))
        TooltipFilledTonalIconButton(description = getText(R.string.remove_template),
            onClick = onRemove,
            colors = androidx.compose.material3.IconButtonDefaults.filledTonalIconButtonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            )
        ) {
            Icon(Icons.Default.Close, contentDescription = getText(R.string.remove_template))
        }
    }
}
