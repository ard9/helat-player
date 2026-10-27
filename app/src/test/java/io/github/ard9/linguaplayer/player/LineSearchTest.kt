package io.github.ard9.linguaplayer.player

import io.github.ard9.linguaplayer.data.ClipMargins
import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import io.github.ard9.linguaplayer.subtitle.mkv.MkvSubtitleTrack
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LineSearchTest {
    private fun track(number: Long, language: String, default: Boolean = false) =
        MkvSubtitleTrack(number, "S_TEXT/UTF8", language, "", default, null, ByteArray(0))

    private val persian = track(2, "per", default = true)
    private val english = track(3, "eng")
    private val french = track(4, "fre", default = true)
    private val margins = ClipMargins(400, 600)

    @Test
    fun `the shown non-Persian track comes first, Persian last`() {
        val tracks = listOf(persian, english, french)
        assertEquals(listOf(1, 2, 0), LineSearch.preferredOrder(tracks, shown = english))
        // Nothing shown: the default non-Persian track first.
        assertEquals(listOf(2, 1, 0), LineSearch.preferredOrder(tracks, shown = null))
        // A Persian track on screen is still not used for the text first.
        assertEquals(listOf(2, 1, 0), LineSearch.preferredOrder(tracks, shown = persian))
    }

    private val search = BuiltInSearch(
        atMs = 5_500,
        lines = mapOf(
            2L to listOf(SubtitleLine(4_900, 7_100, "حالت چطوره؟")),
            3L to listOf(SubtitleLine(1_000, 3_000, "Hello."), SubtitleLine(5_000, 7_000, "How are you?")),
            4L to emptyList(),
        ),
        tracks = listOf(persian, english, french),
    )

    @Test
    fun `a line from a track gets margins and its Persian meaning`() {
        val found = LineSearch.fromTrack(search, 1, margins)!!
        assertEquals(FoundLine(4_600, 7_600, "How are you?", "حالت چطوره؟", 1), found)
    }

    @Test
    fun `the Persian track itself has no meaning, and an empty track has no line`() {
        assertEquals("", LineSearch.fromTrack(search, 0, margins)!!.meaning)
        assertNull(LineSearch.fromTrack(search, 2, margins))
    }

    @Test
    fun `best skips tracks without a line`() {
        // French is the default but has no line here, so English is used.
        assertEquals(1, LineSearch.best(search, shown = null, margins)!!.trackIndex)
    }

    @Test
    fun `margins never go before the start of the film`() {
        val found = LineSearch.fromFile(listOf(SubtitleLine(200, 1_500, "Hi")), 1_000, margins)!!
        assertEquals(0L to 2_100L, found.startMs to found.endMs)
        assertEquals(-1, found.trackIndex)
    }

    @Test
    fun `a line mostly saved already is a duplicate`() {
        val saved = listOf(4_600L to 7_600L)
        assertTrue(LineSearch.isDuplicate(saved, 4_700, 7_500))
        assertFalse(LineSearch.isDuplicate(saved, 7_000, 9_000))
        assertFalse(LineSearch.isDuplicate(emptyList(), 0, 1_000))
    }
}
