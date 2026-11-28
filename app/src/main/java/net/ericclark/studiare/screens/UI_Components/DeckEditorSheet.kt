package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Settings
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

/** Which tab of the Simple Editor's side sheet is showing — Statistics first, per the user's
 *  request, then Sort (the deck's card-order settings, moved out of Deck Settings into their own
 *  tab), then Settings, then (hardware-keyboard-gated, same as every other side sheet) Shortcuts. */
enum class DeckEditorSheetTab { STATISTICS, SORT, SETTINGS, SHORTCUTS }

/**
 * The Simple Editor's single entry point into its [DeckEditorSheetHost] — one side-navigation
 * icon (same Material Symbol as every other side sheet's entry button in this app) standing in
 * for what used to be three separate floating-toolbar/app-bar affordances (Deck Statistics, Deck
 * Settings, and the standalone keyboard-shortcuts button): all four (Sort included) live as tabs
 * inside the one sheet this opens to the Statistics tab.
 */
@Composable
fun DeckEditorSheetButton(onClick: () -> Unit) {
    TooltipIconButton(description = getText(R.string.deck_settings), onClick = onClick) {
        MaterialSymbol(codepoint = MaterialSymbols.SIDE_NAVIGATION, contentDescription = getText(R.string.deck_settings))
    }
}

/**
 * The Simple/Bulk Editor's side sheet — the same modal/docked mechanics as every other side sheet
 * in this app (a *standard* docked panel above some width threshold, a *modal* scrim-backed
 * overlay otherwise, the same close-X, tab row, and M3 Expressive lateral tab transition) but
 * hosting Deck Statistics/Deck Settings/Keyboard Shortcuts instead of a study session's or Study
 * Hub's own tabs. [sheetTab] is `null` when the sheet is closed, in which case [content] renders
 * exactly as it did before this wrapper existed. [statisticsContent]/[settingsContent] are built
 * by the caller since they carry the editor's own deck state.
 *
 * [isWideLayout] decides the docked-vs-modal breakpoint and defaults to [LocalIsDesktopWidth] (the
 * Simple Editor's own threshold). The Bulk Editor passes its own, looser `windowWidthSizeClass !=
 * Compact` instead — the same breakpoint its pre-sheet wide-screen layout already showed Deck
 * Statistics inline at, so that content stays visible under the same circumstances it did before.
 */
@Composable
fun DeckEditorSheetHost(
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: DeckEditorSheetTab?,
    onTabChange: (DeckEditorSheetTab) -> Unit,
    onDismiss: () -> Unit,
    padding: PaddingValues,
    statisticsContent: @Composable () -> Unit,
    sortContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    isWideLayout: Boolean = LocalIsDesktopWidth.current,
    content: @Composable (PaddingValues) -> Unit
) {
    if (isWideLayout) {
        // AnimatedVisibility's exit transition keeps composing its content for the duration of the
        // animation, even after `sheetTab` itself has already gone back to null - so without this,
        // the panel would vanish instantly (before the shrink/fade finishes) instead of animating
        // out. Remembering the last non-null tab keeps something valid to render throughout.
        var lastSheetTab by remember { mutableStateOf(sheetTab ?: DeckEditorSheetTab.entries.first()) }
        LaunchedEffect(sheetTab) { sheetTab?.let { lastSheetTab = it } }
        val motionScheme = MaterialTheme.motionScheme
        // Starts at false regardless of sheetTab's actual initial value (e.g. already open by
        // default in the Bulk Editor) so the enter transition still plays on this screen's very
        // first composition instead of the panel just appearing fully-sized - a plain
        // `visible = sheetTab != null` skips animating whenever true is also the initial value,
        // since AnimatedVisibility treats that as "was already visible."
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
                    DeckEditorSheetPanel(viewModel, screenId, lastSheetTab, onTabChange, onDismiss, statisticsContent, sortContent, settingsContent)
                }
            }
        }
    } else {
        content(padding)
        if (sheetTab != null) {
            DeckEditorSheetModal(viewModel, screenId, sheetTab, onTabChange, onDismiss, statisticsContent, sortContent, settingsContent)
        }
    }
}

/** Compact/medium width: an edge-anchored overlay with a scrim, dismissible by tapping outside it. */
@Composable
private fun DeckEditorSheetModal(
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: DeckEditorSheetTab,
    onTabChange: (DeckEditorSheetTab) -> Unit,
    onDismiss: () -> Unit,
    statisticsContent: @Composable () -> Unit,
    sortContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit
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
            DeckEditorSheetBody(
                viewModel = viewModel,
                screenId = screenId,
                sheetTab = sheetTab,
                onTabChange = onTabChange,
                onClose = onDismiss,
                statisticsContent = statisticsContent,
                sortContent = sortContent,
                settingsContent = settingsContent,
                modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge)
            )
        }
    }
}

