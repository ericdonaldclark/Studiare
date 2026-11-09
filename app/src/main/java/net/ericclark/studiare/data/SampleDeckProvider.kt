package net.ericclark.studiare.data

/**
 * Builds a hand-authored, fixed-id "tutorial deck" — each card's front names a specific app
 * feature and its back explains it, with the card itself configured to demonstrate that feature
 * (a tag, a flag, rich text, a pin, a difficulty rating, etc.) rather than being generic study
 * content. Fixed ids mean tapping "Import Sample Deck" (Settings → About → Help) more than once
 * updates the same rows in place instead of duplicating them.
 */
object SampleDeckProvider {

    private const val ROOT_DECK_ID = "sample-deck-showcase"
    private const val SET_ID = "sample-deck-showcase-idioms-set"

    /** Returns the decks (root + nested set) and their cards, ready to insert via [DeckDao]/[CardDao]. */
    fun buildDecksWithCards(): List<DeckWithCards> {
        val now = System.currentTimeMillis()
        val rootCards = buildRootCards(now)
        val setCards = buildSetCards(now)

        val rootDeck = Deck(
            id = ROOT_DECK_ID,
            name = "Studiare Feature Showcase",
            parentDeckId = null,
            createdAt = now, updatedAt = now,
            normalizationType = NormalizationType.UPPERCASE_FIRST_LETTER,
            deckSortMode = DeckSortMode.A_TO_Z,
            defaultPromptSide = CardSide.FRONT,
            isPinned = true,
            cardIds = rootCards.map { it.id },
            frontLanguage = "en",
            backLanguage = "en",
            frontNoteTemplates = listOf(NoteField(name = "Example Sentence", content = "", type = MediaType.HTML)),
            description = "A tutorial deck — each card explains one of Studiare's features and " +
                "demonstrates it directly. Study it like any other deck, or open individual cards " +
                "in the editor to see how each feature is configured. Delete it whenever you're ready.",
            isPendingSync = true, isDeleted = false
        )

        val idiomsSet = Deck(
            id = SET_ID,
            name = "Idioms & Expressions",
            parentDeckId = ROOT_DECK_ID,
            createdAt = now, updatedAt = now,
            deckSortMode = DeckSortMode.DATE_ADDED_OLD_TO_NEW,
            cardIds = setCards.map { it.id },
            frontLanguage = "en",
            backLanguage = "en",
            description = "A child set nested under the showcase deck, demonstrating Studiare's set hierarchy and per-set linkage settings.",
            linkageSettings = LinkageSettings(
                syncCardAdditions = true, syncCardDeletions = false,
                linkCardData = false, linkCardOrder = false,
                linkFieldConfig = true, linkMetadata = false, linkScoring = false
            ),
            isPendingSync = true, isDeleted = false
        )

        return listOf(
            DeckWithCards(rootDeck, rootCards),
            DeckWithCards(idiomsSet, setCards)
        )
    }

    private fun buildRootCards(now: Long) = listOf(
            sampleCard("sample-card-intro", ROOT_DECK_ID, now,
                "Welcome to Studiare!",
                "This is a sample deck, not a translation deck — every card here explains one of " +
                    "the app's features, and the card itself is set up to demonstrate it. Flip " +
                    "through them like a normal study session, or open each card in the editor to " +
                    "see exactly how it's configured. Delete this deck anytime from the Decks screen.",
                tags = listOf("Getting Started")),

            sampleCard("sample-card-tags", ROOT_DECK_ID, now,
                "Tags",
                "This card is tagged \"Getting Started\" and \"Organization\" — tags group related " +
                    "cards across a deck so you can filter a study session down to just a topic, or " +
                    "find cards quickly in Manage Tags.",
                tags = listOf("Getting Started", "Organization")),

            sampleCard("sample-card-flags", ROOT_DECK_ID, now,
                "Colored Flags",
                "This card has a blue flag set. Flags are a free-form marker — red for \"needs " +
                    "fixing,\" blue for \"interesting,\" or whatever meaning you give them — and you " +
                    "can filter a study session to just the cards carrying one.",
                tags = listOf("Organization"), flag = CardFlag.BLUE),

            sampleCard("sample-card-richtext", ROOT_DECK_ID, now,
                "Rich Text Formatting",
                "Plain text answer, but check this card's front — it's not plain. Cards can use " +
                    "<b>bold</b>, <i>italic</i>, and other HTML formatting instead of plain text.",
                tags = listOf("Content"),
                frontRichText = "This front uses <b>bold</b> and <i>italic</i> HTML formatting."),

            sampleCard("sample-card-notefield", ROOT_DECK_ID, now,
                "Custom Note Fields",
                "Open this card in the editor and look for an \"Example Sentence\" field below the " +
                    "front/back — decks can define extra per-card fields like that (plain text, " +
                    "rich text, audio, images) via the Template Editor in Deck Settings.",
                tags = listOf("Content"),
                frontNotes = listOf(NoteField(
                    name = "Example Sentence",
                    content = "<i>This whole field only exists because this deck's template defines it.</i>",
                    type = MediaType.HTML
                ))),

            sampleCard("sample-card-pinned", ROOT_DECK_ID, now,
                "Pinned Cards",
                "Notice the pin on this card — pinned cards can be sorted to the front of the " +
                    "Simple/Bulk Editor and show up on the Pinned screen, so the ones you care about " +
                    "most are always easy to find.",
                tags = listOf("Organization"), isPinned = true),

            sampleCard("sample-card-known", ROOT_DECK_ID, now,
                "Known Cards",
                "This card is marked \"Known.\" Mark cards you've already mastered so study sessions " +
                    "can optionally skip or de-prioritize them.",
                tags = listOf("Study"), isKnown = true),

            sampleCard("sample-card-difficulty", ROOT_DECK_ID, now,
                "Difficulty Ratings",
                "This card's difficulty is set to 4 out of 5. Rate cards by difficulty to drive " +
                    "weighted study (harder cards appear more often) or to filter a session to just " +
                    "a difficulty range.",
                tags = listOf("Study"), difficulty = DifficultySetting.FOUR),

            sampleCard("sample-card-language", ROOT_DECK_ID, now,
                "Language Settings",
                "This deck has a front and back language set (both English here) — Deck Settings " +
                    "lets each side have its own, which drives text-to-speech pronunciation and " +
                    "voice-input matching in audio-based study modes.",
                tags = listOf("Organization")),

            sampleCard("sample-card-sort", ROOT_DECK_ID, now,
                "Card Order & Case Normalization",
                "This deck's Card Order is set to alphabetical (A–Z), and Case Normalization " +
                    "capitalizes the first letter of typed answers automatically — both live in " +
                    "Deck Settings, alongside the prompt-side and daily-limit settings.",
                tags = listOf("Organization")),

            sampleCard("sample-card-sets", ROOT_DECK_ID, now,
                "Nested Sets",
                "Open Set Manager for this deck and you'll find \"Idioms & Expressions\" nested " +
                    "underneath it — decks can contain child Sets, each with their own cards and " +
                    "settings, to group a big deck into smaller chunks without leaving the deck.",
                tags = listOf("Organization")),

            sampleCard("sample-card-presets", ROOT_DECK_ID, now,
                "Session Presets",
                "Open this deck's Study Hub and look at the Presets carousel at the top — this deck " +
                    "ships with three saved presets (Quick Flashcard Review, Spaced Repetition/FSRS, " +
                    "and a Matching Game) so you can jump straight into a pre-configured study " +
                    "session with one tap instead of setting one up from scratch.",
                tags = listOf("Study"))
    )

