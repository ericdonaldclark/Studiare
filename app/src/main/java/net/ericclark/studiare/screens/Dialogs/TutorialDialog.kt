package net.ericclark.studiare.screens.Dialogs

import net.ericclark.studiare.AnimatedDialog
import net.ericclark.studiare.DialogSection
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import net.ericclark.studiare.R
import net.ericclark.studiare.components.getText
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions

private data class TutorialPageContent(
    val icon: ImageVector,
    val title: String,
    val body: String,
    /** A small, non-interactive recreation of the real control being taught, with one element
     * highlighted — not a screenshot, a redrawn mini mockup using the same icons/shapes/colors. */
    val mockup: (@Composable () -> Unit)? = null
)

/**
 * First-launch tutorial — also re-openable from Settings (About > Help > Replay Tutorial) via
 * FlashcardViewModel.requestShowTutorial(). onDismiss is called on Skip, the close (X) button,
 * back press, or finishing the last page; the caller (FlashcardViewModel.dismissTutorial()) marks
 * it seen regardless of which of those ended it, so it never reappears uninvited afterward.
 */
@Composable
fun TutorialDialog(onDismiss: () -> Unit) {
    val dimensions = LocalStudiareDimensions.current
    // Plain val, not remember{} — getText() is @Composable and can only be called directly from
    // composable body code, not from inside a remember{} calculation lambda. Rebuilding this tiny
    // list on every recomposition is trivial, so there's no real cost to not memoizing it.
    val corePages = listOf(
        TutorialPageContent(Icons.Default.Style, getText(R.string.tutorial_decks_title), getText(R.string.tutorial_decks_body)) { AddDeckFabMenuMockup() },
        TutorialPageContent(Icons.Default.Edit, getText(R.string.tutorial_cards_title), getText(R.string.tutorial_cards_body)) { EditorToolbarMockup() },
        TutorialPageContent(Icons.Default.AccountTree, getText(R.string.tutorial_sets_title), getText(R.string.tutorial_sets_body)) { SetsButtonMockup() },
        TutorialPageContent(Icons.Default.School, getText(R.string.tutorial_modes_title), getText(R.string.tutorial_modes_body)) { CategoryPickerMockup() }
    )
    // +1 for the trailing "Explore More" page, which isn't a TutorialPageContent since its
    // content is a set of collapsible topics rather than a single icon/title/body/mockup.
    val pageCount = corePages.size + 1
    val pagerState = rememberPagerState(pageCount = { pageCount })
    val coroutineScope = rememberCoroutineScope()

    AnimatedDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = true, dismissOnClickOutside = false)
    ) {
        Card(
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxSize(0.9f)
        ) {
            Column(modifier = Modifier.fillMaxSize().padding(dimensions.paddingLarge)) {
                // "Welcome to Studiare" lives here, in the persistent header, rather than as its
                // own dedicated first slide — it's visible throughout instead of costing a swipe,
                // which matters more as more core-loop slides get added after it.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(getText(R.string.tutorial_welcome_title), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = getText(R.string.skip))
                    }
                }
                Spacer(Modifier.height(dimensions.spacingSmall))
                LinearProgressIndicator(
                    progress = { (pagerState.currentPage + 1).toFloat() / pageCount },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                    if (page < corePages.size) {
                        TutorialCorePage(corePages[page])
                    } else {
                        TutorialExploreMorePage()
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    OutlinedButton(
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) } },
                        enabled = pagerState.currentPage > 0,
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(getText(R.string.back))
                    }
                    Button(
                        onClick = {
                            if (pagerState.currentPage == pageCount - 1) {
                                onDismiss()
                            } else {
                                coroutineScope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                            }
                        },
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) {
                        Text(if (pagerState.currentPage == pageCount - 1) getText(R.string.done) else getText(R.string.next))
                    }
                }
            }
        }
    }
}

