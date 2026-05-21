package net.ericclark.studiare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

data class ReviewLog(
    val id: Long = System.currentTimeMillis(), // Timestamp of the review
    val ease: Int, // 1=Again, 2=Hard, 3=Good, 4=Easy
    val interval: Long, // New interval (in days or seconds)
    val lastInterval: Long, // Previous interval
    val factor: Double, // Ease factor / Stability
    val durationMs: Long, // Time spent answering
    val type: Int // 0=Learning, 1=Review, 2=Relearn, 3=Cram
)

@Entity(tableName = "cards")
data class Card(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val ownerDeckId: String? = null,

    val front: String = "",
    val frontType: CardDataType = CardDataType.TEXT,
    val frontRichText: String? = null,
    val back: String = "",
    val backType: CardDataType = CardDataType.TEXT,
    val backRichText: String? = null,
    val frontNotes: List<NoteField> = emptyList(),
    val backNotes: List<NoteField> = emptyList(),

    val difficulty: DifficultySetting = DifficultySetting.ONE,
    val isKnown: Boolean = false,
    val tags: List<String> = emptyList(),

    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),

    val reviewedAt: Long? = null,
    val reviewedCount: Int = 0,
    val gradedAttempts: List<Long> = emptyList(),
    val incorrectAttempts: List<Long> = emptyList(),

    val reviewLogs: List<ReviewLog> = emptyList(),
    val absoluteDueDate: Long? = null,

    val fsrsStability: Double? = null,
    val fsrsDifficulty: Double? = null,
    val fsrsElapsedDays: Double? = null,
    val fsrsScheduledDays: Double? = null,
    val fsrsState: FsrsState? = FsrsState.NEW,
    val fsrsLastReview: Long? = null,
    val fsrsLapses: Int = 0,

    val isSuspended: Boolean = false,
    val flag: CardFlag = CardFlag.NONE,
    val lastReviewDurationMs: Long = 0,

    // --- NEW: SYNC METADATA ---
    val isPendingSync: Boolean = true, // True by default for newly created items
    val isDeleted: Boolean = false     // True when a user deletes it (Soft Delete)
)