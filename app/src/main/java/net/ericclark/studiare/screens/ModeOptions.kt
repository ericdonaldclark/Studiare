package net.ericclark.studiare.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.withShortcut
import androidx.compose.ui.input.key.Key
import net.ericclark.studiare.CountPicker
import net.ericclark.studiare.DialogSection
import net.ericclark.studiare.R
import net.ericclark.studiare.ShortcutScreen
import net.ericclark.studiare.TooltipIconButton
import net.ericclark.studiare.TooltipFilledTonalIconButton
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

/*
 * Mode options: every user-facing option a session mode can have, declared once here and rendered by
 * both the session creation dialog (CreateStudySessionDialog) and Settings → Mode Defaults.
 *
 * To add an option:
 *  1. Add its field to ModeDefaultSettings (data/Values.kt) — that's where storage and JSON live.
 *  2. Add an object below (a SwitchModeOption subclass for a plain on/off, or a ModeOption subclass
 *     for anything else) with its label, the modes it applies to, its fallback, and its control.
 *  3. Add it to [modeOptions]. Both screens pick it up from there.
 */

/** What a control needs beyond its own value. */
class ModeOptionContext(
    /** Upper bound for a difficulty's count stepper (the session dialog passes what its filters leave). */
    val maxForDifficulty: (Int) -> Int,
    /** Total cards the current filters leave; only used for summaries in the session dialog. */
    val availableCardsCount: Int = 0,
    /** Settings only: applies this value to every mode. Null where that doesn't make sense (the dialog). */
    val onApplyToAll: ((ModeDefaultSettings) -> Unit)? = null,
    /** The category being configured. Some defaults differ by category (e.g. Typing's length hint). Null in a running session. */
    val category: StudyCategory? = null
)

/**
 * Whether an option can be changed from a running session's settings button.
 * LIVE: takes effect immediately or on the next card. START_ONLY: the session builds what it needs at
 * start (answer choices, memory board, crossword grid, difficulty mix), so it isn't offered mid-session.
 */
enum class SessionEdit { LIVE, START_ONLY }

abstract class ModeOption(
    val labelRes: Int,
    /** Modes this option appears for; empty means every mode. */
    private val modes: Set<SessionMode> = emptySet(),
    /** In the session dialog, shown as its own collapsible section instead of inside Mode Settings. */
    val dialogSection: Boolean = false,
    val sessionEdit: SessionEdit = SessionEdit.LIVE
) {
    val id: String get() = this::class.java.simpleName

    fun appliesTo(mode: SessionMode): Boolean = modes.isEmpty() || mode in modes

    /** Subtitle for the dialog section while collapsed. */
    @Composable
    open fun summary(values: ModeDefaultSettings, context: ModeOptionContext): String? = null

    /** The control itself. [values] are the current settings; [onChange] receives the full new set. */
    @Composable
    abstract fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit)
}

/** A plain on/off option: a label, a description, and a switch. */
abstract class SwitchModeOption(
    labelRes: Int,
    private val descriptionRes: Int,
    modes: Set<SessionMode> = emptySet(),
    sessionEdit: SessionEdit = SessionEdit.LIVE
) : ModeOption(labelRes, modes, sessionEdit = sessionEdit) {
    abstract fun valueIn(values: ModeDefaultSettings): Boolean
    abstract fun withValue(values: ModeDefaultSettings, value: Boolean): ModeDefaultSettings

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        ModeSwitchRow(getText(labelRes), getText(descriptionRes), valueIn(values)) { onChange(withValue(values, it)) }
    }
}

/** Flush-left on/off row (title, description, switch). No ListItem inset, so it lines up with the other controls. */
@Composable
private fun ModeSwitchRow(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }.padding(vertical = dimensions.paddingSmall)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** -/+ stepper with a tonal value indicator, shared by the stepper options. */
@Composable
private fun ValueStepper(
    valueText: String,
    canDecrease: Boolean, onDecrease: () -> Unit,
    canIncrease: Boolean, onIncrease: () -> Unit,
    decreaseDescription: String, increaseDescription: String
) {
    val dimensions = LocalStudiareDimensions.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = Modifier.fillMaxWidth()
    ) {
        TooltipFilledTonalIconButton(description = decreaseDescription, onClick = onDecrease, enabled = canDecrease) {
            Icon(Icons.Default.Remove, decreaseDescription)
        }
        Spacer(Modifier.width(dimensions.spacingSmall))
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                text = valueText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
            )
        }
        Spacer(Modifier.width(dimensions.spacingSmall))
        TooltipFilledTonalIconButton(description = increaseDescription, onClick = onIncrease, enabled = canIncrease) {
            Icon(Icons.Default.Add, increaseDescription)
        }
    }
}

object NumberOfAnswersOption : ModeOption(R.string.answers, setOf(SessionMode.MULTIPLE_CHOICE), sessionEdit = SessionEdit.START_ONLY) {
    fun valueIn(values: ModeDefaultSettings): Int = values.numberOfAnswers ?: ModeOptionDefaults.NUMBER_OF_ANSWERS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val answers = valueIn(values)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)
        ) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = answers.toString(),
                canDecrease = answers > 2, onDecrease = { onChange(values.copy(numberOfAnswers = answers - 1)) },
                canIncrease = answers < 8, onIncrease = { onChange(values.copy(numberOfAnswers = answers + 1)) },
                decreaseDescription = getText(R.string.less), increaseDescription = getText(R.string.more)
            )
        }
    }
}

