package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.KeyboardShortcutsContent
import net.ericclark.studiare.LocalIsDesktopWidth
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.TooltipIconButton
import net.ericclark.studiare.components.MaterialSymbol
import net.ericclark.studiare.components.MaterialSymbols
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * Which tab of a Sort + Shortcuts side sheet is showing. Shared by every screen that has its own
 * sort controls plus keyboard shortcuts but no study session (Study Hub, Decks, Set Manager) -
 * each supplies its own [SortShortcutsSheetHost.sortContent]; the Shortcuts tab itself is always
 * the same [KeyboardShortcutsContent].
 */
enum class SortShortcutsSheetTab { SORT, SHORTCUTS }

/**
 * A screen's single entry point into its [SortShortcutsSheetHost] — one side-navigation icon
 * (same Material Symbol as a study session's own gear/side-nav button, [SessionOptionsAction])
 * standing in for what used to be two separate icons (a Sort button and a standalone
 * keyboard-shortcuts button): both tools live as tabs inside the one sheet this opens to the Sort
 * tab.
 */
@Composable
fun SortShortcutsSheetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    TooltipIconButton(description = getText(R.string.sort_shortcuts_sheet_title), onClick = onClick, modifier = modifier) {
        MaterialSymbol(codepoint = MaterialSymbols.SIDE_NAVIGATION, contentDescription = getText(R.string.sort_shortcuts_sheet_title))
    }
}

/**
 * A Sort + Shortcuts side sheet — the same modal/docked mechanics as the study-session side sheet
 * ([SessionSheetHost]: a *standard* docked panel on desktop-sized width via [LocalIsDesktopWidth],
 * a *modal* scrim-backed overlay otherwise, the same close-X, tab row, and M3 Expressive lateral
 * tab transition) but hosting a screen's own Sort and Keyboard Shortcuts tools instead of a study
 * session's own tabs. [sheetTab] is `null` when the sheet is closed, in which case [content]
 * renders exactly as it did before this wrapper existed. [sortContent] is the
 * [SortShortcutsSheetTab.SORT] tab's body — built by the caller (e.g. [SessionSortContent],
 * [net.ericclark.studiare.screens.Dialogs.DeckSortContent]) since it carries that screen's own
 * sort state.
 */
@Composable
fun SortShortcutsSheetHost(
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: SortShortcutsSheetTab?,
    onTabChange: (SortShortcutsSheetTab) -> Unit,
    onDismiss: () -> Unit,
    padding: PaddingValues,
    sortContent: @Composable () -> Unit,
    content: @Composable (PaddingValues) -> Unit
) {
    val isWideLayout = LocalIsDesktopWidth.current

    if (isWideLayout) {
        // AnimatedVisibility's exit transition keeps composing its content for the duration of the
        // animation, even after `sheetTab` itself has already gone back to null - so without this,
        // the panel would vanish instantly (before the shrink/fade finishes) instead of animating
        // out. Remembering the last non-null tab keeps something valid to render throughout.
        var lastSheetTab by remember { mutableStateOf(sheetTab ?: SortShortcutsSheetTab.entries.first()) }
        LaunchedEffect(sheetTab) { sheetTab?.let { lastSheetTab = it } }
        val motionScheme = MaterialTheme.motionScheme
        // Starts at false regardless of sheetTab's actual initial value so the enter transition
        // still plays on this screen's very first composition instead of the panel just
        // appearing fully-sized - a plain `visible = sheetTab != null` skips animating whenever
        // true is also the initial value, since AnimatedVisibility treats that as "was already
        // visible."
        val panelVisibleState = remember { MutableTransitionState(false) }
        panelVisibleState.targetState = sheetTab != null

        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content(PaddingValues(0.dp))
            }
            AnimatedVisibility(
                visibleState = panelVisibleState,
                enter = expandHorizontally(animationSpec = motionScheme.defaultSpatialSpec(), expandFrom = Alignment.End) +
                    fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                exit = shrinkHorizontally(animationSpec = motionScheme.defaultSpatialSpec(), shrinkTowards = Alignment.End) +
                    fadeOut(animationSpec = motionScheme.fastEffectsSpec())
            ) {
                Row {
                    VerticalDivider()
                    SortShortcutsSheetPanel(viewModel, screenId, lastSheetTab, onTabChange, onDismiss, sortContent)
                }
            }
        }
    } else {
        content(padding)
        if (sheetTab != null) {
            SortShortcutsSheetModal(viewModel, screenId, sheetTab, onTabChange, onDismiss, sortContent)
        }
    }
}

/** Compact/medium width: an edge-anchored overlay with a scrim, dismissible by tapping outside it. */
@Composable
private fun SortShortcutsSheetModal(
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: SortShortcutsSheetTab,
    onTabChange: (SortShortcutsSheetTab) -> Unit,
    onDismiss: () -> Unit,
    sortContent: @Composable () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
        contentAlignment = Alignment.CenterEnd,
        enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
        exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
    ) {
        Surface(
            modifier = Modifier.fillMaxHeight().widthIn(max = 400.dp),
            shape = RoundedCornerShape(topStart = dimensions.cornerRadiusLarge, bottomStart = dimensions.cornerRadiusLarge),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            SortShortcutsSheetBody(
                viewModel = viewModel,
                screenId = screenId,
                sheetTab = sheetTab,
                onTabChange = onTabChange,
                onClose = onDismiss,
                sortContent = sortContent,
                modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge)
            )
        }
    }
}

