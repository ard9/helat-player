package io.github.ard9.linguaplayer.stream

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ChunkStoreTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun bytes(n: Int, seed: Int) = ByteArray(n) { (it + seed).toByte() }

    @Test
    fun `kept chunks come back, also after a restart`() {
        val store = ChunkStore(temp.root, 1_000_000, 100)
        store.prepare("film", 1_000)
        store.put("film", 3, bytes(100, 3))
        assertArrayEquals(bytes(100, 3), store.get("film", 3, 100))
        assertNull(store.get("film", 4, 100))
        val again = ChunkStore(temp.root, 1_000_000, 100)
        again.prepare("film", 1_000)
        assertArrayEquals(bytes(100, 3), again.get("film", 3, 100))
    }

    @Test
    fun `the oldest chunks go first when full`() {
        val store = ChunkStore(temp.root, 300, 100)
        store.prepare("a", 10_000)
        repeat(3) { store.put("a", it.toLong(), bytes(100, it)) }
        store.get("a", 0, 100) // Recently used again.
        store.put("a", 3, bytes(100, 3))
        assertEquals(300, store.size())
        assertNull(store.get("a", 1, 100))
        assertArrayEquals(bytes(100, 0), store.get("a", 0, 100))
    }

    @Test
    fun `a file of another length starts empty`() {
        val store = ChunkStore(temp.root, 1_000_000, 100)
        store.prepare("film", 1_000)
        store.put("film", 0, bytes(100, 0))
        store.prepare("film", 2_000)
        assertNull(store.get("film", 0, 100))
        assertEquals(0, store.size())
    }

    @Test
    fun `a chunk of the wrong size is not used`() {
        val store = ChunkStore(temp.root, 1_000_000, 100)
        store.prepare("film", 1_050)
        store.put("film", 10, bytes(40, 0))
        assertNull(store.get("film", 10, 50))
        assertTrue(store.size() == 0L)
    }
}
