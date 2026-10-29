package net.ericclark.studiare.screens.UI_Components

import net.ericclark.studiare.TooltipFilledTonalIconButton
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.R
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import net.ericclark.studiare.*
import net.ericclark.studiare.components.ColorSwatch
import net.ericclark.studiare.components.SimpleColorPicker
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip

/** Phone settings list row: title and subtitle, opens the category's page on tap. */
@Composable
internal fun SettingsCategoryRow(title: String, subtitle: String?, dimensions: StudiareDimensions, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = dimensions.paddingLarge, vertical = 20.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The "#" circle to the left of a [ColorPickerRow]'s divider — opens a hex-entry dialog for that
 * row. [activeHexColor] is non-null when the row's current color is the one last typed in through
 * this same button, in which case the circle fills with that color and picks up the same
 * selected-style highlight border [ColorSwatch] uses, just without its checkmark (the "#" stays put
 * so the button is still recognizable).
 */
@Composable
private fun HexEntryCircle(activeHexColor: String?, onClick: () -> Unit) {
    val interactionSource = remember { MutableInteractionSource() }
    val parsedColor = activeHexColor?.let { net.ericclark.studiare.components.parseHexColor(it) }
    Box(
        modifier = Modifier
            .size(40.dp)
            .pressSquish(interactionSource, 0.85f)
            .clip(CircleShape)
            .background(parsedColor ?: MaterialTheme.colorScheme.surfaceVariant)
            .border(
                width = if (parsedColor != null) 3.dp else 1.dp,
                color = if (parsedColor != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
                shape = CircleShape
            )
            .clickable(interactionSource = interactionSource, indication = LocalIndication.current, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            "#",
            style = MaterialTheme.typography.titleMedium,
            color = if (parsedColor != null && parsedColor.luminance() <= 0.5f) Color.White
                else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * One color slot in the Custom Theme dialog. Left of the divider: a fixed, always-present swatch
 * ([presetColor]) showing whichever color this slot got from the last top preset button pressed
 * (or the dialog's starting value before any preset is pressed) — it never moves when the user
 * picks a swatch from the palette on the right or types a hex code, only when a preset is pressed
 * again — plus a "#" button that opens a hex-entry dialog for this one slot. Right of the divider:
 * the normal preset palette. [color]'s checkmark can land on both sides at once if the active value
 * happens to equal both the frozen preset color and one of the palette swatches.
 */
@Composable
fun ColorPickerRow(
    label: String,
    color: String,
    presetColor: String,
    lastEnteredHex: String?,
    onHexEntryRequested: () -> Unit,
    description: String? = null,
    onColorChange: (String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        if (description != null) {
            Text(
                description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ColorSwatch(
                hexColor = presetColor,
                isSelected = presetColor.equals(color, ignoreCase = true),
                onClick = { onColorChange(presetColor) }
            )
            Spacer(Modifier.width(dimensions.spacingSmall))
            HexEntryCircle(
                activeHexColor = lastEnteredHex?.takeIf { it.equals(color, ignoreCase = true) },
                onClick = onHexEntryRequested
            )
            Spacer(Modifier.width(dimensions.spacingSmall))
            VerticalDivider(modifier = Modifier.height(40.dp))
            Spacer(Modifier.width(dimensions.spacingSmall))
            // Reusing the SimpleColorPicker from Tags.kt
            SimpleColorPicker(
                selectedColor = color,
                onColorSelected = onColorChange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
internal fun SettingsSubsection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "subsectionChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column { content() }
    }
}

@Composable
fun SettingsInfoRow(
    label: String,
    value: String,
    isAlternate: Boolean = false,
    onClick: (() -> Unit)? = null,
    // Defaults to a tint of the surrounding surface, which washes out to near-invisible when the
    // container itself is already that same tone (e.g. DeckStats' surfaceContainerHigh card) —
    // callers in that situation should pass a color that actually contrasts against their own background.
    alternateColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f),
    // Color.Unspecified preserves the default "inherit LocalContentColor" behavior — only stats/
    // difficulty-highlight rows (tertiary) should pass an explicit override.
    valueColor: Color = Color.Unspecified
) {
    val background = if (isAlternate) alternateColor else Color.Transparent
    // Label and value share the row's width; on a narrow pane they stack instead, so neither gets squeezed
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        val valueStyle = MaterialTheme.typography.titleMedium
        if (maxWidth >= 320.dp) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
                Text(value, style = valueStyle, fontWeight = FontWeight.Bold, textAlign = TextAlign.End, modifier = Modifier.weight(1f), color = valueColor)
            }
        } else {
            Column {
                Text(label, style = MaterialTheme.typography.bodyLarge)
                Text(value, style = valueStyle, fontWeight = FontWeight.Bold, color = valueColor)
            }
        }
    }
}

/**
 * A settings row for a segmented-button control. On the wide two-pane settings layout, the title
 * and description sit in a left column with the segmented buttons sized to their own content and
 * anchored to the right — mirroring the two-pane settings screen's own split, without introducing
 * a second real pane. On the single-column phone layout ([isWideScreen] false), this renders
 * exactly as before: title above, full-width segmented row, optional description below.
 */
@Composable
internal fun SettingsSegmentedSetting(
    isWideScreen: Boolean,
    description: String? = null,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    segmented: @Composable (Modifier) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val segmentedWidth = 440.dp
    val titleMinWidth = 200.dp
    // Title beside the fixed-width buttons only when there's room for both; otherwise stack them like the phone
    // layout, so the title is never squeezed into a narrow column.
    BoxWithConstraints(modifier = modifier.fillMaxWidth()) {
        val sideBySide = isWideScreen && maxWidth >= segmentedWidth + titleMinWidth + dimensions.spacingLarge
        if (sideBySide) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f).padding(end = dimensions.spacingLarge)) {
                    title()
                    if (description != null) {
                        Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                // ConnectedButtonGroup's own "wrap content" sizing comes out narrower than its labels need,
                // so it gets a concrete width to divide among its items.
                segmented(Modifier.width(segmentedWidth))
            }
        } else {
            // Title above, full-width segmented row, optional description below.
            Column(modifier = Modifier.fillMaxWidth()) {
                title()
                segmented(Modifier.fillMaxWidth())
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
                }
            }
        }
    }
}

/** A settings row with a title, description and trailing switch; the whole row toggles it. */
@Composable
internal fun SettingSwitchItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { StudiareSwitch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
    )
}

/** A horizontally-scrolling row of pill `FilterChip`s, generic over any typed item — same visual shape as [ShortcutCategoryChips] but keyed by value instead of a display string, so callers don't need a label↔item round-trip. */
/** The same chips as [TypedChipRow], with every option visible at once and wrapping onto new lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> TypedChipGrid(
    items: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    /** Optional leading icon per item, shown regardless of selected state (unlike
     * [ConnectedButtonGroup]'s default checkmark-when-selected). */
    iconFor: (@Composable (T) -> androidx.compose.ui.graphics.vector.ImageVector)? = null
) {
    val dimensions = LocalStudiareDimensions.current
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
        verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelected(item) },
                label = { Text(labelFor(item), maxLines = 1, softWrap = false) },
                leadingIcon = if (iconFor != null) {
                    { Icon(iconFor(item), contentDescription = null, modifier = Modifier.size(FilterChipDefaults.IconSize)) }
                } else null,
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/**
 * M3 Expressive's replacement for the deprecated `SegmentedButton`: a row of connected
 * [ToggleButton]s sharing one pill-shaped group — full-round outer corners, small inner corners
 * where buttons meet — with the selected item picking out a filled color and (by default) a leading
 * checkmark. Verified against the real M3 1.5.0-alpha15 sources (`ButtonGroupDefaults.connected*ButtonShapes`,
 * the official `SingleSelectConnectedButtonGroupSample`), not guessed.
 *
 * @param iconFor overrides the default checkmark-when-selected leading icon, e.g. for an item that
 *   needs a different icon when selected (a "Custom" theme option showing an edit icon). Receives
 *   `(item, isSelected)`; return nothing to omit the icon for that item/state. When [labelFor] is
 *   null, this becomes the button's entire content (an icon-only button, e.g. a grid/tree view toggle) —
 *   the caller must supply its own `contentDescription` on the icon in that case.
 * @param contentPadding overrides each button's content padding — e.g. for an icon-only group
 *   that needs to be narrow enough to sit in a top app bar's navigation-icon slot. `null` (the
 *   default) keeps every existing call site's current sizing (`ToggleButton`'s own default).
 */
