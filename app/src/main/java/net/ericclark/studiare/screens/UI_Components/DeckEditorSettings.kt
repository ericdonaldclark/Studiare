package net.ericclark.studiare.screens.UI_Components

import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.DialogAccentCap
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
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
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
    val selected = languages.find { it.first == selectedCode }
        ?: (selectedCode to getText(R.string.unknown))
    ExpressiveDropdownMenuField(
        options = languages,
        selectedOption = selected,
        optionLabel = { it.second },
        onSelect = { onLanguageSelected(it.first) },
        menuMaxHeight = 300.dp
    )
}

/**
 * The repeating accent-color cycle [ExpressiveDropdownMenuField] tints its selected row with.
 * Deliberately avoids tertiary — that role is now reserved for stats/difficulty highlights
 * app-wide, and this is a purely decorative "every Nth row" accent with no such meaning.
 */
@Composable
private fun expressiveSwatchColors(): List<Color> {
    val scheme = MaterialTheme.colorScheme
    return listOf(scheme.primary, scheme.inversePrimary, scheme.secondary)
}

/**
 * A standard Material3 outlined "exposed dropdown menu": a boxy, bordered trigger (rounded
 * corners, not a full pill) that opens a vertical [ExposedDropdownMenu], cycling through the
 * theme's accent roles so the selected row's text and (right-aligned) checkmark are tinted to
 * match. Used for every rounded-edge dropdown in the app — a single shared composable, so this is
 * the one place that needs to change to restyle all of them.
 */
