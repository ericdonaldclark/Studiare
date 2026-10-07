package net.ericclark.studiare.screens

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedPreferencesValidationTest {

    @Test
    fun numbersMustParse() {
        assertTrue(isValidPreferenceText("Int", " 42 "))
        assertFalse(isValidPreferenceText("Int", "4.5"))
        assertTrue(isValidPreferenceText("Long", "9000000000"))
        assertFalse(isValidPreferenceText("Long", "abc"))
        assertTrue(isValidPreferenceText("Double", "0.25"))
        assertFalse(isValidPreferenceText("Double", ""))
        assertTrue(isValidPreferenceText("Float", "1.5"))
    }

    @Test
    fun booleansMustBeTrueOrFalse() {
        assertTrue(isValidPreferenceText("Boolean", "true"))
        assertFalse(isValidPreferenceText("Boolean", "yes"))
    }

    @Test
    fun textAndSetsAreAlwaysAccepted() {
        assertTrue(isValidPreferenceText("String", ""))
        assertTrue(isValidPreferenceText("Set<String>", "a, b"))
    }
}
