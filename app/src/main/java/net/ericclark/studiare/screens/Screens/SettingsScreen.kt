package net.ericclark.studiare.screens.Screens

import android.annotation.SuppressLint
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.documentfile.provider.DocumentFile
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.google.android.gms.auth.api.identity.AuthorizationResult
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import net.ericclark.studiare.*
import net.ericclark.studiare.components.DriveBackupClient
import net.ericclark.studiare.components.ScheduledBackupWorker
import net.ericclark.studiare.BuildConfig
import net.ericclark.studiare.R
import net.ericclark.studiare.components.speech.WhisperModelSize
import net.ericclark.studiare.components.TagChip
import net.ericclark.studiare.components.TagCleanupDialog
import net.ericclark.studiare.components.TagEditorDialog
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import net.ericclark.studiare.screens.Dialogs.AnkiFieldMappingDialog
import net.ericclark.studiare.screens.Dialogs.CustomThemeDialog
import net.ericclark.studiare.screens.Dialogs.SavedPreferencesDialog
import net.ericclark.studiare.screens.UI_Components.ExpressiveDropdownMenuField
import net.ericclark.studiare.screens.UI_Components.KeyboardShortcutSettingsContent
import net.ericclark.studiare.screens.UI_Components.ModeDefaultsSettingsContent
import net.ericclark.studiare.screens.UI_Components.NotificationPermissionRow
import net.ericclark.studiare.screens.UI_Components.SettingSwitchItem
import net.ericclark.studiare.screens.UI_Components.SettingsCategoryRow
import net.ericclark.studiare.screens.UI_Components.SettingsInfoRow
import net.ericclark.studiare.screens.UI_Components.SettingsSegmentedSetting
import net.ericclark.studiare.screens.UI_Components.SettingsSubsection
import net.ericclark.studiare.screens.UI_Components.VoiceLanguageRow

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
    val showTagEditorState = remember { mutableStateOf(false) }
    var showTagEditor by showTagEditorState
    val tagToEditState = remember { mutableStateOf<TagDefinition?>(null) }
    var tagToEdit by tagToEditState
    val tagToCleanupState = remember { mutableStateOf<String?>(null) }
    var tagToCleanup by tagToCleanupState

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
    // Held as MutableState (not just `var x by remember {...}`) so the Audio & Voice category's own
    // extracted composable (see AudioVoiceCategoryContent) and the confirmation dialogs below, which
    // stay here since they're not category-specific UI, can share the same state objects.
    val whisperSizeToDownloadState = remember { mutableStateOf<WhisperModelSize?>(null) }
    var whisperSizeToDownload by whisperSizeToDownloadState
    val whisperSizeToDeleteState = remember { mutableStateOf<WhisperModelSize?>(null) }
    var whisperSizeToDelete by whisperSizeToDeleteState
    val downloadingWhisperSizeState = remember { mutableStateOf<WhisperModelSize?>(null) }
    var downloadingWhisperSize by downloadingWhisperSizeState
    val whisperDownloadProgressState = remember { mutableFloatStateOf(0f) }
    var whisperDownloadProgress by whisperDownloadProgressState
    val whisperDownloadJobState = remember { mutableStateOf<kotlinx.coroutines.Job?>(null) }
    var whisperDownloadJob by whisperDownloadJobState

    // Dialog States
    val showDeleteAllDecksDialogState = rememberSaveable { mutableStateOf(false) }
    var showDeleteAllDecksDialog by showDeleteAllDecksDialogState
    var showCustomThemeDialog by remember { mutableStateOf(false) }

    // Dialog States for Language Management
    val languageToDownloadState = remember { mutableStateOf<String?>(null) }
    var languageToDownload by languageToDownloadState
    val languageToDeleteState = remember { mutableStateOf<String?>(null) }
    var languageToDelete by languageToDeleteState
    val showDownloadAllConfirmState = remember { mutableStateOf(false) }
    var showDownloadAllConfirm by showDownloadAllConfirmState
    val showDeleteAllConfirmState = remember { mutableStateOf(false) }
    var showDeleteAllConfirm by showDeleteAllConfirmState
    var showWipeLocalConfirm by rememberSaveable { mutableStateOf(false) }
    var showWipeCloudConfirm by rememberSaveable { mutableStateOf(false) }
    val showFieldMapperState = rememberSaveable { mutableStateOf(false) }
    var showFieldMapper by showFieldMapperState

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

    val snackbarHostState = remember { SnackbarHostState() }

    // --- Data-Driven Category Definitions ---
    val categories = listOfNotNull(
        SettingCategoryData(
            id = "customization",
            title = getText(R.string.customization),
            subtitle = stringResource(R.string.customization_subtitle, themeName, spacingName),
            content = { CustomizationCategoryContent(viewModel) { showCustomThemeDialog = true } }
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
            content = { ModeDefaultsSettingsContent(viewModel, snackbarHostState) }
        ),
        SettingCategoryData(
            id = "backup",
            title = getText(R.string.backup_and_sync),
            subtitle = if (!isBackendConnected) "Not set up" else if (isUserAnonymous) getText(R.string.offline_mode) else stringResource(R.string.connected_as, userEmail ?: ""),
            content = { BackupSyncCategoryContent(viewModel, isImportingJson, jsonPickerLauncher) }
        ),
        SettingCategoryData(
            id = "audio_voice",
            title = getText(R.string.audio_voice_category),
            subtitle = "${stringResource(R.string.downloaded_count, downloadedCount, detectedLanguages.size)} · " +
                (currentWhisperSize?.let { stringResource(R.string.speech_recognition_subtitle_active, it.asString()) }
                    ?: getText(R.string.speech_recognition_subtitle_none)),
            content = {
                AudioVoiceCategoryContent(
                    viewModel = viewModel,
                    currentWhisperSize = currentWhisperSize,
                    whisperSizeToDownloadState = whisperSizeToDownloadState,
                    whisperSizeToDeleteState = whisperSizeToDeleteState,
                    downloadingWhisperSizeState = downloadingWhisperSizeState,
                    whisperDownloadProgressState = whisperDownloadProgressState,
                    whisperDownloadJobState = whisperDownloadJobState,
                    languageToDownloadState = languageToDownloadState,
                    languageToDeleteState = languageToDeleteState,
                    showDownloadAllConfirmState = showDownloadAllConfirmState,
                    showDeleteAllConfirmState = showDeleteAllConfirmState
                )
            }
        ),
        SettingCategoryData(
            id = "tags",
            title = getText(R.string.tags_manage),
            subtitle = stringResource(R.string.tags_defined_count, tags.size),
            content = {
                TagsCategoryContent(viewModel, tags, tagToEditState, tagToCleanupState, showTagEditorState)
            }
        ),
        SettingCategoryData(
            id = "delete",
            title = getText(R.string.delete_all_decks),
            subtitle = getText(R.string.action_cannot_be_undone),
            content = { DeleteAllDecksCategoryContent(showDeleteAllDecksDialogState) }
        ),
        if (!isDebug) null else SettingCategoryData(
            id = "troubleshooting",
            title = getText(R.string.debug),
            subtitle = getText(R.string.developer_tools),
            content = { TroubleshootingCategoryContent(viewModel, showFieldMapperState) }
        ),
        SettingCategoryData(
            id = "info",
            title = getText(R.string.info),
            subtitle = getText(R.string.stats_for_nerds),
            content = {
                InfoCategoryContent(tags.size, totalDecks, totalSets, totalCards, collectionCount, activeSessionCount, cardStats, storageUsage)
            }
        ),
        SettingCategoryData(
            id = "about",
            title = getText(R.string.about),
            subtitle = stringResource(R.string.app_info),
            content = { AboutCategoryContent(viewModel, versionNum, buildDateString, lastExportTimestamp, lastImportTimestamp) }
        )
    )

    // Phone: the list opens one category at a time on its own page (like Android Settings).
    // Wide: the nav rail always has one category selected, shown alone in the content pane —
    // composing only that one category instead of every category at once (and never a scrolling
    // list of them) is what keeps the wide layout's first paint cheap.
    var openCategoryId by rememberSaveable { mutableStateOf<String?>(null) }
    var selectedWideCategoryIndex by rememberSaveable { mutableStateOf(0) }
    val isWideLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
    val openCategory = if (isWideLayout) null else categories.firstOrNull { it.id == openCategoryId }
    BackHandler(enabled = !isWideLayout && openCategory != null) { openCategoryId = null }

    // --- Main UI Scaffold ---
    Scaffold(
        topBar = {
            CustomTopAppBar(
                viewModel = viewModel,
                screenId = ShortcutScreen.SETTINGS,
                title = {
                    val title = if (isWideLayout) categories.getOrNull(selectedWideCategoryIndex)?.title else openCategory?.title
                    Text(title ?: getText(R.string.settings))
                },
                navigationIcon = {
                    TooltipIconButton(
                        description = getText(R.string.back),
                        onClick = { if (!isWideLayout && openCategory != null) openCategoryId = null else navController.popBackStack() }
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = getText(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            val categoryKeyMap = listOf(
                Key.One, Key.Two, Key.Three, Key.Four, Key.Five,
                Key.Six, Key.Seven, Key.Eight, Key.Nine
            )

            // Folding devices: switching between the compact outer screen and the larger inner
            // screen (or back) carries the current category along instead of dropping back to the
            // list / losing place — compact's open category becomes the wide pane's selection, and
            // the wide pane's selected category becomes compact's open category.
            var previousWindowWidthSizeClass by remember { mutableStateOf(windowWidthSizeClass) }
            LaunchedEffect(windowWidthSizeClass) {
                val wasExpanded = previousWindowWidthSizeClass >= WindowWidthSizeClass.Expanded
                val isExpanded = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
                if (!wasExpanded && isExpanded) {
                    selectedWideCategoryIndex = categories.indexOfFirst { it.id == openCategoryId }.takeIf { it >= 0 } ?: 0
                } else if (wasExpanded && !isExpanded) {
                    openCategoryId = categories.getOrNull(selectedWideCategoryIndex)?.id
                }
                previousWindowWidthSizeClass = windowWidthSizeClass
            }

            val motionScheme = MaterialTheme.motionScheme
            val reducedMotion = LocalReducedMotion.current

            // The very first composition of a freshly-navigated-to Settings screen renders with
            // nothing between it and the NavHost's own enter transition — an AnimatedContent
            // wrapper sitting there even on a non-animating first mount was swallowing that outer
            // transition entirely in the wide layout (never in compact, which has its own nested
            // AnimatedContent below regardless). Only once already composed once does a live
            // fold/unfold get its own scale+fade transition, same as compact's list<->category
            // switch below.
            var hasComposedSettingsOnce by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) { hasComposedSettingsOnce = true }

            // Confirmed by isolation testing: the wide-layout first-visit delay/no-transition is
            // the cost of composing whichever category's content happens to be selected (opening
            // on a cheap category showed no delay at all; opening on Customization did). The nav
            // rail itself is cheap. So the rail composes immediately, but the selected category's
            // content pane waits until the NavHost's enter/pop transition into Settings has
            // actually finished playing before it composes — the heavy first-time class-load/JIT
            // of that category then happens after the screen is already visible, not during the
            // transition that was getting eaten by it.
            val navAnimatedVisibilityScope = LocalNavAnimatedVisibilityScope.current
            // `isRunning` is racy to read this early — it can still report false before the
            // animation clock has ticked even once, which let both panes through immediately on
            // the first try. `currentState` doesn't have that race: it moves off PreEnter/PostExit
            // to Visible only once the enter/pop transition has actually settled.
            var mainContentReady by remember { mutableStateOf(navAnimatedVisibilityScope == null) }
            LaunchedEffect(navAnimatedVisibilityScope) {
                val transition = navAnimatedVisibilityScope?.transition ?: return@LaunchedEffect
                snapshotFlow { transition.currentState }.first { it == EnterExitState.Visible }
                mainContentReady = true
            }

            @Composable
            fun SettingsWideOrCompactLayout(wideLayout: Boolean) {
            if (wideLayout) {
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
                        categories.forEachIndexed { index, category ->
                            val isSelected = selectedWideCategoryIndex == index
                            val selectCategory = { selectedWideCategoryIndex = index }

                            // M3 Expressive Side Menu Item
                            Surface(
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                    .clickable { selectCategory() }
                                    .let {
                                        if (index in 0..8) it.withShortcut(categoryKeyMap[index], "${index + 1}") { selectCategory() } else it
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

                    // Right Pane: only the selected category composes — switching categories no
                    // longer means scrolling a list of every category's full content at once.
                    // Also gated on mainContentReady (see above) so its first composition happens
                    // after, not during, the Settings nav transition; its own scale+fade in below
                    // covers that otherwise-abrupt appearance. Pulled into its own function (rather
                    // than inlined here) because AnimatedVisibility's plain overload is otherwise
                    // ambiguous with RowScope.AnimatedVisibility while still lexically inside Row.
                    val selectedCategory = categories.getOrNull(selectedWideCategoryIndex)
                    if (selectedCategory != null) {
                        SettingsWideRightPane(
                            modifier = Modifier.weight(2.5f),
                            visible = mainContentReady,
                            reducedMotion = reducedMotion,
                            motionScheme = motionScheme,
                            dimensions = dimensions,
                            content = selectedCategory.content
                        )
                    }
                }
            } else {
                // --- PHONE LAYOUT: list <-> one category's own page, same scale+fade transition
                // used to open Settings itself (the NavHost's enter/exit transitions in
                // MainActivity.kt) — opening uses the forward (enter/exit) spec, closing (back)
                // uses the pop spec.
                AnimatedContent(
                    targetState = openCategoryId,
                    transitionSpec = {
                        if (reducedMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else if (targetState != null && initialState == null) {
                            (scaleIn(initialScale = 0.95f, animationSpec = motionScheme.defaultSpatialSpec()) +
                                fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                                (scaleOut(targetScale = 1.05f, animationSpec = motionScheme.defaultSpatialSpec()) +
                                    fadeOut(animationSpec = motionScheme.defaultEffectsSpec()))
                        } else {
                            (scaleIn(initialScale = 1.05f, animationSpec = motionScheme.defaultSpatialSpec()) +
                                fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                                (scaleOut(targetScale = 0.95f, animationSpec = motionScheme.defaultSpatialSpec()) +
                                    fadeOut(animationSpec = motionScheme.defaultEffectsSpec()))
                        }
                    },
                    label = "settingsCategoryTransition"
                ) { categoryId ->
                    val category = categories.firstOrNull { it.id == categoryId }
                    if (category == null) {
                        // --- PHONE LAYOUT: one row per category, no dividers; a row opens that category's page ---
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 100.dp)
                        ) {
                            items(categories.size) { index ->
                                val c = categories[index]
                                SettingsCategoryRow(
                                    title = c.title,
                                    subtitle = c.subtitle,
                                    dimensions = dimensions,
                                    onClick = { openCategoryId = c.id }
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
                            category.content()
                        }
                    }
                }
            }
            }

            if (hasComposedSettingsOnce) {
                AnimatedContent(
                    targetState = isWideLayout,
                    transitionSpec = {
                        if (reducedMotion) {
                            EnterTransition.None togetherWith ExitTransition.None
                        } else {
                            (scaleIn(initialScale = 0.95f, animationSpec = motionScheme.defaultSpatialSpec()) +
                                fadeIn(animationSpec = motionScheme.defaultEffectsSpec())) togetherWith
                                (scaleOut(targetScale = 1.05f, animationSpec = motionScheme.defaultSpatialSpec()) +
                                    fadeOut(animationSpec = motionScheme.defaultEffectsSpec()))
                        }
                    },
                    label = "settingsLayoutTransition"
                ) { wideLayout ->
                    SettingsWideOrCompactLayout(wideLayout)
                }
            } else {
                SettingsWideOrCompactLayout(isWideLayout)
            }
        }
    }
}

// Pulled out of SettingsWideOrCompactLayout's Row because the plain (non-scoped) overload of
// AnimatedVisibility is otherwise ambiguous with RowScope.AnimatedVisibility for as long as
// RowScope is still an implicit receiver in the lexical scope of the call.
@Composable
private fun SettingsWideRightPane(
    modifier: Modifier,
    visible: Boolean,
    reducedMotion: Boolean,
    motionScheme: MotionScheme,
    dimensions: StudiareDimensions,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier) {
        AnimatedVisibility(
            visible = visible,
            enter = if (reducedMotion) {
                EnterTransition.None
            } else {
                scaleIn(initialScale = 0.95f, animationSpec = motionScheme.defaultSpatialSpec()) +
                    fadeIn(animationSpec = motionScheme.defaultEffectsSpec())
            },
            exit = ExitTransition.None
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(end = dimensions.paddingLarge, bottom = 100.dp)
            ) {
                content()
            }
        }
    }
}

// Settings categories below are each their own top-level composable (rather than lambdas defined
// inline inside SettingsScreen's giant category list) so that opening Settings only has to
// class-load/JIT the one category actually being shown, not all ten at once — this was real,
// measurable cost on top of everything else fixed in this screen so far.
private fun formatBytes(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.getDefault(), "%.2f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.getDefault(), "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.getDefault(), "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

private fun formatTimestamp(timestamp: Long, context: android.content.Context): String {
    if (timestamp == 0L) return context.getString(R.string.not_available)
    val dateFormat = SimpleDateFormat("MM/dd/yy, h:mm a", Locale.getDefault())
    return dateFormat.format(Date(timestamp))
}

@Composable
private fun AboutCategoryContent(
    viewModel: FlashcardViewModel,
    versionNum: String,
    buildDateString: String,
    lastExportTimestamp: Long,
    lastImportTimestamp: Long
) {
    val context = LocalContext.current
    val dimensions = LocalStudiareDimensions.current
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
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

        SettingsSubsection(getText(R.string.help)) {
            val replayTutorialInteractionSource = remember { MutableInteractionSource() }
            Button(
                onClick = { viewModel.requestShowTutorial() },
                interactionSource = replayTutorialInteractionSource,
                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).pressSquish(replayTutorialInteractionSource),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
            ) {
                Text(getText(R.string.replay_tutorial))
            }
        }

        SettingsSubsection("Device") {
            SettingsInfoRow("Model", "${android.os.Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${android.os.Build.MODEL}", isAlternate = false)
            SettingsInfoRow("Android", "${android.os.Build.VERSION.RELEASE} (API ${android.os.Build.VERSION.SDK_INT})", isAlternate = true)
            SettingsInfoRow("Width size class", windowWidthSizeClass.toString(), isAlternate = false)
            SettingsInfoRow("Height size class", windowHeightSizeClass.toString(), isAlternate = true)
            SettingsInfoRow(getText(R.string.window_size), "${androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp} × ${androidx.compose.ui.platform.LocalConfiguration.current.screenHeightDp} dp", isAlternate = false)

        }

        SettingsSubsection("Backup") {
            SettingsInfoRow("Last Export", formatTimestamp(lastExportTimestamp, context), isAlternate = false)
            SettingsInfoRow("Last Import", formatTimestamp(lastImportTimestamp, context), isAlternate = true)

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

@Composable
private fun DeleteAllDecksCategoryContent(showDeleteAllDecksDialogState: MutableState<Boolean>) {
    val dimensions = LocalStudiareDimensions.current
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
    var showDeleteAllDecksDialog by showDeleteAllDecksDialogState

    Column {
        val deleteAllDecksInteractionSource = remember { MutableInteractionSource() }
        Button(
            onClick = { showDeleteAllDecksDialog = true },
            interactionSource = deleteAllDecksInteractionSource,
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).pressSquish(deleteAllDecksInteractionSource),
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

@Composable
private fun TroubleshootingCategoryContent(viewModel: FlashcardViewModel, showFieldMapperState: MutableState<Boolean>) {
    val dimensions = LocalStudiareDimensions.current
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
    val showSizeOverlay by viewModel.showSizeOverlay.collectAsState()
    var showFieldMapper by showFieldMapperState

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingSwitchItem(getText(R.string.show_size_overlay), getText(R.string.show_size_overlay_desc), showSizeOverlay) { viewModel.setShowSizeOverlay(it) }
        var showSavedPreferences by remember { mutableStateOf(false) }
        val savedPrefsInteractionSource = remember { MutableInteractionSource() }
        Button(
            onClick = { showSavedPreferences = true },
            interactionSource = savedPrefsInteractionSource,
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).pressSquish(savedPrefsInteractionSource),
            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
        ) {
            Text(getText(R.string.manage_saved_preferences))
        }
        if (showSavedPreferences) {
            SavedPreferencesDialog(viewModel = viewModel, onDismiss = { showSavedPreferences = false })
        }

        Spacer(Modifier.height(dimensions.spacingSmall))

        val forceCrashInteractionSource = remember { MutableInteractionSource() }
        Button(
            onClick = { throw RuntimeException("Test Crash from Settings") },
            interactionSource = forceCrashInteractionSource,
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).pressSquish(forceCrashInteractionSource),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
        ) {
            Text(getText(R.string.force_crash))
        }

        Spacer(Modifier.height(dimensions.spacingSmall))

        val fieldMapperInteractionSource = remember { MutableInteractionSource() }
        Button(
            onClick = { showFieldMapper = true },
            interactionSource = fieldMapperInteractionSource,
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).pressSquish(fieldMapperInteractionSource),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
        ) {
            Text(getText(R.string.field_mapper))
        }
    }
}

@Composable
private fun InfoCategoryContent(
    tagCount: Int,
    totalDecks: Int,
    totalSets: Int,
    totalCards: Int,
    collectionCount: Int,
    activeSessionCount: Int,
    cardStats: FlashcardViewModel.CardStats,
    storageUsage: Pair<Long, Long>
) {
    Column {
        SettingsSubsection("Collection") {
            SettingsInfoRow(getText(R.string.total_decks), "$totalDecks", isAlternate = false)
            SettingsInfoRow(getText(R.string.total_sets), "$totalSets", isAlternate = true)
            SettingsInfoRow(getText(R.string.total_cards), "$totalCards", isAlternate = false)
            SettingsInfoRow("Tags", "$tagCount", isAlternate = true)
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

@Composable
private fun TagsCategoryContent(
    viewModel: FlashcardViewModel,
    tags: List<TagDefinition>,
    tagToEditState: MutableState<TagDefinition?>,
    tagToCleanupState: MutableState<String?>,
    showTagEditorState: MutableState<Boolean>
) {
    val dimensions = LocalStudiareDimensions.current
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
    val sortTagsByDateCreated by viewModel.sortTagsByDateCreated.collectAsState()
    var tagToEdit by tagToEditState
    var tagToCleanup by tagToCleanupState
    var showTagEditor by showTagEditorState

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
        Button(
            onClick = { tagToEdit = null; showTagEditor = true },
            interactionSource = createTagInteractionSource,
            modifier = Modifier.align(Alignment.CenterHorizontally).fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).pressSquish(createTagInteractionSource),
            shape = RoundedCornerShape(dimensions.cornerRadiusButton)
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(Modifier.width(dimensions.spacingSmall))
            Text(getText(R.string.tag_create_new))
        }
    }
}

@Composable
private fun AudioVoiceCategoryContent(
    viewModel: FlashcardViewModel,
    currentWhisperSize: WhisperModelSize?,
    whisperSizeToDownloadState: MutableState<WhisperModelSize?>,
    whisperSizeToDeleteState: MutableState<WhisperModelSize?>,
    downloadingWhisperSizeState: MutableState<WhisperModelSize?>,
    whisperDownloadProgressState: MutableState<Float>,
    whisperDownloadJobState: MutableState<kotlinx.coroutines.Job?>,
    languageToDownloadState: MutableState<String?>,
    languageToDeleteState: MutableState<String?>,
    showDownloadAllConfirmState: MutableState<Boolean>,
    showDeleteAllConfirmState: MutableState<Boolean>
) {
    val dimensions = LocalStudiareDimensions.current
    val detectedLanguages = viewModel.getUniqueDeckLanguages()
    val downloadedLanguages by viewModel.downloadedHdLanguages.collectAsState()
    val voiceDownload by viewModel.voiceDownload.collectAsState()
    var whisperSizeToDownload by whisperSizeToDownloadState
    var whisperSizeToDelete by whisperSizeToDeleteState
    var downloadingWhisperSize by downloadingWhisperSizeState
    var whisperDownloadProgress by whisperDownloadProgressState
    var whisperDownloadJob by whisperDownloadJobState
    var languageToDownload by languageToDownloadState
    var languageToDelete by languageToDeleteState
    var showDownloadAllConfirm by showDownloadAllConfirmState
    var showDeleteAllConfirm by showDeleteAllConfirmState

    Column {
        Text(
            getText(R.string.audio_output),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = dimensions.paddingSmall)
        )
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
                    // While voices download, this becomes Cancel downloads: it stops at once and keeps nothing
                    val voicesBusy = voiceDownload != null
                    Button(
                        onClick = { if (voicesBusy) viewModel.cancelVoiceDownload() else showDownloadAllConfirm = true },
                        interactionSource = downloadAllInteractionSource,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).pressSquish(downloadAllInteractionSource),
                        enabled = voicesBusy || detectedLanguages.any { !downloadedLanguages.contains(it) },
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(if (voicesBusy) R.string.cancel_downloads else R.string.download_all))
                    }

                    val deleteAllLangInteractionSource = remember { MutableInteractionSource() }
                    OutlinedButton(
                        onClick = { showDeleteAllConfirm = true },
                        interactionSource = deleteAllLangInteractionSource,
                        modifier = Modifier.weight(1f).defaultMinSize(minHeight = 56.dp).pressSquish(deleteAllLangInteractionSource),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        enabled = downloadedLanguages.isNotEmpty(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.delete_all))
                    }
                }
            }
        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

        Text(
            getText(R.string.speech_recognition_category),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = dimensions.paddingSmall)
        )
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

@Composable
private fun CustomizationCategoryContent(
    viewModel: FlashcardViewModel,
    onShowCustomThemeDialog: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val windowHeightSizeClass = LocalWindowHeightSizeClass.current
    val themeMode by viewModel.themeMode.collectAsState()
    val spacingMode by viewModel.spacingMode.collectAsState()
    val storedDeckSetsDisplayMode by viewModel.deckSetsDisplayMode.collectAsState()
    val storedTreeLargeScreenLayout by viewModel.treeLargeScreenLayout.collectAsState()
    val deckSetsDisplayMode = DeckSetsDisplayMode.resolve(storedDeckSetsDisplayMode, windowWidthSizeClass, windowHeightSizeClass)
    val treeLargeScreenLayout = DeckSetsDisplayMode.resolveTreeDirection(storedTreeLargeScreenLayout, windowWidthSizeClass, windowHeightSizeClass)
    val gridLoadingIndicator by viewModel.gridLoadingIndicator.collectAsState()
    val treeLoadingIndicator by viewModel.treeLoadingIndicator.collectAsState()
    val reduceMotion by viewModel.reduceMotion.collectAsState()
    val disableCardFlipAnimations by viewModel.disableCardFlipAnimations.collectAsState()
    val alwaysOpenBulkEditor by viewModel.alwaysOpenBulkEditor.collectAsState()

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
                                onShowCustomThemeDialog()
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
        SettingsSegmentedSetting(
            isWideScreen = isWideSettingsLayout,
            description = getText(R.string.always_open_bulk_editor_desc),
            title = { Text(getText(R.string.always_open_bulk_editor), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(bottom = 4.dp)) }
        ) { segmentedModifier ->
            SingleChoiceSegmentedButtonRow(modifier = segmentedModifier) {
                val options = listOf(false to R.string.simple_editor, true to R.string.bulk_editor)
                options.forEachIndexed { index, (isBulk, labelRes) ->
                    SegmentedButton(
                        selected = alwaysOpenBulkEditor == isBulk,
                        onClick = { viewModel.setAlwaysOpenBulkEditor(isBulk) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size)
                    ) {
                        Text(getText(labelRes), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupSyncCategoryContent(
    viewModel: FlashcardViewModel,
    isImportingJson: Boolean,
    jsonPickerLauncher: androidx.activity.result.ActivityResultLauncher<String>
) {
    val dimensions = LocalStudiareDimensions.current
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val context = LocalContext.current
    val isBackendConnected by viewModel.isBackendConnected.collectAsState()
    val isUserAnonymous by viewModel.isUserAnonymous.collectAsState()
    val backendProjectId by viewModel.backendProjectId.collectAsState()
    val isSyncSetupPending by viewModel.isSyncSetupPending.collectAsState()
    val isSyncing by viewModel.isSyncing.collectAsState()
    val hasPendingChanges by viewModel.hasPendingChanges.collectAsState()
    val syncDecksAndCards by viewModel.syncDecksAndCards.collectAsState()
    val syncReviewData by viewModel.syncReviewData.collectAsState()
    val syncSavedSessions by viewModel.syncSavedSessions.collectAsState()
    val syncOnlyOnWifi by viewModel.syncOnlyOnWifi.collectAsState()

    val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded

    Text(
        getText(R.string.cloud_sync_section_title),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = dimensions.paddingSmall)
    )

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
                    Button(
                        onClick = { showAuthDialog = true },
                        interactionSource = connectInteractionSource,
                        modifier = Modifier.fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).pressSquish(connectInteractionSource),
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
                                        FilledTonalButton(
                                            onClick = { viewModel.triggerSync() },
                                            interactionSource = syncInteractionSource,
                                            modifier = Modifier.fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f).defaultMinSize(minHeight = 56.dp).pressSquish(syncInteractionSource),
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
                        .pressSquish(syncDecksInteractionSource)
                        .clickable(
                            interactionSource = syncDecksInteractionSource,
                            indication = LocalIndication.current
                        ) { viewModel.setSyncDecksAndCards(!syncDecksAndCards) }
                )

                // Toggle 2: Review Data
                val syncReviewInteractionSource = remember { MutableInteractionSource() }
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
                        .pressSquish(syncReviewInteractionSource)
                        .clickable(
                            interactionSource = syncReviewInteractionSource,
                            indication = LocalIndication.current
                        ) { viewModel.setSyncReviewData(!syncReviewData) }
                )

                // Toggle 3: Saved Sessions
                val syncSessionsInteractionSource = remember { MutableInteractionSource() }
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
                        .pressSquish(syncSessionsInteractionSource)
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
                            .pressSquish(syncWifiInteractionSource)
                            .clickable(
                                interactionSource = syncWifiInteractionSource,
                                indication = LocalIndication.current
                            ) { viewModel.setSyncOnlyOnWifi(!syncOnlyOnWifi) }
                    )
                }
            }
        }
    }

    AutomaticBackupsSection(viewModel)
}

