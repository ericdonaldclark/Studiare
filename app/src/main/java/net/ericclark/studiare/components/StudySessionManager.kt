package net.ericclark.studiare.components

import net.ericclark.studiare.data.*
import net.ericclark.studiare.screens.studyRouteFor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.collections.iterator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

class StudySessionManager(
    private val cardUtils: CardUtils,
    private val viewModelScope: CoroutineScope,
    private val getStudyState: () -> StudyState?,
    private val setStudyState: (StudyState?) -> Unit,
    private val getAllDecks: () -> List<DeckWithCards>,
    private val getAllActiveSessions: () -> List<ActiveSession>,
    private val onToastMessage: (String?) -> Unit,
    private val saveCard: (Card) -> Unit,
    private val saveDeck: (Deck) -> Unit,
    private val saveSession: (ActiveSession) -> Unit,
    private val deleteSessionById: (String) -> Unit
) {
    // --- Session Persistence & Controls ---

    private fun updateAndSaveStudyState(newState: StudyState?) {
        var stateToProcess = newState

        if (stateToProcess != null && stateToProcess.currentCardIndex > stateToProcess.furthestCardIndex) {
            stateToProcess = stateToProcess.copy(furthestCardIndex = stateToProcess.currentCardIndex)
        }

        if (stateToProcess != null && stateToProcess.schedulingMode == SchedulingMode.FSRS) {
            val currentCard = stateToProcess.shuffledCards.getOrNull(stateToProcess.currentCardIndex)
            if (currentCard != null) {
                val intervals = calculateFSRSIntervals(currentCard, stateToProcess.deckWithCards.deck)
                stateToProcess = stateToProcess.copy(nextIntervals = intervals)
            }
        }

        setStudyState(stateToProcess)
        if (stateToProcess == null) return

        val currentSessions = getAllActiveSessions()
        val updatedSession = currentSessions.find { it.id == stateToProcess.sessionId }?.copy(
            currentCardIndex = stateToProcess.currentCardIndex,
            shuffledCardIds = stateToProcess.shuffledCards.map { it.id },
            furthestCardIndex = stateToProcess.furthestCardIndex,
            wrongSelections = stateToProcess.wrongSelections,
            correctAnswerFound = stateToProcess.correctAnswerFound,
            showQuestion = stateToProcess.showFront,
            isFlipped = stateToProcess.isFlipped,
            firstTryCorrectCount = stateToProcess.firstTryCorrectCount,
            hasAttempted = stateToProcess.hasAttempted,
            lastAccessed = System.currentTimeMillis(),
            mcOptions = stateToProcess.mcOptions,
            pickerOptions = stateToProcess.pickerOptions,
            matchingCardIdsOnScreen = stateToProcess.matchingCardsOnScreen.map { it.id },
            matchedPairs = stateToProcess.successfullyMatchedPairs,
            incorrectCardIds = stateToProcess.incorrectCardIds,
            isGraded = stateToProcess.isGraded,
            allowMultipleGuesses = stateToProcess.allowMultipleGuesses,
            enableStt = stateToProcess.enableStt,
            hideAnswerText = stateToProcess.hideAnswerText,
            attemptedCardIds = stateToProcess.attemptedCardIds,
            fingersAndToes = stateToProcess.fingersAndToes,
            maxMemoryTiles = stateToProcess.maxMemoryTiles,
            crosswordUserInputs = stateToProcess.crosswordUserInputs.mapValues { it.value.toString() },
            showCorrectWords = stateToProcess.showCorrectWords,
            numberOfAnswers = stateToProcess.numberOfAnswers,
            showCorrectLetters = stateToProcess.showCorrectLetters,
            quizPromptSide = stateToProcess.quizPromptSide,
            freeformLayoutVertical = stateToProcess.freeformLayoutVertical,
            gridDensity = stateToProcess.gridDensity,
            audioPlaybackSpeed = stateToProcess.audioPlaybackSpeed,
            audioReplayCount = stateToProcess.audioReplayCount,
            audioAutoAdvance = stateToProcess.audioAutoAdvance,
            audioAnswerDelaySeconds = stateToProcess.audioAnswerDelaySeconds,
            audioNextCardDelaySeconds = stateToProcess.audioNextCardDelaySeconds,
            freeformShowBothSides = stateToProcess.freeformShowBothSides,
            freeformSwipeNavigation = stateToProcess.freeformSwipeNavigation,
            typingIgnoreFormatting = stateToProcess.typingIgnoreFormatting,
            typingAutoSubmit = stateToProcess.typingAutoSubmit,
            typingDisableAutocorrect = stateToProcess.typingDisableAutocorrect,
            typingShowLengthHint = stateToProcess.typingShowLengthHint,
            flashcardAutoFlipSeconds = stateToProcess.flashcardAutoFlipSeconds,
            flashcardDoubleTapToFlip = stateToProcess.flashcardDoubleTapToFlip,
            flashcardRandomizeFirstSide = stateToProcess.flashcardRandomizeFirstSide,
            requireConfirmTap = stateToProcess.requireConfirmTap,
            autoAdvanceAfterCorrect = stateToProcess.autoAdvanceAfterCorrect,
            autoAdvanceDelaySeconds = stateToProcess.autoAdvanceDelaySeconds,
            autoListen = stateToProcess.autoListen,
            listenStartSound = stateToProcess.listenStartSound,
            listenCorrectSound = stateToProcess.listenCorrectSound,
            listenIncorrectSound = stateToProcess.listenIncorrectSound,
            speakingFrontSpeed = stateToProcess.speakingFrontSpeed,
            speakingBackSpeed = stateToProcess.speakingBackSpeed,
            listResetPosition = stateToProcess.listResetPosition,
            listDimWrongGuesses = stateToProcess.listDimWrongGuesses,
            listRemoveGuessed = stateToProcess.listRemoveGuessed,
            matchingHighlightStyle = stateToProcess.matchingHighlightStyle,
            matchingWrongDelayMs = stateToProcess.matchingWrongDelayMs,
            matchingCorrectHighlightMs = stateToProcess.matchingCorrectHighlightMs,
            matchingShowCorrectDialog = stateToProcess.matchingShowCorrectDialog,
            anagramFirstLetterHint = stateToProcess.anagramFirstLetterHint,
            anagramUppercase = stateToProcess.anagramUppercase,
            anagramColorVowels = stateToProcess.anagramColorVowels,
            crosswordHighlightWord = stateToProcess.crosswordHighlightWord,
            crosswordAutoAdvanceCell = stateToProcess.crosswordAutoAdvanceCell,
            crosswordCompactClues = stateToProcess.crosswordCompactClues,
            crosswordFeedbackMode = stateToProcess.crosswordFeedbackMode,
            hangmanMaxMistakes = stateToProcess.hangmanMaxMistakes,
            hangmanRevealSpeedMs = stateToProcess.hangmanRevealSpeedMs,
            hangmanHideVisual = stateToProcess.hangmanHideVisual,
            memoryFlipAnimation = stateToProcess.memoryFlipAnimation,
            memoryGrayMatched = stateToProcess.memoryGrayMatched,
            memoryPeekSeconds = stateToProcess.memoryPeekSeconds,
            memoryWrongPairMs = stateToProcess.memoryWrongPairMs,
            wordSearchHideFound = stateToProcess.wordSearchHideFound,
            wordSearchHighlightColor = stateToProcess.wordSearchHighlightColor,
            wordSearchWords = stateToProcess.wordSearchWords,
            wordSearchGrid = stateToProcess.wordSearchGrid.map { it.joinToString("") },
            wordSearchGridWidth = stateToProcess.wordSearchGridWidth,
            wordSearchGridHeight = stateToProcess.wordSearchGridHeight,
            wordSearchFoundWordIds = stateToProcess.wordSearchFoundWordIds
        )
        if (updatedSession != null) {
            saveSession(updatedSession)
        }
    }

    private fun calculateFSRSIntervals(card: Card, deck: Deck): Map<Int, String> {
        return mapOf(
            1 to formatInterval(FsrsAlgorithm.calculateNextState(card,  Rating.AGAIN, deck).scheduledDays),
            2 to formatInterval(FsrsAlgorithm.calculateNextState(card, Rating.HARD, deck).scheduledDays),
            3 to formatInterval(FsrsAlgorithm.calculateNextState(card, Rating.GOOD, deck).scheduledDays),
            4 to formatInterval(FsrsAlgorithm.calculateNextState(card, Rating.EASY, deck).scheduledDays)
        )
    }

    private fun formatInterval(days: Double): String {
        val minutes = days * 24 * 60
        return when {
            minutes < 1.0 -> "<1m"
            minutes < 60.0 -> "${minutes.roundToInt()}m"
            days < 1.0 -> "${(days * 24).roundToInt()}h"
            days < 30.0 -> "${days.roundToInt()}d"
            days < 365.0 -> "${(days / 30).roundToInt()}mo"
            else -> "${String.format("%.1f", days / 365)}y"
        }
    }

    fun deleteSession(sessionToDelete: ActiveSession) {
        deleteSessionById(sessionToDelete.id)
    }

    fun deleteCurrentStudySession() {
        getStudyState()?.let { state ->
            getAllActiveSessions().firstOrNull { it.id == state.sessionId }?.let { deleteSession(it) }
        }
    }

    fun copySession(session: ActiveSession) {
        val newSession = session.copy(
            id = UUID.randomUUID().toString(),
            currentCardIndex = 0,
            furthestCardIndex = 0,
            wrongSelections = emptyList(),
            correctAnswerFound = false,
            showQuestion = true,
            isFlipped = false,
            firstTryCorrectCount = 0,
            hasAttempted = false,
            createdAt = System.currentTimeMillis(),
            lastAccessed = System.currentTimeMillis(),
            mcOptions = emptyMap(),
            incorrectCardIds = emptyList(),
            wordSearchFoundWordIds = emptySet()
        )
        saveSession(newSession)
    }

    fun restartSession(session: ActiveSession) {
        val reset = session.copy(
            currentCardIndex = 0,
            furthestCardIndex = 0,
            wrongSelections = emptyList(),
            correctAnswerFound = false,
            showQuestion = true,
            isFlipped = false,
            firstTryCorrectCount = 0,
            hasAttempted = false,
            lastAccessed = System.currentTimeMillis(),
            mcOptions = emptyMap(),
            incorrectCardIds = emptyList(),
            wordSearchFoundWordIds = emptySet()
        )
        saveSession(reset)
    }

    fun resumeStudySession(session: ActiveSession) {
        val deck = getAllDecks().find { it.deck.id == session.deckId } ?: return
        val cardsInOrder = session.shuffledCardIds.mapNotNull { id -> deck.cards.find { it.id == id } }

        saveSession(session.copy(lastAccessed = System.currentTimeMillis()))

        val cwInputs = session.crosswordUserInputs.mapValues { it.value.first() }
        val completedIds = if (session.showCorrectWords) {
            session.crosswordWords.filter { word ->
                word.word.indices.all { i ->
                    val x = if (word.isAcross) word.startX + i else word.startX
                    val y = if (word.isAcross) word.startY else word.startY + i
                    cwInputs["$x,$y"] == word.word[i]
                }
            }.map { it.id }.toSet()
        } else emptySet()

        val sel1 = if (session.memorySelectedId1 != null) session.memorySelectedId1 to session.memorySelectedSide1!! else null
        val sel2 = if (session.memorySelectedId2 != null) session.memorySelectedId2 to session.memorySelectedSide2!! else null

        val newState = StudyState(
            sessionId = session.id,
            deckWithCards = deck,
            studyMode = session.mode,
            schedulingMode = session.schedulingMode,
            audioPlaybackSpeed = session.audioPlaybackSpeed,
            audioReplayCount = session.audioReplayCount,
            audioAutoAdvance = session.audioAutoAdvance,
            audioAnswerDelaySeconds = session.audioAnswerDelaySeconds,
            audioNextCardDelaySeconds = session.audioNextCardDelaySeconds,
            gridDensity = session.gridDensity,
            freeformShowBothSides = session.freeformShowBothSides,
            freeformSwipeNavigation = session.freeformSwipeNavigation,
            typingIgnoreFormatting = session.typingIgnoreFormatting,
            typingAutoSubmit = session.typingAutoSubmit,
            typingDisableAutocorrect = session.typingDisableAutocorrect,
            typingShowLengthHint = session.typingShowLengthHint,
            flashcardAutoFlipSeconds = session.flashcardAutoFlipSeconds,
            flashcardDoubleTapToFlip = session.flashcardDoubleTapToFlip,
            flashcardRandomizeFirstSide = session.flashcardRandomizeFirstSide,
            requireConfirmTap = session.requireConfirmTap,
            autoAdvanceAfterCorrect = session.autoAdvanceAfterCorrect,
            autoAdvanceDelaySeconds = session.autoAdvanceDelaySeconds,
            autoListen = session.autoListen,
            listenStartSound = session.listenStartSound,
            listenCorrectSound = session.listenCorrectSound,
            listenIncorrectSound = session.listenIncorrectSound,
            speakingFrontSpeed = session.speakingFrontSpeed,
            speakingBackSpeed = session.speakingBackSpeed,
            listResetPosition = session.listResetPosition,
            listDimWrongGuesses = session.listDimWrongGuesses,
            listRemoveGuessed = session.listRemoveGuessed,
            matchingHighlightStyle = session.matchingHighlightStyle,
            matchingWrongDelayMs = session.matchingWrongDelayMs,
            matchingCorrectHighlightMs = session.matchingCorrectHighlightMs,
            matchingShowCorrectDialog = session.matchingShowCorrectDialog,
            anagramFirstLetterHint = session.anagramFirstLetterHint,
            anagramUppercase = session.anagramUppercase,
            anagramColorVowels = session.anagramColorVowels,
            crosswordHighlightWord = session.crosswordHighlightWord,
            crosswordAutoAdvanceCell = session.crosswordAutoAdvanceCell,
            crosswordCompactClues = session.crosswordCompactClues,
            crosswordFeedbackMode = session.crosswordFeedbackMode,
            hangmanMaxMistakes = session.hangmanMaxMistakes,
            hangmanRevealSpeedMs = session.hangmanRevealSpeedMs,
            hangmanHideVisual = session.hangmanHideVisual,
            memoryFlipAnimation = session.memoryFlipAnimation,
            memoryGrayMatched = session.memoryGrayMatched,
            memoryPeekSeconds = session.memoryPeekSeconds,
            memoryWrongPairMs = session.memoryWrongPairMs,
            wordSearchHideFound = session.wordSearchHideFound,
            wordSearchHighlightColor = session.wordSearchHighlightColor,
            nextIntervals = if (session.schedulingMode == SchedulingMode.FSRS && cardsInOrder.isNotEmpty()) {
                calculateFSRSIntervals(cardsInOrder[session.currentCardIndex], deck.deck)
            } else emptyMap(),
            isWeighted = session.isWeighted,
            shuffledCards = cardsInOrder,
            quizPromptSide = session.quizPromptSide,
            currentCardIndex = session.currentCardIndex,
            furthestCardIndex = session.furthestCardIndex,
            wrongSelections = session.wrongSelections,
            correctAnswerFound = session.correctAnswerFound,
            showFront = session.showQuestion,
            isFlipped = session.isFlipped,
            firstTryCorrectCount = session.firstTryCorrectCount,
            hasAttempted = session.hasAttempted,
            numberOfAnswers = session.numberOfAnswers,
            showCorrectLetters = session.showCorrectLetters,
            limitAnswerPool = session.limitAnswerPool,
            difficulties = session.difficulties,
            cardOrder = session.cardOrder,
            mcOptions = session.mcOptions,
            pickerOptions = session.pickerOptions,
            matchingCardsOnScreen = session.matchingCardIdsOnScreen.mapNotNull { id -> deck.cards.find { it.id == id } },
            successfullyMatchedPairs = session.matchedPairs,
            incorrectCardIds = session.incorrectCardIds,
            isGraded = session.isGraded,
            allowMultipleGuesses = session.allowMultipleGuesses,
            enableStt = session.enableStt,
            hideAnswerText = session.hideAnswerText,
            attemptedCardIds = session.attemptedCardIds,
            fingersAndToes = session.fingersAndToes,
            hangmanMistakes = 0,
            guessedLetters = emptySet(),
            maxMemoryTiles = session.maxMemoryTiles,
            memorySelected1 = sel1,
            memorySelected2 = sel2,
            memoryConsecutiveWrongSideTaps = 0,
            crosswordWords = session.crosswordWords,
            crosswordGridWidth = session.crosswordGridWidth,
            crosswordGridHeight = session.crosswordGridHeight,
            crosswordUserInputs = cwInputs,
            crosswordSelectedWordId = session.crosswordWords.firstOrNull()?.id,
            crosswordSelectedCell = session.crosswordWords.firstOrNull()
                ?.let { it.startX to it.startY },
            showCorrectWords = session.showCorrectWords,
            completedWordIds = completedIds,
            freeformLayoutVertical = session.freeformLayoutVertical,
            wordSearchWords = session.wordSearchWords,
            wordSearchGrid = session.wordSearchGrid.map { it.toList() },
            wordSearchGridWidth = session.wordSearchGridWidth,
            wordSearchGridHeight = session.wordSearchGridHeight,
            wordSearchFoundWordIds = session.wordSearchFoundWordIds
        )
        setStudyState(newState)
    }

    fun endStudySession() { setStudyState(null) }

    /** Applies changes from the in-session settings button to the running session and saves them with it. */
    fun applySessionOptions(values: ModeDefaultSettings) {
        val state = getStudyState() ?: return
        updateAndSaveStudyState(state.copy(
            numberOfAnswers = values.numberOfAnswers ?: state.numberOfAnswers,
            showCorrectLetters = values.showCorrectLetters ?: state.showCorrectLetters,
            fingersAndToes = values.fingersAndToes ?: state.fingersAndToes,
            maxMemoryTiles = values.maxMemoryTiles ?: state.maxMemoryTiles,
            gridDensity = values.gridDensity ?: state.gridDensity,
            freeformLayoutVertical = values.freeformLayoutVertical ?: state.freeformLayoutVertical,
            quizPromptSide = values.quizPromptSide ?: state.quizPromptSide,
            audioPlaybackSpeed = values.audioPlaybackSpeed ?: state.audioPlaybackSpeed,
            audioReplayCount = values.audioReplayCount ?: state.audioReplayCount,
            audioAutoAdvance = values.audioAutoAdvance ?: state.audioAutoAdvance,
            audioAnswerDelaySeconds = values.audioAnswerDelaySeconds ?: state.audioAnswerDelaySeconds,
            audioNextCardDelaySeconds = values.audioNextCardDelaySeconds ?: state.audioNextCardDelaySeconds,
            freeformShowBothSides = values.freeformShowBothSides ?: state.freeformShowBothSides,
            freeformSwipeNavigation = values.freeformSwipeNavigation ?: state.freeformSwipeNavigation,
            typingIgnoreFormatting = values.typingIgnoreFormatting ?: state.typingIgnoreFormatting,
            typingAutoSubmit = values.typingAutoSubmit ?: state.typingAutoSubmit,
            typingDisableAutocorrect = values.typingDisableAutocorrect ?: state.typingDisableAutocorrect,
            typingShowLengthHint = values.typingShowLengthHint ?: state.typingShowLengthHint,
            flashcardAutoFlipSeconds = values.flashcardAutoFlipSeconds ?: state.flashcardAutoFlipSeconds,
            flashcardDoubleTapToFlip = values.flashcardDoubleTapToFlip ?: state.flashcardDoubleTapToFlip,
            flashcardRandomizeFirstSide = values.flashcardRandomizeFirstSide ?: state.flashcardRandomizeFirstSide,
            requireConfirmTap = values.requireConfirmTap ?: state.requireConfirmTap,
            autoAdvanceAfterCorrect = values.autoAdvanceAfterCorrect ?: state.autoAdvanceAfterCorrect,
            autoAdvanceDelaySeconds = values.autoAdvanceDelaySeconds ?: state.autoAdvanceDelaySeconds,
            autoListen = values.autoListen ?: state.autoListen,
            listenStartSound = values.listenStartSound ?: state.listenStartSound,
            listenCorrectSound = values.listenCorrectSound ?: state.listenCorrectSound,
            listenIncorrectSound = values.listenIncorrectSound ?: state.listenIncorrectSound,
            speakingFrontSpeed = values.speakingFrontSpeed ?: state.speakingFrontSpeed,
            speakingBackSpeed = values.speakingBackSpeed ?: state.speakingBackSpeed,
            listResetPosition = values.listResetPosition ?: state.listResetPosition,
            listDimWrongGuesses = values.listDimWrongGuesses ?: state.listDimWrongGuesses,
            listRemoveGuessed = values.listRemoveGuessed ?: state.listRemoveGuessed,
            matchingHighlightStyle = values.matchingHighlightStyle ?: state.matchingHighlightStyle,
            matchingWrongDelayMs = values.matchingWrongDelayMs ?: state.matchingWrongDelayMs,
            matchingCorrectHighlightMs = values.matchingCorrectHighlightMs ?: state.matchingCorrectHighlightMs,
            matchingShowCorrectDialog = values.matchingShowCorrectDialog ?: state.matchingShowCorrectDialog,
            anagramFirstLetterHint = values.anagramFirstLetterHint ?: state.anagramFirstLetterHint,
            anagramUppercase = values.anagramUppercase ?: state.anagramUppercase,
            anagramColorVowels = values.anagramColorVowels ?: state.anagramColorVowels,
            crosswordHighlightWord = values.crosswordHighlightWord ?: state.crosswordHighlightWord,
            crosswordAutoAdvanceCell = values.crosswordAutoAdvanceCell ?: state.crosswordAutoAdvanceCell,
            crosswordCompactClues = values.crosswordCompactClues ?: state.crosswordCompactClues,
            crosswordFeedbackMode = values.crosswordFeedbackMode ?: state.crosswordFeedbackMode,
            hangmanMaxMistakes = values.hangmanMaxMistakes ?: state.hangmanMaxMistakes,
            hangmanRevealSpeedMs = values.hangmanRevealSpeedMs ?: state.hangmanRevealSpeedMs,
            hangmanHideVisual = values.hangmanHideVisual ?: state.hangmanHideVisual,
            memoryFlipAnimation = values.memoryFlipAnimation ?: state.memoryFlipAnimation,
            memoryGrayMatched = values.memoryGrayMatched ?: state.memoryGrayMatched,
            memoryPeekSeconds = values.memoryPeekSeconds ?: state.memoryPeekSeconds,
            memoryWrongPairMs = values.memoryWrongPairMs ?: state.memoryWrongPairMs,
            wordSearchHideFound = values.wordSearchHideFound ?: state.wordSearchHideFound,
            wordSearchHighlightColor = values.wordSearchHighlightColor ?: state.wordSearchHighlightColor
        ))
    }

    fun startStudySession(
        parentDeck: DeckWithCards, mode: SessionMode, isWeighted: Boolean, numCards: Int, quizPromptSide: CardSide,
        numAnswers: Int, showCorrectLetters: Boolean, limitAnswerPool: Boolean, isGraded: Boolean,
        allowMultipleGuesses: Boolean, enableStt: Boolean, hideAnswerText: Boolean, fingersAndToes: Boolean,
        maxMemoryTiles: Int, gridDensity: Int, freeFormVerticalLayout: Boolean, config: AutoSetConfig, onSessionCreated: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var sessionCards = cardUtils.getFilteredAndSortedCards(parentDeck, config)

            if (config.schedulingMode == SchedulingMode.FSRS) {
                val now = System.currentTimeMillis()
                val oneDayMillis = 24 * 60 * 60 * 1000L

                sessionCards = sessionCards.filter { it.isDueForFsrs(now) }
            }

            // Difficulty weighting: keep only the first N cards of each difficulty (in the already-sorted
            // order), so the session holds exactly the requested mix. Filter keeps sort order intact.
            config.difficultyCounts?.let { counts ->
                val chosenIds = sessionCards.groupBy { it.difficulty.value }
                    .flatMap { (difficulty, cards) -> cards.take(counts[difficulty] ?: 0) }
                    .map { it.id }.toSet()
                sessionCards = sessionCards.filter { it.id in chosenIds }
            }
            val finalCards = sessionCards.take(min(numCards, sessionCards.size))

            if (finalCards.isEmpty()) {
                withContext(Dispatchers.Main) {
                    onToastMessage("No cards due for review!")
                }
                return@launch
            }

            val currentTime = System.currentTimeMillis()
            var cwWords: List<CrosswordWord> = emptyList()
            var cwWidth = 0
            var cwHeight = 0

            var wsWords: List<net.ericclark.studiare.data.WordSearchWord> = emptyList()
            var wsGrid: List<String> = emptyList()
            var wsWidth = 0
            var wsHeight = 0

            if (mode == SessionMode.CROSSWORD) {
                val (words, dim) = generateCrossword(finalCards, quizPromptSide, gridDensity)
                cwWords = words; cwWidth = dim.first; cwHeight = dim.second
            } else if (mode == SessionMode.WORD_SEARCH) {
                val (words, grid, width, height) = generateWordSearch(finalCards, quizPromptSide, gridDensity)
                wsWords = words; wsGrid = grid; wsWidth = width; wsHeight = height
            }
            val pickerOptions = if (mode == SessionMode.LIST) {
                val pickSide = if (quizPromptSide == CardSide.FRONT) CardSide.BACK else CardSide.FRONT
                parentDeck.cards.map { if (pickSide == CardSide.FRONT) it.front else it.back }.filter { it.isNotBlank() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            } else emptyList()

            val newSession = ActiveSession(
                id = UUID.randomUUID().toString(),
                deckId = parentDeck.deck.id,
                mode = mode,
                schedulingMode = config.schedulingMode,
                audioPlaybackSpeed = config.audioPlaybackSpeed,
                audioReplayCount = config.audioReplayCount,
                audioAutoAdvance = config.audioAutoAdvance,
                audioAnswerDelaySeconds = config.audioAnswerDelaySeconds,
                audioNextCardDelaySeconds = config.audioNextCardDelaySeconds,
                gridDensity = gridDensity,
                freeformShowBothSides = config.freeformShowBothSides,
                freeformSwipeNavigation = config.freeformSwipeNavigation,
                typingIgnoreFormatting = config.typingIgnoreFormatting,
                typingAutoSubmit = config.typingAutoSubmit,
                typingDisableAutocorrect = config.typingDisableAutocorrect,
                typingShowLengthHint = config.typingShowLengthHint,
                flashcardAutoFlipSeconds = config.flashcardAutoFlipSeconds,
                flashcardDoubleTapToFlip = config.flashcardDoubleTapToFlip,
                flashcardRandomizeFirstSide = config.flashcardRandomizeFirstSide,
                requireConfirmTap = config.requireConfirmTap,
                autoAdvanceAfterCorrect = config.autoAdvanceAfterCorrect,
                autoAdvanceDelaySeconds = config.autoAdvanceDelaySeconds,
                autoListen = config.autoListen,
                listenStartSound = config.listenStartSound,
                listenCorrectSound = config.listenCorrectSound,
                listenIncorrectSound = config.listenIncorrectSound,
                speakingFrontSpeed = config.speakingFrontSpeed,
                speakingBackSpeed = config.speakingBackSpeed,
                listResetPosition = config.listResetPosition,
                listDimWrongGuesses = config.listDimWrongGuesses,
                listRemoveGuessed = config.listRemoveGuessed,
                matchingHighlightStyle = config.matchingHighlightStyle,
                matchingWrongDelayMs = config.matchingWrongDelayMs,
                matchingCorrectHighlightMs = config.matchingCorrectHighlightMs,
                matchingShowCorrectDialog = config.matchingShowCorrectDialog,
                anagramFirstLetterHint = config.anagramFirstLetterHint,
                anagramUppercase = config.anagramUppercase,
                anagramColorVowels = config.anagramColorVowels,
                crosswordHighlightWord = config.crosswordHighlightWord,
                crosswordAutoAdvanceCell = config.crosswordAutoAdvanceCell,
                crosswordCompactClues = config.crosswordCompactClues,
                crosswordFeedbackMode = config.crosswordFeedbackMode,
                hangmanMaxMistakes = config.hangmanMaxMistakes,
                hangmanRevealSpeedMs = config.hangmanRevealSpeedMs,
                hangmanHideVisual = config.hangmanHideVisual,
                memoryFlipAnimation = config.memoryFlipAnimation,
                memoryGrayMatched = config.memoryGrayMatched,
                memoryPeekSeconds = config.memoryPeekSeconds,
                memoryWrongPairMs = config.memoryWrongPairMs,
                wordSearchHideFound = config.wordSearchHideFound,
                wordSearchHighlightColor = config.wordSearchHighlightColor,
                isWeighted = isWeighted,
                difficulties = config.selectedDifficulties,
                totalCards = finalCards.size,
                shuffledCardIds = finalCards.map { it.id },
                quizPromptSide = quizPromptSide, // Updated parameter mapping
                createdAt = currentTime,
                lastAccessed = currentTime,
                numberOfAnswers = numAnswers,
                showCorrectLetters = showCorrectLetters,
                limitAnswerPool = limitAnswerPool,
                cardOrder = config.sortMode,

                // --- ASSIGN THE SAVED CONFIGURATION ---
                selectionMode = config.selectionMode,
                selectedTags = config.selectedTags,
                excludeKnown = config.excludeKnown,
                sortDirection = config.sortDirection,
                sortSide = config.sortSide,
                alphabetStart = config.alphabetStart,
                alphabetEnd = config.alphabetEnd,
                filterSide = config.filterSide,
                cardOrderStart = config.cardOrderStart,
                cardOrderEnd = config.cardOrderEnd,
                timeValue = config.timeValue,
                timeUnit = config.timeUnit,
                filterType = config.filterType,
                reviewCountThreshold = config.reviewCountThreshold,
                reviewCountDirection = config.reviewCountDirection,
                scoreThreshold = config.scoreThreshold,
                scoreDirection = config.scoreDirection,
                // --------------------------------------

                pickerOptions = pickerOptions,
                isGraded = isGraded,
                allowMultipleGuesses = allowMultipleGuesses,
                enableStt = enableStt,
                hideAnswerText = hideAnswerText,
                attemptedCardIds = emptyList(),
                fingersAndToes = fingersAndToes,
                maxMemoryTiles = maxMemoryTiles,
                crosswordWords = cwWords,
                crosswordGridWidth = cwWidth,
                crosswordGridHeight = cwHeight,
                crosswordUserInputs = emptyMap(),
                showCorrectWords = true,
                freeformLayoutVertical = freeFormVerticalLayout,
                wordSearchWords = wsWords,
                wordSearchGrid = wsGrid,
                wordSearchGridWidth = wsWidth,
                wordSearchGridHeight = wsHeight,
                wordSearchFoundWordIds = emptySet()
            )

            // Save to Room DB
            saveSession(newSession)

            withContext(Dispatchers.Main) {
                setStudyState(
                    StudyState(
                        sessionId = newSession.id,
                        deckWithCards = parentDeck,
                        studyMode = mode,
                        schedulingMode = config.schedulingMode,
                        audioPlaybackSpeed = config.audioPlaybackSpeed,
                        audioReplayCount = config.audioReplayCount,
                        audioAutoAdvance = config.audioAutoAdvance,
                        audioAnswerDelaySeconds = config.audioAnswerDelaySeconds,
                        audioNextCardDelaySeconds = config.audioNextCardDelaySeconds,
                        gridDensity = gridDensity,
                        freeformShowBothSides = config.freeformShowBothSides,
                        freeformSwipeNavigation = config.freeformSwipeNavigation,
                        typingIgnoreFormatting = config.typingIgnoreFormatting,
                        typingAutoSubmit = config.typingAutoSubmit,
                        typingDisableAutocorrect = config.typingDisableAutocorrect,
                        typingShowLengthHint = config.typingShowLengthHint,
                        flashcardAutoFlipSeconds = config.flashcardAutoFlipSeconds,
                        flashcardDoubleTapToFlip = config.flashcardDoubleTapToFlip,
                        flashcardRandomizeFirstSide = config.flashcardRandomizeFirstSide,
                        requireConfirmTap = config.requireConfirmTap,
                        autoAdvanceAfterCorrect = config.autoAdvanceAfterCorrect,
                        autoAdvanceDelaySeconds = config.autoAdvanceDelaySeconds,
                        autoListen = config.autoListen,
                        listenStartSound = config.listenStartSound,
                        listenCorrectSound = config.listenCorrectSound,
                        listenIncorrectSound = config.listenIncorrectSound,
                        speakingFrontSpeed = config.speakingFrontSpeed,
                        speakingBackSpeed = config.speakingBackSpeed,
                        listResetPosition = config.listResetPosition,
                        listDimWrongGuesses = config.listDimWrongGuesses,
                        listRemoveGuessed = config.listRemoveGuessed,
                        matchingHighlightStyle = config.matchingHighlightStyle,
                        matchingWrongDelayMs = config.matchingWrongDelayMs,
                        matchingCorrectHighlightMs = config.matchingCorrectHighlightMs,
                        matchingShowCorrectDialog = config.matchingShowCorrectDialog,
                        anagramFirstLetterHint = config.anagramFirstLetterHint,
                        anagramUppercase = config.anagramUppercase,
                        anagramColorVowels = config.anagramColorVowels,
                        crosswordHighlightWord = config.crosswordHighlightWord,
                        crosswordAutoAdvanceCell = config.crosswordAutoAdvanceCell,
                        crosswordCompactClues = config.crosswordCompactClues,
                        crosswordFeedbackMode = config.crosswordFeedbackMode,
                        hangmanMaxMistakes = config.hangmanMaxMistakes,
                        hangmanRevealSpeedMs = config.hangmanRevealSpeedMs,
                        hangmanHideVisual = config.hangmanHideVisual,
                        memoryFlipAnimation = config.memoryFlipAnimation,
                        memoryGrayMatched = config.memoryGrayMatched,
                        memoryPeekSeconds = config.memoryPeekSeconds,
                        memoryWrongPairMs = config.memoryWrongPairMs,
                        wordSearchHideFound = config.wordSearchHideFound,
                        wordSearchHighlightColor = config.wordSearchHighlightColor,
                        nextIntervals = if (config.schedulingMode == SchedulingMode.FSRS && finalCards.isNotEmpty()) {
                            calculateFSRSIntervals(finalCards[0], parentDeck.deck)
                        } else emptyMap(),
                        isWeighted = isWeighted,
                        shuffledCards = finalCards,
                        quizPromptSide = quizPromptSide,
                        numberOfAnswers = numAnswers,
                        showCorrectLetters = showCorrectLetters,
                        limitAnswerPool = limitAnswerPool,
                        difficulties = config.selectedDifficulties,
                        cardOrder = config.sortMode,
                        pickerOptions = pickerOptions,
                        isGraded = isGraded,
                        allowMultipleGuesses = allowMultipleGuesses,
                        enableStt = enableStt,
                        hideAnswerText = hideAnswerText,
                        attemptedCardIds = emptyList(),
                        fingersAndToes = fingersAndToes,
                        maxMemoryTiles = maxMemoryTiles,
                        crosswordWords = cwWords,
                        crosswordGridWidth = cwWidth,
                        crosswordGridHeight = cwHeight,
                        crosswordUserInputs = emptyMap(),
                        crosswordSelectedWordId = cwWords.firstOrNull()?.id,
                        showCorrectWords = true,
                        completedWordIds = emptySet(),
                        freeformLayoutVertical = freeFormVerticalLayout,
                        wordSearchWords = wsWords,
                        wordSearchGrid = wsGrid.map { it.toList() },
                        wordSearchGridWidth = wsWidth,
                        wordSearchGridHeight = wsHeight,
                        wordSearchFoundWordIds = emptySet()
                    )
                )
                onSessionCreated()
            }
        }
    }

    fun submitFsrsGrade(rating: Int) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]
            val isCorrect = rating > 1
            processCardReview(card, isCorrect = isCorrect, isGraded = true, explicitRating = rating)

            val alreadyAttempted = state.attemptedCardIds.contains(card.id)
            val newAttempted = if (alreadyAttempted) state.attemptedCardIds else state.attemptedCardIds + card.id

            if (isCorrect) {
                val newScore = if (!alreadyAttempted) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount
                if (state.currentCardIndex < state.shuffledCards.size - 1) {
                    updateAndSaveStudyState(state.copy(
                        correctAnswerFound = false,
                        firstTryCorrectCount = newScore,
                        hasAttempted = false,
                        currentCardIndex = state.currentCardIndex + 1,
                        wrongSelections = emptyList(),
                        showFront = true,
                        isFlipped = false,
                        isCardRevealed = false,
                        attemptedCardIds = newAttempted
                    ))
                } else {
                    updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = newScore, isComplete = true, attemptedCardIds = newAttempted))
                }
            } else {
                val newIncorrect = (state.incorrectCardIds + card.id).distinct()
                if (state.currentCardIndex < state.shuffledCards.size - 1) {
                    updateAndSaveStudyState(state.copy(
                        hasAttempted = false,
                        correctAnswerFound = false,
                        incorrectCardIds = newIncorrect,
                        currentCardIndex = state.currentCardIndex + 1,
                        wrongSelections = emptyList(),
                        showFront = true,
                        isFlipped = false,
                        isCardRevealed = false,
                        attemptedCardIds = newAttempted
                    ))
                } else {
                    updateAndSaveStudyState(state.copy(hasAttempted = true, incorrectCardIds = newIncorrect, isComplete = true, attemptedCardIds = newAttempted))
                }
            }
        }
    }

    fun restartStudySession() {
        getStudyState()?.let { state ->
            val session = getAllActiveSessions().firstOrNull { it.id == state.sessionId } ?: return@let
            deleteSession(session)
            val config = AutoSetConfig(
                mode = AutoSetCreationMode.ONE,
                numSets = 1,
                maxCardsPerSet = session.totalCards,

                // --- PULL FILTERS DIRECTLY FROM THE SAVED SESSION ---
                selectionMode = session.selectionMode,
                selectedTags = session.selectedTags,
                selectedDifficulties = session.difficulties,
                excludeKnown = session.excludeKnown,
                includeSuspended = false,
                selectedFlags = emptyList(),
                sortMode = session.cardOrder,
                sortDirection = session.sortDirection,
                sortSide = session.sortSide,
                alphabetStart = session.alphabetStart,
                alphabetEnd = session.alphabetEnd,
                filterSide = session.filterSide,
                cardOrderStart = session.cardOrderStart,
                cardOrderEnd = session.cardOrderEnd,
                timeValue = session.timeValue,
                timeUnit = session.timeUnit,
                filterType = session.filterType,
                reviewCountThreshold = session.reviewCountThreshold,
                reviewCountDirection = session.reviewCountDirection,
                scoreThreshold = session.scoreThreshold,
                scoreDirection = session.scoreDirection,
                // ----------------------------------------------------
                // The exact per-difficulty mix is whatever the original session drew.
                difficultyCounts = if (session.isWeighted) difficultyMixOf(state.deckWithCards, session.shuffledCardIds) else null,

                schedulingMode = session.schedulingMode,
                audioPlaybackSpeed = session.audioPlaybackSpeed,
                audioReplayCount = session.audioReplayCount,
                audioAutoAdvance = session.audioAutoAdvance,
                audioAnswerDelaySeconds = session.audioAnswerDelaySeconds,
                audioNextCardDelaySeconds = session.audioNextCardDelaySeconds,
                freeformShowBothSides = session.freeformShowBothSides,
                freeformSwipeNavigation = session.freeformSwipeNavigation,
                typingIgnoreFormatting = session.typingIgnoreFormatting,
                typingAutoSubmit = session.typingAutoSubmit,
                typingDisableAutocorrect = session.typingDisableAutocorrect,
                typingShowLengthHint = session.typingShowLengthHint,
                flashcardAutoFlipSeconds = session.flashcardAutoFlipSeconds,
                flashcardDoubleTapToFlip = session.flashcardDoubleTapToFlip,
                flashcardRandomizeFirstSide = session.flashcardRandomizeFirstSide,
                requireConfirmTap = session.requireConfirmTap,
                autoAdvanceAfterCorrect = session.autoAdvanceAfterCorrect,
                autoAdvanceDelaySeconds = session.autoAdvanceDelaySeconds,
                autoListen = session.autoListen,
                listenStartSound = session.listenStartSound,
                listenCorrectSound = session.listenCorrectSound,
                listenIncorrectSound = session.listenIncorrectSound,
                speakingFrontSpeed = session.speakingFrontSpeed,
                speakingBackSpeed = session.speakingBackSpeed,
                listResetPosition = session.listResetPosition,
                listDimWrongGuesses = session.listDimWrongGuesses,
                listRemoveGuessed = session.listRemoveGuessed,
                matchingHighlightStyle = session.matchingHighlightStyle,
                matchingWrongDelayMs = session.matchingWrongDelayMs,
                matchingCorrectHighlightMs = session.matchingCorrectHighlightMs,
                matchingShowCorrectDialog = session.matchingShowCorrectDialog,
                anagramFirstLetterHint = session.anagramFirstLetterHint,
                anagramUppercase = session.anagramUppercase,
                anagramColorVowels = session.anagramColorVowels,
                crosswordHighlightWord = session.crosswordHighlightWord,
                crosswordAutoAdvanceCell = session.crosswordAutoAdvanceCell,
                crosswordCompactClues = session.crosswordCompactClues,
                crosswordFeedbackMode = session.crosswordFeedbackMode,
                hangmanMaxMistakes = session.hangmanMaxMistakes,
                hangmanRevealSpeedMs = session.hangmanRevealSpeedMs,
                hangmanHideVisual = session.hangmanHideVisual,
                memoryFlipAnimation = session.memoryFlipAnimation,
                memoryGrayMatched = session.memoryGrayMatched,
                memoryPeekSeconds = session.memoryPeekSeconds,
                memoryWrongPairMs = session.memoryWrongPairMs,
                wordSearchHideFound = session.wordSearchHideFound,
                wordSearchHighlightColor = session.wordSearchHighlightColor
            )
            startStudySession(state.deckWithCards, session.mode, session.isWeighted,
                session.totalCards, session.quizPromptSide, session.numberOfAnswers,
                session.showCorrectLetters, session.limitAnswerPool, session.isGraded,
                session.allowMultipleGuesses, session.enableStt, session.hideAnswerText,
                session.fingersAndToes, session.maxMemoryTiles,
                session.gridDensity, freeFormVerticalLayout = session.freeformLayoutVertical, config ) {}
        }
    }

    private fun difficultyMixOf(deck: DeckWithCards, cardIds: List<String>): Map<Int, Int> {
        val cardsById = deck.cards.associateBy { it.id }
        return cardIds.mapNotNull { cardsById[it] }.groupingBy { it.difficulty.value }.eachCount()
    }

    fun restartSameSession() {
        getStudyState()?.let { state ->
            getAllActiveSessions().firstOrNull { it.id == state.sessionId }?.let { session ->
                val reset = session.copy(currentCardIndex = 0, wrongSelections = emptyList(), correctAnswerFound = false, showQuestion = true, isFlipped = false, firstTryCorrectCount = 0, hasAttempted = false, lastAccessed = System.currentTimeMillis(), mcOptions = emptyMap(), matchedPairs = emptyList(), incorrectCardIds = emptyList(), wordSearchFoundWordIds = emptySet())
                resumeStudySession(reset)
                saveSession(reset)
            }
        }
    }

    fun startReviewSession(onSessionStarted: (route: String) -> Unit) {
        val state = getStudyState() ?: return
        val incorrect = state.shuffledCards.filter { it.id in state.incorrectCardIds }
        if (incorrect.isEmpty()) return
        val deck = state.deckWithCards.copy(cards = incorrect)
        val route = studyRouteFor(state.studyMode)

        val existingSession = getAllActiveSessions().find { it.id == state.sessionId }
        val schedulingMode = existingSession?.schedulingMode ?: SchedulingMode.NORMAL

        val config = AutoSetConfig(
            mode = AutoSetCreationMode.ONE,
            numSets = 1,
            maxCardsPerSet = incorrect.size,
            selectionMode = SelectionMode.ANY,
            selectedTags = emptyList(),
            selectedDifficulties = listOf(1, 2, 3, 4, 5),
            excludeKnown = false,
            includeSuspended = false,
            selectedFlags = emptyList(),
            sortMode = state.cardOrder,
            sortDirection = Direction.ASC,
            sortSide = CardSide.FRONT,
            alphabetStart = "A",
            alphabetEnd = "Z",
            filterSide = CardSide.FRONT,
            cardOrderStart = 1,
            cardOrderEnd = incorrect.size,
            timeValue = 7,
            timeUnit = TimeUnit.DAYS,
            filterType = FilterType.EXCLUDE,
            reviewCountThreshold = 0,
            reviewCountDirection = Direction.ASC,
            scoreThreshold = 0,
            scoreDirection = Direction.ASC,
            schedulingMode = schedulingMode,
            audioPlaybackSpeed = state.audioPlaybackSpeed,
            audioReplayCount = state.audioReplayCount,
            audioAutoAdvance = state.audioAutoAdvance,
            audioAnswerDelaySeconds = state.audioAnswerDelaySeconds,
            audioNextCardDelaySeconds = state.audioNextCardDelaySeconds,
            freeformShowBothSides = state.freeformShowBothSides,
            freeformSwipeNavigation = state.freeformSwipeNavigation,
            typingIgnoreFormatting = state.typingIgnoreFormatting,
            typingAutoSubmit = state.typingAutoSubmit,
            typingDisableAutocorrect = state.typingDisableAutocorrect,
            typingShowLengthHint = state.typingShowLengthHint,
            flashcardAutoFlipSeconds = state.flashcardAutoFlipSeconds,
            flashcardDoubleTapToFlip = state.flashcardDoubleTapToFlip,
            flashcardRandomizeFirstSide = state.flashcardRandomizeFirstSide,
            requireConfirmTap = state.requireConfirmTap,
            autoAdvanceAfterCorrect = state.autoAdvanceAfterCorrect,
            autoAdvanceDelaySeconds = state.autoAdvanceDelaySeconds,
            autoListen = state.autoListen,
            listenStartSound = state.listenStartSound,
            listenCorrectSound = state.listenCorrectSound,
            listenIncorrectSound = state.listenIncorrectSound,
            speakingFrontSpeed = state.speakingFrontSpeed,
            speakingBackSpeed = state.speakingBackSpeed,
            listResetPosition = state.listResetPosition,
            listDimWrongGuesses = state.listDimWrongGuesses,
            listRemoveGuessed = state.listRemoveGuessed,
            matchingHighlightStyle = state.matchingHighlightStyle,
            matchingWrongDelayMs = state.matchingWrongDelayMs,
            matchingCorrectHighlightMs = state.matchingCorrectHighlightMs,
            matchingShowCorrectDialog = state.matchingShowCorrectDialog,
            anagramFirstLetterHint = state.anagramFirstLetterHint,
            anagramUppercase = state.anagramUppercase,
            anagramColorVowels = state.anagramColorVowels,
            crosswordHighlightWord = state.crosswordHighlightWord,
            crosswordAutoAdvanceCell = state.crosswordAutoAdvanceCell,
            crosswordCompactClues = state.crosswordCompactClues,
            crosswordFeedbackMode = state.crosswordFeedbackMode,
            hangmanMaxMistakes = state.hangmanMaxMistakes,
            hangmanRevealSpeedMs = state.hangmanRevealSpeedMs,
            hangmanHideVisual = state.hangmanHideVisual,
            memoryFlipAnimation = state.memoryFlipAnimation,
            memoryGrayMatched = state.memoryGrayMatched,
            memoryPeekSeconds = state.memoryPeekSeconds,
            memoryWrongPairMs = state.memoryWrongPairMs,
            wordSearchHideFound = state.wordSearchHideFound,
            wordSearchHighlightColor = state.wordSearchHighlightColor
        )

        startStudySession(deck, state.studyMode, false, incorrect.size,
            state.quizPromptSide, state.numberOfAnswers, state.showCorrectLetters,
            state.limitAnswerPool, state.isGraded, state.allowMultipleGuesses, state.enableStt,
            state.hideAnswerText, state.fingersAndToes, state.maxMemoryTiles, state.gridDensity,
            state.freeformLayoutVertical, config ) {

            val sessionToDel = getAllActiveSessions().firstOrNull { it.id == state.sessionId }
            if (sessionToDel != null) {
                deleteSession(sessionToDel)
            }
            onSessionStarted(route)
        }
    }

    fun submitSelfGradedResult(isCorrect: Boolean) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]

            processCardReview(card, isCorrect = isCorrect, isGraded = true)

            val alreadyAttempted = state.attemptedCardIds.contains(card.id)
            val newAttempted = if (alreadyAttempted) state.attemptedCardIds else state.attemptedCardIds + card.id
            if (isCorrect) {
                val newScore = if (!alreadyAttempted) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount
                if (state.currentCardIndex < state.shuffledCards.size - 1) updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = newScore, hasAttempted = true, currentCardIndex = state.currentCardIndex + 1, wrongSelections = emptyList(), showFront = true, isFlipped = false, isCardRevealed = false, attemptedCardIds = newAttempted))
                else updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = newScore, isComplete = true, attemptedCardIds = newAttempted))
            } else {
                val newIncorrect = (state.incorrectCardIds + card.id).distinct()
                if (state.currentCardIndex < state.shuffledCards.size - 1) updateAndSaveStudyState(state.copy(hasAttempted = true, incorrectCardIds = newIncorrect, currentCardIndex = state.currentCardIndex + 1, wrongSelections = emptyList(), showFront = true, isFlipped = false, isCardRevealed = false, attemptedCardIds = newAttempted))
                else updateAndSaveStudyState(state.copy(hasAttempted = true, incorrectCardIds = newIncorrect, isComplete = true, attemptedCardIds = newAttempted))
            }
        }
    }

    fun submitHangmanGuess(char: Char) {
        getStudyState()?.let { state ->
            if (state.correctAnswerFound || state.isComplete) return@let
            val card = state.shuffledCards[state.currentCardIndex]
            val answer = (if (state.quizPromptSide == CardSide.FRONT) card.back else card.front).uppercase()
            val guess = char.uppercaseChar()
            if (guess in state.guessedLetters) return@let
            val newGuessed = state.guessedLetters + guess
            val isCorrect = answer.contains(guess)
            val newMistakes = if (isCorrect) state.hangmanMistakes else state.hangmanMistakes + 1
            val allFound = answer.filter { it.isLetter() }.all { it in newGuessed }
            val isLost = newMistakes >= (if (state.fingersAndToes) 27 else state.hangmanMaxMistakes)

            if (allFound) {
                processCardReview(card, isCorrect = true, isGraded = state.isGraded)
                updateAndSaveStudyState(state.copy(guessedLetters = newGuessed, correctAnswerFound = true, hasAttempted = true, firstTryCorrectCount = if (state.hangmanMistakes == 0) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount))
            } else if (isLost) {
                val newIncorrect = (state.incorrectCardIds + card.id).distinct()
                processCardReview(card, isCorrect = false, isGraded = state.isGraded)
                updateAndSaveStudyState(state.copy(guessedLetters = newGuessed, hangmanMistakes = newMistakes, correctAnswerFound = true, hasAttempted = true, incorrectCardIds = newIncorrect))
            } else {
                updateAndSaveStudyState(state.copy(guessedLetters = newGuessed, hangmanMistakes = newMistakes, hasAttempted = true))
            }
        }
    }

    fun submitFlashcardQuizAnswer(selectedOption: String) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]
            val correct = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
            val isCorrect = selectedOption == correct

            if (state.schedulingMode == SchedulingMode.FSRS) {
                if (isCorrect) {
                    val already = state.attemptedCardIds.contains(card.id)
                    updateAndSaveStudyState(state.copy(
                        correctAnswerFound = true,
                        firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount,
                        hasAttempted = true,
                        lastIncorrectAnswer = null,
                        attemptedCardIds = if (already) state.attemptedCardIds else state.attemptedCardIds + card.id
                    ))
                } else {
                    processCardReview(card, isCorrect = false, isGraded = true, explicitRating = 1)
                    updateAndSaveStudyState(state.copy(
                        wrongSelections = state.wrongSelections + selectedOption,
                        hasAttempted = true,
                        correctAnswerFound = true,
                        incorrectCardIds = (state.incorrectCardIds + card.id).distinct(),
                        attemptedCardIds = (state.attemptedCardIds + card.id).distinct()
                    ))
                }
            } else {
                processCardReview(card, isCorrect = isCorrect, isGraded = state.isGraded)
                if (isCorrect) {
                    val already = state.attemptedCardIds.contains(card.id)
                    updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount, hasAttempted = true, lastIncorrectAnswer = null, attemptedCardIds = if (already) state.attemptedCardIds else state.attemptedCardIds + card.id))
                } else if (!state.allowMultipleGuesses) {
                    // Quiz (List/MC share this function): reveal the correct answer after the
                    // first wrong guess, mirroring Matching's selectMatchingItem auto-reveal. Both
                    // List's PickerActionButtons and MC's option list already branch on
                    // correctAnswerFound to show the answer/hide other options, so no UI change
                    // is needed beyond setting it here.
                    updateAndSaveStudyState(state.copy(wrongSelections = state.wrongSelections + selectedOption, hasAttempted = true, lastIncorrectAnswer = selectedOption, correctAnswerFound = true, incorrectCardIds = (state.incorrectCardIds + card.id).distinct(), attemptedCardIds = (state.attemptedCardIds + card.id).distinct()))
                } else {
                    updateAndSaveStudyState(state.copy(wrongSelections = state.wrongSelections + selectedOption, hasAttempted = true, lastIncorrectAnswer = selectedOption, incorrectCardIds = (state.incorrectCardIds + card.id).distinct(), attemptedCardIds = (state.attemptedCardIds + card.id).distinct()))
                }
            }
        }
    }

    fun submitTypingAnswer(answer: String) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]
            val correct = if (state.quizPromptSide == CardSide.FRONT) card.back else card.front
            val isCorrect = typingAnswerMatches(answer, correct, state.typingIgnoreFormatting)

            if (state.schedulingMode == SchedulingMode.FSRS) {
                if (isCorrect) {
                    val already = state.attemptedCardIds.contains(card.id)
                    val newAttempted = if (already) state.attemptedCardIds else state.attemptedCardIds + card.id
                    updateAndSaveStudyState(state.copy(
                        correctAnswerFound = true,
                        firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount,
                        hasAttempted = true,
                        lastIncorrectAnswer = null,
                        attemptedCardIds = newAttempted
                    ))
                } else {
                    processCardReview(card, isCorrect = false, isGraded = true, explicitRating = 1)
                    val newIncorrect = (state.incorrectCardIds + card.id).distinct()
                    val newAttempted = (state.attemptedCardIds + card.id).distinct()
                    updateAndSaveStudyState(state.copy(
                        correctAnswerFound = true,
                        hasAttempted = true,
                        lastIncorrectAnswer = answer,
                        incorrectCardIds = newIncorrect,
                        attemptedCardIds = newAttempted
                    ))
                }
            } else {
                processCardReview(card, isCorrect = isCorrect, isGraded = state.isGraded)
                if (isCorrect) {
                    val already = state.attemptedCardIds.contains(card.id)
                    updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount, hasAttempted = true, lastIncorrectAnswer = null, attemptedCardIds = if (already) state.attemptedCardIds else state.attemptedCardIds + card.id))
                } else {
                    updateAndSaveStudyState(state.copy(hasAttempted = true, lastIncorrectAnswer = answer, attemptedCardIds = (state.attemptedCardIds + card.id).distinct()))
                }
            }
            // Auto-advance: a correct answer moves on to the next card.
            if (isCorrect && state.typingAutoSubmit) nextCard()
        }
    }

    /**
     * Grading entry point for the spoken-answer quiz modes (Listen & Speak, Listen & Type).
     * Same state transitions as [submitTypingAnswer] (FSRS vs normal branches, first-try scoring,
     * `attemptedCardIds`/`incorrectCardIds` bookkeeping) but takes a pre-computed [isCorrect]
     * instead of doing its own exact/space-stripped comparison — the caller checks the answer
     * with [net.ericclark.studiare.components.speech.AnswerMatcher], which is Unicode-aware and
     * (for spoken answers) tolerant of a small edit distance, unlike the exact match here.
     */
    fun submitListenAnswer(answer: String, isCorrect: Boolean) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]

            if (state.schedulingMode == SchedulingMode.FSRS) {
                if (isCorrect) {
                    val already = state.attemptedCardIds.contains(card.id)
                    val newAttempted = if (already) state.attemptedCardIds else state.attemptedCardIds + card.id
                    updateAndSaveStudyState(state.copy(
                        correctAnswerFound = true,
                        firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount,
                        hasAttempted = true,
                        lastIncorrectAnswer = null,
                        attemptedCardIds = newAttempted
                    ))
                } else {
                    processCardReview(card, isCorrect = false, isGraded = true, explicitRating = 1)
                    val newIncorrect = (state.incorrectCardIds + card.id).distinct()
                    val newAttempted = (state.attemptedCardIds + card.id).distinct()
                    updateAndSaveStudyState(state.copy(
                        correctAnswerFound = true,
                        hasAttempted = true,
                        lastIncorrectAnswer = answer,
                        incorrectCardIds = newIncorrect,
                        attemptedCardIds = newAttempted
                    ))
                }
            } else {
                processCardReview(card, isCorrect = isCorrect, isGraded = state.isGraded)
                if (isCorrect) {
                    val already = state.attemptedCardIds.contains(card.id)
                    updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount, hasAttempted = true, lastIncorrectAnswer = null, attemptedCardIds = if (already) state.attemptedCardIds else state.attemptedCardIds + card.id))
                } else {
                    updateAndSaveStudyState(state.copy(hasAttempted = true, lastIncorrectAnswer = answer, attemptedCardIds = (state.attemptedCardIds + card.id).distinct()))
                }
            }
            if (isCorrect && state.studyMode == SessionMode.TYPED_LISTEN && state.typingAutoSubmit) nextCard()
            if (isCorrect && state.studyMode == SessionMode.SPOKEN_LISTEN && state.autoAdvanceAfterCorrect) nextCard()
        }
    }

    fun submitTypingCorrect() {
        getStudyState()?.let { state ->
            processCardReview(state.shuffledCards[state.currentCardIndex], isCorrect = true, isGraded = state.isGraded)
            updateAndSaveStudyState(state.copy(correctAnswerFound = true, hasAttempted = true, lastIncorrectAnswer = null))
            // Auto-advance: a correct typed answer moves on (typing modes, and typed listening).
            if (state.typingAutoSubmit && state.studyMode in listOf(SessionMode.TYPING, SessionMode.TYPING_SCORED, SessionMode.TYPED_LISTEN)) nextCard()
            else if (state.studyMode == SessionMode.SPOKEN_LISTEN && state.autoAdvanceAfterCorrect) nextCard()
        }
    }

    fun revealAnswer() {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]
            processCardReview(card, isCorrect = false, isGraded = state.isGraded)
            updateAndSaveStudyState(state.copy(correctAnswerFound = true, hasAttempted = true, lastIncorrectAnswer = "", incorrectCardIds = (state.incorrectCardIds + card.id).distinct()))
        }
    }

    fun flipStudyMode() { getStudyState()?.let { updateAndSaveStudyState(it.copy(isFlipped = !it.isFlipped)) } }

    fun flipCard() {
        getStudyState()?.let { state ->
            val session = getAllActiveSessions().find { it.id == state.sessionId }
            val mode = session?.schedulingMode ?: SchedulingMode.NORMAL
            if (mode == SchedulingMode.NORMAL && !state.showFront) {
                processCardReview(state.shuffledCards[state.currentCardIndex], isCorrect = true, isGraded = false)
            }
            updateAndSaveStudyState(state.copy(showFront = !state.showFront, isCardRevealed = state.showFront || state.isCardRevealed))
        }
    }

    fun previousCard() {
        getStudyState()?.let { state -> if (state.currentCardIndex > 0) {
            val newState = state.copy(currentCardIndex = state.currentCardIndex - 1, wrongSelections = emptyList(), correctAnswerFound = false,
                showFront = true, hasAttempted = false, lastIncorrectAnswer = null, isCardRevealed = false);
            updateAndSaveStudyState(if (listOf(SessionMode.MULTIPLE_CHOICE, SessionMode.TYPING_SCORED, SessionMode.TYPING, SessionMode.ANAGRAM, SessionMode.LIST, SessionMode.HANGMAN, SessionMode.SPOKEN_LISTEN, SessionMode.TYPED_LISTEN).contains(newState.studyMode))
                newState.copy(correctAnswerFound = true) else newState) } } }

    fun nextCard() {
        getStudyState()?.let { state ->
            val session = getAllActiveSessions().find { it.id == state.sessionId }
            val mode = session?.schedulingMode ?: SchedulingMode.NORMAL
            if (mode == SchedulingMode.NORMAL) {
                if (!state.showFront) processCardReview(state.shuffledCards[state.currentCardIndex], isCorrect = true, isGraded = false)
                processCardReview(state.shuffledCards[state.currentCardIndex], isCorrect = true, isGraded = false)
            }
            // Picking with "remove completed": a card answered correctly leaves the session, so the next card takes its place.
            if (state.studyMode == SessionMode.LIST && state.listRemoveGuessed && state.correctAnswerFound && state.lastIncorrectAnswer == null) {
                val remaining = state.shuffledCards.filterIndexed { i, _ -> i != state.currentCardIndex }
                val cleared = state.copy(shuffledCards = remaining, wrongSelections = emptyList(), correctAnswerFound = false, showFront = true, hasAttempted = false, lastIncorrectAnswer = null, isCardRevealed = false)
                if (remaining.isEmpty()) updateAndSaveStudyState(cleared.copy(isComplete = true))
                else updateAndSaveStudyState(cleared.copy(currentCardIndex = state.currentCardIndex.coerceAtMost(remaining.size - 1)))
            }
            else if (state.currentCardIndex < state.shuffledCards.size - 1) updateAndSaveStudyState(state.copy(currentCardIndex = state.currentCardIndex + 1, wrongSelections = emptyList(), correctAnswerFound = false, showFront = true, hasAttempted = false, lastIncorrectAnswer = null, isCardRevealed = false, hangmanMistakes = 0, guessedLetters = emptySet()))
            else {
                if (state.studyMode == SessionMode.TYPING_SCORED) {
                    val score = state.firstTryCorrectCount.toFloat() / state.shuffledCards.size
                    val deck = state.deckWithCards.deck
                    saveDeck(deck.copy(averageQuizScore = if (deck.averageQuizScore == null) score else (deck.averageQuizScore + score) / 2))
                }
                updateAndSaveStudyState(state.copy(isComplete = true))
            }
        }
    }

    fun selectAnswer(option: String) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]
            val correct = if (state.isFlipped) card.front else card.back
            val isCorrect = option == correct
            val already = state.attemptedCardIds.contains(card.id)
            val newAttempted = if (already) state.attemptedCardIds else state.attemptedCardIds + card.id

            if (state.schedulingMode == SchedulingMode.FSRS) {
                if (isCorrect) {
                    updateAndSaveStudyState(state.copy(
                        correctAnswerFound = true,
                        firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount,
                        hasAttempted = true,
                        attemptedCardIds = newAttempted
                    ))
                } else {
                    processCardReview(card, isCorrect = false, isGraded = true, explicitRating = 1)
                    updateAndSaveStudyState(state.copy(
                        wrongSelections = state.wrongSelections + option,
                        hasAttempted = true,
                        correctAnswerFound = true,
                        incorrectCardIds = (state.incorrectCardIds + card.id).distinct(),
                        attemptedCardIds = newAttempted
                    ))
                }
            } else {
                processCardReview(card, isCorrect = isCorrect, isGraded = state.isGraded)
                if (isCorrect) {
                    updateAndSaveStudyState(state.copy(correctAnswerFound = true, hasAttempted = true, firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount, attemptedCardIds = newAttempted))
                } else {
                    updateAndSaveStudyState(state.copy(wrongSelections = state.wrongSelections + option, hasAttempted = true, incorrectCardIds = (state.incorrectCardIds + card.id).distinct(), attemptedCardIds = newAttempted, correctAnswerFound = !state.allowMultipleGuesses))
                }
            }
        }
    }

    fun generateOptionsForCurrentCardIfNeeded() {
        val state = getStudyState() ?: return
        val validModes = listOf(SessionMode.MULTIPLE_CHOICE, SessionMode.TYPING_SCORED, SessionMode.LIST)
        if (state.studyMode !in validModes && state.numberOfAnswers < ModeOptionDefaults.NUMBER_OF_ANSWERS_MIN) return

        val card = state.shuffledCards.getOrNull(state.currentCardIndex) ?: return
        if (state.mcOptions.containsKey(card.id)) return

        val getOptionText: (Card) -> String = { (if (state.isFlipped) it.front else it.back).trim().lowercase() }
        val effectiveLimit = state.limitAnswerPool && state.difficulties.isNotEmpty()
        val pool = if (effectiveLimit) state.deckWithCards.cards.filter { it.difficulty.value in state.difficulties } else state.deckWithCards.cards

        val wrong = pool.filter { it.id != card.id }.distinctBy { getOptionText(it) }.shuffled().take(state.numberOfAnswers - 1)
        val allOptions = (wrong + card).shuffled().map { it.id }
        updateAndSaveStudyState(state.copy(mcOptions = state.mcOptions + (card.id to allOptions)))
    }

    fun initMemoryGrid() {
        getStudyState()?.let { state ->
            val remaining = state.shuffledCards.filter { it.id !in state.successfullyMatchedPairs }
            val batch = remaining.take(state.maxMemoryTiles / 2).map { it.id }
            if (batch.isEmpty()) updateAndSaveStudyState(state.copy(isComplete = true))
            else updateAndSaveStudyState(state.copy(memoryActiveCardIds = batch, memorySelected1 = null, memorySelected2 = null, memoryConsecutiveWrongSideTaps = 0))
        }
    }

    fun selectMemoryTile(cardId: String, side: CardSide) {
        getStudyState()?.let { state ->
            if (state.memorySelected1 == null) { updateAndSaveStudyState(state.copy(memorySelected1 = cardId to side, memoryConsecutiveWrongSideTaps = 0)); return@let }
            if (state.memorySelected2 != null && state.memorySelected2?.first == cardId && state.memorySelected2?.second == side) {
                if (state.memorySelected1.first == state.memorySelected2!!.first) {
                    val newMatched = state.successfullyMatchedPairs + state.memorySelected1.first
                    updateAndSaveStudyState(state.copy(successfullyMatchedPairs = newMatched, memorySelected1 = null, memorySelected2 = null, isComplete = state.shuffledCards.count { it.id !in newMatched } == 0, memoryConsecutiveWrongSideTaps = 0))
                } else updateAndSaveStudyState(state.copy(memorySelected2 = null, memoryConsecutiveWrongSideTaps = 0))
                return@let
            }
            if (state.memorySelected1.first == cardId && state.memorySelected1.second == side) { updateAndSaveStudyState(state.copy(memorySelected1 = null, memorySelected2 = null, memoryConsecutiveWrongSideTaps = 0)); return@let }
            if (state.memorySelected2 == null) {
                if (state.memorySelected1.second == side) {
                    val count = state.memoryConsecutiveWrongSideTaps + 1
                    if (count >= 3) { onToastMessage("Tap on a ${if (side == CardSide.FRONT) "Pink" else "Blue"} tile to match, or tap the selected ${if (side == CardSide.FRONT) "Blue" else "Pink"} tile."); updateAndSaveStudyState(state.copy(memoryConsecutiveWrongSideTaps = 0)) }
                    else updateAndSaveStudyState(state.copy(memoryConsecutiveWrongSideTaps = count))
                } else updateAndSaveStudyState(state.copy(memorySelected2 = cardId to side, memoryConsecutiveWrongSideTaps = 0))
            }
        }
    }

    fun clearMemorySelection() {
        getStudyState()?.let { updateAndSaveStudyState(it.copy(memorySelected1 = null, memorySelected2 = null, memoryConsecutiveWrongSideTaps = 0)) }
    }

    fun startNewMatchingRound(cardsPerColumn: Int) {
        val state = getStudyState() ?: return
        val remaining = state.shuffledCards.drop(state.currentCardIndex)
        val roundCards = remaining.take(cardsPerColumn)
        if (roundCards.isEmpty()) updateAndSaveStudyState(state.copy(isComplete = true))
        else updateAndSaveStudyState(state.copy(matchingCardsOnScreen = roundCards, selectedMatchingItem = null, successfullyMatchedPairs = emptyList(), incorrectlyMatchedPair = null, matchingCardsPerColumn = cardsPerColumn, matchingAttemptedIncorrectly = emptyList()))
    }

    fun advanceMatchingRound() {
        val state = getStudyState() ?: return
        val newIndex = state.currentCardIndex + state.matchingCardsOnScreen.size
        if (newIndex >= state.shuffledCards.size) updateAndSaveStudyState(state.copy(isComplete = true))
        else {
            updateAndSaveStudyState(state.copy(
                currentCardIndex = newIndex,
                matchingCardsOnScreen = emptyList(),
                selectedMatchingItem = null,
                successfullyMatchedPairs = emptyList(),
                incorrectlyMatchedPair = null,
                matchingAttemptedIncorrectly = emptyList(),
                matchingRevealPair = emptyList()
            ))
        }
    }

    fun selectMatchingItem(cardId: String, side: String) {
        val state = getStudyState() ?: return
        if (state.matchingRevealPair.isNotEmpty()) {
            if (cardId in state.matchingRevealPair) {
                val newMatched = state.successfullyMatchedPairs + state.matchingRevealPair
                val newState = state.copy(successfullyMatchedPairs = newMatched, selectedMatchingItem = null, incorrectlyMatchedPair = null, matchingRevealPair = emptyList())
                updateAndSaveStudyState(newState)
            }
            return
        }
        val current = state.selectedMatchingItem
        val newSel = cardId to side
        if (current == newSel) { updateAndSaveStudyState(state.copy(selectedMatchingItem = null, incorrectlyMatchedPair = null)); return }
        if (current == null || current.second == side) { updateAndSaveStudyState(state.copy(selectedMatchingItem = newSel, incorrectlyMatchedPair = null)); return }

        if (current.first == newSel.first) {
            val card = state.matchingCardsOnScreen.first { it.id == cardId }
            val isFirstTry = cardId !in state.matchingAttemptedIncorrectly

            processCardReview(card, isCorrect = true, isGraded = state.isGraded)

            val newMatched = state.successfullyMatchedPairs + cardId
            val newState = state.copy(successfullyMatchedPairs = newMatched, selectedMatchingItem = null, incorrectlyMatchedPair = null, firstTryCorrectCount = if (isFirstTry) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount)
            updateAndSaveStudyState(newState)
        } else {
            val incIds = (state.incorrectCardIds + current.first + newSel.first).distinct()
            val incAtt = (state.matchingAttemptedIncorrectly + current.first).distinct()

            processCardReview(state.matchingCardsOnScreen.first { it.id == current.first }, isCorrect = false, isGraded = state.isGraded)

            if (!state.allowMultipleGuesses) updateAndSaveStudyState(state.copy(selectedMatchingItem = null, incorrectlyMatchedPair = null, matchingAttemptedIncorrectly = incAtt, incorrectCardIds = incIds, matchingRevealPair = listOf(current.first)))
            else updateAndSaveStudyState(state.copy(selectedMatchingItem = null, incorrectlyMatchedPair = current to newSel, matchingAttemptedIncorrectly = incAtt, incorrectCardIds = incIds))
        }
    }

    /**
     * Practice's "Give Me the Answer" button — reuses the same [matchingRevealPair] mechanism
     * [selectMatchingItem] triggers automatically after a wrong guess when `!allowMultipleGuesses`
     * (Quiz), but user-initiated instead. Requires a card already selected (Matching has no single
     * "current card" the way Flashcard/List do) — the button is disabled until one is.
     */
    fun revealMatchingAnswer() {
        val state = getStudyState() ?: return
        val current = state.selectedMatchingItem ?: return
        val card = state.matchingCardsOnScreen.first { it.id == current.first }
        val incAtt = (state.matchingAttemptedIncorrectly + current.first).distinct()

        processCardReview(card, isCorrect = false, isGraded = state.isGraded)

        updateAndSaveStudyState(state.copy(
            selectedMatchingItem = null,
            incorrectlyMatchedPair = null,
            matchingAttemptedIncorrectly = incAtt,
            incorrectCardIds = (state.incorrectCardIds + current.first).distinct(),
            matchingRevealPair = listOf(current.first)
        ))
    }

    fun selectCrosswordWord(wordId: String) {
        getStudyState()?.let { state ->
            val word = state.crosswordWords.find { it.id == wordId } ?: return@let
            var targetX = word.startX; var targetY = word.startY
            for (i in word.word.indices) {
                val x = if (word.isAcross) word.startX + i else word.startX
                val y = if (word.isAcross) word.startY else word.startY + i
                if (!state.crosswordUserInputs.containsKey("$x,$y")) { targetX = x; targetY = y; break }
            }
            updateAndSaveStudyState(state.copy(crosswordSelectedWordId = wordId, crosswordSelectedCell = targetX to targetY))
        }
    }

    fun selectCrosswordCell(x: Int, y: Int) {
        getStudyState()?.let { state ->
            val words = state.crosswordWords.filter { word -> if (word.isAcross) y == word.startY && x >= word.startX && x < word.startX + word.word.length else x == word.startX && y >= word.startY && y < word.startY + word.word.length }
            if (words.isEmpty()) return@let
            val newWordId = if (words.size > 1 && words.any { it.id == state.crosswordSelectedWordId }) words.first { it.id != state.crosswordSelectedWordId }.id else words.first().id
            updateAndSaveStudyState(state.copy(crosswordSelectedCell = x to y, crosswordSelectedWordId = newWordId))
        }
    }

    fun submitCrosswordChar(char: Char) {
        getStudyState()?.let { state ->
            val (selX, selY) = state.crosswordSelectedCell ?: return@let
            val inputs = state.crosswordUserInputs.toMutableMap()
            inputs["$selX,$selY"] = char.uppercaseChar()
            val activeWord = state.crosswordWords.find { it.id == state.crosswordSelectedWordId }
            var nextCell = selX to selY
            if (activeWord != null) {
                val indexInWord = if (activeWord.isAcross) selX - activeWord.startX else selY - activeWord.startY
                // With auto-advance on, the cursor only moves on from a correct letter; a wrong one stays to be retyped.
                val isCorrectLetter = activeWord.word.getOrNull(indexInWord)?.uppercaseChar() == char.uppercaseChar()
                val mayAdvance = !state.crosswordAutoAdvanceCell || isCorrectLetter
                if (mayAdvance && indexInWord < activeWord.word.length - 1) nextCell = if (activeWord.isAcross) (selX + 1) to selY else selX to (selY + 1)
            }
            val newCompletedIds = if (state.showCorrectWords) state.crosswordWords.filter { word -> word.word.indices.all { i -> val x = if (word.isAcross) word.startX + i else word.startX; val y = if (word.isAcross) word.startY else word.startY + i; inputs["$x,$y"] == word.word[i] } }.map { it.id }.toSet() else emptySet()

            (newCompletedIds - state.completedWordIds).forEach { cardId ->
                state.shuffledCards.find { it.id == cardId }?.let { processCardReview(it, isCorrect = true, isGraded = state.isGraded) }
            }

            updateAndSaveStudyState(state.copy(crosswordUserInputs = inputs, crosswordSelectedCell = nextCell, isComplete = newCompletedIds.size == state.crosswordWords.size, completedWordIds = newCompletedIds))
        }
    }

    // Clears the current cell, or if it is already empty steps back within the word and clears that one.
    fun deleteCrosswordChar() {
        getStudyState()?.let { state ->
            val (selX, selY) = state.crosswordSelectedCell ?: return@let
            val inputs = state.crosswordUserInputs.toMutableMap()
            var target = selX to selY
            if (!inputs.containsKey("$selX,$selY")) {
                val activeWord = state.crosswordWords.find { it.id == state.crosswordSelectedWordId }
                if (activeWord != null) {
                    val indexInWord = if (activeWord.isAcross) selX - activeWord.startX else selY - activeWord.startY
                    if (indexInWord > 0) target = if (activeWord.isAcross) (selX - 1) to selY else selX to (selY - 1)
                }
            }
            if (inputs.remove("${target.first},${target.second}") == null && target == (selX to selY)) return@let
            updateAndSaveStudyState(state.copy(crosswordUserInputs = inputs, crosswordSelectedCell = target, isComplete = false))
        }
    }

    fun provideCrosswordHint(wordId: String, fillEntireWord: Boolean) {
        getStudyState()?.let { state ->
            val word = state.crosswordWords.find { it.id == wordId } ?: return@let
            val inputs = state.crosswordUserInputs.toMutableMap()
            var changesMade = false
            for (i in word.word.indices) {
                val x = if (word.isAcross) word.startX + i else word.startX
                val y = if (word.isAcross) word.startY else word.startY + i
                if (inputs["$x,$y"] != word.word[i]) { inputs["$x,$y"] = word.word[i]; changesMade = true; if (!fillEntireWord) break }
            }
            if (changesMade) {
                val newCompletedIds = if (state.showCorrectWords) state.crosswordWords.filter { w -> w.word.indices.all { i -> val x = if (w.isAcross) w.startX + i else w.startX; val y = if (w.isAcross) w.startY else w.startY + i; inputs["$x,$y"] == w.word[i] } }.map { it.id }.toSet() else emptySet()

                (newCompletedIds - state.completedWordIds).forEach { cardId ->
                    state.shuffledCards.find { it.id == cardId }?.let { processCardReview(it, isCorrect = true, isGraded = state.isGraded) }
                }

                updateAndSaveStudyState(state.copy(crosswordUserInputs = inputs, isComplete = newCompletedIds.size == state.crosswordWords.size, completedWordIds = newCompletedIds))
            }
        }
    }

    fun getIncorrectCardInfo(selectedAnswer: String) { getStudyState()?.let { state -> val card = state.deckWithCards.cards.find { (if (state.isFlipped) it.front else it.back) == selectedAnswer }; if (card != null) onToastMessage(if (state.isFlipped) "Back: ${card.back}" else "Front: ${card.front}") } }

    fun updateFreeformIndex(index: Int) {
        getStudyState()?.let { state ->
            if (state.currentCardIndex != index) {
                updateAndSaveStudyState(state.copy(
                    currentCardIndex = index,
                    furthestCardIndex = kotlin.math.max(state.furthestCardIndex, index)
                ))
            }
        }
    }

    fun completeFreeformSession() {
        getStudyState()?.let { state ->
            updateAndSaveStudyState(state.copy(isComplete = true))
        }
    }

    private fun processCardReview(card: Card, isCorrect: Boolean, isGraded: Boolean, explicitRating: Int? = null) {
        getStudyState()?.let { state ->
            val session = getAllActiveSessions().find { it.id == state.sessionId }
            val mode = session?.schedulingMode ?: SchedulingMode.NORMAL
            val now = System.currentTimeMillis()

            if (mode == SchedulingMode.FSRS) {
                val rating = explicitRating ?: (if (isCorrect) Rating.GOOD.value else Rating.AGAIN.value)
                val result = FsrsAlgorithm.calculateNextState(card, Rating.fromInt(rating), state.deckWithCards.deck)

                // NEW: Build the Review Log
                val newLog = ReviewLog(
                    id = now,
                    ease = rating,
                    interval = result.scheduledDays.toLong(),
                    lastInterval = (card.fsrsScheduledDays ?: 0.0).toLong(),
                    factor = result.stability,
                    durationMs = card.lastReviewDurationMs, // Will be 0 until a timer feature is added
                    type = card.fsrsState?.value ?: FsrsState.NEW.value
                )

                val newCard = card.copy(
                    fsrsStability = result.stability,
                    fsrsDifficulty = result.difficulty,
                    fsrsElapsedDays = result.elapsedDays,
                    fsrsScheduledDays = result.scheduledDays,
                    fsrsState = result.state,
                    fsrsLastReview = now,
                    fsrsLapses = if (!isCorrect) card.fsrsLapses + 1 else card.fsrsLapses,
                    reviewedAt = now,
                    reviewedCount = card.reviewedCount + 1,
                    gradedAttempts = if (isGraded) card.gradedAttempts + now else card.gradedAttempts,
                    incorrectAttempts = if (!isCorrect) card.incorrectAttempts + now else card.incorrectAttempts,
                    absoluteDueDate = result.dueTimestamp, // NEW
                    reviewLogs = card.reviewLogs + newLog  // NEW
                )
                saveCard(newCard)
            } else if (isGraded) {
                val rating = if (isCorrect) Rating.GOOD else Rating.AGAIN
                val result = FsrsAlgorithm.calculateNextState(card, rating, state.deckWithCards.deck)

                val newLog = ReviewLog(
                    id = now, ease = rating.value, interval = result.scheduledDays.toLong(),
                    lastInterval = (card.fsrsScheduledDays ?: 0.0).toLong(), factor = result.stability,
                    durationMs = card.lastReviewDurationMs, type = card.fsrsState?.value ?: FsrsState.NEW.value
                )

                val newCard = card.copy(
                    fsrsStability = result.stability,
                    fsrsDifficulty = result.difficulty,
                    fsrsElapsedDays = result.elapsedDays,
                    fsrsScheduledDays = result.scheduledDays,
                    fsrsState = result.state,
                    fsrsLastReview = now,
                    fsrsLapses = if (!isCorrect) card.fsrsLapses + 1 else card.fsrsLapses,
                    reviewedAt = now,
                    reviewedCount = card.reviewedCount + 1,
                    gradedAttempts = card.gradedAttempts + now,
                    incorrectAttempts = if (!isCorrect) card.incorrectAttempts + now else card.incorrectAttempts,
                    absoluteDueDate = result.dueTimestamp, // NEW
                    reviewLogs = card.reviewLogs + newLog  // NEW
                )
                saveCard(newCard)
            } else {
                // Ungraded (Learn/Practice): the card was reviewed, but an ungraded wrong answer
                // must not count toward the card's graded incorrectAttempts stat — only isGraded
                // sessions (the branch above) or FSRS do that. Session-level mistake-tracking
                // (incorrectCardIds/wrongSelections, already set by every mode's own submit
                // function) is where practice mistakes live instead.
                val newCard = card.copy(reviewedAt = now, reviewedCount = card.reviewedCount + 1)
                saveCard(newCard)
            }
        }
    }

    /**
     * Audio mode's card index lives in the bound [AudioStudyService] rather than being driven by
     * user actions routed through this manager, so it needs its own persistence entry point
     * instead of going through the quiz/flashcard submit paths. Without this, the Audio session's
     * position was only ever updated in memory (`FlashcardViewModel.studyState`) and never written
     * to the `sessions` table, so force-stopping the app (or the process simply dying) silently
     * rewound the session to card 1.
     */
    fun updateAudioProgress(index: Int) {
        getStudyState()?.let { state ->
            if (state.currentCardIndex != index) {
                updateAndSaveStudyState(state.copy(currentCardIndex = index))
            }
        }
    }

    private fun generateCrossword(cards: List<Card>, promptSide: CardSide, density: Int): Pair<List<CrosswordWord>, Pair<Int, Int>> {
        val wordList = cards.map { card ->
            val answer = (if (promptSide == CardSide.FRONT) card.back else card.front).trim().uppercase().filter { it.isLetter() }
            val clue = if (promptSide == CardSide.FRONT) card.front else card.back
            Triple(card.id, answer, clue)
        }.filter { it.second.length >= 2 }.sortedByDescending { it.second.length }

        if (wordList.isEmpty()) return emptyList<CrosswordWord>() to (0 to 0)

        val placedChars = mutableMapOf<String, Char>()
        val placedWords = mutableListOf<CrosswordWord>()

        val first = wordList.first()
        addWordToGrid(first, 0, 0, true, 0, placedWords, placedChars)

        val remaining = wordList.drop(1).toMutableList()
        var attempts = 0

        while (remaining.isNotEmpty() && attempts < 1000) {
            attempts++
            val currentCandidate = remaining.removeAt(0)
            val validMoves = mutableListOf<Triple<Int, Int, Boolean>>()

            for (i in currentCandidate.second.indices) {
                val charToMatch = currentCandidate.second[i]
                val potentialIntersections = placedChars.filter { it.value == charToMatch }

                for ((key, _) in potentialIntersections) {
                    val parts = key.split(",")
                    val gridX = parts[0].toInt()
                    val gridY = parts[1].toInt()

                    if (canPlaceWord(currentCandidate.second, gridX - i, gridY, true, placedChars)) {
                        validMoves.add(Triple(gridX - i, gridY, true))
                    }
                    if (canPlaceWord(currentCandidate.second, gridX, gridY - i, false, placedChars)) {
                        validMoves.add(Triple(gridX, gridY - i, false))
                    }
                }
            }

            if (validMoves.isNotEmpty()) {
                val bestMove = if (density == 1) {
                    validMoves.first()
                } else {
                    val curMinX = placedWords.minOfOrNull { it.startX } ?: 0
                    val curMaxX = placedWords.maxOfOrNull { if (it.isAcross) it.startX + it.word.length - 1 else it.startX } ?: 0
                    val curMinY = placedWords.minOfOrNull { it.startY } ?: 0
                    val curMaxY = placedWords.maxOfOrNull { if (it.isAcross) it.startY else it.startY + it.word.length - 1 } ?: 0

                    validMoves.minBy { (x, y, isAcross) ->
                        val wordLen = currentCandidate.second.length
                        val newMinX = min(curMinX, x)
                        val newMaxX = max(curMaxX, if (isAcross) x + wordLen - 1 else x)
                        val newMinY = min(curMinY, y)
                        val newMaxY = max(curMaxY, if (isAcross) y else y + wordLen - 1)
                        (newMaxX - newMinX + 1) * (newMaxY - newMinY + 1)
                    }
                }
                addWordToGrid(currentCandidate, bestMove.first, bestMove.second, bestMove.third, 0, placedWords, placedChars)
            } else {
                if (attempts < 200) remaining.add(currentCandidate)
            }
        }

        if (placedWords.isEmpty()) return emptyList<CrosswordWord>() to (0 to 0)

        val minX = placedWords.minOf { it.startX }
        val minY = placedWords.minOf { it.startY }
        val maxX = placedWords.maxOf { if (it.isAcross) it.startX + it.word.length - 1 else it.startX }
        val maxY = placedWords.maxOf { if (it.isAcross) it.startY else it.startY + it.word.length - 1 }

        val width = maxX - minX + 1
        val height = maxY - minY + 1

        val normalizedWords = placedWords.map { it.copy(startX = it.startX - minX, startY = it.startY - minY) }

        var clueCounter = 1
        val renumberedWords = normalizedWords.toMutableList()
        val finalWords = mutableListOf<CrosswordWord>()

        for (y in 0 until height) {
            for (x in 0 until width) {
                val wordsStartingHere = renumberedWords.filter { it.startX == x && it.startY == y }
                if (wordsStartingHere.isNotEmpty()) {
                    wordsStartingHere.forEach { word ->
                        finalWords.add(word.copy(number = clueCounter))
                        renumberedWords.remove(word)
                    }
                    clueCounter++
                }
            }
        }
        return finalWords to (width to height)
    }

    private fun canPlaceWord(word: String, startX: Int, startY: Int, isAcross: Boolean, grid: Map<String, Char>): Boolean {
        for (i in word.indices) {
            val x = if (isAcross) startX + i else startX
            val y = if (isAcross) startY else startY + i
            val cellContent = grid["$x,$y"]
            if (cellContent != null && cellContent != word[i]) return false
            // An existing cell must be a true crossing. If it already has a neighbor along this
            // word's direction, the word would overlap another word running the same way
            // (e.g. "casa" inside "casale"), producing two clues with the same number and direction.
            if (cellContent != null) {
                val beforeKey = if (isAcross) "${x-1},$y" else "$x,${y-1}"
                val afterKey = if (isAcross) "${x+1},$y" else "$x,${y+1}"
                if (grid.containsKey(beforeKey) || grid.containsKey(afterKey)) return false
            }
            if (cellContent == null) {
                if (isAcross) { if (grid.containsKey("$x,${y-1}") || grid.containsKey("$x,${y+1}")) return false }
                else { if (grid.containsKey("${x-1},$y") || grid.containsKey("${x+1},$y")) return false }
            }
            if (i == 0) {
                val prevX = if (isAcross) x - 1 else x
                val prevY = if (isAcross) y else y - 1
                if (grid.containsKey("$prevX,$prevY")) return false
            }
            if (i == word.length - 1) {
                val nextX = if (isAcross) x + 1 else x
                val nextY = if (isAcross) y else y + 1
                if (grid.containsKey("$nextX,$nextY")) return false
            }
        }
        return true
    }

    private fun addWordToGrid(info: Triple<String, String, String>, startX: Int, startY: Int, isAcross: Boolean, number: Int, wordList: MutableList<CrosswordWord>, grid: MutableMap<String, Char>) {
        wordList.add(
            CrosswordWord(
                info.first,
                info.second,
                info.third,
                startX,
                startY,
                isAcross,
                number
            )
        )
        for (i in info.second.indices) {
            val x = if (isAcross) startX + i else startX
            val y = if (isAcross) startY else startY + i
            grid["$x,$y"] = info.second[i]
        }
    }

    fun submitWordSearchMatch(startCell: Pair<Int, Int>, endCell: Pair<Int, Int>) {
        val state = getStudyState() ?: return

        // Find if these coordinates match any word (forwards or backwards)
        val matchedWord = state.wordSearchWords.find { word ->
            val isMatchForward = word.startX == startCell.first && word.startY == startCell.second &&
                    word.endX == endCell.first && word.endY == endCell.second
            val isMatchBackward = word.startX == endCell.first && word.startY == endCell.second &&
                    word.endX == startCell.first && word.endY == startCell.second

            isMatchForward || isMatchBackward
        }

        if (matchedWord != null && matchedWord.id !in state.wordSearchFoundWordIds) {
            val newFoundIds = state.wordSearchFoundWordIds + matchedWord.id

            // Log review
            state.shuffledCards.find { it.id == matchedWord.id }?.let {
                processCardReview(it, isCorrect = true, isGraded = state.isGraded)
            }

            val isSessionComplete = newFoundIds.size == state.wordSearchWords.size
            updateAndSaveStudyState(state.copy(
                wordSearchFoundWordIds = newFoundIds,
                isComplete = isSessionComplete
            ))
        }
    }

    private fun generateWordSearch(cards: List<Card>, promptSide: CardSide, density: Int): Tuple4<List<net.ericclark.studiare.data.WordSearchWord>, List<String>, Int, Int> {
        // Prepare words: use the shorter side as the word to find, and the longer side as the clue
        val candidateWords = cards.map { card ->
            val front = card.front.trim()
            val back = card.back.trim()

            val (answerRaw, clueRaw) = if (promptSide == CardSide.FRONT) {
                back to front
            } else {
                front to back
            }

            val answer = answerRaw.uppercase().filter { it.isLetter() }
            Triple(card.id, answer, clueRaw)
        }.filter { it.second.length >= 3 }.sortedByDescending { it.second.length }

        if (candidateWords.isEmpty()) return Tuple4(emptyList(), emptyList(), 0, 0)

        // Calculate grid size based on longest word, number of words, and density
        val longestWordLength = candidateWords.maxOf { it.second.length }
        val totalChars = candidateWords.sumOf { it.second.length }

        val sizeMultiplier = when(density) {
            1 -> 2.5   // Sparse
            2 -> 1.8  // Balanced
            else -> 1.3 // Compact
        }

        val estimatedArea = totalChars * sizeMultiplier
        var gridSize = maxOf(longestWordLength, kotlin.math.sqrt(estimatedArea).toInt())
        gridSize = max(gridSize, 8) // Minimum size

        val grid = Array(gridSize) { CharArray(gridSize) { ' ' } }
        val placedWords = mutableListOf<net.ericclark.studiare.data.WordSearchWord>()
        val random = java.util.Random()

        // 8 possible directions
        val directions = listOf(
            Pair(1, 0), Pair(0, 1), Pair(1, 1), Pair(1, -1), // Right, Down, Diagonal-Down-Right, Diagonal-Up-Right
            Pair(-1, 0), Pair(0, -1), Pair(-1, -1), Pair(-1, 1) // Backwards equivalents
        )

        for (candidate in candidateWords) {
            val (id, word, clue) = candidate
            var placed = false
            var attempts = 0

            while (!placed && attempts < 200) {
                val dir = directions.random()
                val dx = dir.first
                val dy = dir.second

                // Random start position
                val startX = random.nextInt(gridSize)
                val startY = random.nextInt(gridSize)
                val endX = startX + (word.length - 1) * dx
                val endY = startY + (word.length - 1) * dy

                // Check bounds
                if (endX in 0 until gridSize && endY in 0 until gridSize) {
                    var canPlace = true
                    for (i in word.indices) {
                        val cx = startX + i * dx
                        val cy = startY + i * dy
                        if (grid[cy][cx] != ' ' && grid[cy][cx] != word[i]) {
                            canPlace = false
                            break
                        }
                    }

                    if (canPlace) {
                        for (i in word.indices) {
                            val cx = startX + i * dx
                            val cy = startY + i * dy
                            grid[cy][cx] = word[i]
                        }
                        placedWords.add(net.ericclark.studiare.data.WordSearchWord(id, word, clue, startX, startY, endX, endY))
                        placed = true
                    }
                }
                attempts++
            }
        }

        // Fill empty spaces with random uppercase letters
        for (y in 0 until gridSize) {
            for (x in 0 until gridSize) {
                if (grid[y][x] == ' ') {
                    grid[y][x] = ('A'..'Z').random(kotlin.random.Random(random.nextLong()))
                }
            }
        }

        val gridStrings = grid.map { String(it) }
        return Tuple4(placedWords, gridStrings, gridSize, gridSize)
    }
}

data class Tuple4<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

/** Whether a Guided (FSRS) session includes this card now: new cards always, others once they're due. */
fun Card.isDueForFsrs(nowMillis: Long): Boolean {
    if (fsrsState == FsrsState.NEW || fsrsState == null) return true
    val dueAt = (fsrsLastReview ?: 0L) + ((fsrsScheduledDays ?: 0.0) * 24 * 60 * 60 * 1000L).toLong()
    return nowMillis >= dueAt
}
