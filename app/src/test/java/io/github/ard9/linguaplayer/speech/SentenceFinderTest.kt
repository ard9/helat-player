package io.github.ard9.linguaplayer.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SentenceFinderTest {
    /** Builds per-frame probabilities: speech (0.9) inside the given ranges, else silence (0.05). */
    private fun probabilities(totalMs: Int, vararg speech: IntRange, dips: List<IntRange> = emptyList()): FloatArray {
        val frames = (totalMs / SentenceFinder.FRAME_MS).toInt()
        return FloatArray(frames) { i ->
            val ms = (i * SentenceFinder.FRAME_MS).toInt()
            when {
                dips.any { ms in it } -> 0.4f
                speech.any { ms in it } -> 0.9f
                else -> 0.05f
            }
        }
    }

    private fun assertSpan(startMs: Int, endMs: Int, span: SentenceFinder.Span?) {
        assertNotNull(span)
        assertEquals(startMs.toDouble(), span!!.startMs, 40.0)
        assertEquals(endMs.toDouble(), span.endMs, 40.0)
    }

    @Test
    fun `finds the sentence around the moment`() {
        val p = probabilities(14_000, 1_000..3_000, 5_000..7_000)
        assertSpan(5_000, 7_000, SentenceFinder.find(p, 6_000.0))
    }

    @Test
    fun `in a pause picks the sentence that just ended`() {
        val p = probabilities(14_000, 1_000..3_000, 5_000..7_000)
        assertSpan(1_000, 3_000, SentenceFinder.find(p, 4_000.0))
    }

    @Test
    fun `short pauses between words stay in one sentence`() {
        val p = probabilities(14_000, 1_000..2_000, 2_250..4_000)
        assertSpan(1_000, 4_000, SentenceFinder.find(p, 1_500.0))
    }

    @Test
    fun `very long speech is split at dips`() {
        val p = probabilities(14_000, 500..12_000, dips = listOf(6_000..6_300))
        assertSpan(6_300, 12_000, SentenceFinder.find(p, 9_000.0))
    }

    @Test
    fun `no speech gives nothing`() {
        assertNull(SentenceFinder.find(probabilities(14_000), 7_000.0))
    }

    @Test
    fun `too short noises are ignored`() {
        assertNull(SentenceFinder.find(probabilities(14_000, 5_000..5_100), 5_050.0))
    }
}
