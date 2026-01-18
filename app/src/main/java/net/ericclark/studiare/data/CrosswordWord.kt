package net.ericclark.studiare.data

data class CrosswordWord(
    val id: String, // Corresponds to Card ID
    val word: String, // The answer text (stripped of spaces/punctuation)
    val clue: String, // The prompt text
    val startX: Int,
    val startY: Int,
    val isAcross: Boolean,
    val number: Int // The clue number (1, 2, 3...)
)
