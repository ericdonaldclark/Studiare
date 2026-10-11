package net.ericclark.studiare.screens.Dialogs

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.screens.UI_Components.ColorPickerRow

@Composable
fun CustomThemeDialog(
    initialColors: CustomThemeColors,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var primary by remember { mutableStateOf(initialColors.primary) }
    var secondary by remember { mutableStateOf(initialColors.secondary) }
    var tertiary by remember { mutableStateOf(initialColors.tertiary) }
    var background by remember { mutableStateOf(initialColors.background) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        // M3 Expressive Card
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(getText(R.string.custom_theme), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                ColorPickerRow(getText(R.string.primary), primary) { primary = it }
                ColorPickerRow(getText(R.string.secondary), secondary) { secondary = it }
                ColorPickerRow(getText(R.string.tertiary), tertiary) { tertiary = it }
                ColorPickerRow(getText(R.string.background), background) { background = it }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))

                    val applyInteractionSource = remember { MutableInteractionSource() }
                    val isApplyPressed by applyInteractionSource.collectIsPressedAsState()
                    val applyScale by animateFloatAsState(
                        targetValue = if (isApplyPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "applySquish"
                    )
                    Button(
                        onClick = { onSave(primary, secondary, tertiary, background) },
                        interactionSource = applyInteractionSource,
                        modifier = Modifier.scale(applyScale),
                        shape = RoundedCornerShape(net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.apply))
                    }
                }
            }
        }
    }
}

/**
 * Asks for the new key in a dialog on purpose: a dialog is its own window, so while it's up none
 * of the app's key handlers (Go Home, Esc, Alt hints, per-screen shortcuts...) receive the
 * keystroke — it can only ever be captured as the new binding, never also trigger its action.
 */
@Composable
internal fun ShortcutCaptureDialog(
    entry: ShortcutEntry,
    onKeyCaptured: (Key) -> Unit,
    onCancel: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Esc/Back has to be a bindable key here, so leaving is only via the Cancel button.
    AnimatedDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .widthIn(min = 280.dp, max = 400.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        // A modifier pressed on its way to another key shouldn't become the binding.
                        if (event.type == KeyEventType.KeyDown && event.key !in modifierOnlyKeys) {
                            onKeyCaptured(event.key)
                        }
                        true
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(getText(R.string.press_the_new_key), style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(
                    "for \"${entry.action}\"" +
                        (entry.remappable?.modifierPrefix?.let { " (with $it held)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(
                    "Keyboard shortcuts are paused until you press a key or cancel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(dimensions.spacingLarge))
                TextButton(onClick = onCancel, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
            }
        }
    }
}

internal val modifierOnlyKeys = setOf(
    Key.AltLeft, Key.AltRight, Key.CtrlLeft, Key.CtrlRight, Key.ShiftLeft, Key.ShiftRight,
    Key.MetaLeft, Key.MetaRight, Key.CapsLock, Key.NumLock, Key.ScrollLock, Key.Function
)

internal data class PendingShortcutRemap(
    val entry: ShortcutEntry,
    val key: androidx.compose.ui.input.key.Key,
    val conflicts: List<ShortcutEntry>
)
