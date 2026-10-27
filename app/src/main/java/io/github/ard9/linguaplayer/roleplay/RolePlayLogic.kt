package io.github.ard9.linguaplayer.roleplay

import io.github.ard9.linguaplayer.subtitle.SubtitleLine
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** One subtitle line of a role-play scene and who says it. */
data class RoleLine(val startMs: Long, val endMs: Long, val text: String, val mine: Boolean)

object Roles {
    /** Alternates roles line by line; the actor says the first line unless [iStart]. */
    fun alternate(lines: List<SubtitleLine>, iStart: Boolean = false): List<RoleLine> =
        lines.mapIndexed { i, line -> RoleLine(line.startMs, line.endMs, line.text, mine = (i % 2 == 0) == iStart) }
}

/**
 * Decides when the user has finished saying a line, from speech probabilities that arrive
 * every [frameMs]: after some speech, a pause of [silenceMs] ends it. Without any speech it
 * gives up after [noSpeechMs]; it never runs longer than [maxMs].
 */
class SpeechEndDetector(
    private val frameMs: Double = 32.0,
    private val silenceMs: Long = 900,
    private val noSpeechMs: Long = 6_000,
    private val maxMs: Long = 20_000,
) {
    private var elapsed = 0.0
    private var spoke = false
    private var quietFor = 0.0
    private var speechFor = 0.0

    /** Where speech first began, in ms from the start (0 until speech is heard). */
    var speechStartMs = 0L
        private set

    /** True once the speaking turn is over. */
    fun next(probability: Float): Boolean {
        elapsed += frameMs
        if (probability >= SPEECH) {
            speechFor += frameMs
            quietFor = 0.0
            // A short click is not speech; about a tenth of a second is.
            if (speechFor >= MIN_SPEECH_MS && !spoke) {
                spoke = true
                speechStartMs = (elapsed - speechFor).toLong()
            }
        } else if (probability < QUIET) {
            speechFor = 0.0
            quietFor += frameMs
        }
        return when {
            elapsed >= maxMs -> true
            spoke -> quietFor >= silenceMs
            else -> elapsed >= noSpeechMs
        }
    }

    /** Whether any speech was heard. */
    val heardSpeech: Boolean get() = spoke

    private companion object {
        const val SPEECH = 0.5f
        const val QUIET = 0.35f
        const val MIN_SPEECH_MS = 96.0
    }
}

/** Writes 16-bit mono PCM to a WAV file; the sizes in the header are filled in by [close]. */
class WavWriter(private val file: File, private val sampleRate: Int) : AutoCloseable {
    private val out = RandomAccessFile(file, "rw")
    private var dataBytes = 0L

    init {
        out.setLength(0)
        out.write(header(0))
    }

    fun write(samples: ShortArray, count: Int = samples.size) {
        val buffer = ByteBuffer.allocate(count * 2).order(ByteOrder.LITTLE_ENDIAN)
        for (i in 0 until count) buffer.putShort(samples[i])
        out.write(buffer.array())
        dataBytes += count * 2
    }

    val durationMs: Long get() = dataBytes / 2 * 1000 / sampleRate

    override fun close() {
        out.seek(0)
        out.write(header(dataBytes))
        out.close()
    }

    private fun header(dataSize: Long): ByteArray = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN).apply {
        put("RIFF".toByteArray(Charsets.US_ASCII)); putInt((36 + dataSize).toInt())
        put("WAVE".toByteArray(Charsets.US_ASCII))
        put("fmt ".toByteArray(Charsets.US_ASCII)); putInt(16)
        putShort(1); putShort(1); putInt(sampleRate); putInt(sampleRate * 2); putShort(2); putShort(16)
        put("data".toByteArray(Charsets.US_ASCII)); putInt(dataSize.toInt())
    }.array()
}
