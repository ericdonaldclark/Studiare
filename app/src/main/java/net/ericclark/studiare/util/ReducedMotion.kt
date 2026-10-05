package net.ericclark.studiare.util

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

private fun isSystemReducedMotion(context: Context): Boolean {
    // Public setting, no permission needed. This is the same value the OS's own
    // "Remove animations" accessibility toggle writes (0 = animations off).
    val scale = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return scale == 0f
}

/**
 * Live-updating read of the OS-level "remove animations" setting. Registers a [ContentObserver]
 * so toggling it in system settings while the app is open takes effect immediately, without a
 * restart.
 */
@Composable
fun rememberSystemReducedMotion(): State<Boolean> {
    val context = LocalContext.current
    val state = remember { mutableStateOf(isSystemReducedMotion(context)) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                state.value = isSystemReducedMotion(context)
            }
        }
        context.contentResolver.registerContentObserver(
            Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),
            false,
            observer
        )
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    return state
}
