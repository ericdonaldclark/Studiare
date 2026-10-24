package net.ericclark.studiare.screens.Dialogs

import androidx.activity.compose.BackHandler
import net.ericclark.studiare.TooltipFilledTonalIconButton
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.TooltipIconButton
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuDropdownProvider
import androidx.compose.foundation.text.contextmenu.provider.LocalTextContextMenuToolbarProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuDataProvider
import androidx.compose.foundation.text.contextmenu.provider.TextContextMenuProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.ParagraphStyle
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import net.ericclark.studiare.components.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.LaunchedEffect
import net.ericclark.studiare.R
import com.mohamedrejeb.richeditor.model.rememberRichTextState
import com.mohamedrejeb.richeditor.ui.material3.RichTextEditor
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.window.DialogProperties

/**
 * Icon-only formatting/clipboard toolbar button: filled-tonal when [active], plain otherwise.
 * [Modifier.focusProperties] with `canFocus = false` keeps every one of these from stealing
 * Compose focus away from the rich text field when tapped — without that, tapping any toolbar
 * button would collapse whatever text the user had selected, since focus normally moves to
 * whatever was just clicked. [RichTextState]'s own selection is plain state on the state object
 * (not derived from focus), so as long as focus never actually leaves the field, a selection
 * survives any number of consecutive toolbar taps.
 */
@Composable
private fun RichTextToggleButton(
    description: String,
    active: Boolean,
    onClick: () -> Unit,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val buttonModifier = Modifier
        .focusProperties { canFocus = false }
        .pressSquish(interactionSource)
    if (active) {
        TooltipFilledTonalIconButton(
            description = description,
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            interactionSource = interactionSource,
            content = content
        )
    } else {
        TooltipIconButton(
            description = description,
            onClick = onClick,
            enabled = enabled,
            modifier = buttonModifier,
            interactionSource = interactionSource,
            content = content
        )
    }
}

