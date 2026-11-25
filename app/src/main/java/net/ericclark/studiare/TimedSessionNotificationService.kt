package net.ericclark.studiare

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.ericclark.studiare.components.AppLogger
import net.ericclark.studiare.data.formatCountdown
import net.ericclark.studiare.data.timedSessionTickDelaySeconds

/**
 * Mirrors the app bar's Timed Session countdown pill (see [AdaptiveSessionTitle]/`TimedSessionPill`
 * in CommonFormControls.kt) in a notification while the app is backgrounded - started/stopped by
 * `FlashcardViewModel`'s existing ProcessLifecycleOwner hook, using
 * `StudySessionManager.timedSessionNotificationSnapshot()` to decide whether there's anything to
 * show (null when Timed Session is off, already complete, or "pause outside session" is on - that
 * setting already stops the countdown itself, so there's nothing live to display).
 *
 * This service owns no session state and makes no decisions about when the session actually ends -
 * `StudySessionManager.scheduleTimeoutCheck()`'s own coroutine (which keeps running in the
 * ViewModel's scope regardless of whether the Activity is visible) is still what force-completes
 * the session. This is purely a read-only, self-contained display: it's handed one snapshot of the
 * countdown's own wall-clock math (duration/accumulated/resumedAt) and re-derives the remaining
 * time itself the same way [net.ericclark.studiare.data.timedSessionRemainingSeconds] does, rather
 * than staying bound to the ViewModel.
 *
 * On Android 16+ this uses the real "Live Update" / progress-centric notification APIs
 * ([Notification.ProgressStyle], `setRequestPromotedOngoing`, `setShortCriticalText` for the
 * status-bar chip) added in SDK 36's later minor revision. Those are new, fast-moving platform
 * surface - calling them is wrapped in a try/catch and falls back to a plain ongoing notification
 * on any failure, since a method existing at compile time (compileSdkMinor 36.1) doesn't guarantee
 * it exists on every device actually reporting SDK_INT 36.
 */
class TimedSessionNotificationService : Service() {

