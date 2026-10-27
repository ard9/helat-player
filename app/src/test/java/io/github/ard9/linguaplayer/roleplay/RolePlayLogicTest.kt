package io.github.ard9.linguaplayer.roleplay

import io.github.ard9.linguaplayer.speech.WavReader
import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RolePlayLogicTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val lines = (0 until 5).map { SubtitleLine(it * 3_000L, it * 3_000L + 2_000, "line $it") }

    @Test
    fun `the actor starts and roles alternate`() {
        assertEquals(listOf(false, true, false, true, false), Roles.alternate(lines).map { it.mine })
    }

    @Test
    fun `the user can start`() {
        assertEquals(listOf(true, false, true, false, true), Roles.alternate(lines, iStart = true).map { it.mine })
    }

    /** Feeds [ms] of probability [p] and returns whether the detector ended the turn. */
    private fun SpeechEndDetector.feed(p: Float, ms: Int): Boolean {
        var ended = false
        repeat(ms / 32) { if (next(p)) ended = true }
        return ended
    }

    @Test
    fun `a pause after speech ends the turn`() {
        val d = SpeechEndDetector()
        assertFalse(d.feed(0.05f, 1_000)) // thinking before speaking
        assertFalse(d.feed(0.9f, 2_000))
        assertFalse(d.feed(0.05f, 500)) // a pause between words
        assertFalse(d.feed(0.9f, 1_000))
        assertTrue(d.feed(0.05f, 1_000))
        assertTrue(d.heardSpeech)
    }

    @Test
    fun `knows where speech began`() {
        val d = SpeechEndDetector()
        d.feed(0.05f, 1_000) // 31 frames of silence: 992 ms
        assertEquals(0L, d.speechStartMs)
        d.feed(0.9f, 500)
        assertEquals(992L, d.speechStartMs)
        d.feed(0.05f, 300)
        d.feed(0.9f, 300) // later speech does not move the start
        assertEquals(992L, d.speechStartMs)
    }

    @Test
    fun `a click is not speech`() {
        val d = SpeechEndDetector()
        assertFalse(d.feed(0.9f, 64))
        assertFalse(d.feed(0.05f, 3_000))
        assertFalse(d.heardSpeech)
    }

    @Test
    fun `gives up when nothing is said`() {
        val d = SpeechEndDetector(noSpeechMs = 4_000)
        assertFalse(d.feed(0.05f, 3_900))
        assertTrue(d.feed(0.05f, 200))
        assertFalse(d.heardSpeech)
    }

    @Test
    fun `never longer than the maximum`() {
        val d = SpeechEndDetector(maxMs = 5_000)
        assertFalse(d.feed(0.9f, 4_900))
        assertTrue(d.feed(0.9f, 200))
    }

    @Test
    fun `the WAV writer makes files the reader understands`() {
        val file = temp.newFile("voice.wav")
        WavWriter(file, 16_000).use { w ->
            w.write(ShortArray(16_000) { 1_000 })
            w.write(ShortArray(8_000) { 2_000 }, count = 4_000)
            assertEquals(1_250, w.durationMs)
        }
        val samples = WavReader.read(file, 16_000, 60_000)
        assertEquals(20_000, samples.size)
        assertEquals(1_000 / 32768f, samples.first(), 1e-6f)
        assertEquals(2_000 / 32768f, samples.last(), 1e-6f)
    }
}
