package net.ericclark.studiare.screens

import net.ericclark.studiare.TooltipIconButton
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.components.*
import net.ericclark.studiare.DifficultySlider
import net.ericclark.studiare.MarkKnownButton
import kotlinx.coroutines.launch
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.TextField
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.R
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset

/**
 * Orders [cards] per [type] — the same logic [SimpleEditorScreen] applies to its own display-only
 * sort, extracted here so Bulk Editor's initial card list matches it instead of just reflecting
 * whatever raw order the deck's stored `cardIds` happens to be in (the two editors showing
 * different orders despite the same `deckSortMode` was a real bug, not just a UI inconsistency).
 */
internal fun sortCardStates(cards: List<CardEditorState>, type: DeckSortMode): List<CardEditorState> = when (type) {
    DeckSortMode.A_TO_Z -> cards.sortedBy { it.front.value.lowercase() }
    DeckSortMode.Z_TO_A -> cards.sortedByDescending { it.front.value.lowercase() }
    DeckSortMode.ONE_TO_FIVE -> cards.sortedWith(compareBy<CardEditorState> { it.difficulty.value }.thenBy { it.front.value.lowercase() })
    DeckSortMode.FIVE_TO_ONE -> cards.sortedWith(compareByDescending<CardEditorState> { it.difficulty.value }.thenBy { it.front.value.lowercase() })
    DeckSortMode.DATE_ADDED_OLD_TO_NEW -> cards.sortedWith(compareBy<CardEditorState> { it.createdAt.value }.thenBy { it.front.value.lowercase() })
    DeckSortMode.DATE_ADDED_NEW_TO_OLD -> cards.sortedWith(compareByDescending<CardEditorState> { it.createdAt.value }.thenBy { it.front.value.lowercase() })
    DeckSortMode.DATE_MODIFIED_NEW_TO_OLD -> cards.sortedWith(compareBy<CardEditorState> { it.updatedAt.value }.thenBy { it.front.value.lowercase() })
    DeckSortMode.DATE_MODIFIED_OLD_TO_NEW -> cards.sortedWith(compareByDescending<CardEditorState> { it.updatedAt.value }.thenBy { it.front.value.lowercase() })
}

