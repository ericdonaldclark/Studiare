package net.ericclark.studiare.screens

import androidx.compose.animation.AnimatedVisibility
import net.ericclark.studiare.TooltipIconButton
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.data.CollectionWithDecks
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.withShortcut

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CollectionManagerScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val dimensions = LocalStudiareDimensions.current
    val allCollections by viewModel.allCollectionsWithDecks.collectAsState()

    // We only want to show root decks (Decks that aren't sets) in the mapping checklist
    val allRootDecks by remember {
        derivedStateOf {
            viewModel.allDecks.value?.map { it.deck }?.filter { it.parentDeckId == null }?.sortedBy { it.name } ?: emptyList()
        }
    }

    var showCreateDialog by remember { mutableStateOf(false) }
    var collectionToRename by remember { mutableStateOf<CollectionWithDecks?>(null) }
    var expandedCollectionId by remember { mutableStateOf<String?>(null) }

    if (showCreateDialog || collectionToRename != null) {
        val isEditMode = collectionToRename != null
        var nameInput by remember { mutableStateOf(collectionToRename?.collection?.name ?: "") }
        val context = androidx.compose.ui.platform.LocalContext.current

        AnimatedDialog(onDismissRequest = {
            showCreateDialog = false
            collectionToRename = null
        }) {
            Surface(
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                    Text(if (isEditMode) "Rename Collection" else "New Collection", style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    OutlinedTextField(
                        value = nameInput,
                        onValueChange = { nameInput = it },
                        label = { Text("Collection Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(dimensions.spacingLarge))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = { showCreateDialog = false; collectionToRename = null }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                            Text("Cancel")
                        }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Button(
                            onClick = {
                                val finalName = nameInput.trim()

                                if (finalName.equals("UNINITIALIZED", ignoreCase = true)) {
                                    android.widget.Toast.makeText(context, "Collection cannot be named 'UNINITIALIZED'", android.widget.Toast.LENGTH_SHORT).show()
                                    return@Button // Stop here and keep the dialog open
                                }

                                if (finalName.isNotBlank()) {
                                    if (isEditMode) {
                                        viewModel.updateCollection(collectionToRename!!.collection.id, finalName)
                                    } else {
                                        viewModel.createCollection(finalName)
                                    }
                                }
                                showCreateDialog = false
                                collectionToRename = null
                            },
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) { Text("Save") }
                    }
                }
            }
        }
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.COLLECTIONS,
                title = { Text("Manage Collections") },
                navigationIcon = {
                    TooltipIconButton(description = "Back", onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New Collection") }
            )
        }
    ) { padding ->
        if (allCollections.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No custom collections yet.\nTap the button below to create one.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(dimensions.paddingMedium),
                verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)
            ) {
                val keyMap = listOf(
                    Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
                    Key.Six, Key.Seven, Key.Eight, Key.Nine
                )
                itemsIndexed(allCollections, key = { _, c -> c.collection.id }) { index, collectionData ->
                    val isExpanded = expandedCollectionId == collectionData.collection.id
                    val toggleExpanded = { expandedCollectionId = if (isExpanded) null else collectionData.collection.id }

                    val motionScheme = MaterialTheme.motionScheme
                    ElevatedCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .animateItem(
                                fadeInSpec = motionScheme.defaultEffectsSpec(),
                                fadeOutSpec = motionScheme.defaultEffectsSpec(),
                                placementSpec = motionScheme.defaultSpatialSpec()
                            ),
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { toggleExpanded() }
                                    .let {
                                        if (index in 0..8) it.withShortcut(keyMap[index], "${index + 1}") { toggleExpanded() } else it
                                    }
                                    .padding(dimensions.paddingMedium),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = collectionData.collection.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "${collectionData.decks.size} Decks assigned",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                TooltipIconButton(description = "Rename", onClick = { collectionToRename = collectionData }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                                }
                                TooltipIconButton(description = "Delete", onClick = { viewModel.deleteCollection(collectionData.collection.id) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                }

                                val rotation by animateFloatAsState(if (isExpanded) 180f else 0f)
                                Icon(
                                    Icons.Default.ExpandMore,
                                    contentDescription = "Expand",
                                    modifier = Modifier.rotate(rotation)
                                )
                            }

                            // Expandable Checklist of Decks
                            AnimatedVisibility(visible = isExpanded) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
                                ) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    Spacer(Modifier.height(dimensions.spacingSmall))

                                    if (allRootDecks.isEmpty()) {
                                        Text("You haven't created any decks yet.", modifier = Modifier.padding(8.dp))
                                    } else {
                                        allRootDecks.forEach { deck ->
                                            val isAssigned = collectionData.decks.any { it.id == deck.id }
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable {
                                                        viewModel.toggleDeckInCollection(collectionData.collection.id, deck.id, !isAssigned)
                                                    }
                                                    .padding(vertical = 8.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Checkbox(
                                                    checked = isAssigned,
                                                    onCheckedChange = null // Handled by Row click
                                                )
                                                Spacer(Modifier.width(12.dp))
                                                Text(deck.name, style = MaterialTheme.typography.bodyLarge)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}