package net.ericclark.studiare.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.LocalDrawerState

/**
 * For Jetpack Compose: Shorthand for stringResource
 */
@Composable
fun getText(@StringRes resId: Int): String {
    return stringResource(id = resId)
}

/**
 * For Standard Kotlin: Shorthand for context.getString
 */
fun getText(context: Context, @StringRes resId: Int): String {
    return context.getString(resId)
}