@Composable
internal fun <T> ConnectedButtonGroup(
    items: List<T>,
    selected: T,
    onSelected: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelFor: (@Composable (T) -> String)? = null,
    iconFor: (@Composable (T, Boolean) -> Unit)? = null,
    contentPadding: PaddingValues? = null,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        items.forEachIndexed { index, item ->
            val checked = item == selected
            ToggleButton(
                checked = checked,
                onCheckedChange = { onSelected(item) },
                modifier = Modifier
                    .weight(1f)
                    .semantics { role = Role.RadioButton },
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    items.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
                contentPadding = contentPadding ?: ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight)
            ) {
                when {
                    // Only reserve the icon slot while checked — an unchecked button has no icon
                    // at all here (same as the default-checkmark branch below), so its label sits
                    // alone and centers correctly, matching every other ConnectedButtonGroup.
                    checked && iconFor != null && labelFor != null -> {
                        Box(Modifier.size(ToggleButtonDefaults.IconSize)) { iconFor(item, checked) }
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    }
                    iconFor != null -> iconFor(item, checked)
                    checked -> {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    }
                }
                if (labelFor != null) {
                    Text(labelFor(item), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@Composable
internal fun <T> TypedChipRow(
    items: List<T>,
    selected: T,
    labelFor: @Composable (T) -> String,
    onSelected: (T) -> Unit,
    centered: Boolean = false
) {
    val dimensions = LocalStudiareDimensions.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = if (centered) Arrangement.spacedBy(dimensions.spacingSmall, Alignment.CenterHorizontally)
            else Arrangement.spacedBy(dimensions.spacingSmall)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelected(item) },
                label = { Text(labelFor(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/**
 * One language's voice: its name, what it's for and its size, with progress, a queued note, and the cancel,
 * download or delete control. Shared by Settings → Audio & Voice and the first-run voice prompt.
 */
@Composable
internal fun VoiceLanguageRow(
    languageName: String,
    sizeText: String,
    isDownloaded: Boolean,
    isDownloading: Boolean,
    isQueued: Boolean,
    progress: Float,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null
) {
    val dimensions = LocalStudiareDimensions.current
    ListItem(
        leadingContent = leading,
        headlineContent = { Text(languageName, fontWeight = FontWeight.SemiBold) },
        supportingContent = {
            Column {
                Text(stringResource(R.string.voice_model_desc, languageName, sizeText), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (isDownloading) {
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                    )
                } else if (isQueued) {
                    Text(getText(R.string.voice_queued), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                when {
                    isDownloading -> TextButton(
                        onClick = onCancel,
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) { Text(getText(R.string.cancel)) }
                    isDownloaded -> {
                        Icon(androidx.compose.material.icons.Icons.Default.Check, null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                        if (onDelete != null) {
                            Spacer(Modifier.width(dimensions.spacingSmall))
                            TooltipFilledTonalIconButton(
                                description = getText(R.string.delete),
                                onClick = onDelete,
                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                )
                            ) { Icon(androidx.compose.material.icons.Icons.Default.Delete, getText(R.string.delete)) }
                        }
                    }
                    else -> TooltipFilledTonalIconButton(description = getText(R.string.download), onClick = onDownload) {
                        Icon(androidx.compose.material.icons.Icons.Default.Download, getText(R.string.download))
                    }
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

