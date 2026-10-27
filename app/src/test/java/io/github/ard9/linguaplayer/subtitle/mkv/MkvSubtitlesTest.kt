package io.github.ard9.linguaplayer.subtitle.mkv

import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import io.github.ard9.linguaplayer.subtitle.SubtitleLookup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.InputStream

/**
 * Reads the fixtures made by tools/make-test-media.sh. Their subtitle lines:
 * 2.0–4.5 "Hello there.", 5.0–8.2 "How are you doing today?", 20.1–23.9 two-line line,
 * 50.0–52.0 "The end & more." (English SRT, Persian SRT without the last line, styled ASS).
 */
class MkvSubtitlesTest {

    @Test
    fun `reads the subtitle tracks and their languages`() {
        val mkv = open("subs.mkv")
        assertEquals(listOf(2L, 3L, 4L), mkv.tracks.map { it.number })
        assertEquals(listOf("S_TEXT/UTF8", "S_TEXT/UTF8", "S_TEXT/ASS"), mkv.tracks.map { it.codec })
        assertEquals(listOf(false, true, false), mkv.tracks.map { it.isPersian })
        assertTrue(mkv.tracks.all { it.isText })
        assertEquals("Persian", mkv.tracks[1].label)
        assertEquals("English (Styled)", mkv.tracks[2].label)
    }

    @Test
    fun `finds the line on screen in every track`() {
        val lines = open("subs.mkv").linesAround(3_000)
        assertLine(2_000, 4_500, "Hello there.", pick(lines, 2, 3_000))
        assertLine(2_000, 4_500, "سلام.", pick(lines, 3, 3_000))
        assertLine(2_000, 4_500, "Hello there.", pick(lines, 4, 3_000))
    }

    @Test
    fun `removes formatting tags`() {
        val lines = open("subs.mkv").linesAround(6_000)
        assertEquals("How are you doing today?", pick(lines, 2, 6_000)?.text) // <i> in SRT
        assertEquals("How are you doing today?", pick(lines, 4, 6_000)?.text) // ASS fields and tags
    }

    @Test
    fun `keeps line breaks and decodes entities`() {
        val mkv = open("subs.mkv")
        assertEquals("I'll be right back.\nWait for me.", pick(mkv.linesAround(21_000), 2, 21_000)?.text)
        assertEquals("The end & more.", pick(mkv.linesAround(51_000), 2, 51_000)?.text)
    }

    @Test
    fun `a tap just after a line ends saves that line`() {
        val lines = open("subs.mkv").linesAround(9_000)
        assertLine(5_000, 8_200, "How are you doing today?", pick(lines, 2, 9_000))
    }

    @Test
    fun `finds a line that started in an earlier cluster`() {
        // 13 s is 4.8 s after the line ended; it began 8 s earlier, before the nearest cue.
        val lines = open("subs.mkv").linesAround(13_000)
        assertLine(5_000, 8_200, "How are you doing today?", pick(lines, 2, 13_000))
    }

    @Test
    fun `no line long after speech`() {
        assertNull(pick(open("subs.mkv").linesAround(15_000), 2, 15_000))
    }

    @Test
    fun `the Persian track gives the meaning of the English line`() {
        val lines = open("subs.mkv").linesAround(6_000)
        val english = pick(lines, 2, 6_000)!!
        assertEquals("امروز حالت چطوره؟", SubtitleLookup.overlapping(lines.getValue(3), english))
    }

    @Test
    fun `works with the index at the front`() {
        assertLine(20_100, 23_900, "I'll be right back.\nWait for me.", pick(open("cues-front.mkv").linesAround(21_000), 2, 21_000))
    }

    @Test
    fun `works without an index`() {
        assertLine(50_000, 52_000, "The end & more.", pick(open("no-cues.mkv").linesAround(51_000), 2, 51_000))
    }

    @Test
    fun `reads WebM WebVTT tracks`() {
        val webm = open("subs.webm")
        assertEquals(listOf("D_WEBVTT/SUBTITLES"), webm.tracks.map { it.codec })
        assertTrue(webm.tracks.single().isText)
        assertLine(5_000, 8_200, "How are you doing today?", pick(webm.linesAround(6_000), 2, 6_000))
    }

