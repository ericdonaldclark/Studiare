package net.ericclark.studiare.data

data class DuplicateCheckResult(
    val duplicates: List<DuplicateInfo>,
    val deckId: String?,
    val deckName: String,
    val cardsToSave: List<CardDataForSave>,
    val normalizationType: NormalizationType,
    val sortType: DeckSortMode,
    val parentDeckId: String?,
    val frontLanguage: String,
    val backLanguage: String,
    // New Fields (with defaults)
    val description: String = "",
    val dailyNewCardLimit: Int = 20,
    val dailyReviewLimit: Int = 200
)