package net.ericclark.studiare.data

data class CardDataForSave(
    val id: String,
    val front: String,
    val back: String,
    val frontNotes: String?,
    val backNotes: String?,
    val difficulty: Int,
    val isKnown: Boolean,
    val reviewedCount: Int = 0,
    val gradedAttempts: List<Long> = emptyList(),
    val incorrectAttempts: List<Long> = emptyList(),
    val tags: List<String> = emptyList(),
    val createdAt: Long? = null,
    val updatedAt: Long? = null,
    val reviewedAt: Long? = null,

    // --- NEW FIELDS ---
    val defaultSortOrder: Long = 0,
    val isSuspended: Boolean = false,
    val flag: Int = 0,
    val lastReviewDurationMs: Long = 0,

    // --- FSRS FIELDS ---
    val fsrsStability: Double? = null,
    val fsrsDifficulty: Double? = null,
    val fsrsElapsedDays: Double? = null,
    val fsrsScheduledDays: Double? = null,
    val fsrsState: Int? = null,
    val fsrsLastReview: Long? = null,
    val fsrsLapses: Int = 0
)