/**
 * Green/red outlines on letters as they're typed. Typing's Practice shows them by default and Quiz doesn't;
 * Anagram shows them. Typing's Learn always shows them, so it isn't offered there.
 */
object ShowCorrectLettersOption : ModeOption(R.string.show_correct_letters, setOf(SessionMode.ANAGRAM, SessionMode.TYPING_SCORED)) {
    fun valueIn(values: ModeDefaultSettings, category: StudyCategory?): Boolean =
        values.showCorrectLetters ?: (category != StudyCategory.QUIZ)

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        ModeSwitchRow(getText(labelRes), getText(R.string.typing_show_correct_letters_desc), valueIn(values, context.category)) {
            onChange(values.copy(showCorrectLetters = it))
        }
    }
}

object FingersAndToesOption : SwitchModeOption(R.string.fingers_and_toes, R.string.fingers_and_toes_desc, setOf(SessionMode.HANGMAN)) {
    override fun valueIn(values: ModeDefaultSettings) = values.fingersAndToes ?: ModeOptionDefaults.FINGERS_AND_TOES
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(fingersAndToes = value)
}

object MaxMemoryTilesOption : ModeOption(R.string.memory_tiles, setOf(SessionMode.MEMORY), sessionEdit = SessionEdit.START_ONLY) {
    fun valueIn(values: ModeDefaultSettings): Int = values.maxMemoryTiles ?: ModeOptionDefaults.MAX_MEMORY_TILES

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val tiles = valueIn(values)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)
        ) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = "$tiles Tiles",
                canDecrease = tiles > 4, onDecrease = { onChange(values.copy(maxMemoryTiles = (tiles - 2).coerceAtLeast(4))) },
                canIncrease = tiles < 100, onIncrease = { onChange(values.copy(maxMemoryTiles = tiles + 2)) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

object GridDensityOption : ModeOption(R.string.grid_density, setOf(SessionMode.CROSSWORD, SessionMode.WORD_SEARCH), sessionEdit = SessionEdit.START_ONLY) {
    fun valueIn(values: ModeDefaultSettings): Int = values.gridDensity ?: ModeOptionDefaults.GRID_DENSITY

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val density = valueIn(values)
        val densityLabel = when (density) {
            1 -> getText(R.string.sparse); 2 -> getText(R.string.balanced); else -> getText(R.string.compact)
        }
        Text(getText(labelRes) + ": $densityLabel")
        Slider(
            value = density.toFloat(),
            onValueChange = { onChange(values.copy(gridDensity = it.roundToInt())) },
            valueRange = 1f..3f,
            steps = 1
        )
    }
}

object ShowCorrectWordsOption : SwitchModeOption(R.string.show_correct_words, R.string.show_correct_words_desc, setOf(SessionMode.CROSSWORD, SessionMode.WORD_SEARCH), sessionEdit = SessionEdit.START_ONLY) {
    override fun valueIn(values: ModeDefaultSettings) = values.showCorrectWords ?: ModeOptionDefaults.SHOW_CORRECT_WORDS
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(showCorrectWords = value)
}

object FreeformLayoutOption : SwitchModeOption(R.string.vertical_layout, R.string.vertical_layout_desc, setOf(SessionMode.FREEFORM)) {
    override fun valueIn(values: ModeDefaultSettings) = values.freeformLayoutVertical ?: ModeOptionDefaults.FREEFORM_LAYOUT_VERTICAL
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(freeformLayoutVertical = value)
}

/** Shown in every mode; its own section in the session dialog. Front/back segmented choice. */
object PromptSideOption : ModeOption(R.string.prompt_side) {
    fun valueIn(values: ModeDefaultSettings): CardSide = values.quizPromptSide ?: CardSide.FRONT

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val side = valueIn(values)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)
        ) {
            Text(getText(labelRes))
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = side == CardSide.FRONT,
                onClick = { onChange(values.copy(quizPromptSide = CardSide.FRONT)) },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(CardSide.FRONT.asString(), style = MaterialTheme.typography.labelLarge) }
            SegmentedButton(
                selected = side == CardSide.BACK,
                onClick = { onChange(values.copy(quizPromptSide = CardSide.BACK)) },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(CardSide.BACK.asString(), style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

/**
 * Shown in every mode; its own section in the session dialog. A switch, then (when on) a 1-5 chip row
 * picking a difficulty and a count stepper for how many cards of that difficulty the session takes.
 * The session dialog caps each count at what its filters leave; Settings lets it go up to 100.
 */
object DifficultyWeightingOption : ModeOption(R.string.difficulty_weighting, dialogSection = true, sessionEdit = SessionEdit.START_ONLY) {
    fun isWeighted(values: ModeDefaultSettings): Boolean = values.difficultyWeighted ?: false

    private fun storedCounts(values: ModeDefaultSettings): List<Int> =
        values.difficultyCounts?.takeIf { it.size == 5 } ?: DEFAULT_DIFFICULTY_COUNTS

    /** The count for [difficulty], capped by [context]. */
    fun effectiveCountFor(values: ModeDefaultSettings, difficulty: Int, context: ModeOptionContext): Int =
        storedCounts(values)[difficulty - 1].coerceIn(0, context.maxForDifficulty(difficulty))

    /** Total cards the weighting takes (each difficulty's effective count, summed). */
    fun totalFor(values: ModeDefaultSettings, context: ModeOptionContext): Int =
        (1..5).sumOf { effectiveCountFor(values, it, context) }

    private fun withCount(values: ModeDefaultSettings, difficulty: Int, count: Int): ModeDefaultSettings {
        val counts = storedCounts(values).toMutableList()
        counts[difficulty - 1] = count
        return values.copy(difficultyCounts = counts)
    }

    @Composable
    override fun summary(values: ModeDefaultSettings, context: ModeOptionContext): String =
        if (isWeighted(values)) stringResource(R.string.count_of_total_format, totalFor(values, context), context.availableCardsCount)
        else getText(R.string.difficulty_weighting_off)

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val dimensions = LocalStudiareDimensions.current
        var selectedDifficulty by rememberSaveable { mutableIntStateOf(1) }
        val weighted = isWeighted(values)

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(getText(R.string.difficulty_weighting_switch), modifier = Modifier.weight(1f))
            Switch(checked = weighted, onCheckedChange = { onChange(values.copy(difficultyWeighted = it)) })
        }
        if (weighted) {
            Spacer(Modifier.height(dimensions.spacingSmall))
            TypedChipRow(
                items = DifficultySetting.entries,
                selected = DifficultySetting.fromInt(selectedDifficulty),
                labelFor = { it.value.toString() },
                onSelected = { selectedDifficulty = it.value }
            )
            val count = effectiveCountFor(values, selectedDifficulty, context)
            CountPicker(
                label = stringResource(R.string.difficulty_count, selectedDifficulty, count),
                value = count,
                max = context.maxForDifficulty(selectedDifficulty),
                onValueChange = { onChange(withCount(values, selectedDifficulty, it)) },
                showSlider = false,
                showPresets = false,
                min = 0
            )
        }
        context.onApplyToAll?.let { applyToAll ->
            Spacer(Modifier.height(dimensions.spacingSmall))
            FilledTonalButton(
                onClick = { applyToAll(values) },
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) { Text(getText(R.string.difficulty_weighting_apply_all)) }
        }
    }
}

