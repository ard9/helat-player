package io.github.ard9.linguaplayer.clip

import io.github.ard9.linguaplayer.speech.WavReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class WavTest {
    @get:Rule
    val temp = TemporaryFolder()

    /**
     * Writes 16-bit PCM where every sample encodes its own second (value = second * 1000),
     * so tests can tell which part of the audio was kept.
     */
    private fun wav(seconds: Int, rate: Int, channels: Int, declaredDataSize: Int? = null): File {
        val frames = seconds * rate
        val data = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (f in 0 until frames) repeat(channels) { data.putShort(((f / rate) * 1000).toShort()) }
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
            put("RIFF".toByteArray()); putInt(36 + data.capacity()); put("WAVE".toByteArray())
            put("fmt ".toByteArray()); putInt(16); putShort(1); putShort(channels.toShort())
            putInt(rate); putInt(rate * channels * 2); putShort((channels * 2).toShort()); putShort(16)
            put("data".toByteArray()); putInt(declaredDataSize ?: data.capacity())
        }
        return temp.newFile().apply { writeBytes(header.array() + data.array()) }
    }

    private fun secondOf(sample: Float) = Math.round(sample * 32768 / 1000)

    @Test
    fun `trim keeps the last part`() {
        val file = wav(seconds = 10, rate = 8_000, channels = 1)
        WavTrim.keepLast(file, 4_000)
        val samples = WavReader.read(file, 8_000, 60_000)
        assertEquals(4 * 8_000, samples.size)
        assertEquals(6, secondOf(samples.first()))
        assertEquals(9, secondOf(samples.last()))
    }

    @Test
    fun `trim leaves audio of the right length alone`() {
        val file = wav(seconds = 4, rate = 8_000, channels = 1)
        val before = file.readBytes()
        WavTrim.keepLast(file, 4_000)
        assertTrue(before.contentEquals(file.readBytes()))
    }

    @Test
    fun `trim works when the data size was never written`() {
        val file = wav(seconds = 10, rate = 8_000, channels = 1, declaredDataSize = 0)
        WavTrim.keepLast(file, 2_000)
        val samples = WavReader.read(file, 8_000, 60_000)
        assertEquals(2 * 8_000, samples.size)
        assertEquals(8, secondOf(samples.first()))
    }

    @Test
    fun `reader downmixes, resamples and stops at the limit`() {
        val file = wav(seconds = 6, rate = 48_000, channels = 2)
        val samples = WavReader.read(file, 16_000, 3_000)
        assertTrue("size ${samples.size}", abs(samples.size - 3 * 16_000) <= 1)
        assertEquals(0, secondOf(samples.first()))
        assertEquals(2, secondOf(samples.last()))
    }
}
