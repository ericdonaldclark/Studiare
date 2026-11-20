package net.ericclark.studiare.screens.Dialogs

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.DialogSection
import net.ericclark.studiare.R
import net.ericclark.studiare.StudiareSwitch
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.NormalizationType
import net.ericclark.studiare.data.TextCorrectionSettings
import net.ericclark.studiare.data.applyTextCorrections
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.data.normalizeText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/** Deliberately messy — carries one example of each correction toggle below (leading/trailing
 * whitespace, a doubled internal space, a space before a comma with no space after it, a period
 * glued straight onto the next sentence, and a consecutively repeated word) so toggling any of
 * them produces a visible, real change in every option's preview underneath. */
private const val textNormalizationDemoText = " Lorem ipsum dolor  sit amet ,consectetur adipiscing elit.Sed do eiusmod eiusmod tempor incididunt ut labore. "

/** One of the five toggles in [TextCorrectionSettings], paired with a label and a getter/setter
 * pair so the row below can be generated instead of repeated five times by hand. */
private data class CorrectionToggle(
    val label: String,
    val get: (TextCorrectionSettings) -> Boolean,
    val set: (TextCorrectionSettings, Boolean) -> TextCorrectionSettings
)

private val correctionToggles: List<CorrectionToggle> = listOf(
    CorrectionToggle("Trim Leading & Trailing Whitespace", { it.trimWhitespace }, { s, v -> s.copy(trimWhitespace = v) }),
    CorrectionToggle("Collapse Consecutive Spaces Into One", { it.collapseConsecutiveSpaces }, { s, v -> s.copy(collapseConsecutiveSpaces = v) }),
    CorrectionToggle("Add Space After Punctuation", { it.addSpaceAfterPunctuation }, { s, v -> s.copy(addSpaceAfterPunctuation = v) }),
    CorrectionToggle("Remove Space Before Punctuation", { it.removeSpaceBeforePunctuation }, { s, v -> s.copy(removeSpaceBeforePunctuation = v) }),
    CorrectionToggle("Remove Duplicate Consecutive Words", { it.removeDuplicateConsecutiveWords }, { s, v -> s.copy(removeDuplicateConsecutiveWords = v) })
)

/**
 * Shared by "Card Text Normalization" (per-deck) and "Tag Text Normalization" (one global rule
 * set): pick how text is capitalized, and which whitespace/punctuation corrections run alongside
 * it. [initialType]/[initialCorrections] seed the starting selection; [onConfirm] fires with the
 * chosen [NormalizationType] and [TextCorrectionSettings] when Save is pressed, [onDismiss] on
 * Cancel/outside-tap without committing anything. [title]/[subtitle] default to the card-text
 * copy; the tag-name caller passes its own.
 *
 * [NormalizationType.NONE] is shown on its own above the three collapsible categories (all
 * collapsed by default) — it's the default state, not really a "case" to pick among the rest.
 */
@Composable
fun TextNormalizationDialog(
    initialType: NormalizationType,
    initialCorrections: TextCorrectionSettings = TextCorrectionSettings(),
    title: String = getText(R.string.case_normalization),
    subtitle: String = getText(R.string.text_normalization_dialog_subtitle),
    onDismiss: () -> Unit,
    onConfirm: (NormalizationType, TextCorrectionSettings) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var selectedType by remember { mutableStateOf(initialType) }
    var corrections by remember { mutableStateOf(initialCorrections) }
    // Every category starts collapsed.
    val expandedCategories = remember { mutableStateMapOf<String, Boolean>() }
    val correctedPreviewText = remember(corrections) { applyTextCorrections(corrections, textNormalizationDemoText) }

    @Composable
    fun OptionRow(type: NormalizationType) {
        val selected = type == selectedType
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                .clickable { selectedType = type }
                .background(if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent)
                .padding(dimensions.paddingSmall),
            verticalAlignment = Alignment.Top
        ) {
            RadioButton(selected = selected, onClick = { selectedType = type })
            Column(modifier = Modifier.padding(start = dimensions.spacingSmall)) {
                Text(
                    type.asString(),
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    normalizeText(type, correctedPreviewText),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    @Composable
    fun Category(title: String, types: List<NormalizationType>) {
        val isExpanded = expandedCategories[title] ?: false
        DialogSection(
            title = title,
            subtitle = "${types.size} options",
            isExpanded = isExpanded,
            onToggle = { expandedCategories[title] = !isExpanded },
            contentTopSpacing = dimensions.spacingSmall
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                types.forEach { OptionRow(it) }
            }
        }
    }

    AnimatedDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).heightIn(max = 720.dp)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(top = dimensions.spacingSmall, bottom = dimensions.spacingMedium)
                        .fillMaxWidth()
                )
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                ) {
                    correctionToggles.forEach { toggle ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(toggle.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            StudiareSwitch(
                                checked = toggle.get(corrections),
                                onCheckedChange = { corrections = toggle.set(corrections, it) }
                            )
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingSmall))
                    OptionRow(NormalizationType.NONE)
                    Category("Basic Cases", NormalizationType.basicCases)
                    Category("Editorial & Smart Cases", NormalizationType.editorialAndSmartCases)
                    Category("Programmatic & Novelty Cases", NormalizationType.programmaticAndNoveltyCases)
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(onClick = { onConfirm(selectedType, corrections) }) { Text(getText(R.string.save)) }
                }
            }
        }
    }
}
