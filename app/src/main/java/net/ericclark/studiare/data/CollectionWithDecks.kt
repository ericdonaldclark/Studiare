package net.ericclark.studiare.data

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class CollectionWithDecks(
    @Embedded val collection: DeckCollection,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = CollectionDeckCrossRef::class,
            parentColumn = "collectionId",
            entityColumn = "deckId"
        )
    )
    val decks: List<Deck>
)