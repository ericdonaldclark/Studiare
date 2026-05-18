package net.ericclark.studiare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Query("SELECT * FROM sessions WHERE isDeleted = 0")
    fun getAllActiveSessions(): Flow<List<ActiveSession>>

    @Query("SELECT * FROM sessions WHERE isPendingSync = 1")
    fun getPendingSyncSessions(): List<ActiveSession>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(session: ActiveSession)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateAll(sessions: List<ActiveSession>)

    @Query("UPDATE sessions SET isDeleted = 1, isPendingSync = 1 WHERE id = :sessionId")
    fun softDelete(sessionId: String)

    @Query("DELETE FROM sessions WHERE id = :sessionId")
    fun hardDelete(sessionId: String)

    @Query("DELETE FROM sessions WHERE isDeleted = 1")
    fun purgeDeletedSessions()
}