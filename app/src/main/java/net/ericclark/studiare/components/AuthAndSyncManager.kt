package net.ericclark.studiare.components

import android.util.Log
import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.Deck
import net.ericclark.studiare.data.TagDefinition
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.data.*
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
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * Manages Firebase Authentication, Firestore Data Syncing, and CRUD operations.
 * Acts as the single source of truth for remote data (Decks, Cards, Tags, Sessions).
 */
class AuthAndSyncManager(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferenceManager: PreferenceManager, // Added PreferenceManager
    private val viewModelScope: CoroutineScope,
    private val onProcessingChanged: (Boolean) -> Unit
) {
    private val TAG = "AuthAndSyncManager"

    // --- Data State Flows ---
    private val _localDecks = MutableStateFlow<List<Deck>?>(null)
    val localDecks: StateFlow<List<Deck>?> = _localDecks

    private val _localCards = MutableStateFlow<List<Card>?>(null)
    val localCards: StateFlow<List<Card>?> = _localCards

    private val _localTags = MutableStateFlow<List<TagDefinition>>(emptyList())
    val localTags: StateFlow<List<TagDefinition>> = _localTags

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
    private var syncOnlyOnWifi = true

    // Internal state for conflict resolution
    private var pendingLocalDecks: List<Deck> = emptyList()
    private var pendingLocalCards: List<Card> = emptyList()

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
                preferenceManager.syncSavedSessionsFlow,
                preferenceManager.syncOnlyOnWifiFlow
            ) { decks, review, sessions, wifi ->
                Quadruple(decks, review, sessions, wifi)
            }.distinctUntilChanged()
                .collectLatest { (decks, review, sessions, wifi) ->
                syncDecksAndCards = decks
                syncReviewData = review
                syncSavedSessions = sessions
                syncOnlyOnWifi = wifi

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

    data class Quadruple<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)

    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
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

        if (syncOnlyOnWifi && !isWifiConnected()) {
            Log.d(TAG, "Sync restricted to WiFi only. Detaching listeners.")
            return
        }

        // 1. Decks
        decksListener = db.collection("users").document(uid).collection("decks")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener

                // Read as FirestoreDeck, instantly translate to clean App Deck
                val decks = snapshot?.toObjects(FirestoreDeck::class.java)
                    ?.map { it.toAppDeck() } ?: emptyList()

                _localDecks.value = decks
            }

        // 2. Cards
        cardsListener = db.collection("users").document(uid).collection("cards")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                // Read as FirestoreCard, instantly translate to clean App Card
                val cards = snapshot?.toObjects(FirestoreCard::class.java)
                    ?.map { it.toAppCard() } ?: emptyList()

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

                    // Read as FirestoreActiveSession, instantly translate to clean App ActiveSession
                    val sessions = snapshot?.toObjects(FirestoreActiveSession::class.java)
                        ?.map { it.toAppActiveSession() } ?: emptyList()

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

    fun saveDeckToFirestore(deck: Deck) {
        if (!syncDecksAndCards) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return

        // Translate clean App Card to FirestoreCard right before saving
        val dbDeck = deck.toFirestoreDeck()

        db.collection("users").document(uid).collection("decks").document(deck.id)
            .set(dbDeck, SetOptions.merge())
    }

    fun saveCardToFirestore(card: Card) {
        if (!syncDecksAndCards) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return

        // Translate clean App Card to FirestoreCard right before saving
        val dbCard = card.toFirestoreCard()

        db.collection("users").document(uid).collection("cards").document(card.id)
            .set(dbCard, SetOptions.merge()) // Save the DTO
    }

    fun deleteDeckFromFirestore(deckId: String) {
        if (!syncDecksAndCards) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("decks").document(deckId).delete()
    }

    fun deleteCardFromFirestore(cardId: String) {
        if (!syncDecksAndCards) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("cards").document(cardId).delete()
    }

    // --- Session Sync ---
    fun saveSessionToFirestore(session: ActiveSession) {
        if (!syncDecksAndCards || !syncReviewData || !syncSavedSessions) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return

        // Translate clean App Card to FirestoreCard right before saving
        val dbSession = session.toFirestoreActiveSession()

        db.collection("users").document(uid).collection("sessions").document(session.id)
            .set(dbSession, SetOptions.merge())
    }

    fun saveSessionsBatch(sessions: List<ActiveSession>) {
        if (!syncDecksAndCards || !syncReviewData || !syncSavedSessions) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return

        sessions.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { session ->
                batch.set(db.collection("users").document(uid).collection("sessions").document(session.id), session, SetOptions.merge())
            }
            batch.commit()
        }
    }

    fun deleteSessionFromFirestore(sessionId: String) {
        if (!syncDecksAndCards || !syncReviewData || !syncSavedSessions) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("sessions").document(sessionId).delete()
    }

    // Atomic Batch Delete
    fun deleteSessionsBatch(sessionIds: List<String>) {
        if (!syncDecksAndCards || !syncReviewData || !syncSavedSessions) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return

        sessionIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id ->
                batch.delete(db.collection("users").document(uid).collection("sessions").document(id))
            }
            // Add a failure listener just in case, though fire-and-forget is usually fine here
            batch.commit().addOnFailureListener { e ->
                AppLogger.e(TAG, "Failed to batch delete sessions", e)
            }
        }
    }

    // --- Tags ---

    fun saveTagDefinition(tagDef: TagDefinition) {
        if (!syncDecksAndCards) return
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("tags").document(tagDef.id)
            .set(tagDef, SetOptions.merge())
    }

    fun deleteTagDefinition(tagDef: TagDefinition) {
        if (!syncDecksAndCards) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
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