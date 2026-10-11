package net.ericclark.studiare

import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextOverflow
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.data.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.Color
import net.ericclark.studiare.components.getText
import androidx.compose.ui.draw.scale
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource

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
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "buttonSquish"
    )

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
            .scale(scale),
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
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.85f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessMedium
        ),
        label = "knownSquish"
    )

    OutlinedIconToggleButton(
        checked = isKnown,
        onCheckedChange = { onClick() },
        interactionSource = interactionSource,
        modifier = Modifier
            .size(56.dp) // Increased to Expressive 56dp touch target
            .scale(scale),
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
        Icon(
            imageVector = if (isKnown) Icons.Filled.Check else Icons.Default.Check,
            contentDescription = if (isKnown) getText(R.string.mark_as_not_known) else getText(R.string.mark_as_known)
        )
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
