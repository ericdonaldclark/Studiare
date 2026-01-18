package net.ericclark.studiare.studymodes

import android.content.res.Configuration
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.zIndex
import androidx.navigation.NavController
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.StudyCompletionScreen
import net.ericclark.studiare.data.*
import kotlin.math.roundToInt


@Composable
fun MemoryScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val state = viewModel.studyState ?: return
    val context = LocalContext.current
    val toastMessage = viewModel.toastMessage

    // CHANGED: Collect both column counts
    val portraitColumns by viewModel.memoryGridColumnsPortrait.collectAsState()
    val landscapeColumns by viewModel.memoryGridColumnsLandscape.collectAsState()

    // NEW: Detect Orientation
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    // Determine which count to use for the Grid
    val activeColumns = if (isLandscape) landscapeColumns else portraitColumns

    var showSettingsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            Toast.makeText(context, toastMessage, Toast.LENGTH_LONG).show()
            viewModel.clearToastMessage()
        }
    }

    if (state.isComplete) {
        StudyCompletionScreen(
            navController = navController,
            viewModel = viewModel
        )
        return
    }

    LaunchedEffect(Unit) {
        if (state.memoryActiveCardIds.isEmpty()) {
            viewModel.initMemoryGrid()
        }
    }

    // CHANGED: Pass both values to dialog
    if (showSettingsDialog) {
        MemorySettingsDialog(
            portraitColumns = portraitColumns,
            landscapeColumns = landscapeColumns,
            onSave = { p, l -> viewModel.setMemoryGridColumns(p, l) },
            onDismiss = { showSettingsDialog = false }
        )
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("${state.deckWithCards.deck.name} - Memory") },
                navigationIcon = {
                    IconButton(onClick = { viewModel.endStudySession(); navController.popBackStack() }) {
                        Icon(
                            Icons.Default.ArrowBack,
                            "Back"
                        )
                    }
                },
                // NEW: Settings Button in Action Bar
                actions = {
                    IconButton(onClick = { showSettingsDialog = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Grid Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {

            // 1. Grid
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                MemoryGrid(state, viewModel, activeColumns)
            }

            // 2. Overlay (Second Selection)
            if (state.memorySelected2 != null) {
                // ... [Existing Overlay Logic] ...
                val (id, side) = state.memorySelected2
                val card = state.deckWithCards.cards.find { it.id == id }
                val isMatch = state.memorySelected1?.first == id

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.4f))
                        .zIndex(20f)
                        .clickable { viewModel.selectMemoryTile(id, side) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        if (card != null) {
                            MemoryTile(
                                isFaceUp = true,
                                side = side,
                                text = if (side == "Front") card.front else card.back,
                                onClick = { viewModel.selectMemoryTile(id, side) },
                                modifier = Modifier
                                    .size(240.dp)
                                    .shadow(16.dp, RoundedCornerShape(16.dp)),
                                textSize = 32.sp
                            )
                        }
                        Spacer(Modifier.height(24.dp))
                        Surface(
                            shape = RoundedCornerShape(32.dp),
                            color = if (isMatch) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                            shadowElevation = 8.dp
                        ) {
                            Text(
                                text = if (isMatch) "Match!" else "No Match",
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 32.dp, vertical = 12.dp),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // 3. Bottom Bar
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(8.dp)
                    .zIndex(10f)
            ) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp),
                    contentAlignment = Alignment.Center
                ) {
                    // UPDATED Check: Show "Next Set" if all active cards are in matched pairs
                    val allActiveMatched = state.memoryActiveCardIds.isNotEmpty() && state.memoryActiveCardIds.all { it in state.successfullyMatchedPairs }

                    if (allActiveMatched) {
                        Button(
                            onClick = { viewModel.initMemoryGrid() },
                            modifier = Modifier.fillMaxWidth(0.5f).height(50.dp)
                        ) {
                            Text("Next Set", fontSize = 18.sp)
                        }
                    }
                    else if (state.memorySelected1 != null) {
                        val (id, side) = state.memorySelected1
                        val card = state.deckWithCards.cards.find { it.id == id }

                        if (card != null) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(80.dp)
                                    .clickable { viewModel.selectMemoryTile(id, side) },
                                elevation = CardDefaults.cardElevation(4.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (side == "Front") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
                                )
                            ) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (side == "Front") card.front else card.back,
                                        textAlign = TextAlign.Center,
                                        style = MaterialTheme.typography.headlineSmall,
                                        color = if (side == "Front") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Select a matching tile from the grid", color = Color.Gray)
                    }
                }
            }
        }
    }
}

