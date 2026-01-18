package net.ericclark.studiare.data

import java.util.UUID

/**
 * Represents a single flashcard.
 * Stored in a top-level 'cards' collection (or user-level).
 */
data class Card(
    val id: String = UUID.randomUUID().toString(),
    val front: String = "",
    val back: String = "",
    val frontNotes: String? = null,
    val backNotes: String? = null,
    val difficulty: Int = 1,
    val reviewedAt: Long? = null,
    // Fix: Use @field:JvmField to force Firestore to use "isKnown" exactly.
    @field:JvmField
    val isKnown: Boolean = false,
    val tags: List<String> = emptyList(),
    // Optional: Track the "main" deck for ownership logic, if strictly needed
    val ownerDeckId: String? = null,

    // --- NEW PROPERTIES ---
    // Total times reviewed (graded or not)
    val reviewedCount: Int = 0,
    // List of timestamps for every graded review attempt
    val gradedAttempts: List<Long> = emptyList(),
    // List of timestamps for every incorrect answer in a graded mode
    val incorrectAttempts: List<Long> = emptyList()
) {
    // No-argument constructor needed for Firestore deserialization
    constructor() : this(UUID.randomUUID().toString())
}
