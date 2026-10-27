package io.github.ard9.linguaplayer.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SubtitleParserTest {
    @Test
    fun `parses SRT`() {
        val lines = SubtitleParser.parseText(
            """
            1
            00:00:01,500 --> 00:00:03,000
            <i>Hello</i>

            2
            00:01:02,250 --> 00:01:04,000
            Two
            lines
            """.trimIndent()
        )
        assertEquals(2, lines.size)
        assertEquals(SubtitleLine(1_500, 3_000, "Hello"), lines[0])
        assertEquals(SubtitleLine(62_250, 64_000, "Two\nlines"), lines[1])
    }

    @Test
    fun `parses WebVTT with short timestamps`() {
        val lines = SubtitleParser.parseText("WEBVTT\n\n00:05.000 --> 00:07.500\nShort\n")
        assertEquals(listOf(SubtitleLine(5_000, 7_500, "Short")), lines)
    }

    @Test
    fun `skips broken cues`() {
        val lines = SubtitleParser.parseText("1\n00:00:05,000 --> 00:00:04,000\nBackwards\n\n2\nnot a time\nText\n")
        assertEquals(emptyList<SubtitleLine>(), lines)
    }

    @Test
    fun `decodes UTF-8 with BOM and falls back to Windows-1252`() {
        val bom = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "سلام".toByteArray()
        assertEquals("سلام", SubtitleParser.decode(bom))
        assertEquals("café", SubtitleParser.decode(byteArrayOf(0x63, 0x61, 0x66, 0xE9.toByte())))
    }
}

class SubtitleLookupTest {
    private val lines = listOf(
        SubtitleLine(1_000, 3_000, "first"),
        SubtitleLine(2_500, 4_000, "overlap"),
        SubtitleLine(10_000, 12_000, "later"),
    )

    @Test
    fun `picks the latest line on screen`() {
        assertEquals("overlap", SubtitleLookup.nearMoment(lines, 2_800)?.text)
    }

    @Test
    fun `a tap shortly after a line picks it`() {
        assertEquals("later", SubtitleLookup.nearMoment(lines, 16_000)?.text)
    }

    @Test
    fun `nothing when the last line ended long ago`() {
        assertNull(SubtitleLookup.nearMoment(lines, 17_500))
    }

    @Test
    fun `overlapping finds the translation`() {
        val translation = listOf(SubtitleLine(950, 3_050, "اول"), SubtitleLine(9_900, 12_100, "بعد"))
        assertEquals("بعد", SubtitleLookup.overlapping(translation, lines[2]))
        assertNull(SubtitleLookup.overlapping(translation, SubtitleLine(5_000, 6_000, "none")))
    }
}
