package net.ericclark.studiare.screens.Dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.PreferenceEntry
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText

/** Whether [text] can be saved as [type]. Only number and boolean types are checked; text is always accepted. */
internal fun isValidPreferenceText(type: String, text: String): Boolean = when (type) {
    "Boolean" -> text == "true" || text == "false"
    "Int" -> text.trim().toIntOrNull() != null
    "Long" -> text.trim().toLongOrNull() != null
    "Float" -> text.trim().toFloatOrNull() != null
    "Double" -> text.trim().toDoubleOrNull() != null
    else -> true
}

/** The value shown in the table: the stored value, or the default with "(default)" after it. */
@Composable
private fun displayValue(entry: PreferenceEntry): String {
    val shown = entry.value ?: entry.defaultValue ?: return getText(R.string.pref_not_set)
    return if (entry.value == null || entry.value == entry.defaultValue) {
        getText(R.string.pref_default_value).format(shown)
    } else {
        shown
    }
}

/** Full-screen table of every saved preference. Booleans flip inline; other types open a small edit dialog. */
@Composable
fun SavedPreferencesDialog(viewModel: FlashcardViewModel, onDismiss: () -> Unit) {
    val entries by viewModel.savedPreferences.collectAsState()
    var editing by remember { mutableStateOf<PreferenceEntry?>(null) }
    var confirmResetAll by remember { mutableStateOf(false) }
    val rowShade = MaterialTheme.colorScheme.surfaceContainerHigh

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(modifier = Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.pref_close))
                    }
                    Text(
                        getText(R.string.saved_preferences_title),
                        style = MaterialTheme.typography.headlineSmall,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { confirmResetAll = true }) {
                        Text(getText(R.string.pref_reset_all))
                    }
                }
                Text(
                    getText(R.string.saved_preferences_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )

                // Table header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(getText(R.string.pref_col_name), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.42f))
                    Text(getText(R.string.pref_col_type), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.2f))
                    Text(getText(R.string.pref_col_value), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.38f))
                }

                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    itemsIndexed(entries) { index, entry ->
                        // Zebra striping: every other row takes the shaded colour
                        val rowBackground = if (index % 2 == 1) rowShade else MaterialTheme.colorScheme.surface
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(rowBackground)
                                .then(if (entry.editable && entry.type != "Boolean") Modifier.clickable { editing = entry } else Modifier)
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(0.42f)) {
                                Text(entry.name, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                if (!entry.editable) {
                                    Text(getText(R.string.pref_read_only), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            Text(entry.type, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(0.2f))
                            Row(modifier = Modifier.weight(0.38f), verticalAlignment = Alignment.CenterVertically) {
                                if (entry.type == "Boolean" && entry.editable) {
                                    Switch(
                                        checked = entry.value == "true",
                                        onCheckedChange = { viewModel.savePreference(entry.name, "Boolean", it.toString()) }
                                    )
                                    if (entry.value == null) {
                                        Spacer(Modifier.width(8.dp))
                                        Text(getText(R.string.pref_default_suffix), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    val usingDefault = entry.value == null || entry.value == entry.defaultValue
                                    Text(
                                        displayValue(entry),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = if (usingDefault) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmResetAll) {
        AlertDialog(
            onDismissRequest = { confirmResetAll = false },
            text = { Text(getText(R.string.pref_reset_all_confirm)) },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.resetAllPreferences()
                    confirmResetAll = false
                }) { Text(getText(R.string.pref_reset_all)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmResetAll = false }) { Text(getText(R.string.cancel)) }
            }
        )
    }

    editing?.let { entry ->
        PreferenceEditDialog(
            entry = entry,
            onSave = { text ->
                viewModel.savePreference(entry.name, entry.type, text)
                editing = null
            },
            onReset = {
                viewModel.clearPreference(entry.name, entry.type)
                editing = null
            },
            onDismiss = { editing = null }
        )
    }
}

@Composable
private fun PreferenceEditDialog(
    entry: PreferenceEntry,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(entry.value ?: "") }
    val valid = isValidPreferenceText(entry.type, text)
    val isNumber = entry.type in setOf("Int", "Long", "Float", "Double")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(entry.name, fontFamily = FontFamily.Monospace) },
        text = {
            Column {
                Text(entry.type, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = !entry.type.startsWith("Set"),
                    isError = !valid,
                    supportingText = if (!valid) {
                        { Text(getText(R.string.pref_invalid_number).format(entry.type)) }
                    } else if (entry.type.startsWith("Set")) {
                        { Text("Comma-separated") }
                    } else null,
                    placeholder = entry.defaultValue?.let { default -> { Text(getText(R.string.pref_default_value).format(default)) } }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(text) }, enabled = valid) { Text(getText(R.string.save)) }
        },
        dismissButton = {
            Row {
                TextButton(onClick = onReset) { Text(getText(R.string.pref_reset_default)) }
                TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
            }
        }
    )
}
