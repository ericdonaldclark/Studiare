package net.ericclark.studiare.components

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import net.ericclark.studiare.BackupDestination
import net.ericclark.studiare.BackupRetention
import net.ericclark.studiare.PreferenceManager
import net.ericclark.studiare.data.AppDatabase
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupRunSummary(val success: Boolean)

private const val BACKUP_FILE_PREFIX = "Studiare_Backup_"
private const val BACKUP_FILE_SUFFIX = ".json"

/**
 * The scheduled-backup choke point: resolves the current decks, writes a full JSON export to the
 * one currently-selected destination (local folder or Google Drive — a single choice, not both at
 * once), sweeps that destination's retention, and stamps `lastExportedAt` on every exported deck.
 * Called by [ScheduledBackupWorker] for both the periodic schedule and manual "Back up now" runs —
 * never touches [net.ericclark.studiare.FlashcardViewModel], since a background worker has none.
 * Runs regardless of the master "Enable Automatic Backups" toggle — that toggle only controls
 * whether the periodic schedule is enqueued (see `FlashcardViewModel`'s `init` block); "Back up
 * now" is an explicit user action and always goes through.
 */
class ScheduledBackupManager(private val context: Context) {
    private val preferenceManager = PreferenceManager(context)

    suspend fun runBackup(): BackupRunSummary = withContext(Dispatchers.IO) {
        val prefs = preferenceManager
        val database = AppDatabase.getDatabase(context)
        val decks = database.deckDao().getAllActiveDecks().first()
        val cards = database.cardDao().getAllActiveCards().first()
        val decksWithCards = DeckLinkageResolver.resolveEffectiveDecks(decks, cards)
        val json = buildDecksJson(decksWithCards)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault())
        val fileName = "$BACKUP_FILE_PREFIX${dateFormat.format(Date())}$BACKUP_FILE_SUFFIX"

        val destination = prefs.backupDestinationFlow.first()
        val success = runCatching {
            if (destination == BackupDestination.DRIVE) writeDriveBackup(fileName, json, prefs) else writeLocalBackup(fileName, json, prefs)
        }.getOrDefault(false)
        prefs.recordBackupRun(success)

        if (success) {
            val now = System.currentTimeMillis()
            decksWithCards.forEach { deckWithCards ->
                database.deckDao().insertOrUpdate(deckWithCards.deck.copy(lastExportedAt = now, isPendingSync = true))
            }
        }

        BackupRunSummary(success)
    }

    private suspend fun writeLocalBackup(fileName: String, json: String, prefs: PreferenceManager): Boolean {
        val folderUriString = prefs.localBackupFolderUriFlow.first() ?: return false
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUriString)) ?: return false
        if (!folder.exists() || !folder.isDirectory) return false
        val newFile = folder.createFile("application/json", fileName) ?: return false
        val written = context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(json.toByteArray()) }
        if (written == null) return false

        val retention = prefs.backupRetentionCountFlow.first()
        if (retention != BackupRetention.UNLIMITED) {
            folder.listFiles()
                .filter { it.name?.startsWith(BACKUP_FILE_PREFIX) == true && it.name?.endsWith(BACKUP_FILE_SUFFIX) == true }
                .sortedByDescending { it.name } // filenames embed yyyy-MM-dd_HHmm, so lexicographic order is chronological
                .drop(retention)
                .forEach { it.delete() }
        }
        return true
    }

    private suspend fun writeDriveBackup(fileName: String, json: String, prefs: PreferenceManager): Boolean {
        val authResult = DriveBackupClient.authorize(context) ?: return false
        if (authResult.hasResolution()) {
            // Access was revoked outside the app (e.g. the user's Google Account permissions page);
            // a headless worker has no Activity to show the consent screen again.
            prefs.setDriveBackupNeedsReauth(true)
            return false
        }
        prefs.setDriveBackupNeedsReauth(false)
        val accessToken = authResult.accessToken ?: return false

        val existingFolderId = prefs.driveBackupFolderIdFlow.first()
        val folderId = DriveBackupClient.ensureBackupFolder(accessToken, existingFolderId) ?: return false
        if (folderId != existingFolderId) prefs.setDriveBackupFolderId(folderId)

        if (!DriveBackupClient.uploadBackup(accessToken, folderId, fileName, json)) return false

        val retention = prefs.backupRetentionCountFlow.first()
        if (retention != BackupRetention.UNLIMITED) {
            // Already createdTime-descending (newest first) — drop keeps the first N, deletes the rest.
            DriveBackupClient.listBackupFiles(accessToken, folderId)
                .drop(retention)
                .forEach { DriveBackupClient.deleteFile(accessToken, it.id) }
        }
        return true
    }
}
