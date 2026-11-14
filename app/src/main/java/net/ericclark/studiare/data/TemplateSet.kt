package net.ericclark.studiare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID
import net.ericclark.studiare.R

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

/** How a card's current front/back note fields compare to this template's field shape — drives
 * the "Use Template" dialog's per-row chip. Checked by name+type only, never content. */
enum class TemplateMatchStatus(override val labelResId: Int) : StringResourceEnum {
    /** Same fields, same order, on both sides — Apply would be a complete no-op. */
    MATCHING(R.string.template_match_matching),
    /** Same fields (nothing missing, nothing extra) but at least one side's order differs. */
    SAME_FIELDS(R.string.template_match_same_fields),
    /** The card already has every one of this template's fields, plus at least one more. */
    EXTRA_FIELDS(R.string.template_match_extra_fields),
    /** The card is missing at least one of this template's fields — Apply would add it. */
    NOT_MATCHING(R.string.template_match_not_matching)
}

/** See [TemplateMatchStatus]. */
fun TemplateSet.matchStatus(frontNotes: List<NoteField>, backNotes: List<NoteField>): TemplateMatchStatus {
    fun shapeOf(field: NoteField) = field.name to field.type
    fun isSubset(template: List<NoteField>, card: List<NoteField>): Boolean {
        val cardShapes = card.map { shapeOf(it) }
        return template.all { shapeOf(it) in cardShapes }
    }
    val frontIsSubset = isSubset(frontFields, frontNotes)
    val backIsSubset = isSubset(backFields, backNotes)
    if (!frontIsSubset || !backIsSubset) return TemplateMatchStatus.NOT_MATCHING

    val sameSizePerSide = frontFields.size == frontNotes.size && backFields.size == backNotes.size
    if (!sameSizePerSide) return TemplateMatchStatus.EXTRA_FIELDS

    val orderMatches = frontFields.map { shapeOf(it) } == frontNotes.map { shapeOf(it) } &&
        backFields.map { shapeOf(it) } == backNotes.map { shapeOf(it) }
    return if (orderMatches) TemplateMatchStatus.MATCHING else TemplateMatchStatus.SAME_FIELDS
}
