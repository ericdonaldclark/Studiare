package net.ericclark.studiare.data

import java.util.UUID

data class CardDataForSave(
    val id: String,
    val front: String,
    val frontRichText: String?,
    val back: String,
    val backRichText: String?,
    val frontNotes: List<NoteField> = emptyList(),
    val backNotes: List<NoteField> = emptyList(),
    val difficulty: DifficultySetting = DifficultySetting.ONE,
    val isKnown: Boolean,
    val reviewedCount: Int = 0,
    val gradedAttempts: List<Long> = emptyList(),
    val incorrectAttempts: List<Long> = emptyList(),
    val reviewLogs: List<ReviewLog> = emptyList(),
    val absoluteDueDate: Long? = null,
    val tags: List<String> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val reviewedAt: Long? = null,

    // --- NEW FIELDS ---
    val isSuspended: Boolean = false,
    val flag: CardFlag = CardFlag.NONE,
    val lastReviewDurationMs: Long = 0,
    val isPinned: Boolean = false,

    // --- FSRS FIELDS ---
    val fsrsStability: Double? = null,
    val fsrsDifficulty: Double? = null,
    val fsrsElapsedDays: Double? = null,
    val fsrsScheduledDays: Double? = null,
    val fsrsState: FsrsState? = null,
    val fsrsLastReview: Long? = null,
    val fsrsLapses: Int = 0,

    // --- TEMPLATE LOCKING --- (read-only passthrough from whatever the card already has; see
    // FlashcardViewModel.saveDeckWithCards' ex?.field ?: cd.field fallback, same as the FSRS
    // fields above — only the dedicated template actions below ever actually change these)
    val enforcedTemplateSetId: String? = null,
    val isDeckTemplateEnforced: Boolean = false
)

fun Card.toCardDataForSave(): CardDataForSave = CardDataForSave(
    id = id,
    front = front,
    frontRichText = frontRichText,
    back = back,
    backRichText = backRichText,
    frontNotes = frontNotes,
    backNotes = backNotes,
    difficulty = difficulty,
    isKnown = isKnown,
    reviewedCount = reviewedCount,
    gradedAttempts = gradedAttempts,
    incorrectAttempts = incorrectAttempts,
    reviewLogs = reviewLogs,
    absoluteDueDate = absoluteDueDate,
    tags = tags,
    isSuspended = isSuspended,
    flag = flag,
    isPinned = isPinned,
    createdAt = createdAt,
    updatedAt = updatedAt,
    enforcedTemplateSetId = enforcedTemplateSetId,
    isDeckTemplateEnforced = isDeckTemplateEnforced
)

/**
 * A true independent duplicate: fresh id, content/tags/media copied as-is, every review/FSRS
 * field reset to a freshly-created card's defaults (no isKnown, no history, no due date, no
 * pin) — a clone's whole point is a new starting point, not a snapshot of review progress.
 */
fun CardDataForSave.asClone(): CardDataForSave = copy(
    id = UUID.randomUUID().toString(),
    isKnown = false,
    reviewedCount = 0,
    gradedAttempts = emptyList(),
    incorrectAttempts = emptyList(),
    reviewLogs = emptyList(),
    absoluteDueDate = null,
    isSuspended = false,
    flag = CardFlag.NONE,
    lastReviewDurationMs = 0,
    isPinned = false,
    fsrsStability = null,
    fsrsDifficulty = null,
    fsrsElapsedDays = null,
    fsrsScheduledDays = null,
    fsrsState = null,
    fsrsLastReview = null,
    fsrsLapses = 0,
    createdAt = System.currentTimeMillis(),
    updatedAt = System.currentTimeMillis(),
    reviewedAt = null,
    enforcedTemplateSetId = null,
    isDeckTemplateEnforced = false
)

/**
 * Builds the persisted [Card] row for a move to [ownerDeckId] — unlike [asClone], a move carries
 * review/FSRS state across decks as-is rather than resetting it, so it starts from [existing]'s
 * full row (when the card was already persisted) and overlays just this editor's latest
 * content/tags/metadata fields, the same ex-fallback pattern FlashcardViewModel's own
 * saveDeckWithCards mapping uses. Falls back to a fresh [Card] when [existing] is null (the card
 * was never saved before the move, e.g. a still-unsaved Bulk Editor row). `isDeckTemplateEnforced`
 * is always cleared — it refers to the *owning deck's* template, which no longer applies once the
 * card belongs to a different deck; `enforcedTemplateSetId` (a library template, deck-independent)
 * carries over unchanged.
 */
fun CardDataForSave.toMovedCard(ownerDeckId: String, existing: Card?): Card = (existing ?: Card(id = id)).copy(
    ownerDeckId = ownerDeckId,
    front = front,
    frontRichText = frontRichText,
    back = back,
    backRichText = backRichText,
    frontNotes = frontNotes,
    backNotes = backNotes,
    difficulty = difficulty,
    isKnown = isKnown,
    tags = tags,
    isSuspended = isSuspended,
    flag = flag,
    isPinned = isPinned,
    updatedAt = System.currentTimeMillis(),
    isPendingSync = true,
    isDeckTemplateEnforced = false
)