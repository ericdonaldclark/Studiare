package net.ericclark.studiare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.max
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.LocalWindowHeightSizeClass
import net.ericclark.studiare.LocalWindowWidthSizeClass
import java.util.UUID
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.ui.graphics.Color
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

enum class MapperDestination { UNMAPPED, FRONT, BACK, FRONT_NOTES, BACK_NOTES }

data class MapperItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isCustomText: Boolean = false,
    var destination: MapperDestination = MapperDestination.UNMAPPED,
    var type: net.ericclark.studiare.data.MediaType = net.ericclark.studiare.data.MediaType.PLAIN_TEXT
)

data class AnkiMappingConfig(
    val originalAnkiName: String,
    val deckName: String,
    val mapping: Map<MapperDestination, List<MapperItem>>
)

val LocalChipWidth = compositionLocalOf<androidx.compose.ui.unit.Dp> { 120.dp }

@Composable
fun AnkiFieldMappingDialog(
    ankiFields: List<Pair<String, net.ericclark.studiare.data.MediaType>>,
    originalAnkiName: String,
    initialDeckName: String = originalAnkiName.split("::").first().trim(),
    hasNextDeck: Boolean = false,
    currentDeckMappingIndex: Int = 1,
    totalDecksToMap: Int = 1,
    subDecksDetected: Int = 0,
    decksSkippedMapping: Int = 0,
    onDismiss: () -> Unit,
    onSaveMapping: (List<AnkiMappingConfig>) -> Unit
) {
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(decksSkippedMapping) {
        if (decksSkippedMapping > 0) {
            snackbarHostState.showSnackbar("$decksSkippedMapping deck(s) did not require field mapping.")
        }
    }
    // --- NEW: Auto-map common field names ---
    var items by remember(initialDeckName) {
        mutableStateOf(
            buildList {
                var frontMapped = false
                var backMapped = false

                ankiFields.forEach { (text, type) ->
                    val destination = when {
                        !frontMapped && (text.equals("Front", ignoreCase = true) || text.equals("Question", ignoreCase = true)) -> {
                            frontMapped = true
                            MapperDestination.FRONT
                        }
                        !backMapped && (text.equals("Back", ignoreCase = true) || text.equals("Answer", ignoreCase = true)) -> {
                            backMapped = true
                            MapperDestination.BACK
                        }
                        else -> MapperDestination.UNMAPPED
                    }
                    add(MapperItem(text = text, type = type, destination = destination))
                }
            }
        )
    }
    var deckName by remember(initialDeckName) { mutableStateOf(initialDeckName) }
    val completedConfigs = remember(initialDeckName) { mutableStateListOf<AnkiMappingConfig>() }
    var draggedItem by remember { mutableStateOf<MapperItem?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }

    val dimensions = LocalStudiareDimensions.current

    // Track drop zone boundaries globally
    var frontBounds by remember { mutableStateOf(Rect.Zero) }
    var backBounds by remember { mutableStateOf(Rect.Zero) }
    var frontNotesBounds by remember { mutableStateOf(Rect.Zero) }
    var backNotesBounds by remember { mutableStateOf(Rect.Zero) }
    var unmappedBounds by remember { mutableStateOf(Rect.Zero) }

    var showCustomTextDialog by remember { mutableStateOf(false) }
    var customTextValue by remember { mutableStateOf("") }

    val widthSizeClass = LocalWindowWidthSizeClass.current
    val heightSizeClass = LocalWindowHeightSizeClass.current

    val isCompactLandscape = heightSizeClass == WindowHeightSizeClass.Compact
    val isLandscape = widthSizeClass != WindowWidthSizeClass.Compact && heightSizeClass != WindowHeightSizeClass.Compact

    // Calculate exact max width based on longest field name + icons
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val calculatedChipWidth = remember(items) {
        val maxTextWidth = items.maxOfOrNull {
            val text = if (it.isCustomText) "\"${it.text}\"" else "${it.text} | ${it.type.name}"
            textMeasurer.measure(text, labelStyle).size.width
        } ?: 0
        with(density) { maxTextWidth.toDp() + 80.dp } // Add 80dp for Drag Handle, Overflow Menu, and Padding
    }
    val chipWidth = max(120.dp, calculatedChipWidth)

    // --- Item Mutation Callbacks ---
    val onUpdateItem: (MapperItem) -> Unit = { updated ->
        items = items.map { if (it.id == updated.id) updated else it }
    }

    // Track the bounding boxes of individual items for sorting ---
    var itemBounds by remember { mutableStateOf(mapOf<String, Rect>()) }
    val onItemBoundsCalculated: (String, Rect) -> Unit = { id, rect ->
        itemBounds = itemBounds + (id to rect)
    }

    if (showCustomTextDialog) {
        AlertDialog(
            onDismissRequest = { showCustomTextDialog = false },
            title = { Text("Add Custom Text") },
            text = {
                OutlinedTextField(
                    value = customTextValue,
                    onValueChange = { customTextValue = it },
                    label = { Text("Text (e.g. 'Artist?')") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (customTextValue.isNotBlank()) items = items + MapperItem(text = customTextValue, isCustomText = true)
                    customTextValue = ""
                    showCustomTextDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showCustomTextDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Unified Drag Handlers
    val onDragStart: (MapperItem, Offset) -> Unit = { item, offset ->
        draggedItem = item
        dragPosition = offset
    }

    val onDrag: (Offset) -> Unit = { dragAmount ->
        dragPosition += dragAmount
    }

    val onDragEnd: () -> Unit = {
        if (draggedItem != null) {
            val dropTarget = Offset(dragPosition.x + 50f, dragPosition.y + 25f)
            val targetDest = when {
                frontBounds.contains(dropTarget) -> MapperDestination.FRONT
                backBounds.contains(dropTarget) -> MapperDestination.BACK
                frontNotesBounds.contains(dropTarget) -> MapperDestination.FRONT_NOTES
                backNotesBounds.contains(dropTarget) -> MapperDestination.BACK_NOTES
                unmappedBounds.contains(dropTarget) -> MapperDestination.UNMAPPED
                else -> draggedItem!!.destination
            }

            // Remove the dragged item from its old position
            val itemsWithoutDragged = items.filter { it.id != draggedItem!!.id }.toMutableList()
            val itemToInsert = draggedItem!!.copy(destination = targetDest)

            if (targetDest == MapperDestination.FRONT || targetDest == MapperDestination.BACK) {
                // Enforce single item
                val existingIndex = itemsWithoutDragged.indexOfFirst { it.destination == targetDest }
                if (existingIndex != -1) {
                    itemsWithoutDragged[existingIndex] = itemsWithoutDragged[existingIndex].copy(destination = MapperDestination.UNMAPPED)
                }
                itemsWithoutDragged.add(itemToInsert)
                items = itemsWithoutDragged

            } else if (targetDest == MapperDestination.FRONT_NOTES || targetDest == MapperDestination.BACK_NOTES) {
                // --- NEW: Spatial Reordering ---
                val destItems = itemsWithoutDragged.filter { it.destination == targetDest }
                var insertIndex = destItems.size

                // Find which item we are dropping it above based on the Y coordinate
                for ((index, destItem) in destItems.withIndex()) {
                    val bounds = itemBounds[destItem.id]
                    if (bounds != null && dropTarget.y < bounds.center.y) {
                        insertIndex = index
                        break
                    }
                }

                // Group everything, modify the target group, then flatten to preserve the new order
                val grouped = itemsWithoutDragged.groupBy { it.destination }.toMutableMap()
                val targetGroup = (grouped[targetDest] ?: emptyList()).toMutableList()
                targetGroup.add(insertIndex, itemToInsert)
                grouped[targetDest] = targetGroup

                items = MapperDestination.values().flatMap { grouped[it] ?: emptyList() }

            } else {
                itemsWithoutDragged.add(itemToInsert)
                items = itemsWithoutDragged
            }

            draggedItem = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        CompositionLocalProvider(LocalChipWidth provides chipWidth) {
            Box(modifier = Modifier.fillMaxSize()) {
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                    modifier = Modifier.fillMaxWidth(0.98f).fillMaxHeight(0.98f).align(Alignment.Center)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.weight(1f),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Map Anki Fields",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (completedConfigs.isNotEmpty()) {
                                    Text(
                                        "${completedConfigs.size} deck(s) configured.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                } else {
                                    Text(
                                        "Drag fields into Studiare's structure.",
                                        style = MaterialTheme.typography.bodyMedium,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                    )
                                }
                            }
                            if (isCompactLandscape || isLandscape) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        if (totalDecksToMap > 1) {
                                            SuggestionChip(onClick = {}, label = { Text("$currentDeckMappingIndex of $totalDecksToMap Decks") })
                                        }
                                        if (subDecksDetected > 0) {
                                            SuggestionChip(onClick = {}, label = { Text("$subDecksDetected Sets Detected") })
                                        }
                                    }
                                    TextField(
                                        value = deckName,
                                        onValueChange = { deckName = it },
                                        label = { Text("Deck Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                        singleLine = true,
                                        colors = TextFieldDefaults.colors(
                                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                            focusedIndicatorColor = Color.Transparent,
                                            unfocusedIndicatorColor = Color.Transparent
                                        )
                                    )
                                }
                            }
                        }

                        if (!isCompactLandscape && !isLandscape)
                        {
                            Spacer(Modifier.height(8.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (totalDecksToMap > 1) {
                                    SuggestionChip(onClick = {}, label = { Text("$currentDeckMappingIndex of $totalDecksToMap Decks") })
                                }
                                if (subDecksDetected > 0) {
                                    SuggestionChip(onClick = {}, label = { Text("$subDecksDetected Sets Detected") })
                                }
                            }

                            TextField(
                                value = deckName,
                                onValueChange = { deckName = it },
                                label = { Text("Deck Name") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                singleLine = true,
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                                    focusedIndicatorColor = Color.Transparent,
                                    unfocusedIndicatorColor = Color.Transparent
                                )
                            )
                        }


                        Spacer(Modifier.height(8.dp))

                        // --- Responsive Layout Area ---
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (isCompactLandscape) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    UnmappedArea(
                                        items = items, draggedItemId = draggedItem?.id, onBoundsCalculated = { unmappedBounds = it },
                                        onShowCustomDialog = { showCustomTextDialog = true }, onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd,
                                        onUpdateItem = onUpdateItem, onItemBoundsCalculated = onItemBoundsCalculated,
                                        modifier = Modifier.weight(0.4f).fillMaxHeight()
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    // FIX: Changed LazyColumn to Column to allow vertical weight distribution
                                    Column(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                                        // FIX: Added weight(1f) and removed the item { ... } wrappers
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            DropZone("Front", MapperDestination.FRONT, items, draggedItem?.id, { frontBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                            DropZone("Back", MapperDestination.BACK, items, draggedItem?.id, { backBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            DropZone("Front Notes", MapperDestination.FRONT_NOTES, items, draggedItem?.id, { frontNotesBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                            DropZone("Back Notes", MapperDestination.BACK_NOTES, items, draggedItem?.id, { backNotesBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else if (isLandscape) {
                                Row(modifier = Modifier.fillMaxSize()) {
                                    UnmappedArea(
                                        items = items, draggedItemId = draggedItem?.id, onBoundsCalculated = { unmappedBounds = it },
                                        onShowCustomDialog = { showCustomTextDialog = true }, onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd,
                                        onUpdateItem = onUpdateItem, onItemBoundsCalculated = onItemBoundsCalculated,
                                        modifier = Modifier.weight(0.3f).fillMaxHeight()
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(0.7f).fillMaxHeight()) {
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            DropZone("Front", MapperDestination.FRONT, items, draggedItem?.id, { frontBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                            DropZone("Back", MapperDestination.BACK, items, draggedItem?.id, { backBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            DropZone("Front Notes", MapperDestination.FRONT_NOTES, items, draggedItem?.id, { frontNotesBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                            DropZone("Back Notes", MapperDestination.BACK_NOTES, items, draggedItem?.id, { backNotesBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                        }
                                    }
                                }
                            } else {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    UnmappedArea(
                                        items = items, draggedItemId = draggedItem?.id, onBoundsCalculated = { unmappedBounds = it },
                                        onShowCustomDialog = { showCustomTextDialog = true }, onDragStart = onDragStart, onDrag = onDrag, onDragEnd = onDragEnd,
                                        onUpdateItem = onUpdateItem, onItemBoundsCalculated = onItemBoundsCalculated,
                                        modifier = Modifier.weight(0.35f).fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Column(modifier = Modifier.weight(0.65f).fillMaxWidth()) {
                                        Row(modifier = Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            DropZone("Front", MapperDestination.FRONT, items, draggedItem?.id, { frontBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                            DropZone("Back", MapperDestination.BACK, items, draggedItem?.id, { backBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(modifier = Modifier.weight(2f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                            DropZone("Front Notes", MapperDestination.FRONT_NOTES, items, draggedItem?.id, { frontNotesBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                            DropZone("Back Notes", MapperDestination.BACK_NOTES, items, draggedItem?.id, { backNotesBounds = it }, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated, Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // --- BUTTON SECTION ---
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.End,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                TextButton(onClick = onDismiss) { Text("Cancel") }
                                Spacer(Modifier.width(8.dp))

                                // Save & Create Another
                                FilledTonalButton(onClick = {
                                    val mapping = items.groupBy { it.destination }
                                    completedConfigs.add(AnkiMappingConfig(originalAnkiName,deckName, mapping))

                                    // Reset UI for the next Studiare deck from this same Anki deck
                                    items = ankiFields.map { MapperItem(text = it.first, type = it.second) }
                                    deckName = "$initialDeckName ${completedConfigs.size + 1}"
                                }) { Text("Save & Create Another") }

                                // Landscape: Button sits in row
                                if (isLandscape || isCompactLandscape) {
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            val mapping = items.groupBy { it.destination }
                                            if (mapping.keys.any { it != MapperDestination.UNMAPPED } || completedConfigs.isEmpty()) {
                                                completedConfigs.add(AnkiMappingConfig(originalAnkiName,deckName, mapping))
                                            }
                                            onSaveMapping(completedConfigs.toList())
                                        }
                                    ) {
                                        Text(if (hasNextDeck) "Confirm & Next Deck" else "Confirm & Finish")
                                    }
                                }
                            }

                            // Portrait: Button sits below
                            if (!isLandscape && !isCompactLandscape) {
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val mapping = items.groupBy { it.destination }
                                        if (mapping.keys.any { it != MapperDestination.UNMAPPED } || completedConfigs.isEmpty()) {
                                            completedConfigs.add(AnkiMappingConfig(originalAnkiName, deckName, mapping))
                                        }
                                        onSaveMapping(completedConfigs.toList())
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(if (hasNextDeck) "Confirm & Next Deck" else "Confirm & Finish")
                                }
                            }
                        }
                    }
                }

                // Global Drag Overlay
                if (draggedItem != null) {
                    Box(modifier = Modifier.offset { IntOffset(dragPosition.x.roundToInt(), dragPosition.y.roundToInt()) }) {
                        FieldChip(draggedItem!!, isDragging = true)
                    }
                }
                SnackbarHost(
                    hostState = snackbarHostState,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp)
                )
            }
        }
    }
}

@Composable
fun UnmappedArea(
    items: List<MapperItem>,
    draggedItemId: String?,
    onBoundsCalculated: (Rect) -> Unit,
    onShowCustomDialog: () -> Unit,
    onDragStart: (MapperItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onUpdateItem: (MapperItem) -> Unit,
    onItemBoundsCalculated: (String, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val unmappedItems = items.filter { it.destination == MapperDestination.UNMAPPED }
    val gridState = rememberLazyGridState()

    Box(
        modifier = modifier
            .onGloballyPositioned { onBoundsCalculated(it.boundsInRoot()) }
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(8.dp)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("Unmapped Fields", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = onShowCustomDialog) {
                    Icon(Icons.Default.Add, contentDescription = null)
                    Text("Custom Text")
                }
            }

            // FIX: Removed the Row, Custom Scrollbar, and userScrollEnabled restrictions
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = LocalChipWidth.current),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                items(unmappedItems, key = { it.id }) { item ->
                    DraggableItem(item, draggedItemId == item.id, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated)
                }
            }
        }
    }
}

@Composable
fun DropZone(
    title: String,
    destination: MapperDestination,
    allItems: List<MapperItem>,
    draggedItemId: String?,
    onBoundsCalculated: (Rect) -> Unit,
    onDragStart: (MapperItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onUpdateItem: (MapperItem) -> Unit,
    onItemBoundsCalculated: (String, Rect) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = allItems.filter { it.destination == destination }
    Box(
        modifier = modifier
            .onGloballyPositioned { onBoundsCalculated(it.boundsInRoot()) }
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant,
                RoundedCornerShape(12.dp,))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(8.dp)
    ) {
        // FIX: Add fillMaxSize to the Column
        Column(modifier = Modifier.fillMaxSize()) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.weight(1f) // FIX: Add weight(1f) to the LazyColumn
            ) {
                items(items, key = { it.id }) { item ->
                    DraggableItem(item, draggedItemId == item.id, onDragStart, onDrag, onDragEnd, onUpdateItem, onItemBoundsCalculated)
                }
            }
        }
    }
}

@Composable
fun DraggableItem(
    item: MapperItem,
    isDragging: Boolean,
    onDragStart: (MapperItem, Offset) -> Unit,
    onDrag: (Offset) -> Unit,
    onDragEnd: () -> Unit,
    onUpdateItem: (MapperItem) -> Unit,
    onItemBoundsCalculated: (String, Rect) -> Unit
) {
    var globalPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .onGloballyPositioned {
                globalPosition = it.positionInRoot()
                onItemBoundsCalculated(item.id, it.boundsInRoot()) // Log the bounds for sorting
            }
            .alpha(if (isDragging) 0.2f else 1f)
    ) {
        FieldChip(
            item = item,
            isDragging = false,
            onUpdateItem = onUpdateItem,
            dragModifier = Modifier.pointerInput(item.id) {
                detectDragGestures(
                    onDragStart = { onDragStart(item, globalPosition) },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        onDrag(dragAmount)
                    },
                    onDragEnd = { onDragEnd() },
                    onDragCancel = { onDragEnd() }
                )
            }
        )
    }
}

@Composable
fun FieldChip(
    item: MapperItem,
    isDragging: Boolean,
    onUpdateItem: (MapperItem) -> Unit = {},
    dragModifier: Modifier = Modifier
) {
    var showMediaTypeMenu by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (item.isCustomText) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier
            .width(LocalChipWidth.current)
            .padding(horizontal = 4.dp),
        tonalElevation = if (isDragging) 8.dp else 0.dp
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Default.DragIndicator,
                contentDescription = "Drag Handle",
                tint = if (item.isCustomText) MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                modifier = Modifier
                    .size(20.dp)
                    .then(dragModifier) // FIX: Apply drag listener ONLY to the handle
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (item.isCustomText) "\"${item.text}\"" else "${item.text} | ${item.type.toString()}",
                modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.labelLarge,
                color = if (item.isCustomText) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Box {
                IconButton(
                    onClick = { showMediaTypeMenu = true },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Change Media Type",
                        tint = if (item.isCustomText) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                // Secondary Media Type Selection Menu
                DropdownMenu(
                    expanded = showMediaTypeMenu,
                    onDismissRequest = { showMediaTypeMenu = false }
                ) {
                    net.ericclark.studiare.data.MediaType.entries.forEach { mediaType ->
                        DropdownMenuItem(
                            text = { Text(mediaType.toString()) },
                            onClick = {
                                onUpdateItem(item.copy(type = mediaType))
                                showMediaTypeMenu = false
                            }
                        )
                    }
                }
            }
        }
    }
}