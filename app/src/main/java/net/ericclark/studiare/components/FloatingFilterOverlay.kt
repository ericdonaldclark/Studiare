package net.ericclark.studiare.components

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import net.ericclark.studiare.R
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/**
 * A filter search field floating above a bottom toolbar, opened from a filter icon there. A
 * full-screen transparent scrim behind it dismisses on an outside tap; the field itself sits on
 * top and handles its own taps normally. Shared by every screen with a toolbar filter button.
 */
@Composable
fun FloatingFilterOverlay(
    visible: Boolean,
    filterText: String,
    onFilterTextChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val dimensions = LocalStudiareDimensions.current
    val focusRequester = remember { FocusRequester() }
    Popup(onDismissRequest = onDismiss, properties = PopupProperties(focusable = true)) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) { detectTapGestures { onDismiss() } }
        ) {
            TextField(
                value = filterText,
                onValueChange = onFilterTextChange,
                placeholder = { Text(getText(R.string.cards_filter_)) },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = dimensions.paddingMedium)
                    .padding(bottom = 96.dp)
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                singleLine = true
            )
        }
    }
    LaunchedEffect(Unit) { focusRequester.requestFocus() }
}
