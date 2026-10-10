package net.ericclark.studiare.screens.UI_Components

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
import net.ericclark.studiare.screens.Dialogs.CreateStudySessionDialog

/*
 * Mode options: every user-facing option a session mode can have, declared once here and rendered by
 * both the session creation dialog (CreateStudySessionDialog) and Settings → Mode Defaults.
 *
 * To add an option:
 *  1. Add its field to ModeDefaultSettings (data/Values.kt) — that's where storage and JSON live.
 *  2. Add an object below (a SwitchModeOption subclass for a plain on/off, or a ModeOption subclass
 *     for anything else) with its label, the modes it applies to, its fallback, and its control.
 *  3. List it under its study modes in the `_modes:` key of mode-options.yaml. Both screens pick it up from there.
 */

/** What a control needs beyond its own value. */
class ModeOptionContext(
    /** Upper bound for a difficulty's count stepper (the session dialog passes what its filters leave). */
    val maxForDifficulty: (Int) -> Int,
    /** Total cards the current filters leave; only used for summaries in the session dialog. */
    val availableCardsCount: Int = 0,
    /** Settings only: applies this value to every mode. Null where that doesn't make sense (the dialog). */
    val onApplyToAll: ((ModeDefaultSettings) -> Unit)? = null,
    /**
     * The category being configured — some defaults differ by category (e.g. Typing's length hint),
     * and the `_modes` registry itself is keyed by (category, mode) pair. In a running session this
     * is derived from [StudyState.currentCategory] rather than stored directly.
     */
    val category: StudyCategory,
    /** The mode being configured — lets a shared option (e.g. "require confirm tap") resolve a
     *  per-(category, mode) default override without every option needing this, only the few that do. */
    val mode: SessionMode
)

/**
 * Whether an option can be changed from a running session's settings button.
 * LIVE: takes effect immediately or on the next card. START_ONLY: the session builds what it needs at
 * start (answer choices, memory board, crossword grid, difficulty mix), so it isn't offered mid-session.
 */
enum class SessionEdit { LIVE, START_ONLY }

abstract class ModeOption(
    val labelRes: Int,
    /** In the session dialog, shown as its own collapsible section instead of inside Mode Settings. */
    val dialogSection: Boolean = false,
    val sessionEdit: SessionEdit = SessionEdit.LIVE
) {
    val id: String get() = this::class.java.simpleName

    /** Whether this option is listed for [mode] under [category] in the mode-options YAML (`_modes`). */
    fun appliesTo(category: StudyCategory, mode: SessionMode): Boolean = id in ModeOptionLayout.optionIdsFor(category, mode)

    /** Subtitle for the dialog section while collapsed. */
    @Composable
    open fun summary(values: ModeDefaultSettings, context: ModeOptionContext): String? = null

    /**
     * Whether this option should be shown at all, given the other current option values — e.g. an
     * auto-advance delay stepper only makes sense while its paired auto-advance switch is on.
     * Defaults to always visible.
     */
    open fun isVisible(values: ModeDefaultSettings): Boolean = true

    /** The control itself. [values] are the current settings; [onChange] receives the full new set. */
    @Composable
    abstract fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit)
}

/** A plain on/off option: a label, a description, and a switch. */
abstract class SwitchModeOption(
    labelRes: Int,
    private val descriptionRes: Int,
    sessionEdit: SessionEdit = SessionEdit.LIVE
) : ModeOption(labelRes, sessionEdit = sessionEdit) {
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

object NumberOfAnswers : ModeOption(R.string.answers, sessionEdit = SessionEdit.START_ONLY) {
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
                canDecrease = answers > ModeOptionDefaults.NUMBER_OF_ANSWERS_MIN,
                onDecrease = { onChange(values.copy(numberOfAnswers = (answers - ModeOptionDefaults.NUMBER_OF_ANSWERS_STEP).coerceAtLeast(ModeOptionDefaults.NUMBER_OF_ANSWERS_MIN))) },
                canIncrease = answers < ModeOptionDefaults.NUMBER_OF_ANSWERS_MAX,
                onIncrease = { onChange(values.copy(numberOfAnswers = (answers + ModeOptionDefaults.NUMBER_OF_ANSWERS_STEP).coerceAtMost(ModeOptionDefaults.NUMBER_OF_ANSWERS_MAX))) },
                decreaseDescription = getText(R.string.less), increaseDescription = getText(R.string.more)
            )
        }
    }
}

/**
 * Green/red outlines on letters as they're typed. Typing's Practice shows them by default and Quiz doesn't;
 * Anagram shows them. Typing's Learn always shows them, so it isn't offered there.
 */
object ShowCorrectLetters : ModeOption(R.string.show_correct_letters) {
    fun valueIn(values: ModeDefaultSettings, category: StudyCategory?): Boolean =
        values.showCorrectLetters ?: (category != StudyCategory.QUIZ)

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        ModeSwitchRow(getText(labelRes), getText(R.string.typing_show_correct_letters_desc), valueIn(values, context.category)) {
            onChange(values.copy(showCorrectLetters = it))
        }
    }
}

