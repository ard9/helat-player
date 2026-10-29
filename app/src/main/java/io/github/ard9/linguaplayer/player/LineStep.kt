package io.github.ard9.linguaplayer.player

import io.github.ard9.linguaplayer.subtitle.SubtitleLine

/**
 * Where the back and forward buttons jump: the start of the previous or next subtitle line.
 * Pure logic on one track's lines; the player plays from a little before the start it returns.
 */
object LineStep {
    /**
     * A jump lands up to this long before a line starts (the lead-in), and that line still
     * counts as the current one, so pressing again moves on instead of landing on it again.
     */
    const val LEAD_TOLERANCE_MS = 500L

    /**
     * The start of the line before the current one. In the pause after a line, the line just
     * heard is the one to go back to. Null if there is none.
     */
    fun previousStart(lines: List<SubtitleLine>, nowMs: Long): Long? {
        val starts = distinctStarts(lines)
        val currentIndex = starts.indexOfLast { (start, _) -> start <= nowMs + LEAD_TOLERANCE_MS }
        if (currentIndex < 0) return null
        val (start, end) = starts[currentIndex]
        return if (nowMs >= end) start else starts.getOrNull(currentIndex - 1)?.first
    }

    /** The start of the first line after the current one, or null if there is none. */
    fun nextStart(lines: List<SubtitleLine>, nowMs: Long): Long? =
        distinctStarts(lines).firstOrNull { (start, _) -> start > nowMs + LEAD_TOLERANCE_MS }?.first

    /** Lines sorted by start; lines sharing a start count once, ending with the longest. */
    private fun distinctStarts(lines: List<SubtitleLine>): List<Pair<Long, Long>> =
        lines.groupBy { it.startMs }
            .map { (start, group) -> start to group.maxOf { it.endMs } }
            .sortedBy { it.first }
}
