package net.ericclark.studiare.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import net.ericclark.studiare.*
import net.ericclark.studiare.components.SimpleColorPicker
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*

/**
 * Measures [heightProbes] off-screen at the same width [content] will actually render at, then
 * calls [content] with the tallest result. Used to give the Remap Shortcuts section a fixed height
 * matching the tallest of its own shortcut categories, instead of resizing (and reflowing
 * everything after it) every time its own content changes size, e.g. switching shortcut
 * categories. Each probe is a fully real, independent composition purely for measurement — it's
 * never placed/drawn, and its own `remember`/state never touches the real render's.
 */
@Composable
internal fun MaxContentHeightLayout(
    heightProbes: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    content: @Composable (maxContentHeight: Dp) -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val probeConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val maxHeightPx = heightProbes.withIndex().maxOfOrNull { (index, probe) ->
            subcompose("probe_$index", probe).maxOf { it.measure(probeConstraints).height }
        } ?: 0
        val maxHeightDp = maxHeightPx.toDp()

        val contentPlaceables = subcompose("realContent") { content(maxHeightDp) }.map { it.measure(constraints) }
        val height = contentPlaceables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            contentPlaceables.forEach { it.place(0, 0) }
        }
    }
}

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

// Helper Composable for Expandable Sections (wide layout only)
@Composable
fun SettingsSectionWrapper(
    title: String,
    subtitle: String?,
    isWideScreen: Boolean,
    dimensions: StudiareDimensions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    // On wide screens, the sections are permanently expanded
    val effectivelyExpanded = isWideScreen || isExpanded

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isWideScreen) { isExpanded = !isExpanded }
                .padding(horizontal = dimensions.paddingLarge, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
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

            // Only show the chevron on narrow screens
            if (!isWideScreen) {
                val rotation by animateFloatAsState(
                    targetValue = if (effectivelyExpanded) 180f else 0f,
                    label = "chevronRotation"
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (effectivelyExpanded) "Collapse" else "Expand",
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }
        }

        // Animated Content Expansion
        AnimatedVisibility(
            visible = effectivelyExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensions.paddingLarge)
                    .padding(bottom = dimensions.paddingMedium)
            ) {
                content()
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun ColorPickerRow(label: String, color: String, onColorChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        // Reusing the SimpleColorPicker from Tags.kt
        SimpleColorPicker(
            selectedColor = color,
            onColorSelected = onColorChange
        )
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
fun SettingsInfoRow(label: String, value: String, isAlternate: Boolean = false, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        colors = ListItemDefaults.colors(containerColor = if (isAlternate) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f) else Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
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
    if (isWideScreen) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = dimensions.spacingLarge)) {
                title()
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            // SingleChoiceSegmentedButtonRow's own "wrap content" sizing (no width modifier) comes
            // out narrower than its labels actually need — confirmed independent of IntrinsicSize
            // hints applied from outside it, so the fix is a concrete width for it to divide among
            // its segments via their internal equal-weight sizing, same as fillMaxWidth already
            // does correctly in the phone layout below, just bounded instead of full-bleed.
            segmented(Modifier.width(440.dp).fillMaxWidth())
        }
    } else {
        // Unchanged from before this row had a wide-screen variant: title's own modifier carries
        // whatever spacing it always had, segmented gets a plain fillMaxWidth, description (if any)
        // sits below with its usual top padding.
        Column(modifier = modifier.fillMaxWidth()) {
            title()
            segmented(Modifier.fillMaxWidth())
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
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
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
    )
}

/** A horizontally-scrolling row of pill `FilterChip`s, generic over any typed item — same visual shape as [ShortcutCategoryChips] but keyed by value instead of a display string, so callers don't need a label↔item round-trip. */
/** The same chips as [TypedChipRow], with every option visible at once and wrapping onto new lines. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun <T> TypedChipGrid(items: List<T>, selected: T, labelFor: @Composable (T) -> String, onSelected: (T) -> Unit) {
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
                shape = RoundedCornerShape(50)
            )
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
