package net.ericclark.studiare.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import net.ericclark.studiare.AppLogger
import net.ericclark.studiare.ConflictResolutionStrategy
import net.ericclark.studiare.PreferenceManager
import net.ericclark.studiare.data.*

/**
 * Manages Firebase Authentication and Offline-First Background Syncing.
 * Triggers syncs on app startup and when backgrounded to push Room DB changes to Firestore.
 */
class AuthAndSyncManager(
    private val context: Context,
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val preferenceManager: PreferenceManager,
    private val viewModelScope: CoroutineScope,
    private val onProcessingChanged: (Boolean) -> Unit
) {
    private val TAG = "AuthAndSyncManager"

    // --- Room Database ---
    private val database = AppDatabase.getDatabase(context)
    private val deckDao = database.deckDao()
    private val cardDao = database.cardDao()

    // --- Data State Flows (For legacy items not yet in Room) ---
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

    // Firestore listeners (Only for items not yet in Room)
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
                    triggerSync() // Sync when network enables
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

        // Setup App Lifecycle Sync Triggers (Sync on Open and on Minimize)
        try {
            ProcessLifecycleOwner.get().lifecycle.addObserver(LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_START || event == Lifecycle.Event.ON_STOP) {
                    triggerSync()
                }
            })
        } catch (e: Exception) {
            AppLogger.e(TAG, "ProcessLifecycleOwner not found. Sync relies on Auth changes.", e)
        }
    }

    data class Quadruple<T1, T2, T3, T4>(val t1: T1, val t2: T2, val t3: T3, val t4: T4)

    private fun isWifiConnected(): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) || capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
    }

    private fun signInAnonymously() {
        auth.signInAnonymously().addOnFailureListener { e -> AppLogger.e(TAG, "Auth failed", e) }
    }

    // --- BACKGROUND SYNC ENGINE (ROOM <-> FIRESTORE) ---

    fun triggerSync() {
        if (_isUserAnonymous.value || !syncDecksAndCards) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                pushLocalChanges(uid)
                pullRemoteChanges(uid)
            } catch (e: Exception) {
                AppLogger.e(TAG, "Background sync failed", e)
            }
        }
    }

    private suspend fun pushLocalChanges(uid: String) {
        val pendingDecks = deckDao.getPendingSyncDecks()
        val pendingCards = cardDao.getPendingSyncCards()

        // 1. Push Decks
        if (pendingDecks.isNotEmpty()) {
            pendingDecks.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { deck ->
                    val ref = db.collection("users").document(uid).collection("decks").document(deck.id)
                    if (deck.isDeleted) batch.delete(ref)
                    else batch.set(ref, deck.toFirestoreDeck(), SetOptions.merge())
                }
                batch.commit().await()

                // Mark locally as successfully synced or delete completely
                chunk.forEach { deck ->
                    if (deck.isDeleted) deckDao.hardDelete(deck.id)
                    else deckDao.insertOrUpdate(deck.copy(isPendingSync = false))
                }
            }
        }

        // 2. Push Cards
        if (pendingCards.isNotEmpty()) {
            pendingCards.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { card ->
                    val ref = db.collection("users").document(uid).collection("cards").document(card.id)
                    if (card.isDeleted) batch.delete(ref)
                    else batch.set(ref, card.toFirestoreCard(), SetOptions.merge())
                }
                batch.commit().await()

                // Mark locally as successfully synced or delete completely
                chunk.forEach { card ->
                    if (card.isDeleted) cardDao.hardDelete(card.id)
                    else cardDao.insertOrUpdate(card.copy(isPendingSync = false))
                }
            }
        }
    }

    private suspend fun pullRemoteChanges(uid: String) {
        // 1. Pull Decks
        val remoteDecksSnap = db.collection("users").document(uid).collection("decks").get().await()
        val remoteDecks = remoteDecksSnap.toObjects(FirestoreDeck::class.java).map { it.toAppDeck() }

        val localDecksMap = deckDao.getAllActiveDecks().first().associateBy { it.id }
        val decksToSave = mutableListOf<Deck>()

        remoteDecks.forEach { remoteDeck ->
            val localDeck = localDecksMap[remoteDeck.id]
            // Insert if it's new, or if the cloud version is strictly newer
            if (localDeck == null || remoteDeck.updatedAt > localDeck.updatedAt) {
                decksToSave.add(remoteDeck.copy(isPendingSync = false, isDeleted = false))
            }
        }
        if (decksToSave.isNotEmpty()) deckDao.insertOrUpdateAll(decksToSave)

        // 2. Pull Cards
        val remoteCardsSnap = db.collection("users").document(uid).collection("cards").get().await()
        val remoteCards = remoteCardsSnap.toObjects(FirestoreCard::class.java).map { it.toAppCard() }

        val localCardsMap = cardDao.getAllActiveCards().first().associateBy { it.id }
        val cardsToSave = mutableListOf<Card>()

        remoteCards.forEach { remoteCard ->
            val localCard = localCardsMap[remoteCard.id]
            // Insert if it's new, or if the cloud version is strictly newer
            if (localCard == null || remoteCard.updatedAt > localCard.updatedAt) {
                cardsToSave.add(remoteCard.copy(isPendingSync = false, isDeleted = false))
            }
        }
        if (cardsToSave.isNotEmpty()) cardDao.insertOrUpdateAll(cardsToSave)
    }

    // --- Legacy Listeners (For items not yet in Room) ---

    private fun setupFirestoreListeners(uid: String) {
        tagsListener?.remove()
        sessionsListener?.remove()
        tagsListener = null
        sessionsListener = null

        if (!syncDecksAndCards) return
        if (syncOnlyOnWifi && !isWifiConnected()) return

        // Tags (Always sync if Decks are synced)
        tagsListener = db.collection("users").document(uid).collection("tags")
            .orderBy("name")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                if (snapshot != null && snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

                val tags = snapshot?.toObjects(TagDefinition::class.java) ?: emptyList()
                _localTags.value = tags
            }

        // Sessions
        if (syncReviewData && syncSavedSessions) {
            sessionsListener = db.collection("users").document(uid).collection("sessions")
                .addSnapshotListener { snapshot, e ->
                    if (e != null) return@addSnapshotListener
                    if (snapshot != null && snapshot.metadata.hasPendingWrites()) return@addSnapshotListener

                    val sessions = snapshot?.toObjects(FirestoreActiveSession::class.java)
                        ?.map { it.toAppActiveSession() } ?: emptyList()

                    viewModelScope.launch { preferenceManager.saveActiveSessions(sessions) }
                }
        }
    }

    fun cleanup() {
        tagsListener?.remove()
        sessionsListener?.remove()
    }

    suspend fun <T> safeWrite(task: Task<T>) {
        if (_isUserAnonymous.value) return
        try { task.await() } catch (e: Exception) { AppLogger.e(TAG, "Online write failed", e) }
    }

    // --- Session Sync (Unchanged, writes directly to DB) ---

    fun saveSessionToFirestore(session: ActiveSession) {
        if (!syncDecksAndCards || !syncReviewData || !syncSavedSessions) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return

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
                val dbSession = session.toFirestoreActiveSession()
                batch.set(db.collection("users").document(uid).collection("sessions").document(session.id), dbSession, SetOptions.merge())
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

    fun deleteSessionsBatch(sessionIds: List<String>) {
        if (!syncDecksAndCards || !syncReviewData || !syncSavedSessions) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return

        sessionIds.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { id -> batch.delete(db.collection("users").document(uid).collection("sessions").document(id)) }
            batch.commit().addOnFailureListener { e -> AppLogger.e(TAG, "Failed to batch delete sessions", e) }
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

    // --- Account Linking & Conflict Resolution ---

    fun linkGoogleAccount(credential: AuthCredential, onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        onProcessingChanged(true)
        _isSyncSetupPending.value = true

        // Capture local snapshot safely before attempting merge
        viewModelScope.launch {
            pendingLocalDecks = deckDao.getAllActiveDecks().first()
            pendingLocalCards = cardDao.getAllActiveCards().first()

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
    }

    private fun checkForCloudConflict(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            if (uid == null) { onProcessingChanged(false); _isSyncSetupPending.value = false; return@launch }
            try {
                val cloudDecks = db.collection("users").document(uid).collection("decks").get().await().toObjects(FirestoreDeck::class.java)
                val cloudCards = db.collection("users").document(uid).collection("cards").get().await().toObjects(FirestoreCard::class.java)

                if (pendingLocalDecks.isEmpty() && pendingLocalCards.isEmpty()) { onResult(true, null) }
                else if (cloudDecks.isEmpty() && cloudCards.isEmpty()) { uploadLocalDataToCloud(); onResult(true, null) }
                else { _showConflictDialog.value = true; onResult(true, null) }
            } catch(e: Exception) { onResult(false, e.message) }
            finally { onProcessingChanged(false); _isSyncSetupPending.value = false }
        }
    }

    fun resolveConflict(strategy: ConflictResolutionStrategy) {
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
                triggerSync() // Trigger a final pull to establish parity
            } finally { onProcessingChanged(false); _isSyncSetupPending.value = false; pendingLocalDecks = emptyList(); pendingLocalCards = emptyList() }
        }
    }

    private suspend fun deleteAllCloudData(uid: String) {
        val deckSnap = db.collection("users").document(uid).collection("decks").get().await()
        deckSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
        val cardSnap = db.collection("users").document(uid).collection("cards").get().await()
        cardSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
    }

    private suspend fun uploadLocalDataToCloud(merge: Boolean = false, overwriteCloud: Boolean = true) {
        val uid = _userId.value ?: return
        val cloudDecksIds = if (merge && !overwriteCloud) pendingLocalDecks.map { it.id }.toSet() else emptySet()
        val cloudCardsIds = if (merge && !overwriteCloud) pendingLocalCards.map { it.id }.toSet() else emptySet()

        pendingLocalDecks.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach {
                if (!merge || overwriteCloud || !cloudDecksIds.contains(it.id)) {
                    batch.set(db.collection("users").document(uid).collection("decks").document(it.id), it.toFirestoreDeck())
                }
            }
            batch.commit().await()
            deckDao.insertOrUpdateAll(chunk.map { it.copy(isPendingSync = false) })
        }

        pendingLocalCards.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach {
                if (!merge || overwriteCloud || !cloudCardsIds.contains(it.id)) {
                    batch.set(db.collection("users").document(uid).collection("cards").document(it.id), it.toFirestoreCard())
                }
            }
            batch.commit().await()
            cardDao.insertOrUpdateAll(chunk.map { it.copy(isPendingSync = false) })
        }
    }

    fun signOut() { auth.signOut() }
}