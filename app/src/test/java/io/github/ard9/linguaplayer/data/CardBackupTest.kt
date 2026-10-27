package io.github.ard9.linguaplayer.data

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CardBackupTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun card(id: String, image: Boolean = true) = Card(
        id = id, text = "Text $id", meaning = "معنی $id", sourceTitle = "Film", sourceUri = "content://film",
        startMs = 1_000, endMs = 2_000, audioFileName = "$id.m4a", imageFileName = if (image) "$id.jpg" else null,
        box = 3, createdAt = 10, nextReviewAt = 20,
    )

    private class Store(root: File) {
        val clips = File(root, "clips").apply { mkdirs() }
        val images = File(root, "images").apply { mkdirs() }
        fun audio(c: Card) = File(clips, c.audioFileName)
        fun image(c: Card) = c.imageFileName?.let { File(images, it) }
    }

    private fun backupOf(cards: List<Card>, store: Store): ByteArray {
        val out = ByteArrayOutputStream()
        CardBackup.export(out, cards, store::audio, store::image)
        return out.toByteArray()
    }

    /** A hand-made ZIP, for backups the app would never write. */
    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            for ((name, data) in entries) {
                z.putNextEntry(ZipEntry(name))
                z.write(data)
                z.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun cardsJson(vararg cards: Card) =
        JSONObject().put("format", 1).put("cards", JSONArray().apply { cards.forEach { put(it.toJson()) } })
            .toString().toByteArray()

    @Test
    fun `export and import restore cards and their files`() {
        val source = Store(temp.newFolder())
        val cards = listOf(card("a"), card("b", image = false))
        cards.forEach { source.audio(it).writeText("audio ${it.id}") }
        source.image(cards[0])!!.writeText("image a")

        val target = Store(temp.newFolder())
        val result = CardBackup.import(ByteArrayInputStream(backupOf(cards, source)), emptySet(), target.clips, target.images)

        assertEquals(cards, result.added)
        assertEquals(0, result.skipped)
        assertEquals("audio b", target.audio(cards[1]).readText())
        assertEquals("image a", target.image(cards[0])!!.readText())
    }

    @Test
    fun `cards already present are skipped`() {
        val source = Store(temp.newFolder())
        val cards = listOf(card("a"), card("b"))
        cards.forEach { source.audio(it).writeText("x") }
        val target = Store(temp.newFolder())
        val result = CardBackup.import(ByteArrayInputStream(backupOf(cards, source)), setOf("a"), target.clips, target.images)
        assertEquals(listOf("b"), result.added.map { it.id })
        assertEquals(1, result.skipped)
        assertFalse(File(target.clips, "a.m4a").exists())
    }

    @Test
    fun `a card whose audio is missing is not added`() {
        val target = Store(temp.newFolder())
        val result = CardBackup.import(
            ByteArrayInputStream(zip("cards.json" to cardsJson(card("a")))), emptySet(), target.clips, target.images,
        )
        assertTrue(result.added.isEmpty())
        assertEquals(1, result.skipped)
    }

    @Test
    fun `files cannot be written outside the card folders`() {
        val root = temp.newFolder()
        val target = Store(root)
        val evil = card("a").copy(audioFileName = "../../evil.m4a", imageFileName = "../escape.jpg")
        val backup = zip(
            "cards.json" to cardsJson(evil),
            "clips/../../evil.m4a" to "bad".toByteArray(),
            "../escape.jpg" to "bad".toByteArray(),
        )
        val result = CardBackup.import(ByteArrayInputStream(backup), emptySet(), target.clips, target.images)
        assertTrue(result.added.isEmpty())
        assertFalse(File(root.parentFile, "evil.m4a").exists())
        assertFalse(File(root, "escape.jpg").exists())
        assertTrue(root.walkTopDown().filter { it.isFile }.none())
    }

    @Test
    fun `existing files are never overwritten`() {
        val target = Store(temp.newFolder())
        File(target.clips, "mine.m4a").writeText("my audio")
        val sneaky = card("new").copy(audioFileName = "mine.m4a", imageFileName = null)
        val backup = zip("cards.json" to cardsJson(sneaky), "clips/mine.m4a" to "replaced".toByteArray())
        val result = CardBackup.import(ByteArrayInputStream(backup), emptySet(), target.clips, target.images)
        assertTrue(result.added.isEmpty())
        assertEquals("my audio", File(target.clips, "mine.m4a").readText())
    }

    @Test
    fun `files no card asks for are ignored`() {
        val target = Store(temp.newFolder())
        val backup = zip("cards.json" to cardsJson(), "clips/extra.m4a" to "x".toByteArray())
        CardBackup.import(ByteArrayInputStream(backup), emptySet(), target.clips, target.images)
        assertFalse(File(target.clips, "extra.m4a").exists())
    }

    @Test
    fun `other ZIP files are rejected without writing anything`() {
        val target = Store(temp.newFolder())
        val backup = zip("hello.txt" to "hi".toByteArray(), "clips/a.m4a" to "x".toByteArray())
        expectFailure { CardBackup.import(ByteArrayInputStream(backup), emptySet(), target.clips, target.images) }
        assertTrue(target.clips.list()!!.isEmpty())
    }

    @Test
    fun `damaged card data is rejected`() {
        val target = Store(temp.newFolder())
        expectFailure {
            CardBackup.import(ByteArrayInputStream(zip("cards.json" to "{not json".toByteArray())), emptySet(), target.clips, target.images)
        }
    }

    @Test
    fun `a backup from a newer format is rejected`() {
        val target = Store(temp.newFolder())
        val json = JSONObject().put("format", 99).put("cards", JSONArray()).toString().toByteArray()
        expectFailure { CardBackup.import(ByteArrayInputStream(zip("cards.json" to json)), emptySet(), target.clips, target.images) }
    }

    private fun expectFailure(block: () -> Unit) {
        try {
            block()
            fail("expected the import to fail")
        } catch (e: CardBackup.InvalidBackupException) {
            // Expected.
        }
    }
}
