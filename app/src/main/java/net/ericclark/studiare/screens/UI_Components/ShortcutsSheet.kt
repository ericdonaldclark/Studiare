package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.KeyboardShortcutsContent
import net.ericclark.studiare.LocalIsDesktopWidth
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * A side sheet that's nothing but [KeyboardShortcutsContent] - for a screen with keyboard
 * shortcuts to show but no sort/options of its own, so a tab row would have nothing to switch
 * between (unlike [SortShortcutsSheetHost], which always has at least a Sort tab alongside
 * Shortcuts). Same modal/docked mechanics as every other side sheet in this app (a *standard*
 * docked panel on desktop-sized width via [LocalIsDesktopWidth], a *modal* scrim-backed overlay
 * otherwise, the same close-X), just with no tab row at all since there's only ever the one thing
 * to show. [isOpen] is false when the sheet is closed, in which case [content] renders exactly as
 * it did before this wrapper existed.
 */
@Composable
fun ShortcutsSheetHost(
    viewModel: FlashcardViewModel,
    screenId: ShortcutScreen,
    isOpen: Boolean,
    onDismiss: () -> Unit,
    padding: PaddingValues,
    content: @Composable (PaddingValues) -> Unit
) {
    val isWideLayout = LocalIsDesktopWidth.current

    if (isWideLayout && isOpen) {
        Row(modifier = Modifier.padding(padding).fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                content(PaddingValues(0.dp))
            }
            VerticalDivider()
            ShortcutsSheetPanel(viewModel, screenId, onDismiss)
        }
    } else {
        content(padding)
        if (isOpen) {
            ShortcutsSheetModal(viewModel, screenId, onDismiss)
        }
    }
}

/** Compact/medium width: an edge-anchored overlay with a scrim, dismissible by tapping outside it. */
@Composable
private fun ShortcutsSheetModal(viewModel: FlashcardViewModel, screenId: ShortcutScreen, onDismiss: () -> Unit) {
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
            ShortcutsSheetBody(
                viewModel = viewModel,
                screenId = screenId,
                onClose = onDismiss,
                modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge)
            )
        }
    }
}

/** Desktop-sized width: a persistent, docked panel (no scrim) meant to sit as the trailing item of a [Row]. */
@Composable
private fun ShortcutsSheetPanel(viewModel: FlashcardViewModel, screenId: ShortcutScreen, onDismiss: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    Surface(
        modifier = Modifier.fillMaxHeight().width(380.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        ShortcutsSheetBody(
            viewModel = viewModel,
            screenId = screenId,
            onClose = onDismiss,
            modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge)
        )
    }
}

/** The header/content shared by both [ShortcutsSheetModal] and [ShortcutsSheetPanel]. */
@Composable
private fun ShortcutsSheetBody(viewModel: FlashcardViewModel, screenId: ShortcutScreen, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val dimensions = LocalStudiareDimensions.current
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(getText(R.string.keyboard_shortcuts), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = getText(R.string.close_capitalized))
            }
        }
        Spacer(Modifier.height(dimensions.spacingMedium))
        Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
            KeyboardShortcutsContent(viewModel = viewModel, currentScreen = screenId, listMaxHeight = 2000.dp)
        }
    }
}
