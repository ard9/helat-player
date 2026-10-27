package io.github.ard9.linguaplayer.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderTimeTest {
    private val tehran = ZoneId.of("Asia/Tehran")
    private fun at(h: Int, m: Int) = ZonedDateTime.of(2026, 10, 27, h, m, 0, 0, tehran).toInstant()

    @Test
    fun `later today when the time is still ahead`() {
        assertEquals(Duration.ofMinutes(4 * 60 + 30), ReminderTime.delayUntilNext(at(15, 30), tehran, 20 * 60))
    }

    @Test
    fun `tomorrow when the time has passed`() {
        assertEquals(Duration.ofHours(23), ReminderTime.delayUntilNext(at(21, 0), tehran, 20 * 60))
    }

    @Test
    fun `a run that fires right on time schedules tomorrow`() {
        assertEquals(Duration.ofHours(24), ReminderTime.delayUntilNext(at(20, 0), tehran, 20 * 60))
        assertEquals(Duration.ofMinutes(24 * 60 + 1), ReminderTime.delayUntilNext(at(19, 59), tehran, 20 * 60))
    }

    @Test
    fun `midnight and the last minute of the day`() {
        assertEquals(Duration.ofMinutes(30), ReminderTime.delayUntilNext(at(23, 30), tehran, 0))
        assertEquals(Duration.ofMinutes(29), ReminderTime.delayUntilNext(at(23, 30), tehran, 23 * 60 + 59))
    }
}
