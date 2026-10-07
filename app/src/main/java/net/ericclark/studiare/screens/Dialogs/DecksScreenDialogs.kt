package net.ericclark.studiare.screens.Dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import java.util.*
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.data.*
import androidx.compose.foundation.LocalIndication

@Composable
fun ImportOverwriteDialog(
    decksToOverwrite: List<Deck>,
    onDismiss: () -> Unit,
    onConfirm: (List<String>) -> Unit
) {
    val selectedDeckIds =
        remember { mutableStateListOf(*decksToOverwrite.map { it.id }.toTypedArray()) }
    val deckGroups = remember(decksToOverwrite) {
        val naturalOrderComparator = Comparator<String> { s1, s2 ->
            val regex = Regex("\\d+|\\D+")
            val matches1 = regex.findAll(s1).map { it.value }.toList()
            val matches2 = regex.findAll(s2).map { it.value }.toList()

            for (i in 0 until minOf(matches1.size, matches2.size)) {
                val m1 = matches1[i]
                val m2 = matches2[i]
                if (m1 != m2) {
                    val n1 = m1.toLongOrNull()
                    val n2 = m2.toLongOrNull()
                    if (n1 != null && n2 != null) {
                        return@Comparator n1.compareTo(n2)
                    }
                    return@Comparator m1.compareTo(m2, ignoreCase = true)
                }
            }
            matches1.size.compareTo(matches2.size)
        }
        val mainDecks = decksToOverwrite.filter { it.parentDeckId == null }.sortedBy { it.name }
        val setsByParentId =
            decksToOverwrite.filter { it.parentDeckId != null }.groupBy { it.parentDeckId!! }
        val setComparator =
            Comparator<Deck> { d1, d2 -> naturalOrderComparator.compare(d1.name, d2.name) }
        mainDecks.map { mainDeck ->
            mainDeck to (setsByParentId[mainDeck.id]?.sortedWith(
                setComparator
            ) ?: emptyList())
        }
    }

    val dimensions = net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                Text(getText(R.string.overwrite_existing), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingSmall))
                Column {
                    Text(
                        getText(R.string.select_decks_to_overwrite),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.height(12.dp))
                    LazyColumn(
                        modifier = Modifier
                            .heightIn(max = 300.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    ) {
                        deckGroups.forEach { (mainDeck, sets) ->
                            item(key = mainDeck.id) {
                                OverwriteDeckItem(
                                    deck = mainDeck,
                                    isSelected = mainDeck.id in selectedDeckIds,
                                    onToggle = {
                                        if (mainDeck.id in selectedDeckIds) selectedDeckIds.remove(
                                            mainDeck.id
                                        ) else selectedDeckIds.add(mainDeck.id)
                                    }
                                )
                            }
                            items(sets, key = { it.id }) { set ->
                                OverwriteDeckItem(
                                    deck = set,
                                    isSelected = set.id in selectedDeckIds,
                                    onToggle = {
                                        if (set.id in selectedDeckIds) selectedDeckIds.remove(
                                            set.id
                                        ) else selectedDeckIds.add(set.id)
                                    },
                                    isSet = true
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(onClick = { onConfirm(selectedDeckIds.toList()) }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.overwrite_selected)) }
                }
            }
        }
    }
}

@Composable
internal fun OverwriteDeckItem(
    deck: Deck,
    isSelected: Boolean,
    onToggle: () -> Unit,
    isSet: Boolean = false
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "tileSquish"
    )
    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onToggle() }
            .padding(vertical = 12.dp, horizontal = 16.dp)
            .padding(start = if (isSet) 24.dp else 0.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = null)
        Spacer(Modifier.width(16.dp))
        Text(deck.name, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
fun TopSliderDialogSection(
    options: List<String>,
    selectedMode: String,
    onModeChange: (String) -> Unit
) {
    androidx.compose.material3.SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = selectedMode == mode,
                onClick = { onModeChange(mode) },
                shape = androidx.compose.material3.SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size
                )
            ) {
                Text(text = mode, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}


@Composable
fun DuplicateWarningDialog(
    result: DuplicateCheckResult,
    onDismiss: () -> Unit,
    onConfirmRemove: () -> Unit,
    onConfirmSaveAnyway: () -> Unit
) {
    val dimensions = net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                Text(getText(R.string.duplicates_found), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingSmall))
                Column {
                    Text(stringResource(R.string.duplicates_found_message, result.deckName))
                    Spacer(Modifier.height(16.dp))
                    LazyColumn(modifier = Modifier.heightIn(max = 150.dp)) {
                        items(result.duplicates) { duplicate ->
                            Text(
                                stringResource(
                                    R.string.duplicate_item_format,
                                    duplicate.text,
                                    duplicate.count
                                ), style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
                Spacer(Modifier.height(dimensions.spacingLarge))
                Column(horizontalAlignment = Alignment.End, modifier = Modifier.fillMaxWidth()) {
                    Button(onClick = onConfirmRemove, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.remove_and_save)) }
                    TextButton(onClick = onConfirmSaveAnyway, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.save_anyway)) }
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                }
            }
        }
    }
}

@Composable
fun DeckSortDialog(
    currentSortMode: DeckSortMode,
    onDismiss: () -> Unit,
    onSortModeSelected: (DeckSortMode) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current

    // Filter out the difficulty sort options for Decks
    val options = listOf(
        DeckSortMode.A_TO_Z,
        DeckSortMode.Z_TO_A,
        DeckSortMode.DATE_ADDED_NEW_TO_OLD,
        DeckSortMode.DATE_ADDED_OLD_TO_NEW,
        DeckSortMode.DATE_MODIFIED_NEW_TO_OLD,
        DeckSortMode.DATE_MODIFIED_OLD_TO_NEW
    )

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp), // M3 Expressive Dialog Shape
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 600.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = getText(R.string.sort_decks),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    items(options) { mode ->
                        SelectableDialogItem(
                            text = mode.asString(),
                            isSelected = mode == currentSortMode,
                            onClick = {
                                onSortModeSelected(mode)
                                onDismiss()
                            }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.cancel))
                    }
                }
            }
        }
    }
}
