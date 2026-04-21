package net.ericclark.studiare.components

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.ericclark.studiare.components.AppLogger
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID

class MediaStorageManager(private val context: Context) {
    private val TAG = "MediaStorageManager"

    // Create an isolated directory inside the app's internal files specifically for user media
    private val mediaDir = File(context.filesDir, "media_vault").apply {
        if (!exists()) mkdirs()
    }

    /**
     * Saves a file from an Android Uri (like an image picker) into the vault.
     * Returns the newly generated local filename.
     */
    suspend fun saveMediaFileFromUri(sourceUri: Uri, originalExtension: String): String? = withContext(Dispatchers.IO) {
        val localFilename = "${UUID.randomUUID()}.$originalExtension"
        val destFile = File(mediaDir, localFilename)

        try {
            context.contentResolver.openInputStream(sourceUri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext localFilename
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to copy media from URI", e)
            return@withContext null
        }
    }

    /**
     * Saves a file directly from an InputStream.
     * This will be critical for extracting files directly out of the Anki .apkg zip file.
     */
    suspend fun saveMediaFileFromStream(inputStream: InputStream, targetFilename: String): String? = withContext(Dispatchers.IO) {
        val destFile = File(mediaDir, targetFilename)
        try {
            inputStream.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            return@withContext targetFilename
        } catch (e: Exception) {
            AppLogger.e(TAG, "Failed to save media from stream", e)
            return@withContext null
        }
    }

    /**
     * Gets the physical File object for the UI to render (e.g., passing to Coil for images).
     */
    fun getMediaFile(localFilename: String): File? {
        val file = File(mediaDir, localFilename)
        return if (file.exists()) file else null
    }

    /**
     * Deletes a specific media file. Call this when an attachment or card is deleted.
     */
    suspend fun deleteMediaFile(localFilename: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(mediaDir, localFilename)
        return@withContext if (file.exists()) file.delete() else false
    }
}