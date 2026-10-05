package net.ericclark.studiare

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.nativeKeyCode
import net.ericclark.studiare.screens.sessionOptionScreens

/**
 * Every screen that can report keyboard shortcuts to [KeyboardShortcutsDialog]. Used to filter
 * the dialog down to just the current screen's shortcuts. [OTHER] is the fallback for screens
 * with no shortcuts of their own (they still see the always-shown Global entries).
 */
enum class ShortcutScreen {
    DECKS, SETS, DECK_EDITOR, SETTINGS, RECENTS, COLLECTIONS,
    // TYPING_SCORED was QUIZ_FLASHCARD — a misleading name (it was never actually shared with
    // List's FlashcardQuizScreen/LIST_QUIZ despite the name; confirmed via full-codebase grep its
    // only usage was Typing's graded screen). Renamed alongside SessionMode.QUIZ -> TYPING_SCORED.
    STUDY_HUB, FLASHCARD, TYPING_SCORED, LIST_QUIZ, MULTIPLE_CHOICE,
    MATCHING, MEMORY, TYPING, ANAGRAM, HANGMAN, CROSSWORD, WORD_SEARCH,
    FREEFORM, AUDIO, TYPED_LISTEN, SPOKEN_LISTEN, OTHER
}

/**
 * Describes a shortcut whose single base key can be rebound from Settings → Keyboard.
 * [modifierPrefix] (e.g. "Alt", "Ctrl") is kept fixed — only the base key itself is remappable,
 * since that covers every remappable shortcut in the app today (all are either bare or carry
 * exactly one fixed modifier) without the added complexity of remapping modifiers too.
 */
data class RemapSpec(
    val defaultKey: Key,
    val modifierPrefix: String? = null
)

/**
 * One documented shortcut. [screens] is the set of screens it's active on; an empty set means
 * it's global (always shown, on every screen, in every filter mode). [id] is a stable identity
 * used both as a list key and, for [remappable] entries, as the key into the user's remap
 * preferences. Shortcuts with no [remappable] spec (multi-key alternatives like "Esc / Backspace",
 * ranges like "1-9"/"A-Z", or gameplay keys embedded in many duplicated per-mode handlers) are
 * fixed — shown for reference but not rebindable, since remapping them meaningfully would need a
 * much larger change to the handlers that already branch on several keys at once. A fixed entry
 * that occupies one or two literal (non-range) keys can still list them in [fixedKeys] purely so
 * remapping *other* shortcuts can detect a collision with it — ranges/generic entries ("1-9",
 * "Arrow Keys") deliberately leave this empty, since they're not a specific key to collide with.
 */
data class ShortcutEntry(
    val id: String,
    val action: String,
    val keys: String,
    val category: String,
    val screens: Set<ShortcutScreen> = emptySet(),
    val remappable: RemapSpec? = null,
    val fixedKeys: List<RemapSpec> = emptyList()
)

/** What [entry] currently occupies: one binding for a remappable entry, or its [fixedKeys]. */
private fun currentBindings(entry: ShortcutEntry, remaps: Map<String, Long>): List<RemapSpec> {
    val spec = entry.remappable
    if (spec != null) {
        val key = remaps[entry.id]?.let { Key(it) } ?: spec.defaultKey
        return listOf(RemapSpec(key, spec.modifierPrefix))
    }
    return entry.fixedKeys
}

/**
 * Every other shortcut that would stop being reachable (or become ambiguous) if [id] were bound
 * to [candidateKey] with [candidateModifier] — same key + same modifier, on a screen [id] is
 * also active on (or either is global). Call this *before* committing a remap.
 */
fun findShortcutConflicts(
    remaps: Map<String, Long>,
    id: String,
    candidateKey: Key,
    candidateModifier: String?
): List<ShortcutEntry> {
    val changing = allShortcuts.firstOrNull { it.id == id } ?: return emptyList()
    return allShortcuts.filter { other ->
        other.id != id &&
            (changing.screens.isEmpty() || other.screens.isEmpty() || changing.screens.any { it in other.screens }) &&
            currentBindings(other, remaps).any { it.modifierPrefix == candidateModifier && it.defaultKey == candidateKey }
    }
}

