package net.ericclark.studiare.data

import net.ericclark.studiare.R
import java.util.Locale

/** The house-style defaults Settings → Deck Options lets the user configure once and apply
 * broadly (all current decks, all current + future decks, future decks only, or a hand-picked
 * subset) — see [net.ericclark.studiare.screens.UI_Components.DeckOptionsCategoryContent]. A
 * non-null stored value here is used as the starting point for every brand-new deck in place of
 * [net.ericclark.studiare.screens.Screens.DeckEditorScreen]'s own hardcoded literals. */
data class DefaultDeckOptions(
    val deckSortMode: DeckSortMode = DeckSortMode.DATE_ADDED_OLD_TO_NEW,
    val defaultPromptSide: CardSide = CardSide.AUTO_BY_DECK,
    val frontLanguage: String = Locale.getDefault().language,
    val backLanguage: String = Locale.getDefault().language,
    val normalizationType: NormalizationType = NormalizationType.NONE,
    val textCorrections: TextCorrectionSettings = TextCorrectionSettings()
)

/** Which of [DefaultDeckOptions]' settings a single Deck Options apply action targets — lets
 * Settings → Deck Options apply Card Order, Default Prompt Side, Deck Language, and Text
 * Normalization independently, as well as all four together. */
enum class DeckOptionField(override val labelResId: Int) : StringResourceEnum {
    CARD_ORDER(R.string.sort_card_order),
    PROMPT_SIDE(R.string.default_prompt_side),
    LANGUAGE(R.string.language_set),
    NORMALIZATION(R.string.case_normalization);

    companion object {
        val ALL: Set<DeckOptionField> = entries.toSet()
    }
}

/** [this] with only the settings named by [fields] replaced from [source] — everything else
 * (including deck-specific values when [this] is an existing deck's own current settings, folded
 * into [DefaultDeckOptions] shape by the caller) is left untouched. Used both to merge a partial
 * apply into the stored future-deck default and, per target deck, into that deck's own fields. */
fun DefaultDeckOptions.withFieldsFrom(source: DefaultDeckOptions, fields: Set<DeckOptionField>): DefaultDeckOptions = copy(
    deckSortMode = if (DeckOptionField.CARD_ORDER in fields) source.deckSortMode else deckSortMode,
    defaultPromptSide = if (DeckOptionField.PROMPT_SIDE in fields) source.defaultPromptSide else defaultPromptSide,
    frontLanguage = if (DeckOptionField.LANGUAGE in fields) source.frontLanguage else frontLanguage,
    backLanguage = if (DeckOptionField.LANGUAGE in fields) source.backLanguage else backLanguage,
    normalizationType = if (DeckOptionField.NORMALIZATION in fields) source.normalizationType else normalizationType,
    textCorrections = if (DeckOptionField.NORMALIZATION in fields) source.textCorrections else textCorrections
)
