package net.ericclark.studiare.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DeckCollectionDao {
    @Query("SELECT * FROM collections WHERE isDeleted = 0")
    fun getAllActiveCollections(): Flow<List<DeckCollection>>

    @Transaction
    @Query("SELECT * FROM collections WHERE isDeleted = 0")
    fun getCollectionsWithDecks(): Flow<List<CollectionWithDecks>>

    @Transaction
    @Query("SELECT * FROM collections WHERE id = :collectionId AND isDeleted = 0 LIMIT 1")
    fun getCollectionWithDecksById(collectionId: String): Flow<CollectionWithDecks?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(collection: DeckCollection)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCrossRef(crossRef: CollectionDeckCrossRef)

    @Delete
    suspend fun deleteCrossRef(crossRef: CollectionDeckCrossRef)

    @Query("UPDATE collections SET isDeleted = 1, isPendingSync = 1, updatedAt = :timestamp WHERE id = :collectionId")
    suspend fun softDelete(collectionId: String, timestamp: Long = System.currentTimeMillis())
}