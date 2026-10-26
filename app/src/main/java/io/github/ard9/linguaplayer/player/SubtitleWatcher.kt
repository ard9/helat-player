package io.github.ard9.linguaplayer.player

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.SurfaceView
import android.view.View
import android.view.ViewGroup
import kotlin.math.abs
import kotlin.math.max

/**
 * Watches the layer libVLC draws built-in subtitles on, and records when each subtitle
 * appears and disappears. Those moments are the start and end of the spoken line, so a line
 * can be saved accurately without a subtitle file. Main thread only.
 */
class SubtitleWatcher(
    private val layout: () -> View?,
    private val clock: () -> Long,
    private val isActive: () -> Boolean,
) {
    /** One subtitle as it was shown: when, and a picture of its text (for reading it). */
    data class Line(val startMs: Long, val endMs: Long, val image: Bitmap?)

    sealed interface SaveTarget {
        data class Ready(val line: Line) : SaveTarget
        /** A subtitle is on screen; its end is known once it disappears. */
        data object WaitForEnd : SaveTarget
        data object NoLine : SaveTarget
    }

    /** Called when a subtitle disappears or is replaced by the next one. */
    var onLineClosed: ((Line) -> Unit)? = null

    private val handler = Handler(Looper.getMainLooper())
    private val sample = Bitmap.createBitmap(SAMPLE_WIDTH, SAMPLE_HEIGHT, Bitmap.Config.ARGB_8888)
    private val samplePixels = IntArray(SAMPLE_WIDTH * SAMPLE_HEIGHT)
    private val lines = ArrayDeque<Line>()
    private var running = false
    private var busy = false
    private var generation = 0

    private var openSignature: BooleanArray? = null
    private var openStartMs = 0L
    private var openImage: Bitmap? = null

    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            if (isActive() && !busy) sampleOnce()
            handler.postDelayed(this, INTERVAL_MS)
        }
    }

    fun start() {
        if (running) return
        running = true
        handler.post(tick)
    }

    fun stop() {
        running = false
        handler.removeCallbacks(tick)
    }

    /** Forget everything, e.g. after a seek, when the timing of an open subtitle is lost. */
    fun reset() {
        generation++
        openSignature = null
        openImage = null
        lines.clear()
    }

    fun lineForSave(positionMs: Long): SaveTarget {
        if (openSignature != null) return SaveTarget.WaitForEnd
        val line = lines.lastOrNull { it.startMs <= positionMs && positionMs - it.endMs <= RECENT_MS }
            ?: return SaveTarget.NoLine
        return SaveTarget.Ready(line)
    }

    private fun sampleOnce() {
        val surface = findSubtitleSurface() ?: return
        if (surface.width <= 0 || surface.height <= 0 || !surface.holder.surface.isValid) return
        val timeMs = clock()
        val requestGeneration = generation
        busy = true
        try {
            PixelCopy.request(surface, sample, { result ->
                busy = false
                if (result == PixelCopy.SUCCESS && requestGeneration == generation) {
                    onSample(signature(), timeMs, surface)
                }
            }, handler)
        } catch (e: IllegalArgumentException) {
            busy = false
        }
    }

    private fun onSample(signature: BooleanArray, timeMs: Long, surface: SurfaceView) {
        val empty = signature.count { it } < MIN_CELLS
        val open = openSignature
        when {
            open == null -> if (!empty) openLine(signature, timeMs, surface)
            empty -> closeLine(timeMs)
            changed(open, signature) -> {
                closeLine(timeMs)
                openLine(signature, timeMs, surface)
            }
        }
    }

    private fun openLine(signature: BooleanArray, timeMs: Long, surface: SurfaceView) {
        openSignature = signature
        openStartMs = timeMs
        openImage = null
        captureText(surface) { image -> if (openSignature === signature) openImage = image }
    }

    private fun closeLine(timeMs: Long) {
        val line = Line(openStartMs, timeMs, openImage)
        openSignature = null
        openImage = null
        if (line.endMs - line.startMs !in MIN_LINE_MS..MAX_LINE_MS) return
        // A replay (for example a preview) shows the same line again; keep one copy.
        lines.removeAll { abs(it.startMs - line.startMs) < 400 }
        lines.addLast(line)
        while (lines.size > MAX_LINES) lines.removeFirst()
        onLineClosed?.invoke(line)
    }

    /** Which cells of a coarse grid contain subtitle pixels. */
    private fun signature(): BooleanArray {
        sample.getPixels(samplePixels, 0, SAMPLE_WIDTH, 0, 0, SAMPLE_WIDTH, SAMPLE_HEIGHT)
        val cells = BooleanArray(GRID_COLUMNS * GRID_ROWS)
        val counts = IntArray(cells.size)
        val cellWidth = SAMPLE_WIDTH / GRID_COLUMNS
        val cellHeight = SAMPLE_HEIGHT / GRID_ROWS
        for (y in 0 until SAMPLE_HEIGHT) {
            for (x in 0 until SAMPLE_WIDTH) {
                if (Color.alpha(samplePixels[y * SAMPLE_WIDTH + x]) > ALPHA_THRESHOLD) {
                    val cell = (y / cellHeight).coerceAtMost(GRID_ROWS - 1) * GRID_COLUMNS +
                        (x / cellWidth).coerceAtMost(GRID_COLUMNS - 1)
                    counts[cell]++
                }
            }
        }
        for (i in cells.indices) cells[i] = counts[i] >= PIXELS_PER_CELL
        return cells
    }

    private fun changed(a: BooleanArray, b: BooleanArray): Boolean {
        var distance = 0
        for (i in a.indices) if (a[i] != b[i]) distance++
        val size = max(a.count { it }, b.count { it })
        return distance > max(MIN_CHANGED_CELLS, (size * CHANGE_RATIO).toInt())
    }

    /** A larger copy of the subtitle, cropped and put on black so text recognition can read it. */
    private fun captureText(surface: SurfaceView, onResult: (Bitmap?) -> Unit) {
        val scale = minOf(1f, CAPTURE_MAX_WIDTH.toFloat() / surface.width)
        val width = (surface.width * scale).toInt().coerceAtLeast(1)
        val height = (surface.height * scale).toInt().coerceAtLeast(1)
        val full = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        try {
            PixelCopy.request(surface, full, { result ->
                onResult(if (result == PixelCopy.SUCCESS) cropOnBlack(full) else null)
            }, handler)
        } catch (e: IllegalArgumentException) {
            onResult(null)
        }
    }

    private fun cropOnBlack(source: Bitmap): Bitmap? {
        val w = source.width
        val h = source.height
        val pixels = IntArray(w * h)
        source.getPixels(pixels, 0, w, 0, 0, w, h)
        var left = w
        var top = h
        var right = -1
        var bottom = -1
        for (y in 0 until h) {
            for (x in 0 until w) {
                if (Color.alpha(pixels[y * w + x]) > ALPHA_THRESHOLD) {
                    if (x < left) left = x
                    if (x > right) right = x
                    if (y < top) top = y
                    if (y > bottom) bottom = y
                }
            }
        }
        if (right < 0) return null
        left = (left - CROP_MARGIN).coerceAtLeast(0)
        top = (top - CROP_MARGIN).coerceAtLeast(0)
        right = (right + CROP_MARGIN).coerceAtMost(w - 1)
        bottom = (bottom + CROP_MARGIN).coerceAtMost(h - 1)
        val cropped = Bitmap.createBitmap(source, left, top, right - left + 1, bottom - top + 1)
        val result = Bitmap.createBitmap(cropped.width, cropped.height, Bitmap.Config.ARGB_8888)
        Canvas(result).apply {
            drawColor(Color.BLACK)
            drawBitmap(cropped, 0f, 0f, null)
        }
        return result
    }

    /** libVLC names its subtitle layer "surface_subtitles"; otherwise it is the second SurfaceView. */
    private fun findSubtitleSurface(): SurfaceView? {
        val root = layout() ?: return null
        val id = root.resources.getIdentifier("surface_subtitles", "id", root.context.packageName)
        if (id != 0) (root.findViewById<View>(id) as? SurfaceView)?.let { return it }
        val surfaces = mutableListOf<SurfaceView>()
        collectSurfaces(root, surfaces)
        return surfaces.getOrNull(1)
    }

    private fun collectSurfaces(view: View, into: MutableList<SurfaceView>) {
        if (view is SurfaceView) into.add(view)
        if (view is ViewGroup) for (i in 0 until view.childCount) collectSurfaces(view.getChildAt(i), into)
    }

    private companion object {
        const val INTERVAL_MS = 100L
        const val SAMPLE_WIDTH = 192
        const val SAMPLE_HEIGHT = 108
        const val GRID_COLUMNS = 32
        const val GRID_ROWS = 18
        const val PIXELS_PER_CELL = 3
        const val ALPHA_THRESHOLD = 100
        const val MIN_CELLS = 2
        const val MIN_CHANGED_CELLS = 4
        const val CHANGE_RATIO = 0.25f
        const val MIN_LINE_MS = 300L
        const val MAX_LINE_MS = 20_000L
        const val MAX_LINES = 30
        const val RECENT_MS = 15_000L
        const val CAPTURE_MAX_WIDTH = 1280
        const val CROP_MARGIN = 12
    }
}
