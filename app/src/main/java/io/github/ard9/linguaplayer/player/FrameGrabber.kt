package io.github.ard9.linguaplayer.player

import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import java.io.File
import java.io.FileOutputStream

/** Takes a picture of the current video frame shown in libVLC's video layout. */
object FrameGrabber {
    private const val MAX_WIDTH = 640

    /** Calls [onResult] on the main thread with the frame, or null if it could not be taken. */
    fun grab(videoLayout: View, onResult: (Bitmap?) -> Unit) {
        val surface = findVideoSurface(videoLayout)
        if (surface == null || surface.width <= 0 || surface.height <= 0 || !surface.holder.surface.isValid) {
            onResult(null)
            return
        }
        val scale = minOf(1f, MAX_WIDTH.toFloat() / surface.width)
        val bitmap = Bitmap.createBitmap(
            (surface.width * scale).toInt().coerceAtLeast(1),
            (surface.height * scale).toInt().coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        try {
            PixelCopy.request(surface, bitmap, { result ->
                if (result == PixelCopy.SUCCESS) onResult(bitmap) else onResult(null)
            }, Handler(Looper.getMainLooper()))
        } catch (e: IllegalArgumentException) {
            onResult(null)
        }
    }

    fun saveJpeg(bitmap: Bitmap, file: File): Boolean = try {
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.JPEG, 80, it) }
    } catch (e: Exception) {
        file.delete()
        false
    }

    /** libVLC names its video surface "surface_video"; fall back to the first SurfaceView. */
    private fun findVideoSurface(root: View): SurfaceView? {
        val id = root.resources.getIdentifier("surface_video", "id", root.context.packageName)
        if (id != 0) (root.findViewById<View>(id) as? SurfaceView)?.let { return it }
        return firstSurfaceView(root)
    }

    private fun firstSurfaceView(view: View): SurfaceView? {
        if (view is SurfaceView) return view
        if (view is ViewGroup) {
            for (i in 0 until view.childCount) {
                firstSurfaceView(view.getChildAt(i))?.let { return it }
            }
        }
        return null
    }
}
