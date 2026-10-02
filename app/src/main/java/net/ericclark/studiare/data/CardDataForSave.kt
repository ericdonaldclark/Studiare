package net.ericclark.studiare.data

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

    // --- FSRS FIELDS ---
    val fsrsStability: Double? = null,
    val fsrsDifficulty: Double? = null,
    val fsrsElapsedDays: Double? = null,
    val fsrsScheduledDays: Double? = null,
    val fsrsState: FsrsState? = null,
    val fsrsLastReview: Long? = null,
    val fsrsLapses: Int = 0
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
    createdAt = createdAt,
    updatedAt = updatedAt
)