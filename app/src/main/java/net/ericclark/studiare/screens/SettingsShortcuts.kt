package net.ericclark.studiare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.key
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*

@Composable
internal fun KeyboardShortcutSettingsContent(viewModel: FlashcardViewModel, initialCategory: String? = null) {
    val dimensions = LocalStudiareDimensions.current
    val showShortcutsButton by viewModel.showShortcutsButton.collectAsState()
    val remaps by viewModel.shortcutRemaps.collectAsState()

    val remappableShortcuts = remember { allShortcuts.filter { it.remappable != null } }
    val categories = remember { remappableShortcuts.map { it.category }.distinct() }
    var selectedCategory by remember { mutableStateOf(initialCategory ?: categories.first()) }
    var listeningFor by remember { mutableStateOf<ShortcutEntry?>(null) }
    var pendingRemap by remember { mutableStateOf<PendingShortcutRemap?>(null) }

    val entriesForCategory = remember(selectedCategory) {
        remappableShortcuts.filter { it.category == selectedCategory }
    }

    listeningFor?.let { entry ->
        ShortcutCaptureDialog(
            entry = entry,
            onKeyCaptured = { key ->
                val conflicts = findShortcutConflicts(remaps, entry.id, key, entry.remappable?.modifierPrefix)
                if (conflicts.isEmpty()) {
                    viewModel.setShortcutRemap(entry.id, key)
                } else {
                    pendingRemap = PendingShortcutRemap(entry, key, conflicts)
                }
                listeningFor = null
            },
            onCancel = { listeningFor = null }
        )
    }

    pendingRemap?.let { pending ->
        ConfirmationDialog(
            title = getText(R.string.key_already_in_use),
            text = stringResource(
                if (pending.conflicts.size == 1) R.string.shortcut_conflict_message_singular else R.string.shortcut_conflict_message_plural,
                pending.conflicts.joinToString(", ") { it.action },
                pending.entry.action
            ),
            confirmButtonText = getText(R.string.reassign_anyway),
            onConfirm = {
                viewModel.setShortcutRemap(pending.entry.id, pending.key)
                pendingRemap = null
            },
            onDismiss = { pendingRemap = null }
        )
    }

    Column {
        SettingSwitchItem(
            "Show shortcuts button",
            "Show the keyboard-shortcuts button in the top bar",
            showShortcutsButton
        ) { viewModel.setShowShortcutsButton(it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

        Text(
            "Remap Shortcuts",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = dimensions.spacingSmall)
        )
        Text(
            "Tap a shortcut's key to rebind it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimensions.spacingMedium)
        )

        ShortcutCategoryChips(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it }
        )

        Spacer(Modifier.height(dimensions.spacingMedium))

        Column {
            entriesForCategory.forEachIndexed { index, entry ->
                val liveConflicts = remember(entry, remaps) {
                    val spec = entry.remappable
                    if (spec == null) {
                        emptyList()
                    } else {
                        val currentKey = remaps[entry.id]?.let { Key(it) } ?: spec.defaultKey
                        findShortcutConflicts(remaps, entry.id, currentKey, spec.modifierPrefix)
                    }
                }
                ShortcutRemapRow(
                    entry = entry,
                    currentDisplay = entry.displayKeys(remaps),
                    isListening = listeningFor?.id == entry.id,
                    isCustomized = entry.remappable != null && remaps.containsKey(entry.id),
                    conflicts = liveConflicts,
                    isAlternate = index % 2 == 1,
                    onStartListening = { listeningFor = entry },
                    onReset = { viewModel.setShortcutRemap(entry.id, null) }
                )
            }
        }
    }
}

@Composable
internal fun ShortcutRemapRow(
    entry: ShortcutEntry,
    currentDisplay: String,
    isListening: Boolean,
    isCustomized: Boolean,
    conflicts: List<ShortcutEntry>,
    isAlternate: Boolean = false,
    onStartListening: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isAlternate) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.action, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))

        if (entry.remappable == null) {
            Text(
                currentDisplay,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (conflicts.isNotEmpty()) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = getText(R.string.shortcut_conflict_warning),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                )
            }
            if (isCustomized) {
                TooltipIconButton(description = getText(R.string.reset_to_default), onClick = onReset, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = getText(R.string.reset_to_default), modifier = Modifier.size(18.dp))
                }
            }
            FilterChip(
                selected = isListening,
                onClick = onStartListening,
                label = { Text(if (isListening) "Press a key…" else currentDisplay, maxLines = 1) },
                shape = RoundedCornerShape(50),
                colors = if (conflicts.isNotEmpty() && !isListening) {
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                }
            )
        }
    }
    if (conflicts.isNotEmpty()) {
        Text(
            "Also used by " + conflicts.joinToString(", ") { it.action },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    }
}
