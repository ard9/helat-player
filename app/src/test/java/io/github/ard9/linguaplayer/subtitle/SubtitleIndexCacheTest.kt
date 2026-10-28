package io.github.ard9.linguaplayer.subtitle

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SubtitleIndexCacheTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val lines = mapOf(
        2L to listOf(SubtitleLine(2_000, 4_500, "Hello there."), SubtitleLine(5_000, 8_200, "Line\nTwo")),
        3L to listOf(SubtitleLine(2_000, 4_500, "سلام.")),
    )
    private val uri = "https://example.com/film.mkv?token=secret"

    @Test
    fun `saved lines come back for the same tracks`() {
        val cache = SubtitleIndexCache(temp.root)
        cache.save(uri, lines)
        assertEquals(lines, cache.load(uri, setOf(2L, 3L)))
        assertNull(cache.load(uri, setOf(2L)))
        assertNull(cache.load("https://example.com/other.mkv", setOf(2L, 3L)))
    }

    @Test
    fun `the address is not written in the file name or the file`() {
        val cache = SubtitleIndexCache(temp.root)
        cache.save(uri, lines)
        val file = temp.root.listFiles()!!.single()
        assertFalse(file.name.contains("secret"))
        assertFalse(file.readText().contains("secret"))
    }

    @Test
    fun `a damaged file is ignored and removed`() {
        val cache = SubtitleIndexCache(temp.root)
        cache.save(uri, lines)
        temp.root.listFiles()!!.single().writeText("{not json")
        assertNull(cache.load(uri, setOf(2L, 3L)))
        assertEquals(0, temp.root.listFiles()!!.size)
    }

    @Test
    fun `only the most recent films are kept`() {
        val cache = SubtitleIndexCache(temp.root)
        repeat(SubtitleIndexCache.MAX_FILES + 5) { cache.save("film-$it", lines) }
        assertEquals(SubtitleIndexCache.MAX_FILES, temp.root.listFiles()!!.size)
    }
}
