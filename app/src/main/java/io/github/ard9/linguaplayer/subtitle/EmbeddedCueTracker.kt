package io.github.ard9.linguaplayer.subtitle

import kotlin.math.abs

/**
 * Embedded subtitle tracks only tell us when a cue appears or disappears,
 * so this rebuilds timed lines from those events as the video plays.
 */
class EmbeddedCueTracker {
    sealed interface SaveTarget {
        data class Ready(val line: SubtitleLine) : SaveTarget
        /** A line is on screen; its end time is known only when it disappears. */
        data object WaitForEnd : SaveTarget
        data object NoLine : SaveTarget
    }

    private val lines = mutableListOf<SubtitleLine>()
    private var openText: String? = null
    private var openStartMs = 0L

    /** Feed the current cue text. Returns the line that just ended, if any. */
    fun onCues(text: String, positionMs: Long): SubtitleLine? {
        val previous = openText
        if (previous != null && text == previous) return null

        var closed: SubtitleLine? = null
        if (previous != null) {
            val duration = positionMs - openStartMs
            if (duration in 1..MAX_LINE_MS) {
                closed = SubtitleLine(openStartMs, positionMs, previous)
                remember(closed)
            }
            openText = null
        }
        if (text.isNotBlank()) {
            openText = text
            openStartMs = positionMs
        }
        return closed
    }

    fun lineForSave(positionMs: Long): SaveTarget {
        if (openText != null) return SaveTarget.WaitForEnd
        val line = lines.lastOrNull { it.startMs <= positionMs } ?: return SaveTarget.NoLine
        return SaveTarget.Ready(line)
    }

    /** After a seek the open cue's timing is meaningless. */
    fun dropOpenLine() {
        openText = null
    }

    private fun remember(line: SubtitleLine) {
        lines.removeAll { it.text == line.text && abs(it.startMs - line.startMs) < 1000 }
        lines.add(line)
        lines.sortBy { it.startMs }
        if (lines.size > MAX_LINES) lines.removeAt(0)
    }

    private companion object {
        const val MAX_LINE_MS = 30_000L
        const val MAX_LINES = 3000
    }
}
