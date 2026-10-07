package net.ericclark.studiare.screens

import org.junit.Assert.assertEquals
import org.junit.Test

class PendingVoiceLanguagesTest {

    @Test
    fun newDeckLanguageIsPendingEvenAfterEarlierLanguagesWereHandled() {
        // English was downloaded earlier; Italian arrives later and is still asked about
        val pending = pendingVoiceLanguagesFor(
            deckLanguages = listOf("en", "it"),
            downloaded = setOf("en"),
            dismissed = emptySet(),
            allDismissed = false
        )
        assertEquals(listOf("it"), pending)
    }

    @Test
    fun dismissedLanguagesAreNotAskedAgain() {
        val pending = pendingVoiceLanguagesFor(
            deckLanguages = listOf("en", "it", "fr"),
            downloaded = emptySet(),
            dismissed = setOf("it"),
            allDismissed = false
        )
        assertEquals(listOf("en", "fr"), pending)
    }

    @Test
    fun dismissingAllStopsEverything() {
        val pending = pendingVoiceLanguagesFor(
            deckLanguages = listOf("en", "it"),
            downloaded = emptySet(),
            dismissed = emptySet(),
            allDismissed = true
        )
        assertEquals(emptyList<String>(), pending)
    }
}
