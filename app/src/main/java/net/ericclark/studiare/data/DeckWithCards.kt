package net.ericclark.studiare.data

/**
 * A data class that represents the relationship between a Deck and its Cards in memory.
 * This is used by the UI. The ViewModel constructs this by joining Decks and Cards.
 */
data class DeckWithCards(
    val deck: Deck,
    val cards: List<Card>
)
