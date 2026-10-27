package io.github.ard9.linguaplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class CardSearchTest {
    private fun card(id: String, text: String, meaning: String = "", film: String = "Film", created: Long = 0) = Card(
        id = id, text = text, meaning = meaning, sourceTitle = film, sourceUri = "content://x",
        startMs = 0, endMs = 1_000, audioFileName = "$id.m4a", imageFileName = null,
        box = 1, createdAt = created, nextReviewAt = 0,
    )

    private val cards = listOf(
        card("a", "I'll be right back.", "زود برمی‌گردم", created = 1),
        card("b", "Where are you going?", "کجا میری؟", film = "Big Night", created = 2),
        card("c", "Right now!", "همین الان", created = 3),
    )

    private fun ids(query: String) = CardSearch.find(cards, query).map { it.id }

    @Test
    fun `finds by text, ignoring case, newest first`() {
        assertEquals(listOf("c", "a"), ids("RIGHT"))
    }

    @Test
    fun `every word must match`() {
        assertEquals(listOf("a"), ids("right back"))
        assertEquals(emptyList<String>(), ids("right going"))
    }

    @Test
    fun `finds by meaning and by film`() {
        assertEquals(listOf("b"), ids("کجا"))
        assertEquals(listOf("b"), ids("big night"))
    }

    @Test
    fun `Arabic letter forms and the half space do not matter`() {
        assertEquals(listOf("a"), ids("برميگردم"))
        assertEquals(listOf("b"), ids("ميري"))
    }

    @Test
    fun `an empty query finds nothing`() {
        assertEquals(emptyList<String>(), ids("   "))
    }
}
