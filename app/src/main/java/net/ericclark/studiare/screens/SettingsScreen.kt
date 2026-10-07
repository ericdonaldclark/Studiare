package net.ericclark.studiare.screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.ui.input.key.Key
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.ericclark.studiare.*
import net.ericclark.studiare.BuildConfig
import net.ericclark.studiare.R
import net.ericclark.studiare.components.speech.WhisperModelSize
import net.ericclark.studiare.components.TagChip
import net.ericclark.studiare.components.TagCleanupDialog
import net.ericclark.studiare.components.TagEditorDialog
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*

internal data class SettingCategoryData(
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
    val disableCardFlipAnimations by viewModel.disableCardFlipAnimations.collectAsState()
    val alwaysOpenBulkEditor by viewModel.alwaysOpenBulkEditor.collectAsState()
    val isDebug by viewModel.isDebug.collectAsState()
    val showSizeOverlay by viewModel.showSizeOverlay.collectAsState()

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
    var whisperDownloadJob by remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }

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
                whisperDownloadJob = viewModel.startWhisperModelDownload(
                    size = targetSize,
                    onProgress = { progress -> whisperDownloadProgress = progress },
                    onComplete = { success ->
                        downloadingWhisperSize = null
                        whisperDownloadJob = null
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
                    SettingSwitchItem(getText(R.string.disable_card_flip_animations), getText(R.string.disable_card_flip_animations_desc), disableCardFlipAnimations) { viewModel.setDisableCardFlipAnimations(it) }

                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingSmall))
                    Text(getText(R.string.editor_header), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))
                    SettingSwitchItem(getText(R.string.always_open_bulk_editor), getText(R.string.always_open_bulk_editor_desc), alwaysOpenBulkEditor) { viewModel.setAlwaysOpenBulkEditor(it) }
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
            id = "audio_voice",
            title = getText(R.string.audio_voice_category),
            subtitle = "${stringResource(R.string.downloaded_count, downloadedCount, detectedLanguages.size)} · " +
                (currentWhisperSize?.let { stringResource(R.string.speech_recognition_subtitle_active, it.asString()) }
                    ?: getText(R.string.speech_recognition_subtitle_none)),
            content = {
                val voiceDownload by viewModel.voiceDownload.collectAsState()
                Column {
                    SettingsSubsection(getText(R.string.audio_output), initiallyExpanded = true) {
                        Column {
                            NotificationPermissionRow()
                            Text(
                                getText(R.string.languages_detected_desc),
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
                                        val isDownloadingThis = voiceDownload?.language == lang
                                        val isQueued = voiceDownload?.queued?.contains(lang) == true
                                        val langName = try { Locale(lang).displayLanguage } catch (e: Exception) { lang }
                                        val sizeInfo = viewModel.getFormattedModelSize(lang)

                                        VoiceLanguageRow(
                                            languageName = langName,
                                            sizeText = sizeInfo,
                                            isDownloaded = isDownloaded,
                                            isDownloading = isDownloadingThis,
                                            isQueued = isQueued,
                                            progress = voiceDownload?.fraction ?: 0f,
                                            onDownload = { languageToDownload = lang },
                                            onCancel = { viewModel.cancelVoiceDownload() },
                                            onDelete = { languageToDelete = lang }
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
                                // While voices download, this becomes Cancel downloads: it stops at once and keeps nothing
                                val voicesBusy = voiceDownload != null
                                Button(
                                    onClick = { if (voicesBusy) viewModel.cancelVoiceDownload() else showDownloadAllConfirm = true },
                                    interactionSource = downloadAllInteractionSource,
                                    modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).scale(downloadAllScale),
                                    enabled = voicesBusy || detectedLanguages.any { !downloadedLanguages.contains(it) },
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) {
                                    Text(getText(if (voicesBusy) R.string.cancel_downloads else R.string.download_all))
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

                    SettingsSubsection(getText(R.string.speech_recognition_category), initiallyExpanded = true) {
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
                                        trailingContent = when {
                                            isDownloadingThis -> {
                                                {
                                                    TextButton(
                                                        onClick = {
                                                            // Stops at once and keeps nothing (the downloader removes the partial model)
                                                            whisperDownloadJob?.cancel()
                                                            whisperDownloadJob = null
                                                            downloadingWhisperSize = null
                                                            whisperDownloadProgress = 0f
                                                        },
                                                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                                    ) { Text(getText(R.string.cancel)) }
                                                }
                                            }
                                            isActive -> {
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
                                            }
                                            else -> null
                                        },
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
                }
            }
        ),
        SettingCategoryData(
            id = "tags",
            title = getText(R.string.tags_manage),
            subtitle = stringResource(R.string.tags_defined_count, tags.size),
            content = {
                val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
                val sortTagsByDateCreated by viewModel.sortTagsByDateCreated.collectAsState()
                Column {
                    SettingSwitchItem(
                        getText(R.string.sort_tags_by_date_created),
                        getText(R.string.sort_tags_by_date_created_desc),
                        sortTagsByDateCreated
                    ) { viewModel.setSortTagsByDateCreated(it) }
                    Spacer(Modifier.height(dimensions.spacingSmall))

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
                            val sortedTags = if (sortTagsByDateCreated) tags.sortedByDescending { it.createdAt } else tags.sortedBy { it.name.lowercase() }
                            sortedTags.forEach { tag ->
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
                    SettingSwitchItem(getText(R.string.show_size_overlay), getText(R.string.show_size_overlay_desc), showSizeOverlay) { viewModel.setShowSizeOverlay(it) }
                    var showSavedPreferences by remember { mutableStateOf(false) }
                    val savedPrefsInteractionSource = remember { MutableInteractionSource() }
                    val isSavedPrefsPressed by savedPrefsInteractionSource.collectIsPressedAsState()
                    val savedPrefsScale by animateFloatAsState(
                        targetValue = if (isSavedPrefsPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "savedPrefsSquish"
                    )
                    Button(
                        onClick = { showSavedPreferences = true },
                        interactionSource = savedPrefsInteractionSource,
                        modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).scale(savedPrefsScale),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.manage_saved_preferences))
                    }
                    if (showSavedPreferences) {
                        SavedPreferencesDialog(viewModel = viewModel, onDismiss = { showSavedPreferences = false })
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
                        SettingsInfoRow(getText(R.string.window_size), "${androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp} × ${androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp} dp", isAlternate = false)

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

    // Phone only: the list opens one category at a time on its own page (like Android Settings).
    // The wide layout shows every category at once, so it never reads this.
    var openCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    val openCategory = if (windowWidthSizeClass >= WindowWidthSizeClass.Expanded) null else categories.firstOrNull { it.id == openCategoryId }
    BackHandler(enabled = openCategory != null) { openCategoryId = null }

    // --- Main UI Scaffold ---
    Scaffold(
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.SETTINGS,
                title = { Text(openCategory?.title ?: getText(R.string.settings)) },
                navigationIcon = {
                    TooltipIconButton(
                        description = getText(R.string.back),
                        onClick = { if (openCategory != null) openCategoryId = null else navController.popBackStack() }
                    ) {
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
            } else if (openCategory == null) {
                // --- PHONE LAYOUT: one row per category, no dividers; a row opens that category's page ---
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 100.dp)
                ) {
                    items(categories.size) { index ->
                        val category = categories[index]
                        SettingsCategoryRow(
                            title = category.title,
                            subtitle = category.subtitle,
                            dimensions = dimensions,
                            onClick = { openCategoryId = category.id }
                        )
                    }
                }
            } else {
                // --- PHONE LAYOUT: one category's settings on its own page ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = dimensions.paddingLarge)
                        .padding(bottom = 100.dp)
                ) {
                    openCategory.content()
                }
            }
        }
    }
}
