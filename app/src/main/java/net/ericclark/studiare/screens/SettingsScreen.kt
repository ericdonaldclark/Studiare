package net.ericclark.studiare.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.unit.Constraints
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.ericclark.studiare.*
import net.ericclark.studiare.BuildConfig
import net.ericclark.studiare.R
import net.ericclark.studiare.components.SimpleColorPicker
import net.ericclark.studiare.components.speech.WhisperModelSize
import net.ericclark.studiare.components.TagChip
import net.ericclark.studiare.components.TagCleanupDialog
import net.ericclark.studiare.components.TagEditorDialog
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*

private data class SettingCategoryData(
    val id: String,
    val title: String,
    val subtitle: String?,
    val content: @Composable () -> Unit
)

@SuppressLint("LocalContextGetResourceValueCall")
@Composable
fun SettingsScreen(
    navController: NavController,
    viewModel: FlashcardViewModel
) {
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
    val hasHardwareKeyboard = androidx.compose.ui.platform.LocalConfiguration.current.keyboard ==
        android.content.res.Configuration.KEYBOARD_QWERTY

    // --- State Collection ---
    val isUserAnonymous by viewModel.isUserAnonymous.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val showConflictDialog by viewModel.showConflictDialog.collectAsState()
    val isSyncSetupPending by viewModel.isSyncSetupPending.collectAsState()

    // Collect Sync Preferences
    val syncDecksAndCards by viewModel.syncDecksAndCards.collectAsState()
    val syncReviewData by viewModel.syncReviewData.collectAsState()
    val syncSavedSessions by viewModel.syncSavedSessions.collectAsState()
    val syncOnlyOnWifi by viewModel.syncOnlyOnWifi.collectAsState()

    // --- Sync Trackers ---
    val isSyncing by viewModel.isSyncing.collectAsState()
    val hasPendingChanges by viewModel.hasPendingChanges.collectAsState()

    // --- Info Stats ---
    val totalDecks by viewModel.totalDecks.collectAsState()
    val totalSets by viewModel.totalSets.collectAsState()
    val totalCards by viewModel.totalCards.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkPendingChanges()
    }

    // Customization States
    val themeMode by viewModel.themeMode.collectAsState()
    val customColors by viewModel.customThemeColors.collectAsState()
    val spacingMode by viewModel.spacingMode.collectAsState()
    val storedDeckSetsDisplayMode by viewModel.deckSetsDisplayMode.collectAsState()
    val deckSetsDisplayMode = DeckSetsDisplayMode.resolve(storedDeckSetsDisplayMode, windowWidthSizeClass, windowHeightSizeClass)
    val storedTreeLargeScreenLayout by viewModel.treeLargeScreenLayout.collectAsState()
    val treeLargeScreenLayout = DeckSetsDisplayMode.resolveTreeDirection(storedTreeLargeScreenLayout, windowWidthSizeClass, windowHeightSizeClass)
    val gridLoadingIndicator by viewModel.gridLoadingIndicator.collectAsState()
    val treeLoadingIndicator by viewModel.treeLoadingIndicator.collectAsState()
    val reduceMotion by viewModel.reduceMotion.collectAsState()
    val isDebug by viewModel.isDebug.collectAsState()

    // Map Spacing Mode to Dimensions
    val dimensions = LocalStudiareDimensions.current

    val lastExportTimestamp by viewModel.lastExportTimestamp.collectAsState()
    val lastImportTimestamp by viewModel.lastImportTimestamp.collectAsState()

    val tags by viewModel.tags.collectAsState()
    var showTagEditor by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<TagDefinition?>(null) }
    var tagToCleanup by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    val isBackendConnected by viewModel.isBackendConnected.collectAsState()
    val backendProjectId by viewModel.backendProjectId.collectAsState()

    // BYOB Loading State
    var isImportingJson by remember { mutableStateOf(false) }

    val jsonPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        uri?.let {
            isImportingJson = true
            viewModel.importBackendConfig(context, it)
        }
    }

    // Clear JSON loading state on connection or timeout if error
    LaunchedEffect(isBackendConnected) {
        if (isBackendConnected) isImportingJson = false
    }
    LaunchedEffect(isImportingJson) {
        if (isImportingJson) {
            kotlinx.coroutines.delay(3000)
            isImportingJson = false
        }
    }

    // Observables for Language Management
    val detectedLanguages = viewModel.getUniqueDeckLanguages()
    val downloadedLanguages by viewModel.downloadedHdLanguages.collectAsState()

    // Observables for Whisper speech-recognition model management
    val whisperModelSizeId by viewModel.whisperModelSize.collectAsState()
    val currentWhisperSize = remember(whisperModelSizeId) { WhisperModelSize.fromId(whisperModelSizeId) }
    var whisperSizeToDownload by remember { mutableStateOf<WhisperModelSize?>(null) }
    var whisperSizeToDelete by remember { mutableStateOf<WhisperModelSize?>(null) }
    var downloadingWhisperSize by remember { mutableStateOf<WhisperModelSize?>(null) }
    var whisperDownloadProgress by remember { mutableFloatStateOf(0f) }

    // Dialog States
    var showDeleteAllDecksDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomThemeDialog by remember { mutableStateOf(false) }

    // Dialog States for Language Management
    var languageToDownload by remember { mutableStateOf<String?>(null) }
    var languageToDelete by remember { mutableStateOf<String?>(null) }
    var showDownloadAllConfirm by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }
    var showWipeLocalConfirm by rememberSaveable { mutableStateOf(false) }
    var showWipeCloudConfirm by rememberSaveable { mutableStateOf(false) }
    var showFieldMapper by rememberSaveable { mutableStateOf(false) }

    // --- Dialogs (Conflict, Delete, Tags, Langs) ---
    if (showConflictDialog) {
        AnimatedDialog(onDismissRequest = { /* Prevent dismissing without choice */ }) {
            Surface(
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                    Text(getText(R.string.sync_conflict), style = MaterialTheme.typography.headlineSmall)
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Text(getText(R.string.sync_conflict_desc), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(dimensions.spacingLarge))
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.MERGE_KEEP_LOCAL) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                            Text(getText(R.string.merge_overwrite_cloud))
                        }
                        Button(onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.MERGE_KEEP_CLOUD) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                            Text(getText(R.string.merge_keep_cloud))
                        }
                        OutlinedButton(
                            onClick = { showWipeCloudConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(R.string.use_local_wipe_cloud))
                        }
                        OutlinedButton(
                            onClick = { showWipeLocalConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(R.string.use_cloud_wipe_local))
                        }
                    }
                }
            }
        }
    }

    if (showFieldMapper) {
        AnkiFieldMappingDialog(
            ankiFields = listOf(
                Pair("State", MediaType.PLAIN_TEXT), Pair("Capital", MediaType.PLAIN_TEXT), Pair("StateSnd", MediaType.AUDIO), Pair("CapitalSnd", MediaType.AUDIO), Pair("Map", MediaType.IMAGE),
                Pair("Postal", MediaType.PLAIN_TEXT)),
            originalAnkiName = "Test",
            onDismiss = { showFieldMapper = false },
            onSaveMapping = { configs ->
                configs.forEach { config ->
                    android.util.Log.d("AnkiMapper", "Deck: ${config.deckName}")
                    config.mapping.forEach { (dest, items) ->
                        android.util.Log.d("AnkiMapper", "  $dest -> ${items.map { it.text }}")
                    }
                }
            }
        )
    }

    if (showCustomThemeDialog) {
        CustomThemeDialog(
            initialColors = customColors,
            onDismiss = { showCustomThemeDialog = false },
            onSave = { p, s, t, b ->
                viewModel.setCustomThemeColors(p, s, t, b)
                showCustomThemeDialog = false
            }
        )
    }

    if (showDeleteAllDecksDialog) {
        ConfirmationDialog(
            title = getText(R.string.delete_all_decks_question),
            text = getText(R.string.delete_all_decks_confirm),
            onConfirm = { viewModel.deleteAllDecks(); showDeleteAllDecksDialog = false },
            onDismiss = { showDeleteAllDecksDialog = false },
            confirmButtonText = getText(R.string.delete_all)
        )
    }

    if (showTagEditor) {
        TagEditorDialog(
            tag = tagToEdit,
            existingTags = tags,
            onDismiss = { showTagEditor = false; tagToEdit = null },
            onSave = { name, color ->
                if (tagToEdit == null) {
                    val newTag = TagDefinition(name = name, color = color)
                    viewModel.saveTagDefinition(newTag)
                } else {
                    val updatedTag = tagToEdit!!.copy(name = name, color = color)
                    viewModel.renameTag(updatedTag, tagToEdit!!.name)
                }
                showTagEditor = false; tagToEdit = null
            }
        )
    }

    if (tagToCleanup != null) {
        TagCleanupDialog(tagName = tagToCleanup!!, viewModel = viewModel, onDismiss = { tagToCleanup = null })
    }

    if (languageToDownload != null) {
        ConfirmationDialog(
            title = getText(R.string.download_language_question),
            text = stringResource(R.string.download_language_confirm, Locale(languageToDownload!!).displayLanguage),
            confirmButtonText = getText(R.string.download),
            onConfirm = { viewModel.startHdLanguageDownload(context, listOf(languageToDownload!!)); languageToDownload = null },
            onDismiss = { languageToDownload = null }
        )
    }

    if (languageToDelete != null) {
        ConfirmationDialog(
            title = getText(R.string.delete_language_question),
            text = stringResource(R.string.delete_language_confirm, Locale(languageToDelete!!).displayLanguage),
            confirmButtonText = getText(R.string.delete),
            onConfirm = { viewModel.deleteHdLanguage(context, languageToDelete!!); languageToDelete = null },
            onDismiss = { languageToDelete = null }
        )
    }

    if (showDownloadAllConfirm) {
        val missingLanguages = detectedLanguages.filter { !downloadedLanguages.contains(it) }
        ConfirmationDialog(
            title = getText(R.string.download_all_question),
            text = stringResource(R.string.download_all_confirm, missingLanguages.size),
            confirmButtonText = getText(R.string.download_all),
            onConfirm = { viewModel.startHdLanguageDownload(context, missingLanguages); showDownloadAllConfirm = false },
            onDismiss = { showDownloadAllConfirm = false }
        )
    }

    if (showDeleteAllConfirm) {
        ConfirmationDialog(
            title = getText(R.string.delete_all_models_question),
            text = getText(R.string.delete_all_models_confirm),
            confirmButtonText = getText(R.string.delete_all),
            onConfirm = { viewModel.deleteAllHdLanguages(context); showDeleteAllConfirm = false },
            onDismiss = { showDeleteAllConfirm = false }
        )
    }

    if (whisperSizeToDownload != null) {
        val targetSize = whisperSizeToDownload!!
        val replacing = currentWhisperSize
        val downloadFailedMessage = getText(R.string.download_failed)
        ConfirmationDialog(
            title = if (replacing != null) {
                stringResource(R.string.replace_whisper_model_question, replacing.asString(), targetSize.asString())
            } else {
                stringResource(R.string.download_whisper_model_question, targetSize.asString())
            },
            text = stringResource(R.string.download_size_format, stringResource(targetSize.descriptionResId), stringResource(targetSize.downloadSizeLabelResId)),
            confirmButtonText = getText(R.string.download),
            onConfirm = {
                whisperSizeToDownload = null
                downloadingWhisperSize = targetSize
                whisperDownloadProgress = 0f
                viewModel.startWhisperModelDownload(
                    size = targetSize,
                    onProgress = { progress -> whisperDownloadProgress = progress },
                    onComplete = { success ->
                        downloadingWhisperSize = null
                        if (!success) {
                            Toast.makeText(context, downloadFailedMessage, Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            },
            onDismiss = { whisperSizeToDownload = null }
        )
    }

    if (whisperSizeToDelete != null) {
        val size = whisperSizeToDelete!!
        ConfirmationDialog(
            title = stringResource(R.string.delete_whisper_model_question, size.asString()),
            text = getText(R.string.delete_whisper_model_confirm),
            confirmButtonText = getText(R.string.delete),
            onConfirm = { viewModel.deleteWhisperModel(size); whisperSizeToDelete = null },
            onDismiss = { whisperSizeToDelete = null }
        )
    }

    if (showWipeLocalConfirm) {
        ConfirmationDialog(
            title = getText(R.string.use_cloud_wipe_local),
            text = getText(R.string.action_cannot_be_undone),
            confirmButtonText = getText(R.string.confirm),
            onConfirm = {
                viewModel.resolveConflict(ConflictResolutionStrategy.USE_CLOUD_WIPE_LOCAL)
                showWipeLocalConfirm = false
            },
            onDismiss = { showWipeLocalConfirm = false }
        )
    }

    if (showWipeCloudConfirm) {
        ConfirmationDialog(
            title = getText(R.string.use_local_wipe_cloud),
            text = getText(R.string.action_cannot_be_undone),
            confirmButtonText = getText(R.string.confirm),
            onConfirm = {
                viewModel.resolveConflict(ConflictResolutionStrategy.USE_LOCAL_WIPE_CLOUD)
                showWipeCloudConfirm = false
            },
            onDismiss = { showWipeCloudConfirm = false }
        )
    }

    // --- Pre-compute variables for categories ---
    val downloadedCount = detectedLanguages.count { downloadedLanguages.contains(it) }

    val themeName = when(themeMode) {
        0 -> getText(context, R.string.light_mode)
        1 -> getText(context, R.string.dark_mode)
        2 -> getText(context, R.string.bw_mode)
        3 -> getText(context, R.string.custom)
        else -> getText(context, R.string.unknown_theme_mode)
    }

    val spacingName = when(spacingMode) {
        0 -> getText(context, R.string.compact_mode)
        1 -> getText(context, R.string.normal_mode)
        2 -> getText(context, R.string.comfortable_mode)
        else -> getText(context, R.string.unknown_theme_mode)
    }

    val fullVersionInfo = BuildConfig.VERSION_NAME
    val versionNum = fullVersionInfo.split("-")[0]
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy, h:mm a", Locale.getDefault()) }
    val notAvailableStr = getText(context, R.string.not_available)
    fun formatTimestamp(timestamp: Long): String = if (timestamp == 0L) notAvailableStr else dateFormat.format(Date(timestamp))
    val buildDateString = remember(viewModel.buildTime) { dateFormat.format(Date(viewModel.buildTime)) }

    // --- Info / About data ---
    val cardStats by viewModel.cardStats.collectAsState()
    val activeSessionCount = viewModel.allActiveSessions.collectAsState().value.size
    val collectionCount = viewModel.allCollectionsWithDecks.collectAsState().value.size
    val storageUsage by produceState(Pair(0L, 0L), totalCards, totalDecks) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val dbBytes = listOf("", "-wal", "-shm").sumOf { context.getDatabasePath("studiare_database$it").let { f -> if (f.exists()) f.length() else 0L } }
            val fileBytes = context.filesDir.walkTopDown().filter { it.isFile }.sumOf { it.length() } +
                    context.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
            Pair(dbBytes, fileBytes)
        }
    }
    fun formatBytes(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.getDefault(), "%.0f KB", bytes / (1L shl 10).toDouble())
        else -> "$bytes B"
    }
    val libraries = remember {
        listOf(
            "Jetpack Compose & Material 3" to "User interface",
            "AndroidX Navigation, Lifecycle & DataStore" to "App structure and preferences",
            "Room" to "Local database",
            "Firebase (Auth, Firestore, Analytics, Crashlytics)" to "Sync, sign-in and diagnostics",
            "Google Play Services Auth" to "Google sign-in",
            "Ktor" to "Model downloads",
            "sherpa-onnx" to "Offline speech recognition and synthesis",
            "Kotlin Coroutines" to "Background work",
            "Coil" to "Image loading",
            "Compose Rich Editor" to "Rich text editing",
            "OpenCSV" to "CSV import and export",
            "Gson" to "JSON serialization",
            "Apache Commons Compress & zstd-jni" to "Anki package import",
            "AndroidX Security Crypto" to "Encrypted preferences",
            "Compose Shimmer" to "Loading placeholders",
            "AndroidX Core SplashScreen & ProfileInstaller" to "Startup"
        )
    }

    // --- Data-Driven Category Definitions ---
    val categories = listOfNotNull(
        SettingCategoryData(
            id = "customization",
            title = getText(R.string.customization),
            subtitle = stringResource(R.string.customization_subtitle, themeName, spacingName),
            content = {
                // Wide/two-pane settings layout only — the single-column phone accordion below
                // keeps its original stacked (label above, control below) rows unchanged.
                val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
                Column {
                    // --- Theme ---
                    SettingsSegmentedSetting(
                        isWideScreen = isWideSettingsLayout,
                        title = { Text(getText(R.string.theme), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall)) }
                    ) { segmentedModifier ->
                        SingleChoiceSegmentedButtonRow(modifier = segmentedModifier.padding(bottom = dimensions.spacingMedium)) {
                            val themes = listOf(
                                getText(R.string.light_mode) to 0,
                                getText(R.string.dark_mode) to 1,
                                getText(R.string.bw_mode) to 2,
                                getText(R.string.custom) to 3
                            )
                            themes.forEachIndexed { index, (name, mode) ->
                                SegmentedButton(
                                    selected = themeMode == mode,
                                    onClick = {
                                        if (mode == 3) {
                                            showCustomThemeDialog = true
                                        } else {
                                            viewModel.setThemeMode(mode)
                                        }
                                    },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = themes.size),
                                    icon = {
                                        if (mode == 3 && themeMode == 3) {
                                            Icon(Icons.Default.Edit, contentDescription = getText(R.string.edit_custom_theme), modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                                        } else {
                                            SegmentedButtonDefaults.Icon(active = themeMode == mode)
                                        }
                                    }
                                ) {
                                    Text(name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingSmall))

                    // --- Spacing ---
                    val currentSpacingDesc = when(spacingMode) {
                        0 -> getText(R.string.tighter_layout)
                        1 -> getText(R.string.standard_material_3)
                        2 -> getText(R.string.expressive_airy)
                        else -> ""
                    }
                    SettingsSegmentedSetting(
                        isWideScreen = isWideSettingsLayout,
                        description = currentSpacingDesc,
                        title = { Text(getText(R.string.spacing), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall)) }
                    ) { segmentedModifier ->
                        SingleChoiceSegmentedButtonRow(modifier = segmentedModifier) {
                            val spacings = listOf(
                                getText(R.string.compact_mode) to 0,
                                getText(R.string.normal_mode) to 1,
                                getText(R.string.comfortable_mode) to 2
                            )
                            spacings.forEachIndexed { index, (name, mode) ->
                                SegmentedButton(
                                    selected = spacingMode == mode,
                                    onClick = { viewModel.setSpacingMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = spacings.size)
                                ) {
                                    Text(name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                    // --- Layout Header --- (merged: deck-sets display + large-screen layout toggles)
                    Text(getText(R.string.layout_header), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))

                    SettingsSegmentedSetting(
                        isWideScreen = isWideSettingsLayout,
                        description = getText(R.string.display_sets_desc),
                        title = { Text(getText(R.string.display_sets), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp)) }
                    ) { segmentedModifier ->
                        SingleChoiceSegmentedButtonRow(modifier = segmentedModifier) {
                            val displayModes = listOf(DeckSetsDisplayMode.OFF, DeckSetsDisplayMode.UNDER_DECKS, DeckSetsDisplayMode.BESIDE_DECKS)
                            displayModes.forEachIndexed { index, mode ->
                                SegmentedButton(
                                    selected = deckSetsDisplayMode == mode,
                                    onClick = { viewModel.setDeckSetsDisplayMode(mode) },
                                    shape = SegmentedButtonDefaults.itemShape(index = index, count = displayModes.size)
                                ) {
                                    Text(mode.asString(), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }

                    // The tree's large-screen layout only exists on larger windows, so only offer it there.
                    if (windowWidthSizeClass != WindowWidthSizeClass.Compact) {
                        Spacer(Modifier.height(dimensions.spacingMedium))
                        SettingsSegmentedSetting(
                            isWideScreen = isWideSettingsLayout,
                            description = getText(R.string.tree_view_direction_desc),
                            title = { Text(getText(R.string.tree_view_direction), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp)) }
                        ) { segmentedModifier ->
                            SingleChoiceSegmentedButtonRow(modifier = segmentedModifier) {
                                val directions = listOf(false to R.string.vertical, true to R.string.horizontal)
                                directions.forEachIndexed { index, (isHorizontal, labelRes) ->
                                    SegmentedButton(
                                        selected = treeLargeScreenLayout == isHorizontal,
                                        onClick = { viewModel.setTreeLargeScreenLayout(isHorizontal) },
                                        shape = SegmentedButtonDefaults.itemShape(index = index, count = directions.size)
                                    ) {
                                        Text(getText(labelRes), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))
                    Text(getText(R.string.loading_header), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))
                    SettingSwitchItem(getText(R.string.grid_loading_indicator), getText(R.string.grid_loading_indicator_desc), gridLoadingIndicator) { viewModel.setGridLoadingIndicator(it) }
                    SettingSwitchItem(getText(R.string.tree_loading_indicator), getText(R.string.tree_loading_indicator_desc), treeLoadingIndicator) { viewModel.setTreeLoadingIndicator(it) }

                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingSmall))
                    Text(getText(R.string.motion_header), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))
                    SettingSwitchItem(getText(R.string.reduce_motion), getText(R.string.reduce_motion_desc), reduceMotion) { viewModel.setReduceMotion(it) }
                }
            }
        ),
        if (hasHardwareKeyboard) {
            SettingCategoryData(
                id = "keyboard",
                title = getText(R.string.keyboard_settings_title),
                subtitle = getText(R.string.keyboard_settings_subtitle),
                content = { KeyboardShortcutSettingsContent(viewModel) }
            )
        } else null,
        SettingCategoryData(
            id = "mode_defaults",
            title = getText(R.string.mode_defaults_title),
            subtitle = getText(R.string.mode_defaults_subtitle),
            content = { ModeDefaultsSettingsContent(viewModel) }
        ),
        SettingCategoryData(
            id = "backup",
            title = getText(R.string.backup_and_sync),
            subtitle = if (!isBackendConnected) "Not set up" else if (isUserAnonymous) getText(R.string.offline_mode) else stringResource(R.string.connected_as, userEmail ?: ""),
            content = {
                val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
                if (!isBackendConnected) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.CloudOff, null, modifier = Modifier.size(48.dp), tint = Color.Gray)
                        Spacer(Modifier.height(dimensions.spacingMedium))
                        Text(
                            "Set up your own backend to safely sync your data across all your devices.",
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                        )
                        TextButton(
                            onClick = {
                                val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/ericdonaldclark/Studiare"))
                                context.startActivity(intent)
                            },
                            modifier = Modifier.padding(top = 8.dp),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(R.string.learn_firebase_setup))
                        }
                        Spacer(Modifier.height(dimensions.spacingMedium))
                        if (isImportingJson) {
                            CircularProgressIndicator(modifier = Modifier.size(36.dp))
                            Spacer(Modifier.height(8.dp))
                            Text(getText(R.string.connecting_to_firebase), style = MaterialTheme.typography.bodyMedium)
                        } else {
                            Button(
                                onClick = { jsonPickerLauncher.launch("application/json") },
                                modifier = Modifier.fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp),
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) {
                                Text(getText(R.string.import_firebase_config))
                            }
                        }
                    }
                } else {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        var showBackendInfo by remember { mutableStateOf(false) }
                        SuggestionChip(
                            onClick = { showBackendInfo = true },
                            label = { Text(getText(R.string.backend_set_up)) },
                            icon = { Icon(Icons.Default.Info, null) },
                            colors = SuggestionChipDefaults.suggestionChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            ),
                            border = null
                        )

                        if (showBackendInfo) {
                            AnimatedDialog(onDismissRequest = { showBackendInfo = false }) {
                                Surface(
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                    tonalElevation = 6.dp
                                ) {
                                    Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                                        Text(getText(R.string.firebase_details), style = MaterialTheme.typography.headlineSmall)
                                        Spacer(Modifier.height(dimensions.spacingSmall))
                                        Text(stringResource(R.string.project_id_format, backendProjectId ?: getText(R.string.unknown)), style = MaterialTheme.typography.bodyMedium)
                                        Spacer(Modifier.height(dimensions.spacingLarge))
                                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                            TextButton(onClick = { showBackendInfo = false }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.close_capitalized)) }
                                        }
                                    }
                                }
                            }
                        }

                        if (isSyncSetupPending) {
                            CircularWavyProgressIndicator()
                            Spacer(Modifier.height(dimensions.spacingMedium))
                            Text(getText(R.string.finishing_sync), style = MaterialTheme.typography.bodyLarge)
                        } else {
                            if (isUserAnonymous) {
                                var showAuthDialog by remember { mutableStateOf(false) }

                                if (showAuthDialog) {
                                    var emailInput by remember { mutableStateOf("") }
                                    var passwordInput by remember { mutableStateOf("") }

                                    AnimatedDialog(onDismissRequest = { showAuthDialog = false }) {
                                        Surface(
                                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                                            tonalElevation = 6.dp
                                        ) {
                                            Column(modifier = Modifier.padding(dimensions.paddingLarge).widthIn(min = 280.dp, max = 560.dp)) {
                                                Text(getText(R.string.sync_account), style = MaterialTheme.typography.headlineSmall)
                                                Spacer(Modifier.height(dimensions.spacingMedium))
                                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    Text(getText(R.string.sync_account_desc))
                                                    OutlinedTextField(
                                                        value = emailInput,
                                                        onValueChange = { emailInput = it },
                                                        label = { Text(getText(R.string.email)) },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                    OutlinedTextField(
                                                        value = passwordInput,
                                                        onValueChange = { passwordInput = it },
                                                        label = { Text(getText(R.string.password)) },
                                                        singleLine = true,
                                                        modifier = Modifier.fillMaxWidth(),
                                                        visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation()
                                                    )
                                                }
                                                Spacer(Modifier.height(dimensions.spacingLarge))
                                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                                    TextButton(onClick = { showAuthDialog = false }, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                                                    Spacer(Modifier.width(dimensions.spacingSmall))
                                                    Button(
                                                        onClick = {
                                                            if (emailInput.isNotBlank() && passwordInput.isNotBlank()) {
                                                                viewModel.linkEmailAccount(emailInput, passwordInput, context)
                                                                showAuthDialog = false
                                                            }
                                                        },
                                                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                                    ) { Text(getText(R.string.submit)) }
                                                }
                                            }
                                        }
                                    }
                                }

                                Text(
                                    "Create an account on your backend to link your devices and secure your data.",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = dimensions.paddingMedium)
                                )
                                val connectInteractionSource = remember { MutableInteractionSource() }
                                val isConnectPressed by connectInteractionSource.collectIsPressedAsState()
                                val connectScale by animateFloatAsState(
                                    targetValue = if (isConnectPressed) 0.95f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    label = "connectSquish"
                                )
                                Button(
                                    onClick = { showAuthDialog = true },
                                    interactionSource = connectInteractionSource,
                                    modifier = Modifier.fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).scale(connectScale),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) {
                                    Text(getText(R.string.create_log_in_sync_account))
                                }

                                OutlinedButton(
                                    onClick = { viewModel.removeBackendConnection() },
                                    modifier = Modifier.fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).padding(top = dimensions.spacingSmall),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) {
                                    Text(getText(R.string.remove_firebase_setup))
                                }
                            } else {
                                Text(
                                    getText(R.string.data_synced_desc),
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = dimensions.paddingMedium)
                                )
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                    modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.paddingMedium)
                                ) {
                                    Column(
                                        modifier = Modifier.padding(dimensions.paddingMedium),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        AnimatedContent(
                                            targetState = isSyncing,
                                            transitionSpec = {
                                                (slideInVertically() + fadeIn() + expandVertically()).togetherWith(
                                                    slideOutVertically() + fadeOut() + shrinkVertically()
                                                )
                                            },
                                            label = "syncStatusTransition"
                                        ) { syncing ->
                                            if (syncing) {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    CircularWavyProgressIndicator(modifier = Modifier.size(28.dp))
                                                    Spacer(Modifier.height(8.dp))
                                                    Text(
                                                        text = "Syncing with cloud...",
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            } else {
                                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                    val statusText = if (hasPendingChanges) getText(R.string.pending_changes_upload) else getText(R.string.all_data_backed_up)
                                                    val icon = if (hasPendingChanges) Icons.Default.CloudUpload else Icons.Default.CloudDone
                                                    val tint = if (hasPendingChanges) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary

                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            imageVector = icon,
                                                            contentDescription = null,
                                                            tint = tint,
                                                            modifier = Modifier.size(20.dp)
                                                        )
                                                        Spacer(Modifier.width(dimensions.spacingSmall))
                                                        Text(
                                                            text = statusText,
                                                            style = MaterialTheme.typography.labelLarge,
                                                            color = tint
                                                        )
                                                    }
                                                    Text(
                                                        text = getText(R.string.sync_automatically_minimized),
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        textAlign = TextAlign.Center,
                                                        modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                                    )

                                                    val syncInteractionSource = remember { MutableInteractionSource() }
                                                    val isSyncPressed by syncInteractionSource.collectIsPressedAsState()
                                                    val syncScale by animateFloatAsState(
                                                        targetValue = if (isSyncPressed) 0.95f else 1f,
                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                                        label = "syncSquish"
                                                    )
                                                    FilledTonalButton(
                                                        onClick = { viewModel.triggerSync() },
                                                        interactionSource = syncInteractionSource,
                                                        modifier = Modifier.fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).scale(syncScale),
                                                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                                    ) {
                                                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                                                        Spacer(Modifier.width(dimensions.spacingSmall))
                                                        Text(getText(R.string.sync_now))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                OutlinedButton(
                                    onClick = { viewModel.signOut() },
                                    modifier = Modifier.fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = dimensions.paddingSmall),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) {
                                    Text(getText(R.string.log_out_only))
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.signOut()
                                        viewModel.removeBackendConnection()
                                    },
                                    modifier = Modifier.fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 36.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) {
                                    Text(getText(R.string.log_out_remove_setup))
                                }
                            }
                        }

                        // Sync Toggles Section ---
                        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text(
                                getText(R.string.sync_preferences),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                            )

                            // Toggle 1: Decks and Cards
                            val syncDecksInteractionSource = remember { MutableInteractionSource() }
                            val isSyncDecksPressed by syncDecksInteractionSource.collectIsPressedAsState()
                            val syncDecksScale by animateFloatAsState(
                                targetValue = if (isSyncDecksPressed) 0.95f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "syncDecksSquish"
                            )

                            ListItem(
                                headlineContent = { Text(getText(R.string.sync_decks_cards)) },
                                supportingContent = { Text(getText(R.string.sync_decks_cards_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = syncDecksAndCards,
                                        onCheckedChange = { viewModel.setSyncDecksAndCards(it) }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(syncDecksScale)
                                    .clickable(
                                        interactionSource = syncDecksInteractionSource,
                                        indication = LocalIndication.current
                                    ) { viewModel.setSyncDecksAndCards(!syncDecksAndCards) }
                            )

                            // Toggle 2: Review Data
                            val syncReviewInteractionSource = remember { MutableInteractionSource() }
                            val isSyncReviewPressed by syncReviewInteractionSource.collectIsPressedAsState()
                            val syncReviewScale by animateFloatAsState(
                                targetValue = if (isSyncReviewPressed) 0.95f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "syncReviewSquish"
                            )
                            ListItem(
                                headlineContent = { Text(getText(R.string.sync_review_data)) },
                                supportingContent = { Text(getText(R.string.sync_review_data_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = syncReviewData,
                                        onCheckedChange = { viewModel.setSyncReviewData(it) }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(syncReviewScale)
                                    .clickable(
                                        interactionSource = syncReviewInteractionSource,
                                        indication = LocalIndication.current
                                    ) { viewModel.setSyncReviewData(!syncReviewData) }
                            )

                            // Toggle 3: Saved Sessions
                            val syncSessionsInteractionSource = remember { MutableInteractionSource() }
                            val isSyncSessionsPressed by syncSessionsInteractionSource.collectIsPressedAsState()
                            val syncSessionsScale by animateFloatAsState(
                                targetValue = if (isSyncSessionsPressed) 0.95f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "syncSessionsSquish"
                            )
                            ListItem(
                                headlineContent = { Text(getText(R.string.sync_saved_sessions)) },
                                supportingContent = { Text(getText(R.string.sync_saved_sessions_desc)) },
                                trailingContent = {
                                    Switch(
                                        checked = syncSavedSessions,
                                        onCheckedChange = { viewModel.setSyncSavedSessions(it) }
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .scale(syncSessionsScale)
                                    .clickable(
                                        interactionSource = syncSessionsInteractionSource,
                                        indication = LocalIndication.current
                                    ) { viewModel.setSyncSavedSessions(!syncSavedSessions) }
                            )

                            // Data Usage Section
                            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    getText(R.string.data_usage),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                                )

                                val syncWifiInteractionSource = remember { MutableInteractionSource() }
                                val isSyncWifiPressed by syncWifiInteractionSource.collectIsPressedAsState()
                                val syncWifiScale by animateFloatAsState(
                                    targetValue = if (isSyncWifiPressed) 0.95f else 1f,
                                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                    label = "syncWifiSquish"
                                )
                                ListItem(
                                    headlineContent = { Text(getText(R.string.sync_wifi_only)) },
                                    supportingContent = { Text(getText(R.string.sync_wifi_only_desc)) },
                                    trailingContent = {
                                        Switch(
                                            checked = syncOnlyOnWifi,
                                            onCheckedChange = { viewModel.setSyncOnlyOnWifi(it) }
                                        )
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .scale(syncWifiScale)
                                        .clickable(
                                            interactionSource = syncWifiInteractionSource,
                                            indication = LocalIndication.current
                                        ) { viewModel.setSyncOnlyOnWifi(!syncOnlyOnWifi) }
                                )
                            }
                        }
                    }
                }
            }
        ),
        SettingCategoryData(
            id = "languages",
            title = getText(R.string.manage_languages),
            subtitle = stringResource(R.string.downloaded_count, downloadedCount, detectedLanguages.size),
            content = {
                Column {
                    Text(
                        getText(R.string.languages_detected_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                    )

                    // Language Table
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        if (detectedLanguages.isEmpty()) {
                            Text(
                                getText(R.string.no_languages_detected),
                                modifier = Modifier.padding(dimensions.paddingMedium),
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            detectedLanguages.forEachIndexed { index, lang ->
                                val isDownloaded = downloadedLanguages.contains(lang)
                                val langName = try { Locale(lang).displayLanguage } catch (e: Exception) { lang }
                                val sizeInfo = viewModel.getFormattedModelSize(lang)

                                ListItem(
                                    headlineContent = { Text(langName, fontWeight = FontWeight.SemiBold) },
                                    supportingContent = { Text(sizeInfo) },
                                    trailingContent = {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            if (isDownloaded) {
                                                Icon(Icons.Default.Check, null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                                                Spacer(Modifier.width(dimensions.spacingSmall))
                                                TooltipFilledTonalIconButton(description = getText(R.string.delete), 
                                                    onClick = { languageToDelete = lang },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                ) { Icon(Icons.Default.Delete, getText(R.string.delete)) }
                                            } else {
                                                TooltipFilledTonalIconButton(description = getText(R.string.download), onClick = { languageToDownload = lang }) {
                                                    Icon(Icons.Default.Download, getText(R.string.download))
                                                }
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                                if (index < detectedLanguages.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(dimensions.spacingMedium))

                    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        val downloadAllInteractionSource = remember { MutableInteractionSource() }
                        val isDownloadAllPressed by downloadAllInteractionSource.collectIsPressedAsState()
                        val downloadAllScale by animateFloatAsState(
                            targetValue = if (isDownloadAllPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "downloadAllSquish"
                        )
                        Button(
                            onClick = { showDownloadAllConfirm = true },
                            interactionSource = downloadAllInteractionSource,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).scale(downloadAllScale),
                            enabled = detectedLanguages.any { !downloadedLanguages.contains(it) },
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(R.string.download_all))
                        }

                        val deleteAllLangInteractionSource = remember { MutableInteractionSource() }
                        val isDeleteAllLangPressed by deleteAllLangInteractionSource.collectIsPressedAsState()
                        val deleteAllLangScale by animateFloatAsState(
                            targetValue = if (isDeleteAllLangPressed) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "deleteAllLangSquish"
                        )
                        OutlinedButton(
                            onClick = { showDeleteAllConfirm = true },
                            interactionSource = deleteAllLangInteractionSource,
                            modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).scale(deleteAllLangScale),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            enabled = downloadedLanguages.isNotEmpty(),
                            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                        ) {
                            Text(getText(R.string.delete_all))
                        }
                    }
                }
            }
        ),
        SettingCategoryData(
            id = "speech_recognition",
            title = getText(R.string.speech_recognition_category),
            subtitle = currentWhisperSize?.let { stringResource(R.string.speech_recognition_subtitle_active, it.asString()) }
                ?: getText(R.string.speech_recognition_subtitle_none),
            content = {
                Column {
                    Text(
                        getText(R.string.speech_recognition_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                    ) {
                        WhisperModelSize.entries.forEachIndexed { index, size ->
                            val isActive = currentWhisperSize == size
                            val isDownloadingThis = downloadingWhisperSize == size
                            val rowClickable = downloadingWhisperSize == null && !isActive

                            ListItem(
                                leadingContent = {
                                    RadioButton(selected = isActive, onClick = null, enabled = rowClickable)
                                },
                                headlineContent = {
                                    Text(
                                        stringResource(R.string.whisper_model_name_and_size_format, size.asString(), stringResource(size.downloadSizeLabelResId)),
                                        fontWeight = FontWeight.SemiBold
                                    )
                                },
                                supportingContent = {
                                    Column {
                                        Text(stringResource(size.descriptionResId), style = MaterialTheme.typography.bodySmall)
                                        if (isDownloadingThis) {
                                            Spacer(Modifier.height(dimensions.spacingSmall))
                                            LinearProgressIndicator(
                                                progress = { whisperDownloadProgress },
                                                modifier = Modifier.fillMaxWidth(),
                                                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                                            )
                                        }
                                    }
                                },
                                trailingContent = if (isActive) {
                                    {
                                        TooltipFilledTonalIconButton(
                                            description = getText(R.string.delete),
                                            onClick = { whisperSizeToDelete = size },
                                            colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                                contentColor = MaterialTheme.colorScheme.onErrorContainer
                                            )
                                        ) { Icon(Icons.Default.Delete, getText(R.string.delete)) }
                                    }
                                } else null,
                                modifier = if (rowClickable) {
                                    Modifier.clickable { whisperSizeToDownload = size }
                                } else {
                                    Modifier
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            if (index < WhisperModelSize.entries.size - 1) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }
                        }
                    }
                }
            }
        ),
        SettingCategoryData(
            id = "tags",
            title = getText(R.string.tags_manage),
            subtitle = stringResource(R.string.tags_defined_count, tags.size),
            content = {
                val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
                Column {
                    if (tags.isEmpty()) {
                        Text(
                            getText(R.string.no_tags_created),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = dimensions.paddingSmall)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            tags.sortedBy { it.name.lowercase() }.forEach { tag ->
                                ListItem(
                                    headlineContent = { TagChip(text = tag.name, colorHex = tag.color) },
                                    trailingContent = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            TooltipFilledTonalIconButton(description = getText(R.string.tag_remove_from_cards_icon), onClick = { tagToCleanup = tag.name }) {
                                                Icon(Icons.Default.Clear, getText(R.string.tag_remove_from_cards_icon))
                                            }
                                            TooltipFilledTonalIconButton(description = getText(R.string.edit), onClick = { tagToEdit = tag; showTagEditor = true }) {
                                                Icon(Icons.Default.Edit, getText(R.string.edit))
                                            }
                                            TooltipFilledTonalIconButton(description = getText(R.string.delete), 
                                                onClick = { viewModel.deleteTagDefinition(tag) },
                                                colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                    containerColor = MaterialTheme.colorScheme.errorContainer,
                                                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                )
                                            ) {
                                                Icon(Icons.Default.Delete, getText(R.string.delete))
                                            }
                                        }
                                    },
                                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
                                    modifier = Modifier.clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(dimensions.spacingMedium))

                    val createTagInteractionSource = remember { MutableInteractionSource() }
                    val isCreateTagPressed by createTagInteractionSource.collectIsPressedAsState()
                    val createTagScale by animateFloatAsState(
                        targetValue = if (isCreateTagPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "createTagSquish"
                    )
                    Button(
                        onClick = { tagToEdit = null; showTagEditor = true },
                        interactionSource = createTagInteractionSource,
                        modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).scale(createTagScale),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Text(getText(R.string.tag_create_new))
                    }
                }
            }
        ),
        SettingCategoryData(
            id = "delete",
            title = getText(R.string.delete_all_decks),
            subtitle = getText(R.string.action_cannot_be_undone),
            content = {
                val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
                Column {
                    val deleteAllDecksInteractionSource = remember { MutableInteractionSource() }
                    val isDeleteAllDecksPressed by deleteAllDecksInteractionSource.collectIsPressedAsState()
                    val deleteAllDecksScale by animateFloatAsState(
                        targetValue = if (isDeleteAllDecksPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "deleteAllDecksSquish"
                    )
                    Button(
                        onClick = { showDeleteAllDecksDialog = true },
                        interactionSource = deleteAllDecksInteractionSource,
                        modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).scale(deleteAllDecksScale),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.delete_all_decks))
                    }
                    Text(
                        getText(R.string.action_cannot_be_undone),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.fillMaxWidth().padding(top = dimensions.paddingSmall),
                        textAlign = TextAlign.Center
                    )
                }
            }
        ),
        if (!isDebug) null else SettingCategoryData(
            id = "troubleshooting",
            title = getText(R.string.debug),
            subtitle = getText(R.string.developer_tools),
            content = {
                val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
                Column(modifier = Modifier.fillMaxWidth()) {
                    val resetAudioInteractionSource = remember { MutableInteractionSource() }
                    val isResetAudioPressed by resetAudioInteractionSource.collectIsPressedAsState()
                    val resetAudioScale by animateFloatAsState(
                        targetValue = if (isResetAudioPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "resetAudioSquish"
                    )
                    Button(
                        onClick = { viewModel.setHdAudioPrompted(false); Toast.makeText(context, context.getString(R.string.hd_audio_prompt_reset), Toast.LENGTH_SHORT).show() },
                        interactionSource = resetAudioInteractionSource,
                        modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).scale(resetAudioScale),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.reset_hd_audio_prompt))
                    }

                    Spacer(Modifier.height(dimensions.spacingSmall))

                    val forceCrashInteractionSource = remember { MutableInteractionSource() }
                    val isForceCrashPressed by forceCrashInteractionSource.collectIsPressedAsState()
                    val forceCrashScale by animateFloatAsState(
                        targetValue = if (isForceCrashPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "forceCrashSquish"
                    )
                    Button(
                        onClick = { throw RuntimeException("Test Crash from Settings") },
                        interactionSource = forceCrashInteractionSource,
                        modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).scale(forceCrashScale),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.force_crash))
                    }

                    Spacer(Modifier.height(dimensions.spacingSmall))

                    val fieldMapperInteractionSource = remember { MutableInteractionSource() }
                    val isFieldMapperPressed by fieldMapperInteractionSource.collectIsPressedAsState()
                    val fieldMapperScale by animateFloatAsState(
                        targetValue = if (isFieldMapperPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "fieldMapperSquish"
                    )
                    Button(
                        onClick = { showFieldMapper = true },
                        interactionSource = fieldMapperInteractionSource,
                        modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).scale(fieldMapperScale),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.field_mapper))
                    }
                }
            }
        ),
        SettingCategoryData(
            id = "info",
            title = getText(R.string.info),
            subtitle = getText(R.string.stats_for_nerds),
            content = {
                Column {
                    SettingsSubsection("Collection") {
                        SettingsInfoRow(getText(R.string.total_decks), "$totalDecks", isAlternate = false)
                        SettingsInfoRow(getText(R.string.total_sets), "$totalSets", isAlternate = true)
                        SettingsInfoRow(getText(R.string.total_cards), "$totalCards", isAlternate = false)
                        SettingsInfoRow("Tags", "${tags.size}", isAlternate = true)
                        SettingsInfoRow("Collections", "$collectionCount", isAlternate = false)

                    }

                    SettingsSubsection("Study Activity") {
                        SettingsInfoRow("Saved sessions", "$activeSessionCount", isAlternate = false)
                        SettingsInfoRow("Total reviews", "${cardStats.totalReviews}", isAlternate = true)
                        SettingsInfoRow("Cards reviewed today", "${cardStats.reviewedToday}", isAlternate = false)

                    }

                    SettingsSubsection("Card Status") {
                        SettingsInfoRow(FsrsState.NEW.asString(), "${cardStats.newCards}", isAlternate = false)
                        SettingsInfoRow(FsrsState.LEARNING.asString(), "${cardStats.learning}", isAlternate = true)
                        SettingsInfoRow(FsrsState.REVIEW.asString(), "${cardStats.review}", isAlternate = false)
                        SettingsInfoRow(FsrsState.RELEARNING.asString(), "${cardStats.relearning}", isAlternate = true)
                        SettingsInfoRow(getText(R.string.suspended), "${cardStats.suspended}", isAlternate = false)
                        SettingsInfoRow("Known", if (totalCards > 0) "${cardStats.known} (${cardStats.known * 100 / totalCards}%)" else "0", isAlternate = true)

                    }

                    SettingsSubsection("Difficulty Spread") {
                        DifficultySetting.entries.forEachIndexed { index, level ->
                            SettingsInfoRow(level.asString(), "${cardStats.difficultyCounts[level] ?: 0}", isAlternate = index % 2 == 1)
                        }

                    }

                    SettingsSubsection("Storage Used") {
                        SettingsInfoRow("Database", formatBytes(storageUsage.first), isAlternate = false)
                        SettingsInfoRow("Downloaded files & cache", formatBytes(storageUsage.second), isAlternate = true)
                        SettingsInfoRow("Total", formatBytes(storageUsage.first + storageUsage.second), isAlternate = false)
                    }
                }
            }
        ),
        SettingCategoryData(
            id = "about",
            title = getText(R.string.about),
            subtitle = stringResource(R.string.app_info),
            content = {
                // Tap "App Version" 7 times to enable the hidden Debug category, mirroring
                // Android's own build-number Easter egg. The count resets if the user leaves this
                // screen (composition disposed) or backgrounds the app (ON_STOP) before reaching 7;
                // isDebug itself is one-way (see FlashcardViewModel.enableDebugMode) so it's never
                // un-set once earned.
                var appVersionTapCount by remember { mutableStateOf(0) }
                val lifecycleOwner = LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_STOP) appVersionTapCount = 0
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
                }

                Column {
                    SettingsSubsection("App") {
                        SettingsInfoRow(
                            "App Version",
                            versionNum,
                            isAlternate = false,
                            onClick = {
                                appVersionTapCount++
                                if (appVersionTapCount >= 7) {
                                    viewModel.enableDebugMode()
                                }
                            }
                        )
                        SettingsInfoRow("Build Date", buildDateString, isAlternate = true)
                        SettingsInfoRow("Build Type", if (BuildConfig.DEBUG) "Debug" else "Release", isAlternate = false)
                        SettingsInfoRow("Version Code", "${BuildConfig.VERSION_CODE}", isAlternate = true)
                        SettingsInfoRow("Package", context.packageName, isAlternate = false)
                        SettingsInfoRow("Target / Min SDK", "${context.applicationInfo.targetSdkVersion} / ${context.applicationInfo.minSdkVersion}", isAlternate = true)

                    }

                    SettingsSubsection("Device") {
                        SettingsInfoRow("Model", "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}", isAlternate = false)
                        SettingsInfoRow("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", isAlternate = true)
                        SettingsInfoRow("Width size class", windowWidthSizeClass.toString(), isAlternate = false)
                        SettingsInfoRow("Height size class", windowHeightSizeClass.toString(), isAlternate = true)

                    }

                    SettingsSubsection("Backup") {
                        SettingsInfoRow("Last Export", formatTimestamp(lastExportTimestamp), isAlternate = false)
                        SettingsInfoRow("Last Import", formatTimestamp(lastImportTimestamp), isAlternate = true)

                    }

                    SettingsSubsection("Libraries Included") {
                        libraries.forEachIndexed { index, (name, purpose) ->
                            ListItem(
                                headlineContent = { Text(name) },
                                supportingContent = { Text(purpose) },
                                colors = ListItemDefaults.colors(containerColor = if (index % 2 == 1) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f) else Color.Transparent)
                            )
                        }
                    }
                }
            }
        )
    )

    // --- Main UI Scaffold ---
    Scaffold(
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.SETTINGS,
                title = { Text(getText(R.string.settings)) },
                navigationIcon = {
                    TooltipIconButton(description = getText(R.string.back), onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val listState = rememberLazyListState()
            val coroutineScope = rememberCoroutineScope()
            val categoryKeyMap = listOf(
                Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
                Key.Six, Key.Seven, Key.Eight, Key.Nine
            )

            // Trailing categories (e.g. Info/About) are often shorter than the viewport, so there's
            // not enough content left below them to ever scroll their top all the way up — meaning
            // `firstVisibleItemIndex` alone can physically never reach them, and clicking them would
            // otherwise silently re-highlight whatever category happens to land at the top instead.
            // Pin the explicitly-clicked category until the user actually drags the list themselves,
            // at which point organic scroll-position tracking takes back over.
            var pinnedCategoryIndex by remember { mutableStateOf<Int?>(null) }
            val isListDragged by listState.interactionSource.collectIsDraggedAsState()
            LaunchedEffect(isListDragged) {
                if (isListDragged) pinnedCategoryIndex = null
            }

            // Sized against the Keyboard section's own shortcut categories (each rendered in full,
            // toggle/header/chips included, via the real composable with every category forced in
            // turn), not the unrelated settings categories around it — those can be much taller and
            // left a large empty gap under a short remap category like Decks.
            val remapCategoryHeightProbes: List<@Composable () -> Unit> = remember {
                allShortcuts.filter { it.remappable != null }
                    .map { it.category }
                    .distinct()
                    .map { category ->
                        { KeyboardShortcutSettingsContent(viewModel, initialCategory = category) }
                    }
            }

            if (windowWidthSizeClass >= WindowWidthSizeClass.Expanded) {
                // --- TABLET / INNER FOLD LAYOUT (Two-Pane) ---
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(dimensions.paddingLarge)
                ) {
                    // Left Pane: Navigation Rail
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState())
                            .padding(start = dimensions.paddingLarge, top = dimensions.paddingLarge),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // Figure out which item is currently visible at the top of the right pane
                        val firstVisibleIndex = remember { derivedStateOf { listState.firstVisibleItemIndex } }

                        categories.forEachIndexed { index, category ->
                            val isSelected = (pinnedCategoryIndex ?: firstVisibleIndex.value) == index
                            val jumpToCategory = {
                                pinnedCategoryIndex = index
                                coroutineScope.launch { listState.animateScrollToItem(index) }
                            }

                            // M3 Expressive Side Menu Item
                            Surface(
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .clickable { jumpToCategory() }
                                    .let {
                                        if (index in 0..8) it.withShortcut(categoryKeyMap[index], "${index + 1}") { jumpToCategory() } else it
                                    }
                            ) {
                                Text(
                                    text = category.title,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    // Right Pane: Expanded Settings Content. Wrapped so the Remap Shortcuts
                    // category can be pinned to the tallest of its own shortcut categories (see
                    // MaxContentHeightLayout) instead of reflowing everything below it whenever its
                    // own content changes size (e.g. switching shortcut categories).
                    MaxContentHeightLayout(
                        modifier = Modifier.weight(2.5f),
                        heightProbes = remapCategoryHeightProbes
                    ) { maxContentHeight ->
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(end = dimensions.paddingLarge, bottom = 100.dp)
                        ) {
                            items(categories.size) { index ->
                                val category = categories[index]
                                SettingsSectionWrapper(
                                    title = category.title,
                                    subtitle = category.subtitle,
                                    isWideScreen = true,
                                    dimensions = dimensions
                                ) {
                                    if (category.id == "keyboard") {
                                        Box(Modifier.fillMaxWidth().height(maxContentHeight).verticalScroll(rememberScrollState())) {
                                            category.content()
                                        }
                                    } else {
                                        category.content()
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // --- PHONE LAYOUT (Single Column, Accordion) ---
                MaxContentHeightLayout(
                    modifier = Modifier.fillMaxSize(),
                    heightProbes = remapCategoryHeightProbes
                ) { maxContentHeight ->
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 100.dp)
                    ) {
                        items(categories.size) { index ->
                            val category = categories[index]
                            SettingsSectionWrapper(
                                title = category.title,
                                subtitle = category.subtitle,
                                isWideScreen = false,
                                dimensions = dimensions
                            ) {
                                if (category.id == "keyboard") {
                                    Box(Modifier.fillMaxWidth().height(maxContentHeight).verticalScroll(rememberScrollState())) {
                                        category.content()
                                    }
                                } else {
                                    category.content()
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Measures [heightProbes] off-screen at the same width [content] will actually render at, then
 * calls [content] with the tallest result. Used to give the Remap Shortcuts section a fixed height
 * matching the tallest of its own shortcut categories, instead of resizing (and reflowing
 * everything after it) every time its own content changes size, e.g. switching shortcut
 * categories. Each probe is a fully real, independent composition purely for measurement — it's
 * never placed/drawn, and its own `remember`/state never touches the real render's.
 */
@Composable
private fun MaxContentHeightLayout(
    heightProbes: List<@Composable () -> Unit>,
    modifier: Modifier = Modifier,
    content: @Composable (maxContentHeight: Dp) -> Unit
) {
    SubcomposeLayout(modifier = modifier) { constraints ->
        val probeConstraints = constraints.copy(minWidth = 0, minHeight = 0, maxHeight = Constraints.Infinity)
        val maxHeightPx = heightProbes.withIndex().maxOfOrNull { (index, probe) ->
            subcompose("probe_$index", probe).maxOf { it.measure(probeConstraints).height }
        } ?: 0
        val maxHeightDp = maxHeightPx.toDp()

        val contentPlaceables = subcompose("realContent") { content(maxHeightDp) }.map { it.measure(constraints) }
        val height = contentPlaceables.maxOfOrNull { it.height } ?: 0
        layout(constraints.maxWidth, height) {
            contentPlaceables.forEach { it.place(0, 0) }
        }
    }
}

// Helper Composable for Expandable Sections
@Composable
fun SettingsSectionWrapper(
    title: String,
    subtitle: String?,
    isWideScreen: Boolean,
    dimensions: StudiareDimensions,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    var isExpanded by rememberSaveable { mutableStateOf(false) }
    // On wide screens, the sections are permanently expanded
    val effectivelyExpanded = isWideScreen || isExpanded

    Column(modifier = modifier.fillMaxWidth()) {
        // Section Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isWideScreen) { isExpanded = !isExpanded }
                .padding(horizontal = dimensions.paddingLarge, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Only show the chevron on narrow screens
            if (!isWideScreen) {
                val rotation by animateFloatAsState(
                    targetValue = if (effectivelyExpanded) 180f else 0f,
                    label = "chevronRotation"
                )
                Icon(
                    imageVector = Icons.Default.ExpandMore,
                    contentDescription = if (effectivelyExpanded) "Collapse" else "Expand",
                    modifier = Modifier.graphicsLayer { rotationZ = rotation }
                )
            }
        }

        // Animated Content Expansion
        AnimatedVisibility(
            visible = effectivelyExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = dimensions.paddingLarge)
                    .padding(bottom = dimensions.paddingMedium)
            ) {
                content()
            }
        }

        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
    }
}

@Composable
fun CustomThemeDialog(
    initialColors: CustomThemeColors,
    onDismiss: () -> Unit,
    onSave: (String, String, String, String) -> Unit
) {
    var primary by remember { mutableStateOf(initialColors.primary) }
    var secondary by remember { mutableStateOf(initialColors.secondary) }
    var tertiary by remember { mutableStateOf(initialColors.tertiary) }
    var background by remember { mutableStateOf(initialColors.background) }

    AnimatedDialog(onDismissRequest = onDismiss) {
        // M3 Expressive Card
        ElevatedCard(
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(getText(R.string.custom_theme), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                ColorPickerRow(getText(R.string.primary), primary) { primary = it }
                ColorPickerRow(getText(R.string.secondary), secondary) { secondary = it }
                ColorPickerRow(getText(R.string.tertiary), tertiary) { tertiary = it }
                ColorPickerRow(getText(R.string.background), background) { background = it }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(8.dp))

                    val applyInteractionSource = remember { MutableInteractionSource() }
                    val isApplyPressed by applyInteractionSource.collectIsPressedAsState()
                    val applyScale by animateFloatAsState(
                        targetValue = if (isApplyPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "applySquish"
                    )
                    Button(
                        onClick = { onSave(primary, secondary, tertiary, background) },
                        interactionSource = applyInteractionSource,
                        modifier = Modifier.scale(applyScale),
                        shape = RoundedCornerShape(net.ericclark.studiare.ui.theme.LocalStudiareDimensions.current.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.apply))
                    }
                }
            }
        }
    }
}

@Composable
fun ColorPickerRow(label: String, color: String, onColorChange: (String) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(label, style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(8.dp))
        // Reusing the SimpleColorPicker from Tags.kt
        SimpleColorPicker(
            selectedColor = color,
            onColorSelected = onColorChange
        )
    }
}

@Composable
private fun SettingsSubsection(
    title: String,
    initiallyExpanded: Boolean = false,
    content: @Composable () -> Unit
) {
    var expanded by rememberSaveable(title) { mutableStateOf(initiallyExpanded) }
    val rotation by animateFloatAsState(if (expanded) 180f else 0f, label = "subsectionChevron")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Icon(
            Icons.Default.ExpandMore,
            contentDescription = if (expanded) "Collapse" else "Expand",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.graphicsLayer { rotationZ = rotation }
        )
    }
    AnimatedVisibility(visible = expanded) {
        Column { content() }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String, isAlternate: Boolean = false, onClick: (() -> Unit)? = null) {
    ListItem(
        headlineContent = { Text(label) },
        trailingContent = { Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
        colors = ListItemDefaults.colors(containerColor = if (isAlternate) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f) else Color.Transparent),
        modifier = if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier
    )
}

/**
 * A settings row for a segmented-button control. On the wide two-pane settings layout, the title
 * and description sit in a left column with the segmented buttons sized to their own content and
 * anchored to the right — mirroring the two-pane settings screen's own split, without introducing
 * a second real pane. On the single-column phone layout ([isWideScreen] false), this renders
 * exactly as before: title above, full-width segmented row, optional description below.
 */
@Composable
private fun SettingsSegmentedSetting(
    isWideScreen: Boolean,
    description: String? = null,
    modifier: Modifier = Modifier,
    title: @Composable () -> Unit,
    segmented: @Composable (Modifier) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    if (isWideScreen) {
        Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f).padding(end = dimensions.spacingLarge)) {
                title()
                if (description != null) {
                    Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 4.dp))
                }
            }
            // SingleChoiceSegmentedButtonRow's own "wrap content" sizing (no width modifier) comes
            // out narrower than its labels actually need — confirmed independent of IntrinsicSize
            // hints applied from outside it, so the fix is a concrete width for it to divide among
            // its segments via their internal equal-weight sizing, same as fillMaxWidth already
            // does correctly in the phone layout below, just bounded instead of full-bleed.
            segmented(Modifier.width(440.dp).fillMaxWidth())
        }
    } else {
        // Unchanged from before this row had a wide-screen variant: title's own modifier carries
        // whatever spacing it always had, segmented gets a plain fillMaxWidth, description (if any)
        // sits below with its usual top padding.
        Column(modifier = modifier.fillMaxWidth()) {
            title()
            segmented(Modifier.fillMaxWidth())
            if (description != null) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
}

/** A settings row with a title, description and trailing switch; the whole row toggles it. */
@Composable
private fun SettingSwitchItem(title: String, description: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    ListItem(
        headlineContent = { Text(title) },
        supportingContent = { Text(description) },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.fillMaxWidth().clickable { onCheckedChange(!checked) }
    )
}

@Composable
private fun KeyboardShortcutSettingsContent(viewModel: FlashcardViewModel, initialCategory: String? = null) {
    val dimensions = LocalStudiareDimensions.current
    val showShortcutsButton by viewModel.showShortcutsButton.collectAsState()
    val remaps by viewModel.shortcutRemaps.collectAsState()

    val remappableShortcuts = remember { allShortcuts.filter { it.remappable != null } }
    val categories = remember { remappableShortcuts.map { it.category }.distinct() }
    var selectedCategory by remember { mutableStateOf(initialCategory ?: categories.first()) }
    var listeningFor by remember { mutableStateOf<ShortcutEntry?>(null) }
    var pendingRemap by remember { mutableStateOf<PendingShortcutRemap?>(null) }

    val entriesForCategory = remember(selectedCategory) {
        remappableShortcuts.filter { it.category == selectedCategory }
    }

    listeningFor?.let { entry ->
        ShortcutCaptureDialog(
            entry = entry,
            onKeyCaptured = { key ->
                val conflicts = findShortcutConflicts(remaps, entry.id, key, entry.remappable?.modifierPrefix)
                if (conflicts.isEmpty()) {
                    viewModel.setShortcutRemap(entry.id, key)
                } else {
                    pendingRemap = PendingShortcutRemap(entry, key, conflicts)
                }
                listeningFor = null
            },
            onCancel = { listeningFor = null }
        )
    }

    pendingRemap?.let { pending ->
        ConfirmationDialog(
            title = getText(R.string.key_already_in_use),
            text = stringResource(
                if (pending.conflicts.size == 1) R.string.shortcut_conflict_message_singular else R.string.shortcut_conflict_message_plural,
                pending.conflicts.joinToString(", ") { it.action },
                pending.entry.action
            ),
            confirmButtonText = getText(R.string.reassign_anyway),
            onConfirm = {
                viewModel.setShortcutRemap(pending.entry.id, pending.key)
                pendingRemap = null
            },
            onDismiss = { pendingRemap = null }
        )
    }

    Column {
        SettingSwitchItem(
            "Show shortcuts button",
            "Show the keyboard-shortcuts button in the top bar",
            showShortcutsButton
        ) { viewModel.setShowShortcutsButton(it) }

        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

        Text(
            "Remap Shortcuts",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = dimensions.spacingSmall)
        )
        Text(
            "Tap a shortcut's key to rebind it.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimensions.spacingMedium)
        )

        ShortcutCategoryChips(
            categories = categories,
            selectedCategory = selectedCategory,
            onCategorySelected = { selectedCategory = it }
        )

        Spacer(Modifier.height(dimensions.spacingMedium))

        Column {
            entriesForCategory.forEachIndexed { index, entry ->
                val liveConflicts = remember(entry, remaps) {
                    val spec = entry.remappable
                    if (spec == null) {
                        emptyList()
                    } else {
                        val currentKey = remaps[entry.id]?.let { Key(it) } ?: spec.defaultKey
                        findShortcutConflicts(remaps, entry.id, currentKey, spec.modifierPrefix)
                    }
                }
                ShortcutRemapRow(
                    entry = entry,
                    currentDisplay = entry.displayKeys(remaps),
                    isListening = listeningFor?.id == entry.id,
                    isCustomized = entry.remappable != null && remaps.containsKey(entry.id),
                    conflicts = liveConflicts,
                    isAlternate = index % 2 == 1,
                    onStartListening = { listeningFor = entry },
                    onReset = { viewModel.setShortcutRemap(entry.id, null) }
                )
            }
        }
    }
}

/** A horizontally-scrolling row of pill `FilterChip`s, generic over any typed item — same visual shape as [ShortcutCategoryChips] but keyed by value instead of a display string, so callers don't need a label↔item round-trip. */
@Composable
private fun <T> TypedChipRow(items: List<T>, selected: T, labelFor: @Composable (T) -> String, onSelected: (T) -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)
    ) {
        items.forEach { item ->
            FilterChip(
                selected = selected == item,
                onClick = { onSelected(item) },
                label = { Text(labelFor(item), maxLines = 1, overflow = TextOverflow.Ellipsis) },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/**
 * Settings → Mode Defaults: a category chip row above a mode chip row (filtered to that category's
 * modes, via the same [modesForCategory] `CreateStudySessionDialog`'s `ModeSelectionSection` uses),
 * then a settings panel for the selected (category, mode) pair — mirroring
 * [KeyboardShortcutSettingsContent]'s "pick a chip, edit the list below it" shape. Every control
 * applies live, same as every other Settings toggle (including Keyboard remaps above).
 */
@Composable
private fun ModeDefaultsSettingsContent(viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val modeDefaults by viewModel.modeDefaultSettings.collectAsState()

    val categories = remember { listOf(StudyCategory.LEARN, StudyCategory.PRACTICE, StudyCategory.QUIZ, StudyCategory.GAMES) }
    var selectedCategory by rememberSaveable { mutableStateOf(StudyCategory.LEARN) }
    var selectedMode by rememberSaveable { mutableStateOf(SessionMode.AUDIO) }
    val modesInCategory = remember(selectedCategory) { modesForCategory(selectedCategory) }
    LaunchedEffect(selectedCategory) {
        if (selectedMode !in modesInCategory) selectedMode = modesInCategory.first()
    }

    val settings = modeDefaults[selectedCategory to selectedMode] ?: ModeDefaultSettings()
    fun update(transform: (ModeDefaultSettings) -> ModeDefaultSettings) {
        viewModel.setModeDefaultSettings(selectedCategory, selectedMode, transform(settings))
    }

    Column {
        Text(
            getText(R.string.mode_defaults_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = dimensions.spacingMedium)
        )

        TypedChipRow(
            items = categories,
            selected = selectedCategory,
            labelFor = { it.asString() },
            onSelected = { selectedCategory = it }
        )

        Spacer(Modifier.height(dimensions.spacingSmall))

        TypedChipRow(
            items = modesInCategory,
            selected = selectedMode,
            labelFor = { it.asString() },
            onSelected = { selectedMode = it }
        )

        Spacer(Modifier.height(dimensions.spacingMedium))
        HorizontalDivider(modifier = Modifier.padding(bottom = dimensions.spacingMedium))

        // Prompt side — every mode gets this one, same as ModeSettingsSection's unconditional panel.
        Text(getText(R.string.prompt_side), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(dimensions.spacingSmall))
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            SegmentedButton(
                selected = (settings.quizPromptSide ?: CardSide.FRONT) == CardSide.FRONT,
                onClick = { update { it.copy(quizPromptSide = CardSide.FRONT) } },
                shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
            ) { Text(getText(R.string.front)) }
            SegmentedButton(
                selected = (settings.quizPromptSide ?: CardSide.FRONT) == CardSide.BACK,
                onClick = { update { it.copy(quizPromptSide = CardSide.BACK) } },
                shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
            ) { Text(getText(R.string.back)) }
        }

        when (selectedMode) {
            SessionMode.MULTIPLE_CHOICE -> {
                Spacer(Modifier.height(dimensions.spacingMedium))
                val numberOfAnswers = settings.numberOfAnswers ?: 4
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(getText(R.string.answers), modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TooltipFilledTonalIconButton(
                            description = getText(R.string.less),
                            onClick = { if (numberOfAnswers > 2) update { it.copy(numberOfAnswers = numberOfAnswers - 1) } },
                            enabled = numberOfAnswers > 2
                        ) { Icon(Icons.Default.Remove, getText(R.string.less)) }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Surface(
                            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                        ) {
                            Text(
                                text = numberOfAnswers.toString(),
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
                            )
                        }
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        TooltipFilledTonalIconButton(
                            description = getText(R.string.more),
                            onClick = { if (numberOfAnswers < 8) update { it.copy(numberOfAnswers = numberOfAnswers + 1) } },
                            enabled = numberOfAnswers < 8
                        ) { Icon(Icons.Default.Add, getText(R.string.more)) }
                    }
                }
            }
            SessionMode.ANAGRAM -> {
                Spacer(Modifier.height(dimensions.spacingSmall))
                SettingSwitchItem(
                    getText(R.string.show_correct_letters),
                    getText(R.string.show_correct_letters_desc),
                    settings.showCorrectLetters ?: true
                ) { update { prev -> prev.copy(showCorrectLetters = it) } }
            }
            SessionMode.HANGMAN -> {
                Spacer(Modifier.height(dimensions.spacingSmall))
                SettingSwitchItem(
                    getText(R.string.fingers_and_toes),
                    getText(R.string.fingers_and_toes_desc),
                    settings.fingersAndToes ?: false
                ) { update { prev -> prev.copy(fingersAndToes = it) } }
            }
            SessionMode.MEMORY -> {
                Spacer(Modifier.height(dimensions.spacingMedium))
                val maxMemoryTiles = settings.maxMemoryTiles ?: 20
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TooltipIconButton(
                        description = getText(R.string.decrease),
                        onClick = { if (maxMemoryTiles > 4) update { it.copy(maxMemoryTiles = maxMemoryTiles - 2) } },
                        enabled = maxMemoryTiles > 4
                    ) { Icon(Icons.Default.Remove, getText(R.string.decrease)) }
                    Surface(
                        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    ) {
                        Text(
                            "$maxMemoryTiles Tiles",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = dimensions.paddingLarge, vertical = dimensions.paddingSmall)
                        )
                    }
                    TooltipIconButton(
                        description = getText(R.string.increase),
                        onClick = { if (maxMemoryTiles < 100) update { it.copy(maxMemoryTiles = maxMemoryTiles + 2) } },
                        enabled = maxMemoryTiles < 100
                    ) { Icon(Icons.Default.Add, getText(R.string.increase)) }
                }
            }
            SessionMode.CROSSWORD, SessionMode.WORD_SEARCH -> {
                Spacer(Modifier.height(dimensions.spacingMedium))
                val gridDensity = settings.gridDensity ?: 2
                val densityLabel = when (gridDensity) {
                    1 -> getText(R.string.sparse); 2 -> getText(R.string.balanced); else -> getText(R.string.compact)
                }
                Text(getText(R.string.grid_density) + ": $densityLabel", modifier = Modifier.padding(bottom = dimensions.spacingSmall))
                Slider(
                    value = gridDensity.toFloat(),
                    onValueChange = { update { prev -> prev.copy(gridDensity = it.roundToInt()) } },
                    valueRange = 1f..3f,
                    steps = 1
                )
                SettingSwitchItem(
                    getText(R.string.show_correct_words),
                    getText(R.string.show_correct_words_desc),
                    settings.showCorrectWords ?: true
                ) { update { prev -> prev.copy(showCorrectWords = it) } }
            }
            SessionMode.FREEFORM -> {
                Spacer(Modifier.height(dimensions.spacingSmall))
                SettingSwitchItem(
                    stringResource(R.string.vertical_layout),
                    getText(R.string.vertical_layout_desc),
                    settings.freeformLayoutVertical ?: false
                ) { update { prev -> prev.copy(freeformLayoutVertical = it) } }
            }
            else -> {} // No mode-specific option beyond the universal prompt side above.
        }
    }
}

/**
 * Asks for the new key in a dialog on purpose: a dialog is its own window, so while it's up none
 * of the app's key handlers (Go Home, Esc, Alt hints, per-screen shortcuts...) receive the
 * keystroke — it can only ever be captured as the new binding, never also trigger its action.
 */
@Composable
private fun ShortcutCaptureDialog(
    entry: ShortcutEntry,
    onKeyCaptured: (Key) -> Unit,
    onCancel: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focusRequester.requestFocus() } }

    // Esc/Back has to be a bindable key here, so leaving is only via the Cancel button.
    AnimatedDialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)
    ) {
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(dimensions.paddingLarge)
                    .widthIn(min = 280.dp, max = 400.dp)
                    .focusRequester(focusRequester)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        // A modifier pressed on its way to another key shouldn't become the binding.
                        if (event.type == KeyEventType.KeyDown && event.key !in modifierOnlyKeys) {
                            onKeyCaptured(event.key)
                        }
                        true
                    },
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Keyboard, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(getText(R.string.press_the_new_key), style = MaterialTheme.typography.headlineSmall, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(
                    "for \"${entry.action}\"" +
                        (entry.remappable?.modifierPrefix?.let { " (with $it held)" } ?: ""),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(
                    "Keyboard shortcuts are paused until you press a key or cancel.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
                Spacer(Modifier.height(dimensions.spacingLarge))
                TextButton(onClick = onCancel, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
            }
        }
    }
}

