package net.ericclark.studiare.data

import java.util.UUID

/*
 * Represents the state of an active study session to allow resuming.
 * Persisted as JSON in DataStore.
 */
data class ActiveSession(
    val id: String = UUID.randomUUID().toString(),
    val deckId: String,
    val mode: String,
    val schedulingMode: String = "Normal",
    val isWeighted: Boolean,
    val difficulties: List<Int>,
    val totalCards: Int,
    val shuffledCardIds: List<String>,
    val quizPromptSide: String = "Question",
    val currentCardIndex: Int = 0,
    val wrongSelections: List<String> = emptyList(),
    val correctAnswerFound: Boolean = false,
    val showQuestion: Boolean = true,
    val isFlipped: Boolean = false,
    val firstTryCorrectCount: Int = 0,
    val hasAttempted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis(),
    val numberOfAnswers: Int = 4,
    val showCorrectLetters: Boolean = false,
    val limitAnswerPool: Boolean = true,
    val cardOrder: String = "Random",
    val mcOptions: Map<String, List<String>> = emptyMap(),
    val pickerOptions: List<String> = emptyList(),
    val matchingCardIdsOnScreen: List<String> = emptyList(),
    val matchedPairs: List<String> = emptyList(),
    val incorrectCardIds: List<String> = emptyList(),
    val isGraded: Boolean = false,
    val allowMultipleGuesses: Boolean = true,
    val enableStt: Boolean = false,
    val hideAnswerText: Boolean = false,
    // Track IDs of cards that have already been graded to prevent score inflation
    val attemptedCardIds: List<String> = emptyList(),
    val fingersAndToes: Boolean = false,
    // ADDED: Memory Mode State
    val maxMemoryTiles: Int = 20,
    // ID of the card currently "held" at the bottom (first selection)
    val memorySelectedId1: String? = null,
    // "Front" or "Back" for the first selection
    val memorySelectedSide1: String? = null,
    // ID of the card currently shown above the grid (second selection)
    val memorySelectedId2: String? = null,
    // "Front" or "Back" for the second selection
    val memorySelectedSide2: String? = null,
    // Crossword states
    val crosswordWords: List<CrosswordWord> = emptyList(), // JSON serialized list of CrosswordWord
    val crosswordUserInputs: Map<String, String> = emptyMap(), // "x,y" -> "char"
    val crosswordGridWidth: Int = 0,
    val crosswordGridHeight: Int = 0,
    val showCorrectWords: Boolean = true
)
