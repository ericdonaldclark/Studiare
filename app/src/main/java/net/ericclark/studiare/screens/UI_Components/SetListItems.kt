package net.ericclark.studiare.screens.UI_Components

import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.*
import net.ericclark.studiare.R
import net.ericclark.studiare.data.*
import net.ericclark.studiare.ui.theme.*
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.size
import androidx.compose.ui.draw.*

@Composable
fun SubSetListItem(
    deck: DeckWithCards,
    dimensions: StudiareDimensions,
    onStudy: (String?) -> Unit,
    onManageSets: () -> Unit
) {
    val cardInteractionSource = remember { MutableInteractionSource() }
    val isCardPressed by cardInteractionSource.collectIsPressedAsState()
    val isCardFocused by cardInteractionSource.collectIsFocusedAsState()
    val cardBorderColor = if (isCardFocused) MaterialTheme.colorScheme.primary else Color.Transparent

    val cardScale by animateFloatAsState(
        targetValue = if (isCardPressed) 0.98f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "subSetCardSquish"
    )

    Card(
        modifier = Modifier
            .width(190.dp)
            .height(160.dp)
            .scale(cardScale)
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
                .fillMaxSize()
                .padding(dimensions.paddingMedium)
        ) {
            Column {
                Text(
                    deck.deck.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.cards_count_lowercase, deck.cards.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                StudySplitButton(
                    onStudyMain = { onStudy(null) },
                    onStudyOption = { onStudy(it) },
                    enabled = deck.cards.isNotEmpty()
                )
            }
        }
    }
}
