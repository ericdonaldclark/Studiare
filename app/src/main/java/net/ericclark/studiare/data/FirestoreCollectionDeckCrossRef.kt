package net.ericclark.studiare.data

data class FirestoreCollectionDeckCrossRef(
    val id: String = "", // Best practice: Format as "${collectionId}_${deckId}"
    val collectionId: String = "",
    val deckId: String = "",
    val ownerId: String = ""
)