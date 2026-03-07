package net.ericclark.studiare.data

import java.util.UUID

/**
 * Represents a single flashcard.
 * Stored in a top-level 'cards' collection (or user-level).
 */
data class FirestoreCard(
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

    // Timestamps
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    // Basic Stats
    val reviewedAt: Long? = null,
    val reviewedCount: Int = 0,
    val gradedAttempts: List<Long> = emptyList(),
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
    val fsrsState: Int? = 0,
    // Timestamp of the last review used for FSRS calculations
    val fsrsLastReview: Long? = null,
    // Count of times the card was forgotten (lapses)
    val fsrsLapses: Int = 0,

    // --- NEW FIELDS ---
    // Whether the card is temporarily suspended from study sessions
    @field:JvmField
    val isSuspended: Boolean = false,
    // User flag (e.g. 0=None, 1=Red, 2=Orange, 3=Green)
    val flag: Int = 0,
    // Duration of the last review in milliseconds (for analytics)
    val lastReviewDurationMs: Long = 0
) {
    // No-argument constructor needed for Firestore deserialization
    constructor() : this(UUID.randomUUID().toString())

    // 1. Translate Database -> App
    fun toAppCard(): Card {
        return Card(
            id = this.id,
            ownerDeckId = this.ownerDeckId,
            front = this.front,
            back = this.back,
            frontNotes = this.frontNotes,
            backNotes = this.backNotes,
            difficulty = DifficultySetting.fromInt(this.difficulty),
            isKnown = this.isKnown,
            tags = this.tags,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            reviewedAt = this.reviewedAt,
            reviewedCount = this.reviewedCount,
            gradedAttempts = this.gradedAttempts,
            incorrectAttempts = this.incorrectAttempts,
            fsrsStability = this.fsrsStability,
            fsrsDifficulty = this.fsrsDifficulty,
            fsrsElapsedDays = this.fsrsElapsedDays,
            fsrsScheduledDays = this.fsrsScheduledDays,
            fsrsState = FsrsState.fromInt(this.fsrsState),
            fsrsLastReview = this.fsrsLastReview,
            fsrsLapses = this.fsrsLapses,
            isSuspended = this.isSuspended,
            flag = CardFlag.fromInt(this.flag),
            lastReviewDurationMs = this.lastReviewDurationMs
        )
    }
}

// 2. Translate App -> Database
fun Card.toFirestoreCard(): FirestoreCard {
    return FirestoreCard(
        id = this.id,
        ownerDeckId = this.ownerDeckId,
        front = this.front,
        back = this.back,
        frontNotes = this.frontNotes,
        backNotes = this.backNotes,
        difficulty = this.difficulty.value,
        isKnown = this.isKnown,
        tags = this.tags,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        reviewedAt = this.reviewedAt,
        reviewedCount = this.reviewedCount,
        gradedAttempts = this.gradedAttempts,
        incorrectAttempts = this.incorrectAttempts,
        fsrsStability = this.fsrsStability,
        fsrsDifficulty = this.fsrsDifficulty,
        fsrsElapsedDays = this.fsrsElapsedDays,
        fsrsScheduledDays = this.fsrsScheduledDays,
        fsrsState = this.fsrsState?.value,
        fsrsLastReview = this.fsrsLastReview,
        fsrsLapses = this.fsrsLapses,
        isSuspended = this.isSuspended,
        flag = this.flag.value,
        lastReviewDurationMs = this.lastReviewDurationMs
    )
}

