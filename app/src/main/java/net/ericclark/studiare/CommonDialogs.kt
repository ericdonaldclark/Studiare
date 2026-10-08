package net.ericclark.studiare

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.width
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.data.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import net.ericclark.studiare.components.getText
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items

/**
 * Shared "Select Collection" dialog. Used both by the compact-mode collection
 * name/dropdown in the top app bar and by the wide-screen NavigationRail's
 * collections button, so both entry points open the exact same UI.
 */
@Composable
fun CollectionPickerDialog(
    selectedCollectionId: String?,
    allCollections: List<CollectionWithDecks>,
    onSelectCollection: (String?) -> Unit,
    onEditCollections: () -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(28.dp), // M3 Expressive Dialog Shape
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 500.dp)
        ) {
            Column(modifier = Modifier.padding(24.dp)) {
                Text(
                    text = "Select Collection",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    item {
                        SelectableDialogItem(
                            text = getText(R.string.decks_all),
                            isSelected = selectedCollectionId == null,
                            onClick = { onSelectCollection(null) }
                        )
                    }
                    items(allCollections, key = { it.collection.id }) { collectionData ->
                        SelectableDialogItem(
                            text = collectionData.collection.name,
                            isSelected = selectedCollectionId == collectionData.collection.id,
                            onClick = { onSelectCollection(collectionData.collection.id) }
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
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        onClick = onEditCollections,
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.edit_collections))
                    }
                }
            }
        }
    }
}

@Composable
fun SelectableDialogItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "selectableItemSquish"
    )

    val containerColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        label = "containerColor"
    )
    val contentColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "contentColor"
    )
    val fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(containerColor)
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onClick = onClick
            )
            .padding(vertical = 16.dp, horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = fontWeight),
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(Icons.Default.Check, contentDescription = getText(R.string.selected), tint = contentColor)
        }
    }
}

/**
 * A drop-in replacement for [Dialog] that scales+fades its content in and out using the
 * current [MaterialTheme.motionScheme], instead of the platform's instant show/hide.
 * [onDismissRequest] is only invoked once the exit animation finishes, so callers can
 * safely remove the dialog from composition (e.g. `showDialog = false`) in response to it.
 */
@Composable
fun AnimatedDialog(
    onDismissRequest: () -> Unit,
    properties: DialogProperties = DialogProperties(),
    content: @Composable () -> Unit
) {
    val visibleState = remember { MutableTransitionState(false) }
    visibleState.targetState = true
    val motionScheme = MaterialTheme.motionScheme

    // Lets screens (see Modifier.autoFocusable) know not to reclaim keyboard focus for
    // themselves while a dialog is on top of them.
    DisposableEffect(Unit) {
        DialogTracker.openCount.intValue++
        onDispose { DialogTracker.openCount.intValue-- }
    }

    LaunchedEffect(visibleState) {
        snapshotFlow { visibleState.isIdle && !visibleState.targetState }
            .collect { shouldDismiss -> if (shouldDismiss) onDismissRequest() }
    }

    Dialog(
        onDismissRequest = { visibleState.targetState = false },
        properties = properties
    ) {
        AnimatedVisibility(
            visibleState = visibleState,
            enter = scaleIn(initialScale = 0.9f, animationSpec = motionScheme.defaultSpatialSpec()) +
                fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
            exit = scaleOut(targetScale = 0.9f, animationSpec = motionScheme.defaultSpatialSpec()) +
                fadeOut(animationSpec = motionScheme.defaultEffectsSpec())
        ) {
            content()
        }
    }
}

@Composable
fun ConfirmationDialog(
    title: String,
    text: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmButtonText: String? = null,
    dismissButtonText: String? = null, // ADD THIS PARAMETER
    icon: @Composable (() -> Unit)? = null
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .widthIn(min = 280.dp, max = 560.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                if (icon != null) {
                    Box(modifier = Modifier.padding(bottom = dimensions.spacingSmall)) { icon() }
                }
                Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(dimensions.spacingSmall))
                Text(text, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(dismissButtonText ?: getText(R.string.cancel))
                    }
                    Spacer(modifier = Modifier.width(dimensions.spacingSmall))
                    Button(onClick = onConfirm, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(confirmButtonText ?: getText(R.string.confirm))
                    }
                }
            }
        }
    }
}