@Composable
private fun TutorialCorePage(page: TutorialPageContent) {
    val dimensions = LocalStudiareDimensions.current
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            page.icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(dimensions.spacingMedium))
        Text(page.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(dimensions.spacingSmall))
        Text(
            page.body,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 480.dp)
        )
        if (page.mockup != null) {
            Spacer(Modifier.height(dimensions.spacingLarge))
            page.mockup.invoke()
        }
    }
}

/** Wraps the one element a slide is teaching with an outline plus a small "Tap here" callout,
 * reused across all three mockups below instead of rebuilding the annotation each time. */
@Composable
private fun MockupHighlight(shape: androidx.compose.ui.graphics.Shape, content: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(modifier = Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape).padding(3.dp)) {
            content()
        }
        Spacer(Modifier.height(2.dp))
        Icon(
            Icons.Default.ArrowUpward,
            contentDescription = getText(R.string.tutorial_tap_here),
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(28.dp)
        )
    }
}

/** A pill-shaped item matching FloatingActionButtonMenuItem's look (DecksScreen.kt). */
@Composable
private fun MockupPill(icon: ImageVector, label: String) {
    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer, tonalElevation = 2.dp) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

/** Recreates DecksScreen's expanded Add Deck FAB menu (DecksScreen.kt:882-963): Export/Import
 * pills, then Add Deck highlighted, above a closed (X) FAB — same icons and stacking order. */
@Composable
private fun AddDeckFabMenuMockup() {
    Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        MockupPill(Icons.Default.Upload, getText(R.string.decks_export))
        MockupPill(Icons.Default.Download, getText(R.string.decks_import))
        MockupHighlight(shape = CircleShape) {
            MockupPill(Icons.Default.Add, getText(R.string.deck_create))
        }
        Spacer(Modifier.height(2.dp))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.primaryContainer, modifier = Modifier.size(56.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

/** Recreates the editor's HorizontalFloatingToolbar (vibrant primaryContainer tone, same 5 icons)
 * paired with the Add Card FAB (SimpleEditorScreen.kt:248-281) — the FAB is what's highlighted. */
@Composable
private fun EditorToolbarMockup() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer, tonalElevation = 2.dp) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                listOf(Icons.Default.ArrowDownward, Icons.Default.ArrowUpward, Icons.Default.Info, Icons.Default.Search, Icons.Default.Settings).forEach { icon ->
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                }
            }
        }
        MockupHighlight(shape = CircleShape) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                }
            }
        }
    }
}

/** Recreates the "N Sets" button shown on a deck's tile (DecksScreenItems.kt:130-153): a text
 * button with the AccountTree icon, primary-tinted, opening that deck's Set Manager. */
