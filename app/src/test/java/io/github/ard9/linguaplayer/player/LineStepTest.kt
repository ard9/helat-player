package io.github.ard9.linguaplayer.player

import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LineStepTest {
    private val lines = listOf(
        SubtitleLine(10_000, 12_000, "one"),
        SubtitleLine(14_000, 16_000, "two"),
        SubtitleLine(20_000, 23_000, "three"),
    )

    @Test
    fun `back from inside a line goes to the line before it`() {
        assertEquals(10_000L, LineStep.previousStart(lines, 15_000))
    }

    @Test
    fun `back in the pause after a line goes to that line`() {
        assertEquals(14_000L, LineStep.previousStart(lines, 18_000))
    }

    @Test
    fun `back again from the lead-in of a line moves on`() {
        // A jump to line two lands 0.3 s before it; back must go to line one, not line two.
        assertEquals(10_000L, LineStep.previousStart(lines, 13_700))
    }

    @Test
    fun `no line before the first one`() {
        assertNull(LineStep.previousStart(lines, 11_000))
        assertNull(LineStep.previousStart(lines, 2_000))
    }

    @Test
    fun `forward goes to the next line`() {
        assertEquals(14_000L, LineStep.nextStart(lines, 11_000))
        assertEquals(20_000L, LineStep.nextStart(lines, 17_000))
        assertEquals(10_000L, LineStep.nextStart(lines, 1_000))
    }

    @Test
    fun `forward again from the lead-in of a line moves on`() {
        assertEquals(20_000L, LineStep.nextStart(lines, 13_700))
    }

    @Test
    fun `no line after the last one`() {
        assertNull(LineStep.nextStart(lines, 21_000))
    }

    @Test
    fun `lines sharing a start count once, in any order`() {
        val split = listOf(
            SubtitleLine(14_000, 16_000, "two b"),
            SubtitleLine(10_000, 12_000, "one"),
            SubtitleLine(14_000, 15_000, "two a"),
        )
        assertEquals(14_000L, LineStep.nextStart(split, 11_000))
        assertEquals(10_000L, LineStep.previousStart(split, 15_500))
        assertEquals(14_000L, LineStep.previousStart(split, 17_000))
    }

    @Test
    fun `a line more than 10 s away is not stepped to`() {
        val far = listOf(SubtitleLine(20_000, 22_000, "later"))
        assertNull("next line 10 s + 1 ms away", LineStep.nextStart(far, 9_999))
        assertEquals(20_000L, LineStep.nextStart(far, 10_000))
        val sparse = listOf(SubtitleLine(10_000, 12_000, "one"), SubtitleLine(3_010_000, 3_012_000, "much later"))
        assertNull("50 minutes of silence", LineStep.nextStart(sparse, 13_000))
        assertNull("the line heard is long ago", LineStep.previousStart(sparse, 40_000))
        assertEquals(10_000L, LineStep.previousStart(sparse, 19_000))
    }

    @Test
    fun `no lines`() {
        assertNull(LineStep.previousStart(emptyList(), 5_000))
        assertNull(LineStep.nextStart(emptyList(), 5_000))
    }
}