@Composable
fun CardEditor(
    cardState: CardEditorState,
    cardNumber: Int,
    totalCards: Int,
    onDelete: () -> Unit,
    onKnownClick: () -> Unit,
    allTags: List<TagDefinition>,
    currentDeckTags: Set<String>,
    onUpdateTags: (Set<String>) -> Unit,
    onCreateTag: (String, String) -> Unit,
    onOpenRichTextEditor: (target: String, initialHtml: String, title: String) -> Unit,
    snackbarHostState: androidx.compose.material3.SnackbarHostState,
    coroutineScope: kotlinx.coroutines.CoroutineScope,
    showInfoButton: Boolean = true,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    var showInfo by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
    ) {
        FlippableCardShell(
            cardNumber = cardNumber,
            totalCards = totalCards,
            showInfo = showInfo,
            onToggleInfo = { showInfo = !showInfo },
            modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall),
            showToggleButton = showInfoButton,
            frontTopBarExtra = {
                val deleteInteractionSource = remember { MutableInteractionSource() }
                val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
                val deleteScale by animateFloatAsState(
                    targetValue = if (isDeletePressed) 0.85f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "deleteSquish"
                )
                TooltipIconButton(description = getText(R.string.delete), onClick = onDelete, interactionSource = deleteInteractionSource, modifier = Modifier.scale(deleteScale)) {
                    Icon(Icons.Default.Delete, getText(R.string.delete), tint = MaterialTheme.colorScheme.error)
                }
            },
            infoContent = { CardMetadataContent(cardState) },
            frontContent = {
                Column(Modifier
                    .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                ) {
                    // --- FRONT ---
                    CardSideEditor(
                        sideLabel = CardSide.FRONT.asString(),
                        plainText = cardState.front.value,
                        onPlainTextChange = { cardState.front.value = it },
                        isRichText = cardState.isFrontRichText.value,
                        onToggleRichText = { isRich ->
                            cardState.isFrontRichText.value = isRich
                            if (!isRich) {
                                cardState.frontRichTextInfo.value = null
                                cardState.front.value = cardState.front.value.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                            }
                        },
                        onEditRichTextClick = {
                            onOpenRichTextEditor("front", cardState.frontRichTextInfo.value ?: cardState.front.value, "Edit Front (Rich Text)")
                        },
                        actionIcon = {
                            TooltipIconButton(description = getText(R.string.add_front_note), onClick = {
                                cardState.frontNotes.value = cardState.frontNotes.value + NoteField(name = "", content = "", type = MediaType.PLAIN_TEXT)
                            }) {
                                Icon(Icons.Default.Add, contentDescription = getText(R.string.add_front_note), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
        
                    cardState.frontNotes.value.forEachIndexed { index, note ->
                        val enterTransition = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                        AnimatedVisibility(
                            visibleState = enterTransition,
                            enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        ) {
                            DynamicNoteEditor(
                                note = note,
                                noteIndex = index,
                                onNoteChange = { updatedNote ->
                                    val newList = cardState.frontNotes.value.toMutableList()
                                    newList[index] = updatedNote
                                    cardState.frontNotes.value = newList
                                },
                                onEditRichTextClick = {
                                    onOpenRichTextEditor("frontNote_$index", note.content, "Edit ${note.name}")
                                },
                                onRemove = {
                                    val removedNote = cardState.frontNotes.value[index]
                                    val newList = cardState.frontNotes.value.toMutableList()
                                    newList.removeAt(index)
                                    cardState.frontNotes.value = newList
        
                                    coroutineScope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Note removed",
                                            actionLabel = "Undo",
                                            duration = androidx.compose.material3.SnackbarDuration.Short
                                        )
                                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                            val restoreList = cardState.frontNotes.value.toMutableList()
                                            restoreList.add(index.coerceIn(0, restoreList.size), removedNote)
                                            cardState.frontNotes.value = restoreList
                                        }
                                    }
                                }
                            )
                        }
                    }
        
                    Spacer(Modifier.height(dimensions.spacingMedium))
        
                    // --- BACK ---
                    CardSideEditor(
                        sideLabel = CardSide.BACK.asString(),
                        plainText = cardState.back.value,
                        onPlainTextChange = { cardState.back.value = it },
                        isRichText = cardState.isBackRichText.value,
                        onToggleRichText = { isRich ->
                            cardState.isBackRichText.value = isRich
                            if (!isRich) {
                                cardState.backRichTextInfo.value = null
                                cardState.back.value = cardState.back.value.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                            }
                        },
                        onEditRichTextClick = {
                            onOpenRichTextEditor("back", cardState.backRichTextInfo.value ?: cardState.back.value, "Edit Back (Rich Text)")
                        },
                        actionIcon = {
                            TooltipIconButton(description = getText(R.string.add_back_note), onClick = {
                                cardState.backNotes.value = cardState.backNotes.value + NoteField(name = "", content = "", type = MediaType.PLAIN_TEXT)
                            }) {
                                Icon(Icons.Default.Add, contentDescription = getText(R.string.add_back_note), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
        
                    cardState.backNotes.value.forEachIndexed { index, note ->
                        val enterTransition = remember { androidx.compose.animation.core.MutableTransitionState(false) }.apply { targetState = true }
                        AnimatedVisibility(
                            visibleState = enterTransition,
                            enter = fadeIn() + expandVertically(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
                        ) {
                            DynamicNoteEditor(
                                note = note,
                                noteIndex = index,
                                onNoteChange = { updatedNote ->
                                    val newList = cardState.backNotes.value.toMutableList()
                                    newList[index] = updatedNote
                                    cardState.backNotes.value = newList
                                },
                                onEditRichTextClick = {
                                    onOpenRichTextEditor("backNote_$index", note.content, "Edit ${note.name}")
                                },
                                onRemove = {
                                    val removedNote = cardState.backNotes.value[index]
                                    val newList = cardState.backNotes.value.toMutableList()
                                    newList.removeAt(index)
                                    cardState.backNotes.value = newList
        
                                    coroutineScope.launch {
                                        snackbarHostState.currentSnackbarData?.dismiss()
                                        val result = snackbarHostState.showSnackbar(
                                            message = "Note removed",
                                            actionLabel = "Undo",
                                            duration = androidx.compose.material3.SnackbarDuration.Short // FIXED: Prevent permanent snackbar
                                        )
                                        if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                                            val restoreList = cardState.frontNotes.value.toMutableList()
                                            restoreList.add(index.coerceIn(0, restoreList.size), removedNote)
                                            cardState.frontNotes.value = restoreList
                                        }
                                    }
                                }
                            )
                        }
                    }
        
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    CardTagRow(
                        cardTags = cardState.tags.value,
                        allTags = allTags,
                        currentDeckTags = currentDeckTags,
                        onUpdateTags = onUpdateTags,
                        onCreateTag = onCreateTag
                    )
        
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        DifficultySlider(
                            label = stringResource(R.string.difficulty),
                            difficulty = cardState.difficulty.value,
                            onDifficultyChange = { cardState.difficulty.value = it },
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.width(dimensions.spacingMedium))
                        Box(modifier = Modifier.padding(bottom = dimensions.paddingSmall).size(48.dp)) {
                            MarkKnownButton(
                                isKnown = cardState.isKnown.value,
                                onClick = onKnownClick
                            )
                        }
                    }
                }
            }
        )
    }
}

@Composable
fun CustomVerticalScrollbar(
    listState: androidx.compose.foundation.lazy.LazyListState,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val totalItemsCount = listState.layoutInfo.totalItemsCount
    val density = LocalDensity.current
    var barHeight by remember { mutableStateOf(0f) }

    if (totalItemsCount > 1) {
        Box(
            modifier = modifier
                .onSizeChanged { barHeight = it.height.toFloat() }
                .pointerInput(totalItemsCount, barHeight) {
                    detectVerticalDragGestures(
                        onDragStart = { offset ->
                            if (barHeight > 0) {
                                val percentage = (offset.y / barHeight).coerceIn(0f, 1f)
                                val index = (percentage * (totalItemsCount - 1)).toInt()
                                coroutineScope.launch { listState.scrollToItem(index) }
                            }
                        },
                        onVerticalDrag = { change, _ ->
                            if (barHeight > 0) {
                                val percentage = (change.position.y / barHeight).coerceIn(0f, 1f)
                                val index = (percentage * (totalItemsCount - 1)).toInt()
                                coroutineScope.launch { listState.scrollToItem(index) }
                            }
                        }
                    )
                }
        ) {
            if (barHeight > 0 && totalItemsCount > 0) {
                val visibleItems = listState.layoutInfo.visibleItemsInfo.size
                val thumbHeightPx = (barHeight * visibleItems / totalItemsCount).coerceAtLeast(100f)
                val firstVisible = listState.firstVisibleItemIndex
                val scrollOffsetPx = (firstVisible.toFloat() / totalItemsCount) * barHeight

                val thumbHeightDp = with(density) { thumbHeightPx.toDp() }
                val scrollOffsetDp = with(density) { scrollOffsetPx.toDp() }

                Box(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .offset(y = scrollOffsetDp)
                        .width(6.dp)
                        .height(thumbHeightDp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.5f), CircleShape)
                )
            }
        }
    }
}

@Composable
fun CardSideEditor(
    sideLabel: String,
    plainText: String,
    onPlainTextChange: (String) -> Unit,
    isRichText: Boolean,
    onToggleRichText: (Boolean) -> Unit,
    onEditRichTextClick: () -> Unit,
    modifier: Modifier = Modifier,
    actionIcon: @Composable (() -> Unit)? = null
) {
    val dimensions = LocalStudiareDimensions.current
    Column(modifier = modifier) {
        Text(sideLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(horizontal = dimensions.paddingSmall))
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.weight(1f)) {
                if (isRichText) {
                    Box(modifier = Modifier.fillMaxWidth().clickable { onEditRichTextClick() }) {
                        TextField(
                            value = plainText.takeIf { it.isNotBlank() } ?: "Click to edit rich text...",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                focusedIndicatorColor = Color.Transparent,
                                unfocusedIndicatorColor = Color.Transparent,
                                disabledIndicatorColor = Color.Transparent,
                                disabledTextColor = MaterialTheme.colorScheme.onSurface
                            ),
                            trailingIcon = {
                                TextButton(onClick = { onToggleRichText(false) }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                                    Text(getText(R.string.rich_text))
                                }
                            }
                        )
                    }
                } else {
                    TextField(
                        value = plainText,
                        onValueChange = onPlainTextChange,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent
                        ),
                        trailingIcon = {
                            TextButton(onClick = { onToggleRichText(true) }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                                Text(getText(R.string.plain_text))
                            }
                        }
                    )
                }
            }
            if (actionIcon != null) {
                actionIcon()
            }
        }
    }
}

