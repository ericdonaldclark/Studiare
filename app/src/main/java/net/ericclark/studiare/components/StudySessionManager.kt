package net.ericclark.studiare.components

import net.ericclark.studiare.data.*
import net.ericclark.studiare.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.collections.iterator
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Manages the logic for active study sessions.
 * Updated to support FSRS v4.5 scheduling.
 */
class StudySessionManager(
    private val preferenceManager: net.ericclark.studiare.PreferenceManager,
    private val authAndSyncManager: AuthAndSyncManager,
    private val cardUtils: CardUtils,
    private val viewModelScope: CoroutineScope,
    private val getStudyState: () -> StudyState?,
    private val setStudyState: (StudyState?) -> Unit,
    private val getAllDecks: () -> List<DeckWithCards>,
    private val getAllActiveSessions: () -> List<ActiveSession>,
    private val onToastMessage: (String?) -> Unit
) {
    // --- Session Persistence & Controls ---

    private fun updateAndSaveStudyState(newState: StudyState?) {
        var stateToProcess = newState

        if (stateToProcess != null && stateToProcess.schedulingMode == "Spaced Repetition") {
            val currentCard = stateToProcess.shuffledCards.getOrNull(stateToProcess.currentCardIndex)
            if (currentCard != null) {
                val intervals = calculateFSRSIntervals(currentCard, stateToProcess.deckWithCards.deck)
                stateToProcess = stateToProcess.copy(nextIntervals = intervals)
            }
        }

        setStudyState(stateToProcess) // Use the processed state
        if (stateToProcess == null) return

        viewModelScope.launch {
            val currentSessions = getAllActiveSessions()
            val updatedSessions = currentSessions.map { session ->
                if (session.id == stateToProcess.sessionId) {
                    session.copy(
                        currentCardIndex = stateToProcess.currentCardIndex,
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
                        showCorrectWords = stateToProcess.showCorrectWords
                    )
                } else session
            }
            preferenceManager.saveActiveSessions(updatedSessions)
        }
    }

    private fun calculateFSRSIntervals(card: Card, deck: Deck): Map<Int, String> {
        return mapOf(
            1 to formatInterval(FsrsAlgorithm.calculateNextState(card, 1, deck).scheduledDays),
            2 to formatInterval(FsrsAlgorithm.calculateNextState(card, 2, deck).scheduledDays),
            3 to formatInterval(FsrsAlgorithm.calculateNextState(card, 3, deck).scheduledDays),
            4 to formatInterval(FsrsAlgorithm.calculateNextState(card, 4, deck).scheduledDays)
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
        viewModelScope.launch {
            val updated = getAllActiveSessions().filter { it.id != sessionToDelete.id }
            preferenceManager.saveActiveSessions(updated)
        }
    }

    fun deleteCurrentStudySession() {
        getStudyState()?.let { state ->
            getAllActiveSessions().firstOrNull { it.id == state.sessionId }?.let { deleteSession(it) }
        }
    }

    fun copySession(session: ActiveSession) {
        viewModelScope.launch {
            val newSession = session.copy(
                id = UUID.randomUUID().toString(),
                currentCardIndex = 0,
                wrongSelections = emptyList(),
                correctAnswerFound = false,
                showQuestion = true,
                isFlipped = false,
                firstTryCorrectCount = 0,
                hasAttempted = false,
                createdAt = System.currentTimeMillis(),
                lastAccessed = System.currentTimeMillis(),
                mcOptions = emptyMap(),
                incorrectCardIds = emptyList()
            )
            preferenceManager.saveActiveSessions(getAllActiveSessions() + newSession)
        }
    }

    fun restartSession(session: ActiveSession) {
        viewModelScope.launch {
            val updated = getAllActiveSessions().map {
                if (it.id == session.id) it.copy(
                    currentCardIndex = 0,
                    wrongSelections = emptyList(),
                    correctAnswerFound = false,
                    showQuestion = true,
                    isFlipped = false,
                    firstTryCorrectCount = 0,
                    hasAttempted = false,
                    lastAccessed = System.currentTimeMillis(),
                    mcOptions = emptyMap(),
                    incorrectCardIds = emptyList()
                ) else it
            }
            preferenceManager.saveActiveSessions(updated)
        }
    }

    fun resumeStudySession(session: ActiveSession) {
        val deck = getAllDecks().find { it.deck.id == session.deckId } ?: return
        val cardsInOrder = session.shuffledCardIds.mapNotNull { id -> deck.cards.find { it.id == id } }

        viewModelScope.launch {
            val updated = getAllActiveSessions().map {
                if (it.id == session.id) it.copy(lastAccessed = System.currentTimeMillis()) else it
            }
            preferenceManager.saveActiveSessions(updated)
        }

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
            nextIntervals = if (session.schedulingMode == "Spaced Repetition" && cardsInOrder.isNotEmpty()) {
                calculateFSRSIntervals(cardsInOrder[session.currentCardIndex], deck.deck)
            } else emptyMap(),
            isWeighted = session.isWeighted,
            shuffledCards = cardsInOrder,
            quizPromptSide = session.quizPromptSide,
            currentCardIndex = session.currentCardIndex,
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
            completedWordIds = completedIds
        )
        setStudyState(newState)
    }

    fun endStudySession() { setStudyState(null) }

    // --- Session Initialization ---

    fun startStudySession(
        parentDeck: DeckWithCards, mode: String, isWeighted: Boolean, numCards: Int, quizPromptSide: String, numAnswers: Int, showCorrectLetters: Boolean, limitAnswerPool: Boolean,
        isGraded: Boolean, selectAnswer: Boolean, allowMultipleGuesses: Boolean, enableStt: Boolean, hideAnswerText: Boolean, fingersAndToes: Boolean, maxMemoryTiles: Int,
        gridDensity: Int, config: AutoSetConfig, onSessionCreated: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            var sessionCards = cardUtils.getFilteredAndSortedCards(parentDeck, config)
            if (isWeighted && config.sortMode == "Random") {
                val weightedList = sessionCards.flatMap { card -> List(card.difficulty) { card } }
                sessionCards = cardUtils.createPerceivedRandomList(weightedList)
            }
            val finalCards = sessionCards.take(min(numCards, sessionCards.size))
            if (finalCards.isEmpty()) return@launch

            val currentTime = System.currentTimeMillis()
            var cwWords: List<CrosswordWord> = emptyList()
            var cwWidth = 0
            var cwHeight = 0

            val internalMode = if (mode == "Flashcard" && selectAnswer) "Flashcard Quiz" else if (mode == "Typing" && isGraded) "Quiz" else mode
            if (mode == "Crossword") {
                val (words, dim) = generateCrossword(finalCards, quizPromptSide, gridDensity)
                cwWords = words; cwWidth = dim.first; cwHeight = dim.second
            }
            val pickerOptions = if (internalMode == "Flashcard Quiz") {
                val pickSide = if (quizPromptSide == "Front") "Back" else "Front"
                parentDeck.cards.map { if (pickSide == "Front") it.front else it.back }.filter { it.isNotBlank() }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
            } else emptyList()

            val newSession = ActiveSession(
                id = UUID.randomUUID().toString(),
                deckId = parentDeck.deck.id,
                mode = if (mode == "Crossword") "Crossword" else internalMode,
                // Pass scheduling mode from config
                schedulingMode = config.schedulingMode,
                isWeighted = isWeighted,
                difficulties = config.selectedDifficulties,
                totalCards = finalCards.size,
                shuffledCardIds = finalCards.map { it.id },
                quizPromptSide = quizPromptSide,
                createdAt = currentTime,
                lastAccessed = currentTime,
                numberOfAnswers = numAnswers,
                showCorrectLetters = showCorrectLetters,
                limitAnswerPool = limitAnswerPool,
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
                showCorrectWords = true
            )
            if (config.schedulingMode != "Spaced Repetition") {
                val updatedSessions = getAllActiveSessions() + newSession
                preferenceManager.saveActiveSessions(updatedSessions)
            }

            withContext(Dispatchers.Main) {
                setStudyState(
                    StudyState(
                        sessionId = newSession.id,
                        deckWithCards = parentDeck,
                        studyMode = if (mode == "Crossword") "Crossword" else internalMode,
                        schedulingMode = config.schedulingMode,
                        nextIntervals = if (config.schedulingMode == "Spaced Repetition" && finalCards.isNotEmpty()) {
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
                        completedWordIds = emptySet()
                    )
                )
                onSessionCreated()
            }
        }
    }

    fun submitFsrsGrade(rating: Int) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]

            // 1=Again, 2=Hard, 3=Good, 4=Easy
            val isCorrect = rating > 1

            // Pass the explicit rating to the processing logic
            processCardReview(card, isCorrect = isCorrect, isGraded = true, explicitRating = rating)

            // Standard state updates for UI progression
            val alreadyAttempted = state.attemptedCardIds.contains(card.id)
            val newAttempted = if (alreadyAttempted) state.attemptedCardIds else state.attemptedCardIds + card.id

            if (isCorrect) {
                // If Easy/Good/Hard, treat as correct for session scoring
                val newScore = if (!alreadyAttempted) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount

                if (state.currentCardIndex < state.shuffledCards.size - 1) {
                    updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = newScore, hasAttempted = true, currentCardIndex = state.currentCardIndex + 1, wrongSelections = emptyList(), showFront = true, isFlipped = false, isCardRevealed = false, attemptedCardIds = newAttempted))
                } else {
                    updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = newScore, isComplete = true, attemptedCardIds = newAttempted))
                }
            } else {
                // "Again" is treated as incorrect
                val newIncorrect = (state.incorrectCardIds + card.id).distinct()

                if (state.currentCardIndex < state.shuffledCards.size - 1) {
                    updateAndSaveStudyState(state.copy(hasAttempted = true, incorrectCardIds = newIncorrect, currentCardIndex = state.currentCardIndex + 1, wrongSelections = emptyList(), showFront = true, isFlipped = false, isCardRevealed = false, attemptedCardIds = newAttempted))
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
                "One",
                1,
                session.totalCards,
                "Difficulty",
                emptyList(),
                session.difficulties,
                false,
                session.cardOrder,
                "ASC",
                "Front",
                "A",
                "Z",
                "Front",
                1,
                session.totalCards,
                7,
                "Days",
                "Exclude",
                0,
                "Minimum",
                0,
                "Minimum",
                // Preserve scheduling mode
                schedulingMode = session.schedulingMode
            )
            startStudySession(state.deckWithCards, session.mode, session.isWeighted, session.totalCards, session.quizPromptSide, session.numberOfAnswers, session.showCorrectLetters, session.limitAnswerPool, session.isGraded, session.mode == "Flashcard Quiz", session.allowMultipleGuesses, session.enableStt, session.hideAnswerText, session.fingersAndToes, session.maxMemoryTiles, 2, config) {}
        }
    }

    fun restartSameSession() {
        getStudyState()?.let { state ->
            getAllActiveSessions().firstOrNull { it.id == state.sessionId }?.let { session ->
                val reset = session.copy(currentCardIndex = 0, wrongSelections = emptyList(), correctAnswerFound = false, showQuestion = true, isFlipped = false, firstTryCorrectCount = 0, hasAttempted = false, lastAccessed = System.currentTimeMillis(), mcOptions = emptyMap(), matchedPairs = emptyList(), incorrectCardIds = emptyList())
                resumeStudySession(reset)
                viewModelScope.launch { preferenceManager.saveActiveSessions(getAllActiveSessions().map { if (it.id == reset.id) reset else it }) }
            }
        }
    }

    fun startReviewSession(onSessionStarted: (route: String) -> Unit) {
        val state = getStudyState() ?: return
        val incorrect = state.shuffledCards.filter { it.id in state.incorrectCardIds }
        if (incorrect.isEmpty()) return
        val deck = state.deckWithCards.copy(cards = incorrect)
        val route = when (state.studyMode) { "Flashcard" -> "flashcardStudy"; "Multiple Choice" -> "mcStudy"; "Matching" -> "matchingStudy"; "Quiz" -> "quizStudy"; "Typing" -> "typingStudy"; "Flashcard Quiz" -> "flashcardQuizStudy"; "Anagram" -> "anagramStudy"; "Hangman" -> "hangmanStudy"; "Memory" -> "memoryStudy"; "Crossword" -> "crosswordStudy"; "Audio" -> "audioStudy"; else -> return }

        // Find existing session to copy scheduling mode if possible, else default
        val existingSession = getAllActiveSessions().find { it.id == state.sessionId }
        val schedulingMode = existingSession?.schedulingMode ?: "Normal"

        val config = AutoSetConfig(
            "One",
            1,
            incorrect.size,
            "Any",
            emptyList(),
            listOf(1, 2, 3, 4, 5),
            false,
            state.cardOrder,
            "ASC",
            "Front",
            "A",
            "Z",
            "Front",
            1,
            incorrect.size,
            7,
            "Days",
            "Exclude",
            0,
            "Minimum",
            0,
            "Minimum",
            schedulingMode = schedulingMode
        )

        startStudySession(deck, state.studyMode, state.isWeighted, incorrect.size, state.quizPromptSide, state.numberOfAnswers, state.showCorrectLetters, state.limitAnswerPool, state.isGraded, state.studyMode == "Flashcard Quiz", state.allowMultipleGuesses, state.enableStt, state.hideAnswerText, state.fingersAndToes, state.maxMemoryTiles, 2, config) {
            viewModelScope.launch {
                deleteSession(getAllActiveSessions().first { it.id == state.sessionId })
                onSessionStarted(route)
            }
        }
    }

    // --- Answer Processing Methods ---

    fun submitSelfGradedResult(isCorrect: Boolean) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]

            // FSRS Update Logic Replaces `updateCardReviewedAt`
            processCardReview(card, isCorrect = isCorrect, isGraded = true)

            // Note: processCardReview handles logging incorrect attempt if FSRS is on or if Normal mode logic dictates
            // But we maintain the state management logic below

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
            val answer = (if (state.quizPromptSide == "Front") card.back else card.front).uppercase()
            val guess = char.uppercaseChar()
            if (guess in state.guessedLetters) return@let
            val newGuessed = state.guessedLetters + guess
            val isCorrect = answer.contains(guess)
            val newMistakes = if (isCorrect) state.hangmanMistakes else state.hangmanMistakes + 1
            val allFound = answer.filter { it.isLetter() }.all { it in newGuessed }
            val isLost = newMistakes >= (if (state.fingersAndToes) 27 else 7)

            if (allFound) {
                processCardReview(card, isCorrect = true, isGraded = state.isGraded)
                updateAndSaveStudyState(state.copy(guessedLetters = newGuessed, correctAnswerFound = true, hasAttempted = true, firstTryCorrectCount = if (state.hangmanMistakes == 0) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount))
            } else if (isLost) {
                val newIncorrect = (state.incorrectCardIds + card.id).distinct()
                // FSRS requires recording the failure
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
            val correct = if (state.quizPromptSide == "Front") card.back else card.front
            val isCorrect = selectedOption == correct

            processCardReview(card, isCorrect = isCorrect, isGraded = state.isGraded)

            if (isCorrect) {
                val already = state.attemptedCardIds.contains(card.id)
                updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount, hasAttempted = true, lastIncorrectAnswer = null, attemptedCardIds = if (already) state.attemptedCardIds else state.attemptedCardIds + card.id))
            } else {
                updateAndSaveStudyState(state.copy(hasAttempted = true, lastIncorrectAnswer = selectedOption, incorrectCardIds = (state.incorrectCardIds + card.id).distinct(), attemptedCardIds = (state.attemptedCardIds + card.id).distinct()))
            }
        }
    }

    fun submitQuizAnswer(answer: String) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]
            val correct = if (state.quizPromptSide == "Front") card.back else card.front
            val isCorrect = answer.equals(correct.replace(" ", ""), ignoreCase = true)

            processCardReview(card, isCorrect = isCorrect, isGraded = state.isGraded)

            if (isCorrect) {
                val already = state.attemptedCardIds.contains(card.id)
                updateAndSaveStudyState(state.copy(correctAnswerFound = true, firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount, hasAttempted = true, lastIncorrectAnswer = null, attemptedCardIds = if (already) state.attemptedCardIds else state.attemptedCardIds + card.id))
            } else {
                updateAndSaveStudyState(state.copy(hasAttempted = true, lastIncorrectAnswer = answer, attemptedCardIds = (state.attemptedCardIds + card.id).distinct()))
            }
        }
    }

    fun submitTypingCorrect() {
        getStudyState()?.let { state ->
            processCardReview(state.shuffledCards[state.currentCardIndex], isCorrect = true, isGraded = state.isGraded)
            updateAndSaveStudyState(state.copy(correctAnswerFound = true, hasAttempted = true, lastIncorrectAnswer = null))
        }
    }

    fun revealQuizAnswer() {
        getStudyState()?.let { state ->
            val card = state.shuffledCards[state.currentCardIndex]
            // Treating 'reveal' as a fail/Again for scheduling
            processCardReview(card, isCorrect = false, isGraded = state.isGraded)
            updateAndSaveStudyState(state.copy(correctAnswerFound = true, hasAttempted = true, lastIncorrectAnswer = "", incorrectCardIds = (state.incorrectCardIds + card.id).distinct()))
        }
    }

    fun flipStudyMode() { getStudyState()?.let { updateAndSaveStudyState(it.copy(isFlipped = !it.isFlipped)) } }

    fun flipCard() {
        getStudyState()?.let { state ->
            // In Normal mode, flipping was tracked as a review. In FSRS, mere flipping is likely not a graded review.
            // We maintain legacy behavior for Normal mode.
            val session = getAllActiveSessions().find { it.id == state.sessionId }
            val mode = session?.schedulingMode ?: "Normal"
            if (mode == "Normal" && !state.showFront) {
                processCardReview(state.shuffledCards[state.currentCardIndex], isCorrect = true, isGraded = false)
            }
            updateAndSaveStudyState(state.copy(showFront = !state.showFront, isCardRevealed = state.showFront || state.isCardRevealed))
        }
    }

    fun previousCard() { getStudyState()?.let { state -> if (state.currentCardIndex > 0) { val newState = state.copy(currentCardIndex = state.currentCardIndex - 1, wrongSelections = emptyList(), correctAnswerFound = false, showFront = true, hasAttempted = false, lastIncorrectAnswer = null, isCardRevealed = false); updateAndSaveStudyState(if (listOf("Multiple Choice", "Quiz", "Typing", "Anagram", "Flashcard Quiz", "Hangman").contains(newState.studyMode)) newState.copy(correctAnswerFound = true) else newState) } } }

    fun nextCard() {
        getStudyState()?.let { state ->
            // Legacy: Passive review on exit if Normal mode
            val session = getAllActiveSessions().find { it.id == state.sessionId }
            val mode = session?.schedulingMode ?: "Normal"
            if (mode == "Normal") {
                if (!state.showFront) processCardReview(state.shuffledCards[state.currentCardIndex], isCorrect = true, isGraded = false)
                processCardReview(state.shuffledCards[state.currentCardIndex], isCorrect = true, isGraded = false)
            }

            if (state.currentCardIndex < state.shuffledCards.size - 1) updateAndSaveStudyState(state.copy(currentCardIndex = state.currentCardIndex + 1, wrongSelections = emptyList(), correctAnswerFound = false, showFront = true, hasAttempted = false, lastIncorrectAnswer = null, isCardRevealed = false, hangmanMistakes = 0, guessedLetters = emptySet()))
            else {
                if (state.studyMode == "Quiz") {
                    val score = state.firstTryCorrectCount.toFloat() / state.shuffledCards.size
                    val deck = state.deckWithCards.deck
                    authAndSyncManager.saveDeckToFirestore(deck.copy(averageQuizScore = if (deck.averageQuizScore == null) score else (deck.averageQuizScore + score) / 2))
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

            processCardReview(card, isCorrect = isCorrect, isGraded = state.isGraded)

            if (isCorrect) {
                updateAndSaveStudyState(state.copy(correctAnswerFound = true, hasAttempted = true, firstTryCorrectCount = if (!already) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount, attemptedCardIds = newAttempted))
            } else {
                updateAndSaveStudyState(state.copy(wrongSelections = state.wrongSelections + option, hasAttempted = true, incorrectCardIds = (state.incorrectCardIds + card.id).distinct(), attemptedCardIds = newAttempted, correctAnswerFound = !state.allowMultipleGuesses))
            }
        }
    }

    fun generateOptionsForCurrentCardIfNeeded() {
        val state = getStudyState() ?: return
        if (state.studyMode != "Multiple Choice") return
        val card = state.shuffledCards.getOrNull(state.currentCardIndex) ?: return
        if (state.mcOptions.containsKey(card.id)) return
        val getOptionText: (Card) -> String = { (if (state.isFlipped) it.front else it.back).trim().lowercase() }
        val pool = if (state.limitAnswerPool) state.deckWithCards.cards.filter { it.difficulty in state.difficulties } else state.deckWithCards.cards
        val wrong = pool.filter { it.id != card.id }.distinctBy { getOptionText(it) }.shuffled().take(state.numberOfAnswers - 1)
        updateAndSaveStudyState(state.copy(mcOptions = state.mcOptions + (card.id to (wrong + card).shuffled().map { it.id })))
    }

    // --- Special Games (Memory & Crossword) ---

    fun initMemoryGrid() {
        getStudyState()?.let { state ->
            val remaining = state.shuffledCards.filter { it.id !in state.successfullyMatchedPairs }
            val batch = remaining.take(state.maxMemoryTiles / 2).map { it.id }
            if (batch.isEmpty()) updateAndSaveStudyState(state.copy(isComplete = true))
            else updateAndSaveStudyState(state.copy(memoryActiveCardIds = batch, memorySelected1 = null, memorySelected2 = null, memoryConsecutiveWrongSideTaps = 0))
        }
    }

    fun selectMemoryTile(cardId: String, side: String) {
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
                    if (count >= 3) { onToastMessage("Tap on a ${if (side == "Front") "Pink" else "Blue"} tile to match, or tap the selected ${if (side == "Front") "Blue" else "Pink"} tile."); updateAndSaveStudyState(state.copy(memoryConsecutiveWrongSideTaps = 0)) }
                    else updateAndSaveStudyState(state.copy(memoryConsecutiveWrongSideTaps = count))
                } else updateAndSaveStudyState(state.copy(memorySelected2 = cardId to side, memoryConsecutiveWrongSideTaps = 0))
            }
        }
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
        else updateAndSaveStudyState(state.copy(currentCardIndex = newIndex))
    }

    fun selectMatchingItem(cardId: String, side: String) {
        val state = getStudyState() ?: return
        if (state.matchingRevealPair.isNotEmpty()) {
            if (cardId in state.matchingRevealPair) {
                val newMatched = state.successfullyMatchedPairs + state.matchingRevealPair
                val newState = state.copy(successfullyMatchedPairs = newMatched, selectedMatchingItem = null, incorrectlyMatchedPair = null, matchingRevealPair = emptyList())
                updateAndSaveStudyState(newState)
                if (newMatched.size == state.matchingCardsOnScreen.size) {
                    if (state.currentCardIndex + state.matchingCardsOnScreen.size >= state.shuffledCards.size) updateAndSaveStudyState(newState.copy(isComplete = true))
                    else updateAndSaveStudyState(newState.copy(currentCardIndex = state.currentCardIndex + state.matchingCardsOnScreen.size))
                }
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
            if (newMatched.size == state.matchingCardsOnScreen.size) {
                if (state.currentCardIndex + state.matchingCardsOnScreen.size >= state.shuffledCards.size) updateAndSaveStudyState(newState.copy(isComplete = true))
                else updateAndSaveStudyState(newState.copy(currentCardIndex = state.currentCardIndex + state.matchingCardsOnScreen.size))
            }
        } else {
            val incIds = (state.incorrectCardIds + current.first + newSel.first).distinct()
            val incAtt = (state.matchingAttemptedIncorrectly + current.first).distinct()

            processCardReview(state.matchingCardsOnScreen.first { it.id == current.first }, isCorrect = false, isGraded = state.isGraded)

            if (!state.allowMultipleGuesses) updateAndSaveStudyState(state.copy(selectedMatchingItem = null, incorrectlyMatchedPair = null, matchingAttemptedIncorrectly = incAtt, incorrectCardIds = incIds, matchingRevealPair = listOf(current.first)))
            else updateAndSaveStudyState(state.copy(selectedMatchingItem = null, incorrectlyMatchedPair = current to newSel, matchingAttemptedIncorrectly = incAtt, incorrectCardIds = incIds))
        }
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
                if (indexInWord < activeWord.word.length - 1) nextCell = if (activeWord.isAcross) (selX + 1) to selY else selX to (selY + 1)
            }
            val newCompletedIds = if (state.showCorrectWords) state.crosswordWords.filter { word -> word.word.indices.all { i -> val x = if (word.isAcross) word.startX + i else word.startX; val y = if (word.isAcross) word.startY else word.startY + i; inputs["$x,$y"] == word.word[i] } }.map { it.id }.toSet() else emptySet()

            (newCompletedIds - state.completedWordIds).forEach { cardId ->
                state.shuffledCards.find { it.id == cardId }?.let { processCardReview(it, isCorrect = true, isGraded = state.isGraded) }
            }

            updateAndSaveStudyState(state.copy(crosswordUserInputs = inputs, crosswordSelectedCell = nextCell, isComplete = newCompletedIds.size == state.crosswordWords.size, completedWordIds = newCompletedIds))
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

    // --- Helpers (Card Filtering, Crossword Gen, etc.) ---

    fun getIncorrectCardInfo(selectedAnswer: String) { getStudyState()?.let { state -> val card = state.deckWithCards.cards.find { (if (state.isFlipped) it.front else it.back) == selectedAnswer }; if (card != null) onToastMessage(if (state.isFlipped) "Back: ${card.back}" else "Front: ${card.front}") } }

    /**
     * Unified method for processing reviews.
     * Handles both "Normal" mode (timestamps) and "Spaced Repetition" mode (FSRS algorithm).
     */
    private fun processCardReview(card: Card, isCorrect: Boolean, isGraded: Boolean, explicitRating: Int? = null) {
        getStudyState()?.let { state ->
            val session = getAllActiveSessions().find { it.id == state.sessionId }
            val mode = session?.schedulingMode ?: "Normal"

            // 1. If we are in FSRS Mode, we ALWAYS update FSRS fields.
            if (mode == "Spaced Repetition") {
                val rating = explicitRating ?: (if (isCorrect) FsrsAlgorithm.RATING_GOOD else FsrsAlgorithm.RATING_AGAIN)
                val result = FsrsAlgorithm.calculateNextState(card, rating, state.deckWithCards.deck)

                val newCard = card.copy(
                    fsrsStability = result.stability,
                    fsrsDifficulty = result.difficulty,
                    fsrsElapsedDays = result.elapsedDays,
                    fsrsScheduledDays = result.scheduledDays,
                    fsrsState = result.state,
                    fsrsLastReview = System.currentTimeMillis(),
                    fsrsLapses = if (!isCorrect) card.fsrsLapses + 1 else card.fsrsLapses,

                    // Keep legacy fields in sync
                    reviewedAt = System.currentTimeMillis(),
                    reviewedCount = card.reviewedCount + 1,
                    gradedAttempts = if (isGraded) card.gradedAttempts + System.currentTimeMillis() else card.gradedAttempts,
                    incorrectAttempts = if (!isCorrect) card.incorrectAttempts + System.currentTimeMillis() else card.incorrectAttempts
                )
                authAndSyncManager.saveCardToFirestore(newCard)
            }
            // 2. If in Normal Mode, we ONLY update FSRS if it was a GRADED review (Active Recall).
            // Passive flipping does NOT update FSRS.
            else if (isGraded) {
                // Map Normal Mode results to FSRS:
                // Correct -> Good (3)
                // Incorrect -> Again (1)
                val rating = if (isCorrect) FsrsAlgorithm.RATING_GOOD else FsrsAlgorithm.RATING_AGAIN

                // We calculate the new state but we do NOT change the scheduling mode of the session.
                // We just silently update the card's memory state in the background.
                val result = FsrsAlgorithm.calculateNextState(card, rating, state.deckWithCards.deck)

                val newCard = card.copy(
                    fsrsStability = result.stability,
                    fsrsDifficulty = result.difficulty,
                    fsrsElapsedDays = result.elapsedDays,
                    fsrsScheduledDays = result.scheduledDays,
                    fsrsState = result.state,
                    fsrsLastReview = System.currentTimeMillis(),
                    fsrsLapses = if (!isCorrect) card.fsrsLapses + 1 else card.fsrsLapses,

                    reviewedAt = System.currentTimeMillis(),
                    reviewedCount = card.reviewedCount + 1,
                    gradedAttempts = card.gradedAttempts + System.currentTimeMillis(),
                    incorrectAttempts = if (!isCorrect) card.incorrectAttempts + System.currentTimeMillis() else card.incorrectAttempts
                )
                authAndSyncManager.saveCardToFirestore(newCard)
            }
            // 3. Normal Mode + Non-Graded (e.g. just flipping to read)
            // Do NOT touch FSRS fields. Just update legacy timestamps if needed.
            else {
                if (isCorrect) { // Treat "isCorrect" here as "Viewed"
                    val newCard = card.copy(
                        reviewedAt = System.currentTimeMillis(),
                        reviewedCount = card.reviewedCount + 1
                    )
                    authAndSyncManager.saveCardToFirestore(newCard)
                }
                // If incorrect in non-graded mode, we usually don't track it, or just incorrectAttempts.
                else {
                    val newCard = card.copy(
                        incorrectAttempts = card.incorrectAttempts + System.currentTimeMillis()
                    )
                    authAndSyncManager.saveCardToFirestore(newCard)
                }
            }
        }
    }

    // Explicit public method to handle grading from AudioService
    fun handleGradingResult(cardId: String, isCorrect: Boolean) {
        getStudyState()?.let { state ->
            val card = state.shuffledCards.find { it.id == cardId } ?: return@let

            processCardReview(card, isCorrect = isCorrect, isGraded = true)

            val alreadyAttempted = state.attemptedCardIds.contains(cardId)
            val newAttemptedList = if (alreadyAttempted) state.attemptedCardIds else state.attemptedCardIds + cardId

            if (isCorrect) {
                val newScore = if (!alreadyAttempted) state.firstTryCorrectCount + 1 else state.firstTryCorrectCount
                updateAndSaveStudyState(state.copy(firstTryCorrectCount = newScore, attemptedCardIds = newAttemptedList))
            } else {
                val newIncorrectIds = (state.incorrectCardIds + card.id).distinct()
                updateAndSaveStudyState(state.copy(incorrectCardIds = newIncorrectIds, attemptedCardIds = newAttemptedList))
            }
        }
    }

    private fun logIncorrectAttempt(card: Card) {
        // Deprecated internal helper, redirected to processCardReview for consistency if needed,
        // but mostly replaced by direct processCardReview calls with isCorrect=false.
        processCardReview(card, isCorrect = false, isGraded = true)
    }



    // --- Crossword Logic ---

    private fun generateCrossword(cards: List<Card>, promptSide: String, density: Int): Pair<List<CrosswordWord>, Pair<Int, Int>> {
        val wordList = cards.map { card ->
            val answer = (if (promptSide == "Front") card.back else card.front).trim().uppercase().filter { it.isLetter() }
            val clue = if (promptSide == "Front") card.front else card.back
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
}