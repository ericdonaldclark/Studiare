package net.ericclark.studiare.data

data class StudyState(
    val sessionId: String,
    val deckWithCards: DeckWithCards,
    val studyMode: SessionMode,
    // Mirrors ActiveSession.startedCategory - see its own doc comment.
    val startedCategory: StudyCategory = StudyCategory.PRACTICE,
    val schedulingMode: SchedulingMode = SchedulingMode.NORMAL,
    val nextIntervals: Map<Int, String> = emptyMap(),
    val isWeighted: Boolean,
    val shuffledCards: List<Card>,
    val quizPromptSide: CardSide = CardSide.FRONT,
    /** Cached once-per-session result of [computeAutoByDeckSide], used to resolve [quizPromptSide] when it's [CardSide.AUTO_BY_DECK]. */
    val resolvedAutoByDeckSide: CardSide = CardSide.FRONT,
    val currentCardIndex: Int = 0,
    val furthestCardIndex: Int = 0,
    val wrongSelections: List<String> = emptyList(),
    val correctAnswerFound: Boolean = false,
    val showFront: Boolean = true,
    val isFlipped: Boolean = false,
    val isComplete: Boolean = false,
    val firstTryCorrectCount: Int = 0,
    val hasAttempted: Boolean = false,
    val lastIncorrectAnswer: String? = null,
    val numberOfAnswers: Int = ModeOptionDefaults.NUMBER_OF_ANSWERS,
    val showCorrectLetters: Boolean = ModeOptionDefaults.SHOW_CORRECT_LETTERS,
    val limitAnswerPool: Boolean = true,
    val difficulties: List<Int> = emptyList(),
    val cardOrder: SortMode = SortMode.RANDOM,
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
    val attemptedCardIds: List<String> = emptyList(),
    val hangmanMistakes: Int = 0,
    val guessedLetters: Set<Char> = emptySet(),
    // Memory Mode Fields
    val maxMemoryTiles: Int = ModeOptionDefaults.MAX_MEMORY_TILES,
    // Audio mode
    val audioPlaybackSpeed: Float = ModeOptionDefaults.AUDIO_PLAYBACK_SPEED,
    val audioReplayCount: Int = ModeOptionDefaults.AUDIO_REPLAY_COUNT,
    val audioAutoAdvance: Boolean = ModeOptionDefaults.AUDIO_AUTO_ADVANCE,
    val audioAnswerDelaySeconds: Double = ModeOptionDefaults.AUDIO_ANSWER_DELAY_SECONDS,
    val audioNextCardDelaySeconds: Double = ModeOptionDefaults.AUDIO_NEXT_CARD_DELAY_SECONDS,
    val gridDensity: Int = ModeOptionDefaults.GRID_DENSITY,
    val freeformShowBothSides: Boolean = ModeOptionDefaults.FREEFORM_SHOW_BOTH_SIDES,
    val freeformSwipeNavigation: Int = ModeOptionDefaults.FREEFORM_SWIPE_NAVIGATION,
    val typingIgnoreFormatting: Boolean = ModeOptionDefaults.TYPING_IGNORE_FORMATTING,
    val typingDisableAutocorrect: Boolean = ModeOptionDefaults.TYPING_DISABLE_AUTOCORRECT,
    val typingShowLengthHint: Boolean = ModeOptionDefaults.TYPING_SHOW_LENGTH_HINT,
    val typedListenUseMultipleChoice: Boolean = ModeOptionDefaults.TYPED_LISTEN_USE_MULTIPLE_CHOICE,
    val hearMatchAutoPlayAllOptions: Boolean = ModeOptionDefaults.HEAR_MATCH_AUTO_PLAY_ALL_OPTIONS,
    val hideDifficultySelection: Boolean = ModeOptionDefaults.HIDE_DIFFICULTY_SELECTION,
    val continuousSession: Boolean = ModeOptionDefaults.CONTINUOUS_SESSION,
    val flashcardAutoFlipSeconds: Int = ModeOptionDefaults.FLASHCARD_AUTO_FLIP_SECONDS,
    val flashcardAutoFlip: Boolean = ModeOptionDefaults.FLASHCARD_AUTO_FLIP,
    val flashcardDoubleTapToFlip: Boolean = ModeOptionDefaults.FLASHCARD_DOUBLE_TAP_TO_FLIP,
    val flashcardRandomizeFirstSide: Boolean = ModeOptionDefaults.FLASHCARD_RANDOMIZE_FIRST_SIDE,
    val requireConfirmTap: Boolean = ModeOptionDefaults.REQUIRE_CONFIRM_TAP,
    val autoAdvance: Boolean = ModeOptionDefaults.AUTO_ADVANCE,
    val autoAdvanceDelaySeconds: Double = ModeOptionDefaults.AUTO_ADVANCE_DELAY_SECONDS,
    val autoListen: Boolean = ModeOptionDefaults.AUTO_LISTEN,
    val listenStartSound: Boolean = ModeOptionDefaults.LISTEN_START_SOUND,
    val listenCorrectSound: Boolean = ModeOptionDefaults.LISTEN_CORRECT_SOUND,
    val listenIncorrectSound: Boolean = ModeOptionDefaults.LISTEN_INCORRECT_SOUND,
    val speakingFrontSpeed: Float = ModeOptionDefaults.SPEAKING_FRONT_SPEED,
    val speakingBackSpeed: Float = ModeOptionDefaults.SPEAKING_BACK_SPEED,
    val listResetPosition: Boolean = ModeOptionDefaults.LIST_RESET_POSITION,
    val listDimWrongGuesses: Boolean = ModeOptionDefaults.LIST_DIM_WRONG_GUESSES,
    val listRemoveGuessed: Boolean = ModeOptionDefaults.LIST_REMOVE_GUESSED,
    val matchingHighlightStyle: String = ModeOptionDefaults.MATCHING_HIGHLIGHT_STYLE,
    val matchingWrongDelayMs: Int = ModeOptionDefaults.MATCHING_WRONG_DELAY_MS,
    val matchingCorrectHighlightMs: Int = ModeOptionDefaults.MATCHING_CORRECT_HIGHLIGHT_MS,
    val matchingShowCorrectDialog: Boolean = ModeOptionDefaults.MATCHING_SHOW_CORRECT_DIALOG,
    val anagramFirstLetterHint: Boolean = ModeOptionDefaults.ANAGRAM_FIRST_LETTER_HINT,
    val anagramUppercase: Boolean = ModeOptionDefaults.ANAGRAM_UPPERCASE,
    val anagramColorVowels: Boolean = ModeOptionDefaults.ANAGRAM_COLOR_VOWELS,
    val crosswordHighlightWord: Boolean = ModeOptionDefaults.CROSSWORD_HIGHLIGHT_WORD,
    val crosswordAutoAdvanceCell: Boolean = ModeOptionDefaults.CROSSWORD_AUTO_ADVANCE_CELL,
    val crosswordCompactClues: Boolean = ModeOptionDefaults.CROSSWORD_COMPACT_CLUES,
    val hangmanMaxMistakes: Int = ModeOptionDefaults.HANGMAN_MAX_MISTAKES,
    val hangmanHideVisual: Boolean = ModeOptionDefaults.HANGMAN_HIDE_VISUAL,
    val memoryGrayMatched: Boolean = ModeOptionDefaults.MEMORY_GRAY_MATCHED,
    val memoryPeekSeconds: Int = ModeOptionDefaults.MEMORY_PEEK_SECONDS,
    val memoryGridColumnsPortrait: Int = ModeOptionDefaults.MEMORY_GRID_COLUMNS_PORTRAIT,
    val memoryGridColumnsLandscape: Int = ModeOptionDefaults.MEMORY_GRID_COLUMNS_LANDSCAPE,
    val memoryWrongPairMs: Int = ModeOptionDefaults.MEMORY_WRONG_PAIR_MS,
    val memoryCorrectPairMs: Int = ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS,
    val memorySubmitAnswer: Boolean = ModeOptionDefaults.MEMORY_SUBMIT_ANSWER,
    val wordSearchHideFound: Boolean = ModeOptionDefaults.WORD_SEARCH_HIDE_FOUND,
    val wordSearchHighlightColor: Int = ModeOptionDefaults.WORD_SEARCH_HIGHLIGHT_COLOR,
    // Active cards currently on the grid
    val memoryActiveCardIds: List<String> = emptyList(),
    // Current Selections
    val memorySelected1: Pair<String, CardSide>? = null, // ID, Side
    val memorySelected2: Pair<String, CardSide>? = null,  // ID, Side
    val memoryConsecutiveWrongSideTaps: Int = 0,
    // --- NEW: Crossword State ---
    val crosswordWords: List<CrosswordWord> = emptyList(),
    val crosswordUserInputs: Map<String, Char> = emptyMap(), // Key is "x,y"
    val crosswordGridWidth: Int = 0,
    val crosswordGridHeight: Int = 0,
    val crosswordSelectedWordId: String? = null,
    val crosswordSelectedCell: Pair<Int, Int>? = null,
    val showCorrectWords: Boolean = ModeOptionDefaults.SHOW_CORRECT_WORDS,
    val completedWordIds: Set<String> = emptySet(),
    val freeformLayoutVertical: Boolean = ModeOptionDefaults.FREEFORM_LAYOUT_VERTICAL,
    val linkageSettings: LinkageSettings = LinkageSettings(),
    // Word Search State
    val wordSearchWords: List<net.ericclark.studiare.data.WordSearchWord> = emptyList(),
    val wordSearchGrid: List<List<Char>> = emptyList(),
    val wordSearchGridWidth: Int = 0,
    val wordSearchGridHeight: Int = 0,
    val wordSearchFoundWordIds: Set<String> = emptySet(),
    // --- Time Tracking ---
    // Pause-safe elapsed time: accumulatedTimeSeconds is everything already banked from previous
    // runs (earlier resumes, or before the app was last backgrounded); sessionResumedAt is when
    // the clock most recently started running again. Reading "now" requires both, since a plain
    // `now - createdAt` would count backgrounded/paused time as if the user were still studying.
    val accumulatedTimeSeconds: Long = 0,
    val sessionResumedAt: Long = System.currentTimeMillis(),

    // --- Timed Sessions ---
    val timedSessionEnabled: Boolean = false,
    val timedSessionDurationSeconds: Int = 0,
    val timedSessionPauseOutsideSession: Boolean = false,
    val timedSessionDisplayUpdateSecondsUnder60: Int = 0,
    val timedSessionDisplayUpdateSecondsOver60: Int = 0,
    // True only when StudySessionManager force-completed this session because its timer ran out,
    // rather than the user finishing every card normally - lets the completion screen show a
    // different header ("Time's Up!" instead of "Congratulations!").
    val endedByTimeout: Boolean = false,
    // The countdown's own pause-capable clock - see ActiveSession's matching fields' doc comment
    // for why this is separate from accumulatedTimeSeconds/sessionResumedAt above.
    val timedSessionAccumulatedSeconds: Long = 0,
    val timedSessionResumedAt: Long = System.currentTimeMillis()
)

