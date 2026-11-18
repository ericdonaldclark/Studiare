package net.ericclark.studiare.screens.Dialogs

import net.ericclark.studiare.TooltipFilledTonalIconButton
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.TooltipIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.components.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.runtime.LaunchedEffect
import net.ericclark.studiare.R
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.screens.Screens.DeckEditorScreen
import net.ericclark.studiare.screens.UI_Components.CardEditor
import net.ericclark.studiare.screens.UI_Components.ConnectedButtonGroup
import net.ericclark.studiare.screens.UI_Components.DynamicNoteEditor

/** The action buttons at the bottom of a [CardEditDialog] — either Cancel/Save (editing an existing card) or Cancel/Add More/Add (creating a new one). */
sealed class CardEditDialogMode {
    data class Edit(val onSave: () -> Unit, val onDelete: () -> Unit, val onClone: () -> Unit, val onMove: () -> Unit, val onUseTemplate: () -> Unit, val onClearData: () -> Unit) : CardEditDialogMode()
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
    onRenameTag: (TagDefinition, String) -> Unit,
    onDeleteTag: (TagDefinition) -> Unit,
    mode: CardEditDialogMode,
    onCancel: () -> Unit,
    normalizationType: NormalizationType = NormalizationType.NONE,
    textCorrections: TextCorrectionSettings = TextCorrectionSettings()
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
                .widthIn(min = 280.dp, max = 560.dp)
                .heightIn(max = 900.dp)
        ) {
            Box {
                Column(
                    modifier = Modifier.padding(dimensions.paddingMedium)
                ) {
                    // Scrolls on its own, bounded by the Surface's heightIn above, so the
                    // Cancel/Save row below stays pinned to the bottom instead of scrolling away
                    // with long card content.
                    Column(
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .verticalScroll(rememberScrollState())
                    ) {
                        CardEditor(
                            cardState = cardState,
                            cardNumber = cardNumber,
                            totalCards = totalCards,
                            onDelete = { if (mode is CardEditDialogMode.Edit) mode.onDelete() },
                            onCloneClick = if (mode is CardEditDialogMode.Edit) mode.onClone else null,
                            onMoveClick = if (mode is CardEditDialogMode.Edit) mode.onMove else null,
                            onUseTemplateClick = if (mode is CardEditDialogMode.Edit) mode.onUseTemplate else null,
                            onClearDataClick = if (mode is CardEditDialogMode.Edit) mode.onClearData else null,
                            onKnownClick = { cardState.isKnown.value = !cardState.isKnown.value },
                            onPinClick = { cardState.isPinned.value = !cardState.isPinned.value },
                            allTags = allTags,
                            currentDeckTags = currentDeckTags,
                            onUpdateTags = onUpdateTags,
                            onCreateTag = onCreateTag,
                            onRenameTag = onRenameTag,
                            onDeleteTag = onDeleteTag,
                            onOpenRichTextEditor = { target, initialHtml, title ->
                                richTextTarget = target
                                richTextInitialHtml = initialHtml
                                richTextTitle = title
                            },
                            snackbarHostState = dialogSnackbarHostState,
                            coroutineScope = coroutineScope,
                            showInfoButton = false,
                            normalizationType = normalizationType,
                            textCorrections = textCorrections
                        )
                    }

                    Spacer(Modifier.height(dimensions.spacingMedium))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onCancel, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                            Text(getText(R.string.cancel))
                        }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Row(verticalAlignment = Alignment.CenterVertically) {
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
            Column(
                modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Emergency, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(getText(R.string.unsaved_changes), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(getText(R.string.unsaved_changes_save), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
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

/**
 * Same warning [DeckSettingsDialog]'s "Clear Review Data" confirms, scoped down to the card(s)
 * this was invoked on — the per-card overflow menu's and multi-select's own "Clear Data" action,
 * in both deck editors. [cardCount] only changes the message's wording (singular/plural); it
 * never resets anything outside the specific card id(s) the caller already captured before
 * showing this.
 */
@Composable
fun ClearCardDataConfirmDialog(cardCount: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Filled.Emergency, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(getText(R.string.clear_data_question), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(pluralStringResource(R.plurals.clear_data_message, cardCount, cardCount), textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(
                        onClick = onConfirm,
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) { Text(getText(R.string.clear_data)) }
                }
            }
        }
    }
}

/** Sets a [DifficultySetting] via a [net.ericclark.studiare.DifficultySlider] — shared by the
 * multi-select "Difficulty" bulk action (mirrors [ClearCardDataConfirmDialog]'s small-
 * confirmation-dialog shape, [message] is the pluralized "Set the difficulty for N cards" text,
 * no [onClear]) and the filter overlay's difficulty quick-filter (a plain explanatory [message],
 * [onClear] clears the filter back to "any difficulty" instead of applying one). */
@Composable
fun SetDifficultyDialog(
    message: String,
    onDismiss: () -> Unit,
    onConfirm: (net.ericclark.studiare.data.DifficultySetting) -> Unit,
    initialDifficulty: net.ericclark.studiare.data.DifficultySetting = net.ericclark.studiare.data.DifficultySetting.ONE,
    onClear: (() -> Unit)? = null
) {
    val dimensions = LocalStudiareDimensions.current
    var difficulty by remember { mutableStateOf(initialDifficulty) }
    AnimatedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                Text(getText(R.string.set_difficulty), style = MaterialTheme.typography.headlineSmall, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(message)
                Spacer(Modifier.height(dimensions.spacingSmall))
                net.ericclark.studiare.DifficultySlider(
                    label = getText(R.string.difficulty),
                    difficulty = difficulty,
                    onDifficultyChange = { difficulty = it }
                )
                Spacer(Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    if (onClear != null) {
                        TextButton(onClick = onClear, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.clear_filter_text)) }
                    } else {
                        Spacer(Modifier.width(1.dp))
                    }
                    Row {
                        TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Button(
                            onClick = { onConfirm(difficulty) },
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) { Text(getText(R.string.save)) }
                    }
                }
            }
        }
    }
}

