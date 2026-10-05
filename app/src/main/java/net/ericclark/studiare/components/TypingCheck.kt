package net.ericclark.studiare.components

import java.text.Normalizer

/**
 * Whether a typed answer matches the card's answer. Spaces never count. With [ignoreFormatting] on,
 * case, accents and punctuation are ignored; off, the answer must match exactly.
 */
fun typingAnswerMatches(typed: String, correct: String, ignoreFormatting: Boolean): Boolean {
    val typedLetters = typed.replace(" ", "")
    val correctLetters = correct.replace(" ", "")
    return if (ignoreFormatting) foldForTyping(typedLetters) == foldForTyping(correctLetters)
    else typedLetters == correctLetters
}

private fun foldForTyping(text: String): String =
    Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[\\p{P}\\p{S}]+"), "")
