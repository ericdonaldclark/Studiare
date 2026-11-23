package net.ericclark.studiare.data

/** Settings → Tags' "Tag Text Normalization" — the single global [NormalizationType]/
 * [TextCorrectionSettings] pair applied to every tag's [TagDefinition.name] whenever it's
 * created or renamed (see [net.ericclark.studiare.FlashcardViewModel.normalizeTagName]). One set
 * of rules for all tags, not per-deck, since tag names are already globally unique. */
data class TagNormalizationSettings(
    val normalizationType: NormalizationType = NormalizationType.NONE,
    val textCorrections: TextCorrectionSettings = TextCorrectionSettings()
)
