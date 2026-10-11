package net.ericclark.studiare.components.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnswerMatcherTest {

    // --- Typed matching: exact after normalization, no fuzz ---

    @Test
    fun typed_caseInsensitive() {
        assertTrue(AnswerMatcher.matchesTyped("Caldo", "caldo"))
    }

    @Test
    fun typed_ignoresSurroundingWhitespace() {
        assertTrue(AnswerMatcher.matchesTyped("  caldo  ", "caldo"))
    }

    @Test
    fun typed_ignoresPunctuation() {
        assertTrue(AnswerMatcher.matchesTyped("caldo!", "caldo"))
        assertTrue(AnswerMatcher.matchesTyped("don't", "dont"))
    }

    @Test
    fun typed_collapsesInternalWhitespace() {
        assertTrue(AnswerMatcher.matchesTyped("New   York", "new york"))
    }

    @Test
    fun typed_doesNotFoldAccents() {
        // A physical keyboard has no excuse for dropping an accent it can type.
        assertFalse(AnswerMatcher.matchesTyped("cafe", "café"))
    }

    @Test
    fun typed_doesNotFuzzMatchOneCharOff() {
        assertFalse(AnswerMatcher.matchesTyped("caldl", "caldo"))
    }

    @Test
    fun typed_preservesNonLatinScripts() {
        assertTrue(AnswerMatcher.matchesTyped("こんにちは", "こんにちは"))
        assertFalse(AnswerMatcher.matchesTyped("こんにちは", "さようなら"))
    }

    // --- Spoken matching: accent-folded, fuzzy above a length floor ---

    @Test
    fun spoken_exactMatch() {
        assertTrue(AnswerMatcher.matchesSpoken("caldo", "caldo"))
    }

    @Test
    fun spoken_foldsAccents() {
        assertTrue(AnswerMatcher.matchesSpoken("cafe", "café"))
        assertTrue(AnswerMatcher.matchesSpoken("ano", "año"))
    }

    @Test
    fun spoken_toleratesOneEditOnLongEnoughWords() {
        // One substitution, both 5+ letters — recognizer misheard one sound.
        assertTrue(AnswerMatcher.matchesSpoken("hallo", "hello"))
    }

    @Test
    fun spoken_rejectsTwoEditsOnLongWords() {
        // "pledge" vs "pleske": two substitutions (d->s, g->k).
        assertFalse(AnswerMatcher.matchesSpoken("pleske", "pledge"))
    }

    @Test
    fun spoken_doesNotFuzzMatchShortWords() {
        // "cat"/"bat" is one substitution but both are short, real, different words —
        // fuzzing here would accept a wrong answer far more often than it forgives a
        // genuine misrecognition.
        assertFalse(AnswerMatcher.matchesSpoken("bat", "cat"))
    }

    @Test
    fun spoken_shortWordsStillNeedExactMatch() {
        assertTrue(AnswerMatcher.matchesSpoken("cat", "cat"))
    }

    @Test
    fun spoken_lengthFloorIsConfigurable() {
        // Default floor (5) rejects fuzzing a 3-letter word.
        assertFalse(AnswerMatcher.matchesSpoken("bat", "cat"))
        // Lowering the floor to 3 allows the same one-substitution pair through.
        assertTrue(AnswerMatcher.matchesSpoken("bat", "cat", fuzzyMatchMinLength = 3))
    }

    // --- normalize() / foldAccents() / levenshteinDistance() directly ---

    @Test
    fun normalize_stripsPunctuationAndCase() {
        assertEquals("hello world", AnswerMatcher.normalize("Hello, World!"))
    }

    @Test
    fun foldAccents_removesCombiningMarks() {
        assertEquals("cafe", AnswerMatcher.foldAccents("café"))
    }

    @Test
    fun levenshteinDistance_basicCases() {
        assertEquals(0, AnswerMatcher.levenshteinDistance("same", "same"))
        assertEquals(4, AnswerMatcher.levenshteinDistance("", "abcd"))
        assertEquals(1, AnswerMatcher.levenshteinDistance("caldo", "caldl"))
        assertEquals(1, AnswerMatcher.levenshteinDistance("hallo", "hello"))
    }
}