object MaxMemoryTiles : ModeOption(R.string.memory_tiles, sessionEdit = SessionEdit.START_ONLY) {
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
                canDecrease = tiles > ModeOptionDefaults.MAX_MEMORY_TILES_MIN,
                onDecrease = { onChange(values.copy(maxMemoryTiles = (tiles - ModeOptionDefaults.MAX_MEMORY_TILES_STEP).coerceAtLeast(ModeOptionDefaults.MAX_MEMORY_TILES_MIN))) },
                canIncrease = tiles < ModeOptionDefaults.MAX_MEMORY_TILES_MAX,
                onIncrease = { onChange(values.copy(maxMemoryTiles = (tiles + ModeOptionDefaults.MAX_MEMORY_TILES_STEP).coerceAtMost(ModeOptionDefaults.MAX_MEMORY_TILES_MAX))) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

object GridDensity : ModeOption(R.string.grid_density, sessionEdit = SessionEdit.START_ONLY) {
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

object ShowCorrectWords : SwitchModeOption(R.string.show_correct_words, R.string.show_correct_words_desc, sessionEdit = SessionEdit.START_ONLY) {
    override fun valueIn(values: ModeDefaultSettings) = values.showCorrectWords ?: ModeOptionDefaults.SHOW_CORRECT_WORDS
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(showCorrectWords = value)
}

object FreeformLayout : SwitchModeOption(R.string.vertical_layout, R.string.vertical_layout_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.freeformLayoutVertical ?: ModeOptionDefaults.FREEFORM_LAYOUT_VERTICAL
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(freeformLayoutVertical = value)
}

/** Shown in every mode; its own section in the session dialog. Front/back segmented choice. */
object PromptSide : ModeOption(R.string.prompt_side) {
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
object DifficultyWeighting : ModeOption(R.string.difficulty_weighting, dialogSection = true, sessionEdit = SessionEdit.START_ONLY) {
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

object AudioPlaybackSpeed : ModeOption(R.string.audio_playback_speed) {
    fun valueIn(values: ModeDefaultSettings): Float = values.audioPlaybackSpeed ?: ModeOptionDefaults.AUDIO_PLAYBACK_SPEED

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SpeedStepper(
            labelRes, valueIn(values),
            min = ModeOptionDefaults.AUDIO_PLAYBACK_SPEED_MIN, max = ModeOptionDefaults.AUDIO_PLAYBACK_SPEED_MAX,
            step = ModeOptionDefaults.AUDIO_PLAYBACK_SPEED_STEP
        ) { onChange(values.copy(audioPlaybackSpeed = it)) }
    }
}

object AudioReplayCount : ModeOption(R.string.audio_replay_count) {
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
                canDecrease = plays > ModeOptionDefaults.AUDIO_REPLAY_COUNT_MIN,
                onDecrease = { onChange(values.copy(audioReplayCount = (plays - ModeOptionDefaults.AUDIO_REPLAY_COUNT_STEP).coerceAtLeast(ModeOptionDefaults.AUDIO_REPLAY_COUNT_MIN))) },
                canIncrease = plays < ModeOptionDefaults.AUDIO_REPLAY_COUNT_MAX,
                onIncrease = { onChange(values.copy(audioReplayCount = (plays + ModeOptionDefaults.AUDIO_REPLAY_COUNT_STEP).coerceAtMost(ModeOptionDefaults.AUDIO_REPLAY_COUNT_MAX))) },
                decreaseDescription = getText(R.string.less), increaseDescription = getText(R.string.more)
            )
        }
    }
}

/** Seconds stepper; the range and step come from the mode-options YAML via the caller. */
@Composable
private fun SecondsStepper(label: String, seconds: Double, min: Double, max: Double, step: Double, onChange: (Double) -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)
    ) {
        Text(label)
        ValueStepper(
            valueText = stringResource(R.string.time_seconds_format, seconds),
            canDecrease = seconds > min, onDecrease = { onChange((seconds - step).coerceAtLeast(min)) },
            canIncrease = seconds < max, onIncrease = { onChange((seconds + step).coerceAtMost(max)) },
            decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
        )
    }
}

object AudioAnswerDelay : ModeOption(R.string.answer_delay) {
    fun valueIn(values: ModeDefaultSettings): Double = values.audioAnswerDelaySeconds ?: ModeOptionDefaults.AUDIO_ANSWER_DELAY_SECONDS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SecondsStepper(
            getText(labelRes), valueIn(values),
            min = ModeOptionDefaults.AUDIO_ANSWER_DELAY_SECONDS_MIN, max = ModeOptionDefaults.AUDIO_ANSWER_DELAY_SECONDS_MAX,
            step = ModeOptionDefaults.AUDIO_ANSWER_DELAY_SECONDS_STEP
        ) { onChange(values.copy(audioAnswerDelaySeconds = it)) }
    }
}

