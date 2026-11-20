package net.ericclark.studiare.screens.UI_Components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import java.util.*
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.data.*
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.foundation.LocalIndication
import androidx.compose.material3.MaterialTheme.shapes
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.SplitButtonDefaults
import androidx.navigation.NavController
import androidx.compose.runtime.livedata.observeAsState
import net.ericclark.studiare.screens.Dialogs.studyRouteFor
import net.ericclark.studiare.screens.Dialogs.createStudySessionRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeckListItem(
    deck: DeckSummary,
    dimensions: StudiareDimensions,
    setsCount: Int,
    onStudy: (String?) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageSets: () -> Unit,
    onTogglePin: (() -> Unit)? = null,
    showManageSetsButton: Boolean = true,
    tapOpensStudy: Boolean = true,
    index: Int = -1,
    activeSession: ActiveSession? = null,
    onResumeSession: (ActiveSession) -> Unit = {},
    onRestartSession: (ActiveSession) -> Unit = {},
    onCreateSession: () -> Unit = {},
    // Both null by default, used only by the Pinned screen's Sets carousel (which renders sets
    // full-size via this composable, same as Set Manager does) to show a set's parent-deck path
    // (e.g. "Nouns -> Set 1", or "Nouns...Set 1" with generations skipped when nested more than
    // one level deep under another set) instead of the set's bare name. Every other caller leaves
    // these null and gets the original plain-name behavior.
    displayName: String? = null,
    fullPathTooltip: String? = null,
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    val cardBorderColor =
        if (isCardFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    SelectableTileOverlay(selected = selected, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(dimensions.cornerRadiusMedium)) {
    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = dimensions.cardElevation,
            pressedElevation = 8.dp
        ),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .pressSquish(cardInteractionSource, 0.98f)
            .let {
                if (index in 0..8) {
                    val keyMap = listOf(
                        Key.One,
                        Key.Two,
                        Key.Three,
                        Key.Four,
                        Key.Five,
                        Key.Six,
                        Key.Seven,
                        Key.Eight,
                        Key.Nine
                    )
                    it.withShortcut(keyMap[index], "${index + 1}") {
                        onStudy(null)
                    }
                } else it
            }
            .border(
                if (isCardFocused) 6.dp else 0.dp,
                cardBorderColor,
                RoundedCornerShape(dimensions.cornerRadiusMedium)
            )
            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
            .combinedClickable(
                interactionSource = cardInteractionSource,
                indication = LocalIndication.current,
                onLongClick = onLongClick
            ) {
                if (selectionMode) {
                    onToggleSelect()
                } else {
                    // On deck tiles, tapping opens study sessions (a deck with no cards goes to its sets).
                    // Set tiles pass tapOpensStudy = false: tapping opens the set's sets page and only the
                    // Study button opens sessions.
                    if (tapOpensStudy && deck.totalCards > 0) onStudy(null) else onManageSets()
                }
            }
    ) {
        // At small (Compact) padding, the fixed corner radius crowds the name/count into the
        // top-left corner; keep at least as much clearance as the corner itself needs.
        Column(modifier = Modifier.padding(dimensions.paddingMedium.coerceAtLeast(dimensions.cornerRadiusMedium))) {
            // alignByBaseline (not a Row-level verticalAlignment) lines up the Manage Sets button
            // with the name's own baseline specifically, regardless of what's below it (the chip)
            // — a plain Alignment.Bottom would instead align with the bottom of this whole Column
            // (name+chip), and a weight+Bottom combination here previously blew up this Row's
            // measured height for reasons that weren't worth chasing further; alignByBaseline is
            // the standard, well-supported Compose tool for "align with this one line of text."
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).alignByBaseline()) {
                    val titleText = displayName ?: deck.deck.name
                    if (fullPathTooltip != null) {
                        // Generations were skipped in titleText (e.g. "Nouns...Set 1") — always
                        // show the full path on tooltip, not just when the (already-shortened)
                        // text itself overflows, since the point is the display text hides real
                        // segments regardless of whether it happens to fit.
                        TooltipBox(
                            positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
                                positioning = TooltipAnchorPosition.Below
                            ),
                            tooltip = { PlainTooltip { Text(fullPathTooltip) } },
                            state = rememberTooltipState(isPersistent = true)
                        ) {
                            Text(
                                text = titleText,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        TextWithOverflowTooltip(
                            text = titleText,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    SuggestionChip(
                        onClick = { },
                        label = { Text(pluralStringResource(R.plurals.cards_count, deck.totalCards, deck.totalCards)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            labelColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ),
                        border = null
                    )
                }

                if (showManageSetsButton) {
                    // Changed to display number of sets
                    val manageInteractionSource = remember { MutableInteractionSource() }
                    TextButton(
                        onClick = onManageSets,
                        interactionSource = manageInteractionSource,
                        modifier = Modifier.alignByBaseline().pressSquish(manageInteractionSource),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Icon(
                            Icons.Default.AccountTree,
                            getText(R.string.manage_sets),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            stringResource(R.string.sets_count_simple, setsCount),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Spacer(Modifier.height(dimensions.paddingLarge))

            // One line: the Study button never wraps. The flexible spacer on the left is what gives way on a
            // narrow tile, so the space before the edit icon shrinks first.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Spacer(Modifier.weight(1f))
                val editInteractionSource = remember { MutableInteractionSource() }
                Row(verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(description = getText(R.string.edit),
                    onClick = onEdit,
                    interactionSource = editInteractionSource,
                    modifier = Modifier.pressSquish(editInteractionSource, 0.85f)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        getText(R.string.edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onTogglePin != null) {
                    val pinInteractionSource = remember { MutableInteractionSource() }
                    val pinTint by animateColorAsState(
                        targetValue = if (deck.deck.isPinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "pinColor"
                    )
                    val pinLabel = if (deck.deck.parentDeckId == null) {
                        if (deck.deck.isPinned) getText(R.string.unpin_deck) else getText(R.string.pin_deck)
                    } else {
                        if (deck.deck.isPinned) getText(R.string.unpin_set) else getText(R.string.pin_set)
                    }
                    TooltipIconButton(
                        description = pinLabel,
                        onClick = onTogglePin,
                        interactionSource = pinInteractionSource,
                        modifier = Modifier.pressSquish(pinInteractionSource, 0.85f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PushPin,
                            contentDescription = pinLabel,
                            tint = pinTint
                        )
                    }
                }

                val deleteInteractionSource = remember { MutableInteractionSource() }
                TooltipIconButton(description = getText(R.string.delete),
                    onClick = onDelete,
                    interactionSource = deleteInteractionSource,
                    modifier = Modifier.pressSquish(deleteInteractionSource, 0.85f)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        getText(R.string.delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
                }
                StudySplitButton(
                    onStudyMain = { onStudy(null) },
                    onResumeSession = activeSession?.let { session -> { onResumeSession(session) } },
                    onRestartSession = activeSession?.let { session -> { onRestartSession(session) } },
                    onCreateSession = onCreateSession,
                    enabled = deck.totalCards > 0,
                )
                /*
                val studyInteractionSource = remember { MutableInteractionSource() }
                val isStudyPressed by studyInteractionSource.collectIsPressedAsState()
                val studyScale by animateFloatAsState(
                    targetValue = if (isStudyPressed) 0.95f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "studySquish"
                )
                Button(
                    onClick = onStudy,
                    interactionSource = studyInteractionSource,
                    modifier = Modifier.scale(studyScale),
                    enabled = deck.cards.isNotEmpty(),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(getText(R.string.study))
                }
                */
            }
        }
    }
    }
}

@Composable
fun SetListItem(
    deck: DeckSummary,
    dimensions: StudiareDimensions,
    onStudy: (String?) -> Unit,
    onEdit: () -> Unit = {},
    onDelete: () -> Unit = {},
    onManageSets: () -> Unit = {},
    onTogglePin: (() -> Unit)? = null,
    activeSession: ActiveSession? = null,
    onResumeSession: (ActiveSession) -> Unit = {},
    onRestartSession: (ActiveSession) -> Unit = {},
    onCreateSession: () -> Unit = {},
    selected: Boolean = false,
    selectionMode: Boolean = false,
    onToggleSelect: () -> Unit = {},
    onLongClick: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent
    var showOverflow by remember { mutableStateOf(false) }

    SelectableTileOverlay(selected = selected, shape = RoundedCornerShape(dimensions.cornerRadiusMedium)) {
    Card(
        modifier = Modifier
            // Width stays fixed for a uniform carousel row; height is now content-driven, same
            // as DeckListItem, so both the content-to-button and button-to-bottom-edge gaps
            // match the deck tile's exactly instead of stretching/compressing to fill a fixed
            // height.
            .width(dimensions.setTileWidth)
            .border(
                if (isFocused) 6.dp else 0.dp,
                borderColor,
                RoundedCornerShape(dimensions.cornerRadiusMedium)
            )
            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
            .combinedClickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current,
                onLongClick = onLongClick
            ) {
                // Tapping elsewhere in the tile opens this set's Study Hub, same as tapping the
                // split button's main action — it no longer jumps to the PARENT deck's set
                // manager (that was never this set's own manager to begin with). Drilling into
                // this set's own sets is now only reachable via the overflow menu's "Manage".
                if (selectionMode) onToggleSelect() else onStudy(null)
            },
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                // Same corner-clearance floor as DeckListItem, above.
                .padding(dimensions.paddingMedium.coerceAtLeast(dimensions.cornerRadiusMedium))
        ) {
            // Top-right corner, same as DeckListItem's Manage Sets button — not next to the
            // Study button below, which has no room to spare on this tile's fixed setTileWidth.
            //
            // Baseline alignment (alignByBaseline) turned out unreliable here: Material's type
            // styles carry line-height padding above/below the actual glyphs, so a font's
            // "baseline" isn't the same point as the text's visual bottom — and a bare Icon has no
            // baseline at all to approximate it with, unlike DeckListItem's Manage Sets button
            // (real Text inside, so baseline alignment happened to land close enough there).
            // Instead: measure the name's REAL rendered line-bottom via onTextLayout, measure the
            // button's own actual height via onSizeChanged, and offset the button by the exact
            // difference — exact pixel math from real measurements, not a font-metric guess, and
            // it never touches the button's own size/touch target.
            var nameLineBottomPx by remember { mutableStateOf(0f) }
            var overflowButtonHeightPx by remember { mutableStateOf(0f) }
            // The icon isn't drawn at the bottom of its own touch-target box — IconButton centers
            // it within that (larger) box — so matching the box's bottom to the text's line-bottom
            // left the icon glyph itself floating well above the text. This measures the icon's
            // own rendered size too, so the offset below targets where the GLYPH ends up, not the
            // invisible touch-target bounds around it.
            var overflowIconHeightPx by remember { mutableStateOf(0f) }
            // Same story horizontally: the icon sits centered inside its touch-target box
            // left-to-right too, leaving more empty space between it and the tile's right edge
            // than the name has on the left. Shifting the box right by half the box/icon width
            // difference brings the icon's own right edge flush with where the (unshifted) box's
            // right edge already lines up against the Column's matching left padding.
            var overflowButtonWidthPx by remember { mutableStateOf(0f) }
            var overflowIconWidthPx by remember { mutableStateOf(0f) }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    TextWithOverflowTooltip(
                        text = deck.deck.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        onTextLayout = { nameLineBottomPx = it.getLineBottom(0) }
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        pluralStringResource(R.plurals.cards_count_lowercase, deck.totalCards, deck.totalCards),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Edit/Pin/Delete/Manage, collapsed into one overflow button — DeckListItem shows
                // these as separate bare icons, but the set tile's fixed setTileWidth has no room
                // for four more icons.
                Box(
                    modifier = Modifier
                        .onSizeChanged {
                            overflowButtonHeightPx = it.height.toFloat()
                            overflowButtonWidthPx = it.width.toFloat()
                        }
                        .offset {
                            // The icon glyph sits centered inside the touch-target box, i.e. at
                            // boxTop + (boxHeight + iconHeight) / 2 once placed — solve for the
                            // boxTop (this offset, since the Row places it at relative 0 by
                            // default) that puts that point exactly at the name's line-bottom.
                            val targetY = nameLineBottomPx - (overflowButtonHeightPx + overflowIconHeightPx) / 2f
                            val targetX = (overflowButtonWidthPx - overflowIconWidthPx) / 2f
                            IntOffset(targetX.roundToInt(), targetY.roundToInt())
                        }
                ) {
                    TooltipIconButton(
                        description = getText(R.string.options_more),
                        onClick = { showOverflow = true }
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = getText(R.string.options_more),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.onSizeChanged {
                                overflowIconHeightPx = it.height.toFloat()
                                overflowIconWidthPx = it.width.toFloat()
                            }
                        )
                    }
                    DropdownMenu(expanded = showOverflow, onDismissRequest = { showOverflow = false }) {
                        DropdownMenuItem(
                            text = { Text(getText(R.string.edit)) },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = { showOverflow = false; onEdit() }
                        )
                        if (onTogglePin != null) {
                            val pinned = deck.deck.isPinned
                            DropdownMenuItem(
                                text = { Text(getText(if (pinned) R.string.unpin_set else R.string.pin_set)) },
                                leadingIcon = {
                                    Icon(
                                        Icons.Rounded.PushPin,
                                        contentDescription = null,
                                        tint = if (pinned) MaterialTheme.colorScheme.primary else LocalContentColor.current
                                    )
                                },
                                onClick = { showOverflow = false; onTogglePin() }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(getText(R.string.delete), color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { showOverflow = false; onDelete() }
                        )
                        DropdownMenuItem(
                            text = { Text(getText(R.string.manage_sets)) },
                            // This set's OWN manager (its children), not the parent deck's — see
                            // onManageSets' doc comment on the composable's callers.
                            leadingIcon = { Icon(Icons.Default.AccountTree, contentDescription = null) },
                            onClick = { showOverflow = false; onManageSets() }
                        )
                    }
                }
            }

            // Same fixed gap DeckListItem uses between its content block and the button row.
            Spacer(Modifier.height(dimensions.paddingLarge))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                StudySplitButton(
                    onStudyMain = { onStudy(null) },
                    onResumeSession = activeSession?.let { session -> { onResumeSession(session) } },
                    onRestartSession = activeSession?.let { session -> { onRestartSession(session) } },
                    onCreateSession = onCreateSession,
                    enabled = deck.totalCards > 0
                )
            }
        }
    }
    }
}

@Composable
fun LoadingOverlay(message: String? = null) {
    val dimensions = LocalStudiareDimensions.current
    val displayMessage = message ?: getText(R.string.processing)
    AnimatedDialog(onDismissRequest = { }) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier.padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularWavyProgressIndicator(
                    modifier = Modifier.size(56.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
                Spacer(modifier = Modifier.height(24.dp))
                Text(text = displayMessage, style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
fun StudySplitButton(
    onStudyMain: () -> Unit,
    onResumeSession: (() -> Unit)? = null,
    onRestartSession: (() -> Unit)? = null,
    onCreateSession: () -> Unit = {},
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    includeText: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    val dimensions = LocalStudiareDimensions.current

    // 1. Create the asymmetric shape for the Left (Leading) button
    val leadingShape = RoundedCornerShape(
        topStart = dimensions.cornerRadiusButton,
        bottomStart = dimensions.cornerRadiusButton,
        topEnd = 0.dp,
        bottomEnd = 0.dp
    )

    // 2. Create the asymmetric shape for the Right (Trailing) button
    val trailingShape = RoundedCornerShape(
        topStart = 0.dp,
        bottomStart = 0.dp,
        topEnd = dimensions.cornerRadiusButton,
        bottomEnd = dimensions.cornerRadiusButton
    )

    Box(modifier = modifier) {
        SplitButtonLayout(
            leadingButton = {
                SplitButtonDefaults.LeadingButton(
                    onClick = onStudyMain,
                    // THE FIX: Wrap the shape in the required SplitButtonShapes object
                    shapes = androidx.compose.material3.SplitButtonShapes(
                        shape = leadingShape,
                        pressedShape = leadingShape,
                        checkedShape = leadingShape
                    ),
                    modifier = if (!includeText) Modifier.width(64.dp) else Modifier
                ) {
                    if (includeText) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(SplitButtonDefaults.LeadingIconSize)
                        )
                        Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                        Text(getText(R.string.study), fontWeight = FontWeight.Bold)
                    } else {
                        Box(
                            modifier = Modifier.fillMaxWidth(),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            },
            trailingButton = {
                SplitButtonDefaults.TrailingButton(
                    checked = expanded,
                    onCheckedChange = { expanded = it },
                    // THE FIX: Wrap the shape in the required SplitButtonShapes object
                    shapes = androidx.compose.material3.SplitButtonShapes(
                        shape = trailingShape,
                        pressedShape = trailingShape,
                        checkedShape = trailingShape
                    )
                ) {
                    val rotation by androidx.compose.animation.core.animateFloatAsState(
                        targetValue = if (expanded) 180f else 0f,
                        label = "Trailing Icon Rotation"
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = getText(R.string.options_more),
                        modifier = Modifier.graphicsLayer { rotationZ = rotation }
                    )
                }
            }
        )

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            shape = RoundedCornerShape(dimensions.cornerRadiusLarge),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            DropdownMenuItem(
                text = { Text(getText(R.string.resume_last_session)) },
                leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null) },
                enabled = onResumeSession != null,
                onClick = { expanded = false; onResumeSession?.invoke() }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.restart_last_session)) },
                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                enabled = onRestartSession != null,
                onClick = { expanded = false; onRestartSession?.invoke() }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.create_session_label)) },
                leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                onClick = { expanded = false; onCreateSession() }
            )
        }
    }
}

/**
 * Resolves, resumes, restarts and starts fresh sessions for "the most recently touched session for
 * a given deck," for the Study split button's dropdown (Resume/Restart/Create Session). Mirrors the
 * resume/restart pattern already used for saved-session tiles in the navigation drawer tree
 * (NavigationDrawerNode.kt/NavigationDrawerTree.kt) — same pendingResume-then-navigate
 * LaunchedEffect, same restart confirmation dialog — just generalized from "the tile the user
 * tapped" to "whichever session is newest for this deck." Restarting also resumes into the reset
 * session (not just resetting it in place), the same way Resume does, since a restarted session has
 * nothing useful to look at until it's actually opened. Call [rememberDeckSessionActions] once per
 * screen (not once per deck row), so its dialogs and navigation effect aren't duplicated per row.
 */
class DeckSessionActions internal constructor(
    private val sessions: List<ActiveSession>,
    private val onResumeAction: (ActiveSession) -> Unit,
    private val onRestartAction: (ActiveSession) -> Unit,
    private val onCreateSessionAction: (String) -> Unit
) {
    fun latestFor(deckId: String): ActiveSession? =
        sessions.filter { it.deckId == deckId }.maxByOrNull { it.lastAccessed }

    fun resume(session: ActiveSession) = onResumeAction(session)
    fun restart(session: ActiveSession) = onRestartAction(session)
    fun startNewSession(deckId: String) = onCreateSessionAction(deckId)
}

@Composable
fun rememberDeckSessionActions(
    viewModel: net.ericclark.studiare.FlashcardViewModel,
    navController: NavController
): DeckSessionActions {
    val allActiveSessions by viewModel.allActiveSessions.collectAsState()
    val allDecksWithCards by viewModel.allDecks.observeAsState(emptyList())
    var pendingResume by remember { mutableStateOf<ActiveSession?>(null) }
    var sessionToRestart by remember { mutableStateOf<ActiveSession?>(null) }
    var newSessionDeckId by remember { mutableStateOf<String?>(null) }

    val activeStudyState = viewModel.studyState
    LaunchedEffect(activeStudyState?.sessionId, pendingResume) {
        val pending = pendingResume
        if (pending != null && activeStudyState?.sessionId == pending.id) {
            navController.navigate(studyRouteFor(pending.mode))
            pendingResume = null
        }
    }

    sessionToRestart?.let { session ->
        ConfirmationDialog(
            title = getText(R.string.restart_session_title),
            text = getText(R.string.restart_session_desc),
            onConfirm = {
                val reset = viewModel.restartSession(session)
                sessionToRestart = null
                pendingResume = reset
                viewModel.resumeStudySession(reset)
            },
            onDismiss = { sessionToRestart = null }
        )
    }

    LaunchedEffect(newSessionDeckId) {
        val deckId = newSessionDeckId ?: return@LaunchedEffect
        // Create Study Session is its own screen now (it creates something new and navigates to
        // it, unlike every other dialog here), so this just navigates there instead of showing
        // dialog state. Falls back to the same last-used category the Study Hub's own
        // "+ Create Session" FAB defaults to (StudyScreens.kt).
        navController.navigate(createStudySessionRoute(deckId, viewModel.lastStudyCategory.value))
        newSessionDeckId = null
    }

    return remember(allActiveSessions, allDecksWithCards) {
        DeckSessionActions(
            sessions = allActiveSessions,
            onResumeAction = { session -> pendingResume = session; viewModel.resumeStudySession(session) },
            onRestartAction = { session -> sessionToRestart = session },
            onCreateSessionAction = { deckId -> newSessionDeckId = deckId }
        )
    }
}
