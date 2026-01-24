package net.ericclark.studiare.data

import java.util.Locale
import java.util.UUID

/**
 * Represents a single flashcard deck.
 * In Firestore, relationships are often managed by storing lists of IDs.
 */
data class Deck(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val parentDeckId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val averageQuizScore: Float? = null,
    val normalizationType: Int = 0,
    val sortType: Int = 0,
    // Fix: Use @field:JvmField to force Firestore to use "isStarred" exactly.
    @field:JvmField
    val isStarred: Boolean = false,
    // NoSQL relationship: Store list of card IDs directly in the deck document
    val cardIds: List<String> = emptyList(),
    // Language settings for the deck sides
    val frontLanguage: String = Locale.getDefault().language,
    val backLanguage: String = Locale.getDefault().language,

    // --- FSRS CONFIGURATION ---
    // Whether FSRS scheduling is enabled for this deck
    val fsrsEnabled: Boolean = false,
    // The model weights (typically 17 or 19 doubles)
    val fsrsWeights: List<Double> = emptyList(),
    // Desired retention rate (e.g., 0.9 for 90%)
    val fsrsDesiredRetention: Double = 0.9,
    // Maximum interval in days for FSRS
    val fsrsMaximumInterval: Int = 36500
) {
    // No-argument constructor needed for Firestore deserialization
    constructor() : this(UUID.randomUUID().toString())
}