@Composable
fun <T> ExpressiveDropdownMenuField(
    options: List<T>,
    selectedOption: T,
    optionLabel: @Composable (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    menuMaxHeight: Dp = 400.dp
) {
    var expanded by remember { mutableStateOf(false) }
    val swatchColors = expressiveSwatchColors()
    val dimensions = LocalStudiareDimensions.current

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = optionLabel(selectedOption),
            onValueChange = {},
            readOnly = true,
            trailingIcon = {
                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
            },
            colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium) // boxy, not the old full-pill CircleShape
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // rounded menu card, not the default near-square corners
            modifier = Modifier.heightIn(max = menuMaxHeight)
        ) {
            options.forEachIndexed { index, option ->
                val isSelected = option == selectedOption
                val color = swatchColors[index % swatchColors.size]
                DropdownMenuItem(
                    text = { Text(optionLabel(option)) },
                    trailingIcon = if (isSelected) {
                        { Icon(Icons.Default.Check, contentDescription = null, tint = color) }
                    } else null,
                    colors = MenuDefaults.itemColors(
                        textColor = if (isSelected) color else MaterialTheme.colorScheme.onSurface
                    ),
                    // Padding first, then clip+background — the selected pill sits inset within the
                    // row instead of washing the full-width rectangle the plain background() used to.
                    modifier = Modifier
                        .padding(horizontal = dimensions.spacingSmall, vertical = 2.dp)
                        .then(
                            if (isSelected) {
                                Modifier
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .background(color.copy(alpha = 0.2f))
                            } else Modifier
                        ),
                    onClick = {
                        onSelect(option)
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

    // DeckStats' own Card is surfaceContainerHigh, which is the same tone SettingsInfoRow's
    // default alternate-row tint is drawn from — the zebra striping washed out to nearly
    // invisible as a result. surfaceContainerHighest actually contrasts against it.
    val alternateRowColor = MaterialTheme.colorScheme.surfaceContainerHighest

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // Larger rounding
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh) // Stronger contrast
    ) {
        Column(modifier = Modifier.padding(vertical = dimensions.paddingSmall)) {
            Text(
                getText(R.string.deck_statistics),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.paddingLarge)
            )
            Spacer(Modifier.height(dimensions.spacingSmall))

            var row = 0
            SettingsInfoRow(getText(R.string.date_created), dateFormat.format(Date(deckWithCards.deck.createdAt)), isAlternate = row++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow(getText(R.string.last_updated), dateFormat.format(Date(deckWithCards.deck.updatedAt)), isAlternate = row++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow(
                "Last Exported",
                deckWithCards.deck.lastExportedAt?.let { dateFormat.format(Date(it)) } ?: getText(R.string.not_available),
                isAlternate = row++ % 2 == 1,
                alternateColor = alternateRowColor
            )
            SettingsInfoRow(getText(R.string.total_cards), "$totalCards", isAlternate = row++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow(getText(R.string.known_label), "$knownCount", isAlternate = row++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow(getText(R.string.unknown_label), "$unknownCount", isAlternate = row++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow(getText(R.string.due_now_label), "$dueNowCount", isAlternate = row++ % 2 == 1, alternateColor = alternateRowColor)
            deckWithCards.deck.averageQuizScore?.let {
                SettingsInfoRow(getText(R.string.avg_score_label), "${(it * 100).roundToInt()}%", isAlternate = row++ % 2 == 1, alternateColor = alternateRowColor, valueColor = MaterialTheme.colorScheme.tertiary)
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
                SettingsInfoRow("${getText(R.string.difficulty)} ${difficulty.value}", "$count", isAlternate = index % 2 == 1, alternateColor = alternateRowColor, valueColor = MaterialTheme.colorScheme.tertiary)
            }

            Spacer(Modifier.height(dimensions.spacingSmall))
            androidx.compose.material3.HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(dimensions.spacingSmall))

            Text(
                getText(R.string.deck_settings),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = dimensions.paddingLarge)
            )
            val frontLanguageName = remember(deckWithCards.deck.frontLanguage) {
                try { Locale(deckWithCards.deck.frontLanguage).displayLanguage } catch (e: Exception) { deckWithCards.deck.frontLanguage }
            }
            val backLanguageName = remember(deckWithCards.deck.backLanguage) {
                try { Locale(deckWithCards.deck.backLanguage).displayLanguage } catch (e: Exception) { deckWithCards.deck.backLanguage }
            }
            var metaRow = 0
            SettingsInfoRow(
                "Type",
                if (deckWithCards.deck.parentDeckId != null) "Set" else "Deck",
                isAlternate = metaRow++ % 2 == 1,
                alternateColor = alternateRowColor
            )
            SettingsInfoRow(getText(R.string.language_front), frontLanguageName, isAlternate = metaRow++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow(getText(R.string.language_back), backLanguageName, isAlternate = metaRow++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow(getText(R.string.case_normalization), deckWithCards.deck.normalizationType.asString(), isAlternate = metaRow++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow(getText(R.string.card_order), deckWithCards.deck.deckSortMode.asString(), isAlternate = metaRow++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow(getText(R.string.default_prompt_side), deckWithCards.deck.defaultPromptSide.asString(), isAlternate = metaRow++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow("Daily New Card Limit", "${deckWithCards.deck.dailyNewCardLimit}", isAlternate = metaRow++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow("Daily Review Limit", "${deckWithCards.deck.dailyReviewLimit}", isAlternate = metaRow++ % 2 == 1, alternateColor = alternateRowColor)
            SettingsInfoRow("Starred", if (deckWithCards.deck.isStarred) "Yes" else "No", isAlternate = metaRow++ % 2 == 1, alternateColor = alternateRowColor)
        }
    }
}

/** [DeckStats] in a dialog with a Close button — shared by Simple Editor and the Bulk Editor's toolbar. */
@Composable
fun DeckInfoDialog(deckWithCards: DeckWithCards, onDismiss: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    // Previously: a bare Column with no Card/Surface of its own and no scroll modifier anywhere
    // in the chain, so the platform's own dialog window chrome (different corner radius than the
    // rest of the app) showed through, and content taller than the dialog was just hard-clipped
    // with nothing to scroll. Matches the Card + fillMaxHeight(0.85f) + scrollable-column pattern
    // used by DeckSettingsDialog/AdvancedDeckEditorDialog/RichTextEditorDialog.
    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.padding(top = dimensions.paddingSmall)) {
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    DeckStats(deckWithCards = deckWithCards)
                }
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = dimensions.paddingLarge, vertical = dimensions.spacingMedium)
                ) {
                    Text(getText(R.string.close_capitalized))
                }
            }
        }
    }
}

@Composable
fun DeckSettingsDialog(
    initialDeckName: String,
    initialNormalizationType: NormalizationType,
    initialDeckSort: DeckSortMode,
    initialDefaultPromptSide: CardSide,
    initialFrontLanguage: String,
    initialBackLanguage: String,
    isCreatingNewDeck: Boolean = false,
    onDismiss: () -> Unit,
    onSave: (String, NormalizationType, DeckSortMode, CardSide, String, String) -> Unit,
    onClearReviewData: () -> Unit,
    onOpenTemplateEditor: () -> Unit = {},
    canOpenTemplateEditor: Boolean = true,
    // Global (not per-deck) default-editor preference — same StateFlow/setter FlashcardViewModel
    // exposes to Settings' Customization > Editor section, shown here too as a shortcut so it
    // doesn't need its own trip to Settings.
    alwaysOpenBulkEditor: Boolean = false,
    onSetAlwaysOpenBulkEditor: (Boolean) -> Unit = {}
) {
    val dimensions = LocalStudiareDimensions.current
    var deckName by remember { mutableStateOf(initialDeckName) }
    var normalizationType by remember { mutableStateOf(initialNormalizationType) }
    var sortType by remember { mutableStateOf(initialDeckSort) }
    var defaultPromptSide by remember { mutableStateOf(initialDefaultPromptSide) }
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

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            // A fixed dp cap here (what this had before) doesn't adapt to the device's actual
            // available height — on a screen shorter than that cap, the dialog window itself gets
            // clipped by the screen before the cap is ever reached, which is the same underlying
            // problem as before, just at a different threshold. A fraction of the available space
            // (matching AdvancedDeckEditorDialog/RichTextEditorDialog's own sizing) scales with
            // the actual screen instead.
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxHeight(0.85f),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                DialogAccentCap()
                Column(
                    modifier = Modifier
                        .padding(dimensions.paddingLarge)
                        .fillMaxWidth()
                ) {
                Text(getText(R.string.deck_options), style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(dimensions.spacingSmall))

                // Scrollable content area
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false) // Allows scrolling without forcing full screen height
                        .verticalScroll(rememberScrollState())
                ) {
                    // Deck Name Section
                    DialogSection(title = getText(R.string.deck_name), contentTopSpacing = 0.dp, titleColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            getText(R.string.deck_name_explainer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        TextField(
                            value = deckName,
                            onValueChange = { deckName = it },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent
                            )
                        )
                    }

                    // Sorting Section (Card Order)
                    DialogSection(title = stringResource(R.string.sort_card_order), contentTopSpacing = 0.dp, titleColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            getText(R.string.sort_card_order_explainer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        ExpressiveDropdownMenuField(
                            options = DeckSortMode.entries,
                            selectedOption = sortType,
                            optionLabel = { it.asString() },
                            onSelect = { sortType = it }
                        )
                    }

                    // Default Prompt Side Section
                    DialogSection(title = getText(R.string.default_prompt_side), contentTopSpacing = 0.dp, titleColor = MaterialTheme.colorScheme.primary) {
                        Text(
                            getText(R.string.default_prompt_side_explainer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        ExpressiveDropdownMenuField(
                            options = listOf(CardSide.AUTO_BY_DECK, CardSide.AUTO_BY_CARD, CardSide.FRONT, CardSide.BACK),
                            selectedOption = defaultPromptSide,
                            optionLabel = { it.asString() },
                            onSelect = { defaultPromptSide = it }
                        )
                    }

                    // NEW: Language Section
                    DialogSection(title = getText(R.string.language_set), contentTopSpacing = 0.dp, titleColor = MaterialTheme.colorScheme.primary) {
                        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        Text(
                            getText(R.string.language_set_explainer),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            getText(R.string.language_front),
                            style = MaterialTheme.typography.labelMedium
                        )
                        LanguageDropdown(
                            languages = availableLanguages,
                            selectedCode = frontLanguage,
                            onLanguageSelected = { frontLanguage = it }
                        )
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

                // Normalization Section
                DialogSection(title = getText(R.string.case_normalization), contentTopSpacing = 0.dp, titleColor = MaterialTheme.colorScheme.primary) {
                    Text(
                        getText(R.string.case_normalization_explainer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    ExpressiveDropdownMenuField(
                        options = NormalizationType.entries,
                        selectedOption = normalizationType,
                        optionLabel = { it.asString() },
                        onSelect = { normalizationType = it }
                    )
                }

                // Template Editor Section
                DialogSection(title = getText(R.string.advanced_editor), contentTopSpacing = 0.dp, titleColor = MaterialTheme.colorScheme.primary) {
                    Text(
                        getText(R.string.define_default_note_fields_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    OutlinedButton(
                        onClick = { onDismiss(); onOpenTemplateEditor() },
                        enabled = canOpenTemplateEditor,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.advanced_editor))
                    }
                }

                // Default Deck Editor Section (global, not per-deck)
                DialogSection(title = getText(R.string.select_global_default_deck_editor), contentTopSpacing = 0.dp, titleColor = MaterialTheme.colorScheme.primary) {
                    Text(
                        getText(R.string.select_global_default_deck_editor_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    ConnectedButtonGroup(
                        items = listOf(false, true),
                        selected = alwaysOpenBulkEditor,
                        onSelected = onSetAlwaysOpenBulkEditor,
                        labelFor = { if (it) getText(R.string.bulk_editor) else getText(R.string.simple_editor) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Review Data Section
                DialogSection(title = getText(R.string.review_data_section_title), showDivider = false, contentTopSpacing = 0.dp, titleColor = MaterialTheme.colorScheme.primary) {
                    Text(
                        getText(R.string.review_data_clear_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(dimensions.spacingSmall))
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
            }
                Spacer(Modifier.height(dimensions.spacingMedium))

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Button(
                            onClick = { onSave(deckName.trim(), normalizationType, sortType, defaultPromptSide, frontLanguage, backLanguage) },
                            enabled = deckName.isNotBlank(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(if (isCreatingNewDeck) R.string.save_and_create else R.string.save_and_close))
                        }
                    }
                }
                }
            }
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
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog),
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
