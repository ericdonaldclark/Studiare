package net.ericclark.studiare.data

import java.util.UUID

data class ActiveSession(
    val id: String = UUID.randomUUID().toString(),
    val deckId: String = "",
    val mode: SessionMode = SessionMode.FLASHCARD,
    val isWeighted: Boolean = false,
    val difficulties: List<Int> = emptyList(),
    val totalCards: Int = 0,
    val shuffledCardIds: List<String> = emptyList(),
    val quizPromptSide: CardSide = CardSide.FRONT,
    val currentCardIndex: Int = 0,
    val wrongSelections: List<String> = emptyList(),
    val correctAnswerFound: Boolean = false,
    val showQuestion: Boolean = true,
    val isFlipped: Boolean = false,
    val firstTryCorrectCount: Int = 0,
    val hasAttempted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis(),
    val schedulingMode: SchedulingMode = SchedulingMode.NORMAL,

    /// Mode specific options
    val numberOfAnswers: Int = 4,
    val showCorrectLetters: Boolean = false,
    val limitAnswerPool: Boolean = true,
    val cardOrder: SortMode = SortMode.RANDOM,
    val mcOptions: Map<String, List<String>> = emptyMap(),
    val pickerOptions: List<String> = emptyList(),
    val matchingCardIdsOnScreen: List<String> = emptyList(),
    val matchedPairs: List<String> = emptyList(),
    val incorrectCardIds: List<String> = emptyList(),
    val isGraded: Boolean = false,
    val allowMultipleGuesses: Boolean = true,
    val enableStt: Boolean = false,
    val hideAnswerText: Boolean = false,
    val attemptedCardIds: List<String> = emptyList(),
    val fingersAndToes: Boolean = false,

    // Memory
    val maxMemoryTiles: Int = 12,
    val memorySelectedId1: String? = null,
    val memorySelectedSide1: CardSide? = null,
    val memorySelectedId2: String? = null,
    val memorySelectedSide2: CardSide? = null,

    // Crossword
    val crosswordWords: List<CrosswordWord> = emptyList(),
    val crosswordUserInputs: Map<String, String> = emptyMap(),
    val crosswordGridWidth: Int = 0,
    val crosswordGridHeight: Int = 0,
    val showCorrectWords: Boolean = true
)