object AudioPlaybackSpeedOption : ModeOption(R.string.audio_playback_speed, setOf(SessionMode.AUDIO, SessionMode.TYPED_LISTEN)) {
    fun valueIn(values: ModeDefaultSettings): Float = values.audioPlaybackSpeed ?: ModeOptionDefaults.AUDIO_PLAYBACK_SPEED

    private fun speedLabel(speed: Float): String = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×"

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val dimensions = LocalStudiareDimensions.current
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
            Text(getText(labelRes))
            TypedChipRow(
                items = AUDIO_PLAYBACK_SPEEDS,
                selected = valueIn(values),
                labelFor = { speedLabel(it) },
                onSelected = { onChange(values.copy(audioPlaybackSpeed = it)) },
                centered = true
            )
        }
    }
}

object AudioReplayCountOption : ModeOption(R.string.audio_replay_count, setOf(SessionMode.AUDIO, SessionMode.TYPED_LISTEN)) {
    fun valueIn(values: ModeDefaultSettings): Int = values.audioReplayCount ?: ModeOptionDefaults.AUDIO_REPLAY_COUNT

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val plays = valueIn(values)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)
        ) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = plays.toString(),
                canDecrease = plays > 1, onDecrease = { onChange(values.copy(audioReplayCount = plays - 1)) },
                canIncrease = plays < 3, onIncrease = { onChange(values.copy(audioReplayCount = plays + 1)) },
                decreaseDescription = getText(R.string.less), increaseDescription = getText(R.string.more)
            )
        }
    }
}

/** Seconds steppers for the audio delays: 0.5s steps from 0.5s to 10s. */
@Composable
private fun SecondsStepper(label: String, seconds: Double, min: Double = 0.5, max: Double = 10.0, onChange: (Double) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)
    ) {
        Text(label)
        ValueStepper(
            valueText = stringResource(R.string.time_seconds_format, seconds),
            canDecrease = seconds > min, onDecrease = { onChange((seconds - 0.5).coerceAtLeast(min)) },
            canIncrease = seconds < max, onIncrease = { onChange((seconds + 0.5).coerceAtMost(max)) },
            decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
        )
    }
}

object AudioAnswerDelayOption : ModeOption(R.string.answer_delay, setOf(SessionMode.AUDIO)) {
    fun valueIn(values: ModeDefaultSettings): Double = values.audioAnswerDelaySeconds ?: ModeOptionDefaults.AUDIO_ANSWER_DELAY_SECONDS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SecondsStepper(getText(labelRes), valueIn(values)) { onChange(values.copy(audioAnswerDelaySeconds = it)) }
    }
}

object AudioNextCardDelayOption : ModeOption(R.string.next_card_delay, setOf(SessionMode.AUDIO)) {
    fun valueIn(values: ModeDefaultSettings): Double = values.audioNextCardDelaySeconds ?: ModeOptionDefaults.AUDIO_NEXT_CARD_DELAY_SECONDS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SecondsStepper(getText(labelRes), valueIn(values)) { onChange(values.copy(audioNextCardDelaySeconds = it)) }
    }
}

object FreeformShowBothSidesOption : SwitchModeOption(R.string.freeform_show_both_sides, R.string.freeform_show_both_sides_desc, setOf(SessionMode.FREEFORM)) {
    override fun valueIn(values: ModeDefaultSettings) = values.freeformShowBothSides ?: ModeOptionDefaults.FREEFORM_SHOW_BOTH_SIDES
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(freeformShowBothSides = value)
}

object FreeformSwipeNavigationOption : SwitchModeOption(R.string.freeform_swipe_navigation, R.string.freeform_swipe_navigation_desc, setOf(SessionMode.FREEFORM)) {
    override fun valueIn(values: ModeDefaultSettings) = values.freeformSwipeNavigation ?: ModeOptionDefaults.FREEFORM_SWIPE_NAVIGATION
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(freeformSwipeNavigation = value)
}

