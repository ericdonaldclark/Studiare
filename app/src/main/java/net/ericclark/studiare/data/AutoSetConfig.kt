package net.ericclark.studiare.data

data class AutoSetConfig(
    val mode: String,          // "One", "Multiple", "Split All"
    val numSets: Int,          // Used for "Multiple"
    val maxCardsPerSet: Int,
    val selectionMode: String, // "Any", "Tags", "Difficulty"
    val selectedTags: List<String>,
    val selectedDifficulties: List<Int>,
    val excludeKnown: Boolean,

    // --- NEW FILTERS ---
    val includeSuspended: Boolean = false, // Default to FALSE to exclude suspended cards
    val selectedFlags: List<Int> = emptyList(), // Empty means "Any" (ignore flag filter)

    val sortMode: String,      // "Random", "Alphabetical", "Review Date", etc.
    val sortDirection: String, // "ASC", "DESC"
    val sortSide: String,       // "Front", "Back"
    val alphabetStart: String = "A",
    val alphabetEnd: String = "Z",
    val filterSide: String = "Front",
    val cardOrderStart: Int = 1,
    val cardOrderEnd: Int = 100,
    val timeValue: Int = 7,
    val timeUnit: String = "Days",
    val filterType: String = "Include", // "Include" or "Exclude"
    val reviewCountThreshold: Int = 0,
    val reviewCountDirection: String = "Minimum", // "Minimum" (>=) or "Maximum" (<=)
    val scoreThreshold: Int = 0, // 0-100
    val scoreDirection: String = "Minimum",
    val schedulingMode: String = "Normal" // "Normal" or "Spaced Repetition"
)