@Composable
fun FullScreenMediaViewerDialog(note: NoteField, onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Close button
            TooltipIconButton(description = getText(R.string.close_capitalized), onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Close, contentDescription = getText(R.string.close_capitalized), tint = Color.White)
            }

            // Content
            Box(modifier = Modifier.fillMaxSize().padding(top = 64.dp, bottom = 64.dp), contentAlignment = Alignment.Center) {
                when (note.type) {
                    MediaType.IMAGE -> {
                        AsyncImage(
                            model = note.content,
                            contentDescription = note.name,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )
                    }
                    MediaType.VIDEO -> {
                        androidx.compose.ui.viewinterop.AndroidView(
                            factory = { context ->
                                android.widget.VideoView(context).apply {
                                    setVideoPath(note.content)
                                    val mediaController = android.widget.MediaController(context)
                                    mediaController.setAnchorView(this)
                                    setMediaController(mediaController)
                                    start()
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    MediaType.AUDIO -> {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(100.dp))
                            Spacer(Modifier.height(32.dp))
                            var isPlaying by remember { mutableStateOf(false) }
                            val mediaPlayer = remember { android.media.MediaPlayer() }

                            DisposableEffect(note.content) {
                                try {
                                    mediaPlayer.setDataSource(note.content)
                                    mediaPlayer.prepare()
                                } catch (e: Exception) { e.printStackTrace() }
                                onDispose { mediaPlayer.release() }
                            }

                            TooltipIconButton(description = getText(R.string.play_pause), 
                                onClick = {
                                    if (mediaPlayer.isPlaying) {
                                        mediaPlayer.pause()
                                        isPlaying = false
                                    } else {
                                        mediaPlayer.start()
                                        isPlaying = true
                                    }
                                },
                                modifier = Modifier.size(80.dp).background(MaterialTheme.colorScheme.primary, CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Default.Clear else Icons.Default.PlayArrow,
                                    contentDescription = getText(R.string.play_pause),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }
                    }
                    else -> {}
                }
            }

            // Label
            Text(
                text = note.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.BottomCenter).padding(32.dp)
            )
        }
    }
}

/**
 * A centered loading indicator held back for [delayMillis], so fast loads never flash it. Used
 * as the alternative to skeleton loaders (see the Layout & Loading settings).
 *
 * [isLoading] is the real, live loading state — not just whether this composable is currently
 * mounted. A caller wrapping this in its own exit transition (a fade-out, an `AnimatedContent`
 * shrink, etc.) keeps it composed for the length of that transition, well after the real data
 * has already arrived; keying the delay on [isLoading] instead of `Unit` means the pending
 * delay is cancelled and the spinner hides immediately once [isLoading] goes false, regardless
 * of whatever animation the caller still has it wrapped in.
 *
 * The delay's completion is also re-checked against the latest [isLoading] via
 * [rememberUpdatedState] rather than trusting the value captured when the delay started:
 * confirmed on-device, real data can arrive in the same instant the delay elapses, and Compose
 * cancelling the stale coroutine (because its key changed) isn't guaranteed to win that race —
 * without the re-check, the spinner could still flip to visible for one stale write before the
 * next recomposition corrects it, which is what "shows right as/after loading finishes" turned
 * out to be: not a delay tuning issue, a delay-vs-real-data ordering race, so changing
 * [delayMillis] alone can never fix it.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DelayedLoadingIndicator(isLoading: Boolean, modifier: Modifier = Modifier, delayMillis: Long = 400) {
    var showSpinner by remember { mutableStateOf(false) }
    val currentIsLoading = rememberUpdatedState(isLoading)
    LaunchedEffect(isLoading) {
        if (isLoading) {
            kotlinx.coroutines.delay(delayMillis)
            if (currentIsLoading.value) showSpinner = true
        } else {
            showSpinner = false
        }
    }
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        if (showSpinner) LoadingIndicator()
    }
}
