package net.ericclark.studiare.screens.UI_Components
import net.ericclark.studiare.*
import net.ericclark.studiare.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import net.ericclark.studiare.components.AudioServiceManager
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.util.*
import androidx.compose.material3.VerticalDivider
import androidx.compose.foundation.lazy.itemsIndexed
import net.ericclark.studiare.screens.*
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import net.ericclark.studiare.components.getText
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.draw.scale
import net.ericclark.studiare.screens.Screens.StudyModeSelectionScreen

/**
 * The "pick a category" content: a title/subtitle followed by one button per [StudyCategory] plus
 * Guided (FSRS), each with a short description underneath. Shared by the Study Hub's empty state
 * (title="No Active Sessions" + a subtitle) and the full-page dialog the FAB opens once sessions
 * already exist (title="Pick a Category", no subtitle) — see [StudyModeSelectionScreen].
 */
@Composable
fun CategoryPickerContent(
    title: String,
    subtitle: String?,
    onCategorySelected: (StudyCategory) -> Unit,
    onGuidedSelected: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dimensions = LocalStudiareDimensions.current
    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )
            if (subtitle != null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(32.dp))

            FilledTonalButton(
                onClick = { onCategorySelected(StudyCategory.LEARN) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.School,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.category_learn),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_learn_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { onCategorySelected(StudyCategory.PRACTICE) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.MenuBook,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.category_practice),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_practice_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { onCategorySelected(StudyCategory.QUIZ) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Quiz,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.category_quiz),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_quiz_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { onCategorySelected(StudyCategory.GAMES) },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.category_game),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_game_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(12.dp))

            Button(
                onClick = onGuidedSelected,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(dimensions.cornerRadiusButton),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.align(Alignment.CenterStart)
                    )
                    Text(
                        text = getText(R.string.spaced_repetition_label),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = getText(R.string.category_smart_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun HdLanguageSelectionDialog(
    languages: List<String>,
    downloadedLanguages: Set<String>,
    languageSizes: Map<String, String>,
    voiceDownload: AudioServiceManager.VoiceDownloadProgress?,
    onDownload: (List<String>) -> Unit,
    onCancelDownload: () -> Unit,
    onStartSession: () -> Unit,
    onDismissForThese: () -> Unit,
    onDismissForAll: () -> Unit,
    onClose: () -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    // New (not yet downloaded) languages start selected
    val selectedLanguages = remember {
        mutableStateListOf<String>().apply { addAll(languages.filter { !downloadedLanguages.contains(it) }) }
    }
    val busy = voiceDownload != null

    AnimatedDialog(onDismissRequest = onClose) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            modifier = Modifier.fillMaxWidth().heightIn(max = 680.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(modifier = Modifier.padding(dimensions.paddingLarge)) {
                Text(getText(R.string.download_hd_languages_title), style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(dimensions.spacingSmall))
                Text(
                    getText(R.string.download_hd_languages_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(dimensions.spacingMedium))

                LazyColumn(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                ) {
                    itemsIndexed(languages) { index, code ->
                        val isDownloaded = downloadedLanguages.contains(code)
                        val isDownloadingThis = voiceDownload?.language == code
                        val name = try {
                            Locale(code).displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
                        } catch (e: Exception) {
                            code
                        }
                        VoiceLanguageRow(
                            languageName = name,
                            sizeText = languageSizes[code] ?: "?",
                            isDownloaded = isDownloaded,
                            isDownloading = isDownloadingThis,
                            isQueued = voiceDownload?.queued?.contains(code) == true,
                            progress = if (isDownloadingThis) voiceDownload?.fraction ?: 0f else 0f,
                            onDownload = { onDownload(listOf(code)) },
                            onCancel = onCancelDownload,
                            leading = if (!isDownloaded) {
                                {
                                    Checkbox(
                                        checked = selectedLanguages.contains(code),
                                        enabled = !busy,
                                        onCheckedChange = { checked ->
                                            if (checked) selectedLanguages.add(code) else selectedLanguages.remove(code)
                                        }
                                    )
                                }
                            } else null
                        )
                        if (index < languages.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))

                // While a download runs, Cancel stops it at once and keeps nothing; otherwise download what's selected
                if (busy) {
                    OutlinedButton(
                        onClick = onCancelDownload,
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) { Text(getText(R.string.cancel_downloads)) }
                } else {
                    Button(
                        onClick = { onDownload(selectedLanguages.toList()) },
                        modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                        enabled = selectedLanguages.isNotEmpty(),
                        shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                    ) { Text(getText(R.string.download_selected)) }
                }
                Spacer(Modifier.height(dimensions.spacingSmall))

                // Starts the session without saving anything; a download in progress keeps going
                Button(
                    onClick = onStartSession,
                    modifier = Modifier.fillMaxWidth().defaultMinSize(minHeight = 56.dp),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.start_session)) }

                Spacer(Modifier.height(dimensions.spacingSmall))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    TextButton(onClick = onDismissForThese, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.dismiss_these_languages))
                    }
                    TextButton(onClick = onDismissForAll, shape = RoundedCornerShape(dimensions.cornerRadiusButton)) {
                        Text(getText(R.string.dismiss_all_languages))
                    }
                }

                Spacer(Modifier.height(dimensions.spacingMedium))
                Text(
                    getText(R.string.voice_settings_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
