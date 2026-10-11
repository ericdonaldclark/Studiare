package net.ericclark.studiare.components

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import net.ericclark.studiare.LocalWindowHeightSizeClass
import net.ericclark.studiare.LocalWindowWidthSizeClass

/**
 * Debug overlay: the window's size in dp and its width and height size classes. Recomposes on every resize
 * (LocalConfiguration updates with the window). Drag it to move it out of the way; double-tap to put it back
 * in its default corner.
 */
@Composable
fun WindowSizeOverlay(modifier: Modifier = Modifier) {
    var dragX by rememberSaveable { mutableFloatStateOf(0f) }
    var dragY by rememberSaveable { mutableFloatStateOf(0f) }
    val configuration = LocalConfiguration.current
    val widthClass = LocalWindowWidthSizeClass.current
    val heightClass = LocalWindowHeightSizeClass.current
    Surface(
        modifier = modifier
            .offset { IntOffset(dragX.roundToInt(), dragY.roundToInt()) }
            .pointerInput(Unit) {
                detectDragGestures { change, drag ->
                    change.consume()
                    dragX += drag.x
                    dragY += drag.y
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { dragX = 0f; dragY = 0f })
            }
            .padding(8.dp),
        shape = RoundedCornerShape(8.dp),
        color = Color.Black.copy(alpha = 0.7f)
    ) {
        Text(
            text = "${configuration.screenWidthDp} × ${configuration.screenHeightDp} dp\nWidth: $widthClass\nHeight: $heightClass",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
