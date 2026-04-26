package net.ericclark.studiare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

data class DeckSummary(
    val deck: Deck,
    val totalCards: Int
)
@Dao
interface DeckDao {
    // Flow is naturally asynchronous, so it does not need the suspend keyword
    @Query("SELECT * FROM decks WHERE isDeleted = 0")
    fun getAllActiveDecks(): Flow<List<Deck>>
    @Query("SELECT * FROM decks WHERE id = :deckId LIMIT 1")
    fun getDeckById(deckId: String): Deck?

    @Query("SELECT * FROM decks WHERE isPendingSync = 1")
    fun getPendingSyncDecks(): List<Deck>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdate(deck: Deck)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertOrUpdateAll(decks: List<Deck>)

    @Query("UPDATE decks SET isDeleted = 1, isPendingSync = 1, updatedAt = :timestamp WHERE id = :deckId")
    fun softDelete(deckId: String, timestamp: Long)

    @Query("DELETE FROM decks WHERE id = :deckId")
    fun hardDelete(deckId: String)
}