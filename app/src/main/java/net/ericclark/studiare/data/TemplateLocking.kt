package net.ericclark.studiare.data

/** A card's effective field-shape lock — per-card override wins over the deck's own policy.
 * Null means unlocked (Manual, or Apply mode, or Enforce mode but neither flag set on this card).
 * See Card.enforcedTemplateSetId/isDeckTemplateEnforced for why these are resolved live rather
 * than snapshotted. */
data class LockedTemplateFields(val front: List<NoteField>, val back: List<NoteField>)

fun effectiveLockedFields(
    enforcedTemplateSetId: String?,
    isDeckTemplateEnforced: Boolean,
    templateSets: List<TemplateSet>,
    deck: Deck?
): LockedTemplateFields? {
    if (enforcedTemplateSetId != null) {
        val templateSet = templateSets.find { it.id == enforcedTemplateSetId } ?: return null
        return LockedTemplateFields(templateSet.frontFields, templateSet.backFields)
    }
    if (isDeckTemplateEnforced && deck != null) {
        return LockedTemplateFields(deck.frontNoteTemplates, deck.backNoteTemplates)
    }
    return null
}

/** Whether a specific note field (by name+type) is one of the locked template's own fields —
 * used to gate that one [DynamicNoteEditor] instance's lock, not the whole card. */
fun List<NoteField>.containsShapeOf(field: NoteField): Boolean = any { it.name == field.name && it.type == field.type }