/** One circular swatch in a text/highlight color picker; `color == null` renders the "clear" option. */
@Composable
private fun ColorSwatch(color: Color?, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .focusProperties { canFocus = false }
            .clip(CircleShape)
            .then(if (color != null) Modifier.background(color) else Modifier)
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (color == null) {
            Icon(
                Icons.Default.FormatColorReset,
                contentDescription = "Clear color",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun RichTextEditorDialog(
    initialHtml: String?,
    title: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val state = rememberRichTextState()

    // Snapshot of the HTML right after loading (not the raw initialHtml input itself, since
    // setHtml/toHtml round-tripping can normalize formatting even with zero edits) — comparing
    // against this instead of initialHtml avoids a false "dirty" the instant the dialog opens.
    var cleanHtmlSnapshot by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(Unit) {
        if (!initialHtml.isNullOrBlank()) {
            state.setHtml(initialHtml)
        }
        cleanHtmlSnapshot = state.toHtml()
    }
    val isDirty = cleanHtmlSnapshot != null && state.toHtml() != cleanHtmlSnapshot

    var showUnsavedChangesDialog by remember { mutableStateOf(false) }
    val requestDismiss = {
        if (isDirty) showUnsavedChangesDialog = true else onDismiss()
    }

    // Suppresses the platform's native text-selection popup, which otherwise blocks the
    // formatting toolbar below. This Compose Foundation version defaults
    // ComposeFoundationFlags.isNewContextMenuEnabled = true, which routes selection UI through
    // LocalTextContextMenuToolbarProvider/LocalTextContextMenuDropdownProvider rather than the
    // classic LocalTextToolbar — overriding only the latter (tried first) left the native popup
    // showing, since it's simply not consulted anymore under this flag. Providing non-null no-op
    // providers for both is what ProvideDefaultPlatformTextContextMenuProviders checks for before
    // it would otherwise wire up the real, functional ones itself.
    val noOpContextMenuProvider = remember {
        object : TextContextMenuProvider {
            override suspend fun showTextContextMenu(dataProvider: TextContextMenuDataProvider) {
                // Intentionally does nothing — see comment above.
            }
        }
    }

    // Cut/Copy/Paste/Select All are implemented directly against RichTextState's own public
    // selection/text API rather than by capturing native toolbar callbacks (which depends on
    // internals liable to change, as the above comment's whole premise demonstrates), so these
    // work regardless of whichever context-menu system happens to be active.
    val clipboardManager = LocalClipboardManager.current
    fun selectedPlainText(): String {
        val selection = state.selection
        if (selection.collapsed) return ""
        val length = state.annotatedString.length
        val start = selection.min.coerceIn(0, length)
        val end = selection.max.coerceIn(0, length)
        return state.annotatedString.text.substring(start, end)
    }

    // Workaround for an apparent library rendering quirk: applying a SpanStyle to an *existing*
    // selection (addSpanStyle(style, range)) correctly updates the underlying data (confirmed —
    // it's there after save/reload) but doesn't always repaint the live editor. A plain
    // selection-only nudge (collapse then restore) did NOT fix this (confirmed by the user
    // retesting) — RichTextState.updateTextFieldValue takes a different internal branch for a
    // selection-only change that skips rebuilding the field's visualTransformation entirely, so
    // nothing forces BasicTextField to re-read the freshly-styled annotatedString. A full
    // setHtml(toHtml()) round-trip definitely rebuilds it (that's the documented code path a
    // save-and-reopen already takes, which is exactly why reopening always showed it correctly),
    // so this re-parses in place and immediately restores the selection synchronously, before the
    // next frame renders, so the user shouldn't see the cursor jump to the end that setHtml alone
    // would otherwise cause.
    fun nudgeRedraw() {
        val savedSelection = state.selection
        state.setHtml(state.toHtml())
        val length = state.annotatedString.length
        state.selection = TextRange(
            savedSelection.start.coerceIn(0, length),
            savedSelection.end.coerceIn(0, length)
        )
    }

    // Underline and strikethrough are both carried on the same SpanStyle.textDecoration field, so
    // toggling one has to preserve the other if it's already active, rather than just overwriting it.
    fun toggleDecoration(target: TextDecoration) {
        val current = state.currentSpanStyle.textDecoration ?: TextDecoration.None
        val others = listOf(TextDecoration.Underline, TextDecoration.LineThrough)
            .filter { it != target && current.contains(it) }
        state.removeSpanStyle(SpanStyle(textDecoration = current))
        val newSet = if (current.contains(target)) others else others + target
        if (newSet.isNotEmpty()) state.addSpanStyle(SpanStyle(textDecoration = TextDecoration.combine(newSet)))
    }

    // Subscript and superscript are mutually exclusive (both map to baselineShift).
    fun toggleBaselineShift(target: BaselineShift) {
        val current = state.currentSpanStyle.baselineShift
        state.removeSpanStyle(SpanStyle(baselineShift = BaselineShift.Subscript))
        state.removeSpanStyle(SpanStyle(baselineShift = BaselineShift.Superscript))
        if (current != target) state.addSpanStyle(SpanStyle(baselineShift = target))
    }

    // Stand-in for a heading: the library has no public heading API, so this just bumps the
    // selected text's size — still useful for emphasizing a key term on a flashcard.
    val emphasisFontSize = 1.25.em
    fun toggleEmphasis() {
        if (state.currentSpanStyle.fontSize == emphasisFontSize) {
            state.removeSpanStyle(SpanStyle(fontSize = emphasisFontSize))
        } else {
            state.addSpanStyle(SpanStyle(fontSize = emphasisFontSize))
        }
    }

    val presetColors = remember {
        listOf(
            Color(0xFFE53935), Color(0xFFFB8C00), Color(0xFF43A047),
            Color(0xFF1E88E5), Color(0xFF8E24AA), Color(0xFF6D4C41)
        )
    }
    // Highlight colors are semi-transparent (unlike presetColors' solid text colors) so the
    // system's own text-selection overlay still shows through and stays visible on top of a
    // highlighted range — a fully opaque background otherwise makes a selection within
    // highlighted text impossible to see.
    val highlightColors = remember { presetColors.map { it.copy(alpha = 0.4f) } }
    var showTextColorPicker by remember { mutableStateOf(false) }
    var showHighlightColorPicker by remember { mutableStateOf(false) }
    var showLinkPopup by remember { mutableStateOf(false) }
    var linkUrl by remember { mutableStateOf("") }
    // Captured the moment each dropdown-opening button is tapped, since opening the dropdown can
    // disturb the field's live selection before the user actually picks a swatch/types a link —
    // applying styles against this instead of state.selection by then is what makes those
    // actions reliably affect the text the user actually selected.
    var textColorSelectionSnapshot by remember { mutableStateOf(TextRange.Zero) }
    var highlightSelectionSnapshot by remember { mutableStateOf(TextRange.Zero) }
    var linkSelectionSnapshot by remember { mutableStateOf(TextRange.Zero) }

    if (showUnsavedChangesDialog) {
        UnsavedChangesDialog(
            onDismiss = { showUnsavedChangesDialog = false },
            onDiscard = {
                showUnsavedChangesDialog = false
                onDismiss()
            },
            onSave = {
                showUnsavedChangesDialog = false
                onSave(state.toHtml())
            }
        )
    }

    // System back press and tap-outside-to-dismiss are both turned off here (dismissOnBackPress/
    // dismissOnClickOutside = false) — routing the earlier attempt's dirty-check through
    // AnimatedDialog's own Dialog(onDismissRequest = ...) didn't actually intercept back presses
    // on this build, confirmed by the user retesting with no change at all. BackHandler is a
    // separate, directly-testable AndroidX API that doesn't depend on Dialog's own internal
    // dismiss-triggering, so back press now goes through the exact same requestDismiss the X
    // button already uses. Tap-outside is simply disabled rather than also wired up, since the
    // user only asked about the X button and back gesture.
    BackHandler(onBack = requestDismiss)

    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                topBar = {
                    TopAppBar(
                        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        navigationIcon = {
                            TooltipIconButton(description = getText(R.string.cancel), onClick = requestDismiss) {
                                Icon(Icons.Default.Close, getText(R.string.cancel))
                            }
                        },
                        actions = {
                            Button(
                                onClick = { onSave(state.toHtml()) },
                                modifier = Modifier.padding(end = dimensions.paddingSmall),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) {
                                Text(getText(R.string.save))
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                },
                bottomBar = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(vertical = dimensions.paddingSmall)
                    ) {
                        // Row 1: inline formatting + clipboard
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = dimensions.paddingSmall),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentDecoration = state.currentSpanStyle.textDecoration ?: TextDecoration.None

                            RichTextToggleButton(
                                description = "Bold",
                                active = state.currentSpanStyle.fontWeight == FontWeight.Bold,
                                onClick = { state.toggleSpanStyle(SpanStyle(fontWeight = FontWeight.Bold)); nudgeRedraw() }
                            ) { Icon(Icons.Default.FormatBold, "Bold") }

                            RichTextToggleButton(
                                description = "Italic",
                                active = state.currentSpanStyle.fontStyle == FontStyle.Italic,
                                onClick = { state.toggleSpanStyle(SpanStyle(fontStyle = FontStyle.Italic)); nudgeRedraw() }
                            ) { Icon(Icons.Default.FormatItalic, "Italic") }

                            RichTextToggleButton(
                                description = "Underline",
                                active = currentDecoration.contains(TextDecoration.Underline),
                                onClick = { toggleDecoration(TextDecoration.Underline); nudgeRedraw() }
                            ) { Icon(Icons.Default.FormatUnderlined, "Underline") }

                            RichTextToggleButton(
                                description = "Strikethrough",
                                active = currentDecoration.contains(TextDecoration.LineThrough),
                                onClick = { toggleDecoration(TextDecoration.LineThrough); nudgeRedraw() }
                            ) { Icon(Icons.Default.FormatStrikethrough, "Strikethrough") }

                            RichTextToggleButton(
                                description = "Subscript",
                                active = state.currentSpanStyle.baselineShift == BaselineShift.Subscript,
                                onClick = { toggleBaselineShift(BaselineShift.Subscript); nudgeRedraw() }
                            ) { Icon(Icons.Default.Subscript, "Subscript") }

                            RichTextToggleButton(
                                description = "Superscript",
                                active = state.currentSpanStyle.baselineShift == BaselineShift.Superscript,
                                onClick = { toggleBaselineShift(BaselineShift.Superscript); nudgeRedraw() }
                            ) { Icon(Icons.Default.Superscript, "Superscript") }

                            RichTextToggleButton(
                                description = "Code",
                                active = state.isCodeSpan,
                                onClick = { state.toggleCodeSpan(); nudgeRedraw() }
                            ) { Icon(Icons.Default.Code, "Code") }

                            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 2.dp))

                            RichTextToggleButton(
                                description = "Clear formatting",
                                active = false,
                                onClick = { state.clearSpanStyles(); nudgeRedraw() }
                            ) { Icon(Icons.Default.FormatClear, "Clear formatting") }

                            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 2.dp))

                            val hasSelection = !state.selection.collapsed

                            RichTextToggleButton(
                                description = "Cut",
                                active = false,
                                enabled = hasSelection,
                                onClick = {
                                    val text = selectedPlainText()
                                    if (text.isNotEmpty()) {
                                        clipboardManager.setText(AnnotatedString(text))
                                        state.removeSelectedText()
                                    }
                                }
                            ) { Icon(Icons.Default.ContentCut, "Cut") }

                            RichTextToggleButton(
                                description = getText(R.string.copy),
                                active = false,
                                enabled = hasSelection,
                                onClick = {
                                    val text = selectedPlainText()
                                    if (text.isNotEmpty()) clipboardManager.setText(AnnotatedString(text))
                                }
                            ) { Icon(Icons.Default.ContentCopy, getText(R.string.copy)) }

                            RichTextToggleButton(
                                description = "Paste",
                                active = false,
                                onClick = {
                                    val clipped = clipboardManager.getText()?.text
                                    if (!clipped.isNullOrEmpty()) state.replaceSelectedText(clipped)
                                }
                            ) { Icon(Icons.Default.ContentPaste, "Paste") }

                            RichTextToggleButton(
                                description = getText(R.string.select_all),
                                active = false,
                                enabled = state.annotatedString.isNotEmpty(),
                                onClick = {
                                    // Toggles to deselect when everything is already selected,
                                    // rather than reassigning the exact same TextRange again.
                                    // That reassignment is a genuine library bug, not a style
                                    // issue: RichTextState.selection's setter unconditionally
                                    // triggers a rebuild through updateTextFieldValue, and when
                                    // the "new" selection is identical to the current one,
                                    // updateTextFieldValue's own branching treats that as NOT a
                                    // pure selection change and falls into its full-rebuild path
                                    // instead — which applies the library's own "hide background
                                    // color under an active selection" behavior, and nothing
                                    // forces another rebuild afterwards to restore it once
                                    // deselected. Only ever assigning a genuinely different
                                    // TextRange avoids that rebuild path entirely.
                                    val fullRange = TextRange(0, state.annotatedString.length)
                                    state.selection = if (state.selection == fullRange) {
                                        TextRange(fullRange.end)
                                    } else {
                                        fullRange
                                    }
                                }
                            ) { Icon(Icons.Default.SelectAll, getText(R.string.select_all)) }
                        }

                        Spacer(Modifier.height(dimensions.spacingSmall))

                        // Row 2: structure + color
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                                .padding(horizontal = dimensions.paddingSmall),
                            horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val currentAlign = state.currentParagraphStyle.textAlign ?: TextAlign.Start
                            val alignOptions = listOf(
                                TextAlign.Left to Icons.Default.FormatAlignLeft,
                                TextAlign.Center to Icons.Default.FormatAlignCenter,
                                TextAlign.Right to Icons.Default.FormatAlignRight
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                                alignOptions.forEachIndexed { index, (align, icon) ->
                                    ToggleButton(
                                        checked = currentAlign == align,
                                        onCheckedChange = { state.toggleParagraphStyle(ParagraphStyle(textAlign = align)); nudgeRedraw() },
                                        shapes = when (index) {
                                            0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                            alignOptions.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                            else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp),
                                        modifier = Modifier.focusProperties { canFocus = false }
                                    ) { Icon(icon, contentDescription = null) }
                                }
                            }

                            RichTextToggleButton(
                                description = "Bulleted list",
                                active = state.isUnorderedList,
                                onClick = { state.toggleUnorderedList(); nudgeRedraw() }
                            ) { Icon(Icons.Default.FormatListBulleted, "Bulleted list") }

                            RichTextToggleButton(
                                description = "Numbered list",
                                active = state.isOrderedList,
                                onClick = { state.toggleOrderedList(); nudgeRedraw() }
                            ) { Icon(Icons.Default.FormatListNumbered, "Numbered list") }

                            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 2.dp))

                            Box {
                                RichTextToggleButton(
                                    description = "Text color",
                                    active = state.currentSpanStyle.color != Color.Unspecified,
                                    onClick = {
                                        // The dropdown popup opening can disturb the field's live
                                        // selection before a swatch is actually tapped, so the
                                        // range is captured now and applied explicitly below
                                        // instead of relying on state.selection/currentSpanStyle
                                        // being unchanged by the time that happens.
                                        textColorSelectionSnapshot = state.selection
                                        showTextColorPicker = true
                                    }
                                ) { Icon(Icons.Default.FormatColorText, "Text color") }
                                DropdownMenu(expanded = showTextColorPicker, onDismissRequest = { showTextColorPicker = false }) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = dimensions.paddingSmall, vertical = dimensions.paddingSmall / 2),
                                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                                    ) {
                                        presetColors.forEach { swatch ->
                                            ColorSwatch(
                                                color = swatch,
                                                selected = state.currentSpanStyle.color == swatch,
                                                onClick = {
                                                    if (!textColorSelectionSnapshot.collapsed) {
                                                        state.addSpanStyle(SpanStyle(color = swatch), textColorSelectionSnapshot)
                                                        nudgeRedraw()
                                                    }
                                                    showTextColorPicker = false
                                                }
                                            )
                                        }
                                        ColorSwatch(
                                            color = null,
                                            selected = false,
                                            onClick = {
                                                if (!textColorSelectionSnapshot.collapsed) {
                                                    state.removeSpanStyle(SpanStyle(color = Color.Black), textColorSelectionSnapshot)
                                                    nudgeRedraw()
                                                }
                                                showTextColorPicker = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box {
                                RichTextToggleButton(
                                    description = "Highlight",
                                    active = state.currentSpanStyle.background != Color.Unspecified,
                                    onClick = {
                                        highlightSelectionSnapshot = state.selection
                                        showHighlightColorPicker = true
                                    }
                                ) { Icon(Icons.Default.FormatColorFill, "Highlight") }
                                DropdownMenu(expanded = showHighlightColorPicker, onDismissRequest = { showHighlightColorPicker = false }) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = dimensions.paddingSmall, vertical = dimensions.paddingSmall / 2),
                                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                                    ) {
                                        highlightColors.forEach { swatch ->
                                            ColorSwatch(
                                                color = swatch,
                                                selected = state.currentSpanStyle.background == swatch,
                                                onClick = {
                                                    if (!highlightSelectionSnapshot.collapsed) {
                                                        state.addSpanStyle(SpanStyle(background = swatch), highlightSelectionSnapshot)
                                                        nudgeRedraw()
                                                    }
                                                    showHighlightColorPicker = false
                                                }
                                            )
                                        }
                                        ColorSwatch(
                                            color = null,
                                            selected = false,
                                            onClick = {
                                                if (!highlightSelectionSnapshot.collapsed) {
                                                    state.removeSpanStyle(SpanStyle(background = Color.Black), highlightSelectionSnapshot)
                                                    nudgeRedraw()
                                                }
                                                showHighlightColorPicker = false
                                            }
                                        )
                                    }
                                }
                            }

                            Box {
                                RichTextToggleButton(
                                    description = "Link",
                                    active = state.isLink,
                                    onClick = {
                                        linkUrl = ""
                                        linkSelectionSnapshot = state.selection
                                        showLinkPopup = true
                                    }
                                ) { Icon(Icons.Default.Link, "Link") }
                                DropdownMenu(expanded = showLinkPopup, onDismissRequest = { showLinkPopup = false }) {
                                    Column(modifier = Modifier.padding(dimensions.paddingMedium).width(240.dp)) {
                                        TextField(
                                            value = linkUrl,
                                            onValueChange = { linkUrl = it },
                                            placeholder = { Text("https://...") },
                                            modifier = Modifier.fillMaxWidth(),
                                            singleLine = true
                                        )
                                        Spacer(Modifier.height(dimensions.spacingSmall))
                                        Button(
                                            onClick = {
                                                if (linkUrl.isNotBlank() && !linkSelectionSnapshot.collapsed) {
                                                    state.addLinkToTextRange(linkUrl.trim(), linkSelectionSnapshot)
                                                    nudgeRedraw()
                                                }
                                                showLinkPopup = false
                                            },
                                            modifier = Modifier.align(Alignment.End),
                                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                        ) { Text("Add") }
                                    }
                                }
                            }

                            VerticalDivider(modifier = Modifier.height(32.dp).padding(horizontal = 2.dp))

                            RichTextToggleButton(
                                description = "Emphasize",
                                active = state.currentSpanStyle.fontSize == emphasisFontSize,
                                onClick = { toggleEmphasis(); nudgeRedraw() }
                            ) { Icon(Icons.Default.Title, "Emphasize") }
                        }
                    }
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .padding(paddingValues)
                        .fillMaxSize()
                        .padding(dimensions.paddingMedium)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(dimensions.paddingMedium)
                    ) {
                        CompositionLocalProvider(
                            LocalTextContextMenuToolbarProvider provides noOpContextMenuProvider,
                            LocalTextContextMenuDropdownProvider provides noOpContextMenuProvider
                        ) {
                            RichTextEditor(
                                state = state,
                                modifier = Modifier.fillMaxSize(),
                                placeholder = { Text(getText(R.string.start_typing_placeholder)) }
                            )
                        }
                    }
                }
            }
        }
    }
}
