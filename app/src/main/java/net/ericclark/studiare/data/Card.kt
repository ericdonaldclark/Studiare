package net.ericclark.studiare.data

import java.util.UUID

/**
 * Represents a single flashcard.
 * Stored in a top-level 'cards' collection (or user-level).
 */
data class Card(
    val id: String = UUID.randomUUID().toString(),
    val ownerDeckId: String? = null,
    // User facing
    val front: String = "",
    val back: String = "",
    val frontNotes: String? = null,
    val backNotes: String? = null,
    val difficulty: Int = 1,
    @field:JvmField
    val isKnown: Boolean = false,
    val tags: List<String> = emptyList(),


    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    // Total times reviewed (graded or not)
    val reviewedAt: Long? = null,
    val reviewedCount: Int = 0,
    // List of timestamps for every graded review attempt
    val gradedAttempts: List<Long> = emptyList(),
    // List of timestamps for every incorrect answer in a graded mode
    val incorrectAttempts: List<Long> = emptyList(),

    // --- FSRS FIELDS ---
    // Stability (S): The interval (in days) when retrievability is 90%
    val fsrsStability: Double? = null,
    // Difficulty (D): The difficulty of the card, typically 1.0 - 10.0
    val fsrsDifficulty: Double? = null,
    // Days elapsed since the last review
    val fsrsElapsedDays: Double? = null,
    // Scheduled days until the next review
    val fsrsScheduledDays: Double? = null,
    // State: 0=New, 1=Learning, 2=Review, 3=Relearning
    val fsrsState: Int? = null,
    // Timestamp of the last review used for FSRS calculations
    val fsrsLastReview: Long? = null,
    // Count of times the card was forgotten (lapses)
    val fsrsLapses: Int = 0
) {
    // No-argument constructor needed for Firestore deserialization
    constructor() : this(UUID.randomUUID().toString())
}
