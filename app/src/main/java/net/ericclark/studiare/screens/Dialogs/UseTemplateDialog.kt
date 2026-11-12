package net.ericclark.studiare.screens.Dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Description
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
 *
 * Apply and Enforce are independent toggles per row, not a mutually-exclusive group — neither is
 * selected until used. Enforce is a real, persisted, two-way toggle (its checked state reflects
 * [activeTemplateSetId]; un-checking it is "Remove Enforcement," with no separate button needed).
 * Apply has no ongoing state to reflect — it's a one-time field merge — so its checked "Applied"
 * look is purely this-dialog-session feedback (`remember`'d per row), not restored on reopen.
 */
@Composable
fun UseTemplateDialog(
    templateSets: List<TemplateSet>,
    onDismiss: () -> Unit,
    onApply: (TemplateSet) -> Unit,
    onEnforce: (TemplateSet) -> Unit,
    onOpenTemplateEditor: () -> Unit,
    /** Clears whatever is currently locking the selected card(s) — a per-card Enforce choice and/
     * or the deck's own Enforce policy alike. Invoked by un-checking an already-Enforced row. */
    onRemoveEnforcement: () -> Unit,
    /** The [TemplateSet] id currently enforced on the card, if any and if unambiguous — null for
     * a multi-card selection (no single "the card" to check) or when nothing is enforced. Drives
     * which row's selector starts on Enforce and which row shows the check icon. */
    activeTemplateSetId: String? = null
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
                            val isEnforced = templateSet.id == activeTemplateSetId
                            var appliedThisSession by remember(templateSet.id) { mutableStateOf(false) }
                            // Mutually exclusive with Enforce — Apply already clears Enforce (see
                            // its own onCheckedChange below), and this is the reverse direction:
                            // whenever this row becomes the enforced one, drop its own stale
                            // "Applied" look instead of showing both checked at once.
                            LaunchedEffect(isEnforced) { if (isEnforced) appliedThisSession = false }
                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.spacingSmall)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (isEnforced) Icons.Default.CheckCircle else Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(templateSet.name, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                                }
                                Spacer(Modifier.height(dimensions.spacingSmall))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
                                ) {
                                    ToggleButton(
                                        checked = appliedThisSession,
                                        onCheckedChange = {
                                            appliedThisSession = true
                                            onApply(templateSet)
                                            // Apply means "merge fields, don't lock" — so it also
                                            // clears whatever enforcement might currently be in
                                            // effect (harmless no-op if nothing was enforced).
                                            onRemoveEnforcement()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shapes = ButtonGroupDefaults.connectedLeadingButtonShapes()
                                    ) {
                                        if (appliedThisSession) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                                            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                                        }
                                        Text(getText(if (appliedThisSession) R.string.applied else R.string.apply))
                                    }
                                    ToggleButton(
                                        checked = isEnforced,
                                        onCheckedChange = { checked -> if (checked) onEnforce(templateSet) else onRemoveEnforcement() },
                                        modifier = Modifier.weight(1f),
                                        shapes = ButtonGroupDefaults.connectedTrailingButtonShapes()
                                    ) {
                                        if (isEnforced) {
                                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(ToggleButtonDefaults.IconSize))
                                            Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                                        }
                                        Text(getText(if (isEnforced) R.string.enforced else R.string.enforce))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.close))
                    }
                    FilledTonalButton(onClick = onOpenTemplateEditor, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.open_template_editor))
                    }
                }
            }
        }
    }
}
