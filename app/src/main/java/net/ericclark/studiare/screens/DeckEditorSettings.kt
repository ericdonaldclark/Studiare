package net.ericclark.studiare.screens

import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.SequencedSelectionChip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.components.*
import net.ericclark.studiare.DialogSection
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.R

@Composable
fun LanguageDropdown(
    languages: List<Pair<String, String>>,
    selectedCode: String,
    onLanguageSelected: (String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var expanded by remember { mutableStateOf(false) }
    val selectedName = languages.find { it.first == selectedCode }?.second ?: getText(R.string.unknown)

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = selectedName,
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            // M3 Expressive Update: Tinted container with no harsh outline borders
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(
                focusedBorderColor = Color.Transparent,
                unfocusedBorderColor = Color.Transparent,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = CircleShape // Enforce the M3 Expressive pill shape
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 300.dp)
        ) {
            languages.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        onLanguageSelected(code)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun DeckStats(deckWithCards: DeckWithCards) {
    val dimensions = LocalStudiareDimensions.current
    val difficultyCounts = deckWithCards.cards.groupingBy { it.difficulty }.eachCount()
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy, h:mm a", Locale.getDefault()) }
    val totalCards = deckWithCards.cards.size
    val knownCount = deckWithCards.cards.count { it.isKnown }
    val unknownCount = totalCards - knownCount
    val now = remember { System.currentTimeMillis() }
    val dueNowCount = deckWithCards.cards.count { it.absoluteDueDate != null && it.absoluteDueDate <= now }

    Card(
        modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // Larger rounding
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) // Stronger contrast
    ) {
        Column(modifier = Modifier.padding(vertical = dimensions.paddingSmall)) {
            Text(
                getText(R.string.deck_statistics),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = dimensions.paddingLarge)
            )
            Spacer(Modifier.height(dimensions.spacingSmall))

            var row = 0
            SettingsInfoRow(getText(R.string.date_created), dateFormat.format(Date(deckWithCards.deck.createdAt)), isAlternate = row++ % 2 == 1)
            SettingsInfoRow(getText(R.string.last_updated), dateFormat.format(Date(deckWithCards.deck.updatedAt)), isAlternate = row++ % 2 == 1)
            SettingsInfoRow(getText(R.string.total_cards), "$totalCards", isAlternate = row++ % 2 == 1)
            SettingsInfoRow(getText(R.string.known_label), "$knownCount", isAlternate = row++ % 2 == 1)
            SettingsInfoRow(getText(R.string.unknown_label), "$unknownCount", isAlternate = row++ % 2 == 1)
            SettingsInfoRow(getText(R.string.due_now_label), "$dueNowCount", isAlternate = row++ % 2 == 1)
            deckWithCards.deck.averageQuizScore?.let {
                SettingsInfoRow(getText(R.string.avg_score_label), "${(it * 100).roundToInt()}%", isAlternate = row++ % 2 == 1)
            }

            Spacer(Modifier.height(dimensions.spacingSmall))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(dimensions.spacingSmall))

            Text(
                getText(R.string.difficulty_breakdown),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimensions.paddingLarge)
            )
            DifficultySetting.entries.forEachIndexed { index, difficulty ->
                val count = difficultyCounts[difficulty] ?: 0
                SettingsInfoRow("${getText(R.string.difficulty)} ${difficulty.value}", "$count", isAlternate = index % 2 == 1)
            }
        }
    }
}

