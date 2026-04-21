package net.ericclark.studiare.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaAttachmentDao {
    @Query("SELECT * FROM media_attachments WHERE cardId = :cardId")
    fun getAttachmentsForCard(cardId: String): Flow<List<MediaAttachment>>

    @Query("SELECT * FROM media_attachments WHERE cardId = :cardId")
    fun getAttachmentsForCardSync(cardId: String): List<MediaAttachment>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(attachment: MediaAttachment)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateAll(attachments: List<MediaAttachment>)

    @Query("DELETE FROM media_attachments WHERE id = :attachmentId")
    fun delete(attachmentId: String)

    @Query("DELETE FROM media_attachments WHERE cardId = :cardId")
    fun deleteAttachmentsForCard(cardId: String)
}