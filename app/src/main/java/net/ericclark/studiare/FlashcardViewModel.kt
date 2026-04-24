package net.ericclark.studiare

import android.app.Application
import android.content.Context
import androidx.compose.remote.creation.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import net.ericclark.studiare.data.*
import net.ericclark.studiare.components.*
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import kotlin.math.max
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.first

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
    // Initialize these FIRST so they are available for the Managers below
    private val preferenceManager = PreferenceManager(application)
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
    private val cardUtils = CardUtils()

    // --- NEW: ROOM DATABASE ---
    private val database = AppDatabase.getDatabase(application)
    private val deckDao: DeckDao = database.deckDao()
    private val cardDao: CardDao = database.cardDao()
    private val tagDao: TagDao = database.tagDao()
    private val sessionDao: SessionDao = database.sessionDao()

    // --- Managers ---

    // 1. Initialize AuthAndSyncManager
    private val authAndSyncManager = AuthAndSyncManager(
        context = application,
        db = db,
        auth = auth,
        preferenceManager = preferenceManager,
        viewModelScope = viewModelScope,
        onProcessingChanged = { isProcessing = it }
    )

    // 2. Initialize StudySessionManager
    private val studySessionManager = StudySessionManager(
        cardUtils = cardUtils,
        viewModelScope = viewModelScope,
        getStudyState = { studyState },
        setStudyState = { studyState = it },
        getAllDecks = { _allDecksWithCards.value ?: emptyList() },
        getAllActiveSessions = { _allActiveSessions.value },
        onToastMessage = { toastMessage = it },
        saveCard = { card -> updateCard(card) },
        saveDeck = { deck -> viewModelScope.launch(Dispatchers.IO) { deckDao.insertOrUpdate(deck.copy(isPendingSync = true)) } },
        saveSession = { session -> viewModelScope.launch(Dispatchers.IO) { sessionDao.insertOrUpdate(session.copy(isPendingSync = true)) } },
        deleteSessionById = { sessionId -> viewModelScope.launch(Dispatchers.IO) { sessionDao.softDelete(sessionId) } }
    )

    // 3. Initialize AudioServiceManager
    private val audioServiceManager by lazy {
        AudioServiceManager(
            context = getApplication(),
            preferenceManager = preferenceManager,
            viewModelScope = viewModelScope,
            getCurrentStudyState = { studyState },
            onAudioProgressUpdate = { index -> updateAudioSessionProgress(index) },
            onGradingResult = { cardId, isCorrect ->
                studySessionManager.handleGradingResult(cardId, isCorrect)
            }
        )
    }

    // 4. Initialize ImportExportManager
    private val importExportManager by lazy {
        ImportExportManager(
            db = db,
            preferenceManager = preferenceManager,
            viewModelScope = viewModelScope,
            userIdProvider = { authAndSyncManager.userId.value },
            getLocalDecks = { localDecks },
            getLocalCards = { localCards },
            onProcessingChanged = { isProcessing = it },
            onOverwriteConfirmationChanged = { _overwriteConfirmation.value = it },
            getOverwriteConfirmation = { _overwriteConfirmation.value },
            safeWrite = { task -> authAndSyncManager.safeWrite(task) },
            // Redirect saves to Room DAOs
            saveDeckToFirestore = { deck -> deckDao.insertOrUpdate(deck.copy(isPendingSync = true)) },
            saveCardToFirestore = { card -> cardDao.insertOrUpdate(card.copy(isPendingSync = true)) },
            onError = { importError = it }
        )
    }

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
    val tags: StateFlow<List<TagDefinition>> = tagDao.getAllActiveTags()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- Delegated State Flows (Audio) ---
    val audioIsListening: StateFlow<Boolean> get() = audioServiceManager.audioIsListening
    val audioFeedback: StateFlow<String?> get() = audioServiceManager.audioFeedback
    val audioWaitingForGrade: StateFlow<Boolean> get() = audioServiceManager.audioWaitingForGrade
    val audioCardIndex: StateFlow<Int> get() = audioServiceManager.audioCardIndex
    val audioIsFlipped: StateFlow<Boolean> get() = audioServiceManager.audioIsFlipped
    val audioIsPlaying: StateFlow<Boolean> get() = audioServiceManager.audioIsPlaying
    val isAudioServiceBound: StateFlow<Boolean> get() = audioServiceManager.isAudioServiceBound

    // --- Delegated State Flows (Sync Status) ---
    val isSyncing: StateFlow<Boolean> get() = authAndSyncManager.isSyncing
    val hasPendingChanges: StateFlow<Boolean> get() = authAndSyncManager.hasPendingChanges



    fun checkPendingChanges() {
        authAndSyncManager.checkPendingChanges()
    }

    val deckSortMode: StateFlow<DeckSortMode> = preferenceManager.deckSortModeFlow
        .map { DeckSortMode.fromInt(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DeckSortMode.A_TO_Z)

    // --- ROOM STATE FLOWS ---
    private val localDecksFlow: StateFlow<List<Deck>> = deckDao.getAllActiveDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val localCardsFlow: StateFlow<List<Card>> = cardDao.getAllActiveCards()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- USER COLLECTION STATS ---
    val totalDecks: StateFlow<Int> = localDecksFlow.map { list -> list.count { it.parentDeckId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalSets: StateFlow<Int> = localDecksFlow.map { list -> list.count { it.parentDeckId != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCards: StateFlow<Int> = localCardsFlow.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Internal Helpers for Data Access ---
    private val localDecks: List<Deck> get() = localDecksFlow.value
    private val localCards: List<Card> get() = localCardsFlow.value
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

    var importError by mutableStateOf<String?>(null)
        private set

    fun clearImportError() {
        importError = null
    }

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

    private val _allActiveSessions: StateFlow<List<ActiveSession>> = sessionDao.getAllActiveSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    private val _currentDeckId = MutableStateFlow<String?>(null)
    val activeSessions: StateFlow<List<ActiveSession>>

    val allActiveSessions: StateFlow<List<ActiveSession>> get() = _allActiveSessions

    //val databaseVersion: Int = 10
    val lastExportTimestamp: StateFlow<Long>
    val lastImportTimestamp: StateFlow<Long>

    val downloadedHdLanguages: StateFlow<Set<String>> = preferenceManager.downloadedHdLanguagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val memoryGridColumnsPortrait: StateFlow<Int> = preferenceManager.memoryGridColumnsPortraitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val memoryGridColumnsLandscape: StateFlow<Int> = preferenceManager.memoryGridColumnsLandscapeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val spacingMode: StateFlow<Int> = preferenceManager.spacingModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpacingMode.COMFORTABLE)

    val animationMode: StateFlow<Int> = preferenceManager.animationModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnimationMode.NORMAL)

    val displaySetsUnderDecks: StateFlow<Boolean> = preferenceManager.displaySetsUnderDecksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val customThemeColors: StateFlow<CustomThemeColors> = combine(
        preferenceManager.customPrimaryFlow,
        preferenceManager.customSecondaryFlow,
        preferenceManager.customTertiaryFlow,
        preferenceManager.customBackgroundFlow
    ) { p, s, t, b -> CustomThemeColors(p, s, t, b) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000),
            CustomThemeColors("#6750A4", "#625B71", "#7D5260", "#FFFBFE"))

    // --- Sync Toggles ---
    val syncDecksAndCards: StateFlow<Boolean> = preferenceManager.syncDecksAndCardsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val syncReviewData: StateFlow<Boolean> = preferenceManager.syncReviewDataFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val syncSavedSessions: StateFlow<Boolean> = preferenceManager.syncSavedSessionsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    // Sync Only on WiFi State
    val syncOnlyOnWifi: StateFlow<Boolean> = preferenceManager.syncOnlyOnWifiFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isLargeScreenDrawerOpen: StateFlow<Boolean> = preferenceManager.isLargeScreenDrawerOpen.stateIn(
        viewModelScope, SharingStarted.Lazily, false
    )

    init {
        // Initialize Theme & Preferences
        themeMode = preferenceManager.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)
        lastExportTimestamp = preferenceManager.lastExportTimestampFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
        lastImportTimestamp = preferenceManager.lastImportTimestampFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        activeSessions = combine(_allActiveSessions, _currentDeckId) { sessions, deckId ->
            if (deckId == null) emptyList() else sessions.filter { it.deckId == deckId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // 4. Observe Data Changes from Room instead of Manager
        viewModelScope.launch {
            // Collecting directly from the DAOs prevents the fake "emptyList()"
            // initial emission and waits for the real database read to complete.
            combine(deckDao.getAllActiveDecks(), cardDao.getAllActiveCards()) { decks, cards ->
                combineDecksAndCards(decks, cards)
            }.collect {}
        }

        // 5. Run Media Garbage Collection once on startup
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(5000) // Let the app settle first
            try {
                // Call .first() directly on the Flow
                val decks = deckDao.getAllActiveDecks().first()
                val cards = cardDao.getAllActiveCards().first()

                net.ericclark.studiare.components.MediaStorageUtils.cleanOrphanedMedia(application, cards, decks)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to run startup GC", e)
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        authAndSyncManager.cleanup()
    }

    fun setCustomThemeColors(primary: String, secondary: String, tertiary: String, background: String) {
        viewModelScope.launch {
            preferenceManager.setCustomThemeColors(primary, secondary, tertiary, background)
            // Automatically switch to Custom Mode when saving
            preferenceManager.setThemeMode(ThemeMode.CUSTOM)
        }
    }

    fun setDeckSortMode(mode: DeckSortMode) {
        viewModelScope.launch { preferenceManager.setDeckSortMode(mode.value) }
    }

    // --- Sync Preference Setters (With Hierarchy Logic) ---

    fun setSyncDecksAndCards(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setSyncDecksAndCards(enabled)
            // If Master is OFF, children MUST be OFF
            if (!enabled) {
                preferenceManager.setSyncReviewData(false)
                preferenceManager.setSyncSavedSessions(false)
            }
        }
    }

    fun setSyncReviewData(enabled: Boolean) {
        viewModelScope.launch {
            // Cannot enable if parent is disabled
            if (enabled && !syncDecksAndCards.value) return@launch

            preferenceManager.setSyncReviewData(enabled)
            // If Review is OFF, Session MUST be OFF
            if (!enabled) {
                preferenceManager.setSyncSavedSessions(false)
            }
        }
    }

    fun setSyncSavedSessions(enabled: Boolean) {
        viewModelScope.launch {
            // Cannot enable if parents are disabled
            if (enabled && (!syncDecksAndCards.value || !syncReviewData.value)) return@launch

            preferenceManager.setSyncSavedSessions(enabled)
        }
    }

    fun setSyncOnlyOnWifi(enabled: Boolean) {
        viewModelScope.launch {
            preferenceManager.setSyncOnlyOnWifi(enabled)
        }
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

    fun triggerSync() {
        authAndSyncManager.triggerSync()
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
            saveDeckWithCards(
                result.deckId, result.deckName, distinctCards, result.normalizationType, result.sortType,
                result.parentDeckId, null, result.frontLanguage, result.backLanguage,
                result.description, result.dailyNewCardLimit, result.dailyReviewLimit
            )
        }
        processNextInImportQueue()
    }

    fun saveImportIgnoringDuplicates() {
        _importDuplicateQueue.value.firstOrNull()?.let { result ->
            saveDeckWithCards(
                result.deckId, result.deckName, result.cardsToSave, result.normalizationType, result.sortType,
                result.parentDeckId, null, result.frontLanguage, result.backLanguage,
                result.description, result.dailyNewCardLimit, result.dailyReviewLimit
            )
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
        parentDeck: DeckWithCards, mode: SessionMode, isWeighted: Boolean, numCards: Int, quizPromptSide: CardSide, numAnswers: Int,
        showCorrectLetters: Boolean, limitAnswerPool: Boolean, isGraded: Boolean, selectAnswer: Boolean, allowMultipleGuesses: Boolean,
        enableStt: Boolean, hideAnswerText: Boolean, fingersAndToes: Boolean, maxMemoryTiles: Int, gridDensity: Int, config: AutoSetConfig,
        onSessionCreated: () -> Unit
    ) {
        studySessionManager.startStudySession(parentDeck, mode, isWeighted, numCards, quizPromptSide, numAnswers, showCorrectLetters, limitAnswerPool, isGraded, selectAnswer, allowMultipleGuesses, enableStt, hideAnswerText, fingersAndToes, maxMemoryTiles, gridDensity, config, onSessionCreated)
    }
    fun submitFsrsGrade(rating: Int) { studySessionManager.submitFsrsGrade(rating) }

    fun submitAudioFsrsGrade(rating: Int) {
        submitFsrsGrade(rating)
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
    fun selectMemoryTile(cardId: String, side: CardSide) { studySessionManager.selectMemoryTile(cardId, side) }

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

    fun updateFreeformIndex(index: Int) { studySessionManager.updateFreeformIndex(index) }
    fun completeFreeformSession() { studySessionManager.completeFreeformSession() }

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

    fun setSpacingMode(mode: Int) {
        viewModelScope.launch { preferenceManager.setSpacingMode(mode) }
    }

    fun setAnimationMode(mode: Int) {
        viewModelScope.launch { preferenceManager.setAnimationMode(mode) }
    }

    fun setDisplaySetsUnderDecks(enabled: Boolean) {
        viewModelScope.launch { preferenceManager.setDisplaySetsUnderDecks(enabled) }
    }

    fun setLargeScreenDrawerOpen(isOpen: Boolean) {
        viewModelScope.launch {
            preferenceManager.setLargeScreenDrawerOpen(isOpen)
        }
    }

    // --- Editor & CRUD Helpers ---

    private fun String.normalizeForDuplicateCheck(): String = this.filter { it.isLetterOrDigit() }.lowercase()

    fun checkForDuplicatesInEditor(
        deckId: String?,
        deckName: String,
        cards: List<CardDataForSave>,
        normalizationType: NormalizationType,
        sortType: DeckSortMode,
        parentDeckId: String?,
        frontLanguage: String,
        backLanguage: String,
        description: String,
        dailyNewCardLimit: Int,
        dailyReviewLimit: Int,
        frontNoteTemplates: List<NoteField>,
        backNoteTemplates: List<NoteField>
    ) {
        val duplicates = cards.groupBy { it.front.normalizeForDuplicateCheck() to it.back.normalizeForDuplicateCheck() }
            .filter { it.value.size > 1 }
            .map { (pair, group) ->
                DuplicateInfo("Front: '${group.first().front}'", group.size)
            }

        if (duplicates.isNotEmpty()) {
            _editorDuplicateResult.value = DuplicateCheckResult(
                duplicates, deckId, deckName, cards, normalizationType, sortType, parentDeckId,
                frontLanguage, backLanguage, description, dailyNewCardLimit, dailyReviewLimit,
                frontNoteTemplates, backNoteTemplates
            )
        } else {
            saveDeckWithCards(
                deckId, deckName, cards, normalizationType, sortType, parentDeckId, null,
                frontLanguage, backLanguage, description, dailyNewCardLimit, dailyReviewLimit,
                frontNoteTemplates, backNoteTemplates
            )
        }
    }

    fun clearDeckReviewData(deckId: String) {
        val deck = localDecks.find { it.id == deckId } ?: return
        val cardIds = deck.cardIds
        val cardsToReset = localCards.filter { it.id in cardIds }

        if (cardsToReset.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isProcessing = true }
            try {
                val updatedCards = cardsToReset.map { card ->
                    card.copy(
                        reviewedCount = 0,
                        gradedAttempts = emptyList(),
                        incorrectAttempts = emptyList(),
                        reviewedAt = null,
                        isKnown = false,
                        updatedAt = System.currentTimeMillis(),
                        fsrsStability = null,
                        fsrsDifficulty = null,
                        fsrsElapsedDays = null,
                        fsrsScheduledDays = null,
                        fsrsState = FsrsState.NEW,
                        fsrsLastReview = null,
                        fsrsLapses = 0,
                        isPendingSync = true
                    )
                }
                cardDao.insertOrUpdateAll(updatedCards)

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
    fun saveEditorWithDuplicatesRemoved() { _editorDuplicateResult.value?.let { result ->
        saveDeckWithCards(result.deckId, result.deckName, result.cardsToSave.distinctBy
        { it.front.normalizeForDuplicateCheck() to it.back.normalizeForDuplicateCheck() },
            result.normalizationType, result.sortType, result.parentDeckId, null, result.frontLanguage, result.backLanguage,
            result.description, result.dailyNewCardLimit, result.dailyReviewLimit, result.frontNoteTemplates, result.backNoteTemplates) }; dismissEditorDuplicateWarning() }
    fun saveEditorIgnoringDuplicates() { _editorDuplicateResult.value?.let { result ->
        saveDeckWithCards(result.deckId, result.deckName, result.cardsToSave,
            result.normalizationType, result.sortType, result.parentDeckId,
            null, result.frontLanguage, result.backLanguage,
            result.description, result.dailyNewCardLimit, result.dailyReviewLimit, result.frontNoteTemplates, result.backNoteTemplates) }; dismissEditorDuplicateWarning() }

    private fun saveDeckWithCards(
        deckId: String?,
        deckName: String,
        cardsToSave: List<CardDataForSave>,
        normalizationType: NormalizationType,
        sortType: DeckSortMode,
        parentDeckId: String? = null,
        isStarred: Boolean? = null,
        frontLanguage: String,
        backLanguage: String,
        description: String,
        dailyNewCardLimit: Int,
        dailyReviewLimit: Int,
        frontNoteTemplates: List<NoteField> = emptyList(),
        backNoteTemplates: List<NoteField> = emptyList()
    ) {
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
                deckSortMode = sortType,
                isStarred = isStarred ?: existingDeck?.isStarred ?: false,
                cardIds = cardIds,
                frontLanguage = frontLanguage,
                backLanguage = backLanguage,
                frontNoteTemplates = frontNoteTemplates,
                backNoteTemplates = backNoteTemplates,
                description = description,
                dailyNewCardLimit = dailyNewCardLimit,
                dailyReviewLimit = dailyReviewLimit,
                isPendingSync = true
            )

            val mappedCards = cardsToSave.map { cd ->
                val ex = localCards.find { it.id == cd.id }
                Card(
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
                    ownerDeckId = if (parentDeckId == null) id else ex?.ownerDeckId,
                    createdAt = ex?.createdAt ?: cd.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isSuspended = cd.isSuspended,
                    flag = cd.flag,
                    lastReviewDurationMs = ex?.lastReviewDurationMs ?: cd.lastReviewDurationMs,
                    fsrsStability = ex?.fsrsStability ?: cd.fsrsStability,
                    fsrsDifficulty = ex?.fsrsDifficulty ?: cd.fsrsDifficulty,
                    fsrsElapsedDays = ex?.fsrsElapsedDays ?: cd.fsrsElapsedDays,
                    fsrsScheduledDays = ex?.fsrsScheduledDays ?: cd.fsrsScheduledDays,
                    fsrsState = ex?.fsrsState ?: cd.fsrsState,
                    fsrsLastReview = ex?.fsrsLastReview ?: cd.fsrsLastReview,
                    fsrsLapses = ex?.fsrsLapses ?: cd.fsrsLapses,
                    isPendingSync = true
                )
            }

            // Save to Room
            deckDao.insertOrUpdate(deck)
            cardDao.insertOrUpdateAll(mappedCards)

            // Update parent deck if needed
            if (deck.parentDeckId != null) {
                localDecks.find { it.id == deck.parentDeckId }?.let { parent ->
                    deckDao.insertOrUpdate(parent.copy(
                        updatedAt = System.currentTimeMillis(),
                        cardIds = (parent.cardIds + cardIds).distinct(),
                        isPendingSync = true
                    ))
                }
            }

            // Soft Remove deleted cards
            (existingDeck?.cardIds ?: emptyList()).filter { it !in cardIds }.let { removed ->
                if (removed.isNotEmpty()) {
                    removed.forEach { rid ->
                        if (localDecks.none { d -> d.id != id && d.cardIds.contains(rid) }) {
                            cardDao.softDelete(rid, System.currentTimeMillis())
                        }
                    }
                    handleCardDeletionsInSessions(removed)
                }
            }
        }
    }

    fun createSet(parentDeckId: String, setName: String, cardIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            deckDao.insertOrUpdate(Deck(UUID.randomUUID().toString(), setName, parentDeckId, cardIds = cardIds, isPendingSync = true))
        }
    }

    fun updateSet(setId: String, setName: String, cardIds: List<String>) {
        localDecks.find { it.id == setId }?.let {
            viewModelScope.launch(Dispatchers.IO) {
                deckDao.insertOrUpdate(it.copy(name = setName, cardIds = cardIds, updatedAt = System.currentTimeMillis(), isPendingSync = true))
            }
        }
    }

    fun createAutomaticSets(parentDeck: DeckWithCards, config: AutoSetConfig, startCardId: String? = null) {
        viewModelScope.launch(Dispatchers.Default) {
            var pool = cardUtils.getFilteredAndSortedCards(parentDeck, config)
            if (startCardId != null) pool = pool.dropWhile { it.id != startCardId }
            val chunks = when (config.mode) {
                AutoSetCreationMode.ONE -> listOf(pool.take(config.maxCardsPerSet));
                AutoSetCreationMode.MULTIPLE -> pool.take(config.numSets * config.maxCardsPerSet).chunked(config.maxCardsPerSet);
                AutoSetCreationMode.SPLIT_ALL -> pool.chunked(config.maxCardsPerSet);
            }
            val existing = localDecks.filter { it.parentDeckId == parentDeck.deck.id }
            val nextNum = (existing.mapNotNull { it.name.removePrefix("Set ").toIntOrNull() }.maxOrNull() ?: 0) + 1

            withContext(Dispatchers.IO) {
                chunks.forEachIndexed { i, chunk ->
                    if (chunk.isNotEmpty()) {
                        deckDao.insertOrUpdate(Deck(UUID.randomUUID().toString(), "Set ${nextNum + i}", parentDeck.deck.id, cardIds = chunk.map { it.id }, isPendingSync = true))
                    }
                }
            }
        }
    }

    fun deleteDeck(deckId: String) {
        val deck = localDecks.find { it.id == deckId } ?: return
        viewModelScope.launch(Dispatchers.IO) {
            deckDao.softDelete(deckId, System.currentTimeMillis())
            localDecks.filter { it.parentDeckId == deckId }.forEach { deckDao.softDelete(it.id, System.currentTimeMillis()) }
            deck.cardIds.forEach { cid ->
                if (localDecks.none { d -> d.id != deckId && d.cardIds.contains(cid) }) {
                    cardDao.softDelete(cid, System.currentTimeMillis())
                    handleCardDeletionsInSessions(listOf(cid))
                }
            }
        }
    }

    fun toggleDeckStar(deck: Deck) {
        viewModelScope.launch(Dispatchers.IO) {
            deckDao.insertOrUpdate(deck.copy(isStarred = !deck.isStarred, updatedAt = System.currentTimeMillis(), isPendingSync = true))
        }
    }

    fun deleteAllDecks() {
        viewModelScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { isProcessing = true }
            try {
                val timestamp = System.currentTimeMillis()
                localDecks.forEach { deckDao.softDelete(it.id, timestamp) }
                localCards.forEach { cardDao.softDelete(it.id, timestamp) }
                preferenceManager.saveActiveSessions(emptyList())
            } catch (e: Exception) { AppLogger.e(TAG, "deleteAllDecks failed", e) }
            finally { withContext(Dispatchers.Main) { isProcessing = false } }
        }
    }

    fun deleteAllSessionsForDeck(deckId: String) {
        val sessionsToDelete = _allActiveSessions.value.filter { it.deckId == deckId }
        viewModelScope.launch(Dispatchers.IO) {
            sessionsToDelete.forEach { sessionDao.softDelete(it.id) }
        }
    }

    fun deleteAllSetsForDeck(parentDeckId: String) {
        val sets = localDecks.filter { it.parentDeckId == parentDeckId }
        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            sets.forEach { deckDao.softDelete(it.id, timestamp) }

            val setIds = sets.map { it.id }
            val sessionsToDelete = _allActiveSessions.value.filter { it.deckId in setIds }
            sessionsToDelete.forEach { sessionDao.softDelete(it.id) }
        }
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            cardDao.softDelete(cardId, System.currentTimeMillis())
            handleCardDeletionsInSessions(listOf(cardId))
            localDecks.filter { it.cardIds.contains(cardId) }.forEach {
                deckDao.insertOrUpdate(it.copy(cardIds = it.cardIds - cardId, updatedAt = System.currentTimeMillis(), isPendingSync = true))
            }
        }
    }

    private fun handleCardDeletionsInSessions(deletedIds: List<String>) {
        viewModelScope.launch {
            val updated = _allActiveSessions.value.mapNotNull { s ->
                if (s.shuffledCardIds.any { it in deletedIds }) {
                    val newIds = s.shuffledCardIds.filter { it !in deletedIds }
                    if (newIds.isEmpty()) {
                        null // Drop the session if it has no cards left
                    } else {
                        // Adjust the current index so we don't go out of bounds
                        val cardsRemovedBeforeCurrent = s.shuffledCardIds.take(s.currentCardIndex).count { it in deletedIds }
                        val newIndex = (s.currentCardIndex - cardsRemovedBeforeCurrent).coerceIn(0, kotlin.math.max(0, newIds.size - 1))
                        s.copy(
                            shuffledCardIds = newIds,
                            totalCards = newIds.size,
                            currentCardIndex = newIndex
                        )
                    }
                } else s
            }
            preferenceManager.saveActiveSessions(updated)
        }
    }

    fun updateCard(card: Card) {
        viewModelScope.launch(Dispatchers.IO) {
            val updatedCard = card.copy(updatedAt = System.currentTimeMillis(), isPendingSync = true)

            val finalCard = if (syncDecksAndCards.value && !syncReviewData.value) {
                // "Sync Review Data" is OFF. Preserve its review state from local cache.
                val oldCard = localCards.find { it.id == card.id }
                if (oldCard != null) {
                    updatedCard.copy(
                        reviewedCount = oldCard.reviewedCount,
                        reviewedAt = oldCard.reviewedAt,
                        isKnown = oldCard.isKnown,
                        gradedAttempts = oldCard.gradedAttempts,
                        incorrectAttempts = oldCard.incorrectAttempts,
                        fsrsStability = oldCard.fsrsStability,
                        fsrsDifficulty = oldCard.fsrsDifficulty,
                        fsrsElapsedDays = oldCard.fsrsElapsedDays,
                        fsrsScheduledDays = oldCard.fsrsScheduledDays,
                        fsrsState = oldCard.fsrsState,
                        fsrsLastReview = oldCard.fsrsLastReview,
                        fsrsLapses = oldCard.fsrsLapses
                    )
                } else updatedCard
            } else updatedCard

            cardDao.insertOrUpdate(finalCard)

            localDecks.filter { it.cardIds.contains(card.id) }.forEach {
                deckDao.insertOrUpdate(it.copy(updatedAt = System.currentTimeMillis(), isPendingSync = true))
            }

            withContext(Dispatchers.Main) {
                studyState?.let { state -> studyState = state.copy(shuffledCards = state.shuffledCards.map { if (it.id == card.id) updatedCard else it }, deckWithCards = state.deckWithCards.copy(cards = state.deckWithCards.cards.map { if (it.id == card.id) updatedCard else it })) }
            }
        }
    }

    fun updateCardDifficulty(card: Card, diff: DifficultySetting) { updateCard(card.copy(difficulty = diff)) }
    fun toggleCardKnownStatus(card: Card) { val new = card.copy(isKnown = !card.isKnown, updatedAt = System.currentTimeMillis()); updateCard(new); if (new.isKnown) handleCardDeletionsInSessions(listOf(card.id)) }

    // --- Tag Operations (Delegated to Room) ---
    fun saveTagDefinition(tag: TagDefinition) {
        viewModelScope.launch(Dispatchers.IO) { tagDao.insertOrUpdate(tag.copy(isPendingSync = true)) }
    }

    fun deleteTagDefinition(tag: TagDefinition) {
        viewModelScope.launch(Dispatchers.IO) { tagDao.softDelete(tag.id) }
    }

    fun renameTag(tag: TagDefinition, oldName: String) {
        if (tag.name.trim() == oldName) {
            saveTagDefinition(tag)
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            // 1. Save the renamed tag to Room
            tagDao.insertOrUpdate(tag.copy(isPendingSync = true))

            // 2. Update any cards that had the old tag name
            val cardsToUpdate = localCards.filter { it.tags.contains(oldName) }.map { card ->
                card.copy(
                    tags = card.tags.map { if (it == oldName) tag.name.trim() else it },
                    updatedAt = System.currentTimeMillis(),
                    isPendingSync = true
                )
            }
            if (cardsToUpdate.isNotEmpty()) {
                cardDao.insertOrUpdateAll(cardsToUpdate)
            }
        }
    }

    fun removeTagFromCards(tagName: String, cardIds: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            val cardsToUpdate = localCards.filter { it.id in cardIds }.map { card ->
                card.copy(tags = card.tags - tagName, updatedAt = System.currentTimeMillis(), isPendingSync = true)
            }
            if (cardsToUpdate.isNotEmpty()) cardDao.insertOrUpdateAll(cardsToUpdate)
        }
    }

    fun getCardsForTag(tagName: String): List<DeckWithCards> {
        return _allDecksWithCards.value?.mapNotNull { d -> val tagged = d.cards.filter { it.tags.contains(tagName) }; if (tagged.isNotEmpty()) d.copy(cards = tagged) else null } ?: emptyList()
    }
}

class FlashcardViewModelFactory(private val application: Application) : androidx.lifecycle.ViewModelProvider.Factory {
    override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(FlashcardViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return FlashcardViewModel(application) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}