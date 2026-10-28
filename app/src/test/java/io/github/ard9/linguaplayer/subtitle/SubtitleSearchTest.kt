package io.github.ard9.linguaplayer.subtitle

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleSearchTest {
    private val english = listOf(
        SubtitleLine(2_000, 4_500, "Hello there."),
        SubtitleLine(20_100, 23_900, "I'll be right back.\nWait for me."),
        SubtitleLine(50_000, 52_000, "Right now!"),
    )
    private val persian = listOf(SubtitleLine(20_100, 23_900, "زود برمی‌گردم."))

    @Test
    fun `finds lines in every track, in time order`() {
        val hits = SubtitleSearch.find(listOf(english, persian), "right")
        assertEquals(listOf(20_100L, 50_000L), hits.map { it.line.startMs })
        assertEquals(listOf(0), hits.map { it.source }.distinct())
    }

    @Test
    fun `a Persian word finds the Persian line, also with Arabic letters`() {
        val hits = SubtitleSearch.find(listOf(english, persian), "برميگردم")
        assertEquals(listOf(1 to 20_100L), hits.map { it.source to it.line.startMs })
    }

    @Test
    fun `words may be on different lines of one subtitle`() {
        assertEquals(1, SubtitleSearch.find(listOf(english), "back wait").size)
    }

    @Test
    fun `nothing for an empty query, and no more than the maximum`() {
        assertEquals(0, SubtitleSearch.find(listOf(english), "  ").size)
        val many = (0 until 500).map { SubtitleLine(it * 1_000L, it * 1_000L + 500, "line") }
        assertEquals(SubtitleSearch.MAX_HITS, SubtitleSearch.find(listOf(many), "line").size)
    }
}