    companion object {
        // _v3: channel settings (importance, sound, vibration) can't be changed in code once a
        // channel id has been created on a device (Android ignores a later
        // createNotificationChannel() call with the same id) - bump this id again whenever one of
        // those settings changes, so it actually takes effect instead of silently no-op'ing on
        // devices that already created an earlier version of this channel.
        private const val CHANNEL_ID = "TimedSessionChannel_v4"
        private const val NOTIFICATION_ID = 2 // AudioStudyService uses 1 - distinct ids, both can show at once
        private const val TIMEUP_NOTIFICATION_ID = 3

        private const val EXTRA_DECK_NAME = "deckName"
        private const val EXTRA_MODE_LABEL_RES_ID = "modeLabelResId"
        private const val EXTRA_DURATION_SECONDS = "durationSeconds"
        private const val EXTRA_ACCUMULATED_SECONDS = "accumulatedSeconds"
        private const val EXTRA_RESUMED_AT = "resumedAt"
        private const val EXTRA_DISPLAY_FREQUENCY_UNDER_60 = "displayFrequencyUnder60"
        private const val EXTRA_DISPLAY_FREQUENCY_OVER_60 = "displayFrequencyOver60"

        fun start(context: Context, snapshot: net.ericclark.studiare.components.StudySessionManager.TimedSessionNotificationSnapshot) {
            val intent = Intent(context, TimedSessionNotificationService::class.java)
                .putExtra(EXTRA_DECK_NAME, snapshot.deckName)
                .putExtra(EXTRA_MODE_LABEL_RES_ID, snapshot.modeLabelResId)
                .putExtra(EXTRA_DURATION_SECONDS, snapshot.durationSeconds)
                .putExtra(EXTRA_ACCUMULATED_SECONDS, snapshot.accumulatedSeconds)
                .putExtra(EXTRA_RESUMED_AT, snapshot.resumedAt)
                .putExtra(EXTRA_DISPLAY_FREQUENCY_UNDER_60, snapshot.displayFrequencyUnder60)
                .putExtra(EXTRA_DISPLAY_FREQUENCY_OVER_60, snapshot.displayFrequencyOver60)
            androidx.core.content.ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, TimedSessionNotificationService::class.java))
        }
    }

    private data class Snapshot(
        val deckName: String,
        val modeLabelResId: Int,
        val durationSeconds: Int,
        val accumulatedSeconds: Long,
        val resumedAt: Long,
        val displayFrequencyUnder60: Int,
        val displayFrequencyOver60: Int
    )

    private val serviceScope = CoroutineScope(Dispatchers.Main + Job())
    private var tickJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val snapshot = intent?.let {
            Snapshot(
                deckName = it.getStringExtra(EXTRA_DECK_NAME) ?: return@let null,
                modeLabelResId = it.getIntExtra(EXTRA_MODE_LABEL_RES_ID, 0),
                durationSeconds = it.getIntExtra(EXTRA_DURATION_SECONDS, 0),
                accumulatedSeconds = it.getLongExtra(EXTRA_ACCUMULATED_SECONDS, 0L),
                resumedAt = it.getLongExtra(EXTRA_RESUMED_AT, System.currentTimeMillis()),
                displayFrequencyUnder60 = it.getIntExtra(EXTRA_DISPLAY_FREQUENCY_UNDER_60, 5),
                displayFrequencyOver60 = it.getIntExtra(EXTRA_DISPLAY_FREQUENCY_OVER_60, 30)
            )
        }
        if (snapshot == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val initialRemaining = remainingSecondsFor(snapshot)
        startForeground(NOTIFICATION_ID, buildNotification(snapshot, initialRemaining))

        tickJob?.cancel()
        tickJob = serviceScope.launch {
            var remaining = initialRemaining
            while (remaining > 0) {
                delay(timedSessionTickDelaySeconds(remaining, snapshot.displayFrequencyUnder60, snapshot.displayFrequencyOver60) * 1000)
                remaining = remainingSecondsFor(snapshot)
                if (remaining > 0) updateNotification(snapshot, remaining)
            }
            postTimeUpNotification(snapshot)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(snapshot: Snapshot, remainingSeconds: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(snapshot, remainingSeconds))
        }
    }

    private fun remainingSecondsFor(snapshot: Snapshot): Long {
        val elapsed = snapshot.accumulatedSeconds + (System.currentTimeMillis() - snapshot.resumedAt) / 1000
        return (snapshot.durationSeconds - elapsed).coerceAtLeast(0)
    }

    private fun contentIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP }
        return PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE)
    }

    private fun buildNotification(snapshot: Snapshot, remainingSeconds: Long): Notification {
        val modeLabel = if (snapshot.modeLabelResId != 0) getString(snapshot.modeLabelResId) else ""
        val title = if (modeLabel.isNotEmpty()) "${snapshot.deckName} - $modeLabel" else snapshot.deckName
        val contentText = getString(R.string.timed_session_notification_text_format, formatCountdown(remainingSeconds))

        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(contentText)
            .setSmallIcon(R.drawable.studiare_solid)
            .setContentIntent(contentIntent())
            .setOngoing(true)
            .setOnlyAlertOnce(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Without this, Android 12+ can silently defer showing a foreground service's
            // notification for an app it considers "quiet" (no recent notification activity) -
            // which is exactly the symptom reported: the countdown notification/chip only appeared
            // when a leftover notification from a previous run was still present, never on a truly
            // cold start. FOREGROUND_SERVICE_IMMEDIATE opts out of that deferral entirely.
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }

        if (Build.VERSION.SDK_INT >= 36) {
            try {
                val notificationManager = getSystemService(NotificationManager::class.java)
                // canPostPromotedNotifications() reflects the OS's own eligibility/quota decision for
                // the status bar chip (device revision, the user's Live Updates setting, per-app rate
                // limits...) - logged so an intermittent missing chip can be attributed to the OS
                // declining promotion rather than a bug here, instead of guessing.
                val canPromote = notificationManager?.canPostPromotedNotifications() ?: false
                AppLogger.d("TimedSessionNotificationService", "canPostPromotedNotifications=$canPromote")

                val progressStyle = Notification.ProgressStyle()
                    .setProgressSegments(listOf(Notification.ProgressStyle.Segment(snapshot.durationSeconds.coerceAtLeast(1))))
                    .setProgress((snapshot.durationSeconds - remainingSeconds).toInt().coerceIn(0, snapshot.durationSeconds.coerceAtLeast(1)))
                builder
                    .setStyle(progressStyle)
                    .setShortCriticalText(formatCountdown(remainingSeconds))
                    .setRequestPromotedOngoing(true)
            } catch (e: Throwable) {
                // Live Update is new, fast-moving platform surface - a device reporting SDK_INT 36
                // isn't guaranteed to have this specific minor revision's APIs. Falls back silently
                // to the plain notification already built above.
                AppLogger.e("TimedSessionNotificationService", "Live Update notification style unavailable, falling back", e)
            }
        }

        return builder.build()
    }

    private fun postTimeUpNotification(snapshot: Snapshot) {
        val modeLabel = if (snapshot.modeLabelResId != 0) getString(snapshot.modeLabelResId) else ""
        val title = if (modeLabel.isNotEmpty()) "${snapshot.deckName} - $modeLabel" else snapshot.deckName
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(getString(R.string.timed_session_notification_timeup_text))
            .setSmallIcon(R.drawable.studiare_solid)
            .setContentIntent(contentIntent())
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        ) {
            getSystemService(NotificationManager::class.java)?.notify(TIMEUP_NOTIFICATION_ID, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // DEFAULT, not LOW - LOW lands the notification in the shade's collapsed "Silent"
            // section, which a live countdown the user is meant to actually notice shouldn't be in.
            // Sound/vibration are suppressed explicitly instead, so a countdown that updates many
            // times a session doesn't alert on every tick (setOnlyAlertOnce covers re-notify() calls
            // after the first, but not the very first post).
            val channel = NotificationChannel(CHANNEL_ID, getString(R.string.timed_session_notification_channel_name), NotificationManager.IMPORTANCE_DEFAULT)
            channel.description = getString(R.string.timed_session_notification_channel_desc)
            channel.setSound(null, null)
            channel.enableVibration(false)
            channel.setShowBadge(false)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        tickJob?.cancel()
        serviceScope.cancel()
    }
}
