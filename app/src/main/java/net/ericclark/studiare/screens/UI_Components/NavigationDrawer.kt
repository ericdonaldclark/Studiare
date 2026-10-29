package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.ui.graphics.Color
import net.ericclark.studiare.data.ActiveSession
import net.ericclark.studiare.data.StudyCategory
import net.ericclark.studiare.data.displayCategory

/** The icon associated with a study category — used both for [sessionModeIcon] (saved-session
 * tiles) and directly wherever a category itself needs representing, e.g. the create-session
 * screen's category-selection chips. */
fun categoryIcon(category: StudyCategory): ImageVector = when (category) {
    StudyCategory.GUIDED -> Icons.Default.Schedule
    StudyCategory.GAMES -> Icons.Default.SportsEsports
    StudyCategory.LEARN -> Icons.Default.School
    StudyCategory.QUIZ -> Icons.Default.Quiz
    StudyCategory.PRACTICE -> Icons.AutoMirrored.Filled.MenuBook
}

/** Icon shown in place of a play button on a saved session tile, keyed off which tab it's from. */
fun sessionModeIcon(session: net.ericclark.studiare.data.ActiveSession): ImageVector = categoryIcon(session.displayCategory())

fun sessionModeDescription(session: net.ericclark.studiare.data.ActiveSession): String = when (session.displayCategory()) {
    StudyCategory.GUIDED -> "Guided"
    StudyCategory.GAMES -> "Game"
    StudyCategory.LEARN -> "Learn"
    StudyCategory.QUIZ -> "Quiz"
    StudyCategory.PRACTICE -> "Practice"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReversedActionButton(icon: ImageVector, text: String, enabled: Boolean = true, onClick: () -> Unit) {
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    val borderColor = if (isFocused) MaterialTheme.colorScheme.onPrimary else Color.Transparent
    val contentAlpha = if (enabled) 1f else 0.4f

    ElevatedCard(
        onClick = onClick,
        enabled = enabled,
        interactionSource = interactionSource,
        modifier = Modifier.border(if (isFocused) 6.dp else 0.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = contentAlpha),
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = text, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onPrimary.copy(alpha = contentAlpha))
            Spacer(Modifier.width(8.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary.copy(alpha = contentAlpha))
        }
    }
}
