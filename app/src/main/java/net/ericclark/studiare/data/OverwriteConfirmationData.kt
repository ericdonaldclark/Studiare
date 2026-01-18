package net.ericclark.studiare.data

data class OverwriteConfirmationData(
    val decksToOverwrite: List<Deck>,
    val parsedDecks: List<ParsedDeck>,
    val allParsedCards: Map<String, Card>
)
