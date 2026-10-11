package net.ericclark.studiare.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypingCheckTest {
    @Test
    fun ignoringFormattingMatchesCaseAccentsAndPunctuation() {
        assertTrue(typingAnswerMatches("MEZZO", "mezzo", ignoreFormatting = true))
        assertTrue(typingAnswerMatches("citta", "città", ignoreFormatting = true))
        assertTrue(typingAnswerMatches("ciao", "Ciao!", ignoreFormatting = true))
        assertTrue(typingAnswerMatches("luomo", "l'uomo", ignoreFormatting = true))
        assertTrue(typingAnswerMatches("ho detto", "ho  detto", ignoreFormatting = true))
    }

    @Test
    fun exactModeStillRequiresExactText() {
        assertFalse(typingAnswerMatches("MEZZO", "mezzo", ignoreFormatting = false))
        assertFalse(typingAnswerMatches("citta", "città", ignoreFormatting = false))
        assertTrue(typingAnswerMatches("mezzo", "mezzo", ignoreFormatting = false))
    }

    @Test
    fun wrongLettersNeverMatch() {
        assertFalse(typingAnswerMatches("mezza", "mezzo", ignoreFormatting = true))
    }
}
