package io.github.ard9.linguaplayer.roleplay

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaPlayer
import android.media.MediaRecorder
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import io.github.ard9.linguaplayer.speech.SileroVad
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import java.io.IOException
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/** Records the user's voice to WAV, optionally stopping by itself when they stop speaking. */
class VoiceRecorder(private val context: Context) {
    class Recording(val durationMs: Long, val heardSpeech: Boolean)

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /**
     * Records into [file] until [shouldStop], [maxMs], or (with [stopWhenSilent]) a pause after
     * speech. Blocking; call from a background thread.
     */
    @RequiresPermission(Manifest.permission.RECORD_AUDIO)
    fun record(file: File, stopWhenSilent: Boolean, maxMs: Long, shouldStop: () -> Boolean): Recording {
        val rate = SileroVad.SAMPLE_RATE
        val minBuffer = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBuffer <= 0) throw IOException("The microphone cannot record at $rate Hz")
        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            rate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuffer, SileroVad.CHUNK * 2 * 4),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            throw IOException("The microphone is not available")
        }
        val vad = SileroVad.Stream(context)
        val detector = SpeechEndDetector(maxMs = maxMs)
        val chunk = ShortArray(SileroVad.CHUNK)
        val floats = FloatArray(SileroVad.CHUNK)
        try {
            WavWriter(file, rate).use { wav ->
                recorder.startRecording()
                while (!shouldStop() && wav.durationMs < maxMs) {
                    var filled = 0
                    while (filled < chunk.size) {
                        val n = recorder.read(chunk, filled, chunk.size - filled)
                        if (n < 0) throw IOException("Recording failed ($n)")
                        filled += n
                    }
                    wav.write(chunk)
                    for (i in chunk.indices) floats[i] = chunk[i] / 32768f
                    val ended = detector.next(vad.next(floats))
                    if (stopWhenSilent && ended) break
                }
                recorder.stop()
                return Recording(wav.durationMs, detector.heardSpeech)
            }
        } finally {
            recorder.release()
        }
    }
}

/** Plays a recorded WAV and returns when it has finished (or is cancelled). */
suspend fun playRecording(file: File) {
    val player = MediaPlayer()
    try {
        suspendCancellableCoroutine { continuation ->
            try {
                player.setDataSource(file.path)
                player.setOnCompletionListener { if (continuation.isActive) continuation.resume(Unit) }
                player.setOnErrorListener { _, what, _ ->
                    if (continuation.isActive) continuation.resumeWithException(IOException("Playback error $what"))
                    true
                }
                player.prepare()
                player.start()
            } catch (e: Exception) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            continuation.invokeOnCancellation { runCatching { player.stop() } }
        }
    } finally {
        player.release()
    }
}
