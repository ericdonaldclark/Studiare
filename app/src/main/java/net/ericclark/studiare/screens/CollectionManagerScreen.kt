package net.ericclark.studiare.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.data.CollectionWithDecks
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

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

        AlertDialog(
            onDismissRequest = {
                showCreateDialog = false
                collectionToRename = null
            },
            title = { Text(if (isEditMode) "Rename Collection" else "New Collection") },
            text = {
                OutlinedTextField(
                    value = nameInput,
                    onValueChange = { nameInput = it },
                    label = { Text("Collection Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
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
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; collectionToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("Manage Collections") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showCreateDialog = true },
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
                items(allCollections, key = { it.collection.id }) { collectionData ->
                    val isExpanded = expandedCollectionId == collectionData.collection.id

                    ElevatedCard(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium)
                    ) {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Header Row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        expandedCollectionId = if (isExpanded) null else collectionData.collection.id
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

                                IconButton(onClick = { collectionToRename = collectionData }) {
                                    Icon(Icons.Default.Edit, contentDescription = "Rename", tint = MaterialTheme.colorScheme.primary)
                                }
                                IconButton(onClick = { viewModel.deleteCollection(collectionData.collection.id) }) {
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