/** Total time spent in this session so far, including whatever's run since [StudyState.sessionResumedAt]
 * (frozen at 0 additional once [StudyState.isComplete], since [net.ericclark.studiare.components.StudySessionManager]
 * folds the final run into [StudyState.accumulatedTimeSeconds] the moment a session completes). */
fun StudyState.elapsedSeconds(): Long =
    accumulatedTimeSeconds + if (isComplete) 0 else (System.currentTimeMillis() - sessionResumedAt) / 1000

/**
 * The time shown on the completion screen - [elapsedSeconds] for an ordinary finish, but exactly
 * [StudyState.timedSessionDurationSeconds] when the session ended by timeout. A timed-out session
 * lasted precisely as long as its timer, by definition, regardless of how much of that time was
 * spent paused, backgrounded, or away in another screen - [elapsedSeconds]' own wall-clock
 * pause/resume bookkeeping (accurate for ordinary sessions) has no bearing on that fixed number.
 */
fun StudyState.completionTimeSeconds(): Long = if (endedByTimeout) timedSessionDurationSeconds.toLong() else elapsedSeconds()

/** Seconds left on this session's timer, or null if Timed Session isn't on. Never negative - once
 * it would go below 0, [net.ericclark.studiare.components.StudySessionManager] has already force-completed
 * the session. Uses the timer's own clock, not [elapsedSeconds] - when
 * [StudyState.timedSessionPauseOutsideSession] is off, that clock never pauses, so this keeps
 * counting down in real time even while the app is backgrounded. */
