package net.ericclark.studiare.components

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ProcessLifecycleOwner
import com.google.android.gms.tasks.Task
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore
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
import kotlinx.coroutines.withContext
import net.ericclark.studiare.ConflictResolutionStrategy
import net.ericclark.studiare.PreferenceManager
import net.ericclark.studiare.data.*

/**
 * Manages Firebase Authentication and 100% Offline-First Background Syncing.
 * Implements Delta Syncing to prevent excessive Firestore reads.
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

    // --- Room Database & Sync Prefs ---
    private val database = AppDatabase.getDatabase(context)
    private val deckDao = database.deckDao()
    private val cardDao = database.cardDao()
    private val tagDao = database.tagDao()
    private val sessionDao = database.sessionDao()
    private val deckCollectionDao = database.deckCollectionDao()

    // NEW: Used to track the last delta sync to prevent massive reads
    private val syncPrefs = context.getSharedPreferences("studiare_sync_prefs", Context.MODE_PRIVATE)

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

    // --- Sync State Trackers ---
    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing

    private val _hasPendingChanges = MutableStateFlow(false)
    val hasPendingChanges: StateFlow<Boolean> = _hasPendingChanges

    // Sync Preferences Checks
    private var syncDecksAndCards = true
    private var syncReviewData = true
    private var syncSavedSessions = true
    private var syncOnlyOnWifi = true

    // Internal state for conflict resolution
    private var pendingLocalDecks: List<Deck> = emptyList()
    private var pendingLocalCards: List<Card> = emptyList()

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
                    triggerSync()
                }
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

    fun checkPendingChanges() {
        viewModelScope.launch(Dispatchers.IO) {
            val pDecks = deckDao.getPendingSyncDecks().isNotEmpty()
            val pCards = cardDao.getPendingSyncCards().isNotEmpty()
            val pTags = tagDao.getPendingSyncTags().isNotEmpty()
            val pSessions = sessionDao.getPendingSyncSessions().isNotEmpty()
            val pCollections = deckCollectionDao.getPendingSyncCollections().isNotEmpty()
            _hasPendingChanges.value = pDecks || pCards || pTags || pSessions || pCollections
        }
    }

    // --- BACKGROUND SYNC ENGINE (ROOM <-> FIRESTORE) ---

    fun triggerSync() {
        if (_isUserAnonymous.value || !syncDecksAndCards) return
        if (syncOnlyOnWifi && !isWifiConnected()) return
        val uid = _userId.value ?: return

        // Prevent overlapping syncs
        if (_isSyncing.value) return
        _isSyncing.value = true

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Get the timestamp of the last successful sync
                val lastSyncTime = syncPrefs.getLong("last_sync_$uid", 0L)
                // Record the start time of THIS sync to avoid missing concurrent updates
                val currentSyncTime = System.currentTimeMillis()

                pushLocalChanges(uid)
                pullRemoteChanges(uid, lastSyncTime)

                // If successful, save the new timestamp
                syncPrefs.edit().putLong("last_sync_$uid", currentSyncTime).apply()

            } catch (e: Exception) {
                AppLogger.e(TAG, "Background sync failed", e)
            } finally {
                _isSyncing.value = false
                checkPendingChanges()
            }
        }
    }

    private suspend fun pushLocalChanges(uid: String) {
        // 1. Push Decks
        val pendingDecks = deckDao.getPendingSyncDecks()
        if (pendingDecks.isNotEmpty()) {
            pendingDecks.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { deck ->
                    val ref = db.collection("users").document(uid).collection("decks").document(deck.id)
                    if (deck.isDeleted) batch.delete(ref)
                    else batch.set(ref, deck.toFirestoreDeck(), SetOptions.merge())
                }
                batch.commit().await()
                chunk.forEach { deck ->
                    if (deck.isDeleted) deckDao.hardDelete(deck.id)
                    else deckDao.insertOrUpdate(deck.copy(isPendingSync = false))
                }
            }
        }

        // 2. Push Cards
        val pendingCards = cardDao.getPendingSyncCards()
        if (pendingCards.isNotEmpty()) {
            pendingCards.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { card ->
                    val ref = db.collection("users").document(uid).collection("cards").document(card.id)
                    if (card.isDeleted) batch.delete(ref)
                    else batch.set(ref, card.toFirestoreCard(), SetOptions.merge())
                }
                batch.commit().await()
                chunk.forEach { card ->
                    if (card.isDeleted) cardDao.hardDelete(card.id)
                    else cardDao.insertOrUpdate(card.copy(isPendingSync = false))
                }
            }
        }

        // 3. Push Tags
        val pendingTags = tagDao.getPendingSyncTags()
        if (pendingTags.isNotEmpty()) {
            pendingTags.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { tag ->
                    val ref = db.collection("users").document(uid).collection("tags").document(tag.id)
                    if (tag.isDeleted) batch.delete(ref)
                    else batch.set(ref, tag, SetOptions.merge())
                }
                batch.commit().await()
                chunk.forEach { tag ->
                    if (tag.isDeleted) tagDao.hardDelete(tag.id)
                    else tagDao.insertOrUpdate(tag.copy(isPendingSync = false))
                }
            }
        }

        // 4. Push Sessions (Only if sync preferences allow)
        if (syncReviewData && syncSavedSessions) {
            val pendingSessions = sessionDao.getPendingSyncSessions()
            if (pendingSessions.isNotEmpty()) {
                pendingSessions.chunked(400).forEach { chunk ->
                    val batch = db.batch()
                    chunk.forEach { session ->
                        val ref = db.collection("users").document(uid).collection("sessions").document(session.id)
                        if (session.isDeleted) batch.delete(ref)
                        else batch.set(ref, session.toFirestoreActiveSession(), SetOptions.merge())
                    }
                    batch.commit().await()
                    chunk.forEach { session ->
                        if (session.isDeleted) sessionDao.hardDelete(session.id)
                        else sessionDao.insertOrUpdate(session.copy(isPendingSync = false))
                    }
                }
            }
        }// 5. Push Collections
        val pendingCollections = deckCollectionDao.getPendingSyncCollections()
        if (pendingCollections.isNotEmpty()) {
            pendingCollections.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { collection ->
                    val ref = db.collection("users").document(uid).collection("deckCollections").document(collection.id)
                    if (collection.isDeleted) {
                        batch.delete(ref)
                    } else {
                        batch.set(ref, FirestoreDeckCollection(
                            id = collection.id, name = collection.name, description = collection.description,
                            createdAt = collection.createdAt, updatedAt = collection.updatedAt,
                            isDeleted = collection.isDeleted, ownerId = uid
                        ), SetOptions.merge())
                    }
                }
                batch.commit().await()
                chunk.forEach { collection ->
                    if (!collection.isDeleted) {
                        deckCollectionDao.insertOrUpdate(collection.copy(isPendingSync = false))
                    }
                }
            }
        }

        // 6. Push Cross References (Full push for active collections)
        val allCollectionsFlow = deckCollectionDao.getCollectionsWithDecks().first()
        val crossRefsToPush = allCollectionsFlow.flatMap { collData ->
            collData.decks.map { deck ->
                FirestoreCollectionDeckCrossRef(
                    id = "${collData.collection.id}_${deck.id}",
                    collectionId = collData.collection.id,
                    deckId = deck.id,
                    ownerId = uid
                )
            }
        }
        if (crossRefsToPush.isNotEmpty()) {
            crossRefsToPush.chunked(400).forEach { chunk ->
                val batch = db.batch()
                chunk.forEach { crossRef ->
                    val ref = db.collection("users").document(uid).collection("collectionLinks").document(crossRef.id)
                    batch.set(ref, crossRef, SetOptions.merge())
                }
                batch.commit().await()
            }
        }
    }

    private suspend fun pullRemoteChanges(uid: String, lastSyncTime: Long) {
        // 1. Pull Decks (DELTA SYNC)
        val decksQuery = db.collection("users").document(uid).collection("decks")
        val remoteDecksSnap = if (lastSyncTime > 0) decksQuery.whereGreaterThan("updatedAt", lastSyncTime).get().await() else decksQuery.get().await()
        val remoteDecks = remoteDecksSnap.toObjects(FirestoreDeck::class.java).map { it.toAppDeck() }
        val localDecksMap = deckDao.getAllActiveDecks().first().associateBy { it.id }
        val decksToSave = mutableListOf<Deck>()

        remoteDecks.forEach { remoteDeck ->
            val localDeck = localDecksMap[remoteDeck.id]
            if (localDeck == null || remoteDeck.updatedAt > localDeck.updatedAt) {
                decksToSave.add(remoteDeck.copy(isPendingSync = false, isDeleted = false))
            }
        }
        if (decksToSave.isNotEmpty()) deckDao.insertOrUpdateAll(decksToSave)

        // 2. Pull Cards (DELTA SYNC - Saves massive reads)
        val cardsQuery = db.collection("users").document(uid).collection("cards")
        val remoteCardsSnap = if (lastSyncTime > 0) cardsQuery.whereGreaterThan("updatedAt", lastSyncTime).get().await() else cardsQuery.get().await()
        val remoteCards = remoteCardsSnap.toObjects(FirestoreCard::class.java).map { it.toAppCard() }
        val localCardsMap = cardDao.getAllActiveCards().first().associateBy { it.id }
        val cardsToSave = mutableListOf<Card>()

        remoteCards.forEach { remoteCard ->
            val localCard = localCardsMap[remoteCard.id]
            if (localCard == null || remoteCard.updatedAt > localCard.updatedAt) {
                cardsToSave.add(remoteCard.copy(isPendingSync = false, isDeleted = false))
            }
        }
        if (cardsToSave.isNotEmpty()) cardDao.insertOrUpdateAll(cardsToSave)

        // 3. Pull Tags (Always a full pull, as they lack an updatedAt field, but usually less than 20 reads total)
        val remoteTagsSnap = db.collection("users").document(uid).collection("tags").get().await()
        val remoteTags = remoteTagsSnap.toObjects(TagDefinition::class.java)
        val localTagsMap = tagDao.getAllActiveTags().first().associateBy { it.id }
        val tagsToSave = mutableListOf<TagDefinition>()

        remoteTags.forEach { remoteTag ->
            val localTag = localTagsMap[remoteTag.id]
            if (localTag == null || remoteTag.createdAt > localTag.createdAt) {
                tagsToSave.add(remoteTag.copy(isPendingSync = false, isDeleted = false))
            }
        }
        if (tagsToSave.isNotEmpty()) tagDao.insertOrUpdateAll(tagsToSave)

        // 4. Pull Sessions (DELTA SYNC)
        if (syncReviewData && syncSavedSessions) {
            val sessionsQuery = db.collection("users").document(uid).collection("sessions")
            val remoteSessionsSnap = if (lastSyncTime > 0) sessionsQuery.whereGreaterThan("lastAccessed", lastSyncTime).get().await() else sessionsQuery.get().await()
            val remoteSessions = remoteSessionsSnap.toObjects(FirestoreActiveSession::class.java).map { it.toAppActiveSession() }
            val localSessionsMap = sessionDao.getAllActiveSessions().first().associateBy { it.id }
            val sessionsToSave = mutableListOf<ActiveSession>()

            remoteSessions.forEach { remoteSession ->
                val localSession = localSessionsMap[remoteSession.id]
                if (localSession == null || remoteSession.lastAccessed > localSession.lastAccessed) {
                    sessionsToSave.add(remoteSession.copy(isPendingSync = false, isDeleted = false))
                }
            }
            if (sessionsToSave.isNotEmpty()) sessionDao.insertOrUpdateAll(sessionsToSave)
        }

        // 5. Pull Collections (DELTA SYNC)
        val collectionsQuery = db.collection("users").document(uid).collection("deckCollections")
        val remoteCollectionsSnap = if (lastSyncTime > 0) collectionsQuery.whereGreaterThan("updatedAt", lastSyncTime).get().await() else collectionsQuery.get().await()
        val remoteCollections = remoteCollectionsSnap.toObjects(FirestoreDeckCollection::class.java)

        val localCollectionsMap = deckCollectionDao.getAllActiveCollections().first().associateBy { it.id }
        val collectionsToSave = mutableListOf<DeckCollection>()

        remoteCollections.forEach { remoteColl ->
            val localColl = localCollectionsMap[remoteColl.id]
            if (localColl == null || remoteColl.updatedAt > localColl.updatedAt) {
                collectionsToSave.add(
                    DeckCollection(
                        id = remoteColl.id, name = remoteColl.name, description = remoteColl.description,
                        createdAt = remoteColl.createdAt, updatedAt = remoteColl.updatedAt,
                        isDeleted = remoteColl.isDeleted, isPendingSync = false
                    )
                )
            }
        }
        if (collectionsToSave.isNotEmpty()) {
            collectionsToSave.forEach { deckCollectionDao.insertOrUpdate(it) }
        }

        // 6. Pull Cross Refs
        val crossRefsSnap = db.collection("users").document(uid).collection("collectionLinks").get().await()
        val remoteCrossRefs = crossRefsSnap.toObjects(FirestoreCollectionDeckCrossRef::class.java)

        remoteCrossRefs.forEach { remoteRef ->
            deckCollectionDao.insertCrossRef(CollectionDeckCrossRef(remoteRef.collectionId, remoteRef.deckId))
        }
    }

    fun cleanup() {}

    suspend fun <T> safeWrite(task: Task<T>) {
        if (_isUserAnonymous.value) return
        try { task.await() } catch (e: Exception) { AppLogger.e(TAG, "Online write failed", e) }
    }

    // --- Account Linking & Conflict Resolution ---

    fun linkGoogleAccount(credential: AuthCredential, onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        onProcessingChanged(true)
        _isSyncSetupPending.value = true

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

                // Reset the sync timestamp to 0 so the next triggerSync pulls EVERYTHING fresh
                syncPrefs.edit().putLong("last_sync_$uid", 0L).apply()
                triggerSync()
            } finally { onProcessingChanged(false); _isSyncSetupPending.value = false; pendingLocalDecks = emptyList(); pendingLocalCards = emptyList() }
        }
    }

    private suspend fun deleteAllCloudData(uid: String) {
        val deckSnap = db.collection("users").document(uid).collection("decks").get().await()
        deckSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
        val cardSnap = db.collection("users").document(uid).collection("cards").get().await()
        cardSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
        val tagSnap = db.collection("users").document(uid).collection("tags").get().await()
        tagSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
        val sessionSnap = db.collection("users").document(uid).collection("sessions").get().await()
        sessionSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
        val collSnap = db.collection("users").document(uid).collection("deckCollections").get().await()
        collSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
        val linkSnap = db.collection("users").document(uid).collection("collectionLinks").get().await()
        linkSnap.documents.chunked(400).forEach { chunk -> val b = db.batch(); chunk.forEach { b.delete(it.reference) }; b.commit().await() }
    }

    private suspend fun uploadLocalDataToCloud(merge: Boolean = false, overwriteCloud: Boolean = true) = withContext(Dispatchers.IO) {
        val uid = _userId.value ?: return@withContext
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

        // Tags and Sessions are pushed regardless of Conflict strategy for safety
        val localTags = tagDao.getAllActiveTags().first()
        localTags.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.set(db.collection("users").document(uid).collection("tags").document(it.id), it) }
            batch.commit().await()
            tagDao.insertOrUpdateAll(chunk.map { it.copy(isPendingSync = false) })
        }

        val localSessions = sessionDao.getAllActiveSessions().first()
        localSessions.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.set(db.collection("users").document(uid).collection("sessions").document(it.id), it.toFirestoreActiveSession()) }
            batch.commit().await()
            sessionDao.insertOrUpdateAll(chunk.map { it.copy(isPendingSync = false) })
        }

        val localCollections = deckCollectionDao.getCollectionsWithDecks().first()
        localCollections.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { collData ->
                batch.set(db.collection("users").document(uid).collection("deckCollections").document(collData.collection.id), FirestoreDeckCollection(
                    id = collData.collection.id, name = collData.collection.name, description = collData.collection.description,
                    createdAt = collData.collection.createdAt, updatedAt = collData.collection.updatedAt,
                    isDeleted = collData.collection.isDeleted, ownerId = uid
                ))
                collData.decks.forEach { deck ->
                    val crossRef = FirestoreCollectionDeckCrossRef(
                        id = "${collData.collection.id}_${deck.id}", collectionId = collData.collection.id, deckId = deck.id, ownerId = uid
                    )
                    batch.set(db.collection("users").document(uid).collection("collectionLinks").document(crossRef.id), crossRef)
                }
            }
            batch.commit().await()
            chunk.forEach { deckCollectionDao.insertOrUpdate(it.collection.copy(isPendingSync = false)) }
        }
    }

    fun signOut() { auth.signOut() }
}