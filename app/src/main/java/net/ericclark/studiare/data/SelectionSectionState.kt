package net.ericclark.studiare.data

data class SelectionSectionState(
    val selectionMode: SelectionMode,
    val selectedTags: List<String>,
    val selectedDifficulties: List<Int>,
    val excludeKnown: Boolean,
    val alphabetStart: String,
    val alphabetEnd: String,
    val filterSide: CardSide,
    val cardOrderStart: Int,
    val cardOrderEnd: Int,
    val timeValue: Int,
    val timeUnit: TimeUnit,
    val filterType: FilterType,
    val reviewThreshold: Int,
    val reviewDirection: Direction,
    val scoreThreshold: Int,
    val scoreDirection: Direction,
    // External Data
    val availableTags: List<String>,
    val allTagDefinitions: List<TagDefinition>,
    val availableCardsCount: Int,
    val totalCards: Int,
    val maxDeckReviews: Int
)
