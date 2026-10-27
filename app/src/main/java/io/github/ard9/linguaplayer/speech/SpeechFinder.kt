package io.github.ard9.linguaplayer.speech

import android.content.Context
import android.net.Uri
import io.github.ard9.linguaplayer.clip.VlcClipper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.max
import kotlin.math.roundToLong

/**
 * Without subtitles: looks at the audio from 8 s before to 6 s after the moment Save was
 * pressed and finds where the sentence starts and ends (the pauses around it).
 */
class SpeechFinder(private val context: Context, private val vlc: VlcClipper) {
    data class Found(
        val startMs: Long,
        val endMs: Long,
        /** The sentence may begin before the analysed audio. */
        val cutAtStart: Boolean,
        /** The sentence may continue after the analysed audio. */
        val cutAtEnd: Boolean,
    )

    /** What was found, plus a short description of the audio checked (to explain a miss). */
    data class Outcome(val found: Found?, val details: String)

    /** Main thread. [onDone] runs on the main thread. */
    fun find(scope: CoroutineScope, source: Uri, positionMs: Long, onDone: (Result<Outcome>) -> Unit) {
        val windowStart = max(0L, positionMs - BEFORE_MS)
        val windowEnd = positionMs + AFTER_MS
        val wav = File(context.cacheDir, "speech-${System.nanoTime()}.wav")
        vlc.clip(source, windowStart, windowEnd, wav, sampleRate = SileroVad.SAMPLE_RATE) { cut ->
            if (cut.isFailure) {
                wav.delete()
                onDone(Result.failure(cut.exceptionOrNull()!!))
            } else {
                scope.launch {
                    val result = withContext(Dispatchers.Default) {
                        try {
                            runCatching { analyse(wav, windowStart, windowEnd - windowStart, positionMs) }
                        } finally {
                            wav.delete()
                        }
                    }
                    onDone(result)
                }
            }
        }
    }

    private fun analyse(wav: File, windowStart: Long, windowMs: Long, positionMs: Long): Outcome {
        val samples = WavReader.read(wav, SileroVad.SAMPLE_RATE, windowMs)
        val probabilities = SileroVad.probabilities(context, samples)
        val analysedMs = probabilities.size * SentenceFinder.FRAME_MS
        val peak = samples.maxOfOrNull { kotlin.math.abs(it) } ?: 0f
        val details = String.format(
            java.util.Locale.US,
            "checked %.1f s, speech score %.2f, level %.2f",
            analysedMs / 1000,
            probabilities.maxOrNull() ?: 0f,
            peak,
        )
        val span = SentenceFinder.find(probabilities, (positionMs - windowStart).toDouble())
            ?: return Outcome(null, details)
        val edge = 2 * SentenceFinder.FRAME_MS
        return Outcome(found(span, windowStart, analysedMs, edge), details)
    }

    private fun found(span: SentenceFinder.Span, windowStart: Long, analysedMs: Double, edge: Double): Found {
        return Found(
            startMs = max(0L, windowStart + span.startMs.roundToLong() - PADDING_MS),
            endMs = windowStart + span.endMs.roundToLong() + PADDING_MS,
            cutAtStart = windowStart > 0 && span.startMs <= edge,
            cutAtEnd = span.endMs >= analysedMs - edge,
        )
    }

    private companion object {
        const val BEFORE_MS = 8_000L
        const val AFTER_MS = 6_000L
        const val PADDING_MS = 150L
    }
}