object AudioNextCardDelay : ModeOption(R.string.next_card_delay) {
    fun valueIn(values: ModeDefaultSettings): Double = values.audioNextCardDelaySeconds ?: ModeOptionDefaults.AUDIO_NEXT_CARD_DELAY_SECONDS
    override fun isVisible(values: ModeDefaultSettings) = values.audioAutoAdvance ?: ModeOptionDefaults.AUDIO_AUTO_ADVANCE

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SecondsStepper(
            getText(labelRes), valueIn(values),
            min = ModeOptionDefaults.AUDIO_NEXT_CARD_DELAY_SECONDS_MIN, max = ModeOptionDefaults.AUDIO_NEXT_CARD_DELAY_SECONDS_MAX,
            step = ModeOptionDefaults.AUDIO_NEXT_CARD_DELAY_SECONDS_STEP
        ) { onChange(values.copy(audioNextCardDelaySeconds = it)) }
    }
}

object FreeformShowBothSides : SwitchModeOption(R.string.freeform_show_both_sides, R.string.freeform_show_both_sides_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.freeformShowBothSides ?: ModeOptionDefaults.FREEFORM_SHOW_BOTH_SIDES
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(freeformShowBothSides = value)
}

object FreeformSwipeNavigation : SwitchModeOption(R.string.freeform_swipe_navigation, R.string.freeform_swipe_navigation_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.freeformSwipeNavigation ?: ModeOptionDefaults.FREEFORM_SWIPE_NAVIGATION
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(freeformSwipeNavigation = value)
}

object TypingIgnoreFormatting : SwitchModeOption(R.string.typing_ignore_formatting, R.string.typing_ignore_formatting_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.typingIgnoreFormatting ?: ModeOptionDefaults.TYPING_IGNORE_FORMATTING
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(typingIgnoreFormatting = value)
}

object TypingDisableAutocorrect : SwitchModeOption(R.string.typing_disable_autocorrect, R.string.typing_disable_autocorrect_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.typingDisableAutocorrect ?: ModeOptionDefaults.TYPING_DISABLE_AUTOCORRECT
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(typingDisableAutocorrect = value)
}

/** Scored typing only: Learn always shows a box for every letter. Practice shows the length by default, Quiz doesn't. */
object TypingShowLengthHint : ModeOption(R.string.typing_show_length_hint) {
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
object FlashcardAutoFlip : ModeOption(R.string.flashcard_auto_flip) {
    fun valueIn(values: ModeDefaultSettings): Boolean = values.flashcardAutoFlip ?: ModeOptionDefaults.FLASHCARD_AUTO_FLIP
    private fun secondsIn(values: ModeDefaultSettings): Int = values.flashcardAutoFlipSeconds ?: ModeOptionDefaults.FLASHCARD_AUTO_FLIP_SECONDS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val dimensions = LocalStudiareDimensions.current
        val enabled = valueIn(values)
        val seconds = secondsIn(values)
        val min = ModeOptionDefaults.FLASHCARD_AUTO_FLIP_SECONDS_MIN
        val max = ModeOptionDefaults.FLASHCARD_AUTO_FLIP_SECONDS_MAX
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().clickable { onChange(values.copy(flashcardAutoFlip = !enabled)) }
                    .padding(vertical = dimensions.paddingSmall)
            ) {
                Text(getText(labelRes), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(
                    checked = enabled,
                    onCheckedChange = { on -> onChange(values.copy(flashcardAutoFlip = on)) }
                )
            }
            if (enabled) {
                ValueStepper(
                    valueText = "${seconds}s",
                    canDecrease = seconds > min, onDecrease = { onChange(values.copy(flashcardAutoFlipSeconds = (seconds - ModeOptionDefaults.FLASHCARD_AUTO_FLIP_SECONDS_STEP).coerceAtLeast(min))) },
                    canIncrease = seconds < max, onIncrease = { onChange(values.copy(flashcardAutoFlipSeconds = (seconds + ModeOptionDefaults.FLASHCARD_AUTO_FLIP_SECONDS_STEP).coerceAtMost(max))) },
                    decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
                )
            }
        }
    }
}

object FlashcardDoubleTap : SwitchModeOption(R.string.flashcard_double_tap, R.string.flashcard_double_tap_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.flashcardDoubleTapToFlip ?: ModeOptionDefaults.FLASHCARD_DOUBLE_TAP_TO_FLIP
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(flashcardDoubleTapToFlip = value)
}

object FlashcardRandomizeSide : SwitchModeOption(R.string.flashcard_randomize_side, R.string.flashcard_randomize_side_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.flashcardRandomizeFirstSide ?: ModeOptionDefaults.FLASHCARD_RANDOMIZE_FIRST_SIDE
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(flashcardRandomizeFirstSide = value)
}

