package io.github.ard9.linguaplayer.clip

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * VLC sometimes ignores :start-time (it then writes audio from the start of the media up to
 * the end time) or overshoots the end. Both leave a WAV longer than asked for; this keeps
 * only the last [wantedMs], which ends at the requested end time.
 */
object WavTrim {
    private const val TOLERANCE_MS = 1_500L

    fun keepLast(file: File, wantedMs: Long) {
        val info = RandomAccessFile(file, "r").use { parse(it) }
        val durationMs = info.dataSize * 1000 / info.byteRate
        if (durationMs <= wantedMs + TOLERANCE_MS) return

        val keep = (wantedMs * info.byteRate / 1000) / info.blockAlign * info.blockAlign
        val skip = (info.dataSize - keep) / info.blockAlign * info.blockAlign
        val tmp = File(file.parentFile, file.name + ".trim")
        RandomAccessFile(file, "r").use { input ->
            tmp.outputStream().buffered().use { out ->
                out.write(header(info, keep))
                input.seek(info.dataOffset + skip)
                val buffer = ByteArray(64 * 1024)
                var left = keep
                while (left > 0) {
                    val n = input.read(buffer, 0, minOf(buffer.size.toLong(), left).toInt())
                    if (n < 0) break
                    out.write(buffer, 0, n)
                    left -= n
                }
            }
        }
        if (!tmp.renameTo(file)) {
            file.delete()
            if (!tmp.renameTo(file)) throw IOException("Could not save the trimmed audio")
        }
    }

    private class Info(
        val fmt: ByteArray,
        val byteRate: Long,
        val blockAlign: Long,
        val dataOffset: Long,
        val dataSize: Long,
    )

    private fun parse(raf: RandomAccessFile): Info {
        val head = ByteArray(12)
        raf.readFully(head)
        if (String(head, 0, 4, Charsets.US_ASCII) != "RIFF") throw IOException("Not a WAV file")
        var fmt: ByteArray? = null
        val chunk = ByteArray(8)
        while (raf.filePointer + 8 <= raf.length()) {
            raf.readFully(chunk)
            val id = String(chunk, 0, 4, Charsets.US_ASCII)
            val size = ByteBuffer.wrap(chunk, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
            if (id == "fmt ") {
                fmt = ByteArray(size.toInt().coerceIn(16, 64)).also { raf.readFully(it) }
                raf.seek(raf.filePointer - fmt.size + size + (size and 1))
            } else if (id == "data") {
                val f = fmt ?: throw IOException("Audio format missing")
                val b = ByteBuffer.wrap(f).order(ByteOrder.LITTLE_ENDIAN)
                val byteRate = b.getInt(8).toLong()
                val blockAlign = b.getShort(12).toLong()
                if (byteRate <= 0 || blockAlign <= 0) throw IOException("Bad audio format")
                val available = raf.length() - raf.filePointer
                val dataSize = if (size == 0L || size > available) available else size
                return Info(f, byteRate, blockAlign, raf.filePointer, dataSize)
            } else {
                raf.seek(raf.filePointer + size + (size and 1))
            }
        }
        throw IOException("No audio data")
    }

    private fun header(info: Info, dataSize: Long): ByteArray {
        val b = ByteBuffer.allocate(12 + 8 + info.fmt.size + 8).order(ByteOrder.LITTLE_ENDIAN)
        b.put("RIFF".toByteArray(Charsets.US_ASCII))
        b.putInt((4 + 8 + info.fmt.size + 8 + dataSize).toInt())
        b.put("WAVE".toByteArray(Charsets.US_ASCII))
        b.put("fmt ".toByteArray(Charsets.US_ASCII))
        b.putInt(info.fmt.size)
        b.put(info.fmt)
        b.put("data".toByteArray(Charsets.US_ASCII))
        b.putInt(dataSize.toInt())
        return b.array()
    }
}
