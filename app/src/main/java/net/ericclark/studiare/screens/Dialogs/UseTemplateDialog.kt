package net.ericclark.studiare.screens.Dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.TemplateSet
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * Per-card "Use Template" — picks a saved [TemplateSet] (the same library
 * [AdvancedDeckEditorDialog]'s "Start from Template" picker reads) and either Applies it (adds
 * any missing fields onto the card's current ones, one-time) or Enforces it (replaces the card's
 * fields with the template's exact shape and locks them — see
 * [net.ericclark.studiare.data.effectiveLockedFields]). Deliberately no "existing deck template"
 * special-cased entry: the full library is the only source, per the chosen design.
 */
@Composable
fun UseTemplateDialog(
    templateSets: List<TemplateSet>,
    onDismiss: () -> Unit,
    onApply: (TemplateSet) -> Unit,
    onEnforce: (TemplateSet) -> Unit,
    onOpenTemplateEditor: () -> Unit,
    /** Clears whatever is currently locking the selected card(s) — a per-card Enforce choice and/
     * or the deck's own Enforce policy alike. Null hides the button entirely; shown unconditionally
     * otherwise (harmless no-op on a card that wasn't locked, same convention as this app's other
     * always-both-directions toggles, e.g. Pin/Unpin). */
    onRemoveEnforcement: (() -> Unit)? = null
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).heightIn(max = 560.dp)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    getText(R.string.use_template_dialog_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Text(
                    getText(R.string.use_template_dialog_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = dimensions.spacingMedium).fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                if (templateSets.isEmpty()) {
                    Text(
                        getText(R.string.no_saved_templates),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.spacingLarge),
                        textAlign = TextAlign.Center
                    )
                } else {
                    LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                        items(templateSets, key = { it.id }) { templateSet ->
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.spacingSmall),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(templateSet.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                OutlinedButton(onClick = { onApply(templateSet) }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                                    Text(getText(R.string.apply))
                                }
                                Spacer(Modifier.width(8.dp))
                                Button(onClick = { onEnforce(templateSet) }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                                    Text(getText(R.string.enforce))
                                }
                            }
                        }
                    }
                }

                if (onRemoveEnforcement != null) {
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    OutlinedButton(
                        onClick = onRemoveEnforcement,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.remove_enforcement))
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                OutlinedButton(
                    onClick = onOpenTemplateEditor,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.height(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(getText(R.string.open_template_editor))
                }
                Spacer(Modifier.height(dimensions.spacingSmall))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.cancel))
                    }
                }
            }
        }
    }
}
