package net.ericclark.studiare.components

import android.content.Context
import android.net.Uri
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import android.database.sqlite.SQLiteDatabase
import com.google.firebase.crashlytics.internal.Logger.TAG
import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.Deck
import net.ericclark.studiare.data.CardDataType
import java.util.UUID
import net.ericclark.studiare.data.MediaAttachment
import net.ericclark.studiare.data.MediaType

class AnkiExtractionEngine(
    private val context: Context,
    private val mediaStorageManager: MediaStorageManager
) {
    private val TAG = "AnkiExtractionEngine"
    private val gson = Gson()

    /**
     * Unzips the Anki package, processes the media, and returns the File pointing to the SQLite database.
     */
    suspend fun extractAndProcessApkg(apkgUri: Uri): File? = withContext(Dispatchers.IO) {
        val tempDir = File(context.cacheDir, "anki_extraction_${System.currentTimeMillis()}").apply { mkdirs() }

        try {
            // 1. Extract the ZIP archive
            context.contentResolver.openInputStream(apkgUri)?.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry: ZipEntry? = zis.nextEntry
                    while (entry != null) {
                        val outFile = File(tempDir, entry.name)
                        FileOutputStream(outFile).use { fos ->
                            zis.copyTo(fos)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }

            // 2. Locate the database file
            val dbFile = File(tempDir, "collection.anki21").takeIf { it.exists() }
                ?: File(tempDir, "collection.anki2").takeIf { it.exists() }

            if (dbFile == null) {
                AppLogger.e(TAG, "No valid Anki database found in the package.")
                return@withContext null
            }

            // 3. Process the 'media' registry
            val mediaRegistryFile = File(tempDir, "media")
            if (mediaRegistryFile.exists()) {
                val jsonString = mediaRegistryFile.readText()
                val type = object : TypeToken<Map<String, String>>() {}.type
                val mediaMap: Map<String, String> = gson.fromJson(jsonString, type)

                // 4. Move files to Studiare's Media Vault
                mediaMap.forEach { (ankiId, originalFilename) ->
                    val extractedMediaFile = File(tempDir, ankiId)
                    if (extractedMediaFile.exists()) {
                        FileInputStream(extractedMediaFile).use { fileStream ->
                            // Save to our vault using the manager we built in Phase 1
                            mediaStorageManager.saveMediaFileFromStream(
                                inputStream = fileStream,
                                targetFilename = originalFilename
                            )
                        }
                    }
                }
            }

            return@withContext dbFile

        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to extract Anki package", e)
            tempDir.deleteRecursively() // Clean up on failure
            return@withContext null
        }
    }

    /**
     * Call this after parsing the SQLite database to clean up the temporary extraction folder.
     */
    fun cleanupTempDirectory(dbFile: File) {
        dbFile.parentFile?.deleteRecursively()
    }
}

/**
 * Connects to the extracted Anki SQLite database, parses the notes/cards,
 * and maps them into Studiare's data models.
 */
suspend fun parseAnkiDatabase(dbFile: File, newDeckName: String): Triple<Deck, List<Card>, List<MediaAttachment>>? = withContext(Dispatchers.IO) {
    var database: SQLiteDatabase? = null
    try {
        // Open the external SQLite file
        database = SQLiteDatabase.openDatabase(dbFile.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

        val newDeck = Deck(id = UUID.randomUUID().toString(), name = newDeckName)
        val extractedCards = mutableListOf<Card>()
        val extractedAttachments = mutableListOf<MediaAttachment>()

        // Regex patterns for Anki media
        val imgRegex = Regex("<img[^>]+src=[\"']([^\"']+)[\"'][^>]*>", RegexOption.IGNORE_CASE)
        val soundRegex = Regex("\\[sound:(.+?)\\]", RegexOption.IGNORE_CASE)

        // Query: Join Cards and Notes to get the active flashcard data.
        // Anki stores the actual text in the 'flds' column (separated by an invisible character).
        val cursor = database.rawQuery(
            "SELECT n.flds, n.tags FROM cards c INNER JOIN notes n ON c.nid = n.id",
            null
        )

        if (cursor.moveToFirst()) {
            val fldsIndex = cursor.getColumnIndex("flds")
            val tagsIndex = cursor.getColumnIndex("tags")

            do {
                // Anki separates fields using the Unit Separator character (ASCII 31)
                val rawFields = cursor.getString(fldsIndex)
                val fields = rawFields.split('\u001F')

                val rawTags = cursor.getString(tagsIndex)
                // Anki tags are space-separated strings with leading/trailing spaces
                val tagList = rawTags?.trim()?.split(" ")?.filter { it.isNotBlank() } ?: emptyList()

                if (fields.isNotEmpty()) {
                    val frontText = fields.getOrNull(0) ?: ""
                    val backText = fields.getOrNull(1) ?: ""

                    // If Anki has extra fields (like Pronunciation, Audio, extra context),
                    // squash them into the backNotes so no data is lost.
                    var extraNotes = if (fields.size > 2) {
                        fields.subList(2, fields.size).joinToString("<br><br>")
                    } else null

                    val cardId = UUID.randomUUID().toString()

                    // Helper function to extract attachments and clean the text
                    fun extractAndClean(text: String?): String {
                        if (text == null) return ""
                        var cleanText = text

                        // Extract Images
                        imgRegex.findAll(cleanText).forEach { matchResult ->
                            val filename = matchResult.groupValues[1]
                            extractedAttachments.add(MediaAttachment(cardId = cardId, type = MediaType.IMAGE, localFilename = filename))
                        }

                        // Extract Audio/Video
                        soundRegex.findAll(cleanText).forEach { matchResult ->
                            val filename = matchResult.groupValues[1]
                            val type = if (filename.endsWith(".mp4") || filename.endsWith(".mov")) MediaType.VIDEO else MediaType.AUDIO
                            extractedAttachments.add(MediaAttachment(cardId = cardId, type = type, localFilename = filename))
                        }

                        // Clean Anki's proprietary sound tags from the visible text
                        cleanText = cleanText.replace(soundRegex, "")
                        // We leave <img> tags in the HTML so AnnotatedString can try to parse them,
                        // or the user can just use the thumbnail row we built.

                        return cleanText
                    }

                    val cleanFront = extractAndClean(frontText)
                    val cleanBack = extractAndClean(backText)
                    val cleanNotes = extractAndClean(extraNotes)

                    extractedCards.add(
                        Card(
                            id = cardId,
                            ownerDeckId = newDeck.id,
                            front = cleanFront,
                            frontType = CardDataType.TEXT,
                            back = cleanBack,
                            backType = CardDataType.TEXT,
                            backNotes = cleanNotes,
                            backNotesType = CardDataType.TEXT,
                            tags = tagList
                        )
                    )
                }
            } while (cursor.moveToNext())
        }
        cursor.close()

        // Link the extracted cards to the deck before returning
        val finalDeck = newDeck.copy(cardIds = extractedCards.map { it.id })

        return@withContext Triple(finalDeck, extractedCards, extractedAttachments)

    } catch (e: Exception) {
        AppLogger.e(TAG, "Failed to parse SQLite database", e)
        return@withContext null
    } finally {
        database?.close()
    }
}