@Composable
private fun SetsButtonMockup() {
    MockupHighlight(shape = CircleShape) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Icon(Icons.Default.AccountTree, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(androidx.compose.ui.res.stringResource(R.string.sets_count_simple, 4), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

/** A row matching CategoryPickerContent's tonal buttons (StudyHubPickers.kt:46-226): full-width,
 * 10dp corners, icon pinned start — Learn is highlighted as the one to try first. */
@Composable
private fun MockupCategoryRow(icon: ImageVector, label: String) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        modifier = Modifier.fillMaxWidth().height(48.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 16.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(12.dp))
            Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun CategoryPickerMockup() {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.widthIn(max = 280.dp)
    ) {
        MockupHighlight(shape = RoundedCornerShape(10.dp)) {
            MockupCategoryRow(Icons.Default.School, getText(R.string.category_learn))
        }
        MockupCategoryRow(Icons.Default.MenuBook, getText(R.string.category_practice))
        MockupCategoryRow(Icons.Default.Quiz, getText(R.string.category_quiz))
    }
}

@Composable
private fun TutorialExploreMorePage() {
    val dimensions = LocalStudiareDimensions.current
    var templatesExpanded by remember { mutableStateOf(false) }
    var editorsExpanded by remember { mutableStateOf(false) }
    var syncExpanded by remember { mutableStateOf(false) }
    var customizationExpanded by remember { mutableStateOf(false) }
    var categoriesExpanded by remember { mutableStateOf(false) }
    var promptSideExpanded by remember { mutableStateOf(false) }
    var shortcutsExpanded by remember { mutableStateOf(false) }
    var ankiImportExpanded by remember { mutableStateOf(false) }
    var tagsExpanded by remember { mutableStateOf(false) }
    var sessionExpanded by remember { mutableStateOf(false) }
    var modeDefaultsExpanded by remember { mutableStateOf(false) }

    // Scrollable — 11 topics plus the intro no longer fit in the dialog's fixed height.
    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(getText(R.string.tutorial_explore_title), style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            getText(R.string.tutorial_explore_body),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(dimensions.spacingMedium))

        DialogSection(
            title = getText(R.string.tutorial_topic_templates_title),
            subtitle = getText(R.string.tutorial_topic_templates_subtitle),
            isExpanded = templatesExpanded,
            onToggle = { templatesExpanded = !templatesExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_templates_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            TemplatesMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_editors_title),
            subtitle = getText(R.string.tutorial_topic_editors_subtitle),
            isExpanded = editorsExpanded,
            onToggle = { editorsExpanded = !editorsExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_editors_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            EditorSwitchMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_sync_title),
            subtitle = getText(R.string.tutorial_topic_sync_subtitle),
            isExpanded = syncExpanded,
            onToggle = { syncExpanded = !syncExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_sync_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            SyncToggleMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_customization_title),
            subtitle = getText(R.string.tutorial_topic_customization_subtitle),
            isExpanded = customizationExpanded,
            onToggle = { customizationExpanded = !customizationExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_customization_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            ThemePickerMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_categories_title),
            subtitle = getText(R.string.tutorial_topic_categories_subtitle),
            isExpanded = categoriesExpanded,
            onToggle = { categoriesExpanded = !categoriesExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_categories_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            AllCategoriesMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_promptside_title),
            subtitle = getText(R.string.tutorial_topic_promptside_subtitle),
            isExpanded = promptSideExpanded,
            onToggle = { promptSideExpanded = !promptSideExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_promptside_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            PromptSideMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_shortcuts_title),
            subtitle = getText(R.string.tutorial_topic_shortcuts_subtitle),
            isExpanded = shortcutsExpanded,
            onToggle = { shortcutsExpanded = !shortcutsExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_shortcuts_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            ShortcutBadgeMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_ankiimport_title),
            subtitle = getText(R.string.tutorial_topic_ankiimport_subtitle),
            isExpanded = ankiImportExpanded,
            onToggle = { ankiImportExpanded = !ankiImportExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_ankiimport_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            AnkiImportMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_tags_title),
            subtitle = getText(R.string.tutorial_topic_tags_subtitle),
            isExpanded = tagsExpanded,
            onToggle = { tagsExpanded = !tagsExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_tags_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            TagsMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_session_title),
            subtitle = getText(R.string.tutorial_topic_session_subtitle),
            isExpanded = sessionExpanded,
            onToggle = { sessionExpanded = !sessionExpanded }
        ) {
            Text(getText(R.string.tutorial_topic_session_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            CreateSessionMockup()
        }
        DialogSection(
            title = getText(R.string.tutorial_topic_modedefaults_title),
            subtitle = getText(R.string.tutorial_topic_modedefaults_subtitle),
            isExpanded = modeDefaultsExpanded,
            onToggle = { modeDefaultsExpanded = !modeDefaultsExpanded },
            showDivider = false
        ) {
            Text(getText(R.string.tutorial_topic_modedefaults_body), style = MaterialTheme.typography.bodyMedium)
            TopicMockupSpacer()
            ModeDefaultsMockup()
        }
    }
}

@Composable
private fun TopicMockupSpacer() {
    Spacer(Modifier.height(LocalStudiareDimensions.current.spacingMedium))
}

/** Recreates Template Editor's "Start from Template" / "Save as Template" buttons
 * (DeckEditorDialogs.kt) — both OutlinedButtons, Save as Template highlighted since that's the
 * entry point (you need a saved template before Start from Template has anything to offer). */
@Composable
private fun TemplatesMockup() {
    val dimensions = LocalStudiareDimensions.current
    Column(horizontalAlignment = Alignment.Start, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        MockupOutlinedPill(Icons.Default.FileOpen, getText(R.string.start_from_template))
        MockupHighlight(shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
            MockupOutlinedPill(Icons.Default.Save, getText(R.string.save_as_template))
        }
    }
}

@Composable
private fun MockupOutlinedPill(icon: ImageVector, label: String) {
    val dimensions = LocalStudiareDimensions.current
    Surface(
        shape = RoundedCornerShape(dimensions.cornerRadiusButton),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Recreates the plain-text "Bulk Editor"/"Simple Editor" switch button shown in each editor's
 * top bar (SimpleEditorScreen.kt:237-244, DeckEditorScreen.kt). */
@Composable
private fun EditorSwitchMockup() {
    val dimensions = LocalStudiareDimensions.current
    MockupHighlight(shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
        Surface(shape = RoundedCornerShape(dimensions.cornerRadiusButton), color = MaterialTheme.colorScheme.secondaryContainer) {
            Text(
                getText(R.string.bulk_editor),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
            )
        }
    }
}

/** A generic "sync your data" toggle row — Settings > Backup & Sync holds several switches like
 * this (AuthAndSyncManager's syncDecksAndCards and friends). */
@Composable
private fun SyncToggleMockup() {
    var checked by remember { mutableStateOf(true) }
    MockupHighlight(shape = RoundedCornerShape(50)) {
        Surface(shape = RoundedCornerShape(50), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 6.dp, top = 4.dp, bottom = 4.dp)
            ) {
                Icon(Icons.Default.Sync, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text(getText(R.string.sync_decks_cards), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.Switch(checked = checked, onCheckedChange = { checked = it })
            }
        }
    }
}

/** Recreates Settings > Customization's theme picker (SettingsScreen.kt) — a simplified 2-way
 * version of the real connected button group (M3 Expressive's replacement for SegmentedButton; the
 * real one also has 4th "Custom" and 3rd "B&W" options). Full-round outer corners, small inner
 * corners where the pills meet, selected item filled with a leading checkmark — same shape language
 * as ConnectedButtonGroup (SettingsComponents.kt). */
@Composable
private fun ThemePickerMockup() {
    val dimensions = LocalStudiareDimensions.current
    val options = listOf(getText(R.string.light_mode), getText(R.string.dark_mode))
    val innerCorner = 4.dp
    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { index, label ->
            val selected = index == 1
            val shape = when (index) {
                0 -> RoundedCornerShape(topStart = dimensions.cornerRadiusButton, bottomStart = dimensions.cornerRadiusButton, topEnd = innerCorner, bottomEnd = innerCorner)
                else -> RoundedCornerShape(topStart = innerCorner, bottomStart = innerCorner, topEnd = dimensions.cornerRadiusButton, bottomEnd = dimensions.cornerRadiusButton)
            }
            val content: @Composable () -> Unit = {
                Surface(
                    shape = shape,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        if (selected) {
                            Icon(
                                Icons.Default.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                        }
                        Text(
                            label,
                            style = MaterialTheme.typography.labelMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (selected) MockupHighlight(shape = shape, content = content) else content()
        }
    }
}

/** All 5 study categories, unhighlighted — this topic explains what each one means rather than
 * pointing at a single one to tap, unlike the "Start Studying" core page's CategoryPickerMockup. */
@Composable
private fun AllCategoriesMockup() {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.widthIn(max = 280.dp)) {
        MockupCategoryRow(Icons.Default.School, getText(R.string.category_learn))
        MockupCategoryRow(Icons.Default.MenuBook, getText(R.string.category_practice))
        MockupCategoryRow(Icons.Default.Quiz, getText(R.string.category_quiz))
        MockupCategoryRow(Icons.Default.SportsEsports, getText(R.string.category_game))
        MockupCategoryRow(Icons.Default.Schedule, getText(R.string.category_smart))
    }
}

/** Recreates Deck Options' pill-shaped dropdown (ExpressiveDropdownMenuField, DeckEditorSettings.kt)
 * showing the Default Prompt Side field collapsed. */
@Composable
private fun PromptSideMockup() {
    MockupHighlight(shape = CircleShape) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHighest) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 16.dp, end = 10.dp, top = 10.dp, bottom = 10.dp)
            ) {
                Text(getText(R.string.prompt_side_auto_by_deck), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.width(8.dp))
                Icon(Icons.Default.KeyboardArrowDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Recreates a ShortcutHintOverlay badge (CommonShortcuts.kt:381+) — a dark rounded pill with a
 * bold white key letter, shown when holding Alt. */
@Composable
private fun ShortcutBadgeMockup() {
    MockupHighlight(shape = RoundedCornerShape(50)) {
        Surface(shape = RoundedCornerShape(50), color = androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.85f)) {
            Text(
                "N",
                color = androidx.compose.ui.graphics.Color.White,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }
    }
}

/** Reuses the same Import pill built for AddDeckFabMenuMockup — identical control either way. */
@Composable
private fun AnkiImportMockup() {
    MockupHighlight(shape = CircleShape) {
        MockupPill(Icons.Default.Download, getText(R.string.decks_import))
    }
}

/** Recreates TagChip (components/Tags.kt:417+) — colored squircle, bold adaptive text color. */
@Composable
private fun TagsMockup() {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        val dimensions = LocalStudiareDimensions.current
        listOf(
            "Verb" to MaterialTheme.colorScheme.tertiaryContainer,
            "Hard" to MaterialTheme.colorScheme.errorContainer,
            "Review" to MaterialTheme.colorScheme.secondaryContainer
        ).forEach { (label, color) ->
            Surface(shape = RoundedCornerShape(dimensions.cornerRadiusMedium), color = color) {
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

/** Recreates the Create Study Session dialog's Category section (CreateStudySessionDialog.kt:
 * CategorySelectionSection) — same DialogSection + TypedChipGrid pairing the real dialog uses,
 * reduced to a couple of real FilterChips. */
@Composable
private fun CreateSessionMockup() {
    MockupHighlight(shape = RoundedCornerShape(50)) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            androidx.compose.material3.FilterChip(
                selected = true,
                onClick = {},
                label = { Text(getText(R.string.category_learn)) },
                shape = RoundedCornerShape(50)
            )
            androidx.compose.material3.FilterChip(
                selected = false,
                onClick = {},
                label = { Text(getText(R.string.category_quiz)) },
                shape = RoundedCornerShape(50)
            )
        }
    }
}

/** Recreates a Mode Defaults settings row (Settings → Mode Defaults) with its per-option ⋮ "apply
 * to other modes" menu button. */
@Composable
private fun ModeDefaultsMockup() {
    val dimensions = LocalStudiareDimensions.current
    MockupHighlight(shape = RoundedCornerShape(dimensions.cornerRadiusMedium)) {
        Surface(shape = RoundedCornerShape(dimensions.cornerRadiusMedium), color = MaterialTheme.colorScheme.surfaceContainerHigh) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(start = 14.dp, end = 4.dp, top = 8.dp, bottom = 8.dp)
            ) {
                Text(getText(R.string.difficulty_weighting), style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.width(10.dp))
                Icon(Icons.Default.MoreVert, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        }
    }
}
