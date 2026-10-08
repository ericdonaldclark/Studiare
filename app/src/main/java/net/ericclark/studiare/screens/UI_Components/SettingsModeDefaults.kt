package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.screens.Dialogs.CreateStudySessionDialog
import net.ericclark.studiare.screens.Dialogs.ModeSelectionSection

/**
 * Settings → Mode Defaults: a category chip row above a mode chip row (filtered to that category's
 * modes, via the same [modesForCategory] `CreateStudySessionDialog`'s `ModeSelectionSection` uses),
 * then a settings panel for the selected (category, mode) pair — mirroring
 * [KeyboardShortcutSettingsContent]'s "pick a chip, edit the list below it" shape. Every control
 * applies live, same as every other Settings toggle (including Keyboard remaps above).
 */
@Composable
internal fun ModeDefaultsSettingsContent(viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val modeDefaults by viewModel.modeDefaultSettings.collectAsState()

    val categories = remember { listOf(StudyCategory.LEARN, StudyCategory.PRACTICE, StudyCategory.QUIZ, StudyCategory.GAMES, StudyCategory.GUIDED) }
    var selectedCategory by rememberSaveable { mutableStateOf(StudyCategory.LEARN) }
    var selectedMode by rememberSaveable { mutableStateOf(SessionMode.AUDIO) }
    val modesInCategory = remember(selectedCategory) { modesForCategory(selectedCategory) }
    LaunchedEffect(selectedCategory) {
        if (selectedMode !in modesInCategory) selectedMode = modesInCategory.first()
    }

    val settings = modeDefaults[selectedCategory to selectedMode] ?: ModeDefaultSettings()
    // Settings has no deck to count from, so difficulty counts go up to 100 (the other steppers' ceiling).
    val context = ModeOptionContext(
        category = selectedCategory,
        mode = selectedMode,
        maxForDifficulty = { 100 },
        onApplyToAll = { values ->
            viewModel.applyDifficultyWeightingToAllModes(
                values.difficultyWeighted ?: false,
                values.difficultyCounts?.takeIf { it.size == 5 } ?: DEFAULT_DIFFICULTY_COUNTS
            )
        }
    )

    Column {
        Text(
            getText(R.string.mode_defaults_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimensions.spacingMedium)
        )

        Text(getText(R.string.category), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(dimensions.spacingSmall))
        TypedChipRow(
            items = categories,
            selected = selectedCategory,
            labelFor = { it.asString() },
            onSelected = { selectedCategory = it }
        )

        Spacer(Modifier.height(dimensions.spacingSmall))

        Text(getText(R.string.mode), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(dimensions.spacingSmall))
        TypedChipRow(
            items = modesInCategory,
            selected = selectedMode,
            labelFor = { it.asString() },
            onSelected = { selectedMode = it }
        )

        Spacer(Modifier.height(dimensions.spacingMedium))
        HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.spacingMedium))

        // Every option comes from the shared registry (screens/ModeOptions.kt), the same controls the
        // session dialog renders. Options with a dialog section get a heading here, since Settings
        // doesn't collapse them.
        modeOptionsFor(selectedCategory, selectedMode).filter { it.isVisible(settings) }.forEach { option ->
            if (option.dialogSection) {
                Text(getText(option.labelRes), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(dimensions.spacingSmall))
            } else {
                Spacer(Modifier.height(dimensions.spacingSmall))
            }
            option.Control(settings, context) { viewModel.setModeDefaultSettings(selectedCategory, selectedMode, it) }
            if (option.dialogSection) {
                Spacer(Modifier.height(dimensions.spacingMedium))
                HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.spacingMedium))
            }
        }
    }
}
