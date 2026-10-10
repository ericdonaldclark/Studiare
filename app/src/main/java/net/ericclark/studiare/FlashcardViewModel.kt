package net.ericclark.studiare

import android.app.Application
import android.content.Context
import androidx.compose.remote.creation.first
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateSetOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import net.ericclark.studiare.screens.Dialogs.pendingVoiceLanguagesFor
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
import kotlinx.coroutines.flow.update
import net.ericclark.studiare.screens.Dialogs.AnkiMappingConfig
import net.ericclark.studiare.screens.Dialogs.CreateStudySessionDialog

enum class ConflictResolutionStrategy {
    USE_CLOUD_WIPE_LOCAL,
    USE_LOCAL_WIPE_CLOUD,
    MERGE_KEEP_LOCAL, // Overwrite cloud matches with local
    MERGE_KEEP_CLOUD  // Keep cloud matches, add new local
}

sealed class PaneDestination(val paneKey: String) {
    data object DeckList : PaneDestination("deckList")
    data class SetManager(val deckId: String) : PaneDestination("set:$deckId")
    // autoOpen mirrors the "?autoOpen=" query param the NavController route accepts
    // (e.g. "study"/"quiz"/"game"/"fsrs" from the Study split-button's dropdown) — it's
    // excluded from paneKey on purpose so re-pushing the same deck's pane still dedupes.
    data class StudyModeSelection(val deckId: String, val autoOpen: String? = null) : PaneDestination("study:$deckId")
    data class SavedSessions(val deckId: String) : PaneDestination("sessions:$deckId")
    // Add more cases here as you add new drill-down layers (card browser, etc.)
}

/**
 * The main ViewModel for the application. It handles business logic and delegates
 * infrastructure/syncing/audio/study operations to specialized Managers.
 */
class FlashcardViewModel(application: Application) : AndroidViewModel(application) {

    // --- Core Dependencies ---
    // Initialize these FIRST so they are available for the Managers below
    private val preferenceManager = PreferenceManager(application)
    private val cardUtils = CardUtils()

    // --- BYOB Credentials & State ---
    private val credentialManager = FirebaseCredentialManager(application)

    private val _isBackendConnected = MutableStateFlow(credentialManager.hasCredentials())
    val isBackendConnected: StateFlow<Boolean> = _isBackendConnected

    private val _backendProjectId = MutableStateFlow(credentialManager.getActiveProjectId())
    val backendProjectId: StateFlow<String?> = _backendProjectId
    private var dynamicApp: com.google.firebase.FirebaseApp? = null
    private var auth: FirebaseAuth? = null
    private var db: FirebaseFirestore? = null

    // --- NEW: ROOM DATABASE ---
    private val database = AppDatabase.getDatabase(application)
    private val deckDao: DeckDao = database.deckDao()
    private val cardDao: CardDao = database.cardDao()
    private val tagDao: TagDao = database.tagDao()
    private val sessionDao: SessionDao = database.sessionDao()
    private val deckCollectionDao: DeckCollectionDao = database.deckCollectionDao()

    var hasStartedLoading = false

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
            onAudioProgressUpdate = { index -> updateAudioSessionProgress(index) }
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

