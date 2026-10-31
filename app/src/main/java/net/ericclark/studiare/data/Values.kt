package net.ericclark.studiare.data

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.R
import org.json.JSONArray
import org.json.JSONObject
import net.ericclark.studiare.screens.Screens.CreateStudySessionScreen
import net.ericclark.studiare.screens.Screens.ModeSelectionSection

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
    BACK(R.string.back),
    AUTO_BY_DECK(R.string.prompt_side_auto_by_deck),
    AUTO_BY_CARD(R.string.prompt_side_auto_by_card);
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

/** Aggregate "auto" resolution for [CardSide.AUTO_BY_DECK]: the side with more total characters across every card makes the better prompt/hint, the shorter side the better hidden answer. Ties favor [CardSide.FRONT]. */
fun computeAutoByDeckSide(cards: List<Card>): CardSide {
    val frontTotal = cards.sumOf { it.front.trim().length }
    val backTotal = cards.sumOf { it.back.trim().length }
    return if (frontTotal >= backTotal) CardSide.FRONT else CardSide.BACK
}

/** Resolves a (possibly "auto") [CardSide] setting down to a concrete [CardSide.FRONT]/[CardSide.BACK] for one [card]. [resolvedAutoByDeckSide] is the session's once-per-session cached result of [computeAutoByDeckSide]. */
fun resolvePromptSide(setting: CardSide, card: Card, resolvedAutoByDeckSide: CardSide): CardSide = when (setting) {
    CardSide.FRONT, CardSide.BACK -> setting
    CardSide.AUTO_BY_CARD -> if (card.front.trim().length >= card.back.trim().length) CardSide.FRONT else CardSide.BACK
    CardSide.AUTO_BY_DECK -> resolvedAutoByDeckSide
}

/**
 * List mode's shared distractor-answer pool needs one consistent side across the whole deck, so
 * [CardSide.AUTO_BY_CARD] collapses to [CardSide.AUTO_BY_DECK] here rather than resolving per-card —
 * there's no single "non-prompt" side to pick from when cards disagree. Used both where that pool is
 * built and wherever a specific card's "correct" answer needs to match it.
 */
fun resolvePoolPromptSide(setting: CardSide, resolvedAutoByDeckSide: CardSide): CardSide = when (setting) {
    CardSide.AUTO_BY_CARD, CardSide.AUTO_BY_DECK -> resolvedAutoByDeckSide
    else -> setting
}

enum class StudyCategory(override val labelResId: Int) : StringResourceEnum {
    LEARN(R.string.category_learn),
    PRACTICE(R.string.category_practice),
    GAMES(R.string.category_game),
    QUIZ(R.string.category_quiz),
    // Guided (FSRS): a normal session-dialog category whose cards come from the FSRS due rule.
    GUIDED(R.string.category_smart);
}

fun String.toStudyCategory(): StudyCategory {
    return when (this.lowercase().trim()) {
        "learn" -> StudyCategory.LEARN
        "study" -> StudyCategory.PRACTICE
        "game" -> StudyCategory.GAMES
        "quiz" -> StudyCategory.QUIZ
        "smart" -> StudyCategory.GUIDED
        else -> runCatching { StudyCategory.valueOf(this) }.getOrDefault(StudyCategory.PRACTICE)
    }
}

/** Orders the category- or mode-level groups in the Study Hub's active-session list. */
enum class GroupSortMode(val value: Int, override val labelResId: Int) : StringResourceEnum {
    ALPHABETICAL(1, R.string.sort_alphabetical),
    MOST_RECENT(2, R.string.group_sort_most_recent),
    SESSION_COUNT(3, R.string.group_sort_session_count);

    companion object {
        fun fromInt(value: Int?): GroupSortMode = entries.find { it.value == value } ?: MOST_RECENT
    }
}

/** Orders session tiles against each other in the Study Hub's active-session list. */
enum class SessionTileSortMode(val value: Int, override val labelResId: Int) : StringResourceEnum {
    LAST_ACCESSED(0, R.string.tile_sort_last_accessed),
    DATE_CREATED(1, R.string.tile_sort_date_created),
    PROGRESS(2, R.string.tile_sort_progress);

