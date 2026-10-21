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
import net.ericclark.studiare.data.DeckWithCards
import org.json.JSONArray
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class BackupRunSummary(val success: Boolean)

/** What actually gets written — built once per run, then handed to whichever destination is enabled. */
private data class BackupContent(val bytes: ByteArray, val mimeType: String, val fileExtension: String)

private const val BACKUP_FILE_PREFIX = "Studiare_Backup_"

/**
 * The scheduled-backup choke point: resolves the configured decks (all of them, unless the user
 * narrowed it down via Settings → Automatic Backups → "Select Format and Content"), builds the
 * configured format (JSON by default, or CSV/Anki `.apkg`/`.colpkg`), writes it to the one
 * currently-selected destination (local folder or Google Drive — a single choice, not both at
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
        var decksWithCards = DeckLinkageResolver.resolveEffectiveDecks(decks, cards)

        // null (the default, unless the user has picked specific decks via "Select Format and
        // Content") means every active deck — including ones created after that was configured.
        val deckIdsJson = prefs.backupDeckIdsFlow.first()
        if (deckIdsJson != null) {
            val selectedIds = runCatching {
                val array = JSONArray(deckIdsJson)
                (0 until array.length()).map { array.getString(it) }.toSet()
            }.getOrDefault(emptySet())
            decksWithCards = decksWithCards.filter { it.deck.id in selectedIds }
        }

        if (!prefs.backupIncludeMetadataFlow.first()) {
            decksWithCards = stripMetadataForExport(decksWithCards)
        }

        val content = buildBackupContent(prefs.backupFormatFlow.first(), decksWithCards)
        if (content == null) {
            prefs.recordBackupRun(false)
            return@withContext BackupRunSummary(false)
        }

        val dateFormat = SimpleDateFormat("yyyy-MM-dd_HHmm", Locale.getDefault())
        val fileBaseName = "$BACKUP_FILE_PREFIX${dateFormat.format(Date())}"

        val destination = prefs.backupDestinationFlow.first()
        val success = runCatching {
            if (destination == BackupDestination.DRIVE) writeDriveBackup(fileBaseName, content, prefs) else writeLocalBackup(fileBaseName, content, prefs)
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

    /** Anki formats write through [buildAnkiPackage]'s own destination-`Uri` writer, so they're staged to a temp cache file first and read back as bytes. */
    private suspend fun buildBackupContent(format: String, decksToExport: List<DeckWithCards>): BackupContent? = when (format) {
        "CSV" -> BackupContent(buildDecksCsv(decksToExport).toByteArray(), "text/csv", "csv")
        "ANKI_APKG", "ANKI_COLPKG" -> {
            val tempFile = File(context.cacheDir, "scheduled_backup_${System.currentTimeMillis()}.tmp")
            val built = runCatching { buildAnkiPackage(context, decksToExport, Uri.fromFile(tempFile)) }.getOrDefault(false)
            if (built && tempFile.exists()) {
                val bytes = tempFile.readBytes()
                tempFile.delete()
                BackupContent(bytes, "application/octet-stream", if (format == "ANKI_APKG") "apkg" else "colpkg")
            } else {
                tempFile.delete()
                null
            }
        }
        else -> BackupContent(buildDecksJson(decksToExport).toByteArray(), "application/json", "json")
    }

    private suspend fun writeLocalBackup(fileBaseName: String, content: BackupContent, prefs: PreferenceManager): Boolean {
        val folderUriString = prefs.localBackupFolderUriFlow.first() ?: return false
        val folder = DocumentFile.fromTreeUri(context, Uri.parse(folderUriString)) ?: return false
        if (!folder.exists() || !folder.isDirectory) return false
        val newFile = folder.createFile(content.mimeType, "$fileBaseName.${content.fileExtension}") ?: return false
        val written = context.contentResolver.openOutputStream(newFile.uri)?.use { it.write(content.bytes) }
        if (written == null) return false

        val retention = prefs.backupRetentionCountFlow.first()
        if (retention != BackupRetention.UNLIMITED) {
            folder.listFiles()
                .filter { it.name?.startsWith(BACKUP_FILE_PREFIX) == true }
                .sortedByDescending { it.name } // filenames embed yyyy-MM-dd_HHmm, so lexicographic order is chronological
                .drop(retention)
                .forEach { it.delete() }
        }
        return true
    }

    private suspend fun writeDriveBackup(fileBaseName: String, content: BackupContent, prefs: PreferenceManager): Boolean {
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

        val fileName = "$fileBaseName.${content.fileExtension}"
        if (!DriveBackupClient.uploadBackup(accessToken, folderId, fileName, content.mimeType, content.bytes)) return false

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
