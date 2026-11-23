package net.ericclark.studiare.screens.Dialogs

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.R
import net.ericclark.studiare.FlashcardViewModel
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.data.naturalOrderComparator
import net.ericclark.studiare.screens.UI_Components.TypedChipRow
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.ui.theme.pressSquish
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


/**
 * A dialog for selecting decks and a format for export.
 * @param decks The list of all available decks.
 * @param onDismiss Callback for when the dialog is dismissed.
 * @param onExport Callback that provides the list of selected decks and the chosen format.
 * @param actionLabel The confirm button's label — "Export" by default, but Settings' "Select
 * Format and Content" reuses this same dialog with "Apply" instead, to persist a choice for
 * Scheduled Export Backup rather than writing a file.
 * @param initialFormat Preselects the format chip row — used by the Settings reuse above so
 * reopening it shows the currently-configured format, not always "JSON".
 * @param initialSelectedDeckIds When non-null, only these decks start selected instead of every
 * deck — used by the Settings reuse above to restore a previously-saved content selection.
 * @param initialIncludeMetadata Preselects the "include metadata" checkbox.
 * @param title The dialog's headline — "Export Decks" by default; Settings' reuse above passes
 * "Select Format and Content" instead, since that flow never actually exports a file.
 */
@Composable
fun ExportDecksDialog(
    decks: List<net.ericclark.studiare.data.DeckWithCards>,
    onDismiss: () -> Unit,
    onExport: (selectedDecks: List<net.ericclark.studiare.data.DeckWithCards>, format: String, includeMetadata: Boolean) -> Unit,
    actionLabel: String? = null,
    initialFormat: String = "JSON",
    initialSelectedDeckIds: Set<String>? = null,
    initialIncludeMetadata: Boolean = true,
    title: String? = null
) {
    val dimensions = LocalStudiareDimensions.current
    var includeSets by rememberSaveable { mutableStateOf(true) }
    var includeMetadata by rememberSaveable { mutableStateOf(initialIncludeMetadata) }
    val selectedDecks = remember { mutableStateListOf<net.ericclark.studiare.data.DeckWithCards>() }
    var format by remember { mutableStateOf(initialFormat) }
    var hasAppliedInitialSelection by remember { mutableStateOf(false) }

    // Only needed here for the initial-selection-restore effect below — DeckSetPickerGrid
    // recomputes this same (cheap, pure) derivation itself for its own tree/select-all/counter.
    val rootDecks = remember(decks) { decks.filter { it.deck.parentDeckId == null }.sortedWith(compareBy(naturalOrderComparator) { it.deck.name }) }
    val availableItemsToSelect = remember(includeSets, decks, rootDecks) {
        if (includeSets) decks else rootDecks
    }

    LaunchedEffect(availableItemsToSelect) {
        selectedDecks.clear()
        if (initialSelectedDeckIds != null && !hasAppliedInitialSelection) {
            selectedDecks.addAll(availableItemsToSelect.filter { it.deck.id in initialSelectedDeckIds })
            hasAppliedInitialSelection = true
        } else {
            selectedDecks.addAll(availableItemsToSelect)
        }
    }

    val labelForFormat: @Composable (String) -> String = { fmt ->
        when (fmt) {
            "CSV" -> getText(R.string.format_csv)
            "ANKI_APKG" -> getText(R.string.format_apkg_long)
            "ANKI_COLPKG" -> getText(R.string.format_colpkg_long)
            else -> getText(R.string.format_json)
        }
    }

    AnimatedDialog(onDismissRequest = onDismiss, properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)) {

        Card(
            modifier = Modifier.widthIn(min = 280.dp, max = 560.dp).fillMaxHeight(0.9f),
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium), // Dynamic corner radius
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            elevation = CardDefaults.cardElevation(defaultElevation = dimensions.cardElevation)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
              // Everything except the action buttons scrolls, so short windows can still reach every option.
              Column(modifier = Modifier.weight(1f, fill = false).verticalScroll(rememberScrollState())) {
                Text(
                    title ?: getText(R.string.export_decks_title),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier
                        .padding(bottom = dimensions.spacingSmall)
                        .fillMaxWidth()
                )

                net.ericclark.studiare.screens.UI_Components.DeckSetPickerGrid(
                    decks = decks,
                    selectedDecks = selectedDecks,
                    includeSets = includeSets
                )
                Spacer(Modifier.height(dimensions.spacingSmall))

                val includeSetsInteractionSource = remember { MutableInteractionSource() }
                ListItem(
                    headlineContent = { Text(text = getText(R.string.sets_include), color = MaterialTheme.colorScheme.onSurface) },
                    trailingContent = { Checkbox(checked = includeSets, onCheckedChange = { includeSets = it }, enabled = true) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressSquish(includeSetsInteractionSource, 0.95f)
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .clickable(interactionSource = includeSetsInteractionSource, indication = LocalIndication.current) { includeSets = !includeSets }
                )

                // NEW: Include Metadata Checkbox
                val includeMetadataInteractionSource = remember { MutableInteractionSource() }
                ListItem(
                    headlineContent = { Text(text = getText(R.string.include_metadata), color = MaterialTheme.colorScheme.onSurface) },
                    supportingContent = { Text(text = getText(R.string.include_metadata_desc), color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { Checkbox(checked = includeMetadata, onCheckedChange = { includeMetadata = it }, enabled = true) },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressSquish(includeMetadataInteractionSource, 0.95f)
                        .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
                        .clickable(interactionSource = includeMetadataInteractionSource, indication = LocalIndication.current) { includeMetadata = !includeMetadata }
                )
                Spacer(Modifier.height(dimensions.spacingSmall))

                Text(getText(R.string.export_format), style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(dimensions.spacingSmall))

                TypedChipRow(items = listOf("JSON", "CSV"), selected = format, labelFor = labelForFormat, onSelected = { format = it })
                Spacer(Modifier.height(dimensions.spacingSmall))
                TypedChipRow(items = listOf("ANKI_APKG", "ANKI_COLPKG"), selected = format, labelFor = labelForFormat, onSelected = { format = it })
                Spacer(Modifier.height(dimensions.spacingMedium))
              }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) { Text(getText(R.string.cancel)) }
                    Spacer(Modifier.width(dimensions.spacingSmall))

                    val exportInteractionSource = remember { MutableInteractionSource() }
                    Button(
                        onClick = { onExport(selectedDecks.toList(), format, includeMetadata) },
                        interactionSource = exportInteractionSource,
                        modifier = Modifier
                            .defaultMinSize(minHeight = 56.dp)
                            .pressSquish(exportInteractionSource, 0.95f),
                        enabled = selectedDecks.isNotEmpty(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(actionLabel ?: getText(R.string.export))
                    }
                }
            }
        }
    }
}

