package io.github.ard9.linguaplayer.data

import org.json.JSONObject
import java.util.UUID
import kotlin.math.min

/** One saved sentence: its audio clip, subtitle text and Leitner state. */
data class Card(
    val id: String,
    val text: String,
    val sourceTitle: String,
    val sourceUri: String,
    val startMs: Long,
    val endMs: Long,
    val audioFileName: String,
    val box: Int,
    val createdAt: Long,
    val nextReviewAt: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("text", text)
        .put("sourceTitle", sourceTitle)
        .put("sourceUri", sourceUri)
        .put("startMs", startMs)
        .put("endMs", endMs)
        .put("audioFileName", audioFileName)
        .put("box", box)
        .put("createdAt", createdAt)
        .put("nextReviewAt", nextReviewAt)

    companion object {
        fun newId(): String = UUID.randomUUID().toString()

        fun fromJson(o: JSONObject): Card = Card(
            id = o.getString("id"),
            text = o.getString("text"),
            sourceTitle = o.optString("sourceTitle"),
            sourceUri = o.optString("sourceUri"),
            startMs = o.getLong("startMs"),
            endMs = o.getLong("endMs"),
            audioFileName = o.getString("audioFileName"),
            box = o.getInt("box").coerceIn(1, Leitner.BOX_COUNT),
            createdAt = o.getLong("createdAt"),
            nextReviewAt = o.getLong("nextReviewAt"),
        )
    }
}

/** Leitner system with 5 boxes. A box number is how well the card is known. */
object Leitner {
    const val BOX_COUNT = 5
    private const val DAY_MS = 24L * 60 * 60 * 1000
    private val intervalDays = intArrayOf(1, 2, 4, 8, 16)

    fun intervalMs(box: Int): Long = intervalDays[box.coerceIn(1, BOX_COUNT) - 1] * DAY_MS

    fun isDue(card: Card, now: Long): Boolean = card.nextReviewAt <= now

    fun knew(card: Card, now: Long): Card {
        val box = min(BOX_COUNT, card.box + 1)
        return card.copy(box = box, nextReviewAt = now + intervalMs(box))
    }

    fun forgot(card: Card, now: Long): Card =
        card.copy(box = 1, nextReviewAt = now + intervalMs(1))
}

fun formatTime(ms: Long): String {
    val totalSeconds = ms / 1000
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
