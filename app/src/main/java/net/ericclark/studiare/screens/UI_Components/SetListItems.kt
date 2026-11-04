package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.*

@Composable
fun SubSetListItem(
    deck: DeckWithCards,
    dimensions: StudiareDimensions,
    onStudy: (String?) -> Unit,
    onManageSets: () -> Unit,
    activeSession: ActiveSession? = null,
    onResumeSession: (ActiveSession) -> Unit = {},
    onRestartSession: (ActiveSession) -> Unit = {},
    onCreateSession: () -> Unit = {}
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    val cardBorderColor = if (isCardFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    Card(
        modifier = Modifier
            // Width stays fixed for a uniform row; height is content-driven, same as
            // DeckListItem, so every gap matches the deck tile's exactly — see SetListItem
            // (DecksScreenItems.kt) for the full reasoning.
            .width(dimensions.setTileWidth)
            .pressSquish(cardInteractionSource, 0.98f)
            .border(if (isCardFocused) 6.dp else 0.dp, cardBorderColor, RoundedCornerShape(dimensions.cornerRadiusMedium))
            .clip(RoundedCornerShape(dimensions.cornerRadiusMedium))
            .clickable(
                interactionSource = cardInteractionSource,
                indication = LocalIndication.current
            ) { onManageSets() },
        shape = RoundedCornerShape(dimensions.cornerRadiusMedium),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(
            modifier = Modifier
                // Same corner-clearance floor as DeckListItem/SetListItem.
                .padding(dimensions.paddingMedium.coerceAtLeast(dimensions.cornerRadiusMedium))
        ) {
            Text(
                deck.deck.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Text(
                pluralStringResource(R.plurals.cards_count_lowercase, deck.cards.size, deck.cards.size),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Same fixed gap DeckListItem uses between its content block and the button row.
            Spacer(Modifier.height(dimensions.paddingLarge))

            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                StudySplitButton(
                    onStudyMain = { onStudy(null) },
                    onResumeSession = activeSession?.let { session -> { onResumeSession(session) } },
                    onRestartSession = activeSession?.let { session -> { onRestartSession(session) } },
                    onCreateSession = onCreateSession,
                    enabled = deck.cards.isNotEmpty()
                )
            }
        }
    }
}
