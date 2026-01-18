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
    val tags: List<String> = emptyList()
)
