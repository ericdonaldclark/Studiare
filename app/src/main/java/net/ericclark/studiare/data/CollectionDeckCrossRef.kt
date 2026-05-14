package net.ericclark.studiare.data

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "collection_deck_cross_ref",
    primaryKeys = ["collectionId", "deckId"],
    indices = [Index(value = ["deckId"])]
)
data class CollectionDeckCrossRef(
    val collectionId: String,
    val deckId: String
)