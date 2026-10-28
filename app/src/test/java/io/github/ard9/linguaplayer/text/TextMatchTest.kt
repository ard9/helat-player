package io.github.ard9.linguaplayer.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextMatchTest {
    @Test
    fun `normalizing folds case, letter forms, half spaces and spaces`() {
        assertEquals("i'll be right back", TextMatch.normalize("  I'll  be\nright BACK "))
        assertEquals("میخواهم", TextMatch.normalize("مي‌خواهم"))
        assertEquals("کتاب", TextMatch.normalize("كتاب"))
    }

    @Test
    fun `every word must be in the text`() {
        val words = TextMatch.words("right BACK")
        assertTrue(TextMatch.containsAll("I'll be right back.", words))
        assertFalse(TextMatch.containsAll("Right now!", words))
        assertFalse(TextMatch.containsAll("anything", emptyList()))
    }

    @Test
    fun `highlights point into the original text`() {
        val text = "Right now, right   HERE"
        val ranges = TextMatch.highlights(text, TextMatch.words("right here"))
        assertEquals(listOf(0..4, 11..15, 19..22), ranges)
        assertEquals("HERE", text.substring(ranges[2]))
    }

    @Test
    fun `highlights work across a half space and merge overlaps`() {
        val text = "می‌خواهم"
        assertEquals(listOf(0..text.lastIndex), TextMatch.highlights(text, TextMatch.words("میخواهم")))
        assertEquals(listOf(0..3), TextMatch.highlights("aaaa", listOf("aa")))
    }
}
