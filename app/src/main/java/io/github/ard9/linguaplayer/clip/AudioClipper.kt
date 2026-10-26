package io.github.ard9.linguaplayer.clip

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * Cuts a time range out of a video or audio source and saves only the audio as AAC (.m4a).
 * Must be called on the main thread; the callback is delivered on the main thread.
 * Lives at application scope so an export keeps running when the player screen closes.
 */
@OptIn(UnstableApi::class)
class AudioClipper(private val context: Context) {
    private val active = mutableSetOf<Transformer>()

    fun clip(source: Uri, startMs: Long, endMs: Long, output: File, onDone: (Result<Unit>) -> Unit) {
        val mediaItem = MediaItem.Builder()
            .setUri(source)
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()
        val edited = EditedMediaItem.Builder(mediaItem)
            .setRemoveVideo(true)
            .build()

        lateinit var transformer: Transformer
        transformer = Transformer.Builder(context)
            .setAudioMimeType(MimeTypes.AUDIO_AAC)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                    active.remove(transformer)
                    onDone(Result.success(Unit))
                }

                override fun onError(
                    composition: Composition,
                    exportResult: ExportResult,
                    exportException: ExportException,
                ) {
                    active.remove(transformer)
                    output.delete()
                    onDone(Result.failure(exportException))
                }
            })
            .build()
        active.add(transformer)
        transformer.start(edited, output.absolutePath)
    }
}
