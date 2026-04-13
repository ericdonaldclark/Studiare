package net.ericclark.studiare.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // --- List Converters ---
    @TypeConverter fun fromStringList(value: List<String>?): String = gson.toJson(value ?: emptyList<String>())
    @TypeConverter fun toStringList(value: String?): List<String> = gson.fromJson(value, object : TypeToken<List<String>>() {}.type) ?: emptyList()

    @TypeConverter fun fromLongList(value: List<Long>?): String = gson.toJson(value ?: emptyList<Long>())
    @TypeConverter fun toLongList(value: String?): List<Long> = gson.fromJson(value, object : TypeToken<List<Long>>() {}.type) ?: emptyList()

    @TypeConverter fun fromDoubleList(value: List<Double>?): String = gson.toJson(value ?: emptyList<Double>())
    @TypeConverter fun toDoubleList(value: String?): List<Double> = gson.fromJson(value, object : TypeToken<List<Double>>() {}.type) ?: emptyList()

    @TypeConverter fun fromIntList(value: List<Int>?): String = gson.toJson(value ?: emptyList<Int>())
    @TypeConverter fun toIntList(value: String?): List<Int> = gson.fromJson(value, object : TypeToken<List<Int>>() {}.type) ?: emptyList()

    // --- Original Enum Converters ---
    @TypeConverter fun fromCardDataType(value: CardDataType?): String = value?.name ?: CardDataType.TEXT.name
    @TypeConverter fun toCardDataType(value: String?): CardDataType = runCatching { CardDataType.valueOf(value ?: "") }.getOrDefault(CardDataType.TEXT)

    @TypeConverter fun fromDifficultySetting(value: DifficultySetting?): Int = value?.value ?: 1
    @TypeConverter fun toDifficultySetting(value: Int?): DifficultySetting = DifficultySetting.fromInt(value)

    @TypeConverter fun fromFsrsState(value: FsrsState?): Int? = value?.value
    @TypeConverter fun toFsrsState(value: Int?): FsrsState? = FsrsState.fromInt(value)

    @TypeConverter fun fromCardFlag(value: CardFlag?): Int = value?.value ?: 0
    @TypeConverter fun toCardFlag(value: Int?): CardFlag = CardFlag.fromInt(value ?: 0)

    @TypeConverter fun fromNormalizationType(value: NormalizationType?): Int = value?.value ?: 0
    @TypeConverter fun toNormalizationType(value: Int?): NormalizationType = NormalizationType.fromInt(value)

    @TypeConverter fun fromDeckSortMode(value: DeckSortMode?): Int = value?.value ?: 0
    @TypeConverter fun toDeckSortMode(value: Int?): DeckSortMode = DeckSortMode.fromInt(value)

    // --- NEW: SESSION & TAG CONVERTERS ---
    @TypeConverter fun fromSessionMode(value: SessionMode?): String = value?.name ?: SessionMode.FLASHCARD.name
    @TypeConverter fun toSessionMode(value: String?): SessionMode = runCatching { SessionMode.valueOf(value ?: "") }.getOrDefault(SessionMode.FLASHCARD)

    @TypeConverter fun fromSchedulingMode(value: SchedulingMode?): String = value?.name ?: SchedulingMode.NORMAL.name
    @TypeConverter fun toSchedulingMode(value: String?): SchedulingMode = runCatching { SchedulingMode.valueOf(value ?: "") }.getOrDefault(SchedulingMode.NORMAL)

    @TypeConverter fun fromCardSide(value: CardSide?): String? = value?.name
    @TypeConverter fun toCardSide(value: String?): CardSide? = value?.let { runCatching { CardSide.valueOf(it) }.getOrNull() }

    @TypeConverter fun fromSortMode(value: SortMode?): String = value?.name ?: SortMode.RANDOM.name
    @TypeConverter fun toSortMode(value: String?): SortMode = runCatching { SortMode.valueOf(value ?: "") }.getOrDefault(SortMode.RANDOM)

    @TypeConverter fun fromStringListMap(value: Map<String, List<String>>?): String = gson.toJson(value ?: emptyMap<String, List<String>>())
    @TypeConverter fun toStringListMap(value: String?): Map<String, List<String>> = gson.fromJson(value, object : TypeToken<Map<String, List<String>>>() {}.type) ?: emptyMap()

    @TypeConverter fun fromStringStringMap(value: Map<String, String>?): String = gson.toJson(value ?: emptyMap<String, String>())
    @TypeConverter fun toStringStringMap(value: String?): Map<String, String> = gson.fromJson(value, object : TypeToken<Map<String, String>>() {}.type) ?: emptyMap()

    @TypeConverter fun fromCrosswordWordList(value: List<CrosswordWord>?): String = gson.toJson(value ?: emptyList<CrosswordWord>())
    @TypeConverter fun toCrosswordWordList(value: String?): List<CrosswordWord> = gson.fromJson(value, object : TypeToken<List<CrosswordWord>>() {}.type) ?: emptyList()

    @TypeConverter fun fromSelectionMode(value: SelectionMode?): String = value?.name ?: SelectionMode.ANY.name
    @TypeConverter fun toSelectionMode(value: String?): SelectionMode = runCatching { SelectionMode.valueOf(value ?: "") }.getOrDefault(SelectionMode.ANY)

    @TypeConverter fun fromDirection(value: Direction?): String = value?.name ?: Direction.ASC.name
    @TypeConverter fun toDirection(value: String?): Direction = runCatching { Direction.valueOf(value ?: "") }.getOrDefault(Direction.ASC)

    @TypeConverter fun fromTimeUnit(value: TimeUnit?): String = value?.name ?: TimeUnit.DAYS.name
    @TypeConverter fun toTimeUnit(value: String?): TimeUnit = runCatching { TimeUnit.valueOf(value ?: "") }.getOrDefault(TimeUnit.DAYS)

    @TypeConverter fun fromFilterType(value: FilterType?): String = value?.name ?: FilterType.EXCLUDE.name
    @TypeConverter fun toFilterType(value: String?): FilterType = runCatching { FilterType.valueOf(value ?: "") }.getOrDefault(FilterType.EXCLUDE)
}