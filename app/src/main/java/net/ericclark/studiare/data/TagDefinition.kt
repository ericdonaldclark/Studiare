package net.ericclark.studiare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "tags")
data class TagDefinition(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val color: String = "#0D47A1",
    val createdAt: Long = System.currentTimeMillis(),

    // --- SYNC METADATA ---
    val isPendingSync: Boolean = true,
    val isDeleted: Boolean = false
) {
    constructor() : this(UUID.randomUUID().toString())
}