object AnagramFirstLetterHint : SwitchModeOption(R.string.anagram_first_letter_hint, R.string.anagram_first_letter_hint_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.anagramFirstLetterHint ?: ModeOptionDefaults.ANAGRAM_FIRST_LETTER_HINT
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(anagramFirstLetterHint = value)
}

object AnagramUppercase : SwitchModeOption(R.string.anagram_uppercase, R.string.anagram_uppercase_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.anagramUppercase ?: ModeOptionDefaults.ANAGRAM_UPPERCASE
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(anagramUppercase = value)
}

object AnagramColorVowels : SwitchModeOption(R.string.anagram_color_vowels, R.string.anagram_color_vowels_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.anagramColorVowels ?: ModeOptionDefaults.ANAGRAM_COLOR_VOWELS
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(anagramColorVowels = value)
}

object HangmanMaxMistakes : ModeOption(R.string.hangman_max_mistakes, sessionEdit = SessionEdit.START_ONLY) {
    fun valueIn(values: ModeDefaultSettings): Int = values.hangmanMaxMistakes ?: ModeOptionDefaults.HANGMAN_MAX_MISTAKES

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val guesses = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = guesses.toString(),
                canDecrease = guesses > ModeOptionDefaults.HANGMAN_MAX_MISTAKES_MIN,
                onDecrease = { onChange(values.copy(hangmanMaxMistakes = (guesses - ModeOptionDefaults.HANGMAN_MAX_MISTAKES_STEP).coerceAtLeast(ModeOptionDefaults.HANGMAN_MAX_MISTAKES_MIN))) },
                canIncrease = guesses < ModeOptionDefaults.HANGMAN_MAX_MISTAKES_MAX,
                onIncrease = { onChange(values.copy(hangmanMaxMistakes = (guesses + ModeOptionDefaults.HANGMAN_MAX_MISTAKES_STEP).coerceAtMost(ModeOptionDefaults.HANGMAN_MAX_MISTAKES_MAX))) },
                decreaseDescription = getText(R.string.less), increaseDescription = getText(R.string.more)
            )
        }
    }
}

object HangmanHideVisual : SwitchModeOption(R.string.hangman_hide_visual, R.string.hangman_hide_visual_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.hangmanHideVisual ?: ModeOptionDefaults.HANGMAN_HIDE_VISUAL
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(hangmanHideVisual = value)
}

object MemoryGrayMatched : SwitchModeOption(R.string.memory_gray_matched, R.string.memory_gray_matched_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.memoryGrayMatched ?: ModeOptionDefaults.MEMORY_GRAY_MATCHED
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(memoryGrayMatched = value)
}

