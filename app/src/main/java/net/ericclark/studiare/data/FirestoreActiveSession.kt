package net.ericclark.studiare.data

import java.util.UUID

data class FirestoreActiveSession(
    val id: String = UUID.randomUUID().toString(),
    val deckId: String = "",
    val mode: String = "Flashcard",
    val isWeighted: Boolean = false,
    val difficulties: List<Int> = emptyList(),
    val totalCards: Int = 0,
    val shuffledCardIds: List<String> = emptyList(),
    val quizPromptSide: String = "Front",
    val currentCardIndex: Int = 0,
    val wrongSelections: List<String> = emptyList(),
    val correctAnswerFound: Boolean = false,
    val showQuestion: Boolean = true,
    val isFlipped: Boolean = false,
    val firstTryCorrectCount: Int = 0,
    val hasAttempted: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val lastAccessed: Long = System.currentTimeMillis(),
    val schedulingMode: String = "Normal",

    // Mode specific options
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
    val attemptedCardIds: List<String> = emptyList(),
    val fingersAndToes: Boolean = false,

    // Memory
    val maxMemoryTiles: Int = 12,
    val memorySelectedId1: String? = null,
    val memorySelectedSide1: String? = null,
    val memorySelectedId2: String? = null,
    val memorySelectedSide2: String? = null,

    // Crossword
    val crosswordWords: List<CrosswordWord> = emptyList(),
    val crosswordUserInputs: Map<String, String> = emptyMap(),
    val crosswordGridWidth: Int = 0,
    val crosswordGridHeight: Int = 0,
    val showCorrectWords: Boolean = true
) {
    // No-arg constructor for Firestore
    constructor() : this(id = UUID.randomUUID().toString())

    // 1. Translate Database -> App
    fun toAppActiveSession(): ActiveSession {
        return ActiveSession(
            id = this.id,
            deckId = this.deckId,
            mode = this.mode.toSessionMode(),
            isWeighted = this.isWeighted,
            difficulties = this.difficulties,
            totalCards = this.totalCards,
            shuffledCardIds = this.shuffledCardIds,
            quizPromptSide = this.quizPromptSide.toCardSide(),
            currentCardIndex = this.currentCardIndex,
            wrongSelections = this.wrongSelections,
            correctAnswerFound = this.correctAnswerFound,
            showQuestion = this.showQuestion,
            isFlipped = this.isFlipped,
            firstTryCorrectCount = this.firstTryCorrectCount,
            hasAttempted = this.hasAttempted,
            createdAt = this.createdAt,
            lastAccessed = this.lastAccessed,
            schedulingMode = this.schedulingMode.toSchedulingMode(),
            /// Mode specific options
            numberOfAnswers = this.numberOfAnswers,
            showCorrectLetters = this.showCorrectLetters,
            limitAnswerPool = this.limitAnswerPool,
            cardOrder = this.cardOrder.toSortMode(),
            mcOptions = this.mcOptions,
            pickerOptions = this.pickerOptions,
            matchingCardIdsOnScreen = this.matchingCardIdsOnScreen,
            matchedPairs = this.matchedPairs,
            incorrectCardIds = this.incorrectCardIds,
            isGraded = this.isGraded,
            allowMultipleGuesses = this.allowMultipleGuesses,
            enableStt = this.enableStt,
            hideAnswerText = this.hideAnswerText,
            attemptedCardIds = this.attemptedCardIds,
            fingersAndToes = this.fingersAndToes,
            // Memory
            maxMemoryTiles = this.maxMemoryTiles,
            memorySelectedId1 = this.memorySelectedId1,
            memorySelectedSide1 = this.memorySelectedSide1?.toCardSide(),
            memorySelectedId2 = this.memorySelectedId2,
            memorySelectedSide2 = this.memorySelectedSide2?.toCardSide(),
            // Crossword
            crosswordWords = this.crosswordWords,
            crosswordUserInputs = this.crosswordUserInputs,
            crosswordGridWidth = this.crosswordGridWidth,
            crosswordGridHeight = this.crosswordGridHeight,
            showCorrectWords = this.showCorrectWords
        )
    }
}
// 2. Translate App -> Database
fun ActiveSession.toFirestoreActiveSession(): FirestoreActiveSession {
    return FirestoreActiveSession(
        id = this.id,
        deckId = this.deckId,
        mode = this.mode.name,
        isWeighted = this.isWeighted,
        difficulties = this.difficulties,
        totalCards = this.totalCards,
        shuffledCardIds = this.shuffledCardIds,
        quizPromptSide = this.quizPromptSide.name,
        currentCardIndex = this.currentCardIndex,
        wrongSelections = this.wrongSelections,
        correctAnswerFound = this.correctAnswerFound,
        showQuestion = this.showQuestion,
        isFlipped = this.isFlipped,
        firstTryCorrectCount = this.firstTryCorrectCount,
        hasAttempted = this.hasAttempted,
        createdAt = this.createdAt,
        lastAccessed = this.lastAccessed,
        schedulingMode = this.schedulingMode.name,
        /// Mode specific options
        numberOfAnswers = this.numberOfAnswers,
        showCorrectLetters = this.showCorrectLetters,
        limitAnswerPool = this.limitAnswerPool,
        cardOrder = this.cardOrder.name,
        mcOptions = this.mcOptions,
        pickerOptions = this.pickerOptions,
        matchingCardIdsOnScreen = this.matchingCardIdsOnScreen,
        matchedPairs = this.matchedPairs,
        incorrectCardIds = this.incorrectCardIds,
        isGraded = this.isGraded,
        allowMultipleGuesses = this.allowMultipleGuesses,
        enableStt = this.enableStt,
        hideAnswerText = this.hideAnswerText,
        attemptedCardIds = this.attemptedCardIds,
        fingersAndToes = this.fingersAndToes,
        // Memory
        maxMemoryTiles = this.maxMemoryTiles,
        memorySelectedId1 = this.memorySelectedId1,
        memorySelectedSide1 = this.memorySelectedSide1?.name,
        memorySelectedId2 = this.memorySelectedId2,
        memorySelectedSide2 = this.memorySelectedSide2?.name,
        // Crossword
        crosswordWords = this.crosswordWords,
        crosswordUserInputs = this.crosswordUserInputs,
        crosswordGridWidth = this.crosswordGridWidth,
        crosswordGridHeight = this.crosswordGridHeight,
        showCorrectWords = this.showCorrectWords
    )
}