/** Desktop-sized width: a persistent, docked panel (no scrim) meant to sit as the trailing item of a [Row]. */
@Composable
private fun SortShortcutsSheetPanel(
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: SortShortcutsSheetTab,
    onTabChange: (SortShortcutsSheetTab) -> Unit,
    onDismiss: () -> Unit,
    sortContent: @Composable () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Surface(
        modifier = Modifier.fillMaxHeight().width(380.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        SortShortcutsSheetBody(
            viewModel = viewModel,
            screenId = screenId,
            sheetTab = sheetTab,
            onTabChange = onTabChange,
            onClose = onDismiss,
            sortContent = sortContent,
            modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge)
        )
    }
}

/** The header/tab row/tab body shared by both [SortShortcutsSheetModal] and [SortShortcutsSheetPanel]. */
@Composable
private fun SortShortcutsSheetBody(
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: SortShortcutsSheetTab,
    onTabChange: (SortShortcutsSheetTab) -> Unit,
    onClose: () -> Unit,
    sortContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    // Same check the study-session sheet and CustomTopAppBar use to gate a keyboard-shortcuts
    // affordance on - nothing useful to show in the Shortcuts tab without a physical keyboard.
    val hasHardwareKeyboard = androidx.compose.ui.platform.LocalConfiguration.current.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY
    val visibleTabs = remember(hasHardwareKeyboard) {
        if (hasHardwareKeyboard) SortShortcutsSheetTab.entries.toList() else SortShortcutsSheetTab.entries.filter { it != SortShortcutsSheetTab.SHORTCUTS }
    }
    // Covers a keyboard detaching while this sheet happens to be open on the now-hidden tab
    // (Chromebook/tablet with a detachable keyboard) - falls back to Sort instead of leaving the
    // tab row's selection pointed at a tab that's no longer there.
    LaunchedEffect(hasHardwareKeyboard, sheetTab) {
        if (!hasHardwareKeyboard && sheetTab == SortShortcutsSheetTab.SHORTCUTS) onTabChange(SortShortcutsSheetTab.SORT)
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(getText(R.string.sort_shortcuts_sheet_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = getText(R.string.close_capitalized))
            }
        }
        Spacer(Modifier.height(dimensions.spacingMedium))

        // ScrollableTabRow so a tab's content never wraps to a second line, but each tab is given
        // an explicit minimum width of (container width / tab count) so with few/short tabs they
        // still stretch to fill the row evenly instead of bunching up at the start; it only
        // actually scrolls once a tab's content needs more room than that even split provides.
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val tabMinWidth = with(LocalDensity.current) { (constraints.maxWidth / visibleTabs.size).toDp() }
            ScrollableTabRow(
                selectedTabIndex = visibleTabs.indexOf(sheetTab).coerceAtLeast(0),
                edgePadding = 0.dp
            ) {
                Tab(
                    selected = sheetTab == SortShortcutsSheetTab.SORT,
                    onClick = { onTabChange(SortShortcutsSheetTab.SORT) },
                    modifier = Modifier.widthIn(min = tabMinWidth)
                ) {
                    SideSheetTabLabel(Icons.Default.Sort, getText(R.string.sort_shortcuts_sheet_tab_sort))
                }
                if (hasHardwareKeyboard) {
                    Tab(
                        selected = sheetTab == SortShortcutsSheetTab.SHORTCUTS,
                        onClick = { onTabChange(SortShortcutsSheetTab.SHORTCUTS) },
                        modifier = Modifier.widthIn(min = tabMinWidth)
                    ) {
                        SideSheetTabLabel(Icons.Default.Keyboard, getText(R.string.session_sheet_tab_keyboard))
                    }
                }
            }
        }
        Spacer(Modifier.height(dimensions.spacingMedium))

        // Same M3 Expressive lateral tab transition as the study-session sheet - content slides in
        // the direction of travel, combined with a fade; this app's MotionScheme override
        // (ui/theme/Motion.kt) collapses both specs to a snap under reduced motion.
        val motionScheme = MaterialTheme.motionScheme
        AnimatedContent(
            targetState = sheetTab,
            modifier = Modifier.weight(1f, fill = false),
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> if (forward) width else -width } +
                    fadeIn(animationSpec = motionScheme.defaultEffectsSpec()))
                    .togetherWith(
                        slideOutHorizontally(animationSpec = motionScheme.defaultSpatialSpec()) { width -> if (forward) -width else width } +
                            fadeOut(animationSpec = motionScheme.fastEffectsSpec())
                    )
            },
            label = "sortShortcutsSheetTab"
        ) { tab ->
            when (tab) {
                SortShortcutsSheetTab.SORT -> sortContent()
                // listMaxHeight set large: the Sort tab supplies its own scroll internally, and
                // Shortcuts should match that rather than being capped by its standalone-dialog
                // default (which exists only because that dialog has no outer scroll to defer to).
                SortShortcutsSheetTab.SHORTCUTS -> KeyboardShortcutsContent(
                    viewModel = viewModel,
                    currentScreen = screenId,
                    listMaxHeight = 2000.dp
                )
            }
        }
    }
}