fun StudyState.timedSessionRemainingSeconds(): Long? {
    if (!timedSessionEnabled) return null
    val timerElapsed = timedSessionAccumulatedSeconds + if (isComplete) 0 else (System.currentTimeMillis() - timedSessionResumedAt) / 1000
    return (timedSessionDurationSeconds - timerElapsed).coerceAtLeast(0)
}

/**
 * How long a live Timed Session display (the app bar pill, [net.ericclark.studiare.TimedSessionNotificationService])
 * should wait before its next redraw, given [remainingSeconds] left and the two configured
 * cadences. The last 5 seconds always update every second regardless of either setting, so the
 * final countdown never looks frozen.
 */
fun timedSessionTickDelaySeconds(remainingSeconds: Long, displayFrequencyUnder60: Int, displayFrequencyOver60: Int): Long {
    if (remainingSeconds <= 5) return 1L
    val freq = (if (remainingSeconds <= 60) displayFrequencyUnder60 else displayFrequencyOver60).toLong().coerceAtLeast(1)
    // Never let one tick jump straight past the always-every-second final 5 seconds (e.g. 12 -> 7 -> 2
    // would skip 6/5/4/3) - clamp so the tick right before entering that window lands exactly on 5.
    return freq.coerceAtMost(remainingSeconds - 5)
}

