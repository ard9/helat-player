package io.github.ard9.linguaplayer.speech

import java.io.File
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Reads a 16-bit PCM mono WAV file into samples in -1..1. */
object WavReader {
    private const val MAX_BYTES = 8 * 1024 * 1024

    fun readMono16(file: File, expectedRate: Int): FloatArray {
        val bytes = file.readBytes()
        if (bytes.size < 12 || bytes.size > MAX_BYTES) throw IOException("Unexpected audio size")
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        if (tag(bytes, 0) != "RIFF" || tag(bytes, 8) != "WAVE") throw IOException("Not a WAV file")

        var position = 12
        var formatOk = false
        while (position + 8 <= bytes.size) {
            val id = tag(bytes, position)
            val declared = buffer.getInt(position + 4)
            val body = position + 8
            when (id) {
                "fmt " -> {
                    val format = buffer.getShort(body).toInt()
                    val channels = buffer.getShort(body + 2).toInt()
                    val rate = buffer.getInt(body + 4)
                    val bits = buffer.getShort(body + 14).toInt()
                    // 1 = PCM, 0xFFFE = WAVE_FORMAT_EXTENSIBLE (still PCM for this use).
                    if ((format != 1 && format != 0xFFFE.toShort().toInt()) ||
                        channels != 1 || bits != 16 || rate != expectedRate
                    ) {
                        throw IOException("Unexpected audio format")
                    }
                    formatOk = true
                }
                "data" -> {
                    if (!formatOk) throw IOException("Audio format missing")
                    // Some writers leave the size unset; then the data runs to the end of the file.
                    val size = if (declared <= 0 || body + declared > bytes.size) bytes.size - body else declared
                    val count = size / 2
                    return FloatArray(count) { buffer.getShort(body + it * 2) / 32768f }
                }
            }
            if (declared < 0 || body + declared > bytes.size) break
            position = body + declared + (declared and 1)
        }
        throw IOException("No audio data")
    }

    private fun tag(bytes: ByteArray, at: Int) = String(bytes, at, 4, Charsets.US_ASCII)
}