object TypingIgnoreFormattingOption : SwitchModeOption(R.string.typing_ignore_formatting, R.string.typing_ignore_formatting_desc, setOf(SessionMode.TYPING, SessionMode.TYPING_SCORED, SessionMode.TYPED_LISTEN)) {
    override fun valueIn(values: ModeDefaultSettings) = values.typingIgnoreFormatting ?: ModeOptionDefaults.TYPING_IGNORE_FORMATTING
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(typingIgnoreFormatting = value)
}

object TypingAutoAdvanceOption : SwitchModeOption(R.string.typing_auto_submit, R.string.typing_auto_submit_desc, setOf(SessionMode.TYPING, SessionMode.TYPING_SCORED, SessionMode.TYPED_LISTEN)) {
    override fun valueIn(values: ModeDefaultSettings) = values.typingAutoSubmit ?: ModeOptionDefaults.TYPING_AUTO_SUBMIT
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(typingAutoSubmit = value)
}

object TypingDisableAutocorrectOption : SwitchModeOption(R.string.typing_disable_autocorrect, R.string.typing_disable_autocorrect_desc, setOf(SessionMode.TYPING, SessionMode.TYPING_SCORED, SessionMode.TYPED_LISTEN)) {
    override fun valueIn(values: ModeDefaultSettings) = values.typingDisableAutocorrect ?: ModeOptionDefaults.TYPING_DISABLE_AUTOCORRECT
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(typingDisableAutocorrect = value)
}

/** Scored typing only: Learn always shows a box for every letter. Practice shows the length by default, Quiz doesn't. */
object TypingShowLengthHintOption : ModeOption(R.string.typing_show_length_hint, setOf(SessionMode.TYPING_SCORED, SessionMode.TYPED_LISTEN)) {
    fun valueIn(values: ModeDefaultSettings, context: ModeOptionContext): Boolean =
        values.typingShowLengthHint ?: (context.category != StudyCategory.QUIZ)

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        ModeSwitchRow(getText(labelRes), getText(R.string.typing_show_length_hint_desc), valueIn(values, context)) {
            onChange(values.copy(typingShowLengthHint = it))
        }
    }
}

/** Flashcard: flip the card by itself after this many seconds on the first side (0 = off). */
object FlashcardAutoFlipOption : ModeOption(R.string.flashcard_auto_flip, setOf(SessionMode.FLASHCARD)) {
    fun valueIn(values: ModeDefaultSettings): Int = values.flashcardAutoFlipSeconds ?: ModeOptionDefaults.FLASHCARD_AUTO_FLIP_SECONDS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val dimensions = LocalStudiareDimensions.current
        val seconds = valueIn(values)
        val enabled = seconds > 0
        val min = ModeOptionDefaults.FLASHCARD_AUTO_FLIP_START_SECONDS_MIN
        val max = ModeOptionDefaults.FLASHCARD_AUTO_FLIP_START_SECONDS_MAX
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
            // Off stores 0; turning it on starts at the default delay, and the stepper takes over from there
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onChange(values.copy(flashcardAutoFlipSeconds = if (enabled) 0 else ModeOptionDefaults.FLASHCARD_AUTO_FLIP_START_SECONDS)) }
                    .padding(vertical = dimensions.paddingSmall)
            ) {
                Text(getText(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { on -> onChange(values.copy(flashcardAutoFlipSeconds = if (on) ModeOptionDefaults.FLASHCARD_AUTO_FLIP_START_SECONDS else 0)) }
                )
            }
            if (enabled) {
                ValueStepper(
                    valueText = "${seconds}s",
                    canDecrease = seconds > min, onDecrease = { onChange(values.copy(flashcardAutoFlipSeconds = seconds - 1)) },
                    canIncrease = seconds < max, onIncrease = { onChange(values.copy(flashcardAutoFlipSeconds = seconds + 1)) },
                    decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
                )
            }
        }
    }
}

object FlashcardDoubleTapOption : SwitchModeOption(R.string.flashcard_double_tap, R.string.flashcard_double_tap_desc, setOf(SessionMode.FLASHCARD)) {
    override fun valueIn(values: ModeDefaultSettings) = values.flashcardDoubleTapToFlip ?: ModeOptionDefaults.FLASHCARD_DOUBLE_TAP_TO_FLIP
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(flashcardDoubleTapToFlip = value)
}

object FlashcardRandomizeSideOption : SwitchModeOption(R.string.flashcard_randomize_side, R.string.flashcard_randomize_side_desc, setOf(SessionMode.FLASHCARD)) {
    override fun valueIn(values: ModeDefaultSettings) = values.flashcardRandomizeFirstSide ?: ModeOptionDefaults.FLASHCARD_RANDOMIZE_FIRST_SIDE
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(flashcardRandomizeFirstSide = value)
}

object AnagramFirstLetterHintOption : SwitchModeOption(R.string.anagram_first_letter_hint, R.string.anagram_first_letter_hint_desc, setOf(SessionMode.ANAGRAM)) {
    override fun valueIn(values: ModeDefaultSettings) = values.anagramFirstLetterHint ?: ModeOptionDefaults.ANAGRAM_FIRST_LETTER_HINT
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(anagramFirstLetterHint = value)
}

object AnagramUppercaseOption : SwitchModeOption(R.string.anagram_uppercase, R.string.anagram_uppercase_desc, setOf(SessionMode.ANAGRAM)) {
    override fun valueIn(values: ModeDefaultSettings) = values.anagramUppercase ?: ModeOptionDefaults.ANAGRAM_UPPERCASE
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(anagramUppercase = value)
}

