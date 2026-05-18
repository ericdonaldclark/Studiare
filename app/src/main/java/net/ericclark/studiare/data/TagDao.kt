package net.ericclark.studiare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TagDao {
    @Query("SELECT * FROM tags WHERE isDeleted = 0 ORDER BY name COLLATE NOCASE ASC")
    fun getAllActiveTags(): Flow<List<TagDefinition>>

    @Query("SELECT * FROM tags WHERE isPendingSync = 1")
    fun getPendingSyncTags(): List<TagDefinition>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(tag: TagDefinition)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateAll(tags: List<TagDefinition>)

    @Query("UPDATE tags SET isDeleted = 1, isPendingSync = 1 WHERE id = :tagId")
    fun softDelete(tagId: String)

    @Query("DELETE FROM tags WHERE id = :tagId")
    fun hardDelete(tagId: String)

    @Query("DELETE FROM tags WHERE isDeleted = 1")
    fun purgeDeletedTags()
}