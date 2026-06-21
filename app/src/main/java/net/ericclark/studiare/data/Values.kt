package net.ericclark.studiare.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.R

interface StringResourceEnum {
    val labelResId: Int
}

@Composable
fun StringResourceEnum.asString(): String {
    return stringResource(id = this.labelResId)
}

fun StringResourceEnum.asString(context: Context): String {
    return context.getString(this.labelResId)
}

/**
 * For Jetpack Compose: Converts enum entries to a list of readable strings
 * Usage: val modeStrings = SessionMode.entries.asList()
 */
@Composable
fun <T : StringResourceEnum> Iterable<T>.asList(): List<String> {
    return this.map { it.asString() }
}

/**
 * For Standard Kotlin: Converts enum entries to a list of readable strings
 * Usage: val modeStrings = SessionMode.entries.asList(context)
 */
fun <T : StringResourceEnum> Iterable<T>.asList(context: Context): List<String> {
    return this.map { it.asString(context) }
}

enum class CardSide(override val labelResId: Int) : StringResourceEnum {
    FRONT(R.string.front),
    BACK(R.string.back);
}

fun String.toCardSide(): CardSide {
    return when (this.lowercase().trim()) {
        "front" -> CardSide.FRONT
        "question" -> CardSide.FRONT
        "back" -> CardSide.BACK
        "answer" -> CardSide.BACK
        else -> runCatching { CardSide.valueOf(this) }.getOrDefault(CardSide.FRONT)
    }
}

enum class StudyPreset(override val labelResId: Int) : StringResourceEnum {
    STUDY(R.string.preset_practice),
    GAMES(R.string.preset_game),
    QUIZ(R.string.preset_quiz);
}

fun String.toStudyPreset(): StudyPreset {
    return when (this.lowercase().trim()) {
        "study" -> StudyPreset.STUDY
        "game" -> StudyPreset.GAMES
        "quiz" -> StudyPreset.QUIZ
        else -> runCatching { StudyPreset.valueOf(this) }.getOrDefault(StudyPreset.STUDY)
    }
}

enum class SessionMode(override val labelResId: Int) : StringResourceEnum {
    FLASHCARD(R.string.mode_flashcard),
    LIST(R.string.mode_list),
    MULTIPLE_CHOICE(R.string.mode_mc),
    TYPING(R.string.mode_typing),
    ANAGRAM(R.string.mode_anagram),
    CROSSWORD(R.string.mode_cw),
    HANGMAN(R.string.mode_hangman),
    MEMORY(R.string.mode_memory),
    MATCHING(R.string.mode_matching),
    AUDIO(R.string.mode_audio),
    QUIZ(R.string.mode_quiz),
    FREEFORM(R.string.mode_freeform),
    WORD_SEARCH(R.string.mode_word_search);
}

fun String.toSessionMode(): SessionMode {
    return when (this.lowercase().trim()) {
        "flashcard" -> SessionMode.FLASHCARD
        "list" -> SessionMode.LIST
        "multiple choice" -> SessionMode.MULTIPLE_CHOICE
        "multiple_choice" -> SessionMode.MULTIPLE_CHOICE
        "typing" -> SessionMode.TYPING
        "anagram" -> SessionMode.ANAGRAM
        "crossword" -> SessionMode.CROSSWORD
        "hangman" -> SessionMode.HANGMAN
        "memory" -> SessionMode.MEMORY
        "matching" -> SessionMode.MATCHING
        "audio" -> SessionMode.AUDIO
        "freeform" -> SessionMode.FREEFORM
        "wordsearch" -> SessionMode.WORD_SEARCH
        "word search" -> SessionMode.WORD_SEARCH
        "word_search" -> SessionMode.WORD_SEARCH
        else -> runCatching { SessionMode.valueOf(this) }.getOrDefault(SessionMode.FLASHCARD)
    }
}

