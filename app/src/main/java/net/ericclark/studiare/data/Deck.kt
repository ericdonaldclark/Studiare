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
    val isPinned: Boolean = false,
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

    // Per-deck opt-out for the "pinned cards sort first" default (Simple/Bulk Editor) — lives
    // alongside deckSortMode since it's the same per-deck "Sort Card Order" settings section.
    val sortPinnedCardsFirst: Boolean = true,

    // Stamped whenever this deck is included in a completed export (CSV/JSON/Anki) — null until
    // the first one. Deliberately per-deck rather than reusing the app-wide
    // FlashcardViewModel.lastExportTimestamp preference, since export lets the user pick a subset
    // of decks, so a deck left out of the most recent export shouldn't show that export's time.
    val lastExportedAt: Long? = null,

    // --- NEW: SYNC METADATA ---
    val isPendingSync: Boolean = true,
    val isDeleted: Boolean = false,

    // --- TEMPLATE LOCKING ---
    // Superseded by seedNewCardsFromTemplate/enforceTemplate below — kept declared (unread,
    // unwritten by any app logic) only so Room's schema still matches the existing `templateMode`
    // column without a destructive column-drop migration.
    val templateMode: TemplateMode = TemplateMode.MANUAL,
    // Whether newly-created cards start pre-filled with this deck's own frontNoteTemplates/
    // backNoteTemplates. Set by the Template Editor's "Apply to New"/"Apply to All" buttons —
    // both turn this on; "Apply to All" additionally sweeps existing cards, one-time, at save.
    val seedNewCardsFromTemplate: Boolean = false,
    // Whether this deck's own template fields are shape-locked (name/order/type; content stays
    // editable) — implies seeding too, since a card can't be locked to fields it was never given.
    // templateScope decides whether that lock reaches existing cards too, or only new ones.
    val enforceTemplate: Boolean = false,
    val templateScope: TemplateScope = TemplateScope.NEW_CARDS,
    // The named TemplateSet backing this deck's own template fields — every save of the Template
    // Editor requires a name, which upserts this same TemplateSet (by id) rather than leaving the
    // deck's template as an anonymous, un-shareable field list. Null only for a deck whose
    // template has never been saved since this requirement was added.
    val activeTemplateSetId: String? = null
)