@Composable
fun DynamicNoteEditor(
    note: NoteField,
    noteIndex: Int,
    onNoteChange: (NoteField) -> Unit,
    onEditRichTextClick: () -> Unit,
    onRemove: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var showTypeDropdown by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val primaryColor = MaterialTheme.colorScheme.primary

    val visualMediaLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.OpenDocument(),
        onResult = { uri ->
            uri?.let {
                val localPath = MediaStorageUtils.copyMediaToInternalStorage(context, it, "media")
                if (localPath != null) onNoteChange(note.copy(content = localPath))
            }
        }
    )

    val audioLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.GetContent(),
        onResult = { uri ->
            uri?.let {
                val localPath = MediaStorageUtils.copyMediaToInternalStorage(context, it, "audio")
                if (localPath != null) onNoteChange(note.copy(content = localPath))
            }
        }
    )

    val typeDropdown = @Composable {
        Box {
            TextButton(onClick = { showTypeDropdown = true }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                Text(note.type.toString())
            }
            androidx.compose.material3.DropdownMenu(
                expanded = showTypeDropdown,
                onDismissRequest = { showTypeDropdown = false }
            ) {
                MediaType.entries.forEach { mediaType ->
                    DropdownMenuItem(
                        text = { Text(mediaType.toString()) },
                        onClick = {
                            var newContent = note.content
                            val isOldMedia = note.type in listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO)
                            val isNewMedia = mediaType in listOf(MediaType.IMAGE, MediaType.VIDEO, MediaType.AUDIO)

                            if (isOldMedia != isNewMedia) {
                                newContent = ""
                            }
                            else if (mediaType == MediaType.PLAIN_TEXT && (note.type == MediaType.RICH_TEXT || note.type == MediaType.HTML)) {
                                newContent = newContent.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim()
                            }

                            onNoteChange(note.copy(type = mediaType, content = newContent))
                            showTypeDropdown = false
                        }
                    )
                }
            }
        }
    }

    // Standardized M3 Expressive Field Colors
    val textFieldColors = TextFieldDefaults.colors(
        focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        focusedIndicatorColor = Color.Transparent,
        unfocusedIndicatorColor = Color.Transparent,
        disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        disabledIndicatorColor = Color.Transparent,
        disabledTextColor = MaterialTheme.colorScheme.onSurface
    )

    // Shapes to merge the two text fields seamlessly
    val topShape = RoundedCornerShape(
        topStart = dimensions.cornerRadiusMedium,
        topEnd = dimensions.cornerRadiusMedium,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
    val bottomShape = RoundedCornerShape(
        topStart = 0.dp,
        topEnd = 0.dp,
        bottomStart = dimensions.cornerRadiusMedium,
        bottomEnd = dimensions.cornerRadiusMedium
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = dimensions.spacingMedium),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                // The visual grouping line
                .drawBehind {
                    val dotRadius = 4.dp.toPx() // Adjust this value to make the dot larger or smaller
                    drawCircle(
                        color = primaryColor.copy(alpha = 0.4f), // Remove the .copy() if you want a solid color
                        radius = dotRadius,
                        center = androidx.compose.ui.geometry.Offset(
                            x = dotRadius,     // Places the dot right on the left edge
                            y = size.height / 2f    // Centers the dot vertically
                        )
                    )
                }
                .padding(start = dimensions.paddingMedium) // Indent away from the line
        ) {
            // TOP LINE: Field Name
            TextField(
                value = note.name,
                onValueChange = { onNoteChange(note.copy(name = it)) },
                placeholder = { Text(stringResource(R.string.field_number_placeholder_format, noteIndex + 1)) },
                modifier = Modifier.fillMaxWidth(),
                shape = topShape,
                singleLine = true,
                textStyle = MaterialTheme.typography.labelMedium.copy(color = primaryColor),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedTextColor = primaryColor,
                    unfocusedTextColor = primaryColor,
                    focusedPlaceholderColor = primaryColor.copy(alpha = 0.6f),
                    unfocusedPlaceholderColor = primaryColor.copy(alpha = 0.6f)
                )
            )

            androidx.compose.material3.HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // BOTTOM LINE: Content
            when (note.type) {
                MediaType.PLAIN_TEXT, MediaType.WEB_LINK, MediaType.HTML -> {
                    // The hint says what the box expects for the selected type, and disappears once typing starts
                    val hintRes = when (note.type) {
                        MediaType.WEB_LINK -> R.string.note_hint_web_link
                        MediaType.HTML -> R.string.note_hint_html
                        else -> R.string.note_hint_plain_text
                    }
                    TextField(
                        value = note.content,
                        onValueChange = { onNoteChange(note.copy(content = it)) },
                        placeholder = { Text(stringResource(hintRes)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = bottomShape,
                        colors = textFieldColors,
                        trailingIcon = typeDropdown
                    )
                }
                MediaType.RICH_TEXT -> {
                    Box(modifier = Modifier.fillMaxWidth().clickable { onEditRichTextClick() }) {
                        TextField(
                            value = note.content.replace(Regex("<[^>]*>"), "").replace("&nbsp;", " ").trim().takeIf { it.isNotBlank() } ?: "Click to edit rich text...",
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = bottomShape,
                            colors = textFieldColors,
                            leadingIcon = {
                                TooltipIconButton(description = getText(R.string.edit_rich_text), onClick = onEditRichTextClick) {
                                    Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_rich_text))
                                }
                            },
                            trailingIcon = typeDropdown
                        )
                    }
                }
                MediaType.IMAGE, MediaType.VIDEO -> {
                    val hasMedia = note.content.isNotBlank() && note.content.startsWith(context.filesDir.absolutePath)
                    val displayInfo = if (!hasMedia) "Add Media..." else note.content.substringAfterLast('/')
                    Box(modifier = Modifier.fillMaxWidth().clickable {
                        val mimeType = if (note.type == MediaType.IMAGE) arrayOf("image/*") else arrayOf("video/*")
                        visualMediaLauncher.launch(mimeType)
                    }) {
                        TextField(
                            value = displayInfo,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = bottomShape,
                            colors = textFieldColors,
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            trailingIcon = typeDropdown
                        )
                    }
                }
                MediaType.AUDIO -> {
                    val hasMedia = note.content.isNotBlank() && note.content.startsWith(context.filesDir.absolutePath)
                    val displayInfo = if (!hasMedia) "Add Audio..." else note.content.substringAfterLast('/')
                    Box(modifier = Modifier.fillMaxWidth().clickable { audioLauncher.launch("audio/*") }) {
                        TextField(
                            value = displayInfo,
                            onValueChange = {},
                            readOnly = true,
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = bottomShape,
                            colors = textFieldColors,
                            leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                            trailingIcon = typeDropdown
                        )
                    }
                }
            }
        }

        TooltipIconButton(description = getText(R.string.remove_note), 
            onClick = onRemove,
            modifier = Modifier.padding(start = dimensions.spacingSmall, top = 8.dp)
        ) {
            Icon(Icons.Default.Close, contentDescription = getText(R.string.remove_note), tint = MaterialTheme.colorScheme.error)
        }
    }
}