    companion object {
        fun fromInt(value: Int?): SessionTileSortMode = entries.find { it.value == value } ?: LAST_ACCESSED
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
    // Was QUIZ — Typing's graded/hint-toggleable screen, named "Quiz" back when Typing was the
    // only other mode besides Flashcard. Renamed to avoid colliding with the Quiz *tab* concept,
    // which now applies to every gradeable mode, not just this one. Shares mode_typing's label —
    // to the user this is still just "Typing," distinguished by which tab it's under, not by name.
    TYPING_SCORED(R.string.mode_typing),
    FREEFORM(R.string.mode_freeform),
    WORD_SEARCH(R.string.mode_word_search),
    // The audio-mode split (see the roadmap plan) originally shipped as 4 separate modes
    // (SPEECH_TO_TEXT/TEXT_TO_SPEECH practice, LISTEN_SPEAK/LISTEN_TYPE quiz) before being
    // collapsed into these 2 — practice vs quiz for each is entirely a function of `isGraded`
    // (already stored per-session), so a separate enum value per practice/quiz pairing was pure
    // duplication. Renamed "Listening"/"Speaking" (Learn/Practice/Quiz/Games/Smart reorg) — under
    // the old graded-vs-non-graded toggle these used to display as "Speech-to-Text"/"Listen & Type"
    // and "Text-to-Speech"/"Listen & Speak" depending on isGraded; now the mode name itself doesn't
    // vary by grading (practice/quiz context shows via the tab/badge instead), just like every
    // other mode. See `studymodes/TypedListenMode.kt`/`SpokenListenMode.kt` for the still-real
    // isGraded behavior split (front-only vs. also-revealing-the-answer-side).
    LISTEN_TYPE(R.string.mode_listening),
    SEE_SPEAK(R.string.mode_speaking),
    // Audio-answer multiple choice: a text prompt, N unlabeled audio clips as candidate answers —
    // pick the one that matches by ear. See `studymodes/HearMatchMode.kt`.
    HEAR_MATCH(R.string.mode_hear_match);
}

private val gameSessionModes = listOf(
    SessionMode.ANAGRAM, SessionMode.CROSSWORD, SessionMode.HANGMAN, SessionMode.MEMORY, SessionMode.WORD_SEARCH
)
private val learnSessionModes = listOf(SessionMode.TYPING, SessionMode.FREEFORM, SessionMode.AUDIO)

/**
 * Which tab a saved session belongs to, for display purposes (Recents/session-drawer badges) —
 * now that several modes no longer vary their own name by practice/quiz (Learn/Practice/Quiz/
 * Games/Smart reorg), this is how those surfaces communicate which one a session is.
 */
fun ActiveSession.displayCategory(): StudyCategory = when {
    schedulingMode == SchedulingMode.FSRS -> StudyCategory.GUIDED
    mode in gameSessionModes -> StudyCategory.GAMES
    mode in learnSessionModes -> StudyCategory.LEARN
    isGraded -> StudyCategory.QUIZ
    else -> StudyCategory.PRACTICE
}

/**
 * A saved session's own field values, reshaped into the same [ModeDefaultSettings] shape the
 * Settings → Mode Defaults registry (`modeOptionsFor`/`ModeOption.Control`) already understands —
 * so a session tile can show exactly which mode-specific options *this session* actually ran with,
 * via the same generic, registry-driven chip rendering Settings uses, instead of a second
 * hand-maintained set of per-mode chip branches. Every field here maps 1:1 from an `ActiveSession`
 * field of the same name, except [ModeDefaultSettings.difficultyWeighted] (from [isWeighted] — a
 * resolved boolean on the session, there's no separate stored "weighted" field to copy verbatim)
 * and [ModeDefaultSettings.difficultyCounts] (left `null` — that field only matters for the
 * Settings dialog's own count-breakdown summary text, never shown on a tile's chip).
 */
fun ActiveSession.toModeDefaultSettings(): ModeDefaultSettings = ModeDefaultSettings(
    numberOfAnswers = numberOfAnswers,
    showCorrectLetters = showCorrectLetters,
    maxMemoryTiles = maxMemoryTiles,
    gridDensity = gridDensity,
    showCorrectWords = showCorrectWords,
    freeformLayoutVertical = freeformLayoutVertical,
    quizPromptSide = quizPromptSide,
    difficultyWeighted = isWeighted,
    difficultyCounts = null,
    audioPlaybackSpeed = audioPlaybackSpeed,
    audioReplayCount = audioReplayCount,
    audioAutoAdvance = audioAutoAdvance,
    audioAnswerDelaySeconds = audioAnswerDelaySeconds,
    audioNextCardDelaySeconds = audioNextCardDelaySeconds,
    freeformShowBothSides = freeformShowBothSides,
    freeformSwipeNavigation = freeformSwipeNavigation,
    typingIgnoreFormatting = typingIgnoreFormatting,
    typingDisableAutocorrect = typingDisableAutocorrect,
    typingShowLengthHint = typingShowLengthHint,
    typedListenUseMultipleChoice = typedListenUseMultipleChoice,
    hearMatchAutoPlayAllOptions = hearMatchAutoPlayAllOptions,
    hideDifficultySelection = hideDifficultySelection,
    flashcardAutoFlipSeconds = flashcardAutoFlipSeconds,
    flashcardAutoFlip = flashcardAutoFlip,
    flashcardDoubleTapToFlip = flashcardDoubleTapToFlip,
    flashcardRandomizeFirstSide = flashcardRandomizeFirstSide,
    requireConfirmTap = requireConfirmTap,
    allowMultipleGuesses = allowMultipleGuesses,
    autoAdvance = autoAdvance,
    autoAdvanceDelaySeconds = autoAdvanceDelaySeconds,
    autoListen = autoListen,
    listenStartSound = listenStartSound,
    listenCorrectSound = listenCorrectSound,
    listenIncorrectSound = listenIncorrectSound,
    speakingFrontSpeed = speakingFrontSpeed,
    speakingBackSpeed = speakingBackSpeed,
    listResetPosition = listResetPosition,
    listDimWrongGuesses = listDimWrongGuesses,
    listRemoveGuessed = listRemoveGuessed,
    matchingHighlightStyle = matchingHighlightStyle,
    matchingWrongDelayMs = matchingWrongDelayMs,
    matchingCorrectHighlightMs = matchingCorrectHighlightMs,
    matchingShowCorrectDialog = matchingShowCorrectDialog,
    anagramFirstLetterHint = anagramFirstLetterHint,
    anagramUppercase = anagramUppercase,
    anagramColorVowels = anagramColorVowels,
    crosswordHighlightWord = crosswordHighlightWord,
    crosswordAutoAdvanceCell = crosswordAutoAdvanceCell,
    crosswordCompactClues = crosswordCompactClues,
    hangmanMaxMistakes = hangmanMaxMistakes,
    hangmanHideVisual = hangmanHideVisual,
    memoryGrayMatched = memoryGrayMatched,
    memoryPeekSeconds = memoryPeekSeconds,
    memoryGridColumnsPortrait = memoryGridColumnsPortrait,
    memoryGridColumnsLandscape = memoryGridColumnsLandscape,
    memoryWrongPairMs = memoryWrongPairMs,
    memoryCorrectPairMs = memoryCorrectPairMs,
    memorySubmitAnswer = memorySubmitAnswer,
    wordSearchHideFound = wordSearchHideFound,
    wordSearchHighlightColor = wordSearchHighlightColor
)

/**
 * The reverse of [toModeDefaultSettings]: overlays [values]' non-null fields onto this session,
 * leaving every field [values] left `null` untouched. Used by the UI Playground, whose
 * "Mode-Specific Options" section edits a live [ModeDefaultSettings] via the same `Control()`
 * composables Settings → Mode Defaults uses — those edits need to land on the sample session's own
 * fields to show up in [net.ericclark.studiare.screens.UI_Components.SessionTile]'s chips, since
 * that component now derives its chips from the session itself ([toModeDefaultSettings]), not from
 * a separately-tracked settings object.
 */
fun ActiveSession.applyModeDefaults(values: ModeDefaultSettings): ActiveSession = copy(
    numberOfAnswers = values.numberOfAnswers ?: numberOfAnswers,
    showCorrectLetters = values.showCorrectLetters ?: showCorrectLetters,
    maxMemoryTiles = values.maxMemoryTiles ?: maxMemoryTiles,
    gridDensity = values.gridDensity ?: gridDensity,
    showCorrectWords = values.showCorrectWords ?: showCorrectWords,
    freeformLayoutVertical = values.freeformLayoutVertical ?: freeformLayoutVertical,
    quizPromptSide = values.quizPromptSide ?: quizPromptSide,
    isWeighted = values.difficultyWeighted ?: isWeighted,
    audioPlaybackSpeed = values.audioPlaybackSpeed ?: audioPlaybackSpeed,
    audioReplayCount = values.audioReplayCount ?: audioReplayCount,
    audioAutoAdvance = values.audioAutoAdvance ?: audioAutoAdvance,
    audioAnswerDelaySeconds = values.audioAnswerDelaySeconds ?: audioAnswerDelaySeconds,
    audioNextCardDelaySeconds = values.audioNextCardDelaySeconds ?: audioNextCardDelaySeconds,
    freeformShowBothSides = values.freeformShowBothSides ?: freeformShowBothSides,
    freeformSwipeNavigation = values.freeformSwipeNavigation ?: freeformSwipeNavigation,
    typingIgnoreFormatting = values.typingIgnoreFormatting ?: typingIgnoreFormatting,
    typingDisableAutocorrect = values.typingDisableAutocorrect ?: typingDisableAutocorrect,
    typingShowLengthHint = values.typingShowLengthHint ?: typingShowLengthHint,
    typedListenUseMultipleChoice = values.typedListenUseMultipleChoice ?: typedListenUseMultipleChoice,
    hearMatchAutoPlayAllOptions = values.hearMatchAutoPlayAllOptions ?: hearMatchAutoPlayAllOptions,
    hideDifficultySelection = values.hideDifficultySelection ?: hideDifficultySelection,
    flashcardAutoFlipSeconds = values.flashcardAutoFlipSeconds ?: flashcardAutoFlipSeconds,
    flashcardAutoFlip = values.flashcardAutoFlip ?: flashcardAutoFlip,
    flashcardDoubleTapToFlip = values.flashcardDoubleTapToFlip ?: flashcardDoubleTapToFlip,
    flashcardRandomizeFirstSide = values.flashcardRandomizeFirstSide ?: flashcardRandomizeFirstSide,
    requireConfirmTap = values.requireConfirmTap ?: requireConfirmTap,
    allowMultipleGuesses = values.allowMultipleGuesses ?: allowMultipleGuesses,
    autoAdvance = values.autoAdvance ?: autoAdvance,
    autoAdvanceDelaySeconds = values.autoAdvanceDelaySeconds ?: autoAdvanceDelaySeconds,
    autoListen = values.autoListen ?: autoListen,
    listenStartSound = values.listenStartSound ?: listenStartSound,
    listenCorrectSound = values.listenCorrectSound ?: listenCorrectSound,
    listenIncorrectSound = values.listenIncorrectSound ?: listenIncorrectSound,
    speakingFrontSpeed = values.speakingFrontSpeed ?: speakingFrontSpeed,
    speakingBackSpeed = values.speakingBackSpeed ?: speakingBackSpeed,
    listResetPosition = values.listResetPosition ?: listResetPosition,
    listDimWrongGuesses = values.listDimWrongGuesses ?: listDimWrongGuesses,
    listRemoveGuessed = values.listRemoveGuessed ?: listRemoveGuessed,
    matchingHighlightStyle = values.matchingHighlightStyle ?: matchingHighlightStyle,
    matchingWrongDelayMs = values.matchingWrongDelayMs ?: matchingWrongDelayMs,
    matchingCorrectHighlightMs = values.matchingCorrectHighlightMs ?: matchingCorrectHighlightMs,
    matchingShowCorrectDialog = values.matchingShowCorrectDialog ?: matchingShowCorrectDialog,
    anagramFirstLetterHint = values.anagramFirstLetterHint ?: anagramFirstLetterHint,
    anagramUppercase = values.anagramUppercase ?: anagramUppercase,
    anagramColorVowels = values.anagramColorVowels ?: anagramColorVowels,
    crosswordHighlightWord = values.crosswordHighlightWord ?: crosswordHighlightWord,
    crosswordAutoAdvanceCell = values.crosswordAutoAdvanceCell ?: crosswordAutoAdvanceCell,
    crosswordCompactClues = values.crosswordCompactClues ?: crosswordCompactClues,
    hangmanMaxMistakes = values.hangmanMaxMistakes ?: hangmanMaxMistakes,
    hangmanHideVisual = values.hangmanHideVisual ?: hangmanHideVisual,
    memoryGrayMatched = values.memoryGrayMatched ?: memoryGrayMatched,
    memoryPeekSeconds = values.memoryPeekSeconds ?: memoryPeekSeconds,
    memoryGridColumnsPortrait = values.memoryGridColumnsPortrait ?: memoryGridColumnsPortrait,
    memoryGridColumnsLandscape = values.memoryGridColumnsLandscape ?: memoryGridColumnsLandscape,
    memoryWrongPairMs = values.memoryWrongPairMs ?: memoryWrongPairMs,
    memoryCorrectPairMs = values.memoryCorrectPairMs ?: memoryCorrectPairMs,
    memorySubmitAnswer = values.memorySubmitAnswer ?: memorySubmitAnswer,
    wordSearchHideFound = values.wordSearchHideFound ?: wordSearchHideFound,
    wordSearchHighlightColor = values.wordSearchHighlightColor ?: wordSearchHighlightColor
)

/**
 * Which `SessionMode`s a category's chip list offers in `CreateStudySessionScreen`'s
 * `ModeSelectionSection` — shared with the Settings "Mode Defaults" section so both stay in sync.
 * `SMART` is excluded: the FSRS flow is a separate dialog with its own mode list, not this one.
 */
// Alphabetized by each mode's display label (Flashcard, Listening, Matching, Multiple Choice,
// Picking, Speaking, Typing, etc.) — not by enum name, since e.g. LIST displays as "Picking".
fun modesForCategory(category: StudyCategory): List<SessionMode> = when (category) {
    StudyCategory.GAMES -> listOf(SessionMode.ANAGRAM, SessionMode.CROSSWORD, SessionMode.HANGMAN, SessionMode.MEMORY, SessionMode.WORD_SEARCH)
    StudyCategory.LEARN -> listOf(SessionMode.AUDIO, SessionMode.FREEFORM, SessionMode.TYPING, SessionMode.FLASHCARD)
    StudyCategory.PRACTICE -> listOf(SessionMode.FLASHCARD, SessionMode.HEAR_MATCH, SessionMode.LISTEN_TYPE, SessionMode.MATCHING, SessionMode.MULTIPLE_CHOICE, SessionMode.LIST, SessionMode.SEE_SPEAK, SessionMode.TYPING_SCORED)
    StudyCategory.QUIZ -> listOf(SessionMode.FLASHCARD, SessionMode.HEAR_MATCH, SessionMode.LISTEN_TYPE, SessionMode.MATCHING, SessionMode.MULTIPLE_CHOICE, SessionMode.LIST, SessionMode.SEE_SPEAK, SessionMode.TYPING_SCORED)
    StudyCategory.GUIDED -> listOf(SessionMode.FLASHCARD, SessionMode.HEAR_MATCH, SessionMode.LISTEN_TYPE, SessionMode.MULTIPLE_CHOICE, SessionMode.LIST, SessionMode.SEE_SPEAK, SessionMode.TYPING_SCORED)
}

/**
 * The category a running session belongs to — not stored directly on [StudyState], but always
 * recoverable: games/learn-exclusive modes map to exactly one category each (see [modesForCategory]),
 * and the rest (Practice vs Quiz vs Guided) are told apart by
 * [StudyState.schedulingMode]/[StudyState.isGraded], the same distinction `CreateStudySessionScreen`
 * used to build the session in the first place. Flashcard is offered under Learn too, but since
 * Learn-Flashcard and Practice-Flashcard are both ungraded there's no stored field to tell them apart
 * — it resolves to Practice, which is harmless because every option that differs between the two
 * (DifficultyWeighting) is start-only and never shown mid-session.
 */
private val learnExclusiveModes = listOf(SessionMode.AUDIO, SessionMode.FREEFORM, SessionMode.TYPING)

fun StudyState.currentCategory(): StudyCategory = when {
    studyMode in modesForCategory(StudyCategory.GAMES) -> StudyCategory.GAMES
    studyMode in learnExclusiveModes -> StudyCategory.LEARN
    schedulingMode == SchedulingMode.FSRS -> StudyCategory.GUIDED
    isGraded -> StudyCategory.QUIZ
    else -> StudyCategory.PRACTICE
}

/** Difficulty weighting's starting counts: 1 card of difficulty 1, 2 of difficulty 2, and so on. Index 0 = difficulty 1. */
val DEFAULT_DIFFICULTY_COUNTS: List<Int> = ModeOptionDefaults.DIFFICULTY_COUNTS

/** Audio mode playback speeds offered in the session dialog and Mode Defaults. 1.0 is normal speed. */

/**
 * Per-(category, mode) defaults for `CreateStudySessionScreen`'s mode-specific options, set from
 * Settings → Mode Defaults. `null` means "nothing stored, use the dialog's own hardcoded fallback."
 * Difficulty weighting is stored here too (`difficultyWeighted` + `difficultyCounts`, index 0 =
 * difficulty 1). Excludes every option `applyCategory()` forces on its own (`isGraded`,
 * `allowMultipleGuesses`, etc.), since a stored default for those would never actually take effect.
 */
data class ModeDefaultSettings(
    val numberOfAnswers: Int? = null,
    val showCorrectLetters: Boolean? = null,
    val maxMemoryTiles: Int? = null,
    val gridDensity: Int? = null,
    val showCorrectWords: Boolean? = null,
    val freeformLayoutVertical: Boolean? = null,
    val quizPromptSide: CardSide? = null,
    val difficultyWeighted: Boolean? = null,
    val difficultyCounts: List<Int>? = null,
    val audioPlaybackSpeed: Float? = null,
    val audioReplayCount: Int? = null,
    val audioAutoAdvance: Boolean? = null,
    val audioAnswerDelaySeconds: Double? = null,
    val audioNextCardDelaySeconds: Double? = null,
    val freeformShowBothSides: Boolean? = null,
    val freeformSwipeNavigation: Int? = null,
    val typingIgnoreFormatting: Boolean? = null,
    val typingDisableAutocorrect: Boolean? = null,
    val typingShowLengthHint: Boolean? = null,
    val typedListenUseMultipleChoice: Boolean? = null,
    val hearMatchAutoPlayAllOptions: Boolean? = null,
    val hideDifficultySelection: Boolean? = null,
    val flashcardAutoFlipSeconds: Int? = null,
    val flashcardAutoFlip: Boolean? = null,
    val flashcardDoubleTapToFlip: Boolean? = null,
    val flashcardRandomizeFirstSide: Boolean? = null,
    val requireConfirmTap: Boolean? = null,
    val allowMultipleGuesses: Boolean? = null,
    val autoAdvance: Boolean? = null,
    val autoAdvanceDelaySeconds: Double? = null,
    val autoListen: Boolean? = null,
    val listenStartSound: Boolean? = null,
    val listenCorrectSound: Boolean? = null,
    val listenIncorrectSound: Boolean? = null,
    val speakingFrontSpeed: Float? = null,
    val speakingBackSpeed: Float? = null,
    val listResetPosition: Boolean? = null,
    val listDimWrongGuesses: Boolean? = null,
    val listRemoveGuessed: Boolean? = null,
    val matchingHighlightStyle: String? = null,
    val matchingWrongDelayMs: Int? = null,
    val matchingCorrectHighlightMs: Int? = null,
    val matchingShowCorrectDialog: Boolean? = null,
    val anagramFirstLetterHint: Boolean? = null,
    val anagramUppercase: Boolean? = null,
    val anagramColorVowels: Boolean? = null,
    val crosswordHighlightWord: Boolean? = null,
    val crosswordAutoAdvanceCell: Boolean? = null,
    val crosswordCompactClues: Boolean? = null,
    val hangmanMaxMistakes: Int? = null,
    val hangmanHideVisual: Boolean? = null,
    val memoryGrayMatched: Boolean? = null,
    val memoryPeekSeconds: Int? = null,
    val memoryGridColumnsPortrait: Int? = null,
    val memoryGridColumnsLandscape: Int? = null,
    val memoryWrongPairMs: Int? = null,
    val memoryCorrectPairMs: Int? = null,
    val memorySubmitAnswer: Boolean? = null,
    val wordSearchHideFound: Boolean? = null,
    val wordSearchHighlightColor: Int? = null
) {
    /** Only the non-null fields, as one JSON object (one leaf of the Settings → Mode Defaults store). */
    fun toJson(): JSONObject = JSONObject().apply {
        numberOfAnswers?.let { put("numberOfAnswers", it) }
        showCorrectLetters?.let { put("showCorrectLetters", it) }
        maxMemoryTiles?.let { put("maxMemoryTiles", it) }
        gridDensity?.let { put("gridDensity", it) }
        showCorrectWords?.let { put("showCorrectWords", it) }
        freeformLayoutVertical?.let { put("freeformLayoutVertical", it) }
        quizPromptSide?.let { put("quizPromptSide", it.name) }
        difficultyWeighted?.let { put("difficultyWeighted", it) }
        difficultyCounts?.let { put("difficultyCounts", JSONArray(it)) }
        audioPlaybackSpeed?.let { put("audioPlaybackSpeed", it.toDouble()) }
        audioReplayCount?.let { put("audioReplayCount", it) }
        audioAutoAdvance?.let { put("audioAutoAdvance", it) }
        audioAnswerDelaySeconds?.let { put("audioAnswerDelaySeconds", it) }
        audioNextCardDelaySeconds?.let { put("audioNextCardDelaySeconds", it) }
        freeformShowBothSides?.let { put("freeformShowBothSides", it) }
        freeformSwipeNavigation?.let { put("freeformSwipeNavigation", it) }
        typingIgnoreFormatting?.let { put("typingIgnoreFormatting", it) }
        typingDisableAutocorrect?.let { put("typingDisableAutocorrect", it) }
        typingShowLengthHint?.let { put("typingShowLengthHint", it) }
        typedListenUseMultipleChoice?.let { put("typedListenUseMultipleChoice", it) }
        hearMatchAutoPlayAllOptions?.let { put("hearMatchAutoPlayAllOptions", it) }
        hideDifficultySelection?.let { put("hideDifficultySelection", it) }
        flashcardAutoFlipSeconds?.let { put("flashcardAutoFlipSeconds", it) }
        flashcardAutoFlip?.let { put("flashcardAutoFlip", it) }
        flashcardDoubleTapToFlip?.let { put("flashcardDoubleTapToFlip", it) }
        flashcardRandomizeFirstSide?.let { put("flashcardRandomizeFirstSide", it) }
        requireConfirmTap?.let { put("requireConfirmTap", it) }
        allowMultipleGuesses?.let { put("allowMultipleGuesses", it) }
        autoAdvance?.let { put("autoAdvance", it) }
        autoAdvanceDelaySeconds?.let { put("autoAdvanceDelaySeconds", it) }
        autoListen?.let { put("autoListen", it) }
        listenStartSound?.let { put("listenStartSound", it) }
        listenCorrectSound?.let { put("listenCorrectSound", it) }
        listenIncorrectSound?.let { put("listenIncorrectSound", it) }
        speakingFrontSpeed?.let { put("speakingFrontSpeed", it.toDouble()) }
        speakingBackSpeed?.let { put("speakingBackSpeed", it.toDouble()) }
        listResetPosition?.let { put("listResetPosition", it) }
        listDimWrongGuesses?.let { put("listDimWrongGuesses", it) }
        listRemoveGuessed?.let { put("listRemoveGuessed", it) }
        matchingHighlightStyle?.let { put("matchingHighlightStyle", it) }
        matchingWrongDelayMs?.let { put("matchingWrongDelayMs", it) }
        matchingCorrectHighlightMs?.let { put("matchingCorrectHighlightMs", it) }
        matchingShowCorrectDialog?.let { put("matchingShowCorrectDialog", it) }
        anagramFirstLetterHint?.let { put("anagramFirstLetterHint", it) }
        anagramUppercase?.let { put("anagramUppercase", it) }
        anagramColorVowels?.let { put("anagramColorVowels", it) }
        crosswordHighlightWord?.let { put("crosswordHighlightWord", it) }
        crosswordAutoAdvanceCell?.let { put("crosswordAutoAdvanceCell", it) }
        crosswordCompactClues?.let { put("crosswordCompactClues", it) }
        hangmanMaxMistakes?.let { put("hangmanMaxMistakes", it) }
        hangmanHideVisual?.let { put("hangmanHideVisual", it) }
        memoryGrayMatched?.let { put("memoryGrayMatched", it) }
        memoryPeekSeconds?.let { put("memoryPeekSeconds", it) }
        memoryGridColumnsPortrait?.let { put("memoryGridColumnsPortrait", it) }
        memoryGridColumnsLandscape?.let { put("memoryGridColumnsLandscape", it) }
        memoryWrongPairMs?.let { put("memoryWrongPairMs", it) }
        memoryCorrectPairMs?.let { put("memoryCorrectPairMs", it) }
        memorySubmitAnswer?.let { put("memorySubmitAnswer", it) }
        wordSearchHideFound?.let { put("wordSearchHideFound", it) }
        wordSearchHighlightColor?.let { put("wordSearchHighlightColor", it) }
    }

    /** Non-null fields of [other] win; null fields of [other] keep this value. */
    fun overlaidWith(other: ModeDefaultSettings?): ModeDefaultSettings {
        if (other == null) return this
        return ModeDefaultSettings(
            numberOfAnswers = other.numberOfAnswers ?: numberOfAnswers,
            showCorrectLetters = other.showCorrectLetters ?: showCorrectLetters,
            maxMemoryTiles = other.maxMemoryTiles ?: maxMemoryTiles,
            gridDensity = other.gridDensity ?: gridDensity,
            showCorrectWords = other.showCorrectWords ?: showCorrectWords,
            freeformLayoutVertical = other.freeformLayoutVertical ?: freeformLayoutVertical,
            quizPromptSide = other.quizPromptSide ?: quizPromptSide,
            difficultyWeighted = other.difficultyWeighted ?: difficultyWeighted,
            difficultyCounts = other.difficultyCounts ?: difficultyCounts,
            audioPlaybackSpeed = other.audioPlaybackSpeed ?: audioPlaybackSpeed,
            audioReplayCount = other.audioReplayCount ?: audioReplayCount,
            audioAutoAdvance = other.audioAutoAdvance ?: audioAutoAdvance,
            audioAnswerDelaySeconds = other.audioAnswerDelaySeconds ?: audioAnswerDelaySeconds,
            audioNextCardDelaySeconds = other.audioNextCardDelaySeconds ?: audioNextCardDelaySeconds,
            freeformShowBothSides = other.freeformShowBothSides ?: freeformShowBothSides,
            freeformSwipeNavigation = other.freeformSwipeNavigation ?: freeformSwipeNavigation,
            typingIgnoreFormatting = other.typingIgnoreFormatting ?: typingIgnoreFormatting,
            typingDisableAutocorrect = other.typingDisableAutocorrect ?: typingDisableAutocorrect,
            typingShowLengthHint = other.typingShowLengthHint ?: typingShowLengthHint,
            typedListenUseMultipleChoice = other.typedListenUseMultipleChoice ?: typedListenUseMultipleChoice,
            hearMatchAutoPlayAllOptions = other.hearMatchAutoPlayAllOptions ?: hearMatchAutoPlayAllOptions,
            hideDifficultySelection = other.hideDifficultySelection ?: hideDifficultySelection,
            flashcardAutoFlipSeconds = other.flashcardAutoFlipSeconds ?: flashcardAutoFlipSeconds,
            flashcardAutoFlip = other.flashcardAutoFlip ?: flashcardAutoFlip,
            flashcardDoubleTapToFlip = other.flashcardDoubleTapToFlip ?: flashcardDoubleTapToFlip,
            flashcardRandomizeFirstSide = other.flashcardRandomizeFirstSide ?: flashcardRandomizeFirstSide,
            requireConfirmTap = other.requireConfirmTap ?: requireConfirmTap,
            allowMultipleGuesses = other.allowMultipleGuesses ?: allowMultipleGuesses,
            autoAdvance = other.autoAdvance ?: autoAdvance,
            autoAdvanceDelaySeconds = other.autoAdvanceDelaySeconds ?: autoAdvanceDelaySeconds,
            autoListen = other.autoListen ?: autoListen,
            listenStartSound = other.listenStartSound ?: listenStartSound,
            listenCorrectSound = other.listenCorrectSound ?: listenCorrectSound,
            listenIncorrectSound = other.listenIncorrectSound ?: listenIncorrectSound,
            speakingFrontSpeed = other.speakingFrontSpeed ?: speakingFrontSpeed,
            speakingBackSpeed = other.speakingBackSpeed ?: speakingBackSpeed,
            listResetPosition = other.listResetPosition ?: listResetPosition,
            listDimWrongGuesses = other.listDimWrongGuesses ?: listDimWrongGuesses,
            listRemoveGuessed = other.listRemoveGuessed ?: listRemoveGuessed,
            matchingHighlightStyle = other.matchingHighlightStyle ?: matchingHighlightStyle,
            matchingWrongDelayMs = other.matchingWrongDelayMs ?: matchingWrongDelayMs,
            matchingCorrectHighlightMs = other.matchingCorrectHighlightMs ?: matchingCorrectHighlightMs,
            matchingShowCorrectDialog = other.matchingShowCorrectDialog ?: matchingShowCorrectDialog,
            anagramFirstLetterHint = other.anagramFirstLetterHint ?: anagramFirstLetterHint,
            anagramUppercase = other.anagramUppercase ?: anagramUppercase,
            anagramColorVowels = other.anagramColorVowels ?: anagramColorVowels,
            crosswordHighlightWord = other.crosswordHighlightWord ?: crosswordHighlightWord,
            crosswordAutoAdvanceCell = other.crosswordAutoAdvanceCell ?: crosswordAutoAdvanceCell,
            crosswordCompactClues = other.crosswordCompactClues ?: crosswordCompactClues,
            hangmanMaxMistakes = other.hangmanMaxMistakes ?: hangmanMaxMistakes,
            hangmanHideVisual = other.hangmanHideVisual ?: hangmanHideVisual,
            memoryGrayMatched = other.memoryGrayMatched ?: memoryGrayMatched,
            memoryPeekSeconds = other.memoryPeekSeconds ?: memoryPeekSeconds,
            memoryGridColumnsPortrait = other.memoryGridColumnsPortrait ?: memoryGridColumnsPortrait,
            memoryGridColumnsLandscape = other.memoryGridColumnsLandscape ?: memoryGridColumnsLandscape,
            memoryWrongPairMs = other.memoryWrongPairMs ?: memoryWrongPairMs,
            memoryCorrectPairMs = other.memoryCorrectPairMs ?: memoryCorrectPairMs,
            memorySubmitAnswer = other.memorySubmitAnswer ?: memorySubmitAnswer,
            wordSearchHideFound = other.wordSearchHideFound ?: wordSearchHideFound,
            wordSearchHighlightColor = other.wordSearchHighlightColor ?: wordSearchHighlightColor
        )
    }

    companion object {
        /** Inverse of [toJson]; a missing key reads as null (nothing stored for that option). */
        fun fromJson(leaf: JSONObject): ModeDefaultSettings = ModeDefaultSettings(
            numberOfAnswers = if (leaf.has("numberOfAnswers")) leaf.getInt("numberOfAnswers") else null,
            showCorrectLetters = if (leaf.has("showCorrectLetters")) leaf.getBoolean("showCorrectLetters") else null,
            maxMemoryTiles = if (leaf.has("maxMemoryTiles")) leaf.getInt("maxMemoryTiles") else null,
            gridDensity = if (leaf.has("gridDensity")) leaf.getInt("gridDensity") else null,
            showCorrectWords = if (leaf.has("showCorrectWords")) leaf.getBoolean("showCorrectWords") else null,
            freeformLayoutVertical = if (leaf.has("freeformLayoutVertical")) leaf.getBoolean("freeformLayoutVertical") else null,
            quizPromptSide = if (leaf.has("quizPromptSide")) leaf.getString("quizPromptSide").toCardSide() else null,
            difficultyWeighted = if (leaf.has("difficultyWeighted")) leaf.getBoolean("difficultyWeighted") else null,
            difficultyCounts = if (leaf.has("difficultyCounts")) leaf.getJSONArray("difficultyCounts").let { arr -> List(arr.length()) { arr.getInt(it) } } else null,
            audioPlaybackSpeed = if (leaf.has("audioPlaybackSpeed")) leaf.getDouble("audioPlaybackSpeed").toFloat() else null,
            audioReplayCount = if (leaf.has("audioReplayCount")) leaf.getInt("audioReplayCount") else null,
            audioAutoAdvance = if (leaf.has("audioAutoAdvance")) leaf.getBoolean("audioAutoAdvance") else null,
            audioAnswerDelaySeconds = if (leaf.has("audioAnswerDelaySeconds")) leaf.getDouble("audioAnswerDelaySeconds") else null,
            audioNextCardDelaySeconds = if (leaf.has("audioNextCardDelaySeconds")) leaf.getDouble("audioNextCardDelaySeconds") else null,
            freeformShowBothSides = if (leaf.has("freeformShowBothSides")) leaf.getBoolean("freeformShowBothSides") else null,
            freeformSwipeNavigation = if (leaf.has("freeformSwipeNavigation")) leaf.getInt("freeformSwipeNavigation") else null,
            typingIgnoreFormatting = if (leaf.has("typingIgnoreFormatting")) leaf.getBoolean("typingIgnoreFormatting") else null,
            typingDisableAutocorrect = if (leaf.has("typingDisableAutocorrect")) leaf.getBoolean("typingDisableAutocorrect") else null,
            typingShowLengthHint = if (leaf.has("typingShowLengthHint")) leaf.getBoolean("typingShowLengthHint") else null,
            typedListenUseMultipleChoice = if (leaf.has("typedListenUseMultipleChoice")) leaf.getBoolean("typedListenUseMultipleChoice") else null,
            hearMatchAutoPlayAllOptions = if (leaf.has("hearMatchAutoPlayAllOptions")) leaf.getBoolean("hearMatchAutoPlayAllOptions") else null,
            hideDifficultySelection = if (leaf.has("hideDifficultySelection")) leaf.getBoolean("hideDifficultySelection") else null,
            flashcardAutoFlipSeconds = if (leaf.has("flashcardAutoFlipSeconds")) leaf.getInt("flashcardAutoFlipSeconds") else null,
            flashcardAutoFlip = if (leaf.has("flashcardAutoFlip")) leaf.getBoolean("flashcardAutoFlip") else null,
            flashcardDoubleTapToFlip = if (leaf.has("flashcardDoubleTapToFlip")) leaf.getBoolean("flashcardDoubleTapToFlip") else null,
            flashcardRandomizeFirstSide = if (leaf.has("flashcardRandomizeFirstSide")) leaf.getBoolean("flashcardRandomizeFirstSide") else null,
            requireConfirmTap = if (leaf.has("requireConfirmTap")) leaf.getBoolean("requireConfirmTap") else null,
            allowMultipleGuesses = if (leaf.has("allowMultipleGuesses")) leaf.getBoolean("allowMultipleGuesses") else null,
            autoAdvance = if (leaf.has("autoAdvance")) leaf.getBoolean("autoAdvance") else null,
            autoAdvanceDelaySeconds = if (leaf.has("autoAdvanceDelaySeconds")) leaf.getDouble("autoAdvanceDelaySeconds") else null,
            autoListen = if (leaf.has("autoListen")) leaf.getBoolean("autoListen") else null,
            listenStartSound = if (leaf.has("listenStartSound")) leaf.getBoolean("listenStartSound") else null,
            listenCorrectSound = if (leaf.has("listenCorrectSound")) leaf.getBoolean("listenCorrectSound") else null,
            listenIncorrectSound = if (leaf.has("listenIncorrectSound")) leaf.getBoolean("listenIncorrectSound") else null,
            speakingFrontSpeed = if (leaf.has("speakingFrontSpeed")) leaf.getDouble("speakingFrontSpeed").toFloat() else null,
            speakingBackSpeed = if (leaf.has("speakingBackSpeed")) leaf.getDouble("speakingBackSpeed").toFloat() else null,
            listResetPosition = if (leaf.has("listResetPosition")) leaf.getBoolean("listResetPosition") else null,
            listDimWrongGuesses = if (leaf.has("listDimWrongGuesses")) leaf.getBoolean("listDimWrongGuesses") else null,
            listRemoveGuessed = if (leaf.has("listRemoveGuessed")) leaf.getBoolean("listRemoveGuessed") else null,
            matchingHighlightStyle = if (leaf.has("matchingHighlightStyle")) leaf.getString("matchingHighlightStyle") else null,
            matchingWrongDelayMs = if (leaf.has("matchingWrongDelayMs")) leaf.getInt("matchingWrongDelayMs") else null,
            matchingCorrectHighlightMs = if (leaf.has("matchingCorrectHighlightMs")) leaf.getInt("matchingCorrectHighlightMs") else null,
            matchingShowCorrectDialog = if (leaf.has("matchingShowCorrectDialog")) leaf.getBoolean("matchingShowCorrectDialog") else null,
            anagramFirstLetterHint = if (leaf.has("anagramFirstLetterHint")) leaf.getBoolean("anagramFirstLetterHint") else null,
            anagramUppercase = if (leaf.has("anagramUppercase")) leaf.getBoolean("anagramUppercase") else null,
            anagramColorVowels = if (leaf.has("anagramColorVowels")) leaf.getBoolean("anagramColorVowels") else null,
            crosswordHighlightWord = if (leaf.has("crosswordHighlightWord")) leaf.getBoolean("crosswordHighlightWord") else null,
            crosswordAutoAdvanceCell = if (leaf.has("crosswordAutoAdvanceCell")) leaf.getBoolean("crosswordAutoAdvanceCell") else null,
            crosswordCompactClues = if (leaf.has("crosswordCompactClues")) leaf.getBoolean("crosswordCompactClues") else null,
            hangmanMaxMistakes = if (leaf.has("hangmanMaxMistakes")) leaf.getInt("hangmanMaxMistakes") else null,
            hangmanHideVisual = if (leaf.has("hangmanHideVisual")) leaf.getBoolean("hangmanHideVisual") else null,
            memoryGrayMatched = if (leaf.has("memoryGrayMatched")) leaf.getBoolean("memoryGrayMatched") else null,
            memoryPeekSeconds = if (leaf.has("memoryPeekSeconds")) leaf.getInt("memoryPeekSeconds") else null,
            memoryGridColumnsPortrait = if (leaf.has("memoryGridColumnsPortrait")) leaf.getInt("memoryGridColumnsPortrait") else null,
            memoryGridColumnsLandscape = if (leaf.has("memoryGridColumnsLandscape")) leaf.getInt("memoryGridColumnsLandscape") else null,
            memoryWrongPairMs = if (leaf.has("memoryWrongPairMs")) leaf.getInt("memoryWrongPairMs") else null,
            memoryCorrectPairMs = if (leaf.has("memoryCorrectPairMs")) leaf.getInt("memoryCorrectPairMs") else null,
            memorySubmitAnswer = if (leaf.has("memorySubmitAnswer")) leaf.getBoolean("memorySubmitAnswer") else null,
            wordSearchHideFound = if (leaf.has("wordSearchHideFound")) leaf.getBoolean("wordSearchHideFound") else null,
            wordSearchHighlightColor = if (leaf.has("wordSearchHighlightColor")) leaf.getInt("wordSearchHighlightColor") else null
        )
    }
}

fun String.toSessionMode(): SessionMode {
    return when (this.lowercase().trim()) {
        "flashcard" -> SessionMode.FLASHCARD
        "list" -> SessionMode.LIST
        "multiple choice" -> SessionMode.MULTIPLE_CHOICE
        "multiple_choice" -> SessionMode.MULTIPLE_CHOICE
        "typing" -> SessionMode.TYPING
        // Legacy name for TYPING_SCORED (see the enum's doc comment) — old backups/Firestore docs
        // may still have mode:"QUIZ"; Room's own Converters get the equivalent fix via MIGRATION_13_14.
        "quiz" -> SessionMode.TYPING_SCORED
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
        // Legacy enum names, renamed to LISTEN_TYPE/SEE_SPEAK — old backups/Firestore docs may still
        // have these exact strings; `valueOf(this)` below would otherwise throw and silently fall
        // back to FLASHCARD.
        "typed_listen" -> SessionMode.LISTEN_TYPE
        "spoken_listen" -> SessionMode.SEE_SPEAK
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

// Replaces the old separate `displaySetsUnderDecks`/`gridLargeScreenLayout` booleans: the two
// only ever meant something in combination (grid-large-screen-layout was ignored once sets were
// hidden entirely), so a single 3-state setting is the actual shape of the choice being made.
/** The window width (dp) at which the app switches to desktop mode (nav rail) and shows multiple panes. */
const val DESKTOP_MIN_WIDTH_DP = 925

enum class DeckSetsDisplayMode(val value: Int, override val labelResId: Int) : StringResourceEnum {
    OFF(0, R.string.display_sets_off),
    UNDER_DECKS(1, R.string.display_sets_under_decks_option),
    BESIDE_DECKS(2, R.string.display_sets_beside_decks_option);

    companion object {
        fun fromInt(value: Int?): DeckSetsDisplayMode {
            return DeckSetsDisplayMode.entries.find { it.value == value } ?: BESIDE_DECKS
        }

        /**
         * Resolves the *default* shown before a user ever picks explicitly (`stored == null`):
         * the full "beside decks" flow layout only earns its keep when there's genuinely a lot of
         * screen to spread across in both dimensions — a wide-but-short or tall-but-narrow window
         * still gets the more conservative "under decks" list. Once the user picks anything
         * (including re-picking the same value the default would have shown), `stored` is no
         * longer null and this size check is never consulted again for them.
         */
        fun resolve(
            stored: DeckSetsDisplayMode?,
            widthSizeClass: androidx.compose.material3.windowsizeclass.WindowWidthSizeClass,
            heightSizeClass: androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
        ): DeckSetsDisplayMode {
            if (stored != null) return stored
            val bothExpanded = widthSizeClass >= androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded &&
                heightSizeClass >= androidx.compose.material3.windowsizeclass.WindowHeightSizeClass.Expanded
            return if (bothExpanded) BESIDE_DECKS else UNDER_DECKS
        }

        /** Same size check as [resolve], for the tree view's vertical/horizontal direction default. */
        fun resolveTreeDirection(
            stored: Boolean?,
            widthSizeClass: androidx.compose.material3.windowsizeclass.WindowWidthSizeClass,
            heightSizeClass: androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
        ): Boolean {
            if (stored != null) return stored
            return widthSizeClass >= androidx.compose.material3.windowsizeclass.WindowWidthSizeClass.Expanded &&
                heightSizeClass >= androidx.compose.material3.windowsizeclass.WindowHeightSizeClass.Expanded
        }
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

fun normalizeText(type: NormalizationType, text: String): String = when (type) {
    NormalizationType.UPPERCASE_FIRST_LETTER -> text.replaceFirstChar { it.uppercase() }
    NormalizationType.UPPERCASE_ALL_LETTERS -> text.uppercase()
    NormalizationType.UPPERCASE_EACH_WORD -> text.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
    NormalizationType.LOWERCASE_FIRST_LETTER -> text.replaceFirstChar { it.lowercase() }
    NormalizationType.LOWERCASE_ALL_LETTERS -> text.lowercase()
    NormalizationType.LOWERCASE_EACH_WORD -> text.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.lowercase() } }
    NormalizationType.NONE -> text
}

/**
 * Applies [normalizeText] to a field's current value the moment it loses focus, purely as live
 * visual feedback while editing — the actual save pipeline (FlashcardViewModel.saveDeckWithCards)
 * re-normalizes everything anyway, so this never needs to be the source of truth, just a preview.
 * Tracks its own previously-focused flag so it fires only on a true focused->unfocused edge, not
 * on the field's very first (already-unfocused) composition.
 */
@Composable
fun Modifier.normalizeCaseOnFocusLost(
    normalizationType: NormalizationType,
    text: String,
    onNormalized: (String) -> Unit
): Modifier {
    var wasFocused by remember { mutableStateOf(false) }
    return this.onFocusChanged { focusState ->
        if (wasFocused && !focusState.isFocused) {
            val normalized = normalizeText(normalizationType, text)
            if (normalized != text) onNormalized(normalized)
        }
        wasFocused = focusState.isFocused
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