    private val naturalSortRegex = Regex("\\d+|\\D+")
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
    val audioFeedback: StateFlow<String?> get() = audioServiceManager.audioFeedback
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
    val deckViewMode: StateFlow<Int> = preferenceManager.deckViewModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // --- Study Hub active-session sort/grouping (global, same for every deck) ---
    val groupByCategory: StateFlow<Boolean> = preferenceManager.groupByCategoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val groupByMode: StateFlow<Boolean> = preferenceManager.groupByModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val categorySortMode: StateFlow<GroupSortMode> = preferenceManager.categorySortModeFlow
        .map { GroupSortMode.fromInt(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroupSortMode.MOST_RECENT)
    val categorySortDirection: StateFlow<Direction> = preferenceManager.categorySortDirectionFlow
        .map { it.toDirection() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Direction.DESC)
    val modeSortMode: StateFlow<GroupSortMode> = preferenceManager.modeSortModeFlow
        .map { GroupSortMode.fromInt(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), GroupSortMode.MOST_RECENT)
    val modeSortDirection: StateFlow<Direction> = preferenceManager.modeSortDirectionFlow
        .map { it.toDirection() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Direction.DESC)
    val sessionTileSortMode: StateFlow<SessionTileSortMode> = preferenceManager.sessionTileSortModeFlow
        .map { SessionTileSortMode.fromInt(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SessionTileSortMode.LAST_ACCESSED)
    val sessionTileSortDirection: StateFlow<Direction> = preferenceManager.sessionTileSortDirectionFlow
        .map { it.toDirection() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), Direction.DESC)

    // Settings → Mode Defaults (global, same for every deck): per-(category, mode) defaults for
    // CreateStudySessionDialog's mode-specific options.
    val modeDefaultSettings: StateFlow<Map<Pair<StudyCategory, SessionMode>, ModeDefaultSettings>> = preferenceManager.modeDefaultSettingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    // --- ROOM STATE FLOWS ---
    val allCollectionsWithDecks: StateFlow<List<CollectionWithDecks>> = deckCollectionDao.getCollectionsWithDecks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- SELECTED COLLECTION STATE ---
    private val _selectedCollectionId = MutableStateFlow<String?>("UNINITIALIZED") // null represents the virtual "All Decks"
    val selectedCollectionId: StateFlow<String?> = _selectedCollectionId

    fun selectCollection(collectionId: String?) {
        _selectedCollectionId.value = collectionId
        viewModelScope.launch { preferenceManager.setSelectedCollectionId(collectionId) }
    }
    private val localDecksFlow: StateFlow<List<Deck>> = deckDao.getAllActiveDecks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    private val localCardsFlow: StateFlow<List<Card>> = cardDao.getAllActiveCards()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // --- Lightweight UI Flow ---
    val groupedAndSortedDecks: StateFlow<List<Pair<DeckSummary, List<DeckSummary>>>> = combine(
        localDecksFlow,
        deckSortMode,
        _selectedCollectionId,
        allCollectionsWithDecks
    ) { decks, sortMode, selectedCollId, collections ->
        withContext(Dispatchers.Default) {
            val summaries = decks.map { DeckSummary(it, it.cardIds.size) }

            // 1. Determine which parent decks belong in the current view
            val allowedRootDecks = if (selectedCollId == "UNINITIALIZED") {
                emptyList()
            } else if (selectedCollId == null) {
                // "All Decks" mode: Get all decks that don't have a parent
                summaries.filter { it.deck.parentDeckId == null }
            } else {
                // Specific Collection mode: Get decks mapped to this collection
                val collectionData = collections.find { it.collection.id == selectedCollId }
                val validDeckIds = collectionData?.decks?.map { it.id } ?: emptyList()
                summaries.filter { it.deck.id in validDeckIds }
            }

            // 2. Map sets to their parents (this remains the same across all views)
            // 2. Map sets to their parents (this remains the same across all views)
            val setsByParent = summaries.filter { it.deck.parentDeckId != null }.groupBy { it.deck.parentDeckId!! }

            val deckComparator = getDeckComparator(sortMode)

            val mainDecks = allowedRootDecks.sortedWith(deckComparator)
            mainDecks.map { mainDeck ->
                val sets = (setsByParent[mainDeck.deck.id] ?: emptyList()).sortedWith(deckComparator)
                mainDeck to sets
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // --- USER COLLECTION STATS ---
    val totalDecks: StateFlow<Int> = localDecksFlow.map { list -> list.count { it.parentDeckId == null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalSets: StateFlow<Int> = localDecksFlow.map { list -> list.count { it.parentDeckId != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val totalCards: StateFlow<Int> = localCardsFlow.map { it.size }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    data class CardStats(
        val newCards: Int = 0,
        val learning: Int = 0,
        val review: Int = 0,
        val relearning: Int = 0,
        val suspended: Int = 0,
        val known: Int = 0,
        val difficultyCounts: Map<DifficultySetting, Int> = emptyMap(),
        val totalReviews: Int = 0,
        val reviewedToday: Int = 0
    )

    val cardStats: StateFlow<CardStats> = localCardsFlow.map { cards ->
        val startOfDay = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        CardStats(
            newCards = cards.count { it.fsrsState == null || it.fsrsState == FsrsState.NEW },
            learning = cards.count { it.fsrsState == FsrsState.LEARNING },
            review = cards.count { it.fsrsState == FsrsState.REVIEW },
            relearning = cards.count { it.fsrsState == FsrsState.RELEARNING },
            suspended = cards.count { it.isSuspended },
            known = cards.count { it.isKnown },
            difficultyCounts = cards.groupingBy { it.difficulty }.eachCount(),
            totalReviews = cards.sumOf { it.reviewedCount },
            reviewedToday = cards.count { (it.reviewedAt ?: 0L) >= startOfDay }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CardStats())

    // --- Internal Helpers for Data Access ---
    private val localDecks: List<Deck> get() = localDecksFlow.value
    private val localCards: List<Card> get() = localCardsFlow.value
    private val currentUserId: String? get() = authAndSyncManager.userId.value

    // --- ViewModel UI State ---
    private val _isInitialDataLoaded = MutableStateFlow(false)
    val isInitialDataLoaded: StateFlow<Boolean> = _isInitialDataLoaded

    private val _allDecksWithCards = MutableLiveData<List<DeckWithCards>>(emptyList())
    val allDecks: LiveData<List<DeckWithCards>> = _allDecksWithCards

    var studyState by mutableStateOf<StudyState?>(null)
        private set

    var isLoading by mutableStateOf(true)
        private set

    // Tree view UI state: kept here (not in the composable) so it survives navigating to another
    // screen and back. It's in-memory only, so it resets like everything else when the app restarts.
    val treeSelectedPath = mutableStateListOf<String>()
    val treeExpandedNodeIds = mutableStateSetOf<String>()

    fun collapseTreeToTopLevel() {
        treeSelectedPath.clear()
        treeExpandedNodeIds.clear()
    }

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
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    val hdPromptDismissedLanguages: StateFlow<Set<String>> = preferenceManager.hdPromptDismissedLanguagesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    var toastMessage by mutableStateOf<String?>(null)
        private set

    val themeMode: StateFlow<Int>

    // null until Room has actually delivered the sessions table, so screens can tell
    // "not loaded yet" apart from "genuinely no sessions".
    //
    // Eagerly (not Lazily) shared: StudySessionManager.updateAndSaveStudyState reads
    // _allActiveSessions.value directly (not via collectAsState) to find-and-update the
    // session row being saved. Lazily only starts the underlying Room query once some
    // Compose screen actually collects it — and a full-screen route with no other screen
    // composed behind it (e.g. AudioStudyScreen) has no such collector. Confirmed live: with
    // Lazily, saving the Audio session's card position from inside that screen read back
    // availableIds=[] (an empty list) every time, so the position was silently never
    // persisted and force-stopping the app rewound the session to card 1. Eagerly keeps this
    // populated for the ViewModel's whole lifetime regardless of what's on screen.
    private val _allActiveSessionsOrNull: StateFlow<List<ActiveSession>?> = sessionDao.getAllActiveSessions()
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val allActiveSessionsOrNull: StateFlow<List<ActiveSession>?> get() = _allActiveSessionsOrNull

    private val _allActiveSessions: StateFlow<List<ActiveSession>> = _allActiveSessionsOrNull
        .map { it ?: emptyList() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    // ── Miller-column pane stack ──────────────────────────────────────────────
    // Each entry is one "layer" the user drilled into. New layers are always
    // pushed onto the end; popping removes the deepest layer. The UI decides
    // how many of the tail entries fit on screen (see takeLast in DecksScreen).
    private val _paneStack = MutableStateFlow<List<PaneDestination>>(listOf(PaneDestination.DeckList))
    val paneStack: StateFlow<List<PaneDestination>> = _paneStack

    fun pushPane(destination: PaneDestination) {
        // Replace rather than duplicate if the same pane is already topmost.
        _paneStack.update { stack ->
            if (stack.lastOrNull()?.paneKey == destination.paneKey) stack else stack + destination
        }
    }

    fun popPane() {
        _paneStack.update { stack -> if (stack.size > 1) stack.dropLast(1) else stack }
    }

    // Opens [destination] as the pane directly after [afterPaneKey], dropping anything
    // that was previously open deeper than that pane (Miller-column behaviour).
    fun pushPaneAfter(afterPaneKey: String, destination: PaneDestination) {
        _paneStack.update { stack ->
            val idx = stack.indexOfFirst { it.paneKey == afterPaneKey }
            val base = if (idx >= 0) stack.take(idx + 1) else stack
            if (base.lastOrNull()?.paneKey == destination.paneKey) base else base + destination
        }
    }

    // Closes [paneKey] and every pane deeper than it, going up exactly one level.
    fun closePane(paneKey: String) {
        _paneStack.update { stack ->
            val idx = stack.indexOfFirst { it.paneKey == paneKey }
            if (idx > 0) stack.take(idx) else stack
        }
    }

    fun popToPane(paneKey: String) {
        _paneStack.update { stack ->
            val idx = stack.indexOfFirst { it.paneKey == paneKey }
            if (idx >= 0) stack.take(idx + 1) else stack
        }
    }

    // ── Back-compat shims ─────────────────────────────────────────────────────
    // Old call sites (DecksScreen, SetsScreen, StudyScreens, NavigationDrawer)
    // call setCurrentDeckId/setCurrentSetId directly — keep those signatures
    // working by mapping them onto the stack, so you don't have to touch
    // every call site in this pass.
    val currentDeckId: StateFlow<String?> = paneStack
        .map { stack -> (stack.getOrNull(1) as? PaneDestination.SetManager)?.deckId }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    val currentSetId: StateFlow<String?> = paneStack
        .map { stack -> (stack.lastOrNull() as? PaneDestination.StudyModeSelection)?.deckId }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    fun setCurrentSetId(id: String?) {
        _paneStack.update { stack ->
            val withoutStudy = stack.filterNot { it is PaneDestination.StudyModeSelection }
            if (id == null) withoutStudy else withoutStudy + PaneDestination.StudyModeSelection(id)
        }
    }

    fun setCurrentDeckId(deckId: String?) {
        _paneStack.value = if (deckId == null) {
            listOf(PaneDestination.DeckList)
        } else {
            listOf(PaneDestination.DeckList, PaneDestination.SetManager(deckId))
        }
    }
    val activeSessions: StateFlow<List<ActiveSession>>

    val allActiveSessions: StateFlow<List<ActiveSession>> get() = _allActiveSessions

    //val databaseVersion: Int = 10
    val lastExportTimestamp: StateFlow<Long>
    val lastImportTimestamp: StateFlow<Long>

    val downloadedHdLanguages: StateFlow<Set<String>> = preferenceManager.downloadedHdLanguagesFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    // null = no Whisper speech-recognition model downloaded/chosen yet. Eagerly (not
    // WhileSubscribed): the listening modes gate their front-audio autoplay on this resolving to
    // its real persisted value before the first card plays (so audio doesn't start before the
    // first-use prompt has had a chance to show) — WhileSubscribed left `.value` stuck at the
    // default until *this* screen's own collectAsState() started subscribing, which was too late.
    val whisperModelSize: StateFlow<String?> = preferenceManager.whisperModelSizeFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val hasPromptedWhisperModel: StateFlow<Boolean> = preferenceManager.hasPromptedWhisperModelFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val memoryGridColumnsPortrait: StateFlow<Int> = preferenceManager.memoryGridColumnsPortraitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 3)

    val memoryGridColumnsLandscape: StateFlow<Int> = preferenceManager.memoryGridColumnsLandscapeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 5)

    val spacingMode: StateFlow<Int> = preferenceManager.spacingModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SpacingMode.COMFORTABLE)

    val animationMode: StateFlow<Int> = preferenceManager.animationModeFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnimationMode.NORMAL)

    // Null = never explicitly chosen; resolved against the current window size in the UI layer
    // (DeckSetsDisplayMode.resolve/resolveTreeDirection) rather than a fixed default here, since
    // window size class isn't available at the ViewModel level.
    val deckSetsDisplayMode: StateFlow<DeckSetsDisplayMode?> = preferenceManager.deckSetsDisplayModeFlow
        .map { it?.let { v -> DeckSetsDisplayMode.fromInt(v) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val treeLargeScreenLayout: StateFlow<Boolean?> = preferenceManager.treeLargeScreenLayoutFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
    val gridLoadingIndicator: StateFlow<Boolean> = preferenceManager.gridLoadingIndicatorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val treeLoadingIndicator: StateFlow<Boolean> = preferenceManager.treeLoadingIndicatorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val reduceMotion: StateFlow<Boolean> = preferenceManager.reduceMotionFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val disableCardFlipAnimations: StateFlow<Boolean> = preferenceManager.disableCardFlipAnimationsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val alwaysOpenBulkEditor: StateFlow<Boolean> = preferenceManager.alwaysOpenBulkEditorFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    // Eager so `.value` is current when the + button reads it on click; Learn until a category has been used
    val lastStudyCategory: StateFlow<StudyCategory> = preferenceManager.lastStudyCategoryFlow
        .map { name -> StudyCategory.entries.firstOrNull { it.name == name } ?: StudyCategory.LEARN }
        .stateIn(viewModelScope, SharingStarted.Eagerly, StudyCategory.LEARN)
    val sortTagsByDateCreated: StateFlow<Boolean> = preferenceManager.sortTagsByDateCreatedFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val shortcutsCurrentScreenOnly: StateFlow<Boolean> = preferenceManager.shortcutsCurrentScreenOnlyFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val showSizeOverlay: StateFlow<Boolean> = preferenceManager.showSizeOverlayFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val showShortcutsButton: StateFlow<Boolean> = preferenceManager.showShortcutsButtonFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val isDebug: StateFlow<Boolean> = preferenceManager.isDebugFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val shortcutRemaps: StateFlow<Map<String, Long>> = preferenceManager.shortcutRemapsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val deckSetCountsSnapshotMap: StateFlow<Map<String, List<Int>>?> = preferenceManager.deckSetCountsSnapshotFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val deckSetCountsSnapshot: StateFlow<List<Int>?> = combine(
        deckSetCountsSnapshotMap,
        _selectedCollectionId
    ) { map, selectedId ->
        if (map == null || selectedId == "UNINITIALIZED") null
        else map[selectedId ?: "ALL_DECKS"] ?: emptyList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

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

    init {
        // Initialize Theme & Preferences
        // Initialize Theme & Preferences
        hasStartedLoading = true
        initializeDynamicFirebase()
        viewModelScope.launch {
            _selectedCollectionId.value = preferenceManager.selectedCollectionIdFlow.first()
        }
        themeMode = preferenceManager.themeModeFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.DARK)
        lastExportTimestamp = preferenceManager.lastExportTimestampFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)
        lastImportTimestamp = preferenceManager.lastImportTimestampFlow.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0L)

        activeSessions = combine(_allActiveSessions, currentDeckId) { sessions, deckId ->
            if (deckId == null) emptyList() else sessions.filter { it.deckId == deckId }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

        // --- Deterministic Initial Load Check ---
        viewModelScope.launch(Dispatchers.IO) {
            val initialDecks = deckDao.getAllActiveDecks().first()
            deckCollectionDao.getCollectionsWithDecks().first()
            sessionDao.getAllActiveSessions().first()

            withContext(Dispatchers.Main) {
                _isInitialDataLoaded.value = true
                // ONLY drop loading instantly if DB is completely empty.
                if (initialDecks.isEmpty()) {
                    isLoading = false
                } else {
                    // Give groupedAndSortedDecks a fraction of a second to combine, then drop
                    // loading so we don't hang for 2 seconds on legitimately empty collections.
                    viewModelScope.launch {
                        kotlinx.coroutines.delay(100)
                        isLoading = false
                    }
                }
            }
        }

        // Safely drop the loading spinner ONLY once the real mapped data is ready
        viewModelScope.launch {
            groupedAndSortedDecks.collect { groups ->
                if (groups.isNotEmpty() && isLoading) {
                    isLoading = false
                }
            }
        }

        // Safety fallback: Prevent infinite loading
        viewModelScope.launch {
            kotlinx.coroutines.delay(2000)
            if (isLoading) {
                isLoading = false
            }
        }

        // 4. Observe Data Changes from Room
        viewModelScope.launch {
            // Give the UI 300ms to paint the lightweight DecksScreen before hammering the CPU
            kotlinx.coroutines.delay(300)

            // FIX: Reuse the cached flows instead of querying the DB again
            combine(localDecksFlow, localCardsFlow) { decks, cards ->
                combineDecksAndCards(decks, cards)
            }.collect {}
        }

        // 5. Run Media Garbage Collection once on startup
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(5000) // Let the app settle first
            try {
                // Call .first() directly on the Flow for decks
                val decks = deckDao.getAllActiveDecks().first()

                // Fetch cards in 100-item chunks to prevent CursorWindow crashes
                val allCards = mutableListOf<Card>()
                var currentOffset = 0
                val batchSize = 100

                while (true) {
                    val batch = cardDao.getActiveCardsPaged(limit = batchSize, offset = currentOffset)
                    if (batch.isEmpty()) break
                    allCards.addAll(batch)
                    currentOffset += batchSize
                }

                net.ericclark.studiare.components.MediaStorageUtils.cleanOrphanedMedia(application, allCards, decks)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Failed to run startup GC", e)
            }
        }

        // 6. Run Database Garbage Collection for ghost records
        // Snapshot Updater: Observe root decks and update the lightweight snapshot in DataStore
        viewModelScope.launch(Dispatchers.Default) {
            // Using raw Room flows guarantees it suspends and skips the fake 'emptyList' state
            combine(deckDao.getAllActiveDecks(), deckSortMode, deckCollectionDao.getCollectionsWithDecks()) { decks, sortMode, collections ->
                val snapshotMap = mutableMapOf<String, List<Int>>()
                val comparator = getDeckComparator(sortMode)

                val summaries = decks.map { DeckSummary(it, it.cardIds.size) }
                val setsByParent = summaries.filter { it.deck.parentDeckId != null }.groupBy { it.deck.parentDeckId!! }

                fun computeCounts(allowedRootDecks: List<DeckSummary>): List<Int> {
                    val sortedRootDecks = allowedRootDecks.sortedWith(comparator)
                    return sortedRootDecks.map { root -> setsByParent[root.deck.id]?.size ?: 0 }
                }

                // 1. "All Decks"
                val allDecksRoot = summaries.filter { it.deck.parentDeckId == null }
                snapshotMap["ALL_DECKS"] = computeCounts(allDecksRoot)

                // 2. Each Collection
                collections.forEach { collectionData ->
                    val validDeckIds = collectionData.decks.map { it.id }
                    val collectionRootDecks = summaries.filter { it.deck.id in validDeckIds }
                    snapshotMap[collectionData.collection.id] = computeCounts(collectionRootDecks)
                }

                snapshotMap
            }.collect { snapshotMap ->
                preferenceManager.setDeckSetCountsSnapshot(snapshotMap)
            }
        }

        // 6. Run Database Garbage Collection for ghost records
        viewModelScope.launch(Dispatchers.IO) {
            kotlinx.coroutines.delay(2000)
            if (isUserAnonymous.value) {
                try {
                    cardDao.purgeDeletedCards()
                    deckDao.purgeDeletedDecks()
                    sessionDao.purgeDeletedSessions()
                    tagDao.purgeDeletedTags()
                    deckCollectionDao.purgeDeletedCollections()
                } catch (e: Exception) {
                    AppLogger.e(TAG, "Failed to purge ghost records", e)
                }
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
    fun setDeckViewMode(mode: Int) {
        // Panes opened from the old layout (sets, sessions) don't belong in the new one.
        if (mode != deckViewMode.value) popToPane("deckList")
        viewModelScope.launch { preferenceManager.setDeckViewMode(mode) }
    }

    fun setGroupByCategory(value: Boolean) {
        viewModelScope.launch { preferenceManager.setGroupByCategory(value) }
    }
    fun setGroupByMode(value: Boolean) {
        viewModelScope.launch { preferenceManager.setGroupByMode(value) }
    }
    fun setCategorySortMode(mode: GroupSortMode) {
        viewModelScope.launch { preferenceManager.setCategorySortMode(mode.value) }
    }
    fun setCategorySortDirection(direction: Direction) {
        viewModelScope.launch { preferenceManager.setCategorySortDirection(direction.name) }
    }
    fun setModeSortMode(mode: GroupSortMode) {
        viewModelScope.launch { preferenceManager.setModeSortMode(mode.value) }
    }
    fun setModeSortDirection(direction: Direction) {
        viewModelScope.launch { preferenceManager.setModeSortDirection(direction.name) }
    }
    fun setSessionTileSortMode(mode: SessionTileSortMode) {
        viewModelScope.launch { preferenceManager.setSessionTileSortMode(mode.value) }
    }
    fun setSessionTileSortDirection(direction: Direction) {
        viewModelScope.launch { preferenceManager.setSessionTileSortDirection(direction.name) }
    }

    fun setModeDefaultSettings(category: StudyCategory, mode: SessionMode, settings: ModeDefaultSettings) {
        viewModelScope.launch { preferenceManager.setModeDefaultSettings(category, mode, settings) }
    }

    // Settings → Mode Defaults → "Apply to all modes": copies one difficulty-weighting default to every
    // (category, mode) pair, leaving each pair's other stored defaults untouched.
    fun applyDifficultyWeightingToAllModes(weighted: Boolean, counts: List<Int>) {
        viewModelScope.launch {
            for (category in StudyCategory.entries) {
                for (mode in modesForCategory(category)) {
                    val existing = modeDefaultSettings.value[category to mode] ?: ModeDefaultSettings()
                    preferenceManager.setModeDefaultSettings(
                        category, mode, existing.copy(difficultyWeighted = weighted, difficultyCounts = counts)
                    )
                }
            }
        }
    }

    private fun initializeDynamicFirebase() {
        dynamicApp = credentialManager.getOrInitializeFirebaseApp()
        if (dynamicApp != null) {
            auth = FirebaseAuth.getInstance(dynamicApp!!)
            db = FirebaseFirestore.getInstance(dynamicApp!!)
            _isBackendConnected.value = true
            _backendProjectId.value = credentialManager.getActiveProjectId()
        } else {
            auth = null
            db = null
            _isBackendConnected.value = false
            _backendProjectId.value = null
        }
        authAndSyncManager.updateFirebaseInstances(db, auth)
        importExportManager.updateDb(db)
    }

    fun importBackendConfig(context: Context, uri: android.net.Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val inputStream = context.contentResolver.openInputStream(uri)
                val jsonString = inputStream?.bufferedReader().use { it?.readText() }
                if (jsonString != null) {
                    val success = credentialManager.parseAndSaveCredentials(jsonString)
                    withContext(Dispatchers.Main) {
                        if (success) {
                            initializeDynamicFirebase()
                            toastMessage = "Backend connected successfully!"
                        } else {
                            toastMessage = "Invalid configuration file."
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) { toastMessage = "Error reading file." }
            }
        }
    }

    fun linkEmailAccount(email: String, pass: String, context: Context) {
        authAndSyncManager.linkEmailAccount(email, pass) { success, errorMsg ->
            if (success) {
                // Conflict resolution dialog will trigger automatically if needed via the state flow
            } else {
                toastMessage = "Authentication failed: ${errorMsg ?: "Unknown error"}"
            }
        }
    }

    fun removeBackendConnection() {
        credentialManager.clearCredentials()
        _isBackendConnected.value = false
        _backendProjectId.value = null
        authAndSyncManager.updateFirebaseInstances(null, null)
        importExportManager.updateDb(null)
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

    fun playAudioShownSide() {
        audioServiceManager.playAudioShownSide()
    }

    fun flipAudioShownSide() {
        audioServiceManager.flipAudioShownSide()
    }

    fun skipAudioNext() {
        audioServiceManager.skipAudioNext()
    }

    fun skipAudioPrevious() {
        audioServiceManager.skipAudioPrevious()
    }

    fun setAudioContinuousPlay(enabled: Boolean) {
        audioServiceManager.setAudioContinuousPlay(enabled)
    }

    fun setAudioPlaybackSpeed(speed: Float) {
        audioServiceManager.setAudioPlaybackSpeed(speed)
    }

    fun setAudioReplayCount(count: Int) {
        audioServiceManager.setAudioReplayCount(count)
    }

    // In-session settings button: changes apply to the running session and are saved with it.
    fun updateSessionOptions(values: ModeDefaultSettings) {
        studySessionManager.applySessionOptions(values)
    }

    fun updateAudioDelays(answerDelaySeconds: Double, nextCardDelaySeconds: Double) {
        audioServiceManager.updateAudioDelays(answerDelaySeconds, nextCardDelaySeconds)
    }

    // --- Delegation to ImportExportManager ---

    fun getDecksAsString(decksToExport: List<DeckWithCards>, format: String, includeMetadata: Boolean = true): String {
        val finalDecks = if (includeMetadata) decksToExport else stripMetadata(decksToExport)
        return importExportManager.getDecksAsString(finalDecks, format)
    }

    fun importDecksFromString(content: String, mimeType: String?) {
        importExportManager.importDecksFromString(content, mimeType)
    }

    suspend fun analyzeAnkiPackage(context: Context, sourceUri: android.net.Uri): List<Pair<String, List<Pair<String, net.ericclark.studiare.data.MediaType>>>> {
        return importExportManager.analyzeAnkiPackage(context, sourceUri)
    }

    fun importFromAnkiPackage(
        context: Context,
        ankiPackageUri: android.net.Uri,
        fieldMappings: List<net.ericclark.studiare.screens.Dialogs.AnkiMappingConfig>? = null
    ) {
        viewModelScope.launch {
            importExportManager.importFromAnkiPackage(context, ankiPackageUri, fieldMappings)
        }
    }

    fun exportToAnkiPackage(context: Context, decksToExport: List<DeckWithCards>, destinationUri: android.net.Uri, includeMetadata: Boolean = true) {
        val finalDecks = if (includeMetadata) decksToExport else stripMetadata(decksToExport)
        viewModelScope.launch {
            importExportManager.exportToAnkiPackage(context, finalDecks, destinationUri)
        }
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

    // Whether the one-time notification explainer has been shown (see CreateStudySessionDialog)
    val notificationPromptShown: StateFlow<Boolean> = preferenceManager.notificationPromptShownFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)
    fun markNotificationPromptShown() { viewModelScope.launch { preferenceManager.setNotificationPromptShown(true) } }
    // Debug: the saved-preferences table
    val savedPreferences: StateFlow<List<PreferenceEntry>> = preferenceManager.preferenceEntries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    fun savePreference(name: String, type: String, text: String) {
        viewModelScope.launch { preferenceManager.setPreference(name, type, text) }
    }
    fun clearPreference(name: String, type: String) {
        viewModelScope.launch { preferenceManager.clearPreference(name, type) }
    }
    fun resetAllPreferences() {
        viewModelScope.launch { preferenceManager.clearAllEditablePreferences(savedPreferences.value) }
    }

    fun setHdAudioPrompted(prompted: Boolean = true) {
        audioServiceManager.setHdAudioPrompted(prompted)
    }

    /** Languages in the decks the voice prompt would still ask about: not downloaded and not dismissed. */
    fun pendingVoiceLanguages(): List<String> = pendingVoiceLanguagesFor(
        deckLanguages = getUniqueDeckLanguages(),
        downloaded = downloadedHdLanguages.value,
        dismissed = hdPromptDismissedLanguages.value,
        allDismissed = hasPromptedHdLanguages.value
    )

    /** "Dismiss for these languages": the voice prompt stops asking about [languages]. */
    fun dismissVoicePromptFor(languages: List<String>) {
        viewModelScope.launch { preferenceManager.addHdPromptDismissedLanguages(languages) }
    }

    /** "Dismiss for all languages": the voice prompt stops asking altogether. */
    fun dismissVoicePromptForAll() = setHdAudioPrompted(true)

    fun getUniqueDeckLanguages(): List<String> {
        return audioServiceManager.getUniqueDeckLanguages(_allDecksWithCards.value ?: emptyList())
    }

    fun getFormattedModelSize(langCode: String): String {
        return audioServiceManager.getFormattedModelSize(langCode)
    }

    fun startHdLanguageDownload(context: Context, languages: List<String>) {
        audioServiceManager.startHdLanguageDownload(languages)
    }
    /** The voice download in progress (language, fraction, queued languages), or null when none is running. */
    val voiceDownload: StateFlow<AudioServiceManager.VoiceDownloadProgress?> get() = audioServiceManager.voiceDownload
    fun cancelVoiceDownload() = audioServiceManager.cancelVoiceDownload()

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

    // --- Delegation to AudioServiceManager (Whisper speech recognition) ---

    fun startWhisperModelDownload(
        size: net.ericclark.studiare.components.speech.WhisperModelSize,
        onProgress: (Float) -> Unit,
        onComplete: (Boolean) -> Unit
    ): kotlinx.coroutines.Job {
        return audioServiceManager.startWhisperModelDownload(size, onProgress, onComplete)
    }

    fun deleteWhisperModel(size: net.ericclark.studiare.components.speech.WhisperModelSize) {
        audioServiceManager.deleteWhisperModel(size) { msg -> toastMessage = msg }
    }

    fun setHasPromptedWhisperModel(prompted: Boolean = true) {
        viewModelScope.launch { preferenceManager.setHasPromptedWhisperModel(prompted) }
    }

    // --- Delegation to StudySessionManager (Study Logic) ---

    fun startStudySession(
        parentDeck: DeckWithCards, mode: SessionMode, isWeighted: Boolean, numCards: Int, quizPromptSide: CardSide,
        numAnswers: Int, showCorrectLetters: Boolean, limitAnswerPool: Boolean, isGraded: Boolean,
        allowMultipleGuesses: Boolean,
        maxMemoryTiles: Int, gridDensity: Int, config: AutoSetConfig, freeformLayoutVertical: Boolean,
        onSessionCreated: () -> Unit
    ) {
        studySessionManager.startStudySession(parentDeck, mode, isWeighted, numCards, quizPromptSide, numAnswers,
            showCorrectLetters, limitAnswerPool, isGraded, allowMultipleGuesses,
            maxMemoryTiles, gridDensity, freeformLayoutVertical, config,
            onSessionCreated)
    }
    fun submitFsrsGrade(rating: Int) { studySessionManager.submitFsrsGrade(rating) }
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
    fun submitTypingAnswer(answer: String) { studySessionManager.submitTypingAnswer(answer) }
    fun submitListenAnswer(answer: String, isCorrect: Boolean) { studySessionManager.submitListenAnswer(answer, isCorrect) }
    fun submitTypingCorrect() { studySessionManager.submitTypingCorrect() }
    fun selectAnswer(option: String) { studySessionManager.selectAnswer(option) }
    fun revealAnswer() { studySessionManager.revealAnswer() }
    fun generateOptionsForCurrentCardIfNeeded() { studySessionManager.generateOptionsForCurrentCardIfNeeded() }

    fun flipCard() { studySessionManager.flipCard() }
    fun flipStudyMode() { studySessionManager.flipStudyMode() }
    fun nextCard() { studySessionManager.nextCard() }
    fun previousCard() { studySessionManager.previousCard() }

    fun initMemoryGrid() { studySessionManager.initMemoryGrid() }
    fun selectMemoryTile(cardId: String, side: CardSide) { studySessionManager.selectMemoryTile(cardId, side) }
    fun clearMemorySelection() { studySessionManager.clearMemorySelection() }

    fun selectCrosswordWord(wordId: String) { studySessionManager.selectCrosswordWord(wordId) }
    fun selectCrosswordCell(x: Int, y: Int) { studySessionManager.selectCrosswordCell(x, y) }
    fun submitCrosswordChar(char: Char) { studySessionManager.submitCrosswordChar(char) }
    fun deleteCrosswordChar() { studySessionManager.deleteCrosswordChar() }
    fun provideCrosswordHint(wordId: String, fillEntireWord: Boolean) { studySessionManager.provideCrosswordHint(wordId, fillEntireWord) }

    fun submitWordSearchMatch(startCell: Pair<Int, Int>, endCell: Pair<Int, Int>) {
        studySessionManager.submitWordSearchMatch(startCell, endCell)
    }
    fun startNewMatchingRound(cardsPerColumn: Int) {
        studySessionManager.startNewMatchingRound(cardsPerColumn)
    }

    fun advanceMatchingRound() {
        studySessionManager.advanceMatchingRound()
    }

    fun selectMatchingItem(cardId: String, side: String) {
        studySessionManager.selectMatchingItem(cardId, side)
    }

    fun revealMatchingAnswer() {
        studySessionManager.revealMatchingAnswer()
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
                var effectiveDeck = deck
                var effectiveCards = deck.cardIds.mapNotNull { cardsMap[it] }

                if (deck.parentDeckId != null) {
                    val parent = decksMap[deck.parentDeckId]
                    if (parent != null) {
                        val linkage = deck.linkageSettings

                        if (linkage.linkMetadata) {
                            effectiveDeck = effectiveDeck.copy(
                                createdAt = parent.createdAt,
                                updatedAt = parent.updatedAt
                            )
                        }
                        if (linkage.linkFieldConfig) {
                            effectiveDeck = effectiveDeck.copy(
                                frontLanguage = parent.frontLanguage,
                                backLanguage = parent.backLanguage,
                                frontNoteTemplates = parent.frontNoteTemplates,
                                backNoteTemplates = parent.backNoteTemplates
                            )
                        }
                        if (linkage.linkCardOrder) {
                            effectiveDeck = effectiveDeck.copy(deckSortMode = parent.deckSortMode)
                        }
                        if (linkage.linkScoring) {
                            effectiveDeck = effectiveDeck.copy(
                                fsrsEnabled = parent.fsrsEnabled,
                                fsrsWeights = parent.fsrsWeights,
                                dailyNewCardLimit = parent.dailyNewCardLimit,
                                dailyReviewLimit = parent.dailyReviewLimit
                            )
                        }
                    }
                }

                DeckWithCards(effectiveDeck, effectiveCards)
            }.sortedBy { it.deck.name }

            _allDecksWithCards.postValue(combined)
        }
    }

    private fun getDeckComparator(sortMode: DeckSortMode): Comparator<DeckSummary> {
        val naturalOrderComparator = Comparator<String> { s1, s2 ->
            val matches1 = naturalSortRegex.findAll(s1).map { it.value }.toList()
            val matches2 = naturalSortRegex.findAll(s2).map { it.value }.toList()

            for (i in 0 until kotlin.math.min(matches1.size, matches2.size)) {
                val m1 = matches1[i]
                val m2 = matches2[i]
                if (m1 != m2) {
                    val n1 = m1.toLongOrNull()
                    val n2 = m2.toLongOrNull()
                    if (n1 != null && n2 != null) return@Comparator n1.compareTo(n2)
                    return@Comparator m1.compareTo(m2, ignoreCase = true)
                }
            }
            matches1.size.compareTo(matches2.size)
        }

        val byMode = Comparator<DeckSummary> { d1, d2 ->
            when (sortMode) {
                DeckSortMode.A_TO_Z -> naturalOrderComparator.compare(d1.deck.name, d2.deck.name)
                DeckSortMode.Z_TO_A -> naturalOrderComparator.compare(d2.deck.name, d1.deck.name)
                DeckSortMode.DATE_ADDED_NEW_TO_OLD -> d2.deck.createdAt.compareTo(d1.deck.createdAt)
                DeckSortMode.DATE_ADDED_OLD_TO_NEW -> d1.deck.createdAt.compareTo(d2.deck.createdAt)
                DeckSortMode.DATE_MODIFIED_NEW_TO_OLD -> d2.deck.updatedAt.compareTo(d1.deck.updatedAt)
                DeckSortMode.DATE_MODIFIED_OLD_TO_NEW -> d1.deck.updatedAt.compareTo(d2.deck.updatedAt)
                else -> naturalOrderComparator.compare(d1.deck.name, d2.deck.name)
            }
        }
        // Starred decks and sets always come first, then the chosen sort applies within each group.
        return compareByDescending<DeckSummary> { it.deck.isStarred }.then(byMode)
    }

    private fun updateAudioSessionProgress(index: Int) {
        studySessionManager.updateAudioProgress(index)
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

    fun setDeckSetsDisplayMode(mode: DeckSetsDisplayMode) { viewModelScope.launch { preferenceManager.setDeckSetsDisplayMode(mode.value) } }
    fun setTreeLargeScreenLayout(enabled: Boolean) { viewModelScope.launch { preferenceManager.setTreeLargeScreenLayout(enabled) } }
    fun setGridLoadingIndicator(enabled: Boolean) { viewModelScope.launch { preferenceManager.setGridLoadingIndicator(enabled) } }
    fun setTreeLoadingIndicator(enabled: Boolean) { viewModelScope.launch { preferenceManager.setTreeLoadingIndicator(enabled) } }
    fun setReduceMotion(enabled: Boolean) { viewModelScope.launch { preferenceManager.setReduceMotion(enabled) } }
    fun setDisableCardFlipAnimations(enabled: Boolean) { viewModelScope.launch { preferenceManager.setDisableCardFlipAnimations(enabled) } }
    fun setAlwaysOpenBulkEditor(enabled: Boolean) { viewModelScope.launch { preferenceManager.setAlwaysOpenBulkEditor(enabled) } }
    fun setLastStudyCategory(category: StudyCategory) { viewModelScope.launch { preferenceManager.setLastStudyCategory(category.name) } }
    fun setSortTagsByDateCreated(enabled: Boolean) { viewModelScope.launch { preferenceManager.setSortTagsByDateCreated(enabled) } }
    /** A one-shot routing decision at click-time, not reactive UI — reads `.value` directly rather than via `collectAsState()`. */
    fun deckEditRoute(deckId: String): String = if (alwaysOpenBulkEditor.value) "deckEditor?deckId=$deckId" else "simpleEditor?deckId=$deckId"
    fun setShortcutsCurrentScreenOnly(enabled: Boolean) { viewModelScope.launch { preferenceManager.setShortcutsCurrentScreenOnly(enabled) } }
    fun setShowShortcutsButton(enabled: Boolean) { viewModelScope.launch { preferenceManager.setShowShortcutsButton(enabled) } }
    fun setShowSizeOverlay(enabled: Boolean) { viewModelScope.launch { preferenceManager.setShowSizeOverlay(enabled) } }
    fun enableDebugMode() { viewModelScope.launch { preferenceManager.setIsDebug(true) } }
    fun setShortcutRemap(id: String, key: androidx.compose.ui.input.key.Key?) {
        viewModelScope.launch { preferenceManager.setShortcutRemap(id, key?.keyCode) }
    }

    // --- Editor & CRUD Helpers ---

    private fun String.normalizeForDuplicateCheck(): String = this.filter { it.isLetterOrDigit() }.lowercase()

    /**
     * Simple Editor has no top-level Save — every per-card dialog commits immediately. Rather than
     * a parallel single-card persistence path, this reuses the exact whole-deck save pipeline Bulk
     * Editor's own `saveAction` uses: the deck's current persisted cards, unchanged, with just the
     * one target card upserted (edited or newly added), going through the same duplicate check.
     */
    fun saveSingleCard(deck: DeckWithCards, cardData: CardDataForSave) {
        val cardsToSave = deck.cards.filter { it.id != cardData.id }.map { it.toCardDataForSave() } + cardData
        checkForDuplicatesInEditor(
            deck.deck.id, deck.deck.name, cardsToSave, deck.deck.normalizationType, deck.deck.deckSortMode,
            deck.deck.parentDeckId, deck.deck.frontLanguage, deck.deck.backLanguage, deck.deck.description,
            deck.deck.dailyNewCardLimit, deck.deck.dailyReviewLimit, deck.deck.frontNoteTemplates, deck.deck.backNoteTemplates
        )
    }

    /** See [saveSingleCard] — same pipeline, with the one card excluded instead of upserted. */
    fun deleteSingleCard(deck: DeckWithCards, cardId: String) {
        val cardsToSave = deck.cards.filter { it.id != cardId }.map { it.toCardDataForSave() }
        checkForDuplicatesInEditor(
            deck.deck.id, deck.deck.name, cardsToSave, deck.deck.normalizationType, deck.deck.deckSortMode,
            deck.deck.parentDeckId, deck.deck.frontLanguage, deck.deck.backLanguage, deck.deck.description,
            deck.deck.dailyNewCardLimit, deck.deck.dailyReviewLimit, deck.deck.frontNoteTemplates, deck.deck.backNoteTemplates
        )
    }

    fun setDeckSortMode(deck: DeckWithCards, mode: DeckSortMode) {
        val cardsToSave = deck.cards.map { it.toCardDataForSave() }
        checkForDuplicatesInEditor(
            deck.deck.id, deck.deck.name, cardsToSave, deck.deck.normalizationType, mode,
            deck.deck.parentDeckId, deck.deck.frontLanguage, deck.deck.backLanguage, deck.deck.description,
            deck.deck.dailyNewCardLimit, deck.deck.dailyReviewLimit, deck.deck.frontNoteTemplates, deck.deck.backNoteTemplates
        )
    }

    fun renameDeck(deck: DeckWithCards, newName: String) {
        val cardsToSave = deck.cards.map { it.toCardDataForSave() }
        checkForDuplicatesInEditor(
            deck.deck.id, newName, cardsToSave, deck.deck.normalizationType, deck.deck.deckSortMode,
            deck.deck.parentDeckId, deck.deck.frontLanguage, deck.deck.backLanguage, deck.deck.description,
            deck.deck.dailyNewCardLimit, deck.deck.dailyReviewLimit, deck.deck.frontNoteTemplates, deck.deck.backNoteTemplates
        )
    }

    /** Mirrors Bulk Editor's Deck Options save: re-normalizes every card's text with the chosen [normalizationType], same as `applyNormalization` there. */
    fun updateDeckOptions(
        deck: DeckWithCards,
        normalizationType: NormalizationType,
        sortMode: DeckSortMode,
        frontLanguage: String,
        backLanguage: String
    ) {
        fun normalizeNotes(notes: List<NoteField>) = notes.map {
            if (it.type == MediaType.PLAIN_TEXT) it.copy(content = normalizeText(normalizationType, it.content)) else it
        }
        val cardsToSave = deck.cards.map { card ->
            card.toCardDataForSave().let {
                it.copy(
                    front = normalizeText(normalizationType, it.front),
                    back = normalizeText(normalizationType, it.back),
                    frontNotes = normalizeNotes(it.frontNotes),
                    backNotes = normalizeNotes(it.backNotes)
                )
            }
        }
        checkForDuplicatesInEditor(
            deck.deck.id, deck.deck.name, cardsToSave, normalizationType, sortMode,
            deck.deck.parentDeckId, frontLanguage, backLanguage, deck.deck.description,
            deck.deck.dailyNewCardLimit, deck.deck.dailyReviewLimit, deck.deck.frontNoteTemplates, deck.deck.backNoteTemplates
        )
    }

    fun saveNoteTemplates(deck: DeckWithCards, newFront: List<NoteField>, newBack: List<NoteField>, addToExistingCards: Boolean) {
        val cardsToSave = deck.cards.map { card ->
            var data = card.toCardDataForSave()
            if (addToExistingCards) {
                val currentFrontNames = data.frontNotes.map { it.name }
                val currentBackNames = data.backNotes.map { it.name }
                val missingFront = newFront.filter { it.name !in currentFrontNames }.map { it.copy(content = "") }
                val missingBack = newBack.filter { it.name !in currentBackNames }.map { it.copy(content = "") }
                if (missingFront.isNotEmpty() || missingBack.isNotEmpty()) {
                    data = data.copy(frontNotes = data.frontNotes + missingFront, backNotes = data.backNotes + missingBack)
                }
            }
            data
        }
        checkForDuplicatesInEditor(
            deck.deck.id, deck.deck.name, cardsToSave, deck.deck.normalizationType, deck.deck.deckSortMode,
            deck.deck.parentDeckId, deck.deck.frontLanguage, deck.deck.backLanguage, deck.deck.description,
            deck.deck.dailyNewCardLimit, deck.deck.dailyReviewLimit, newFront, newBack
        )
    }

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
                        reviewLogs = emptyList(),
                        absoluteDueDate = null, // <--- ADD THIS MISSING RESET
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
                    reviewLogs = ex?.reviewLogs ?: cd.reviewLogs,
                    absoluteDueDate = ex?.absoluteDueDate ?: cd.absoluteDueDate,
                    tags = cd.tags,
                    ownerDeckId = if (parentDeckId == null) id else ex?.ownerDeckId,
                    createdAt = ex?.createdAt ?: cd.createdAt ?: System.currentTimeMillis(),
                    updatedAt = System.currentTimeMillis(),
                    isSuspended = cd.isSuspended,
                    flag = cd.flag,
                    lastReviewDurationMs = ex?.lastReviewDurationMs ?: cd.lastReviewDurationMs,
                    fsrsStability = ex?.fsrsStability ?: cd.fsrsStability,
                    // A new or never-reviewed card has no FSRS-computed difficulty yet — seed one
                    // from its 1-5 difficulty tag instead of leaving it null (FsrsAlgorithm.seedDifficulty).
                    fsrsDifficulty = ex?.fsrsDifficulty ?: cd.fsrsDifficulty ?: FsrsAlgorithm.seedDifficulty(cd.difficulty),
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

            // --- NEW: Handle Addition/Deletion Sync Logic ---
            val removedCardIds = (existingDeck?.cardIds ?: emptyList()).filter { it !in cardIds }
            val addedCardIds = cardIds.filter { it !in (existingDeck?.cardIds ?: emptyList()) }

            if (deck.parentDeckId != null) {
                // We are editing a CHILD SET. Sync changes UP to the parent based on settings.
                localDecks.find { it.id == deck.parentDeckId }?.let { parent ->
                    var newParentCardIds = parent.cardIds

                    if (deck.linkageSettings.syncCardAdditions && addedCardIds.isNotEmpty()) {
                        newParentCardIds = (newParentCardIds + addedCardIds).distinct()
                    }

                    if (deck.linkageSettings.syncCardDeletions && removedCardIds.isNotEmpty()) {
                        newParentCardIds = newParentCardIds - removedCardIds.toSet()
                    }

                    if (newParentCardIds != parent.cardIds) {
                        deckDao.insertOrUpdate(parent.copy(
                            updatedAt = System.currentTimeMillis(),
                            cardIds = newParentCardIds,
                            isPendingSync = true
                        ))
                    }
                }
            } else {
                // We are editing a ROOT DECK. Sync deletions DOWN to child sets to prevent orphaned references.
                if (removedCardIds.isNotEmpty()) {
                    val childSets = localDecks.filter { it.parentDeckId == id }
                    childSets.forEach { child ->
                        if (child.cardIds.any { it in removedCardIds }) {
                            deckDao.insertOrUpdate(child.copy(
                                updatedAt = System.currentTimeMillis(),
                                cardIds = child.cardIds - removedCardIds.toSet(),
                                isPendingSync = true
                            ))
                        }
                    }
                }
            }

            // Soft Remove deleted cards
            if (removedCardIds.isNotEmpty()) {
                val cardsToDelete = removedCardIds.filter { rid ->
                    localDecks.none { d -> d.id != id && d.cardIds.contains(rid) }
                }
                if (cardsToDelete.isNotEmpty()) {
                    if (isUserAnonymous.value) {
                        cardDao.hardDeleteCards(cardsToDelete)
                    } else {
                        cardDao.softDeleteCards(cardsToDelete, System.currentTimeMillis())
                    }
                }
                handleCardDeletionsInSessions(removedCardIds)
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
            val timestamp = System.currentTimeMillis()

            // 1. Bulk delete the parent deck and all its sets/sub-decks
            val subDecks = localDecks.filter { it.parentDeckId == deckId }.map { it.id }
            val decksToDelete = listOf(deckId) + subDecks

            // 2. Identify orphaned cards
            val cardsToDelete = deck.cardIds.filter { cid ->
                localDecks.none { d -> d.id !in decksToDelete && d.cardIds.contains(cid) }
            }

            // 3. Bulk delete based on Auth State
            if (isUserAnonymous.value) {
                deckDao.hardDeleteDecks(decksToDelete)
                if (cardsToDelete.isNotEmpty()) {
                    cardDao.hardDeleteCards(cardsToDelete)
                    handleCardDeletionsInSessions(cardsToDelete)
                }
            } else {
                deckDao.softDeleteDecks(decksToDelete, timestamp)
                if (cardsToDelete.isNotEmpty()) {
                    cardDao.softDeleteCards(cardsToDelete, timestamp)
                    handleCardDeletionsInSessions(cardsToDelete)
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

                val deckIds = localDecks.map { it.id }
                val cardIds = localCards.map { it.id }

                if (isUserAnonymous.value) {
                    if (deckIds.isNotEmpty()) deckDao.hardDeleteDecks(deckIds)
                    if (cardIds.isNotEmpty()) cardDao.hardDeleteCards(cardIds)
                } else {
                    if (deckIds.isNotEmpty()) deckDao.softDeleteDecks(deckIds, timestamp)
                    if (cardIds.isNotEmpty()) cardDao.softDeleteCards(cardIds, timestamp)
                }

                preferenceManager.saveActiveSessions(emptyList())
            } catch (e: Exception) { AppLogger.e(TAG, "deleteAllDecks failed", e) }
            finally { withContext(Dispatchers.Main) { isProcessing = false } }
        }
    }

    fun deleteAllSessionsForDeck(deckId: String) {
        val sessionsToDelete = _allActiveSessions.value.filter { it.deckId == deckId }
        viewModelScope.launch(Dispatchers.IO) {
            sessionsToDelete.forEach {
                if (isUserAnonymous.value) sessionDao.hardDelete(it.id)
                else sessionDao.softDelete(it.id)
            }
        }
    }

    fun deleteAllSetsForDeck(parentDeckId: String) {
        val sets = localDecks.filter { it.parentDeckId == parentDeckId }
        if (sets.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val timestamp = System.currentTimeMillis()
            val setIds = sets.map { it.id }

            if (isUserAnonymous.value) {
                deckDao.hardDeleteDecks(setIds)
                val sessionsToDelete = _allActiveSessions.value.filter { it.deckId in setIds }
                sessionsToDelete.forEach { sessionDao.hardDelete(it.id) }
            } else {
                deckDao.softDeleteDecks(setIds, timestamp)
                val sessionsToDelete = _allActiveSessions.value.filter { it.deckId in setIds }
                sessionsToDelete.forEach { sessionDao.softDelete(it.id) }
            }
        }
    }

    fun deleteCard(cardId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isUserAnonymous.value) {
                cardDao.hardDelete(cardId)
            } else {
                cardDao.softDelete(cardId, System.currentTimeMillis())
            }
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
                        reviewedAt = oldCard.reviewedAt,
                        isKnown = oldCard.isKnown,
                        gradedAttempts = oldCard.gradedAttempts,
                        incorrectAttempts = oldCard.incorrectAttempts,
                        reviewLogs = oldCard.reviewLogs,
                        absoluteDueDate = oldCard.absoluteDueDate,
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

    fun cloneDeckAsSet(parentDeck: DeckWithCards, setName: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val linkage = LinkageSettings() // Uses the new defaults: sync additions (true), don't sync deletions (false)

            deckDao.insertOrUpdate(Deck(
                id = UUID.randomUUID().toString(),
                name = setName,
                parentDeckId = parentDeck.deck.id,
                cardIds = parentDeck.cards.map { it.id }, // Clone all cards
                linkageSettings = linkage,
                isPendingSync = true
            ))
        }
    }

    // --- COLLECTION MANAGEMENT ---

    fun createCollection(name: String, description: String = "") {
        if (name.trim().equals("UNINITIALIZED", ignoreCase = true)) {
            toastMessage = "Collection cannot be named 'UNINITIALIZED'"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val newCollection = DeckCollection(
                name = name,
                description = description,
                isPendingSync = true
            )
            deckCollectionDao.insertOrUpdate(newCollection)
        }
    }

    fun updateCollection(collectionId: String, newName: String) {
        if (newName.trim().equals("UNINITIALIZED", ignoreCase = true)) {
            toastMessage = "Collection cannot be named 'UNINITIALIZED'"
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            val collectionsWithDecks = allCollectionsWithDecks.value
            val target = collectionsWithDecks.find { it.collection.id == collectionId }?.collection
            if (target != null) {
                deckCollectionDao.insertOrUpdate(target.copy(name = newName, updatedAt = System.currentTimeMillis(), isPendingSync = true))
            }
        }
    }

    fun deleteCollection(collectionId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (isUserAnonymous.value) {
                deckCollectionDao.hardDelete(collectionId)
            } else {
                deckCollectionDao.softDelete(collectionId, System.currentTimeMillis())
            }
            // If the user deletes the collection they are currently viewing, reset to "All Decks"
            if (_selectedCollectionId.value == collectionId) {
                _selectedCollectionId.value = null
            }
        }
    }

    fun toggleDeckInCollection(collectionId: String, deckId: String, isIncluded: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val crossRef = CollectionDeckCrossRef(collectionId, deckId)
            if (isIncluded) {
                deckCollectionDao.insertCrossRef(crossRef)
            } else {
                deckCollectionDao.deleteCrossRef(crossRef)
            }

            // Touch the collection to update its timestamp for syncing
            val collectionsWithDecks = allCollectionsWithDecks.value
            val target = collectionsWithDecks.find { it.collection.id == collectionId }?.collection
            if (target != null) {
                deckCollectionDao.insertOrUpdate(target.copy(updatedAt = System.currentTimeMillis(), isPendingSync = true))
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

    // --- Update updateLinkageSettings to clear metadata if unlinked ---
    fun updateLinkageSettings(setId: String, newSettings: LinkageSettings) {
        viewModelScope.launch(Dispatchers.IO) {
            val set = localDecks.find { it.id == setId } ?: return@launch
            val parent = localDecks.find { it.id == set.parentDeckId }

            var updatedSet = set.copy(linkageSettings = newSettings, updatedAt = System.currentTimeMillis(), isPendingSync = true)

            if (parent != null) {
                // 1. Deck-Level Snapshot: Fields
                if (set.linkageSettings.linkFieldConfig && !newSettings.linkFieldConfig) {
                    updatedSet = updatedSet.copy(
                        frontLanguage = parent.frontLanguage,
                        backLanguage = parent.backLanguage,
                        frontNoteTemplates = parent.frontNoteTemplates,
                        backNoteTemplates = parent.backNoteTemplates
                    )
                }

                // 2. Deck-Level Snapshot: Metadata
                if (set.linkageSettings.linkMetadata && !newSettings.linkMetadata) {
                    updatedSet = updatedSet.copy(
                        createdAt = parent.createdAt,
                        updatedAt = parent.updatedAt
                    )
                }

                // 3. Deck-Level Snapshot: Scoring (Missed this previously!)
                if (set.linkageSettings.linkScoring && !newSettings.linkScoring) {
                    updatedSet = updatedSet.copy(
                        fsrsEnabled = parent.fsrsEnabled,
                        fsrsWeights = parent.fsrsWeights,
                        dailyNewCardLimit = parent.dailyNewCardLimit,
                        dailyReviewLimit = parent.dailyReviewLimit,
                        averageQuizScore = parent.averageQuizScore
                    )
                }

                // --- DEEP UNLINK: Clone cards to sever the database relationship ---
                val needsCardCloning = (set.linkageSettings.linkCardData && !newSettings.linkCardData) ||
                        (set.linkageSettings.linkScoring && !newSettings.linkScoring) ||
                        (set.linkageSettings.linkMetadata && !newSettings.linkMetadata)

                if (needsCardCloning) {
                    val currentCards = localCards.filter { it.id in set.cardIds }
                    val clonedCards = mutableListOf<Card>()
                    val newCardIds = mutableListOf<String>()

                    // We wipe review/score data if EITHER Scoring or Metadata is unlinked
                    val stripData = (set.linkageSettings.linkMetadata && !newSettings.linkMetadata) ||
                            (set.linkageSettings.linkScoring && !newSettings.linkScoring)

                    currentCards.forEach { card ->
                        val newId = UUID.randomUUID().toString()
                        newCardIds.add(newId)

                        // 4. Card-Level Wipe
                        clonedCards.add(card.copy(
                            id = newId,
                            ownerDeckId = setId, // Take ownership of the clone
                            isPendingSync = true,
                            createdAt = if (stripData) System.currentTimeMillis() else card.createdAt,
                            updatedAt = System.currentTimeMillis(),
                            // Wipe the metadata clean so the new set starts completely fresh
                            reviewLogs = if (stripData) emptyList() else card.reviewLogs,
                            absoluteDueDate = if (stripData) null else card.absoluteDueDate,
                            lastReviewDurationMs = if (stripData) 0L else card.lastReviewDurationMs,
                            fsrsStability = if (stripData) null else card.fsrsStability,
                            fsrsDifficulty = if (stripData) null else card.fsrsDifficulty,
                            fsrsElapsedDays = if (stripData) null else card.fsrsElapsedDays,
                            fsrsScheduledDays = if (stripData) null else card.fsrsScheduledDays,
                            fsrsState = if (stripData) FsrsState.NEW else card.fsrsState,
                            fsrsLastReview = if (stripData) null else card.fsrsLastReview,
                            fsrsLapses = if (stripData) 0 else card.fsrsLapses,
                            reviewedCount = if (stripData) 0 else card.reviewedCount,
                            gradedAttempts = if (stripData) emptyList() else card.gradedAttempts,
                            incorrectAttempts = if (stripData) emptyList() else card.incorrectAttempts,
                            reviewedAt = if (stripData) null else card.reviewedAt,
                            isKnown = if (stripData) false else card.isKnown,
                            isSuspended = if (stripData) false else card.isSuspended,
                            flag = if (stripData) CardFlag.NONE else card.flag
                        ))
                    }

                    if (clonedCards.isNotEmpty()) {
                        cardDao.insertOrUpdateAll(clonedCards)
                        updatedSet = updatedSet.copy(cardIds = newCardIds)
                    }
                }
            }

            deckDao.insertOrUpdate(updatedSet)
        }
    }

    // --- Add this private helper function to the bottom of the ViewModel ---
    private fun stripMetadata(decks: List<DeckWithCards>): List<DeckWithCards> {
        return decks.map { dwc ->
            val strippedDeck = dwc.deck.copy(
                createdAt = 0L,
                updatedAt = 0L,
                averageQuizScore = null
            )
            val strippedCards = dwc.cards.map { card ->
                card.copy(
                    createdAt = 0L,
                    updatedAt = 0L,
                    reviewedAt = null,
                    reviewedCount = 0,
                    gradedAttempts = emptyList(),
                    incorrectAttempts = emptyList(),
                    fsrsStability = null,
                    fsrsDifficulty = null,
                    fsrsElapsedDays = null,
                    fsrsScheduledDays = null,
                    fsrsState = FsrsState.NEW,
                    fsrsLastReview = null,
                    fsrsLapses = 0,
                    isSuspended = false,
                    flag = CardFlag.NONE,
                    lastReviewDurationMs = 0L,
                    reviewLogs = emptyList(),
                    absoluteDueDate = null
                )
            }
            DeckWithCards(strippedDeck, strippedCards)
        }
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