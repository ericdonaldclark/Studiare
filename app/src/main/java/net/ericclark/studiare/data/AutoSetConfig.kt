package net.ericclark.studiare.data

data class AutoSetConfig(
    val mode: AutoSetCreationMode,          // "One", "Multiple", "Split All"
    val numSets: Int,          // Used for "Multiple"
    val maxCardsPerSet: Int,
    val selectionMode: SelectionMode, // "Any", "Tags", "Difficulty"
    val selectedTags: List<String>,
    val selectedDifficulties: List<Int>,
    val excludeKnown: Boolean,

    // --- NEW FILTERS ---
    val includeSuspended: Boolean = false, // Default to FALSE to exclude suspended cards
    val selectedFlags: List<Int> = emptyList(), // Empty means "Any" (ignore flag filter)

    val sortMode: SortMode,      // "Random", "Alphabetical", "Review Date", etc.
    val sortDirection: Direction, // "ASC", "DESC"
    val sortSide: CardSide,       // "Front", "Back"
    val alphabetStart: String = "A",
    val alphabetEnd: String = "Z",
    val filterSide: CardSide = CardSide.FRONT,
    val cardOrderStart: Int = 1,
    val cardOrderEnd: Int = 100,
    val timeValue: Int = 7,
    val timeUnit: TimeUnit = TimeUnit.DAYS,
    val filterType: FilterType = FilterType.INCLUDE, // "Include" or "Exclude"
    val reviewCountThreshold: Int = 0,
    val reviewCountDirection: Direction = Direction.ASC, // "Minimum" (>=) or "Maximum" (<=)
    val scoreThreshold: Int = 0, // 0-100
    val scoreDirection: Direction = Direction.DESC, // = "Minimum",
    val schedulingMode: SchedulingMode = SchedulingMode.NORMAL // "Normal" or "Spaced Repetition"
)