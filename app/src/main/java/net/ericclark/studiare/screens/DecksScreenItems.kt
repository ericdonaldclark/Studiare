package net.ericclark.studiare.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
import androidx.compose.material.icons.automirrored.filled.MenuBook

@Composable
fun DeckListItem(
    deck: DeckSummary,
    dimensions: StudiareDimensions,
    setsCount: Int,
    onStudy: (String?) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onManageSets: () -> Unit,
    onToggleStar: (() -> Unit)? = null,
    showManageSetsButton: Boolean = true,
    tapOpensStudy: Boolean = true,
    index: Int = -1
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    val cardBorderColor =
        if (isCardFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "cardSquish"
    )

    ElevatedCard(
        elevation = CardDefaults.elevatedCardElevation(
            defaultElevation = dimensions.cardElevation,
            pressedElevation = 8.dp
        ),
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        modifier = Modifier
            .fillMaxWidth()
            .scale(cardScale)
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
            .clickable(
                interactionSource = cardInteractionSource,
                indication = LocalIndication.current
            ) {
                // On deck tiles, tapping opens study sessions (a deck with no cards goes to its sets).
                // Set tiles pass tapOpensStudy = false: tapping opens the set's sets page and only the
                // Study button opens sessions.
                if (tapOpensStudy && deck.totalCards > 0) onStudy(null) else onManageSets()
            }
    ) {
        // At small (Compact) padding, the fixed corner radius crowds the name/count into the
        // top-left corner; keep at least as much clearance as the corner itself needs.
        Column(modifier = Modifier.padding(dimensions.paddingMedium.coerceAtLeast(dimensions.cornerRadiusMedium))) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = deck.deck.name,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    SuggestionChip(
                        onClick = { },
                        label = { Text(stringResource(R.string.cards_count, deck.totalCards)) },
                        colors = SuggestionChipDefaults.suggestionChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        border = null
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (showManageSetsButton) {
                        // Changed to display number of sets
                        val manageInteractionSource = remember { MutableInteractionSource() }
                        val isManagePressed by manageInteractionSource.collectIsPressedAsState()
                        val manageScale by animateFloatAsState(
                            targetValue = if (isManagePressed) 0.95f else 1f,
                            animationSpec = spring(
                                dampingRatio = Spring.DampingRatioMediumBouncy,
                                stiffness = Spring.StiffnessMedium
                            ),
                            label = "manageSquish"
                        )
                        TextButton(
                            onClick = onManageSets,
                            interactionSource = manageInteractionSource,
                            modifier = Modifier.scale(manageScale),
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
                val isEditPressed by editInteractionSource.collectIsPressedAsState()
                val editScale by animateFloatAsState(
                    targetValue = if (isEditPressed) 0.85f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "editSquish"
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                TooltipIconButton(description = getText(R.string.edit), 
                    onClick = onEdit,
                    interactionSource = editInteractionSource,
                    modifier = Modifier.scale(editScale)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        getText(R.string.edit),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (onToggleStar != null) {
                    val starInteractionSource = remember { MutableInteractionSource() }
                    val isStarPressed by starInteractionSource.collectIsPressedAsState()
                    val starScale by animateFloatAsState(
                        targetValue = if (isStarPressed) 0.85f else 1f,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioMediumBouncy,
                            stiffness = Spring.StiffnessMedium
                        ),
                        label = "starSquish"
                    )
                    val starTint by animateColorAsState(
                        targetValue = if (deck.deck.isStarred) Color(0xFFFFD700) else MaterialTheme.colorScheme.onSurfaceVariant,
                        animationSpec = spring(stiffness = Spring.StiffnessMedium),
                        label = "starColor"
                    )
                    val starLabel = if (deck.deck.parentDeckId == null) {
                        if (deck.deck.isStarred) getText(R.string.unstar_deck) else getText(R.string.star_deck)
                    } else {
                        if (deck.deck.isStarred) getText(R.string.unstar_set) else getText(R.string.star_set)
                    }
                    TooltipIconButton(
                        description = starLabel,
                        onClick = onToggleStar,
                        interactionSource = starInteractionSource,
                        modifier = Modifier.scale(starScale)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Star,
                            contentDescription = starLabel,
                            tint = starTint
                        )
                    }
                }

                val deleteInteractionSource = remember { MutableInteractionSource() }
                val isDeletePressed by deleteInteractionSource.collectIsPressedAsState()
                val deleteScale by animateFloatAsState(
                    targetValue = if (isDeletePressed) 0.85f else 1f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "deleteSquish"
                )
                TooltipIconButton(description = getText(R.string.delete), 
                    onClick = onDelete,
                    interactionSource = deleteInteractionSource,
                    modifier = Modifier.scale(deleteScale)
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
                    onStudyOption = { onStudy(it) },
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

@Composable
fun SetListItem(
    deck: DeckSummary,
    dimensions: StudiareDimensions,
    onStudy: (String?) -> Unit,
    onOpenSets: () -> Unit = {}
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = Modifier
            .width(190.dp)
            .height(160.dp)
            .border(
                if (isFocused) 6.dp else 0.dp,
                borderColor,
                RoundedCornerShape(dimensions.cornerRadiusMedium)
            )
            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
            .clickable(
                interactionSource = interactionSource,
                indication = LocalIndication.current
            ) { onOpenSets() },
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                // Same corner-clearance floor as DeckListItem, above.
                .padding(dimensions.paddingMedium.coerceAtLeast(dimensions.cornerRadiusMedium))
        ) {
            Column {
                Text(
                    deck.deck.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.cards_count_lowercase, deck.totalCards),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // REPLACED: Normal Button with the new Split Button
                StudySplitButton(
                    onStudyMain = { onStudy(null) },
                    onStudyOption = { onStudy(it) },
                    enabled = deck.totalCards > 0
                )
            }

            // This Box fills the rest of the height, floating the button perfectly in the middle
            /*
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
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
                    enabled = deck.cards.isNotEmpty(),
                    modifier = Modifier.scale(studyScale),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(getText(R.string.study))
                }
            }
            */
        }
    }
}

@Composable
fun LoadingOverlay(message: String? = null) {
    val displayMessage = message ?: getText(R.string.processing)
    AnimatedDialog(onDismissRequest = { }) {
        Card(
            shape = RoundedCornerShape(28.dp),
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
    onStudyOption: (String) -> Unit,
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
            modifier = Modifier.background(
                MaterialTheme.colorScheme.surfaceContainerHigh,
                RoundedCornerShape(16.dp)
            )
        ) {
            DropdownMenuItem(
                text = { Text(getText(R.string.category_learn)) },
                leadingIcon = { Icon(Icons.Default.School, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("learn") }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.category_practice)) },
                leadingIcon = {
                    Icon(
                        Icons.AutoMirrored.Filled.MenuBook,
                        contentDescription = null
                    )
                },
                onClick = { expanded = false; onStudyOption("study") }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.category_quiz)) },
                leadingIcon = { Icon(Icons.Default.Quiz, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("quiz") }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.category_game)) },
                leadingIcon = { Icon(Icons.Default.SportsEsports, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("game") }
            )
            DropdownMenuItem(
                text = { Text(getText(R.string.spaced_repetition_label)) },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                onClick = { expanded = false; onStudyOption("fsrs") }
            )
        }
    }
}
