package io.github.ard9.linguaplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BoxIntervalsTest {
    private val defaults = BoxIntervals.DEFAULT

    @Test
    fun `defaults are 1, 2, 4, 8 and 16 days`() {
        assertEquals(listOf(1, 2, 4, 8, 16), defaults.days)
        assertEquals(1, defaults.daysFor(1))
        assertEquals(16, defaults.daysFor(5))
        assertEquals(16, defaults.daysFor(9))
    }

    @Test
    fun `a change within the order touches only that box`() {
        assertEquals(listOf(1, 3, 4, 8, 16), defaults.with(2, 3).days)
    }

    @Test
    fun `a longer box raises the later ones, a shorter one lowers the earlier ones`() {
        assertEquals(listOf(1, 2, 10, 10, 16), defaults.with(3, 10).days)
        assertEquals(listOf(1, 2, 3, 3, 16), defaults.with(4, 3).days)
    }

    @Test
    fun `values stay between 1 and 365 days`() {
        assertEquals(1, defaults.with(1, 0).daysFor(1))
        assertEquals(listOf(1, 2, 4, 8, 365), defaults.with(5, 1000).days)
    }

    @Test
    fun `stored values are cleaned up`() {
        assertEquals(defaults, BoxIntervals.sanitized(listOf(null, null, null, null, null)))
        assertEquals(listOf(3, 3, 4, 8, 16), BoxIntervals.sanitized(listOf(3, 2, null, null, null)).days)
        assertEquals(listOf(1, 2, 4, 8, 365), BoxIntervals.sanitized(listOf(0, 2, 4, 8, 9999)).days)
    }
}
