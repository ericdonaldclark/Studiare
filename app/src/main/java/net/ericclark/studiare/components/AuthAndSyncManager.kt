package net.ericclark.studiare.components

import android.util.Log
import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.Deck
import net.ericclark.studiare.data.TagDefinition
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Manages Firebase Authentication, Firestore Data Syncing, and CRUD operations.
 * Acts as the single source of truth for remote data (Decks, Cards, Tags).
 */
class AuthAndSyncManager(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth,
    private val viewModelScope: CoroutineScope,
    private val onProcessingChanged: (Boolean) -> Unit
) {
    private val TAG = "AuthAndSyncManager"

    // --- Data State Flows (Updated to Nullable) ---
    // Initialize with null to represent "Loading" state
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

    // Internal state for conflict resolution
    private var pendingLocalDecks: List<net.ericclark.studiare.data.Deck> = emptyList()
    private var pendingLocalCards: List<net.ericclark.studiare.data.Card> = emptyList()

    // Firestore listeners
    private var decksListener: ListenerRegistration? = null
    private var cardsListener: ListenerRegistration? = null
    private var tagsListener: ListenerRegistration? = null

    init {
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

        // Initial check
        if (auth.currentUser == null) {
            signInAnonymously()
        }
    }

    private fun signInAnonymously() {
        auth.signInAnonymously()
            .addOnSuccessListener {
                // Auth listener will handle the rest
            }
            .addOnFailureListener { e ->
                AppLogger.e(TAG, "Auth failed", e)
            }
    }

    private fun setupFirestoreListeners(uid: String) {
        decksListener?.remove()
        cardsListener?.remove()
        tagsListener?.remove()

        decksListener = db.collection("users").document(uid).collection("decks")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val decks = snapshot?.toObjects(Deck::class.java) ?: emptyList()
                _localDecks.value = decks
            }

        cardsListener = db.collection("users").document(uid).collection("cards")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val cards = snapshot?.toObjects(Card::class.java) ?: emptyList()
                _localCards.value = cards
            }

        tagsListener = db.collection("users").document(uid).collection("tags")
            .orderBy("name")
            .addSnapshotListener { snapshot, e ->
                if (e != null) return@addSnapshotListener
                val tags = snapshot?.toObjects(TagDefinition::class.java) ?: emptyList()
                _localTags.value = tags
            }
    }

    fun cleanup() {
        decksListener?.remove()
        cardsListener?.remove()
        tagsListener?.remove()
    }

    // --- Account Linking & Sync Logic ---

    fun linkGoogleAccount(credential: AuthCredential, onResult: (Boolean, String?) -> Unit) {
        val user = auth.currentUser ?: return
        onProcessingChanged(true)
        _isSyncSetupPending.value = true

        // Capture Local Data (Handle potential nulls safely)
        pendingLocalDecks = _localDecks.value ?: emptyList()
        pendingLocalCards = _localCards.value ?: emptyList()
        AppLogger.d(TAG, "Capturing local data: ${pendingLocalDecks.size} decks, ${pendingLocalCards.size} cards")

        user.linkWithCredential(credential)
            .addOnSuccessListener {
                Log.d(TAG, "Link successful. Checking cloud data...")
                checkForCloudConflict(onResult)
            }
            .addOnFailureListener { e ->
                if (e is FirebaseAuthUserCollisionException) {
                    Log.d(TAG, "Account already exists. Switching to it.")
                    auth.signInWithCredential(credential)
                        .addOnSuccessListener {
                            AppLogger.w(TAG, "Account exists, switching...", e)
                            checkForCloudConflict(onResult)
                        }
                        .addOnFailureListener { signInError ->
                            onProcessingChanged(false)
                            _isSyncSetupPending.value = false
                            onResult(false, signInError.message)
                        }
                } else {
                    AppLogger.e(TAG, "Link failed", e)
                    onProcessingChanged(false)
                    _isSyncSetupPending.value = false
                    onResult(false, e.message)
                }
            }
    }

    private fun checkForCloudConflict(onResult: (Boolean, String?) -> Unit) {
        viewModelScope.launch {
            val uid = auth.currentUser?.uid
            if (uid == null) {
                onProcessingChanged(false)
                _isSyncSetupPending.value = false
                return@launch
            }

            try {
                val cloudDecksSnapshot = db.collection("users").document(uid).collection("decks").get().await()
                val cloudCardsSnapshot = db.collection("users").document(uid).collection("cards").get().await()

                val cloudDecks = cloudDecksSnapshot.toObjects(Deck::class.java)
                val cloudCards = cloudCardsSnapshot.toObjects(Card::class.java)

                Log.d(TAG, "Cloud check: ${cloudDecks.size} decks, ${cloudCards.size} cards")

                if (pendingLocalDecks.isEmpty() && pendingLocalCards.isEmpty()) {
                    // Case A: No local data. Use cloud.
                    onProcessingChanged(false)
                    _isSyncSetupPending.value = false
                    onResult(true, null)
                } else if (cloudDecks.isEmpty() && cloudCards.isEmpty()) {
                    // Case B: No cloud data. Upload local.
                    uploadLocalDataToCloud()
                    onProcessingChanged(false)
                    _isSyncSetupPending.value = false
                    onResult(true, null)
                } else {
                    // Case C: Conflict.
                    _showConflictDialog.value = true
                    onResult(true, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error checking cloud data", e)
                onProcessingChanged(false)
                _isSyncSetupPending.value = false
                onResult(false, "Failed to sync: ${e.message}")
            }
        }
    }

    fun resolveConflict(strategy: net.ericclark.studiare.ConflictResolutionStrategy) {
        viewModelScope.launch {
            _showConflictDialog.value = false
            onProcessingChanged(true)
            try {
                val uid = _userId.value ?: return@launch
                Log.d(TAG, "Resolving conflict with strategy: $strategy")

                when (strategy) {
                    ConflictResolutionStrategy.USE_CLOUD_WIPE_LOCAL -> { /* Do nothing, listeners update */ }
                    ConflictResolutionStrategy.USE_LOCAL_WIPE_CLOUD -> {
                        deleteAllCloudData(uid)
                        uploadLocalDataToCloud()
                    }
                    ConflictResolutionStrategy.MERGE_KEEP_LOCAL -> {
                        uploadLocalDataToCloud(merge = true, overwriteCloud = true)
                    }
                    ConflictResolutionStrategy.MERGE_KEEP_CLOUD -> {
                        uploadLocalDataToCloud(merge = true, overwriteCloud = false)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Conflict resolution failed", e)
            } finally {
                onProcessingChanged(false)
                _isSyncSetupPending.value = false
                pendingLocalDecks = emptyList()
                pendingLocalCards = emptyList()
            }
        }
    }

    private suspend fun deleteAllCloudData(uid: String) {
        val deckSnap = db.collection("users").document(uid).collection("decks").get().await()
        deckSnap.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }

        val cardSnap = db.collection("users").document(uid).collection("cards").get().await()
        cardSnap.documents.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { batch.delete(it.reference) }
            batch.commit().await()
        }
    }

    private suspend fun uploadLocalDataToCloud(merge: Boolean = false, overwriteCloud: Boolean = true) {
        val uid = _userId.value ?: return

        // Need to handle nulls here, though conflict resolution implies data exists
        val currentDecks = _localDecks.value ?: emptyList()
        val currentCards = _localCards.value ?: emptyList()

        val cloudDecksIds = if (merge && !overwriteCloud) currentDecks.map { it.id }.toSet() else emptySet()
        val cloudCardsIds = if (merge && !overwriteCloud) currentCards.map { it.id }.toSet() else emptySet()

        pendingLocalDecks.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { deck ->
                if (!merge || overwriteCloud || !cloudDecksIds.contains(deck.id)) {
                    batch.set(db.collection("users").document(uid).collection("decks").document(deck.id), deck)
                }
            }
            batch.commit().await()
        }

        pendingLocalCards.chunked(400).forEach { chunk ->
            val batch = db.batch()
            chunk.forEach { card ->
                if (!merge || overwriteCloud || !cloudCardsIds.contains(card.id)) {
                    batch.set(db.collection("users").document(uid).collection("cards").document(card.id), card)
                }
            }
            batch.commit().await()
        }
    }

    fun signOut() {
        auth.signOut()
    }

    // --- CRUD Operations ---

    /**
     * Helper for Firestore writes.
     * If offline (Anonymous), do NOT await, to avoid hanging.
     */
    suspend fun <T> safeWrite(task: Task<T>) {
        if (_isUserAnonymous.value) {
            // Offline/Anon: Fire and forget
        } else {
            // Online: Await to ensure integrity
            try {
                task.await()
            } catch (e: Exception) {
                AppLogger.e(TAG, "Online write failed", e)
            }
        }
    }

    fun saveDeckToFirestore(deck: net.ericclark.studiare.data.Deck) {
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("decks").document(deck.id)
            .set(deck, SetOptions.merge())
    }

    fun saveCardToFirestore(card: net.ericclark.studiare.data.Card) {
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("cards").document(card.id)
            .set(card, SetOptions.merge())
    }

    fun deleteDeckFromFirestore(deckId: String) {
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("decks").document(deckId).delete()
    }

    fun deleteCardFromFirestore(cardId: String) {
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("cards").document(cardId).delete()
    }

    fun saveTagDefinition(tagDef: net.ericclark.studiare.data.TagDefinition) {
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("tags").document(tagDef.id)
            .set(tagDef, SetOptions.merge())
    }

    fun deleteTagDefinition(tagDef: net.ericclark.studiare.data.TagDefinition) {
        val uid = _userId.value ?: return
        db.collection("users").document(uid).collection("tags").document(tagDef.id).delete()
    }
}