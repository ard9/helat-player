package io.github.ard9.linguaplayer.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** Loads a card picture in the background, downsampled to about [maxWidthPx]. */
@Composable
fun rememberCardImage(file: File?, maxWidthPx: Int): ImageBitmap? {
    val image by produceState<ImageBitmap?>(initialValue = null, file, maxWidthPx) {
        value = if (file == null) null else withContext(Dispatchers.IO) { decode(file, maxWidthPx) }
    }
    return image
}

private fun decode(file: File, maxWidthPx: Int): ImageBitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)
    var sample = 1
    while (bounds.outWidth / (sample * 2) >= maxWidthPx) sample *= 2
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return BitmapFactory.decodeFile(file.path, options)?.asImageBitmap()
}
