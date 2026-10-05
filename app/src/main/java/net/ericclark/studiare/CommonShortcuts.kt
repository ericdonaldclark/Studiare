package net.ericclark.studiare

import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextOverflow
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.data.*
import androidx.compose.ui.graphics.Color
import net.ericclark.studiare.components.getText
import kotlinx.coroutines.delay
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer

/**
 * The pill-shaped category switcher shown by both [KeyboardShortcutsDialog] and the Settings →
 * Keyboard remapping screen, so browsing "which part of the app" a shortcut belongs to always
 * looks and behaves the same.
 */
@Composable
fun ShortcutCategoryChips(
    categories: List<String>,
    selectedCategory: String,
    onCategorySelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
    ) {
        categories.forEach { category ->
            FilterChip(
                selected = selectedCategory == category,
                onClick = { onCategorySelected(category) },
                label = { Text(category, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

@Composable
fun KeyboardShortcutsDialog(
    onDismiss: () -> Unit,
    viewModel: FlashcardViewModel,
    currentScreen: ShortcutScreen
) {
    val dimensions = LocalStudiareDimensions.current
    val currentScreenOnly by viewModel.shortcutsCurrentScreenOnly.collectAsState()
    val remaps by viewModel.shortcutRemaps.collectAsState()

    // Stable order of categories as they first appear in allShortcuts.
    val categories = remember { allShortcuts.map { it.category }.distinct() }
    var selectedCategory by remember { mutableStateOf(categories.first()) }

    val displayedShortcuts = remember(currentScreenOnly, currentScreen, selectedCategory) {
        if (currentScreenOnly) {
            allShortcuts.filter { it.screens.isEmpty() || currentScreen in it.screens }
        } else {
            allShortcuts.filter { it.category == selectedCategory }
        }
    }
    // In "current screen" mode, group the (already screen-filtered) list by category so
    // related shortcuts still read as sections, just without any tab/chip switcher.
    val groupedForCurrentScreen = remember(displayedShortcuts, currentScreenOnly) {
        if (currentScreenOnly) displayedShortcuts.groupBy { it.category } else emptyMap()
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 300.dp, max = 480.dp)) {
                Text(getText(R.string.keyboard_shortcuts), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingMedium))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(getText(R.string.current_screen_only), style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Only show shortcuts that work here",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = currentScreenOnly,
                        onCheckedChange = { viewModel.setShortcutsCurrentScreenOnly(it) }
                    )
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                HorizontalDivider()
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Tabs only make sense when browsing everything; filtered-to-this-screen mode
                // is already a short, flat list with nothing to switch between.
                if (!currentScreenOnly) {
                    ShortcutCategoryChips(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        onCategorySelected = { selectedCategory = it }
                    )
                    Spacer(Modifier.height(dimensions.spacingMedium))
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp) // Cap height to prevent dialog overflow; scrolls beyond that.
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                ) {
                    if (currentScreenOnly) {
                        groupedForCurrentScreen.forEach { (category, entries) ->
                            ShortcutSection(category)
                            entries.forEach { ShortcutItem(it.action, it.displayKeys(remaps)) }
                        }
                    } else {
                        displayedShortcuts.forEach { ShortcutItem(it.action, it.displayKeys(remaps)) }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.close_capitalized))
                    }
                }
            }
        }
    }
}

@Composable
fun ShortcutSection(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
    )
}

@Composable
fun ShortcutItem(action: String, shortcut: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = action,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
        )
        ShortcutKeys(shortcut)
    }
}

