package net.ericclark.studiare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "collections")
data class DeckCollection(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val description: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // --- SYNC METADATA ---
    val isPendingSync: Boolean = true,
    val isDeleted: Boolean = false
)