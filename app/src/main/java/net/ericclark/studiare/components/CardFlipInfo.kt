package net.ericclark.studiare.components

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flip
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.SuggestionChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import net.ericclark.studiare.R
import net.ericclark.studiare.TooltipIconButton
import net.ericclark.studiare.data.CardEditorState
import net.ericclark.studiare.data.CardFlag
import net.ericclark.studiare.data.asString
import net.ericclark.studiare.screens.UI_Components.SettingsInfoRow
import net.ericclark.studiare.ui.theme.LocalStudiareDimensions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Shared wrapper for both editors' per-card UI: a static top row (card-number chip, optional
 * front-face-only extras, and an Info/Flip toggle icon) above a content area that flips in place
 * between [frontContent] and [infoContent] — a 3D rotation on the content only (the top row never
 * rotates, so the toggle control stays legible mid-flip), resizing to whichever face is current.
 */
@Composable
fun FlippableCardShell(
    cardNumber: Int,
    totalCards: Int,
    showInfo: Boolean,
    onToggleInfo: () -> Unit,
    modifier: Modifier = Modifier,
    showToggleButton: Boolean = true,
    contentTopSpacing: androidx.compose.ui.unit.Dp? = null,
    frontTopBarExtra: @Composable RowScope.() -> Unit = {},
    frontContent: @Composable BoxScope.() -> Unit,
    infoContent: @Composable BoxScope.() -> Unit
) {
    val dimensions = LocalStudiareDimensions.current
    val animateFlip = net.ericclark.studiare.ui.theme.LocalCardFlipAnimated.current
    val rotation by animateFloatAsState(
        targetValue = if (showInfo) 180f else 0f,
        animationSpec = if (animateFlip) spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow) else androidx.compose.animation.core.snap(),
        label = "cardFlip"
    )

    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SuggestionChip(
                onClick = onToggleInfo,
                label = { Text(stringResource(R.string.blank_of_blank, cardNumber, totalCards)) },
                colors = SuggestionChipDefaults.suggestionChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                ),
                border = null
            )
            Spacer(modifier = Modifier.weight(1f))
            if (!showInfo) {
                frontTopBarExtra()
            }
            if (showToggleButton) {
                TooltipIconButton(
                    description = if (showInfo) getText(R.string.flip_card) else getText(R.string.card_info),
                    onClick = onToggleInfo,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = if (showInfo) Icons.Default.Flip else Icons.Default.Info,
                        contentDescription = if (showInfo) getText(R.string.flip_card) else getText(R.string.card_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Spacer(Modifier.height(contentTopSpacing ?: dimensions.spacingSmall))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    rotationY = rotation
                    cameraDistance = 12f * density
                }
                .animateContentSize(animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium))
        ) {
            if (rotation < 90f) {
                frontContent()
            } else {
                Box(modifier = Modifier.graphicsLayer { rotationY = 180f }) {
                    infoContent()
                }
            }
        }
    }
}

/**
 * The flip-card's info face: every metadata field we have on a card, read-only. Reused by both
 * Simple Editor's [CardEditorState] (converted fresh from the persisted `Card` for display) and
 * Bulk Editor's live editing [CardEditorState].
 */
@Composable
fun CardMetadataContent(cardState: CardEditorState) {
    val dimensions = LocalStudiareDimensions.current
    val dateFormat = remember { SimpleDateFormat("MM/dd/yy 'at' h:mm a", Locale.getDefault()) }
    var row = 0

    Column {
        Text(
            cardState.front.value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth().padding(horizontal = dimensions.paddingMedium)
        )
        Spacer(Modifier.height(dimensions.spacingSmall))

        SettingsInfoRow(getText(R.string.date_created), dateFormat.format(Date(cardState.createdAt.value)), isAlternate = row++ % 2 == 1)
        SettingsInfoRow(getText(R.string.last_updated), dateFormat.format(Date(cardState.updatedAt.value)), isAlternate = row++ % 2 == 1)
        SettingsInfoRow(getText(R.string.times_reviewed), "${cardState.reviewedCount.value}", isAlternate = row++ % 2 == 1)
        val correct = cardState.gradedAttempts.value.size - cardState.incorrectAttempts.value.size
        SettingsInfoRow(getText(R.string.correct), "$correct", isAlternate = row++ % 2 == 1)
        SettingsInfoRow(getText(R.string.incorrect), "${cardState.incorrectAttempts.value.size}", isAlternate = row++ % 2 == 1)
        cardState.absoluteDueDate.value?.let {
            SettingsInfoRow(getText(R.string.due_date), dateFormat.format(Date(it)), isAlternate = row++ % 2 == 1)
        }
        SettingsInfoRow(getText(R.string.difficulty), "${cardState.difficulty.value.value}", isAlternate = row++ % 2 == 1)
        SettingsInfoRow(getText(R.string.known_label), if (cardState.isKnown.value) getText(R.string.yes) else getText(R.string.no), isAlternate = row++ % 2 == 1)
        if (cardState.isSuspended.value) {
            SettingsInfoRow(getText(R.string.suspended), getText(R.string.yes), isAlternate = row++ % 2 == 1)
        }
        if (cardState.flag.value != CardFlag.NONE) {
            SettingsInfoRow(getText(R.string.card_flag_label), cardState.flag.value.asString(), isAlternate = row++ % 2 == 1)
        }
        if (cardState.lastReviewDurationMs.value > 0) {
            val seconds = (cardState.lastReviewDurationMs.value / 1000).toInt()
            SettingsInfoRow(getText(R.string.review_duration_label), stringResource(R.string.review_duration_seconds_format, seconds), isAlternate = row++ % 2 == 1)
        }

        cardState.fsrsState.value?.let { state ->
            Spacer(Modifier.height(dimensions.spacingSmall))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Spacer(Modifier.height(dimensions.spacingSmall))
            row = 0

            SettingsInfoRow(getText(R.string.fsrs_state_label), state.asString(), isAlternate = row++ % 2 == 1)
            cardState.fsrsStability.value?.let {
                SettingsInfoRow(getText(R.string.fsrs_stability_label), "%.2f".format(it), isAlternate = row++ % 2 == 1)
            }
            cardState.fsrsDifficulty.value?.let {
                SettingsInfoRow(getText(R.string.fsrs_difficulty_label), "%.2f".format(it), isAlternate = row++ % 2 == 1)
            }
            cardState.fsrsElapsedDays.value?.let {
                SettingsInfoRow(getText(R.string.fsrs_elapsed_days_label), "%.1f".format(it), isAlternate = row++ % 2 == 1)
            }
            cardState.fsrsScheduledDays.value?.let {
                SettingsInfoRow(getText(R.string.fsrs_scheduled_days_label), "%.1f".format(it), isAlternate = row++ % 2 == 1)
            }
            cardState.fsrsLastReview.value?.let {
                SettingsInfoRow(getText(R.string.fsrs_last_review_label), dateFormat.format(Date(it)), isAlternate = row++ % 2 == 1)
            }
            SettingsInfoRow(getText(R.string.fsrs_lapses_label), "${cardState.fsrsLapses.value}", isAlternate = row++ % 2 == 1)
        }
    }
}
