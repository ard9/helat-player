package io.github.ard9.linguaplayer.player

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

/** Reads the text of a subtitle picture on the device (ML Kit, bundled model, no network). */
object SubtitleOcr {
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }

    /** Calls [onResult] on the main thread with the text, or "" if nothing could be read. */
    fun read(image: Bitmap, onResult: (String) -> Unit) {
        try {
            recognizer.process(InputImage.fromBitmap(image, 0))
                .addOnSuccessListener { result -> onResult(joinLines(result.textBlocks.flatMap { it.lines }.map { it.text })) }
                .addOnFailureListener { onResult("") }
        } catch (e: Exception) {
            onResult("")
        }
    }

    /** Lines of one sentence are joined with spaces; dialogue lines starting with "-" stay separate. */
    private fun joinLines(lines: List<String>): String {
        val builder = StringBuilder()
        for (raw in lines) {
            val line = raw.trim()
            if (line.isEmpty()) continue
            if (builder.isNotEmpty()) builder.append(if (line.startsWith("-")) "\n" else " ")
            builder.append(line)
        }
        return builder.toString()
    }
}
