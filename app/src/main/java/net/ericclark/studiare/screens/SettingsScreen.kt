package net.ericclark.studiare.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import net.ericclark.studiare.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.components.TagChip
import net.ericclark.studiare.components.TagCleanupDialog
import net.ericclark.studiare.components.TagEditorDialog
import net.ericclark.studiare.ui.theme.*
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import net.ericclark.studiare.BuildConfig
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.ui.res.stringResource
import net.ericclark.studiare.R
import androidx.compose.ui.window.Dialog

@Composable
fun SettingsScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
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
    var tagsExpanded by rememberSaveable { mutableStateOf(false) }
    var showTagEditor by remember { mutableStateOf(false) }
    var tagToEdit by remember { mutableStateOf<net.ericclark.studiare.data.TagDefinition?>(null) }
    var tagToCleanup by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    // Observables for Language Management
    val detectedLanguages = viewModel.getUniqueDeckLanguages()
    val downloadedLanguages by viewModel.downloadedHdLanguages.collectAsState()

    // Segment Expansion States
    var customizationExpanded by rememberSaveable { mutableStateOf(false) }
    var deleteExpanded by rememberSaveable { mutableStateOf(false) }
    var syncExpanded by rememberSaveable { mutableStateOf(false) }
    var troubleshootExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    var languagesExpanded by rememberSaveable { mutableStateOf(false) }

    // Dialog States
    var showDeleteAllDecksDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomThemeDialog by remember { mutableStateOf(false) }

    // Dialog States for Language Management
    var languageToDownload by remember { mutableStateOf<String?>(null) }
    var languageToDelete by remember { mutableStateOf<String?>(null) }
    var showDownloadAllConfirm by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // Configure Google Sign In
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(stringResource(R.string.default_web_client_id)) // Fixed line
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
                            Toast.makeText(context, "Account connected!", Toast.LENGTH_SHORT).show()
                        } else {
                            if (error != null) {
                                Toast.makeText(context, "Connection failed: $error", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }
            } catch (e: ApiException) {
                Toast.makeText(context, "Google sign in failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    // --- Dialogs (Conflict, Delete, Tags, Langs) ---
    if (showConflictDialog) {
        AlertDialog(
            onDismissRequest = { /* Prevent dismissing without choice */ },
            title = { Text("Sync Conflict") },
            text = {
                Text("You have existing local data, but your Google account also has data in the cloud.\n\nHow would you like to proceed?")
            },
            confirmButton = {},
            dismissButton = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.MERGE_KEEP_LOCAL) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Merge (Overwrite Cloud Matches)")
                    }
                    Button(onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.MERGE_KEEP_CLOUD) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Merge (Keep Cloud Matches)")
                    }
                    OutlinedButton(
                        onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.USE_LOCAL_WIPE_CLOUD) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Use Local (Delete All Cloud Data)")
                    }
                    OutlinedButton(
                        onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.USE_CLOUD_WIPE_LOCAL) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Use Cloud (Discard Local)")
                    }
                }
            }
        )
    }

    // Custom Theme Dialog
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
            title = "Delete All Decks?",
            text = "Are you sure you want to delete ALL decks? This action cannot be undone.",
            onConfirm = { viewModel.deleteAllDecks(); showDeleteAllDecksDialog = false },
            onDismiss = { showDeleteAllDecksDialog = false },
            confirmButtonText = "Delete All"
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
            title = "Download Language?",
            text = "Download the high-definition model for ${Locale(languageToDownload!!).displayLanguage}?",
            confirmButtonText = "Download",
            onConfirm = { viewModel.startHdLanguageDownload(context, listOf(languageToDownload!!)); languageToDownload = null },
            onDismiss = { languageToDownload = null }
        )
    }

    if (languageToDelete != null) {
        ConfirmationDialog(
            title = "Delete Language?",
            text = "Delete the downloaded model for ${Locale(languageToDelete!!).displayLanguage}? Audio will revert to the system default.",
            confirmButtonText = "Delete",
            onConfirm = { viewModel.deleteHdLanguage(context, languageToDelete!!); languageToDelete = null },
            onDismiss = { languageToDelete = null }
        )
    }

    if (showDownloadAllConfirm) {
        val missingLanguages = detectedLanguages.filter { !downloadedLanguages.contains(it) }
        ConfirmationDialog(
            title = "Download All?",
            text = "Download ${missingLanguages.size} missing language models?",
            confirmButtonText = "Download All",
            onConfirm = { viewModel.startHdLanguageDownload(context, missingLanguages); showDownloadAllConfirm = false },
            onDismiss = { showDownloadAllConfirm = false }
        )
    }

    if (showDeleteAllConfirm) {
        ConfirmationDialog(
            title = "Delete All Models?",
            text = "Are you sure you want to delete all downloaded HD language models?",
            confirmButtonText = "Delete All",
            onConfirm = { viewModel.deleteAllHdLanguages(context); showDeleteAllConfirm = false },
            onDismiss = { showDeleteAllConfirm = false }
        )
    }

    Scaffold(
        topBar = {
            CustomTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(dimensions.paddingMedium), // Dynamic Padding
            verticalArrangement = Arrangement.spacedBy(dimensions.spacingLarge) // Dynamic Spacing
        ) {
            // 1. Backup & Sync Section
            SettingsCard(dimensions) {
                DialogSection(
                    title = "Backup & Sync",
                    subtitle = if (isUserAnonymous) "Offline Mode" else "Connected as $userEmail",
                    isExpanded = syncExpanded,
                    onToggle = { syncExpanded = !syncExpanded }
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        if (isSyncSetupPending) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(dimensions.spacingMedium))
                            Text("Finishing Sync...", style = MaterialTheme.typography.bodyLarge)
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
                                    "Connect your Google Account to sync your decks across multiple devices and backup your data.",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = dimensions.paddingMedium)
                                )
                                Button(
                                    onClick = { googleSignInClient.signOut().addOnCompleteListener { launcher.launch(googleSignInClient.signInIntent) } },
                                    modifier = Modifier.fillMaxWidth().height(50.dp)
                                ) {
                                    Text("Connect Google Account")
                                }
                            } else {
                                Text(
                                    "Your data is synced to your Google Account.",
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(bottom = dimensions.paddingMedium)
                                )
                                OutlinedButton(
                                    onClick = { viewModel.signOut(); googleSignInClient.signOut() },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Disconnect & Use Local Storage")
                                }
                                Spacer(Modifier.height(dimensions.spacingSmall))
                                Text(
                                    "Disconnecting will switch to local offline storage. Your cloud data remains safe.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // Sync Toggles Section ---
                            HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                            Column(modifier = Modifier.fillMaxWidth()) {
                                Text(
                                    "Sync Preferences",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                                )

                                // Toggle 1: Decks and Cards
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setSyncDecksAndCards(!syncDecksAndCards) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Sync Decks & Cards")
                                        Text(
                                            "Backup flashcards and decks to the cloud",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Switch(
                                        checked = syncDecksAndCards,
                                        onCheckedChange = { viewModel.setSyncDecksAndCards(it) }
                                    )
                                }

                                // Toggle 2: Review Data
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = syncDecksAndCards) { viewModel.setSyncReviewData(!syncReviewData) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Sync Review Data",
                                            color = if (syncDecksAndCards) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                        Text(
                                            "Backup study progress and card statistics",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (syncDecksAndCards) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                    Switch(
                                        checked = syncReviewData,
                                        onCheckedChange = { viewModel.setSyncReviewData(it) },
                                        enabled = syncDecksAndCards
                                    )
                                }

                                // Toggle 3: Saved Sessions
                                val sessionsEnabled = syncDecksAndCards && syncReviewData
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = sessionsEnabled) { viewModel.setSyncSavedSessions(!syncSavedSessions) }
                                        .padding(vertical = 4.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "Sync Saved Sessions",
                                            color = if (sessionsEnabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                        Text(
                                            "Backup active study sessions",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (sessionsEnabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                                        )
                                    }
                                    Switch(
                                        checked = syncSavedSessions,
                                        onCheckedChange = { viewModel.setSyncSavedSessions(it) },
                                        enabled = sessionsEnabled
                                    )
                                }
                                // --- NEW: Data Usage Section ---
                                HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                                Column(modifier = Modifier.fillMaxWidth()) {
                                    Text(
                                        "Data Usage",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(bottom = dimensions.paddingSmall)
                                    )

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { viewModel.setSyncOnlyOnWifi(!syncOnlyOnWifi) }
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Sync Only on WiFi")
                                            Text(
                                                "Pause sync when using mobile data",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Switch(
                                            checked = syncOnlyOnWifi,
                                            onCheckedChange = { viewModel.setSyncOnlyOnWifi(it) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // 2. Manage Downloaded Languages Section
            SettingsCard(dimensions) {
                val downloadedCount = detectedLanguages.count { downloadedLanguages.contains(it) }
                DialogSection(
                    title = "Manage Languages",
                    subtitle = "$downloadedCount / ${detectedLanguages.size} downloaded",
                    isExpanded = languagesExpanded,
                    onToggle = { languagesExpanded = !languagesExpanded }
                ) {
                    Text(
                        "The following languages have been detected in your decks:",
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
                                "No languages detected.",
                                modifier = Modifier.padding(dimensions.paddingMedium),
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            detectedLanguages.forEachIndexed { index, lang ->
                                val isDownloaded = downloadedLanguages.contains(lang)
                                val langName = try { Locale(lang).displayLanguage } catch (e: Exception) { lang }
                                val sizeInfo = viewModel.getFormattedModelSize(lang)

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(dimensions.paddingSmall),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(langName, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                                        Text(sizeInfo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }

                                    if (isDownloaded) {
                                        Icon(Icons.Default.Check, null, tint = Color(0xFF22C55E), modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(dimensions.spacingSmall))
                                        IconButton(onClick = { languageToDelete = lang }) {
                                            Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                        }
                                    } else {
                                        FilledTonalIconButton(onClick = { languageToDownload = lang }) {
                                            Icon(Icons.Default.Download, "Download")
                                        }
                                    }
                                }
                                if (index < detectedLanguages.size - 1) {
                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(dimensions.spacingMedium))

                    Row(horizontalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        Button(
                            onClick = { showDownloadAllConfirm = true },
                            modifier = Modifier.weight(1f),
                            enabled = detectedLanguages.any { !downloadedLanguages.contains(it) }
                        ) {
                            Text("Download All")
                        }
                        OutlinedButton(
                            onClick = { showDeleteAllConfirm = true },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            enabled = downloadedLanguages.isNotEmpty()
                        ) {
                            Text("Delete All")
                        }
                    }
                }
            }

            // 3. Manage Tags Section
            SettingsCard(dimensions) {
                DialogSection(
                    title = "Manage Tags",
                    subtitle = "${tags.size} tags defined",
                    isExpanded = tagsExpanded,
                    onToggle = { tagsExpanded = !tagsExpanded }
                ) {
                    if (tags.isEmpty()) {
                        Text(
                            "No tags created yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            fontStyle = FontStyle.Italic,
                            modifier = Modifier.padding(vertical = dimensions.paddingSmall)
                        )
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            tags.sortedBy { it.name.lowercase() }.forEach { tag ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                                        .padding(dimensions.paddingSmall),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TagChip(text = tag.name, colorHex = tag.color)
                                    Spacer(Modifier.weight(1f))
                                    IconButton(onClick = { tagToCleanup = tag.name }) {
                                        Icon(Icons.Default.Clear, "Remove from cards", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { tagToEdit = tag; showTagEditor = true }) {
                                        Icon(Icons.Default.Edit, "Edit", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                    IconButton(onClick = { viewModel.deleteTagDefinition(tag) }) {
                                        Icon(Icons.Default.Delete, "Delete", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(dimensions.spacingMedium))
                    Button(onClick = { tagToEdit = null; showTagEditor = true }, modifier = Modifier.fillMaxWidth()) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(dimensions.spacingSmall))
                        Text("Create New Tag")
                    }
                }
            }

            // 4. Customization Section (Consolidated)
            SettingsCard(dimensions) {
                val themeName = when(themeMode) { 0 -> "Light"; 1 -> "Dark"; 2 -> "B&W"; 3 -> "Custom"; else -> "Unknown" }
                val spacingName = when(spacingMode) { 0 -> "Compact"; 1 -> "Normal"; 2 -> "Comfortable"; else -> "Unknown" }

                DialogSection(
                    title = "Customization",
                    subtitle = "$themeName • $spacingName",
                    isExpanded = customizationExpanded,
                    onToggle = { customizationExpanded = !customizationExpanded }
                ) {
                    Column {
                        // --- Theme Header ---
                        Text("Theme", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))
                        val themes = listOf(
                            "Light Mode" to 0, "Dark Mode" to 1, "Black & White Mode" to 2, "Custom" to 3)
                        themes.forEach { (name, mode) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.setThemeMode(mode) }.padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = themeMode == mode,
                                    onClick = {
                                        if (mode == ThemeMode.CUSTOM) showCustomThemeDialog = true
                                        else viewModel.setThemeMode(mode)
                                    }
                                )
                                Spacer(Modifier.width(dimensions.spacingSmall))
                                Text(name)

                                if (mode == ThemeMode.CUSTOM && themeMode == ThemeMode.CUSTOM) {
                                    Spacer(Modifier.weight(1f))
                                    TextButton(onClick = { showCustomThemeDialog = true }) {
                                        Text("Edit")
                                    }
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                        // --- Spacing Header ---
                        Text("Spacing", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))
                        val spacings = listOf("Compact" to 0, "Normal" to 1, "Comfortable" to 2)
                        spacings.forEach { (name, mode) ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { viewModel.setSpacingMode(mode) }.padding(vertical = 4.dp)
                            ) {
                                RadioButton(selected = spacingMode == mode, onClick = { viewModel.setSpacingMode(mode) })
                                Spacer(Modifier.width(dimensions.spacingSmall))
                                Column {
                                    Text(name)
                                    val desc = when(mode) { 0 -> "Tighter layout"; 1 -> "Standard Material 3"; 2 -> "Expressive (Airy)"; else -> "" }
                                    Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = dimensions.spacingMedium))

                        // --- Other Header ---
                        Text("Other", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = dimensions.paddingSmall))
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { viewModel.setDisplaySetsUnderDecks(!displaySetsUnderDecks) }.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Display Sets Under Decks")
                                Text("Show sets indented under their parent deck", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(checked = displaySetsUnderDecks, onCheckedChange = { viewModel.setDisplaySetsUnderDecks(it) })
                        }
                    }
                }
            }

            // 5. Delete Decks Section
            SettingsCard(dimensions) {
                DialogSection(
                    title = "Delete All Decks",
                    isExpanded = deleteExpanded,
                    onToggle = { deleteExpanded = !deleteExpanded }
                ) {
                    Button(
                        onClick = { showDeleteAllDecksDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete All Decks")
                    }
                    Text(
                        "This action cannot be undone.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = dimensions.paddingSmall),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // 6. Troubleshooting Section
            SettingsCard(dimensions) {
                DialogSection(
                    title = "Debug",
                    subtitle = "Developer Tools",
                    isExpanded = troubleshootExpanded,
                    onToggle = { troubleshootExpanded = !troubleshootExpanded }
                ) {
                    Button(
                        onClick = { viewModel.setHdAudioPrompted(false); Toast.makeText(context, "HD Audio Prompt Reset", Toast.LENGTH_SHORT).show() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("Reset HD Audio Prompt")
                    }
                }
            }

            // 7. About Section
            val fullVersionInfo = BuildConfig.VERSION_NAME
            val versionNum = fullVersionInfo.split("-")[0]
            SettingsCard(dimensions) {
                DialogSection(
                    title = "About",
                    subtitle = "Version $versionNum",
                    isExpanded = aboutExpanded,
                    onToggle = { aboutExpanded = !aboutExpanded }
                ) {
                    val dateFormat = remember { SimpleDateFormat("MM/dd/yy, h:mm a", Locale.getDefault()) }
                    fun formatTimestamp(timestamp: Long): String = if (timestamp == 0L) "N/A" else dateFormat.format(Date(timestamp))
                    val buildDateString = remember(viewModel.buildTime) { dateFormat.format(Date(viewModel.buildTime)) }

                    Column(verticalArrangement = Arrangement.spacedBy(dimensions.spacingSmall)) {
                        Text("Version: $versionNum")
                        Text("Build Date: $buildDateString")
                        Text("Last Export: ${formatTimestamp(lastExportTimestamp)}")
                        Text("Last Import: ${formatTimestamp(lastImportTimestamp)}")
                    }
                }
            }

            Spacer(Modifier.height(dimensions.spacingLarge * 2))
        }
    }
}

// Helper Composable for Consistent Expressive Styling
@Composable
fun SettingsCard(dimensions: StudiareDimensions, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(dimensions.cornerRadiusLarge), // Dynamic Shape
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = dimensions.cardElevation)
    ) {
        Column(
            modifier = Modifier.padding(dimensions.paddingMedium), // Dynamic Internal Padding
            content = content
        )
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
                Text("Custom Theme", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(16.dp))

                ColorPickerRow("Primary", primary) { primary = it }
                ColorPickerRow("Secondary", secondary) { secondary = it }
                ColorPickerRow("Tertiary", tertiary) { tertiary = it }
                ColorPickerRow("Background", background) { background = it }

                Spacer(Modifier.height(24.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancel") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = { onSave(primary, secondary, tertiary, background) }) {
                        Text("Apply")
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
        net.ericclark.studiare.components.SimpleColorPicker(
            selectedColor = color,
            onColorSelected = onColorChange
        )
    }
}