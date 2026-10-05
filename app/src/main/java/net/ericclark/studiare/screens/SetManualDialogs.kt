package net.ericclark.studiare.screens

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Check
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.key.key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.draw.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass

@Composable
fun ManualSetCreatorDialog(
    parentDeck: DeckWithCards,
    viewModel: FlashcardViewModel,
    onDismiss: () -> Unit
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val dimensions = LocalStudiareDimensions.current
    val motionScheme = MaterialTheme.motionScheme
    var setName by rememberSaveable { mutableStateOf("") }
    var isEditingName by rememberSaveable { mutableStateOf(false) }
    val selectedCards = remember { mutableStateListOf<Card>() }

    val availableCards = remember(parentDeck.cards, selectedCards.toList()) {
        parentDeck.cards.filter { it !in selectedCards }
    }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .fillMaxSize()
            ) {
                Text(
                    text = getText(R.string.pick_and_choose),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                if (isEditingName) {
                    OutlinedTextField(
                        value = setName,
                        onValueChange = { setName = it },
                        label = { Text(getText(R.string.default_set_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        trailingIcon = {
                            TooltipIconButton(description = getText(R.string.done), onClick = { isEditingName = false }) {
                                Icon(Icons.Default.Check, contentDescription = getText(R.string.done))
                            }
                        },
                        singleLine = true
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                            .clickable { isEditingName = true }
                            .padding(dimensions.paddingSmall),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (setName.isNotBlank()) setName else getText(R.string.default_set_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_name), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                if (windowWidthSizeClass != WindowWidthSizeClass.Compact)
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                    ) {
                        // Left Column: Available Cards
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(getText(R.string.available), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.count_parentheses_format, availableCards.size), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            LazyColumn(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                            ) {
                                itemsIndexed(availableCards, key = { _, card -> "available-${card.id}" }) { index, card ->
                                    CardSelectItem(
                                        card = card, index = index, onToggle = { selectedCards.add(card) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                            placementSpec = motionScheme.defaultSpatialSpec()
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add))
                                    }
                                }
                            }
                        }
                        // Right Column: Selected Cards
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(getText(R.string.selected_label), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.count_parentheses_format, selectedCards.size), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            LazyColumn(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                            ) {
                                itemsIndexed(selectedCards, key = { _, card -> "selected-${card.id}" }) { index, card ->
                                    CardSelectItem(
                                        card = card, index = index, onToggle = { selectedCards.remove(card) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                            placementSpec = motionScheme.defaultSpatialSpec()
                                        )
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = getText(R.string.card_remove))
                                    }
                                }
                            }
                        }
                    }
                }
                else
                {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                    ) {
                        // Top Section: Selected Cards
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(getText(R.string.selected_label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                            Text(stringResource(R.string.count_parentheses_format, selectedCards.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            LazyColumn(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .fillMaxWidth()
                            ) {
                                itemsIndexed(selectedCards, key = { _, card -> "selected-${card.id}" }) { index, card ->
                                    CardSelectItem(
                                        card = card, index = index, onToggle = { selectedCards.remove(card) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                            placementSpec = motionScheme.defaultSpatialSpec()
                                        )
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = getText(R.string.card_remove))
                                    }
                                }
                            }
                        }

                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                        // Bottom Section: Available Cards
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(getText(R.string.available), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.count_parentheses_format, availableCards.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            LazyColumn(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .fillMaxWidth()
                            ) {
                                itemsIndexed(availableCards, key = { _, card -> "available-${card.id}" }) { index, card ->
                                    CardSelectItem(
                                        card = card, index = index, onToggle = { selectedCards.add(card) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                            placementSpec = motionScheme.defaultSpatialSpec()
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add))
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(
                        onClick = {
                            viewModel.createSet(parentDeck.deck.id, setName, selectedCards.map { it.id })
                            onDismiss()
                        },
                        enabled = setName.isNotBlank() && selectedCards.isNotEmpty(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.save_set))
                    }
                }
            }
        }
    }
}

@Composable
fun ManualSetEditorDialog(
    navController: NavController,
    parentDeck: DeckWithCards,
    setForEditing: DeckWithCards,
    viewModel: FlashcardViewModel,
    onDismiss: () -> Unit
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val dimensions = LocalStudiareDimensions.current
    val motionScheme = MaterialTheme.motionScheme
    var setName by rememberSaveable { mutableStateOf(setForEditing.deck.name) }
    var isEditingName by rememberSaveable { mutableStateOf(false) }
    val selectedCards = remember { mutableStateListOf(*setForEditing.cards.toTypedArray()) }

    val availableCards = remember(parentDeck.cards, selectedCards.toList()) {
        parentDeck.cards.filter { it !in selectedCards }
    }

    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.95f)
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .fillMaxSize()
            ) {
                Text(
                    text = getText(R.string.set_edit),
                    style = MaterialTheme.typography.headlineSmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                if (isEditingName) {
                    OutlinedTextField(
                        value = setName,
                        onValueChange = { setName = it },
                        label = { Text(getText(R.string.default_set_name)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        trailingIcon = {
                            TooltipIconButton(description = getText(R.string.done), onClick = { isEditingName = false }) {
                                Icon(Icons.Default.Check, contentDescription = getText(R.string.done))
                            }
                        },
                        singleLine = true
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                            .clickable { isEditingName = true }
                            .padding(dimensions.paddingSmall),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (setName.isNotBlank()) setName else getText(R.string.default_set_name),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_name), modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(dimensions.spacingMedium))
                if (windowWidthSizeClass != WindowWidthSizeClass.Compact)
                {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
                    ) {
                        // Left Column: Available Cards
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(getText(R.string.available), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.count_parentheses_format, availableCards.size), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            LazyColumn(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                            ) {
                                itemsIndexed(availableCards, key = { _, card -> "available-${card.id}" }) { index, card ->
                                    CardSelectItem(
                                        card = card, index = index, onToggle = { selectedCards.add(card) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                            placementSpec = motionScheme.defaultSpatialSpec()
                                        )
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add))
                                    }
                                }
                            }
                        }
                        // Right Column: Selected Cards
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(getText(R.string.selected_label), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.count_parentheses_format, selectedCards.size), style = MaterialTheme.typography.bodyMedium)
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            LazyColumn(
                                modifier = Modifier
                                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                            ) {
                                itemsIndexed(selectedCards, key = { _, card -> "selected-${card.id}" }) { index, card ->
                                    CardSelectItem(
                                        card = card, index = index, onToggle = { selectedCards.remove(card) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                            placementSpec = motionScheme.defaultSpatialSpec()
                                        )
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = getText(R.string.card_remove))
                                    }
                                }
                            }
                        }
                    }
                }
                else
                {
                    // Top Section: Selected Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(getText(R.string.selected_label), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
                        Text(stringResource(R.string.count_parentheses_format, selectedCards.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        LazyColumn(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                .fillMaxWidth()
                        ) {
                            itemsIndexed(selectedCards, key = { _, card -> "selected-${card.id}" }) { index, card ->
                                CardSelectItem(
                                        card = card, index = index, onToggle = { selectedCards.remove(card) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                            placementSpec = motionScheme.defaultSpatialSpec()
                                        )
                                    ) {
                                    Icon(Icons.Default.Remove, contentDescription = getText(R.string.card_remove))
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    // Bottom Section: Available Cards
                    Column(
                        modifier = Modifier.weight(1f),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(getText(R.string.available), style = MaterialTheme.typography.titleMedium)
                        Text(stringResource(R.string.count_parentheses_format, availableCards.size), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(dimensions.spacingSmall))
                        LazyColumn(
                            modifier = Modifier
                                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusMedium))
                                .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                .fillMaxWidth()
                        ) {
                            itemsIndexed(availableCards, key = { _, card -> "available-${card.id}" }) { index, card ->
                                CardSelectItem(
                                        card = card, index = index, onToggle = { selectedCards.add(card) },
                                        modifier = Modifier.animateItem(
                                            fadeInSpec = motionScheme.defaultEffectsSpec(),
                                            fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                            placementSpec = motionScheme.defaultSpatialSpec()
                                        )
                                    ) {
                                    Icon(Icons.Default.Add, contentDescription = getText(R.string.card_add))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedButton(onClick = {
                            onDismiss()
                            navController.navigate("deckEditor?deckId=${setForEditing.deck.id}")
                        },
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(getText(R.string.advanced_settings))
                        }

                        Row {
                            TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                            Spacer(Modifier.width(dimensions.spacingSmall))
                            Button(
                                onClick = {
                                    viewModel.updateSet(setForEditing.deck.id, setName, selectedCards.map { it.id })
                                    onDismiss()
                                },
                                enabled = setName.isNotBlank() && selectedCards.isNotEmpty(),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) {
                                Text(getText(R.string.save_changes))
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                navController.navigate("deckEditor?deckId=${setForEditing.deck.id}")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(getText(R.string.advanced_settings))
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                            Spacer(Modifier.width(dimensions.spacingSmall))
                            Button(
                                onClick = {
                                    viewModel.updateSet(setForEditing.deck.id, setName, selectedCards.map { it.id })
                                    onDismiss()
                                },
                                enabled = setName.isNotBlank() && selectedCards.isNotEmpty(),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) {
                                Text(getText(R.string.save_changes))
                            }
                        }
                    }
                }
            }
        }
    }
}
