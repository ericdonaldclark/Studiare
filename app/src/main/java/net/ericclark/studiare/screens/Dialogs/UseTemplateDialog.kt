package net.ericclark.studiare.screens.Dialogs

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import kotlinx.coroutines.delay
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.TemplateMatchStatus
import net.ericclark.studiare.data.TemplateSet
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * Per-card "Use Template" — picks a saved [TemplateSet] (the same library
 * [AdvancedDeckEditorDialog]'s "Start from Template" picker reads) and Applies it: adds any
 * missing fields onto the card's current ones, one-time. Deliberately no "existing deck template"
 * special-cased entry: the full library is the only source, per the chosen design.
 *
 * Apply has no ongoing state to reflect — it's a one-time field merge — so a row's "Applied" look
 * is purely transient this-press feedback (reverts to "Apply" after 2 seconds). The row's chip,
 * on the other hand, reflects [matchStatusFor] live — recomputed on every recomposition, so it
 * updates right after an Apply actually changes the card's fields — and can show as matching (or
 * partially matching) for more than one row at once if several templates share a shape.
 */
@Composable
fun UseTemplateDialog(
    templateSets: List<TemplateSet>,
    onDismiss: () -> Unit,
    onApply: (TemplateSet) -> Unit,
    onOpenTemplateEditor: () -> Unit,
    /** How the card this dialog is for currently compares to each template's field shape. Always
     * [TemplateMatchStatus.NOT_MATCHING] for a multi-card selection (no single "the card" to check). */
    matchStatusFor: (TemplateSet) -> TemplateMatchStatus = { TemplateMatchStatus.NOT_MATCHING }
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
                            // A fresh non-zero value each press restarts the 2-second window (even
                            // if pressed again before the previous one elapsed); 0L means "not
                            // currently showing the transient Applied feedback."
                            var appliedAt by remember(templateSet.id) { mutableStateOf(0L) }
                            LaunchedEffect(appliedAt) {
                                if (appliedAt != 0L) {
                                    delay(2000)
                                    appliedAt = 0L
                                }
                            }
                            val showingApplied = appliedAt != 0L
                            // Recomputed every recomposition (not `remember`'d) so it reflects the
                            // card's fields live, including right after this template's own Apply
                            // (or any other row's) actually changes them.
                            val matchStatus = matchStatusFor(templateSet)

                            Column(modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.spacingSmall)) {
                                Row(
                                    horizontalArrangement = Arrangement.Start,
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    // Fixed — doesn't swap to a "matching" icon or anything else;
                                    // the chip below is what reflects the card/template comparison.
                                    Icon(
                                        imageVector = Icons.Default.Description,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        templateSet.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                    )
                                }
                                Spacer(Modifier.height(dimensions.spacingSmall))
                                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                    AssistChip(
                                        onClick = {},
                                        label = { Text(matchStatus.asString()) }
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Button(
                                        onClick = {
                                            appliedAt = System.currentTimeMillis()
                                            onApply(templateSet)
                                        },
                                        shape = CircleShape
                                    ) {
                                        Text(getText(if (showingApplied) R.string.applied else R.string.apply))
                                    }
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(onClick = onOpenTemplateEditor, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.open_template_editor))
                    }
                    FilledTonalButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.close))
                    }
                }
            }
        }
    }
}
