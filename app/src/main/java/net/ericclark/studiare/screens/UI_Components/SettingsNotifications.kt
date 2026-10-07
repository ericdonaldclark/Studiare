package net.ericclark.studiare.screens.UI_Components

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import net.ericclark.studiare.ConfirmationDialog
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

/**
 * Notifications status in Settings → Manage Languages, with a way to ask for the permission again. The first
 * ask is preceded by the same explanation as the session-start prompt. If Android has stopped showing the
 * prompt (the user denied it for good), the button opens the app's notification settings instead.
 * Audio study works without the permission, so this row is optional. Nothing is shown before Android 13.
 */
@Composable
internal fun NotificationPermissionRow() {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
    val context = LocalContext.current
    val activity = context.findActivity()
    val dimensions = LocalStudiareDimensions.current
    val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
    var deniedForGood by remember { mutableStateOf(false) }
    var showExplainer by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        deniedForGood = !isGranted && activity != null &&
            !activity.shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)
    }

    if (showExplainer) {
        ConfirmationDialog(
            title = getText(R.string.notification_explainer_title),
            text = getText(R.string.notification_explainer_desc),
            confirmButtonText = getText(R.string.notification_explainer_allow),
            onConfirm = {
                showExplainer = false
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            },
            dismissButtonText = getText(R.string.notification_explainer_not_now),
            onDismiss = { showExplainer = false }
        )
    }

    Column(modifier = Modifier.padding(bottom = dimensions.paddingSmall)) {
        SettingsInfoRow(
            getText(R.string.settings_notifications),
            getText(if (granted) R.string.notifications_allowed else R.string.notifications_off),
            isAlternate = false
        )
        if (!granted) {
            Button(
                onClick = {
                    if (deniedForGood) {
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        )
                    } else {
                        showExplainer = true
                    }
                },
                modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) {
                Text(getText(if (deniedForGood) R.string.open_notification_settings else R.string.settings_allow_notifications))
            }
        }
    }
}
