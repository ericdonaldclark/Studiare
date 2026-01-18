package net.ericclark.studiare.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import net.ericclark.studiare.ConfirmationDialog
import net.ericclark.studiare.ConflictResolutionStrategy
import net.ericclark.studiare.CustomTopAppBar
import net.ericclark.studiare.DialogSection
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.R
import net.ericclark.studiare.data.*
import net.ericclark.studiare.components.TagChip
import net.ericclark.studiare.components.TagCleanupDialog
import net.ericclark.studiare.components.TagEditorDialog
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SettingsScreen(navController: NavController, viewModel: net.ericclark.studiare.FlashcardViewModel) {
    val isUserAnonymous by viewModel.isUserAnonymous.collectAsState()
    val userEmail by viewModel.userEmail.collectAsState()
    val showConflictDialog by viewModel.showConflictDialog.collectAsState()
    val isSyncSetupPending by viewModel.isSyncSetupPending.collectAsState()
    val themeMode by viewModel.themeMode.collectAsState()
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
    var themeExpanded by rememberSaveable { mutableStateOf(false) }
    var deleteExpanded by rememberSaveable { mutableStateOf(false) }
    var syncExpanded by rememberSaveable { mutableStateOf(true) }
    var troubleshootExpanded by rememberSaveable { mutableStateOf(false) }
    var aboutExpanded by rememberSaveable { mutableStateOf(false) }
    var languagesExpanded by rememberSaveable { mutableStateOf(false) }

    // Dialog States
    var showDeleteAllDecksDialog by rememberSaveable { mutableStateOf(false) }

    // Dialog States for Language Management
    var languageToDownload by remember { mutableStateOf<String?>(null) }
    var languageToDelete by remember { mutableStateOf<String?>(null) }
    var showDownloadAllConfirm by remember { mutableStateOf(false) }
    var showDeleteAllConfirm by remember { mutableStateOf(false) }

    // Configure Google Sign In
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
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

    // Conflict Dialog
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
                    Button(
                        onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.MERGE_KEEP_LOCAL) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Merge (Overwrite Cloud Matches)")
                    }
                    Button(
                        onClick = { viewModel.resolveConflict(ConflictResolutionStrategy.MERGE_KEEP_CLOUD) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
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

    if (showDeleteAllDecksDialog) {
        ConfirmationDialog(
            title = "Delete All Decks?",
            text = "Are you sure you want to delete ALL decks? This action cannot be undone.",
            onConfirm = {
                viewModel.deleteAllDecks()
                showDeleteAllDecksDialog = false
            },
            onDismiss = {
                showDeleteAllDecksDialog = false
            },
            confirmButtonText = "Delete All"
        )
    }

    // --- Tag Dialogs ---
    if (showTagEditor) {
        TagEditorDialog(
            tag = tagToEdit,
            existingTags = tags,
            onDismiss = { showTagEditor = false; tagToEdit = null },
            onSave = { name, color ->
                if (tagToEdit == null) {
                    // Create
                    val newTag = TagDefinition(
                        name = name,
                        color = color
                    )
                    viewModel.saveTagDefinition(newTag)
                } else {
                    // Edit (Rename handles both name and color updates via ViewModel logic)
                    val updatedTag = tagToEdit!!.copy(name = name, color = color)
                    viewModel.renameTag(updatedTag, tagToEdit!!.name)
                }
                showTagEditor = false
                tagToEdit = null
            }
        )
    }

    if (tagToCleanup != null) {
        TagCleanupDialog(
            tagName = tagToCleanup!!,
            viewModel = viewModel,
            onDismiss = { tagToCleanup = null }
        )
    }

    // --- Language Confirmation Dialogs ---

    if (languageToDownload != null) {
        ConfirmationDialog(
            title = "Download Language?",
            text = "Download the high-definition model for ${Locale(languageToDownload!!).displayLanguage}?",
            confirmButtonText = "Download",
            onConfirm = {
                viewModel.startHdLanguageDownload(context, listOf(languageToDownload!!))
                languageToDownload = null
            },
            onDismiss = { languageToDownload = null }
        )
    }

    if (languageToDelete != null) {
        ConfirmationDialog(
            title = "Delete Language?",
            text = "Delete the downloaded model for ${Locale(languageToDelete!!).displayLanguage}? Audio will revert to the system default.",
            confirmButtonText = "Delete",
            onConfirm = {
                viewModel.deleteHdLanguage(context, languageToDelete!!)
                languageToDelete = null
            },
            onDismiss = { languageToDelete = null }
        )
    }

    if (showDownloadAllConfirm) {
        val missingLanguages = detectedLanguages.filter { !downloadedLanguages.contains(it) }
        ConfirmationDialog(
            title = "Download All?",
            text = "Download ${missingLanguages.size} missing language models?",
            confirmButtonText = "Download All",
            onConfirm = {
                viewModel.startHdLanguageDownload(context, missingLanguages)
                showDownloadAllConfirm = false
            },
            onDismiss = { showDownloadAllConfirm = false }
        )
    }

    if (showDeleteAllConfirm) {
        ConfirmationDialog(
            title = "Delete All Models?",
            text = "Are you sure you want to delete all downloaded HD language models?",
            confirmButtonText = "Delete All",
            onConfirm = {
                viewModel.deleteAllHdLanguages(context)
                showDeleteAllConfirm = false
            },
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
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Backup & Sync Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                Spacer(Modifier.height(16.dp))
                                Text(
                                    "Finishing Sync...",
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            } else {
                                Icon(
                                    imageVector = if (isUserAnonymous) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    modifier = Modifier.size(48.dp),
                                    tint = if (isUserAnonymous) Color.Gray else MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.height(16.dp))

                                if (isUserAnonymous) {
                                    Text(
                                        "Connect your Google Account to sync your decks across multiple devices and backup your data.",
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(bottom = 24.dp)
                                    )

                                    Button(
                                        onClick = {
                                            googleSignInClient.signOut().addOnCompleteListener {
                                                launcher.launch(googleSignInClient.signInIntent)
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(50.dp)
                                    ) {
                                        Text("Connect Google Account")
                                    }
                                } else {
                                    Text(
                                        "Your data is synced to your Google Account.",
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(bottom = 24.dp)
                                    )

                                    OutlinedButton(
                                        onClick = {
                                            viewModel.signOut()
                                            googleSignInClient.signOut()
                                        },
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                    ) {
                                        Text("Disconnect & Use Local Storage")
                                    }

                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "Disconnecting will switch to local offline storage. Your cloud data remains safe.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = Color.Gray,
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 2. Manage Downloaded Languages Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val downloadedCount = detectedLanguages.count { downloadedLanguages.contains(it) }
                    val statusSubtitle = "$downloadedCount / ${detectedLanguages.size} downloaded"

                    DialogSection(
                        title = "Manage Downloaded Languages",
                        subtitle = statusSubtitle,
                        isExpanded = languagesExpanded,
                        onToggle = { languagesExpanded = !languagesExpanded }
                    ) {
                        Text(
                            "The following languages have been detected in your decks:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )

                        // Language Table
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(
                                    1.dp,
                                    MaterialTheme.colorScheme.outlineVariant,
                                    RoundedCornerShape(4.dp)
                                )
                                .clip(RoundedCornerShape(4.dp))
                        ) {
                            // Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.surfaceVariant)
                                    .height(IntrinsicSize.Min),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Language", modifier = Modifier
                                        .weight(1f)
                                        .padding(8.dp), fontWeight = FontWeight.Bold
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                // Added Size Header
                                Text(
                                    "Size",
                                    modifier = Modifier
                                        .weight(0.6f)
                                        .padding(8.dp),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    "Status", modifier = Modifier
                                        .weight(1f)
                                        .padding(8.dp), fontWeight = FontWeight.Bold
                                )
                                VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                Text(
                                    "Action",
                                    modifier = Modifier
                                        .weight(0.8f)
                                        .padding(8.dp),
                                    fontWeight = FontWeight.Bold,
                                    textAlign = TextAlign.Center
                                )
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                            // Rows
                            if (detectedLanguages.isEmpty()) {
                                Text(
                                    "No languages detected.",
                                    modifier = Modifier.padding(16.dp),
                                    fontStyle = FontStyle.Italic
                                )
                            } else {
                                detectedLanguages.forEachIndexed { index, lang ->
                                    val isDownloaded = downloadedLanguages.contains(lang)
                                    val langName = try {
                                        Locale(lang).displayLanguage
                                    } catch (e: Exception) {
                                        lang
                                    }
                                    // Fetch size
                                    val sizeInfo = viewModel.getFormattedModelSize(lang)

                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(IntrinsicSize.Min),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            langName, modifier = Modifier
                                                .weight(1f)
                                                .padding(8.dp)
                                        )

                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                        // Size Column
                                        Text(
                                            sizeInfo,
                                            modifier = Modifier
                                                .weight(0.6f)
                                                .padding(8.dp),
                                            textAlign = TextAlign.Center,
                                            fontSize = 12.sp
                                        )

                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                        // Status
                                        Row(
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            if (isDownloaded) {
                                                Icon(
                                                    Icons.Default.Check,
                                                    contentDescription = null,
                                                    tint = Color(0xFF22C55E),
                                                    modifier = Modifier.size(16.dp)
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    "Ready",
                                                    fontSize = 12.sp
                                                ) // Changed from "Downloaded" to save space
                                            } else {
                                                Text(
                                                    "Not Downloaded",
                                                    fontSize = 12.sp,
                                                    color = Color.Gray
                                                )
                                            }
                                        }

                                        VerticalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                                        // Action Button
                                        Box(
                                            modifier = Modifier.weight(0.8f),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDownloaded) {
                                                IconButton(onClick = { languageToDelete = lang }) {
                                                    Icon(
                                                        Icons.Default.Delete,
                                                        contentDescription = "Delete",
                                                        tint = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                            } else {
                                                IconButton(onClick = {
                                                    languageToDownload = lang
                                                }) {
                                                    Icon(
                                                        Icons.Default.Download,
                                                        contentDescription = "Download",
                                                        tint = MaterialTheme.colorScheme.primary
                                                    )
                                                }
                                            }
                                        }
                                    }
                                    if (index < detectedLanguages.size - 1) {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // Bulk Action Buttons
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
            }

            // --- 3. Manage Tags Section ---
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                                tags.sortedBy { it.name.lowercase() }.forEach { tag ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .border(
                                                1.dp,
                                                MaterialTheme.colorScheme.outlineVariant,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // USE THE REUSABLE COMPONENT
                                        TagChip(
                                            text = tag.name,
                                            colorHex = tag.color
                                        )

                                        Spacer(Modifier.weight(1f))

                                        // Actions
                                        IconButton(onClick = { tagToCleanup = tag.name }) {
                                            Icon(
                                                Icons.Default.Clear,
                                                contentDescription = "Remove from cards",
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = {
                                            tagToEdit = tag; showTagEditor = true
                                        }) {
                                            Icon(
                                                Icons.Default.Edit,
                                                contentDescription = "Edit",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(onClick = {
                                            viewModel.deleteTagDefinition(tag)
                                        }) {
                                            Icon(
                                                Icons.Default.Delete,
                                                contentDescription = "Delete",
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { tagToEdit = null; showTagEditor = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Create New Tag")
                        }
                    }
                }
            }

            // 4. Theme Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val themeName = when(themeMode) {
                        0 -> "Light Mode"
                        1 -> "Dark Mode"
                        2 -> "Black & White Mode"
                        else -> "Unknown"
                    }

                    DialogSection(
                        title = "Theme",
                        subtitle = themeName,
                        isExpanded = themeExpanded,
                        onToggle = { themeExpanded = !themeExpanded }
                    ) {
                        Column {
                            val themes = listOf(
                                "Light Mode" to 0,
                                "Dark Mode" to 1,
                                "Black & White Mode" to 2
                            )
                            themes.forEach { (name, mode) ->
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.setThemeMode(mode) }
                                        .padding(vertical = 8.dp)
                                ) {
                                    RadioButton(
                                        selected = themeMode == mode,
                                        onClick = { viewModel.setThemeMode(mode) }
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(name)
                                }
                            }
                        }
                    }
                }
            }

            // 5. Delete Decks Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
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
                            modifier = Modifier.padding(top = 8.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 6. Troubleshooting Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DialogSection(
                        title = "Troubleshooting",
                        subtitle = "Fix common issues",
                        isExpanded = troubleshootExpanded,
                        onToggle = { troubleshootExpanded = !troubleshootExpanded }
                    ) {
                        Button(
                            onClick = {
                                viewModel.setHdAudioPrompted(false)
                                Toast.makeText(context, "HD Audio Prompt Reset", Toast.LENGTH_SHORT)
                                    .show()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                        ) {
                            Text("Reset HD Audio Prompt")
                        }
                    }
                }
            }

            // 7. About Section
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    DialogSection(
                        title = "About",
                        subtitle = "Version 1.0",
                        isExpanded = aboutExpanded,
                        onToggle = { aboutExpanded = !aboutExpanded }
                    ) {
                        val dateFormat =
                            remember { SimpleDateFormat("MM/dd/yy, h:mm a", Locale.getDefault()) }

                        fun formatTimestamp(timestamp: Long): String =
                            if (timestamp == 0L) "N/A" else dateFormat.format(Date(timestamp))

                        val buildDateString =
                            remember(viewModel.buildTime) { dateFormat.format(Date(viewModel.buildTime)) }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Build Date: $buildDateString")
                            Text("Last Export: ${formatTimestamp(lastExportTimestamp)}")
                            Text("Last Import: ${formatTimestamp(lastImportTimestamp)}")
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}