object AnagramColorVowelsOption : SwitchModeOption(R.string.anagram_color_vowels, R.string.anagram_color_vowels_desc, setOf(SessionMode.ANAGRAM)) {
    override fun valueIn(values: ModeDefaultSettings) = values.anagramColorVowels ?: ModeOptionDefaults.ANAGRAM_COLOR_VOWELS
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(anagramColorVowels = value)
}

object HangmanMaxMistakesOption : ModeOption(R.string.hangman_max_mistakes, setOf(SessionMode.HANGMAN), sessionEdit = SessionEdit.START_ONLY) {
    fun valueIn(values: ModeDefaultSettings): Int = values.hangmanMaxMistakes ?: ModeOptionDefaults.HANGMAN_MAX_MISTAKES

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val guesses = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = guesses.toString(),
                canDecrease = guesses > ModeOptionDefaults.HANGMAN_MAX_MISTAKES_MIN, onDecrease = { onChange(values.copy(hangmanMaxMistakes = guesses - 1)) },
                canIncrease = guesses < ModeOptionDefaults.HANGMAN_MAX_MISTAKES_MAX, onIncrease = { onChange(values.copy(hangmanMaxMistakes = guesses + 1)) },
                decreaseDescription = getText(R.string.less), increaseDescription = getText(R.string.more)
            )
        }
    }
}

