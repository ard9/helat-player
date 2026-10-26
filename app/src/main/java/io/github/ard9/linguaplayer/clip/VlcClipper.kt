package io.github.ard9.linguaplayer.clip

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import io.github.ard9.linguaplayer.player.openVlcMedia
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.MediaPlayer
import java.io.File
import java.io.IOException
import java.util.Locale

/**
 * Fallback: cuts a range with libVLC's stream output and saves the audio as a small
 * mono WAV file. Slower than Transformer, but reads every format VLC can play.
 * Must be called on the main thread; the callback is delivered on the main thread.
 */
class VlcClipper(private val context: Context, private val libVlc: () -> LibVLC) {
    private val handler = Handler(Looper.getMainLooper())

    fun clip(source: Uri, startMs: Long, endMs: Long, output: File, onDone: (Result<Unit>) -> Unit) {
        val opened = try {
            openVlcMedia(context, libVlc(), source)
        } catch (e: Exception) {
            onDone(Result.failure(e))
            return
        }
        val media = opened.media
        media.addOption(":start-time=${seconds(startMs)}")
        media.addOption(":stop-time=${seconds(endMs)}")
        media.addOption(":no-sout-video")
        media.addOption(":no-sout-spu")
        media.addOption(
            ":sout=#transcode{vcodec=none,scodec=none,acodec=s16l,channels=1,samplerate=22050}" +
                ":std{access=file,mux=wav,dst=${output.absolutePath}}"
        )

        val player = MediaPlayer(libVlc())
        player.setMedia(media)
        media.release()

        var finished = false
        lateinit var timeout: Runnable
        fun finish(error: Exception?) {
            if (finished) return
            finished = true
            handler.removeCallbacks(timeout)
            // Release outside the event callback; stopping also closes the WAV file.
            handler.post {
                player.setEventListener(null)
                player.stop()
                player.release()
                opened.close()
                if (error == null && output.length() > MIN_WAV_BYTES) {
                    onDone(Result.success(Unit))
                } else {
                    output.delete()
                    onDone(Result.failure(error ?: IOException("VLC produced no audio")))
                }
            }
        }
        timeout = Runnable { finish(IOException("Timed out while cutting the audio")) }

        player.setEventListener(MediaPlayer.EventListener { event ->
            when (event.type) {
                MediaPlayer.Event.EndReached, MediaPlayer.Event.Stopped -> finish(null)
                MediaPlayer.Event.EncounteredError -> finish(IOException("VLC could not read this media"))
            }
        })
        handler.postDelayed(timeout, (endMs - startMs) * 3 + TIMEOUT_EXTRA_MS)
        player.play()
    }

    private fun seconds(ms: Long) = String.format(Locale.US, "%.3f", ms / 1000.0)

    private companion object {
        const val MIN_WAV_BYTES = 1024L
        const val TIMEOUT_EXTRA_MS = 30_000L
    }
}
