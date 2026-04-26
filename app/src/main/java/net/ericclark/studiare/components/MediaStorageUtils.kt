package net.ericclark.studiare.components

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

object MediaStorageUtils {
    fun copyMediaToInternalStorage(context: Context, uri: Uri, prefix: String): String? {
        return try {
            val extension = context.contentResolver.getType(uri)?.split("/")?.lastOrNull() ?: "bin"
            val fileName = "${prefix}_${UUID.randomUUID()}.$extension"
            val destFile = File(context.filesDir, fileName)

            context.contentResolver.openInputStream(uri)?.use { input ->
                FileOutputStream(destFile).use { output ->
                    input.copyTo(output)
                }
            }
            destFile.absolutePath
        } catch (e: Exception) {
            AppLogger.e("MediaStorageUtils", "Failed to copy media", e)
            null
        }
    }

    fun cleanOrphanedMedia(context: Context, cards: List<net.ericclark.studiare.data.Card>, decks: List<net.ericclark.studiare.data.Deck>) {
        try {
            // Collect all valid media paths currently used in the database
            val validPaths = mutableSetOf<String>()

            cards.forEach { card ->
                card.frontNotes.forEach { if (it.content.startsWith(context.filesDir.absolutePath)) validPaths.add(it.content) }
                card.backNotes.forEach { if (it.content.startsWith(context.filesDir.absolutePath)) validPaths.add(it.content) }
            }

            decks.forEach { deck ->
                deck.frontNoteTemplates.forEach { if (it.content.startsWith(context.filesDir.absolutePath)) validPaths.add(it.content) }
                deck.backNoteTemplates.forEach { if (it.content.startsWith(context.filesDir.absolutePath)) validPaths.add(it.content) }
            }

            // Scan files directory for our media prefixes and delete if not in validPaths
            val allFiles = context.filesDir.listFiles() ?: return

            allFiles.forEach { file ->
                if ((file.name.startsWith("media_") || file.name.startsWith("audio_")) && !validPaths.contains(file.absolutePath)) {
                    AppLogger.d("MediaStorageUtils", "Deleting orphaned media file: ${file.name}")
                    file.delete()
                }
            }
        } catch (e: Exception) {
            AppLogger.e("MediaStorageUtils", "Failed to clean orphaned media", e)
        }
    }
}

