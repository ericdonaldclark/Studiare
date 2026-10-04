package net.ericclark.studiare.data

data class AutoSetConfig(
    val mode: AutoSetCreationMode,          // "One", "Multiple", "Split All"
    val numSets: Int,          // Used for "Multiple"
    val maxCardsPerSet: Int,
    val selectionMode: SelectionMode, // "Any", "Tags", "Difficulty"
    val selectedTags: List<String>,
    val selectedDifficulties: List<Int>,
    val excludeKnown: Boolean,

    // --- NEW FILTERS ---
    val includeSuspended: Boolean = false, // Default to FALSE to exclude suspended cards
    val selectedFlags: List<Int> = emptyList(), // Empty means "Any" (ignore flag filter)
    val difficultyCounts: Map<Int, Int>? = null, // Difficulty weighting: how many cards of each 1-5 difficulty to take; null = off
    // Audio mode options (other modes ignore these)
    val audioPlaybackSpeed: Float = 1f,
    val audioReplayCount: Int = 1,
    val audioAutoAdvance: Boolean = true,
    val audioAnswerDelaySeconds: Double = 2.0,
    val audioNextCardDelaySeconds: Double = 2.0,
    val freeformShowBothSides: Boolean = true,
    val freeformSwipeNavigation: Boolean = true,
    val typingIgnoreFormatting: Boolean = true,
    val typingAutoSubmit: Boolean = false,
    val typingDisableAutocorrect: Boolean = true,
    val typingShowLengthHint: Boolean = true,
    val flashcardAutoFlipSeconds: Int = 0,
    val flashcardDoubleTapToFlip: Boolean = false,
    val flashcardRandomizeFirstSide: Boolean = false,
    val requireConfirmTap: Boolean = false,
    val autoAdvanceAfterCorrect: Boolean = false,
    val autoAdvanceDelaySeconds: Double = 1.0,
    val autoListen: Boolean = false,
    val speakingFrontSpeed: Float = 1f,
    val speakingBackSpeed: Float = 1f,
    val listResetPosition: Boolean = false,
    val listDimWrongGuesses: Boolean = false,
    val listRemoveGuessed: Boolean = false,
    val matchingHighlightStyle: String = "FILL",
    val matchingWrongDelayMs: Int = 1000,
    val matchingShowCorrectDialog: Boolean = false,
    val anagramFirstLetterHint: Boolean = false,
    val anagramUppercase: Boolean = true,
    val anagramColorVowels: Boolean = false,
    val crosswordHighlightWord: Boolean = true,
    val crosswordAutoAdvanceCell: Boolean = false,
    val crosswordCompactClues: Boolean = false,
    val crosswordFeedbackMode: String = "LETTER",
    val hangmanMaxMistakes: Int = 7,
    val hangmanRevealSpeedMs: Int = 300,
    val hangmanHideVisual: Boolean = false,
    val memoryFlipAnimation: Boolean = true,
    val memoryGrayMatched: Boolean = false,
    val memoryPeekSeconds: Int = 0,
    val memoryWrongPairMs: Int = 0,
    val wordSearchHideFound: Boolean = false,
    val wordSearchHighlightColor: Int = -14498466,

    val sortMode: SortMode,      // "Random", "Alphabetical", "Review Date", etc.
    val sortDirection: Direction, // "ASC", "DESC"
    val sortSide: CardSide,       // "Front", "Back"
    val alphabetStart: String = "A",
    val alphabetEnd: String = "Z",
    val filterSide: CardSide = CardSide.FRONT,
    val cardOrderStart: Int = 1,
    val cardOrderEnd: Int = 100,
    val timeValue: Int = 7,
    val timeUnit: TimeUnit = TimeUnit.DAYS,
    val filterType: FilterType = FilterType.INCLUDE, // "Include" or "Exclude"
    val reviewCountThreshold: Int = 0,
    val reviewCountDirection: Direction = Direction.ASC, // "Minimum" (>=) or "Maximum" (<=)
    val scoreThreshold: Int = 0, // 0-100
    val scoreDirection: Direction = Direction.DESC, // = "Minimum",
    val schedulingMode: SchedulingMode = SchedulingMode.NORMAL // "Normal" or "Spaced Repetition"
)