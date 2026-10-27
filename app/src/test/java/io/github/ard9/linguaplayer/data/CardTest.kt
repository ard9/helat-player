package io.github.ard9.linguaplayer.data

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

    @Test
    fun `knowing a card moves it up and doubles the wait`() {
        val now = 1_000_000L
        val next = Leitner.knew(card, now)
        assertEquals(2, next.box)
        assertEquals(now + 2 * day, next.nextReviewAt)
        assertEquals(5, Leitner.knew(card.copy(box = 5), now).box)
    }

    @Test
    fun `forgetting a card sends it back to box 1`() {
        val next = Leitner.forgot(card.copy(box = 4), 0)
        assertEquals(1, next.box)
        assertEquals(day, next.nextReviewAt)
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
