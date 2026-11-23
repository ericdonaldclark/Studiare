package net.ericclark.studiare

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.data.*
import androidx.compose.ui.graphics.Color
import net.ericclark.studiare.components.getText
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import net.ericclark.studiare.ui.theme.pressSquish

/**
 * A stable, custom implementation of a TopAppBar to avoid using experimental Material3 APIs.
 * @param title The title composable to be displayed in the app bar.
 * @param modifier The modifier to be applied to the app bar.
 * @param navigationIcon The composable for the navigation icon.
 * @param actions The composable for the actions on the trailing side.
 */
/**
 * Icon-only button that shows [description] as a tooltip on long press, mouse hover, and keyboard
 * focus. Each button owns its tooltip state.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipIconButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: IconButtonColors = IconButtonDefaults.iconButtonColors(),
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val tooltipState = rememberTooltipState()
    val source = interactionSource ?: remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) tooltipState.show() else tooltipState.dismiss()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Below
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = tooltipState
    ) {
        IconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            colors = colors,
            interactionSource = source,
            content = content
        )
    }
}

/** [TooltipIconButton] in the filled-tonal style. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TooltipFilledTonalIconButton(
    description: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    shape: androidx.compose.ui.graphics.Shape = IconButtonDefaults.filledShape,
    colors: IconButtonColors = IconButtonDefaults.filledTonalIconButtonColors(),
    interactionSource: androidx.compose.foundation.interaction.MutableInteractionSource? = null,
    content: @Composable () -> Unit
) {
    val tooltipState = rememberTooltipState()
    val source = interactionSource ?: remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val focused by source.collectIsFocusedAsState()
    LaunchedEffect(focused) {
        if (focused) tooltipState.show() else tooltipState.dismiss()
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Below
        ),
        tooltip = { PlainTooltip { Text(description) } },
        state = tooltipState
    ) {
        FilledTonalIconButton(
            onClick = onClick,
            modifier = modifier,
            enabled = enabled,
            shape = shape,
            colors = colors,
            interactionSource = source,
            content = content
        )
    }
}

/**
 * A single-line, end-ellipsized [Text] that only gets a tooltip (showing the full, unclipped
 * text) when it's actually overflowing — never wraps one around text that fits. Overflow is
 * detected from the real layout via [onTextLayout] rather than a character-count guess, so it
 * stays correct across fonts/locales/dynamic type sizes.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextWithOverflowTooltip(
    text: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: androidx.compose.ui.text.font.FontWeight? = null,
    minLines: Int = 1,
    maxLines: Int = 1,
    onTextLayout: (androidx.compose.ui.text.TextLayoutResult) -> Unit = {}
) {
    var isOverflowing by remember(text) { mutableStateOf(false) }
    val tooltipState = rememberTooltipState(isPersistent = true)
    val coroutineScope = rememberCoroutineScope()
    // TooltipBox is always present now, never conditionally swapped for a plain Box: switching
    // the composable structure based on isOverflowing — which is itself derived from this Text's
    // measured layout — let TooltipBox's own wrapping change what width the Text measured against
    // from one recomposition to the next, flipping isOverflowing back and forth forever. With the
    // structure fixed, only the long-press detector below reacts to isOverflowing (no tooltip
    // trigger when the text already fits), so the Text is always measured the same way.
    //
    // enableUserInput is deliberately false — TooltipBox's own built-in long-press detector sits
    // on this same anchor Box, which is nested *inside* the deck/set tile's own
    // combinedClickable(onLongClick = ...) (multi-select entry). Both gestures were racing the
    // same long-press, so long-pressing an overflowing name could enter multi-select instead of
    // (or as well as) showing the tooltip. Driving tooltipState.show() from our own
    // Modifier.pointerInput on the Text node — the deepest point in the tree — consumes the
    // long-press there, before it can bubble up to the tile's gesture, the same way the
    // Edit/Pin/Delete icon buttons already don't trigger the tile's own click/long-click.
    TooltipBox(
        positionProvider = TooltipDefaults.rememberTooltipPositionProvider(
            positioning = TooltipAnchorPosition.Below
        ),
        tooltip = { PlainTooltip { Text(text) } },
        state = tooltipState,
        enableUserInput = false,
        modifier = modifier
    ) {
        Text(
            text = text,
            style = style,
            fontWeight = fontWeight,
            color = color,
            minLines = minLines,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .fillMaxWidth()
                .pointerInput(isOverflowing) {
                    if (isOverflowing) {
                        detectTapGestures(onLongPress = { coroutineScope.launch { tooltipState.show() } })
                    }
                },
            onTextLayout = { result -> isOverflowing = result.hasVisualOverflow; onTextLayout(result) }
        )
    }
}

// Loading Overlay Composable


/**
 * A reusable button for navigating between cards in a study session.
 * @param onClick The action to perform when the button is clicked.
 * @param icon The icon to display on the button.
 * @param modifier The modifier to apply to the button.
 */