private val modifierOnlyKeys = setOf(
    Key.AltLeft, Key.AltRight, Key.CtrlLeft, Key.CtrlRight, Key.ShiftLeft, Key.ShiftRight,
    Key.MetaLeft, Key.MetaRight, Key.CapsLock, Key.NumLock, Key.ScrollLock, Key.Function
)

private data class PendingShortcutRemap(
    val entry: ShortcutEntry,
    val key: androidx.compose.ui.input.key.Key,
    val conflicts: List<ShortcutEntry>
)

@Composable
private fun ShortcutRemapRow(
    entry: ShortcutEntry,
    currentDisplay: String,
    isListening: Boolean,
    isCustomized: Boolean,
    conflicts: List<ShortcutEntry>,
    isAlternate: Boolean = false,
    onStartListening: () -> Unit,
    onReset: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isAlternate) MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = 0.4f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(entry.action, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))

        if (entry.remappable == null) {
            Text(
                currentDisplay,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            if (conflicts.isNotEmpty()) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = getText(R.string.shortcut_conflict_warning),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(18.dp).padding(end = 4.dp)
                )
            }
            if (isCustomized) {
                TooltipIconButton(description = getText(R.string.reset_to_default), onClick = onReset, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Refresh, contentDescription = getText(R.string.reset_to_default), modifier = Modifier.size(18.dp))
                }
            }
            FilterChip(
                selected = isListening,
                onClick = onStartListening,
                label = { Text(if (isListening) "Press a key…" else currentDisplay, maxLines = 1) },
                shape = RoundedCornerShape(50),
                colors = if (conflicts.isNotEmpty() && !isListening) {
                    FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        labelColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                } else {
                    FilterChipDefaults.filterChipColors()
                }
            )
        }
    }
    if (conflicts.isNotEmpty()) {
        Text(
            "Also used by " + conflicts.joinToString(", ") { it.action },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.padding(top = 2.dp)
        )
    }
    }
}
