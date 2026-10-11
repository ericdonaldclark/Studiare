package net.ericclark.studiare.components.speech

import java.text.Normalizer

/**
 * Shared answer-comparison logic for the speech/typing study modes (Speech-to-Text,
 * Text-to-Speech, Listen & Speak, Listen & Type).
 *
 * Deliberately separate from `AudioStudyService.checkAnswer`, which only strips to
 * `[a-z0-9 ]` — that silently reduces any non-Latin answer (Chinese, Japanese, Cyrillic,
 * Arabic, etc.) to an empty string, and strips accents by deleting the letter entirely
 * rather than folding it (e.g. "café" -> "caf"). Typing/Quiz modes keep their own existing
 * exact, space-stripped comparison; this is only for the new speech-aware modes.
 *
 * Pure Kotlin / no Android dependency, so it runs as a fast local unit test.
 */
object AnswerMatcher {

    /** Case-folds, strips punctuation and collapses whitespace. Keeps non-Latin scripts intact. */
    fun normalize(input: String): String {
        val nfc = Normalizer.normalize(input, Normalizer.Form.NFC)
        return nfc
            .lowercase()
            .replace(Regex("[\\p{Punct}\\p{IsPunctuation}]+"), "")
            .trim()
            .replace(Regex("\\s+"), " ")
    }

    /** Decomposes and drops combining marks, so "café"/"cafe" and "año"/"ano" compare equal. */
    fun foldAccents(input: String): String {
        val decomposed = Normalizer.normalize(input, Normalizer.Form.NFD)
        return decomposed.replace(Regex("\\p{Mn}+"), "")
    }

    /** Typed answers: normalized equality. No fuzzing — a keyboard has no excuse for typos. */
    fun matchesTyped(input: String, expected: String): Boolean {
        return normalize(input) == normalize(expected)
    }

    /**
     * Spoken answers: also accent-folded, and tolerant of a single character's difference
     * once the expected word is long enough that one substitution/insertion/deletion isn't
     * likely to collide with a different real word. Recognizers routinely drop accents and
     * mishear one letter of an unfamiliar or short word; below the length floor a fuzzy match
     * is more likely to accept a wrong answer than to forgive a genuine misrecognition, so it
     * requires an exact match instead.
     */
    fun matchesSpoken(input: String, expected: String, fuzzyMatchMinLength: Int = 5): Boolean {
        val a = foldAccents(normalize(input))
        val b = foldAccents(normalize(expected))
        if (a == b) return true
        if (b.length < fuzzyMatchMinLength) return false
        return levenshteinDistance(a, b) <= 1
    }

    /** Classic O(n*m) edit distance; answers here are short words/phrases, not documents. */
    fun levenshteinDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previousRow = IntArray(b.length + 1) { it }
        var currentRow = IntArray(b.length + 1)

        for (i in 1..a.length) {
            currentRow[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                currentRow[j] = minOf(
                    currentRow[j - 1] + 1,      // insertion
                    previousRow[j] + 1,         // deletion
                    previousRow[j - 1] + cost   // substitution
                )
            }
            val swap = previousRow
            previousRow = currentRow
            currentRow = swap
        }
        return previousRow[b.length]
    }
}
