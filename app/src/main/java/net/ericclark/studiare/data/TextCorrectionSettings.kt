package net.ericclark.studiare.data

/** Whitespace/punctuation cleanup corrections applied alongside [NormalizationType] whenever a
 * deck's cards are saved — see [net.ericclark.studiare.screens.Dialogs.TextNormalizationDialog].
 * Every correction defaults to on. */
data class TextCorrectionSettings(
    val trimWhitespace: Boolean = true,
    val collapseConsecutiveSpaces: Boolean = true,
    val addSpaceAfterPunctuation: Boolean = true,
    val removeSpaceBeforePunctuation: Boolean = true,
    val removeDuplicateConsecutiveWords: Boolean = true
)

/** Applies [settings] to [text], in a fixed order: trim, collapse spaces, add space after
 * punctuation, remove space before punctuation, then dedupe consecutive words. Order matters —
 * e.g. collapsing spaces before fixing punctuation spacing avoids leaving a double space behind. */
fun applyTextCorrections(settings: TextCorrectionSettings, text: String): String {
    var result = text
    if (settings.trimWhitespace) result = result.trim()
    if (settings.collapseConsecutiveSpaces) result = result.replace(Regex(" {2,}"), " ")
    if (settings.addSpaceAfterPunctuation) result = result.replace(Regex("([.,!?;:])([A-Za-z])"), "$1 $2")
    if (settings.removeSpaceBeforePunctuation) result = result.replace(Regex("\\s+([.,!?;:])"), "$1")
    if (settings.removeDuplicateConsecutiveWords) result = removeDuplicateConsecutiveWords(result)
    return result
}

private fun removeDuplicateConsecutiveWords(text: String): String {
    val duplicateWordRegex = Regex("(?i)\\b(\\w+)(\\s+)\\1\\b")
    var result = text
    while (true) {
        val replaced = duplicateWordRegex.replace(result) { it.groupValues[1] }
        if (replaced == result) break
        result = replaced
    }
    return result
}
