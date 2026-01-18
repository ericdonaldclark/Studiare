package net.ericclark.studiare.data

data class DuplicateCheckResult(
    val duplicates: List<DuplicateInfo>,
    val deckId: String?,
    val deckName: String,
    val cardsToSave: List<CardDataForSave>,
    val normalizationType: Int,
    val sortType: Int,
    val parentDeckId: String? = null,
    val frontLanguage: String,
    val backLanguage: String
)