/** "H:MM:SS" once an hour or more is left, otherwise "M:SS" - the clock-style format used by the
 *  Timed Session app bar pill and background notification (distinct from [formatElapsedTime]'s
 *  single-dominant-unit style, which fits a completion-screen badge better than a live countdown). */
fun formatCountdown(totalSeconds: Long): String {
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds) else "%d:%02d".format(minutes, seconds)
}

/** The options a running session lets the user change from its settings button, read back from this state. */
fun StudyState.sessionOptions(): ModeDefaultSettings = ModeDefaultSettings(
    numberOfAnswers = numberOfAnswers,
    showCorrectLetters = showCorrectLetters,
    maxMemoryTiles = maxMemoryTiles,
    gridDensity = gridDensity,
    freeformLayoutVertical = freeformLayoutVertical,
    quizPromptSide = quizPromptSide,
    audioPlaybackSpeed = audioPlaybackSpeed,
    audioReplayCount = audioReplayCount,
    audioAutoAdvance = audioAutoAdvance,
    audioAnswerDelaySeconds = audioAnswerDelaySeconds,
    audioNextCardDelaySeconds = audioNextCardDelaySeconds,
    freeformShowBothSides = freeformShowBothSides,
    freeformSwipeNavigation = freeformSwipeNavigation,
    typingIgnoreFormatting = typingIgnoreFormatting,
    typingDisableAutocorrect = typingDisableAutocorrect,
    typingShowLengthHint = typingShowLengthHint,
    typedListenUseMultipleChoice = typedListenUseMultipleChoice,
    hearMatchAutoPlayAllOptions = hearMatchAutoPlayAllOptions,
    hideDifficultySelection = hideDifficultySelection,
    continuousSession = continuousSession,
    flashcardAutoFlipSeconds = flashcardAutoFlipSeconds,
    flashcardAutoFlip = flashcardAutoFlip,
    flashcardDoubleTapToFlip = flashcardDoubleTapToFlip,
    flashcardRandomizeFirstSide = flashcardRandomizeFirstSide,
    requireConfirmTap = requireConfirmTap,
    allowMultipleGuesses = allowMultipleGuesses,
    autoAdvance = autoAdvance,
    autoAdvanceDelaySeconds = autoAdvanceDelaySeconds,
    autoListen = autoListen,
    listenStartSound = listenStartSound,
    listenCorrectSound = listenCorrectSound,
    listenIncorrectSound = listenIncorrectSound,
    speakingFrontSpeed = speakingFrontSpeed,
    speakingBackSpeed = speakingBackSpeed,
    listResetPosition = listResetPosition,
    listDimWrongGuesses = listDimWrongGuesses,
    listRemoveGuessed = listRemoveGuessed,
    matchingHighlightStyle = matchingHighlightStyle,
    matchingWrongDelayMs = matchingWrongDelayMs,
    matchingCorrectHighlightMs = matchingCorrectHighlightMs,
    matchingShowCorrectDialog = matchingShowCorrectDialog,
    anagramFirstLetterHint = anagramFirstLetterHint,
    anagramUppercase = anagramUppercase,
    anagramColorVowels = anagramColorVowels,
    crosswordHighlightWord = crosswordHighlightWord,
    crosswordAutoAdvanceCell = crosswordAutoAdvanceCell,
    crosswordCompactClues = crosswordCompactClues,
    hangmanMaxMistakes = hangmanMaxMistakes,
    hangmanHideVisual = hangmanHideVisual,
    memoryGrayMatched = memoryGrayMatched,
    memoryPeekSeconds = memoryPeekSeconds,
    memoryGridColumnsPortrait = memoryGridColumnsPortrait,
    memoryGridColumnsLandscape = memoryGridColumnsLandscape,
    memoryWrongPairMs = memoryWrongPairMs,
    memoryCorrectPairMs = memoryCorrectPairMs,
    memorySubmitAnswer = memorySubmitAnswer,
    wordSearchHideFound = wordSearchHideFound,
    wordSearchHighlightColor = wordSearchHighlightColor
)

