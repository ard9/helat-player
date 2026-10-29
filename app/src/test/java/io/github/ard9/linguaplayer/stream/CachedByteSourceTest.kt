package io.github.ard9.linguaplayer.stream

import io.github.ard9.linguaplayer.subtitle.mkv.ByteSource
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.InputStream

class CachedByteSourceTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val chunk = 1024
    private val data = ByteArray(10 * chunk + 300) { (it * 7 + it / 13).toByte() }

    /** The film on the "server"; remembers where each read started. */
    private class Network(private val data: ByteArray) : ByteSource {
        val opened = mutableListOf<Long>()
        override val isRemote = true
        override fun open(from: Long, toInclusive: Long?): InputStream {
            opened.add(from)
            val end = minOf(toInclusive ?: (data.size - 1L), data.size - 1L)
            return ByteArrayInputStream(data, from.toInt(), (end - from + 1).toInt())
        }
    }

    /** A store holding chunks [kept] of the film, as the player would have left it. */
    private fun store(vararg kept: Int): ChunkStore {
        val store = ChunkStore(temp.root, 1L shl 30, chunk)
        store.prepare("film", data.size.toLong())
        for (i in kept) store.put("film", i.toLong(), data.copyOfRange(i * chunk, minOf((i + 1) * chunk, data.size)))
        return store
    }

    @Test
    fun `kept parts are read without the network`() {
        val net = Network(data)
        val source = CachedByteSource(store(2, 3, 4), "film", net)
        val read = source.open(2 * chunk + 100L, 4 * chunk + 900L).use { it.readBytes() }
        assertArrayEquals(data.copyOfRange(2 * chunk + 100, 4 * chunk + 901), read)
        assertEquals(emptyList<Long>(), net.opened)
    }

    @Test
    fun `the network is used from the first part that is not kept`() {
        val net = Network(data)
        val source = CachedByteSource(store(0, 1), "film", net)
        val read = source.open(500, 3 * chunk + 10L).use { it.readBytes() }
        assertArrayEquals(data.copyOfRange(500, 3 * chunk + 11), read)
        assertEquals(listOf(2L * chunk), net.opened)
    }

    @Test
    fun `reads to the end of the film, including a short last chunk`() {
        val net = Network(data)
        val source = CachedByteSource(store(9, 10), "film", net)
        assertArrayEquals(data.copyOfRange(9 * chunk + 5, data.size), source.open(9 * chunk + 5L).use { it.readBytes() })
        assertEquals(emptyList<Long>(), net.opened)
    }

    @Test
    fun `skipping over parts that are not kept downloads nothing`() {
        val net = Network(data)
        val source = CachedByteSource(store(0, 6), "film", net)
        source.open(10).use { stream ->
            assertEquals(data[10], stream.read().toByte())
            assertEquals(6L * chunk - 11, stream.skip(6L * chunk - 11))
            val b = ByteArray(50)
            assertEquals(50, stream.read(b))
            assertArrayEquals(data.copyOfRange(6 * chunk, 6 * chunk + 50), b)
        }
        assertEquals(emptyList<Long>(), net.opened)
    }

    @Test
    fun `before the player has opened the film everything comes from the network`() {
        val net = Network(data)
        val source = CachedByteSource(ChunkStore(temp.root, 1L shl 30, chunk), "film", net)
        assertArrayEquals(data.copyOfRange(100, 200), source.open(100, 199).use { it.readBytes() })
        assertEquals(listOf(100L), net.opened)
    }
}
