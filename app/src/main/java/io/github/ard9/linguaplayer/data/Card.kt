package io.github.ard9.linguaplayer.data

import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

/** One saved sentence: its audio clip, optional picture, text, meaning and Leitner state. */
data class Card(
    val id: String,
    val text: String,
    val meaning: String,
    val sourceTitle: String,
    val sourceUri: String,
    val startMs: Long,
    val endMs: Long,
    val audioFileName: String,
    /** A frame of the video at the moment of saving; null if none could be taken. */
    val imageFileName: String?,
    val box: Int,
    val createdAt: Long,
    val nextReviewAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("text", text)
        .put("meaning", meaning)
        .put("sourceTitle", sourceTitle)
        .put("sourceUri", sourceUri)
        .put("startMs", startMs)
        .put("endMs", endMs)
        .put("audioFileName", audioFileName)
        .put("imageFileName", imageFileName ?: JSONObject.NULL)
        .put("box", box)
        .put("createdAt", createdAt)
        .put("nextReviewAt", nextReviewAt)

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJson(o: JSONObject): Card = Card(
            id = o.getString("id"),
            text = o.getString("text"),
            meaning = o.optString("meaning"),
            sourceTitle = o.optString("sourceTitle"),
            sourceUri = o.optString("sourceUri"),
            startMs = o.getLong("startMs"),
            endMs = o.getLong("endMs"),
            audioFileName = o.getString("audioFileName"),
            imageFileName = if (o.isNull("imageFileName")) null else o.getString("imageFileName"),
            box = o.getInt("box").coerceIn(1, Leitner.BOX_COUNT),
            createdAt = o.getLong("createdAt"),
            nextReviewAt = o.getLong("nextReviewAt"),
        )
    }
}

/**
 * Leitner system with 5 boxes. A box number is how well the card is known. A card is next due
 * at the start of a day (in the phone's time zone), so reviewing at a different hour does not
 * push it later; how many days each box waits is set in Settings ([BoxIntervals]).
 */
object Leitner {
    const val BOX_COUNT = 5

    fun isDue(card: Card, now: Long): Boolean = card.nextReviewAt <= now

    /** When a card that reaches [box] at [now] is due: the start of the day [intervals] ahead. */
    fun dueAt(box: Int, now: Long, intervals: BoxIntervals, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
            .plusDays(intervals.daysFor(box).toLong())
            .atStartOfDay(zone).toInstant().toEpochMilli()

    fun knew(card: Card, now: Long, intervals: BoxIntervals, zone: ZoneId = ZoneId.systemDefault()): Card {
        val box = min(BOX_COUNT, card.box + 1)
        return card.copy(box = box, nextReviewAt = dueAt(box, now, intervals, zone))
    }

    fun forgot(card: Card, now: Long, intervals: BoxIntervals, zone: ZoneId = ZoneId.systemDefault()): Card =
        card.copy(box = 1, nextReviewAt = dueAt(1, now, intervals, zone))

    /**
     * Moved by hand: due as if it had just reached [box]. Box 1 is due at once, as moving a card
     * there means learning it again.
     */
    fun moveTo(card: Card, box: Int, now: Long, intervals: BoxIntervals, zone: ZoneId = ZoneId.systemDefault()): Card {
        val target = box.coerceIn(1, BOX_COUNT)
        return card.copy(box = target, nextReviewAt = if (target == 1) now else dueAt(target, now, intervals, zone))
    }
}

/** Like [formatTime] but with tenths of a second, e.g. "1:23.4". */
fun formatClock(ms: Long): String = "${formatTime(ms)}.${(ms % 1000) / 100}"

/** Parses "83", "1:23", "1:23.4" or "1:02:03.5" into milliseconds; null if invalid. */
fun parseClock(text: String): Long? {
    val parts = text.trim().split(':')
    if (parts.isEmpty() || parts.size > 3) return null
    val seconds = parts.last().toDoubleOrNull() ?: return null
    if (seconds < 0 || (parts.size > 1 && seconds >= 60)) return null
    var total = 0L
    for (part in parts.dropLast(1)) {
        val value = part.toLongOrNull() ?: return null
        if (value < 0) return null
        total = total * 60 + value
    }
    return total * 60_000 + Math.round(seconds * 1000)
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    // Always ASCII digits: film times are also typed into the editor and parsed back.
    return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s) else String.format(Locale.US, "%d:%02d", m, s)
}
