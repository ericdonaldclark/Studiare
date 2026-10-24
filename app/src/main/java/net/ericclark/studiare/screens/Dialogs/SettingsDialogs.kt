package net.ericclark.studiare.screens.Dialogs

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.screens.UI_Components.ColorPickerRow

private fun Color.toHexColorString(): String = "#%06X".format(0xFFFFFF and toArgb())

@Composable
fun CustomThemeDialog(
    initialColors: CustomThemeColors,
    isCustomThemeActive: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val context = LocalContext.current
    var primary by remember { mutableStateOf(initialColors.primary) }
    var secondary by remember { mutableStateOf(initialColors.secondary) }
    var tertiary by remember { mutableStateOf(initialColors.tertiary) }
    var background by remember { mutableStateOf(initialColors.background) }

    // Each row's own "frozen" preset indicator — only ever reassigned by a top preset button
    // (Light/Dark/B&W/Current), never by picking a palette swatch or typing a hex code directly,
    // so the user always has a one-tap way back to "what the last preset gave this slot."
    var presetPrimary by remember { mutableStateOf(initialColors.primary) }
    var presetSecondary by remember { mutableStateOf(initialColors.secondary) }
    var presetTertiary by remember { mutableStateOf(initialColors.tertiary) }
    var presetBackground by remember { mutableStateOf(initialColors.background) }

    // Which row's "#" button was pressed, if any: its current value and how to write a new one back.
    var hexEntryTarget by remember { mutableStateOf<Pair<String, (String) -> Unit>?>(null) }

    // The last hex each row's own "#" button actually confirmed — lets that circle show itself as
    // filled/highlighted while it's still the active color, same as presetPrimary does for the
    // frozen preset circle, but keyed off typing a hex instead of pressing a top preset.
    var hexEnteredPrimary by remember { mutableStateOf<String?>(null) }
    var hexEnteredSecondary by remember { mutableStateOf<String?>(null) }
    var hexEnteredTertiary by remember { mutableStateOf<String?>(null) }
    var hexEnteredBackground by remember { mutableStateOf<String?>(null) }

    // Lets the user start from one of the other built-in themes and then tweak 1+ colors from
    // there, instead of only ever building a custom theme from scratch.
    fun applyPreset(scheme: ColorScheme) {
        primary = scheme.primary.toHexColorString()
        secondary = scheme.secondary.toHexColorString()
        tertiary = scheme.tertiary.toHexColorString()
        background = scheme.background.toHexColorString()
        presetPrimary = primary
        presetSecondary = secondary
        presetTertiary = tertiary
        presetBackground = background
    }

    // "Current" isn't a built-in ColorScheme — it's whatever the custom theme's own colors already
    // are — so it resets straight from initialColors instead of going through applyPreset.
    fun applyCurrentPreset() {
        primary = initialColors.primary
        secondary = initialColors.secondary
        tertiary = initialColors.tertiary
        background = initialColors.background
        presetPrimary = primary
        presetSecondary = secondary
        presetTertiary = tertiary
        presetBackground = background
    }

    if (hexEntryTarget != null) {
        val (currentHex, onConfirm) = hexEntryTarget!!
        HexColorEntryDialog(
            initialHex = currentHex,
            onDismiss = { hexEntryTarget = null },
            onConfirm = { hex ->
                onConfirm(hex)
                hexEntryTarget = null
            }
        )
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        // M3 Expressive Card
        ElevatedCard(
            shape = RoundedCornerShape(dimensions.cornerRadiusDialog),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(getText(R.string.custom_theme), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingSmall))

                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
                ) {
                    AssistChip(
                        onClick = {
                            applyPreset(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicLightColorScheme(context)
                                else LightColorScheme
                            )
                        },
                        label = { Text(getText(R.string.light_mode)) },
                        shape = RoundedCornerShape(50)
                    )
                    AssistChip(
                        onClick = {
                            applyPreset(
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context)
                                else DarkColorScheme
                            )
                        },
                        label = { Text(getText(R.string.dark_mode)) },
                        shape = RoundedCornerShape(50)
                    )
                    AssistChip(
                        onClick = { applyPreset(BlackAndWhiteColorScheme) },
                        label = { Text(getText(R.string.bw_mode)) },
                        shape = RoundedCornerShape(50)
                    )
                    if (isCustomThemeActive) {
                        AssistChip(
                            onClick = { applyCurrentPreset() },
                            label = { Text(getText(R.string.current_theme_preset)) },
                            shape = RoundedCornerShape(50)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))

                ColorPickerRow(
                    label = getText(R.string.primary),
                    color = primary,
                    presetColor = presetPrimary,
                    lastEnteredHex = hexEnteredPrimary,
                    onHexEntryRequested = { hexEntryTarget = primary to { hex -> primary = hex; hexEnteredPrimary = hex } },
                    description = getText(R.string.primary_color_desc)
                ) { primary = it }
                ColorPickerRow(
                    label = getText(R.string.secondary),
                    color = secondary,
                    presetColor = presetSecondary,
                    lastEnteredHex = hexEnteredSecondary,
                    onHexEntryRequested = { hexEntryTarget = secondary to { hex -> secondary = hex; hexEnteredSecondary = hex } },
                    description = getText(R.string.secondary_color_desc)
                ) { secondary = it }
                ColorPickerRow(
                    label = getText(R.string.tertiary),
                    color = tertiary,
                    presetColor = presetTertiary,
                    lastEnteredHex = hexEnteredTertiary,
                    onHexEntryRequested = { hexEntryTarget = tertiary to { hex -> tertiary = hex; hexEnteredTertiary = hex } },
                    description = getText(R.string.tertiary_color_desc)
                ) { tertiary = it }
                ColorPickerRow(
                    label = getText(R.string.background),
                    color = background,
                    presetColor = presetBackground,
                    lastEnteredHex = hexEnteredBackground,
                    onHexEntryRequested = { hexEntryTarget = background to { hex -> background = hex; hexEnteredBackground = hex } },
                    description = getText(R.string.background_color_desc)
                ) { background = it }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))

                    val applyInteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = { onSave(primary, secondary, tertiary, background) },
                        interactionSource = applyInteractionSource,
                        modifier = Modifier.pressSquish(applyInteractionSource, 0.95f),
                        shape = RoundedCornerShape(net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.apply))
                    }
                }
            }
        }
    }
}

