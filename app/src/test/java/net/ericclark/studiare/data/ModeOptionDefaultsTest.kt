package net.ericclark.studiare.data

import net.ericclark.studiare.screens.UI_Components.allModeOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ModeOptionDefaultsTest {

    @Test
    fun sessionDefaultsMatchGeneratedConstants() {
        val session = ActiveSession()
        assertEquals(ModeOptionDefaults.MAX_MEMORY_TILES, session.maxMemoryTiles)
        assertEquals(ModeOptionDefaults.HANGMAN_MAX_MISTAKES, session.hangmanMaxMistakes)
        assertEquals(ModeOptionDefaults.WORD_SEARCH_HIGHLIGHT_COLOR, session.wordSearchHighlightColor)
        assertEquals(ModeOptionDefaults.SPEAKING_FRONT_SPEED, session.speakingFrontSpeed)
        assertEquals(ModeOptionDefaults.CROSSWORD_FEEDBACK_MODE, session.crosswordFeedbackMode)
    }

    @Test
    fun requireConfirmTapDependsOnCategoryAndMode() {
        assertTrue(ModeOptionDefaults.requireConfirmTapFor(StudyCategory.PRACTICE, SessionMode.LIST))
        assertTrue(ModeOptionDefaults.requireConfirmTapFor(StudyCategory.QUIZ, SessionMode.LIST))
        assertTrue(ModeOptionDefaults.requireConfirmTapFor(StudyCategory.GUIDED, SessionMode.LIST))
        assertEquals(false, ModeOptionDefaults.requireConfirmTapFor(StudyCategory.PRACTICE, SessionMode.MULTIPLE_CHOICE))
        assertEquals(false, ModeOptionDefaults.requireConfirmTapFor(StudyCategory.PRACTICE, SessionMode.MATCHING))
    }

    @Test
    fun hangmanDefaultIsWithinItsBounds() {
        assertTrue(ModeOptionDefaults.HANGMAN_MAX_MISTAKES in ModeOptionDefaults.HANGMAN_MAX_MISTAKES_MIN..ModeOptionDefaults.HANGMAN_MAX_MISTAKES_MAX)
    }

    @Test
    fun everyListedOptionResolvesToAnOption() {
        // A typo in a _modes list would otherwise drop that option silently (mapNotNull)
        val known = allModeOptions.map { it.id }.toSet()
        val listed = ModeOptionLayout.byCategoryMode.values.flatten()
        assertTrue("unknown option ids: ${listed.filter { it !in known }}", listed.all { it in known })
    }
}
