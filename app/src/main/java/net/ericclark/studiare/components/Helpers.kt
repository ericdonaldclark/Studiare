package net.ericclark.studiare.components

import android.content.Context
import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

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
