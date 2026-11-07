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
import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import net.ericclark.studiare.components.getText
import kotlinx.coroutines.delay
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import net.ericclark.studiare.ui.theme.pressSquish
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import coil.compose.AsyncImage
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import net.ericclark.studiare.components.WindowSizeOverlay

/**
 * Shared "which page is active" logic for the dot indicators below any `LazyRow`-based carousel
 * (session tiles, presets, sets, decks, ...): the active indicator is the first item that's fully
 * visible in the viewport (i.e. the left-most fully-on-screen card), not whichever item happens to
 * be nearest the viewport's center. Falls back to the left-most visible item (even if only
 * partially visible) on the rare frame where nothing is fully visible, e.g. mid-fling or an item
 * wider than the viewport itself.
 */
@Composable
fun rememberCarouselIndicatorIndex(listState: LazyListState): State<Int> = remember {
    derivedStateOf {
        val layoutInfo = listState.layoutInfo
        val visibleItemsInfo = layoutInfo.visibleItemsInfo
        if (visibleItemsInfo.isEmpty()) {
            0
        } else {
            val viewportStart = layoutInfo.viewportStartOffset
            val viewportEnd = layoutInfo.viewportEndOffset
            visibleItemsInfo.firstOrNull { it.offset >= viewportStart && it.offset + it.size <= viewportEnd }?.index
                ?: visibleItemsInfo.first().index
        }
    }
}

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
    AnimatedDialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog), // M3 Expressive Dialog Shape
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier
                .widthIn(min = 280.dp, max = 560.dp)
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
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { MutableInteractionSource() }

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
            .pressSquish(interactionSource, 0.97f)
            .clip(RoundedCornerShape(dimensions.cornerRadiusLarge))
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
        // A Compose Dialog opens its own Android Window, drawn above the activity's — so the
        // debug WindowSizeOverlay (normally placed once at the activity's top level) is invisible
        // behind any open dialog unless it's also drawn inside this window. Wrapping content in a
        // fillMaxSize Box lets it share this dialog's window instead of needing its own —
        // contentAlignment = Center keeps real dialog content centered within that now-full-size
        // Box instead of defaulting to Box's own TopStart (see the wrapContentSize() note below
        // for why the Box needs to be full-size at all).
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            AnimatedVisibility(
                visibleState = visibleState,
                // wrapContentSize(): with `usePlatformDefaultWidth = false` (used by most dialogs
                // here for edge-to-edge width on phones), Android measures the Dialog window's
                // root content with an EXACTLY/tight width spec (MATCH_PARENT), not a loose/AT_MOST
                // one — confirmed via ViewRootImpl.getRootMeasureSpec. A tight incoming constraint
                // breaks every dialog's own `Modifier.widthIn(max = 560.dp)`-style cap: `widthIn`
                // "enforces incoming" by coercing its own target range to stay WITHIN whatever the
                // parent already dictated, so when the parent is tight at, say, 852dp, the 560dp
                // cap gets clamped back UP to 852dp instead of shrinking it — the cap becomes a
                // total no-op on any screen wider than its own max. (Window HEIGHT uses
                // WRAP_CONTENT/AT_MOST, i.e. a genuinely loose constraint, which is why each
                // dialog's `heightIn(max = ...)` cap already worked correctly on its own.)
                // wrapContentSize() loosens the constraint this AnimatedVisibility hands down to
                // `content()` back to a real 0..852 range, so every dialog's own existing
                // widthIn/heightIn chain resolves the way it was written to, and this Box's
                // contentAlignment then centers the correctly-capped result — without editing the
                // ~40 individual dialog call sites that rely on this pattern. Dialogs that
                // deliberately want more than the 560dp cap (the Anki field-mapping dialog's
                // drag-and-drop canvas, ManualSetCreatorDialog) size themselves via
                // fillMaxSize()/fillMaxWidth(fraction) instead of widthIn, both of which compute
                // from the incoming constraint's max regardless of looseness — unaffected by this.
                modifier = Modifier.wrapContentSize(),
                // NOT expandVertically/shrinkVertically: unlike scale, those actually animate the
                // AnimatedVisibility container's real layout height frame-by-frame, not just a visual
                // transform. Several dialogs under this shared wrapper size their own content as a
                // fraction of whatever height they're given (CreateStudySessionDialog's
                // Modifier.fillMaxHeight(0.98f) plus its own documented "infinite-height probe" for
                // its weighted panels is one) — fighting that against a container whose height is
                // itself mid-animation caused a genuine measure/animate feedback loop (confirmed via
                // screen recording: the dialog's content visibly flickered open/closed in a cycle
                // instead of settling). A plain fade has no layout-size component at all, so it can't
                // conflict with any dialog's internal sizing logic — still not scale, just not
                // spatial either, which M3 doesn't require.
                enter = fadeIn(animationSpec = motionScheme.defaultEffectsSpec()),
                // Exit uses the fast spec (see MainActivity's NavHost transitions for why) so the
                // dialog clears quickly instead of cross-fading at full duration against new content.
                exit = fadeOut(animationSpec = motionScheme.fastEffectsSpec())
            ) {
                content()
            }
            if (LocalShowSizeOverlay.current) {
                WindowSizeOverlay(Modifier.align(Alignment.TopEnd))
            }
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
    // Bypasses AnimatedDialog (a full-screen, non-animated media viewer doesn't need the fade),
    // so it needs its own copy of the "debug overlay shares this dialog's window" fix.
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
            // Close button
            TooltipIconButton(description = getText(R.string.close_capitalized), onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(16.dp)) {
                Icon(Icons.Default.Close, contentDescription = getText(R.string.close_capitalized), tint = Color.White)
            }
            if (LocalShowSizeOverlay.current) {
                // TopStart here, not TopEnd like everywhere else — the close button above already
                // occupies this screen's top-end corner.
                WindowSizeOverlay(Modifier.align(Alignment.TopStart))
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
