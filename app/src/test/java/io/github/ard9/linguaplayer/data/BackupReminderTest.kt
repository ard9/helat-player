package io.github.ard9.linguaplayer.data

import io.github.ard9.linguaplayer.data.BackupReminder.DAY_MS
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BackupReminderTest {
    private fun card(created: Long) = Card(
        id = "c$created", text = "", meaning = "", sourceTitle = "Film", sourceUri = "content://x",
        startMs = 0, endMs = 1_000, audioFileName = "a.m4a", imageFileName = null,
        box = 1, createdAt = created, nextReviewAt = 0,
    )

    private val now = 100 * DAY_MS
    private val cards = listOf(card(now - 30 * DAY_MS), card(now - 2 * DAY_MS))

    @Test
    fun `no cards, no reminder`() {
        assertNull(BackupReminder.daysWithoutBackup(now, emptyList(), null, 0))
    }

    @Test
    fun `never backed up counts from the oldest card`() {
        assertEquals(30, BackupReminder.daysWithoutBackup(now, cards, null, 0))
        assertNull(BackupReminder.daysWithoutBackup(now, listOf(card(now - 13 * DAY_MS)), null, 0))
    }

    @Test
    fun `a recent backup means no reminder`() {
        assertNull(BackupReminder.daysWithoutBackup(now, cards, now - 13 * DAY_MS, 0))
        assertEquals(14, BackupReminder.daysWithoutBackup(now, cards, now - 14 * DAY_MS, 0))
    }

    @Test
    fun `closing it hides it until the snooze ends`() {
        assertNull(BackupReminder.daysWithoutBackup(now, cards, null, now + 1))
        assertEquals(30, BackupReminder.daysWithoutBackup(now, cards, null, now))
    }
}