/** Desktop-sized width: a persistent, docked panel (no scrim) meant to sit as the trailing item of a [Row]. */
@Composable
private fun DeckEditorSheetPanel(
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: DeckEditorSheetTab,
    onTabChange: (DeckEditorSheetTab) -> Unit,
    onDismiss: () -> Unit,
    statisticsContent: @Composable () -> Unit,
    sortContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    Surface(
        modifier = Modifier.fillMaxHeight().width(380.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        DeckEditorSheetBody(
            viewModel = viewModel,
            screenId = screenId,
            sheetTab = sheetTab,
            onTabChange = onTabChange,
            onClose = onDismiss,
            statisticsContent = statisticsContent,
            sortContent = sortContent,
            settingsContent = settingsContent,
            modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge)
        )
    }
}

/** The header/tab row/tab body shared by both [DeckEditorSheetModal] and [DeckEditorSheetPanel]. */
@Composable
private fun DeckEditorSheetBody(
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    sheetTab: DeckEditorSheetTab,
    onTabChange: (DeckEditorSheetTab) -> Unit,
    onClose: () -> Unit,
    statisticsContent: @Composable () -> Unit,
    sortContent: @Composable () -> Unit,
    settingsContent: @Composable () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    // Same check every other side sheet in this app uses to gate a keyboard-shortcuts tab on -
    // nothing useful to show there without a physical keyboard.
    val hasHardwareKeyboard = androidx.compose.ui.platform.LocalConfiguration.current.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY
    val visibleTabs = remember(hasHardwareKeyboard) {
        if (hasHardwareKeyboard) DeckEditorSheetTab.entries.toList() else DeckEditorSheetTab.entries.filter { it != DeckEditorSheetTab.SHORTCUTS }
    }
    // Covers a keyboard detaching while this sheet happens to be open on the now-hidden tab
    // (Chromebook/tablet with a detachable keyboard) - falls back to Statistics instead of leaving
    // the tab row's selection pointed at a tab that's no longer there.
    LaunchedEffect(hasHardwareKeyboard, sheetTab) {
        if (!hasHardwareKeyboard && sheetTab == DeckEditorSheetTab.SHORTCUTS) onTabChange(DeckEditorSheetTab.STATISTICS)
    }

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(getText(R.string.deck_settings), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
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
                    selected = sheetTab == DeckEditorSheetTab.STATISTICS,
                    onClick = { onTabChange(DeckEditorSheetTab.STATISTICS) },
                    modifier = Modifier.widthIn(min = tabMinWidth)
                ) {
                    SideSheetTabLabel(Icons.Default.Info, getText(R.string.deck_statistics))
                }
                Tab(
                    selected = sheetTab == DeckEditorSheetTab.SORT,
                    onClick = { onTabChange(DeckEditorSheetTab.SORT) },
                    modifier = Modifier.widthIn(min = tabMinWidth)
                ) {
                    SideSheetTabLabel(Icons.Default.Sort, getText(R.string.sort))
                }
                Tab(
                    selected = sheetTab == DeckEditorSheetTab.SETTINGS,
                    onClick = { onTabChange(DeckEditorSheetTab.SETTINGS) },
                    modifier = Modifier.widthIn(min = tabMinWidth)
                ) {
                    SideSheetTabLabel(Icons.Default.Settings, getText(R.string.deck_settings))
                }
                if (hasHardwareKeyboard) {
                    Tab(
                        selected = sheetTab == DeckEditorSheetTab.SHORTCUTS,
                        onClick = { onTabChange(DeckEditorSheetTab.SHORTCUTS) },
                        modifier = Modifier.widthIn(min = tabMinWidth)
                    ) {
                        SideSheetTabLabel(Icons.Default.Keyboard, getText(R.string.session_sheet_tab_keyboard))
                    }
                }
            }
        }
        Spacer(Modifier.height(dimensions.spacingMedium))

        // Same M3 Expressive lateral tab transition as every other side sheet - content slides in
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
            label = "deckEditorSheetTab"
        ) { tab ->
            when (tab) {
                // DeckStats manages its own internal layout (no outer scroll needed here) - same
                // bare content DeckInfoDialog itself wraps in a Card elsewhere.
                DeckEditorSheetTab.STATISTICS -> Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    statisticsContent()
                }
                // DeckCardOrderContent/DeckSettingsContent are both self-contained (their own
                // outer Column/scroll), so they're rendered directly rather than wrapped in
                // another scroll container.
                DeckEditorSheetTab.SORT -> sortContent()
                DeckEditorSheetTab.SETTINGS -> settingsContent()
                // listMaxHeight set large: Settings supplies its own scroll internally, and
                // Shortcuts should match that rather than being capped by its standalone-dialog
                // default (which exists only because that dialog has no outer scroll to defer to).
                DeckEditorSheetTab.SHORTCUTS -> KeyboardShortcutsContent(
                    viewModel = viewModel,
                    currentScreen = screenId,
                    listMaxHeight = 2000.dp
                )
            }
        }
    }
}
