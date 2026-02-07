package net.ericclark.studiare.components

import android.util.Log
import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.Deck
import net.ericclark.studiare.data.TagDefinition
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.*
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Authentication, Firestore Data Syncing, and CRUD operations.
 * Acts as the single source of truth for remote data (Decks, Cards, Tags, Sessions).
 */
class AuthAndSyncManager(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferenceManager: PreferenceManager, // Added PreferenceManager
    private val viewModelScope: CoroutineScope,
    private val onProcessingChanged: (Boolean) -> Unit
) {
    private val TAG = "AuthAndSyncManager"

    // --- Data State Flows ---
    private val _localDecks = MutableStateFlow<List<net.ericclark.studiare.data.Deck>?>(null)
    val localDecks: StateFlow<List<net.ericclark.studiare.data.Deck>?> = _localDecks

    private val _localCards = MutableStateFlow<List<net.ericclark.studiare.data.Card>?>(null)
    val localCards: StateFlow<List<net.ericclark.studiare.data.Card>?> = _localCards

    private val _localTags = MutableStateFlow<List<net.ericclark.studiare.data.TagDefinition>>(emptyList())
    val localTags: StateFlow<List<net.ericclark.studiare.data.TagDefinition>> = _localTags

    // --- Auth & Sync State Flows ---
    private val _userId = MutableStateFlow<String?>(null)
    val userId: StateFlow<String?> = _userId

    private val _isUserAnonymous = MutableStateFlow(true)
    val isUserAnonymous: StateFlow<Boolean> = _isUserAnonymous

    private val _userEmail = MutableStateFlow<String?>(null)
    val userEmail: StateFlow<String?> = _userEmail

    private val _isSyncSetupPending = MutableStateFlow(false)
    val isSyncSetupPending: StateFlow<Boolean> = _isSyncSetupPending

    private val _showConflictDialog = MutableStateFlow(false)
    val showConflictDialog: StateFlow<Boolean> = _showConflictDialog

    // Sync Preferences Checks
    private var syncDecksAndCards = true
    private var syncReviewData = true
    private var syncSavedSessions = true

    // Internal state for conflict resolution
    private var pendingLocalDecks: List<net.ericclark.studiare.data.Deck> = emptyList()
    private var pendingLocalCards: List<net.ericclark.studiare.data.Card> = emptyList()

    // Firestore listeners
    private var decksListener: ListenerRegistration? = null
    private var cardsListener: ListenerRegistration? = null
    private var tagsListener: ListenerRegistration? = null
    private var sessionsListener: ListenerRegistration? = null

    init {
        // Observe Sync Preferences
        viewModelScope.launch {
            combine(
                preferenceManager.syncDecksAndCardsFlow,
                preferenceManager.syncReviewDataFlow,
                preferenceManager.syncSavedSessionsFlow
            ) { decks, review, sessions ->
                Triple(decks, review, sessions)
            }.collectLatest { (decks, review, sessions) ->
                syncDecksAndCards = decks
                syncReviewData = review
                syncSavedSessions = sessions

                // Refresh listeners if user is logged in
                _userId.value?.let { uid -> setupFirestoreListeners(uid) }
            }
        }

        // Initialize Auth Listener
        auth.addAuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user != null) {
                _userId.value = user.uid

                AppLogger.setUserId(user.uid)
                AppLogger.setCustomKey("isAnonymous", user.isAnonymous.toString())

                _isUserAnonymous.value = user.isAnonymous
                _userEmail.value = user.email

                if (user.isAnonymous) {
                    db.disableNetwork()
                    _isSyncSetupPending.value = false
                } else {
                    db.enableNetwork()
                }
                setupFirestoreListeners(user.uid)
            } else {
                AppLogger.setUserId("")
                _userId.value = null
                signInAnonymously()
            }
        }

        if (auth.currentUser == null) {
            signInAnonymously()
        }
    }

    private fun signInAnonymously() {
        auth.signInAnonymously().addOnFailureListener { e -> AppLogger.e(TAG, "Auth failed", e) }
    }

    private fun setupFirestoreListeners(uid: String) {
        // Clear existing
        decksListener?.remove()
        cardsListener?.remove()
        tagsListener?.remove()
        sessionsListener?.remove()
        decksListener = null
        cardsListener = null
        tagsListener = null
        sessionsListener = null

        // Hierarchy Logic: If Master is OFF, no listeners attached
        if (!syncDecksAndCards) {
            Log.d(TAG, "Sync Decks/Cards disabled. Detaching listeners.")
            return
        }

        // 1. Decks
        decksListener = db.collection("users").document(uid).collection("decks")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val decks = snapshot?.toObjects(Deck::class.java) ?: emptyList()
                _localDecks.value = decks
            }

        // 2. Cards
        cardsListener = db.collection("users").document(uid).collection("cards")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val cards = snapshot?.toObjects(Card::class.java) ?: emptyList()
                _localCards.value = cards
            }

        // 3. Tags (Always sync if Decks are synced)
        tagsListener = db.collection("users").document(uid).collection("tags")
            .orderBy("name")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val tags = snapshot?.toObjects(TagDefinition::class.java) ?: emptyList()
                _localTags.value = tags
            }

        // 4. Sessions (Only if Saved Sessions + Review Data + Decks are all enabled)
        // Hierarchy: Sessions requires Review Data which requires Decks
        if (syncReviewData && syncSavedSessions) {
            sessionsListener = db.collection("users").document(uid).collection("sessions")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    val sessions = snapshot?.toObjects(ActiveSession::class.java) ?: emptyList()
                    // Update Local DataStore
                    viewModelScope.launch {
                        preferenceManager.saveActiveSessions(sessions)
                    }
                }
        }
    }

    fun cleanup() {
        decksListener?.remove()
        cardsListener?.remove()
        tagsListener?.remove()
        sessionsListener?.remove()
    }

    // --- Sync Logic & CRUD ---

    suspend fun <T> safeWrite(task: Task<T>) {
        if (_isUserAnonymous.value) return
        try { task.await() } catch (e: Exception) { AppLogger.e(TAG, "Online write failed", e) }
    }

    fun saveDeckToFirestore(deck: net.ericclark.studiare.data.Deck) {
        if (!syncDecksAndCards) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("decks").document(deck.id)
            .set(deck, SetOptions.merge())
    }

    fun saveCardToFirestore(card: net.ericclark.studiare.data.Card) {
        if (!syncDecksAndCards) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("cards").document(card.id)
            .set(card, SetOptions.merge())
    }

    fun deleteDeckFromFirestore(deckId: String) {
        if (!syncDecksAndCards) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("decks").document(deckId).delete()
    }

    fun deleteCardFromFirestore(cardId: String) {
        if (!syncDecksAndCards) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("cards").document(cardId).delete()
    }

    // --- Session Sync ---

    fun saveSessionToFirestore(session: ActiveSession) {
        // Hierarchy check
        if (!syncDecksAndCards || !syncReviewData || !syncSavedSessions) return

        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("sessions").document(session.id)
            .set(session, SetOptions.merge())
    }

    fun saveSessionsBatch(sessions: List<ActiveSession>) {
        if (!syncDecksAndCards || !syncReviewData || !syncSavedSessions) return
        val uid = _userId.value ?: return

        sessions.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { session ->
                batch.set(db.collection("users").document(uid).collection("sessions").document(session.id), session, SetOptions.merge())
            }
            batch.commit() // Fire and forget for batch
        }
    }

    fun deleteSessionFromFirestore(sessionId: String) {
        if (!syncDecksAndCards || !syncReviewData || !syncSavedSessions) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("sessions").document(sessionId).delete()
    }

    // --- Tags ---

    fun saveTagDefinition(tagDef: net.ericclark.studiare.data.TagDefinition) {
        if (!syncDecksAndCards) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("tags").document(tagDef.id)
            .set(tagDef, SetOptions.merge())
    }

    fun deleteTagDefinition(tagDef: net.ericclark.studiare.data.TagDefinition) {
        if (!syncDecksAndCards) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("tags").document(tagDef.id).delete()
    }

    // --- Account Linking (Unchanged logic, simplified for brevity as implementation is same) ---
    fun linkGoogleAccount(credential: AuthCredential, onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        onProcessingChanged(true)
        _isSyncSetupPending.value = true
        pendingLocalDecks = _localDecks.value ?: emptyList()
        pendingLocalCards = _localCards.value ?: emptyList()

        user.linkWithCredential(credential)
            .addOnSuccessListener { checkForCloudConflict(onResult) }
            .addOnFailureListener { e ->
                if (e is FirebaseAuthUserCollisionException) {
                    auth.signInWithCredential(credential).addOnSuccessListener { checkForCloudConflict(onResult) }
                        .addOnFailureListener { onProcessingChanged(false); _isSyncSetupPending.value = false; onResult(false, it.message) }
                } else {
                    onProcessingChanged(false); _isSyncSetupPending.value = false; onResult(false, e.message)
                }
            }
    }

    private fun checkForCloudConflict(onResult: (Boolean, String?) -> Unit) {
        // Logic remains same as original file...
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            if (uid == null) { onProcessingChanged(false); _isSyncSetupPending.value = false; return@launch }
            try {
                val cloudDecks = db.collection("users").document(uid).collection("decks").get().await().toObjects(Deck::class.java)
                val cloudCards = db.collection("users").document(uid).collection("cards").get().await().toObjects(Card::class.java)

                if (pendingLocalDecks.isEmpty() && pendingLocalCards.isEmpty()) { onResult(true, null) }
                else if (cloudDecks.isEmpty() && cloudCards.isEmpty()) { uploadLocalDataToCloud(); onResult(true, null) }
                else { _showConflictDialog.value = true; onResult(true, null) }
            } catch(e: Exception) { onResult(false, e.message) }
            finally { onProcessingChanged(false); _isSyncSetupPending.value = false }
        }
    }

    fun resolveConflict(strategy: ConflictResolutionStrategy) {
        // Logic remains same as original file...
        viewModelScope.launch {
            _showConflictDialog.value = false
            onProcessingChanged(true)
            val uid = _userId.value ?: return@launch
            try {
                when (strategy) {
                    ConflictResolutionStrategy.USE_LOCAL_WIPE_CLOUD -> { deleteAllCloudData(uid); uploadLocalDataToCloud() }
                    ConflictResolutionStrategy.MERGE_KEEP_LOCAL -> { uploadLocalDataToCloud(merge = true, overwriteCloud = true) }
                    ConflictResolutionStrategy.MERGE_KEEP_CLOUD -> { uploadLocalDataToCloud(merge = true, overwriteCloud = false) }
                    else -> {}
                }
            } finally { onProcessingChanged(false); _isSyncSetupPending.value = false; pendingLocalDecks = emptyList(); pendingLocalCards = emptyList() }
        }
    }

    private suspend fun deleteAllCloudData(uid: String) {
        // Logic remains same...
        val deckSnap = db.collection("users").document(uid).collection("decks").get().await()
        deckSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
        val cardSnap = db.collection("users").document(uid).collection("cards").get().await()
        cardSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
    }

    private suspend fun uploadLocalDataToCloud(merge: Boolean = false, overwriteCloud: Boolean = true) {
        // Logic remains same...
        val uid = _userId.value ?: return
        val currentDecks = _localDecks.value ?: emptyList()
        val currentCards = _localCards.value ?: emptyList()
        val cloudDecksIds = if (merge && !overwriteCloud) currentDecks.map { it.id }.toSet() else emptySet()
        val cloudCardsIds = if (merge && !overwriteCloud) currentCards.map { it.id }.toSet() else emptySet()

        pendingLocalDecks.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { if (!merge || overwriteCloud || !cloudDecksIds.contains(it.id)) batch.set(db.collection("users").document(uid).collection("decks").document(it.id), it) }
            batch.commit().await()
        }
        pendingLocalCards.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { if (!merge || overwriteCloud || !cloudCardsIds.contains(it.id)) batch.set(db.collection("users").document(uid).collection("cards").document(it.id), it) }
            batch.commit().await()
        }
    }

    fun signOut() { auth.signOut() }
}