@Composable
fun DeckSettingsDialog(
    initialNormalizationType: NormalizationType,
    initialDeckSort: DeckSortMode,
    initialFrontLanguage: String,
    initialBackLanguage: String,
    onDismiss: () -> Unit,
    onSave: (NormalizationType, DeckSortMode, String, String) -> Unit,
    onClearReviewData: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var normalizationType by remember { mutableStateOf(initialNormalizationType) }
    var sortType by remember { mutableStateOf(initialDeckSort) }
    var frontLanguage by remember { mutableStateOf(initialFrontLanguage) }
    var backLanguage by remember { mutableStateOf(initialBackLanguage) }
    var showClearConfirm by remember { mutableStateOf(false) }

    // Moved from the old LanguageSelectionDialog
    val availableLanguages = remember {
        Locale.getAvailableLocales()
            .map { it.language to it.displayLanguage }
            .filter { it.second.isNotEmpty() }
            .distinctBy { it.first }
            .sortedBy { it.second }
    }

    if (showClearConfirm) {
        AnimatedDialog(onDismissRequest = { showClearConfirm = false }) {
            Surface(
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                    Text(getText(R.string.review_data_clear_question), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Text(getText(R.string.review_data_clear_message))
                    Spacer(Modifier.height(dimensions.spacingLarge))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showClearConfirm = false }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Button(
                            onClick = {
                                showClearConfirm = false
                                onClearReviewData()
                            },
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                            colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                        ) { Text(getText(R.string.clear_data)) }
                    }
                }
            }
        }
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier.padding(dimensions.paddingLarge)
            ) {
                Text(getText(R.string.deck_options), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Scrollable content area
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false) // Allows scrolling without forcing full screen height
                        .verticalScroll(rememberScrollState())
                ) {
                    // NEW: Language Section
                    DialogSection(title = getText(R.string.language_set)) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        Text(
                            getText(R.string.language_front),
                            style = MaterialTheme.typography.labelMedium
                        )
                        LanguageDropdown(
                            languages = availableLanguages,
                            selectedCode = frontLanguage,
                            onLanguageSelected = { frontLanguage = it }
                        )
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        Text(
                            getText(R.string.language_back),
                            style = MaterialTheme.typography.labelMedium
                        )
                        LanguageDropdown(
                            languages = availableLanguages,
                            selectedCode = backLanguage,
                            onLanguageSelected = { backLanguage = it }
                        )
                    }
                }

                Spacer(Modifier.height(dimensions.spacingSmall))

                // Normalization Section
                DialogSection(title = getText(R.string.case_normalization)) {
                    SettingsFilterChipGroup(
                        options = NormalizationType.entries.map { it.asString() },
                        selectedItem = normalizationType.asString(),
                        onSelect = { normalizationType = it.toNormalizationType() }
                    )
                }

                // Sorting Section
                DialogSection(title = stringResource(R.string.sort_card_order)) {
                    SettingsFilterChipGroup(
                        options = DeckSortMode.entries.map { it.asString() },
                        selectedItem = sortType.asString(),
                        onSelect = { sortType = it.toDeckSortMode() }
                    )
                }

                Spacer(Modifier.height(dimensions.spacingLarge))

                OutlinedButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(getText(R.string.review_data_clear))
                }
            }
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(onClick = { onSave(normalizationType, sortType, frontLanguage, backLanguage) }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.save_and_close))
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsFilterChipGroup(options: List<String>, selectedItem: String, onSelect: (String) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
        verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
    ) {
        options.forEach { text ->
            SequencedSelectionChip(
                selected = selectedItem == text,
                onClick = { onSelect(text) },
                label = { Text(text, maxLines = 1, softWrap = false) }
            )
        }
    }
}


@Composable
fun LinkageSettingsDialog(
    currentSettings: LinkageSettings,
    onDismiss: () -> Unit,
    onSave: (LinkageSettings) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var settings by remember { mutableStateOf(currentSettings) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .heightIn(max = 600.dp)
            ) {
                Text(
                    "Set Linkage Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        "Control how this Set syncs with its parent Deck. Unlinking a property allows you to customize it locally.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(Modifier.height(dimensions.spacingMedium))

                    LinkageToggleRow(
                        label = "Sync Card Additions to Parent",
                        isLinked = settings.syncCardAdditions,
                        onToggle = { settings = settings.copy(syncCardAdditions = it) }
                    )
                    LinkageToggleRow(
                        label = "Sync Card Deletions to Parent",
                        isLinked = settings.syncCardDeletions,
                        onToggle = { settings = settings.copy(syncCardDeletions = it) }
                    )
                    LinkageToggleRow(
                        label = "Link Card Data (Text edits)",
                        isLinked = settings.linkCardData,
                        onToggle = { settings = settings.copy(linkCardData = it) }
                    )
                    LinkageToggleRow(
                        label = "Link Field Config (Templates)",
                        isLinked = settings.linkFieldConfig,
                        onToggle = { settings = settings.copy(linkFieldConfig = it) }
                    )
                    LinkageToggleRow(
                        label = "Link Card Order (Sorting)",
                        isLinked = settings.linkCardOrder,
                        onToggle = { settings = settings.copy(linkCardOrder = it) }
                    )
                    LinkageToggleRow(
                        label = "Link Scoring (FSRS weights)",
                        isLinked = settings.linkScoring,
                        onToggle = { settings = settings.copy(linkScoring = it) }
                    )
                    LinkageToggleRow(
                        label = "Link Metadata (Timestamps)",
                        isLinked = settings.linkMetadata,
                        onToggle = { settings = settings.copy(linkMetadata = it) }
                    )
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(onClick = { onSave(settings) }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.apply)) }
                }
            }
        }
    }
}

@Composable
fun LinkageToggleRow(label: String, isLinked: Boolean, onToggle: (Boolean) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!isLinked) }
            .padding(vertical = dimensions.paddingSmall, horizontal = dimensions.paddingSmall),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isLinked) Icons.Default.Link else Icons.Default.LinkOff,
            contentDescription = null,
            tint = if (isLinked) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(dimensions.spacingMedium))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f) // This forces the text to wrap instead of cutting off the switch
        )
        Spacer(Modifier.width(dimensions.spacingSmall))
        androidx.compose.material3.Switch(checked = isLinked, onCheckedChange = onToggle)
    }
}
