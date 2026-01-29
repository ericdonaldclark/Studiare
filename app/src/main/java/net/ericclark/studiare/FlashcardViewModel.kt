package net.ericclark.studiare

import android.app.Application
import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import net.ericclark.studiare.BuildConfig
import net.ericclark.studiare.data.*
import net.ericclark.studiare.components.*
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.ericclark.studiare.components.AudioServiceManager
import net.ericclark.studiare.components.AuthAndSyncManager
import net.ericclark.studiare.components.CardUtils
import net.ericclark.studiare.components.ImportExportManager
import net.ericclark.studiare.components.StudySessionManager
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.data.AutoSetConfig
import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.CardDataForSave
import net.ericclark.studiare.data.Deck
import net.ericclark.studiare.data.DeckWithCards
import net.ericclark.studiare.data.DuplicateCheckResult
import net.ericclark.studiare.data.DuplicateInfo
import net.ericclark.studiare.data.OverwriteConfirmationData
import net.ericclark.studiare.data.StudyState
import net.ericclark.studiare.data.TagDefinition
import java.util.UUID
import kotlin.math.max
import kotlin.math.min
import android.widget.Toast

enum class ConflictResolutionStrategy {
    USE_CLOUD_WIPE_LOCAL,
    USE_LOCAL_WIPE_CLOUD,
    MERGE_KEEP_LOCAL, // Overwrite cloud matches with local
    MERGE_KEEP_CLOUD  // Keep cloud matches, add new local
}
/**
 * The main ViewModel for the application. It handles business logic and delegates
 * infrastructure/syncing/audio/study operations to specialized Managers.
 */
class FlashcardViewModel(application: Application) : AndroidViewModel(application) {

    // --- Core Dependencies ---
    private val preferenceManager = PreferenceManager(application)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val cardUtils = CardUtils()

    // --- Managers ---
    private val authAndSyncManager: AuthAndSyncManager
    private val audioServiceManager: AudioServiceManager
    private val importExportManager: ImportExportManager
    private val studySessionManager: StudySessionManager

    // --- Constants / Utils ---
    private val TAG = "FlashcardViewModel"
    val buildTime: Long = BuildConfig.BUILD_TIME

    // --- Coroutine Handling ---
    private val errorHandler = CoroutineExceptionHandler { _, exception ->
        AppLogger.e(TAG, "Uncaught coroutine exception", exception)
    }

    // --- Delegated State Flows (Auth & Data) ---
    val isUserAnonymous: StateFlow<Boolean> get() = authAndSyncManager.isUserAnonymous
    val userEmail: StateFlow<String?> get() = authAndSyncManager.userEmail
    val isSyncSetupPending: StateFlow<Boolean> get() = authAndSyncManager.isSyncSetupPending
    val showConflictDialog: StateFlow<Boolean> get() = authAndSyncManager.showConflictDialog
    val tags: StateFlow<List<TagDefinition>> get() = authAndSyncManager.localTags

    // --- Delegated State Flows (Audio) ---
    val audioIsListening: StateFlow<Boolean> get() = audioServiceManager.audioIsListening
    val audioFeedback: StateFlow<String?> get() = audioServiceManager.audioFeedback
    val audioWaitingForGrade: StateFlow<Boolean> get() = audioServiceManager.audioWaitingForGrade
    val audioCardIndex: StateFlow<Int> get() = audioServiceManager.audioCardIndex
    val audioIsFlipped: StateFlow<Boolean> get() = audioServiceManager.audioIsFlipped
    val audioIsPlaying: StateFlow<Boolean> get() = audioServiceManager.audioIsPlaying
    val isAudioServiceBound: StateFlow<Boolean> get() = audioServiceManager.isAudioServiceBound

    // --- Internal Helpers for Data Access ---
    private val localDecks: List<Deck> get() = authAndSyncManager.localDecks.value ?: emptyList()
    private val localCards: List<Card> get() = authAndSyncManager.localCards.value ?: emptyList()
    private val currentUserId: String? get() = authAndSyncManager.userId.value

    // --- ViewModel UI State ---
    private val _allDecksWithCards = MutableLiveData<List<DeckWithCards>>(emptyList())
    val allDecks: LiveData<List<DeckWithCards>> = _allDecksWithCards