enum class SelectionMode(override val labelResId: Int) : StringResourceEnum {
    ANY(R.string.selection_any),
    DIFFICULTY(R.string.selection_difficulty),
    TAGS(R.string.selection_tags),
    ALPHABET(R.string.selection_alphabet),
    CARD_ORDER(R.string.selection_card_order),
    REVIEW_DATE(R.string.selection_review_date),
    INCORRECT_DATE(R.string.selection_incorrect_date),
    REVIEW_COUNT(R.string.selection_review_count),
    SCORE(R.string.selection_score);
}

fun String.toSelectionMode(): SelectionMode {
    return when (this.lowercase().trim()) {
        "any" -> SelectionMode.ANY
        "difficulty" -> SelectionMode.DIFFICULTY
        "tags" -> SelectionMode.TAGS
        "alphabetical" -> SelectionMode.ALPHABET
        "card order" -> SelectionMode.CARD_ORDER
        "card_order" -> SelectionMode.CARD_ORDER
        "review date" -> SelectionMode.REVIEW_DATE
        "review_date" -> SelectionMode.REVIEW_DATE
        "incorrect date" -> SelectionMode.INCORRECT_DATE
        "incorrect_date" -> SelectionMode.INCORRECT_DATE
        "review count" -> SelectionMode.REVIEW_COUNT
        "review_count" -> SelectionMode.REVIEW_COUNT
        "score" -> SelectionMode.SCORE
        else -> runCatching { SelectionMode.valueOf(this) }.getOrDefault(SelectionMode.ANY)
    }
}

enum class SortMode(val value: Int, override val labelResId: Int) : StringResourceEnum {
    ALPHABETICAL(2, R.string.sort_alphabetical),
    REVIEW_DATE(3, R.string.sort_review_date),
    INCORRECT_DATE(4, R.string.sort_incorrect_date),
    REVIEW_COUNT(5, R.string.sort_review_count),
    SCORE(6, R.string.sort_score),
    CARD_ORDER(7, R.string.sort_card_order),
    RANDOM(1, R.string.sort_random),
    NONE(0, R.string.none);

    companion object {
        fun fromInt(value: Int?): SortMode {
            return SortMode.entries.find { it.value == value } ?: NONE
        }
    }
}

fun String.toSortMode(): SortMode {
    return when (this.lowercase().trim()) {
        "alphabetical" -> SortMode.ALPHABETICAL
        "review date" -> SortMode.REVIEW_DATE
        "review_date" -> SortMode.REVIEW_DATE
        "incorrect date" -> SortMode.INCORRECT_DATE
        "incorrect_date" -> SortMode.INCORRECT_DATE
        "review count" -> SortMode.REVIEW_COUNT
        "review_count" -> SortMode.REVIEW_COUNT
        "score" -> SortMode.SCORE
        "card order" -> SortMode.CARD_ORDER
        "card_order" -> SortMode.CARD_ORDER
        "random" -> SortMode.RANDOM
        "none" -> SortMode.NONE
        else -> runCatching { SortMode.valueOf(this) }.getOrDefault(SortMode.RANDOM)
    }
}

enum class DeckSortMode(val value: Int, override val labelResId: Int) : StringResourceEnum {
    A_TO_Z(4, R.string.alphabetical_a_to_z),
    Z_TO_A(5, R.string.alphabetical_z_to_a),
    ONE_TO_FIVE(6, R.string.difficulty_1_to_5),
    FIVE_TO_ONE(7, R.string.difficulty_5_to_1),
    DATE_ADDED_NEW_TO_OLD(1, R.string.date_added_new_to_old),
    DATE_ADDED_OLD_TO_NEW(0, R.string.date_added_old_to_new),
    DATE_MODIFIED_NEW_TO_OLD(2, R.string.date_modified_new_to_old),
    DATE_MODIFIED_OLD_TO_NEW(3, R.string.date_modified_old_to_new);

    companion object {
        fun fromInt(value: Int?): DeckSortMode {
            return DeckSortMode.entries.find { it.value == value } ?: DATE_ADDED_OLD_TO_NEW
        }
    }
}