/** A single row's own hex-entry sub-dialog, opened by [ColorPickerRow]'s "#" button. */
@Composable
private fun HexColorEntryDialog(
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var hex by remember { mutableStateOf(initialHex) }
    val isValid = remember(hex) {
        runCatching { android.graphics.Color.parseColor(hex) }.isSuccess
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column {
                DialogAccentCap()
                Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 400.dp)) {
                    Text(getText(R.string.enter_hex_color), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(dimensions.spacingMedium))

                    OutlinedTextField(
                        value = hex,
                        onValueChange = { hex = it },
                        label = { Text(getText(R.string.hex_color)) },
                        singleLine = true,
                        isError = !isValid,
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .background(
                                        if (isValid) net.ericclark.studiare.components.parseHexColor(hex) else Color.Transparent,
                                        androidx.compose.foundation.shape.CircleShape
                                    )
                                    .border(1.dp, MaterialTheme.colorScheme.outline, androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    )

                    Spacer(Modifier.height(dimensions.spacingLarge))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                        Spacer(Modifier.width(8.dp))
                        val confirmInteractionSource = remember { MutableInteractionSource() }
                        Button(
                            onClick = { onConfirm(hex) },
                            enabled = isValid,
                            interactionSource = confirmInteractionSource,
                            modifier = Modifier.pressSquish(confirmInteractionSource, 0.95f),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(R.string.apply))
                        }
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
            Column {
                DialogAccentCap()
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