@Composable
fun StudyCardNavButton(
    onClick: () -> Unit,
    icon: @Composable () -> Unit,
    containerColor: Color? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    // Determine colors: Use provided ones or fallback to M3 defaults
    val colors = if (containerColor != null) {
        IconButtonDefaults.filledTonalIconButtonColors(
            containerColor = containerColor
        )
    } else {
        IconButtonDefaults.filledTonalIconButtonColors()
    }

    // M3 Expressive prefers FilledTonal for secondary actions

    FilledTonalIconButton(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = modifier
            .size(42.dp) // Increased to Expressive 56dp standard touch target
            .pressSquish(interactionSource, 0.85f),
        shape = CircleShape, // Enforce expressive circular shape
        colors = colors
    ) {
        icon()
    }
}

/**
 * A circular button with a checkmark to mark a card as "known".
 * @param isKnown The current known status of the card.
 * @param onClick The action to perform when the button is clicked.
 */
@Composable
fun MarkKnownButton(
    isKnown: Boolean,
    onClick: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    OutlinedIconToggleButton(
        checked = isKnown,
        onCheckedChange = { onClick() },
        interactionSource = interactionSource,
        modifier = Modifier
            .size(56.dp) // Increased to Expressive 56dp touch target
            .pressSquish(interactionSource, 0.85f),
        shape = CircleShape, // Explicitly enforce expressive circular shape
        colors = IconButtonDefaults.outlinedIconToggleButtonColors(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
            checkedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            checkedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        ),
        border = IconButtonDefaults.outlinedIconToggleButtonBorder(
            enabled = true,
            checked = isKnown
        )
    ) {
        if (isKnown) {
            // The "check_circle" Material Symbol isn't in this app's subsetted
            // material_symbols_outlined.ttf (only PRELIMINARY's codepoint was added to it) — the
            // classic bundled vector icon stands in for it rather than requiring a font rebuild.
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = getText(R.string.mark_as_not_known)
            )
        } else {
            net.ericclark.studiare.components.MaterialSymbol(
                codepoint = net.ericclark.studiare.components.MaterialSymbols.PRELIMINARY,
                contentDescription = getText(R.string.mark_as_known),
                fill = 0f,
                // Sized up slightly from CheckCircle's 24dp — same measured height, but its thinner
                // outline strokes read as visually smaller next to CheckCircle's solid fill.
                size = 30.dp
            )
        }
    }
}



@Composable
fun ToggleButton(text: String, isSelected: Boolean, onClick: () -> Unit, enabled: Boolean = true, modifier: Modifier = Modifier) {
    val dimensions = LocalStudiareDimensions.current

    val targetContainerColor = when {
        !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.primaryContainer // M3 Expressive shift: PrimaryContainer for selected toggles
        else -> Color.Transparent
    }

    val targetContentColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer // M3 Expressive shift
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "toggleBg"
    )

    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "toggleText"
    )

    val border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder

    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        border = border,
        enabled = enabled,
        modifier = modifier,
        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
        contentPadding = PaddingValues(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
    ) {
        Text(text, maxLines = 1)
    }
}

@Composable
fun ToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current

    val targetContainerColor = when {
        !isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    val targetContentColor = when {
        !isSelected -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.primary
    }

    val containerColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContainerColor,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "toggleBg"
    )

    val contentColor by androidx.compose.animation.animateColorAsState(
        targetValue = targetContentColor,
        animationSpec = androidx.compose.animation.core.spring(stiffness = androidx.compose.animation.core.Spring.StiffnessMedium),
        label = "toggleText"
    )

    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(containerColor = containerColor, contentColor = contentColor),
        border = if (isSelected) null else ButtonDefaults.outlinedButtonBorder,
        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
        contentPadding = PaddingValues(horizontal = dimensions.paddingSmall, vertical = dimensions.paddingSmall)
    ) {
        Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.labelLarge)
    }
}
