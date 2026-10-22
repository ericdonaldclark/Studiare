package net.ericclark.studiare.components

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

/**
 * Used for both the periodic schedule and the manual "Back up now" one-time request — same code
 * path either way, so testing "Back up now" live exercises exactly what the schedule will run.
 */
class ScheduledBackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val summary = ScheduledBackupManager(applicationContext).runBackup()
        return if (summary.success) Result.success() else Result.failure()
    }

    companion object {
        const val PERIODIC_WORK_NAME = "studiare_scheduled_backup"
        const val MANUAL_WORK_NAME = "studiare_manual_backup"
    }
}
