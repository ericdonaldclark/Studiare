package net.ericclark.studiare.data

import net.ericclark.studiare.R


const val DIFFICULTY :String = "Difficulty"
const val IS_KNOWN :String = "isKnown"
const val TAGS :String = "tags"

val isFrontSide : Boolean = true

enum class StudyPreset(val labelResId: Int) {
    STUDY(R.string.preset_study),
    GAME(R.string.preset_game),
    QUIZ(R.string.preset_quiz);
}

enum class SessionMode(val labelResId: Int) {
    FLASHCARD(R.string.mode_flashcard),
    MULTIPLE_CHOICE(R.string.mode_mc),
    TYPING(R.string.mode_typing),
    ANAGRAM(R.string.mode_anagram),
    CROSSWORD(R.string.mode_cw),
    HANGMAN(R.string.mode_hangman),
    MEMORY(R.string.mode_memory),
    MATCHING(R.string.mode_matching),
    AUDIO(R.string.mode_audio);
}

enum class SelectionMode(val labelResId: Int) {
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

enum class SortMode(val labelResId: Int) {
    ALPHABETICAL(R.string.sort_alphabetical),
    REVIEW_DATE(R.string.sort_review_date),
    INCORRECT_DATE(R.string.sort_incorrect_date),
    REVIEW_COUNT(R.string.sort_review_count),
    SCORE(R.string.sort_score),
    CARD_ORDER(R.string.sort_card_order),
    RANDOM(R.string.sort_random);
}

enum class TimeUnit(val labelResId: Int) {
    DAYS(R.string.time_unit_days),
    WEEKS(R.string.time_unit_weeks),
    MONTHS(R.string.time_unit_months),
    YEARS(R.string.time_unit_years);
}

enum class FilterType(val labelResId: Int) {
    INCLUDE(R.string.filter_include),
    EXCLUDE(R.string.filter_exclude);
}

enum class SchedulingMode(val labelResId: Int) {
    NORMAL(R.string.scheduling_mode_normal),
    FSRS(R.string.scheduling_mode_fsrs)
}

enum class Direction(val labelResId: Int) {
    MIN(R.string.direction_min),
    MAX(R.string.direction_max),
}

enum class AutoSetCreationMode(val labelResId: Int) {
    ONE(R.string.one_text),
    MULTIPLE(R.string.multiple_text),
    SPLIT_ALL(R.string.split_all);
}

