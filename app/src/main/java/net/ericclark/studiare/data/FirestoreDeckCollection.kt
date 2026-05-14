package net.ericclark.studiare.data

data class FirestoreDeckCollection(
    val id: String = "",
    val name: String = "",
    val description: String = "",
    val createdAt: Long = 0L,
    val updatedAt: Long = 0L,
    val isDeleted: Boolean = false,
    val ownerId: String = "" // Required for Firestore security rules
)