/** Snapshot of what actually gets persisted (name + fields) — compared against the live editing
 * state to tell whether anything has changed since the dialog opened, was last Opened/Reset, or
 * was last committed, so Reset/Save can disable themselves with nothing to act on. */
private data class TemplateFieldSnapshot(val front: List<NoteField>, val back: List<NoteField>, val name: String)

@Composable
fun AdvancedDeckEditorDialog(
    frontTemplates: List<NoteField>,
    backTemplates: List<NoteField>,
    onDismiss: () -> Unit,
    /** Called once the resolved template has already been persisted. The `String` param is the
     * resolved template id this deck should now link to (`Deck.activeTemplateSetId`). Last param
     * is `applyToExistingCards` — true only when "Apply to All" was pressed, false for "Apply to
     * New" (seeding turns on either way; only "All" also sweeps existing cards). */
    onSave: (List<NoteField>, List<NoteField>, String, String, Boolean) -> Unit,
    /** Name of the [TemplateSet] already backing this deck's template (see
     * `Deck.activeTemplateSetId`), or blank for a deck whose template has never been named yet. */
    initialTemplateName: String = "",
    /** The [TemplateSet] this dialog starts out editing in place — typically the deck's own
     * current `activeTemplateSetId`, so reopening the editor on a deck that already applies a
     * template resumes editing that same shared template instead of always starting fresh. */
    initialEditingTemplateId: String? = null,
    savedTemplateSets: List<TemplateSet> = emptyList(),
    /** Upserts a [TemplateSet] by id — used for both in-place "Save" (same id) and "Save As" (a
     * freshly minted id), and for a bare "Apply" that never explicitly pressed Save first. */
    onPersistTemplate: (id: String, name: String, front: List<NoteField>, back: List<NoteField>) -> Unit = { _, _, _, _ -> },
    onDeleteTemplateSet: (TemplateSet) -> Unit = {},
    /** Names of every deck currently linked to the given template id (by `activeTemplateSetId`) —
     * shown in the delete-confirmation dialog so deleting a still-in-use template is an informed
     * choice, not a surprise. Empty when nothing currently uses it. */
    decksUsingTemplate: (templateId: String) -> List<String> = { emptyList() },
    onOpenExportDialog: () -> Unit = {},
    /** Clears the deck's link to whatever shared template it's currently using (and its own
     * default seed fields). This dialog stays open afterward, reset to the same blank/unsaved
     * state a fresh "Default" open starts from. Never touches any card's existing fields. Only
     * ever invoked while `editingTemplateId != null`. */
    onRemoveTemplate: () -> Unit = {},
    /** False for a deck with no cards yet (a brand-new deck, or an existing one that's still
     * empty) — there's nothing an "Apply to All" could sweep that "Apply to New" wouldn't already
     * cover, so the footer collapses to a single "Apply" button instead of offering a choice
     * that's really the same outcome either way. */
    hasExistingCards: Boolean = true,
    /** False for Settings' global Templates list, which opens this same dialog to edit a shared
     * template in place with no particular deck in context — Apply/Apply to New/Apply to All (and
     * the name field's "Remove" button, which detaches *a deck's* link) don't mean anything
     * without one, so the footer collapses to a plain Cancel/Save instead, and Save closes the
     * dialog once it persists (every other path here leaves it open, since Apply/Save there are
     * just one step in a still-open deck editor). */
    footerShowsApplyButtons: Boolean = true
) {
    val dimensions = LocalStudiareDimensions.current
    // Keyed on the incoming templates so each fresh open of this dialog starts from whatever was
    // actually last saved, instead of a plain `remember {}` that (depending on how this
    // conditionally-composed dialog is recomposed) could keep reusing stale/empty state across
    // separate opens. The key only changes between opens (it's stable for the lifetime of a
    // single open session), so in-progress edits within one session are untouched.
    var localFront by remember(frontTemplates) { mutableStateOf(frontTemplates) }
    var localBack by remember(backTemplates) { mutableStateOf(backTemplates) }
    // No longer a permanently-visible field — only ever set via Open (copies the picked
    // TemplateSet's name) or the naming dialog below (typed once, at the moment of a Save/Apply).
    var templateName by remember(initialTemplateName) { mutableStateOf(initialTemplateName) }
    var editingTemplateId by remember(initialEditingTemplateId) { mutableStateOf(initialEditingTemplateId) }
    // What's currently on disk for this template (or blank, for a never-saved one) — updated on
    // open/Open/Reset/commit, compared against the live `local*`/`templateName` state to drive
    // Reset/Save's enabled state and whether "Save As" still applies (name unchanged since then).
    var baseline by remember { mutableStateOf(TemplateFieldSnapshot(frontTemplates, backTemplates, initialTemplateName.trim())) }
    val hasChanges = localFront != baseline.front || localBack != baseline.back || templateName.trim() != baseline.name
    // Separate from `hasChanges` (which is relative to the last-opened/saved baseline, and stays
    // false the moment a previously-applied template is reopened) — Reset should be enabled
    // whenever there's anything to reset *to* blank, regardless of whether it's been edited since.
    val isBlankState = editingTemplateId == null && templateName.isBlank() && localFront.isEmpty() && localBack.isEmpty()
    var showOverflowMenu by remember { mutableStateOf(false) }
    var showTemplatePicker by remember { mutableStateOf(false) }
    var templateSetToDelete by remember { mutableStateOf<TemplateSet?>(null) }

    // Drives the naming/confirm dialog shared by every path that can persist a template: typing a
    // name for a never-saved one, or — when `editingTemplateId` shows one already existed — asking
    // whether to overwrite it in place ("Save") or fork a copy ("Save As"). `pendingApplyAll` is
    // non-null only when this was triggered by Apply to New/All, so it knows to also link+seed (or
    // sweep) the current deck once naming resolves; null means a bare Save/Save As with no Apply.
    var showNameDialog by remember { mutableStateOf(false) }
    var nameDialogDraft by remember { mutableStateOf("") }
    var pendingApplyAll by remember { mutableStateOf<Boolean?>(null) }

    // Shared by every path that can persist a template — reused under a real template id
    // (`editingTemplateId`, unless [forceFork] mints a fresh one for "Save As").
    fun commitTemplateEdit(forceFork: Boolean = false, onCommitted: (resolvedId: String) -> Unit) {
        val resolvedId = if (forceFork) java.util.UUID.randomUUID().toString() else (editingTemplateId ?: java.util.UUID.randomUUID().toString())
        val trimmedName = templateName.trim()
        onPersistTemplate(resolvedId, trimmedName, localFront, localBack)
        editingTemplateId = resolvedId
        templateName = trimmedName
        baseline = TemplateFieldSnapshot(localFront, localBack, trimmedName)
        onCommitted(resolvedId)
    }

    // Save (with no name yet) and both Apply buttons funnel through here instead of committing
    // directly — Apply always does, regardless of whether a name already exists, per the "existed
    // beforehand → offer Save or Save As" requirement; a bare Save only does when still unnamed.
    fun requestNaming(applyAll: Boolean?) {
        nameDialogDraft = templateName
        pendingApplyAll = applyAll
        showNameDialog = true
    }

    fun resolveNaming(forceFork: Boolean) {
        templateName = nameDialogDraft.trim()
        showNameDialog = false
        val applyAll = pendingApplyAll
        pendingApplyAll = null
        commitTemplateEdit(forceFork = forceFork) { resolvedId ->
            if (applyAll != null) {
                onSave(localFront, localBack, resolvedId, templateName, applyAll)
            } else if (!footerShowsApplyButtons) {
                // A bare Save (no Apply involved) in the footerless-Apply mode — see saveTemplate.
                onDismiss()
            }
        }
    }

    // Bottom Save button for the no-Apply-buttons footer (and the overflow menu's own "Save"
    // item, in every mode) — commits under the existing name, or asks for one first via the same
    // naming dialog Apply uses. Closes the dialog afterward only in the no-Apply-buttons mode (see
    // footerShowsApplyButtons's doc comment); resolveNaming above mirrors that for the
    // naming-dialog path.
    fun saveTemplate() {
        if (templateName.isNotBlank()) {
            commitTemplateEdit { if (!footerShowsApplyButtons) onDismiss() }
        } else {
            requestNaming(applyAll = null)
        }
    }

    // Apply to New/All — no naming dialog at all when the template was opened via "Open" and
    // nothing about it (name or fields) has changed since: there's nothing new to save or fork,
    // so just apply the existing, already-saved template as-is.
    fun applyTemplate(applyAll: Boolean) {
        // Still the blank, never-touched default (no name, no fields) — nothing to apply at all,
        // so just close the editor instead of asking for a name to save an empty template under.
        if (editingTemplateId == null && templateName.isBlank() && localFront.isEmpty() && localBack.isEmpty()) {
            onDismiss()
            return
        }
        val openedTemplate = editingTemplateId?.let { id -> savedTemplateSets.find { it.id == id } }
        val unchanged = openedTemplate != null &&
            openedTemplate.name == templateName &&
            openedTemplate.frontFields == localFront &&
            openedTemplate.backFields == localBack
        when {
            unchanged -> onSave(localFront, localBack, openedTemplate!!.id, templateName, applyAll)
            // A name's already in place (typed directly, or carried over from an opened template)
            // — nothing to ask, just commit under that name like Save would.
            templateName.isNotBlank() -> commitTemplateEdit { resolvedId ->
                onSave(localFront, localBack, resolvedId, templateName, applyAll)
            }
            else -> requestNaming(applyAll)
        }
    }

    AnimatedDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxHeight(0.9f)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(48.dp)) // balances the trailing icon so the title stays centered
                    Text(
                        getText(R.string.advanced_editor),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                    Box {
                        androidx.compose.material3.IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.more_options))
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            // Gated on real saved templates only — the always-present "Default"
                            // entry in the picker below doesn't count on its own.
                            DropdownMenuItem(
                                text = { Text(getText(R.string.open)) },
                                enabled = savedTemplateSets.isNotEmpty(),
                                leadingIcon = { Icon(Icons.Default.FileOpen, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    showTemplatePicker = true
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(getText(R.string.reset)) },
                                enabled = !isBlankState,
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    localFront = emptyList()
                                    localBack = emptyList()
                                    editingTemplateId = null
                                    templateName = ""
                                    baseline = TemplateFieldSnapshot(emptyList(), emptyList(), "")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(getText(R.string.save)) },
                                enabled = hasChanges,
                                leadingIcon = { Icon(Icons.Default.Save, contentDescription = null) },
                                onClick = {
                                    showOverflowMenu = false
                                    saveTemplate()
                                }
                            )
                            // Only meaningful for an existing template whose name hasn't already
                            // been changed — once the name's been edited, that's already a fork in
                            // progress, so Save alone (via the naming dialog, since it's no longer
                            // blank) covers it instead of a separate, redundant action here. Compared
                            // against `baseline.name` (what's actually on disk) rather than looking
                            // the id up in `savedTemplateSets` directly, so this doesn't miss a
                            // just-saved brand-new template before its Flow emission catches up.
                            if (editingTemplateId != null && templateName.trim() == baseline.name) {
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.save_as_template)) },
                                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) },
                                    onClick = {
                                        showOverflowMenu = false
                                        requestNaming(applyAll = null)
                                    }
                                )
                            }
                        }
                        DropdownMenu(expanded = showTemplatePicker, onDismissRequest = { showTemplatePicker = false }) {
                            DropdownMenuItem(
                                text = { Text(getText(R.string.default_template_name)) },
                                onClick = {
                                    localFront = emptyList()
                                    localBack = emptyList()
                                    editingTemplateId = null
                                    templateName = ""
                                    baseline = TemplateFieldSnapshot(emptyList(), emptyList(), "")
                                    showTemplatePicker = false
                                }
                            )
                            savedTemplateSets.forEach { templateSet ->
                                DropdownMenuItem(
                                    text = { Text(templateSet.name) },
                                    onClick = {
                                        localFront = templateSet.frontFields
                                        localBack = templateSet.backFields
                                        templateName = templateSet.name
                                        // Now editing this template in place — Save mutates this
                                        // same row; Save As forks a copy instead.
                                        editingTemplateId = templateSet.id
                                        baseline = TemplateFieldSnapshot(templateSet.frontFields, templateSet.backFields, templateSet.name.trim())
                                        showTemplatePicker = false
                                    },
                                    trailingIcon = {
                                        androidx.compose.material3.IconButton(onClick = {
                                            templateSetToDelete = templateSet
                                            showTemplatePicker = false
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = getText(R.string.delete))
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    getText(R.string.define_default_note_fields_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingSmall))

                LazyColumn(modifier = Modifier.weight(1f)) {
                    item {
                        // Always visible (not conditional on Enforce) — any change made here can
                        // reach existing cards once applied, not just ones locked by Enforce.
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            Text(
                                getText(R.string.enforce_backup_advisory),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onOpenExportDialog) { Text(getText(R.string.export), style = MaterialTheme.typography.labelLarge) }
                        }
                        Spacer(Modifier.height(dimensions.spacingMedium))
                    }
                    item {
                        // Scrolls with the rest now, rather than permanently pinned — Save/Save As/
                        // Apply still work without ever having scrolled here, via the naming dialog.
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = templateName,
                                onValueChange = { templateName = it },
                                singleLine = true,
                                label = { Text(getText(R.string.template_name)) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                            )
                            // Only once there's an actual shared template this deck is linked to —
                            // more discoverable here than buried in the overflow menu, since
                            // "stop using a template" is otherwise easy to miss entirely (blanking
                            // the editor and pressing Apply silently does nothing). Meaningless
                            // without a deck in context, so hidden in footerShowsApplyButtons's
                            // false (global-library-edit) mode too.
                            if (editingTemplateId != null && footerShowsApplyButtons) {
                                Spacer(Modifier.width(dimensions.spacingSmall))
                                Button(
                                    onClick = {
                                        onRemoveTemplate()
                                        // Stays open — the removal already happened, but this is
                                        // now a blank/unsaved editor like any fresh "Default" open,
                                        // not a reason to leave.
                                        localFront = emptyList()
                                        localBack = emptyList()
                                        editingTemplateId = null
                                        templateName = ""
                                        baseline = TemplateFieldSnapshot(emptyList(), emptyList(), "")
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                    ),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) {
                                    Text(getText(R.string.remove), style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        Spacer(Modifier.height(dimensions.spacingMedium))
                    }
                    item { Text(getText(R.string.front_note_templates), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(bottom = 8.dp)) }
                    item { LockedFieldRow(getText(R.string.front)) }
                    itemsIndexed(localFront) { index, template ->
                        TemplateRow(
                            template = template,
                            index = index,
                            onUpdate = { updated -> localFront = localFront.toMutableList().apply { this[index] = updated } },
                            onRemove = { localFront = localFront.toMutableList().apply { removeAt(index) } },
                            onMoveUp = if (index > 0) { { localFront = localFront.toMutableList().apply { add(index - 1, removeAt(index)) } } } else null,
                            onMoveDown = if (index < localFront.lastIndex) {
                                { localFront = localFront.toMutableList().apply { add(index + 1, removeAt(index)) } }
                            } else {
                                // Last front field's "down" carries it across to the back side,
                                // same convention as a card's own note fields.
                                {
                                    localFront = localFront.toMutableList().apply { removeAt(index) }
                                    localBack = listOf(template) + localBack
                                }
                            }
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { localFront = localFront + NoteField(name = "", content = "", type = MediaType.PLAIN_TEXT) },
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
                            index = index,
                            onUpdate = { updated -> localBack = localBack.toMutableList().apply { this[index] = updated } },
                            onRemove = { localBack = localBack.toMutableList().apply { removeAt(index) } },
                            onMoveUp = if (index > 0) {
                                { localBack = localBack.toMutableList().apply { add(index - 1, removeAt(index)) } }
                            } else {
                                // First back field's "up" carries it across to the front side,
                                // same convention as a card's own note fields.
                                {
                                    localBack = localBack.toMutableList().apply { removeAt(index) }
                                    localFront = localFront + template
                                }
                            },
                            onMoveDown = if (index < localBack.lastIndex) { { localBack = localBack.toMutableList().apply { add(index + 1, removeAt(index)) } } } else null
                        )
                    }
                    item {
                        Spacer(Modifier.height(8.dp))
                        FilledTonalButton(
                            onClick = { localBack = localBack + NoteField(name = "", content = "", type = MediaType.PLAIN_TEXT) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text(getText(R.string.add_back_field), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel), style = MaterialTheme.typography.labelLarge) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        if (!footerShowsApplyButtons) {
                            // Settings' global-library-edit mode: no deck in context for Apply to
                            // mean anything against, so just a plain Save — closes once it
                            // persists (see saveTemplate's doc comment).
                            Button(
                                onClick = { saveTemplate() },
                                enabled = hasChanges,
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) { Text(getText(R.string.save), style = MaterialTheme.typography.labelLarge) }
                        } else if (hasExistingCards) {
                            // Never disabled on name alone — pressing either one either applies
                            // directly (nothing changed since opening this template) or asks for/
                            // confirms a name first via the naming dialog, rather than silently
                            // refusing to do anything until the user notices why.
                            OutlinedButton(
                                onClick = { applyTemplate(applyAll = false) },
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) { Text(getText(R.string.apply_to_new), style = MaterialTheme.typography.labelLarge) }
                            Button(
                                onClick = { applyTemplate(applyAll = true) },
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) { Text(getText(R.string.apply_to_all), style = MaterialTheme.typography.labelLarge) }
                        } else {
                            Button(
                                onClick = { applyTemplate(applyAll = false) },
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) { Text(getText(R.string.apply), style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
        }
    }

    if (showNameDialog) {
        // Explicit width override (matching the main editor dialog above) — without it the
        // platform's default dialog width shrinks toward the 280.dp floor, which isn't wide enough
        // for three buttons (Cancel/Save As/Save) on one row without wrapping their text.
        AnimatedDialog(onDismissRequest = { showNameDialog = false }, properties = DialogProperties(usePlatformDefaultWidth = false)) {
            Surface(
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp,
                modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                    Text(
                        // Only ever offers a real choice once a template already existed — a
                        // brand-new one just needs a name, there's nothing yet to fork from.
                        getText(if (editingTemplateId != null) R.string.save_as_template else R.string.template_name),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    OutlinedTextField(
                        value = nameDialogDraft,
                        onValueChange = { nameDialogDraft = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                    )
                    Spacer(Modifier.height(dimensions.spacingLarge))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showNameDialog = false }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                            // Save As is the primary action here when it's offered at all — this
                            // dialog only exists because something changed on an existing template,
                            // and forking a copy is the safer default over mutating the shared one.
                            if (editingTemplateId != null) {
                                OutlinedButton(
                                    onClick = { resolveNaming(forceFork = false) },
                                    enabled = nameDialogDraft.isNotBlank(),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) { Text(getText(R.string.save)) }
                                Button(
                                    onClick = { resolveNaming(forceFork = true) },
                                    enabled = nameDialogDraft.isNotBlank(),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) { Text(getText(R.string.save_as_template)) }
                            } else {
                                Button(
                                    onClick = { resolveNaming(forceFork = false) },
                                    enabled = nameDialogDraft.isNotBlank(),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) { Text(getText(R.string.save)) }
                            }
                        }
                    }
                }
            }
        }
    }

    templateSetToDelete?.let { toDelete ->
        AnimatedDialog(onDismissRequest = { templateSetToDelete = null }) {
            Surface(
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Filled.Emergency, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Text(getText(R.string.delete_template_question), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    Text(stringResource(R.string.delete_template_confirm, toDelete.name), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    val usedByDecks = remember(toDelete.id) { decksUsingTemplate(toDelete.id) }
                    Text(
                        if (usedByDecks.isEmpty()) {
                            getText(R.string.delete_template_not_in_use)
                        } else {
                            pluralStringResource(
                                R.plurals.delete_template_in_use,
                                usedByDecks.size,
                                usedByDecks.size,
                                usedByDecks.joinToString(", ") { "\"$it\"" }
                            )
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (usedByDecks.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(dimensions.spacingLarge))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(
                            onClick = { templateSetToDelete = null },
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) { Text(getText(R.string.cancel)) }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Button(
                            onClick = {
                                onDeleteTemplateSet(toDelete)
                                // Nothing left to edit in place — fall back to a blank, unsaved
                                // template rather than keep pointing at a now-deleted id. Also
                                // propagates to the host screen's own deferred template link/seed
                                // fields (onRemoveTemplate) — without this, closing and reopening
                                // the Template Editor without ever pressing Save/Apply brought the
                                // just-deleted template right back, since the host screen's own
                                // `activeTemplateSetId`/front-back templates were never told.
                                if (editingTemplateId == toDelete.id) {
                                    editingTemplateId = null
                                    localFront = emptyList()
                                    localBack = emptyList()
                                    templateName = ""
                                    baseline = TemplateFieldSnapshot(emptyList(), emptyList(), "")
                                    onRemoveTemplate()
                                }
                                templateSetToDelete = null
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) { Text(getText(R.string.delete)) }
                    }
                }
            }
        }
    }
}

/**
 * Settings' Templates section's "Apply" picker — checklist of every deck, linking+seeding
 * whichever ones get checked to [templateName]'s template (via [onConfirm]'s deck-id set). Same
 * "Apply to New" semantics as the per-deck Template Editor's own Apply button, just across however
 * many decks are chosen here instead of the one deck that dialog is always scoped to; existing
 * cards are never touched. "Apply to All" (this template's split button's dropdown, not this
 * dialog) skips the picker entirely rather than offering a "select all" shortcut here.
 */
@Composable
fun ApplyTemplateToDecksDialog(
    templateName: String,
    decks: List<DeckWithCards>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val selected = remember { mutableStateOf<Set<String>>(emptySet()) }
    AnimatedDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxHeight(0.8f)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    stringResource(R.string.apply_template_to_decks_title, templateName),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))
                if (decks.isEmpty()) {
                    Text(
                        getText(R.string.no_decks_to_apply_template),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingMedium),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        itemsIndexed(decks, key = { _, deck -> deck.deck.id }) { _, deckWithCards ->
                            val deckId = deckWithCards.deck.id
                            val isChecked = deckId in selected.value
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selected.value = if (isChecked) selected.value - deckId else selected.value + deckId }
                                    .padding(vertical = 4.dp)
                            ) {
                                androidx.compose.material3.Checkbox(checked = isChecked, onCheckedChange = {
                                    selected.value = if (isChecked) selected.value - deckId else selected.value + deckId
                                })
                                Text(deckWithCards.deck.name, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(
                        onClick = { onConfirm(selected.value) },
                        enabled = selected.value.isNotEmpty(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) { Text(getText(R.string.apply)) }
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
        OutlinedTextField(
            value = label,
            onValueChange = {},
            readOnly = true,
            enabled = false,
            label = { Text(getText(R.string.field_name)) },
            modifier = Modifier.weight(1f),
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = OutlinedTextFieldDefaults.colors(
                disabledTextColor = MaterialTheme.colorScheme.onSurface
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
    index: Int,
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
        OutlinedTextField(
            value = template.name,
            onValueChange = { onUpdate(template.copy(name = it)) },
            modifier = Modifier.weight(1f),
            label = { Text(getText(R.string.field_name)) },
            placeholder = { Text(stringResource(R.string.field_number_placeholder_format, index + 1)) },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
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
