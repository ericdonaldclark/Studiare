package net.ericclark.studiare.screens

import android.annotation.SuppressLint
import android.app.Activity
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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import net.ericclark.studiare.*
import net.ericclark.studiare.BuildConfig
import net.ericclark.studiare.R
import net.ericclark.studiare.components.SimpleColorPicker
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
    val displaySetsUnderDecks by viewModel.displaySetsUnderDecks.collectAsState()

    // Map Spacing Mode to Dimensions
    val dimensions = when (spacingMode) {
        SpacingMode.COMPACT -> CompactDimensions
        SpacingMode.NORMAL -> NormalDimensions
        else -> ComfortableDimensions
    }

    val lastExportTimestamp by viewModel.lastExportTimestamp.collectAsState()
    val lastImportTimestamp by viewModel.lastImportTimestamp.collectAsState()

    val tags by viewModel.tags.collectAsState()
    var showTagEditor by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<TagDefinition?>(null) }
    var tagToCleanup by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Observables for Language Management
    val detectedLanguages = viewModel.getUniqueDeckLanguages()
    val downloadedLanguages by viewModel.downloadedHdLanguages.collectAsState()

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

    // Configure Google Sign In
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(stringResource(R.string.default_web_client_id))
        .requestEmail()
        .build()

    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)
                account?.idToken?.let { idToken ->
                    val credential = GoogleAuthProvider.getCredential(idToken, null)
                    viewModel.linkGoogleAccount(credential) { success, error ->
                        if (success) {
                            Toast.makeText(context, context.getString(R.string.account_connected), Toast.LENGTH_SHORT).show()
                        } else {
                            if (error != null) {
                                Toast.makeText(context, context.getString(R.string.connection_failed, error), Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: ApiException) {
                Toast.makeText(context, context.getString(R.string.google_sign_in_failed, e.message), Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Dialogs (Conflict, Delete, Tags, Langs) ---
    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissing without choice */ },
            title = { Text(getText(R.string.sync_conflict)) },
            text = { Text(getText(R.string.sync_conflict_desc)) },
            confirmButton = {},
            dismissButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.MERGE_KEEP_LOCAL) }, modifier = Modifier.fillMaxWidth()) {
                        Text(getText(R.string.merge_overwrite_cloud))
                    }
                    Button(onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.MERGE_KEEP_CLOUD) }, modifier = Modifier.fillMaxWidth()) {
                        Text(getText(R.string.merge_keep_cloud))
                    }
                    OutlinedButton(
                        onClick = { showWipeCloudConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text(getText(R.string.use_local_wipe_cloud))
                    }
                    OutlinedButton(
                        onClick = { showWipeLocalConfirm = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(getText(R.string.use_cloud_wipe_local))
                    }
                }
            }
        )
    }

    if (showFieldMapper) {
        AnkiFieldMappingDialog(
            ankiFields = listOf(
                Pair("State", MediaType.PLAIN_TEXT), Pair("Capital", MediaType.PLAIN_TEXT), Pair("StateSnd", MediaType.AUDIO), Pair("CapitalSnd", MediaType.AUDIO), Pair("Map", MediaType.IMAGE),
                Pair("Postal", MediaType.PLAIN_TEXT)),
            originalAnkiName = "Test",
            onDismiss = { showFieldMapper = false },
            onSaveMapping = { configs ->
                // Debug output to logcat
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

    // --- Data-Driven Category Definitions ---
    val categories = listOf(
        SettingCategoryData(
            id = "customization",
            title = getText(R.string.customization),
            subtitle = stringResource(R.string.customization_subtitle, themeName, spacingName),
            content = {
                Column {
                    // --- Theme Header ---
                    Text(getText(R.string.theme), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth().padding(bottom = dimensions.spacingMedium)) {
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
                                        Icon(Icons.Default.Edit, contentDescription = "Edit Custom Theme", modifier = Modifier.size(SegmentedButtonDefaults.IconSize))
                                    } else {
                                        SegmentedButtonDefaults.Icon(active = themeMode == mode)
                                    }
                                }
                            ) {
                                Text(name, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingSmall))

                    // --- Spacing Header ---
                    Text(getText(R.string.spacing), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))

                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
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
                    val currentSpacingDesc = when(spacingMode) {
                        0 -> getText(R.string.tighter_layout)
                        1 -> getText(R.string.standard_material_3)
                        2 -> getText(R.string.expressive_airy)
                        else -> ""
                    }
                    Text(currentSpacingDesc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = 8.dp))

                    HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                    // --- Other Header ---
                    Text(getText(R.string.other), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))

                    val displaySetsInteractionSource = remember { MutableInteractionSource() }
                    val isDisplaySetsPressed by displaySetsInteractionSource.collectIsPressedAsState()
                    val displaySetsScale by animateFloatAsState(
                        targetValue = if (isDisplaySetsPressed) 0.95f else 1f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                        label = "displaySetsSquish"
                    )
                    ListItem(
                        headlineContent = { Text(getText(R.string.display_sets_under_decks)) },
                        supportingContent = { Text(getText(R.string.display_sets_under_decks_desc)) },
                        trailingContent = {
                            Switch(
                                checked = syncDecksAndCards,
                                onCheckedChange = { viewModel.setDisplaySetsUnderDecks(it) }
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier
                            .fillMaxWidth()
                            .scale(displaySetsScale)
                            .clickable(
                                interactionSource = displaySetsInteractionSource,
                                indication = LocalIndication.current
                            ) { viewModel.setDisplaySetsUnderDecks(!displaySetsUnderDecks) }
                    )
                }
            }
        ),
        SettingCategoryData(
            id = "backup",
            title = getText(R.string.backup_and_sync),
            subtitle = if (isUserAnonymous) getText(R.string.offline_mode) else stringResource(R.string.connected_as, userEmail ?: ""),
            content = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (isSyncSetupPending) {
                        CircularWavyProgressIndicator()
                        Spacer(Modifier.height(dimensions.spacingMedium))
                        Text(getText(R.string.finishing_sync), style = MaterialTheme.typography.bodyLarge)
                    } else {
                        Icon(
                            imageVector = if (isUserAnonymous) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = if (isUserAnonymous) Color.Gray else MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.height(dimensions.spacingMedium))

                        if (isUserAnonymous) {
                            Text(
                                getText(R.string.connect_google_account_desc),
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
                                onClick = { googleSignInClient.signOut().addOnCompleteListener { launcher.launch(googleSignInClient.signInIntent) } },
                                interactionSource = connectInteractionSource,
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(connectScale)
                            ) {
                                Text(getText(R.string.connect_google_account))
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
                                                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(syncScale)
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
                            val disconnectInteractionSource = remember { MutableInteractionSource() }
                            val isDisconnectPressed by disconnectInteractionSource.collectIsPressedAsState()
                            val disconnectScale by animateFloatAsState(
                                targetValue = if (isDisconnectPressed) 0.95f else 1f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                                label = "disconnectSquish"
                            )
                            OutlinedButton(
                                onClick = { viewModel.signOut(); googleSignInClient.signOut() },
                                interactionSource = disconnectInteractionSource,
                                modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(disconnectScale),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Text(getText(R.string.disconnect_local_storage))
                            }
                            Spacer(Modifier.height(dimensions.spacingSmall))
                            Text(
                                getText(R.string.disconnect_desc),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
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

                        // M3 Expressive Update: Native ListItem instead of a custom Row
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
                        val sessionsEnabled = syncDecksAndCards && syncReviewData
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
                                                FilledTonalIconButton(
                                                    onClick = { languageToDelete = lang },
                                                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                                                        containerColor = MaterialTheme.colorScheme.errorContainer,
                                                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                                                    )
                                                ) { Icon(Icons.Default.Delete, getText(R.string.delete)) }
                                            } else {
                                                FilledTonalIconButton(onClick = { languageToDownload = lang }) {
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
                            enabled = detectedLanguages.any { !downloadedLanguages.contains(it) }
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
                            enabled = downloadedLanguages.isNotEmpty()
                        ) {
                            Text(getText(R.string.delete_all))
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
                Column {
                    if (tags.isEmpty()) {
                        Text(
                            getText(R.string.no_tags_created),
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(vertical = dimensions.paddingSmall)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            tags.sortedBy { it.name.lowercase() }.forEach { tag ->
                                ListItem(
                                    headlineContent = { TagChip(text = tag.name, colorHex = tag.color) },
                                    trailingContent = {
                                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                            FilledTonalIconButton(onClick = { tagToCleanup = tag.name }) {
                                                Icon(Icons.Default.Clear, getText(R.string.tag_remove_from_cards_icon))
                                            }
                                            FilledTonalIconButton(onClick = { tagToEdit = tag; showTagEditor = true }) {
                                                Icon(Icons.Default.Edit, getText(R.string.edit))
                                            }
                                            FilledTonalIconButton(
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
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(createTagScale)
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
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(deleteAllDecksScale),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
        SettingCategoryData(
            id = "troubleshooting",
            title = getText(R.string.debug),
            subtitle = getText(R.string.developer_tools),
            content = {
                Column {
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
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp).scale(resetAudioScale),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
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
                        modifier = Modifier.fillMaxWidth().scale(forceCrashScale),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
                        modifier = Modifier.fillMaxWidth().scale(fieldMapperScale),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
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
                Column { // Removed spacedBy since ListItem handles its own padding
                    ListItem(
                        headlineContent = { Text(getText(R.string.total_decks)) },
                        trailingContent = { Text("$totalDecks", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text(getText(R.string.total_sets)) },
                        trailingContent = { Text("$totalSets", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text(getText(R.string.total_cards)) },
                        trailingContent = { Text("$totalCards", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        ),
        SettingCategoryData(
            id = "about",
            title = getText(R.string.about),
            subtitle = stringResource(R.string.app_info),
            content = {
                Column {
                    ListItem(
                        headlineContent = { Text("App Version") },
                        supportingContent = { Text(stringResource(R.string.version_label, versionNum)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text("Build Date") },
                        supportingContent = { Text(stringResource(R.string.build_date_label, buildDateString)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text("Last Export") },
                        supportingContent = { Text(formatTimestamp(lastExportTimestamp)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text("Last Import") },
                        supportingContent = { Text(formatTimestamp(lastImportTimestamp)) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        )
    )

    // --- Main UI Scaffold ---
    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text(getText(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
                            val isSelected = firstVisibleIndex.value == index

                            // M3 Expressive Side Menu Item
                            Surface(
                                shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                contentColor = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape)
                                    .clickable {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(index)
                                        }
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

                    // Right Pane: Expanded Settings Content
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.weight(2.5f),
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
                                category.content()
                            }
                        }
                    }
                }
            } else {
                // --- PHONE LAYOUT (Single Column, Accordion) ---
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
                            category.content()
                        }
                    }
                }
            }
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

    Dialog(onDismissRequest = onDismiss) {
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
                    TextButton(onClick = onDismiss) { Text(getText(R.string.cancel)) }
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
                        modifier = Modifier.scale(applyScale)
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