object HangmanRevealSpeedOption : ModeOption(R.string.hangman_reveal_speed, setOf(SessionMode.HANGMAN)) {
    fun valueIn(values: ModeDefaultSettings): Int = values.hangmanRevealSpeedMs ?: ModeOptionDefaults.HANGMAN_REVEAL_SPEED_MS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val ms = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = "${ms} ms",
                canDecrease = ms > 0, onDecrease = { onChange(values.copy(hangmanRevealSpeedMs = (ms - 100).coerceAtLeast(0))) },
                canIncrease = ms < 1000, onIncrease = { onChange(values.copy(hangmanRevealSpeedMs = ms + 100)) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

object HangmanHideVisualOption : SwitchModeOption(R.string.hangman_hide_visual, R.string.hangman_hide_visual_desc, setOf(SessionMode.HANGMAN)) {
    override fun valueIn(values: ModeDefaultSettings) = values.hangmanHideVisual ?: ModeOptionDefaults.HANGMAN_HIDE_VISUAL
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(hangmanHideVisual = value)
}

object MemoryFlipAnimationOption : SwitchModeOption(R.string.memory_flip_animation, R.string.memory_flip_animation_desc, setOf(SessionMode.MEMORY)) {
    override fun valueIn(values: ModeDefaultSettings) = values.memoryFlipAnimation ?: ModeOptionDefaults.MEMORY_FLIP_ANIMATION
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(memoryFlipAnimation = value)
}

object MemoryGrayMatchedOption : SwitchModeOption(R.string.memory_gray_matched, R.string.memory_gray_matched_desc, setOf(SessionMode.MEMORY)) {
    override fun valueIn(values: ModeDefaultSettings) = values.memoryGrayMatched ?: ModeOptionDefaults.MEMORY_GRAY_MATCHED
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(memoryGrayMatched = value)
}

object MemoryPeekOption : ModeOption(R.string.memory_peek, setOf(SessionMode.MEMORY), sessionEdit = SessionEdit.START_ONLY) {
    fun valueIn(values: ModeDefaultSettings): Int = values.memoryPeekSeconds ?: ModeOptionDefaults.MEMORY_PEEK_SECONDS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val seconds = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = if (seconds == 0) getText(R.string.flashcard_auto_flip_off) else "${seconds}s",
                canDecrease = seconds > 0, onDecrease = { onChange(values.copy(memoryPeekSeconds = seconds - 1)) },
                canIncrease = seconds < 10, onIncrease = { onChange(values.copy(memoryPeekSeconds = seconds + 1)) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

object MemoryWrongPairOption : ModeOption(R.string.memory_wrong_pair, setOf(SessionMode.MEMORY)) {
    fun valueIn(values: ModeDefaultSettings): Int = values.memoryWrongPairMs ?: ModeOptionDefaults.MEMORY_WRONG_PAIR_MS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val ms = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = if (ms == 0) getText(R.string.flashcard_auto_flip_off) else "${ms} ms",
                canDecrease = ms > 0, onDecrease = { onChange(values.copy(memoryWrongPairMs = (ms - 500).coerceAtLeast(0))) },
                canIncrease = ms < 5000, onIncrease = { onChange(values.copy(memoryWrongPairMs = ms + 500)) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

/** Colours offered for found-word lines in Word Search. The first is the default green. */
val WORD_SEARCH_HIGHLIGHT_COLORS: List<Int> = listOf(0xFF22C55E.toInt(), 0xFF3B82F6.toInt(), 0xFFF97316.toInt(), 0xFFEC4899.toInt())

object WordSearchHideFoundOption : SwitchModeOption(R.string.word_search_hide_found, R.string.word_search_hide_found_desc, setOf(SessionMode.WORD_SEARCH)) {
    override fun valueIn(values: ModeDefaultSettings) = values.wordSearchHideFound ?: ModeOptionDefaults.WORD_SEARCH_HIDE_FOUND
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(wordSearchHideFound = value)
}

object WordSearchHighlightColorOption : ModeOption(R.string.word_search_highlight_color, setOf(SessionMode.WORD_SEARCH)) {
    fun valueIn(values: ModeDefaultSettings): Int = values.wordSearchHighlightColor ?: WORD_SEARCH_HIGHLIGHT_COLORS.first()

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val dimensions = LocalStudiareDimensions.current
        val selected = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
            Text(getText(labelRes))
            Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
                WORD_SEARCH_HIGHLIGHT_COLORS.forEach { color ->
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(color))
                            .border(
                                androidx.compose.foundation.BorderStroke(if (color == selected) 3.dp else 1.dp, if (color == selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outlineVariant),
                                androidx.compose.foundation.shape.CircleShape
                            )
                            .clickable { onChange(values.copy(wordSearchHighlightColor = color)) }
                    )
                }
            }
        }
    }
}

object ListRequireConfirmOption : SwitchModeOption(R.string.require_confirm_tap, R.string.require_confirm_tap_desc, setOf(SessionMode.LIST)) {
    override fun valueIn(values: ModeDefaultSettings) = values.requireConfirmTap ?: ModeOptionDefaults.requireConfirmTapFor(SessionMode.LIST)
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(requireConfirmTap = value)
}

object ListResetPositionOption : SwitchModeOption(R.string.list_reset_position, R.string.list_reset_position_desc, setOf(SessionMode.LIST)) {
    override fun valueIn(values: ModeDefaultSettings) = values.listResetPosition ?: ModeOptionDefaults.LIST_RESET_POSITION
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(listResetPosition = value)
}

object ListDimWrongOption : SwitchModeOption(R.string.list_dim_wrong, R.string.list_dim_wrong_desc, setOf(SessionMode.LIST)) {
    override fun valueIn(values: ModeDefaultSettings) = values.listDimWrongGuesses ?: ModeOptionDefaults.LIST_DIM_WRONG_GUESSES
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(listDimWrongGuesses = value)
}

object AutoAdvanceAfterCorrectOption : SwitchModeOption(R.string.auto_advance_after_correct, R.string.auto_advance_after_correct_desc, setOf(SessionMode.LIST, SessionMode.MULTIPLE_CHOICE, SessionMode.SPOKEN_LISTEN)) {
    override fun valueIn(values: ModeDefaultSettings) = values.autoAdvanceAfterCorrect ?: ModeOptionDefaults.AUTO_ADVANCE_AFTER_CORRECT
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(autoAdvanceAfterCorrect = value)
}

object AutoAdvanceDelayOption : ModeOption(R.string.auto_advance_delay, setOf(SessionMode.LIST, SessionMode.MULTIPLE_CHOICE)) {
    fun valueIn(values: ModeDefaultSettings): Double = values.autoAdvanceDelaySeconds ?: ModeOptionDefaults.AUTO_ADVANCE_DELAY_SECONDS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SecondsStepper(getText(labelRes), valueIn(values), min = 0.5, max = 5.0) { onChange(values.copy(autoAdvanceDelaySeconds = it)) }
    }
}

object MatchingWrongDelayOption : ModeOption(R.string.matching_wrong_delay, setOf(SessionMode.MATCHING)) {
    fun valueIn(values: ModeDefaultSettings): Int = values.matchingWrongDelayMs ?: ModeOptionDefaults.MATCHING_WRONG_DELAY_MS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val ms = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = "${ms} ms",
                canDecrease = ms > 250, onDecrease = { onChange(values.copy(matchingWrongDelayMs = ms - 250)) },
                canIncrease = ms < 3000, onIncrease = { onChange(values.copy(matchingWrongDelayMs = ms + 250)) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

object MatchingHighlightOption : ModeOption(R.string.matching_highlight, setOf(SessionMode.MATCHING)) {
    fun valueIn(values: ModeDefaultSettings): String = values.matchingHighlightStyle ?: ModeOptionDefaults.MATCHING_HIGHLIGHT_STYLE

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            TypedChipRow(
                items = listOf("FILL", "BORDER"),
                selected = valueIn(values),
                labelFor = { if (it == "FILL") getText(R.string.highlight_fill) else getText(R.string.highlight_border) },
                onSelected = { onChange(values.copy(matchingHighlightStyle = it)) }
            )
        }
    }
}

object ChoiceRequireConfirmOption : SwitchModeOption(R.string.require_confirm_tap, R.string.require_confirm_tap_desc, setOf(SessionMode.MULTIPLE_CHOICE, SessionMode.MATCHING)) {
    override fun valueIn(values: ModeDefaultSettings) = values.requireConfirmTap ?: ModeOptionDefaults.requireConfirmTapFor(SessionMode.MULTIPLE_CHOICE)
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(requireConfirmTap = value)
}

object MatchingShowCorrectOption : SwitchModeOption(R.string.matching_show_correct, R.string.matching_show_correct_desc, setOf(SessionMode.MATCHING)) {
    override fun valueIn(values: ModeDefaultSettings) = values.matchingShowCorrectDialog ?: ModeOptionDefaults.MATCHING_SHOW_CORRECT_DIALOG
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(matchingShowCorrectDialog = value)
}

object ListRemoveGuessedOption : SwitchModeOption(R.string.list_remove_guessed, R.string.list_remove_guessed_desc, setOf(SessionMode.LIST)) {
    override fun valueIn(values: ModeDefaultSettings) = values.listRemoveGuessed ?: ModeOptionDefaults.LIST_REMOVE_GUESSED
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(listRemoveGuessed = value)
}

object CrosswordHighlightWordOption : SwitchModeOption(R.string.crossword_highlight_word, R.string.crossword_highlight_word_desc, setOf(SessionMode.CROSSWORD)) {
    override fun valueIn(values: ModeDefaultSettings) = values.crosswordHighlightWord ?: ModeOptionDefaults.CROSSWORD_HIGHLIGHT_WORD
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(crosswordHighlightWord = value)
}

object CrosswordAutoAdvanceCellOption : SwitchModeOption(R.string.crossword_auto_advance, R.string.crossword_auto_advance_desc, setOf(SessionMode.CROSSWORD)) {
    override fun valueIn(values: ModeDefaultSettings) = values.crosswordAutoAdvanceCell ?: ModeOptionDefaults.CROSSWORD_AUTO_ADVANCE_CELL
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(crosswordAutoAdvanceCell = value)
}

object CrosswordCompactCluesOption : SwitchModeOption(R.string.crossword_compact_clues, R.string.crossword_compact_clues_desc, setOf(SessionMode.CROSSWORD)) {
    override fun valueIn(values: ModeDefaultSettings) = values.crosswordCompactClues ?: ModeOptionDefaults.CROSSWORD_COMPACT_CLUES
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(crosswordCompactClues = value)
}

object CrosswordFeedbackOption : ModeOption(R.string.crossword_feedback, setOf(SessionMode.CROSSWORD)) {
    fun valueIn(values: ModeDefaultSettings): String = values.crosswordFeedbackMode ?: ModeOptionDefaults.CROSSWORD_FEEDBACK_MODE

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            TypedChipRow(
                items = listOf("LETTER", "WORD"),
                selected = valueIn(values),
                labelFor = { if (it == "LETTER") getText(R.string.feedback_letter) else getText(R.string.feedback_word) },
                onSelected = { onChange(values.copy(crosswordFeedbackMode = it)) }
            )
        }
    }
}