    var studyState by mutableStateOf<StudyState?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set

    var isProcessing by mutableStateOf(false)
        private set

    // UI State Flows
    private val _editorDuplicateResult = MutableStateFlow<DuplicateCheckResult?>(null)
    val editorDuplicateResult: StateFlow<DuplicateCheckResult?> = _editorDuplicateResult

    private val _importDuplicateQueue = MutableStateFlow<List<DuplicateCheckResult>>(emptyList())
    val importDuplicateQueue: StateFlow<List<DuplicateCheckResult>> = _importDuplicateQueue

    private val _overwriteConfirmation = MutableStateFlow<OverwriteConfirmationData?>(null)
    val overwriteConfirmation: StateFlow<OverwriteConfirmationData?> = _overwriteConfirmation

    val hasPromptedHdLanguages: StateFlow<Boolean> = preferenceManager.hasPromptedHdLanguagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    var toastMessage by mutableStateOf<String?>(null)
        private set

    val themeMode: StateFlow<Int>

    private val _allActiveSessions: StateFlow<List<ActiveSession>>
    private val _currentDeckId = MutableStateFlow<String?>(null)
    val activeSessions: StateFlow<List<ActiveSession>>

    val databaseVersion: Int = 10
    val lastExportTimestamp: StateFlow<Long>
    val lastImportTimestamp: StateFlow<Long>