/** A short, human-readable name for any [Key], used both for badges and the remap picker. */
fun Key.displayLabel(): String {
    val raw = android.view.KeyEvent.keyCodeToString(this.nativeKeyCode)
    return raw.removePrefix("KEYCODE_").replace('_', ' ')
}

/** What a [ShortcutEntry] should currently display, honoring any user remap. */
fun ShortcutEntry.displayKeys(remaps: Map<String, Long>): String {
    val spec = remappable ?: return keys
    val key = remaps[id]?.let { Key(it) } ?: spec.defaultKey
    val label = key.displayLabel()
    return if (spec.modifierPrefix != null) "${spec.modifierPrefix} + $label" else label
}

/** The key currently bound to [id] (remapped if the user changed it, else [default]). */
fun resolveShortcutKey(remaps: Map<String, Long>, id: String, default: Key): Key =
    remaps[id]?.let { Key(it) } ?: default

/**
 * The user's shortcut remaps, as {shortcut id -> key code}, provided once near the app root
 * (see MainActivity) so any composable that dispatches a remappable shortcut can cheaply read
 * the current binding without threading the ViewModel through every call site.
 */
val LocalShortcutRemaps = compositionLocalOf<Map<String, Long>> { emptyMap() }

// Shared by every "flip a card" study mode: practice Flashcard, the flip-quiz variant, Multiple
// Choice, the list-picker quiz, and Typing/Anagram/Hangman (each layers its own extra entries
// on top of this common set).
private val cardFlipScreens = setOf(
    ShortcutScreen.FLASHCARD, ShortcutScreen.TYPING_SCORED, ShortcutScreen.LIST_QUIZ,
    ShortcutScreen.MULTIPLE_CHOICE, ShortcutScreen.TYPING, ShortcutScreen.ANAGRAM, ShortcutScreen.HANGMAN
)