/**
 * Hosts [ExportDecksDialog] plus the three file-creation launchers (JSON/CSV/Anki) its own
 * `onExport` routes to, and the bookkeeping those need (`decksToExport`, `exportIncludeMetadata`,
 * both scoped privately to this composable). Shows the dialog whenever [show] is true; call sites
 * just need their own `var showExportDialog by remember { mutableStateOf(false) }` plus this one
 * composable, instead of duplicating this whole bundle per screen (`DecksScreen.kt` originally had
 * the only copy).
 */
@Composable
fun ExportDecksLauncher(show: Boolean, onDismiss: () -> Unit, viewModel: FlashcardViewModel) {
    // Deliberately NOT gated behind `if (!show) return` at the top: the three launchers below
    // (and decksToExport/exportIncludeMetadata) must stay alive across the dialog closing, since
    // onExport dismisses the dialog and THEN launches the system file picker — if this whole
    // composable left composition when `show` flips to false, rememberLauncherForActivityResult's
    // DisposableEffect would unregister the callback before the picker returns a result, so the
    // SAF-created document would never get written to (exported as an empty file).
    val context = androidx.compose.ui.platform.LocalContext.current
    var decksToExport by remember { mutableStateOf<List<net.ericclark.studiare.data.DeckWithCards>?>(null) }
    var exportIncludeMetadata by remember { mutableStateOf(true) }

    val jsonExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    val content = viewModel.getDecksAsString(decks, "JSON", exportIncludeMetadata)
                    context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(content.toByteArray()) }
                    viewModel.markDecksExported(decks)
                }
            }
            decksToExport = null
        }
    )
    val csvExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    val content = viewModel.getDecksAsString(decks, "CSV", exportIncludeMetadata)
                    context.contentResolver.openOutputStream(it)?.use { stream -> stream.write(content.toByteArray()) }
                    viewModel.markDecksExported(decks)
                }
            }
            decksToExport = null
        }
    )
    val ankiExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream"),
        onResult = { uri: Uri? ->
            uri?.let {
                decksToExport?.let { decks ->
                    viewModel.exportToAnkiPackage(context, decks, it, exportIncludeMetadata)
                    viewModel.markDecksExported(decks)
                }
            }
            decksToExport = null
        }
    )

    if (!show) return
    val allDecksWithCards by viewModel.allDecks.observeAsState(emptyList())
    ExportDecksDialog(
        decks = allDecksWithCards,
        onDismiss = onDismiss,
        onExport = { selectedDecks, format, includeMetadata ->
            onDismiss()
            decksToExport = selectedDecks
            exportIncludeMetadata = includeMetadata
            val dtFormat = SimpleDateFormat("yyMMddHHmmss", Locale.getDefault()).format(Date())
            when (format) {
                "CSV" -> csvExportLauncher.launch(context.getString(R.string.output_file_name, dtFormat, "csv"))
                "ANKI_APKG" -> ankiExportLauncher.launch("Studiare_Export_${dtFormat}.apkg")
                "ANKI_COLPKG" -> ankiExportLauncher.launch("Studiare_Export_${dtFormat}.colpkg")
                else -> jsonExportLauncher.launch("flashcard_decks_${dtFormat}.json")
            }
        }
    )
}

