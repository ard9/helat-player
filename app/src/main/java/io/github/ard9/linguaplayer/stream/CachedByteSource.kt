package io.github.ard9.linguaplayer.stream

import io.github.ard9.linguaplayer.subtitle.mkv.ByteSource
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** Names an online film in the [ChunkStore] without writing its address (which may hold a token). */
fun streamKey(url: String): String =
    MessageDigest.getInstance("SHA-256").digest(url.toByteArray()).joinToString("") { "%02x".format(it) }

/**
 * Reads an online film for the subtitle reader from the parts the player has already kept in
 * [store] (film [key]), and from [network] from the first part that is not kept. The part just
 * played, and what the player read ahead, are then read without a download.
 *
 * Nothing is added to the store: the subtitle reader may read small pieces from far apart
 * (subtitle cues), and keeping whole chunks for those would fill the store.
 */
class CachedByteSource(
    private val store: ChunkStore,
    private val key: String,
    private val network: ByteSource,
) : ByteSource {
    override val isRemote = true

    override fun open(from: Long, toInclusive: Long?): InputStream {
        // Known once the player has opened the film; until then read from the network.
        val total = store.knownLength(key) ?: return network.open(from, toInclusive)
        if (from >= total) return network.open(from, toInclusive)
        val end = minOf(toInclusive ?: (total - 1), total - 1)
        return KeptFirstStream(from, end, total)
    }

    private inner class KeptFirstStream(private var pos: Long, private val end: Long, private val total: Long) : InputStream() {
        private var chunk: ByteArray? = null
        private var chunkStart = -1L
        private var net: InputStream? = null

        override fun read(): Int {
            val one = ByteArray(1)
            return if (read(one, 0, 1) <= 0) -1 else one[0].toInt() and 0xff
        }

        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            if (pos > end) return -1
            val left = end - pos + 1
            net?.let { stream ->
                val n = stream.read(b, off, minOf(len.toLong(), left).toInt())
                if (n > 0) pos += n
                return n
            }
            val size = store.chunkSize
            val index = pos / size
            val start = index * size
            if (start != chunkStart) {
                chunk = store.get(key, index, minOf(size.toLong(), total - start).toInt())
                chunkStart = if (chunk != null) start else -1
            }
            val kept = chunk ?: run {
                // Not kept: the rest comes from the network.
                net = network.open(pos, end)
                return read(b, off, len)
            }
            val inChunk = (pos - start).toInt()
            val n = minOf(len.toLong(), (kept.size - inChunk).toLong(), left).toInt()
            System.arraycopy(kept, inChunk, b, off, n)
            pos += n
            return n
        }

        override fun skip(n: Long): Long {
            if (n <= 0 || pos > end) return 0
            net?.let { stream ->
                val skipped = stream.skip(n)
                pos += skipped
                return skipped
            }
            // Skipping kept data (or up to data that is not kept) costs nothing.
            val skipped = minOf(n, end - pos + 1)
            pos += skipped
            return skipped
        }

        override fun close() {
            try {
                net?.close()
            } catch (e: IOException) {
                // Nothing to do.
            }
        }
    }
}