object MemoryPeek : ModeOption(R.string.memory_peek, sessionEdit = SessionEdit.START_ONLY) {
    fun valueIn(values: ModeDefaultSettings): Int = values.memoryPeekSeconds ?: ModeOptionDefaults.MEMORY_PEEK_SECONDS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val seconds = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = if (seconds == 0) getText(R.string.flashcard_auto_flip_off) else "${seconds}s",
                canDecrease = seconds > ModeOptionDefaults.MEMORY_PEEK_SECONDS_MIN,
                onDecrease = { onChange(values.copy(memoryPeekSeconds = (seconds - ModeOptionDefaults.MEMORY_PEEK_SECONDS_STEP).coerceAtLeast(ModeOptionDefaults.MEMORY_PEEK_SECONDS_MIN))) },
                canIncrease = seconds < ModeOptionDefaults.MEMORY_PEEK_SECONDS_MAX,
                onIncrease = { onChange(values.copy(memoryPeekSeconds = (seconds + ModeOptionDefaults.MEMORY_PEEK_SECONDS_STEP).coerceAtMost(ModeOptionDefaults.MEMORY_PEEK_SECONDS_MAX))) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

object MemoryWrongPair : ModeOption(R.string.memory_wrong_pair) {
    fun valueIn(values: ModeDefaultSettings): Int = values.memoryWrongPairMs ?: ModeOptionDefaults.MEMORY_WRONG_PAIR_MS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val ms = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = if (ms == 0) getText(R.string.flashcard_auto_flip_off) else "${ms} ms",
                canDecrease = ms > ModeOptionDefaults.MEMORY_WRONG_PAIR_MS_MIN,
                onDecrease = { onChange(values.copy(memoryWrongPairMs = (ms - ModeOptionDefaults.MEMORY_WRONG_PAIR_MS_STEP).coerceAtLeast(ModeOptionDefaults.MEMORY_WRONG_PAIR_MS_MIN))) },
                canIncrease = ms < ModeOptionDefaults.MEMORY_WRONG_PAIR_MS_MAX,
                onIncrease = { onChange(values.copy(memoryWrongPairMs = (ms + ModeOptionDefaults.MEMORY_WRONG_PAIR_MS_STEP).coerceAtMost(ModeOptionDefaults.MEMORY_WRONG_PAIR_MS_MAX))) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

// Plumbing only: field and default exist, but no auto-close-correct behavior is wired into
// MemoryMode.kt yet (see the roadmap plan for the remaining game-logic work).
object MemoryCorrectPair : ModeOption(R.string.memory_correct_pair) {
    fun valueIn(values: ModeDefaultSettings): Int = values.memoryCorrectPairMs ?: ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val ms = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = if (ms == 0) getText(R.string.flashcard_auto_flip_off) else "${ms} ms",
                canDecrease = ms > ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS_MIN,
                onDecrease = { onChange(values.copy(memoryCorrectPairMs = (ms - ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS_STEP).coerceAtLeast(ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS_MIN))) },
                canIncrease = ms < ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS_MAX,
                onIncrease = { onChange(values.copy(memoryCorrectPairMs = (ms + ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS_STEP).coerceAtMost(ModeOptionDefaults.MEMORY_CORRECT_PAIR_MS_MAX))) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

// Plumbing only: field and default exist, but no submit-to-confirm behavior is wired into
// MemoryMode.kt yet (see the roadmap plan for the remaining game-logic work).
object MemorySubmitAnswer : SwitchModeOption(R.string.memory_submit_answer, R.string.memory_submit_answer_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.memorySubmitAnswer ?: ModeOptionDefaults.MEMORY_SUBMIT_ANSWER
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(memorySubmitAnswer = value)
}

/** Colours offered for found-word lines in Word Search. The first is the default green. */
val WORD_SEARCH_HIGHLIGHT_COLORS: List<Int> = ModeOptionDefaults.WORD_SEARCH_HIGHLIGHT_COLORS

object WordSearchHideFound : SwitchModeOption(R.string.word_search_hide_found, R.string.word_search_hide_found_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.wordSearchHideFound ?: ModeOptionDefaults.WORD_SEARCH_HIDE_FOUND
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(wordSearchHideFound = value)
}

object WordSearchHighlightColor : ModeOption(R.string.word_search_highlight_color) {
    fun valueIn(values: ModeDefaultSettings): Int = values.wordSearchHighlightColor ?: ModeOptionDefaults.WORD_SEARCH_HIGHLIGHT_COLOR

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

/**
 * Shared by List, Multiple Choice, and Matching — the only option whose default genuinely varies by
 * (category, mode) today, which is why this isn't a plain [SwitchModeOption]: it needs
 * [ModeOptionContext.mode] to resolve that default, not just [values]. Used to be three separate,
 * identical classes (one per mode) before being merged into one, the same way [PromptSide] and
 * [DifficultyWeighting] are already shared across every mode that lists them.
 */
object RequireConfirmTap : ModeOption(R.string.require_confirm_tap) {
    fun valueIn(values: ModeDefaultSettings, context: ModeOptionContext) =
        values.requireConfirmTap ?: ModeOptionDefaults.requireConfirmTapFor(context.category, context.mode)

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        ModeSwitchRow(getText(labelRes), getText(R.string.require_confirm_tap_desc), valueIn(values, context)) {
            onChange(values.copy(requireConfirmTap = it))
        }
    }
}

object ListResetPosition : SwitchModeOption(R.string.list_reset_position, R.string.list_reset_position_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.listResetPosition ?: ModeOptionDefaults.LIST_RESET_POSITION
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(listResetPosition = value)
}

object ListDimWrong : SwitchModeOption(R.string.list_dim_wrong, R.string.list_dim_wrong_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.listDimWrongGuesses ?: ModeOptionDefaults.LIST_DIM_WRONG_GUESSES
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(listDimWrongGuesses = value)
}

object AutoAdvance : SwitchModeOption(R.string.auto_advance, R.string.auto_advance_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.autoAdvance ?: ModeOptionDefaults.AUTO_ADVANCE
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(autoAdvance = value)
}

object AutoAdvanceDelay : ModeOption(R.string.auto_advance_delay) {
    fun valueIn(values: ModeDefaultSettings): Double = values.autoAdvanceDelaySeconds ?: ModeOptionDefaults.AUTO_ADVANCE_DELAY_SECONDS
    override fun isVisible(values: ModeDefaultSettings) = values.autoAdvance ?: ModeOptionDefaults.AUTO_ADVANCE

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SecondsStepper(
            getText(labelRes), valueIn(values),
            min = ModeOptionDefaults.AUTO_ADVANCE_DELAY_SECONDS_MIN, max = ModeOptionDefaults.AUTO_ADVANCE_DELAY_SECONDS_MAX,
            step = ModeOptionDefaults.AUTO_ADVANCE_DELAY_SECONDS_STEP
        ) { onChange(values.copy(autoAdvanceDelaySeconds = it)) }
    }
}

object MatchingWrongDelay : ModeOption(R.string.matching_wrong_delay) {
    fun valueIn(values: ModeDefaultSettings): Int = values.matchingWrongDelayMs ?: ModeOptionDefaults.MATCHING_WRONG_DELAY_MS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val ms = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = "${ms} ms",
                canDecrease = ms > ModeOptionDefaults.MATCHING_WRONG_DELAY_MS_MIN,
                onDecrease = { onChange(values.copy(matchingWrongDelayMs = (ms - ModeOptionDefaults.MATCHING_WRONG_DELAY_MS_STEP).coerceAtLeast(ModeOptionDefaults.MATCHING_WRONG_DELAY_MS_MIN))) },
                canIncrease = ms < ModeOptionDefaults.MATCHING_WRONG_DELAY_MS_MAX,
                onIncrease = { onChange(values.copy(matchingWrongDelayMs = (ms + ModeOptionDefaults.MATCHING_WRONG_DELAY_MS_STEP).coerceAtMost(ModeOptionDefaults.MATCHING_WRONG_DELAY_MS_MAX))) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

/** Matching: how long a correct match stays highlighted before it fades out (0 = fades straight away). */
object MatchingCorrectHighlight : ModeOption(R.string.matching_correct_highlight) {
    fun valueIn(values: ModeDefaultSettings): Int = values.matchingCorrectHighlightMs ?: ModeOptionDefaults.MATCHING_CORRECT_HIGHLIGHT_MS

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        val ms = valueIn(values)
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
            Text(getText(labelRes))
            ValueStepper(
                valueText = "${ms} ms",
                canDecrease = ms > ModeOptionDefaults.MATCHING_CORRECT_HIGHLIGHT_MS_MIN,
                onDecrease = { onChange(values.copy(matchingCorrectHighlightMs = (ms - ModeOptionDefaults.MATCHING_CORRECT_HIGHLIGHT_MS_STEP).coerceAtLeast(ModeOptionDefaults.MATCHING_CORRECT_HIGHLIGHT_MS_MIN))) },
                canIncrease = ms < ModeOptionDefaults.MATCHING_CORRECT_HIGHLIGHT_MS_MAX,
                onIncrease = { onChange(values.copy(matchingCorrectHighlightMs = (ms + ModeOptionDefaults.MATCHING_CORRECT_HIGHLIGHT_MS_STEP).coerceAtMost(ModeOptionDefaults.MATCHING_CORRECT_HIGHLIGHT_MS_MAX))) },
                decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
            )
        }
    }
}

object MatchingHighlight : ModeOption(R.string.matching_highlight) {
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

/**
 * Shared by Multiple Choice and List ([MatchingAllowMultipleGuesses] is worded differently since
 * Matching reveals a pair, not an answer, and would collide with the pre-existing
 * [MatchingShowCorrect] dialog feature). Shares [RequireConfirmTap]'s reason for needing
 * [ModeOptionContext.mode]: its default ([ModeDefaultSettings.allowMultipleGuesses],
 * [StudyState.allowMultipleGuesses]) varies by (category, mode).
 */
// Renamed from ShowCorrectOnMiss — the switch now directly reflects allowMultipleGuesses (on = may
// keep guessing) instead of its inverse (on = show correct answer immediately), matching the name.
object AllowMultipleGuesses : ModeOption(R.string.allow_multiple_guesses) {
    fun valueIn(values: ModeDefaultSettings, context: ModeOptionContext) =
        values.allowMultipleGuesses ?: ModeOptionDefaults.allowMultipleGuessesFor(context.category, context.mode)

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        ModeSwitchRow(getText(labelRes), getText(R.string.allow_multiple_guesses_desc), valueIn(values, context)) {
            onChange(values.copy(allowMultipleGuesses = it))
        }
    }
}

/**
 * Renamed from MatchingShowCorrectOnMiss — Matching's flavor of [AllowMultipleGuesses] (shares its
 * field), worded around revealing a *pair* rather than an answer, and distinct from
 * [MatchingShowCorrect] just below: that one pops up a dialog on every wrong match regardless of
 * this setting; this one decides whether a wrong match ends that pair's attempt (revealing it on
 * the board, via [StudyState.matchingRevealPair]) or lets the user keep trying it.
 */
object MatchingAllowMultipleGuesses : ModeOption(R.string.matching_allow_multiple_guesses) {
    fun valueIn(values: ModeDefaultSettings, context: ModeOptionContext) =
        values.allowMultipleGuesses ?: ModeOptionDefaults.allowMultipleGuessesFor(context.category, context.mode)

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        ModeSwitchRow(getText(labelRes), getText(R.string.matching_allow_multiple_guesses_desc), valueIn(values, context)) {
            onChange(values.copy(allowMultipleGuesses = it))
        }
    }
}

object MatchingShowCorrect : SwitchModeOption(R.string.matching_show_correct, R.string.matching_show_correct_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.matchingShowCorrectDialog ?: ModeOptionDefaults.MATCHING_SHOW_CORRECT_DIALOG
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(matchingShowCorrectDialog = value)
}

object ListRemoveGuessed : SwitchModeOption(R.string.list_remove_guessed, R.string.list_remove_guessed_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.listRemoveGuessed ?: ModeOptionDefaults.LIST_REMOVE_GUESSED
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(listRemoveGuessed = value)
}

object CrosswordHighlightWord : SwitchModeOption(R.string.crossword_highlight_word, R.string.crossword_highlight_word_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.crosswordHighlightWord ?: ModeOptionDefaults.CROSSWORD_HIGHLIGHT_WORD
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(crosswordHighlightWord = value)
}

object CrosswordAutoAdvanceCell : SwitchModeOption(R.string.crossword_auto_advance, R.string.crossword_auto_advance_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.crosswordAutoAdvanceCell ?: ModeOptionDefaults.CROSSWORD_AUTO_ADVANCE_CELL
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(crosswordAutoAdvanceCell = value)
}

object CrosswordCompactClues : SwitchModeOption(R.string.crossword_compact_clues, R.string.crossword_compact_clues_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.crosswordCompactClues ?: ModeOptionDefaults.CROSSWORD_COMPACT_CLUES
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(crosswordCompactClues = value)
}

object SpeakingFrontSpeed : ModeOption(R.string.speaking_front_speed) {
    fun valueIn(values: ModeDefaultSettings): Float = values.speakingFrontSpeed ?: ModeOptionDefaults.SPEAKING_FRONT_SPEED

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SpeedStepper(
            labelRes, valueIn(values),
            min = ModeOptionDefaults.SPEAKING_FRONT_SPEED_MIN, max = ModeOptionDefaults.SPEAKING_FRONT_SPEED_MAX,
            step = ModeOptionDefaults.SPEAKING_FRONT_SPEED_STEP
        ) { onChange(values.copy(speakingFrontSpeed = it)) }
    }
}

object SpeakingBackSpeed : ModeOption(R.string.speaking_back_speed) {
    fun valueIn(values: ModeDefaultSettings): Float = values.speakingBackSpeed ?: ModeOptionDefaults.SPEAKING_BACK_SPEED

    @Composable
    override fun Control(values: ModeDefaultSettings, context: ModeOptionContext, onChange: (ModeDefaultSettings) -> Unit) {
        SpeedStepper(
            labelRes, valueIn(values),
            min = ModeOptionDefaults.SPEAKING_BACK_SPEED_MIN, max = ModeOptionDefaults.SPEAKING_BACK_SPEED_MAX,
            step = ModeOptionDefaults.SPEAKING_BACK_SPEED_STEP
        ) { onChange(values.copy(speakingBackSpeed = it)) }
    }
}

object AutoListen : SwitchModeOption(R.string.auto_listen, R.string.auto_listen_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.autoListen ?: ModeOptionDefaults.AUTO_LISTEN
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(autoListen = value)
}

object ListenStartSound : SwitchModeOption(R.string.listen_start_sound, R.string.listen_start_sound_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.listenStartSound ?: ModeOptionDefaults.LISTEN_START_SOUND
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(listenStartSound = value)
}

object ListenCorrectSound : SwitchModeOption(R.string.listen_correct_sound, R.string.listen_correct_sound_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.listenCorrectSound ?: ModeOptionDefaults.LISTEN_CORRECT_SOUND
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(listenCorrectSound = value)
}

object ListenIncorrectSound : SwitchModeOption(R.string.listen_incorrect_sound, R.string.listen_incorrect_sound_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.listenIncorrectSound ?: ModeOptionDefaults.LISTEN_INCORRECT_SOUND
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(listenIncorrectSound = value)
}

/** Playback speed label, e.g. "1×" or "0.75×". */
private fun speedLabel(speed: Float): String = (if (speed % 1f == 0f) speed.toInt().toString() else speed.toString()) + "×"

/** Speed stepper for the audio and speaking speed options; the range and step come from the mode-options YAML via the caller. */
@Composable
private fun SpeedStepper(labelRes: Int, speed: Float, min: Float, max: Float, step: Float, onChange: (Float) -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(LocalStudiareDimensions.current.spacingSmall)) {
        Text(getText(labelRes))
        ValueStepper(
            valueText = speedLabel(speed),
            canDecrease = speed > min, onDecrease = { onChange((speed - step).coerceAtLeast(min)) },
            canIncrease = speed < max, onIncrease = { onChange((speed + step).coerceAtMost(max)) },
            decreaseDescription = getText(R.string.decrease), increaseDescription = getText(R.string.increase)
        )
    }
}

object AudioAutoAdvance : SwitchModeOption(R.string.audio_auto_advance, R.string.audio_auto_advance_desc) {
    override fun valueIn(values: ModeDefaultSettings) = values.audioAutoAdvance ?: ModeOptionDefaults.AUDIO_AUTO_ADVANCE
    override fun withValue(values: ModeDefaultSettings, value: Boolean) = values.copy(audioAutoAdvance = value)
}

/** Every mode option, in display order: inline options first, then the ones with their own section. */
internal val allModeOptions: List<ModeOption> = listOf(
    NumberOfAnswers,
    ShowCorrectLetters,
    MaxMemoryTiles,
    GridDensity,
    FreeformLayout,
    FreeformShowBothSides,
    FreeformSwipeNavigation,
    TypingIgnoreFormatting,
    TypingDisableAutocorrect,
    TypingShowLengthHint,
    AnagramFirstLetterHint,
    AnagramUppercase,
    AnagramColorVowels,
    HangmanMaxMistakes,
    HangmanHideVisual,
    RequireConfirmTap,
    AllowMultipleGuesses,
    ListResetPosition,
    ListDimWrong,
    AutoAdvance,
    SpeakingFrontSpeed,
    SpeakingBackSpeed,
    AutoListen,
    ListenStartSound,
    ListenCorrectSound,
    ListenIncorrectSound,
    AutoAdvanceDelay,
    MatchingWrongDelay,
    MatchingCorrectHighlight,
    MatchingHighlight,
    MatchingAllowMultipleGuesses,
    MatchingShowCorrect,
    ListRemoveGuessed,
    CrosswordHighlightWord,
    CrosswordAutoAdvanceCell,
    CrosswordCompactClues,
    ShowCorrectWords,
    MemoryGrayMatched,
    MemoryPeek,
    MemoryWrongPair,
    MemoryCorrectPair,
    MemorySubmitAnswer,
    WordSearchHideFound,
    WordSearchHighlightColor,
    FlashcardAutoFlip,
    FlashcardDoubleTap,
    FlashcardRandomizeSide,
    AudioPlaybackSpeed,
    AudioReplayCount,
    AudioAnswerDelay,
    AudioNextCardDelay,
    AudioAutoAdvance,
    PromptSide,
    DifficultyWeighting
)

/**
 * The options a study mode shows under [category], in display order. Which options each (category, mode)
 * pair lists, and their order, come from the `_modes:` key of mode-options.yaml.
 */
fun modeOptionsFor(category: StudyCategory, mode: SessionMode): List<ModeOption> {
    val byId = allModeOptions.associateBy { it.id }
    return ModeOptionLayout.optionIdsFor(category, mode).mapNotNull { byId[it] }
}

/** Saves a [ModeDefaultSettings] through rememberSaveable (session dialog state survives rotation). */
val ModeDefaultSettingsSaver = androidx.compose.runtime.saveable.Saver<ModeDefaultSettings, String>(
    save = { it.toJson().toString() },
    restore = { ModeDefaultSettings.fromJson(org.json.JSONObject(it)) }
)

/** The options that render inside the dialog's Mode Settings section for [mode] under [category]. */
@Composable
fun ModeOptionsInline(
    category: StudyCategory,
    mode: SessionMode,
    values: ModeDefaultSettings,
    context: ModeOptionContext,
    onChange: (ModeDefaultSettings) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedContent(
        targetState = category to mode,
        transitionSpec = {
            fadeIn(animationSpec = tween(220, delayMillis = 90)) togetherWith
                fadeOut(animationSpec = tween(90)) using SizeTransform(clip = false)
        },
        label = "modeSettingsAnim"
    ) { (targetCategory, targetMode) ->
        Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
            modeOptionsFor(targetCategory, targetMode).filter { !it.dialogSection && it.isVisible(values) }.forEach { option ->
                option.Control(values, context, onChange)
            }
        }
    }
}

/** The options that render as their own collapsible dialog sections for [mode] under [category]. */
@Composable
fun ModeOptionDialogSections(
    category: StudyCategory,
    mode: SessionMode,
    values: ModeDefaultSettings,
    context: ModeOptionContext,
    onChange: (ModeDefaultSettings) -> Unit,
    expandedIds: SnapshotStateList<String>
) {
    // Guided (FSRS) picks its own cards, so difficulty weighting isn't listed under GUIDED_* at all.
    modeOptionsFor(category, mode).filter { it.dialogSection && it.isVisible(values) }.forEach { option ->
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
            category = state.currentCategory(),
            mode = state.studyMode,
            values = state.sessionOptions(),
            onChange = { viewModel.updateSessionOptions(it) },
            onDismiss = { showDialog = false }
        )
    }
}



/** The mode's options that can change in a running session. Start-only options are left out. */
@Composable
fun SessionOptionsContent(category: StudyCategory, mode: SessionMode, values: ModeDefaultSettings, onChange: (ModeDefaultSettings) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    val context = ModeOptionContext(maxForDifficulty = { 0 }, category = category, mode = mode)
    val expandedIds = remember { mutableStateListOf<String>() }
    val options = modeOptionsFor(category, mode).filter { it.sessionEdit == SessionEdit.LIVE && it.isVisible(values) }

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
    category: StudyCategory,
    mode: SessionMode,
    values: ModeDefaultSettings,
    onChange: (ModeDefaultSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier.fillMaxHeight(0.9f),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            // Title and Done stay fixed; only the options scroll, so Done is always visible
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(getText(R.string.session_options), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingMedium))
                Column(
                    modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())
                ) {
                    SessionOptionsContent(category, mode, values, onChange)
                }
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

