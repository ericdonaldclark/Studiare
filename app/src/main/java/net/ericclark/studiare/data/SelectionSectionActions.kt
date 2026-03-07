package net.ericclark.studiare.data

data class SelectionSectionActions(
    val onModeChange: (SelectionMode) -> Unit,
    val onTagsChange: (List<String>) -> Unit,
    val onDifficultiesChange: (List<Int>) -> Unit,
    val onExcludeKnownChange: (Boolean) -> Unit,
    val onAlphabetStartChange: (String) -> Unit,
    val onAlphabetEndChange: (String) -> Unit,
    val onFilterSideChange: (CardSide) -> Unit,
    val onCardOrderStartChange: (Int) -> Unit,
    val onCardOrderEndChange: (Int) -> Unit,
    val onTimeValueChange: (Int) -> Unit,
    val onTimeUnitChange: (TimeUnit) -> Unit,
    val onFilterTypeChange: (FilterType) -> Unit,
    val onReviewThresholdChange: (Int) -> Unit,
    val onReviewDirectionChange: (Direction) -> Unit,
    val onScoreThresholdChange: (Int) -> Unit,
    val onScoreDirectionChange: (Direction) -> Unit
)
