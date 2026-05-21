package net.ericclark.studiare.data

import androidx.compose.ui.text.toLowerCase
import java.util.UUID
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

/**
 * Represents a single flashcard.
 * Stored in a top-level 'cards' collection (or user-level).
 */
data class FirestoreCard(
    val id: String = UUID.randomUUID().toString(),
    val ownerDeckId: String? = null,
    // User facing
    val front: String = "",
    val frontType: String = "text",
    val frontRichText: String? = null,

    val back: String = "",
    val backType: String = "text",
    val backRichText: String? = null,

    val frontNotes: String? = null, // Stored as serialized JSON
    val backNotes: String? = null,  // Stored as serialized JSON

    val difficulty: Any? = 1,
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

    val reviewLogs: String? = null, // Stored as serialized JSON
    val absoluteDueDate: Long? = null,

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
    val flag: Any? = 0,
    // Duration of the last review in milliseconds (for analytics)
    val lastReviewDurationMs: Long = 0
) {
    // No-argument constructor needed for Firestore deserialization
    constructor() : this(UUID.randomUUID().toString())

    private fun parseNotes(notesJson: String?): List<NoteField> {
        if (notesJson.isNullOrBlank()) return emptyList()
        return try {
            if (notesJson.trim().startsWith("[")) {
                Gson().fromJson(notesJson, object : TypeToken<List<NoteField>>() {}.type) ?: emptyList()
            } else {
                listOf(NoteField(name = "Note", content = notesJson, type = MediaType.PLAIN_TEXT))
            }
        } catch (e: Exception) {
            listOf(NoteField(name = "Note", content = notesJson, type = MediaType.PLAIN_TEXT))
        }
    }

    private fun parseReviewLogs(json: String?): List<ReviewLog> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            Gson().fromJson(json, object : TypeToken<List<ReviewLog>>() {}.type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // 1. Translate Database -> App
    fun toAppCard(): Card {

        // Safely parse the flag whether it's an Int, Long, or legacy String
        val parsedFlag = when (flag) {
            is Number -> flag.toInt()
            is String -> flag.toIntOrNull() ?: 0
            else -> 0
        }

        // Safely parse the difficulty whether it's an Int, Long, or legacy String
        val parsedDifficulty = when (difficulty) {
            is Number -> difficulty.toInt()
            is String -> difficulty.toIntOrNull() ?: 1
            else -> 1
        }

        return Card(
            id = this.id,
            ownerDeckId = this.ownerDeckId,
            front = this.front,
            frontType = this.frontType.toCardDataType(),
            frontRichText = this.frontRichText,
            back = this.back,
            backType = this.backType.toCardDataType(),
            backRichText = this.backRichText,
            frontNotes = parseNotes(this.frontNotes),
            backNotes = parseNotes(this.backNotes),
            difficulty = DifficultySetting.fromInt(parsedDifficulty),
            isKnown = this.isKnown,
            tags = this.tags,
            createdAt = this.createdAt,
            updatedAt = this.updatedAt,
            reviewedAt = this.reviewedAt,
            reviewedCount = this.reviewedCount,
            gradedAttempts = this.gradedAttempts,
            incorrectAttempts = this.incorrectAttempts,
            reviewLogs = parseReviewLogs(this.reviewLogs),
            absoluteDueDate = this.absoluteDueDate,
            fsrsStability = this.fsrsStability,
            fsrsDifficulty = this.fsrsDifficulty,
            fsrsElapsedDays = this.fsrsElapsedDays,
            fsrsScheduledDays = this.fsrsScheduledDays,
            fsrsState = FsrsState.fromInt(this.fsrsState),
            fsrsLastReview = this.fsrsLastReview,
            fsrsLapses = this.fsrsLapses,
            isSuspended = this.isSuspended,
            flag = CardFlag.fromInt(parsedFlag),
            lastReviewDurationMs = this.lastReviewDurationMs
        )
    }
}

// 2. Translate App -> Database
fun Card.toFirestoreCard(): FirestoreCard {
    val gson = Gson()
    return FirestoreCard(
        id = this.id,
        ownerDeckId = this.ownerDeckId,
        front = this.front,
        frontType = this.frontType.name.lowercase(),
        frontRichText = this.frontRichText,
        back = this.back,
        backType = this.backType.name.lowercase(),
        backRichText = this.backRichText,
        frontNotes = if (this.frontNotes.isNotEmpty()) gson.toJson(this.frontNotes) else null,
        backNotes = if (this.backNotes.isNotEmpty()) gson.toJson(this.backNotes) else null,
        difficulty = this.difficulty.value,
        isKnown = this.isKnown,
        tags = this.tags,
        createdAt = this.createdAt,
        updatedAt = this.updatedAt,
        reviewedAt = this.reviewedAt,
        reviewedCount = this.reviewedCount,
        gradedAttempts = this.gradedAttempts,
        incorrectAttempts = this.incorrectAttempts,
        reviewLogs = if (this.reviewLogs.isNotEmpty()) gson.toJson(this.reviewLogs) else null,
        absoluteDueDate = this.absoluteDueDate,
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