fun String.toDeckSortMode(): DeckSortMode {
    return when (this.lowercase().trim()) {
        "alphabetical" -> DeckSortMode.A_TO_Z
        "alphabetical (a-z)" -> DeckSortMode.A_TO_Z
        "alphabetical a-z" -> DeckSortMode.A_TO_Z
        "(a-z)" -> DeckSortMode.A_TO_Z
        "a-z" -> DeckSortMode.A_TO_Z
        "alphabetical (z-a)" -> DeckSortMode.Z_TO_A
        "alphabetical z-a" -> DeckSortMode.Z_TO_A
        "(z-a)" -> DeckSortMode.Z_TO_A
        "z-a" -> DeckSortMode.Z_TO_A
        "difficulty (1-5)" -> DeckSortMode.ONE_TO_FIVE
        "difficulty 1-5" -> DeckSortMode.ONE_TO_FIVE
        "1-5" -> DeckSortMode.ONE_TO_FIVE
        "difficulty (5-1)" -> DeckSortMode.FIVE_TO_ONE
        "difficulty 5-1" -> DeckSortMode.FIVE_TO_ONE
        "5-1" -> DeckSortMode.FIVE_TO_ONE
        "date added" -> DeckSortMode.DATE_ADDED_NEW_TO_OLD
        "date_added" -> DeckSortMode.DATE_ADDED_NEW_TO_OLD
        "date added (new to old)" -> DeckSortMode.DATE_ADDED_NEW_TO_OLD
        "date added new to old" -> DeckSortMode.DATE_ADDED_NEW_TO_OLD
        "date added reverse" -> DeckSortMode.DATE_ADDED_OLD_TO_NEW
        "date_added_reverse" -> DeckSortMode.DATE_ADDED_OLD_TO_NEW
        "date added (old to new)" -> DeckSortMode.DATE_ADDED_OLD_TO_NEW
        "date added old to new" -> DeckSortMode.DATE_ADDED_OLD_TO_NEW
        "date modified" -> DeckSortMode.DATE_MODIFIED_NEW_TO_OLD
        "date_modified" -> DeckSortMode.DATE_MODIFIED_NEW_TO_OLD
        "date modified (new to old)" -> DeckSortMode.DATE_MODIFIED_NEW_TO_OLD
        "date modified new to old" -> DeckSortMode.DATE_MODIFIED_NEW_TO_OLD
        "date modified reverse" -> DeckSortMode.DATE_MODIFIED_OLD_TO_NEW
        "date_modified_reverse" -> DeckSortMode.DATE_MODIFIED_OLD_TO_NEW
        "date modified (old to new)" -> DeckSortMode.DATE_MODIFIED_OLD_TO_NEW
        "date modified old to new" -> DeckSortMode.DATE_MODIFIED_OLD_TO_NEW
        else -> runCatching { DeckSortMode.valueOf(this) }.getOrDefault(DeckSortMode.DATE_ADDED_OLD_TO_NEW)
    }
}

enum class TimeUnit(override val labelResId: Int) : StringResourceEnum {
    DAYS(R.string.time_unit_days),
    WEEKS(R.string.time_unit_weeks),
    MONTHS(R.string.time_unit_months),
    YEARS(R.string.time_unit_years);
}

fun String.toTimeUnit(): TimeUnit {
    return when (this.lowercase().trim()) {
        "days" -> TimeUnit.DAYS
        "weeks" -> TimeUnit.WEEKS
        "months" -> TimeUnit.MONTHS
        "years" -> TimeUnit.YEARS
        else -> runCatching { TimeUnit.valueOf(this) }.getOrDefault(TimeUnit.DAYS)
    }
}

enum class FilterType(override val labelResId: Int) : StringResourceEnum {
    INCLUDE(R.string.filter_include),
    EXCLUDE(R.string.filter_exclude);
}

fun String.toFilterType(): FilterType {
    return when (this.lowercase().trim()) {
        "include" -> FilterType.INCLUDE
        "exclude" -> FilterType.EXCLUDE
        else -> runCatching { FilterType.valueOf(this) }.getOrDefault(FilterType.INCLUDE)
    }
}

