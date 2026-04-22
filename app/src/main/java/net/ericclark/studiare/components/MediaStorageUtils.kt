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
}