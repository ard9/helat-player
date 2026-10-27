package io.github.ard9.linguaplayer.subtitle.mkv

import java.io.EOFException
import java.io.IOException
import java.io.InputStream

/**
 * Sequential reader for EBML (the format Matroska/WebM files are made of).
 * Tracks the absolute file position and stops after [budget] bytes, so a damaged or huge
 * file cannot make it read (or download) without end.
 */
class EbmlReader(input: InputStream, start: Long, private val budget: Long) {
    class ReadLimitException : IOException("Read limit reached")

    class Header(val id: Long, val size: Long, val start: Long, val payload: Long)

    private val input = input.buffered(64 * 1024)
    var position: Long = start
        private set
    private val origin = start
    private var pushedBack: Header? = null

    private fun spend(n: Long) {
        position += n
        if (position - origin > budget) throw ReadLimitException()
    }

    fun byte(): Int {
        val b = input.read()
        if (b < 0) throw EOFException()
        spend(1)
        return b
    }

    fun bytes(n: Long, max: Int): ByteArray {
        if (n < 0 || n > max) throw IOException("Element too large")
        val out = ByteArray(n.toInt())
        var off = 0
        while (off < out.size) {
            val r = input.read(out, off, out.size - off)
            if (r < 0) throw EOFException()
            off += r
        }
        spend(n)
        return out
    }

    fun skip(n: Long) {
        var left = n
        while (left > 0) {
            val skipped = input.skip(left)
            if (skipped <= 0) {
                if (input.read() < 0) throw EOFException()
                spend(1)
                left--
            } else {
                spend(skipped)
                left -= skipped
            }
        }
    }

    /** Variable-length integer; ids keep their length marker, sizes do not. */
    fun vint(keepMarker: Boolean): Long {
        val first = byte()
        if (first == 0) throw IOException("Invalid EBML number")
        var length = 1
        var mask = 0x80
        while (first and mask == 0) {
            mask = mask shr 1
            length++
        }
        var value = if (keepMarker) first.toLong() else (first and (mask - 1)).toLong()
        var allOnes = (first and (mask - 1)) == mask - 1
        repeat(length - 1) {
            val b = byte()
            allOnes = allOnes && b == 0xFF
            value = (value shl 8) or b.toLong()
        }
        return if (!keepMarker && allOnes) UNKNOWN_SIZE else value
    }

    /** The next element header, or null at the end of the stream. */
    fun header(): Header? {
        pushedBack?.let {
            pushedBack = null
            return it
        }
        val start = position
        val id = try {
            vint(true)
        } catch (e: EOFException) {
            return null
        }
        val size = vint(false)
        return Header(id, size, start, position)
    }

    fun unread(header: Header) {
        pushedBack = header
    }

    fun uint(size: Long): Long {
        if (size > 8) {
            skip(size)
            return 0
        }
        var value = 0L
        for (b in bytes(size, 8)) value = (value shl 8) or (b.toLong() and 0xFF)
        return value
    }

    fun string(size: Long): String {
        val raw = bytes(size, MAX_STRING)
        val end = raw.indexOf(0).let { if (it < 0) raw.size else it }
        return String(raw, 0, end, Charsets.UTF_8)
    }

    /**
     * Calls [onChild] for each child until [end] (absolute) or, when the parent's size is
     * unknown, until a top-level element begins. [onChild] returns false to stop early.
     * Returns false if stopped early.
     */
    fun children(end: Long?, topLevel: Boolean = false, onChild: (Header) -> Boolean): Boolean {
        while (end == null || position < end) {
            val h = header() ?: return true
            if (end == null && !topLevel && h.id in TOP_LEVEL_IDS) {
                unread(h)
                return true
            }
            if (!onChild(h)) return false
            if (h.size != UNKNOWN_SIZE) {
                val target = h.payload + h.size
                if (position < target) skip(target - position)
            }
        }
        return true
    }

    companion object {
        const val UNKNOWN_SIZE = -1L
        private const val MAX_STRING = 64 * 1024
    }
}