enum class SchedulingMode(override val labelResId: Int) : StringResourceEnum {
    NORMAL(R.string.scheduling_mode_normal),
    FSRS(R.string.scheduling_mode_fsrs)
}

fun String.toSchedulingMode() : SchedulingMode {
    return when (this.lowercase().trim()) {
        "normal" -> SchedulingMode.NORMAL
        "fsrs" -> SchedulingMode.FSRS
        "spaced repetition" -> SchedulingMode.FSRS
        "spaced_repetition" -> SchedulingMode.FSRS
        else -> runCatching { SchedulingMode.valueOf(this) }.getOrDefault(SchedulingMode.NORMAL)
    }
}

/*
enum class MinMax(val labelResId: Int) {
    MIN(R.string.direction_min),
    MAX(R.string.direction_max);
}


fun String.toMinMax(): MinMax {
    return when (this.lowercase().trim()) {
        "min" -> MinMax.MIN
        "minimum" -> MinMax.MIN
        "max" -> MinMax.MAX
        "maximum" -> MinMax.MAX
        else -> runCatching { MinMax.valueOf(this) }.getOrDefault(MinMax.MIN)
    }
}
*/
enum class Direction(override val labelResId: Int) : StringResourceEnum {
    ASC(R.string.direction_asc),
    DESC(R.string.direction_desc);
}

fun String.toDirection(): Direction {
    return when (this.lowercase().trim()) {
        "asc" -> Direction.ASC
        "ascending" -> Direction.ASC
        "desc" -> Direction.DESC
        "descending" -> Direction.DESC
        "min" -> Direction.ASC
        "minimum" -> Direction.ASC
        "max" -> Direction.DESC
        "maximum" -> Direction.DESC
        else -> runCatching { Direction.valueOf(this) }.getOrDefault(Direction.ASC)
    }
}

enum class AutoSetCreationMode(override val labelResId: Int) : StringResourceEnum {
    ONE(R.string.one),
    MULTIPLE(R.string.multiple),
    SPLIT_ALL(R.string.split_all);
}

// Replacement Code
fun String.toAutoSetCreationMode(): AutoSetCreationMode {
    return when (this.lowercase().trim()) {
        "one" -> AutoSetCreationMode.ONE
        "multiple" -> AutoSetCreationMode.MULTIPLE
        "split_all" -> AutoSetCreationMode.SPLIT_ALL
        "split all" -> AutoSetCreationMode.SPLIT_ALL
        else -> runCatching { AutoSetCreationMode.valueOf(this) }.getOrDefault(AutoSetCreationMode.ONE)
    }
}

enum class NormalizationType(val value: Int, override val labelResId: Int) : StringResourceEnum {
    NONE(0,R.string.none),
    UPPERCASE_FIRST_LETTER(1, R.string.normalization_uppercase_first_letter),
    UPPERCASE_ALL_LETTERS(2, R.string.normalization_uppercase_all_letters),
    UPPERCASE_EACH_WORD(3, R.string.normalization_uppercase_each_word),
    LOWERCASE_FIRST_LETTER(4, R.string.normalization_lowercase_first_letter),
    LOWERCASE_ALL_LETTERS(5, R.string.normalization_lowercase_all_letters),
    LOWERCASE_EACH_WORD(6, R.string.normalization_lowercase_each_word);

    companion object {
        fun fromInt(value: Int?): NormalizationType {
            return NormalizationType.entries.find { it.value == value } ?: NONE
        }
    }
}

