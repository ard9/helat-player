package io.github.ard9.linguaplayer.clip

import android.net.Uri
import android.util.Log
import java.io.File

/**
 * Cuts the audio of a time range: tries the fast Transformer path first and falls back to
 * libVLC for formats Transformer cannot read. Main thread only.
 * Lives at application scope so a cut keeps running when the player screen closes.
 */
class ClipService(
    private val transformer: TransformerClipper,
    private val vlc: VlcClipper,
    private val outputFor: (id: String, extension: String) -> File,
) {
    /** On success returns the file that holds the clip. */
    fun clip(source: Uri, startMs: Long, endMs: Long, id: String, onDone: (Result<File>) -> Unit) {
        val m4a = outputFor(id, "m4a")
        transformer.clip(source, startMs, endMs, m4a) { first ->
            if (first.isSuccess) {
                onDone(Result.success(m4a))
            } else {
                Log.w(TAG, "Transformer failed, trying VLC", first.exceptionOrNull())
                val wav = outputFor(id, "wav")
                vlc.clip(source, startMs, endMs, wav) { second -> onDone(second.map { wav }) }
            }
        }
    }

    private companion object {
        const val TAG = "ClipService"
    }
}
