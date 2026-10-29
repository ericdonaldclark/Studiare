package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.font.FontWeight
import kotlinx.coroutines.launch
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.screens.Screens.CreateStudySessionScreen
import net.ericclark.studiare.screens.Screens.ModeSelectionSection

/**
 * Settings → Mode Defaults: a category chip row above a mode chip row (filtered to that category's
 * modes, via the same [modesForCategory] `CreateStudySessionScreen`'s `ModeSelectionSection` uses),
 * then a settings panel for the selected (category, mode) pair — mirroring
 * [KeyboardShortcutSettingsContent]'s "pick a chip, edit the list below it" shape. Every control
 * applies live, same as every other Settings toggle (including Keyboard remaps above).
 *
 * Each option that appears under more than one (category, mode) pair gets its own small inline
 * overflow menu (⋮), alongside its control rather than on a separate row, offering to copy just that
 * one value to its mode's other categories, or to every pair that has it at all — see
 * [otherCategoriesWithOption]/[everyOtherPairWithOption]. Options without that menu still reserve its
 * width (a blank spacer) so every control lines up at the same right edge. Below every option, a
 * mode-level "Apply to all categories" button (shown when the mode has sibling categories) copies the
 * whole currently-configured bundle to those siblings in one go, followed by a "Reset to Default"
 * button clearing just this (category, mode) pair's stored overrides. A "Reset All to Default" button
 * above the category chips clears every stored override in the app. [snackbarHostState] is the one
 * owned by the enclosing Settings screen's `Scaffold`, used to confirm every one of these actions with
 * an Undo.
 */
@Composable
internal fun ModeDefaultsSettingsContent(viewModel: FlashcardViewModel, snackbarHostState: SnackbarHostState) {
    val dimensions = LocalStudiareDimensions.current
    val modeDefaults by viewModel.modeDefaultSettings.collectAsState()
    val androidContext = LocalContext.current
    val scope = rememberCoroutineScope()
    val undoLabel = getText(R.string.undo)

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
        maxForDifficulty = { 100 }
    )

    fun confirmChange(message: String, snapshot: List<Pair<Pair<StudyCategory, SessionMode>, ModeDefaultSettings>>) {
        confirmModeDefaultsChange(scope, snackbarHostState, undoLabel, message, snapshot, viewModel)
    }

    fun confirmApply(targets: List<Pair<StudyCategory, SessionMode>>, snapshot: List<Pair<Pair<StudyCategory, SessionMode>, ModeDefaultSettings>>) {
        confirmModeDefaultsApply(scope, snackbarHostState, undoLabel, androidContext, targets, snapshot, viewModel)
    }

    fun applyAndConfirm(option: ModeOption, targets: List<Pair<StudyCategory, SessionMode>>) {
        confirmApply(targets, viewModel.applyModeOptionToPairs(option, settings, targets))
    }

    Column {
        Text(
            getText(R.string.mode_defaults_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimensions.spacingMedium)
        )

        FilledTonalButton(
            onClick = {
                confirmChange(androidContext.getString(R.string.mode_defaults_reset_all_message), viewModel.resetAllModeDefaults())
            },
            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
        ) { Text(getText(R.string.reset_all_to_default)) }
        Spacer(Modifier.height(dimensions.spacingMedium))

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

            val categoryTargets = remember(option, selectedCategory, selectedMode) { otherCategoriesWithOption(option, selectedCategory, selectedMode) }
            val everywhereTargets = remember(option, selectedCategory, selectedMode) { everyOtherPairWithOption(option, selectedCategory, selectedMode) }
            val hasApplyTargets = categoryTargets.isNotEmpty() || everywhereTargets.isNotEmpty()

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    option.Control(settings, context) { viewModel.setModeDefaultSettings(selectedCategory, selectedMode, it) }
                }
                if (hasApplyTargets) {
                    var menuExpanded by remember(option, selectedCategory, selectedMode) { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = getText(R.string.apply_option_actions))
                        }
                        DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                            if (categoryTargets.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.apply_to_all_categories)) },
                                    onClick = {
                                        menuExpanded = false
                                        applyAndConfirm(option, categoryTargets)
                                    }
                                )
                            }
                            if (everywhereTargets.isNotEmpty()) {
                                DropdownMenuItem(
                                    text = { Text(getText(R.string.apply_everywhere_option)) },
                                    onClick = {
                                        menuExpanded = false
                                        applyAndConfirm(option, everywhereTargets)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    // Keeps every option's control the same width whether or not it has the overflow
                    // icon, so controls across rows stay aligned instead of drifting to the right.
                    Spacer(Modifier.size(48.dp))
                }
            }
            if (option.dialogSection) {
                Spacer(Modifier.height(dimensions.spacingMedium))
                HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.spacingMedium))
            }
        }

        val modeCategoryTargets = remember(selectedCategory, selectedMode) { otherCategoriesWithMode(selectedCategory, selectedMode) }
        if (modeCategoryTargets.isNotEmpty()) {
            Spacer(Modifier.height(dimensions.spacingMedium))
            FilledTonalButton(
                onClick = { confirmApply(modeCategoryTargets, viewModel.applyAllOptionsToCategories(settings, modeCategoryTargets)) },
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) { Text(getText(R.string.apply_to_all_categories)) }
        }

        Spacer(Modifier.height(dimensions.spacingSmall))
        FilledTonalButton(
            onClick = {
                confirmChange(androidContext.getString(R.string.mode_default_reset_message), viewModel.resetModeDefaults(selectedCategory, selectedMode))
            },
            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
        ) { Text(getText(R.string.reset_to_default)) }
    }
}