object SpeakingFrontSpeedOption : ModeOption(R.string.speaking_front_speed, setOf(SessionMode.SPOKEN_LISTEN)) {
    fun valueIn(values: ModeDefaultSettings): Float = values.speakingFrontSpeed ?: ModeOptionDefaults.SPEAKING_FRONT_SPEED

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SpeedChips(labelRes, valueIn(values)) { onChange(values.copy(speakingFrontSpeed = it)) }
    }
}

object SpeakingBackSpeedOption : ModeOption(R.string.speaking_back_speed, setOf(SessionMode.SPOKEN_LISTEN)) {
    fun valueIn(values: ModeDefaultSettings): Float = values.speakingBackSpeed ?: ModeOptionDefaults.SPEAKING_BACK_SPEED

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SpeedChips(labelRes, valueIn(values)) { onChange(values.copy(speakingBackSpeed = it)) }
    }
}

object AutoListenOption : SwitchModeOption(R.string.auto_listen, R.string.auto_listen_desc, setOf(SessionMode.SPOKEN_LISTEN)) {
    override fun valueIn(values: ModeDefaultSettings) = values.autoListen ?: ModeOptionDefaults.AUTO_LISTEN
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(autoListen = value)
}

/** A row of speed chips for one side of a card, shared by the speaking speed options. */
@Composable
private fun SpeedChips(labelRes: Int, selected: Float, onSelected: (Float) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
        Text(getText(labelRes))
        TypedChipRow(
            items = AUDIO_PLAYBACK_SPEEDS,
            selected = selected,
            labelFor = { if (it % 1f == 0f) "${it.toInt()}×" else "$it×" },
            onSelected = onSelected
        )
    }
}

object AudioAutoAdvanceOption : SwitchModeOption(R.string.audio_auto_advance, R.string.audio_auto_advance_desc, setOf(SessionMode.AUDIO)) {
    override fun valueIn(values: ModeDefaultSettings) = values.audioAutoAdvance ?: ModeOptionDefaults.AUDIO_AUTO_ADVANCE
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(audioAutoAdvance = value)
}

/** Every mode option, in display order: inline options first, then the ones with their own section. */
val modeOptions: List<ModeOption> = listOf(
    NumberOfAnswersOption,
    ShowCorrectLettersOption,
    FingersAndToesOption,
    MaxMemoryTilesOption,
    GridDensityOption,
    FreeformLayoutOption,
    FreeformShowBothSidesOption,
    FreeformSwipeNavigationOption,
    TypingIgnoreFormattingOption,
    TypingAutoAdvanceOption,
    TypingDisableAutocorrectOption,
    TypingShowLengthHintOption,
    AnagramFirstLetterHintOption,
    AnagramUppercaseOption,
    AnagramColorVowelsOption,
    HangmanMaxMistakesOption,
    HangmanRevealSpeedOption,
    HangmanHideVisualOption,
    ListRequireConfirmOption,
    ListResetPositionOption,
    ListDimWrongOption,
    AutoAdvanceAfterCorrectOption,
    SpeakingFrontSpeedOption,
    SpeakingBackSpeedOption,
    AutoListenOption,
    AutoAdvanceDelayOption,
    MatchingWrongDelayOption,
    MatchingHighlightOption,
    ChoiceRequireConfirmOption,
    MatchingShowCorrectOption,
    ListRemoveGuessedOption,
    CrosswordHighlightWordOption,
    CrosswordAutoAdvanceCellOption,
    CrosswordCompactCluesOption,
    CrosswordFeedbackOption,
    MemoryFlipAnimationOption,
    MemoryGrayMatchedOption,
    MemoryPeekOption,
    MemoryWrongPairOption,
    WordSearchHideFoundOption,
    WordSearchHighlightColorOption,
    FlashcardAutoFlipOption,
    FlashcardDoubleTapOption,
    FlashcardRandomizeSideOption,
    AudioPlaybackSpeedOption,
    AudioReplayCountOption,
    AudioAnswerDelayOption,
    AudioNextCardDelayOption,
    AudioAutoAdvanceOption,
    PromptSideOption,
    DifficultyWeightingOption
)

