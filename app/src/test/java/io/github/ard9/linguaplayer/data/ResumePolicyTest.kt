package io.github.ard9.linguaplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumePolicyTest {
    private val hour = 3_600_000L

    @Test
    fun `keeps a position in the middle`() {
        assertEquals(20 * 60_000L, ResumePolicy.positionToKeep(20 * 60_000L, hour))
    }

    @Test
    fun `forgets positions near the start`() {
        assertNull(ResumePolicy.positionToKeep(5_000, hour))
    }

    @Test
    fun `forgets positions near the end`() {
        assertNull(ResumePolicy.positionToKeep(hour - 10_000, hour))
    }

    @Test
    fun `keeps positions of streams with unknown length`() {
        assertEquals(60_000L, ResumePolicy.positionToKeep(60_000, 0))
    }
}
