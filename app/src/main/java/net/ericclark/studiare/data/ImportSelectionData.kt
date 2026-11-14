package net.ericclark.studiare.data

data class ImportSelectionData(
    val parsedDecks: List<ParsedDeck>,
    val conflictingDeckIds: Set<String>,
    val allParsedCards: Map<String, Card>
)