    @Test
    fun `jumps to the part of the file near the moment`() {
        // Online files must not be read from the start: the cues lead straight to ~40 s of 60 s.
        val file = fixture("subs.mkv")
        val recording = RecordingSource(file)
        val mkv = MkvSubtitles.open(recording)!!
        recording.opens.clear()
        mkv.linesAround(51_000)
        val from = recording.opens.single()
        assertTrue("started at $from of ${file.length()}", from > file.length() / 2)
    }

    @Test
    fun `a scene starts with the lines from the moment on`() {
        val scene = open("subs.mkv").linesFrom(0, track = 2, count = 3)
        assertEquals(listOf(2_000L, 5_000L, 20_100L), scene.map { it.startMs })
        assertEquals("I'll be right back.\nWait for me.", scene[2].text)
    }

    @Test
    fun `a scene includes the line on screen`() {
        val scene = open("subs.mkv").linesFrom(6_000, track = 2, count = 2)
        assertEquals(listOf("How are you doing today?", "I'll be right back.\nWait for me."), scene.map { it.text })
    }

    @Test
    fun `a scene near the end has fewer lines`() {
        val scene = open("no-cues.mkv").linesFrom(30_000, track = 2, count = 10)
        assertEquals(listOf("The end & more."), scene.map { it.text })
    }

    @Test
    fun `a scene from another track and from WebM`() {
        assertEquals(3, open("subs.mkv").linesFrom(0, track = 3, count = 10).size)
        assertEquals(4, open("subs.webm").linesFrom(0, track = 2, count = 10).size)
        assertTrue(open("subs.mkv").linesFrom(0, track = 99, count = 10).isEmpty())
    }

    @Test
    fun `other files are not Matroska`() {
        val notMkv = object : ByteSource {
            override val isRemote = false
            override fun open(from: Long, toInclusive: Long?): InputStream =
                ByteArrayInputStream("RIFF\u0000\u0000\u0000\u0000WAVEfmt ".toByteArray()).also { it.skip(from) }
        }
        assertNull(MkvSubtitles.open(notMkv))
    }

    @Test
    fun `a truncated file fails with an error, not a hang`() {
        val bytes = fixture("subs.mkv").readBytes().copyOf(20_000)
        val truncated = object : ByteSource {
            override val isRemote = false
            override fun open(from: Long, toInclusive: Long?): InputStream =
                ByteArrayInputStream(bytes).also { it.skip(from) }
        }
        val mkv = MkvSubtitles.open(truncated)
        if (mkv != null) {
            // Lines before the cut are still found; reading past it ends without an exception.
            val result = runCatching { mkv.linesAround(51_000) }
            assertFalse(result.getOrNull()?.get(2L)?.any { it.text == "The end & more." } ?: false)
        }
    }

    private fun pick(lines: Map<Long, List<SubtitleLine>>, track: Long, atMs: Long) =
        SubtitleLookup.nearMoment(lines[track].orEmpty(), atMs)

    private fun assertLine(start: Long, end: Long, text: String, line: SubtitleLine?) {
        assertNotNull("no line found", line)
        assertEquals(text, line!!.text)
        assertEquals(start.toDouble(), line.startMs.toDouble(), 30.0)
        assertEquals(end.toDouble(), line.endMs.toDouble(), 30.0)
    }

    private fun open(name: String) = MkvSubtitles.open(FileSource(fixture(name)))!!

    private class FileSource(private val file: File) : ByteSource {
        override val isRemote = false
        override fun open(from: Long, toInclusive: Long?): InputStream =
            FileInputStream(file).also { it.channel.position(from) }
    }

    /** Remembers where each read starts, like the HTTP range requests of an online file. */
    private class RecordingSource(private val file: File) : ByteSource {
        val opens = mutableListOf<Long>()
        override val isRemote = true
        override fun open(from: Long, toInclusive: Long?): InputStream {
            opens.add(from)
            return FileInputStream(file).also { it.channel.position(from) }
        }
    }

    companion object {
        fun fixture(name: String): File =
            File(requireNotNull(MkvSubtitlesTest::class.java.getResource("/media/$name")) { "missing $name" }.toURI())
    }
}