/** Saves a [ModeDefaultSettings] through rememberSaveable (session dialog state survives rotation). */
val ModeDefaultSettingsSaver = androidx.compose.runtime.saveable.Saver<ModeDefaultSettings, String>(
    save = { it.toJson().toString() },
    restore = { ModeDefaultSettings.fromJson(org.json.JSONObject(it)) }
)

/** The options that render inside the dialog's Mode Settings section for [mode]. */
@Composable
fun ModeOptionsInline(
    mode: SessionMode,
    values: ModeDefaultSettings,
    context: ModeOptionContext,
    onChange: (ModeDefaultSettings) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedContent(
        targetState = mode,
        transitionSpec = {
            fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                fadeOut(animationSpec = tween(90)) using SizeTransform(clip = false)
        },
        label = "modeSettingsAnim"
    ) { targetMode ->
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
            modeOptions.filter { !it.dialogSection && it.appliesTo(targetMode) }.forEach { option ->
                option.Control(values, context, onChange)
            }
        }
    }
}

/** The options that render as their own collapsible dialog sections for [mode]. */
@Composable
fun ModeOptionDialogSections(
    mode: SessionMode,
    values: ModeDefaultSettings,
    context: ModeOptionContext,
    onChange: (ModeDefaultSettings) -> Unit,
    expandedIds: SnapshotStateList<String>
) {
    // Guided (FSRS) picks its own cards, so difficulty weighting isn't offered there.
    modeOptions.filter { it.dialogSection && it.appliesTo(mode) && !(it is DifficultyWeightingOption && context.category == StudyCategory.GUIDED) }.forEach { option ->
        val expanded = option.id in expandedIds
        DialogSection(
            title = getText(option.labelRes),
            subtitle = option.summary(values, context).orEmpty(),
            isExpanded = expanded,
            onToggle = { if (expanded) expandedIds.remove(option.id) else expandedIds.add(option.id) }
        ) {
            option.Control(values, context, onChange)
        }
    }
}

/** Screens whose top bar gets the in-session settings button while a session is running. */
val sessionOptionScreens: Set<ShortcutScreen> = setOf(
    ShortcutScreen.FLASHCARD, ShortcutScreen.TYPING_SCORED, ShortcutScreen.LIST_QUIZ, ShortcutScreen.MULTIPLE_CHOICE,
    ShortcutScreen.MATCHING, ShortcutScreen.MEMORY, ShortcutScreen.TYPING, ShortcutScreen.ANAGRAM,
    ShortcutScreen.HANGMAN, ShortcutScreen.CROSSWORD, ShortcutScreen.WORD_SEARCH, ShortcutScreen.FREEFORM,
    ShortcutScreen.AUDIO, ShortcutScreen.TYPED_LISTEN, ShortcutScreen.SPOKEN_LISTEN
)

/** Top-bar settings button for a running session; opens [SessionOptionsDialog] for the session's mode. */
@Composable
fun SessionOptionsAction(viewModel: FlashcardViewModel, screenId: ShortcutScreen) {
    val state = viewModel.studyState
    if (state == null || screenId !in sessionOptionScreens) return
    var showDialog by remember { mutableStateOf(false) }
    TooltipIconButton(
        description = getText(R.string.session_options),
        onClick = { showDialog = true },
        modifier = Modifier.withShortcut(Key.O, "O", id = "session.options") { showDialog = true }
    ) {
        Icon(Icons.Default.Settings, getText(R.string.session_options))
    }
    if (showDialog) {
        SessionOptionsDialog(
            mode = state.studyMode,
            values = state.sessionOptions(),
            onChange = { viewModel.updateSessionOptions(it) },
            onDismiss = { showDialog = false }
        )
    }
}



/** The mode's options that can change in a running session. Start-only options are left out. */
@Composable
fun SessionOptionsContent(mode: SessionMode, values: ModeDefaultSettings, onChange: (ModeDefaultSettings) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    val context = ModeOptionContext(maxForDifficulty = { 0 })
    val expandedIds = remember { mutableStateListOf<String>() }
    val options = modeOptions.filter { it.appliesTo(mode) && it.sessionEdit == SessionEdit.LIVE }

    Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingMedium)) {
        options.filter { !it.dialogSection }.forEach { option ->
            option.Control(values, context, onChange)
        }
        options.filter { it.dialogSection }.forEach { option ->
            val expanded = option.id in expandedIds
            DialogSection(
                title = getText(option.labelRes),
                subtitle = option.summary(values, context).orEmpty(),
                isExpanded = expanded,
                onToggle = { if (expanded) expandedIds.remove(option.id) else expandedIds.add(option.id) }
            ) {
                option.Control(values, context, onChange)
            }
        }
    }
}

@Composable
fun SessionOptionsDialog(
    mode: SessionMode,
    values: ModeDefaultSettings,
    onChange: (ModeDefaultSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier.padding(dimensions.paddingLarge).verticalScroll(rememberScrollState())
            ) {
                Text(getText(R.string.session_options), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingMedium))
                SessionOptionsContent(mode, values, onChange)
                Spacer(Modifier.height(dimensions.spacingLarge))
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.done)) }
            }
        }
    }
}

