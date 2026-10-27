package io.github.ard9.linguaplayer.subtitle.mkv

import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleTextCleanerTest {
    @Test
    fun `ASS keeps only the text field and removes override tags`() {
        val stored = "12,0,Default,,0,0,0,,{\\an8}{\\i1}Hello,{\\i0} world\\NSecond line"
        assertEquals("Hello, world\nSecond line", SubtitleTextCleaner.clean("S_TEXT/ASS", stored))
    }

    @Test
    fun `ASS hard spaces and lowercase line breaks`() {
        assertEquals("a b\nc", SubtitleTextCleaner.clean("S_TEXT/SSA", "1,0,Default,,0,0,0,,a\\hb\\nc"))
    }

    @Test
    fun `ASS drawings are not text`() {
        assertEquals("", SubtitleTextCleaner.clean("S_TEXT/ASS", "1,0,Sign,,0,0,0,,{\\p1}m 0 0 l 100 0 100 100"))
    }

    @Test
    fun `SRT html tags and entities`() {
        assertEquals("Tom & Jerry <3", SubtitleTextCleaner.clean("S_TEXT/UTF8", "<i>Tom</i> &amp; <b>Jerry</b> &lt;3"))
    }

    @Test
    fun `blank lines and spaces are trimmed`() {
        assertEquals("one\ntwo", SubtitleTextCleaner.clean("S_TEXT/UTF8", "  one \r\n\r\n two  "))
    }
}