    private fun buildSetCards(now: Long) = listOf(
            sampleCard("sample-card-linkage", SET_ID, now,
                "Linked Sets",
                "This set's Deck Settings only partially link back to its parent deck — its card " +
                    "data and card order are independent, but its field configuration stays in " +
                    "sync. Each set controls exactly what it shares with its parent via its own " +
                    "Linkage Settings.",
                tags = listOf("Organization")),

            sampleCard("sample-card-idiom-example", SET_ID, now,
                "Piece of cake",
                "Pan comido — an ordinary content card living inside the nested set, to show sets " +
                    "hold real cards just like any other deck.",
                tags = listOf("Idioms"))
    )

    /** A few saved [SessionPreset]s pointed at the showcase deck, covering different modes/scheduling.
     * [numCards] is capped to the deck's actual card count (never a hardcoded guess) so a preset
     * can never resolve to zero cards and trip the "no cards due" empty-session guard. */
    fun buildSessionPresets(): List<SessionPreset> {
        val now = System.currentTimeMillis()
        val rootCardCount = buildRootCards(now).size
        return listOf(
            SessionPreset(
                id = "sample-preset-flashcard", deckId = ROOT_DECK_ID,
                name = "Quick Flashcard Review",
                sessionMode = SessionMode.FLASHCARD,
                isWeighted = true, numCards = 10,
                selectionMode = SelectionMode.ANY, sortMode = SortMode.RANDOM,
                schedulingMode = SchedulingMode.NORMAL,
                createdAt = now, updatedAt = now, isPendingSync = true
            ),
            SessionPreset(
                id = "sample-preset-fsrs", deckId = ROOT_DECK_ID,
                name = "Spaced Repetition (FSRS)",
                sessionMode = SessionMode.FLASHCARD,
                isGraded = true, numCards = rootCardCount,
                selectionMode = SelectionMode.ANY, sortMode = SortMode.CARD_ORDER,
                schedulingMode = SchedulingMode.FSRS,
                createdAt = now, updatedAt = now, isPendingSync = true
            ),
            SessionPreset(
                id = "sample-preset-matching", deckId = ROOT_DECK_ID,
                name = "Matching Game",
                sessionMode = SessionMode.MATCHING,
                numCards = 6,
                selectionMode = SelectionMode.ANY, sortMode = SortMode.RANDOM,
                schedulingMode = SchedulingMode.NORMAL,
                createdAt = now, updatedAt = now, isPendingSync = true
            )
        )
    }

    private fun sampleCard(
        id: String, ownerDeckId: String, now: Long, front: String, back: String,
        tags: List<String> = emptyList(), difficulty: DifficultySetting = DifficultySetting.ONE,
        flag: CardFlag = CardFlag.NONE, isPinned: Boolean = false, isKnown: Boolean = false,
        frontRichText: String? = null, frontNotes: List<NoteField> = emptyList()
    ) = Card(
        id = id, ownerDeckId = ownerDeckId,
        front = front, back = back, frontRichText = frontRichText,
        frontNotes = frontNotes,
        difficulty = difficulty, isKnown = isKnown, tags = tags,
        createdAt = now, updatedAt = now,
        flag = flag, isPinned = isPinned,
        isPendingSync = true, isDeleted = false
    )
}
