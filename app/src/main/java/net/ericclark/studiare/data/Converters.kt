package net.ericclark.studiare.data

import androidx.room.TypeConverter
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class Converters {
    private val gson = Gson()

    // --- List Converters ---
    @TypeConverter
    fun fromStringList(value: List<String>?): String {
        return gson.toJson(value ?: emptyList<String>())
    }

    @TypeConverter
    fun toStringList(value: String?): List<String> {
        if (value == null) return emptyList()
        val type = object : TypeToken<List<String>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromLongList(value: List<Long>?): String {
        return gson.toJson(value ?: emptyList<Long>())
    }

    @TypeConverter
    fun toLongList(value: String?): List<Long> {
        if (value == null) return emptyList()
        val type = object : TypeToken<List<Long>>() {}.type
        return gson.fromJson(value, type)
    }

    @TypeConverter
    fun fromDoubleList(value: List<Double>?): String {
        return gson.toJson(value ?: emptyList<Double>())
    }

    @TypeConverter
    fun toDoubleList(value: String?): List<Double> {
        if (value == null) return emptyList()
        val type = object : TypeToken<List<Double>>() {}.type
        return gson.fromJson(value, type)
    }

    // --- Enum Converters ---
    @TypeConverter
    fun fromCardDataType(value: CardDataType?): String {
        return value?.name ?: CardDataType.TEXT.name
    }

    @TypeConverter
    fun toCardDataType(value: String?): CardDataType {
        return runCatching { CardDataType.valueOf(value ?: "") }.getOrDefault(CardDataType.TEXT)
    }

    @TypeConverter
    fun fromDifficultySetting(value: DifficultySetting?): Int {
        return value?.value ?: 1
    }

    @TypeConverter
    fun toDifficultySetting(value: Int?): DifficultySetting {
        return DifficultySetting.fromInt(value)
    }

    @TypeConverter
    fun fromFsrsState(value: FsrsState?): Int? {
        return value?.value
    }

    @TypeConverter
    fun toFsrsState(value: Int?): FsrsState? {
        return FsrsState.fromInt(value)
    }

    @TypeConverter
    fun fromCardFlag(value: CardFlag?): Int {
        return value?.value ?: 0
    }

    @TypeConverter
    fun toCardFlag(value: Int?): CardFlag {
        return CardFlag.fromInt(value ?: 0)
    }

    @TypeConverter
    fun fromNormalizationType(value: NormalizationType?): Int {
        return value?.value ?: 0
    }

    @TypeConverter
    fun toNormalizationType(value: Int?): NormalizationType {
        return NormalizationType.fromInt(value)
    }

    @TypeConverter
    fun fromDeckSortMode(value: DeckSortMode?): Int {
        return value?.value ?: 0
    }

    @TypeConverter
    fun toDeckSortMode(value: Int?): DeckSortMode {
        return DeckSortMode.fromInt(value)
    }
}