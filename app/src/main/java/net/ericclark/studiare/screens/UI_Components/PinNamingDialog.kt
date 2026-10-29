package net.ericclark.studiare.screens.UI_Components
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.SessionMode
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared "pin + name it" dialog — used wherever pinning a study session asks for a name: a saved
 * session's overflow menu, the completion screen's pin button, and the session-creation screen's
 * pin button. The text field's placeholder is a real hint (not prefilled editable text) — the
 * typed value is used if non-blank, otherwise [defaultName] is used on confirm.
 */
@Composable
fun PinNamingDialog(
    defaultName: String,
    onDismiss: () -> Unit,
    onConfirm: (name: String) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    var typed by remember { mutableStateOf("") }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                Text(getText(R.string.pin_and_name_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingMedium))
                OutlinedTextField(
                    value = typed,
                    onValueChange = { typed = it },
                    placeholder = { Text(defaultName) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(dimensions.spacingLarge))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.cancel))
                    }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Button(
                        onClick = { onConfirm(typed.trim().ifBlank { defaultName }) },
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.pin))
                    }
                }
            }
        }
    }
}

/** A sensible default name for a newly-pinned session, used as [PinNamingDialog]'s placeholder. */
fun defaultSessionPinName(context: android.content.Context, mode: SessionMode, deckName: String): String {
    val dateFormat = SimpleDateFormat("MM/dd/yy", Locale.getDefault())
    return "${mode.asString(context)} - $deckName - ${dateFormat.format(Date())}"
}
