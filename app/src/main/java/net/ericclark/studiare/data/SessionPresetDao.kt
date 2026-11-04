package net.ericclark.studiare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionPresetDao {
    @Query("SELECT * FROM session_presets WHERE isDeleted = 0 ORDER BY createdAt DESC")
    fun getAllActiveSessionPresets(): Flow<List<SessionPreset>>

    @Query("SELECT * FROM session_presets WHERE id = :id AND isDeleted = 0")
    suspend fun getSessionPresetById(id: String): SessionPreset?

    @Query("SELECT * FROM session_presets WHERE isPendingSync = 1")
    fun getPendingSyncSessionPresets(): List<SessionPreset>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(preset: SessionPreset)

    @Query("UPDATE session_presets SET isDeleted = 1, isPendingSync = 1 WHERE id = :id")
    fun softDelete(id: String)

    @Query("DELETE FROM session_presets WHERE id = :id")
    fun hardDelete(id: String)

    @Query("DELETE FROM session_presets WHERE isDeleted = 1")
    fun purgeDeletedSessionPresets()
}