    val downloadedHdLanguages: StateFlow<Set<String>> = preferenceManager.downloadedHdLanguagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val memoryGridColumnsPortrait: StateFlow<Int> = preferenceManager.memoryGridColumnsPortraitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val memoryGridColumnsLandscape: StateFlow<Int> = preferenceManager.memoryGridColumnsLandscapeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    init {
        // Initialize Theme & Preferences
        themeMode = preferenceManager.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)
        _allActiveSessions = preferenceManager.activeSessionsFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
        lastExportTimestamp = preferenceManager.lastExportTimestampFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
        lastImportTimestamp = preferenceManager.lastImportTimestampFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        activeSessions = combine(_allActiveSessions, _currentDeckId) { sessions, deckId ->
            if (deckId == null) emptyList() else sessions.filter { it.deckId == deckId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // 1. Initialize AuthAndSyncManager
        authAndSyncManager = AuthAndSyncManager(
            db = db,
            auth = auth,
            viewModelScope = viewModelScope,
            onProcessingChanged = { isProcessing = it }
        )

        // 2. Initialize StudySessionManager
        studySessionManager = StudySessionManager(
            preferenceManager = preferenceManager,
            authAndSyncManager = authAndSyncManager,
            cardUtils = cardUtils,
            viewModelScope = viewModelScope,
            getStudyState = { studyState },
            setStudyState = { studyState = it },
            getAllDecks = { _allDecksWithCards.value ?: emptyList() },
            getAllActiveSessions = { _allActiveSessions.value },
            onToastMessage = { toastMessage = it }
        )

        // 3. Initialize AudioServiceManager
        audioServiceManager = AudioServiceManager(
            context = application,
            preferenceManager = preferenceManager,
            viewModelScope = viewModelScope,
            getCurrentStudyState = { studyState },
            onAudioProgressUpdate = { index -> updateAudioSessionProgress(index) },
            onGradingResult = { cardId, isCorrect ->
                // Delegate grading logic to StudySessionManager
                studySessionManager.handleGradingResult(cardId, isCorrect)
            }
        )

        // 4. Observe Data Changes from Manager
        viewModelScope.launch {
            combine(authAndSyncManager.localDecks, authAndSyncManager.localCards) { decks, cards ->
                if (decks != null && cards != null) {
                    combineDecksAndCards(decks, cards)
                }
            }.collect {}
        }

        // 5. Initialize ImportExportManager
        importExportManager = ImportExportManager(
            db = db,
            preferenceManager = preferenceManager,
            viewModelScope = viewModelScope,
            userIdProvider = { currentUserId },
            getLocalDecks = { localDecks },
            getLocalCards = { localCards },
            onProcessingChanged = { isProcessing = it },
            onOverwriteConfirmationChanged = { _overwriteConfirmation.value = it },
            getOverwriteConfirmation = { _overwriteConfirmation.value },
            safeWrite = { task -> authAndSyncManager.safeWrite(task) },
            saveDeckToFirestore = { deck -> authAndSyncManager.saveDeckToFirestore(deck) },
            saveCardToFirestore = { card -> authAndSyncManager.saveCardToFirestore(card) }
        )
    }

    override fun onCleared() {
        super.onCleared()
        authAndSyncManager.cleanup()
    }

    // --- Delegation to AuthAndSyncManager ---

    fun linkGoogleAccount(credential: AuthCredential, onResult: (Boolean, String?) -> Unit) {
        authAndSyncManager.linkGoogleAccount(credential, onResult)
    }

    fun resolveConflict(strategy: ConflictResolutionStrategy) {
        authAndSyncManager.resolveConflict(strategy)
    }

    fun signOut() {
        authAndSyncManager.signOut()
    }

    // --- Delegation to AudioServiceManager ---

    fun bindAudioService() {
        audioServiceManager.bindAudioService()
    }

    fun unbindAudioService() {
        audioServiceManager.unbindAudioService()
    }

    fun toggleAudioPlayPause() {
        audioServiceManager.toggleAudioPlayPause()
    }

    fun skipAudioNext() {
        audioServiceManager.skipAudioNext()
    }

    fun skipAudioPrevious() {
        audioServiceManager.skipAudioPrevious()
    }

    fun skipAudioStt() {
        audioServiceManager.skipAudioStt()
    }

    fun setAudioContinuousPlay(enabled: Boolean) {
        audioServiceManager.setAudioContinuousPlay(enabled)
    }

    fun updateAudioDelays(answerDelaySeconds: Double, nextCardDelaySeconds: Double) {
        audioServiceManager.updateAudioDelays(answerDelaySeconds, nextCardDelaySeconds)
    }

    fun revealAudioAnswer() {
        audioServiceManager.revealAudioAnswer()
    }

    // --- Delegation to ImportExportManager ---

    fun getDecksAsString(decksToExport: List<DeckWithCards>, format: String): String {
        return importExportManager.getDecksAsString(decksToExport, format)
    }

    fun importDecksFromString(content: String, mimeType: String?) {
        importExportManager.importDecksFromString(content, mimeType)
    }

    fun cancelImport() {
        importExportManager.cancelImport()
    }

    fun proceedWithImport(selectedIdsToOverwrite: List<String>) {
        importExportManager.proceedWithImport(selectedIdsToOverwrite)
    }

    private fun processNextInImportQueue() { _importDuplicateQueue.value = _importDuplicateQueue.value.drop(1) }
    fun dismissImportDuplicateWarning() { processNextInImportQueue() }

    fun saveImportWithDuplicatesRemoved() {
        _importDuplicateQueue.value.firstOrNull()?.let { result ->
            val distinctCards = result.cardsToSave.distinctBy { it.front.normalizeForDuplicateCheck() to it.back.normalizeForDuplicateCheck() }
            saveDeckWithCards(result.deckId, result.deckName, distinctCards, result.normalizationType, result.sortType, result.parentDeckId, null, result.frontLanguage, result.backLanguage)
        }
        processNextInImportQueue()
    }

    fun saveImportIgnoringDuplicates() {
        _importDuplicateQueue.value.firstOrNull()?.let { result ->
            saveDeckWithCards(result.deckId, result.deckName, result.cardsToSave, result.normalizationType, result.sortType, result.parentDeckId, null, result.frontLanguage, result.backLanguage)
        }
        processNextInImportQueue()
    }

    // --- Delegation to AudioServiceManager (HD Audio / Sherpa) ---

    fun setHdAudioPrompted(prompted: Boolean = true) {
        audioServiceManager.setHdAudioPrompted(prompted)
    }

    fun getUniqueDeckLanguages(): List<String> {
        return audioServiceManager.getUniqueDeckLanguages(_allDecksWithCards.value ?: emptyList())
    }

    fun getFormattedModelSize(langCode: String): String {
        return audioServiceManager.getFormattedModelSize(langCode)
    }

    fun startHdLanguageDownload(context: Context, languages: List<String>) {
        audioServiceManager.startHdLanguageDownload(languages)
    }

    fun deleteHdLanguage(context: Context, language: String) {
        audioServiceManager.deleteHdLanguage(language) { msg ->
            toastMessage = msg
        }
    }
    fun deleteAllHdLanguages(context: Context) {
        audioServiceManager.deleteAllHdLanguages { msg ->
            toastMessage = msg
        }
    }

    // --- Delegation to StudySessionManager (Study Logic) ---

    fun startStudySession(
        parentDeck: DeckWithCards, mode: String, isWeighted: Boolean, numCards: Int, quizPromptSide: String, numAnswers: Int,
        showCorrectLetters: Boolean, limitAnswerPool: Boolean, isGraded: Boolean, selectAnswer: Boolean, allowMultipleGuesses: Boolean,
        enableStt: Boolean, hideAnswerText: Boolean, fingersAndToes: Boolean, maxMemoryTiles: Int, gridDensity: Int, config: AutoSetConfig,
        onSessionCreated: () -> Unit
    ) {
        studySessionManager.startStudySession(parentDeck, mode, isWeighted, numCards, quizPromptSide, numAnswers, showCorrectLetters, limitAnswerPool, isGraded, selectAnswer, allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes, maxMemoryTiles, gridDensity, config, onSessionCreated)
    }
    fun submitFsrsGrade(rating: Int) { studySessionManager.submitFsrsGrade(rating) }

    fun submitAudioFsrsGrade(rating: Int) {
        // 1. Submit the grade to FSRS logic (updates DB)
        submitFsrsGrade(rating)
        // 2. Tell the audio service to stop waiting and proceed
        audioServiceManager.resumeAfterGrade()
    }
    fun restartStudySession() { studySessionManager.restartStudySession() }
    fun restartSameSession() { studySessionManager.restartSameSession() }
    fun resumeStudySession(session: ActiveSession) { studySessionManager.resumeStudySession(session) }
    fun endStudySession() { studySessionManager.endStudySession() }
    fun deleteCurrentStudySession() { studySessionManager.deleteCurrentStudySession() }
    fun deleteSession(session: ActiveSession) { studySessionManager.deleteSession(session) }
    fun copySession(session: ActiveSession) { studySessionManager.copySession(session) }
    fun restartSession(session: ActiveSession) { studySessionManager.restartSession(session) }
    fun startReviewSession(onSessionStarted: (route: String) -> Unit) { studySessionManager.startReviewSession(onSessionStarted) }

    fun submitSelfGradedResult(isCorrect: Boolean) { studySessionManager.submitSelfGradedResult(isCorrect) }
    fun submitHangmanGuess(char: Char) { studySessionManager.submitHangmanGuess(char) }
    fun submitFlashcardQuizAnswer(selected: String) { studySessionManager.submitFlashcardQuizAnswer(selected) }
    fun submitQuizAnswer(answer: String) { studySessionManager.submitQuizAnswer(answer) }
    fun submitTypingCorrect() { studySessionManager.submitTypingCorrect() }
    fun selectAnswer(option: String) { studySessionManager.selectAnswer(option) }
    fun revealQuizAnswer() { studySessionManager.revealQuizAnswer() }
    fun generateOptionsForCurrentCardIfNeeded() { studySessionManager.generateOptionsForCurrentCardIfNeeded() }

    fun flipCard() { studySessionManager.flipCard() }
    fun flipStudyMode() { studySessionManager.flipStudyMode() }
    fun nextCard() { studySessionManager.nextCard() }
    fun previousCard() { studySessionManager.previousCard() }

    fun initMemoryGrid() { studySessionManager.initMemoryGrid() }
    fun selectMemoryTile(cardId: String, side: String) { studySessionManager.selectMemoryTile(cardId, side) }

    fun selectCrosswordWord(wordId: String) { studySessionManager.selectCrosswordWord(wordId) }
    fun selectCrosswordCell(x: Int, y: Int) { studySessionManager.selectCrosswordCell(x, y) }
    fun submitCrosswordChar(char: Char) { studySessionManager.submitCrosswordChar(char) }
    fun provideCrosswordHint(wordId: String, fillEntireWord: Boolean) { studySessionManager.provideCrosswordHint(wordId, fillEntireWord) }

    fun startNewMatchingRound(cardsPerColumn: Int) {
        studySessionManager.startNewMatchingRound(cardsPerColumn)
    }

    fun advanceMatchingRound() {
        studySessionManager.advanceMatchingRound()
    }

    fun selectMatchingItem(cardId: String, side: String) {
        studySessionManager.selectMatchingItem(cardId, side)
    }

    fun getIncorrectCardInfo(selectedAnswer: String) { studySessionManager.getIncorrectCardInfo(selectedAnswer) }
    fun clearToastMessage() { toastMessage = null }

    // --- Core Logic & Data Combination ---

    private fun combineDecksAndCards(decks: List<Deck>, cards: List<Card>) {
        viewModelScope.launch(Dispatchers.Default) {
            val decksMap = decks.associateBy { it.id }
            val cardsMap = cards.associateBy { it.id }

            val combined = decks.map { deck ->
                val effectiveDeck = if (deck.parentDeckId != null) {
                    val parent = decksMap[deck.parentDeckId]
                    if (parent != null) {
                        deck.copy(
                            frontLanguage = parent.frontLanguage,
                            backLanguage = parent.backLanguage
                        )
                    } else {
                        deck
                    }
                } else {
                    deck
                }

                val deckCards = effectiveDeck.cardIds.mapNotNull { id -> cardsMap[id] }
                DeckWithCards(effectiveDeck, deckCards)
            }.sortedBy { it.deck.name }

            _allDecksWithCards.postValue(combined)

            withContext(Dispatchers.Main) {
                isLoading = false
            }
        }
    }

    private fun updateAudioSessionProgress(index: Int) {
        studyState?.let { state ->
            if (state.currentCardIndex != index) {
                val newState = state.copy(currentCardIndex = index)
                // We manually save state update here or expose method in StudySessionManager
                // Since this is just a progress update, we can update local state and let manager/persistence handle next save
                studyState = newState
            }
        }
    }

    fun setCurrentDeckId(deckId: String?) {
        _currentDeckId.value = deckId
    }

    fun setThemeMode(mode: Int) {
        viewModelScope.launch { preferenceManager.setThemeMode(mode) }
    }

    fun setMemoryGridColumns(portrait: Int, landscape: Int) {
        viewModelScope.launch {
            preferenceManager.setMemoryGridColumns(portrait, landscape)
        }
    }

    // --- Editor & CRUD Helpers ---

    private fun String.normalizeForDuplicateCheck(): String = this.filter { it.isLetterOrDigit() }.lowercase()

    fun checkForDuplicatesInEditor(deckId: String?, deckName: String, cards: List<CardDataForSave>, normalizationType: Int, sortType: Int, parentDeckId: String?, frontLanguage: String, backLanguage: String) {
        val duplicates = cards.groupBy { it.front.normalizeForDuplicateCheck() to it.back.normalizeForDuplicateCheck() }.filter { it.value.size > 1 }.map { (pair, group) ->
            DuplicateInfo(
                "Front: '${group.first().front}'",
                group.size
            )
        }
        if (duplicates.isNotEmpty()) _editorDuplicateResult.value = DuplicateCheckResult(
            duplicates,
            deckId,
            deckName,
            cards,
            normalizationType,
            sortType,
            parentDeckId,
            frontLanguage,
            backLanguage
        )
        else saveDeckWithCards(deckId, deckName, cards, normalizationType, sortType, parentDeckId, null, frontLanguage, backLanguage)
    }

    fun clearDeckReviewData(deckId: String) {
        val uid = currentUserId ?: return
        val deck = localDecks.find { it.id == deckId } ?: return
        val cardIds = deck.cardIds
        val cardsToReset = localCards.filter { it.id in cardIds }

        if (cardsToReset.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isProcessing = true }
            try {
                cardsToReset.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { card ->
                        val resetCard = card.copy(
                            reviewedCount = 0,
                            gradedAttempts = emptyList(),
                            incorrectAttempts = emptyList(),
                            reviewedAt = null,
                            isKnown = false,
                            // Reset FSRS fields to default (New state)
                            fsrsStability = null,
                            fsrsDifficulty = null,
                            fsrsElapsedDays = null,
                            fsrsScheduledDays = null,
                            fsrsState = 0, // STATE_NEW
                            fsrsLastReview = null,
                            fsrsLapses = 0
                        )
                        batch.set(db.collection("users").document(uid).collection("cards").document(card.id), resetCard, SetOptions.merge())
                        authAndSyncManager.saveCardToFirestore(resetCard)
                    }
                    authAndSyncManager.safeWrite(batch.commit())
                }
                withContext(Dispatchers.Main) {
                    toastMessage = "Review data cleared for ${cardsToReset.size} cards."
                }
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to clear review data", e)
                withContext(Dispatchers.Main) {
                    toastMessage = "Failed to clear review data."
                }
            } finally {
                withContext(Dispatchers.Main) { isProcessing = false }
            }
        }
    }

    fun dismissEditorDuplicateWarning() { _editorDuplicateResult.value = null }
    fun saveEditorWithDuplicatesRemoved() { _editorDuplicateResult.value?.let { result -> saveDeckWithCards(result.deckId, result.deckName, result.cardsToSave.distinctBy { it.front.normalizeForDuplicateCheck() to it.back.normalizeForDuplicateCheck() }, result.normalizationType, result.sortType, result.parentDeckId, null, result.frontLanguage, result.backLanguage) }; dismissEditorDuplicateWarning() }
    fun saveEditorIgnoringDuplicates() { _editorDuplicateResult.value?.let { result -> saveDeckWithCards(result.deckId, result.deckName, result.cardsToSave, result.normalizationType, result.sortType, result.parentDeckId, null, result.frontLanguage, result.backLanguage) }; dismissEditorDuplicateWarning() }

    private fun saveDeckWithCards(
        deckId: String?,
        deckName: String,
        cardsToSave: List<CardDataForSave>,
        normalizationType: Int,
        sortType: Int,
        parentDeckId: String? = null,
        isStarred: Boolean? = null,
        frontLanguage: String,
        backLanguage: String
    ) {
        val uid = currentUserId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val id = deckId ?: UUID.randomUUID().toString()
            val existingDeck = localDecks.find { it.id == id }
            val cardIds = cardsToSave.map { it.id }
            val deck = Deck(
                id = id,
                name = deckName,
                parentDeckId = parentDeckId ?: existingDeck?.parentDeckId,
                createdAt = existingDeck?.createdAt ?: System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
                averageQuizScore = existingDeck?.averageQuizScore,
                normalizationType = normalizationType,
                sortType = sortType,
                isStarred = isStarred ?: existingDeck?.isStarred ?: false,
                cardIds = cardIds,
                frontLanguage = frontLanguage,
                backLanguage = backLanguage
            )

            cardsToSave.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { cd ->
                    val ex = localCards.find { it.id == cd.id }
                    val card = Card(
                        id = cd.id,
                        front = cd.front,
                        back = cd.back,
                        frontNotes = cd.frontNotes,
                        backNotes = cd.backNotes,
                        difficulty = cd.difficulty,
                        isKnown = cd.isKnown,
                        reviewedAt = ex?.reviewedAt,
                        reviewedCount = ex?.reviewedCount ?: cd.reviewedCount,
                        gradedAttempts = ex?.gradedAttempts ?: cd.gradedAttempts,
                        incorrectAttempts = ex?.incorrectAttempts ?: cd.incorrectAttempts,
                        tags = cd.tags,
                        ownerDeckId = if (parentDeckId == null) id else ex?.ownerDeckId
                    )
                    batch.set(db.collection("users").document(uid).collection("cards").document(card.id), card, SetOptions.merge())
                }
                authAndSyncManager.safeWrite(batch.commit())
            }
            authAndSyncManager.saveDeckToFirestore(deck)
            if (deck.parentDeckId != null) localDecks.find { it.id == deck.parentDeckId }?.let { parent ->
                authAndSyncManager.saveDeckToFirestore(parent.copy(updatedAt = System.currentTimeMillis(), cardIds = (parent.cardIds + cardIds).distinct()))
            }

            (existingDeck?.cardIds ?: emptyList()).filter { it !in cardIds }.let { removed ->
                if (removed.isNotEmpty()) {
                    val batch = db.batch(); var count = 0
                    removed.forEach { rid ->
                        if (localDecks.none { d -> d.id != id && d.cardIds.contains(rid) }) {
                            batch.delete(db.collection("users").document(uid).collection("cards").document(rid)); count++
                        }
                    }
                    if (count > 0) authAndSyncManager.safeWrite(batch.commit())
                    handleCardDeletionsInSessions(removed)
                }
            }
        }
    }

    fun createSet(parentDeckId: String, setName: String, cardIds: List<String>) { authAndSyncManager.saveDeckToFirestore(
        Deck(UUID.randomUUID().toString(), setName, parentDeckId, cardIds = cardIds)
    ) }
    fun updateSet(setId: String, setName: String, cardIds: List<String>) { localDecks.find { it.id == setId }?.let { authAndSyncManager.saveDeckToFirestore(it.copy(name = setName, cardIds = cardIds, updatedAt = System.currentTimeMillis())) } }

    fun createAutomaticSets(parentDeck: DeckWithCards, config: AutoSetConfig, startCardId: String? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            var pool = cardUtils.getFilteredAndSortedCards(parentDeck, config)
            if (startCardId != null) pool = pool.dropWhile { it.id != startCardId }
            val chunks = when (config.mode) { "One" -> listOf(pool.take(config.maxCardsPerSet)); "Multiple" -> pool.take(config.numSets * config.maxCardsPerSet).chunked(config.maxCardsPerSet); "Split All" -> pool.chunked(config.maxCardsPerSet); else -> emptyList() }
            val existing = localDecks.filter { it.parentDeckId == parentDeck.deck.id }
            val nextNum = (existing.mapNotNull { it.name.removePrefix("Set ").toIntOrNull() }.maxOrNull() ?: 0) + 1
            chunks.forEachIndexed { i, chunk -> if (chunk.isNotEmpty()) createSet(parentDeck.deck.id, "Set ${nextNum + i}", chunk.map { it.id }) }
        }
    }

    fun deleteDeck(deckId: String) {
        val deck = localDecks.find { it.id == deckId } ?: return
        authAndSyncManager.deleteDeckFromFirestore(deckId)
        localDecks.filter { it.parentDeckId == deckId }.forEach { authAndSyncManager.deleteDeckFromFirestore(it.id) }
        deck.cardIds.forEach { cid -> if (localDecks.none { d -> d.id != deckId && d.cardIds.contains(cid) }) { authAndSyncManager.deleteCardFromFirestore(cid); handleCardDeletionsInSessions(listOf(cid)) } }
    }

    fun toggleDeckStar(deck: Deck) { authAndSyncManager.saveDeckToFirestore(deck.copy(isStarred = !deck.isStarred, updatedAt = System.currentTimeMillis())) }

    fun deleteAllDecks() {
        val uid = currentUserId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isProcessing = true }
            try {
                localDecks.chunked(400).forEach { chunk -> val batch = db.batch(); chunk.forEach { batch.delete(db.collection("users").document(uid).collection("decks").document(it.id)) }; authAndSyncManager.safeWrite(batch.commit()) }
                localCards.chunked(400).forEach { chunk -> val batch = db.batch(); chunk.forEach { batch.delete(db.collection("users").document(uid).collection("cards").document(it.id)) }; authAndSyncManager.safeWrite(batch.commit()) }
                preferenceManager.saveActiveSessions(emptyList())
            } catch (e: Exception) { AppLogger.e(TAG, "deleteAllDecks failed", e) }
            finally { withContext(Dispatchers.Main) { isProcessing = false } }
        }
    }

    fun deleteAllSetsForDeck(parentDeckId: String) {
        val uid = currentUserId ?: return
        val sets = localDecks.filter { it.parentDeckId == parentDeckId }
        viewModelScope.launch(Dispatchers.IO) {
            sets.chunked(400).forEach { chunk -> val batch = db.batch(); chunk.forEach { batch.delete(db.collection("users").document(uid).collection("decks").document(it.id)) }; authAndSyncManager.safeWrite(batch.commit()) }
            preferenceManager.saveActiveSessions(_allActiveSessions.value.filterNot { it.deckId in sets.map { s -> s.id } })
        }
    }

    fun deleteAllSessionsForDeck(deckId: String) { viewModelScope.launch { preferenceManager.saveActiveSessions(_allActiveSessions.value.filterNot { it.deckId == deckId }) } }

    fun deleteCard(cardId: String) {
        authAndSyncManager.deleteCardFromFirestore(cardId)
        handleCardDeletionsInSessions(listOf(cardId))
        localDecks.filter { it.cardIds.contains(cardId) }.forEach { authAndSyncManager.saveDeckToFirestore(it.copy(cardIds = it.cardIds - cardId)) }
    }

    private fun handleCardDeletionsInSessions(deletedIds: List<String>) {
        viewModelScope.launch {
            val updated = _allActiveSessions.value.mapNotNull { s ->
                if (s.shuffledCardIds.any { it in deletedIds }) {
                    val newIds = s.shuffledCardIds.filter { it !in deletedIds }
                    if (newIds.isEmpty()) null else s.copy(shuffledCardIds = newIds, totalCards = newIds.size, currentCardIndex = (s.currentCardIndex - s.shuffledCardIds.take(s.currentCardIndex).count { it in deletedIds }).coerceIn(0, max(0, newIds.size - 1)))
                } else s
            }
            preferenceManager.saveActiveSessions(updated)
        }
    }

    fun updateCard(card: Card) {
        authAndSyncManager.saveCardToFirestore(card)
        localDecks.filter { it.cardIds.contains(card.id) }.forEach { authAndSyncManager.saveDeckToFirestore(it.copy(updatedAt = System.currentTimeMillis())) }
        studyState?.let { state -> studyState = state.copy(shuffledCards = state.shuffledCards.map { if (it.id == card.id) card else it }, deckWithCards = state.deckWithCards.copy(cards = state.deckWithCards.cards.map { if (it.id == card.id) card else it })) }
    }

    fun updateCardDifficulty(card: Card, diff: Int) { updateCard(card.copy(difficulty = diff)) }
    fun toggleCardKnownStatus(card: Card) { val new = card.copy(isKnown = !card.isKnown); authAndSyncManager.saveCardToFirestore(new); if (new.isKnown) handleCardDeletionsInSessions(listOf(card.id)); studyState?.let { s -> studyState = s.copy(shuffledCards = s.shuffledCards.map { if (it.id == card.id) new else it }, deckWithCards = s.deckWithCards.copy(cards = s.deckWithCards.cards.map { if (it.id == card.id) new else it })) } }

    // --- Tag Operations (Delegated) ---
    fun saveTagDefinition(tag: TagDefinition) { authAndSyncManager.saveTagDefinition(tag) }
    fun deleteTagDefinition(tag: TagDefinition) { authAndSyncManager.deleteTagDefinition(tag) }

    fun renameTag(tag: TagDefinition, oldName: String) {
        val uid = currentUserId ?: return
        if (tag.name.trim() == oldName) { saveTagDefinition(tag); return }
        viewModelScope.launch(Dispatchers.IO) {
            authAndSyncManager.saveTagDefinition(tag)
            localCards.filter { it.tags.contains(oldName) }.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { card -> batch.update(db.collection("users").document(uid).collection("cards").document(card.id), "tags", card.tags.map { if (it == oldName) tag.name.trim() else it }) }
                authAndSyncManager.safeWrite(batch.commit())
            }
        }
    }

    fun removeTagFromCards(tagName: String, cardIds: List<String>) {
        val uid = currentUserId ?: return
        viewModelScope.launch(Dispatchers.IO) {
            cardIds.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { id -> batch.update(db.collection("users").document(uid).collection("cards").document(id), "tags", FieldValue.arrayRemove(tagName)) }
                authAndSyncManager.safeWrite(batch.commit())
            }
        }
    }

    fun getCardsForTag(tagName: String): List<DeckWithCards> {
        return _allDecksWithCards.value?.mapNotNull { d -> val tagged = d.cards.filter { it.tags.contains(tagName) }; if (tagged.isNotEmpty()) d.copy(cards = tagged) else null } ?: emptyList()
    }

    // --- Private Helpers for Automatic Sets (Retained from Logic Extraction) ---

}

class FlashcardViewModelFactory(private val application: Application) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlashcardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FlashcardViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}