/**
 * Scheduled automatic export of all decks to a local folder or the user's own Google Drive (a
 * single destination, chosen via a segmented button — not independent toggles) — distinct from
 * the Firebase sync UI above (hence rendered regardless of backend-connection state). None of
 * this content is hidden behind the master toggle: that toggle only controls whether the periodic
 * schedule runs, so the interval/destination/retention are always visible and configurable, and
 * "Back up now" always works regardless of its state. The Drive consent flow mirrors the existing
 * SAF `CreateDocument` launchers elsewhere in the app (an Activity-scoped launcher that resolves a
 * pending intent only when first-time consent is needed).
 */
@Composable
private fun AutomaticBackupsSection(viewModel: FlashcardViewModel) {
    val dimensions = LocalStudiareDimensions.current
    val windowWidthSizeClass = LocalWindowWidthSizeClass.current
    val isWideSettingsLayout = windowWidthSizeClass >= WindowWidthSizeClass.Expanded
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val scheduledBackupEnabled by viewModel.scheduledBackupEnabled.collectAsState()
    val intervalUnit by viewModel.scheduledBackupIntervalUnit.collectAsState()
    val intervalCount by viewModel.scheduledBackupIntervalCount.collectAsState()
    val backupDestination by viewModel.backupDestination.collectAsState()
    val localBackupFolderUri by viewModel.localBackupFolderUri.collectAsState()
    val backupRetentionCount by viewModel.backupRetentionCount.collectAsState()
    val backupLastRunAt by viewModel.backupLastRunAt.collectAsState()
    val backupLastRunSuccess by viewModel.backupLastRunSuccess.collectAsState()
    val driveBackupAccountEmail by viewModel.driveBackupAccountEmail.collectAsState()
    val driveBackupFolderId by viewModel.driveBackupFolderId.collectAsState()
    val driveBackupNeedsReauth by viewModel.driveBackupNeedsReauth.collectAsState()

    var isConnectingDrive by remember { mutableStateOf(false) }

    val intervalUnitOptions = remember { listOf(BackupIntervalUnit.HOURS, BackupIntervalUnit.DAYS, BackupIntervalUnit.WEEKS, BackupIntervalUnit.MONTHS) }

    fun finishDriveConnect(authResult: AuthorizationResult?) {
        val accessToken = authResult?.accessToken
        if (accessToken == null) {
            isConnectingDrive = false
            return
        }
        scope.launch {
            val email = DriveBackupClient.fetchAccountEmail(accessToken)
            val folderId = DriveBackupClient.ensureBackupFolder(accessToken, driveBackupFolderId)
            if (email != null && folderId != null) {
                viewModel.setDriveBackupAccountEmail(email)
                viewModel.setDriveBackupFolderId(folderId)
            }
            isConnectingDrive = false
        }
    }

    val driveConsentLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        val data = result.data
        if (result.resultCode == android.app.Activity.RESULT_OK && data != null) {
            finishDriveConnect(DriveBackupClient.resultFromIntent(context, data))
        } else {
            isConnectingDrive = false
        }
    }

    fun startDriveConnect() {
        isConnectingDrive = true
        scope.launch {
            val authResult = DriveBackupClient.authorize(context)
            if (authResult == null) {
                isConnectingDrive = false
                return@launch
            }
            if (authResult.hasResolution()) {
                val pendingIntent = authResult.pendingIntent
                if (pendingIntent != null) {
                    driveConsentLauncher.launch(IntentSenderRequest.Builder(pendingIntent).build())
                } else {
                    isConnectingDrive = false
                }
            } else {
                finishDriveConnect(authResult)
            }
        }
    }

    val folderPickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            viewModel.setLocalBackupFolderUri(uri.toString())
        }
    }

    val manualBackupWorkInfosLiveData = remember(context) {
        WorkManager.getInstance(context).getWorkInfosForUniqueWorkLiveData(ScheduledBackupWorker.MANUAL_WORK_NAME)
    }
    val manualBackupWorkInfos by manualBackupWorkInfosLiveData.observeAsState(emptyList())
    val isBackupRunning = manualBackupWorkInfos.any { it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.RUNNING }

    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

    Text(
        getText(R.string.automatic_backups),
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = dimensions.paddingSmall)
    )
    Column {
        Text(
            getText(R.string.automatic_backups_desc),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 4.dp)
        )

        SettingSwitchItem(
            title = getText(R.string.scheduled_backup_enable),
            description = "",
            checked = scheduledBackupEnabled,
            onCheckedChange = { viewModel.setScheduledBackupEnabled(it) }
        )

        Column {
            Spacer(Modifier.height(dimensions.spacingSmall))
            Text(getText(R.string.backup_interval), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(dimensions.spacingSmall))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                BackupIntervalStepper(value = intervalCount, onValueChange = { viewModel.setScheduledBackupIntervalCount(it) })
                ExpressiveDropdownMenuField(
                    options = intervalUnitOptions,
                    selectedOption = intervalUnit,
                    optionLabel = {
                        when (it) {
                            BackupIntervalUnit.HOURS -> getText(R.string.backup_interval_hours)
                            BackupIntervalUnit.WEEKS -> getText(R.string.backup_interval_weeks)
                            BackupIntervalUnit.MONTHS -> getText(R.string.backup_interval_months)
                            else -> getText(R.string.backup_interval_days)
                        }
                    },
                    onSelect = { viewModel.setScheduledBackupIntervalUnit(it) },
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(Modifier.height(dimensions.spacingMedium))

            Text(getText(R.string.backup_destination), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(dimensions.spacingSmall))

            // Destination choice + whichever controls finish setting it up — grouped by a tonal
            // card (same soft-grouping pattern as the Firebase "data synced" status block above in
            // this screen) rather than a divider, since picking a destination and configuring it
            // is one step, not two separate sections.
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(dimensions.paddingMedium)) {
                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = backupDestination == BackupDestination.LOCAL,
                            onClick = { viewModel.setBackupDestination(BackupDestination.LOCAL) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2)
                        ) { Text(getText(R.string.local_backup), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                        SegmentedButton(
                            selected = backupDestination == BackupDestination.DRIVE,
                            onClick = { viewModel.setBackupDestination(BackupDestination.DRIVE) },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2)
                        ) { Text(getText(R.string.google_drive_backup), maxLines = 1, overflow = TextOverflow.Ellipsis) }
                    }
                    Spacer(Modifier.height(dimensions.spacingMedium))

                    if (backupDestination == BackupDestination.LOCAL) {
                        val folderName = localBackupFolderUri?.let { uriString ->
                            runCatching { DocumentFile.fromTreeUri(context, android.net.Uri.parse(uriString))?.name }.getOrNull()
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedButton(
                                onClick = { folderPickerLauncher.launch(null) },
                                shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                            ) { Text(getText(R.string.choose_backup_folder)) }
                            Text(
                                folderName ?: getText(R.string.backup_folder_not_chosen),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    } else {
                        when {
                            isConnectingDrive -> Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = dimensions.spacingSmall)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                Spacer(Modifier.width(dimensions.spacingSmall))
                                Text(getText(R.string.connect_google_drive))
                            }
                            driveBackupNeedsReauth -> {
                                // The explicit button that launches the Google Drive consent/setup flow,
                                // paired on the same row with why it's needed again.
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Button(
                                        onClick = { startDriveConnect() },
                                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                    ) { Text(getText(R.string.connect_google_drive)) }
                                    Text(
                                        getText(R.string.drive_needs_reauth),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            driveBackupAccountEmail == null -> {
                                // The explicit button that launches the Google Drive consent/setup flow.
                                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                    Button(
                                        onClick = { startDriveConnect() },
                                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                    ) { Text(getText(R.string.connect_google_drive)) }
                                }
                            }
                            else -> {
                                Text(
                                    driveBackupAccountEmail.orEmpty(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    getText(R.string.drive_disconnect_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = dimensions.spacingSmall)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.disconnectDriveBackup() },
                                    modifier = Modifier.fillMaxWidth(if (isWideSettingsLayout) 0.5f else 1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                                ) { Text(getText(R.string.disconnect)) }
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(dimensions.spacingMedium))

            Text(getText(R.string.retention_header), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                getText(R.string.retention_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(dimensions.spacingSmall))
            BackupIntervalStepper(
                value = backupRetentionCount,
                onValueChange = { viewModel.setBackupRetentionCount(it) },
                min = BackupRetention.UNLIMITED,
                max = 50,
                skipZero = true,
                valueLabel = { if (it == BackupRetention.UNLIMITED) getText(R.string.backup_retention_unlimited) else it.toString() }
            )
            Spacer(Modifier.height(dimensions.spacingMedium))

            val backUpNowInteractionSource = remember { MutableInteractionSource() }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text(getText(R.string.last_backup_status), style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(dimensions.spacingSmall))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        Text(formatTimestamp(backupLastRunAt, context), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        when (backupLastRunSuccess) {
                            true -> Icon(Icons.Default.CheckCircle, contentDescription = getText(R.string.backup_status_success), tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                            false -> Icon(Icons.Default.Error, contentDescription = getText(R.string.backup_status_failed), tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            null -> {}
                        }
                    }
                }
                FilledTonalButton(
                    onClick = { viewModel.triggerScheduledBackupNow() },
                    enabled = !isBackupRunning,
                    interactionSource = backUpNowInteractionSource,
                    modifier = Modifier.defaultMinSize(minHeight = 56.dp).pressSquish(backUpNowInteractionSource),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) {
                    if (isBackupRunning) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                    }
                    Spacer(Modifier.width(dimensions.spacingSmall))
                    Text(getText(R.string.back_up_now))
                }
            }
        }
    }
}

/** The -/[value]/+ stepper row (see [CountPicker] for the fuller label+slider+presets version this borrows its look from). */
@Composable
private fun BackupIntervalStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    min: Int = 1,
    max: Int = 99,
    // Retention's stepper runs min=-1 (Unlimited) through positive counts — 0 backups kept is a
    // meaningless state (the run that just finished would delete itself), so stepping skips over it.
    skipZero: Boolean = false,
    valueLabel: @Composable (Int) -> String = { it.toString() }
) {
    val dimensions = LocalStudiareDimensions.current
    val nextDown = if (skipZero && value - 1 == 0) value - 2 else value - 1
    val nextUp = if (skipZero && value + 1 == 0) value + 2 else value + 1
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
        TooltipFilledTonalIconButton(description = getText(R.string.decrease), onClick = { if (value > min) onValueChange(nextDown) }, enabled = value > min) {
            Icon(Icons.Default.Remove, getText(R.string.decrease))
        }
        Surface(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                text = valueLabel(value),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = dimensions.paddingMedium, vertical = dimensions.paddingSmall)
            )
        }
        TooltipFilledTonalIconButton(description = getText(R.string.increase), onClick = { if (value < max) onValueChange(nextUp) }, enabled = value < max) {
            Icon(Icons.Default.Add, getText(R.string.increase))
        }
    }
}
