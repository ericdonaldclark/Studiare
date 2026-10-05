package net.ericclark.studiare

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
    downloadedLanguages: Set<String>, // NEW PARAMETER
    languageSizes: Map<String, String>,
    onDismiss: () -> Unit,
    onDownload: (List<String>) -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val languageDisplayMap = remember(languages) {
        languages.associateWith { code ->
            try {
                Locale(code).displayLanguage.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
            } catch (e: Exception) {
                code
            }
        }
    }

    // State for checkboxes: Initialize with ALL languages selected by default,
    // BUT exclude those already downloaded from the *active* selection set (since we can't download them again).
    val selectedLanguages = remember {
        mutableStateListOf<String>().apply {
            addAll(languages.filter { !downloadedLanguages.contains(it) })
        }
    }

    AnimatedDialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
            modifier = Modifier.fillMaxWidth().heightIn(max = 600.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(dimensions.cornerRadiusSmall))
                    .clip(RoundedCornerShape(dimensions.cornerRadiusSmall))
            ) {
                // --- HEADER ROW ---
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .height(IntrinsicSize.Min),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(getText(R.string.language), modifier = Modifier.weight(0.5f).padding(dimensions.paddingMedium), fontWeight = FontWeight.Bold)
                    VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                    // Size Header
                    Text(getText(R.string.size), modifier = Modifier.weight(0.3f).padding(dimensions.paddingSmall), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                    VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)
                    Text(getText(R.string.download), modifier = Modifier.weight(0.2f).padding(dimensions.paddingSmall), fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // --- LIST CONTENT ---
                LazyColumn {
                    itemsIndexed(languages) { index, code ->
                        val name = languageDisplayMap[code] ?: code
                        val isDownloaded = downloadedLanguages.contains(code)
                        val size = languageSizes[code] ?: "?"

                        val rowInteractionSource = remember { MutableInteractionSource() }
                        val isRowPressed by rowInteractionSource.collectIsPressedAsState()
                        val rowScale by animateFloatAsState(
                            targetValue = if (isRowPressed && !isDownloaded) 0.95f else 1f,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                            label = "langRowSquish"
                        )
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(IntrinsicSize.Min)
                                .scale(rowScale)
                                .clickable(
                                    interactionSource = rowInteractionSource,
                                    indication = LocalIndication.current,
                                    enabled = !isDownloaded
                                ) {
                                    if (selectedLanguages.contains(code)) selectedLanguages.remove(code)
                                    else selectedLanguages.add(code)
                                },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                name,
                                modifier = Modifier.weight(0.5f).padding(dimensions.paddingMedium),
                                color = if(isDownloaded) MaterialTheme.colorScheme.onSurface.copy(alpha=0.5f) else MaterialTheme.colorScheme.onSurface
                            )

                            VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)

                            // Size Value
                            Text(
                                size,
                                modifier = Modifier.weight(0.3f).padding(dimensions.paddingSmall),
                                textAlign = TextAlign.Center,
                                color = if(isDownloaded) Color.Gray else LocalContentColor.current
                            )

                            VerticalDivider(modifier = Modifier.fillMaxHeight(), color = MaterialTheme.colorScheme.outlineVariant)

                            Box(
                                modifier = Modifier.weight(0.4f).fillMaxHeight(),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isDownloaded) {
                                    // Show Disabled Checked Box or Icon
                                    Checkbox(
                                        checked = true,
                                        onCheckedChange = null,
                                        enabled = false
                                    )
                                } else {
                                    Checkbox(
                                        checked = selectedLanguages.contains(code),
                                        onCheckedChange = { checked ->
                                            if (checked) selectedLanguages.add(code)
                                            else selectedLanguages.remove(code)
                                        }
                                    )
                                }
                            }
                        }

                        if (index < languages.size - 1) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }

            Spacer(Modifier.height(dimensions.spacingMedium))

            // Select/Deselect All Buttons
            // Only affect languages that are NOT already downloaded
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                val selectAllInteractionSource = remember { MutableInteractionSource() }
                val isSelectAllPressed by selectAllInteractionSource.collectIsPressedAsState()
                val selectAllScale by animateFloatAsState(targetValue = if (isSelectAllPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "selectAllSquish")
                TextButton(
                    onClick = {
                        selectedLanguages.clear()
                        selectedLanguages.addAll(languages.filter { !downloadedLanguages.contains(it) })
                    },
                    interactionSource = selectAllInteractionSource,
                    modifier = Modifier.scale(selectAllScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.select_all)) }

                val deselectAllInteractionSource = remember { MutableInteractionSource() }
                val isDeselectAllPressed by deselectAllInteractionSource.collectIsPressedAsState()
                val deselectAllScale by animateFloatAsState(targetValue = if (isDeselectAllPressed) 0.95f else 1f, animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium), label = "deselectAllSquish")
                TextButton(
                    onClick = { selectedLanguages.clear() },
                    interactionSource = deselectAllInteractionSource,
                    modifier = Modifier.scale(deselectAllScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.deselect_all)) }
            }

            Spacer(Modifier.height(dimensions.spacingMedium))

            // Action Buttons
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                val cancelLangInteractionSource = remember { MutableInteractionSource() }
                val isCancelLangPressed by cancelLangInteractionSource.collectIsPressedAsState()
                val cancelLangScale by animateFloatAsState(
                    targetValue = if (isCancelLangPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "cancelLangSquish"
                )
                TextButton(
                    onClick = onDismiss,
                    interactionSource = cancelLangInteractionSource,
                    modifier = Modifier.scale(cancelLangScale),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.cancel)) }
                Spacer(Modifier.width(dimensions.spacingSmall))

                val downloadInteractionSource = remember { MutableInteractionSource() }
                val isDownloadPressed by downloadInteractionSource.collectIsPressedAsState()
                val downloadScale by animateFloatAsState(
                    targetValue = if (isDownloadPressed) 0.95f else 1f,
                    animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
                    label = "downloadLangSquish"
                )
                Button(
                    onClick = { onDownload(selectedLanguages.toList()) },
                    interactionSource = downloadInteractionSource,
                    modifier = Modifier.defaultMinSize(minHeight = 56.dp).scale(downloadScale),
                    // Enable only if there are NEW selections
                    enabled = selectedLanguages.isNotEmpty(),
                    shape = RoundedCornerShape(dimensions.cornerRadiusButton)
                ) { Text(getText(R.string.download)) }
            }
        }
    }
}
