package net.ericclark.studiare.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.util.UUID
import kotlin.math.roundToInt
import androidx.compose.ui.platform.LocalContext
import net.ericclark.studiare.LocalWindowHeightSizeClass
import net.ericclark.studiare.LocalWindowWidthSizeClass
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.max
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items

enum class MapperDestination { UNMAPPED, FRONT, BACK, FRONT_NOTES, BACK_NOTES }
val LocalChipWidth = compositionLocalOf<androidx.compose.ui.unit.Dp> { 120.dp }

data class MapperItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val isCustomText: Boolean = false,
    var destination: MapperDestination = MapperDestination.UNMAPPED
)

@Composable
fun AnkiFieldMappingDialog(
    ankiFields: List<String>,
    onDismiss: () -> Unit,
    onSaveMapping: (Map<MapperDestination, List<MapperItem>>) -> Unit
) {
    var items by remember { mutableStateOf(ankiFields.map { MapperItem(text = it) }) }
    var draggedItem by remember { mutableStateOf<MapperItem?>(null) }
    var dragPosition by remember { mutableStateOf(Offset.Zero) }

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

    // Calculate exact max width based on longest field name ---
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelLarge
    val density = LocalDensity.current
    val calculatedChipWidth = remember(items) {
        val maxTextWidth = items.maxOfOrNull {
            val text = if (it.isCustomText) "\"${it.text}\"" else it.text
            textMeasurer.measure(text, labelStyle).size.width
        } ?: 0
        with(density) { maxTextWidth.toDp() + 32.dp } // Add 32dp for padding/buffer
    }
    val chipWidth = max(120.dp, calculatedChipWidth)

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
            // Check the approximate center of the dragged chip to see which zone it landed in
            val dropTarget = Offset(dragPosition.x + 50f, dragPosition.y + 25f)
            val targetDest = when {
                frontBounds.contains(dropTarget) -> MapperDestination.FRONT
                backBounds.contains(dropTarget) -> MapperDestination.BACK
                frontNotesBounds.contains(dropTarget) -> MapperDestination.FRONT_NOTES
                backNotesBounds.contains(dropTarget) -> MapperDestination.BACK_NOTES
                unmappedBounds.contains(dropTarget) -> MapperDestination.UNMAPPED
                else -> draggedItem!!.destination // Snap back if dropped in dead space
            }

            // --- NEW: Enforce single-item constraints for Front and Back ---
            var updatedItems = items
            if (targetDest == MapperDestination.FRONT || targetDest == MapperDestination.BACK) {
                // Check if there is already an item here (that isn't the one we are currently dragging)
                val existingItem = updatedItems.find { it.destination == targetDest && it.id != draggedItem!!.id }
                if (existingItem != null) {
                    // Kick the occupying item back to the unmapped area
                    updatedItems = updatedItems.map {
                        if (it.id == existingItem.id) it.copy(destination = MapperDestination.UNMAPPED) else it
                    }
                }
            }

            items = updatedItems.map { if (it.id == draggedItem!!.id) it.copy(destination = targetDest) else it }
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
                    modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.9f)
                        .align(Alignment.Center)
                ) {
                    Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
                        Text(
                            "Map Anki Fields",
                            style = MaterialTheme.typography.headlineMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            "Drag fields into Studiare's structure.",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Spacer(Modifier.height(16.dp))

                        // --- Responsive Layout Area ---
                        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                            if (isCompactLandscape) {
                                // Phone in Landscape: Split screen with scrollable drop zones to save vertical space
                                Row(modifier = Modifier.fillMaxSize()) {
                                    UnmappedArea(
                                        items = items,
                                        draggedItemId = draggedItem?.id,
                                        onBoundsCalculated = { unmappedBounds = it },
                                        onShowCustomDialog = { showCustomTextDialog = true },
                                        onDragStart = onDragStart,
                                        onDrag = onDrag,
                                        onDragEnd = onDragEnd,
                                        modifier = Modifier.weight(0.4f).fillMaxHeight()
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    LazyColumn(modifier = Modifier.weight(0.6f).fillMaxHeight()) {
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                DropZone(
                                                    "Front",
                                                    MapperDestination.FRONT,
                                                    items,
                                                    draggedItem?.id,
                                                    { frontBounds = it },
                                                    onDragStart,
                                                    onDrag,
                                                    onDragEnd,
                                                    Modifier.weight(1f)
                                                )
                                                DropZone(
                                                    "Front Notes",
                                                    MapperDestination.FRONT_NOTES,
                                                    items,
                                                    draggedItem?.id,
                                                    { frontNotesBounds = it },
                                                    onDragStart,
                                                    onDrag,
                                                    onDragEnd,
                                                    Modifier.weight(1f)
                                                )
                                            }
                                        }
                                        item { Spacer(Modifier.height(8.dp)) }
                                        item {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                DropZone(
                                                    "Back",
                                                    MapperDestination.BACK,
                                                    items,
                                                    draggedItem?.id,
                                                    { backBounds = it },
                                                    onDragStart,
                                                    onDrag,
                                                    onDragEnd,
                                                    Modifier.weight(1f)
                                                )
                                                DropZone(
                                                    "Back Notes",
                                                    MapperDestination.BACK_NOTES,
                                                    items,
                                                    draggedItem?.id,
                                                    { backNotesBounds = it },
                                                    onDragStart,
                                                    onDrag,
                                                    onDragEnd,
                                                    Modifier.weight(1f)
                                                )
                                            }
                                        }
                                    }
                                }
                            } else if (isLandscape) {
                                // Tablet in Landscape: 2x2 Grid fits perfectly on the right
                                Row(modifier = Modifier.fillMaxSize()) {
                                    UnmappedArea(
                                        items = items,
                                        draggedItemId = draggedItem?.id,
                                        onBoundsCalculated = { unmappedBounds = it },
                                        onShowCustomDialog = { showCustomTextDialog = true },
                                        onDragStart = onDragStart,
                                        onDrag = onDrag,
                                        onDragEnd = onDragEnd,
                                        modifier = Modifier.weight(0.3f).fillMaxHeight()
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Column(modifier = Modifier.weight(0.7f).fillMaxHeight()) {
                                        Row(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DropZone(
                                                "Front",
                                                MapperDestination.FRONT,
                                                items,
                                                draggedItem?.id,
                                                { frontBounds = it },
                                                onDragStart,
                                                onDrag,
                                                onDragEnd,
                                                Modifier.weight(1f)
                                            )
                                            DropZone(
                                                "Front Notes",
                                                MapperDestination.FRONT_NOTES,
                                                items,
                                                draggedItem?.id,
                                                { frontNotesBounds = it },
                                                onDragStart,
                                                onDrag,
                                                onDragEnd,
                                                Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DropZone(
                                                "Back",
                                                MapperDestination.BACK,
                                                items,
                                                draggedItem?.id,
                                                { backBounds = it },
                                                onDragStart,
                                                onDrag,
                                                onDragEnd,
                                                Modifier.weight(1f)
                                            )
                                            DropZone(
                                                "Back Notes",
                                                MapperDestination.BACK_NOTES,
                                                items,
                                                draggedItem?.id,
                                                { backNotesBounds = it },
                                                onDragStart,
                                                onDrag,
                                                onDragEnd,
                                                Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Standard Portrait: Unmapped top, 2x2 Grid bottom
                                Column(modifier = Modifier.fillMaxSize()) {
                                    UnmappedArea(
                                        items = items,
                                        draggedItemId = draggedItem?.id,
                                        onBoundsCalculated = { unmappedBounds = it },
                                        onShowCustomDialog = { showCustomTextDialog = true },
                                        onDragStart = onDragStart,
                                        onDrag = onDrag,
                                        onDragEnd = onDragEnd,
                                        modifier = Modifier.weight(0.35f).fillMaxWidth()
                                    )
                                    Spacer(Modifier.height(16.dp))
                                    Column(modifier = Modifier.weight(0.65f).fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DropZone(
                                                "Front",
                                                MapperDestination.FRONT,
                                                items,
                                                draggedItem?.id,
                                                { frontBounds = it },
                                                onDragStart,
                                                onDrag,
                                                onDragEnd,
                                                Modifier.weight(1f)
                                            )
                                            DropZone(
                                                "Front Notes",
                                                MapperDestination.FRONT_NOTES,
                                                items,
                                                draggedItem?.id,
                                                { frontNotesBounds = it },
                                                onDragStart,
                                                onDrag,
                                                onDragEnd,
                                                Modifier.weight(1f)
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        Row(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            DropZone(
                                                "Back",
                                                MapperDestination.BACK,
                                                items,
                                                draggedItem?.id,
                                                { backBounds = it },
                                                onDragStart,
                                                onDrag,
                                                onDragEnd,
                                                Modifier.weight(1f)
                                            )
                                            DropZone(
                                                "Back Notes",
                                                MapperDestination.BACK_NOTES,
                                                items,
                                                draggedItem?.id,
                                                { backNotesBounds = it },
                                                onDragStart,
                                                onDrag,
                                                onDragEnd,
                                                Modifier.weight(1f)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = onDismiss) { Text("Cancel") }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = {
                                val mapping = items.groupBy { it.destination }
                                onSaveMapping(mapping)
                                // REMOVED onDismiss() from here
                            }) { Text("Confirm Mapping") }
                        }
                    }
                }

                // Global Drag Overlay
                if (draggedItem != null) {
                    Box(modifier = Modifier.offset {
                        IntOffset(
                            dragPosition.x.roundToInt(),
                            dragPosition.y.roundToInt()
                        )
                    }) {
                        FieldChip(draggedItem!!, isDragging = true)
                    }
                }
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
    modifier: Modifier = Modifier
) {
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
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = LocalChipWidth.current),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // FIXED BUG 2: 'key = { it.id }' prevents Compose from giving nodes the wrong drag data
                items(items.filter { it.destination == MapperDestination.UNMAPPED }, key = { it.id }) { item ->
                    DraggableItem(item, isDragging = draggedItemId == item.id, onDragStart, onDrag, onDragEnd)
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
    modifier: Modifier = Modifier
) {
    val items = allItems.filter { it.destination == destination }
    Box(
        modifier = modifier
            .onGloballyPositioned { onBoundsCalculated(it.boundsInRoot()) }
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(8.dp)
    ) {
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(8.dp))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                items(items, key = { it.id }) { item ->
                    DraggableItem(item, isDragging = draggedItemId == item.id, onDragStart, onDrag, onDragEnd)
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
    onDragEnd: () -> Unit
) {
    // FIXED BUG 1: Grab the specific item's position globally before dragging
    var globalPosition by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .onGloballyPositioned { globalPosition = it.positionInRoot() }
            .pointerInput(item.id) { // FIXED BUG 2: Attach pointer to item ID
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
            .alpha(if (isDragging) 0.2f else 1f) // Dim the original while dragging
    ) {
        FieldChip(item, isDragging = false)
    }
}

@Composable
fun FieldChip(item: MapperItem, isDragging: Boolean) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (item.isCustomText) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.primaryContainer,
        // Use the provided width instead of fillMaxWidth()
        modifier = Modifier.width(LocalChipWidth.current).padding(horizontal = 4.dp),
        tonalElevation = if (isDragging) 8.dp else 0.dp
    ) {
        Text(
            text = if (item.isCustomText) "\"${item.text}\"" else item.text,
            modifier = Modifier.padding(8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (item.isCustomText) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}