/** Renders a key combo string (e.g. "Ctrl + S / Ctrl + ,") as small rounded key badges. */
@Composable
internal fun ShortcutKeys(keys: String) {
    val alternatives = keys.split(" / ")
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
        alternatives.forEachIndexed { altIndex, alternative ->
            if (altIndex > 0) {
                Text(
                    "or",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )
            }
            val parts = alternative.split(" + ")
            parts.forEachIndexed { partIndex, part ->
                if (partIndex > 0) {
                    Text(
                        "+",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                KeyBadge(part.trim())
            }
        }
    }
}

@Composable
internal fun KeyBadge(token: String) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.secondaryContainer
    ) {
        Text(
            text = token,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

class ShortcutRegistry {
    private val actions = mutableMapOf<Key, () -> Unit>()

    fun register(key: Key, action: () -> Unit) {
        actions[key] = action
    }

    // Only removes the mapping if it's still the same action this caller originally registered.
    // During a navigation transition the next screen's composable can register its own action
    // for the same key *before* the previous screen's composable actually disposes (Compose
    // defers that past the transition) - an unconditional remove() here would then delete the
    // new screen's fresh shortcut instead of the stale one being cleaned up.
    fun unregister(key: Key, action: () -> Unit) {
        if (actions[key] === action) {
            actions.remove(key)
        }
    }

    fun trigger(key: Key): Boolean {
        val action = actions[key]
        if (action != null) {
            action()
            return true
        }
        return false
    }
}

val LocalHintMode = compositionLocalOf { false }
val LocalShortcutRegistry = compositionLocalOf<ShortcutRegistry?> { null }

/** How many [AnimatedDialog]s are currently on top of the screen, anywhere in the app. */
object DialogTracker {
    val openCount = mutableIntStateOf(0)
}

/**
 * A screen's root key-handling container often loses Compose focus for good once something
 * else (most commonly a dialog) briefly takes it — nothing else in this app ever asks for it
 * back, so bare single-key shortcuts silently stop firing until the screen is re-entered. This
 * is a drop-in replacement for the usual `.focusRequester(fr).focusable()` (plus a one-shot
 * `LaunchedEffect(Unit) { fr.requestFocus() }`) that keeps re-claiming focus whenever it's lost.
 *
 * It only fires once [FocusState.hasFocus] goes false, which is true for this node *or any
 * descendant* — so a legitimately-focused child already inside this same screen (a search
 * field, an answer input, ...) is left alone; this only steps in once focus has left the
 * subtree entirely. It also backs off while any dialog is open, so it doesn't fight a dialog's
 * own text field for focus.
 */
fun Modifier.autoFocusable(focusRequester: FocusRequester, initialDelayMs: Long = 0): Modifier = composed {
    var hasFocusWithin by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (initialDelayMs > 0) kotlinx.coroutines.delay(initialDelayMs)
        runCatching { focusRequester.requestFocus() }
    }
    LaunchedEffect(hasFocusWithin) {
        if (!hasFocusWithin) {
            // Give whatever's about to open (a dialog, a newly-focused sibling) a moment to
            // actually claim focus before we decide it was lost to nothing.
            kotlinx.coroutines.delay(60)
            if (!hasFocusWithin && DialogTracker.openCount.intValue == 0) {
                runCatching { focusRequester.requestFocus() }
            }
        }
    }

    this
        .focusRequester(focusRequester)
        .focusable()
        .onFocusChanged { hasFocusWithin = it.hasFocus }
}

/** One shortcut hint badge to draw: its label and where its target is, in window coordinates. */
data class HintBadge(val label: String, val bounds: androidx.compose.ui.geometry.Rect)

/**
 * Where every on-screen [withShortcut] target reports its bounds, so [ShortcutHintOverlay] can
 * draw all the hint badges in one layer above the whole UI. Drawing each badge inside its own
 * target (as this used to) put it under anything composed later — e.g. the top app bar's badges
 * sat beneath the collection header row directly below it — and inside any parent's clip.
 */
class HintOverlayState {
    val badges = androidx.compose.runtime.mutableStateMapOf<Any, HintBadge>()
}

val LocalHintOverlay = compositionLocalOf<HintOverlayState?> { null }

fun Modifier.withShortcut(
    key: Key,
    keyLabel: String,
    id: String? = null,
    action: () -> Unit
): Modifier = composed {
    val registry = LocalShortcutRegistry.current
    val overlay = LocalHintOverlay.current

    // Settings → Keyboard lets the user rebind this shortcut's key; when it has, register and
    // show the badge for the remapped key instead of the default one passed in.
    val remaps = LocalShortcutRemaps.current
    val remappedCode = id?.let { remaps[it] }
    val effectiveKey = if (remappedCode != null) Key(remappedCode) else key
    val effectiveLabel = if (remappedCode != null) effectiveKey.displayLabel() else keyLabel

    DisposableEffect(effectiveKey, registry) {
        registry?.register(effectiveKey, action)
        onDispose { registry?.unregister(effectiveKey, action) }
    }

    val badgeKey = remember { Any() }
    DisposableEffect(badgeKey, overlay) {
        onDispose { overlay?.badges?.remove(badgeKey) }
    }
    // A remap changes the label without necessarily moving the target, so refresh it here.
    SideEffect {
        val existing = overlay?.badges?.get(badgeKey)
        if (existing != null && existing.label != effectiveLabel) {
            overlay.badges[badgeKey] = existing.copy(label = effectiveLabel)
        }
    }

    this.onGloballyPositioned { coordinates ->
        if (overlay != null) {
            // boundsInWindow is clipped by ancestors, so a target scrolled or clipped fully out
            // of view reports an empty rect and gets no badge instead of one clamped on-screen.
            val bounds = if (coordinates.isAttached) coordinates.boundsInWindow() else androidx.compose.ui.geometry.Rect.Zero
            if (bounds.isEmpty) {
                overlay.badges.remove(badgeKey)
            } else if (overlay.badges[badgeKey]?.bounds != bounds || overlay.badges[badgeKey]?.label != effectiveLabel) {
                overlay.badges[badgeKey] = HintBadge(effectiveLabel, bounds)
            }
        }
    }
}

/**
 * Draws every registered hint badge above the rest of the UI while Alt is held. Each badge sits
 * below its target by default, flips above if that wouldn't fit, and is clamped to stay fully in
 * frame — never covering the target it labels except when the target spans nearly the whole window.
 */
@Composable
fun ShortcutHintOverlay(state: HintOverlayState, visible: Boolean) {
    if (!visible) return
    val textMeasurer = rememberTextMeasurer()
    var origin by remember { mutableStateOf(Offset.Zero) }
    // Badges must stay inside the area the user can actually see: on a Chromebook the shelf (and
    // on phones the system bars/keyboard) can cover part of the window without shrinking it.
    val density = androidx.compose.ui.platform.LocalDensity.current
    val layoutDirection = androidx.compose.ui.platform.LocalLayoutDirection.current
    val safe = WindowInsets.safeDrawing
    val insetLeft = safe.getLeft(density, layoutDirection).toFloat()
    val insetTop = safe.getTop(density).toFloat()
    val insetRight = safe.getRight(density, layoutDirection).toFloat()
    val insetBottom = safe.getBottom(density).toFloat()
    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInWindow() }
    ) {
        val style = TextStyle(color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        val gap = 4.dp.toPx()
        state.badges.values.forEach { badge ->
            val target = badge.bounds.translate(-origin.x, -origin.y)
            val text = textMeasurer.measure(badge.label, style)
            val badgeWidth = text.size.width + 32.dp.toPx()
            val badgeHeight = text.size.height + 16.dp.toPx()

            val minX = insetLeft
            val maxX = size.width - insetRight
            val minY = insetTop
            val maxY = size.height - insetBottom
            val x = (target.center.x - badgeWidth / 2f)
                .coerceIn(minX, (maxX - badgeWidth).coerceAtLeast(minX))
            val below = target.bottom + gap
            val y = (if (below + badgeHeight <= maxY) below else target.top - gap - badgeHeight)
                .coerceIn(minY, (maxY - badgeHeight).coerceAtLeast(minY))

            drawRoundRect(
                color = Color.Black.copy(alpha = 0.85f),
                topLeft = Offset(x, y),
                size = Size(badgeWidth, badgeHeight),
                cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
            )
            drawText(
                textMeasurer = textMeasurer,
                text = badge.label,
                style = style,
                topLeft = Offset(x + 16.dp.toPx(), y + 8.dp.toPx())
            )
        }
    }
}
