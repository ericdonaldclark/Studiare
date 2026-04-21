package net.ericclark.studiare.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.UUID

object MediaType {
    const val IMAGE = "IMAGE"
    const val AUDIO = "AUDIO"
    const val VIDEO = "VIDEO"
    const val DOCUMENT = "DOCUMENT"
}

@Entity(
    tableName = "media_attachments",
    foreignKeys = [
        ForeignKey(
            entity = Card::class,
            parentColumns = ["id"],
            childColumns = ["cardId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["cardId"])]
)
data class MediaAttachment(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val cardId: String,
    val type: String, // Use MediaType constants
    val localFilename: String, // E.g., the filename assigned in the app's internal vault
    val originalFilename: String? = null, // Essential for mapping Anki's media registry
    val createdAt: Long = System.currentTimeMillis()
)