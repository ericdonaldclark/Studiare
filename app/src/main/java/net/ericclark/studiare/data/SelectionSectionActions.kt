package net.ericclark.studiare.data

data class SelectionSectionActions(
    val onModeChange: (String) -> Unit,
    val onTagsChange: (List<String>) -> Unit,
    val onDifficultiesChange: (List<Int>) -> Unit,
    val onExcludeKnownChange: (Boolean) -> Unit,
    val onAlphabetStartChange: (String) -> Unit,
    val onAlphabetEndChange: (String) -> Unit,
    val onFilterSideChange: (String) -> Unit,
    val onCardOrderStartChange: (Int) -> Unit,
    val onCardOrderEndChange: (Int) -> Unit,
    val onTimeValueChange: (Int) -> Unit,
    val onTimeUnitChange: (String) -> Unit,
    val onFilterTypeChange: (String) -> Unit,
    val onReviewThresholdChange: (Int) -> Unit,
    val onReviewDirectionChange: (String) -> Unit,
    val onScoreThresholdChange: (Int) -> Unit,
    val onScoreDirectionChange: (String) -> Unit
)