@Composable
fun MemorySettingsDialog(
    portraitColumns: Int,
    landscapeColumns: Int,
    onSave: (Int, Int) -> Unit,
    onDismiss: () -> Unit
) {
    // Local state to hold slider values before saving
    var newPortrait by remember { mutableStateOf(portraitColumns) }
    var newLandscape by remember { mutableStateOf(landscapeColumns) }

    Dialog(onDismissRequest = onDismiss) {
        Card(shape = RoundedCornerShape(16.dp)) {
            Column(modifier = Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Memory Grid Settings", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(24.dp))

                // PORTRAIT SLIDER
                Text("Portrait Columns: $newPortrait", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Slider(
                    value = newPortrait.toFloat(),
                    onValueChange = { newPortrait = it.roundToInt() },
                    valueRange = 2f..6f,
                    steps = 3
                )

                Spacer(Modifier.height(16.dp))

                // LANDSCAPE SLIDER
                Text("Landscape Columns: $newLandscape", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
                Slider(
                    value = newLandscape.toFloat(),
                    onValueChange = { newLandscape = it.roundToInt() },
                    valueRange = 2f..8f, // Allow more columns in landscape
                    steps = 5
                )

                Spacer(Modifier.height(24.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = {
                        onSave(newPortrait, newLandscape)
                        onDismiss()
                    }) {
                        Text("Save")
                    }
                }
            }
        }
    }
}
@Composable
fun MemoryGrid(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel, columns: Int) {
    // Zoom State
    var scale by remember { mutableFloatStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        scale = (scale * zoomChange).coerceIn(0.5f, 3f)
    }

    val activeIds = state.memoryActiveCardIds
    val cards = state.deckWithCards.cards.filter { it.id in activeIds }

    val tiles = remember(activeIds) {
        val list = mutableListOf<Triple<String, String, net.ericclark.studiare.data.Card>>() // ID, Side, Card
        cards.forEach { card ->
            list.add(Triple(card.id, "Front", card))
            list.add(Triple(card.id, "Back", card))
        }
        list.shuffled(kotlin.random.Random(state.sessionId.hashCode().toLong()))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .transformable(state = transformableState)
            .graphicsLayer(
                scaleX = scale,
                scaleY = scale,
            ),
        contentAlignment = Alignment.Center
    ) {
        LazyVerticalGrid(
            // CHANGED: Use Fixed(columns) to guarantee size changes every step
            columns = GridCells.Fixed(columns),
            // CHANGED: Increased bottom padding to 160.dp to prevent cutoff by overlay
            contentPadding = PaddingValues(top = 16.dp, start = 16.dp, end = 16.dp, bottom = 160.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 800.dp)
        ) {
            // FIXED: Ambiguity resolved by using explicit destructuring inside the block
            itemsIndexed(tiles) { index, tile ->
                val (id, side, _) = tile

                val isMatched = id in state.successfullyMatchedPairs
                val isSelected1 = state.memorySelected1?.first == id && state.memorySelected1.second == side
                val isSelected2 = state.memorySelected2?.first == id && state.memorySelected2.second == side

                val isVisible = !isMatched && !isSelected1 && !isSelected2

                if (isVisible) {
                    MemoryTile(
                        isFaceUp = false,
                        side = side,
                        tileNumber = index + 1,
                        onClick = { viewModel.selectMemoryTile(id, side) }
                    )
                } else {
                    Box(modifier = Modifier.aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
fun MemoryInteractionArea(state: net.ericclark.studiare.data.StudyState, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Second Selection (Top - Comparison)
        Box(modifier = Modifier.height(140.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.memorySelected2 != null) {
                val (id, side) = state.memorySelected2
                val card = state.deckWithCards.cards.find { it.id == id }

                if (card != null) {
                    val isMatch = state.memorySelected1?.first == id
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MemoryTile(
                            isFaceUp = true,
                            side = side,
                            text = if (side == "Front") card.front else card.back,
                            onClick = { viewModel.selectMemoryTile(id, side) }, // Tap to Confirm/Return
                            modifier = Modifier.size(100.dp)
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = if (isMatch) "Match!" else "Not a Match",
                            color = if (isMatch) Color(0xFF22C55E) else MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            } else if (state.memorySelected1 != null) {
                Text("Select a matching tile from the grid", color = Color.Gray)
            }
        }

        Divider()

        // First Selection (Bottom - Holder)
        Box(modifier = Modifier.height(140.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
            if (state.memorySelected1 != null) {
                val (id, side) = state.memorySelected1
                val card = state.deckWithCards.cards.find { it.id == id }

                if (card != null) {
                    MemoryTile(
                        isFaceUp = true,
                        side = side,
                        text = if (side == "Front") card.front else card.back,
                        onClick = { viewModel.selectMemoryTile(id, side) }, // Tap to Return
                        modifier = Modifier.size(100.dp)
                    )
                }
            } else {
                Text("Tap a tile to start", color = Color.Gray)
            }
        }
    }
}

@Composable
fun MemoryTile(
    isFaceUp: Boolean,
    side: String,
    text: String = "",
    tileNumber: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier.aspectRatio(1f),
    textSize: androidx.compose.ui.unit.TextUnit = MaterialTheme.typography.bodyMedium.fontSize // New param
) {
    val faceDownColor = if (side == "Front") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary
    val faceUpColor = if (side == "Front") MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.tertiaryContainer
    val textColor = if (side == "Front") MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onTertiaryContainer

    Card(
        modifier = modifier.clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp),
        colors = CardDefaults.cardColors(containerColor = if (isFaceUp) faceUpColor else faceDownColor)
    ) {
        Box(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isFaceUp && tileNumber != null) {
                Text(
                    text = tileNumber.toString(),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.align(Alignment.TopStart).padding(2.dp)
                )
            }

            if (isFaceUp) {
                Text(
                    text = text,
                    textAlign = TextAlign.Center,
                    fontSize = textSize, // Use param
                    color = textColor,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Image(
                    painter = painterResource(id = R.drawable.studiare),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(0.5f), // Relative size
                    colorFilter = androidx.compose.ui.graphics.ColorFilter.tint(Color.White.copy(alpha = 0.7f))
                )
            }
        }
    }
}