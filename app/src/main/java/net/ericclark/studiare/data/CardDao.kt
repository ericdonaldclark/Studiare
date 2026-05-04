package net.ericclark.studiare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CardDao {
    @Query("SELECT * FROM cards WHERE isDeleted = 0")
    fun getAllActiveCards(): Flow<List<Card>>

    // --- NEW: Chunked query to bypass the 2MB CursorWindow limit ---
    @Query("SELECT * FROM cards WHERE isDeleted = 0 LIMIT :limit OFFSET :offset")
    fun getActiveCardsPaged(limit: Int, offset: Int): List<Card>

    @Query("SELECT * FROM cards WHERE ownerDeckId = :deckId AND isDeleted = 0")
    fun getCardsForDeck(deckId: String): Flow<List<Card>>

    @Query("SELECT * FROM cards WHERE isPendingSync = 1")
    fun getPendingSyncCards(): List<Card>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(card: Card)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateAll(cards: List<Card>)

    @Query("UPDATE cards SET isDeleted = 1, isPendingSync = 1, updatedAt = :timestamp WHERE id = :cardId")
    fun softDelete(cardId: String, timestamp: Long)

    @Query("DELETE FROM cards WHERE id = :cardId")
    fun hardDelete(cardId: String)
}