package io.github.ard9.linguaplayer.reminder

import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** When the next daily reminder is due. Pure, so it can be tested. */
object ReminderTime {
    const val DEFAULT_MINUTES = 20 * 60

    /** Time from [now] until the next [minutesOfDay] (0–1439) in [zone]; today if still ahead. */
    fun delayUntilNext(now: Instant, zone: ZoneId, minutesOfDay: Int): Duration {
        val time = LocalTime.of(minutesOfDay / 60, minutesOfDay % 60)
        val local = ZonedDateTime.ofInstant(now, zone)
        var next = local.with(time)
        // A minute of slack, so a run that fires a little early does not schedule itself again today.
        if (!next.isAfter(local.plusMinutes(1))) next = next.plusDays(1).with(time)
        return Duration.between(local, next)
    }
}
