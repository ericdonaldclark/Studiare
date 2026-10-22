package net.ericclark.studiare.components

import net.ericclark.studiare.data.Card
import net.ericclark.studiare.data.Deck
import net.ericclark.studiare.data.DeckWithCards

/**
 * Resolves each set/child-deck's effective metadata/fields/scoring/card-order against its parent
 * according to its own `linkageSettings`, producing the `DeckWithCards` list that both the live UI
 * (`FlashcardViewModel.combineDecksAndCards`) and background exports (`ScheduledBackupManager`) use
 * as the single source of truth for what a deck "actually" looks like.
 */
object DeckLinkageResolver {
    fun resolveEffectiveDecks(decks: List<Deck>, cards: List<Card>): List<DeckWithCards> {
        val decksMap = decks.associateBy { it.id }
        val cardsMap = cards.associateBy { it.id }

        return decks.map { deck ->
            var effectiveDeck = deck
            val effectiveCards = deck.cardIds.mapNotNull { cardsMap[it] }

            if (deck.parentDeckId != null) {
                val parent = decksMap[deck.parentDeckId]
                if (parent != null) {
                    val linkage = deck.linkageSettings

                    if (linkage.linkMetadata) {
                        effectiveDeck = effectiveDeck.copy(
                            createdAt = parent.createdAt,
                            updatedAt = parent.updatedAt
                        )
                    }
                    if (linkage.linkFieldConfig) {
                        effectiveDeck = effectiveDeck.copy(
                            frontLanguage = parent.frontLanguage,
                            backLanguage = parent.backLanguage,
                            frontNoteTemplates = parent.frontNoteTemplates,
                            backNoteTemplates = parent.backNoteTemplates
                        )
                    }
                    if (linkage.linkCardOrder) {
                        effectiveDeck = effectiveDeck.copy(deckSortMode = parent.deckSortMode)
                    }
                    if (linkage.linkScoring) {
                        effectiveDeck = effectiveDeck.copy(
                            fsrsEnabled = parent.fsrsEnabled,
                            fsrsWeights = parent.fsrsWeights,
                            dailyNewCardLimit = parent.dailyNewCardLimit,
                            dailyReviewLimit = parent.dailyReviewLimit
                        )
                    }
                }
            }

            DeckWithCards(effectiveDeck, effectiveCards)
        }.sortedBy { it.deck.name }
    }
}
