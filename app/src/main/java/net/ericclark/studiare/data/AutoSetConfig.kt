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
    val audioPlaybackSpeed: Float = ModeOptionDefaults.AUDIO_PLAYBACK_SPEED,
    val audioReplayCount: Int = ModeOptionDefaults.AUDIO_REPLAY_COUNT,
    val audioAutoAdvance: Boolean = ModeOptionDefaults.AUDIO_AUTO_ADVANCE,
    val audioAnswerDelaySeconds: Double = ModeOptionDefaults.AUDIO_ANSWER_DELAY_SECONDS,
    val audioNextCardDelaySeconds: Double = ModeOptionDefaults.AUDIO_NEXT_CARD_DELAY_SECONDS,
    val freeformShowBothSides: Boolean = ModeOptionDefaults.FREEFORM_SHOW_BOTH_SIDES,
    val freeformSwipeNavigation: Boolean = ModeOptionDefaults.FREEFORM_SWIPE_NAVIGATION,
    val typingIgnoreFormatting: Boolean = ModeOptionDefaults.TYPING_IGNORE_FORMATTING,
    val typingAutoSubmit: Boolean = ModeOptionDefaults.TYPING_AUTO_SUBMIT,
    val typingDisableAutocorrect: Boolean = ModeOptionDefaults.TYPING_DISABLE_AUTOCORRECT,
    val typingShowLengthHint: Boolean = ModeOptionDefaults.TYPING_SHOW_LENGTH_HINT,
    val flashcardAutoFlipSeconds: Int = ModeOptionDefaults.FLASHCARD_AUTO_FLIP_SECONDS,
    val flashcardDoubleTapToFlip: Boolean = ModeOptionDefaults.FLASHCARD_DOUBLE_TAP_TO_FLIP,
    val flashcardRandomizeFirstSide: Boolean = ModeOptionDefaults.FLASHCARD_RANDOMIZE_FIRST_SIDE,
    val requireConfirmTap: Boolean = ModeOptionDefaults.REQUIRE_CONFIRM_TAP,
    val autoAdvanceAfterCorrect: Boolean = ModeOptionDefaults.AUTO_ADVANCE_AFTER_CORRECT,
    val autoAdvanceDelaySeconds: Double = ModeOptionDefaults.AUTO_ADVANCE_DELAY_SECONDS,
    val autoListen: Boolean = ModeOptionDefaults.AUTO_LISTEN,
    val speakingFrontSpeed: Float = ModeOptionDefaults.SPEAKING_FRONT_SPEED,
    val speakingBackSpeed: Float = ModeOptionDefaults.SPEAKING_BACK_SPEED,
    val listResetPosition: Boolean = ModeOptionDefaults.LIST_RESET_POSITION,
    val listDimWrongGuesses: Boolean = ModeOptionDefaults.LIST_DIM_WRONG_GUESSES,
    val listRemoveGuessed: Boolean = ModeOptionDefaults.LIST_REMOVE_GUESSED,
    val matchingHighlightStyle: String = ModeOptionDefaults.MATCHING_HIGHLIGHT_STYLE,
    val matchingWrongDelayMs: Int = ModeOptionDefaults.MATCHING_WRONG_DELAY_MS,
    val matchingShowCorrectDialog: Boolean = ModeOptionDefaults.MATCHING_SHOW_CORRECT_DIALOG,
    val anagramFirstLetterHint: Boolean = ModeOptionDefaults.ANAGRAM_FIRST_LETTER_HINT,
    val anagramUppercase: Boolean = ModeOptionDefaults.ANAGRAM_UPPERCASE,
    val anagramColorVowels: Boolean = ModeOptionDefaults.ANAGRAM_COLOR_VOWELS,
    val crosswordHighlightWord: Boolean = ModeOptionDefaults.CROSSWORD_HIGHLIGHT_WORD,
    val crosswordAutoAdvanceCell: Boolean = ModeOptionDefaults.CROSSWORD_AUTO_ADVANCE_CELL,
    val crosswordCompactClues: Boolean = ModeOptionDefaults.CROSSWORD_COMPACT_CLUES,
    val crosswordFeedbackMode: String = ModeOptionDefaults.CROSSWORD_FEEDBACK_MODE,
    val hangmanMaxMistakes: Int = ModeOptionDefaults.HANGMAN_MAX_MISTAKES,
    val hangmanRevealSpeedMs: Int = ModeOptionDefaults.HANGMAN_REVEAL_SPEED_MS,
    val hangmanHideVisual: Boolean = ModeOptionDefaults.HANGMAN_HIDE_VISUAL,
    val memoryFlipAnimation: Boolean = ModeOptionDefaults.MEMORY_FLIP_ANIMATION,
    val memoryGrayMatched: Boolean = ModeOptionDefaults.MEMORY_GRAY_MATCHED,
    val memoryPeekSeconds: Int = ModeOptionDefaults.MEMORY_PEEK_SECONDS,
    val memoryWrongPairMs: Int = ModeOptionDefaults.MEMORY_WRONG_PAIR_MS,
    val wordSearchHideFound: Boolean = ModeOptionDefaults.WORD_SEARCH_HIDE_FOUND,
    val wordSearchHighlightColor: Int = ModeOptionDefaults.WORD_SEARCH_HIGHLIGHT_COLOR,

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