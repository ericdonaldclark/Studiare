package net.ericclark.studiare.data

data class StudyState(
    val sessionId: String,
    val deckWithCards: DeckWithCards,
    val studyMode: String,
    val schedulingMode: String = "Normal",
    val nextIntervals: Map<Int, String> = emptyMap(),
    val isWeighted: Boolean,
    val shuffledCards: List<Card>,
    val quizPromptSide: String = "Front",
    val currentCardIndex: Int = 0,
    val wrongSelections: List<String> = emptyList(),
    val correctAnswerFound: Boolean = false,
    val showFront: Boolean = true,
    val isFlipped: Boolean = false,
    val isComplete: Boolean = false,
    val firstTryCorrectCount: Int = 0,
    val hasAttempted: Boolean = false,
    val lastIncorrectAnswer: String? = null,
    val numberOfAnswers: Int = 4,
    val showCorrectLetters: Boolean = false,
    val limitAnswerPool: Boolean = true,
    val difficulties: List<Int> = emptyList(),
    val cardOrder: String = "Random",
    val isCardRevealed: Boolean = false,
    val mcOptions: Map<String, List<String>> = emptyMap(),
    val pickerOptions: List<String> = emptyList(),
    val matchingCardsOnScreen: List<Card> = emptyList(),
    val selectedMatchingItem: Pair<String, String>? = null,
    val successfullyMatchedPairs: List<String> = emptyList(),
    val incorrectlyMatchedPair: Pair<Pair<String, String>, Pair<String, String>>? = null,
    val matchingCardsPerColumn: Int = 0,
    val matchingAttemptedIncorrectly: List<String> = emptyList(),
    val incorrectCardIds: List<String> = emptyList(),
    val isGraded: Boolean = false,
    val allowMultipleGuesses: Boolean = true,
    val matchingRevealPair: List<String> = emptyList(),
    val enableStt: Boolean = false,
    val hideAnswerText: Boolean = false,
    val attemptedCardIds: List<String> = emptyList(),
    val fingersAndToes: Boolean = false,
    val hangmanMistakes: Int = 0,
    val guessedLetters: Set<Char> = emptySet(),
    // Memory Mode Fields
    val maxMemoryTiles: Int = 20,
    // Active cards currently on the grid
    val memoryActiveCardIds: List<String> = emptyList(),
    // Current Selections
    val memorySelected1: Pair<String, String>? = null, // ID, Side
    val memorySelected2: Pair<String, String>? = null,  // ID, Side
    val memoryConsecutiveWrongSideTaps: Int = 0,
    // --- NEW: Crossword State ---
    val crosswordWords: List<CrosswordWord> = emptyList(),
    val crosswordUserInputs: Map<String, Char> = emptyMap(), // Key is "x,y"
    val crosswordGridWidth: Int = 0,
    val crosswordGridHeight: Int = 0,
    val crosswordSelectedWordId: String? = null,
    val crosswordSelectedCell: Pair<Int, Int>? = null,
    val showCorrectWords: Boolean = true,
    val completedWordIds: Set<String> = emptySet()
)