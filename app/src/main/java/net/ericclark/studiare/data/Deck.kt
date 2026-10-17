package net.ericclark.studiare.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Locale
import java.util.UUID

@Entity(tableName = "decks")
data class Deck(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val name: String = "",
    val parentDeckId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val averageQuizScore: Float? = null,
    val normalizationType: NormalizationType = NormalizationType.NONE,
    val deckSortMode: DeckSortMode = DeckSortMode.DATE_ADDED_OLD_TO_NEW,
    val defaultPromptSide: CardSide = CardSide.AUTO_BY_DECK,
    val isStarred: Boolean = false,
    val cardIds: List<String> = emptyList(),
    val frontLanguage: String = Locale.getDefault().language,
    val backLanguage: String = Locale.getDefault().language,
    val frontNoteTemplates: List<NoteField> = emptyList(),
    val backNoteTemplates: List<NoteField> = emptyList(),

    val description: String = "",
    val dailyNewCardLimit: Int = 20,
    val dailyReviewLimit: Int = 200,

    val fsrsEnabled: Boolean = false,
    val fsrsWeights: List<Double> = emptyList(),
    val fsrsDesiredRetention: Double = 0.9,
    val fsrsMaximumInterval: Int = 36500,

    val linkageSettings: LinkageSettings = LinkageSettings(),

    // Stamped whenever this deck is included in a completed export (CSV/JSON/Anki) — null until
    // the first one. Deliberately per-deck rather than reusing the app-wide
    // FlashcardViewModel.lastExportTimestamp preference, since export lets the user pick a subset
    // of decks, so a deck left out of the most recent export shouldn't show that export's time.
    val lastExportedAt: Long? = null,

    // --- NEW: SYNC METADATA ---
    val isPendingSync: Boolean = true,
    val isDeleted: Boolean = false
)