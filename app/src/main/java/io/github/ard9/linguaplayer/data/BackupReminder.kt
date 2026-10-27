package io.github.ard9.linguaplayer.data

/** Decides when the home screen suggests making a backup. Pure, so it can be tested. */
object BackupReminder {
    const val DAY_MS = 24L * 60 * 60 * 1000
    /** Remind when the cards are not in any backup this long. */
    const val AFTER_MS = 14 * DAY_MS
    /** Closing the reminder hides it this long. */
    const val SNOOZE_MS = 7 * DAY_MS

    /**
     * Whole days since the cards were last backed up (or since the oldest card, if never), or
     * null when no reminder is due: no cards, snoozed, or backed up recently.
     */
    fun daysWithoutBackup(now: Long, cards: List<Card>, lastBackupAt: Long?, snoozedUntil: Long): Int? {
        if (cards.isEmpty() || now < snoozedUntil) return null
        val since = lastBackupAt ?: cards.minOf { it.createdAt }
        val elapsed = now - since
        return if (elapsed >= AFTER_MS) (elapsed / DAY_MS).toInt() else null
    }
}
