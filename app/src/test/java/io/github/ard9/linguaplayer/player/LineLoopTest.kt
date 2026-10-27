package io.github.ard9.linguaplayer.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LineLoopTest {
    // The line runs from 10 s to 13 s; the loop starts at clock 0 by jumping to 10 s.
    private fun loop() = LineLoop(startMs = 10_000, endMs = 13_000, jumpedAtMs = 0)

    @Test
    fun `waits while the player still reports the old position after the jump`() {
        val loop = loop()
        // Tapped 5 s after the line ended: the old time is far past the end.
        assertFalse(loop.next(timeMs = 18_000, clockMs = 100))
        assertFalse(loop.next(timeMs = 18_000, clockMs = 1_000))
        assertFalse(loop.next(timeMs = 10_050, clockMs = 1_200)) // arrived
        assertFalse(loop.next(timeMs = 12_900, clockMs = 4_000))
    }

    @Test
    fun `jumps back at the end of the line, again and again`() {
        val loop = loop()
        assertFalse(loop.next(10_000, 100))
        assertTrue(loop.next(13_000, 3_100))
        assertFalse(loop.next(13_050, 3_150)) // old position right after the jump
        assertFalse(loop.next(10_000, 3_400))
        assertTrue(loop.next(13_020, 6_400))
    }

    @Test
    fun `a jump that lands a little early counts as arrived`() {
        val loop = loop()
        assertFalse(loop.next(9_000, 100))
        assertTrue(loop.next(13_000, 4_000))
    }

    @Test
    fun `jumps again if the player never arrives`() {
        val loop = loop()
        assertFalse(loop.next(18_000, 3_900))
        assertTrue(loop.next(18_000, 4_000))
    }

    @Test
    fun `never ends by itself when playback is elsewhere`() {
        val loop = loop()
        assertFalse(loop.next(10_000, 100))
        // Far before the line (e.g. paused there): nothing happens, the loop stays.
        assertFalse(loop.next(2_000, 5_000))
        assertTrue(loop.next(40_000, 9_000))
    }
}
