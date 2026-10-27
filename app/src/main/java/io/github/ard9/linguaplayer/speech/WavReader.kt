package io.github.ard9.linguaplayer.speech

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Reads the start of a 16-bit PCM WAV file as mono samples in -1..1 at [targetRate].
 * Tolerant of what VLC may write: any channel count (downmixed), any sample rate (resampled),
 * an unset data size, and audio longer than asked for (only [maxMs] is read).
 */
object WavReader {
    fun read(file: File, targetRate: Int, maxMs: Long): FloatArray {
        RandomAccessFile(file, "r").use { raf ->
            val header = ByteArray(12)
            raf.readFully(header)
            if (tag(header, 0) != "RIFF" || tag(header, 8) != "WAVE") throw IOException("Not a WAV file")

            var channels = 0
            var rate = 0
            val chunkHeader = ByteArray(8)
            while (raf.filePointer + 8 <= raf.length()) {
                raf.readFully(chunkHeader)
                val id = tag(chunkHeader, 0)
                val declared = ByteBuffer.wrap(chunkHeader, 4, 4).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xFFFFFFFFL
                when (id) {
                    "fmt " -> {
                        val fmt = ByteArray(minOf(declared, 64L).toInt())
                        raf.readFully(fmt)
                        val b = ByteBuffer.wrap(fmt).order(ByteOrder.LITTLE_ENDIAN)
                        val format = b.getShort(0).toInt() and 0xFFFF
                        channels = b.getShort(2).toInt()
                        rate = b.getInt(4)
                        val bits = b.getShort(14).toInt()
                        if ((format != 1 && format != 0xFFFE) || bits != 16 || channels !in 1..8 || rate !in 8_000..192_000) {
                            throw IOException("Unsupported audio ($format, $bits bit, $channels ch, $rate Hz)")
                        }
                        raf.seek(raf.filePointer - fmt.size + declared + (declared and 1))
                    }
                    "data" -> {
                        if (channels == 0) throw IOException("Audio format missing")
                        val available = raf.length() - raf.filePointer
                        val size = if (declared == 0L || declared > available) available else declared
                        val wanted = maxMs * rate / 1000 * channels * 2
                        val bytes = ByteArray(minOf(size, wanted).toInt() / (channels * 2) * (channels * 2))
                        raf.readFully(bytes)
                        return resample(downmix(bytes, channels), rate, targetRate)
                    }
                    else -> raf.seek(raf.filePointer + declared + (declared and 1))
                }
            }
            throw IOException("No audio data")
        }
    }

    private fun downmix(bytes: ByteArray, channels: Int): FloatArray {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        val frames = bytes.size / (2 * channels)
        return FloatArray(frames) { f ->
            var sum = 0f
            for (c in 0 until channels) sum += b.getShort((f * channels + c) * 2) / 32768f
            sum / channels
        }
    }

    /** Linear resampling; good enough for speech detection. */
    private fun resample(input: FloatArray, from: Int, to: Int): FloatArray {
        if (from == to || input.isEmpty()) return input
        val outSize = (input.size.toLong() * to / from).toInt()
        val step = from.toDouble() / to
        return FloatArray(outSize) { i ->
            val pos = i * step
            val i0 = pos.toInt().coerceAtMost(input.size - 1)
            val i1 = (i0 + 1).coerceAtMost(input.size - 1)
            val frac = (pos - i0).toFloat()
            input[i0] * (1 - frac) + input[i1] * frac
        }
    }

    private fun tag(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
}
