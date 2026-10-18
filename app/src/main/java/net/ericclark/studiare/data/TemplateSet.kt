package net.ericclark.studiare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

/** A saved front/back note-field layout, reusable as a starting point for other decks' templates. */
@Entity(tableName = "template_sets")
data class TemplateSet(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val frontFields: List<NoteField> = emptyList(),
    val backFields: List<NoteField> = emptyList(),
    val createdAt: Long = System.currentTimeMillis(),

    // --- SYNC METADATA ---
    val isPendingSync: Boolean = true,
    val isDeleted: Boolean = false
) {
    constructor() : this(UUID.randomUUID().toString())
}