val allShortcuts: List<ShortcutEntry> = listOf(
    // ── Global (every screen) ──────────────────────────────────────────────
    ShortcutEntry("global.hint_overlay", "Show Hint Overlay", "Alt (Hold)", "Global"),
    ShortcutEntry("global.show_dialog", "Show This Dialog", "Alt + K", "Global", remappable = RemapSpec(Key.K, "Alt")),
    ShortcutEntry("global.go_back", "Go Back / Up", "Esc / Backspace", "Global", fixedKeys = listOf(RemapSpec(Key.Escape), RemapSpec(Key.Backspace))),
    ShortcutEntry("global.go_home", "Go Home", "Ctrl + H", "Global", remappable = RemapSpec(Key.H, "Ctrl")),
    ShortcutEntry("global.open_settings", "Open Settings", "Ctrl + S / Ctrl + ,", "Global", fixedKeys = listOf(RemapSpec(Key.S, "Ctrl"), RemapSpec(Key.Comma, "Ctrl"))),

    // ── Decks ───────────────────────────────────────────────────────────────
    ShortcutEntry("decks.create_new", "Create New Deck", "N", "Decks", setOf(ShortcutScreen.DECKS), RemapSpec(Key.N)),
    ShortcutEntry("decks.quick_open_grid", "Quick-Open Deck (Grid View)", "Alt + 1-9", "Decks", setOf(ShortcutScreen.DECKS)),
    ShortcutEntry("decks.quick_open_tree", "Expand/Select Deck (Tree View)", "Alt + 1-9", "Decks", setOf(ShortcutScreen.DECKS)),
    ShortcutEntry("decks.sort", "Sort Decks", "Alt + A", "Decks", setOf(ShortcutScreen.DECKS), RemapSpec(Key.A, "Alt")),
    ShortcutEntry("decks.import", "Import Decks", "Alt + I", "Decks", setOf(ShortcutScreen.DECKS), RemapSpec(Key.I, "Alt")),
    ShortcutEntry("decks.export", "Export Decks", "Alt + E", "Decks", setOf(ShortcutScreen.DECKS), RemapSpec(Key.E, "Alt")),
    ShortcutEntry("decks.settings", "Open Settings", "Alt + S", "Decks", setOf(ShortcutScreen.DECKS), RemapSpec(Key.S, "Alt")),

    // ── Sets ────────────────────────────────────────────────────────────────
    ShortcutEntry("sets.open_create_menu", "Open Create-Set Menu", "N", "Sets", setOf(ShortcutScreen.SETS), RemapSpec(Key.N)),
    ShortcutEntry("sets.automatic", "Automatic (Filter & Sort)", "Alt + A", "Sets", setOf(ShortcutScreen.SETS), RemapSpec(Key.A, "Alt")),
    ShortcutEntry("sets.manual", "Manual (Pick & Choose)", "Alt + M", "Sets", setOf(ShortcutScreen.SETS), RemapSpec(Key.M, "Alt")),
    ShortcutEntry("sets.clone", "Clone Entire Deck", "Alt + C", "Sets", setOf(ShortcutScreen.SETS), RemapSpec(Key.C, "Alt")),
    ShortcutEntry("sets.delete_all", "Delete All Sets", "Alt + Del", "Sets", setOf(ShortcutScreen.SETS), RemapSpec(Key.Delete, "Alt")),

    // ── Deck Editor ─────────────────────────────────────────────────────────
    ShortcutEntry("deck_editor.add_card", "Add Card", "Alt + N", "Deck Editor", setOf(ShortcutScreen.DECK_EDITOR), RemapSpec(Key.N, "Alt")),

    // ── Settings ────────────────────────────────────────────────────────────
    ShortcutEntry("settings.jump_to_category", "Jump to Category (Wide Layout)", "Alt + 1-9", "Settings", setOf(ShortcutScreen.SETTINGS)),

    // ── Recents ─────────────────────────────────────────────────────────────
    ShortcutEntry("recents.open_session", "Open Recent Session", "Alt + 1-9", "Recents", setOf(ShortcutScreen.RECENTS)),

    // ── Collections ─────────────────────────────────────────────────────────
    ShortcutEntry("collections.toggle", "Expand/Collapse Collection", "Alt + 1-9", "Collections", setOf(ShortcutScreen.COLLECTIONS)),

    // ── Study Sessions (the "start a session" hub for a deck) ──────────────
    ShortcutEntry("study_hub.start_learn", "Start Learn", "L", "Study Sessions", setOf(ShortcutScreen.STUDY_HUB), RemapSpec(Key.L)),
    ShortcutEntry("study_hub.start_study", "Start Study (Practice)", "P", "Study Sessions", setOf(ShortcutScreen.STUDY_HUB), RemapSpec(Key.P)),
    ShortcutEntry("study_hub.start_quiz", "Start Quiz", "Q", "Study Sessions", setOf(ShortcutScreen.STUDY_HUB), RemapSpec(Key.Q)),
    ShortcutEntry("study_hub.start_game", "Start Game", "G", "Study Sessions", setOf(ShortcutScreen.STUDY_HUB), RemapSpec(Key.G)),
    ShortcutEntry("study_hub.start_spaced_repetition", "Start Guided", "S", "Study Sessions", setOf(ShortcutScreen.STUDY_HUB), RemapSpec(Key.S)),
    ShortcutEntry("study_hub.delete_all_sessions", "Delete All Sessions", "Alt + Del", "Study Sessions", setOf(ShortcutScreen.STUDY_HUB), RemapSpec(Key.Delete, "Alt")),

    // ── Study Sessions (General) — shared across the card-flip modes ───────
    ShortcutEntry("study_general.flip_next", "Flip / Next Card", "Space / Enter", "Study Sessions (General)", cardFlipScreens),
    ShortcutEntry("study_general.previous", "Previous Card", "Left Arrow", "Study Sessions (General)", cardFlipScreens),
    ShortcutEntry("study_general.mark_known", "Mark Known / Unknown", "K / U", "Study Sessions (General)", cardFlipScreens),
    ShortcutEntry("study_general.rate_difficulty", "Rate Difficulty", "1 - 5", "Study Sessions (General)", cardFlipScreens),
    ShortcutEntry("session.options", "Session Options", "Alt + O", "Study Sessions (General)", sessionOptionScreens, RemapSpec(Key.O, "Alt")),

    // ── Multiple Choice ─────────────────────────────────────────────────────
    ShortcutEntry("mc.select_option", "Select Option", "1-9", "Multiple Choice", setOf(ShortcutScreen.MULTIPLE_CHOICE)),

    // ── List-Picker Quiz ────────────────────────────────────────────────────
    ShortcutEntry("list_quiz.navigate", "Navigate List", "Up / Down Arrows", "List-Picker Quiz", setOf(ShortcutScreen.LIST_QUIZ)),
    ShortcutEntry("list_quiz.jump_to_letter", "Jump to Letter", "A-Z", "List-Picker Quiz", setOf(ShortcutScreen.LIST_QUIZ)),
    ShortcutEntry("list_quiz.confirm", "Confirm Selection", "Enter", "List-Picker Quiz", setOf(ShortcutScreen.LIST_QUIZ)),
    ShortcutEntry("list_quiz.reveal", "Reveal Answer", "Space", "List-Picker Quiz", setOf(ShortcutScreen.LIST_QUIZ)),

    // ── Typing / Anagram / Hangman — reveal/submit on top of the general set ─
    ShortcutEntry("typing.submit", "Submit Answer", "Enter", "Typing", setOf(ShortcutScreen.TYPING, ShortcutScreen.TYPING_SCORED)),
    ShortcutEntry("anagram.reveal", "Reveal Answer", "Enter", "Anagram", setOf(ShortcutScreen.ANAGRAM)),
    ShortcutEntry("hangman.reveal", "Reveal Answer", "Enter", "Hangman", setOf(ShortcutScreen.HANGMAN)),

    // ── Matching ────────────────────────────────────────────────────────────
    ShortcutEntry("matching.navigate", "Navigate Item", "Up / Down Arrows", "Matching", setOf(ShortcutScreen.MATCHING)),
    ShortcutEntry("matching.switch_column", "Switch Column", "Left / Right Arrows", "Matching", setOf(ShortcutScreen.MATCHING)),
    ShortcutEntry("matching.select", "Select Tile", "Space / Enter", "Matching", setOf(ShortcutScreen.MATCHING)),

    // ── Memory ──────────────────────────────────────────────────────────────
    ShortcutEntry("memory.navigate", "Navigate Grid", "Arrow Keys", "Memory", setOf(ShortcutScreen.MEMORY)),
    ShortcutEntry("memory.select", "Select Tile", "Space / Enter", "Memory", setOf(ShortcutScreen.MEMORY)),
    ShortcutEntry("memory.undo", "Deselect / Undo Pick", "Esc / Backspace", "Memory", setOf(ShortcutScreen.MEMORY)),
    ShortcutEntry("memory.new_round", "Start New Round (When Complete)", "Space / Enter", "Memory", setOf(ShortcutScreen.MEMORY)),

    // ── Crossword ───────────────────────────────────────────────────────────
    ShortcutEntry("crossword.jump_to_clue", "Jump to Clue", "/ / Ctrl + J", "Crossword", setOf(ShortcutScreen.CROSSWORD)),
    ShortcutEntry("crossword.toggle_clue_focus", "Toggle Clue List Focus", "Alt + C", "Crossword", setOf(ShortcutScreen.CROSSWORD)),
    ShortcutEntry("crossword.switch_tabs", "Switch Across/Down Tabs", "Tab", "Crossword", setOf(ShortcutScreen.CROSSWORD)),
    ShortcutEntry("crossword.navigate_clues", "Navigate Clue List", "Up / Down Arrows", "Crossword", setOf(ShortcutScreen.CROSSWORD)),
    ShortcutEntry("crossword.hint", "Get Hint", "H", "Crossword", setOf(ShortcutScreen.CROSSWORD)),
    ShortcutEntry("crossword.hint_full", "Get Full-Word Hint", "Shift + H", "Crossword", setOf(ShortcutScreen.CROSSWORD)),
    ShortcutEntry("crossword.unfocus_clues", "Unfocus Clue List", "Enter / Esc", "Crossword", setOf(ShortcutScreen.CROSSWORD)),
    ShortcutEntry("crossword.move_cursor", "Move Cursor (Grid)", "Arrow Keys", "Crossword", setOf(ShortcutScreen.CROSSWORD)),
    ShortcutEntry("crossword.clear_letter", "Clear Letter", "Backspace / Delete", "Crossword", setOf(ShortcutScreen.CROSSWORD)),
    ShortcutEntry("crossword.switch_direction", "Switch Across/Down (At Intersection)", "Enter", "Crossword", setOf(ShortcutScreen.CROSSWORD)),

    // ── Word Search ─────────────────────────────────────────────────────────
    ShortcutEntry("word_search.toggle_clue_focus", "Toggle Clue List Focus", "Alt + C", "Word Search", setOf(ShortcutScreen.WORD_SEARCH)),
    ShortcutEntry("word_search.navigate_clues", "Navigate Clue List", "Arrow Keys", "Word Search", setOf(ShortcutScreen.WORD_SEARCH)),
    ShortcutEntry("word_search.select_word", "Select Word (List Focused)", "Enter", "Word Search", setOf(ShortcutScreen.WORD_SEARCH)),
    ShortcutEntry("word_search.move_cursor", "Move Cursor (Grid)", "Arrow Keys", "Word Search", setOf(ShortcutScreen.WORD_SEARCH)),
    ShortcutEntry("word_search.select_range", "Start/End Selection", "Space / Enter", "Word Search", setOf(ShortcutScreen.WORD_SEARCH)),
    ShortcutEntry("word_search.cancel", "Cancel Selection / Hide Cursor", "Esc", "Word Search", setOf(ShortcutScreen.WORD_SEARCH)),

    // ── Audio ───────────────────────────────────────────────────────────────
    ShortcutEntry("audio.play_pause", "Play / Pause", "Space", "Audio", setOf(ShortcutScreen.AUDIO)),
    ShortcutEntry("audio.advance", "Reveal / Advance", "Enter", "Audio", setOf(ShortcutScreen.AUDIO)),
    ShortcutEntry("audio.skip", "Previous / Next Prompt", "Left / Right Arrows", "Audio", setOf(ShortcutScreen.AUDIO)),
    ShortcutEntry("audio.rate_difficulty", "Rate Difficulty", "1 - 5", "Audio", setOf(ShortcutScreen.AUDIO)),
    ShortcutEntry("audio.play_shown", "Play Shown Side (Paused)", "P", "Audio", setOf(ShortcutScreen.AUDIO), RemapSpec(Key.P)),
    ShortcutEntry("audio.flip_shown", "Flip Card (Paused)", "F", "Audio", setOf(ShortcutScreen.AUDIO), RemapSpec(Key.F)),

    // ── Freeform ────────────────────────────────────────────────────────────
    ShortcutEntry("freeform.navigate", "Previous / Next Card", "Arrow Keys", "Freeform", setOf(ShortcutScreen.FREEFORM)),
    ShortcutEntry("freeform.reveal", "Reveal Other Side (One-Side View)", "Space", "Freeform", setOf(ShortcutScreen.FREEFORM), RemapSpec(Key.Spacebar))
)
