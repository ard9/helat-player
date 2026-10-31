package io.github.ard9.linguaplayer.data

import java.time.ZoneId
import java.util.Locale
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CardTest {
    private val day = 24L * 60 * 60 * 1000
    private val card = Card(
        id = "a", text = "Hello", meaning = "سلام", sourceTitle = "Film", sourceUri = "content://x",
        startMs = 1_000, endMs = 2_000, audioFileName = "a.m4a", imageFileName = "a.jpg",
        box = 1, createdAt = 0, nextReviewAt = 0,
    )

    private val utc = ZoneId.of("UTC")
    private val defaults = BoxIntervals.DEFAULT

    @Test
    fun `knowing a card moves it up, due from the start of the day two days on`() {
        val now = 1_000_000L // 00:16 on day 0.
        val next = Leitner.knew(card, now, defaults, utc)
        assertEquals(2, next.box)
        assertEquals(2 * day, next.nextReviewAt)
        assertEquals(5, Leitner.knew(card.copy(box = 5), now, defaults, utc).box)
    }

    @Test
    fun `the time of day of the answer does not matter`() {
        val evening = 22 * 60 * 60 * 1000L
        assertEquals(2 * day, Leitner.knew(card, evening, defaults, utc).nextReviewAt)
        assertEquals(2 * day, Leitner.knew(card, 1L, defaults, utc).nextReviewAt)
    }

    @Test
    fun `days start at midnight in the phone's time zone`() {
        val tehran = ZoneId.of("Asia/Tehran") // UTC+3:30
        // 22:00 UTC on day 0 is already 01:30 on day 1 in Tehran.
        val now = 22 * 60 * 60 * 1000L
        val due = Leitner.forgot(card, now, defaults, tehran).nextReviewAt
        // Day 2 at 00:00 Tehran = day 1 at 20:30 UTC.
        assertEquals(day + 20 * 60 * 60 * 1000L + 30 * 60 * 1000L, due)
    }

    @Test
    fun `forgetting a card sends it back to box 1`() {
        val next = Leitner.forgot(card.copy(box = 4), 0, defaults, utc)
        assertEquals(1, next.box)
        assertEquals(day, next.nextReviewAt)
    }

    @Test
    fun `chosen intervals are used`() {
        val mine = BoxIntervals(listOf(1, 3, 7, 14, 30))
        assertEquals(3 * day, Leitner.knew(card, 5_000, mine, utc).nextReviewAt)
        assertEquals(30 * day, Leitner.knew(card.copy(box = 4), 5_000, mine, utc).nextReviewAt)
    }

    @Test
    fun `a card moved by hand is due as if it had just reached that box`() {
        val moved = Leitner.moveTo(card.copy(box = 2), 4, 5_000, defaults, utc)
        assertEquals(4, moved.box)
        assertEquals(8 * day, moved.nextReviewAt)
        val relearn = Leitner.moveTo(card.copy(box = 5, nextReviewAt = 99 * day), 1, 5_000, defaults, utc)
        assertEquals(1, relearn.box)
        assertEquals("box 1 is due at once", 5_000L, relearn.nextReviewAt)
        assertEquals(5, Leitner.moveTo(card, 9, 0, defaults, utc).box)
    }

    @Test
    fun `cards survive a JSON round trip`() {
        assertEquals(card, Card.fromJson(JSONObject(card.toJson().toString())))
        val noImage = card.copy(imageFileName = null)
        assertEquals(noImage, Card.fromJson(JSONObject(noImage.toJson().toString())))
    }

    @Test
    fun `cards saved by older versions still load`() {
        val old = JSONObject(
            """{"id":"o","text":"Hi","sourceTitle":"F","sourceUri":"u","startMs":1,"endMs":2,
               "audioFileName":"o.m4a","box":9,"createdAt":0,"nextReviewAt":0}"""
        )
        val loaded = Card.fromJson(old)
        assertEquals("", loaded.meaning)
        assertNull(loaded.imageFileName)
        assertEquals(Leitner.BOX_COUNT, loaded.box)
    }

    @Test
    fun `clock text`() {
        assertEquals(83_400L, parseClock("1:23.4"))
        assertEquals(3_723_500L, parseClock("1:02:03.5"))
        assertEquals(83_000L, parseClock("83"))
        assertNull(parseClock("1:75"))
        assertNull(parseClock("abc"))
        assertEquals("1:23.4", formatClock(83_400))
    }

    @Test
    fun `film times use ASCII digits on a Persian phone`() {
        val before = Locale.getDefault()
        try {
            Locale.setDefault(Locale.forLanguageTag("fa"))
            assertEquals("1:02:03", formatTime(3_723_000))
            assertEquals("1:23.4", formatClock(83_400))
            assertEquals(83_400L, parseClock(formatClock(83_400)))
        } finally {
            Locale.setDefault(before)
        }
    }
}
