package net.ericclark.studiare.data

data class SelectionSectionState(
    val selectionMode: String,
    val selectedTags: List<String>,
    val selectedDifficulties: List<Int>,
    val excludeKnown: Boolean,
    val alphabetStart: String,
    val alphabetEnd: String,
    val filterSide: String,
    val cardOrderStart: Int,
    val cardOrderEnd: Int,
    val timeValue: Int,
    val timeUnit: String,
    val filterType: String,
    val reviewThreshold: Int,
    val reviewDirection: String,
    val scoreThreshold: Int,
    val scoreDirection: String,
    // External Data
    val availableTags: List<String>,
    val allTagDefinitions: List<TagDefinition>,
    val availableCardsCount: Int,
    val totalCards: Int,
    val maxDeckReviews: Int
)