fun String.toNormalizationType(): NormalizationType {
    return when (this.lowercase().trim()) {
        "none" -> NormalizationType.NONE
        "uppercase_first_letter" -> NormalizationType.UPPERCASE_FIRST_LETTER
        "uppercase first letter" -> NormalizationType.UPPERCASE_FIRST_LETTER
        "uppercase_all_letters" -> NormalizationType.UPPERCASE_ALL_LETTERS
        "uppercase all letters" -> NormalizationType.UPPERCASE_ALL_LETTERS
        "uppercase_all_words" -> NormalizationType.UPPERCASE_EACH_WORD
        "uppercase all words" -> NormalizationType.UPPERCASE_EACH_WORD
        "lowercase_first_letter" -> NormalizationType.LOWERCASE_FIRST_LETTER
        "lowercase first letter" -> NormalizationType.LOWERCASE_FIRST_LETTER
        "lowercase_all_letters" -> NormalizationType.LOWERCASE_ALL_LETTERS
        "lowercase all letters" -> NormalizationType.LOWERCASE_ALL_LETTERS
        else -> runCatching { NormalizationType.valueOf(this) }.getOrDefault(NormalizationType.NONE)
    }
}


enum class DifficultySetting(val value: Int, override val labelResId: Int) : StringResourceEnum {
    ONE(1, R.string.difficulty_one),
    TWO(2, R.string.difficulty_two),
    THREE(3, R.string.difficulty_three),
    FOUR(4, R.string.difficulty_four),
    FIVE(5, R.string.difficulty_five);

    companion object {
        fun fromInt(value: Int?): DifficultySetting {
            return entries.find { it.value == value } ?: ONE
        }
    }
}

enum class FsrsState(val value: Int, override val labelResId: Int) : StringResourceEnum {
    NEW(0, R.string.state_new),
    LEARNING(1, R.string.state_learning),
    REVIEW(2, R.string.state_review),
    RELEARNING(3, R.string.state_relearning);

    companion object {
        fun fromInt(value: Int?): FsrsState? {
            return entries.find { it.value == value }
        }
    }
}

enum class CardFlag(val value: Int, override val labelResId: Int) : StringResourceEnum {
    NONE(0, R.string.none),
    RED(1, R.string.flag_red),
    ORANGE(2, R.string.flag_orange),
    GREEN(3, R.string.flag_green),
    BLUE(4, R.string.flag_blue);

    companion object {
        fun fromInt(value: Int): CardFlag {
            return entries.find { it.value == value } ?: NONE
        }
    }
}

enum class Rating(val value: Int, override val labelResId: Int) : StringResourceEnum {
    AGAIN(1, R.string.rating_again),
    HARD(2, R.string.rating_hard),
    GOOD(3, R.string.rating_good),
    EASY(4, R.string.rating_easy);

    companion object {
        fun fromInt(value: Int): Rating {
            return entries.find { it.value == value } ?: AGAIN
        }
    }
}

enum class CardDataType(override val labelResId: Int) : StringResourceEnum {
    TEXT(R.string.type_text),
    IMAGE(R.string.type_image),
    VIDEO(R.string.type_video),
    WEB(R.string.type_web),
    AUDIO(R.string.type_audio);
}

fun String.toCardDataType(): CardDataType {
    return when (this.lowercase().trim()) {
        "text" -> CardDataType.TEXT
        "image" -> CardDataType.IMAGE
        "video" -> CardDataType.VIDEO
        "web" -> CardDataType.WEB
        else -> runCatching { CardDataType.valueOf(this) }.getOrDefault(CardDataType.TEXT)
    }
}

enum class ControlType  {
    FAB,
    BUTTON;
}

fun String.ControlType(): ControlType {
    return when (this.lowercase().trim()) {
        "fab" -> ControlType.FAB
        "button" -> ControlType.BUTTON
        else -> runCatching { ControlType.valueOf(this) }.getOrDefault(ControlType.BUTTON)
    }
}

/*
const val TAGS = "Tags"
const val ANY = "Any"
const val DIFFICULTY = "Difficulty"
const val ALPHABETICAL = "Alphabetical"
const val ALPHABET = "Alphabet"
const val CARD_ORDER = "Card Order"
const val REVIEW_DATE = "Review Date"
const val INCORRECT_DATE = "Incorrect Date"
const val REVIEW_COUNT = "Review Count"
const val SCORE = "Score"
const val RANDOM = "Random"
*/